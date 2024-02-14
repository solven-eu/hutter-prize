/*
Copyright 2011-2024 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package eu.solven.hutter_prize.kanzi_only;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kanzi.Error;
import kanzi.Event;
import kanzi.Listener;
import kanzi.SliceByteArray;
import kanzi.app.BlockDecompressor;
import kanzi.app.InfoPrinter;
import kanzi.io.CompressedInputStream;

/**
 * From {@link BlockDecompressor} to handle {@link InputStream}
 * @author Benoit Lacelle
 *
 */
public class InputStreamDecompressor implements Runnable, Callable<Integer>
{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamDecompressor.class);
   private static final int DEFAULT_BUFFER_SIZE = 65536;
   private static final int MAX_CONCURRENCY = 64;

   private int verbosity;
   private final int jobs;
   private final int from; // start block
   private final int to; // end block
   private final ExecutorService pool;
   private final List<Listener> listeners;

   private final InputStream bais;
   private final OutputStream baos;


   public InputStreamDecompressor(Map<String, Object> map, InputStream bais, OutputStream baos)
   {
	   this.bais=bais;
	   this.baos=baos;
	   
      this.verbosity = (Integer) map.remove("verbose");
      this.from = (map.containsKey("from") ? (Integer) map.remove("from") : -1);
      this.to = (map.containsKey("to") ? (Integer) map.remove("to") : -1);
      int concurrency;
                                                                                                                                                        if (map.containsKey("jobs"))
      {
         concurrency = (Integer) map.remove("jobs");

         if (concurrency == 0)
         {
            // Use all cores
            int cores = Runtime.getRuntime().availableProcessors();
            concurrency = Math.min(cores, MAX_CONCURRENCY);
         }
         else if (concurrency > MAX_CONCURRENCY)
         {
            printOut("Warning: the number of jobs is too high, defaulting to "+MAX_CONCURRENCY, this.verbosity>0);
            concurrency = MAX_CONCURRENCY;
         }
      }
      else
      {
          // Default to half of cores
          int cores = Math.max(Runtime.getRuntime().availableProcessors()/2, 1);
          concurrency = Math.min(cores, MAX_CONCURRENCY);
      }

      this.jobs = concurrency;
      this.pool = Executors.newFixedThreadPool(this.jobs);
      this.listeners = new ArrayList<>(10);

      if ((this.verbosity > 0) && (map.size() > 0))
      {
         for (String k : map.keySet())
            printOut("Warning: Ignoring invalid option [" + k + "]", true); //this.verbosity>0
      }
   }


   public void dispose()
   {
      if (this.pool != null)
         this.pool.shutdown();
   }


   @Override
   public void run()
   {
      this.call();
   }


   @Override
   public Integer call()
   {
      long read = 0;
      long before = System.nanoTime();
      int nbFiles = 1;

      // Limit verbosity level when files are processed concurrently
      if ((this.jobs > 1) && (nbFiles > 1) && (this.verbosity > 1))
      {
         printOut("Warning: limiting verbosity to 1 due to concurrent processing of input files.\n", true);
         this.verbosity = 1;
      }

      if (this.verbosity > 2)
      {
         printOut("Verbosity set to "+this.verbosity, true);
         printOut("Using " + this.jobs + " job" + ((this.jobs > 1) ? "s" : ""), true);
         this.addListener(new InfoPrinter(this.verbosity, InfoPrinter.Type.DECODING, System.out));
      }

      int res = 0;

      try
      {
         Map<String, Object> ctx = new HashMap<>();
         ctx.put("verbosity", this.verbosity);
         ctx.put("pool", this.pool);

         if (this.from >= 0)
            ctx.put("from", this.from);

         if (this.to >= 0)
            ctx.put("to", this.to);

         // Run the task(s)
         {
            ctx.put("jobs", this.jobs);
            ByteArrayDecompressTask task = new ByteArrayDecompressTask(ctx, this.listeners, bais, baos);
            ByteArrayDecompressResult fdr = task.call();
            res = fdr.code;
            read = fdr.read;
         }
      }
      catch (Exception e)
      {
    	  throw new RuntimeException(e);
      }

      long after = System.nanoTime();

      if (nbFiles > 1)
      {
         long delta = (after - before) / 1000000L; // convert to ms
         printOut("", this.verbosity>0);
         String str;

         if (delta >= 100000) {
            str = String.format("%1$.1f", (float) delta/1000) + " s";
         } else {
            str = String.valueOf(delta) + " ms";
         }

         printOut("Total decompression time: "+str, this.verbosity > 0);
         printOut("Total output size: "+read+((read>1)?" bytes":" byte"), this.verbosity > 0);
       }

      return res;
   }


    private static void printOut(String msg, boolean print)
    {
       if ((print == true) && (msg != null))
    	   LOGGER.info(msg);
    }


    public final boolean addListener(Listener bl)
    {
       return (bl != null) ? this.listeners.add(bl) : false;
    }


    public final boolean removeListener(Listener bl)
    {
       return (bl != null) ? this.listeners.remove(bl) : false;
    }


    static void notifyListeners(Listener[] listeners, Event evt)
    {
       for (Listener bl : listeners)
       {
          try
          {
             bl.processEvent(evt);
          }
          catch (Exception e)
          {
            // Ignore exceptions in listeners
          }
       }
    }



   static class ByteArrayDecompressResult
   {
       final int code;
       final long read;


      public ByteArrayDecompressResult(int code, long read)
      {
         this.code = code;
         this.read = read;
      }
   }


   static class ByteArrayDecompressTask implements Callable<ByteArrayDecompressResult>
   {
	   private InputStream bais;
	      
      private final Map<String, Object> ctx;
      private CompressedInputStream cis;
      private final OutputStream os;
      private final List<Listener> listeners;


      public ByteArrayDecompressTask(Map<String, Object> ctx, List<Listener> listeners, InputStream bais, OutputStream baos)
      {
    	  this.bais=bais;
    	  this.os = baos;
    	  
         this.ctx = ctx;
         this.listeners = listeners;
      }


      @Override
      public ByteArrayDecompressResult call() throws Exception
      {
         int verbosity = (Integer) this.ctx.get("verbosity");

         if (verbosity > 2)
         {
            printOut("Input file name set to '" + "inMemory" + "'", true);
            printOut("Output file name set to '" + "inMemory" + "'", true);
         }

         long read = 0;
         printOut("\nDecompressing "+"inMemory"+" ...", verbosity>1);
         printOut("", verbosity>3);

         if (this.listeners.size() > 0)
         {
            Event evt = new Event(Event.Type.DECOMPRESSION_START, -1, 0);
            Listener[] array = this.listeners.toArray(new Listener[this.listeners.size()]);
            notifyListeners(array, evt);
         }

         InputStream is = bais;
         {
             this.cis = new CompressedInputStream(is, this.ctx);

             for (Listener bl : this.listeners)
                this.cis.addListener(bl);
          }

         long before = System.nanoTime();

         try
         {
            SliceByteArray sa = new SliceByteArray(new byte[DEFAULT_BUFFER_SIZE], 0);
            int decoded;

            // Decode next block
            do
            {
               decoded = this.cis.read(sa.array, 0, sa.length);

               if (decoded < 0)
               {
                  System.err.println("Reached end of stream");
                  return new ByteArrayDecompressResult(Error.ERR_READ_FILE,  this.cis.getRead());
               }

               try
               {
                  if (decoded > 0)
                  {
                     this.os.write(sa.array, 0, decoded);
                     read += decoded;
                  }
               }
               catch (Exception e)
               {
                  System.err.print("Failed to write decompressed block to file '"+"inMemory"+"': ");
                  System.err.println(e.getMessage());
                  return new ByteArrayDecompressResult(Error.ERR_READ_FILE, this.cis.getRead());
               }
            }
            while (decoded == sa.array.length);
         }
         catch (kanzi.io.IOException e)
         {
            System.err.println("An unexpected condition happened. Exiting ...");
            System.err.println(e.getMessage());
            return new ByteArrayDecompressResult(e.getErrorCode(), this.cis.getRead());
         }
         catch (Exception e)
         {
            System.err.println("An unexpected condition happened. Exiting ...");
            System.err.println(e.getMessage());
            return new ByteArrayDecompressResult(Error.ERR_UNKNOWN, this.cis.getRead());
         }
         finally
         {
            // Close streams to ensure all data are flushed
            this.dispose();

            try
            {
               is.close();
            }
            catch (IOException e)
            {
               // Ignore
            }

            if (this.listeners.size() > 0)
            {
               Event evt = new Event(Event.Type.DECOMPRESSION_END, -1, this.cis.getRead());
               Listener[] array = this.listeners.toArray(new Listener[this.listeners.size()]);
               notifyListeners(array, evt);
            }
         }

         long after = System.nanoTime();
         long delta = (after - before) / 1000000L; // convert to ms
         String str;

         if (verbosity >= 1)
         {
            printOut("", verbosity>1);

            if (delta >= 100000)
               str = String.format("%1$.1f", (float) delta/1000) + " s";
            else
               str = String.valueOf(delta) + " ms";

            if (verbosity > 1)
            {
               printOut("Decompressing:          "+str, true);
               printOut("Input size:             "+this.cis.getRead(), true);
               printOut("Output size:            "+read, true);
            }

            if (verbosity == 1)
            {
               str = String.format("Decompressing %s: %d => %d in %s", "inMemory", this.cis.getRead(), read, str);
               printOut(str, true);
            }

            if ((verbosity > 1) && (delta > 0))
               printOut("Throughput (KB/s): "+(((read * 1000L) >> 10) / delta), true);

            printOut("", verbosity>1);
         }

         return new ByteArrayDecompressResult(0, read);
      }

      public void dispose() throws IOException
      {
         if (this.cis != null)
            this.cis.close();

         if (this.os != null)
            this.os.close();
      }
   }



   static class FileDecompressWorker implements Callable<ByteArrayDecompressResult>
   {
      private final ArrayBlockingQueue<ByteArrayDecompressTask> queue;

      public FileDecompressWorker(ArrayBlockingQueue<ByteArrayDecompressTask> queue)
      {
         this.queue = queue;
      }

      @Override
      public ByteArrayDecompressResult call() throws Exception
      {
         int res = 0;
         long read = 0;

         while (res == 0)
         {
            ByteArrayDecompressTask task = this.queue.poll();

            if (task == null)
               break;

            ByteArrayDecompressResult result = task.call();
            res = result.code;
            read += result.read;
         }

         return new ByteArrayDecompressResult(res, read);
      }
   }
}
