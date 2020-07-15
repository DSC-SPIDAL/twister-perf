package iu.iuni.deletion;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SinkFunc;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import edu.iu.dsc.tws.tset.sets.batch.SinkTSet;
import iu.iuni.deletion.io.TweetWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;

public class TweetIDPartitionJob implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(TweetIDPartitionJob.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String input = args[0];
    String output = args[1];
    int parallel = Integer.parseInt(args[2]);
    int memory = Integer.parseInt(args[3]);
    String date = args[4];
    int tupes = Integer.parseInt(args[5]);

    LOG.info(String.format("Parameters input %s, output %s, parallel %d, memory %d, date %s, tuples %d", input, output, parallel, memory, date, tupes));

    jobConfig.put(Context.ARG_TWEET_INPUT_DIRECTORY, input);
    jobConfig.put(Context.ARG_OUTPUT_DIRECTORY, output);
    jobConfig.put(Context.ARG_PARALLEL, parallel);
    jobConfig.put(Context.ARG_MEMORY, memory);
    jobConfig.put(Context.ARG_SEPARATOR, "\\s+");
    jobConfig.put(Context.ARG_DATE, date);
    jobConfig.put(Context.ARG_TUPLES, tupes);

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(TweetIDPartitionJob.class.getName())
        .setWorkerClass(TweetIDPartitionJob.class)
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
    // first we are going to read the files and sort them
    SinkTSet<Iterator<Tuple<BigInteger, String>>> fileSink = batchEnv.createKeyedSource(new TweeIDSource(),
        parallel).keyedGatherUngrouped(new HashingPartitioner<>()).useDisk().sink(new TweetWriteSink());
    batchEnv.eval(fileSink);
    batchEnv.finishEval(fileSink);
  }


  private static class TweetWriteSink implements SinkFunc<Iterator<Tuple<BigInteger, String>>> {
    TSetContext context;
    int i = 0;
    StringBuilder builder = new StringBuilder();
    String outDir;
    String date;

    @Override
    public void prepare(TSetContext context) {
      try {
        outDir = context.getConfig().getStringValue(Context.ARG_OUTPUT_DIRECTORY);
        FileSystem fs = FileSystemUtils.get(new Path(outDir).toUri(), context.getConfig());
        date = context.getConfig().getStringValue(Context.ARG_DATE);
        if (fs.exists(new Path(outDir + "/partitioned/" + context.getIndex()))) {
          throw new RuntimeException("Failed to write because directory exists");
        }
        this.context = context;
      } catch (IOException e) {
        throw new RuntimeException("Failed to write", e);
      }
    }

    @Override
    public boolean add(Iterator<Tuple<BigInteger, String>> value) {
      LOG.info("Starting to write: ");
      int count = 0;
      int tupes = context.getConfig().getIntegerValue(Context.ARG_TUPLES);
      TweetWriter writer;
      try {
        writer = new TweetWriter(outDir + "/partitioned/" + context.getIndex() + "/" + date + "-" + count, context.getConfig());

        while (value.hasNext()) {
          Tuple<BigInteger, String> next = value.next();
          builder.append(next.getKey().toString()).append("\t").append(next.getValue()).append("\n");
          if (i > 0 && i % 10 == 0) {
            writer.writeWithoutEnd(builder.toString());
            builder = new StringBuilder();
          }
          i++;
          if (i % tupes == 0) {
            writer.close();
            count++;
            writer = new TweetWriter(outDir + "/partitioned/" + context.getIndex() + "/" + date + "-" + count, context.getConfig());
          }
        }
        String s = builder.toString();
        if (!"".equals(s)){
          writer.writeWithoutEnd(builder.toString());
        }
        writer.close();
      } catch (FileNotFoundException e) {
        throw new RuntimeException("Failed", e);
      } catch (Exception e) {
        throw new RuntimeException("Failed to write", e);
      }
      return true;
    }
  }
}
