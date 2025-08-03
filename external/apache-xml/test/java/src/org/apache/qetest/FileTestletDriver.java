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

package org.apache.qetest;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

/**
 * Generic Test driver for FileTestlets.
 * 
 * <p>This driver provides basic services for iterating over a tree 
 * of test files and executing a specified testlet on each test that 
 * is selected by a set of specified filters.  It automatically handles 
 * iteration and optional recursion down the tree, and by default 
 * assumes there are three 'matching' trees for inputs, golds, and 
 * creates a tree for outputs.</p>
 *
 * <p>Key methods are separated into worker methods so subclasses can 
 * override just the parts of the algorithm they need to change.</p>
 *
 * <p>//@todo move and refactor XSLProcessorTestBase to 
 * be more generic and reduce dependencies; also reduce dependency 
 * on internal variables and instead always use lookups into 
 * our testProps object.</p>
 * 
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class FileTestletDriver extends FileBasedTest /// extends XSLProcessorTestBase
{

    //-----------------------------------------------------
    //-------- Constants for common input params --------
    //-----------------------------------------------------

    /**
     * Parameter: Run a specific list of files, instead of 
     * iterating over directories.  
     * <p>Default: null, do normal iteration.</p>
     */
    public static final String OPT_FILELIST = "fileList";

    /**
     * Parameter: FQCN or simple classname of Testlet to use.  
     * <p>User may pass in either a FQCN or just a base classname, 
     * and we will attempt to look it up in any of the most common 
     * Xalan-testing packages.  See QetestUtils.testClassForName().</p>
     * <p>Default: null, use StylesheetTestlet.</p>
     */
    public static final String OPT_TESTLET = "testlet";

    /** Classname of Testlet to use.   */
    protected String testlet = null;

    /**
     * Parameter: FQCN or simple classname of FilenameFilter for 
     * directories under testDir we will process.  
     * If fileList is not set, we simply go to our inputDir, and 
     * then use this filter to iterate through directories returned.
     * <p>Default: null, use ConformanceDirRules.</p>
     */
    public static final String OPT_DIRFILTER = "dirFilter";

    /** Classname of FilenameFilter to use for dirs.  */
    protected String dirFilter = null;

    /**
     * Parameter: FQCN or simple classname of FilenameFilter for 
     * files within subdirs we will process.  
     * If fileList is not set, we simply go through all directories 
     * specified by directoryFilter, and then use this filter to 
     * find all stylesheet test files in that directory to test.
     * Note that this does <b>not</b> handle embedded tests, where 
     * the XML document has an xml-stylesheet PI that defines the 
     * stylesheet to use to process it.
     * <p>Default: null, use ConformanceFileRules.</p>
     */
    public static final String OPT_FILEFILTER = "fileFilter";

    /** Classname of FilenameFilter to use for files.  */
    protected String fileFilter = null;

    /** Unique runId for each specific invocation of this test driver.  */
    protected String runId = null;

    /** Convenience constant: .gold extension for gold files.  */
    public static final String GLD_EXTENSION = ".gld";

    /** Convenience constant: .out extension for output result file.  */
    public static final String OUT_EXTENSION = ".out";


    /** Just initialize test name, comment; numTestCases is not used. */
    public FileTestletDriver()
    {
        testName = "FileTestletDriver";
        testComment = "Test driver for File-based Testlets";
    }


    /**
     * Initialize this test - fill in parameters.
     * Simply fills in convenience variables from user parameters.
     *
     * @param p unused
     * @return true
     */
    public boolean doTestFileInit(Properties p)
    {
        // Copy any of our parameters from testProps to 
        //  our local convenience variables
        testlet = testProps.getProperty(OPT_TESTLET, testlet);
        dirFilter = testProps.getProperty(OPT_DIRFILTER, dirFilter);
        fileFilter = testProps.getProperty(OPT_FILEFILTER, fileFilter);

        // Grab a unique runid for logging out with our tests 
        //  Used in results reporting stylesheets to differentiate 
        //  between different test runs
        runId = QetestUtils.createRunId(testProps.getProperty("runId"));
        testProps.put("runId", runId);  // put back in the properties 
                                        // for later use
        return true;
    }


    /**
     * Run through the directory given to us and run tests found
     * in subdirs; or run through our fileList.
     *
     * This method logs some basic runtime data (like the actual 
     * testlet and ProcessorWrapper implementations used) and 
     * then decides to either run a user-specified fileList or to 
     * use our dirFilter to iterate over the inputDir.
     *
     * @param p Properties block of options to use - unused
     * @return true if OK, false if we should abort
     */
    public boolean runTestCases(Properties p)
    {
        // First log out any other runtime information, like the 
        //  actual current testlet and filters
        try
        {
            // Note that each of these calls actually force the 
            //  creation of an actual object of each type: this is 
            //  required since we may default the types or our call 
            //  to QetestUtils.testClassForName() may return a 
            //  different classname than the user actually specified
            // Care should be taken that the construction of objects 
            //  here does not affect our testing later on
            Properties runtimeProps = new Properties();
            // ... and add a few extra things ourselves
            runtimeProps.put("actual.testlet", getTestlet());
            runtimeProps.put("actual.dirFilter", getDirFilter());
            runtimeProps.put("actual.fileFilter", getFileFilter());
            reporter.logHashtable(Logger.CRITICALMSG, runtimeProps, 
                                  "actual.runtime information");
        }
        catch (Exception e)
        {
            // This is not necessarily an error
            reporter.logThrowable(Logger.WARNINGMSG, e, "Logging actual.runtime threw");
        }

        // Now either run a list of specific tests the user specified, 
        //  or do the default of iterating over a set of directories
        String fileList = testProps.getProperty(OPT_FILELIST);
        if (null != fileList)
        {
            // Process the specific list of tests the user supplied
            String desc = "User-supplied fileList: " + fileList; // provide default value
            // Use static worker class to process the list
            Vector datalets = FileDataletManager.readFileList(reporter, fileList, desc, testProps);

            // Actually process the specified files in a testCase
            processFileList(datalets, desc);
        }
        else
        {
            // Do the default, which is to iterate over the inputDir
            // Note that this calls the testCaseInit/testCaseClose
            //  logging methods itself
            processInputDir();
        }
        return true;
    }


    /**
     * Do the default: test all files found in subdirs
     * of our inputDir, using FilenameFilters for dirs and files.
     * Parameters: none, uses our internal members inputDir, 
     * outputDir, goldDir, etc.  Will attempt to use a default 
     * inputDir if the specified one doesn't exist.
     *
     * This is a special case of recurseSubDir, since we report 
     * differently from the top level.
     */
    public void processInputDir()
    {
        // Ensure the inputDir is there - we must have a valid location for input files
        File topInputDir = new File(inputDir);

        if (!topInputDir.exists())
        {
            // Try a default inputDir
            String oldInputDir = inputDir; // cache for potential error message
            topInputDir = new File((inputDir = getDefaultInputDir()));
            if (!topInputDir.exists())
            {
                // No inputDir, can't do any tests!
                // Note we put this in a fake testCase, since this
                //  is likely the only thing our test reports
                reporter.testCaseInit("processInputDir - mock testcase");
                reporter.checkErr("topInputDir(" + oldInputDir
                                  + ", or " + inputDir + ") does not exist, aborting!");
                reporter.testCaseClose();
                return;
            }
        }

        FileDatalet topDirs = new FileDatalet(topInputDir.getPath(), outputDir, goldDir);

        // Optionally process this topDirs, and always recurse at 
        //  least one level below it
        recurseSubDir(topDirs, getProcessTopDir(), true);
    }


    /**
     * Optionally process all the files in this dir and optionally 
     * recurse downwards using our dirFilter.
     * 
     * This is a pre-order traversal; we process files in this 
     * dir first and then optionally recurse.
     *
     * @param base FileDatalet representing the input, output, 
     * gold directory triplet we should use
     * @param process if we should call processSubDir on this dir
     * @param recurse if we should recurse below this directory, 
     * or just stop here after processSubDir()
     */
    public void recurseSubDir(FileDatalet base, boolean process, boolean recurse)
    {
        // Process this directory first: pre-order traversal
        if (process)
            processSubDir(base);

        if (!recurse)
            return;

        // If we should recurse, do so now
        File inputDir = new File(base.getInput());
        FilenameFilter filter = getDirFilter();
        reporter.logTraceMsg("recurseSubDir(" + inputDir.getPath()
                            + ") looking for subdirs with: " + filter);

        // Use our filter to get a list of directories to process
        String subdirs[] = inputDir.list(filter);

        // Validate that we have some valid directories to process
        if ((null == subdirs) || (subdirs.length <= 0))
        {
            reporter.logWarningMsg("recurseSubDir(" + inputDir.getPath()
                               + ") no valid subdirs found!");
            return;
        }

        // For every subdirectory, check if we should run tests in it
        for (int i = 0; i < subdirs.length; i++)
        {
            File subTestDir = new File(inputDir, subdirs[i]);

            if ((null == subTestDir) || (!subTestDir.exists()))
            {
                // Just log it and continue; presumably we'll find 
                //  other directories to test
                reporter.logWarningMsg("subTestDir(" + subTestDir.getPath() 
                                       + ") does not exist, skipping!");
                continue;
            }
            FileDatalet subdir = new FileDatalet(base, subdirs[i]);

            // Process each other directory, and optionally continue 
            //  to recurse downwards
            recurseSubDir(subdir, true, getRecurseDirs());
        } // end of for...
    }


    /**
     * Process a single subdirectory and run our testlet over 
     * every file found by our fileFilter therein.
     *
     * @param base FileDatalet representing the input, output, 
     * gold directory triplet we should use
     */
    public void processSubDir(FileDatalet base)
    {
        // Validate that each of the specified dirs exists
        // Ask it to be strict in ensuring output, gold are created
        if (!base.validate(true))
        {
            // Just log it and continue; presumably we'll find 
            //  other directories to test
            reporter.logWarningMsg("processSubDir(" + base.getInput() 
                                   + ", " + base.getOutput()
                                   + ", " + base.getGold()
                                   + ") some dir does not exist, skipping!");
            return;
        }

        File subInputDir = new File(base.getInput());
        // Call worker method to process the individual directory
        //  and get a list of .xsl files to test
        Vector files = getFilesFromDir(subInputDir, getFileFilter());

        if ((null == files) || (0 == files.size()))
        {
            reporter.logStatusMsg("processSubDir(" + base.getInput() 
                                   + ") no files found(1), skipping!");
            return;
        }

        // 'Transform' the list of individual test files into a 
        //  list of Datalets with all fields filled in
        //@todo should getFilesFromDir and buildDatalets be combined?
        Vector datalets = buildDatalets(files, base);

        if ((null == datalets) || (0 == datalets.size()))
        {
            reporter.logWarningMsg("processSubDir(" + base.getInput() 
                                   + ") no tests found(2), skipping!");
            return;
        }

        // Now process the list of files found in this dir
        processFileList(datalets, "Testing subdir: " + base.getInput());
    }


    /**
     * Run a list of stylesheet tests through a Testlet.
     * The file names are assumed to be fully specified, and we assume
     * the corresponding directories exist.
     * Each fileList is turned into a testcase.
     *
     * @param vector of Datalet objects to pass in
     * @param desc String to use as testCase description
     */
    public void processFileList(Vector datalets, String desc)
    {
        // Validate arguments
        if ((null == datalets) || (0 == datalets.size()))
        {
            // Bad arguments, report it as an error
            // Note: normally, this should never happen, since 
            //  this class normally validates these arguments 
            //  before calling us
            reporter.checkErr("processFileList: Testlet or datalets are null/blank, nothing to test!");
            return;
        }

        // Put each fileList into a testCase
        reporter.testCaseInit(desc);

        // Now just go through the list and process each set
        int numDatalets = datalets.size();
        reporter.logInfoMsg("processFileList() with " + numDatalets
                            + " potential tests");
        // Iterate over every datalet and test it
        for (int ctr = 0; ctr < numDatalets; ctr++)
        {
            try
            {
                // Create a Testlet to execute a test with this 
                //  next datalet - the Testlet will log all info 
                //  about the test, including calling check*()
                getTestlet().execute((Datalet)datalets.elementAt(ctr));
            } 
            catch (Throwable t)
            {
                // Log any exceptions as fails and keep going
                reporter.logThrowable(Logger.ERRORMSG, t, "Datalet threw");
                reporter.checkErr("Datalet num " + ctr + " threw: " + t.toString());
            }
        }  // of while...
        reporter.testCaseClose();
    }


    /**
     * Use the supplied filter on given directory to return a list 
     * of tests to be run.
     * 
     * The real logic is in the filter, which can be specified as 
     * an option or by overriding getDefaultFileFilter().
     *
     * @param dir directory to scan
     * @param filter to use on this directory; if null, uses default
     * @return Vector of local path\filenames of tests to run;
     * the tests themselves will exist; null if error
     */
    public Vector getFilesFromDir(File dir, FilenameFilter filter)
    {
        // Validate arguments
        if ((null == dir) || (!dir.exists()))
        {
            // Bad arguments, report it as an error
            // Note: normally, this should never happen, since 
            //  this class normally validates these arguments 
            //  before calling us
            reporter.logWarningMsg("getFilesFromDir(" + dir.toString() + ") dir null or does not exist");
            return null;
        }
        // Get the list of 'normal' test files
        String[] files = dir.list(filter);
        Vector v = new Vector(files.length);
        for (int i = 0; i < files.length; i++)
        {
            v.addElement(files[i]);
        }
        reporter.logTraceMsg("getFilesFromDir(" + dir.toString() + ") found " + v.size() + " total files to test");
        return v;
    }


    /**
     * Transform a vector of individual test names into a Vector 
     * of filled-in datalets to be tested
     *
     * This basically just calculates local path\filenames across 
     * the three presumably-parallel directory trees of  
     * inputDir, outputDir and goldDir.  
     * It then stuffs each of these values plus some 
     * generic info like our testProps into each datalet it creates.
     * 
     * @param files Vector of local path\filenames to be tested
     * @param base FileDatalet denoting directories 
     * input, output, gold
     * @return Vector of FileDatalets that are fully filled in,
     * i.e. output, gold, etc are filled in respectively 
     * to input
     */
    public Vector buildDatalets(Vector files, FileDatalet base)
    {
        // Validate arguments
        if ((null == files) || (files.size() < 1))
        {
            // Bad arguments, report it as an error
            // Note: normally, this should never happen, since 
            //  this class normally validates these arguments 
            //  before calling us
            reporter.logWarningMsg("buildDatalets null or empty file vector");
            return null;
        }
        Vector v = new Vector(files.size());

        // For every file in the vector, construct the matching 
        //  out, gold, and xml/xsl files
        for (Enumeration elements = files.elements();
                elements.hasMoreElements(); /* no increment portion */ )
        {
            String file = null;
            try
            {
                file = (String)elements.nextElement();
            }
            catch (ClassCastException cce)
            {
                // Just skip this entry
                reporter.logWarningMsg("Bad file element found, skipping: " + cce.toString());
                continue;
            }
            v.addElement(buildDatalet(base, file));
        }
        return v;
    }

    /**
     * Construct a FileDatalet with corresponding output, gold files.  
     *
     * This basically just calls worker methods to construct and 
     * set options on a datalet to return.
     *
     * @param base FileDatalet denoting directories 
     * input, output, gold
     * @param name bare name of the input file
     * @return FileDatalet that is fully filled in,
     * i.e. output, gold, etc are filled in respectively 
     * to input and any options are set
     */
    protected FileDatalet buildDatalet(FileDatalet base, String name)
    {
        // Worker method to construct paths
        FileDatalet d = buildDataletPaths(base, name);
        // Worker method to set any other options, etc.
        setDataletOptions(d);
        return d;
    }

    /**
     * Construct a FileDatalet with corresponding output, gold files.  
     *
     * This worker method just has the logic to construct the 
     * corresponding output and gold filenames; feel free to subclass.
     *
     * This class simply appends .out and .gld to the end of the 
     * existing names: foo.xml: foo.xml.out, foo.xml.gld.
     *
     * @param base FileDatalet denoting directories 
     * input, output, gold
     * @param name bare name of the input file
     * @return FileDatalet that is fully filled in,
     * i.e. output, gold, etc are filled in respectively 
     * to input
     */
    protected FileDatalet buildDataletPaths(FileDatalet base, String name)
    {
        return new FileDatalet(base.getInput() + File.separator + name, 
                base.getOutput() + File.separator + name + OUT_EXTENSION,
                base.getGold() + File.separator + name + GLD_EXTENSION);
    }

    /**
     * Fillin FileDatalet.setOptions and any other processing.  
     *
     * This is designed to be overriden so subclasses can put any 
     * special items in the datalet's options or do other 
     * preprocessing of the datalet.
     *
     * @param base FileDatalet to apply options, etc. to
     */
    protected void setDataletOptions(FileDatalet base)
    {
        base.setDescription(base.getInput());
        // Optimization: put in a copy of our fileChecker, so 
        //  that each testlet doesn't have to create it's own
        //  fileCheckers should not store state, so this 
        //  shouldn't affect the testing at all
        base.setOptions(testProps);
        // Note: set our options in the datalet first, then 
        //  put the fileChecker directly into their options
        base.getOptions().put("fileCheckerImpl", fileChecker);
    }

    /** If we should process the top level directory (default:false).   */
    protected boolean getProcessTopDir()
    { return false; }

    /** If we should always recurse lower level directories (default:false).   */
    protected boolean getRecurseDirs()
    { return false; }

    /** Default FilenameFilter FQCN for directories.   */
    protected String getDefaultDirFilter()
    { return "org.apache.qetest.DirFilter"; }

    /** Default FilenameFilter FQCN for files.   */
    protected String getDefaultFileFilter()
    { return "org.apache.qetest.FilePatternFilter"; }

    /** Default Testlet FQCN for executing stylesheet tests.   */
    protected String getDefaultTestlet()
    { return "org.apache.qetest.FileTestlet"; }

    /** Default list of packages to search for classes.   */
    protected String[] getDefaultPackages()
    { return QetestUtils.defaultPackages; }

    /** Cached Testlet Class; used for life of this test.   */
    protected Class cachedTestletClazz = null;

    /**
     * Convenience method to get a Testlet to use.  
     * Attempts to return one as specified by our testlet parameter, 
     * otherwise returns a default StylesheetTestlet.
     * 
     * @return Testlet for use in this test; null if error
     */
    public Testlet getTestlet()
    {
        // Find a Testlet class to use if we haven't already
        if (null == cachedTestletClazz)
        {
            cachedTestletClazz = QetestUtils.testClassForName(testlet, 
                                                              getDefaultPackages(),
                                                              getDefaultTestlet());
        }
        try
        {
            // Create it and set our reporter into it
            Testlet t = (Testlet)cachedTestletClazz.newInstance();
            t.setLogger((Logger)reporter);
            return (Testlet)t;
        }
        catch (Exception e)
        {
            // Ooops, none found! This should be very rare, since 
            //  we know the defaultTestlet should be found
            return null;
        }
    }


    /**
     * Convenience method to get a default filter for directories.  
     * Uses category member variable if set.
     * 
     * @return FilenameFilter using DirFilter(category, null).
     */
    public FilenameFilter getDirFilter()
    {
        // Find a FilenameFilter class to use
        Class clazz = QetestUtils.testClassForName(dirFilter, 
                                                   getDefaultPackages(),
                                                   getDefaultDirFilter());
        try
        {
            // Create it, optionally with a category
            String category = testProps.getProperty(OPT_CATEGORY);
            if ((null != category) && (category.length() > 1))  // Arbitrary check for non-null, non-blank string
            {
                Class[] parameterTypes = 
                { 
                    java.lang.String.class, 
                    java.lang.String.class 
                };
                Constructor ctor = clazz.getConstructor(parameterTypes);

                Object[] ctorArgs = 
                {
                    category, 
                    null
                };
                return (FilenameFilter)ctor.newInstance(ctorArgs);
            }
            else
            {
                return (FilenameFilter)clazz.newInstance();
            }
        }
        catch (Exception e)
        {
            // Ooops, none found!
            return null;
        }
    }


    /**
     * Convenience method to get a default filter for files.  
     * Uses excludes member variable if set.
     * 
     * @return FilenameFilter using FileExtensionFilter(null, excludes).
     */
    public FilenameFilter getFileFilter()
    {
        // Find a FilenameFilter class to use
        Class clazz = QetestUtils.testClassForName(fileFilter, 
                                                   getDefaultPackages(),
                                                   getDefaultFileFilter());
        try
        {
            // Create it, optionally with excludes
            String excludes = testProps.getProperty(OPT_EXCLUDES);
            if ((null != excludes) && (excludes.length() > 1))  // Arbitrary check for non-null, non-blank string
            {
                Class[] parameterTypes = 
                { 
                    java.lang.String.class, 
                    java.lang.String.class 
                };
                Constructor ctor = clazz.getConstructor(parameterTypes);

                Object[] ctorArgs = 
                {
                    null, 
                    excludes
                };
                return (FilenameFilter)ctor.newInstance(ctorArgs);
            }
            else
            {
                return (FilenameFilter)clazz.newInstance();
            }
        }
        catch (Exception e)
        {
            // Ooops, none found!
            return null;
        }
    }


    /**
     * Convenience method to get a default inputDir when none or
     * a bad one was given.  
     * @return String pathname of default inputDir "tests\conf".
     */
    public String getDefaultInputDir()
    {
        return "tests" + File.separator + "conf";
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Additional options supported by FileTestletDriver:\n"
                + "    -" + OPT_FILELIST
                + "   <name of listfile of tests to run>\n"
                + "    -" + OPT_DIRFILTER
                + "  <classname of FilenameFilter for dirs>\n"
                + "    -" + OPT_FILEFILTER
                + " <classname of FilenameFilter for files>\n"
                + "    -" + OPT_TESTLET
                + "    <classname of Testlet to execute tests with>\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        FileTestletDriver app = new FileTestletDriver();
        app.doMain(args);
    }
}
