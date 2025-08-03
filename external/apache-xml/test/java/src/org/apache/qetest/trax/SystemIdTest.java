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
 * SystemIdTest.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;

//-------------------------------------------------------------------------

/**
 * Test behavior of various types of systemId forms.  
 * <b>Note:</b> This test is directory-dependent, so if there are 
 * any fails, check the code to see what the test file is expecting 
 * the path/directory/etc. to be.
 * I'm also using RFC1738 as the definition of what various 
 * systemId's should look like: the validation of this test is 
 * a little tricky, since it's not necessarily clear exactly which 
 * forms of file: always have to be supported by the application.
 * (Or which forms of file: are supported by the underlying parser) 
 * Also, we really need to write some platform-dependent tests 
 * for this area, to account for 'c:\foo' type paths in Windows, 
 * and for '/usr/bin' type paths in UNIX-land, as well as various 
 * links and such (although that may be beyond the scope of what 
 * Xalan and it's parser do, and more into testing the JDK itself).
 *
 * Note RFC1738 specifies that file: URL's always use 
 *  forward slash / as a file separator
 * ABSOLUTE-PATHS
 * file:///e:/path/file.txt absolute path to e:/path/file.txt
 *
 * file://localhost/e:/path/file.txt path to e:/path/file.txt 
 *  on localhost, but goes direct to filesystem
 *
 * file://otherhost/e:/path/file.txt absolute path to
 *  otherhost, but RFC1738 doesn't actually specify the 
 *  protocol to use to get to the other machine to actually 
 *  get the file
 *
 * RELATIVE-PATHS
 * file:e:/path/file.txt is theoretically a relative URL
 *  w/r/t the current Base URL
 *
 * file:/e:/path/file.txt is theoretically a relative URL
 *  that starts with /e:/... - not likely to be useful
 *
 * file://file.txt theoretically a hostname (not a filename)
 *  with no path or file portion
 *
 * ILLEGAL-PATHS
 * file://e:\path\file.txt is illegal, since RFC1738
 *  specifically says / forward slashes
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SystemIdTest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Name of a valid, known-good xsl/xml file pair we can use.
     */
    protected XSLTestfileInfo knownGoodFileInfo = new XSLTestfileInfo();

    /** 
     * Just basename of a valid, known-good file, both .xsl/.xml .
     */
    protected String knownGoodBaseName = null;

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Convenience variable for user.dir - cached during test.  */
    protected String savedUserDir = null;

    /** Internal flag for test that we have not yet verified expected result.  */
    protected static final String EXPECTED_RESULT_UNKNOWN = "EXPECTED_RESULT_UNKNOWN";
    
    /** Internal flag for test that should return non-null (and no exceptions).  */
    protected static final String EXPECTED_RESULT_NONNULL = "EXPECTED_RESULT_NONNULL";

    /** 
     * Internal flag for test that should do a transform.  
     * Presumably using the systemId item you just tested and 
     * one of our known-good test files.
     */
    protected static final String EXPECTED_RESULT_DOTRANSFORM = "EXPECTED_RESULT_DOTRANSFORM";

    /** Just initialize test name, comment, numTestCases. */
    public SystemIdTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "SystemIdTest";
        testComment = "Test behavior of various types of systemId forms";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
     * cache user.dir property.  
     *
     * @param p Properties to initialize from (unused)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;

        // Just bare pathnames, not URI's
        knownGoodBaseName = testName;
        knownGoodFileInfo.inputName = testBasePath + knownGoodBaseName + ".xsl";
        knownGoodFileInfo.xmlName = testBasePath + knownGoodBaseName + ".xml";
        knownGoodFileInfo.goldName = goldBasePath + knownGoodBaseName + ".out";

        // Cache user.dir property
        savedUserDir = System.getProperty("user.dir");
        reporter.logHashtable(Logger.STATUSMSG, System.getProperties(), "System.getProperties()");
        reporter.logHashtable(Logger.STATUSMSG, testProps, "testProps");

        return true;
    }


    /**
     * Cleanup this test - uncache user.dir property.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileClose(Properties p)
    {
        // Uncache user.dir property
        System.getProperties().put("user.dir", savedUserDir);
        return true;
    }


    /**
     * Test various forms of XSL and XML systemIds to see what happens.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Test various forms of XSL and XML systemIds to see what happens");

        // The path the user gave us in inputDir:
        // inputDir + "/" + TRAX_SUBDIR + "/" filename
        String inputDirPath = inputDir.replace('\\', '/') 
                              + "/" + TRAX_SUBDIR + "/" + knownGoodBaseName;
        //@todo: determine what type of path inputDir itself is: 
        //  downwards relative, upwards relative, or absolute

        // Assumed correct user.dir path (should be in xml-xalan\test):
        // System.getProperty("user.dir") + "/tests/api/" + TRAX_SUBDIR
        // user.dir in theory should always be absolute
        String userDirPath = System.getProperty("user.dir").replace('\\', '/')
                             + "/tests/api/" + TRAX_SUBDIR + "/" + knownGoodBaseName;

        // Verify that user.dir is in the right place relative to 
        //  the checked-in known-good files
        String userDirExpected = EXPECTED_RESULT_UNKNOWN;
        File f1 = new File(userDirPath + ".xsl");
        File f2 = new File(userDirPath + ".xml");
        if (f1.exists() && f2.exists())
        {
            // The known-good files are there, so expect we can use it
            userDirExpected = EXPECTED_RESULT_DOTRANSFORM;
        }
        else
        {
            reporter.logWarningMsg("Known good files does not appear to exist at: "
                                   + userDirPath + ".xml/.xsl");
        }

        String xslTestIds[][] = 
        {
            // { systemId to test,
            //   description of the test,
            //   expected XSL behavior or exception, 
            //   expected XSL inner exception, 
            //   expected XML behavior or exception, 
            //   expected XML inner exception
            // }

            // Test variations on the inputDir specified by the 
            //  user, to be able to do some adhoc testing
            { "file:///" + inputDirPath, 
            "file:///, user-specified inputDir, /blah1[1a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            { "file://localhost/" + inputDirPath, 
            "file://localhost/, user-specified inputDir, /blah[1b]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            { inputDirPath, 
            "Just user-specified inputDir, /blah (works normally, if relative)[1c]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            // Test variations on the System user.dir; validation 
            //  depends on if it's set correctly
            { "file:///" + userDirPath, 
            "file:///, System(user.dir), /blah (works normally)[2a]",
            userDirExpected,
            null,
            userDirExpected,
            null },

            { "file://localhost/" + userDirPath, 
            "file://localhost/, System(user.dir), /blah (works normally)[2b]",
            userDirExpected,
            null,
            userDirExpected,
            null },

            { userDirPath, 
            "Just System(user.dir), /blah[2c]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            // Absolute path with blank . step
            { "file:///" + System.getProperty("user.dir").replace('\\', '/') 
                         + "/tests/./api/" + TRAX_SUBDIR + "/" + knownGoodBaseName, 
            "file:///, System(user.dir), /./blah (???)[2d]",
            userDirExpected,
            null,
            userDirExpected,
            null },

            // Absolute path with up/down steps
            { "file:///" + System.getProperty("user.dir").replace('\\', '/') 
                         + "/tests/../tests/api/" + TRAX_SUBDIR + "/" + knownGoodBaseName, 
            "file:///, System(user.dir), /updir/../downdir/blah (???)[2e]",
            userDirExpected,
            null,
            userDirExpected,
            null },

            // Just relative paths, should work if user.dir correct
            // Arguable: comment out for 2.0
            { "file:tests/api/" + TRAX_SUBDIR + "/" + knownGoodBaseName, 
            "Just file:/blah relative path[3a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            { "tests/api/" + TRAX_SUBDIR + "/" + knownGoodBaseName, 
            "Just /blah relative path[3b]",
            userDirExpected,
            null,
            userDirExpected,
            null },

            // file://blah should be interperted as a hostname, 
            //  not as a filename, and should fail
            { "file://" + userDirPath, 
            "file://, System(user.dir), /blah (causes hostname error)[4a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
        // Comment out for 2.0 due to SPR SCUU4SUQXU
        /*
            "javax.xml.transform.TransformerConfigurationException", 
            "java.net.UnknownHostException",
            "javax.xml.transform.TransformerException", 
            "java.net.UnknownHostException" },
        */

            { "file://" + inputDirPath, 
            "file://, user-specified inputDir, /blah (causes hostname error)[4b]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
        // Comment out for 2.0 due to SPR SCUU4SUQXU
        /*
            "javax.xml.transform.TransformerConfigurationException", 
            "java.net.UnknownHostException",
            "javax.xml.transform.TransformerException", 
            "java.net.UnknownHostException" },
        */

            // file://host.does.not.exist/blah should fail, here we 
            //  can also validate the error message completely
            { "file://this.host.does.not.exist/" + userDirPath, 
            "file://this.host.does.not.exist/userDir/blah (causes hostname error)[4c]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
        // Comment out for 2.0 due to SPR SCUU4SUQXU
        /*
            "javax.xml.transform.TransformerConfigurationException: this.host.does.not.exist", 
            "java.net.UnknownHostException: this.host.does.not.exist",
            "javax.xml.transform.TransformerException: this.host.does.not.exist", 
            "java.net.UnknownHostException" },
        */

            { "file://this.host.does.not.exist/" + inputDirPath, 
            "file://this.host.does.not.exist/inputDir/blah (causes hostname error)[4d]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
        // Comment out for 2.0 due to SPR SCUU4SUQXU
        /*
            "javax.xml.transform.TransformerConfigurationException: this.host.does.not.exist", 
            "java.net.UnknownHostException: this.host.does.not.exist",
            "javax.xml.transform.TransformerException: this.host.does.not.exist", 
            "java.net.UnknownHostException" },
        */
            

            // Too few leading slashes for the file: spec, probably error
            { "file:/" + userDirPath, 
            "file:/, System(user.dir), /blah (probable error)[5a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            { "file:/" + inputDirPath, 
            "file:/, user-specified inputDir, /blah (probable error)[5b]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            // No leading slashes for the file: spec, behavior is?
            { "file:" + userDirPath, 
            "file:, System(user.dir), /blah (probable error)[6a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },

            { "file:" + inputDirPath, 
            "file:, user-specified inputDir, /blah (probable error)[6b]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
            

            // Using backslashes in the path portion is explicitly
            //  forbidden in the RFC, should give error            
            { "file:///" + userDirPath.replace('/', '\\'),
            "file:///, System(user.dir) \\blah, (backslashes are illegal)[7a]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null },
            { "file:///" + inputDirPath.replace('/', '\\'),
            "file:///, user-specified inputDir \\blah (backslashes are illegal)[7b]",
            EXPECTED_RESULT_UNKNOWN,
            null,
            EXPECTED_RESULT_UNKNOWN,
            null }
        };

        for (int i = 0; i < xslTestIds.length; i++)
        {
            // Loop and attempt to do newTemplates() with each
            testNewTemplatesWithSystemId(xslTestIds[i][0] + ".xsl", xslTestIds[i][1], 
                                         xslTestIds[i][2], xslTestIds[i][3]);

            // Loop and attempt to do newTransformer() with each 
            //  as well, since they have slightly different codepaths
            testNewTransformerWithSystemId(xslTestIds[i][0] + ".xsl", xslTestIds[i][1], 
                                           xslTestIds[i][2], xslTestIds[i][3]);

            // Loop and attempt to do a transform of an xml 
            //  document with each, using known-good stylesheet
            testTransformWithSystemId(xslTestIds[i][0] + ".xml", xslTestIds[i][1], 
                                      xslTestIds[i][4], xslTestIds[i][5]);
        }

        reporter.testCaseClose();
        return true;
    }

    /**
     * Test setting various forms of systemIds to see what happens.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Test setting various forms of systemId to see what happens");

        // This will have imports/includes on various levels, and then 
        //  set the systemId of a Source to hopefully pull in the 
        //  different imports/includes
        reporter.checkPass("//@todo implement this testcase");

        reporter.testCaseClose();
        return true;
    }

    /**
     * Worker method to test factory.newTemplates.
     * Performs validation of various types based on the 
     * expected value.
     *
     * @param sysId systemId of XSL to test with 
     * @param desc description of test, used in check() calls
     * @param expected either one of the EXPECTED_RESULT_* flags 
     * defined in this file, or the start of a .toString of the 
     * exception that you expect will be thrown
     * @param innerExpected the start of a .toString of the 
     * inner or wrapper exception that you expect will be thrown,
     * presumably wrapped inside a TransformerException; if null, 
     * then this is not checked for
     * @return Templates object created as side effect
     */
    protected Templates testNewTemplatesWithSystemId(String sysId, String desc, 
                                                     String expected, String innerExpected)
    {
        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        Throwable thrown = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Use a StreamSource with the systemId, which sets itself automatically
            Source source = new StreamSource(sysId);
            // Changed order of params for easier logging
            reporter.logStatusMsg("newTemplates(" + desc + "): " + sysId + ", " + expected);
            templates = factory.newTemplates(source);
            reporter.logTraceMsg("newTemplates() no exceptions!");
            // Just get the templates now for convenience, 
            //  this implicitly tests that they're non-null
            transformer = templates.newTransformer();
        }
        catch (Throwable t)
        {
            thrown = t;
            reporter.logStatusMsg("newTemplates(" + desc + ") threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "newTemplates(" + desc + ") threw");
        }
        // Call worker method to perform actual validation
        validateWithSystemId(sysId, desc, expected, innerExpected, thrown, transformer);
        return templates;
    }

    /**
     * Worker method to test factory.newTransformer.
     * Performs validation of various types based on the 
     * expected value.
     *
     * @param sysId systemId of XSL to test with 
     * @param desc description of test, used in check() calls
     * @param expected either one of the EXPECTED_RESULT_* flags 
     * defined in this file, or the start of a .toString of the 
     * exception that you expect will be thrown
     * @param innerExpected the start of a .toString of the 
     * inner or wrapper exception that you expect will be thrown,
     * presumably wrapped inside a TransformerException; if null, 
     * then this is not checked for
     * @return Transformer object created as side effect
     */
    protected Transformer testNewTransformerWithSystemId(String sysId, String desc, 
                                                         String expected, String innerExpected)
    {
        TransformerFactory factory = null;
        Transformer transformer = null;
        Throwable thrown = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Use a StreamSource with the systemId, which sets itself automatically
            Source source = new StreamSource(sysId);
            reporter.logStatusMsg("newTransformer(" + desc + "): " + sysId + ", " + expected);
            transformer = factory.newTransformer(source);
            reporter.logTraceMsg("newTransformer() no exceptions!");
        }
        catch (Throwable t)
        {
            thrown = t;
            reporter.logStatusMsg("newTransformer(" + desc + ") threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "newTransformer(" + desc + ") threw");
        }
        // Call worker method to perform actual validation
        validateWithSystemId(sysId, desc, expected, innerExpected, thrown, transformer);
        return transformer;
    }

    /**
     * Worker method to test transformer.transform().
     * Performs validation of various types based on the 
     * expected value, using a known-good XSL file.
     * 
     * @param sysId systemId of XML file to test with 
     * @param desc description of test, used in check() calls
     * @param expected either one of the EXPECTED_RESULT_* flags 
     * defined in this file, or the start of a .toString of the 
     * exception that you expect will be thrown
     * @param innerExpected the start of a .toString of the 
     * inner or wrapper exception that you expect will be thrown,
     * presumably wrapped inside a TransformerException; if null, 
     * then this is not checked for
     */
    protected void testTransformWithSystemId(String sysId, String desc, 
                                             String expected, String innerExpected)
    {
        TransformerFactory factory = null;
        Transformer transformer = null;
        Throwable thrown = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Use a StreamSource with the known-good systemId, which sets itself automatically
            Source source = new StreamSource(knownGoodFileInfo.inputName);
            reporter.logStatusMsg("Transform(" + desc + "): " + sysId + ", " + expected);
            transformer = factory.newTransformer(source);

            // Always try the transform using a new StreamSource
            reporter.logTraceMsg("About to transform(StreamSource(" + sysId + ", " + outNames.nextName() + ")");
            transformer.transform(new StreamSource(sysId), 
                                  new StreamResult(outNames.currentName()));
        }
        catch (Throwable t)
        {
            thrown = t;
            reporter.logStatusMsg("Transform(" + desc + ") threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Transform(" + desc + ") threw");
        }

        // Do our own validation since we've already done a transform implicitly
        // Sorry for the icky if..else.. statement
        if (EXPECTED_RESULT_UNKNOWN.equals(expected))
        {
            // Just log a message: no validation done
            reporter.logWarningMsg("(" + desc + ") not validated!");
        }
        else if (EXPECTED_RESULT_NONNULL.equals(expected))
        {
            // Just validate that our object is non-null
            reporter.check((transformer != null), true, "(" + desc + ") is non-null");
        }
        else if (EXPECTED_RESULT_DOTRANSFORM.equals(expected))
        {
            int result = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(knownGoodFileInfo.goldName), 
                              "(" + desc + ") transform into: " + outNames.currentName());
            if (result == reporter.FAIL_RESULT)
                reporter.logInfoMsg("(" + desc + ") transform failure reason:" + fileChecker.getExtendedInfo());
        }
        else
        {
            // Otherwise, assume it's a string that any exception 
            //  thrown should match the start of our message
            validateException(sysId, desc, expected, innerExpected, thrown);
        }
    }


    /**
     * Worker method to validate either a transform or an exception.
     *
     * @param sysId systemId that you were testing with; used in logging 
     * @param desc description of test, used in check() calls
     * @param expected either one of the EXPECTED_RESULT_* flags 
     * defined in this file, or the start of a .toString of the 
     * exception that you expect will be thrown, only 
     * used when expected=EXPECTED_RESULT_DOTRANSFORM
     * @param innerExpected the start of a .toString of the 
     * inner or wrapper exception that you expect will be thrown,
     * presumably wrapped inside a TransformerException; if null, 
     * then this is not checked for
     * @param thrown any Throwable that was thrown in your operation
     * @param transformer that was created from your operation; only 
     * used for transform when expected=EXPECTED_RESULT_DOTRANSFORM
     */
    protected void validateWithSystemId(String sysId, String desc, 
                                        String expected, String innerExpected,
                                        Throwable thrown, 
                                        Transformer transformer)
    {
        // Sorry for the icky if..else.. statement
        if (EXPECTED_RESULT_UNKNOWN.equals(expected))
        {
            // Just log a message: no validation done
            reporter.logWarningMsg("(" + desc + ") not validated!");
        }
        else if (EXPECTED_RESULT_NONNULL.equals(expected))
        {
            // Just validate that our object is non-null
            reporter.check((transformer != null), true, "(" + desc + ") is non-null");
        }
        else if (EXPECTED_RESULT_DOTRANSFORM.equals(expected))
        {
            try
            {
                // First validate that our object is non-null
                if (transformer != null)
                {
                    // Actually try to use the object in a transform
                    transformer.transform(new StreamSource(QetestUtils.filenameToURL(knownGoodFileInfo.xmlName)), 
                                          new StreamResult(outNames.nextName()));
                    int result = fileChecker.check(reporter, 
                                      new File(outNames.currentName()), 
                                      new File(knownGoodFileInfo.goldName), 
                                      "(" + desc + ") transform into: " + outNames.currentName());
                    if (result == reporter.FAIL_RESULT)
                        reporter.logInfoMsg("(" + desc + ") transform failure reason:" + fileChecker.getExtendedInfo());
                }
                else
                {
                    reporter.checkFail("(" + desc + ") transformer was null!");
                }
            }
            catch (Throwable t)
            {
                reporter.checkFail("(" + desc + ") do transform threw:" + t.toString());
                reporter.logThrowable(reporter.ERRORMSG, t, "(" + desc + ") do transform threw");
            }
        }
        else
        {
            // Otherwise, assume it's a string that any exception 
            //  thrown should match the start of our message
            validateException(sysId, desc, expected, innerExpected, thrown);
        }
    }

    /**
     * Worker method to validate just a thrown exception.
     *
     * @param sysId systemId that you were testing with; currently unused? 
     * @param desc description of test, used in check() calls
     * @param expected the start of a .toString of the 
     * exception that you expect will be thrown
     * @param innerExpected the start of a .toString of the 
     * inner or wrapper exception that you expect will be thrown,
     * presumably wrapped inside a TransformerException; if null, 
     * then this is not checked for
     * @param thrown any Throwable that was thrown in your operation
     */
    protected void validateException(String sysId, String desc, 
                                     String expected, String innerExpected,
                                     Throwable thrown)
    {
        if (thrown == null)
        {
            reporter.checkFail("(" + desc + ") No exception was thrown when expected");
        }
        else
        {
            reporter.check(thrown.toString().startsWith(expected), true, "(" + desc + ") expected exception");
            if ((innerExpected != null)
                && (thrown instanceof TransformerException))
            {
                // Also validate any innerExceptions
                Throwable inner = ((TransformerException)thrown).getException();
                if (inner != null)
                {
                    reporter.check(inner.toString().startsWith(innerExpected), true, "(" + desc + ") inner expected exception");
                }
                else
                {
                    // User specified an innerException but none 
                    //  was found, so fail
                    reporter.checkFail("(" + desc + ") No innerException found like: " + innerExpected);
                }
            }
        }
    }

    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by SystemIdTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + "(Note: test is directory-dependent!)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SystemIdTest app = new SystemIdTest();
        app.doMain(args);
    }
}
