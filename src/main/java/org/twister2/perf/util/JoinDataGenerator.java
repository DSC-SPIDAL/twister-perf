package org.twister2.perf.util;

import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.data.FileSystem;
import edu.iu.dsc.tws.api.resource.IPersistentVolume;
import edu.iu.dsc.tws.api.resource.IVolatileVolume;
import edu.iu.dsc.tws.api.resource.IWorker;
import edu.iu.dsc.tws.api.resource.IWorkerController;
import edu.iu.dsc.tws.data.hdfs.HadoopFileSystem;
import edu.iu.dsc.tws.data.utils.HdfsDataContext;
import edu.iu.dsc.tws.proto.system.job.JobAPI;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class JoinDataGenerator implements IWorker {
  public static void main(String[] args) throws IOException {
    int recordsPerRelation = Integer.parseInt(args[0]);
    int nodes = Integer.parseInt(args[1]);
    String out = args[2];
    long noOfTuplesPerNode = recordsPerRelation / nodes;
    System.out.println("Generating " + noOfTuplesPerNode + " tuples per node...");
    Random random = new Random(System.nanoTime());
    for (int node = 0; node < nodes; node++) {
      BufferedWriter br1 = new BufferedWriter(new FileWriter(new File(out + "/in1/" + node)));
      BufferedWriter br2 = new BufferedWriter(new FileWriter(new File(out + "/in2/" + node)));
      for (int i = 0; i < noOfTuplesPerNode; i++) {
        br1.write(String.format("%d\t%d", random.nextInt(recordsPerRelation), random.nextLong()));
        br1.newLine();
        br2.write(String.format("%d\t%d", random.nextInt(recordsPerRelation), random.nextLong()));
        br2.newLine();
      }
      br1.close();
      br2.close();
    }
  }

  @Override
  public void execute(Config config, JobAPI.Job job, IWorkerController workerController,
                      IPersistentVolume persistentVolume, IVolatileVolume volatileVolume) {

    Configuration configuration1 = new Configuration();
    configuration1.addResource(
        new Path(HdfsDataContext.getHdfsConfigDirectory(config)));
    //FileSystem fs = new HadoopFileSystem();
  }
}
