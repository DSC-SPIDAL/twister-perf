package iu.iuni.deletion.sources;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.data.FileStatus;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import iu.iuni.deletion.Context;
import iu.iuni.deletion.io.TweetIdDateReader;

import java.io.IOException;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TweetIdDateSource implements SourceFunc<Tuple<BigInteger, String>> {
  private static final Logger LOG = Logger.getLogger(TweetIdDateSource.class.getName());

  private String inputFile;
  private TweetIdDateReader currentReader;
  private long count = 0;
  private long MAX_TUPLE_TO_READ = 50000;

  @Override
  public void prepare(TSetContext context) {
    final String inputDir = context.getConfig().getStringValue(Context.ARG_TWEET_INPUT_DIRECTORY);
    String separator = context.getConfig().getStringValue(Context.ARG_SEPARATOR, "\\s+");
    try {
      FileSystem fs = FileSystemUtils.get(new Path(inputDir).toUri(), context.getConfig());
      FileStatus[] fileStatuses = fs.listFiles(new Path(inputDir));

      for (FileStatus s : fileStatuses) {
        if (s.getPath().getName().endsWith("-" + context.getWorkerId())) {
          inputFile = inputDir + "/" + s.getPath().getName();
          currentReader = new TweetIdDateReader(inputFile, context.getConfig(), separator);
          LOG.info("Starting to read: " + inputFile);
          break;
        }
      }

      if (currentReader == null) {
        throw new RuntimeException("There is no input file ending with workerID: -" + context.getWorkerId());
      }

    } catch (IOException e) {
      LOG.log(Level.INFO, "There is an exception", e);
      throw new RuntimeException("There is an exception", e);
    }
  }

  @Override
  public boolean hasNext() {
    try {
      if (currentReader.reachedEnd()) {
        LOG.info("Has read " + (count / 1000) + "K tuples. Finished reading the input file: " + inputFile);
        return false;
      } else if (count >= MAX_TUPLE_TO_READ) {
        LOG.info("Has read " + MAX_TUPLE_TO_READ + " tuples. Done reading the input file: " + inputFile);
        currentReader.closeReader();
        return false;
      } else {
        return true;
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to read", e);
    }
  }

  @Override
  public Tuple<BigInteger, String> next() {
    try {
      count++;
//      if (count > 0 && count % 10000000 == 0) {
//        LOG.info("has read: " + (count / 1000000) + "M tweetID-date pairs.");
//      }

      return currentReader.nextRecord();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read next", e);
    }
  }
}
