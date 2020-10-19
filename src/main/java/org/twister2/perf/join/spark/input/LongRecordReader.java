package org.twister2.perf.join.spark.input;

import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

public class LongRecordReader extends RecordReader<Long, Long> {
  private static final Logger LOG = Logger.getLogger(LongRecordReader.class.getName());

  private int numRecords = 625000;
  private int currentRead = 0;
  private Random random;

  public LongRecordReader() {
    random = new Random(System.nanoTime());
  }

  @Override
  public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext)
      throws IOException, InterruptedException {
    numRecords = taskAttemptContext.getConfiguration().getInt("records", 1000);
    LOG.info("Num records: " + numRecords);
  }

  @Override
  public boolean nextKeyValue() throws IOException, InterruptedException {
    return currentRead++ < numRecords;
  }

  @Override
  public Long getCurrentKey() throws IOException, InterruptedException {
    return random.nextLong();
  }

  @Override
  public Long getCurrentValue() throws IOException, InterruptedException {
    return random.nextLong();
  }

  @Override
  public float getProgress() throws IOException, InterruptedException {
    return (currentRead + 0.0f) / numRecords;
  }

  @Override
  public void close() throws IOException {
  }
}
