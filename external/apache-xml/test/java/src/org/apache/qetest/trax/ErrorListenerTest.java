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
 * ErrorListenerTest.java
 *
 */
package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.LoggingSAXErrorHandler;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * Verify that ErrorListeners are called properly from Transformers.
 * Also verifies basic Transformer behavior after a stylesheet 
 * with errors has been built.
 * Note: parts of this test may rely on specific Xalan functionality, 
 * in that with the specific errors I've chosen, Xalan can actually 
 * continue to process the stylesheet, even though it had an error.
 * XSLTC mode may either throw slighly different kinds of errors, or 
 * may not be able to continue after the error (we should 
 * investigate changing this test to just verify common things, 
 * and then check the rest into the xalanj2 directory).
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ErrorListenerTest extends FileBasedTest
{

    /** Provide sequential output names automatically.   */
    protected OutputNameManager outNames;

    /** 
     * A simple stylesheet with errors for testing in various flavors.  
     * Must be coordinated with templatesExpectedType/Value,
     * transformExpectedType/Value.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** 
     * A simple stylesheet without errors in it.  
     */
    protected XSLTestfileInfo goodFileInfo = new XSLTestfileInfo();

    /** Expected type of error during stylesheet build.  */
    protected int templatesExpectedType = 0;

    /** Expected String of error during stylesheet build.  */
    protected String templatesExpectedValue = null;

    /** Expected type of error during transform.  */
    protected int transformExpectedType = 0;

    /** Expected String of error during transform.  */
    protected String transformExpectedValue = null;

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String ERR_SUBDIR = "err";

    /** Name of expected parent test\tests\api directory.  */
    public static final String API_PARENTDIR = "api";

    /** Just initialize test name, comment, numTestCases. */
    public ErrorListenerTest()
    {
        numTestCases = 4;  // REPLACE_num
        testName = "ErrorListenerTest";
        testComment = "Verify that ErrorListeners are called properly from Transformers.";
    }


    /**
     * Initialize this test  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + ERR_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + ERR_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + ERR_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + ERR_SUBDIR
                              + File.separator;

        goodFileInfo.inputName = inputDir + File.separator 
                              + "trax" + File.separator + "identity.xsl";
        goodFileInfo.xmlName  = inputDir + File.separator 
                              + "trax" + File.separator + "identity.xml";
        goodFileInfo.goldName  = goldDir + File.separator 
                              + "trax" + File.separator + "identity.out";

        testFileInfo.inputName = testBasePath + "ErrorListenerTest.xsl";
        testFileInfo.xmlName = testBasePath + "ErrorListenerTest.xml";
        testFileInfo.goldName = goldBasePath + "ErrorListenerTest.out";
        templatesExpectedType = LoggingErrorListener.TYPE_FATALERROR;
        templatesExpectedValue = "decimal-format names must be unique. Name \"myminus\" has been duplicated";
        transformExpectedType = LoggingErrorListener.TYPE_WARNING;
        transformExpectedValue = "ExpectedMessage from:list1";

        return true;
    }


    /**
     * Build a stylesheet/do a transform with a known-bad stylesheet.
     * Verify that the ErrorListener is called properly.
     * Primarily using StreamSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Build a stylesheet/do a transform with a known-bad stylesheet");
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Set the errorListener and validate it
            factory.setErrorListener(loggingErrorListener);
            reporter.check((factory.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on factory");

            // Attempt to build templates from known-bad stylesheet
            // Validate known errors in stylesheet building 
            loggingErrorListener.setExpected(templatesExpectedType, 
                                             templatesExpectedValue);
            reporter.logInfoMsg("About to factory.newTemplates(" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            reporter.logTraceMsg("loggingErrorListener after newTemplates:" + loggingErrorListener.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            reporter.checkPass("set ErrorListener prevented any exceptions in newTemplates()");
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Build a bad stylesheet/do a transform with SAX.
     * Verify that the ErrorListener is called properly.
     * Primarily using SAXSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Build a bad stylesheet/do a transform with SAX");
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        XMLReader reader = null;
        Templates templates = null;
        Transformer transformer = null;
        TransformerHandler handler = null;
        try
        {
            factory = TransformerFactory.newInstance();
            saxFactory = (SAXTransformerFactory)factory; // assumes SAXSource.feature!

            // Set the errorListener and validate it
            saxFactory.setErrorListener(loggingErrorListener);
            reporter.check((saxFactory.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on saxFactory");

            // Use the JAXP way to get an XMLReader
            reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            InputSource is = new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName));

            // Attempt to build templates from known-bad stylesheet
            // Validate known errors in stylesheet building 
            loggingErrorListener.setExpected(templatesExpectedType, 
                                             templatesExpectedValue);
            reporter.logTraceMsg("About to factory.newTransformerHandler(SAX:" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            handler = saxFactory.newTransformerHandler(new SAXSource(is));
            reporter.logTraceMsg("loggingErrorListener after newTransformerHandler:" + loggingErrorListener.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            reporter.checkPass("set ErrorListener prevented any exceptions in newTransformerHandler()");
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener-SAX unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener-SAX unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Build a bad stylesheet/do a transform with DOMs.
     * Verify that the ErrorListener is called properly.
     * Primarily using DOMSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Build a bad stylesheet/do a transform with DOMs");
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        DocumentBuilderFactory dfactory = null;
        DocumentBuilder docBuilder = null;
        Node xmlNode = null;
        Node xslNode = null;
        try
        {
            // Startup a DOM factory, create some nodes/DOMs
            dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            docBuilder = dfactory.newDocumentBuilder();
            reporter.logInfoMsg("parsing xml, xsl files to DOMs");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            xmlNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.xmlName)));

            // Create a transformer factory with an error listener
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(loggingErrorListener);

            // Attempt to build templates from known-bad stylesheet
            // Validate known errors in stylesheet building 
            loggingErrorListener.setExpected(templatesExpectedType, 
                                             templatesExpectedValue);
            reporter.logTraceMsg("About to factory.newTemplates(DOM:" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            templates = factory.newTemplates(new DOMSource(xslNode));
            reporter.logTraceMsg("loggingErrorListener after newTemplates:" + loggingErrorListener.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            reporter.checkPass("set ErrorListener prevented any exceptions in newTemplates()");

            // This stylesheet will still work, even though errors 
            //  were detected during it's building.  Note that 
            //  future versions of Xalan or other processors may 
            //  not be able to continue here...
            reporter.logErrorMsg("DOM templates/validation Moved to SmoketestOuttakes.java.testCase3 Oct-01 -sc Bugzilla#1062");
/* **** Moved to SmoketestOuttakes.java.testCase4 Oct-01 -sc 
            
            transformer = templates.newTransformer();

            reporter.logTraceMsg("default transformer's getErrorListener is: " + transformer.getErrorListener());
            // Set the errorListener and validate it
            transformer.setErrorListener(loggingErrorListener);
            reporter.check((transformer.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on transformer");

            // Validate the first xsl:message call in the stylesheet
            loggingErrorListener.setExpected(transformExpectedType, 
                                             transformExpectedValue);
            reporter.logInfoMsg("about to transform(DOM, StreamResult)");
            transformer.transform(new DOMSource(xmlNode), 
                                  new StreamResult(outNames.nextName()));
            reporter.logTraceMsg("after transform(...)");
            // Clear out any setExpected or counters
            loggingErrorListener.reset();

            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            fileChecker.check(reporter, 
                    new File(outNames.currentName()), 
                    new File(testFileInfo.goldName), 
                    "DOM transform of error xsl into: " + outNames.currentName());
**** Moved to SmoketestOuttakes.java.testCase4 Oct-01 -sc **** */
            
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener-DOM unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener-DOM unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Miscellaneous other ErrorListener tests.
     * Includes Bugzilla1266.
     * Primarily using StreamSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Miscellaneous other ErrorListener tests");
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            reporter.logInfoMsg("About to factory.newTemplates(" + QetestUtils.filenameToURL(goodFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(goodFileInfo.inputName)));
            transformer = templates.newTransformer();

            // Set the errorListener and validate it
            transformer.setErrorListener(loggingErrorListener);
            reporter.check((transformer.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on transformer");

            reporter.logStatusMsg("Reproduce Bugzilla1266 - warning due to bad output props not propagated");
            reporter.logStatusMsg("transformer.setOutputProperty(encoding, illegal-encoding-value)");
            transformer.setOutputProperty("encoding", "illegal-encoding-value");

            reporter.logTraceMsg("about to transform(...)");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(goodFileInfo.xmlName)), 
                                  new StreamResult(outNames.nextName()));
            reporter.logTraceMsg("after transform(...)");
            reporter.logStatusMsg("loggingErrorListener after transform:" + loggingErrorListener.getQuickCounters());

            // Validate that one warning (about illegal-encoding-value) should have been reported
            int[] errCtr = loggingErrorListener.getCounters();
            reporter.logErrorMsg("Validation of warning throw Moved to Bugzilla1266.java Oct-01 -sc");
/* **** Moved to Bugzilla1266.java Oct-01 -sc
            reporter.check((errCtr[LoggingErrorListener.TYPE_WARNING] > 0), true, "At least one Warning listned to for illegal-encoding-value");
**** Moved to Bugzilla1266.java Oct-01 -sc **** */
            
            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            fileChecker.check(reporter, 
                    new File(outNames.currentName()), 
                    new File(goodFileInfo.goldName), 
                    "transform of good xsl w/bad output props into: " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener4 unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener4 unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by ErrorListenerTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        ErrorListenerTest app = new ErrorListenerTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
