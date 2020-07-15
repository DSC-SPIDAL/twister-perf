package org.twister2.perf.shuffle.tws.bigint;

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
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Logger;

public class InMemoryBigIntShuffle implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(InMemoryBigIntShuffle.class.getName());
  @Override
  public void execute(WorkerEnvironment workerEnvironment) {
    long start = System.currentTimeMillis();
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnvironment);
    Config config = workerEnvironment.getConfig();
    int workerID = workerEnvironment.getWorkerId();

    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);
    // first we are going to read the files and sort them
    SinkTSet<Iterator<Tuple<BigInteger, Long>>> sink1 = batchEnv.createKeyedSource(new SourceFunc<Tuple<BigInteger, Long>>() {
      private int tuples;
      private int currentTupleNum;
      private BigInteger start;

      @Override
      public void prepare(TSetContext context) {
        tuples = context.getConfig().getIntegerValue(Context.ARG_TUPLES);
        start = new BigInteger("100000000000000000").multiply(new BigInteger("" + (workerID + 1)));
      }

      @Override
      public boolean hasNext() {
        return currentTupleNum++ < tuples;
      }

      @Override
      public Tuple<BigInteger, Long> next() {
        BigInteger i = start.add(new BigInteger("1"));
        start = i;
        return new Tuple<>(i, (long) workerID);
      }
    }, parallel).keyedGatherUngrouped(new HashingPartitioner<>(), new Comparator<BigInteger>() {
      @Override
      public int compare(BigInteger o1, BigInteger o2) {
        return o1.compareTo(o2);
      }
    }).useDisk().sink(new SinkFunc<Iterator<Tuple<BigInteger, Long>>>() {
      @Override
      public void prepare(TSetContext context) {
      }

      @Override
      public boolean add(Iterator<Tuple<BigInteger, Long>> value) {
        return true;
      }
    });
    batchEnv.eval(sink1);
    batchEnv.finishEval(sink1);
    //batchEnv.close();
    LOG.info("Total time: " + (System.currentTimeMillis() - start) / 1000 + " seconds");
  }
}
