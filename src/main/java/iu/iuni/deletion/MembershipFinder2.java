package iu.iuni.deletion;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FileStatus;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.dataset.DataPartition;
import edu.iu.dsc.tws.api.dataset.DataPartitionConsumer;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.FlatMapFunc;
import edu.iu.dsc.tws.api.tset.fn.RecordCollector;
import edu.iu.dsc.tws.api.tset.fn.SinkFunc;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import edu.iu.dsc.tws.tset.sets.batch.CachedTSet;
import edu.iu.dsc.tws.tset.sets.batch.KeyedSourceTSet;
import edu.iu.dsc.tws.tset.sets.batch.SinkTSet;
import iu.iuni.deletion.io.TweetIdDateReader;
import iu.iuni.deletion.io.TweetIdReader;
import iu.iuni.deletion.io.TweetWriter;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MembershipFinder2 implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(MembershipFinder2.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String tweetInput = args[0];
    String deleteInput = args[1];
    String outputDir = args[2];
    int parallel = Integer.parseInt(args[3]);
    int memory = Integer.parseInt(args[4]);
    String date = args[5];

    jobConfig.put(Context.ARG_TWEET_INPUT_DIRECTORY, tweetInput);
    jobConfig.put(Context.ARG_DELETE_INPUT_DIRECTORY, deleteInput);
    jobConfig.put(Context.ARG_OUTPUT_DIRECTORY, outputDir);
    jobConfig.put(Context.ARG_PARALLEL, parallel);
    jobConfig.put(Context.ARG_DATE, date);

    Twister2Job twister2Job = Twister2Job.newBuilder()
        .setJobName(MembershipFinder2.class.getName())
        .setWorkerClass(MembershipFinder2.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

  @Override
  public void execute(WorkerEnvironment workerEnvironment) {
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnvironment);
    Config config = workerEnvironment.getConfig();
    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);
    // now lets read the second input file and cache it
    CachedTSet<BigInteger> deleteInput = batchEnv
        .createSource(new DeleteTweetSource(), parallel)
        .partition(new HashingPartitioner<>())
        .flatmap(new FlatMapFunc<BigInteger, BigInteger>() {
          int count = 0;

          @Override
          public void flatMap(BigInteger input, RecordCollector<BigInteger> collector) {
            count++;
            collector.collect(input);
          }

          @Override
          public void close() {
            LOG.info("Cache produced tuples - " + count);
          }
        })
        .cache();

    // now lets read the partitioned file and find the membership
    KeyedSourceTSet<String, BigInteger> inputRecords = batchEnv.createKeyedSource(new TweetIdSource(), parallel).addInput("input", deleteInput);
    SinkTSet<Iterator<Tuple<String, BigInteger>>> sink = inputRecords.keyedPartition(new HashingPartitioner<>()).sink(new MembershipWriter());

    batchEnv.eval(sink);
    batchEnv.finishEval(sink);
  }

  private static class DeleteTweetSource implements SourceFunc<BigInteger> {
    private Queue<TweetIdReader> readers;
    private TSetContext ctx;
    private String inputDir;
    private TweetIdReader currentReader;
    private int count = 0;

    @Override
    public void prepare(TSetContext context) {
      this.ctx = context;
      inputDir = context.getConfig().getStringValue(Context.ARG_DELETE_INPUT_DIRECTORY);
      readers = new LinkedList<>();
      try {
        List<String> inputFiles = new ArrayList<>();
        FileSystem fs = FileSystemUtils.get(new Path(inputDir).toUri(), context.getConfig());
        FileStatus[] fileStatuses = fs.listFiles(new Path(inputDir));
        for (FileStatus s : fileStatuses) {
          inputFiles.add(s.getPath().getName());
        }
        inputFiles.sort(new Comparator<String>() {
          @Override
          public int compare(String s, String t1) {
            return s.compareTo(t1);
          }
        });

        int i = context.getIndex();
        StringBuilder files = new StringBuilder();
        while (i < context.getParallelism()) {
          final String fileName = inputDir + "/" + inputFiles.get(i);
          readers.offer(new TweetIdReader(fileName, context.getConfig(), ","));
          files.append(fileName).append(" ");
          i += context.getParallelism();
        }
        LOG.log(Level.INFO, String.format("input file list %s", files.toString()));
      } catch (IOException e) {
        LOG.log(Level.INFO, "There is an exception", e);
        throw new RuntimeException("There is an exception", e);
      }
      // lets get all the files, sort them and assign accordingly
      currentReader = readers.poll();
    }

    @Override
    public boolean hasNext() {
      try {
        do {
          boolean b = currentReader.reachedEnd();
          if (b) {
            LOG.info("Done reading from file - " + currentReader.getFileName().getPath());
            currentReader = readers.poll();
          } else {
            return true;
          }
        } while (currentReader != null);

        LOG.info("READ DELETE TUPLES " + count);
        return false;
      } catch (Exception e) {
        throw new RuntimeException("Failed to read", e);
      }
    }

    @Override
    public BigInteger next() {
      try {
        count++;
        return currentReader.nextRecord();
      } catch (Exception e) {
        throw new RuntimeException("Failed to read next", e);
      }
    }
  }

  private static class TweetIdSource implements SourceFunc<Tuple<String, BigInteger>> {
    private Queue<TweetIdDateReader> readers;
    private TSetContext ctx;
    private String inputDir;
    private TweetIdDateReader currentReader;
    private int count = 0;
    private int tweetCount = 0;
    private Set<BigInteger> inputMap = new HashSet<>();
    private Tuple<String, BigInteger> currentTuple = null;
    private Set<String> months = new HashSet<>();
    private Set<String> months2 = new HashSet<>();

    @Override
    public void prepare(TSetContext context) {
      this.ctx = context;

      DataPartition a = context.getInput("input");
      DataPartitionConsumer<BigInteger> consumer = a.getConsumer();
      int inputCount = 0;
      while (consumer.hasNext()) {
        BigInteger bigIntegerLongTuple = consumer.next();
        inputMap.add(bigIntegerLongTuple);
        inputCount++;

//        int i = Math.abs(bigIntegerLongTuple.hashCode()) % ctx.getParallelism();
//        if (i != ctx.getIndex() && inputCount % 1000 == 0) {
//          LOG.warning("############## UNMATCHED TWEET TUPLE ############ " + bigIntegerLongTuple.hashCode() + ", " + ctx.getIndex() + ", " + i);
//        }
      }
      LOG.info("ADDED TUPLES TO INPUT MAP " + inputCount);

      String prefix = context.getConfig().getStringValue(Context.ARG_TWEET_INPUT_DIRECTORY);
      inputDir = prefix + "/partitioned/" + context.getIndex();

      readers = new LinkedList<>();
      try {
        List<String> inputFiles = new ArrayList<>();
        FileSystem fs = FileSystemUtils.get(new Path(inputDir).toUri(), context.getConfig());
        FileStatus[] fileStatuses = fs.listFiles(new Path(inputDir));
        for (FileStatus s : fileStatuses) {
          inputFiles.add(s.getPath().getName());
        }
        inputFiles.sort(new Comparator<String>() {
          @Override
          public int compare(String s, String t1) {
            return s.compareTo(t1);
          }
        });

        StringBuilder files = new StringBuilder();
        for (String s : inputFiles) {
          final String fileName = inputDir + "/" + s;
          readers.offer(new TweetIdDateReader(fileName, context.getConfig(), "\\s+"));
          files.append(fileName).append(" ");
        }
        LOG.log(Level.INFO, String.format("input file list %s", files.toString()));
      } catch (IOException e) {
        LOG.log(Level.INFO, "There is an exception", e);
        throw new RuntimeException("There is an exception", e);
      }
      // lets get all the files, sort them and assign accordingly
      currentReader = readers.poll();
    }

    @Override
    public boolean hasNext() {
      try {
        do {
          boolean b = currentReader.reachedEnd();
          if (b) {
            LOG.info("Done reading from file - " + currentReader.getFileName().getPath());
            currentReader = readers.poll();
          } else {
            Tuple<BigInteger, String> input = currentReader.nextRecord();
            if (inputMap.contains(input.getKey())) {
              currentTuple = new Tuple<>(input.getValue(), input.getKey());
              months.add(input.getValue());
              return true;
            } else {
              currentTuple = null;
            }
//            int i = Math.abs(input.hashCode()) % ctx.getParallelism();
//            if (i != ctx.getIndex()) {
//              LOG.warning("*************** UNMATCHED TWEET TUPLE ***************** " + input.hashCode() + ", " + ctx.getIndex() + ", " + i);
//            }
            tweetCount++;
            months2.add(input.getValue());
          }
        } while (currentReader != null);

        LOG.info("MONTHS " + months);
        LOG.info("MONTHS2 " + months2);
        LOG.info("MATCHED tuples " + count + " Unmatched tuples: " + tweetCount);
        return false;
      } catch (Exception e) {
        throw new RuntimeException("Failed to read", e);
      }
    }

    @Override
    public Tuple<String, BigInteger> next() {
      try {
        count++;
        return currentTuple;
      } catch (Exception e) {
        throw new RuntimeException("Failed to read next", e);
      }
    }
  }

  private static class MembershipWriter implements SinkFunc<Iterator<Tuple<String, BigInteger>>> {
    Map<String, TweetWriter> writers = new HashMap<>();

    TSetContext ctx;

    public MembershipWriter() {
    }

    @Override
    public void prepare(TSetContext context) {
      ctx = context;
    }

    private TweetWriter getWriter(String month) {
      if (writers.containsKey(month)) {
        return writers.get(month);
      } else {
        try {
          String prefix = ctx.getConfig().getStringValue(Context.ARG_OUTPUT_DIRECTORY);
          String date = ctx.getConfig().getStringValue(Context.ARG_DATE);
          LOG.info("Writing file " + month);
          TweetWriter writer = new TweetWriter(prefix + "/membership/" + date + "/" + month, ctx.getConfig());
          writers.put(month, writer);
          return writer;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean add(Iterator<Tuple<String, BigInteger>> value) {
      while (value.hasNext()) {
        Tuple<String, BigInteger> input = value.next();
        try {
          TweetWriter writer = getWriter(input.getKey());
          writer.write(input.getValue().toString());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      for (TweetWriter w : writers.values()) {
        w.close();
      }
      return true;
    }
  }
}
