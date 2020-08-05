package org.twister2.perf.shuffle.tws.bigint;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.comms.messaging.types.MessageTypes;
import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.exceptions.TimeoutException;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.util.KryoSerializer;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import org.twister2.perf.io.FileReader;
import org.twister2.perf.io.StreamInputReader;
import org.twister2.perf.shuffle.Context;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndependentJob implements IWorker, Serializable {
  private static final Logger LOG = Logger.getLogger(IndependentJob.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    boolean csv = Boolean.parseBoolean(args[3]);
    boolean write = Boolean.parseBoolean(args[4]);

    LOG.info(String.format("Parameters file %s, parallel %d, memory %d, csv %b, write %b", filePrefix, parallel, memory, csv, write));

    jobConfig.put(Context.ARG_FILE_PREFIX, filePrefix);
    jobConfig.put(Context.ARG_PARALLEL, parallel);
    jobConfig.put(Context.ARG_MEMORY, memory);
    jobConfig.put(Context.ARG_CSV, csv);
    jobConfig.put(Context.ARG_WRITE, write);

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(MembershipJob.class.getName())
        .setWorkerClass(IndependentJob.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

  @Override
  public void execute(Config config, JobAPI.Job job, IWorkerController workerController,
                      IPersistentVolume persistentVolume, IVolatileVolume volatileVolume) {
    int workerID = workerController.getWorkerInfo().getWorkerID();
    FileReader reader;
    String prefix = config.getStringValue(Context.ARG_FILE_PREFIX);
    boolean csv = config.getBooleanValue(Context.ARG_CSV);
    KryoSerializer serializer = new KryoSerializer();
    if (!csv) {
      reader = new StreamInputReader(prefix + "/data/input-" + workerID, config);
    } else {
      reader = new BigIntReader(prefix + "/csvData/input-" + workerID, config);
    }
    int size = 0;
    try {
      size = workerController.getAllWorkers().size();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
    List<Integer> i = new ArrayList<>();
    for (int w = 0; w < size; w++) {
      i.add(w);
    }
    Set<Integer> s = new HashSet<>(i);
    Set<Integer> t = new HashSet<>(i);
    HashingPartitioner<BigInteger> hb = new HashingPartitioner<>();
    hb.prepare(s, t);
    try {
      while (!reader.reachedEnd()) {
        Tuple<BigInteger, Long> big = reader.nextRecord();
        MessageTypes.OBJECT.getDataPacker().packToByteArray(big.getKey());
        MessageTypes.LONG.getDataPacker().packToByteArray(big.getValue());
        hb.partition(workerID, big.getKey());
      }
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "FAiled", e);
    }
  }
}
