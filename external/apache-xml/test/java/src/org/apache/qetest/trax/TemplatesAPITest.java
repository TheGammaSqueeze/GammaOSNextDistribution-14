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
 * TemplatesAPITest.java
 *
 */
package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * Basic API coverage test for the Templates class of TRAX.
 * @author shane_curcuru@lotus.com
 */
public class TemplatesAPITest extends FileBasedTest
{

    /**
     * Cheap-o filename for various output files.
     *
     */
    protected OutputNameManager outNames;

    /** Cheap-o filename set for both API tests and exampleSimple. */
    protected XSLTestfileInfo simpleTest = new XSLTestfileInfo();

    /** Name of a stylesheet with xsl:output HTML. */
    protected String outputFormatXSL = null;

    /** System property name javax.xml.transform.TransformerFactory.  */
    public static final String TRAX_PROCESSOR_XSLT = "javax.xml.transform.TransformerFactory";

    /** Known outputFormat property name from outputFormatTest  */
    public static final String OUTPUT_FORMAT_NAME = OutputKeys.CDATA_SECTION_ELEMENTS;

    /** Known outputFormat property value from outputFormatTest  */
    public static final String OUTPUT_FORMAT_VALUE = "cdataHere";

    /** NEEDSDOC Field TRAX_SUBDIR          */
    public static final String TRAX_SUBDIR = "trax";

    /** Default ctor initializes test name, comment, numTestCases. */
    public TemplatesAPITest()
    {

        numTestCases = 1;  // REPLACE_num
        testName = "TemplatesAPITest";
        testComment = "Basic API coverage test for the Templates class of TRAX";
    }

    /**
     * Initialize this test - Set names of xml/xsl test files, cache system property.  
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean doTestFileInit(Properties p)
    {

        // Used for all tests; just dump files in xapi subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);

        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: "
                                   + outSubDir);

        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SUBDIR
                                         + File.separator + testName, ".out");

        // Used for API coverage and exampleSimple
        String testBasePath = inputDir + File.separator + TRAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir + File.separator + TRAX_SUBDIR
                              + File.separator;

        simpleTest.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIParam.xml");
        simpleTest.inputName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIParam.xsl");
        simpleTest.goldName = goldBasePath + "TransformerAPIParam.out";
        outputFormatXSL = QetestUtils.filenameToURL(testBasePath + "TransformerAPIOutputFormat.xsl");

        reporter.logInfoMsg(TRAX_PROCESSOR_XSLT + " property is: "
                            + System.getProperty(TRAX_PROCESSOR_XSLT));
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(StreamSource.FEATURE)
                  && tf.getFeature(StreamResult.FEATURE)))
            {   // The rest of this test relies on Streams
                reporter.logErrorMsg("Streams not supported! Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; Some tests may be invalid!");
        }

        return true;
    }


    /**
     * TRAX Templates: cover newTransformer(), 
     * getOutputProperties() APIs and basic functionality.
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("TRAX Templates: cover APIs and basic functionality");

        TransformerFactory factory = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
        }
        catch (Exception e)
        {
            reporter.checkFail(
                "Problem creating Processor; cannot continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, e,
                                  "Problem creating Processor");
            return true;
        }

        try
        {
            // Cover APIs newTransformer(), getOutputProperties()
            Templates templates =
                factory.newTemplates(new StreamSource(simpleTest.inputName));
            Transformer transformer = templates.newTransformer();

            reporter.check((transformer != null), true,
                           "newTransformer() is non-null for "
                           + simpleTest.inputName);

            Properties outputFormat = templates.getOutputProperties();

            reporter.check((outputFormat != null), true,
                           "getOutputProperties() is non-null for "
                           + simpleTest.inputName);
            reporter.logHashtable(reporter.STATUSMSG, outputFormat,
                                  "getOutputProperties for " + simpleTest.inputName);

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("xml".equals(outputFormat.getProperty(OutputKeys.METHOD))), true, simpleTest.inputName + ".op.getProperty(" 
                           + OutputKeys.METHOD + ") is default value, act: " + outputFormat.getProperty(OutputKeys.METHOD));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == outputFormat.get(OutputKeys.METHOD)), true, simpleTest.inputName + ".op.get(" 
                           + OutputKeys.METHOD + ") is null value, act: " + outputFormat.get(OutputKeys.METHOD));

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("no".equals(outputFormat.getProperty(OutputKeys.INDENT))), true, simpleTest.inputName + ".op.getProperty(" 
                           + OutputKeys.INDENT + ") is default value, act: " + outputFormat.getProperty(OutputKeys.INDENT));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == (outputFormat.get(OutputKeys.INDENT))), true, simpleTest.inputName + ".op.get(" 
                           + OutputKeys.INDENT + ") is null value, act: " + outputFormat.get(OutputKeys.INDENT));
        }
        catch (Exception e)
        {
            reporter.checkErr("newTransformer/getOutputProperties threw: "
                              + e.toString());
            reporter.logThrowable(reporter.STATUSMSG, e,
                                  "newTransformer/getOutputProperties threw:");
        }

        try
        {
            Templates templates2 =
                factory.newTemplates(new StreamSource(outputFormatXSL));
            Properties outputFormat2 = templates2.getOutputProperties();

            reporter.check((outputFormat2 != null), true,
                           "getOutputProperties() is non-null for "
                           + outputFormatXSL);
            reporter.logHashtable(reporter.STATUSMSG, outputFormat2,
                                  "getOutputProperties for " + outputFormatXSL);

            String tmp = outputFormat2.getProperty(OUTPUT_FORMAT_NAME); // SPR SCUU4RXSG5 - has extra space
            if (OUTPUT_FORMAT_VALUE.equals(tmp))    // Use if so we can put out id with checkPass/checkFail lines
                reporter.checkPass("outputProperties " + OUTPUT_FORMAT_NAME + " has known value ?" + tmp + "?", "SCUU4RXSG5");
            else
                reporter.checkFail("outputProperties " + OUTPUT_FORMAT_NAME + " has known value ?" + tmp + "?", "SCUU4RXSG5");

            tmp = outputFormat2.getProperty("omit-xml-declaration");
            reporter.check(tmp, "yes", "outputProperties omit-xml-declaration has known value ?" + tmp + "?");
        }
        catch (Exception e)
        {
            reporter.checkErr("outputFormat() is html... threw: "
                              + e.toString());
            reporter.logThrowable(reporter.STATUSMSG, e,
                                  "outputFormat() is html... threw:");
        }
        reporter.logTraceMsg("Functionality of Transformers covered in TransformerAPITest, elsewhere");
        reporter.testCaseClose();

        return true;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String usage()
    {

        return ("Common [optional] options supported by TemplatesAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + "-processorClassname classname.of.processor  (to override setPlatformDefaultProcessor to Xalan 2.x)\n"
                + super.usage());
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {

        TemplatesAPITest app = new TemplatesAPITest();

        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
