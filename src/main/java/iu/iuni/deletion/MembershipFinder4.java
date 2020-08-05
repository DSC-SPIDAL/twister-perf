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
import edu.iu.dsc.tws.api.tset.fn.*;
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

/**
 * each worker reads two source files:
 * one with (tweetID - date) pairs, the other is with delete tweet IDs
 * both Tsets are partitioned with hash partitioner
 * each worker locally calculates matching tweetsIDs
 * matched tweetID-date pairs are key gathered
 * each worker writes gathered tweetIds to month files in HDFS
 */

public class MembershipFinder4 implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(MembershipFinder4.class.getName());

  @Override
  public void execute(WorkerEnvironment workerEnv) {
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnv);
    Config config = workerEnv.getConfig();
    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);

    // read the delete input files, partition and cache them
    PersistedTSet<BigInteger> persistedDeleteIDs = batchEnv
        .createSource(new TweetIDSource(), parallel)
        .partition(new HashingPartitioner<>())
        .persist();

    // write to log to check things
    LOG.info("........... persisted delete keys ........");
    // cachedDeleteIDs.pipe().forEach(data -> LOG.info(data.toString()));

    // read tweet ID-date files, partition them and persist them
    KeyedPersistedTSet<BigInteger, String> persistedTweets =
        batchEnv.createKeyedSource(new TweetIdDateSource(), parallel)
            .keyedGatherUngrouped(new HashingPartitioner<>())
            .persist();

    LOG.info("........... Persisted tweetID-date pairs ........");
    // persistedTweets.keyedPipe().forEach(data -> LOG.info(data.getKey() + ": " + data.getValue()));

    // calculate matched tweetID-date pairs
    CachedTSet<Tuple<String, BigInteger>> cachedMatchedTweets =
        persistedTweets
            .keyedPipe()
            .compute(new ComputeMatchingTweets())
            .addInput("delete-input", persistedDeleteIDs)
            .pipe()
            .cache();
    // how can we make cachedMatchedTweets key-value pairs? do we have to use mapToTuple as below?

    LOG.info("........... Matched tweets: gathering ........");
    SinkTSet<Iterator<Tuple<String, Iterator<BigInteger>>>> sinkTSet =
        cachedMatchedTweets
            .mapToTuple(input -> new Tuple<>(input.getKey(), input.getValue()))
            .keyedGather()
            .sink(new MembershipWriter());

    batchEnv.eval(sinkTSet);
    batchEnv.finishEval(sinkTSet);
    LOG.info("........... Done. Exiting....");
  }

  private class ComputeMatchingTweets extends BaseComputeCollectorFunc<Tuple<String, BigInteger>, Tuple<BigInteger, String>> {

    private TSetContext ctx;
    private Set<BigInteger> deleteSet = new TreeSet<>();

    public ComputeMatchingTweets() {
    }

    @Override
    public void prepare(TSetContext context) {
      ctx = context;

      DataPartition a = context.getInput("delete-input");
      DataPartitionConsumer<BigInteger> consumer = a.getConsumer();
      while (consumer.hasNext()) {
        BigInteger deleteTweetID = consumer.next();
        deleteSet.add(deleteTweetID);
      }
      LOG.info("ADDED TUPLES TO DELETE SET: " + deleteSet.size());
    }

    @Override
    public void compute(Tuple<BigInteger, String> input, RecordCollector<Tuple<String, BigInteger>> output) {

      if (deleteSet.contains(input.getKey())) {
        output.collect(new Tuple<>(input.getValue(), input.getKey()));
      }
    }
  }

  private class MembershipWriter implements SinkFunc<Iterator<Tuple<String, Iterator<BigInteger>>>> {

    private TSetContext ctx;
    private String outputDir;

    public MembershipWriter() {
    }

    @Override
    public void prepare(TSetContext context) {
      ctx = context;
      outputDir = ctx.getConfig().getStringValue(Context.ARG_OUTPUT_DIRECTORY);
    }

    private TweetWriter getWriter(String month) {
      try {
        LOG.info("Writing the file: " + month);
        return new TweetWriter(outputDir + "/" + month, ctx.getConfig());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean add(Iterator<Tuple<String, Iterator<BigInteger>>> dataIter) {

      while (dataIter.hasNext()) {
        Tuple<String, Iterator<BigInteger>> data = dataIter.next();
        TweetWriter writer = getWriter(data.getKey());
        Iterator<BigInteger> idIter = data.getValue();
        while (idIter.hasNext()) {
          try {
            writer.write(idIter.next().toString());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

        writer.close();
      }
      return true;
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
        .setWorkerClass(MembershipFinder4.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

}
