package org.twister2.perf.join.spark.input;

import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;

import java.io.IOException;

public class EmptyRecordWriter implements RecordWriter<Long, Long> {
  @Override
  public void write(Long bytes, Long bytes2) throws IOException {
  }

  @Override
  public void close(Reporter reporter) throws IOException {

  }

}
