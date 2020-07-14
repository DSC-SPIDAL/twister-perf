package iu.iuni.deletion.io;

import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseStreamInputReader<K> implements FileReader {
  private static final Logger LOG = Logger.getLogger(StreamInputReader.class.getName());

  private BufferedReader in;

  protected String currentSize;

  private boolean end = false;

  private int count;

  private Path fileName;

  public BaseStreamInputReader(Path fileName, Config config) {
    try {
      this.fileName = fileName;
      FileSystem fs = FileSystemUtils.get(fileName.toUri(), config);
      this.in = new BufferedReader(new InputStreamReader(fs.open(fileName)));
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to create file system - " + fileName);
    }
  }

  public BaseStreamInputReader(String fileName, Config config) {
    try {
      this.fileName = new Path(fileName);
      FileSystem fs = FileSystemUtils.get(new Path(fileName).toUri(), config);
      this.in = new BufferedReader(new InputStreamReader(fs.open(new Path(fileName))));
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to create file system - " + fileName);
    }
  }

  public boolean reachedEnd() throws IOException {
    try {
      currentSize = in.readLine();
      if (currentSize == null) {
        end = true;
      }
      count++;
    } catch (EOFException e) {
      end = true;
      LOG.info("End reached - read tuples - " + count);
    }
    return end;
  }

  public Path getFileName() {
    return fileName;
  }
}
