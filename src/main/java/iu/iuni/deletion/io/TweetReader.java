package iu.iuni.deletion.io;

import edu.iu.dsc.tws.api.comms.structs.Tuple;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class TweetReader {
  private static final Logger LOG = Logger.getLogger(TweetReader.class.getName());
  /**
   * Name of the file to read
   */
  private String fileName;

  /**
   * The input buffer
   */
  private ByteBuffer inputBuffer;

  /**
   * Total bytes read so far
   */
  private long totalRead = 0;

  /**
   * The read channel
   */
  private FileChannel rwChannel;

  public TweetReader(String file) {
    this.fileName = file;

    String outFileName = Paths.get(fileName).toString();
    try {
      rwChannel = new RandomAccessFile(outFileName, "rw").getChannel();
      inputBuffer = rwChannel.map(FileChannel.MapMode.READ_ONLY, 0, rwChannel.size());
      LOG.info("Reading file: " + fileName + " size: " + rwChannel.size());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Tuple<BigInteger, Long> next() throws Exception {
    try {
      if (totalRead < rwChannel.size()) {
        // read the size of the big int
        int size = inputBuffer.getInt();
        byte[] intBuffer = new byte[size];

        inputBuffer.get(intBuffer);

        BigInteger tweetId = new BigInteger(intBuffer);
        long time = inputBuffer.getLong();
        totalRead += size + Integer.BYTES + Long.BYTES;
        return new Tuple<>(tweetId, time);
      }
    } catch (IOException e) {
      throw new Exception(e);
    }
    return null;
  }

  public boolean hasNext() throws Exception {
    try {
      return totalRead < rwChannel.size();
    } catch (IOException e) {
      throw new Exception("Failed to read file", e);
    }
  }

  public void close() throws Exception {
    rwChannel.force(true);
    rwChannel.close();
  }
}
