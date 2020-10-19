package org.twister2.perf.join.spark.input;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;

public class LongOutputFormat implements OutputFormat<Long, Long> {

  @Override
  public org.apache.hadoop.mapred.RecordWriter<Long, Long> getRecordWriter(
      FileSystem fileSystem, JobConf jobConf, String s,
      Progressable progressable) throws IOException {
    return new EmptyRecordWriter();
  }

  @Override
  public void checkOutputSpecs(FileSystem fileSystem, JobConf jobConf) throws IOException {
  }
}
