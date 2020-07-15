package org.twister2.perf.shuffle.tws.bigint;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import org.twister2.perf.io.TweetBufferedOutputWriter;
import org.twister2.perf.shuffle.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.logging.Logger;

public class WritingJob implements IWorker {
  private static final Logger LOG = Logger.getLogger(WritingJob.class.getName());

  @Override
  public void execute(Config config,
                      JobAPI.Job job,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {
    int workerID = workerController.getWorkerInfo().getWorkerID();
    String prefix = config.getStringValue(Context.ARG_FILE_PREFIX);
    boolean csv = config.getBooleanValue(Context.ARG_CSV);
    int records = config.getIntegerValue(Context.ARG_TUPLES);
    try {
      String data = csv ? "csv" : "";
      TweetBufferedOutputWriter outputWriter = new TweetBufferedOutputWriter(prefix + "/" + data + "Data/input-" + workerID, config);
      BigInteger start = new BigInteger("100000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      // now write 1000,000
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < records; i++) {
        BigInteger bi = start.add(new BigInteger(i + ""));
        if (i % 1000000 == 0) {
          LOG.info("Wrote elements: " + i);
        }
        builder.append(bi.toString()).append(",").append(workerID).append("\n");
        if (i > 0 && (i % 10000 == 0 || i == records - 1)) {
          if (csv) {
            outputWriter.writeWithoutEnd(builder.toString());
            builder = new StringBuilder();
          } else {
            outputWriter.write(bi, (long) workerID);
          }
        }
      }
      outputWriter.close();

      TweetBufferedOutputWriter outputWriter2 = new TweetBufferedOutputWriter(prefix + "/" + data + "Data2/second-input-" + workerID, config);
      BigInteger start2 = new BigInteger("100000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      // now write 1000,000
      for (int i = 0; i < 1000000; i++) {
        BigInteger bi = start2.add(new BigInteger(i + ""));
        if (csv) {
          outputWriter2.write(bi.toString() + "," + workerID);
        } else {
          outputWriter2.write(bi, (long) workerID);
        }
      }
      outputWriter2.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    boolean csv = Boolean.parseBoolean(args[3]);
    int tuples = Integer.parseInt(args[4]);

    JobConfig jobConfig = new JobConfig();

    jobConfig.put(Context.ARG_FILE_PREFIX, filePrefix);
    jobConfig.put(Context.ARG_CSV, csv);
    jobConfig.put(Context.ARG_TUPLES, tuples);

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(WritingJob.class.getName())
        .setWorkerClass(WritingJob.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }
}
