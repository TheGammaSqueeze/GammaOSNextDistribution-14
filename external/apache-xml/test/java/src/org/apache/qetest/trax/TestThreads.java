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
 * TestThreads.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Properties;

import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
//-------------------------------------------------------------------------

/**
 * Testing multiple simultaneous processors on different threads with TRAX.
 * <p>No validation of output files is currently done!  You must manually
 * inspect any logfiles.  Most options can be passed in with a Properties file.</p>
 * <p>Note: Most automated tests extend XSLProcessorTestBase, and 
 * are named *Test.java.  Since we are semi-manual, we're 
 * named Test*.java instead.</p>
 * We assume Features.STREAM.
 * @author shane_curcuru@lotus.com
 */
public class TestThreads
{

    /**
     * Convenience method to print out usage information.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public static String usage()
    {

        return ("Usage: TestThreads [-load] file.properties :\n"
                + "    where the properties file can set:,\n"
                + "    inputDir=e:\\builds\\xsl-test\n"
                + "    outputDir=e:\\builds\\xsl-test\\results\n"
                + "    logFile=e:\\builds\\xsl-test\\results\\TestThreads.xml\n"
                + "    numRunners=5\n" + "    numRunnerCalls=10\n"
                + "    setOneFile=bool01\n" + "    setTwoFile=expr01\n"
                + "    setThreeFile=numb01\n" + "    paramName=SomeParam\n"
                + "    paramVal=TheValue\n");
    }

    /** NEEDSDOC Field debug          */
    public boolean debug = true;  // for adhoc debugging

    /**
     * Number of sets of worker threads to create and loops per runner.
     * <p>'numRunners=xx', default is 10; 'numRunnerCalls=xx', default is 50.</p>
     */
    protected int numRunners = 10;

    /**
     * Number of sets of worker threads to create and loops per runner.
     * <p>'numRunners=xx', default is 10; 'numRunnerCalls=xx', default is 50.</p>
     */
    protected int numRunnerCalls = 50;

    /**
     * Root input filenames that certain runners should use, in the inputDir.
     * <p>'setOneFile=File'; 'setTwoFile=File'; 'setThreeFile=File'
     * in .prop file to set; default is TestThreads1, TestThreads2, TestThreads3.</p>
     * <p>Files are found in 'inputDir=c:\bar\baz' from .prop file.</p>
     */
    protected String inputDir = null;

    /** NEEDSDOC Field setOneFilenameRoot          */
    protected String setOneFilenameRoot = "TestThreads1";

    /** NEEDSDOC Field setTwoFilenameRoot          */
    protected String setTwoFilenameRoot = "TestThreads2";

    /** NEEDSDOC Field setThreeFilenameRoot          */
    protected String setThreeFilenameRoot = "TestThreads3";

    /**
     * All output logs and files get put in the outputDir.
     */
    protected String outputDir = null;

    /**
     * Sample PARAM name that certain runners should use.
     * <p>Use 'paramName=xx' in .prop file to set, default is test1.</p>
     */
    protected String paramName = "test1";

    /**
     * Sample PARAM value that certain runners should use.
     * <p>Use 'paramVal=xx' in .prop file to set, default is bar.</p>
     */
    protected String paramVal = "bar";

    /**
     * liaisonClassName that just the *second* set of runners should use.
     * <p>Use 'liaison=xx' in .prop file to set, default is null (whatever the processor's default is).</p>
     */
    protected String liaison = null;  // TRAX unused

    // Used to pass info to runners; simpler to update than changing ctors

    /** RunnerID offset in ctor's array initializer.   */
    public static final int ID = 0;

    /** NEEDSDOC Field XMLNAME          */
    public static final int XMLNAME = 1;

    /** NEEDSDOC Field XSLNAME          */
    public static final int XSLNAME = 2;

    /** NEEDSDOC Field OUTNAME          */
    public static final int OUTNAME = 3;

    /** NEEDSDOC Field PARAMNAME          */
    public static final int PARAMNAME = 4;

    /** NEEDSDOC Field PARAMVAL          */
    public static final int PARAMVAL = 5;

    /** NEEDSDOC Field OPTIONS          */
    public static final int OPTIONS = 6;

    /** NEEDSDOC Field LIAISON          */
    public static final int LIAISON = 7;

    /** NEEDSDOC Field FUTUREUSE          */
    public static final int FUTUREUSE = 8;

    /**
     * Name of main file's output logging; each runner also has separate output.
     */
    protected String logFileName = "TestThreads.xml";

    /**
     * Construct multiple threads with processors and run them all.
     * @author Shane Curcuru & Scott Boag
     * <p>Preprocesses some stylesheets, then creates lots of worker threads.</p>
     */
    public void runTest()
    {

        // Prepare a log file and dump out some basic info
        createLogFile(logFileName);
        println("<?xml version=\"1.0\"?>");
        println("<resultsfile logFile=\"" + logFileName + "\">");
        println("<message desc=\"threads=" + (3 * numRunners)
                + " iterations=" + numRunnerCalls + "\"/>");
        println("<message desc=\"oneF=" + setOneFilenameRoot + " twof="
                + setTwoFilenameRoot + " threef=" + setThreeFilenameRoot
                + "\"/>");
        println("<message desc=\"param=" + paramName + " val=" + paramVal
                + " liaison=" + liaison + "\"/>");

        // Preprocess some stylesheets for use by the runners
        String errStr = "Create processor threw: ";
        Templates stylesheet1, stylesheet2, stylesheet3;

        try
        {
            String setOneURL = filenameToURI(inputDir + setOneFilenameRoot + ".xsl");
            String setTwoURL = filenameToURI(inputDir + setTwoFilenameRoot + ".xsl");
            String setThreeURL = filenameToURI(inputDir + setThreeFilenameRoot + ".xsl");

            TransformerFactory factory = TransformerFactory.newInstance();

            errStr = "Processing stylesheet1 threw: ";
            stylesheet1 =
                factory.newTemplates(new StreamSource(setOneURL));
            errStr = "Processing stylesheet2 threw: ";
            stylesheet2 =
                factory.newTemplates(new StreamSource(setTwoURL));
            errStr = "Processing stylesheet3 threw: ";
            stylesheet3 =
                factory.newTemplates(new StreamSource(setThreeURL));
        }
        catch (Exception e)
        {
            println("<arbitrary desc=\"" + errStr + e.toString() + "\">");

            if (pWriter != null)
            {
                e.printStackTrace(pWriter);
            }

            e.printStackTrace();
            println("</arbitrary>");

            return;
        }

        errStr = "PreCreating runners threw: ";

        try
        {
            String[] rValues = new String[FUTUREUSE];

            // Create a whole bunch of worker threads and run them
            for (int i = 0; i < numRunners; i++)
            {
                TestThreadsRunner r1, r2, r3;
                Thread t1, t2, t3;

                // First set of runners reports on memory usage periodically
                rValues[ID] = "one-" + i;
                rValues[XMLNAME] = filenameToURI(inputDir + setOneFilenameRoot + ".xml");
                rValues[XSLNAME] = filenameToURI(inputDir + setOneFilenameRoot + ".xsl");
                rValues[OUTNAME] = outputDir + setOneFilenameRoot + "r" + i;
                rValues[PARAMNAME] = paramName;
                rValues[PARAMVAL] = paramVal;
                rValues[OPTIONS] = "memory;param";
                errStr = "Creating runnerone-" + i + " threw: ";
                r1 = new TestThreadsRunner(rValues, stylesheet1,
                                           numRunnerCalls);
                t1 = new Thread(r1);

                t1.start();

                // Second set of runners is polite; uses optional liaison
                rValues[ID] = "two-" + i;
                rValues[XMLNAME] = filenameToURI(inputDir + setTwoFilenameRoot + ".xml");
                rValues[XSLNAME] = filenameToURI(inputDir + setTwoFilenameRoot + ".xsl");
                rValues[OUTNAME] = outputDir + setTwoFilenameRoot + "r" + i;
                rValues[PARAMNAME] = paramName;
                rValues[PARAMVAL] = paramVal;
                rValues[OPTIONS] = "polite;param";

                if ((liaison != null) &&!(liaison.equals("")))
                    rValues[LIAISON] = liaison;

                errStr = "Creating runnertwo-" + i + " threw: ";
                r2 = new TestThreadsRunner(rValues, stylesheet2,
                                           numRunnerCalls);
                t2 = new Thread(r2);

                t2.start();

                rValues[LIAISON] = null;

                // Third set of runners will recreate it's processor each time
                // and report memory usage; but not set the param
                // Note: this causes lots of calls to System.gc
                rValues[ID] = "thr-" + i;
                rValues[XMLNAME] = filenameToURI(inputDir + setThreeFilenameRoot + ".xml");
                rValues[XSLNAME] = filenameToURI(inputDir + setThreeFilenameRoot + ".xsl");
                rValues[OUTNAME] = outputDir + setThreeFilenameRoot + "r" + i;
                rValues[PARAMNAME] = paramName;
                rValues[PARAMVAL] = paramVal;
                rValues[OPTIONS] = "recreate;memory";
                errStr = "Creating runnerthree-" + i + " threw: ";
                r3 = new TestThreadsRunner(rValues, stylesheet3,
                                           numRunnerCalls);
                t3 = new Thread(r3);

                t3.start();
                println("<message desc=\"Created " + i
                        + "th set of runners.\"/>");
            }
        }
        catch (Exception e)
        {
            println("<arbitrary desc=\"" + errStr + e.toString() + "\">");

            if (pWriter != null)
            {
                e.printStackTrace(pWriter);
            }

            e.printStackTrace();
            println("</arbitrary>");
        }

        // Clean up our own references, just for completeness
        stylesheet1 = null;
        stylesheet2 = null;
        stylesheet3 = null;
        errStr = null;

        println("<message desc=\"Created all our runners!\"/>");
        println("<message desc=\"TestThreads main thread now complete\"/>");
        println("</resultsfile>");

        if (pWriter != null)
            pWriter.flush();
    }

    /**
     * Read in properties file and set instance variables.  
     *
     * @param fName name of .properties file to read
     * @return false if error occoured
     */
    protected boolean initPropFile(String fName)
    {

        Properties p = new Properties();

        try
        {

            // Load named file into our properties block
            FileInputStream fIS = new FileInputStream(fName);

            p.load(fIS);

            // Parse out any values that match our internal convenience variables
            outputDir = p.getProperty("outputDir", outputDir);

            // Validate the outputDir and use it to reset the logFileName
            File oDir = new File(outputDir);

            if (!oDir.exists())
            {
                if (!oDir.mkdirs())
                {

                    // Error, we can't create the outputDir, default to current dir
                    println("<message desc=\"outputDir(" + outputDir
                            + ") does not exist, defaulting to .\"/>");

                    outputDir = ".";
                }
            }

            // Verify inputDir as well
            inputDir = p.getProperty("inputDir", inputDir);

            File tDir = new File(inputDir);

            if (!tDir.exists())
            {
                if (!tDir.mkdirs())
                {

                    // Error, we can't create the inputDir, abort
                    println("<message desc=\"inputDir(" + inputDir
                            + ") does not exist, terminating test\"/>");

                    return false;
                }
            }

            // Add on separators
            inputDir += File.separator;
            outputDir += File.separator;

            // Each defaults to variable initializers            
            logFileName = p.getProperty("logFile", logFileName);
            setOneFilenameRoot = p.getProperty("setOneFile",
                                               setOneFilenameRoot);
            setTwoFilenameRoot = p.getProperty("setTwoFile",
                                               setTwoFilenameRoot);
            setThreeFilenameRoot = p.getProperty("setThreeFile",
                                                 setThreeFilenameRoot);
            paramName = p.getProperty("paramName", paramName);
            paramVal = p.getProperty("paramVal", paramVal);
            liaison = p.getProperty("liaison", liaison);

            String numb;

            numb = p.getProperty("numRunners");

            if (numb != null)
            {
                try
                {
                    numRunners = Integer.parseInt(numb);
                }
                catch (NumberFormatException numEx)
                {

                    // no-op, leave set as default
                    println("<message desc=\"numRunners threw: "
                            + numEx.toString() + "\"/>");
                }
            }

            numb = p.getProperty("numRunnerCalls");

            if (numb != null)
            {
                try
                {
                    numRunnerCalls = Integer.parseInt(numb);
                }
                catch (NumberFormatException numEx)
                {

                    // no-op, leave set as default
                    println("<message desc=\"numRunnerCalls threw: "
                            + numEx.toString() + "\"/>");
                }
            }
        }
        catch (Exception e)
        {
            println("<arbitrary=\"initPropFile: " + fName + " threw: "
                    + e.toString() + "\">");

            if (pWriter != null)
            {
                e.printStackTrace(pWriter);
            }

            e.printStackTrace();
            println("</arbitrary>");

            return false;
        }

        return true;
    }

    /**
     * Bottleneck output; goes to System.out and main's pWriter.  
     *
     * NEEDSDOC @param s
     */
    protected void println(String s)
    {

        System.out.println(s);

        if (pWriter != null)
            pWriter.println(s);
    }

    /** A simple log output file for the main thread; each runner also has it's own. */
    protected PrintWriter pWriter = null;

    /**
     * Worker method to setup a simple log output file.  
     *
     * NEEDSDOC @param n
     */
    protected void createLogFile(String n)
    {

        try
        {
            pWriter = new PrintWriter(new FileWriter(n, true));
        }
        catch (Exception e)
        {
            System.err.println("<message desc=\"createLogFile threw: "
                               + e.toString() + "\"/>");
            e.printStackTrace();
        }
    }

    /**
     * Startup the test from the command line.  
     *
     * NEEDSDOC @param args
     */
    public static void main(String[] args)
    {

        if (args.length < 1)
        {
            System.err.println("ERROR! Must have at least one argument\n" + usage());

            return;  // Don't System.exit, it's not polite
        }

        TestThreads app = new TestThreads();
        // semi-HACK: accept and ignore -load as first arg only
        String propFileName = null;
        if ("-load".equalsIgnoreCase(args[0]))
        {
            propFileName = args[1];
        }
        else
        {
            propFileName = args[0];
        }
        if (!app.initPropFile(propFileName))  // Side effect: creates pWriter for logging
        {
            System.err.println("ERROR! Could not read properties file: "
                               + propFileName);

            return;
        }

        app.runTest();
    }

    /**
     * Worker method to translate String to URI.  
     * Note: Xerces and Crimson appear to handle some URI references 
     * differently - this method needs further work once we figure out 
     * exactly what kind of format each parser wants (esp. considering 
     * relative vs. absolute references).
     * @param String path\filename of test file
     * @return URL to pass to SystemId
     */
    public static String filenameToURI(String filename)
    {
        File f = new File(filename);
        String tmp = f.getAbsolutePath();
	    if (File.separatorChar == '\\') {
	        tmp = tmp.replace('\\', '/');
	    }
        return "file:///" + tmp;
    }
}  // end of class TestThreads

/**
 * Worker class to run a processor on a separate thread.
 * <p>Currently, no automated validation is done, however most
 * output files and all error logs are saved to disk allowing for
 * later manual verification.</p>
 */
class TestThreadsRunner implements Runnable
{

    /** NEEDSDOC Field xslStylesheet          */
    Templates xslStylesheet;

    /** NEEDSDOC Field numProcesses          */
    int numProcesses;

    /** NEEDSDOC Field runnerID          */
    String runnerID;

    /** NEEDSDOC Field xmlName          */
    String xmlName;

    /** NEEDSDOC Field xslName          */
    String xslName;

    /** NEEDSDOC Field outName          */
    String outName;

    /** NEEDSDOC Field paramName          */
    String paramName;

    /** NEEDSDOC Field paramVal          */
    String paramVal;

    /** NEEDSDOC Field liaison          */
    String liaison;

    /** NEEDSDOC Field polite          */
    boolean polite = false;  // if we should yield each loop

    /** NEEDSDOC Field recreate          */
    boolean recreate = false;  // if we should re-create a new processor each time

    /** NEEDSDOC Field validate          */
    boolean validate = false;  // if we should attempt to validate output files (FUTUREWORK)

    /** NEEDSDOC Field reportMem          */
    boolean reportMem = false;  // if we should report memory usage periodically

    /** NEEDSDOC Field setParam          */
    boolean setParam = false;  // if we should set our parameter or not

    /**
     * Constructor TestThreadsRunner
     *
     *
     * NEEDSDOC @param params
     * NEEDSDOC @param xslStylesheet
     * NEEDSDOC @param numProcesses
     */
    TestThreadsRunner(String[] params, Templates xslStylesheet,
                      int numProcesses)
    {

        this.xslStylesheet = xslStylesheet;
        this.numProcesses = numProcesses;
        this.runnerID = params[TestThreads.ID];
        this.xmlName = params[TestThreads.XMLNAME]; // must already be legal URI
        this.xslName = params[TestThreads.XSLNAME]; // must already be legal URI
        this.outName = params[TestThreads.OUTNAME]; // must be local path/filename
        this.paramName = params[TestThreads.PARAMNAME];
        this.paramVal = params[TestThreads.PARAMVAL];

        if (params[TestThreads.OPTIONS].indexOf("polite") > 0)
            polite = true;

        if (params[TestThreads.OPTIONS].indexOf("recreate") > 0)
            recreate = true;

        if (params[TestThreads.OPTIONS].indexOf("validate") > 0)
            validate = true;

        // Optimization: only report memory if asked to and we're 
        //  in the first iteration of runners created
        if ((params[TestThreads.OPTIONS].indexOf("memory") > 0)
            && (this.runnerID.indexOf("0") >= 0))
            reportMem = true;

        if (params[TestThreads.OPTIONS].indexOf("param") > 0)
            setParam = true;

        if (params[TestThreads.LIAISON] != null)  // TRAX unused
            liaison = params[TestThreads.LIAISON];
    }

    /**
     * Bottleneck output; both to System.out and to our private errWriter.  
     *
     * NEEDSDOC @param s
     */
    protected void println(String s)
    {

        System.out.println(s);

        if (errWriter != null)
            errWriter.println(s);
    }

    /**
     * Bottleneck output; both to System.out and to our private errWriter.  
     *
     * NEEDSDOC @param s
     */
    protected void print(String s)
    {

        System.out.print(s);

        if (errWriter != null)
            errWriter.print(s);
    }

    /** NEEDSDOC Field errWriter          */
    PrintWriter errWriter = null;

    /**
     * NEEDSDOC Method createErrWriter 
     *
     */
    protected void createErrWriter()
    {

        try
        {
            errWriter = new PrintWriter(new FileWriter(outName + ".log"),
                                        true);
        }
        catch (Exception e)
        {
            System.err.println("<message desc=\"" + runnerID + ":threw: "
                               + e.toString() + "\"/>");
        }
    }

    /** Main entrypoint; loop and perform lots of processes. */
    public void run()
    {

        int i = 0;  // loop counter; used for error reporting

        createErrWriter();
        println("<?xml version=\"1.0\"?>");
        println("<testrunner desc=\"" + runnerID + ":started\" fileName=\""
                + xslName + "\">");

        TransformerFactory factory = null;

        try
        {

            // Each runner creates it's own processor for use and it's own error log
            factory = TransformerFactory.newInstance();
            println("<arbitrary desc=\"" + runnerID + ":processing\">");
        }
        catch (Throwable ex)
        {  // If we got here, just log it and bail, no sense continuing
            println("<throwable desc=\"" + ex.toString() + "\"><![CDATA[");
            ex.printStackTrace(errWriter);
            println("\n</throwable>");
            println("<message desc=\"" + runnerID + ":complete-ERROR:after:"
                    + i + "\"/>");
            println("</testrunner>");

            if (errWriter != null)
                errWriter.close();

            return;
        }

        try
        {

            // Loop away...
            for (i = 0; i < numProcesses; i++)
            {

                // Run a process using the pre-compiled stylesheet we were construced with
                {
                    Transformer transformer1 = xslStylesheet.newTransformer();
                    FileOutputStream resultStream1 =
                        new FileOutputStream(outName + ".out");
                    Result result1 = new StreamResult(resultStream1);

                    if (setParam)
                        transformer1.setParameter(paramName, paramVal);

                    print(".");  // Note presence of this in logs shows which process threw an exception
                    transformer1.transform(new StreamSource(xmlName), result1);
                    resultStream1.close();

                    // Temporary vars go out of scope for cleanup here
                }

                // Now process something with a newly-processed stylesheet
                {
                    Templates templates2 =
                        factory.newTemplates(new StreamSource(xslName));
                    Transformer transformer2 = templates2.newTransformer();
                    FileOutputStream resultStream2 =
                        new FileOutputStream(outName + "_.out");
                    Result result2 = new StreamResult(resultStream2);

                    if (setParam)
                        transformer2.setParameter(paramName, paramVal);

                    print("*");  // Note presence of this in logs shows which process threw an exception
                    transformer2.transform(new StreamSource(xmlName), result2);
                    resultStream2.close();
                }

                // if asked, report memory statistics
                if (reportMem)
                {
                    Runtime r = Runtime.getRuntime();

                    r.gc();

                    long freeMemory = r.freeMemory();
                    long totalMemory = r.totalMemory();

                    println("<statistic desc=\"" + runnerID
                            + ":memory:longval-free:doubleval-total\">");
                    println("<longval>" + freeMemory + "</longval>");
                    println("<doubleval>" + totalMemory + "</doubleval>");
                    println("</statistic>");
                }

                // if we're polite, let others play for a bit
                if (polite)
                    java.lang.Thread.yield();
            }

            // IF we get here, we worked without exceptions (presumably successfully)
            println("</arbitrary>");
            println("<message desc=\"" + runnerID + ":complete-OK:after:"
                    + numProcesses + "\"/>");
        }

        // Separate messages for each kind of exception
        catch (TransformerException te)
        {
            println("\n<TransformerException desc=\"" + te.toString() + "\">");
            logStackTrace(te, errWriter);
            logContainedException(te, errWriter);
            println("</TransformerException>");
            println("</arbitrary>");
            println("<message desc=\"" + runnerID + ":complete-ERROR:after:"
                    + i + "\"/>");
        }
        catch (Throwable ex)
        {
            logThrowable(ex, errWriter);
            println("</arbitrary>");
            println("<message desc=\"" + runnerID + ":complete-ERROR:after:"
                    + i + "\"/>");
        }
        finally
        {

            // Cleanup our references, etc.
            println("</testrunner>");

            if (errWriter != null)
                errWriter.close();

            runnerID = null;
            xmlName = null;
            xslName = null;
            xslStylesheet = null;
            outName = null;
        }
    }  // end of run()...

    /**
     * NEEDSDOC Method logContainedException 
     *
     *
     * NEEDSDOC @param parent
     * NEEDSDOC @param p
     */
    private void logContainedException(TransformerException parent, PrintWriter p)
    {

        Throwable containedException = parent.getException();

        if (null != containedException)
        {
            println("<containedexception desc=\""
                    + containedException.toString() + "\">");
            logStackTrace(containedException, p);
            println("</containedexception>");
        }
    }

    /**
     * NEEDSDOC Method logThrowable 
     *
     *
     * NEEDSDOC @param t
     * NEEDSDOC @param p
     */
    private void logThrowable(Throwable t, PrintWriter p)
    {

        println("\n<throwable desc=\"" + t.toString() + "\">");
        logStackTrace(t, p);
        println("</throwable>");
    }

    /**
     * NEEDSDOC Method logStackTrace 
     *
     *
     * NEEDSDOC @param t
     * NEEDSDOC @param p
     */
    private void logStackTrace(Throwable t, PrintWriter p)
    {

        // Should check if (errWriter == null)
        println("<stacktrace><![CDATA[");
        t.printStackTrace(p);

        // Could also echo to stdout, but not really worth it
        println("]]></stacktrace>");
    }
}  // end of class TestThreadsRunner...

// END OF FILE
