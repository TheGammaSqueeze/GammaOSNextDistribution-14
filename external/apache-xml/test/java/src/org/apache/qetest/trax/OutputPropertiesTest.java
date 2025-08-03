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
 * OutputPropertiesTest.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.w3c.dom.Document;

//-------------------------------------------------------------------------

/**
 * Verify how output properties are handled from stylesheets and the API.
 * @author shane_curcuru@lotus.com
 * @author Krishna.Meduri@eng.sun.com
 * @version $Id$
 */
public class OutputPropertiesTest extends FileBasedTest
{

    /** Used for generating names of actual output files.   */
    protected OutputNameManager outNames;

    /** Default OutputPropertiesTest.xml/xsl file pair.   */
    protected XSLTestfileInfo htmlFileInfo = new XSLTestfileInfo();

    /** xml/xsl file pair used in testlets.   */
    protected XSLTestfileInfo testletFileInfo = new XSLTestfileInfo();

    /** Alternate gold file used in testlets.   */
    protected String citiesIndentNoGoldFile = null;

    /** Alternate gold file used in testlets.   */
    protected String citiesMethodTextGoldFile = null;

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Key for testlets to do special validation.  */
    public static final String CROSS_VALIDATE = "validate-non-gold";

    /** Just initialize test name, comment, numTestCases. */
    public OutputPropertiesTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "OutputPropertiesTest";
        testComment = "Verify how output properties are handled from stylesheets and the API";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files, etc.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Possible problem creating output dir: " + outSubDir);
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

        htmlFileInfo.inputName = testBasePath + "OutputPropertiesHTML.xsl";
        htmlFileInfo.xmlName = testBasePath + "OutputPropertiesHTML.xml";
        htmlFileInfo.goldName = goldBasePath + "OutputPropertiesHTML.out";

        testletFileInfo.inputName = testBasePath + File.separator + "sax" 
                                    + File.separator + "cities.xsl";
        testletFileInfo.xmlName = testBasePath + File.separator + "sax" 
                                    + File.separator + "cities.xml";
        testletFileInfo.goldName = goldBasePath + File.separator + "sax" 
                                    + File.separator + "cities.out";
        citiesIndentNoGoldFile = goldBasePath + File.separator + "sax" 
                                    + File.separator + "cities-indent-no.out";
        citiesMethodTextGoldFile = goldBasePath + File.separator + "sax" 
                                    + File.separator + "cities-method-text.out";
        return true;
    }


    /**
     * Verify setting output properties individually or whole blocks.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Verify setting output properties individually or whole blocks.");
        // Simply start with Krishna's individual tests
        testlet0(testletFileInfo.xmlName, testletFileInfo.inputName, testletFileInfo.goldName);
        testlet1(testletFileInfo.xmlName, testletFileInfo.inputName, citiesIndentNoGoldFile);
        testlet2(testletFileInfo.xmlName, testletFileInfo.inputName, citiesIndentNoGoldFile);
        testlet3(testletFileInfo.xmlName, testletFileInfo.inputName, citiesMethodTextGoldFile);

        reporter.testCaseClose();
        return true;
    }

    /**
     * Verify various output properties with HTML.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Verify various output properties with HTML.");
        TransformerFactory factory = null;
        Templates templates = null;
        try
        {
            // Preprocess the HTML stylesheet for later use
            factory = TransformerFactory.newInstance();
            reporter.logInfoMsg("creating shared newTemplates(" + QetestUtils.filenameToURL(htmlFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(htmlFileInfo.inputName)));
            reporter.logHashtable(Logger.STATUSMSG, 
                                  templates.getOutputProperties(), "shared templates output properties");

            // Process the file once with default properties
            Transformer transformer = templates.newTransformer();
            Result result = new StreamResult(outNames.nextName());
            reporter.logInfoMsg("(0)shared transform(" + QetestUtils.filenameToURL(htmlFileInfo.xmlName) 
                                + ", " + outNames.currentName() + ")");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(htmlFileInfo.xmlName)), result);
            // Validate the default transform
            if
                (Logger.PASS_RESULT 
                 != fileChecker.check(reporter, 
                                      new File(outNames.currentName()), 
                                      new File(htmlFileInfo.goldName), 
                                      "(0)shared transform into: " + outNames.currentName()
                                      + " gold: " + htmlFileInfo.goldName)
                )
            {
                reporter.logInfoMsg("(0)shared transform failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            reporter.checkErr("(0)Creating shared stylesheet threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "(0)Creating shared stylesheet threw ");
            return true;
        }

        // Create a number of testcases and use worker method to process
        Properties setProps = new Properties();
        // If we reset only one property each time, the results should match
        //  whether we set it thru properties or individually
        setProps.put("indent", "no");
        outputPropertyTestlet(templates, QetestUtils.filenameToURL(htmlFileInfo.xmlName), 
                              setProps, CROSS_VALIDATE, CROSS_VALIDATE,
                              "(1)Just reset indent=no");

        setProps = new Properties();
        setProps.put("method", "xml");
        outputPropertyTestlet(templates, QetestUtils.filenameToURL(htmlFileInfo.xmlName), 
                              setProps, CROSS_VALIDATE, CROSS_VALIDATE,
                              "(2)Just reset method=xml");

        // Just changing the standalone doesn't affect HTML output
        setProps = new Properties();
        setProps.put("standalone", "no");
        outputPropertyTestlet(templates, QetestUtils.filenameToURL(htmlFileInfo.xmlName), 
                              setProps, htmlFileInfo.goldName, htmlFileInfo.goldName,
                              "(3)Just reset standalone=no");
        reporter.testCaseClose();
        return true;
    }

    /**
     * outputPropertyTestlet: transform with specifically set output Properties.  
     * Sets properties as a block and also individually, and checks 
     * both outputs against the gold file.
     * @author shane_curcuru@lotus.com
     */
    public void outputPropertyTestlet(Templates templates, String xmlId, 
                                      Properties setProps, String goldName1, String goldName2,
                                      String desc)
    {
        try
        {
            reporter.logHashtable(Logger.STATUSMSG, setProps,
                                  "(-)" + desc + " begin with properties");
            // First, set the properties as a block and transform
            Transformer transformer1 = templates.newTransformer();
            transformer1.setOutputProperties(setProps);
            Result result1 = new StreamResult(outNames.nextName());
            reporter.logTraceMsg("(-a)transform(" + xmlId + ", " + outNames.currentName() + ")");
            transformer1.transform(new StreamSource(xmlId), result1);
            // Only do validation if asked to
            if (CROSS_VALIDATE == goldName1)
            {
                reporter.logWarningMsg("(-a-no-validation)" + desc + " into: " + outNames.currentName());
            }
            else if
                (Logger.PASS_RESULT 
                 != fileChecker.check(reporter, 
                                      new File(outNames.currentName()), 
                                      new File(goldName1), 
                                      "(-a)" + desc + " into: " + outNames.currentName()
                                      + " gold: " + goldName1)
                )
            {
                reporter.logInfoMsg("(-a)" + desc + " failure reason:" + fileChecker.getExtendedInfo());
            }

            // Second, set the properties individually and transform
            Transformer transformer2 = templates.newTransformer();
            for (Enumeration names = setProps.propertyNames();
                    names.hasMoreElements(); /* no increment portion */ )
            {
                String key = (String)names.nextElement();
                String value = setProps.getProperty(key);
                transformer2.setOutputProperty(key, value);
            }
            Result result2 = new StreamResult(outNames.nextName());
            reporter.logTraceMsg("(-b)transform(" + xmlId + ", " + outNames.currentName() + ")");
            transformer2.transform(new StreamSource(xmlId), result2);
            // Only do validation if asked to
            // If cross-validating, validate the first file against the second one
            if (CROSS_VALIDATE == goldName2)
            {
                if
                    (Logger.PASS_RESULT 
                     != fileChecker.check(reporter, 
                                          new File(outNames.previousName()), 
                                          new File(outNames.currentName()), 
                                          "(-b)" + desc + " into: " + outNames.currentName()
                                          + " cross: " + outNames.previousName())
                    )
                {
                    reporter.logInfoMsg("(-b)" + desc + " failure reason:" + fileChecker.getExtendedInfo());
                }
            }
            else if
                (Logger.PASS_RESULT 
                 != fileChecker.check(reporter, 
                                      new File(outNames.currentName()), 
                                      new File(goldName2), 
                                      "(-b)" + desc + " into: " + outNames.currentName()
                                      + " gold: " + goldName2)
                )
            {
                reporter.logInfoMsg("(-b)" + desc + " failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("(-)" + desc + " threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "(-)" + desc + " threw ");
        }
    }


    /**
     * testlet0: transform with just stylesheet properties.  
     * @author shane_curcuru@lotus.com
     */
    public void testlet0(String xmlName, String xslName, String goldName)
    {
        try 
        {
            reporter.logStatusMsg("testlet0: transform with just stylesheet properties");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // sc for Xerces
            DocumentBuilder db = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBld.parse(" + xslName + ")");
            Document document = db.parse(new File(xslName));
            DOMSource domSource = new DOMSource(document);
            domSource.setSystemId(QetestUtils.filenameToURL(xslName)); // sc

            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer(domSource);
            Properties xslProps = transformer.getOutputProperties();
            reporter.logHashtable(Logger.STATUSMSG, xslProps, 
                                  "Properties originally from the stylesheet");

            reporter.logTraceMsg("new StreamSource(new FileInputStream(" + xmlName + "))");
            StreamSource streamSource = new StreamSource(new FileInputStream(xmlName));
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            StreamResult streamResult = new StreamResult(fos);

            // Verify transform with existing properties
            reporter.logTraceMsg("transformer.transform(xml, " + outNames.currentName() + ")");
            transformer.transform(streamSource, streamResult);
            fos.close();

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(goldName), 
                                     "(t0)transform with stylesheet properties into: " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(t0)transform with stylesheet properties failure reason:" + fileChecker.getExtendedInfo());
            }
        } 
        catch (Throwable t)
        {
            reporter.checkFail("testlet threw: " + t.toString());
            reporter.logThrowable(Logger.WARNINGMSG, t, "testlet threw ");
        }
    }
    /**
     * testlet1: verify setOutputProperties(Properties).  
     * @author Krishna.Meduri@eng.sun.com
     */
    public void testlet1(String xmlName, String xslName, String goldName)
    {
        try 
        {
            reporter.logStatusMsg("testlet1: verify setOutputProperties(Properties)");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // sc for Xerces
            DocumentBuilder db = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBld.parse(" + xslName + ")");
            Document document = db.parse(new File(xslName));
            DOMSource domSource = new DOMSource(document);
            domSource.setSystemId(QetestUtils.filenameToURL(xslName)); // sc

            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer(domSource);
            Properties xslProps = transformer.getOutputProperties();
            reporter.logHashtable(Logger.STATUSMSG, xslProps, 
                                  "Properties originally from the stylesheet");

            reporter.logTraceMsg("new StreamSource(new FileInputStream(" + xmlName + "))");
            StreamSource streamSource = new StreamSource(new FileInputStream(xmlName));
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            StreamResult streamResult = new StreamResult(fos);

            // Verify setting a whole block of properties
            Properties properties = new Properties();
            properties.put("method", "xml");
            properties.put("encoding", "UTF-8");
            properties.put("omit-xml-declaration", "no");
            properties.put("{http://xml.apache.org/xslt}indent-amount", "0");
            properties.put("indent", "no"); // This should override the indent=yes in the stylesheet
            properties.put("standalone", "no");
            properties.put("version", "1.0");
            properties.put("media-type", "text/xml");
            reporter.logHashtable(Logger.STATUSMSG, properties, 
                                  "Properties block to be set via API");
            reporter.logTraceMsg("transformer.setOutputProperties(properties block)");
            transformer.setOutputProperties(properties);

            reporter.logTraceMsg("transformer.transform(xml, " + outNames.currentName() + ")");
            transformer.transform(streamSource, streamResult);
            fos.close();

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(goldName), 
                                     "(t1)transform after setOutputProperties(props) into: " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(t1)transform after setOutputProperties(props) failure reason:" + fileChecker.getExtendedInfo());
            }
        } 
        catch (Throwable t)
        {
            reporter.checkFail("testlet threw: " + t.toString());
            reporter.logThrowable(Logger.WARNINGMSG, t, "testlet threw ");
        }
    }

    /**
     * testlet2: verify setOutputProperty(s, s).  
     * @author Krishna.Meduri@eng.sun.com
     */
    public void testlet2(String xmlName, String xslName, String goldName)
    {
        try 
        {
            reporter.logStatusMsg("testlet2: verify setOutputProperty(s, s)");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // sc for Xerces
            DocumentBuilder db = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBld.parse(" + xslName + ")");
            Document document = db.parse(new File(xslName));
            DOMSource domSource = new DOMSource(document);
            domSource.setSystemId(QetestUtils.filenameToURL(xslName)); // sc

            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer(domSource);
            Properties xslProps = transformer.getOutputProperties();
            reporter.logHashtable(Logger.STATUSMSG, xslProps, 
                                  "Properties originally from the stylesheet");

            reporter.logTraceMsg("new StreamSource(new FileInputStream(" + xmlName + "))");
            StreamSource streamSource = new StreamSource(new FileInputStream(xmlName));
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            StreamResult streamResult = new StreamResult(fos);

            // Verify setting a whole block of properties
            reporter.logTraceMsg("transformer.setOutputProperty(indent, no)");
            transformer.setOutputProperty("indent", "no");

            reporter.logTraceMsg("transformer.transform(xml, " + outNames.currentName() + ")");
            transformer.transform(streamSource, streamResult);
            fos.close();

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(goldName), 
                                     "(t2)transform after setOutputProperty(indent, no) into: " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(t2)transform after setOutputProperty(indent, no) failure reason:" + fileChecker.getExtendedInfo());
            }
        } 
        catch (Throwable t)
        {
            reporter.checkFail("testlet threw: " + t.toString());
            reporter.logThrowable(Logger.WARNINGMSG, t, "testlet threw ");
        }
    }
    /**
     * testlet1: verify setOutputProperty(s, s).  
     * @author Krishna.Meduri@eng.sun.com
     */
    public void testlet3(String xmlName, String xslName, String goldName)
    {
        try 
        {
            reporter.logInfoMsg("testlet3: verify setOutputProperty(s, s)");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true); // sc for Xerces
            DocumentBuilder db = dbf.newDocumentBuilder();
            reporter.logTraceMsg("docBld.parse(" + xslName + ")");
            Document document = db.parse(new File(xslName));
            DOMSource domSource = new DOMSource(document);
            domSource.setSystemId(QetestUtils.filenameToURL(xslName)); // sc

            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer(domSource);
            Properties xslProps = transformer.getOutputProperties();
            reporter.logHashtable(Logger.STATUSMSG, xslProps, 
                                  "Properties originally from the stylesheet");

            reporter.logTraceMsg("new StreamSource(new FileInputStream(" + xmlName + "))");
            StreamSource streamSource = new StreamSource(new FileInputStream(xmlName));
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            StreamResult streamResult = new StreamResult(fos);

            // Verify setting single property
            reporter.logTraceMsg("transformer.setOutputProperty(method, text)");
            transformer.setOutputProperty("method", "text");

            reporter.logTraceMsg("transformer.transform(xml, " + outNames.currentName() + ")");
            transformer.transform(streamSource, streamResult);
            fos.close();

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(goldName), 
                                     "(t3)transform after setOutputProperty(method, text) into: " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(t3)transform after setOutputProperty(method, text) failure reason:" + fileChecker.getExtendedInfo());
            }
        } 
        catch (Throwable t)
        {
            reporter.checkFail("testlet threw: " + t.toString());
            reporter.logThrowable(Logger.WARNINGMSG, t, "testlet threw ");
        }
    }

    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by OutputPropertiesTest:\n"
                + "(Note: assumes inputDir=tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {

        OutputPropertiesTest app = new OutputPropertiesTest();

        app.doMain(args);
    }
}
