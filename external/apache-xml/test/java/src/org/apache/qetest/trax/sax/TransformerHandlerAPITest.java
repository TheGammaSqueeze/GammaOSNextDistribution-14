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
 * TransformerHandlerAPITest.java
 *
 */
package org.apache.qetest.trax.sax;

import java.io.File;
import java.util.Properties;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;

//-------------------------------------------------------------------------

/**
 * API Coverage test for the TransformerHandler class of TRAX.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class TransformerHandlerAPITest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Information about an xsl/xml file pair for transforming.  
     * Public members include inputName (for xsl); xmlName; goldName; etc.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** 
     * Information about an xsl/xml file pair for transforming with import/include.  
     */
    protected XSLTestfileInfo impInclFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SAX_SUBDIR = "trax" + File.separator + "sax";

    /** Nonsense systemId for various tests.  */
    public static final String NONSENSE_SYSTEMID = "file:///nonsense/system/id/";

    /** Just initialize test name, comment, numTestCases. */
    public TransformerHandlerAPITest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "TransformerHandlerAPITest";
        testComment = "API Coverage test for the TransformerHandler class of TRAX";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SAX_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_SAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_SAX_SUBDIR
                              + File.separator;

        // Note these are initialized as strings denoting filenames,
        //  and *not* as URL/URI's
        testFileInfo.inputName = testBasePath + "SAXTest.xsl";
        testFileInfo.xmlName = testBasePath + "SAXTest.xml";
        testFileInfo.goldName = goldBasePath + "SAXTest.out";

        impInclFileInfo.inputName = testBasePath + "SAXImpIncl.xsl";
        impInclFileInfo.xmlName = testBasePath + "SAXImpIncl.xml";
        impInclFileInfo.goldName = goldBasePath + "SAXImpIncl.out";
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(SAXSource.FEATURE)
                  && tf.getFeature(SAXResult.FEATURE)))
            {   // The rest of this test relies on SAX
                reporter.logErrorMsg("SAX*.FEATURE not supported! Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem creating factory; Some tests may be invalid!");
        }

        return true;
    }


    /**
     * Basic API coverage of set/get methods.
     * Note that most of the functionality of this class goes 
     * far beyond what we test in this testCase.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Basic API coverage of set/get methods");

        // No public constructor available: you must always ask 
        //  a SAXTransformerFactory to give you one
        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;

        try
        {
            // Validate API's for an identity transformer
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Basic construction of identity transformer
            TransformerHandler tHandler = saxFactory.newTransformerHandler();
            reporter.check((tHandler != null), true, "newTransformerHandler() returns non-null");

            // getTemplates API coverage - simple
            Transformer transformer = tHandler.getTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "getTransformer() is non-null on new identity TransformerHandler");

            // set/getSystemId API coverage
            tHandler.setSystemId(NONSENSE_SYSTEMID);
            reporter.checkObject(tHandler.getSystemId(), NONSENSE_SYSTEMID, "identityTransformer.set/getSystemId API coverage");
            tHandler.setSystemId(null);
            reporter.checkObject(tHandler.getSystemId(), null, "identityTransformer.set/getSystemId API coverage to null");

            // setResult API coverage
            Result unusedResult = new StreamResult(outNames.currentName()); // currentName is probably _0
            tHandler.setResult(unusedResult);
            reporter.checkPass("Crash test: identityTransformer.setResult appears to have worked");
            reporter.logStatusMsg("Note that we can't verify setResult since there's no getResult!");
            try
            {
                tHandler.setResult(null);
                reporter.checkFail("identityTransformer.setResult(null) did not throw an exception");            
            }
            catch (IllegalArgumentException iae)
            {
                reporter.checkPass("identityTransformer.setResult(null) properly threw: " + iae.toString());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with TransformerHandler set/get API");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with TransformerHandler set/get API");
        }

        try
        {
            // Validate API's for a 'real' transformer, which is different code
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Basic construction of identity transformer
            TransformerHandler tHandler = saxFactory.newTransformerHandler(new StreamSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            reporter.check((tHandler != null), true, "newTransformerHandler(.." + QetestUtils.filenameToURL(testFileInfo.inputName) + ")) returns non-null");

            // getTemplates API coverage - simple
            Transformer transformer = tHandler.getTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "realTransformer.getTransformer() is non-null");

            // set/getSystemId API coverage
            tHandler.setSystemId(NONSENSE_SYSTEMID);
            reporter.checkObject(tHandler.getSystemId(), NONSENSE_SYSTEMID, "realTransformer.set/getSystemId API coverage");
            tHandler.setSystemId(null);
            reporter.checkObject(tHandler.getSystemId(), null, "realTransformer.set/getSystemId API coverage to null");

            // setResult API coverage
            Result unusedResult = new StreamResult(outNames.nextName()); // use nextName() instead of currentName()
            reporter.logInfoMsg("new StreamResult(" + outNames.currentName() + ")");
            tHandler.setResult(unusedResult);
            reporter.checkPass("Crash test: realTransformer.setResult appears to have worked");
            reporter.logStatusMsg("Note that we can't verify setResult since there's no getResult!");
            try
            {
                tHandler.setResult(null);
                reporter.checkFail("realTransformer.setResult(null) did not throw an exception");            
            }
            catch (IllegalArgumentException iae)
            {
                reporter.checkPass("realTransformer.setResult(null) properly threw: " + iae.toString());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with TransformerHandler set/get API");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with TransformerHandler set/get API");
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Basic functionality of TransformerHandler.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Basic functionality of TransformerHandler");
        // Provide local copies of URLized filenames, so that we can
        //  later run tests with either Strings or URLs
        String xslURI = QetestUtils.filenameToURL(testFileInfo.inputName);
        String xmlURI = QetestUtils.filenameToURL(testFileInfo.xmlName);
        String xslImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.inputName);
        String xmlImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.xmlName);

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        TransformerHandler transformerHandler = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate an identity transformerHandler is valid 
            //  and performs as an identity stylesheet
            transformerHandler = saxFactory.newTransformerHandler();
            transformer = transformerHandler.getTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "identity newTransformerHandler is non-null");
            transformer.transform(new StreamSource(xmlURI), new StreamResult(outNames.nextName()));
            int res = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.xmlName), 
                              "identity newTransformerHandler transform into: " + outNames.currentName());
            if (res == reporter.FAIL_RESULT)
                reporter.logInfoMsg("identity newTransformerHandler transform failure reason:" + fileChecker.getExtendedInfo());

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem TransformerHandler(1)");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem TransformerHandler(1)");
        }

        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate newTransformerHandler(Source) works
            Source xslSource = new StreamSource(xslURI);
            transformerHandler = saxFactory.newTransformerHandler(xslSource);
            transformer = transformerHandler.getTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "newTransformerHandler(Source) is non-null");
            transformer.transform(new StreamSource(xmlURI), new StreamResult(outNames.nextName()));
            int res = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "newTransformerHandler(Source) transform into: " + outNames.currentName());
            if (res == reporter.FAIL_RESULT)
                reporter.logInfoMsg("newTransformerHandler(Source) transform failure reason:" + fileChecker.getExtendedInfo());

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem TransformerHandler(2)");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem TransformerHandler(2)");
        }

        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate newTransformerHandler(Templates) works
            Source xslSource = new StreamSource(xslURI);
            Templates otherTemplates = factory.newTemplates(xslSource);
            transformerHandler = saxFactory.newTransformerHandler(otherTemplates);
            transformer = transformerHandler.getTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "newTransformerHandler(Templates) is non-null");
            transformer.transform(new StreamSource(xmlURI), new StreamResult(outNames.nextName()));
            int res = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "newTransformerHandler(Templates) transform into: " + outNames.currentName());
            if (res == reporter.FAIL_RESULT)
                reporter.logInfoMsg("newTransformerHandler(Templates) transform failure reason:" + fileChecker.getExtendedInfo());

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem TransformerHandler(3)");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem TransformerHandler(3)");
        }
        reporter.logTraceMsg("//@todo validate newTransformerHandler.setResult functionality");
        reporter.logTraceMsg("//@todo validate newTransformerHandler.set/getSystemId functionality");

        reporter.testCaseClose();
        return true;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by TransformerHandlerAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        TransformerHandlerAPITest app = new TransformerHandlerAPITest();
        app.doMain(args);
    }
}
