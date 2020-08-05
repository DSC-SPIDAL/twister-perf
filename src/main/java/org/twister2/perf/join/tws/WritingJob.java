package org.twister2.perf.join.tws;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FSDataOutputStream;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.data.Path;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.data.utils.FileSystemUtils;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;
import org.twister2.perf.shuffle.Context;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WritingJob implements IWorker {
  private static final Logger LOG = Logger.getLogger(WritingJob.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    String filePrefix = args[0];
    int parallel = Integer.parseInt(args[1]);
    int memory = Integer.parseInt(args[2]);
    long tuples = Long.parseLong(args[3]) / parallel;

    JobConfig jobConfig = new JobConfig();

    jobConfig.put(Context.ARG_FILE_PREFIX, filePrefix);
    jobConfig.put(Context.ARG_TUPLES, tuples);
    jobConfig.put("WORKERS", Long.parseLong(args[3]));

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(WritingJob.class.getName())
        .setWorkerClass(WritingJob.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

  @Override
  public void execute(Config config,
                      JobAPI.Job job,
                      IWorkerController workerController,
                      IPersistentVolume persistentVolume,
                      IVolatileVolume volatileVolume) {
    int workerID = workerController.getWorkerInfo().getWorkerID();
    long recordsPerRelation = config.getLongValue(Context.ARG_TUPLES, 1000);
    String fileName = config.getStringValue(Context.ARG_FILE_PREFIX);
    long workers = config.getLongValue("WORKERS", 80);
    int randomRange = recordsPerRelation * workers > Integer.MAX_VALUE ?
        Integer.MAX_VALUE : (int) (recordsPerRelation * workers);
    System.out.println("Generating " + recordsPerRelation + " tuples per node...");
    Random random = new Random(System.nanoTime());
    try {
      FileSystem fs = FileSystemUtils.get(new Path(fileName).toUri(), config);
      FSDataOutputStream out1 = fs.create(new Path(fileName + "/in1/" + workers + "/" + workerID));
      FSDataOutputStream out2 = fs.create(new Path(fileName + "/in2/" + workers + "/" + workerID));

      BufferedWriter br1 = new BufferedWriter(new OutputStreamWriter(out1));
      BufferedWriter br2 = new BufferedWriter(new OutputStreamWriter(out2));
      for (int i = 0; i < recordsPerRelation; i++) {
        br1.write(String.format("%d\t%d", random.nextInt(randomRange), random.nextLong()));
        br1.newLine();
        br2.write(String.format("%d\t%d", random.nextInt(randomRange), random.nextLong()));
        br2.newLine();
      }
      br1.close();
      br2.close();
    } catch (Exception ex) {
      LOG.log(Level.INFO, "Error in generating " + workerID, ex);
    }
  }
}
