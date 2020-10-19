package org.twister2.perf.join.spark.input;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongInputSplit extends InputSplit implements Writable {
  private int elements = 100000;

  private String node;

  public LongInputSplit(int elements) {
    this.elements = elements;
  }

  public LongInputSplit() {
  }

  public int getElements() {
    return elements;
  }

  public void setElements(int elements) {
    this.elements = elements;
  }

  @Override
  public long getLength() throws IOException, InterruptedException {
    return elements * 16;
  }

  @Override
  public String[] getLocations() throws IOException, InterruptedException {
    String[] ret = new String[1];
    ret[0] = node;
    return ret;
  }

  public String getNode() {
    return node;
  }

  public void setNode(String node) {
    this.node = node;
  }

  @Override
  public void write(DataOutput dataOutput) throws IOException {

  }

  @Override
  public void readFields(DataInput dataInput) throws IOException {

  }
}
