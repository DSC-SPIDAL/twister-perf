package org.twister2.perf.join.spark;

import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FilterFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.storage.StorageLevel;
import org.twister2.perf.join.spark.input.LongInputFormat;

import java.util.logging.Logger;

public class JoinJobRandom {
  private final static Logger LOG = Logger.getLogger(JoinJobRandom.class.getName());
  public static void main(String[] args) {
    LOG.info("Starting join job....");
    SparkConf conf = new SparkConf().setAppName("join");
    conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
    conf.registerKryoClasses(new Class[]{Integer.class, Long.class});

    Configuration configuration = new Configuration();
    configuration.setInt("parallel", Integer.parseInt(args[0]));
    configuration.setInt("records", Integer.parseInt(args[1]));
    boolean persist = Boolean.parseBoolean(args[2]);

    JavaSparkContext sc = new JavaSparkContext(conf);

    JavaPairRDD<Long, Long> input1 = sc.newAPIHadoopRDD(configuration, LongInputFormat.class, Long.class, Long.class);
    LOG.info("No of Partitions of input 1 : " + input1.getNumPartitions());


    SQLContext sqlContext = new SQLContext(sc);
    Dataset<Row> ds1 = sqlContext.createDataset(JavaPairRDD.toRDD(input1),
        Encoders.tuple(Encoders.LONG(), Encoders.LONG())).toDF("key", "value1");
    LOG.info("Total elements 1: " + ds1.count());
    Dataset<Row> ds1Persist = null;
    if (persist) {
      ds1Persist = ds1.persist(StorageLevel.MEMORY_AND_DISK());
    }


    JavaPairRDD<Long, Long> input2 = sc.newAPIHadoopRDD(configuration, LongInputFormat.class, Long.class, Long.class);
    Dataset<Row> ds2 = sqlContext.createDataset(JavaPairRDD.toRDD(input2),
        Encoders.tuple(Encoders.LONG(), Encoders.LONG())).toDF("key", "value2");
    LOG.info("Total elements 2: " + ds1.count());
    Dataset<Row> ds2Persist = null;
    if (persist) {
      ds2Persist = ds2.persist(StorageLevel.MEMORY_AND_DISK());
    }

    long start = System.nanoTime();
    Dataset<Row> join;
    if (persist) {
      join = ds1Persist.alias("ds1").join(ds2Persist.alias("ds2"), ds1Persist.col("key")
          .equalTo(ds2Persist.col("key")), "inner").select();
    } else {
      join = ds1.alias("ds1").join(ds2.alias("ds2"), ds1.col("key")
          .equalTo(ds2.col("key")), "inner").select("ds1.key", "value1", "value2");
    }
    LOG.info("Final total: " + join.count());

//    Dataset<Row> f = join.filter(new FilterFunction<Row>() {
//      @Override
//      public boolean call(Row row) throws Exception {
//        return false;
//      }
//    });
    LOG.info("Filter " + join.count() + " Time: " + (System.nanoTime() - start) / 1000000);
    if (args.length > 3) {
      join.write().csv(args[3]);
    } /*else {
      join.foreach(r -> {
        Long key = f.getLong(0);
        Long v1 = f.getLong(1);
      });
    }*/
    sc.stop();
    LOG.info("Stopping join job...");
  }
}
