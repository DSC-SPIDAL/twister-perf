package iu.iuni.deletion.generator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;

import java.io.IOException;
import java.math.BigInteger;

public class HbaseTweetIDPrinter {
  public static void main(String[] args) throws IOException {
    // Instantiating Configuration class
    Configuration config = HBaseConfiguration.create();

    // Instantiating HTable class
    HTable table = new HTable(config, args[0]);

    // Instantiating the Scan class
    Scan scan = new Scan();

    // Getting the scan result
    ResultScanner scanner = table.getScanner(scan);

    // Reading values from scan result
    for (Result result = scanner.next(); result != null; result = scanner.next()) {
      String month = new String(result.getValue("td".getBytes(), "ct".getBytes()));
      BigInteger row = new BigInteger(result.getRow());
      System.out.println("Found row : " + result + " month: " + month + " key: " + row.toString());
    }
    //closing the scanner
    scanner.close();
  }
}
