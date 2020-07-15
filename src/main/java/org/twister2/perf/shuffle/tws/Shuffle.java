package org.twister2.perf.shuffle.tws;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.Twister2Worker;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import org.twister2.perf.shuffle.tws.bigint.InMemoryBigIntShuffle;
import org.twister2.perf.shuffle.tws.bigint.InputPartitionJob;
import org.twister2.perf.shuffle.tws.bigint.MembershipJob;
import org.twister2.perf.shuffle.tws.string.InMemoryStringShuffle;
import org.twister2.perf.shuffle.tws.string.StringShuffle;
import org.twister2.perf.shuffle.Context;

import java.util.HashMap;
import java.util.logging.Logger;

public class Shuffle {
  private static final Logger LOG = Logger.getLogger(Shuffle.class.getName());

  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    boolean csv = Boolean.parseBoolean(args[3]);
    boolean write = Boolean.parseBoolean(args[4]);
    String writer = args[5];

    LOG.info(String.format("Parameters file %s, parallel %d, memory %d, csv %b, write %b shuffle %s", filePrefix, parallel, memory, csv, write, writer));

    jobConfig.put(Context.ARG_FILE_PREFIX, filePrefix);
    jobConfig.put(Context.ARG_PARALLEL, parallel);
    jobConfig.put(Context.ARG_MEMORY, memory);
    jobConfig.put(Context.ARG_CSV, csv);
    jobConfig.put(Context.ARG_WRITE, write);

    Class<? extends Twister2Worker> c;
    if (writer.equals("string")) {
      c = StringShuffle.class;
    } else if (writer.equals("bigint")) {
      c = InputPartitionJob.class;
    } else if (writer.equals("stringmemory")) {
      int records = Integer.parseInt(args[6]);
      int keySize = Integer.parseInt(args[7]);
      int dataSize = Integer.parseInt(args[8]);

      jobConfig.put(Context.ARG_TUPLES, records);
      jobConfig.put(Context.ARG_KEY_SIZE, keySize);
      jobConfig.put(Context.ARG_DATA_SIZE, dataSize);

      c = InMemoryStringShuffle.class;
    } else if (writer.equals("bigintmemory")) {
      int records = Integer.parseInt(args[6]);
      int keySize = Integer.parseInt(args[7]);
      int dataSize = Integer.parseInt(args[8]);

      jobConfig.put(Context.ARG_TUPLES, records);
      jobConfig.put(Context.ARG_KEY_SIZE, keySize);
      jobConfig.put(Context.ARG_DATA_SIZE, dataSize);

      c = InMemoryBigIntShuffle.class;
    } else {
      throw new RuntimeException("Failed to recognize option: " + writer);
    }

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(MembershipJob.class.getName())
        .setWorkerClass(c)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
    LOG.info("Total time: " + (System.currentTimeMillis() - start) / 1000 + " seconds");
  }
}
