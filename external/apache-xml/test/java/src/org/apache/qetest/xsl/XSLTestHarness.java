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
 * XSLTestHarness.java
 *
 */
package org.apache.qetest.xsl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Set;
import java.util.Iterator;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.Reporter;

//-------------------------------------------------------------------------

/**
 * Utility to run multiple FileBasedTest objects in a row.  
 * <p>Generally run from the command line and passed a list 
 * of tests to execute, the XSLTestHarness will run each test in 
 * order, saving the results of each test for reporting later.</p>
 * <p>User must have supplied minimal legal properties in the input 
 * Properties file: outputDir, inputDir, logFile, and tests.</p>
 * @todo update to accept per-test.properties and pass'em thru
 * @todo update to check for similarly named tests (in different pkgs)
 * @todo update TestReporter et al to better cover case when 
 *        user doesn't call testCaseClose (where do results go?)
 * @todo report on memory usage, etc.
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class XSLTestHarness
{

/**
 * Convenience method to print out usage information.
 * @return String denoting usage suitable for printing
 */
    public String usage()
    {
        FileBasedTest tmp = new FileBasedTest();
        return ("XSLTestHarness - execute multiple Tests in sequence and log results:\n"
                + "    Usage: java XSLTestHarness [-load] properties.prop\n"
                + "    Reads in all options from a Properties file:\n"
                + "    " + OPT_TESTS + "=semicolon;delimited;list;of FQCNs tests to run\n"
                + "    Most other options (in prop file only) are identical to FileBasedTest:\n"
                + tmp.usage()
                );
    }

    /** 
     * Various property names we're expecting.  
     * <ul>
     * <li>tests=TestOne;TestTwo;TestThree - semicolon-delimited list of 
     * TestClassNames to execute, in order; assumes all are in the 
     * org.apache.qetest. as base package currently (subject to change)</li>
     * <li>logFile=LogFileName - name of output XML file to store harness 
     * log data in (passed to Reporter; constant in FileBasedTest.java)</li>
     * <li>inputDir=path\\to\\tests - where the tests should find data</li>
     * <li>outputDir=path\\to\\output - where the tests should send output and results</li>
     * <li>goldDir=path\\to\\golds - where the tests should find gold files</li>
     * <li>loggingLevel=50 - how much output tests should produce</li>
     * <li>resultsViewer=Filename.xsl - reference to results processing stylesheet file</li>
     * <li>Any other options are passed as-is to individual tests</li>
     * </ul>
     * <p>Currently each test has it's own logFile in the outputDir, 
     * named after the test.</p>
     */

    /**
     * Parameter: semicolong delimited list of FQCN's of test names.
     * <p>Default: none - this parameter is required.  If the name 
     * is not package-complete, the harness may attempt to 'guess'
     * the correct package underneath org.apache.qetest.</p>
     */
    public static final String OPT_TESTS = "tests";

    /** Delimiter for OPT_TESTS.  */
    public static final String TESTS_DELIMITER = ";";

    /** 
     * We prepend the default package if any test name does not 
     * have a '.' in it.  
     * This is part of our 'guess' at the appropriate packagename.
     * <b>WARNING!</b> Subject to change!
     */
    public static final String DEFAULT_PACKAGE = "org.apache.qetest.";

    /** Separator character for package.ClassName.  */
    public static final String DOT = ".";

    /** Default extension for logFiles.  */
    public static final String LOG_EXTENSION = ".xml";

    /**
     * Generic Properties block for storing initialization info.
     * All startup options get stored in here for later use, both by
     * the test itself and by any Reporters we use.
     */
    protected Properties harnessProps;

    /** Our Reporter, who we tell all our secrets to.  */
    protected Reporter reporter;


    /**
     * Setup any options and construct a list of tests to execute.  
     * <p>Accesses our class variables harnessProps and debug.  
     * Must not use Reporter, since it hasn't been created yet.</p>
     * @param args array of command line arguments
     * @return array of testClassNames to execute; null if error
     */
    protected String[] doTestHarnessInit(String args[])
    {
        // Harness loads all info from one properties file
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
        try
        {
            // Load named file into our properties block
            FileInputStream fIS = new FileInputStream(propFileName);
            harnessProps = new Properties();
            harnessProps.load(fIS);
        } 
        catch (IOException ioe)
        {
            System.err.println("ERROR! loading properties file failed: " + propFileName);
            ioe.printStackTrace();
            return null;
        }

        // Grab the list of tests, which is specific only to the harness
        // String testNames = harnessProps.getProperty(OPT_TESTS);
        StringBuffer testNamesStrBuff = new StringBuffer();      
        Set<String> strPropNames = harnessProps.stringPropertyNames();
        Iterator<String> strPropIter = strPropNames.iterator();
        while (strPropIter.hasNext()) {
           String propName = strPropIter.next();
           if (propName.endsWith(DOT + OPT_TESTS)) {
              String propValue = harnessProps.getProperty(propName);
              testNamesStrBuff.append(propValue + TESTS_DELIMITER);
           }
        }

        String testNames = testNamesStrBuff.toString();
        if (testNames.length() > 0) {
           testNames = testNames.substring(0, testNames.length());
        } 

        if ((testNames == null) || (testNames.length() == 0))
        {
            System.err.println("ERROR! No tests(1) were supplied in the properties file!");
            return null;
        }

        // Split up the list of names
        StringTokenizer st = new StringTokenizer(testNames, TESTS_DELIMITER);
        int testCount = st.countTokens();
        if (testCount == 0)
        {
            System.err.println("ERROR! No tests(2) were supplied in the properties file!");
            return null;
        }
        String tests[] = new String[testCount];
        for (int i = 0; st.hasMoreTokens(); i++)
        {
            String s = st.nextToken();
            if (s.startsWith("org"))
            {   
                // Assume user specified complete package.ClassName
                tests[i] = s;
            }
            else
            {
                // Use QetestUtils to find the correct name.
                tests[i] = QetestUtils.testClassnameForName(s, QetestUtils.defaultPackages, null);
            }
        }
        // Munge the inputDir and goldDir to use platform path 
        //  separators if needed

        String tempS = harnessProps.getProperty(FileBasedTest.OPT_INPUTDIR);
        tempS = swapPathDelimiters(tempS);
        File tempF = new File(tempS);
        if (tempF.exists())
        {
            harnessProps.put(FileBasedTest.OPT_INPUTDIR, tempS);
        }
        else
        {
            System.err.println("ERROR! " + FileBasedTest.OPT_INPUTDIR + " property does not exist! " + tempS);
            return null;
        }
        tempS = harnessProps.getProperty(FileBasedTest.OPT_GOLDDIR);
        tempS = swapPathDelimiters(tempS);
        tempF = new File(tempS);
        if (tempF.exists())
        {
            harnessProps.put(FileBasedTest.OPT_GOLDDIR, tempS);
        }
        else
        {
            System.err.println("WARNING! " + FileBasedTest.OPT_GOLDDIR + " property does not exist! " + tempS);
        }

        // Also swap around path on outputDir, logFile
        tempS = harnessProps.getProperty(FileBasedTest.OPT_OUTPUTDIR);
        tempS = swapPathDelimiters(tempS);
        tempF = new File(tempS);
        if (tempF.exists())
        {
            harnessProps.put(FileBasedTest.OPT_OUTPUTDIR, tempS);
        }
        else
        {
            System.err.println("WARNING! " + FileBasedTest.OPT_OUTPUTDIR + " property does not exist! " + tempS);
        }

        tempS = harnessProps.getProperty(Logger.OPT_LOGFILE);
        tempS = swapPathDelimiters(tempS);
        harnessProps.put(Logger.OPT_LOGFILE, tempS);
        return tests;
    }

    /**
     * Update a path to use system-dependent delimiter.  
     *
     * Allow user to specify a system-dependent path in the 
     * properties file we're loaded from, but then let another 
     * user run the same files on another environment.
     *
     * I'm drawing a complete blank today on the classic way to 
     * do this, so don't be disappointed if you look at the code 
     * and it's goofy.
     */
    protected String swapPathDelimiters(String s)
    {
        if (null == s)
            return null;
        // If we're not on Windows, swap an apparent Windows-based 
        //  backslash separator with a forward slash separator
        // This is because I'm lazy and checkin .properties files 
        //  with Windows based paths, but want unix-based people 
        //  to be able to run the tests as-is
        if (File.separatorChar != '\\') 
            return s.replace('\\', File.separatorChar);
        else
            return s;
    }

    /**
     * Go run the available tests!  
     * <p>This is sort-of the equivalent of runTest() in a Test 
     * object.  Each test is run in order, and is the equivalent 
     * of a testCase for the Harness.  The Harness records a master 
     * log file, and each test puts its results in it's own log file.</p>
     */
    protected boolean runHarness(String testList[])
    {
        // Report that we've begun testing
        // Note that we're hackishly re-using the 'test' metaphor 
        //      on a grand scale here, where each of the harness'
        //      testCases corresponds to one entire Test
        reporter.testFileInit("Harness", "Harness executing " + testList.length + " tests");
        logHarnessProps();

        // Note 'passCount' is poorly named: a test may fail but 
        //  may still return true from runTest. You really have to 
        //  look at the result files to see real test status
        int passCount = 0;
        int nonPassCount = 0;
        // Run each test in order!
        for (int testIdx = 0; testIdx < testList.length; testIdx++)
        {
            boolean testStat = false;
            try
            {
                // This method logs out status to our log file, as well 
                //      as initializing and running the test
                testStat = runOneTest(testList[testIdx], harnessProps);
            }
            catch (Throwable t)
            {
                // Catch everything, log it, and move on
                reporter.checkErr("Test " + testList[testIdx] + " threw: " + t.toString());
                reporter.logThrowable(reporter.ERRORMSG, t, "Test " 
                                      + testList[testIdx] + " threw: " + t.toString());
            }
            finally
            {
                if (testStat)
                    passCount++;
                else
                    nonPassCount++;
            }
        }
        // Below line is not a 'check': each runOneTest call logs it's own status
        // Only for information; remember that the runTest status is not the pass/fail of the test!
        reporter.logCriticalMsg("All tests complete, testStatOK:" + passCount + " testStatNOTOK:" + nonPassCount);

        // Have the reporter write out a summary file for us
        reporter.writeResultsStatus(true);

        // Close reporter and return true only if all tests passed
        // Note the passCount/nonPassCount are misnomers, since they
        //  really only report if a test aborted, not passed
        reporter.testFileClose();
        if ((passCount < 0) && (nonPassCount == 0))
            return true;
        else
            return false;
    }


    /**
     * Run a single FileBasedTest and report it's results.  
     * <p>Uses our class field reporter to dump our results to, also 
     * creates a separate reporter for the test to use.</p>
     * <p>See the code for the specific initialization we custom-craft for 
     * each individual test.  Basically we clone our harnessProps, update the 
     * logFile and outputDir per test, and create a testReporter, then use these 
     * to initialize the test before we call runTest on it.</p>
     * @param testName FQCN of the test to execute; must be instanceof FileBasedTest
     * @param hProps property block to use as initializer
     * @return the pass/fail return from runTest(), which is not necessarily 
     *         the same as what we're going to log as the test's result
     */
    protected boolean runOneTest(String testName, Properties hProps)
    {
        // Report on what we're about to do
        reporter.testCaseInit("runOneTest:" + testName);

        // Validate our basic arguments
        if ((testName == null) || (testName.length() == 0) || (hProps == null))
        {
            reporter.checkErr("runOneTest called with bad arguments!");
            reporter.testCaseClose();
            return false;
        }

        // Calculate just the ClassName of the test for later use as the logFile name
        String bareClassName = null;
        StringTokenizer st = new StringTokenizer(testName, ".");
        for (bareClassName = st.nextToken(); st.hasMoreTokens(); bareClassName = st.nextToken())
        { /* empty loop body */
        }
        st = null; // no longer needed

        // Validate that the output directory exists for the test to put it's results in
        String testOutDir = hProps.getProperty(FileBasedTest.OPT_OUTPUTDIR);
        if ((testOutDir == null) || (testOutDir.length() == 0))
        {
            // Default to current dir plus the bareClassName if not set
            testOutDir = new String("." + File.separator + bareClassName);
        }
        else
        {
            // Append the bareClassName so different tests don't clobber each other
            testOutDir += File.separator + bareClassName;
        }
        File oDir = new File(testOutDir);
        if (!oDir.exists())
        {
            if (!oDir.mkdirs())
            {
                // Report this but keep going anyway
                reporter.logErrorMsg("Could not create testOutDir: " + testOutDir);
            }
        }
        // no longer needed
        oDir = null;

        // Validate we can instantiate the test object itself
        reporter.logTraceMsg("About to newInstance(" + testName + ")");
        FileBasedTest test = null;
        try
        {
            Class testClass = Class.forName(testName);
            test = (FileBasedTest)testClass.newInstance();
        }
        catch (Exception e1)
        {
            reporter.checkErr("Could not create test, threw: " + e1.toString());
            reporter.logThrowable(reporter.ERRORMSG, e1, "Could not create test, threw");
            reporter.testCaseClose();
            return false;
        }

        // Create a properties block for the test and pre-fill it with custom info
        //      Start with the harness' properties, and then replace certain values
        Properties testProps = (Properties)hProps.clone();
        testProps.put(FileBasedTest.OPT_OUTPUTDIR, testOutDir);
        testProps.put(Logger.OPT_LOGFILE, testOutDir + LOG_EXTENSION);

        // Disable the ConsoleReporter for the *individual* tests, it's too confusing
        testProps.put("noDefaultReporter", "true");
        reporter.logHashtable(reporter.INFOMSG, testProps, "testProps before test creation");

        // Initialize the test with the properties we created
        test.setProperties(testProps);
        boolean testInit = test.initializeFromProperties(testProps);
        reporter.logInfoMsg("Test(" + testName + ").initializeFromProperties() = " + testInit);

        // -----------------
        // Execute the test!
        // -----------------
        boolean runTestStat = test.runTest(testProps);

        // Report where the test stored it's results - future use 
        //  by multiViewResults.xsl or some other rolledup report
        // Note we should really handle the filenames here better, 
        //  especially for relative vs. absolute issues
        Hashtable h = new Hashtable(2);
        h.put("result", reporter.resultToString(test.getReporter().getCurrentFileResult()));
        h.put("fileRef", (String)testProps.get(Logger.OPT_LOGFILE));
        reporter.logElement(reporter.WARNINGMSG, "resultsfile", h, test.getTestDescription());
        h = null; // no longer needed

        // Call worker method to actually calculate the result and call check*()        
        logTestResult(bareClassName, test.getReporter().getCurrentFileResult(), 
                      runTestStat, test.getAbortTest());

        // Cleanup local variables and garbage collect, in case tests don't
        //      release all resources or something
        testProps = null;
        test = null;
        logMemory();    // Side effect: System.gc()
        
        reporter.testCaseClose();
        return runTestStat;
    }


    /**
     * Convenience method to report the result of a single test.  
     * <p>Depending on the test's return value, it's currentFileResult, 
     * and if it was ever aborted, we call check to our reporter to log it.</p>
     * @param testName basic name of the test
     * @param testResult result of whole test file
     * @param testStat return value from test.runTest()
     * @param testAborted if the test was aborted at all
     */
    protected void logTestResult(String testName, int testResult, boolean testStat, boolean testAborted)
    {
        // Report the 'rolled-up' results of the test, combining each of the above data
        switch (testResult)
        {
            case Logger.INCP_RESULT:
                // There is no 'checkIncomplete' method, so simply avoid calling check at all
                reporter.logErrorMsg(testName + ".runTest() returned INCP_RESULT!");
                break;
            case Logger.PASS_RESULT:
                // Only report a pass if it returned true and didn't abort
                if (testStat && (!testAborted))
                {
                    reporter.checkPass(testName + ".runTest()");
                }
                else
                {
                    // Assume something went wrong and call it an ERRR
                    reporter.checkErr(testName + ".runTest()");
                }
                break;
            case Logger.AMBG_RESULT:
                reporter.checkAmbiguous(testName + ".runTest()");
                break;
            case Logger.FAIL_RESULT:
                reporter.checkFail(testName + ".runTest()");
                break;
            case Logger.ERRR_RESULT:
                reporter.checkErr(testName + ".runTest()");
                break;
            default:
                // Assume something went wrong
                //  (always 'err' on the safe side, ha, ha)
                reporter.checkErr(testName + ".runTest()");
                break;
        }
    }


    /**
     * Convenience method to log out any version or system info.  
     * <p>Logs System.getProperties(), the harnessProps block, plus 
     * info about the classpath.</p>
     */
    protected void logHarnessProps()
    {
        reporter.logHashtable(reporter.WARNINGMSG, System.getProperties(), "System.getProperties");
        reporter.logHashtable(reporter.WARNINGMSG, harnessProps, "harnessProps");
        // Since we're running a bunch of tests, also check which version 
        //      of various jars we're running against
        logClasspathInfo(System.getProperty("java.class.path"));
    }


    /**
     * Convenience method to log out misc info about your classpath.  
     * @param classpath presumably the java.class.path to search for jars
     */
    protected void logClasspathInfo(String classpath)
    {
        StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
        for (int i = 0; st.hasMoreTokens(); i++)
        {
            logClasspathItem(st.nextToken());
        }
    }


    /**
     * Convenience method to log out misc info about a single classpath entry.  
     * <p>Implicitly looks for specific jars, namely xalan.jar, xerces.jar, etc.</p>
     * @param filename classpath entry to report about
     */
    protected void logClasspathItem(String filename)
    {
        // Make sure the comparison names are all lower case
        // This allows us to do case-insensitive compares, but 
        //      actually use the case-sensitive filename for lookups
        String filenameLC = filename.toLowerCase();
        String checknames[] = { "xalan.jar", "xerces.jar", "testxsl.jar", "minitest.jar"};

        for (int i = 0; i < checknames.length; i++)
        {
            if (filenameLC.indexOf(checknames[i]) > -1)
            {
                File f = new File(filename);
                if (f.exists())
                {
                    Hashtable h = new Hashtable(4);
                    h.put("jarname", checknames[i]);
                    h.put("length", String.valueOf(f.length()));
                    h.put("lastModified", String.valueOf(f.lastModified()));
                    h.put("path", f.getAbsolutePath());
                    reporter.logElement(Reporter.INFOMSG, "classpathitem", h, null);
                }
            }
        }
    }


    /**
     * Cheap-o memory logger - just reports Runtime.totalMemory/freeMemory.  
     */
    protected void logMemory()
    {
        Runtime r = Runtime.getRuntime();
        r.gc();
        reporter.logPerfMsg("UMem", r.freeMemory(), "freeMemory");
        reporter.logPerfMsg("UMem", r.totalMemory(), "totalMemory");
    }


    /**
     * Run the test harness to execute the specified tests.  
     */
    public void doMain(String args[])
    {
        // Must have at least one arg to continue
        if ((args == null) || (args.length == 0))
        {
            System.err.println("ERROR in usage: must have at least one argument");
            System.err.println(usage());
            return;
        }

        // Initialize ourselves and a list of tests to execute
        // Side effects: sets harnessProps, debug
        String tests[] = doTestHarnessInit(args);
        if (tests == null)
        {
            System.err.println("ERROR in usage: Problem during initialization - no tests!");
            System.err.println(usage());
            return;
        }

        // Use a separate copy of our properties to init our Reporter
        Properties reporterProps = (Properties)harnessProps.clone();

        // Ensure we have an XMLFileLogger if we have a logName
        String logF = reporterProps.getProperty(Logger.OPT_LOGFILE);

        if ((logF != null) && (!logF.equals("")))
        {
            // We should ensure there's an XMLFileReporter
            String r = reporterProps.getProperty(Reporter.OPT_LOGGERS);

            if (r == null)
            {
                reporterProps.put(Reporter.OPT_LOGGERS,
                              "org.apache.qetest.XMLFileLogger");
            }
            else if (r.indexOf("XMLFileLogger") <= 0)
            {
                reporterProps.put(Reporter.OPT_LOGGERS,
                              r + Reporter.LOGGER_SEPARATOR
                              + "org.apache.qetest.XMLFileLogger");
            }
        }

        // Ensure we have a ConsoleLogger unless asked not to
        // @todo improve and document this feature
        String noDefault = reporterProps.getProperty("noDefaultReporter");
        if (noDefault == null)
        {
            // We should ensure there's an XMLFileReporter
            String r = reporterProps.getProperty(Reporter.OPT_LOGGERS);

            if (r == null)
            {
                reporterProps.put(Reporter.OPT_LOGGERS,
                              "org.apache.qetest.ConsoleLogger");
            }
            else if (r.indexOf("ConsoleLogger") <= 0)
            {
                reporterProps.put(Reporter.OPT_LOGGERS,
                              r + Reporter.LOGGER_SEPARATOR
                              + "org.apache.qetest.ConsoleLogger");
            }
        }

        // A Reporter will auto-initialize from the values
        //  in the properties block
        reporter = new Reporter(reporterProps);
        reporter.addDefaultLogger();  // add default logger if needed

        // Call worker method to actually run all the tests
        // Worker method manages all it's own reporting, including 
        //  calling testFileInit/testFileClose
        boolean notUsed = runHarness(tests);

        // Tell user if a logFile should have been saved
        String logFile = reporterProps.getProperty(Logger.OPT_LOGFILE);
        if (logFile != null)
        {
            System.out.println("");
            System.out.println("Hey! A summary-harness logFile was written to: " + logFile);
        }
    }


    /**
     * Main method to run the harness from the command line.  
     */
    public static void main (String[] args)
    {
        XSLTestHarness app = new XSLTestHarness();
        app.doMain(args);
    }
}    // end of class XSLTestHarness

