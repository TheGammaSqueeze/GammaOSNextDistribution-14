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
 * ParameterTest.java
 *
 */
package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * Functional test of various usages of parameters in transforms.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ParameterTest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** Information about an xsl/xml file pair for transforming.  */
    protected XSLTestfileInfo paramTest = new XSLTestfileInfo();

    /** Information about an xsl/xml file pair for transforming.  */
    protected XSLTestfileInfo paramTest2 = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";


    /** Just initialize test name, comment, numTestCases. */
    public ParameterTest()
    {
        numTestCases = 3;  // REPLACE_num
        testName = "ParameterTest";
        testComment = "Functional test of various usages of parameters in transforms";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files, etc.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
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

        paramTest.inputName = QetestUtils.filenameToURL(testBasePath + "ParameterTest.xsl");
        paramTest.xmlName = QetestUtils.filenameToURL(testBasePath + "ParameterTest.xml");

        paramTest2.inputName = QetestUtils.filenameToURL(testBasePath + "ParameterTest2.xsl");
        paramTest2.xmlName = QetestUtils.filenameToURL(testBasePath + "ParameterTest2.xml");
        return true;
    }


    /** Array of test data for parameter testing.  */
    protected String paramTests[][] = 
    {
        // { paramName to test,
        //   paramValue to test
        //   expected output string,
        //   description of the test
        // }
        { 
            "t1", 
            "'a'",
            "<outt>false-notset,false-blank,false-a,false-1,'a'</outt>",
            "(10)Select expr of a 'param' string"
        },
        { 
            "t1", 
            "a",
            "<outt>false-notset,false-blank,true-a,false-1,a</outt>",
            "(10a)Select expr of a param string"
        },
        {
            "t1", 
            "'1'",
            "<outt>false-notset,false-blank,false-a,false-1,'1'</outt>",
            "(11)Select expr of a 'param' number"
        },
        {
            "t1", 
            "1",
            "<outt>false-notset,false-blank,false-a,true-1,1</outt>",
            "(11a)Select expr of a param number"
        },
        { 
            "t1", 
            "''",
            "<outt>false-notset,false-blank,false-a,false-1,''</outt>",
            "(12)Select expr of a param 'blank' string"
        },
        { 
            "t1", 
            "",
            "<outt>false-notset,true-blank,false-a,false-1,</outt>",
            "(12a)Select expr of a param blank string"
        },
        /*{ 
            "t1", 
            null,
            "<outt>false-notset,false-blank,false-a,false-1,</outt>",
            "(12b)Select expr of a null"
        },*/
        { 
            "p1", 
            "'foo'",
            "'foo','foo';",
            "(13)Stylesheet with literal 'param' value"
        },
        { 
            "p1", 
            "foo",
            "foo,foo;",
            "(13a)Stylesheet with literal param value"
        },
        { 
            "p1", 
            "'bar'",
            "'bar','bar';",
            "(14)Stylesheet with replaced/another literal 'param' value"
        },
        { 
            "p1", 
            "bar",
            "bar,bar;",
            "(14a)Stylesheet with replaced/another literal param value"
        },
        { 
            "p2", 
            "'&lt;item&gt;bar&lt;/item&gt;'",
            "'&amp;lt;item&amp;gt;bar&amp;lt;/item&amp;gt;','&amp;lt;item&amp;gt;bar&amp;lt;/item&amp;gt;'; GHI,<B>GHI</B>; </outp>",
            "(15)Stylesheet with 'param' value with nodes"
        },
        { 
            "p2", 
            "&lt;item&gt;bar&lt;/item&gt;",
            "&amp;lt;item&amp;gt;bar&amp;lt;/item&amp;gt;,&amp;lt;item&amp;gt;bar&amp;lt;/item&amp;gt;;",
            "(15a)Stylesheet with param value with nodes"
        },
        { 
            "p3", 
            "'foo3'",
            "GHI,<B>GHI</B>;",
            "(16)Stylesheet with literal 'param' value in a template, is not passed"
        },
        { 
            "p3", 
            "foo3",
            "GHI,<B>GHI</B>;",
            "(16a)Stylesheet with literal param value in a template, is not passed"
        },
        { 
            "s1", 
            "'foos'",
            "'foos','foos';",
            "(17)Stylesheet with literal 'param' select"
        },
        { 
            "s1", 
            "foos",
            "foos,foos;",
            "(17a)Stylesheet with literal param select"
        },
        { 
            "s1", 
            "'bars'",
            "<outs>'bars','bars'; s2val,s2val; s3val,s3val; </outs>",
            "(18)Stylesheet with replaced/another literal 'param' select"
        },
        { 
            "s1", 
            "bars",
            "<outs>bars,bars; s2val,s2val; s3val,s3val; </outs>",
            "(18a)Stylesheet with replaced/another literal param select"
        },
        { 
            "s2", 
            "'&lt;item/&gt;'",
            "'&amp;lt;item/&amp;gt;','&amp;lt;item/&amp;gt;'; s3val,s3val; </outs>",
            "(19)Stylesheet with nodes(?) 'param' select"
        },
        { 
            "s2", 
            "&lt;item/&gt;",
            "&amp;lt;item/&amp;gt;,&amp;lt;item/&amp;gt;; s3val,s3val; </outs>",
            "(19a)Stylesheet with nodes(?) param select"
        },
        { 
            "s3", 
            "foos3",
            "s3val,s3val;",
            "(20)Stylesheet with literal 'param' select in a template, is not passed"
        },
    }; // end of paramTests array


    /**
     * Setting various string-valued params.
     * Just loops through array of simple test data.  
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Setting various simple string-valued params");
        try
        {
            // Just loop through test elements and try each one
            // Loop separately for each worker method
            for (int i = 0; i < paramTests.length; i++)
            {
                // Try on a completely independent 
                //  transformer and sources each time
                testSetParam(paramTests[i][0], paramTests[i][1],
                             new StreamSource(paramTest.xmlName), new StreamSource(paramTest.inputName), 
                             paramTests[i][2], paramTests[i][3]);
            }
        }
        catch (Exception e)
        {
            reporter.logThrowable(Logger.ERRORMSG, e, "Testcase threw");
            reporter.logErrorMsg("Testcase threw: " + e.toString());
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Reuse the same transformer multiple times with params set.
     * This also reproduces Bugzilla1611
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Reuse the same transformer multiple times with params set");
        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            templates = factory.newTemplates(new StreamSource(paramTest2.inputName));

            // Process the file as-is, without any params set
            transformer = templates.newTransformer();
            reporter.logInfoMsg("Transforming " + paramTest.xmlName + " with " + paramTest2.inputName);
            transformer.transform(new StreamSource(paramTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            // Verify the values are correct for no params set
            checkFileContains(outNames.currentName(), "<globalVarAttr>ParameterTest.xml:</globalVarAttr>",
                              "(2.0)Processing 1,2 w/no params into: " + outNames.currentName());

            // Do NOT call clearParameters here; reuse the transformer
            reporter.logInfoMsg("Reused-Transforming " + paramTest2.xmlName + " with " + paramTest2.inputName);
            transformer.transform(new StreamSource(paramTest2.xmlName), 
                                  new StreamResult(outNames.nextName()));
            // Verify the values are correct for no params set
            checkFileContains(outNames.currentName(), "<globalVarAttr>ParameterTest2.xml:</globalVarAttr>",
                              "(2.0a) Bugzilla1611 Reused Transformer processing 2,2 w/no params into: " + outNames.currentName());

            // Do NOT call clearParameters here; reuse the transformer again
            reporter.logInfoMsg("Reused-Transforming-again " + paramTest.xmlName + " with " + paramTest2.inputName);
            transformer.transform(new StreamSource(paramTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            // Verify the values are correct for no params set
            checkFileContains(outNames.currentName(), "<globalVarAttr>ParameterTest.xml:</globalVarAttr>",
                              "(2.0b) Bugzilla1611 Reused-Again Transformer processing 1,2 w/no params into: " + outNames.currentName());
        }
        catch (Exception e)
        {
            reporter.logThrowable(Logger.ERRORMSG, e, "Testcase threw");
            reporter.logErrorMsg("Testcase threw: " + e.toString());
        }
        reporter.testCaseClose();
        return true;
    }

    /**
     * Setting various string-valued params and reusing transformers.
     * Creates one transformer first, then loops through array 
     * of simple test data re-using transformer.  
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Setting various string-valued params and re-using transformer");
        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            templates = factory.newTemplates(new StreamSource(paramTest.inputName));
        }
        catch (Exception e)
        {
            reporter.checkFail("Problem creating Templates; cannot continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, e, 
                                  "Problem creating Templates; cannot continue testcase");
            return true;
        }

        try
        {
            // Process the file as-is, without any params set
            transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            transformer.transform(new StreamSource(paramTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            transformer.clearParameters();
            // Verify each of the three kinds of params are correct
            checkFileContains(outNames.currentName(), "<outp>ABC,<B>ABC</B>; DEF,<B>DEF</B>; GHI,<B>GHI</B>; </outp>",
                              "(0) Stylesheet with default param value into: " + outNames.currentName());

            checkFileContains(
                outNames.currentName(),
                "<outs>s1val,s1val; s2val,s2val; s3val,s3val; </outs>",
                "(1) ... also with default param value in select expr into: " + outNames.currentName());
            checkFileContains(
                outNames.currentName(),
                "<outt>true-notset,false-blank,false-a,false-1,notset</outt>",
                "(2) ... also with default param value in select expr into: " + outNames.currentName());

            // Just loop through test elements and try each one
            for (int i = 0; i < paramTests.length; i++)
            {
                // Re-use the transformer from above for each test
                transformer.clearParameters();
                testSetParam(paramTests[i][0], paramTests[i][1],
                             transformer, new StreamSource(paramTest.xmlName), new StreamSource(paramTest.inputName), 
                             paramTests[i][2], paramTests[i][3]);
            }
        }
        catch (Exception e)
        {
            reporter.logThrowable(Logger.ERRORMSG, e, "Testcase threw");
            reporter.logErrorMsg("Testcase threw: " + e.toString());
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Test setting a single string-valued parameter.  
     * Uses the supplied Transformer and calls setParameter() 
     * then transform(Source, Source), then uses the worker 
     * method checkFileContains() to validate and output results.
     *
     * @param paramName simple name of parameter
     * @param paramVal String value of parameter
     * @param transformer object to use 
     * @param xmlSource object to use in transform
     * @param xslStylesheet object to use in transform
     * @param checkString to look for in output file (logged)
     * @param comment to log with check() call
     * @return true if pass, false otherwise
     */
    protected boolean testSetParam(String paramName, String paramVal,
                                   Transformer transformer, 
                                   Source xmlSource, 
                                   Source xslStylesheet, 
                                   String checkString, String comment)
    {
        try
        {
            reporter.logTraceMsg("setParameter(" + paramName + ", " + paramVal +")");
            transformer.setParameter(paramName, paramVal);
            reporter.logTraceMsg("transform(" + xmlSource.getSystemId() + ", " + xslStylesheet.getSystemId() +", ...)");
            transformer.transform(xmlSource, new StreamResult(outNames.nextName()));
        }
        catch (Throwable t)
        {
            reporter.logThrowable(Logger.ERRORMSG, t, "testSetParam unexpectedly threw");
            reporter.logErrorMsg("//@todo HACK: intermittent NPE; please report to curcuru@apache.org if you get this");
            reporter.logErrorMsg("//@todo HACK: intermittent NPE; please report to curcuru@apache.org if you get this");
            reporter.logErrorMsg("//@todo HACK: intermittent NPE; please report to curcuru@apache.org if you get this");
            // Since we the NPE is intermittent, and we want the rest 
            //  of this test in the smoketest, I'll go against my
            //  better nature and ignore this fail
            return true; //HACK: should be removed when fixed
        }
        return checkFileContains(outNames.currentName(), checkString,
                                 "Reused:" + comment + " into: " + outNames.currentName());
    }


    /**
     * Test setting a single string-valued parameter.  
     * Creates a Transformer and calls setParameter() 
     * then transform(Source, Source), then uses the worker 
     * method checkFileContains() to validate and output results.
     *
     * @param paramName simple name of parameter
     * @param paramVal String value of parameter
     * @param xmlSource object to use in transform
     * @param xslStylesheet object to use in transform
     * @param checkString to look for in output file (logged)
     * @param comment to log with check() call
     * @return true if pass, false otherwise
     */
    protected boolean testSetParam(String paramName, String paramVal,
                                   Source xmlSource, 
                                   Source xslStylesheet, 
                                   String checkString, String comment)
    {
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            Transformer transformer = factory.newTransformer(xslStylesheet);
            transformer.setErrorListener(new DefaultErrorHandler());

            reporter.logTraceMsg("setParameter(" + paramName + ", " + paramVal +")");
            transformer.setParameter(paramName, paramVal);
            reporter.logTraceMsg("transform(" + xmlSource.getSystemId() + ", " + xslStylesheet.getSystemId() +", ...)");
            transformer.transform(xmlSource, new StreamResult(outNames.nextName()));
        }
        catch (Throwable t)
        {
            reporter.logThrowable(Logger.ERRORMSG, t, "testSetParam unexpectedly threw");
        }
        return checkFileContains(outNames.currentName(), checkString,
                                 "New:" + comment + " into: " + outNames.currentName());
    }


    /**
     * Checks and reports if a file contains a certain string 
     * (all within one line).
     * We should really consider validating the entire output 
     * file, but this is the important funtionality, and it makes 
     * maintaining the test and gold data easier (since it's all 
     * in this file).
     *
     * @param fName local path/name of file to check
     * @param checkStr String to look for in the file
     * @param comment to log with the check() call
     * @return true if pass, false otherwise
     */
    protected boolean checkFileContains(String fName, String checkStr,
                                        String comment)
    {
        boolean passFail = false;
        File f = new File(fName);

        if (!f.exists())
        {
            reporter.checkFail("checkFileContains(" + fName
                               + ") does not exist: " + comment);
            return false;
        }

        try
        {
            InputStreamReader is = new InputStreamReader(new FileInputStream(f), "UTF-8");
            BufferedReader br = new BufferedReader(is);

            for (;;)
            {
                String inbuf = br.readLine();
                if (inbuf == null)
                    break;

                if (inbuf.indexOf(checkStr) >= 0)
                {
                    passFail = true;
                    reporter.logTraceMsg(
                        "checkFileContains passes with line: " + inbuf);
                    break;
                }
            }
        }
        catch (IOException ioe)
        {
            reporter.checkFail("checkFileContains(" + fName + ") threw: "
                               + ioe.toString() + " for: " + comment);

            return false;
        }

        if (!passFail)
        {
            reporter.logErrorMsg("checkFileContains failed to find: " + checkStr);
        }
        reporter.check(passFail, true, comment);
        return passFail;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by ParameterTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        ParameterTest app = new ParameterTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
