package iu.iuni.deletion.generator;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import iu.iuni.deletion.Context;
import iu.iuni.deletion.io.TweetWriter;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class TweetIDGenerator implements IWorker {
  private static final Logger LOG = Logger.getLogger(TweetIDGenerator.class.getName());

  @Override
  public void execute(Config config, int workerID,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {
    String prefix = config.getStringValue(Context.ARG_OUTPUT_DIRECTORY);
    boolean csv = true;
    int records = config.getIntegerValue(Context.ARG_TUPLES);
    try {
      String data = csv ? "csv" : "";
      TweetWriter outputWriter = new TweetWriter(prefix + "/tweet/input-" + workerID, config);
      BigInteger start = new BigInteger("1000000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      // now write 1000,000
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < records; i++) {
        BigInteger bi = start.add(new BigInteger(i + ""));
        if (i % 1000000 == 0) {
          LOG.info("Wrote elements: " + i);
        }
        builder.append(bi.toString()).append("\t").append(between("2017-01-01", "2019-12-31")).append("\n");
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

      TweetWriter outputWriter2 = new TweetWriter(prefix + "/delete/input-" + workerID, config);
      BigInteger start2 = new BigInteger("1000000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      // now write 1000,000
      for (int i = 0; i < 1000000; i++) {
        BigInteger bi = start2.add(new BigInteger(i + ""));
        if (csv) {
          outputWriter2.write(bi.toString());
        } else {
          outputWriter2.write(bi, (long) workerID);
        }
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
    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    int tuples = Integer.parseInt(args[3]);

    JobConfig jobConfig = new JobConfig();

    jobConfig.put(Context.ARG_OUTPUT_DIRECTORY, filePrefix);
    jobConfig.put(Context.ARG_TUPLES, tuples);

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