package org.twister2.perf.shuffle.tws.string;

import edu.iu.dsc.tws.api.comms.structs.Tuple;
import edu.iu.dsc.tws.api.config.Config;
import edu.iu.dsc.tws.api.resource.*;
import edu.iu.dsc.tws.api.tset.TSetContext;
import edu.iu.dsc.tws.api.tset.fn.SinkFunc;
import edu.iu.dsc.tws.api.tset.fn.SourceFunc;
import edu.iu.dsc.tws.tset.env.BatchEnvironment;
import edu.iu.dsc.tws.tset.env.TSetEnvironment;
import edu.iu.dsc.tws.tset.fn.HashingPartitioner;
import edu.iu.dsc.tws.tset.sets.batch.SinkTSet;
import org.twister2.perf.io.FileReader;
import org.twister2.perf.io.TweetBufferedOutputWriter;
import org.twister2.perf.shuffle.Context;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StringShuffle implements Twister2Worker, Serializable {
  private static final Logger LOG = Logger.getLogger(StringShuffle.class.getName());

  @Override
  public void execute(WorkerEnvironment workerEnvironment) {
    BatchEnvironment batchEnv = TSetEnvironment.initBatch(workerEnvironment);
    Config config = workerEnvironment.getConfig();
    int parallel = config.getIntegerValue(Context.ARG_PARALLEL);
    // first we are going to read the files and sort them
    SinkTSet<Iterator<Tuple<String, String>>> sink1 = batchEnv.createKeyedSource(new SourceFunc<Tuple<String, String>>() {
      FileReader<String, String> reader;

      private TSetContext ctx;

      private String prefix;

      @Override
      public void prepare(TSetContext context) {
        this.ctx = context;
        prefix = context.getConfig().getStringValue(Context.ARG_FILE_PREFIX);
        reader = new StringReader(prefix + "/in/input-" + context.getIndex(), context.getConfig());
      }

      @Override
      public boolean hasNext() {
        try {
          boolean b = reader.reachedEnd();
          if (b) {
            LOG.info("Done reading from file - " + prefix + "/in/input-" + ctx.getIndex());
          }
          return !b;
        } catch (Exception e) {
          throw new RuntimeException("Failed to read", e);
        }
      }

      @Override
      public Tuple<String, String> next() {
        try {
          return reader.nextRecord();
        } catch (Exception e) {
          throw new RuntimeException("Failed to read next", e);
        }
      }
    }, parallel).keyedGatherUngrouped(new HashingPartitioner<>(), new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.compareTo(o2);
      }
    }).useDisk().sink(new SinkFunc<Iterator<Tuple<String, String>>>() {
      TweetBufferedOutputWriter writer;

      TSetContext context;

      boolean csv;

      int i = 0;

      StringBuilder builder = new StringBuilder();

      private BlockingQueue<Tuple<StringBuilder, Boolean>> queue = new ArrayBlockingQueue<>(10);
      Thread thread;

      boolean write;

      @Override
      public void prepare(TSetContext context) {
        try {
          String prefix = context.getConfig().getStringValue(Context.ARG_FILE_PREFIX);
          writer = new TweetBufferedOutputWriter(prefix + "/csvDataOut/outfile-" + context.getIndex(), context.getConfig());
          this.write = context.getConfig().getBooleanValue(Context.ARG_WRITE);
          this.context = context;
          if (write) {
            this.thread = new Thread(new ConsumingThread(queue, writer, write));
            this.thread.start();
          }
        } catch (FileNotFoundException e) {
          throw new RuntimeException("Failed to write", e);
        }
      }

      @Override
      public boolean add(Iterator<Tuple<String, String>> value) {
        LOG.info("Starting to save write: " + write);
        if (write) {
          while (value.hasNext()) {
            try {
              Tuple<String, String> next = value.next();
              builder.append(next.getKey().toString()).append(",").append(next.getValue()).append("\n");
              if (i > 0 && i % 10000 == 0) {
                queue.put(new Tuple<>(builder, false));
                builder = new StringBuilder();
              }
              i++;
            } catch (Exception e) {
              throw new RuntimeException("Failed to write", e);
            }
          }

          try {
            queue.put(new Tuple<>(builder, true));
          } catch (Exception e) {
            throw new RuntimeException("Failed to write", e);
          }

          try {
            thread.join();
          } catch (InterruptedException e) {
            LOG.info("Interrupted");
          }
        } else {
          while (value.hasNext()) {
            value.next();
          }
        }
        writer.close();
        return true;
      }
    });
    batchEnv.eval(sink1);
    batchEnv.finishEval(sink1);
    //batchEnv.close();
  }

  private static class ConsumingThread implements Runnable {
    private BlockingQueue<Tuple<StringBuilder, Boolean>> queue;

    TweetBufferedOutputWriter writer;

    boolean write;

    public ConsumingThread(BlockingQueue<Tuple<StringBuilder, Boolean>> queue,
                           TweetBufferedOutputWriter writer, boolean write) {
      this.queue = queue;
      this.writer = writer;
      this.write = write;
    }

    @Override
    public void run() {
      while (true) {
        try {
          Tuple<StringBuilder, Boolean> value = queue.take();
          writer.writeWithoutEnd(value.toString());
          if (value.getValue()) {
            break;
          }
        } catch (InterruptedException e) {
          LOG.log(Level.INFO, "Interrupted", e);
          break;
        } catch (Exception e) {
          throw new RuntimeException("Failed to write", e);
        }
      }
    }
  }
}
