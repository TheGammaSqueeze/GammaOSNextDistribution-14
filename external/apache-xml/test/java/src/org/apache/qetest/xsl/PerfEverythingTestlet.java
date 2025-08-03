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

/*
 *
 * PerfEverythingTestlet.java
 *
 */
package org.apache.qetest.xsl;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import org.apache.qetest.CheckService;
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.TestletImpl;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

/**
 * Testlet to capture specific timing performance data.
 * This Testlet attempts to mirror what one of our Xalan-C 
 * performance tests does, at least as well as we can compare 
 * Java processing to the C processing model.
 * Note that as a standalone Testlet, this class does waaaay too 
 * much stuff: Testlets should really be smaller test algorithims
 * than here.  However for performance measurements and reporting 
 * purposes, we really need to do it all here.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class PerfEverythingTestlet extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.PerfEverythingTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String describing what this PerfEverythingTestlet does.
     */
    public String getDescription()
    {
        return "PerfEverythingTestlet: processes supplied file in multiple ways over multiple iterations and logs timing data";
    }


    /**
     * Run this PerfEverythingTestlet: execute it's test and return.
     * This algorithim processes the supplied file in several 
     * different ways:
     * <ul>
     * <li>Run one full end-to-end transform and log timing 
     * (a 'preload' process: note that results may vary for this 
     * timing depending on whether you run this Testlet standalone 
     * or if you're using StylesheetTestletDriver to run it, due at 
     * least to classloading issues, if nothing else)</li>
     * <li>Preprocess the stylesheet once and log timing</li>
     * <li>Process that preprocessed stylesheet once and log timing</li>
     * <li>Loop iterations times and preprocess the stylesheet, 
     * then do a transform with it, and log each time</li>
     * <li>Loop iterations times and do a full end-to-end transform
     * and log each time</li>
     * </ul>
     * We (optionally) call Runtime.gc() after every use of the 
     * processor, and log out memory statistics here and there.
     *
     * Note that if any error happens during the execution, we 
     * simply log the error and return: in this case, a &lt;perf&gt;
     * element will <b>not</b> be output at all.
     *
     * @param Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        StylesheetDatalet datalet = null;
        try
        {
            datalet = (StylesheetDatalet)d;
            
        }
        catch (ClassCastException e)
        {
            logger.checkErr("Incorrect Datalet type provided, threw:" + e.toString());
            return;
        }
        logger.logMsg(Logger.STATUSMSG, "About to test: " 
                      + (null == datalet.inputName
                         ? datalet.xmlName
                         : datalet.inputName));
        
        // Cleanup outName only if asked to - delete the file on disk
        if ("true".equalsIgnoreCase(datalet.options.getProperty("deleteOutFile")))
        {
            try
            {
                File outFile = new File(datalet.outputName);
                boolean btmp = outFile.delete();
                logger.logMsg(Logger.TRACEMSG, "Deleting OutFile of::" + datalet.outputName
                                     + " status: " + btmp);
            }
            catch (SecurityException se)
            {
                logger.logMsg(Logger.WARNINGMSG, "Deleting OutFile of::" + datalet.outputName
                                       + " threw: " + se.toString());
                // But continue anyways...
            }
        }

        // Setup: Save options from the datalet in convenience variables
        int iterations = 10;
        boolean preload = true;
        boolean runtimeGC = false;
        try
        {
            iterations = Integer.parseInt(datalet.options.getProperty("iterations"));
        }
        catch (Exception e) { /* no-op, leave as default */ }
        try
        {
            preload = (new Boolean(datalet.options.getProperty("preload"))).booleanValue();
        }
        catch (Exception e) { /* no-op, leave as default */ }
        try
        {
            runtimeGC = (new Boolean(datalet.options.getProperty("runtimeGC"))).booleanValue();
        }
        catch (Exception e) { /* no-op, leave as default */ }

        // Setup: store various timing data in convenience variables
        long singletransform = 0L;  // Very first Preload end-to-end transform
        long etoe = 0L;     // First end-to-end transform during iterations
        long avgetoe = 0L;  // Average of end-to-end transforms during iterations
        long parsexsl = 0L;     // First stylesheet preprocess during iterations
        long avgparsexsl = 0L;  // Average of stylesheet preprocess during iterations
        long unparsedxml = 0L;   // First stylesheet process during iterations
        long avgunparsedxml = 0L;// Average of stylesheet process during iterations

        // Create a new TransformWrapper of appropriate flavor
        //  null arg is unused liaison for TransformWrapper
        //@todo allow user to pass in pre-created 
        //  TransformWrapper so we don't have lots of objects 
        //  created and destroyed for every file
        TransformWrapper transformWrapper = null;
        try
        {
            transformWrapper = TransformWrapperFactory.newWrapper(datalet.flavor);
            transformWrapper.newProcessor(null);
        }
        catch (Throwable t)
        {
            logThrowable(t, getDescription() + " newWrapper/newProcessor threw");
            logger.checkErr(getDescription() + " newWrapper/newProcessor threw: " + t.toString());
            return;
        }

        // Test our supplied file in multiple ways, logging performance data
        try
        {
            // Store local copies of XSL, XML references to avoid 
            //  potential for changing datalet            
            String inputName = datalet.inputName;
            String xmlName = datalet.xmlName;

            logger.logMsg(Logger.TRACEMSG, "executing with: inputName=" + inputName
                          + " xmlName=" + xmlName + " outputName=" + datalet.outputName
                          + " goldName=" + datalet.goldName + " flavor="  + datalet.flavor
                          + " iterations=" + iterations + " preload=" + preload
                          + " algorithim=" + getDescription());

            //@todo make various logMemory calls optional
            logMemory(runtimeGC, true);

            // Measure(singletransform): Very first Preload end-to-end transform
            long[] times = null;
            times = transformWrapper.transform(xmlName, inputName,
                                                datalet.outputName);
            singletransform = times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, false);

            // Measure(parsexsl): once: first preprocess
            times = transformWrapper.buildStylesheet(inputName);
            parsexsl = times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, false);

            // Measure(unparsedxml): once: first process
            times = transformWrapper.transformWithStylesheet(xmlName, datalet.outputName);
            unparsedxml = times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, false);

            for (int ctr = 1; ctr <= iterations; ctr++)
            {
                // Measure(avgparsexsl): average preprocess
                times = transformWrapper.buildStylesheet(inputName);
                avgparsexsl += times[TransformWrapper.IDX_OVERALL];
                logMemory(runtimeGC, false);

                // Measure(avgunparsedxml): average process
                times = transformWrapper.transformWithStylesheet(xmlName, datalet.outputName);
                avgunparsedxml += times[TransformWrapper.IDX_OVERALL];
                logMemory(runtimeGC, false);
            }

            // Measure(etoe): once: first full process
            times = transformWrapper.transform(xmlName, inputName, datalet.outputName);
            etoe = times[TransformWrapper.IDX_OVERALL];
            logMemory(runtimeGC, true);

            for (int ctr = 1; ctr <= iterations; ctr++)
            {
                // Measure(avgetoe): average full process
                times = transformWrapper.transform(xmlName, inputName, datalet.outputName);
                avgetoe += times[TransformWrapper.IDX_OVERALL];
                logMemory(runtimeGC, false);
            }

            // Log special performance element with our timing
            Hashtable attrs = new Hashtable();
            // UniqRunid is an Id that our TestDriver normally sets 
            //  with some unique code, so that results analysis 
            //  stylesheets can compare different test runs
            attrs.put("UniqRunid", datalet.options.getProperty("runId", "runId;none"));
            // processor is the 'flavor' of processor we're testing
            attrs.put("processor", transformWrapper.getDescription());
            // idref is the individual filename
            attrs.put("idref", (new File(datalet.inputName)).getName());
            // inputName is the actual name we gave to the processor
            attrs.put("inputName", inputName);
            attrs.put("iterations", new Integer(iterations));
            attrs.put("singletransform", new Long(singletransform)); // Very first Preload end-to-end transform
            attrs.put("etoe", new Long(etoe)); // First end-to-end transform during iterations
            attrs.put("avgetoe", new Long(avgetoe / iterations)); // Average of end-to-end transforms during iterations
            attrs.put("parsexsl", new Long(parsexsl)); // First stylesheet preprocess during iterations
            attrs.put("avgparsexsl", new Long(avgparsexsl / iterations)); // Average of stylesheet preprocess during iterations
            attrs.put("unparsedxml", new Long(unparsedxml)); // First stylesheet process during iterations
            attrs.put("avgunparsedxml", new Long(avgunparsedxml / iterations)); // Average of stylesheet process during iterations

            logger.logElement(Logger.STATUSMSG, "perf", attrs, "PItr;");

            // If we get here, attempt to validate the contents of 
            //  the last outputFile created
            CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
            // Supply default value
            if (null == fileChecker)
                fileChecker = new XHTFileCheckService();
            if (Logger.PASS_RESULT
                != fileChecker.check(logger,
                                     new File(datalet.outputName), 
                                     new File(datalet.goldName), 
                                     getDescription() + ", " + datalet.getDescription())
               )
                logger.logMsg(Logger.WARNINGMSG, "Failure reason: " + fileChecker.getExtendedInfo());
        }
        // try...catch around entire performance operation
        catch (Throwable t)
        {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            logger.logArbitrary(Logger.ERRORMSG, sw.toString());
            logger.checkErr("PerfEverythingTestlet with:" + datalet.inputName
                                 + " threw: " + t.toString());
            return;
        }

        //@todo Should we attempt to cleanup anything else here?
        //  We've had problems running this testlet over large 
        //  files, so I'm looking for ways to reduce the impact 
        //  this test code has on the JVM
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
        }
        if (doLog)
        {
            logger.logStatistic(Logger.STATUSMSG, Runtime.getRuntime().freeMemory(), 0, "UMem;freeMemory");
            logger.logStatistic(Logger.STATUSMSG, Runtime.getRuntime().totalMemory(), 0, "UMem;totalMemory");
        }
    }


    /**
     * Logs out throwable.toString() and stack trace to our Logger.
     * //@todo Copied from Reporter; should probably be moved into Logger.
     * @param throwable thrown throwable/exception to log out.
     * @param msg description of the throwable.
     */
    protected void logThrowable(Throwable throwable, String msg)
    {
        StringWriter sWriter = new StringWriter();
        sWriter.write(msg + "\n");

        PrintWriter pWriter = new PrintWriter(sWriter);
        throwable.printStackTrace(pWriter);

        logger.logArbitrary(Logger.STATUSMSG, sWriter.toString());
    }
}  // end of class PerfEverythingTestlet

