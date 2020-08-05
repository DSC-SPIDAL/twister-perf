package iu.iuni.deletion.generator;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import iu.iuni.deletion.Context;
import iu.iuni.deletion.io.TweetWriter;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class TweetIDGenerator implements IWorker {
  private static final Logger LOG = Logger.getLogger(TweetIDGenerator.class.getName());

  @Override
  public void execute(Config config,
                      JobAPI.Job job,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {
    int workerID = workerController.getWorkerInfo().getWorkerID();
    int numberOfWorkers = workerController.getNumberOfWorkers();

    String outputDir = config.getStringValue(Context.ARG_OUTPUT_DIRECTORY);
    int recordsPerWorker = config.getIntegerValue(Context.ARG_TUPLES_PER_WORKER);
    try {
      TweetWriter outputWriter = new TweetWriter(outputDir + "/tweet/input-" + workerID, config);
      BigInteger start = new BigInteger("1000000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      // now write 1000,000
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < recordsPerWorker; i++) {
        BigInteger bi = start.add(new BigInteger(i + ""));
        if (i > 0 && i % 1000000 == 0) {
          LOG.info("Wrote elements: " + i);
        }
        builder.append(bi.toString())
            .append("\t")
            .append(between("2017-01-01", "2019-12-31"))
            .append("\n");

        if (i > 0 && (i % 10000 == 0 || i == recordsPerWorker - 1)) {
          outputWriter.writeWithoutEnd(builder.toString());
          // clear StringBuilder
          builder.setLength(0);
        }
      }
      outputWriter.close();

      TweetWriter outputWriter2 = new TweetWriter(outputDir + "/delete/input-" + workerID, config);
      BigInteger start2 = new BigInteger("1000000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      int numberOfDeleteIDsPerWorker = config.getIntegerValue(Context.ARG_NUMBER_OF_DELETE_IDS_PER_WORKER);

      // the ratio of delete IDs that matches deleteID-data pairs generated at above section
      double matchingRatio = 0.5;
      int step = (int) Math.round (recordsPerWorker / (matchingRatio * numberOfDeleteIDsPerWorker));

      int deleteID = 0;
      for (int i = 0; i < numberOfDeleteIDsPerWorker; i++) {
        BigInteger bi = start2.add(new BigInteger(deleteID + ""));
        outputWriter2.write(bi.toString());
        deleteID += step;
      }
      outputWriter2.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Date between(Date startInclusive, Date endExclusive) {
    long startMillis = startInclusive.getTime();
    long endMillis = endExclusive.getTime();
    long randomMillisSinceEpoch = ThreadLocalRandom
        .current()
        .nextLong(startMillis, endMillis);

    return new Date(randomMillisSinceEpoch);
  }

  public static String between(String start, String end) {
    SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat outF = new SimpleDateFormat("yyyy-MM");
    Date d = null;
    try {
      d = between(ft.parse(start), ft.parse(end));
      return outF.format(d);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {

    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    String outputDir = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    int tuplesPerWorker = Integer.parseInt(args[3]);
    int numberOfDeleteIDsPerWorker = Integer.parseInt(args[4]);

    JobConfig jobConfig = new JobConfig();

    jobConfig.put(Context.ARG_OUTPUT_DIRECTORY, outputDir);
    jobConfig.put(Context.ARG_TUPLES_PER_WORKER, tuplesPerWorker);
    jobConfig.put(Context.ARG_NUMBER_OF_DELETE_IDS_PER_WORKER, numberOfDeleteIDsPerWorker);

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName("TweetGen")
        .setWorkerClass(TweetIDGenerator.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }
}