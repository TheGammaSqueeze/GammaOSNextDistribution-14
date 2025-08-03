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
 * ThreadedTestletDriver.java
 *
 */
package org.apache.qetest.xsl;

import java.util.Properties;
import java.util.Vector;

import org.apache.qetest.Logger;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.Reporter;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

//-------------------------------------------------------------------------

/**
 * Test driver for XSLT stylesheet Testlets.
 * 
 * This is a specific driver for XSLT-oriented Testlets, testing 
 * them in an explicitly threaded model.  Currently, this class is 
 * tightly bound to ThreadedStylesheetTestlet/Datalet.
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ThreadedTestletDriver extends StylesheetTestletDriver
{


    /** Just initialize test name, comment; numTestCases is not used. */
    public ThreadedTestletDriver()
    {
        testName = "ThreadedTestletDriver";
        testComment = "Threaded test driver for XSLT stylesheet Testlets";
    }


    /**
     * Do the default: test all stylesheets found in subdirs
     * of our inputDir, using FilenameFilters for dirs and files.
     * This only goes down one level in the tree, eg:
     * <ul>inputDir = tests/conf
     * <li>tests/conf - not tested</li>
     * <li>tests/conf/boolean - test all boolean*.xsl files</li>
     * <li>tests/conf/copy - test all copy*.xsl files</li>
     * <li>tests/conf/copy/foo - not tested</li>
     * <li>tests/conf/xmanual - not tested, since default 
     * ConformanceDirRules excludes dirs starting with 'x|X'</li>
     * <li>tests/whitespace - test all whitespace*.xsl files</li>
     * <li>etc.</li>
     * </ul>
     * Parameters: none, uses our internal members inputDir, 
     * outputDir, testlet, etc.
     */
    public void processInputDir()
    {
        // Implement this later!
        reporter.checkErr("processInputDir not yet implemented use -fileList list.txt instead!");
    }


    /**
     * Run a list of stylesheet tests through a Testlet.
     * The file names are assumed to be fully specified, and we assume
     * the corresponding directories exist.
     * Each fileList is turned into a testcase.
     * The first file in the list is used as a common Template that 
     * is passed to each ThreadedStylesheetTestlet that is created 
     * from every other item in the file list.
     * i.e. the first file is used commonly throughout every 
     * testlet.  For every other file in the list, a new Thread is 
     * created that will perform processes on both the shared 
     * Templates from the first file and from this listed file.
     *
     * @param vector of Datalet objects to pass in
     * @param desc String to use as testCase description
     */
    public void processFileList(Vector datalets, String desc)
    {
        // Validate arguments - must have at least two files to test
        if ((null == datalets) || (datalets.size() < 2))
        {
            // Bad arguments, report it as an error
            // Note: normally, this should never happen, since 
            //  this class normally validates these arguments 
            //  before calling us
            reporter.checkErr("Testlet or datalets are null/less than 2, nothing to test!");
            return;
        }

        // Put everything else into a testCase
        //  This is not necessary, but feels a lot nicer to 
        //  break up large test sets
        reporter.testCaseInit(desc);

        // Now just go through the list and process each set
        int numDatalets = datalets.size();
        reporter.logInfoMsg("processFileList() with " + numDatalets
                            + " potential tests");

        // Take the first Datalet and create a Templates from it; 
        //  this is going to be shared by all other threads/testlets
        StylesheetDatalet firstDatalet = (StylesheetDatalet)datalets.elementAt(0);
        TransformWrapper transformWrapper = null;        
        // Create a TransformWrapper of appropriate flavor
        try
        {
            transformWrapper = TransformWrapperFactory.newWrapper(firstDatalet.flavor);
            transformWrapper.newProcessor(null);
            reporter.logMsg(Logger.INFOMSG, "Created transformWrapper, about to process shared: " + firstDatalet.inputName);
            transformWrapper.buildStylesheet(firstDatalet.inputName);
        }
        catch (Throwable t)
        {
            reporter.logThrowable(Logger.ERRORMSG, t, "Creating transformWrapper: newWrapper/newProcessor threw");
            reporter.checkErr("Creating transformWrapper: newWrapper/newProcessor threw: " + t.toString());
            return;
        }
        
        // Create a ThreadedStylesheetDatalet for shared use        
        ThreadedStylesheetDatalet sharedDatalet = new ThreadedStylesheetDatalet();
        // Copy all other info over
        sharedDatalet.inputName = firstDatalet.inputName;
        sharedDatalet.outputName = firstDatalet.outputName;
        sharedDatalet.goldName = firstDatalet.goldName;
        sharedDatalet.transformWrapper = transformWrapper;
        sharedDatalet.setDescription(firstDatalet.getDescription());
       
        
        // Prepare array to store all datalets for later joining
        //  Note: store all but first datalet, which is used as 
        //  the common shared stylesheet/Templates
        ThreadedTestletInfo[] testletThreads = new ThreadedTestletInfo[numDatalets - 1];
        
        // Iterate over every OTHER datalet and test it
        for (int ctr = 1; ctr < numDatalets; ctr++)
        {
            try
            {
                // Create ThreadedStylesheetDatalets from the common 
                //  one we already have and each of the normal 
                //  StylesheetDatalets that we were handed
                ThreadedStylesheetTestlet testlet = getTestlet(ctr);
                testlet.sharedDatalet = sharedDatalet;
                testlet.setDefaultDatalet((StylesheetDatalet)datalets.elementAt(ctr));
                testlet.threadIdentifier = ctr;
                // Save a copy of each datalet for later joining
                //  Note off-by-one necessary for arrays
                testletThreads[ctr - 1] = new ThreadedTestletInfo(testlet, new Thread(testlet));
                //@todo (optional) start this testlet - should allow 
                //  user to start sequentially or all at once later
                ((testletThreads[ctr - 1]).thread).start();
                reporter.logMsg(Logger.INFOMSG, "Started testlet(" + ctr + ")");
                // Continue looping and creating each Testlet
            } 
            catch (Throwable t)
            {
                // Log any exceptions as fails and keep going
                //@todo improve the below to output more useful info
                reporter.checkFail("Datalet num " + ctr + " threw: " + t.toString());
                reporter.logThrowable(Logger.ERRORMSG, t, "Datalet threw");
            }
        }  // of while...
        
        // We now wait for every thread to finish, and only then 
        //  will we write a final report and finish the test
        //@todo probably an easier way; for now, just join the last one
        reporter.logMsg(Logger.STATUSMSG, "Driver Attempting-to-Join last thread");
        long maxWaitMillis = 100000; // Wait at most xxxx milliseconds
        // Try waiting for the last thread several times
        testletThreads[testletThreads.length - 1].waitForComplete
                (reporter, maxWaitMillis, 10);
        reporter.logMsg(Logger.TRACEMSG, "Driver Apparently-Joined last thread");

        // Also join all other threads
        for (int i = 0; i < (testletThreads.length - 1); i++)
        {
            // Only wait a little while for these
            testletThreads[i].waitForComplete(reporter, maxWaitMillis, 2);
        }
        reporter.logMsg(Logger.INFOMSG, "Driver Apparently-joined all threads");
        
        // Log results from all threads
        for (int i = 0; i < testletThreads.length; i++)
        {
            switch (testletThreads[i].result)
            {
                case Logger.PASS_RESULT:
                    if (testletThreads[i].complete)
                    {
                        reporter.checkPass("Thread(" + i + ") " + testletThreads[i].lastStatus);
                    }
                    else
                    {
                        reporter.checkPass("Thread(" + i + ") " + testletThreads[i].lastStatus);
                        //@todo What kind of status do we do here?
                        // In theory the testlet successfully ran 
                        //  transforms and checked results, but 
                        //  for some reason we don't think the 
                        //  thread completed properly/in time
                        reporter.checkErr("Thread(" + i + ") NOT COMPLETE! " + testletThreads[i].lastStatus);
                    }
                    break;
                case Logger.FAIL_RESULT:
                    reporter.checkFail("Thread(" + i + ") " + testletThreads[i].lastStatus);
                    break;
                case Logger.AMBG_RESULT:
                    reporter.checkAmbiguous("Thread(" + i + ") " + testletThreads[i].lastStatus);
                    break;
                case Logger.ERRR_RESULT:
                    reporter.checkErr("Thread(" + i + ") " + testletThreads[i].lastStatus);
                    break;
                case Logger.INCP_RESULT:
                    reporter.checkErr("Thread(" + i + ") INCP! " + testletThreads[i].lastStatus);
                    break;
                default:
                    reporter.checkErr("Thread(" + i + ") BAD RESULT! " + testletThreads[i].lastStatus);
                    break;
            }
            //@todo optimizaion: null out vars for gc?
            //   or should we do this earlier?
            testletThreads[i].thread = null;
            testletThreads[i].testlet = null;
        }
        reporter.testCaseClose();
    }


    /**
     * Convenience method to get a Testlet to use.  
     * Attempts to return one as specified by our testlet parameter, 
     * otherwise returns a default ThreadedStylesheetTestlet.
     * Overrides StylesheetTestletDriver to use a separate logger 
     * for every testlet, since currently loggers may not be threadsafe.
     * Note: We actually cheat and pass a Reporter to each Testlet, 
     * since they need to keep their own pass/fail state.
     * 
     * @return Testlet for use in this test; null if error
     */
    public ThreadedStylesheetTestlet getTestlet(int ctr)
    {
        // Find a Testlet class to use if we haven't already
        if (null == cachedTestletClazz)
        {
            cachedTestletClazz = QetestUtils.testClassForName(testlet, 
                                                              QetestUtils.defaultPackages,
                                                              defaultTestlet);
        }
        try
        {
            // Create it and set a new logger into it
            ThreadedStylesheetTestlet t = (ThreadedStylesheetTestlet)cachedTestletClazz.newInstance();
            //@todo Note assumption we have a file name to log to!
            //  This is too tightly bound to the file-based Loggers
            //  this design should be cleaned up so we don't know 
            //  what/where/how the loggers are outputing stuff, and 
            //  so each testlet can automatically get a new logger 
            //  based off of our logger
            String testletLogFile = testProps.getProperty(Logger.OPT_LOGFILE, "threadedTestlet");
            int idx = testletLogFile.lastIndexOf("."); // Assumption: there'll be a .extension
            testletLogFile = testletLogFile.substring(0, idx) + ctr + testletLogFile.substring(idx);
            Properties testletLoggerProperties = new Properties(testProps);
            testletLoggerProperties.put(Logger.OPT_LOGFILE, testletLogFile);
            t.setLogger(new Reporter(testletLoggerProperties));
            return t;
        }
        catch (Exception e)
        {
            // Ooops, none found! This should be very rare, since 
            //  we know the defaultTestlet should be found
            return null;
        }
    }


    /** 
     * Local class to store info about threads we spawn.  
     * A simple little data holding class that stores info about 
     * various Threads we spawn (presumably each 
     * ThreadedStylesheetTestlets or the like).
     * <ul>
     * <li>ThreadedStylesheetTestlet</li>
     * <li>Thread - the actual thread started</li>
     * <li>lastStatus - copy of testlet.getDescription, 
     * which the testlet changes to reflect it's current state</li>
     * <li>result - copy of testlet.getResult</li>
     * <li>complete - convenience variable, if we think 
     * the testlet is done (or if we think it's ready to be 
     * reported on; may force this to true if we timeout or think 
     * the testlet/thread has hung, etc.)</li>
     * </ul>
     */
    class ThreadedTestletInfo
    {
        ThreadedStylesheetTestlet testlet = null;
        Thread thread = null;
        String lastStatus = Logger.INCP;
        int result = Logger.INCP_RESULT;
        boolean complete = false;
        ThreadedTestletInfo(ThreadedStylesheetTestlet tst, 
                            Thread t)
        {
            testlet = tst;
            thread = t;
            // Should help ease debugging
            thread.setName(((StylesheetDatalet)testlet.getDefaultDatalet()).inputName);
        }
        
        /** 
         * Attempt to wait for this thread to complete.
         * Essentially does a .join on our thread, waiting 
         * millisWait.  If this doesn't work, it logs a message 
         * and then tries again waitNumTimes.  If the thread is 
         * still not done, then log a message and return anyway.
         * //@todo should we kill the thread at this point?
         */
        void waitForComplete(Logger l, long millisWait, int waitNumTimes)
        {
            // If we think we're already done, just return
            //@todo ensure coordination between this and actual 
            //  Thread state; or remove this
            if (complete)
                return;
                
            try
            {
                thread.join(millisWait);
            }
            catch (InterruptedException ie)
            {
                l.logMsg(Logger.WARNINGMSG, "waitForComplete threw: " + ie.toString());
            }    
            if (!thread.isAlive())
            {
                // If the Thread is already done, then copy over 
                //  each value and return
                complete = true;
                lastStatus = testlet.getDescription();
                result = testlet.getResult();
                return;
            }
            // Otherwise, keep waiting for the thread
            for (int i = 0; i < waitNumTimes; i++)
            {
                l.logMsg(Logger.TRACEMSG, "waitForComplete(" + i + ") of "
                         + thread.getName());
                try
                {
                    thread.join(millisWait);
                }
                catch (InterruptedException ie)
                {
                    l.logMsg(Logger.WARNINGMSG, "waitForComplete(" + i + ") threw: " + ie.toString());
                }    
            }
            if (!thread.isAlive())
            {
                // If the Thread is already done, then set complete 
                //  (which means the thread did finish normally)
                complete = true;
            }
            // Copy over the rest of the testlet/thread's status
            lastStatus = testlet.getDescription();
            result = testlet.getResult();
            return;
        }
    } // end of inner class ThreadedTestletInfo


    /**
     * Convenience method to print out usage information - update if needed.  
     * //@todo update this for iteration, etc.
      * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by ThreadedTestletDriver:\n"
                + "    -" + OPT_FILELIST
                + "  <name of listfile of tests to run>\n"
                + "    -" + OPT_DIRFILTER
                + "  <classname of FilenameFilter for dirs>\n"
                + "    -" + OPT_FILEFILTER
                + "  <classname of FilenameFilter for files>\n"
                + "    -" + OPT_TESTLET
                + "  <classname of Testlet to execute tests with>\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        ThreadedTestletDriver app = new ThreadedTestletDriver();
        app.doMain(args);
    }
}
