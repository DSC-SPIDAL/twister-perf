package iu.iuni.deletion.io;

import java.io.IOException;

public interface FileReader<K> {
  boolean reachedEnd() throws IOException;
  K nextRecord();
}
