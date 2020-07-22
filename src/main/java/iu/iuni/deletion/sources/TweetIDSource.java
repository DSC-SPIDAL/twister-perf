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

  private Queue<TweetIdReader> readers;
  private String inputDir;
  private TweetIdReader currentReader;
  private int count = 0;

  @Override
  public void prepare(TSetContext context) {
    inputDir = context.getConfig().getStringValue(Context.ARG_DELETE_INPUT_DIRECTORY);
    readers = new LinkedList<>();
    try {
      List<String> inputFiles = new ArrayList<>();
      FileSystem fs = FileSystemUtils.get(new Path(inputDir).toUri(), context.getConfig());
      FileStatus[] fileStatuses = fs.listFiles(new Path(inputDir));
      for (FileStatus s : fileStatuses) {
        inputFiles.add(s.getPath().getName());
      }
      inputFiles.sort(new Comparator<String>() {
        @Override
        public int compare(String s, String t1) {
          return s.compareTo(t1);
        }
      });

      int i = context.getIndex();
      StringBuilder files = new StringBuilder();
      while (i < context.getParallelism()) {
        final String fileName = inputDir + "/" + inputFiles.get(i);
        readers.offer(new TweetIdReader(fileName, context.getConfig(), ","));
        files.append(fileName).append(" ");
        i += context.getParallelism();
      }
      LOG.log(Level.INFO, String.format("input file list %s", files.toString()));
    } catch (IOException e) {
      LOG.log(Level.INFO, "There is an exception", e);
      throw new RuntimeException("There is an exception", e);
    }
    // lets get all the files, sort them and assign accordingly
    currentReader = readers.poll();
  }

  @Override
  public boolean hasNext() {
    try {
      do {
        boolean b = currentReader.reachedEnd();
        if (b) {
          LOG.info("Done reading from file - " + currentReader.getFileName().getPath());
          currentReader = readers.poll();
        } else {
          return true;
        }
      } while (currentReader != null);

      LOG.info("READ DELETE TUPLES " + count);
      return false;
    } catch (Exception e) {
      throw new RuntimeException("Failed to read", e);
    }
  }

  @Override
  public BigInteger next() {
    try {
      count++;
      return currentReader.nextRecord();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read next", e);
    }
  }
}
