package iu.iuni.deletion.io;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;

import java.math.BigInteger;

public class TweetIdDateReader extends BaseStreamInputReader<Tuple<BigInteger, String>> {
  private String seperator;

  public TweetIdDateReader(String fileName, Config config, String sep) {
    super(fileName, config);
    this.seperator = sep;
  }

  @Override
  public Tuple<BigInteger, String> nextRecord() {
    String[] a = currentSize.split(seperator);
    return new Tuple<>(new BigInteger(a[0]), a[1]);
  }
}