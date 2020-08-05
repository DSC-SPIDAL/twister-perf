package org.twister2.perf.shuffle.tws.string;

import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import org.twister2.perf.io.TweetBufferedOutputWriter;
import org.twister2.perf.shuffle.Context;

import java.util.logging.Logger;

public class StringWriter implements IWorker {
  private static final Logger LOG = Logger.getLogger(StringWriter.class.getName());

  @Override
  public void execute(Config config,
                      JobAPI.Job job,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {
    int workerID = workerController.getWorkerInfo().getWorkerID();
    String prefix = config.getStringValue(Context.ARG_FILE_PREFIX);
    int records = config.getIntegerValue(Context.ARG_TUPLES);
    int keySize = config.getIntegerValue(Context.ARG_KEY_SIZE);
    int valueSize = config.getIntegerValue(Context.ARG_DATA_SIZE);

    try {
      TweetBufferedOutputWriter outputWriter = new TweetBufferedOutputWriter(prefix + "/in/input-" + workerID, config);
      // now write 1000,000
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < records; i++) {
        if (i % 1000000 == 0) {
          LOG.info("Wrote elements: " + i);
        }
        String key = getAlphaNumericString(keySize) + "-" + workerID;
        String value = getAlphaNumericString(valueSize);
        builder.append(key).append(",").append(value).append("\n");
        if (i > 0 && (i % 10000 == 0 || i == records - 1)) {
          outputWriter.writeWithoutEnd(builder.toString());
          builder = new StringBuilder();
        }
      }
      outputWriter.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // function to generate a random string of length n
  public static String getAlphaNumericString(int n) {
    // chose a Character random from this String
    String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        + "0123456789"
        + "abcdefghijklmnopqrstuvxyz";

    // create StringBuffer size of AlphaNumericString
    StringBuilder sb = new StringBuilder(n);

    for (int i = 0; i < n; i++) {
      // generate a random number between
      // 0 to AlphaNumericString variable length
      int index
          = (int) (AlphaNumericString.length()
          * Math.random());
      // add Character one by one in end of sb
      sb.append(AlphaNumericString
          .charAt(index));
    }
    return sb.toString();
  }

  public static void main(String[] args) {
    String gen = getAlphaNumericString(10);
    System.out.println(gen);
  }
}
