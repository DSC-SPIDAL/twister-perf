package iu.iuni.deletion;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.data.FileStatus;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import iu.iuni.deletion.io.TweetTextReader;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class TweeIDSource implements SourceFunc<Tuple<BigInteger, String>> {
  private static final Logger LOG = Logger.getLogger(TweeIDSource.class.getName());

  private Queue<TweetTextReader> readers;
  private TSetContext ctx;
  private String inputDir;
  private TweetTextReader currentReader;

  @Override
  public void prepare(TSetContext context) {
    this.ctx = context;
    inputDir = context.getConfig().getStringValue(Context.ARG_TWEET_INPUT_DIRECTORY);
    String separator = context.getConfig().getStringValue(Context.ARG_SEPARATOR, ",");
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
        readers.offer(new TweetTextReader(fileName, context.getConfig(), separator));
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
          LOG.info("Done reading from file - " + inputDir + "/part-" + ctx.getIndex());
          currentReader = readers.poll();
        } else {
          return true;
        }
      } while (currentReader != null);

      return false;
    } catch (Exception e) {
      throw new RuntimeException("Failed to read", e);
    }
  }

  @Override
  public Tuple<BigInteger, String> next() {
    try {
      return currentReader.nextRecord();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read next", e);
    }
  }
}
