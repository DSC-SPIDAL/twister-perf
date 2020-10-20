package org.twister2.perf.join.spark;

import org.apache.hadoop.conf.Configuration;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
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

    JavaSparkContext sc = new JavaSparkContext(conf);

    JavaPairRDD<Long, Long> input1 = sc.newAPIHadoopRDD(configuration, LongInputFormat.class, Long.class, Long.class);
    LOG.info("No of Partitions of input 1 : " + input1.getNumPartitions());


    SQLContext sqlContext = new SQLContext(sc);
    Dataset<Row> ds1 = sqlContext.createDataset(JavaPairRDD.toRDD(input1),
        Encoders.tuple(Encoders.LONG(), Encoders.LONG())).toDF("key", "value");
    ds1.persist(StorageLevel.MEMORY_AND_DISK());
    LOG.info("Total elements 1: " + ds1.count());

    JavaPairRDD<Long, Long> input2 = sc.newAPIHadoopRDD(configuration, LongInputFormat.class, Long.class, Long.class);
    Dataset<Row> ds2 = sqlContext.createDataset(JavaPairRDD.toRDD(input2),
        Encoders.tuple(Encoders.LONG(), Encoders.LONG())).toDF("key", "value");
    ds2.persist(StorageLevel.MEMORY_AND_DISK());
    LOG.info("Total elements 2: " + ds1.count());

    Dataset<Row> join = ds1.alias("ds1").join(ds2.alias("ds2"), ds1.col("key")
        .equalTo(ds2.col("key")), "inner").select();

    if (args.length > 3) {
      join.write().text(args[3]);
    } else {
      join.foreach(r -> {
        Integer key = r.getInt(0);
        Long v1 = r.getLong(2);
        Long v2 = r.getLong(3);
      });
    }
    sc.stop();
    LOG.info("Stopping join job...");
  }
}
