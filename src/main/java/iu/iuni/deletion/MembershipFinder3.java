package iu.iuni.deletion;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.dataset.DataPartition;
import edu.iu.dsc.tws.api.dataset.DataPartitionConsumer;
import edu.iu.dsc.tws.api.resource.Twister2Worker;
import edu.iu.dsc.tws.api.resource.WorkerEnvironment;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SinkFunc;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import edu.iu.dsc.tws.tset.sets.batch.*;
import iu.iuni.deletion.io.TweetWriter;
import iu.iuni.deletion.sources.TweetIDSource;
import iu.iuni.deletion.sources.TweetIdDateSource;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;

public class MembershipFinder3 implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(MembershipFinder3.class.getName());

  private int workerID;

  @Override
  public void execute(WorkerEnvironment workerEnv) {
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnv);
    Config config = workerEnv.getConfig();
    workerID = workerEnv.getWorkerId();
    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);

    // now lets read the delete input file, partition and cache it
    PersistedTSet<BigInteger> persistedTDeleteIDs = batchEnv
        .createSource(new TweetIDSource(), parallel)
        .partition(new HashingPartitioner<>())
        .persist();

    LOG.info("........... Persisted delete keys ........");
    persistedTDeleteIDs.pipe().forEach(data -> LOG.info(data.toString()));

    // read tweet IDs, partition them and persist them
    KeyedPersistedTSet<BigInteger, String> persistedTweets =
        batchEnv.createKeyedSource(new TweetIdDateSource(), parallel)
            .keyedGatherUngrouped(new HashingPartitioner<>())
            .persist();

    LOG.info("........... Persisted tweets ........");
    persistedTweets.keyedPipe().forEach(data -> LOG.info(data.getKey() + ": " + data.getValue()));

    MembershipWriter membershipWriter = new MembershipWriter();
    SinkTSet<Tuple<BigInteger, String>> sink =
        persistedTweets.keyedPipe().sink(membershipWriter).addInput("delete-input", persistedTDeleteIDs);

    batchEnv.eval(sink);
    batchEnv.finishEval(sink);

    membershipWriter.close();
    LOG.info("........... Done. Exiting....");
  }

  private class MembershipWriter implements SinkFunc<Tuple<BigInteger, String>> {

    private TSetContext ctx;
    private String outputDir;
    private Set<BigInteger> deleteSet = new TreeSet<>();
    private Map<String, TweetWriter> writers = new HashMap<>();

    public MembershipWriter() {
    }

    @Override
    public void prepare(TSetContext context) {
      ctx = context;
      outputDir = ctx.getConfig().getStringValue(Context.ARG_OUTPUT_DIRECTORY);

      DataPartition a = context.getInput("delete-input");
      DataPartitionConsumer<BigInteger> consumer = a.getConsumer();
      while (consumer.hasNext()) {
        BigInteger deleteTweetID = consumer.next();
        deleteSet.add(deleteTweetID);
      }
      LOG.info("ADDED TUPLES TO DELETE SET: " + deleteSet.size());
    }

    private TweetWriter getWriter(String month) {
      if (writers.containsKey(month)) {
        return writers.get(month);
      } else {
        try {
          LOG.info("Writing file " + month);
          TweetWriter writer = new TweetWriter(outputDir + "/" + workerID + "-" + month, ctx.getConfig());
          writers.put(month, writer);
          return writer;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean add(Tuple<BigInteger, String> input) {

      if (!deleteSet.contains(input.getKey())) {
        // nothing to write
        return true;
      }

      try {
        TweetWriter writer = getWriter(input.getValue());
        writer.write(input.getKey().toString());
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void close() {
      for (TweetWriter w : writers.values()) {
        w.close();
      }
    }
  }

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String tweetInput = args[0];
    String deleteInput = args[1];
    String outputDir = args[2];
    int parallel = Integer.parseInt(args[3]);
    int memory = Integer.parseInt(args[4]);

    jobConfig.put(Context.ARG_TWEET_INPUT_DIRECTORY, tweetInput);
    jobConfig.put(Context.ARG_DELETE_INPUT_DIRECTORY, deleteInput);
    jobConfig.put(Context.ARG_OUTPUT_DIRECTORY, outputDir);
    jobConfig.put(Context.ARG_PARALLEL, parallel);

    Twister2Job twister2Job = Twister2Job.newBuilder()
        .setJobName("membership-finding")
        .setWorkerClass(MembershipFinder3.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

}
