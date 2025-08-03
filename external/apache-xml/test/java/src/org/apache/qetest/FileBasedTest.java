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
 * FileBasedTest.java
 *
 */
package org.apache.qetest;

import org.apache.test.android.AndroidFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.Properties;

//-------------------------------------------------------------------------

/**
 * Base class for file-based tests.
 * Many tests will need to operate on files external to a product
 * under test.  This class provides useful, generic functionality
 * in these cases.
 * <p>FileBasedTest defines a number of common fields that many
 * tests that operate on data files may use.</p>
 * <ul>These are each pre-initialized for you from the command line or property file.
 * <li>inputDir (string representing dir where input files come from)</li>
 * <li>outputDir (string representing dir where output, working, temp files go)</li>
 * <li>goldDir  (string representing dir where known good reference files are)</li>
 * <li>debug (generic boolean flag for debugging)</li>
 * <li>(stored in testProps) loggers (FQCN;of;Loggers to add to our Reporter)</li>
 * <li>(stored in testProps) loggingLevel (passed to Reporters)</li>
 * <li>(stored in testProps) logFile (string filename for any file-based Reporter)</li>
 * <li>fileChecker</li>
 * <li>(stored in testProps) excludes</li>
 * <li>(stored in testProps) category</li>
 * </ul>
 * @author Shane_Curcuru@lotus.com
 * @version 3.0
 */
public class FileBasedTest extends TestImpl
{

    /**
     * Convenience method to print out usage information.
     * @author Shane Curcuru
     * <p>Should be overridden by subclasses, although they are free
     * to call super.usage() to get the common options string.</p>
     *
     * @return String denoting usage of this class
     */
    public String usage()
    {

        return ("Common options supported by FileBasedTest:\n" + "    -"
                + OPT_LOAD
                + " <file.props>  (read in a .properties file,\n"
                + "                         that can set any/all of the other opts)\n"
                + "    -" + OPT_INPUTDIR 
                + "    <path to input files>\n"
                + "    -" + OPT_OUTPUTDIR
                + "   <path to output area - where all output is sent>\n"
                + "    -" + OPT_GOLDDIR
                + "     <path to gold reference output>\n"
                + "    -" + OPT_CATEGORY
                + "    <names;of;categories of tests to run>\n"
                + "    -" + OPT_EXCLUDES
                + "    <list;of;specific file.ext tests to skip>\n" 
                + "    -" + OPT_FILECHECKER
                + " <FQCN of a non-standard FileCheckService>\n"
                + "    -" + Reporter.OPT_LOGGERS
                + "     <FQCN;of;Loggers to use>\n"
                + "    -" + Logger.OPT_LOGFILE
                + "     <resultsFileName> (sends test results to XML file)\n"
                + "    -" + Reporter.OPT_LOGGINGLEVEL 
                + " <int> (level of msgs to log out; 0=few, 99=lots)\n" 
                + "    -" + Reporter.OPT_DEBUG
                + " (prints extra debugging info)\n");
    }

    //-----------------------------------------------------
    //-------- Constants for common input params --------
    //-----------------------------------------------------

    /**
     * Parameter: Load properties file for options
     * <p>Will load named file as a Properties block, setting any
     * applicable options. Command line takes precedence.
     * Format: <code>-load FileName.prop</code></p>
     */
    public static final String OPT_LOAD = "load";

    /**
     * Parameter: Where are test input files?
     * <p>Default: .\inputs.
     * Format: <code>-inputDir path\to\dir</code></p>
     */
    public static final String OPT_INPUTDIR = "inputDir";

    /** Field inputDir:holds String denoting local path for inputs.  */
    protected String inputDir = "." + File.separator + "inputs";

    /**
     * Parameter: Where should we place output files (or temp files, etc.)?
     * <p>Default: .\outputs.
     * Format: <code>-outputDir path\to\dir</code></p>
     */
    public static final String OPT_OUTPUTDIR = "outputDir";

    /** Field outputDir:holds String denoting local path for outputs.  */
    // Android-changed: The original directory isn't writeable on Android.
    // protected String outputDir = "." + File.separator + "outputs";
    protected String outputDir = AndroidFileUtils.getOutputFile("." + File.separator + "outputs")
            .getPath();

    /**
     * Parameter: Where should get "gold" pre-validated XML files?
     * <p>Default: .\golds.
     * Format: <code>-goldDir path\to\dir</code></p>
     */
    public static final String OPT_GOLDDIR = "goldDir";

    /** Field goldDir:holds String denoting local path for golds.  */
    protected String goldDir = "." + File.separator + "golds";

    /**
     * Parameter: Only run a single subcategory of the tests.
     * <p>Default: blank, runs all tests - supply the directory name
     * of a subcategory to run just that set.  Set into testProps 
     * and used from there.</p>
     */
    public static final String OPT_CATEGORY = "category";

    /**
     * Parameter: Should we exclude any specific test files?
     * <p>Default: null (no excludes; otherwise specify 
     * semicolon delimited list of bare filenames something like 
     * 'axes01.xsl;bool99.xsl').  Set into testProps and used 
     * from there</p>
     */
    public static final String OPT_EXCLUDES = "excludes";

    /**
     * Parameter: Which CheckService should we use for XML output Files?
     * <p>Default: org.apache.qetest.XHTFileCheckService.</p>
     */
    public static final String OPT_FILECHECKER = "fileChecker";

    /**
     * Parameter-Default value: org.apache.qetest.XHTFileCheckService.  
     */
    public static final String OPT_FILECHECKER_DEFAULT = "org.apache.qetest.xsl.XHTFileCheckService";

    /** FileChecker instance for use by subclasses; created in preTestFileInit()  */
    protected CheckService fileChecker = null;

    /**
     * Parameter: if Reporters should log performance data, true/false.
     */
    protected boolean perfLogging = false;

    /**
     * Parameter: general purpose debugging flag.
     */
    protected boolean debug = false;

    //-----------------------------------------------------
    //-------- Class members and accessors --------
    //-----------------------------------------------------

    /**
     * Total Number of test case methods defined in this test.
     * <p>Tests must either set this variable or override runTestCases().</p>
     * <p>Unless you override runTestCases(), test cases must be named like so:.</p>
     * <p>Tests must either set this variable or override runTestCases().</p>
     * <p>&nbsp;&nbsp;testCase<I>N</I>, where <I>N</I> is a consecutively
     * numbered whole integer (1, 2, 3,....</p>
     * @see #runTestCases
     */
    public int numTestCases = 0;

    /**
     * Generic Properties block for storing initialization info.
     * All startup options get stored in here for later use, both by
     * the test itself and by any Reporters we use.
     */
    protected Properties testProps = new Properties();

    /**
     * Accessor method for our Properties block, for use by harnesses.
     *
     * @param p if (p != null) testProps = (Properties) p.clone(); 
     */
    public void setProperties(Properties p)
    {

        // Don't allow setting to null!
        if (p != null)
        {
            testProps = (Properties) p.clone();
        }
    }

    /**
     * Accessor method for our Properties block, for use by harnesses.
     *
     * @return our Properties block itself
     */
    public Properties getProperties()
    {
        return testProps;
    }

    /**
     * Default constructor - initialize testName, Comment.
     */
    public FileBasedTest()
    {

        // Only set them if they're not set
        if (testName == null)
            testName = "FileBasedTest.defaultName";

        if (testComment == null)
            testComment = "FileBasedTest.defaultComment";
    }

    //-----------------------------------------------------
    //-------- Implement Test/TestImpl methods --------
    //-----------------------------------------------------

    /**
     * Initialize this test - called once before running testcases.
     * <p>Use the loggers field to create some loggers in a Reporter.</p>
     * @author Shane_Curcuru@lotus.com
     * @see TestImpl#testFileInit(java.util.Properties)
     *
     * @param p Properties to initialize from 
     *
     * @return false if we should abort; true otherwise
     */
    public boolean preTestFileInit(Properties p)
    {

        // Pass our properties block directly to the reporter
        //  so it can use the same values in initialization
        // A Reporter will auto-initialize from the values
        //  in the properties block
        setReporter(QetestFactory.newReporter(p));
        reporter.testFileInit(testName, testComment);

        // Create a file-based CheckService for later use
        if (null == fileChecker)
        {
            String tmpName = testProps.getProperty(OPT_FILECHECKER);
            if ((null != tmpName) && (tmpName.length() > 0))
            {
                // Use the user's specified class; if not available 
                //  will return null which gets covered below
                fileChecker = QetestFactory.newCheckService(reporter, tmpName);
            }
            
            if (null == fileChecker)
            {
                // If that didn't work, then ask for default one that does files
                fileChecker = QetestFactory.newCheckService(reporter, QetestFactory.TYPE_FILES);
            }
            // If we're creating a new one, also applyAttributes
            // (Assume that if we already had one, it already had this done)
            fileChecker.applyAttributes(p);
        }

        return true;
    }

    /**
     * Initialize this test - called once before running testcases.
     * <p>Subclasses <b>must</b> override this to do whatever specific
     * processing they need to initialize their product under test.</p>
     * <p>If for any reason the test should not continue, it <b>must</b>
     * return false from this method.</p>
     * @author Shane_Curcuru@lotus.com
     * @see TestImpl#testFileInit(java.util.Properties)
     *
     * @param p Properties to initialize from 
     *
     * @return false if we should abort; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        /* no-op; feel free to override */
        return true;
    }

    /**
     * Override mostly blank routine to dump environment info.
     * <p>Log out information about our environment in a structured 
     * way: mainly by calling logTestProps() here.</p>
     * 
     * @param p Properties to initialize from 
     *
     * @return false if we should abort; true otherwise
     */
    public boolean postTestFileInit(Properties p)
    {
        logTestProps();
        return true;
    }

    /**
     * Run all of our testcases.
     * <p>use nifty FileBasedTestReporter.executeTests().  May be overridden
     * by subclasses to do their own processing.  If you do not override,
     * you must set numTestCases properly!</p>
     * @author Shane Curcuru
     *
     * @param p Properties to initialize from 
     *
     * @return false if we should abort; true otherwise
     */
    public boolean runTestCases(Properties p)
    {

        // Properties may be currently unused
        reporter.executeTests(this, numTestCases, p);

        return true;
    }

    /**
     * Cleanup this test - called once after running testcases.
     * @author Shane Curcuru
     * <p>Tests should override if they need to do any cleanup.</p>
     *
     * @param p Properties to initialize from 
     *
     * @return false if we should abort; true otherwise
     */
    public boolean doTestFileClose(Properties p)
    {
        /* no-op; feel free to override */
        return true;
    }

    // Use default implementations of preTestFileClose()

    /**
     * Mark the test complete - called once after running testcases.
     * <p>Currently logs a summary of our test status and then tells 
     * our reporter to log the testFileClose. This will calculate 
     * final results, and complete logging for any structured 
     * output logs (like XML files).</p>
     *<p>We also call reporter.writeResultsStatus(true) to 
     * write out a pass/fail marker file.  (This last part is 
     * actually optional, but it's useful and quick, so I'll 
     * do it by default for now.)</p>
     *
     * @param p Unused; passed through to super
     *
     * @return true if OK, false otherwise
     */
    protected boolean postTestFileClose(Properties p)
    {
        // Log out a special summary status, with marker file
        reporter.writeResultsStatus(true);

        // Ask our superclass to handle this as well
        return super.postTestFileClose(p);
    }

    //-----------------------------------------------------
    //-------- Initialize our common input params --------
    //-----------------------------------------------------

    /**
     * Set our instance variables from a Properties file.
     * <p>Must <b>not</b> use reporter.</p>
     * @author Shane Curcuru
     * @param Properties block to set name=value pairs from
     *
     * NEEDSDOC @param props
     * @return status - true if OK, false if error.
     * @todo improve error checking, if needed
     */
    public boolean initializeFromProperties(Properties props)
    {
        // Copy over all properties into our local block
        //  this is a little unusual, but it does allow users 
        //  to set any new sort of properties via the properties 
        //  file, and we'll pick it up - that way this class doesn't
        //  have to get updated when we have new properties
        // Note that this may result in duplicates since we
        //  re-set many of the things from bleow
        for (Enumeration names = props.propertyNames();
                names.hasMoreElements(); /* no increment portion */ )
        {
            Object key = names.nextElement();

            testProps.put(key, props.get(key));
        }


        // Parse out any values that match our internal convenience variables
        // default all values to our current values
        // String values are simply getProperty()'d
        inputDir = props.getProperty(OPT_INPUTDIR, inputDir);
        if (inputDir != null)
            testProps.put(OPT_INPUTDIR, inputDir);

        outputDir = props.getProperty(OPT_OUTPUTDIR, outputDir);
        if (outputDir != null)
            testProps.put(OPT_OUTPUTDIR, outputDir);

        goldDir = props.getProperty(OPT_GOLDDIR, goldDir);
        if (goldDir != null)
            testProps.put(OPT_GOLDDIR, goldDir);

        // The actual fileChecker object is created in preTestFileInit()

        // Use a temp string for those properties we only set 
        //  in our testProps, but don't bother to save ourselves
        String temp = null;

        temp = props.getProperty(OPT_FILECHECKER);
        if (temp != null)
            testProps.put(OPT_FILECHECKER, temp);

        temp = props.getProperty(OPT_CATEGORY);
        if (temp != null)
            testProps.put(OPT_CATEGORY, temp);

        temp = props.getProperty(OPT_EXCLUDES);
        if (temp != null)
            testProps.put(OPT_EXCLUDES, temp);

        temp = props.getProperty(Reporter.OPT_LOGGERS);
        if (temp != null)
            testProps.put(Reporter.OPT_LOGGERS, temp);

        temp = props.getProperty(Logger.OPT_LOGFILE);
        if (temp != null)
            testProps.put(Logger.OPT_LOGFILE, temp);

        // boolean values just check for the non-default value
        String dbg = props.getProperty(Reporter.OPT_DEBUG);

        if ((dbg != null) && dbg.equalsIgnoreCase("true"))
        {
            debug = true;

            testProps.put(Reporter.OPT_DEBUG, "true");
        }

        String pLog = props.getProperty(Reporter.OPT_PERFLOGGING);

        if ((pLog != null) && pLog.equalsIgnoreCase("true"))
        {
            perfLogging = true;

            testProps.put(Reporter.OPT_PERFLOGGING, "true");
        }

        temp = props.getProperty(Reporter.OPT_LOGGINGLEVEL);

        if (temp != null)
            testProps.put(Reporter.OPT_LOGGINGLEVEL, temp);

        return true;
    }

    /**
     * Sets the provided fields with data from an array, presumably
     * from the command line.
     * <p>May be overridden by subclasses, although you should probably
     * read the code to see what default options this handles. Must
     * not use reporter. Calls initializeFromProperties(). After that,
     * sets any internal variables that match items in the array like:
     * <code> -param1 value1 -paramNoValue -param2 value2 </code>
     * Any params that do not match internal variables are simply set
     * into our properties block for later use.  This allows subclasses
     * to simply get their initialization data from the testProps
     * without having to make code changes here.</p>
     * <p>Assumes all params begin with "-" dash, and that all values
     * do <b>not</b> start with a dash.</p>
     * @author Shane Curcuru
     * @param String[] array of arguments
     *
     * @param args array of command line arguments
     * @param flag: are we being called from a subclass?
     * @return status - true if OK, false if error.
     */
    public boolean initializeFromArray(String[] args, boolean flag)
    {

        // Read in command line args and setup internal variables
        String optPrefix = "-";
        int nArgs = args.length;

        // We don't require any arguments: but subclasses might 
        //  want to require certain ones
        // Must read in properties file first, so cmdline can 
        //  override values from properties file
        boolean propsOK = true;

        // IF we are being called the first time on this 
        //  array of arguments, go ahead and process unknown ones
        //  otherwise, don't bother
        if (flag)
        {
            for (int k = 0; k < nArgs; k++)
            {
                if (args[k].equalsIgnoreCase(optPrefix + OPT_LOAD))
                {
                    if (++k >= nArgs)
                    {
                        System.err.println(
                            "ERROR: must supply properties filename for: "
                            + optPrefix + OPT_LOAD);

                        return false;
                    }

                    String loadPropsName = args[k];

                    try
                    {

                        // Load named file into our properties block
                        FileInputStream fIS = new FileInputStream(loadPropsName);
                        Properties p = new Properties();

                        p.load(fIS);
                        p.put(OPT_LOAD, loadPropsName); // Pass along with properties

                        propsOK &= initializeFromProperties(p);
                    }
                    catch (Exception e)
                    {
                        System.err.println(
                            "ERROR: loading properties file failed: " + loadPropsName);
                        e.printStackTrace();

                        return false;
                    }

                    break;
                }
            }  // end of for(...)
        }  // end of if ((flag))

        // Now read in the rest of the command line
        // @todo cleanup loop to be more table-driven
        for (int i = 0; i < nArgs; i++)
        {

            // Set any String args and place them in testProps
            if (args[i].equalsIgnoreCase(optPrefix + OPT_INPUTDIR))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_INPUTDIR);

                    return false;
                }

                inputDir = args[i];

                testProps.put(OPT_INPUTDIR, inputDir);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + OPT_OUTPUTDIR))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_OUTPUTDIR);

                    return false;
                }

                outputDir = args[i];

                testProps.put(OPT_OUTPUTDIR, outputDir);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + OPT_GOLDDIR))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_GOLDDIR);

                    return false;
                }

                goldDir = args[i];

                testProps.put(OPT_GOLDDIR, goldDir);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + OPT_CATEGORY))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_CATEGORY);

                    return false;
                }

                testProps.put(OPT_CATEGORY, args[i]);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + OPT_EXCLUDES))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_EXCLUDES);

                    return false;
                }

                testProps.put(OPT_EXCLUDES, args[i]);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + Reporter.OPT_LOGGERS))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + Reporter.OPT_LOGGERS);

                    return false;
                }

                testProps.put(Reporter.OPT_LOGGERS, args[i]);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + Logger.OPT_LOGFILE))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix + Logger.OPT_LOGFILE);

                    return false;
                }

                testProps.put(Logger.OPT_LOGFILE, args[i]);

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix + OPT_FILECHECKER))
            {
                if (++i >= nArgs)
                {
                    System.out.println("ERROR: must supply arg for: "
                                       + optPrefix + OPT_FILECHECKER);

                    return false;
                }

                testProps.put(OPT_FILECHECKER, args[i]);

                continue;
            }

            // Boolean values are simple flags to switch from defaults only
            if (args[i].equalsIgnoreCase(optPrefix + Reporter.OPT_DEBUG))
            {
                debug = true;

                testProps.put(Reporter.OPT_DEBUG, "true");

                continue;
            }

            if (args[i].equalsIgnoreCase(optPrefix
                                         + Reporter.OPT_PERFLOGGING))
            {
                testProps.put(Reporter.OPT_PERFLOGGING, "true");

                continue;
            }

            // Parse out the integer value
            //  This isn't strictly necessary since the catch-all 
            //  below should take care of it, but better safe than sorry
            if (args[i].equalsIgnoreCase(optPrefix
                                         + Reporter.OPT_LOGGINGLEVEL))
            {
                if (++i >= nArgs)
                {
                    System.err.println("ERROR: must supply arg for: "
                                       + optPrefix
                                       + Reporter.OPT_LOGGINGLEVEL);

                    return false;
                }

                try
                {
                    testProps.put(Reporter.OPT_LOGGINGLEVEL, args[i]);
                }
                catch (NumberFormatException numEx)
                { /* no-op */
                }

                continue;
            }

            // IF we are being called the first time on this 
            //  array of arguments, go ahead and process unknown ones
            //  otherwise, don't bother
            if (flag)
            {

                // Found an arg that we don't know how to process,
                //  so store it for any subclass' use as a catch-all
                // If it starts with - dash, and another non-dash arg follows,
                //  set as a name=value pair in the property block
                if ((args[i].startsWith(optPrefix)) && (i + 1 < nArgs)
                        && (!args[i + 1].startsWith(optPrefix)))
                {

                    // Scrub off the "-" prefix before setting the name
                    testProps.put(args[i].substring(1), args[i + 1]);

                    i++;  // Increment counter to skip next arg
                }

                // Otherwise, just set as name="" in the property block
                else
                {

                    // Scrub off the "-" prefix before setting the name
                    testProps.put(args[i].substring(1), "");
                }
            }
        }  // end of for() loop

        // If we got here, we set the array params OK, so simply return 
        //  the value the initializeFromProperties method returned
        return propsOK;
    }

    //-----------------------------------------------------
    //-------- Other useful and utility methods --------
    //-----------------------------------------------------

    /**
     * Log out any System or common version info.
     * <p>Logs System.getProperties(), and our the testProps block, etc..</p>
     */
    public void logTestProps()
    {
        reporter.logHashtable(reporter.CRITICALMSG, System.getProperties(),
                              "System.getProperties");
        reporter.logHashtable(reporter.CRITICALMSG, testProps, "testProps");
        reporter.logHashtable(reporter.CRITICALMSG, QetestUtils.getEnvironmentHash(), "getEnvironmentHash");
    }


    /**
     * Main worker method to run test from the command line.
     * Test subclasses generally need not override.
     * <p>This is primarily provided to make subclasses implementations
     * of the main method as simple as possible: in general, they
     * should simply do:
     * <code>
     *   public static void main (String[] args)
     *   {
     *       TestSubClass app = new TestSubClass();
     *       app.doMain(args);
     *   }
     * </code>
     *
     * @param args command line arguments
     */
    public void doMain(String[] args)
    {
        // Initialize any instance variables from the command line 
        //  OR specified properties block
        if (!initializeFromArray(args, true))
        {
            System.err.println("ERROR in usage:");
            System.err.println(usage());

            // Don't use System.exit, since that will blow away any containing harnesses
            return;
        }

        // Also pass along the command line, in case someone has 
        //  specific code that's counting on this
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < args.length; i++)
        {
            buf.append(args[i]);
            buf.append(" ");
        }
        testProps.put(MAIN_CMDLINE, buf.toString());

        // Actually go and execute the test
        runTest(testProps);
    }

    /**
     * Main method to run test from the command line.
     * @author Shane Curcuru
     * <p>Test subclasses <b>must</b> override, obviously.
     * Only provided here for debugging.</p>
     *
     * @param args command line arguments
     */
    public static void main(String[] args)
    {
        FileBasedTest app = new FileBasedTest();
        app.doMain(args);
    }
}  // end of class FileBasedTest

