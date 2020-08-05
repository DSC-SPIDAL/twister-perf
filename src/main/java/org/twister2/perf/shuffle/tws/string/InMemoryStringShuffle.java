package org.twister2.perf.shuffle.tws.string;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SinkFunc;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import edu.iu.dsc.tws.tset.sets.batch.SinkTSet;
import org.twister2.perf.shuffle.Context;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Logger;

public class InMemoryStringShuffle implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(StringShuffle.class.getName());

  @Override
  public void execute(WorkerEnvironment workerEnvironment) {
    long start = System.currentTimeMillis();
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnvironment);
    Config config = workerEnvironment.getConfig();
    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);
    // first we are going to read the files and sort them
    SinkTSet<Iterator<Tuple<String, String>>> sink1 = batchEnv.createKeyedSource(new SourceFunc<Tuple<String, String>>() {
      private int keySize;
      private int dataSize;
      private int tuples;
      private int currentTupleNum;

      @Override
      public void prepare(TSetContext context) {
        keySize = context.getConfig().getIntegerValue(Context.ARG_KEY_SIZE);
        dataSize = context.getConfig().getIntegerValue(Context.ARG_DATA_SIZE);
        tuples = context.getConfig().getIntegerValue(Context.ARG_TUPLES);
      }

      @Override
      public boolean hasNext() {
        return currentTupleNum++ < tuples;
      }

      @Override
      public Tuple<String, String> next() {
        return new Tuple<>(StringWriter.getAlphaNumericString(keySize), StringWriter.getAlphaNumericString(dataSize));
      }
    }, parallel).keyedGatherUngrouped(new HashingPartitioner<>(), new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.compareTo(o2);
      }
    }).useDisk().sink(new SinkFunc<Iterator<Tuple<String, String>>>() {
      @Override
      public void prepare(TSetContext context) {
      }

      @Override
      public boolean add(Iterator<Tuple<String, String>> value) {
        return true;
      }
    });
    batchEnv.eval(sink1);
    batchEnv.finishEval(sink1);
    //batchEnv.close();
    LOG.info("Total time: " + (System.currentTimeMillis() - start) / 1000 + " seconds");
  }
}
