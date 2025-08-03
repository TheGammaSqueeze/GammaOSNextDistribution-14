/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id$
 */

package org.apache.qetest.xsl;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.Hashtable;

import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperHelper;

/**
 * Testlet to capture specific timing performance data.
 * This Testlet is a cut-down version of PerfEverythingTestlet.
 *
 * We log out XalanC-like overall performance numbers, as well 
 * as logging out any available data from the times array returned 
 * by our transformWrapper.  Note that different flavors of 
 * transformWrapper will return different sets of timings.
 *
 * @author Shane_Curcuru@us.ibm.com
 * @version $Id$
 */
public class PerfTestlet extends StylesheetTestlet
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.PerfTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     * @return String describing what this PerfTestlet does.
     */
    public String getDescription()
    {
        return "PerfTestlet";
    }


    /** 
     * Worker method to actually perform the transform; 
     * overriden to use command line processing.  
     *
     * Logs out applicable info; attempts to perform transformation 
     * and also prints out a &lt;perf&gt; element.
     *
     * @param datalet to test with
     * @param transformWrapper to have perform the transform
     * @throws allows any underlying exception to be thrown
     */
    protected void testDatalet(StylesheetDatalet datalet, TransformWrapper transformWrapper)
            throws Exception
    {
        // Setup: Save options from the datalet in convenience variables
        int iterations = 5;
        boolean runtimeGC = true;
        long[] times = null;

		// Read in necessary options from test.properties file.
        try
        {
            iterations = Integer.parseInt(datalet.options.getProperty("iterations"));
        }
        catch (Exception e) { /* no-op, leave as default */ }
        try
        {	
        	String gc = datalet.options.getProperty("runtimeGC");
			if (gc != null)
				runtimeGC = (Boolean.valueOf(gc)).booleanValue(); 
        }
        catch (Exception e) { /* no-op, leave as default */ }

        // Setup: store various XalanC-like timing data in convenience variables
		long warmup = 0L;			// First transform. Used to load classes.
        long singletransform = 0L;  // Very first Preload end-to-end transform
        long etoe = 0L;     		// First end-to-end transform during iterations
        long avgetoe = 0L;  		// Average of end-to-end transforms during iterations
        long parsexsl = 0L;     	// First stylesheet preprocess during iterations
        long avgparsexsl = 0L;  	// Average of stylesheet preprocess during iterations
        long unparsedxml = 0L;   	// First stylesheet process during iterations
        long transxml = 0L;			// Transform w/Stylesheet - NO OUTPUT
		long transxmloutput = 0L; 	// Transform w/Stylesheet - OUTPUT

        //logger.logMsg(Logger.TRACEMSG, "executing with: inputName=" + datalet.inputName
        //              + " xmlName=" + datalet.xmlName + " outputName=" + datalet.outputName
        //              + " goldName=" + datalet.goldName + " flavor="  + datalet.flavor
        //              + " iterations=" + iterations 
        //              + " algorithim=" + getDescription());

        //@todo make various logMemory calls optional
        logMemory(runtimeGC, false);

        // Measure(warmup): JVM warm up
        times = transformWrapper.transform(datalet.xmlName, datalet.inputName,
                                            datalet.outputName);
        warmup = times[TransformWrapper.IDX_OVERALL];
        logMemory(runtimeGC, false);

        // Measure(singletransform): Very first Preload end-to-end transform
        times = transformWrapper.transform(datalet.xmlName, datalet.inputName,
                                            datalet.outputName);
        singletransform = times[TransformWrapper.IDX_OVERALL];
        logMemory(runtimeGC, false);

        // Measure(parsexsl): once: first preprocess
        times = transformWrapper.buildStylesheet(datalet.inputName);
        parsexsl = times[TransformWrapper.IDX_OVERALL];
        logMemory(runtimeGC, false);

        // Measure(unparsedxml): once: first process
        times = transformWrapper.transformWithStylesheet(datalet.xmlName, datalet.outputName);
        unparsedxml = times[TransformWrapper.IDX_OVERALL];
        logMemory(runtimeGC, false);

        for (int ctr = 1; ctr <= iterations; ctr++)
        {
            // Measure(avgparsexsl): average preprocess
            times = transformWrapper.buildStylesheet(datalet.inputName);
            avgparsexsl += times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, false);

            // Measure(avgunparsedxml): getTransformer + xmlRead + transform 
            times = transformWrapper.transformWithStylesheet(datalet.xmlName, datalet.outputName);
            transxml += times[TransformWrapper.IDX_TRANSFORM];
            logMemory(runtimeGC, false);

            // Measure(avgunparsedxml): getTransformer + xmlRead + transform + resultWrite
            times = transformWrapper.transformWithStylesheet(datalet.xmlName, datalet.outputName);
            transxmloutput += times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, false);

        }

        // Measure(etoe): once: first full process
        times = transformWrapper.transform(datalet.xmlName, datalet.inputName, datalet.outputName);
        etoe = times[TransformWrapper.IDX_OVERALL];
        logMemory(runtimeGC, false);

        // Aggregate all specific timing data returned by TransformWrappers
        //  note that different flavors of wrappers will be able 
        //  to report different kinds of times
        // This data is separate from the XalanC-like data above
        //@todo determine which data is best to store
        long[] storedTimes = TransformWrapperHelper.getTimeArray();
        long[] storedCounts = TransformWrapperHelper.getTimeArray();
        for (int ctr = 1; ctr <= iterations; ctr++)
        {
            // Measure(avgetoe): average full process
            times = transformWrapper.transform(datalet.xmlName, datalet.inputName, datalet.outputName);
            avgetoe += times[TransformWrapper.IDX_OVERALL];
            aggregateTimes(times, storedTimes, storedCounts);
            logMemory(runtimeGC, false);
        }

        Hashtable attrs = new Hashtable();
        // BOTH: Log all specific timing data returned by TransformWrappers
        logAggregateTimes(attrs, storedTimes, storedCounts);

        // ASLO: Log special XalanC-like performance element with our timing
        // UniqRunid is an Id that our TestDriver normally sets 
        //  with some unique code, so that results analysis 
        //  stylesheets can compare different test runs
        attrs.put("UniqRunid", datalet.options.getProperty("runId", "runId;none"));
        // processor is the 'flavor' of processor we're testing
        attrs.put("processor", transformWrapper.getDescription());
        // idref is the individual filename
        attrs.put("idref", (new File(datalet.inputName)).getName());
        // inputName is the actual name we gave to the processor
        attrs.put("inputName", datalet.inputName);
        attrs.put("iterations", new Integer(iterations));
		attrs.put("warmup", new Long(warmup)); 
        attrs.put("singletransform", new Long(singletransform)); // Very first Preload end-to-end transform
        attrs.put("etoe", new Long(etoe)); // First end-to-end transform during iterations
        // Note that avgetoe should match logTimes()'s OVERALL value
        attrs.put("avgetoe", new Long(avgetoe / iterations)); // Average of end-to-end transforms during iterations
        attrs.put("parsexsl", new Long(parsexsl)); // First stylesheet preprocess during iterations
        attrs.put("avgparsexsl", new Long(avgparsexsl / iterations)); // Average of stylesheet preprocess during iterations
        attrs.put("unparsedxml", new Long(unparsedxml)); // First stylesheet process during iterations
        attrs.put("transxml", new Long(transxml / iterations)); // Average of stylesheet process during iterations

		// Additional metrics for data throughput
		File fIn = new File(datalet.inputName);
		long btIn = iterations * fIn.length();
		attrs.put("BytesIn", new Long(btIn));

		// Due to unknown reasons the output needs to be filtered through a FileInputStream to get it's size.
		File fOut = new File(datalet.outputName);
		FileInputStream fOutStrm = new FileInputStream(fOut);

		int len = fOutStrm.available();
		long btOut = iterations * fOut.length();
		attrs.put("BytesOut", new Long(btOut));
		fOutStrm.close();

		// I've added additional measurments.  DP calculated KBs as ((Ki+Ko)/2)/sec.
		// I now calculate it with the following (Ki+K0)/sec

		// Calculate TRANSFORM thruput (Kb/sec). Based on DataPower; does NOT file I/O
		double KBtdp = (double)(1000 * (btIn + btOut)) / (double)(1024 * 2 * transxml);
		DecimalFormat fmt = new DecimalFormat("####.##");
		StringBuffer x = new StringBuffer( fmt.format(KBtdp));
		attrs.put("KBtdp", x); 

		// Calculate OVERALL thruput (Kb/sec). Based on DataPower; does include file I/O
		double KBtsdp = (double)(1000 * (btIn + btOut)) / (double)(1024 * 2 * transxmloutput);
		//DecimalFormat fmt = new DecimalFormat("####.##");
		x = new StringBuffer(fmt.format(KBtsdp));
		attrs.put("KBtsdp", x); 

		// Calculate TRANSFORM thruput (Kb/sec). Based on ped; does NOT file I/O
		double KBtPD = (double)(1000 * (btIn + btOut)) / (double)(1024 * transxml);
		//DecimalFormat fmt = new DecimalFormat("####.##");
		x = new StringBuffer(fmt.format(KBtPD));
		attrs.put("KBtPD", x); 

		// Calculate OVERALL thruput (Kb/sec). Based on ped; does include file I/O
		double KBtsPD = (double)(1000 * (btIn + btOut)) / (double)(1024 * transxmloutput);
		//DecimalFormat fmt = new DecimalFormat("####.##");
		x = new StringBuffer(fmt.format(KBtsPD));
		attrs.put("KBtsPD", x); 

		logger.logElement(Logger.STATUSMSG, "perf", attrs, fIn.getName());
    }

    /**
     * Worker method: optionally reports Runtime.totalMemory/freeMemory; 
     * optionally first calls .gc() to force garbage collection.  
     * @param doGC: call .gc() or not first
     * @param doLog: log out memory stats or not
     */
    protected void logMemory(boolean doGC, boolean doLog)
    {
        if (doGC)
        {
            Runtime.getRuntime().gc();
			//System.out.print(".");
        }
        if (doLog)
        {
            logger.logStatistic(Logger.STATUSMSG, Runtime.getRuntime().freeMemory(), 0, "UMem;freeMemory");
            logger.logStatistic(Logger.STATUSMSG, Runtime.getRuntime().totalMemory(), 0, "UMem;totalMemory");
        }
    }

    /**
     * Worker method: aggregate timing arrays and keep counters.  
     * @param newTimes new timing data to add to storedTimes
     * @param storedTimes incremented from newTimes
     * @param countTimes number of time slots actually incremented; 
     * i.e. TIME_UNUSED ones are not incremented
     */
    protected void aggregateTimes(long[] newTimes, long[] storedTimes, long[] countTimes)
    {
        // Note assumption in this class that all are same len
        for (int i = 0; i < storedTimes.length; i++)
        {
            // Only aggregate items that have actual timing data
            if (TransformWrapper.TIME_UNUSED != newTimes[i])
            {
                // Be sure to start from 0 if we haven't been hit before
                if (TransformWrapper.TIME_UNUSED == storedTimes[i])
                {
                    // Start counting the time data for this slot
                    storedTimes[i] = newTimes[i];
                    // Start counter for this slot
                    countTimes[i] = 1;
                }
                else
                {
                    // Aggregate the time data for this slot
                    storedTimes[i] += newTimes[i];
                    // Increment counter for this slot
                    countTimes[i]++;
                }
            }
        }
    }

    /**
     * Worker method: log aggregate timing arrays and keep counters.  
     * @param storedTimes to log out
     * @param countTimes number of time slots actually incremented
     */
    protected void logAggregateTimes(Hashtable attrs, long[] storedTimes, long[] countTimes)
    {
        for (int i = 0; i < storedTimes.length; i++)
        {
            // Only log items that have actual timing data
            if (TransformWrapper.TIME_UNUSED != storedTimes[i])
            {
                attrs.put(TransformWrapperHelper.getTimeArrayDesc(i), 
                          new Long(storedTimes[i] / countTimes[i]));
            }
        }
    }
}  // end of class PerfTestlet

