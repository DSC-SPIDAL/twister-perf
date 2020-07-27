package iu.iuni.deletion.sources;

import edu.iu.dsc.tws.api.data.FileStatus;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import iu.iuni.deletion.Context;
import iu.iuni.deletion.MembershipFinder2;
import iu.iuni.deletion.io.TweetIdReader;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * read tweet IDs from a directory
 * input files have a list of tweet IDs
 * each tweetID is a BigInteger
 */
public class TweetIDSource implements SourceFunc<BigInteger> {
  private static final Logger LOG = Logger.getLogger(TweetIDSource.class.getName());

  private String inputFile;
  private TweetIdReader currentReader;

  @Override
  public void prepare(TSetContext context) {
    String inputDir = context.getConfig().getStringValue(Context.ARG_DELETE_INPUT_DIRECTORY);
    try {
      FileSystem fs = FileSystemUtils.get(new Path(inputDir).toUri(), context.getConfig());
      FileStatus[] fileStatuses = fs.listFiles(new Path(inputDir));
      for (FileStatus s : fileStatuses) {
        if (s.getPath().getName().endsWith("-" + context.getWorkerId())) {
          inputFile = inputDir + "/" + s.getPath().getName();
          currentReader = new TweetIdReader(inputFile, context.getConfig(), ",");
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
        LOG.info("Done reading from the input file: " + inputFile);
        return false;
      } else {
        return true;
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to read", e);
    }
  }

  @Override
  public BigInteger next() {
    try {
      return currentReader.nextRecord();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read next", e);
    }
  }
}

