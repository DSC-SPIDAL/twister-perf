package iu.iuni.deletion.io;

import edu.iu.dsc.tws.api.config.Config;

import java.math.BigInteger;

public class TweetIdReader extends BaseStreamInputReader<BigInteger> {
  private String seperator;

  public TweetIdReader(String fileName, Config config, String sep) {
    super(fileName, config);
    this.seperator = sep;
  }

  @Override
  public BigInteger nextRecord() {
    return new BigInteger(currentSize);
  }
}
