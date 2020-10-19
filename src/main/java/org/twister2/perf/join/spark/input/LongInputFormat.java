package org.twister2.perf.join.spark.input;

import org.apache.hadoop.mapreduce.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LongInputFormat extends InputFormat<Long, Long> {
  private static final Logger LOG = Logger.getLogger(LongInputFormat.class.getName());
  private int parallel = 4;

  @Override
  public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
    List<InputSplit> splits = new ArrayList<>();
    int records = jobContext.getConfiguration().getInt("records", 1000);
    int parallel = jobContext.getConfiguration().getInt("parallel", 4);
    LOG.info("Parallel - " + parallel);
    LOG.info("Records - " + records);

    for (int i = 0; i < parallel; i++) {
      LongInputSplit e = new LongInputSplit(records);

      String node = "v-0";
      int index = i % 16;
      if (index >= 10) {
        node += index;
      } else {
        node += "0" + index;
      }
      e.setNode(node);
      splits.add(e);
    }
    return splits;
  }

  @Override
  public RecordReader<Long, Long> createRecordReader(InputSplit inputSplit,
                                                         TaskAttemptContext taskAttemptContext)
      throws IOException, InterruptedException {
    return new LongRecordReader();
  }
}