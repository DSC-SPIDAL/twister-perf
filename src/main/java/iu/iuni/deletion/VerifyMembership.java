package iu.iuni.deletion;

import edu.iu.dsc.tws.api.JobConfig;
import edu.iu.dsc.tws.api.Twister2Job;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import edu.iu.dsc.tws.rsched.core.ResourceAllocator;
import edu.iu.dsc.tws.rsched.job.Twister2Submitter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Logger;

public class VerifyMembership implements IWorker, Serializable {
  private static final Logger LOG = Logger.getLogger(VerifyMembership.class.getName());

  public static void main(String[] args) {
    Config config = ResourceAllocator.loadConfig(new HashMap<>());
    JobConfig jobConfig = new JobConfig();

    String input = args[0];
    String output = args[1];
    int parallel = Integer.parseInt(args[2]);
    int memory = Integer.parseInt(args[3]);

    LOG.info(String.format("Parameters input %s, output %s, parallel %d, memory %d", input, output, parallel, memory));

    jobConfig.put(Context.ARG_TWEET_INPUT_DIRECTORY, input);
    jobConfig.put(Context.ARG_DELETE_INPUT_DIRECTORY, output);
    jobConfig.put(Context.ARG_PARALLEL, parallel);
    jobConfig.put(Context.ARG_MEMORY, memory);
    jobConfig.put(Context.ARG_SEPARATOR, "\\s+");

    Twister2Job twister2Job;
    twister2Job = Twister2Job.newBuilder()
        .setJobName(VerifyMembership.class.getName())
        .setWorkerClass(VerifyMembership.class)
        .addComputeResource(1, memory, parallel)
        .setConfig(jobConfig)
        .build();
    // now submit the job
    Twister2Submitter.submitJob(twister2Job, config);
  }

  @Override
  public void execute(Config config, JobAPI.Job job, IWorkerController workerController,
                      IPersistentVolume persistentVolume, IVolatileVolume volatileVolume) {

  }
}
