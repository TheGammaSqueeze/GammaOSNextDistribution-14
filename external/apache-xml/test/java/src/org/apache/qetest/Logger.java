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
 * Logger.java
 *
 */
package org.apache.qetest;

import java.util.Hashtable;
import java.util.Properties;

/**
 * Interface defining a utility that can log out test results.
 * This interface defnines a standalone utility that can be used
 * to report the results of a test.  It would commonly be used by a
 * testing utility library to produce actual output from the run
 * of a test file.
 * <p>The Logger defines a minimal interface for expressing the result
 * of a test in a generic manner.  Different Loggers can be written
 * to both express the results in different places (on a live console,
 * in a persistent file, over a network) and in different formats -
 * perhaps an XMLTestLogger would express the results in an
 * XML file or object.</p>
 * <p>In many cases, tests will actually call a Reporter, which
 * acts as a composite for Logger objects, and includes numerous
 * useful utility and convenience methods.</p>
 * <ul>Loggers explicitly have a restricted set of logging calls for
 * two main reasons:
 * <li>To help keep tests structured</li>
 * <li>To make it easier to generate 'reports' based on test output
 * (i.e. number of tests passed/failed, graphs of results, etc.)</li>
 * </ul>
 * <p>While there are a number of very good general purpose logging 
 * utilities out there, I prefer to use this Logger or a similar 
 * class to report the results of tests because this class 
 * specifically constrains the format and manner that a test reports 
 * results.  This should help to keep tests more structured and 
 * easier for other users to understand.  Specific Logger 
 * implementations may of course choose to store or log out their 
 * results in any manner they like; in fact I plan on implementing 
 * a <a href="http://jakarta.apache.org/log4j/docs/index.html">
 * Log4JLogger</a> class in the future.</p>
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public interface Logger
{

    //-----------------------------------------------------
    //-------- Constants for common input params --------
    //-----------------------------------------------------

    /**
     * Parameter: FQCN of Logger(s) to use.
     * <p>Default: usually none, but implementers may choose to call
     * setupDefaultLogger(). Will accept multiple classnames separated
     * by ";" semicolon. Format:
     * <code>-reporters org.apache.qetest.ConsoleLogger;org.apache.qetest.SomeOtherLogger</code></p>
     */
    public static final String OPT_LOGGERS = "loggers";

    /** Constant ';' semicolon for delimiter in OPT_LOGGERS field.   */
    public static final String LOGGER_SEPARATOR = ";";

    /**
     * A default Logger classname - ConsoleLogger.
     * //@todo have a factory creation service for this so that any 
     * test or testlet that wishes a default logger at startup 
     * can simply ask a common place for one.
     */
    public static final String DEFAULT_LOGGER =
        "org.apache.qetest.ConsoleLogger";

    /**
     * Parameter: level of output to log, int 0-99.
     * {@link #CRITICALMSG CRITICALMSG = 0}, only very important 
     * messages are output from the test or shown in a report, to
     * {@link #TRACEMSG TRACEMSG}, where all messages are output
     * or shown in the result.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart of levels.}
     */
    public static final String OPT_LOGGINGLEVEL = "loggingLevel";

    /**
     * Parameter: if we should log performance data, true/false.
     */
    public static final String OPT_PERFLOGGING = "perfLogging";

    /**
     * Parameter: if we should dump debugging info to System.err.
     * <p>This is primarily for debugging the test framework itself, 
     * not necessarily for debugging the application under test.</p>
     */
    public static final String OPT_DEBUG = "debug";

    /**
     * Parameter: Name of test results file for file-based Loggers.
     * <p>File-based loggers should use this key as an initializer
     * for the name of their output file.</p>
     * <p>Commandline Format: <code>-logFile path\to\ResultsFileName.ext</code></p>
     * <p>Properties file Format: <code>logFile=path\\to\\ResultsFileName.ext</code></p>
     * //@todo Need more doc about platform separator differences
     */
    public static final String OPT_LOGFILE = "logFile";

    /**
     * Parameter: Indent depth for console or HTML/XML loggers.
     * <p>Loggers may use this as an integer number of spaces to
     * indent, as applicable to their situation.</p>
     * <p>Commandline Format: <code>-indent <i>nn</i></code></p>
     * <p>Properties file Format: <code>indent=<i>nn</i></code></p>
     */
    public static final String OPT_INDENT = "indent";

    //-----------------------------------------------------
    //-------- Constants for Logger and Reporter interactions --------
    //-----------------------------------------------------

    /**
     * This determines the amount of data actually logged out to results.
     * <p>Loggers merely use these constants in their output formats.
     * Reporters will only call contained Loggers to report messages
     * at the current logging level and higher.
     * For example, if you <code>setLoggingLevel(ERRORMSG)</code>
     * (currently found in the {@link Reporter Reporter} subclass) then INFOMSGs
     * will not be reported, presumably speeding execution time and saving
     * output log space.  These levels are also coded into most Logger output,
     * allowing for easy reporting of various levels of results.</p>
     * <ul>Allowable values are:
     * <li>CRITICALMSG - Must-be-printed messages; may print only selected
     * fails (and skip printing most passes).</li>
     * <li>ERRORMSG - Logs an error and (optionally) a fail.</li>
     * <li>FAILSONLY - Skips logging out most pass messages (still
     * reports testFileResults) but prints out all fails.</li>
     * <li>WARNINGMSG - Used for non-fail warnings - the test will
     * continue, hopefully sucessfully.</li>
     * <li>STATUSMSG - Reports on basic status of the test, when you
     * want to include more detail than in a check() call</li>
     * <li>INFOMSG - For more basic test debugging messages.</li>
     * <li>TRACEMSG - Tracing all operations, detailed debugging information.</li>
     * </ul>
     * <p>Note that Logger implementations should also generally 
     * put the actual level that each call is logged at into any  
     * persistent outputs.  This will enable you to use results \
     * analysis tools later on against the results and screen 
     * against loggingLevel then as well.</p>
     * <p>A common technique is to run a set of tests with a very 
     * high loggingLevel, so most or all output is stored by Loggers, 
     * and then analyze the results with a low logging level.  If 
     * those results show some problems, you can always re-analyze 
     * the results with a higher logging level without having to 
     * re-run the test.</p>
     * @see #logMsg(int, java.lang.String)
     */
    // Levels are separated in actual values in case you wish to add your own levels in between
    public static final int CRITICALMSG = 0;  // Lowest possible loggingLevel

    /** 
     * ERRORMSG - Logs an error and (optionally) a fail.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int ERRORMSG = 10;

    /** 
     * FAILSONLY - Skips logging out most pass messages.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int FAILSONLY = 20;

    /** 
     * WARNINGMSG - Used for non-fail warnings.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int WARNINGMSG = 30;

    /** 
     * STATUSMSG - Reports on basic status of the test.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int STATUSMSG = 40;

    /** 
     * INFOMSG - For more basic test debugging messages.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int INFOMSG = 50;

    /** 
     * TRACEMSG - Tracing all operations.  
     * {@link #CRITICALMSG See CRITICALMSG for a chart.}
     */
    public static final int TRACEMSG = 60;  // Highest possible loggingLevel

    /** Default level is {@link #STATUSMSG STATUSMSG}.   */
    public static final int DEFAULT_LOGGINGLEVEL = STATUSMSG;

    /**
     * Constants for tracking results by testcase or testfile.
     * <p>Testfiles default to an incomplete or INCP_RESULT.  If a
     * test never successfully calls a check* method, it's result
     * will be incomplete.</p>
     * <p>Note that a test cannot explicitly reset it's result to be INCP.</p>
     */

    // Note: implementations should never rely on the actual values
    //       of these constants, except possibly to ensure that 
    //       overriding values are > greater than other values
    public static final int INCP_RESULT = 0;

    // Note: string representations are explicitly set to all be 
    //       4 characters long to make it simpler to parse results

    /** 
     * Constant "Incp" for result files.   
     * @see #INCP_RESULT
     */
    public static final String INCP = "Incp";

    /**
     * Constants for tracking results by testcase or testfile.
     * <p>A PASS_RESULT signifies that a specific test point (or a testcase,
     * or testfile) has perfomed an operation correctly and has been verified.</p>
     * <p>A pass overrides an incomplete.</p>
     * @see #checkPass(java.lang.String)
     */
    public static final int PASS_RESULT = 2;

    /** 
     * Constant "Pass" for result files.   
     * @see #PASS_RESULT
     */
    public static final String PASS = "Pass";

    /**
     * Constants for tracking results by testcase or testfile.
     * <p>An AMBG_RESULT or ambiguous result signifies that a specific test
     * point (or a testcase, or testfile) has perfomed an operation but
     * that it has not been verified.</p>
     * <p>Ambiguous results can be used when the test may not have access
     * to baseline, or verified 'gold' result data.  It may also be used
     * during test file creation when the tester has not yet specified the
     * expected behavior of a test.</p>
     * <p>Ambiguous overrides both pass and incomplete.</p>
     * @see #checkAmbiguous(java.lang.String)
     */
    public static final int AMBG_RESULT = 5;

    /** 
     * Constant "Ambg" for result files.   
     * @see #AMBG_RESULT
     */
    public static final String AMBG = "Ambg";

    /**
     * Constants for tracking results by testcase or testfile.
     * <p>A FAIL_RESULT signifies that a specific test point (or a testcase,
     * or testfile) has perfomed an operation incorrectly.</p>
     * <p>A fail in one test point does not necessarily mean that other test
     * points are invalid - well written tests should be able to continue
     * and produce valid results for the rest of the test file.</p>
     * <p>A fail overrides any of incomplete, pass or ambiguous.</p>
     * @see #checkFail(java.lang.String)
     */
    public static final int FAIL_RESULT = 8;

    /** 
     * Constant "Fail" for result files.   
     * @see #FAIL_RESULT
     */
    public static final String FAIL = "Fail";

    /**
     * Constants for tracking results by testcase or testfile.
     * <p>An ERRR_RESULT signifies that some part of the testfile
     * has caused an unexpected error, exception, or other Really Bad Thing.</p>
     * <p>Errors signify that something unexpected happened and that the test
     * may not produce valid results.  It would most commonly be used for
     * problems relating to setting up test data or errors with other software
     * being used (i.e. not problems with the actual software code that the
     * test is attempting to verify).</p>
     * <p>An error overrides <B>any</B> other result.</p>
     * @see #checkErr(java.lang.String)
     */
    public static final int ERRR_RESULT = 9;

    /** 
     * Constant "Errr" for result files.   
     * Note all constant strings for results are 4 chars long
     * to make fixed-length record file-based results simpler.
     * @see #ERRR_RESULT
     */
    public static final String ERRR = "Errr";

    /**
     * Testfiles and testcases should default to incomplete.
     */
    public final int DEFAULT_RESULT = INCP_RESULT;

    //-----------------------------------------------------
    //-------- Control and utility routines --------
    //-----------------------------------------------------

    /**
     * Return a description of what this Logger/Reporter does.
     * @author Shane_Curcuru@lotus.com
     * @return description of how this Logger outputs results, OR
     * how this Reporter uses Loggers, etc..
     */
    public abstract String getDescription();

    /**
     * Returns information about the Property name=value pairs that
     * are understood by this Logger/Reporter.
     * @author Shane_Curcuru@lotus.com
     * @return same as {@link java.applet.Applet#getParameterInfo()}.
     */
    public abstract String[][] getParameterInfo();

    /**
     * Accessor methods for a properties block.
     * @return our Properties block.
     */
    public abstract Properties getProperties();

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
    public abstract void setProperties(Properties p);

    /**
     * Call once to initialize this Logger/Reporter from Properties.
     * <p>Simple hook to allow Logger/Reporters with special output
     * items to initialize themselves.</p>
     *
     * @author Shane_Curcuru@lotus.com
     * @param p Properties block to initialize from.
     * @return status, true if OK, false if an error occoured.
     */
    public abstract boolean initialize(Properties p);

    /**
     * Is this Logger/Reporter ready to log results?
     * Obviously the meaning of 'ready' may be slightly different 
     * between different kinds of Loggers.  A ConsoleLogger may 
     * simply always be ready, since it probably just sends it's 
     * output to System.out.  File-based Loggers may not be ready 
     * until they've opened their file, and if IO errors happen, 
     * may have to report not ready later on as well.
     * @author Shane_Curcuru@lotus.com
     * @return status - true if it's ready to report, false otherwise
     */
    public abstract boolean isReady();

    /**
     * Flush this Logger/Reporter - should ensure all output is flushed.
     * Note that the flush operation is not necessarily pertinent to
     * all types of Logger/Reporter - console-type Loggers no-op this.
     * @author Shane_Curcuru@lotus.com
     */
    public abstract void flush();

    /**
     * Close this Logger/Reporter - should include closing any OutputStreams, etc.
     * Logger/Reporters should return {@link #isReady()} = false 
     * after being closed; presumably they will not actually log 
     * out any further data.
     * @author Shane_Curcuru@lotus.com
     */
    public abstract void close();

    //-----------------------------------------------------
    //-------- Testfile / Testcase start and stop routines --------
    //-----------------------------------------------------

    /**
     * Report that a testfile has started.
     * Implementing Loggers must output/store/report a message
     * that the test file has started.
     * @author Shane_Curcuru@lotus.com
     * @param name file name or tag specifying the test.
     * @param comment comment about the test.
     */
    public abstract void testFileInit(String name, String comment);

    /**
     * Report that a testfile has finished, and report it's result.
     * Implementing Loggers must output a message that the test is
     * finished, and print the results.
     * @author Shane_Curcuru@lotus.com
     * @param msg message to log out
     * @param result result of testfile
     */
    public abstract void testFileClose(String msg, String result);

    /**
     * Report that a testcase has started.
     * @author Shane_Curcuru@lotus.com
     * @param comment short description of this test case's objective.
     */
    public abstract void testCaseInit(String comment);

    /**
     * Report that a testcase has finished, and report it's result.
     * Implementing classes must output a message that a testcase is
     * finished, and print the results.
     * @author Shane_Curcuru@lotus.com
     * @param msg message of name of test case to log out
     * @param result result of testfile
     */
    public abstract void testCaseClose(String msg, String result);

    //-----------------------------------------------------
    //-------- Test results logging routines --------
    //-----------------------------------------------------

    /**
     * Report a comment to result file with specified severity.
     * Print out the message, optionally along with the level (depends
     * on your storage mechanisim: console output probably doesn't need
     * the level, but a file output probably would want it.)
     * <p>Note that some Loggers may limit the comment string,
     * either in overall length or by stripping any linefeeds, etc.
     * This is to allow for optimization of file or database-type
     * reporters with fixed fields.  Users who need to log out
     * special string data should use logArbitrary() instead.</p>
     * <p>Remember, use {@link #checkPass(String)}, or
     * {@link #checkFail(String)}, etc. to report the actual
     * results of your tests.</p>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message; from {@link #CRITICALMSG} 
     * to {@link #TRACEMSG}
     * @param msg comment to log out.
     */
    public abstract void logMsg(int level, String msg);

    /**
     * Report an arbitrary String to result file with specified severity.
     * Log out the String provided exactly as-is.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message; from {@link #CRITICALMSG} 
     * to {@link #TRACEMSG}
     * @param msg arbitrary String to log out.
     */
    public abstract void logArbitrary(int level, String msg);

    /**
     * Logs out statistics to result file with specified severity.
     * This is a general-purpose way to log out numeric statistics.  We accept
     * both a long and a double to allow users to save whatever kind of numbers
     * they need to, with the simplest API.  The actual meanings of the numbers
     * are dependent on the implementer.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message; from {@link #CRITICALMSG} 
     * to {@link #TRACEMSG}
     * @param lVal statistic in long format, if available.
     * @param dVal statistic in double format, if available.
     * @param msg comment to log out.
     */
    public abstract void logStatistic(int level, long lVal, double dVal,
                                      String msg);

    /**
     * Logs out Throwable.toString() and a stack trace of the 
     * Throwable with the specified severity.
     * <p>Works in conjunction with {@link Reporter#setLoggingLevel(int) setLoggingLevel}; 
     * only outputs messages that are more severe than the current 
     * logging level.  Different Logger or Reporter implementations 
     * may implement loggingLevels in different ways currently.</p>
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
    public abstract void logThrowable(int level, Throwable throwable, String msg);

    /**
     * Logs out a element to results with specified severity.
     * This method is primarily for Loggers that output to fixed
     * structures, like files, XML data, or databases.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message; from {@link #CRITICALMSG} 
     * to {@link #TRACEMSG}
     * @param element name of enclosing element
     * @param attrs hash of name=value attributes
     * @param msg Object to log out; up to Loggers to handle
     * processing of this; usually logs just .toString().
     */
    public abstract void logElement(int level, String element,
                                    Hashtable attrs, Object msg);

    /**
     * Logs out contents of a Hashtable with specified severity.
     * <p>Loggers should store or log the full contents of the hashtable.</p>
     * @param level severity of message; from {@link #CRITICALMSG} 
     * to {@link #TRACEMSG}
     * @param hash Hashtable to log the contents of.
     * @param msg decription of the Hashtable.
     */
    public abstract void logHashtable(int level, Hashtable hash, String msg);

    //-----------------------------------------------------
    //-------- Test results reporting check* routines --------
    //-----------------------------------------------------
    // There is no public void checkIncp(String comment) method

    /**
     * Writes out a Pass record with comment.  
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the pass record.
     * @see #PASS_RESULT
     */
    public abstract void checkPass(String comment);

    /**
     * Writes out an ambiguous record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the ambg record.
     * @see #AMBG_RESULT
     */
    public abstract void checkAmbiguous(String comment);

    /**
     * Writes out a Fail record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the fail record.
     * @see #FAIL_RESULT
     */
    public abstract void checkFail(String comment);

    /**
     * Writes out an Error record with comment.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the error record.
     * @see #ERRR_RESULT
     */
    public abstract void checkErr(String comment);


    /* EXPERIMENTAL: have duplicate set of check*() methods 
       that all output some form of ID as well as comment. 
       Leave the non-ID taking forms for both simplicity to the 
       end user who doesn't care about IDs as well as for 
       backwards compatibility.
    */
    
    /**
     * Writes out a Pass record with comment and ID.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the pass record.
     * @param ID token to log with the pass record.
     * @see #PASS_RESULT
     */
    public abstract void checkPass(String comment, String id);

    /**
     * Writes out an ambiguous record with comment and ID.
     * @author Shane_Curcuru@lotus.com
     * @param comment to log with the ambg record.
     * @param ID token to log with the pass record.
     * @see #AMBG_RESULT
     */
    public abstract void checkAmbiguous(String comment, String id);

    /**
     * Writes out a Fail record with comment and ID.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the fail record.
     * @param ID token to log with the pass record.
     * @see #FAIL_RESULT
     */
    public abstract void checkFail(String comment, String id);

    /**
     * Writes out an Error record with comment and ID.
     * @author Shane_Curcuru@lotus.com
     * @param comment comment to log with the error record.
     * @param ID token to log with the pass record.
     * @see #ERRR_RESULT
     */
    public abstract void checkErr(String comment, String id);
}  // end of class Logger

