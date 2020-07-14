package iu.iuni.deletion.generator;

import iu.iuni.deletion.hbase.HBaseTweetIDReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.math.BigInteger;

public class HBaseTweetIDGenerator {
  private static final Log LOG = LogFactory.getLog(HBaseTweetIDReader.class);

  public static void main(String[] args) {
    Configuration config = HBaseConfiguration.create();
    String month = args[0];
    int records = Integer.parseInt(args[1]);
    // calculate the months
    try {
      runJob(config, month, records);
    } catch (IOException  e) {
      e.printStackTrace();
      LOG.error("Exception running the job", e);
    }
  }

  public static void runJob(Configuration conf, String month, int records) throws IOException {
    final String allTweetsTable = "allTweetsTableTest" + "-" + month;

    HTable hTable = new HTable(conf, allTweetsTable);
    BigInteger start = new BigInteger("100000000000000000").multiply(new BigInteger("" + (1)));
    byte[] tds = Bytes.toBytes("td");
    byte[] column_names = Bytes.toBytes("ct");
    for (int i = 0; i < records; i++) {
      BigInteger bi = start.add(new BigInteger(i + ""));
      Put p = new Put(bi.toByteArray());
      byte[] values = Bytes.toBytes(month);
      p.add(tds, column_names, values);
      hTable.put(p);
    }
    System.out.println("Wrote " + records);
    hTable.close();
  }
}
