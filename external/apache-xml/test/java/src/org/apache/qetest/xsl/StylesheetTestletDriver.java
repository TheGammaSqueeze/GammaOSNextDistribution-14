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
import java.io.FilenameFilter;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.qetest.Datalet;
import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.Testlet;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

/**
 * Test driver for XSLT stylesheet Testlets.
 * 
 * This is a generic driver for XSLT-oriented Testlets, and 
 * supports iterating over either a user-supplied, specific list 
 * of files to test or over a directory tree of test files.
 * Note there are a number of design decisions made that are 
 * just slightly specific to stylesheet testing, although this 
 * would be a good model for a completely generic TestletDriver.
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class StylesheetTestletDriver extends FileBasedTest
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

    /** Name of fileList file to read in to get test definitions from.   */
    protected String fileList = null;

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


    /**
     * Parameter: What flavor of TransformWrapper to use: trax.sax|trax.stream|other?
     * <p>Default: trax.</p>
     */
    public static final String OPT_FLAVOR = "flavor";

    /** Parameter: What flavor of TransformWrapper to use: trax.sax|trax.stream|other?  */
    protected String flavor = "trax";


    /**
     * Parameter: Are there any embedded stylesheets in XML files?
     * <p>Default: null (no embedded tests; otherwise specify 
     * semicolon delimited list of bare filenames something like 
     * 'axes02.xml;bool98.xml').</p>
     */
    public static final String OPT_EMBEDDED = "embedded";

    /** Parameter: Are there any embedded stylesheets in XML files?  */
    protected String embedded = null;

    /**
     * Parameter: Is there any preference to which gold file to use?
     * <p>Default: null (use standard .out file)
     * name of processor, could be XalanJ-I, XalanJ-C, or XalanC
     * </p>
     */
    public static final String OPT_PROCESSOR = "processor";

    /**
     * Parameter: Is there any parameters for XSL to use?
     * <p>Default: none
     * </p>
     */
    public static final String OPT_PARAM = "param";

    /** Parameter: What processor is being used?  */
    protected String processor = null;

    /**
     * Parameter: Is trace mode on?
     * <p>Default: null (no trace)
     * if on, non-null
     * </p>
     */
    public static final String OPT_TRACE = "trace";

    /** Parameter: Are we tracing? */
    protected String traceMode = null;

    /**
     * Parameter: Name of test (Conf, Accept) as StylesheetTestletDriver
     * can be used for more than one bucket
     * <p>Default: null (use StylesheetTestletDriver)
     * </p>
     */
    public static final String OPT_TESTNAME = "testName";


    /** Unique runId for each specific invocation of this test driver.  */
    protected String runId = null;

    /** Convenience constant: .xml extension for input data file.  */
    public static final String XML_EXTENSION = ".xml";

    /** Convenience constant: .param extension for input data file.  */
    public static final String PARAM_EXTENSION = ".param";

    /** Convenience constant: .xsl extension for stylesheet file.  */
    public static final String XSL_EXTENSION = ".xsl";

    /** Convenience constant: .out extension for output result file.  */
    public static final String OUT_EXTENSION = ".out";

    /** Convenience constant: .log extension for log file.  */
    public static final String LOG_EXTENSION = ".log";


    /** Just initialize test name, comment; numTestCases is not used. */
    public StylesheetTestletDriver()
    {
        testName = "StylesheetTestletDriver";
        testComment = "Test driver for XSLT stylesheet Testlets";
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
        fileList = testProps.getProperty(OPT_FILELIST, fileList);
        flavor = testProps.getProperty(OPT_FLAVOR, flavor);
        embedded = testProps.getProperty(OPT_EMBEDDED, embedded);
        processor = testProps.getProperty(OPT_PROCESSOR, processor);        
        testName = testProps.getProperty(OPT_TESTNAME, testName);
        traceMode = testProps.getProperty(OPT_TRACE, traceMode);
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
        //  actual flavor of ProcessorWrapper, etc.
        try
        {
            // Note that each of these calls actually force the 
            //  creation of an actual object of each type: this is 
            //  required since we may default the types or our call 
            //  to QetestUtils.testClassForName() may return a 
            //  different classname than the user actually specified
            // Care should be taken that the construction of objects 
            //  here does not affect our testing later on
            // Just grab all the info from the TransformWrapper...
            Properties runtimeProps = TransformWrapperFactory.newWrapper(flavor).getProcessorInfo();
            // ... and add a few extra things ourselves
            runtimeProps.put("actual.testlet", getTestlet());
            runtimeProps.put("actual.dirFilter", getDirFilter());
            runtimeProps.put("actual.fileFilter", getFileFilter());
            reporter.logHashtable(Logger.CRITICALMSG, runtimeProps, 
                                  "actual.runtime information");
        }
        catch (Exception e)
        {
            reporter.logWarningMsg("Logging actual.runtime threw: " + e.toString());
            reporter.logThrowable(Logger.WARNINGMSG, e, "Logging actual.runtime threw");
        }

        // Now either run a list of specific tests the user specified, 
        //  or do the default of iterating over a set of directories
        if (null != fileList)
        {
            // Process the specific list of tests the user supplied
            String desc = "User-supplied fileList: " + fileList; // provide default value
            // Use static worker class to process the list
            Vector datalets = StylesheetDataletManager.readFileList(reporter, fileList, desc, testProps);

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
        // Ensure the inputDir is there - we must have a valid location for input files
        File testDirectory = new File(inputDir);

        if (!testDirectory.exists())
        {
            // Try a default inputDir
            String oldInputDir = inputDir; // cache for potential error message
            testDirectory = new File((inputDir = getDefaultInputDir()));
            if (!testDirectory.exists())
            {
                // No inputDir, can't do any tests!
                // @todo check if this is the best way to express this
                reporter.checkErr("inputDir(" + oldInputDir
                                  + ", or " + inputDir + ") does not exist, aborting!");
                return;
            }
        }

        reporter.logInfoMsg("inputDir(" + testDirectory.getPath()
                            + ") looking for subdirs with: " + dirFilter);

        // Use our filter to get a list of directories to process
        String subdirs[] = testDirectory.list(getDirFilter());

        // Validate that we have some valid directories to process
        if ((null == subdirs) || (subdirs.length <= 0))
        {
            reporter.checkErr("inputDir(" + testDirectory.getPath()
                               + ") no valid subdirs found!");
            return;
        }

        int numSubdirs = subdirs.length;

        // For every subdirectory, check if we should run tests in it
        for (int i = 0; i < numSubdirs; i++)
        {
            File subTestDir = new File(testDirectory, subdirs[i]);

            if ((null == subTestDir) || (!subTestDir.exists()))
            {
                // Just log it and continue; presumably we'll find 
                //  other directories to test
                reporter.logWarningMsg("subTestDir(" + subTestDir.getPath() 
                                       + ") does not exist, skipping!");
                continue;
            }

            // Construct matching directories for outputs and golds
            File subOutDir = new File(outputDir, subdirs[i]);
            File subGoldDir = new File(goldDir, subdirs[i]);

            // Validate that each of the specified dirs exists
            // Returns directory references like so:
            //  testDirectory = 0, outDirectory = 1, goldDirectory = 2
            File[] dirs = validateDirs(new File[] { subTestDir }, 
                                       new File[] { subOutDir, subGoldDir });

            if (null == dirs)  // also ensures that dirs[0] is non-null
            {
                // Just log it and continue; presumably we'll find 
                //  other directories to test
                reporter.logWarningMsg("subTestDir(" + subTestDir.getPath() 
                                       + ") or associated dirs does not exist, skipping!");
                continue;
            }

            // Call worker method to process the individual directory
            //  and get a list of .xsl files to test
            Vector files = getFilesFromDir(subTestDir, getFileFilter(), embedded);
            Hashtable goldFiles = getGoldsFromDir(subGoldDir, getGoldFileFilter(), processor);

            // 'Transform' the list of individual test files into a 
            //  list of Datalets with all fields filled in
            //@todo should getFilesFromDir and buildDatalets be combined?
            Vector datalets = buildDatalets(files, subTestDir, subOutDir, subGoldDir, goldFiles);

            if ((null == datalets) || (0 == datalets.size()))
            {
                // Just log it and continue; presumably we'll find 
                //  other directories to test
                reporter.logWarningMsg("subTestDir(" + subTestDir.getPath() 
                                       + ") did not contain any tests, skipping!");
                continue;
            }

            // Now process the list of files found in this dir
            processFileList(datalets, "Conformance test of: " + subdirs[i]);
        } // end of for...
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
            reporter.checkErr("Testlet or datalets are null/blank, nothing to test!");
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
                //@todo improve the below to output more useful info
                reporter.checkFail("Datalet num " + ctr + " threw: " + t.toString());
                reporter.logThrowable(Logger.ERRORMSG, t, "Datalet threw");
            }
        }  // of while...
        reporter.testCaseClose();
    }


    /**
     * Use the supplied filter on given directory to return a list 
     * of stylesheet tests to be run.
     * Uses the normal filter for variations of *.xsl files, and 
     * also constructs names for any -embedded tests found (which 
     * may be .xml with xml-stylesheet PI's, not just .xsl)
     *
     * @param dir directory to scan
     * @param filter to use on this directory; if null, uses default
     * @param embeddedFiles special list of embedded files to find
     * @return Vector of local path\filenames of tests to run;
     * the tests themselves will exist; null if error
     */
    public Vector getFilesFromDir(File dir, FilenameFilter filter, String embeddedFiles)
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
        reporter.logTraceMsg("getFilesFromDir(" + dir.toString() + ") found " + v.size() + " xsl files to test");

        // Also get a list of any embedded test files here
        //  Optimization: only look for embedded files when likely to find them
        if ((null != embeddedFiles) && (embeddedFiles.indexOf(dir.getName()) > -1))
        {
            // OK, presumably we have an embedded file in the current dir, 
            //  add that name
            StringTokenizer st = new StringTokenizer(embeddedFiles, ";");//@todo resource ;
            while (st.hasMoreTokens())
            {
                String embeddedName = st.nextToken();
                // Check if it's in our dir...
                if (embeddedName.startsWith(dir.getName()))
                {
                    // ...and that it exists
                    if ((new File(dir.getPath() + File.separator + embeddedName)).exists())
                    {
                        v.addElement(embeddedName);
                    }
                    else
                    {
                        reporter.logWarningMsg("Requested embedded file " + dir.getPath() + File.separator + embeddedName
                                               + " does not exist, skipping");
                    }
                }
            }
        }
        reporter.logTraceMsg("getFilesFromDir(" + dir.toString() + ") found " + v.size() + " total files to test");
        return v;
    }

    /**
     * Use the supplied filter on given directory to return a list 
     * of stylesheet tests to be run.
     * Uses the normal filter for variations of *.xsl files, and 
     * also constructs names for any -embedded tests found (which 
     * may be .xml with xml-stylesheet PI's, not just .xsl)
     *
     * @param dir directory to scan
     * @param filter to use on this directory; if null, uses default
     * @param embeddedFiles special list of embedded files to find
     * @return Vector of local path\filenames of tests to run;
     * the tests themselves will exist; null if error
     */
    public Hashtable getGoldsFromDir(File dir, FilenameFilter filter, String processor)
    {
        // Validate arguments
        if ((null == dir) || (!dir.exists()))
        {
            // Bad arguments, report it as an error
            // Note: normally, this should never happen, since 
            //  this class normally validates these arguments 
            //  before calling us
            reporter.logWarningMsg("getGoldsFromDir(" + dir.toString() + ") dir null or does not exist");
            return null;
        }
        // Get the list of 'normal' test files
        String[] files = dir.list(filter);
        Hashtable h = new Hashtable();
        for (int i = 0; i < files.length; i++)
        {        
        	// Check after the first .
        	// if "out", assume to be 'default' file
        	// if name of the processor, override default file.
        	StringTokenizer strTok = new StringTokenizer(files[i],".");
        	String testCase = strTok.nextToken();
        	String procChk = strTok.nextToken();
        	if (procChk.equals(processor) ||
        	    (procChk.equals("out") && h.get(testCase) == null)) {
        	  h.put(testCase,files[i]);
      	    }
        }
        reporter.logTraceMsg("getGoldsFromDir(" + dir.toString() + ") found " + h.size() + " gold files to test");
        return h;
    }

    /**
     * Transform a vector of individual test names into a Vector 
     * of filled-in datalets to be tested
     *
     * This basically just calculates local path\filenames across 
     * the three presumably-parallel directory trees of testLocation 
     * (inputDir), outLocation (outputDir) and goldLocation 
     * (goldDir).  It then stuffs each of these values plus some 
     * generic info like our testProps into each datalet it creates.
     * 
     * @param files Vector of local path\filenames to be tested
     * @param testLocation File denoting directory where all 
     * .xml/.xsl tests are found
     * @param outLocation File denoting directory where all 
     * output files should be put
     * @param goldLocation File denoting directory where all 
     * gold files are found
     * @return Vector of StylesheetDatalets that are fully filled in,
     * i.e. outputName, goldName, etc are filled in respectively 
     * to inputName
     */
    public Vector buildDatalets(Vector files, File testLocation, 
                                File outLocation, File goldLocation,
                                Hashtable goldFiles)
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
            // Check if it's a normal .xsl file, or a .xml file
            //  (we assume .xml files are embedded tests!)
            StylesheetDatalet d = new StylesheetDatalet();
            if (file.endsWith(XML_EXTENSION))
            {
                d.xmlName = testLocation.getPath() + File.separator + file;

                String fileNameRoot = file.substring(0, file.indexOf(XML_EXTENSION));
                d.inputName = null;
                
                d.outputName = outLocation.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
                if (goldFiles != null && goldFiles.get(fileNameRoot) != null) {
                  d.goldName = goldLocation.getPath() + File.separator + goldFiles.get(fileNameRoot);
                } else {
                  d.goldName = goldLocation.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
                }                  
            }
            else if (file.endsWith(XSL_EXTENSION))
            {
                d.inputName = testLocation.getPath() + File.separator + file;

                String fileNameRoot = file.substring(0, file.indexOf(XSL_EXTENSION));
                d.paramName = testLocation.getPath() + File.separator + fileNameRoot + PARAM_EXTENSION;
                d.xmlName = testLocation.getPath() + File.separator + fileNameRoot + XML_EXTENSION;
                d.outputName = outLocation.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
                d.goldName = goldLocation.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
                if (goldFiles != null && goldFiles.get(fileNameRoot) != null) {
                  d.goldName = goldLocation.getPath() + File.separator + goldFiles.get(fileNameRoot);
                } else {
                  d.goldName = goldLocation.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
                }                  
                
            }
            else
            {
                // Hmmm - I'm not sure what we should do here
                reporter.logWarningMsg("Unexpected test file found, skipping: " + file);
                continue;
            }
            d.setDescription(file);
            d.flavor = flavor;
            // Also copy over our own testProps as it's 
            //  options: this allows for future expansion
            //  of values in the datalet
            d.options = new Properties(testProps);
            // Optimization: put in a copy of our fileChecker, so 
            //  that each testlet doesn't have to create it's own
            //  fileCheckers should not store state, so this 
            //  shouldn't affect the testing at all
            d.options.put("fileCheckerImpl", fileChecker);
            if (traceMode != null) {
                d.options.put(TransformWrapper.SET_PROCESSOR_ATTRIBUTES  + "setTraceListener", d.outputName + LOG_EXTENSION);
            }
            v.addElement(d);
        }
        return v;
    }


    /**
     * Validate existence of or create various directories needed.
     * <p>If any optionalDir cannot be created, it's array entry 
     * will be null.</p>
     *
     * @param requiredDirs array of directories that must previously 
     * exist; if none do, will return null; if none passed, return null
     * @param optionalDirs array of optional directories; if they do 
     * not exist they'll be created
     * @return array of file objects, null if any error; all 
     * required dirs are first, in order; then all optionalDirs
     */
    public File[] validateDirs(File[] requiredDirs, File[] optionalDirs)
    {
        if ((null == requiredDirs) || (0 == requiredDirs.length))
        {
            return null;
        }
    
        File[] dirs = new File[(requiredDirs.length + optionalDirs.length)];
        int ctr = 0;

        try
        {
            // Validate requiredDirs exist first
            for (int ir = 0; ir < requiredDirs.length; ir++)
            {
                if (!requiredDirs[ir].exists())
                {
                    reporter.logErrorMsg("validateDirs("
                                         + requiredDirs[ir]
                                         + ") requiredDir did not exist!");
                    return null;
                }
                dirs[ctr] = requiredDirs[ir];
                ctr++;
            }

            // Create any optionalDirs needed
            for (int iopt = 0; iopt < optionalDirs.length; iopt++)
            {
                if (!optionalDirs[iopt].exists())
                {
                    if (!optionalDirs[iopt].mkdirs())
                    {
                        reporter.logWarningMsg("validateDirs("
                                               + optionalDirs[iopt]
                                               + ") optionalDir could not be created");
                        dirs[ctr] = null;
                    }
                    else
                    {
                        reporter.logTraceMsg("validateDirs("
                                             + optionalDirs[iopt]
                                             + ") optionalDir was created");
                        dirs[ctr] = optionalDirs[iopt];
                    }
                }
                else
                {  
                    // It does previously exist, so copy it over
                    dirs[ctr] = optionalDirs[iopt];
                }
                ctr++;
            }
        }
        catch (Exception e)
        {
            reporter.logThrowable(Logger.ERRORMSG, e, "validateDirs threw: " + e.toString());
            return null;
        }

        return dirs;
    }


    /** Default FilenameFilter FQCN for directories.   */
    protected String defaultDirFilter = "org.apache.qetest.xsl.ConformanceDirRules";

    /** Default FilenameFilter FQCN for files.   */
    protected String defaultFileFilter = "org.apache.qetest.xsl.ConformanceFileRules";

    /** Default GoldFilenameFilter FQCN for files.   */
    protected String defaultGoldFileFilter = "org.apache.qetest.xsl.GoldFileRules";


    /** Default Testlet FQCN for executing stylesheet tests.   */
    protected String defaultTestlet = "org.apache.qetest.xsl.StylesheetTestlet";

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
                                                              QetestUtils.defaultPackages,
                                                              defaultTestlet);
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
     * @return FilenameFilter using ConformanceDirRules(category).
     */
    public FilenameFilter getDirFilter()
    {
        // Find a FilenameFilter class to use
        Class clazz = QetestUtils.testClassForName(dirFilter, 
                                                   QetestUtils.defaultPackages,
                                                   defaultDirFilter);
        try
        {
            // Create it, optionally with a category
            String category = testProps.getProperty(OPT_CATEGORY);
            if ((null != category) && (category.length() > 1))  // Arbitrary check for non-null, non-blank string
            {
                Class[] parameterTypes = { java.lang.String.class };
                Constructor ctor = clazz.getConstructor(parameterTypes);

                Object[] ctorArgs = { category };
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
     * @return FilenameFilter using ConformanceFileRules(excludes).
     */
    public FilenameFilter getFileFilter()
    {
        // Find a FilenameFilter class to use
        Class clazz = QetestUtils.testClassForName(fileFilter, 
                                                   QetestUtils.defaultPackages,
                                                   defaultFileFilter);
        try
        {
            // Create it, optionally with excludes
            String excludes = testProps.getProperty(OPT_EXCLUDES);
            if ((null != excludes) && (excludes.length() > 1))  // Arbitrary check for non-null, non-blank string
            {
                Class[] parameterTypes = { java.lang.String.class };
                Constructor ctor = clazz.getConstructor(parameterTypes);

                Object[] ctorArgs = { excludes };
                return (FilenameFilter) ctor.newInstance(ctorArgs);
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
     * @return FilenameFilter using ConformanceFileRules(excludes).
     */
    public FilenameFilter getGoldFileFilter()
    {
        // Find a FilenameFilter class to use
        Class clazz = QetestUtils.testClassForName(fileFilter, 
                                                   QetestUtils.defaultPackages,
                                                   defaultGoldFileFilter);
        try
        {
            // Create it, optionally with excludes
            String excludes = testProps.getProperty(OPT_EXCLUDES);
            if ((null != excludes) && (excludes.length() > 1))  // Arbitrary check for non-null, non-blank string
            {
                Class[] parameterTypes = { java.lang.String.class };
                Constructor ctor = clazz.getConstructor(parameterTypes);

                Object[] ctorArgs = { excludes };
                return (FilenameFilter) ctor.newInstance(ctorArgs);
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
        return ("Additional options supported by StylesheetTestletDriver:\n"
                + "    -" + OPT_FILELIST
                + "   <name of listfile of tests to run>\n"
                + "    -" + OPT_DIRFILTER
                + "  <classname of FilenameFilter for dirs>\n"
                + "    -" + OPT_FILEFILTER
                + " <classname of FilenameFilter for files>\n"
                + "    -" + OPT_TESTLET
                + "    <classname of Testlet to execute tests with>\n"
                + "    -" + OPT_EMBEDDED
                + "   <list;of;specific file.xml embedded tests to run>\n" 
                + "    -" + OPT_FLAVOR
                + "     <trax.sax|trax.dom|etc> which TransformWrapper to use\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        StylesheetTestletDriver app = new StylesheetTestletDriver();
        app.doMain(args);
    }
}
