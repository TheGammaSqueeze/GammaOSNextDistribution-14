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
 * ThreadedStylesheetTestlet.java
 *
 */
package org.apache.qetest.xsl;

import java.io.File;

import org.apache.qetest.CheckService;
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.TestletImpl;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

/**
 * Testlet for basic thread testing of xsl stylesheet files.
 *
 * This class provides a simple testing algorithim for verifying 
 * that Xalan functions properly when run on multiple threads 
 * simultaneously.  Currently it simply does most of what the 
 * normal StylesheetTestlet does, except it does it in the run()
 * method on a thread - thus you'd commonly use this with 
 * ThreadedTestletDriver to start multiple of these testlets 
 * up at the same time.
 * We implement Runnable, so classically a test driver would 
 * create us then pass us to new Thread(us).start(), instead 
 * of calling execute(). @todo find a better way to integrate!
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class ThreadedStylesheetTestlet 
        extends TestletImpl 
        implements Runnable
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.ThreadedStylesheetTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }
    
    /** Accessor for our Datalet instead of calling execute().  */
    public void setDefaultDatalet(StylesheetDatalet d)
    {
        defaultDatalet = d;
    }
    
    /* Special ThreadedStylesheetDatalet with a Templates.  */
    public ThreadedStylesheetDatalet sharedDatalet = new ThreadedStylesheetDatalet();

    /* Description of our current state; changes during our lifecycle.  */
    protected String description = "ThreadedStylesheetTestlet - before execute()";

    /**
     * Accesor method for a brief description of this test.  
     * When created, this returns a description of this testlet.
     * After you've called execute(), this returns a brief 
     * description of our current result or status.
     *
     * @return String describing what this ThreadedStylesheetTestlet does.
     * @see #getResult()
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Accesor method for a brief description of this test.  
     * Automatically adds our identifier at the start.
     *
     * @param d String to set as our current description.
     */
    protected void setDescription(String d)
    {
        description = "[" + threadIdentifier + "]" + d;
    }

    /* Our 'final' test result; actually changes during our lifecycle.  */
    protected int result = Logger.DEFAULT_RESULT;

    /**
     * Accesor method for the final result of this test.  
     * Note: this starts as INCP_RESULT, and given that we're 
     * threaded, may end up as INCP_RESULT and you may not know 
     * the difference.  Could use more thought.
     *
     * @return int one of of Logger.*_RESULT.
     */
    public int getResult()
    {
        return result;
    }

    /* Cheap-o counter: so driver can differentiate each thread.  */
    public int threadIdentifier = 0;

    /**
     * Run this ThreadedStylesheetTestlet: start this test as 
     * a thread and return immediately.
     * Note that you must join() this thread later if you want 
     * to wait until we're done.
     * //@todo improve docs on how to communicate between threads
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
            logger.checkErr("Datalet provided is not a StylesheetDatalet; cannot continue with " + d);
            return;
        }

        logger.logMsg(Logger.STATUSMSG, "About to test: " 
                      + (null == datalet.inputName
                         ? datalet.xmlName
                         : datalet.inputName)
                      + " plus " + sharedDatalet.xmlName);
        
        // All the rest of the test is executed in our thread.
        //if (true) //@todo check defaultDatalet.options...
        //    this.start();
        //@todo Um, how do we do this? Or do we just ask caller to do it?
        logger.logMsg(Logger.CRITICALMSG, "//@todo execute() is not yet implemented - you must start our thread yourself");

        // Return to caller; they must join() us later if they 
        //  want to know when we're actually complete
        return;
    }

    /** 
     * Called by execute() to perform the looping and actual test. 
     * Note: You must have set our defaultDatalet first!
     */
    public void run()
    {
        // Relies on defaultDatalet being set!
        logger.logMsg(Logger.STATUSMSG, "Beginning thread shared output into: " 
                      + ((StylesheetDatalet)defaultDatalet).outputName);
        // Also set our description so outside users know what 
        // point in our Thread lifetime we're at
        setDescription("ThreadedStylesheetTestlet.run() just started...");

        StylesheetDatalet datalet = null;
        try
        {
            datalet = (StylesheetDatalet)defaultDatalet;
        }
        catch (ClassCastException e)
        {
            setDescription("Datalet provided is not a StylesheetDatalet; cannot continue with " + datalet);
            logger.checkErr(description);
            return;
        }
        //@todo validate our Datalet - ensure it has valid 
        //  and/or existing files available.

        // Cleanup outName(s) only if asked to - delete the file on disk
        // Optimization: this takes extra time and often is not 
        //  needed, so only do this if the option is set
        if ("true".equalsIgnoreCase(datalet.options.getProperty("deleteOutFile")))
        {
            try
            {
                boolean btmp = (new File(datalet.outputName)).delete();
                logger.logMsg(Logger.TRACEMSG, "Deleting OutFile of::" + datalet.outputName
                                     + " status: " + btmp);
            }
            catch (SecurityException se)
            {
                logger.logMsg(Logger.WARNINGMSG, "Deleting OutFile of::" + datalet.outputName
                                       + " threw: " + se.toString());
                // But continue anyways...
            }
            //@todo make sure all sharedDatalets use different 
            //  output files! No sense in having them use 
            //  the same file all the time in all threads!
        }

        // Ask our independent datalet how many iterations to use 
        int iterations = sharedDatalet.iterations; // default value
        try
        {
            iterations = Integer.parseInt(datalet.options.getProperty("iterations"));
        }
        catch (NumberFormatException numEx)
        {
            // no-op; leave as default
        }
        
        // Now loop a number of times as specified, and for each 
        //  loop, use the presupplied Templates object, then run 
        //  one independent process, plus validate on first & last
        setDescription("...about to iterate... " + datalet.outputName);
        for (int ctr = 1; (ctr <= iterations); ctr++)
        {
            // Only validate on first and last iteration
            boolean doValidation = ((1 == ctr) || (iterations == ctr));
            logger.logMsg(Logger.TRACEMSG, "About to do iteration " + ctr);
            // Note: logic moved to worker methods for clarity; 
            //  these methods just use our local vars and datalet
            //@todo Note: while I've tried to mirror as much 
            //  structure from StylesheetTestlet as possible, 
            //  this has made this class very inefficent (note 
            //  we iterate, and within each method do the same 
            //  name munging and some basic setup in each worker 
            //  method every time!)
            // Long-term, we should just redesign this to be 
            //  a custom class with it's own algorithim
            processExistingTemplates(sharedDatalet, doValidation);
            processNewStylesheet(datalet, doValidation);
            setDescription("...done iteration # " + ctr);
        }
        // That's it! We're done!
        logger.logMsg(Logger.STATUSMSG, "Completed thread with: " + datalet.getDescription());
        setDescription("All iterations complete! " + datalet.outputName);
        // Also set our result, now that we think we're done
        try
        {
            result = ((org.apache.qetest.Reporter)logger).getCurrentCaseResult();
        }
        catch (ClassCastException cce)
        {
            // Basically a no-op; just log out for info
            logger.logMsg(Logger.WARNINGMSG, "logger is not a Reporter; overall result may be incorrect!");
        }
	}


    /** 
     * Worker method to process premade Templates object.  
     * Uses various local variables.
     * //@todo Should we simply propagate exceptions instead of just logging them?
     */
    private void processExistingTemplates(ThreadedStylesheetDatalet datalet, boolean doValidation)
    {
        // First: use our (presumably) shared Templates object to 
        // perform a Transformation - using the existing 
        // TransformWrapper object that already has built a stylesheet
        if (!datalet.transformWrapper.isStylesheetReady())
        {
            // Can't continue if the Templates is not ready
            logger.logMsg(Logger.WARNINGMSG, "datalet shared Templates isStylesheetReady false!");
            // Anything else we should log out here?  In case someone 
            //  care about this, don't have it fail
            return;
        }
        
        // Since the wrapper's ready (and flavor, etc. setup) then 
        //  just go ahead and ask it to transform
        try
        {
            String outputName = datalet.outputName + threadIdentifier;

            //@todo Should we log a custom logElement here instead?
            logger.logMsg(Logger.TRACEMSG, "About to test shared Templates: "
                          + " xmlName=" + datalet.xmlName 
                          + " outputName=" + outputName
                          + " goldName=" + datalet.goldName);

            // Simply have the wrapper do all the transforming
            //  or processing for us - we handle either normal .xsl 
            //  stylesheet tests or just .xml embedded tests
            long retVal = 0L;
            // Here, we only use the existing Templates to do the transform
            long[] times = datalet.transformWrapper.transformWithStylesheet(datalet.xmlName, outputName);
            retVal = times[TransformWrapper.IDX_OVERALL];

            if (!doValidation)
            {
                logger.logMsg(Logger.TRACEMSG, "Skipping validation of outputName=" + outputName);
                // Only bother to validate the output if asked
                return;
            }
            // If we get here, attempt to validate the contents of 
            //  the last outputFile created - only first and last time through loop!
            CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
            // Supply default value
            if (null == fileChecker)
                fileChecker = new XHTFileCheckService();
            fileChecker.check(logger,
                              new File(outputName), 
                              new File(datalet.goldName), 
                              "Shared Templates of: " + datalet.getDescription());
        }
        // Note that this class can only validate positive test 
        //  cases - we don't handle ExpectedExceptions
        catch (Throwable t)
        {
            // Put the logThrowable first, so it appears before 
            //  the Fail record, and gets color-coded
            logger.logThrowable(Logger.ERRORMSG, t, "Shared Templates of: " + datalet.getDescription());
            logger.checkFail("Shared Templates of: " + datalet.getDescription() 
                             + " threw: " + t.toString());
        }
    }
    /** 
     * Worker method to process a new stylesheet/xml document.  
     * Uses various local variables.
     * Note this is essentially a copy of StylesheetTestlet.execute().
     * //@todo Should we simply propagate exceptions instead of just logging them?
     */
    private void processNewStylesheet(StylesheetDatalet datalet, boolean doValidation)
    {
        // Test our supplied input file, and compare with gold
        try
        {
            String outputName =  datalet.outputName + threadIdentifier;

            //@todo Should we log a custom logElement here instead?
            logger.logMsg(Logger.TRACEMSG, "About to test: inputName=" + datalet.inputName
                          + " xmlName=" + datalet.xmlName + " outputName=" + outputName
                          + " goldName=" + datalet.goldName + " flavor="  + datalet.flavor);

            // Create a new TransformWrapper of appropriate flavor
            //  null arg is unused liaison for TransformWrapper
            TransformWrapper transformWrapper = null;
            try
            {
                transformWrapper = TransformWrapperFactory.newWrapper(datalet.flavor);
                transformWrapper.newProcessor(null);
            }
            catch (Throwable t)
            {
                logger.logThrowable(Logger.ERRORMSG, t, getDescription() + " newWrapper/newProcessor threw");
                logger.checkErr(getDescription() + " newWrapper/newProcessor threw: " + t.toString());
                return;
            }

            // Simply have the wrapper do all the transforming
            //  or processing for us - we handle either normal .xsl 
            //  stylesheet tests or just .xml embedded tests
            long retVal = 0L;
            if (null == datalet.inputName)
            {
                // presume it's an embedded test
                long [] times = transformWrapper.transformEmbedded(datalet.xmlName, outputName);
                retVal = times[TransformWrapper.IDX_OVERALL];
            }
            else
            {
                // presume it's a normal stylesheet test
                long[] times = transformWrapper.transform(datalet.xmlName, datalet.inputName, outputName);
                retVal = times[TransformWrapper.IDX_OVERALL];
            }

            if (!doValidation)
            {
                logger.logMsg(Logger.TRACEMSG, "Skipping validation of outputName=" + outputName);
                // Only bother to validate the output if asked
                return;
            }
            // If we get here, attempt to validate the contents of 
            //  the last outputFile created
            CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
            // Supply default value
            if (null == fileChecker)
                fileChecker = new XHTFileCheckService();
            fileChecker.check(logger,
                              new File(outputName), 
                              new File(datalet.goldName), 
                              getDescription() + " " + datalet.getDescription());
        }
        // Note that this class can only validate positive test 
        //  cases - we don't handle ExpectedExceptions
        catch (Throwable t)
        {
            // Put the logThrowable first, so it appears before 
            //  the Fail record, and gets color-coded
            logger.logThrowable(Logger.ERRORMSG, t, "New stylesheet of: " + datalet.getDescription());
            logger.checkFail("New stylesheet of: " + datalet.getDescription() 
                             + " threw: " + t.toString());
        }
    }

}  // end of class ThreadedStylesheetTestlet

