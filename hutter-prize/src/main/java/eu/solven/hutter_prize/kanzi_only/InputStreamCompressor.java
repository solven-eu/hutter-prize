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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kanzi.Error;
import kanzi.Event;
import kanzi.Listener;
import kanzi.SliceByteArray;
import kanzi.app.InfoPrinter;
import kanzi.io.CompressedOutputStream;
import kanzi.transform.TransformFactory;


/**
 * From {@link InputStreamCompressor} to handle {@link InputStream}
 * @author Benoit Lacelle
 *
 */
public class InputStreamCompressor implements Runnable, Callable<Integer>
{
	private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamCompressor.class);
	
   private static final int DEFAULT_BUFFER_SIZE = 65536;
   private static final int DEFAULT_BLOCK_SIZE  = 4*1024*1024;
   private static final int MIN_BLOCK_SIZE  = 1024;
   private static final int MAX_BLOCK_SIZE  = 1024*1024*1024;
   private static final int MAX_CONCURRENCY = 64;
   private static final String NONE = "NONE";

   private int verbosity;
   private final boolean checksum;
   private final boolean skipBlocks;
   private final boolean autoBlockSize;
   private final InputStream bais;
   private final OutputStream baos;
   private final String codec;
   private final String transform;
   private int blockSize;
   private final int jobs;
   private final List<Listener> listeners;
   private final ExecutorService pool;


   public InputStreamCompressor(Map<String, Object> map, InputStream bais, OutputStream baos)
   {
	   this.bais=bais;
	   this.baos=baos;
	   
      int level = - 1;

      if (map.containsKey("level") == true)
      {
         level = (Integer) map.remove("level");

         if ((level < 0) || (level > 9))
            throw new IllegalArgumentException("Invalid compression level (must be in [0..9], got " + level);

         String tranformAndCodec = getTransformAndCodec(level);
         String[] tokens = tranformAndCodec.split("&");
         this.transform = tokens[0];
         this.codec = tokens[1];
      }
      else
      {
         if ((map.containsKey("transform") == false) && (map.containsKey("entropy") == false))
         {
            // Defaults to level 3
            String tranformAndCodec = getTransformAndCodec(3);
            String[] tokens = tranformAndCodec.split("&");
            this.transform = tokens[0];
            this.codec = tokens[1];
         }
         else
         {
            // Extract transform names. Curate input (EG. NONE+NONE+xxxx => xxxx)
            String strT = map.containsKey("transform") ? (String) map.remove("transform") : "NONE";
            TransformFactory tf = new TransformFactory();
            this.transform = tf.getName(tf.getType(strT));
            this.codec = map.containsKey("entropy") ? (String) map.remove("entropy") : "NONE";
         }
      }

      Boolean bSkip = (Boolean) map.remove("skipBlocks");
      this.skipBlocks = (bSkip == null) ? false : bSkip;
      Integer iBlockSize = (Integer) map.remove("block");

      if (iBlockSize == null)
      {
         switch (level)
         {
             case 6:
                 this.blockSize = 2 * DEFAULT_BLOCK_SIZE;
                 break;
             case 7:
                 this.blockSize = 4 * DEFAULT_BLOCK_SIZE;
                 break;
             case 8:
                 this.blockSize = 4 * DEFAULT_BLOCK_SIZE;
                 break;
             case 9:
                 this.blockSize = 8 *DEFAULT_BLOCK_SIZE;
                 break;
             default:
                 this.blockSize = DEFAULT_BLOCK_SIZE;
         }
      }
      else
      {
         final int bs = iBlockSize;

         if (bs < MIN_BLOCK_SIZE)
            throw new IllegalArgumentException("Minimum block size is "+(MIN_BLOCK_SIZE/1024)+
               " KB ("+MIN_BLOCK_SIZE+" bytes), got "+bs+" bytes");

         if (bs > MAX_BLOCK_SIZE)
            throw new IllegalArgumentException("Maximum block size is "+(MAX_BLOCK_SIZE/(1024*1024*1024))+
               " GB ("+MAX_BLOCK_SIZE+" bytes), got "+bs+" bytes");

         this.blockSize = Math.min((bs+15) & -16, MAX_BLOCK_SIZE);
      }

      Boolean bChecksum = (Boolean) map.remove("checksum");
      this.checksum = (bChecksum == null) ? false : bChecksum;
      Boolean bAuto = (Boolean) map.remove("autoBlock");
      this.autoBlockSize = (bAuto == null) ? false : bAuto;
      this.verbosity = (Integer) map.remove("verbose");
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


   // Return status (success = 0, error < 0)
   @Override
   public Integer call()
   {
      long before = System.nanoTime();

      // Limit verbosity level when files are processed concurrently
      if ((this.jobs > 1) && (this.verbosity > 1))
      {
         printOut("Warning: limiting verbosity to 1 due to concurrent processing of input files.\n", true);
         this.verbosity = 1;
      }

      if (this.verbosity > 2)
      {
         if (this.autoBlockSize == true)
            printOut("Block size set to 'auto'", true);
         else
            printOut("Block size set to " + this.blockSize + " bytes", true);

         printOut("Verbosity set to " + this.verbosity, true);
         printOut("Checksum set to " +  this.checksum, true);
         String etransform = (NONE.equals(this.transform)) ? "no" : this.transform;
         printOut("Using " + etransform + " transform (stage 1)", true);
         String ecodec = (NONE.equals(this.codec)) ? "no" : this.codec;
         printOut("Using " + ecodec + " entropy codec (stage 2)", true);
         printOut("Using " + this.jobs + " job" + ((this.jobs > 1) ? "s" : ""), true);
         this.addListener(new InfoPrinter(this.verbosity, InfoPrinter.Type.ENCODING, System.out));
      }

      int res = 0;
      long read = 0;
      long written = 0;

      try
      {
         Map<String, Object> ctx = new HashMap<>();
         ctx.put("verbosity", this.verbosity);
         ctx.put("skipBlocks", this.skipBlocks);
         ctx.put("checksum", this.checksum);
         ctx.put("pool", this.pool);
         ctx.put("entropy", this.codec);
         ctx.put("transform", this.transform);

         // Run the task(s)
         {

            ctx.put("blockSize", this.blockSize);
            ctx.put("jobs", this.jobs);
            InputStreamCompressTask task = new InputStreamCompressTask(ctx, this.listeners, bais, baos);
            InputStreamCompressResult fcr = task.call();
            res = fcr.code;
            read = fcr.read;
            written = fcr.written;
         }
      }
      catch (Exception e)
      {
    	  throw new RuntimeException(e);
      }

      long after = System.nanoTime();

      {
         long delta = (after - before) / 1000000L; // convert to ms
         printOut("", this.verbosity>0);
         String str;

         if (delta >= 100000) {
            str = String.format("%1$.1f", (float) delta/1000) + " s";
         } else {
            str = String.valueOf(delta) + " ms";
         }

         printOut("Total compression time: "+str, this.verbosity > 0);
         printOut("Total output size: "+written+" byte"+((written>1)?"s":""), this.verbosity > 0);

         if (read != 0)
         {
            float f = written / (float) read;
            printOut("Compression ratio: "+String.format("%1$.6f", f), this.verbosity > 0);
         }
      }

      return res;
    }


   private static void printOut(String msg, boolean print)
   {
      if ((print == true) && (msg != null))
         System.out.println(msg);
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


   private static String getTransformAndCodec(int level)
   {
      switch (level)
      {
        case 0 :
           return "NONE&NONE";

        case 1 :
           return "PACK+LZ&NONE";

        case 2 :
           return "PACK+LZ&HUFFMAN";

        case 3 :
           return "TEXT+UTF+PACK+MM+LZX&HUFFMAN";

        case 4 :
           return "TEXT+UTF+EXE+PACK+MM+ROLZ&NONE";

        case 5 :
           return "TEXT+UTF+BWT+RANK+ZRLT&ANS0";

        case 6 :
           return "TEXT+UTF+BWT+SRT+ZRLT&FPAQ";

        case 7 :
           return "LZP+TEXT+UTF+BWT+LZP&CM";

        case 8 :
           return "EXE+RLT+TEXT+UTF&TPAQ";

        case 9 :
           return "EXE+RLT+TEXT+UTF&TPAQX";

           // Custom
        case 10 :
           return "EXE+RLT+TEXT+UTF&TPAQX";

        default :
           return "Unknown&Unknown";
      }
   }



   static class InputStreamCompressResult
   {
      final int code;
      final long read;
      final long written;


      public InputStreamCompressResult(int code, long read, long written)
      {
         this.code = code;
         this.read = read;
         this.written = written;
      }
   }


   static class InputStreamCompressTask implements Callable<InputStreamCompressResult>
   {
      private final Map<String, Object> ctx;
      private final InputStream is;
      private final OutputStream os;
      private CompressedOutputStream cos;
      private final List<Listener> listeners;


      public InputStreamCompressTask(Map<String, Object> ctx, List<Listener> listeners, InputStream bais, OutputStream baos)
      {
    	  this.is=bais;
    	  this.os=baos;
    	  
         this.ctx = ctx;
         this.listeners = listeners;
      }


      @Override
      public InputStreamCompressResult call() throws Exception
      {
         int verbosity = (Integer) this.ctx.get("verbosity");

         if (verbosity > 2)
         {
            printOut("Input file name set to '" + "inMemory" + "'", true);
            printOut("Output file name set to '" + "inMemory" + "'", true);
         }

         try
         {
            try
            {
               this.cos = new CompressedOutputStream(os, this.ctx);

               for (Listener bl : this.listeners)
                  this.cos.addListener(bl);
            }
            catch (Exception e)
            {
               System.err.println("Cannot create compressed stream: "+e.getMessage());
               return new InputStreamCompressResult(Error.ERR_CREATE_COMPRESSOR, 0, 0);
            }
         }
         catch (Exception e)
         {
            System.err.println("Cannot open output file '"+"inMemory"+"' for writing: " + e.getMessage());
            return new InputStreamCompressResult(Error.ERR_CREATE_FILE, 0, 0);
         }

         // Encode
         printOut("\nCompressing "+"inMemory"+" ...", verbosity>1);
         printOut("", verbosity>3);
         long read = 0;
         SliceByteArray sa = new SliceByteArray(new byte[DEFAULT_BUFFER_SIZE], 0);
         int len;

         if (this.listeners.size() > 0)
         {
            Event evt = new Event(Event.Type.COMPRESSION_START, -1, 0);
            Listener[] array = this.listeners.toArray(new Listener[this.listeners.size()]);
            notifyListeners(array, evt);
         }

         long before = System.nanoTime();

         try
         {
            while (true)
            {
               try
               {
                  len = this.is.read(sa.array, 0, sa.length);
               }
               catch (Exception e)
               {
                  System.err.print("Failed to read block from file '"+"inMemory"+"': ");
                  System.err.println(e.getMessage());
                  return new InputStreamCompressResult(Error.ERR_READ_FILE, read, this.cos.getWritten());
               }

               if (len <= 0)
                  break;

               // Just write block to the compressed output stream !
               read += len;
               this.cos.write(sa.array, 0, len);
            }
         }
         catch (kanzi.io.IOException e)
         {
            System.err.println("An unexpected condition happened. Exiting ...");
            System.err.println(e.getMessage());
            return new InputStreamCompressResult(e.getErrorCode(), read, this.cos.getWritten());
         }
         catch (Exception e)
         {
            System.err.println("An unexpected condition happened. Exiting ...");
            System.err.println(e.getMessage());
            return new InputStreamCompressResult(Error.ERR_UNKNOWN, read, this.cos.getWritten());
         }
         finally
         {
            // Close streams to ensure all data are flushed
            this.dispose();

            try
            {
               os.close();
            }
            catch (IOException e)
            {
               // Ignore
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
               printOut("Compressing:       "+str, true);
               printOut("Input size:        "+read, true);
               printOut("Output size:       "+this.cos.getWritten(), true);

               if (read != 0)
                   printOut("Compression ratio: "+String.format("%1$.6f", (this.cos.getWritten() / (float) read)), true);
            }
            else
            {
               if (read == 0)
               {
                   str = String.format("Compressing %s: %d => %d in %s", "inMemory", read, this.cos.getWritten(), str);
               }
               else
               {
                   float f = this.cos.getWritten() / (float) read;
                   str = String.format("Compressing %s: %d => %d (%.2f%%) in %s", "inMemory", read, this.cos.getWritten(), 100*f, str);
               }

               printOut(str, true);
            }

            if ((verbosity > 1) && (delta != 0) && (read != 0))
               printOut("Throughput (KB/s): "+(((read * 1000L) >> 10) / delta), true);

            printOut("", verbosity>1);
         }

         if (this.listeners.size() > 0)
         {
            Event evt = new Event(Event.Type.COMPRESSION_END, -1, this.cos.getWritten());
            Listener[] array = this.listeners.toArray(new Listener[this.listeners.size()]);
            notifyListeners(array, evt);
         }

         return new InputStreamCompressResult(0, read, this.cos.getWritten());
      }


      public void dispose() throws IOException
      {
         if (this.is != null)
            this.is.close();

         if (this.cos != null)
            this.cos.close();
      }
   }

}
