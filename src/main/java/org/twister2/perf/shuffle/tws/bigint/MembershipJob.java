package org.twister2.perf.shuffle.tws.bigint;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.dataset.DataPartition;
import edu.iu.dsc.tws.api.dataset.DataPartitionConsumer;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.*;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.sets.batch.CachedTSet;
import edu.iu.dsc.tws.tset.sets.batch.SinkTSet;
import edu.iu.dsc.tws.tset.sets.batch.SourceTSet;
import org.twister2.perf.io.TweetBufferedOutputWriter;
import org.twister2.perf.io.TwitterInputReader;
import org.twister2.perf.shuffle.Context;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Finding the membership
 */
public class MembershipJob implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(MembershipJob.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);

    jobConfig.put(Context.ARG_FILE_PREFIX, filePrefix);
    jobConfig.put(Context.ARG_PARALLEL, parallel);

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(MembershipJob.class.getName())
        .setWorkerClass(MembershipJob.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(new JobConfig())
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
    CachedTSet<Tuple<BigInteger, Long>> secondInput = batchEnv.createSource(new SourceFunc<Tuple<BigInteger, Long>>() {
      TwitterInputReader reader;

      @Override
      public void prepare(TSetContext context) {
        String prefix = context.getConfig().getStringValue(Context.ARG_FILE_PREFIX);
        reader = new TwitterInputReader(prefix + "/data/second-input-" + context.getIndex());
      }

      @Override
      public boolean hasNext() {
        try {
          return reader.hasNext();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public Tuple<BigInteger, Long> next() {
        try {
          return reader.next();
        } catch (Exception e) {
          throw new RuntimeException();
        }
      }
    }, parallel).mapToTuple(new MapFunc<Tuple<BigInteger, Long>, Tuple<BigInteger, Long>>() {
      @Override
      public Tuple<BigInteger, Long> map(Tuple<BigInteger, Long> input) {
        return input;
      }
    }).keyedGatherUngrouped().flatmap(new FlatMapFunc<Tuple<BigInteger, Long>, Tuple<BigInteger, Long>>() {
      @Override
      public void flatMap(Tuple<BigInteger, Long> input, RecordCollector<Tuple<BigInteger, Long>> collector) {
        collector.collect(input);
      }
    }).cache();

    // now lets read the partitioned file and find the membership
    SourceTSet<Tuple<BigInteger, Long>> inputRecords = batchEnv.createSource(new SourceFunc<Tuple<BigInteger, Long>>() {
      TwitterInputReader reader;
      @Override
      public void prepare(TSetContext context) {
        String prefix = context.getConfig().getStringValue(Context.ARG_FILE_PREFIX);
        reader = new TwitterInputReader(prefix + "/data/outfile-" + context.getIndex());
      }

      @Override
      public boolean hasNext() {
        try {
          return reader.hasNext();
        } catch (Exception e) {
          throw new RuntimeException("Failed to read", e);
        }
      }

      @Override
      public Tuple<BigInteger, Long> next() {
        try {
          return reader.next();
        } catch (Exception e) {
          throw new RuntimeException("Failed to read", e);
        }
      }
    }, parallel);

    SinkTSet<Iterator<String>> sink = inputRecords.direct().flatmap(new FlatMapFunc<Tuple<BigInteger, Long>, String>() {
      Map<String, Long> inputMap = new HashMap<>();

      TSetContext context;

      @Override
      public void prepare(TSetContext context) {
        this.context = context;
        DataPartition a = context.getInput("input");
        DataPartitionConsumer<Tuple<BigInteger, Long>> consumer = a.getConsumer();
        while (consumer.hasNext()) {
          Tuple<BigInteger, Long> bigIntegerLongTuple = consumer.next();
          inputMap.put(bigIntegerLongTuple.getKey().toString(), bigIntegerLongTuple.getValue());
        }
      }

      @Override
      public void flatMap(Tuple<BigInteger, Long> input, RecordCollector<String> collector) {
        if (inputMap.containsKey(input.getKey().toString())) {
          try {
            collector.collect(input.getKey().toString() + "," + input.getValue());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }).addInput("input", secondInput).direct().sink(new SinkFunc<Iterator<String>>() {
      TweetBufferedOutputWriter writer;

      @Override
      public void prepare(TSetContext context) {
        try {
          String prefix = context.getConfig().getStringValue(Context.ARG_FILE_PREFIX);
          writer = new TweetBufferedOutputWriter(prefix + "/data/final-" + context.getIndex(), config);
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public boolean add(Iterator<String> value) {
        while (value.hasNext()) {
          String input = value.next();
          try {
            writer.write(input);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
        return true;
      }
    });

    batchEnv.eval(sink);
    batchEnv.finishEval(sink);
    //batchEnv.close();
  }
}
