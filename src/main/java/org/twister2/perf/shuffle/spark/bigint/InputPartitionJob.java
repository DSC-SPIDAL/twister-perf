package org.twister2.perf.shuffle.spark.bigint;

import org.apache.spark.HashPartitioner;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;
import org.twister2.perf.shuffle.io.EmptyOutputFormat;
import scala.Tuple2;

import java.math.BigInteger;

public class InputPartitionJob {
  public static void main(String[] args) {
    SparkConf conf = new SparkConf().setAppName("terasort");
    String prefix = args[0] + "/csvData";
    int parallel = Integer.parseInt(args[1]);
    boolean justCSV = Boolean.parseBoolean(args[2]);
    boolean out = Boolean.parseBoolean(args[3]);
    boolean dataSet = Boolean.parseBoolean(args[4]);

    Function<String, String> mapFn = new Function<String, String>() {
      @Override
      public String call(String s) throws Exception {
        return null;
      }
    };

    Function<String, Boolean> filterFn = new Function<String, Boolean>() {
      @Override
      public Boolean call(String s) throws Exception {
        return null;
      }
    };

    JavaSparkContext sc = new JavaSparkContext(conf);
    JavaRDD<String> input = sc.textFile(prefix, parallel);
    JavaRDD<String> filtered = input.filter(filterFn);
    // persist the data to the stable storage
    JavaRDD<String> persisted = filtered.persist(StorageLevel.MEMORY_AND_DISK());
    // use the persisted values
    persisted.map(mapFn);


    JavaPairRDD<BigInteger, Long>  source = input.mapToPair(new PairFunction<String, BigInteger, Long>() {
      @Override
      public Tuple2<BigInteger, Long> call(String s) throws Exception {
        String[] a = s.split(",");
        return new Tuple2<>(new BigInteger(a[0]), Long.parseLong(a[1]));
      }
    });

    if (!dataSet) {
      if (justCSV) {
        if (out) {
          source.saveAsTextFile(args[0] + "/sparkOut2");
        } else {
          source.saveAsHadoopFile(args[0] + "/sparkOut2", BigInteger.class, Long.class, EmptyOutputFormat.class);
        }
      } else {
        if (out) {
          source.repartitionAndSortWithinPartitions(new HashPartitioner(parallel)).saveAsTextFile(args[0] + "/sparkOut");
        } else {
          source.repartitionAndSortWithinPartitions(new HashPartitioner(parallel)).saveAsHadoopFile(
              args[0] + "/sparkOut", BigInteger.class, Long.class, EmptyOutputFormat.class);
        }
      }
    } else {
      SparkSession spark = SparkSession
          .builder()
          .appName("Java Spark SQL basic example")
          .config("spark.some.config.option", "some-value")
          .getOrCreate();

      Dataset<Row> row = spark.createDataset(JavaPairRDD.toRDD(source), Encoders.tuple(Encoders.kryo(BigInteger.class), Encoders.LONG())).toDF("key", "value");
      Dataset<Row> save = row.repartition(row.col("key")).sortWithinPartitions(row.col("key"));
      save.write().csv(args[0] + "/sparkOut");
    }
  }
}
