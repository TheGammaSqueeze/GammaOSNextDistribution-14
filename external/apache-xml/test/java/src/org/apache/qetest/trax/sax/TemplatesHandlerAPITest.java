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
 * TemplatesHandlerAPITest.java
 *
 */
package org.apache.qetest.trax.sax;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * API Coverage test for the TemplatesHandler class of TRAX.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class TemplatesHandlerAPITest extends FileBasedTest
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
    public TemplatesHandlerAPITest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "TemplatesHandlerAPITest";
        testComment = "API Coverage test for the TemplatesHandler class of TRAX";
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
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Basic construction
            TemplatesHandler tHandler = saxFactory.newTemplatesHandler();
            reporter.check((tHandler != null), true, "newTemplatesHandler() returns non-null");

            // getTemplates API coverage - simple
            Templates templates = tHandler.getTemplates();
            reporter.checkObject(templates, null, "getTemplates() is null on new TemplatesHandler");

            // set/getSystemId API coverage
            tHandler.setSystemId(NONSENSE_SYSTEMID);
            reporter.checkObject(tHandler.getSystemId(), NONSENSE_SYSTEMID, "set/getSystemId API coverage");
            tHandler.setSystemId(null);
            reporter.checkObject(tHandler.getSystemId(), null, "set/getSystemId API coverage to null");

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with TemplatesHandler set/get API");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with TemplatesHandler set/get API");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Basic functionality of TemplatesHandler.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Basic functionality of TemplatesHandler");
        // Provide local copies of URLized filenames, so that we can
        //  later run tests with either Strings or URLs
        String xslURI = QetestUtils.filenameToURL(testFileInfo.inputName);
        String xmlURI = QetestUtils.filenameToURL(testFileInfo.xmlName);
        String xslImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.inputName);
        String xmlImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.xmlName);

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        TemplatesHandler templatesHandler = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate a templatesHandler can create a valid stylesheet
            templatesHandler = saxFactory.newTemplatesHandler();

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(templatesHandler);

            // Parse the stylesheet, which means we should be able to getTemplates()
            reader.parse(xslURI);

            //Get the Templates object from the ContentHandler
            templates = templatesHandler.getTemplates();
            reporter.check((templates != null), true, "getTemplates() returns non-null with valid stylesheet");
            Properties xslOutProps = templates.getOutputProperties();
            reporter.check((xslOutProps != null), true, "getTemplates().getOutputProperties() returns non-null with valid stylesheet");
            //@todo validate a specific output property
            transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "getTemplates().newTransformer() returns non-null with valid stylesheet");

            // Validate that this transformer actually works
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            Source xmlSource = new StreamSource(xmlURI);
            transformer.transform(xmlSource, result);
            fos.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(testFileInfo.goldName), 
                        "SAX-built simple transform into: " + outNames.currentName())
                )
                 reporter.logInfoMsg("SAX-built simple transform failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem TemplatesHandler(1)");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem TemplatesHandler(1)");
        }
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate a templatesHandler can create a stylesheet 
            //  with imports/includes, with the default systemId
            templatesHandler = saxFactory.newTemplatesHandler();

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(templatesHandler);

            // Parse the stylesheet, which means we should be able to getTemplates()
            reader.parse(xslImpInclURI);

            //Get the Templates object from the ContentHandler
            templates = templatesHandler.getTemplates();
            reporter.check((templates != null), true, "getTemplates() returns non-null with impincl stylesheet");
            Properties xslOutProps = templates.getOutputProperties();
            reporter.check((xslOutProps != null), true, "getTemplates().getOutputProperties() returns non-null with impincl stylesheet");
            //@todo validate a specific output property
            transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformer != null), true, "getTemplates().newTransformer() returns non-null with impincl stylesheet");

            // Validate that this transformer actually works
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            Source xmlSource = new StreamSource(xmlImpInclURI);
            transformer.transform(xmlSource, result);
            fos.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(impInclFileInfo.goldName), 
                        "SAX-built impincl transform into: " + outNames.currentName())
                )
                 reporter.logInfoMsg("SAX-built impincl transform failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem TemplatesHandler(2)");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem TemplatesHandler(2)");
        }
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // Validate a templatesHandler with an incorrect 
            //  systemId reports an error nicely
            templatesHandler = saxFactory.newTemplatesHandler();

            // Set the base systemId for the handler to a bogus one
            templatesHandler.setSystemId(NONSENSE_SYSTEMID);

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(templatesHandler);

            // Parse the stylesheet, which should throw some 
            //  exception, since the imports/includes won't be 
            //  found at the bogus systemId
            reader.parse(xslImpInclURI);

            // This line will only get run if above didn't throw an exception
            reporter.checkFail("No exception when expected: parsing stylesheet with bad systemId");

        }
        catch (Throwable t)
        {
            String msg = t.toString();
            if (msg != null)
            {
                // Note: 'impincl/SimpleImport.xsl' comes from the stylesheet itself,
                //  so to reduce dependencies, only validate that portion of the msg
                reporter.check((msg.indexOf("impincl/SimpleImport.xsl") > 0), true, 
                               "Expected Exception has proper message for bad systemId");
            }
            else
            {
                reporter.checkFail("Expected Exception has proper message for bad systemId");
            }
            reporter.logThrowable(reporter.STATUSMSG, t,"(potentially) Expected Exception");
        }
        finally
        {
            // Validate the templatesHandler still has systemId set
            reporter.check(templatesHandler.getSystemId(), NONSENSE_SYSTEMID, 
                           "templatesHandler still has systemId set");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method to get an XMLReader.
     *
     * Not the most efficient of methods, but makes the code simpler.
     *
     * @return a new XMLReader for use, with setNamespaceAware(true)
     */
    protected XMLReader getJAXPXMLReader()
            throws Exception
    {
        // Be sure to use the JAXP methods only!
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        return saxParser.getXMLReader();
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by TemplatesHandlerAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        TemplatesHandlerAPITest app = new TemplatesHandlerAPITest();
        app.doMain(args);
    }
}
