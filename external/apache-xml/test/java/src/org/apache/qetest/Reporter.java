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
 * Reporter.java
 *
 */
package org.apache.qetest;

import org.apache.test.android.TestLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Class defining how a test can report results including convenience methods.
 * <p>Tests generally interact with a Reporter, which turns around to call
 * a Logger to actually store the results.  The Reporter serves as a
 * single funnel for all results, hiding both the details and number of
 * actual loggers that might currently be turned on (file, screen, network,
 * etc.) from the test that created us.</p>
 * <p>Note that Reporter adds numerous convenience methods that, while they
 * are not strictly necessary to express a test's results, make coding
 * tests much easier.  Reporter is designed to be subclassed for your
 * particular application; in general you only need to provide setup mechanisims
 * specific to your testing/product environment.</p>
 * @todo all methods should check that available loggers are OK
 * @todo explain better how results are rolled up and calculated
 * @author Shane_Curcuru@lotus.com
 * @author Jo_Grant@lotus.com
 * @version $Id$
 */
public class Reporter implements Logger
{

    /**
     * Parameter: (optional) Name of results summary file.
     * <p>This is a custom parameter optionally used in writeResultsStatus.</p>
     */
    public static final String OPT_SUMMARYFILE = "summaryFile";


    /**
     * Constructor calls initialize(p).
     * @param p Properties block to initialize us with.
     */
    public Reporter(Properties p)
    {
        ready = initialize(p);

        // Android-changed: Hard-coded AndroidLogger here. Otherwise, all test failures are silent.
        addLogger(TestLogger.class.getName(), null);
    }

    /** If we're ready to start outputting yet. */
    protected boolean ready = false;

    //-----------------------------------------------------
    //-------- Implement Logger Control and utility routines --------
    //-----------------------------------------------------

    /**
     * Return a description of what this Logger/Reporter does.
     * @author Shane_Curcuru@lotus.com
     * @return description of how this Logger outputs results, OR
     * how this Reporter uses Loggers, etc..
     */
    public String getDescription()
    {
        return "Reporter: default reporter implementation";
    }

    /**
     * Returns information about the Property name=value pairs that
     * are understood by this Logger/Reporter.
     * @author Shane_Curcuru@lotus.com
     * @return same as {@link java.applet.Applet.getParameterInfo}.
     */
    public String[][] getParameterInfo()
    {

        String pinfo[][] =
        {
            { OPT_LOGGERS, "String", "FQCN of Loggers to add" },
            { OPT_LOGFILE, "String",
              "Name of file to use for file-based Logger output" },
            { OPT_LOGGINGLEVEL, "int",
              "to setLoggingLevel() to control amount of output" },
            { OPT_PERFLOGGING, "boolean",
              "if we should log performance data as well" },
            { OPT_INDENT, "int",
              "number of spaces to indent for supporting Loggers" },
            { OPT_DEBUG, "boolean", "generic debugging flag" }
        };

        return pinfo;
    }

    /**
     * Accessor methods for a properties block.
     * @return our Properties block.
     * @todo should this clone first?
     */
    public Properties getProperties()
    {
        return reporterProps;
    }

    /**
     * Accessor methods for a properties block.
     * Always having a Properties block allows users to pass common
     * options to a Logger/Reporter without having to know the specific
     * 'properties' on the object.
     * <p>Much like in Applets, users can call getParameterInfo() to
     * find out what kind of properties are available.  Callers more
     * commonly simply call initialize(p) instead of setProperties(p)</p>
     * @author Shane_Curcuru@lotus.com
     * @param p Properties to set (should be cloned).
     */
    public void setProperties(Properties p)
    {
        if (p != null)
            reporterProps = (Properties) p.clone();
    }

    /**
     * Call once to initialize this Logger/Reporter from Properties.
     * <p>Simple hook to allow Logger/Reporters with special output
     * items to initialize themselves.</p>
     *
     * @author Shane_Curcuru@lotus.com
     * @param p Properties block to initialize from.
     * @param status, true if OK, false if an error occoured.
     */
    public boolean initialize(Properties p)
    {

        setProperties(p);

        String dbg = reporterProps.getProperty(OPT_DEBUG);

        if ((dbg != null) && dbg.equalsIgnoreCase("true"))
        {
            setDebug(true);
        }

        String perf = reporterProps.getProperty(OPT_PERFLOGGING);

        if ((perf != null) && perf.equalsIgnoreCase("true"))
        {
            setPerfLogging(true);
        }

        // int values need to be parsed
        String logLvl = reporterProps.getProperty(OPT_LOGGINGLEVEL);

        if (logLvl != null)
        {
            try
            {
                setLoggingLevel(Integer.parseInt(logLvl));
            }
            catch (NumberFormatException numEx)
            { /* no-op */
            }
        }

        // Add however many loggers are askedfor
        boolean b = true;
        StringTokenizer st =
            new StringTokenizer(reporterProps.getProperty(OPT_LOGGERS),
                                LOGGER_SEPARATOR);
        int i;

        for (i = 0; st.hasMoreTokens(); i++)
        {
            String temp = st.nextToken();

            if ((temp != null) && (temp.length() > 1))
            {
                b &= addLogger(temp, reporterProps);
            }
        }

        return true;
    }

    /**
     * Is this Logger/Reporter ready to log results?
     * @author Shane_Curcuru@lotus.com
     * @return status - true if it's ready to report, false otherwise
     * @todo should we check our contained Loggers for their status?
     */
    public boolean isReady()
    {
        return ready;
    }

    /**
     * Flush this Logger/Reporter - should ensure all output is flushed.
     * Note that the flush operation is not necessarily pertinent to
     * all types of Logger/Reporter - console-type Loggers no-op this.
     * @author Shane_Curcuru@lotus.com
     */
    public void flush()
    {

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].flush();
        }
    }

    /**
     * Close this Logger/Reporter - should include closing any OutputStreams, etc.
     * Logger/Reporters should return isReady() = false after closing.
     * @author Shane_Curcuru@lotus.com
     */
    public void close()
    {

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].close();
        }
    }

    /**
     * Generic properties for this Reporter.
     * <p>Use a Properties block to make it easier to add new features
     * and to be able to pass data to our loggers.  Any properties that
     * we recognize will be set here, and the entire block will be passed
     * to any loggers that we control.</p>
     */
    protected Properties reporterProps = new Properties();

    /**
     * This determines the amount of data actually logged out to results.
     * <p>Setting this higher will result in more data being logged out.
     * Values range from Reporter.CRITICALMSG (0) to TRACEMSG (60).
     * For non-performance-critical testing, you may wish to set this high,
     * so all data gets logged, and then use reporting tools on the test output
     * to filter for human use (since the appropriate level is stored with
     * every logMsg() call)</p>
     * @see #logMsg(int, java.lang.String)
     */
    protected int loggingLevel = DEFAULT_LOGGINGLEVEL;

    /**
     * Marker that a testcase is currently running.
     * <p>NEEDSWORK: should do a better job of reporting results in cases
     * where users might not call testCaseInit/testCaseClose in non-nested pairs.</p>
     */
    protected boolean duringTestCase = false;

    /**
     * Flag if we should force loggers closed upon testFileClose.
     * <p>Default: true.  Standalone tests can leave this alone.
     * Test Harnesses may want to reset this so they can have multiple
     * file results in one actual output 'file' for file-based loggers.</p>
     */
    protected boolean closeOnFileClose = true;

    /**
     * Accessor method for closeOnFileClose.  
     *
     * @return our value for closeOnFileClose
     */
    public boolean getCloseOnFileClose()
    {
        return closeOnFileClose;
    }

    /**
     * Accessor method for closeOnFileClose.  
     *
     * @param b value to set for closeOnFileClose
     */
    public void setCloseOnFileClose(boolean b)
    {
        closeOnFileClose = b;
    }

    //-----------------------------------------------------
    //-------- Test results computation members and methods --------
    //-----------------------------------------------------

    /** Name of the current test. */
    protected String testName;

    /** Description of the current test. */
    protected String testComment;

    /** Number of current case within a test, usually automatically calculated. */
    protected int caseNum;

    /** Description of current case within a test. */
    protected String caseComment;

    /** Overall test result of current test, automatically calculated. */
    protected int testResult;

    /** Overall test result of current testcase, automatically calculated. */
    protected int caseResult;

    /**
     * Counters for overall number of results - passes, fails, etc.
     * @todo update this if we use TestResult objects
     */
    protected static final int FILES = 0;

    /** NEEDSDOC Field CASES          */
    protected static final int CASES = 1;

    /** NEEDSDOC Field CHECKS          */
    protected static final int CHECKS = 2;

    /** NEEDSDOC Field MAX_COUNTERS          */
    protected static final int MAX_COUNTERS = CHECKS + 1;

    /**
     * Counters for overall number of results - passes, fails, etc.
     * @todo update this if we use TestResult objects
     */
    protected int[] incpCount = new int[MAX_COUNTERS];

    /** NEEDSDOC Field passCount          */
    protected int[] passCount = new int[MAX_COUNTERS];

    /** NEEDSDOC Field ambgCount          */
    protected int[] ambgCount = new int[MAX_COUNTERS];

    /** NEEDSDOC Field failCount          */
    protected int[] failCount = new int[MAX_COUNTERS];

    /** NEEDSDOC Field errrCount          */
    protected int[] errrCount = new int[MAX_COUNTERS];
    

    //-----------------------------------------------------
    //-------- Composite Pattern Variables And Methods --------
    //-----------------------------------------------------

    /**
     * Optimization: max number of loggers, stored in an array.
     * <p>This is a design decision: normally, you might use a ConsoleReporter,
     * some sort of file-based one, and maybe a network-based one.</p>
     */
    protected int MAX_LOGGERS = 3;

    /**
     * Array of loggers to whom we pass results.
     * <p>Store our loggers in an array for optimization, since we want
     * logging calls to take as little time as possible.</p>
     */
    protected Logger[] loggers = new Logger[MAX_LOGGERS];

    /** NEEDSDOC Field numLoggers          */
    protected int numLoggers = 0;

    /**
     * Add a new Logger to our array, optionally initializing it with Properties.
     * <p>Store our Loggers in an array for optimization, since we want
     * logging calls to take as little time as possible.</p>
     * @todo enable users to add more than MAX_LOGGERS
     * @author Gang Of Four
     * @param rName fully qualified class name of Logger to add.
     * @param p (optional) Properties block to initialize the Logger with.
     * @return status - true if successful, false otherwise.
     */
    public boolean addLogger(String rName, Properties p)
    {

        if ((rName == null) || (rName.length() < 1))
            return false;

        debugPrintln("addLogger(" + numLoggers + ", " + rName + " ...)");

        if ((numLoggers + 1) > loggers.length)
        {

            // @todo enable users to add more than MAX_LOGGERS
            return false;
        }

        // Attempt to add Logger to our list
        Class rClass;
        Constructor rCtor;

        try
        {
            rClass = Class.forName(rName);

            debugPrintln("rClass is " + rClass.toString());

            if (p == null)

            // @todo should somehow pass along our own props as well
            // Need to ensure Reporter and callers of this method always 
            //  coordinate the initialization of the Loggers we hold
            {
                loggers[numLoggers] = (Logger) rClass.newInstance();
            }
            else
            {
                Class[] parameterTypes = new Class[1];

                parameterTypes[0] = java.util.Properties.class;
                rCtor = rClass.getConstructor(parameterTypes);

                Object[] initArgs = new Object[1];

                initArgs[0] = (Object) p;
                loggers[numLoggers] = (Logger) rCtor.newInstance(initArgs);
            }
        }
        catch (Exception e)
        {

            // @todo should we inform user why it failed?
            // Note: the logMsg may fail since we might not have any reporters at this point!
            debugPrintln("addLogger exception: " + e.toString());
            logCriticalMsg("addLogger exception: " + e.toString());
            logThrowable(CRITICALMSG, e, "addLogger exception:");

            return false;
        }

        // Increment counter for later use
        numLoggers++;

        return true;
    }

    /**
     * Return an Hashtable of all active Loggers.
     * @todo revisit; perhaps use a Vector
     * @reurns Hash of all active Loggers; null if none
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Hashtable getLoggers()
    {

        // Optimization
        if (numLoggers == 0)
            return (null);

        Hashtable temp = new Hashtable();

        for (int i = 0; i < numLoggers; i++)
        {
            temp.put(loggers[i].getClass().getName(), loggers[i]);
        }

        return temp;
    }

    /**
     * Add the default Logger to this Reporter, whatever it is.
     * <p>Only adds the Logger if numLoggers <= 0; if the user has already
     * setup another Logger, this is a no-op (for the testwriter who doesn't
     * want the performance hit or annoyance of having Console output)</p>
     * @author Gang Of Four
     * @return status - true if successful, false otherwise.
     */
    public boolean addDefaultLogger()
    {

        // Optimization - return true, since they already have a logger
        if (numLoggers > 0)
            return true;

        return addLogger(DEFAULT_LOGGER, reporterProps);
    }

    //-----------------------------------------------------
    //-------- Testfile / Testcase start and stop routines --------
    //-----------------------------------------------------

    /**
     * Call once to initialize your Loggers for your test file.
     * Also resets test name, result, case results, etc.
     * <p>Currently, you must init/close your test file before init/closing
     * any test cases.  No checking is currently done to ensure that
     * mismatched test files are not nested.  This is an area that needs
     * design decisions and some work eventually to be a really clean design.</p>
     * <p>Not only do nested testfiles/testcases have implications for good
     * testing practices, they may also have implications for various Loggers,
     * especially XML or other ones with an implicit hierarcy in the reports.</p>
     * @author Shane_Curcuru@lotus.com
     * @param name file name or tag specifying the test.
     * @param comment comment about the test.
     */
    public void testFileInit(String name, String comment)
    {

        testName = name;
        testComment = comment;
        testResult = DEFAULT_RESULT;
        caseNum = 0;
        caseComment = null;
        caseResult = DEFAULT_RESULT;
        duringTestCase = false;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].testFileInit(testName, testComment);
        }

        // Log out time whole test script starts
        // Note there is a slight delay while logPerfMsg calls all reporters
        long t = System.currentTimeMillis();

        logPerfMsg(TEST_START, t, testName);
    }

    /**
     * Call once to close out your test and summate results.
     * <p>will close an open testCase before closing the file.  May also
     * force all Loggers closed if getCloseOnFileClose() (which may imply
     * that no more output will be logged to file-based reporters)</p>
     * @author Shane_Curcuru@lotus.com
     * @todo make this settable as to how/where the resultsCounters get output
     */
    public void testFileClose()
    {

        // Cache the time whole test script ends
        long t = System.currentTimeMillis();

        if (duringTestCase)
        {

            // Either user messed up (forgot to call testCaseClose) or something went wrong
            logErrorMsg("WARNING! testFileClose when duringTestCase=true!");

            // Force call to testCaseClose()
            testCaseClose();
        }

        // Actually log the time the test script ends after closing any potentially open testcases
        logPerfMsg(TEST_STOP, t, testName);

        // Increment our results counters 
        incrementResultCounter(FILES, testResult);

        // Print out an overall count of results by type
        // @todo make this settable as to how/where the resultsCounters get output
        logResultsCounters();

        // end this testfile - finish up any reporting we need to
        for (int i = 0; i < numLoggers; i++)
        {

            // Log we're done and then flush
            loggers[i].testFileClose(testComment, resultToString(testResult));
            loggers[i].flush();

            // Only close each reporter if asked to; this implies we're done
            //  and can't perform any more logging ourselves (or our reporters)
            if (getCloseOnFileClose())
            {
                loggers[i].close();
            }
        }

        // Note: explicitly leave testResult, caseResult, etc. set for debugging
        //       purposes or for use by external test harnesses
    }

    /**
     * Implement Logger-only method.
     * <p>Here, a Reporter is simply acting as a logger: so don't
     * summate any results, do performance measuring, or anything
     * else, just pass the call through to our Loggers.
     * @param msg message to log out
     * @param result result of testfile
     */
    public void testFileClose(String msg, String result)
    {

        if (duringTestCase)
        {

            // Either user messed up (forgot to call testCaseClose) or something went wrong
            logErrorMsg("WARNING! testFileClose when duringTestCase=true!");

            // Force call to testCaseClose()
            testCaseClose();
        }

        // end this testfile - finish up any reporting we need to
        for (int i = 0; i < numLoggers; i++)
        {

            // Log we're done and then flush
            loggers[i].testFileClose(testComment, resultToString(testResult));
            loggers[i].flush();

            // Only close each reporter if asked to; this implies we're done
            //  and can't perform any more logging ourselves (or our reporters)
            if (getCloseOnFileClose())
            {
                loggers[i].close();
            }
        }
    }

    /**
     * Call once to start each test case; logs out testcase number and your comment.
     * <p>Testcase numbers are calculated as integers incrementing from 1.  Will
     * also close any previously init'd but not closed testcase.</p>
     * @author Shane_Curcuru@lotus.com
     * @todo investigate tieing this to the actual testCase methodnames,
     * instead of blindly incrementing the counter
     * @param comment short description of this test case's objective.
     */
    public void testCaseInit(String comment)
    {

        if (duringTestCase)
        {

            // Either user messed up (forgot to call testCaseClose) or something went wrong
            logErrorMsg("WARNING! testCaseInit when duringTestCase=true!");

            // Force call to testCaseClose()
            testCaseClose();
        }

        caseNum++;

        caseComment = comment;
        caseResult = DEFAULT_RESULT;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].testCaseInit(String.valueOf(caseNum) + " "
                                    + caseComment);
        }

        duringTestCase = true;

        // Note there is a slight delay while logPerfMsg calls all reporters
        long t = System.currentTimeMillis();

        logPerfMsg(CASE_START, t, caseComment);
    }

    /**
     * Call once to end each test case and sub-summate results.
     * @author Shane_Curcuru@lotus.com
     */
    public void testCaseClose()
    {

        long t = System.currentTimeMillis();

        logPerfMsg(CASE_STOP, t, caseComment);

        if (!duringTestCase)
        {
            logErrorMsg("WARNING! testCaseClose when duringTestCase=false!");

            // Force call to testCaseInit()
            // NEEDSWORK: should we really do this?  This ensures any results
            //            are well-formed, however a user might not expect this.
            testCaseInit("WARNING! testCaseClose when duringTestCase=false!");
        }

        duringTestCase = false;
        testResult = java.lang.Math.max(testResult, caseResult);

        // Increment our results counters 
        incrementResultCounter(CASES, caseResult);

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].testCaseClose(
                String.valueOf(caseNum) + " " + caseComment,
                resultToString(caseResult));
        }
    }

    /**
     * Implement Logger-only method.
     * <p>Here, a Reporter is simply acting as a logger: so don't
     * summate any results, do performance measuring, or anything
     * else, just pass the call through to our Loggers.
     * @param msg message of name of test case to log out
     * @param result result of testfile
     */
    public void testCaseClose(String msg, String result)
    {

        if (!duringTestCase)
        {
            logErrorMsg("WARNING! testCaseClose when duringTestCase=false!");

            // Force call to testCaseInit()
            // NEEDSWORK: should we really do this?  This ensures any results
            //            are well-formed, however a user might not expect this.
            testCaseInit("WARNING! testCaseClose when duringTestCase=false!");
        }

        duringTestCase = false;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].testCaseClose(
                String.valueOf(caseNum) + " " + caseComment,
                resultToString(caseResult));
        }
    }

    /**
     * Calls back into a Test to run test cases in order.
     * <p>Use reflection to call back and execute each testCaseXX method
     * in the calling test in order, catching exceptions along the way.</p>
     * //@todo rename to 'executeTestCases' or something
     * //@todo implement options: either an inclusion or exclusion list
     * @author Shane Curcuru
     * @param testObject the test object itself.
     * @param numTestCases number of consecutively numbered test cases to execute.
     * @param options (future use: options to pass to testcases)
     * @return status, true if OK, false if big bad error occoured
     */
    public boolean executeTests(Test testObject, int numTestCases,
                                Object options)
    {

        // Flag denoting if we've had any errors
        boolean gotException = false;

        // Declare all needed java variables
        String tmpErrString = "executeTests: no errors yet";
        Object noArgs[] = new Object[0];  // use options instead
        Class noParams[] = new Class[0];
        Method currTestCase;
        Class testClass;

        // Get class reference for the test applet itself
        testClass = testObject.getClass();

        logTraceMsg("executeTests: running " + numTestCases + " tests now.");

        for (int tcNum = 1; tcNum <= numTestCases; tcNum++)
        {
            try
            {  // get a reference to the next test case that we'll be calling
                tmpErrString = "executeTests: No such method: testCase"
                               + tcNum + "()";
                currTestCase = testClass.getMethod("testCase" + tcNum,
                                                   noParams);

                // Now directly invoke that test case
                tmpErrString =
                    "executeTests: Method threw an exception: testCase"
                    + tcNum + "(): ";

                logTraceMsg("executeTests: invoking testCase" + tcNum
                            + " now.");
                currTestCase.invoke(testObject, noArgs);
            }
            catch (InvocationTargetException ite)
            {
                // Catch any error, log it as an error, and allow next test case to run
                gotException = true;
                testResult = java.lang.Math.max(ERRR_RESULT, testResult);
                tmpErrString += ite.toString();
                logErrorMsg(tmpErrString);

                // Grab the contained error, log it if available 
                java.lang.Throwable containedThrowable =
                    ite.getTargetException();
                if (containedThrowable != null)
                {
                    logThrowable(ERRORMSG, containedThrowable, tmpErrString + "(1)");
                }
                logThrowable(ERRORMSG, ite, tmpErrString + "(2)");
            }  // end of catch
            catch (Throwable t)
            {
                // Catch any error, log it as an error, and allow next test case to run
                gotException = true;
                testResult = java.lang.Math.max(ERRR_RESULT, testResult);
                tmpErrString += t.toString();
                logErrorMsg(tmpErrString);
                logThrowable(ERRORMSG, t, tmpErrString);
            }  // end of catch
        }  // end of for

        // Convenience functionality: remind user if they appear to 
        //  have set numTestCases too low 
        try
        {  
            // Get a reference to the *next* test case after numTestCases
            int moreTestCase = numTestCases + 1;
            currTestCase = testClass.getMethod("testCase" + moreTestCase, noParams);

            // If we get here, we found another testCase - warn the user
            logWarningMsg("executeTests: extra testCase"+ moreTestCase
                          + " found, perhaps numTestCases is too low?");
        }
        catch (Throwable t)
        {
            // Ignore errors: we don't care, since they didn't 
            //  ask us to look for this method anyway
        }

        // Return true only if everything passed
        if (testResult == PASS_RESULT)
            return true;
        else
            return false;
    }  // end of executeTests

    //-----------------------------------------------------
    //-------- Test results logging routines --------
    //-----------------------------------------------------

    /**
     * Accessor for loggingLevel, determines what level of log*() calls get output.
     * @return loggingLevel, as an int.
     */
    public int getLoggingLevel()
    {
        return loggingLevel;
    }

    /**
     * Accessor for loggingLevel, determines what level of log*() calls get output.
     * @param setLL loggingLevel; normalized to be between CRITICALMSG and TRACEMSG.
     */
    public void setLoggingLevel(int setLL)
    {

        if (setLL < CRITICALMSG)
        {
            loggingLevel = CRITICALMSG;
        }
        else if (setLL > TRACEMSG)
        {
            loggingLevel = TRACEMSG;
        }
        else
        {
            loggingLevel = setLL;
        }
    }

    /**
     * Report a comment to result file with specified severity.
     * <p>Works in conjunction with {@link #loggingLevel };
     * only outputs messages that are more severe (i.e. lower)
     * than the current logging level.</p>
     * <p>Note that some Loggers may limit the comment string,
     * either in overall length or by stripping any linefeeds, etc.
     * This is to allow for optimization of file or database-type
     * reporters with fixed fields.  Users who need to log out
     * special string data should use logArbitrary() instead.</p>
     * <p>Remember, use {@link #check(String, String, String)
     * various check*() methods} to report the actual results
     * of your tests.</p>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param msg comment to log out.
     * @see #loggingLevel
     */
    public void logMsg(int level, String msg)
    {

        if (level > loggingLevel)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logMsg(level, msg);
        }
    }

    /**
     * Report an arbitrary String to result file with specified severity.
     * Log out the String provided exactly as-is.
     * @author Shane_Curcuru@lotus.com
     * @param level severity or class of message.
     * @param msg arbitrary String to log out.
     */
    public void logArbitrary(int level, String msg)
    {

        if (level > loggingLevel)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logArbitrary(level, msg);
        }
    }

    /**
     * Logs out statistics to result file with specified severity.
     * <p>This is a general-purpose way to log out numeric statistics.  We accept
     * both a long and a double to allow users to save whatever kind of numbers
     * they need to, with the simplest API.  The actual meanings of the numbers
     * are dependent on the implementer.</p>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param lVal statistic in long format.
     * @param dVal statistic in doubleformat.
     * @param msg comment to log out.
     */
    public void logStatistic(int level, long lVal, double dVal, String msg)
    {

        if (level > loggingLevel)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logStatistic(level, lVal, dVal, msg);
        }
    }

    /**
     * Logs out a element to results with specified severity.
     * This method is primarily for reporters that output to fixed
     * structures, like files, XML data, or databases.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param element name of enclosing element
     * @param attrs hash of name=value attributes
     * @param msg Object to log out; up to reporters to handle
     * processing of this; usually logs just .toString().
     */
    public void logElement(int level, String element, Hashtable attrs,
                           Object msg)
    {

        if (level > loggingLevel)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logElement(level, element, attrs, msg);
        }
    }

    /**
     * Logs out Throwable.toString() and a stack trace of the 
     * Throwable with the specified severity.
     * <p>Works in conjuntion with {@link #setLoggingLevel(int)}; 
     * only outputs messages that are more severe than the current 
     * logging level.</p>
     * <p>This uses logArbitrary to log out your msg - message, 
     * a newline, throwable.toString(), a newline,
     * and then throwable.printStackTrace().</p>
     * <p>Note that this does not imply a failure or problem in 
     * a test in any way: many tests may want to verify that 
     * certain exceptions are thrown, etc.</p>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param throwable throwable/exception to log out.
     * @param msg description of the throwable.
     */
    public void logThrowable(int level, Throwable throwable, String msg)
    {

        if (level > loggingLevel)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logThrowable(level, throwable, msg);
        }
    }

    /**
     * Logs out contents of a Hashtable with specified severity.
     * <p>Works in conjuntion with setLoggingLevel(int); only outputs messages that
     * are more severe than the current logging level.</p>
     * <p>Loggers should store or log the full contents of the hashtable.</p>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param hash Hashtable to log the contents of.
     * @param msg description of the Hashtable.
     */
    public void logHashtable(int level, Hashtable hash, String msg)
    {
        if (level > loggingLevel)
          return;
        
        // Don't log anyway if level is 10 or less.
        //@todo revisit this decision: I don't like having special
        //  rules like this to exclude output.  On the other hand, 
        //  if the user set loggingLevel this low, they really don't 
        //  want much output coming out, and hashtables are big
        if (loggingLevel <= 10)
            return;

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logHashtable(level, hash, msg);
        }
    }

    /**
     * Logs out an critical a comment to results; always printed out.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logCriticalMsg(String msg)
    {
        logMsg(CRITICALMSG, msg);
    }

    // There is no logFailsOnlyMsg(String msg) method

    /**
     * Logs out an error a comment to results.
     * <p>Note that subclassed libraries may choose to override to
     * cause a fail to happen along with printing out the message.</p>
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logErrorMsg(String msg)
    {
        logMsg(ERRORMSG, msg);
    }

    /**
     * Logs out a warning a comment to results.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logWarningMsg(String msg)
    {
        logMsg(WARNINGMSG, msg);
    }

    /**
     * Logs out an status a comment to results.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logStatusMsg(String msg)
    {
        logMsg(STATUSMSG, msg);
    }

    /**
     * Logs out an informational a comment to results.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logInfoMsg(String msg)
    {
        logMsg(INFOMSG, msg);
    }

    /**
     * Logs out an trace a comment to results.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void logTraceMsg(String msg)
    {
        logMsg(TRACEMSG, msg);
    }

    //-----------------------------------------------------
    //-------- Test results reporting check* routines --------
    //-----------------------------------------------------
    // There is no public void checkIncp(String comment) method

    /* EXPERIMENTAL: have duplicate set of check*() methods 
       that all output some form of ID as well as comment. 
       Leave the non-ID taking forms for both simplicity to the 
       end user who doesn't care about IDs as well as for 
       backwards compatibility.
    */

    /**
     * Writes out a Pass record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the pass record.
     */
    public void checkPass(String comment)
    {
        checkPass(comment, null);
    }

    /**
     * Writes out an ambiguous record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the ambg record.
     */
    public void checkAmbiguous(String comment)
    {
        checkAmbiguous(comment, null);
    }

    /**
     * Writes out a Fail record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the fail record.
     */
    public void checkFail(String comment)
    {
        checkFail(comment, null);
    }
    

    /**
     * Writes out an Error record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the error record.
     */
    public void checkErr(String comment)
    {
        checkErr(comment, null);
    }

    /**
     * Writes out a Pass record with comment.
     * A Pass signifies that an individual test point has completed and has
     * been verified to have behaved correctly.
     * <p>If you need to do your own specific comparisons, you can
     * do them in your code and then just call checkPass or checkFail.</p>
     * <p>Derived classes must implement this to <B>both</B> report the
     * results out appropriately <B>and</B> to summate the results, if needed.</p>
     * <p>Pass results are a low priority, except for INCP (incomplete).  Note
     * that if a test never calls check*(), it will have an incomplete result.</p>
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the pass record.
     * @param ID token to log with the pass record.
     */
    public void checkPass(String comment, String id)
    {

        // Increment our results counters 
        incrementResultCounter(CHECKS, PASS_RESULT);

        // Special: only report it actually if needed
        if (getLoggingLevel() > FAILSONLY)
        {
            for (int i = 0; i < numLoggers; i++)
            {
                loggers[i].checkPass(comment, id);
            }
        }

        caseResult = java.lang.Math.max(PASS_RESULT, caseResult);
    }

    /**
     * Writes out an ambiguous record with comment.
     * <p>Ambiguous results are neither pass nor fail. Different test
     * libraries may have slightly different reasons for using ambg.</p>
     * <p>Derived classes must implement this to <B>both</B> report the
     * results out appropriately <B>and</B> to summate the results, if needed.</p>
     * <p>Ambg results have a middling priority, and take precedence over incomplete and pass.</p>
     * <p>An Ambiguous result may signify that the test point has completed and either
     * appears to have succeded, or that it has produced a result but there is no known
     * 'gold' result to compare it to.</p>
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the ambg record.
     * @param ID token to log with the pass record.
     */
    public void checkAmbiguous(String comment, String id)
    {

        // Increment our results counters 
        incrementResultCounter(CHECKS, AMBG_RESULT);

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].checkAmbiguous(comment, id);
        }

        caseResult = java.lang.Math.max(AMBG_RESULT, caseResult);
    }

    /**
     * Writes out a Fail record with comment.
     * <p>If you need to do your own specific comparisons, you can
     * do them in your code and then just call checkPass or checkFail.</p>
     * <p>Derived classes must implement this to <B>both</B> report the
     * results out appropriately <B>and</B> to summate the results, if needed.</p>
     * <p>Fail results have a high priority, and take precedence over incomplete, pass, and ambiguous.</p>
     * <p>A Fail signifies that an individual test point has completed and has
     * been verified to have behaved <B>in</B>correctly.</p>
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the fail record.
     * @param ID token to log with the pass record.
     */
    public void checkFail(String comment, String id)
    {

        // Increment our results counters 
        incrementResultCounter(CHECKS, FAIL_RESULT);

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].checkFail(comment, id);
        }

        caseResult = java.lang.Math.max(FAIL_RESULT, caseResult);
    }

    /**
     * Writes out an Error record with comment.
     * <p>Derived classes must implement this to <B>both</B> report the
     * results out appropriately <B>and</B> to summate the results, if needed.</p>
     * <p>Error results have the highest priority, and take precedence over
     * all other results.</p>
     * <p>An Error signifies that something unusual has gone wrong with the execution
     * of the test at this point - likely something that will require a human to
     * debug to see what really happened.</p>
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the error record.
     * @param ID token to log with the pass record.
     */
    public void checkErr(String comment, String id)
    {

        // Increment our results counters 
        incrementResultCounter(CHECKS, ERRR_RESULT);

        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].checkErr(comment, id);
        }

        caseResult = java.lang.Math.max(ERRR_RESULT, caseResult);
    }

    //-----------------------------------------------------
    //-------- Simplified Performance Logging - beyond interface Reporter --------
    //-----------------------------------------------------

    /** NEEDSDOC Field DEFAULT_PERFLOGGING_LEVEL          */
    protected final boolean DEFAULT_PERFLOGGING_LEVEL = false;

    /**
     * This determines if performance information is logged out to results.
     * <p>When true, extra performance records are written out to result files.</p>
     * @see #logPerfMsg(java.lang.String, long, java.lang.String)
     */
    protected boolean perfLogging = DEFAULT_PERFLOGGING_LEVEL;

    /**
     * Accessor for perfLogging, determines if we log performance info.
     * @todo add PerfLogging to Reporter interface
     * @return Whether or not we log performance info.
     */
    public boolean getPerfLogging()
    {
        return (perfLogging);
    }

    /**
     * Accessor for perfLogging, determines if we log performance info.
     * @param Whether or not we log performance info.
     *
     * NEEDSDOC @param setPL
     */
    public void setPerfLogging(boolean setPL)
    {
        perfLogging = setPL;
    }

    /**
     * Constants used to mark performance records in output.
     */

    // Note: string representations are explicitly set to all be 
    //       4 characters long to make it simpler to parse results
    public static final String TEST_START = "TSrt";

    /** NEEDSDOC Field TEST_STOP          */
    public static final String TEST_STOP = "TStp";

    /** NEEDSDOC Field CASE_START          */
    public static final String CASE_START = "CSrt";

    /** NEEDSDOC Field CASE_STOP          */
    public static final String CASE_STOP = "CStp";

    /** NEEDSDOC Field USER_TIMER          */
    public static final String USER_TIMER = "UTmr";

    /** NEEDSDOC Field USER_TIMESTAMP          */
    public static final String USER_TIMESTAMP = "UTim";

    /** NEEDSDOC Field USER_MEMORY          */
    public static final String USER_MEMORY = "UMem";

    /** NEEDSDOC Field PERF_SEPARATOR          */
    public static final String PERF_SEPARATOR = ";";

    /**
     * Logs out a performance statistic.
     * <p>Only logs times if perfLogging set to true.</p>
     * <p>As an optimization for record-based Loggers, this is a rather simplistic
     * way to log performance info - however it's sufficient for most purposes.</p>
     * @author Frank Bell
     * @param type type of performance statistic.
     * @param data long value of performance statistic.
     * @param msg comment to log out.
     */
    public void logPerfMsg(String type, long data, String msg)
    {

        if (getPerfLogging())
        {
            double dummy = 0;

            for (int i = 0; i < numLoggers; i++)
            {

                // NEEDSWORK: simply put it at the current loggingLevel we have set
                //            Is there a better way to mesh performance output with the rest?
                loggers[i].logStatistic(loggingLevel, data, dummy,
                                        type + PERF_SEPARATOR + msg);
            }
        }
    }

    /**
     * Captures current time in milliseconds, only if perfLogging.
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    protected Hashtable perfTimers = new Hashtable();

    /**
     * NEEDSDOC Method startTimer 
     *
     *
     * NEEDSDOC @param msg
     */
    public void startTimer(String msg)
    {

        // Note optimization: only capture times if perfLogging
        if ((perfLogging) && (msg != null))
        {
            perfTimers.put(msg, new Long(System.currentTimeMillis()));
        }
    }

    /**
     * Captures current time in milliseconds and logs out difference.
     * Will only log times if perfLogging set to true.
     * <p>Only logs time if it finds a corresponding msg entry that was startTimer'd.</p>
     * @author Shane_Curcuru@lotus.com
     * @param msg comment to log out.
     */
    public void stopTimer(String msg)
    {

        // Capture time immediately to reduce latency
        long stopTime = System.currentTimeMillis();

        // Note optimization: only use times if perfLogging
        if ((perfLogging) && (msg != null))
        {
            Long startTime = (Long) perfTimers.get(msg);

            logPerfMsg(USER_TIMER, (stopTime - startTime.longValue()), msg);
            perfTimers.remove(msg);
        }
    }

    /**
     * Accessor for currently running test case number, read-only.
     * @return current test case number.
     */
    public int getCurrentCaseNum()
    {
        return caseNum;
    }

    /**
     * Accessor for current test case's result, read-only.
     * @return current test case result.
     */
    public int getCurrentCaseResult()
    {
        return caseResult;
    }

    /**
     * Accessor for current test case's description, read-only.
     * @return current test case result.
     */
    public String getCurrentCaseComment()
    {
        return caseComment;
    }

    /**
     * Accessor for overall test file result, read-only.
     * @return test file's overall result.
     */
    public int getCurrentFileResult()
    {
        return testResult;
    }

    /**
     * Utility method to log out overall result counters.  
     *
     * @param count number of this kind of result
     * @param desc description of this kind of result
     */
    protected void logResultsCounter(int count, String desc)
    {

        // Optimization: Only log the kinds of results we have
        if (count > 0)
            logStatistic(loggingLevel, count, 0, desc);
    }

    /** Utility method to log out overall result counters. */
    public void logResultsCounters()
    {

        // NEEDSWORK: what's the best format to display this stuff in?
        // NEEDSWORK: what loggingLevel should we use?
        // NEEDSWORK: temporarily skipping the 'files' since 
        //            we only have tests with one file being run
        // logResultsCounter(incpCount[FILES], "incpCount[FILES]");
        logResultsCounter(incpCount[CASES], "incpCount[CASES]");
        logResultsCounter(incpCount[CHECKS], "incpCount[CHECKS]");

        // logResultsCounter(passCount[FILES], "passCount[FILES]");
        logResultsCounter(passCount[CASES], "passCount[CASES]");
        logResultsCounter(passCount[CHECKS], "passCount[CHECKS]");

        // logResultsCounter(ambgCount[FILES], "ambgCount[FILES]");
        logResultsCounter(ambgCount[CASES], "ambgCount[CASES]");
        logResultsCounter(ambgCount[CHECKS], "ambgCount[CHECKS]");

        // logResultsCounter(failCount[FILES], "failCount[FILES]");
        logResultsCounter(failCount[CASES], "failCount[CASES]");
        logResultsCounter(failCount[CHECKS], "failCount[CHECKS]");

        // logResultsCounter(errrCount[FILES], "errrCount[FILES]");
        logResultsCounter(errrCount[CASES], "errrCount[CASES]");
        logResultsCounter(errrCount[CHECKS], "errrCount[CHECKS]");
    }

    /** 
     * Utility method to store overall result counters. 
     *
     * @return a Hashtable of various results items suitable for
     * passing to logElement as attrs
     */
    protected Hashtable createResultsStatusHash()
    {
        Hashtable resHash = new Hashtable();
        if (incpCount[CASES] > 0)
            resHash.put(INCP + "-cases", new Integer(incpCount[CASES]));
        if (incpCount[CHECKS] > 0)
            resHash.put(INCP + "-checks", new Integer(incpCount[CHECKS]));

        if (passCount[CASES] > 0)
            resHash.put(PASS + "-cases", new Integer(passCount[CASES]));
        if (passCount[CHECKS] > 0)
            resHash.put(PASS + "-checks", new Integer(passCount[CHECKS]));

        if (ambgCount[CASES] > 0)
            resHash.put(AMBG + "-cases", new Integer(ambgCount[CASES]));
        if (ambgCount[CHECKS] > 0)
            resHash.put(AMBG + "-checks", new Integer(ambgCount[CHECKS]));

        if (failCount[CASES] > 0)
            resHash.put(FAIL + "-cases", new Integer(failCount[CASES]));
        if (failCount[CHECKS] > 0)
            resHash.put(FAIL + "-checks", new Integer(failCount[CHECKS]));

        if (errrCount[CASES] > 0)
            resHash.put(ERRR + "-cases", new Integer(errrCount[CASES]));
        if (errrCount[CHECKS] > 0)
            resHash.put(ERRR + "-checks", new Integer(errrCount[CHECKS]));
        return resHash;
    }

    /** 
     * Utility method to write out overall result counters. 
     * 
     * <p>This writes out both a testsummary element as well as 
     * writing a separate marker file for the test's currently 
     * rolled-up test results.</p>
     *
     * <p>Note if writeFile is true, we do a bunch of additional 
     * processing, including deleting any potential marker 
     * files, along with creating a new marker file.  This section 
     * of code explicitly does file creation and also includes 
     * some basic XML-isms in it.</p>
     * 
     * <p>Marker files look like: [testStat][testName].xml, where 
     * testStat is the actual current status, like 
     * Pass/Fail/Ambg/Errr/Incp, and testName comes from the 
     * currently executing test; this may be overridden by 
		  * setting OPT_SUMMARYFILE.</p>
     *
     * @param writeFile if we should also write out a separate 
     * Passname/Failname marker file as well
     */
    public void writeResultsStatus(boolean writeFile)
    {
        final String DEFAULT_SUMMARY_NAME = "ResultsSummary.xml";
        Hashtable resultsHash = createResultsStatusHash();
        resultsHash.put("desc", testComment);
        resultsHash.put("testName", testName);
        //@todo the actual path in the property below may not necessarily 
        //  either exist or be the correct location vis-a-vis the file
        //  that we're writing out - but it should be close
        resultsHash.put(OPT_LOGFILE, reporterProps.getProperty(OPT_LOGFILE, DEFAULT_SUMMARY_NAME));
        try
        {
            resultsHash.put("baseref", System.getProperty("user.dir"));
        } 
        catch (Exception e) { /* no-op, ignore */ }

        String elementName = "teststatus";
        String overallResult = resultToString(getCurrentFileResult());
        // Ask each of our loggers to report this
        for (int i = 0; i < numLoggers; i++)
        {
            loggers[i].logElement(CRITICALMSG, elementName, resultsHash, overallResult);
        }

        // Only continue if user asked us to
        if (!writeFile)
            return;

        // Now write an actual file out as a marker for enclosing 
        //  harnesses and build environments

        // Calculate the name relative to any logfile we have
        String logFileBase = null;
        try
        {
            // CanonicalPath gives a better path, especially if 
            //  you mix your path separators up
            logFileBase = (new File(reporterProps.getProperty(OPT_LOGFILE, DEFAULT_SUMMARY_NAME))).getCanonicalPath();
        } 
        catch (IOException ioe)
        {
            logFileBase = (new File(reporterProps.getProperty(OPT_LOGFILE, DEFAULT_SUMMARY_NAME))).getAbsolutePath();
        }
        logFileBase = (new File(logFileBase)).getParent();
		 		 // Either use the testName or an optionally set summary name
		 		 String summaryFileBase = reporterProps.getProperty(OPT_SUMMARYFILE, testName + ".xml");
        final File[] summaryFiles = 
        {
            // Note array is ordered; should be re-designed so this doesn't matter
            // Coordinate PASS name with results.marker in build.xml
            // File name rationale: put Pass/Fail/etc first, so they 
            //  all show up together in dir listing; include 
            //  testName so you know where it came from; make it 
            //  .xml since it is an XML file
            new File(logFileBase, INCP + "-" + summaryFileBase),
            new File(logFileBase, PASS + "-" + summaryFileBase),
            new File(logFileBase, AMBG + "-" + summaryFileBase),
            new File(logFileBase, FAIL + "-" + summaryFileBase),
            new File(logFileBase, ERRR + "-" + summaryFileBase)
        };
        // Clean up any pre-existing files that might be confused 
        //  as markers from this testrun
        for (int i = 0; i < summaryFiles.length; i++)
        {
            if (summaryFiles[i].exists())
                summaryFiles[i].delete();
        }

        File summaryFile = null;
        switch (getCurrentFileResult())
        {
            case INCP_RESULT:
                summaryFile = summaryFiles[0];
                break;
            case PASS_RESULT:
                summaryFile = summaryFiles[1];
                break;
            case AMBG_RESULT:
                summaryFile = summaryFiles[2];
                break;
            case FAIL_RESULT:
                summaryFile = summaryFiles[3];
                break;
            case ERRR_RESULT:
                summaryFile = summaryFiles[4];
                break;
            default:
                // Use error case, this should never happen
                summaryFile = summaryFiles[4];
                break;
        }
		 		 resultsHash.put(OPT_SUMMARYFILE, summaryFile.getPath());
        // Now actually write out the summary file
        try
        {
            PrintWriter printWriter = new PrintWriter(new FileWriter(summaryFile));
            // Fake the output of Logger.logElement mostly; except 
            //  we add an XML header so this is a legal XML doc
            printWriter.println("<?xml version=\"1.0\"?>"); 
            printWriter.println("<" + elementName); 
            for (Enumeration keys = resultsHash.keys();
                    keys.hasMoreElements(); /* no increment portion */ )
            {
                Object key = keys.nextElement();
                printWriter.println(key + "=\"" + resultsHash.get(key) + "\"");
            }
            printWriter.println(">"); 
            printWriter.println(overallResult); 
            printWriter.println("</" + elementName + ">"); 
            printWriter.close();
        }
        catch(Exception e)
        {
            logErrorMsg("writeResultsStatus: Can't write: " + summaryFile);
        }
    }

    //-----------------------------------------------------
    //-------- Test results reporting check* routines --------
    //-----------------------------------------------------

    /**
     * Compares actual and expected, and logs the result, pass/fail.
     * The comment you pass is added along with the pass/fail, of course.
     * Currenly, you may pass a pair of any of these simple {type}:
     * <ui>
     * <li>boolean</li>
     * <li>byte</li>
     * <li>short</li>
     * <li>int</li>
     * <li>long</li>
     * <li>float</li>
     * <li>double</li>
     * <li>String</li>
     * </ui>
     * <p>While tests could simply call checkPass(comment), providing these convenience
     * method can save lines of code, since you can replace:</p>
     * <code>if (foo = bar) <BR>
     *           checkPass(comment); <BR>
     *       else <BR>
     *           checkFail(comment);</code>
     * <p>With the much simpler:</p>
     * <code>check(foo, bar, comment);</code>
     * <p>Plus, you can either use or ignore the boolean return value.</p>
     * <p>Note that individual methods checkInt(...), checkLong(...), etc. also exist.
     * These type-independent overriden methods are provided as a convenience to
     * Java-only testwriters.  JavaScript scripts must call the
     * type-specific checkInt(...), checkString(...), etc. methods directly.</p>
     * <p>Note that testwriters are free to ignore the boolean return value.</p>
     * @author Shane_Curcuru@lotus.com
     * @param actual value returned from your test code.
     * @param expected value that test should return to pass.
     * @param comment to log out with result.
     * @return status, true=pass, false otherwise
     * @see #checkPass
     * @see #checkFail
     * @see #checkObject
     */
    public boolean check(boolean actual, boolean expected, String comment)
    {
        return (checkBool(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(byte actual, byte expected, String comment)
    {
        return (checkByte(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(short actual, short expected, String comment)
    {
        return (checkShort(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(int actual, int expected, String comment)
    {
        return (checkInt(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(long actual, long expected, String comment)
    {
        return (checkLong(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(float actual, float expected, String comment)
    {
        return (checkFloat(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(double actual, double expected, String comment)
    {
        return (checkDouble(actual, expected, comment));
    }

    /**
     * NEEDSDOC Method check 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (check) @return
     */
    public boolean check(String actual, String expected, String comment)
    {
        return (checkString(actual, expected, comment));
    }

    // No check(Object, Object, String) currently provided, please call checkObject(...) directly

    /**
     * Compares actual and expected (Object), and logs the result, pass/fail.
     * <p><b>Special note for checkObject:</b></p>
     * <p>Since this takes an object reference and not a primitive type,
     * it works slightly differently than other check{Type} methods.</p>
     * <ui>
     * <li>If both are null, then Pass</li>
     * <li>Else If actual.equals(expected) than Pass</li>
     * <li>Else Fail</li>
     * </ui>
     * @author Shane_Curcuru@lotus.com
     * @param actual Object returned from your test code.
     * @param expected Object that test should return to pass.
     * @param comment to log out with result.
     * @see #checkPass
     * @see #checkFail
     * @see #check
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean checkObject(Object actual, Object expected, String comment)
    {

        // Pass if both null, or both valid & equals
        if (actual != null)
        {
            if (actual.equals(expected))
            {
                checkPass(comment);

                return true;
            }
            else
            {
                checkFail(comment);

                return false;
            }
        }
        else
        {  // actual is null, so can't use .equals
            if (expected == null)
            {
                checkPass(comment);

                return true;
            }
            else
            {
                checkFail(comment);

                return false;
            }
        }
    }

    /**
     * NEEDSDOC Method checkBool 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkBool) @return
     */
    public boolean checkBool(boolean actual, boolean expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkByte 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkByte) @return
     */
    public boolean checkByte(byte actual, byte expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkShort 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkShort) @return
     */
    public boolean checkShort(short actual, short expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkInt 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkInt) @return
     */
    public boolean checkInt(int actual, int expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkLong 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkLong) @return
     */
    public boolean checkLong(long actual, long expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkFloat 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkFloat) @return
     */
    public boolean checkFloat(float actual, float expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * NEEDSDOC Method checkDouble 
     *
     *
     * NEEDSDOC @param actual
     * NEEDSDOC @param expected
     * NEEDSDOC @param comment
     *
     * NEEDSDOC (checkDouble) @return
     */
    public boolean checkDouble(double actual, double expected, String comment)
    {

        if (actual == expected)
        {
            checkPass(comment);

            return true;
        }
        else
        {
            checkFail(comment);

            return false;
        }
    }

    /**
     * Compares actual and expected (String), and logs the result, pass/fail.
     * <p><b>Special note for checkString:</b></p>
     * <p>Since this takes a String object and not a primitive type,
     * it works slightly differently than other check{Type} methods.</p>
     * <ui>
     * <li>If both are null, then Pass</li>
     * <li>Else If actual.compareTo(expected) == 0 than Pass</li>
     * <li>Else Fail</li>
     * </ui>
     * @author Shane_Curcuru@lotus.com
     * @param actual String returned from your test code.
     * @param expected String that test should return to pass.
     * @param comment to log out with result.
     * @see #checkPass
     * @see #checkFail
     * @see #checkObject
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean checkString(String actual, String expected, String comment)
    {

        // Pass if both null, or both valid & equals
        if (actual != null)
        {

            // .compareTo returns 0 if the strings match lexicographically
            if ((expected != null) && (actual.compareTo(expected) == 0))
            {
                checkPass(comment);

                return true;
            }
            else
            {
                checkFail(comment);

                return false;
            }
        }
        else
        {  // actual is null, so can't use .equals
            if (expected == null)
            {
                checkPass(comment);

                return true;
            }
            else
            {
                checkFail(comment);

                return false;
            }
        }
    }

    /**
     * Uses an external CheckService to Compares actual and expected,
     * and logs the result, pass/fail.
     * <p>CheckServices may be implemented to do custom equivalency
     * checking between complex object types. It is the responsibility
     * of the CheckService to call back into us to report results.</p>
     * @author Shane_Curcuru@lotus.com
     * @param CheckService implementation to use
     *
     * @param service a non-null CheckService implementation for 
     * this type of actual and expected object
     * @param actual Object returned from your test code.
     * @param expected Object that test should return to pass.
     * @param comment to log out with result.
     * @return status true if PASS_RESULT, false otherwise
     * @see #checkPass
     * @see #checkFail
     * @see #check
     */
    public boolean check(CheckService service, Object actual,
                         Object expected, String comment)
    {

        if (service == null)
        {
            checkErr("CheckService null for: " + comment);

            return false;
        }

        if (service.check(this, actual, expected, comment) == PASS_RESULT)
            return true;
        else
            return false;
    }

    /**
     * Uses an external CheckService to Compares actual and expected,
     * and logs the result, pass/fail.
     */
    public boolean check(CheckService service, Object actual,
                         Object expected, String comment, String id)
    {

        if (service == null)
        {
            checkErr("CheckService null for: " + comment);

            return false;
        }

        if (service.check(this, actual, expected, comment, id) == PASS_RESULT)
            return true;
        else
            return false;
    }

    /** Flag to control internal debugging of Reporter; sends extra info to System.out. */
    protected boolean debug = false;

    /**
     * Accessor for internal debugging flag.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean getDebug()
    {
        return (debug);
    }

    /**
     * Accessor for internal debugging flag.  
     *
     * NEEDSDOC @param setDbg
     */
    public void setDebug(boolean setDbg)
    {

        debug = setDbg;

        debugPrintln("setDebug enabled");  // will only print if setDbg was true
    }

    /**
     * Basic debugging output wrapper for Reporter.  
     *
     * NEEDSDOC @param msg
     */
    public void debugPrintln(String msg)
    {

        if (!debug)
            return;

        // If we have reporters, use them
        if (numLoggers > 0)
            logCriticalMsg("RI.dP: " + msg);

            // Otherwise, just dump to the console
        else
            System.out.println("RI.dP: " + msg);
    }

    /**
     * Utility method to increment result counters.  
     *
     * NEEDSDOC @param ctrOffset
     * NEEDSDOC @param r
     */
    public void incrementResultCounter(int ctrOffset, int r)
    {

        switch (r)
        {
        case INCP_RESULT :
            incpCount[ctrOffset]++;
            break;
        case PASS_RESULT :
            passCount[ctrOffset]++;
            break;
        case AMBG_RESULT :
            ambgCount[ctrOffset]++;
            break;
        case FAIL_RESULT :
            failCount[ctrOffset]++;
            break;
        case ERRR_RESULT :
            errrCount[ctrOffset]++;
            break;
        default :
            ;  // NEEDSWORK: should we report this, or allow users to add their own counters?
        }
    }

    /**
     * Utility method to translate an int result to a string.  
     *
     * NEEDSDOC @param r
     *
     * NEEDSDOC ($objectName$) @return
     */
    public static String resultToString(int r)
    {

        switch (r)
        {
        case INCP_RESULT :
            return (INCP);
        case PASS_RESULT :
            return (PASS);
        case AMBG_RESULT :
            return (AMBG);
        case FAIL_RESULT :
            return (FAIL);
        case ERRR_RESULT :
            return (ERRR);
        default :
            return ("Unkn");  // NEEDSWORK: should have better constant for this
        }
    }
}  // end of class Reporter

