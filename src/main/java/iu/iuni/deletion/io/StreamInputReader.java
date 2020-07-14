package iu.iuni.deletion.io;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StreamInputReader implements FileReader {
  private static final Logger LOG = Logger.getLogger(StreamInputReader.class.getName());

  private DataInputStream in;

  private int currentSize;

  private boolean end = false;

  private int count;

  public StreamInputReader(String fileName, Config config) {
    try {
      FileSystem fs = FileSystemUtils.get(new Path(fileName).toUri(), config);
      this.in = new DataInputStream(fs.open(new Path(fileName)));
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to create file system - " + fileName);
    }
  }

  public boolean reachedEnd() throws IOException {
    try {
      currentSize = in.readInt();
    } catch (EOFException e) {
      end = true;
      LOG.info("End reached - read tuples - " + count);
    }
    return end;
  }

  public Tuple<BigInteger, Long> nextRecord() {
    try {
      byte[] intBuffer = new byte[currentSize];
      int read = read(intBuffer, 0, currentSize);
      if (read != currentSize) {
        throw new RuntimeException("Invalid file: read" + count);
      }

      BigInteger tweetId = new BigInteger(intBuffer);
      long time = in.readLong();
      count++;
      return new Tuple<>(tweetId, time);
    } catch (EOFException e) {
      end = true;
      LOG.log(Level.SEVERE, "End reached - read tuples - " + count, e);
      throw new RuntimeException("We cannot reach end here", e);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Error", e);
    }
  }

  private int read(byte[] b, int off, int len) throws IOException {
    int totalRead = 0;
    for (int remainingLength = len, offset = off; remainingLength > 0;) {
      int read = this.in.read(b, offset, remainingLength);
      if (read < 0) {
        return read;
      }
      totalRead += read;
      offset += read;
      remainingLength -= read;
    }
    return totalRead;
  }
}
