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
 * SAXTransformerFactoryAPITest.java
 *
 */
package org.apache.qetest.trax.sax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xml.utils.DefaultErrorHandler;
import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.trax.LoggingErrorListener;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * API Coverage test for SAXTransformerFactory.
 * @author Krishna.Meduri@eng.sun.com
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SAXTransformerFactoryAPITest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Basic test file: cities.xml/xsl/out.
     */
    protected XSLTestfileInfo citiesFileInfo = new XSLTestfileInfo();

    /** 
     * Alternate test file: citiesinclude.xsl.
     */
    protected String citiesIncludeFileName = null;

    /** 
     * Alternate gold file: citiesSerialized.out.
     */
    protected String citiesSerializedFileName = null;

    /** Gold file used for tests we haven't validated the correct results of yet.  */
    protected String NOT_DEFINED;

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SAX_SUBDIR = "trax" + File.separator + "sax";

    /** Just initialize test name, comment, numTestCases. */
    public SAXTransformerFactoryAPITest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "SAXTransformerFactoryAPITest";
        testComment = "API Coverage test for SAXTransformerFactory";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
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

        citiesFileInfo.inputName = testBasePath + "cities.xsl";
        citiesFileInfo.xmlName = testBasePath + "cities.xml";
        citiesFileInfo.goldName = goldBasePath + "cities.out";    // Tests 001 - 009

        citiesIncludeFileName = testBasePath + File.separator + "impincl" 
                                + File.separator + "citiesinclude.xsl"; // Test 004, etc.

        citiesSerializedFileName = goldBasePath + "citiesSerialized.out";    // Tests 010 - 013
        NOT_DEFINED = goldBasePath + "need-validated-output-file-here.out";

        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(SAXSource.FEATURE)
                  && tf.getFeature(SAXResult.FEATURE)))
            {   // The rest of this test relies on SAX
                reporter.logErrorMsg("SAX*.FEATURE not supported! Some tests may be invalid!");
            }

            if (!(tf.getFeature(SAXTransformerFactory.FEATURE)
                  && tf.getFeature(SAXTransformerFactory.FEATURE_XMLFILTER))) {
                // The rest of this test relies on SAXTransformerFactory
                reporter.logErrorMsg("SAXTransformerFactory.FEATURE* not "
                                     +"supported!  Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkErr("Problem creating factory; Some tests may be invalid!");
        }
        return true;
    }


    /**
     * Call various Sun tests of SAX-related classes.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Call various Sun tests of SAX-related classes");

        // Just call each method in order, with appropriate input
        //  and output filenames
        // Each method will call check() itself, and log any problems
        SAXTFactoryTest001(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest002(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest003(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest004(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest005(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest006(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        // Validate the output by comparing against gold
        // 18-Dec-00 Note: need to check what we should be 
        //  validating first - this test case seems incomplete
        // SAXTFactoryTest007(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest008(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest009(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest010(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest011(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest012(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);
        SAXTFactoryTest013(citiesFileInfo.xmlName, citiesFileInfo.inputName, citiesFileInfo.goldName);

        reporter.testCaseClose();
        return true;
    }


    /**
     * SAXTFactoryTest001 test.  
     * This tests newTransformerhandler() method which takes StreamSource as argument. This is a positive test
     */
    public void SAXTFactoryTest001(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest001: newTransformerhandler() method which takes StreamSource as argument. ");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            XMLReader reader = getJAXPXMLReader();

            SAXTransformerFactory saxTFactory = (SAXTransformerFactory)tfactory;
            TransformerHandler handler = saxTFactory.newTransformerHandler(
                        new StreamSource(QetestUtils.filenameToURL(xslName)));
            //Result result = new StreamResult(System.out);
            // Send results out to the next output name
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);

            handler.setResult(result);
            reader.setContentHandler(handler);
            // Log what output is about to be created
            reporter.logInfoMsg("reader.parse() into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));
            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(goldName), 
                        "SAXTFactoryTest001: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest001 threw");
            reporter.checkFail("SAXTFactoryTest001 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest001()


    /**
     * SAXTFactoryTest002 test.  
     * This tests newTransformerhandler() method which takes SAXSource as argument. This is a positive test
     */
    public void SAXTFactoryTest002(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest002: This tests newTransformerhandler() method which takes SAXSource as argument. ");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            XMLReader reader = getJAXPXMLReader();
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;

            InputSource is = new InputSource(new FileInputStream(xslName));
            SAXSource ss = new SAXSource();
            ss.setInputSource(is);

            TransformerHandler handler =  saxTFactory.newTransformerHandler(ss);
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);

            handler.setResult(result);
            reader.setContentHandler(handler);

            // Log what output is about to be created
            reporter.logInfoMsg("SAXTFactoryTest002 into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest002: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest002 threw");
            reporter.checkFail("SAXTFactoryTest002 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest002()

    /**
     * SAXTFactoryTest003 test.  
     * This tests newTransformerhandler() method which takes DOMSource as argument. No relative URIs used. This is a positive test
     */
    public void SAXTFactoryTest003(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest003: This tests newTransformerhandler() method which takes DOMSource as argument. No relative URIs used. ");
        reporter.logStatusMsg("Note: Need to verify that URI's are still correct from porting -sc");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        LoggingErrorListener loggingErrListener = new LoggingErrorListener(reporter);
        tfactory.setErrorListener(loggingErrListener);
        try 
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(new File(" + xslName + "))");
            Document document = docBuilder.parse(new File(xslName));
            Node node = (Node)document;
            DOMSource domSource = new DOMSource(node);

            SAXTransformerFactory saxTFactory = (SAXTransformerFactory)tfactory;
            TransformerHandler handler = saxTFactory.newTransformerHandler(domSource);

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            handler.setResult(result);

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(handler);

            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest003: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest003 threw");
            reporter.checkFail("SAXTFactoryTest003 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest003()


    /**
     * SAXTFactoryTest004 test.  
     * This tests newTransformerhandler() method which takes DOMSource as argument. Here a relative URI is used in citiesinclude.xsl file. setSystemId is not used for DOMSource. It should throw an exception. This is a negative test
     */
    public void SAXTFactoryTest004(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest004: This tests newTransformerhandler() method which takes DOMSource as argument. Here a relative URI is used in citiesinclude.xsl file. setSystemId is not used for DOMSource. It should throw an exception. This is a negative test");
        // Grab an extra outName, so the numbers line up - purely cosmetic, not really needed
        String tmpOutName = outNames.nextName();

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(new File(" + citiesIncludeFileName + "))");
            Document document = docBuilder.parse(new File(citiesIncludeFileName));  // note specific file name used
            Node node = (Node) document;
            DOMSource domSource = new DOMSource(node);
            // setSystemId is not used for DOMSource

            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            TransformerHandler handler = saxTFactory.newTransformerHandler(domSource);

            FileOutputStream fos = new FileOutputStream(tmpOutName);
            Result result = new StreamResult(fos);
            handler.setResult(result);

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(handler);

            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));
            fos.close(); // just to be complete

            reporter.checkFail("Should have thrown exception because systemId not set!");
        } 
        catch (Throwable t) 
        {
            reporter.logStatusMsg("@todo validate specific exception type");
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest004 threw");
            reporter.checkPass("SAXTFactoryTest004 properly threw: " + t.toString());
        }
    }// end of SAXTFactoryTest004()


    /**
     * SAXTFactoryTest005 test.  
     * This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. It should run well. This is a positive test
     */
    public void SAXTFactoryTest005(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest005: This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. It should run well. ");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        LoggingErrorListener loggingErrListener = new LoggingErrorListener(reporter);
        tfactory.setErrorListener(loggingErrListener);
        try 
        {
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(new File(" + citiesIncludeFileName + "))");
            Document document = docBuilder.parse(new File(citiesIncludeFileName));
            Node node = (Node) document;
            DOMSource domSource = new DOMSource(node);
            // String testDirPath = System.getProperty("Tests_Dir"); // @todo: update to new names
            // domSource.setSystemId("file:///" + testDirPath); // @todo: update to new names
            domSource.setSystemId(QetestUtils.filenameToURL(citiesIncludeFileName));

            TransformerHandler handler = saxTFactory.newTransformerHandler(domSource);

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            handler.setResult(result);

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(handler);

            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest005: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest005 threw");
            reporter.checkFail("SAXTFactoryTest005 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest005()
    

    /**
     * SAXTFactoryTest006 test.  
     * This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. Here Constructor that takes systemId as argument is used for creating DOMSource. It should run well. This is a positive test
     */
    public void SAXTFactoryTest006(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest006: This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. Here Constructor that takes systemId as argument is used for creating DOMSource. It should run well. ");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(new File(" + citiesIncludeFileName + "))");
            Document document = docBuilder.parse(new File(citiesIncludeFileName));
            Node node = (Node) document;
            // String testDirPath = System.getProperty("Tests_Dir"); // @todo update systemId
            // DOMSource domSource = new DOMSource(node, "file:///" + testDirPath); // @todo update systemId
            DOMSource domSource = new DOMSource(node, QetestUtils.filenameToURL(citiesIncludeFileName));

            TransformerHandler handler = saxTFactory.newTransformerHandler(domSource);

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            handler.setResult(result);

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(handler);

            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest006: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest006 threw");
            reporter.checkFail("SAXTFactoryTest006 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest006()

    
    /**
     * SAXTFactoryTest007 test.  
     * This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. Here Constructor that takes systemId as argument is used for creating DOMSource. It should run well. This is a positive test
     */
    public void SAXTFactoryTest007(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest007: This tests newTransformerhandler() method which takes DOMSource as argument.  Here a relative URI is used in citiesinclude.xsl file. setSystemId is used for DOMSource. Here Constructor that takes systemId as argument is used for creating DOMSource. It should run well. ");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(new File(" + citiesIncludeFileName + "))");
            Document document = docBuilder.parse(new File(citiesIncludeFileName));
            Node node = (Node) document;
            DOMSource domSource = new DOMSource(node);

            XMLReader reader = getJAXPXMLReader();
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;

            //For xml file.
            reporter.logTraceMsg("docBuilder.parse(" + xmlName + ")");
            Document xmlDocument = docBuilder.parse(new File(xmlName));
            Node xmlNode = (Node) xmlDocument;
            DOMSource xmlDomSource = new DOMSource(xmlNode);
            //Please look into this later...You want to use newTransformerhandler()
            TransformerHandler handler = saxTFactory.newTransformerHandler();
            Transformer transformer = handler.getTransformer();

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);

            // Log what output is about to be created
            reporter.logTraceMsg("transformer.transform(xmlDomSource, StreamResult) into: " + outNames.currentName());
            transformer.transform(xmlDomSource, result);

            // Validate the output by comparing against gold
            // 18-Dec-00 Note: need to check what we should be 
            //  validating first - this test case seems incomplete
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(NOT_DEFINED), 
                              "SAXTFactoryTest007: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest007 threw");
            reporter.checkFail("SAXTFactoryTest007 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest007()


    /**
     * SAXTFactoryTest008 test.  
     * XDESCRIPTION
     */
    public void SAXTFactoryTest008(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest008: Simple SAX: TemplatesHandler to FileOutputStream");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            TemplatesHandler thandler = saxTFactory.newTemplatesHandler();

            XMLReader reader = getJAXPXMLReader();
            reader.setContentHandler(thandler);
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xslName) + ")");
            reader.parse(QetestUtils.filenameToURL(xslName));

            TransformerHandler tfhandler = saxTFactory.newTransformerHandler(thandler.getTemplates());

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);

            tfhandler.setResult(result);
            reader.setContentHandler(tfhandler);
            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest008: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest008 threw");
            reporter.checkFail("SAXTFactoryTest008 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest008()
    

    /**
     * SAXTFactoryTest009 test.  
     * XDESCRIPTION
     */
    public void SAXTFactoryTest009(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest009: Simple SAX with included stylesheet");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            XMLReader reader = getJAXPXMLReader();
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            TemplatesHandler thandler = saxTFactory.newTemplatesHandler();
            // String testDirPath = System.getProperty("Tests_Dir"); // @todo update systemId
            // thandler.setSystemId("file:///" + testDirPath); // @todo update systemId
            thandler.setSystemId(QetestUtils.filenameToURL(citiesIncludeFileName)); // @todo update systemId

            reader.setContentHandler(thandler);
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(citiesIncludeFileName) + ")");
            reader.parse(QetestUtils.filenameToURL(citiesIncludeFileName));

            TransformerHandler tfhandler =
                saxTFactory.newTransformerHandler(thandler.getTemplates());

            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result result = new StreamResult(fos);
            tfhandler.setResult(result);
            reader.setContentHandler(tfhandler);

            // Log what output is about to be created
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            reader.parse(QetestUtils.filenameToURL(xmlName));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldName), 
                              "SAXTFactoryTest009: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest009 threw");
            reporter.checkFail("SAXTFactoryTest009 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest009()


    /**
     * SAXTFactoryTest010 test.  
     * The transformer will use a SAX parser as it's reader
     */
    public void SAXTFactoryTest010(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest010: The transformer will use a SAX parser as it's reader");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            // The transformer will use a SAX parser as it's reader.
            XMLReader reader = getJAXPXMLReader();
            // Set the result handling to be a serialization to the file output stream.
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            TransformerHandler tHandler = saxTFactory.newTransformerHandler();
            tHandler.setResult(realResult);
            reader.setContentHandler(tHandler);

            reporter.logTraceMsg("saxTFactory.newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslName) + "))");
            XMLFilter filter = saxTFactory.newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslName)));

            filter.setParent(reader);

            // Now, when you call transformer.parse, it will set itself as
            // the content handler for the parser object (it's "parent"), and
            // will then call the parse method on the parser.
            // Log what output is about to be created
            reporter.logTraceMsg("filter.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            filter.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(citiesSerializedFileName), 
                              "SAXTFactoryTest010: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest010 threw");
            reporter.checkFail("SAXTFactoryTest010 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest010()


    /**
     * SAXTFactoryTest011 test.  
     * The transformer will use a SAX parser as it's reader
     */
    public void SAXTFactoryTest011(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest011: The transformer will use a SAX parser as it's reader");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            // The transformer will use a SAX parser as it's reader.
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // Ensure we get namespaces! May be required for some Xerces versions
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();

            reporter.logTraceMsg("docBuilder.parse(new File(" + xslName + "))");
            Document document = docBuilder.parse(new File(xslName));
            Node node = (Node) document;
            DOMSource domSource = new DOMSource(node);
            SAXTransformerFactory saxTFactory =  (SAXTransformerFactory) tfactory;
            XMLFilter filter = saxTFactory.newXMLFilter(domSource);

            XMLReader reader = getJAXPXMLReader();
            // Set the result handling to be a serialization to the file output stream.
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            TransformerHandler tHandler = saxTFactory.newTransformerHandler();
            tHandler.setResult(realResult);
            reader.setContentHandler(tHandler);

            filter.setParent(reader);

            // Now, when you call transformer.parse, it will set itself as
            // the content handler for the parser object (it's "parent"), and
            // will then call the parse method on the parser.

            // Log what output is about to be created
            reporter.logTraceMsg("filter.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            filter.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(citiesSerializedFileName), 
                              "SAXTFactoryTest011: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest011 threw");
            reporter.checkFail("SAXTFactoryTest011 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest011()
    

    /**
     * SAXTFactoryTest012 test.  
     * The transformer will use a SAX parser as it's reader
     */
    public void SAXTFactoryTest012(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest012: The transformer will use a SAX parser as it's reader");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            // The transformer will use a SAX parser as it's reader.

            InputSource is = new InputSource(new FileInputStream(xslName));
            SAXSource saxSource = new SAXSource();
            saxSource.setInputSource(is);

            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            reporter.logTraceMsg("newXMLFilter(..." + xslName + ")");
            XMLFilter filter = saxTFactory.newXMLFilter(saxSource);

            XMLReader reader = getJAXPXMLReader();
            // Set the result handling to be a serialization to the file output stream.
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            TransformerHandler tHandler = saxTFactory.newTransformerHandler();
            tHandler.setResult(realResult);
            reader.setContentHandler(tHandler);
            filter.setParent(reader);

            // Now, when you call transformer.parse, it will set itself as
            // the content handler for the parser object (it's "parent"), and
            // will then call the parse method on the parser.
            // Log what output is about to be created
            reporter.logTraceMsg("filter.parse(" + QetestUtils.filenameToURL(xmlName) + ") into: " + outNames.currentName());
            filter.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(citiesSerializedFileName), 
                              "SAXTFactoryTest012: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest012 threw");
            reporter.checkFail("SAXTFactoryTest012 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest012()


    /**
     * SAXTFactoryTest013 test.  
     * The transformer will use a SAX parser as it's reader
     */
    public void SAXTFactoryTest013(String xmlName, String xslName, String goldName) 
    {
        // Log the test we're about to do
        reporter.logStatusMsg("SAXTFactoryTest013: The transformer will use a SAX parser as it's reader");

        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
        try 
        {
            // The transformer will use a SAX parser as it's reader.
            XMLReader reader = getJAXPXMLReader();
            SAXTransformerFactory saxTFactory = (SAXTransformerFactory) tfactory;
            TemplatesHandler thandler = saxTFactory.newTemplatesHandler();
            // String testDirPath = System.getProperty("Tests_Dir"); // @todo update systemId

            // I have put this as it was complaining about systemid
            // thandler.setSystemId("file:///" + testDirPath); // @todo update systemId
            thandler.setSystemId(QetestUtils.filenameToURL(xslName)); // @todo update systemId

            reader.setContentHandler(thandler);
            reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(xslName) + ")");
            reader.parse(QetestUtils.filenameToURL(xslName));

            XMLFilter filter = saxTFactory.newXMLFilter(thandler.getTemplates());

            filter.setParent(reader);
            // Set the result handling to be a serialization to the file output stream.
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            TransformerHandler tHandler = saxTFactory.newTransformerHandler();
            tHandler.setResult(realResult);
            filter.setContentHandler(tHandler);

            // Log what output is about to be created
            reporter.logTraceMsg("filter.parse(" + xmlName + ") into: " + outNames.currentName());
            filter.parse(new InputSource(new FileInputStream(xmlName)));

            // Validate the output by comparing against gold
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(citiesSerializedFileName), 
                              "SAXTFactoryTest013: into " + outNames.currentName());
        } 
        catch (Throwable t) 
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "SAXTFactoryTest013 threw");
            reporter.checkFail("SAXTFactoryTest013 threw: " + t.toString());
        }
    }// end of SAXTFactoryTest013()


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
        return ("Common [optional] options supported by SAXTransformerFactoryAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SAXTransformerFactoryAPITest app = new SAXTransformerFactoryAPITest();
        app.doMain(args);
    }
}
