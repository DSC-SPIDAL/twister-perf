package iu.iuni.deletion.hbase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.MultiTableInputFormat;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * This class reads the allTweetsTable and output the content into the file system
 */
public class HBaseTweetIDReader {
  private static final Log LOG = LogFactory.getLog(HBaseTweetIDReader.class);

  public static void main(String[] args) {
    Configuration conf = HBaseConfiguration.create();
    String startMonth = args[0];
    String endMonth = args[1];
    String outputDir = args[2];
    // calculate the months
    List<String> months = getMonths(startMonth, endMonth);

    try {
      runJob(conf, months, outputDir);
    } catch (IOException | ClassNotFoundException | InterruptedException e) {
      LOG.error("Exception running the job", e);
    }
  }

  public static void runJob(Configuration conf, List<String> months, String outputDirectory) throws IOException,
      ClassNotFoundException, InterruptedException {
    final String allTweetsTable = "allTweetsTableTest";

    List<Scan> scans = new ArrayList<>();
    for (String month : months) {
      String allTweetsTableName = allTweetsTable + "-" + month;
      LOG.info("Reading table - " + allTweetsTableName);
      Scan scan = new Scan();
      scan.setCaching(500);
      scan.setCacheBlocks(false);
      scan.setAttribute(Scan.SCAN_ATTRIBUTES_TABLE_NAME, Bytes.toBytes(allTweetsTableName));
      scans.add(scan);
    }

    if (conf == null) {
      throw new RuntimeException("CONF NULL");
    }

    Job job = Job.getInstance(conf, "Retrieve all the tweets for tweet ID Table " + allTweetsTable);
    job.setJarByClass(HBaseTweetIDReader.class);
    job.setInputFormatClass(MultiTableInputFormat.class);
    FileOutputFormat.setOutputPath(job, new Path(outputDirectory));

    TableMapReduceUtil.initTableMapperJob(
        scans,	          // Scan instance to control CF and attribute selection
        TweetIDReadingMapper.class,   // mapper class
        Text.class,        // mapper output key
        Text.class,	          // mapper output value
        job);
    job.setNumReduceTasks(0);
    boolean b = job.waitForCompletion(true);
    if (!b) {
      throw new IOException("error with job!");
    }
  }

  private static List<String> getMonths(String date1, String date2) {
    List<String> months = new ArrayList<>();
    DateFormat formater = new SimpleDateFormat("yyyy-MM");


    Calendar beginCalendar = Calendar.getInstance();
    Calendar finishCalendar = Calendar.getInstance();

    try {
      beginCalendar.setTime(formater.parse(date1));
      finishCalendar.setTime(formater.parse(date2));
    } catch (ParseException e) {
      LOG.error("Date format error", e);
    }

    while (beginCalendar.before(finishCalendar) || beginCalendar.equals(finishCalendar)) {
      // add one month to date per loop
      String date =     formater.format(beginCalendar.getTime()).toUpperCase();
      System.out.println(date);
      months.add(date);
      beginCalendar.add(Calendar.MONTH, 1);
    }
    return months;
  }

  public static class TweetIDReadingMapper extends TableMapper<Text, Text> {
    private Text text = new Text();

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
      super.setup(context);
    }

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException,
        InterruptedException {
      String month = new String(value.getValue("td".getBytes(), "ct".getBytes()));
      text.set(month);
      context.write(new Text(new BigInteger(key.get()).toString()), text);
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
      super.cleanup(context);
    }
  }
}
