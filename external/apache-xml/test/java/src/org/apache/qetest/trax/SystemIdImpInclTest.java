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
 * SystemIdImpInclTest.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * Test behavior of imports/includes with various setSystemId sources.  
 * <b>Note:</b> This test is directory-dependent, so if there are 
 * any fails, check the code to see what the test file is expecting 
 * the path/directory/etc. to be.
 * //@todo More variations on kinds of systemIds: file: id's that 
 * are absolute, etc.; any/all other forms of id's like http:
 * (which will require network resources available).
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SystemIdImpInclTest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Name of a valid, known-good xsl/xml file pair we can use.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** 
     * Just basename of a valid, known-good file, both .xsl/.xml .
     */
    protected String knownGoodBaseName = null;

    /** Gold filename for level0, i.e. one directory above the testfile.  */
    protected String goldFileLevel0 = "SystemIdImpInclLevel0.out";

    /** Gold filename for level1, i.e. the directory of the testfile.  */
    protected String goldFileLevel1 = "SystemIdImpInclLevel1.out";

    /** Gold filename for level2, i.e. a directory below the testfile.  */
    protected String goldFileLevel2 = "SystemIdImpInclLevel2.out";

    /** Gold filename for http, i.e. a from a webserver.  */
    protected String goldFileHttp = "SystemIdImpInclHttp.out";

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Convenience variable for user.dir - cached during test.  */
    protected String savedUserDir = null;

    /** Just initialize test name, comment, numTestCases. */
    public SystemIdImpInclTest()
    {
        numTestCases = 3; // Set numTestCases to 3 to skip the 4th one that does http:
        testName = "SystemIdImpInclTest";
        testComment = "Test behavior of imports/includes with various setSystemId sources";
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
        knownGoodBaseName = "SystemIdImpIncl";
        testFileInfo.inputName = testBasePath + knownGoodBaseName + ".xsl";
        testFileInfo.xmlName = testBasePath + knownGoodBaseName + ".xml";
        testFileInfo.goldName = goldBasePath + knownGoodBaseName + ".out";
        goldFileLevel0 = goldBasePath + goldFileLevel0; // just prepend path
        goldFileLevel1 = goldBasePath + goldFileLevel1; // just prepend path
        goldFileLevel2 = goldBasePath + goldFileLevel2; // just prepend path
        goldFileHttp = goldBasePath + goldFileHttp; // just prepend path

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
     * Simple StreamSources with different setSystemIds.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Simple StreamSources with different setSystemIds");

        TransformerFactory factory = null;
        try
        {
            factory = TransformerFactory.newInstance();
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Verify we can do basic transforms with readers/streams,
            //  with the 'normal' systemId
            reporter.logInfoMsg("StreamSource.setSystemId(level1)");
            InputStream xslStream1 = new FileInputStream(testFileInfo.inputName);
            Source xslSource1 = new StreamSource(xslStream1);
            xslSource1.setSystemId(QetestUtils.filenameToURL(testFileInfo.inputName));
            
            InputStream xmlStream1 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource1 = new StreamSource(xmlStream1);
            xmlSource1.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            reporter.logTraceMsg("Create FileOutputStream to " + outNames.nextName());
            FileOutputStream fos1 = new FileOutputStream(outNames.currentName());
            Result result1 = new StreamResult(fos1);

            Templates templates1 = factory.newTemplates(xslSource1);
            Transformer transformer1 = templates1.newTransformer();
            reporter.logInfoMsg("About to transform, systemId(level1)");
            transformer1.transform(xmlSource1, result1);
            fos1.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldFileLevel1), 
                              "transform after setSystemId(level1) into " + outNames.currentName())
               )
                reporter.logInfoMsg("transform after setSystemId(level1)... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with setSystemId(level1)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with after setSystemId(level1)");
        }

        try
        {
            // Verify we can do basic transforms with readers/streams,
            //  systemId set up one level
            reporter.logInfoMsg("StreamSource.setSystemId(level0)");
            InputStream xslStream1 = new FileInputStream(testFileInfo.inputName);
            Source xslSource1 = new StreamSource(xslStream1);
            xslSource1.setSystemId(QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"));
            
            InputStream xmlStream1 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource1 = new StreamSource(xmlStream1);
            xmlSource1.setSystemId(QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xml"));

            reporter.logTraceMsg("Create FileOutputStream to " + outNames.nextName());
            FileOutputStream fos1 = new FileOutputStream(outNames.currentName());
            Result result1 = new StreamResult(fos1);

            Templates templates1 = factory.newTemplates(xslSource1);
            Transformer transformer1 = templates1.newTransformer();
            reporter.logInfoMsg("About to transform, systemId(level0)");
            transformer1.transform(xmlSource1, result1);
            fos1.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldFileLevel0), 
                              "transform after setSystemId(level0) into " + outNames.currentName())
               )
                reporter.logInfoMsg("transform after setSystemId(level0)... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with setSystemId(level0)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with setSystemId(level0)");
        }

        try
        {
            // Verify we can do basic transforms with readers/streams,
            //  with the systemId down one level
            reporter.logInfoMsg("StreamSource.setSystemId(level2)");
            InputStream xslStream1 = new FileInputStream(testFileInfo.inputName);
            Source xslSource1 = new StreamSource(xslStream1);
            xslSource1.setSystemId(QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"));
            
            InputStream xmlStream1 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource1 = new StreamSource(xmlStream1);
            xmlSource1.setSystemId(QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xml"));

            reporter.logTraceMsg("Create FileOutputStream to " + outNames.nextName());
            FileOutputStream fos1 = new FileOutputStream(outNames.currentName());
            Result result1 = new StreamResult(fos1);

            Templates templates1 = factory.newTemplates(xslSource1);
            Transformer transformer1 = templates1.newTransformer();
            reporter.logInfoMsg("About to transform, systemId(level2)");
            transformer1.transform(xmlSource1, result1);
            fos1.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldFileLevel2), 
                              "transform after setSystemId(level2) into " + outNames.currentName())
               )
                reporter.logInfoMsg("transform after setSystemId(level2)... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with setSystemId(level2)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with setSystemId(level2)");
        }

        try
        {
            // Verify we can do basic transforms with readers/streams,
            //  with the systemId down one level
            reporter.logInfoMsg("StreamSource.setSystemId(xslonly level2)");
            InputStream xslStream1 = new FileInputStream(testFileInfo.inputName);
            Source xslSource1 = new StreamSource(xslStream1);
            xslSource1.setSystemId(QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"));
            
            InputStream xmlStream1 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource1 = new StreamSource(xmlStream1);
            // Explicitly don't set the xmlId - shouldn't be needed

            reporter.logTraceMsg("Create FileOutputStream to " + outNames.nextName());
            FileOutputStream fos1 = new FileOutputStream(outNames.currentName());
            Result result1 = new StreamResult(fos1);

            Templates templates1 = factory.newTemplates(xslSource1);
            Transformer transformer1 = templates1.newTransformer();
            reporter.logInfoMsg("About to transform, systemId(xslonly level2)");
            transformer1.transform(xmlSource1, result1);
            fos1.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldFileLevel2), 
                              "transform after setSystemId(xslonly level2) into " + outNames.currentName())
               )
                reporter.logInfoMsg("transform after setSystemId(xslonly level2)... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with setSystemId(xslonly level2)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with setSystemId(xslonly level2)");
        }
        reporter.testCaseClose();
        return true;
    }

    /**
     * Verify simple SAXSources with systemIds.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Verify simple SAXSources with systemIds");

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        InputSource xslInpSrc = null;
        Source xslSource = null;
        Source xmlSource = null;
        try
        {
            factory = TransformerFactory.newInstance();
            saxFactory = (SAXTransformerFactory)factory;
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Verify basic transforms with with various systemId
            //  and a SAXSource(InputSource(String))
            // level0: one level up
/********************************
// SAXSource(impSrc(str)) level0, level2
// Note: these cases may not be valid to test, since the setSystemId
//  call will effectively overwrite the underlying InputSource's 
//  real systemId, and thus the stylesheet can't be built
//  Answer: write a custom test case that parses the stylesheet
//  and builds it, but doesn't get imports/includes until later 
//  when we have setSystemId
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "SAXSource(inpSrc(str)).systemId(level0: one up)");
********************************/
            // level1: same systemId as actual file
			// @DEM changes are to allow test to run on various 
			//	platforms regardeless of encodings; force it to 
			//	be UTF-8 since that's what's checked in
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(testFileInfo.inputName), 
                                    xmlSource, goldFileLevel1,
                                    "SAXSource(inpSrc(str)).systemId(level1: same level)");

            // level2: one level down
/********************************
// SAXSource(impSrc(str)) level0, level2
// Note: these cases may not be valid to test, since the setSystemId
//  call will effectively overwrite the underlying InputSource's 
//  real systemId, and thus the stylesheet can't be built
//  Answer: write a custom test case that parses the stylesheet
//  and builds it, but doesn't get imports/includes until later 
//  when we have setSystemId
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel2,
                                    "SAXSource(inpSrc(str)).systemId(level2: one down)");
********************************/
            reporter.logTraceMsg("@todo: add test for SAXSource with reset systemId (see code comments)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with SAXSources(1)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with SAXSources(1)");
        }
        try
        {
            // Verify basic transforms with with various systemId
            //  and a SAXSource(InputSource(InputStream))
            // level0: one level up
            xslInpSrc = new InputSource(new FileInputStream(testFileInfo.inputName));
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "SAXSource(inpSrc(byteS)).systemId(level0: one up)");

            // level1: same systemId as actual file
            xslInpSrc = new InputSource(new FileInputStream(testFileInfo.inputName));
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(testFileInfo.inputName), 
                                    xmlSource, goldFileLevel1,
                                    "SAXSource(inpSrc(byteS)).systemId(level1: same level)");

            // level2: one level down
            xslInpSrc = new InputSource(new FileInputStream(testFileInfo.inputName));
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel2,
                                    "SAXSource(inpSrc(byteS)).systemId(level2: one down)");

            // Verify basic transforms with with various systemId
            //  and a SAXSource(InputSource(Reader))
            // level0: one level up
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "SAXSource(inpSrc(charS)).systemId(level0: one up)");

            // level1: same systemId as actual file
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(testFileInfo.inputName), 
                                    xmlSource, goldFileLevel1,
                                    "SAXSource(inpSrc(charS)).systemId(level1: same level)");

            // level2: one level down
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel2,
                                    "SAXSource(inpSrc(charS)).systemId(level2: one down)");

            // Verify basic transforms with with various systemId
            //  and a SAXSource(XMLReader, InputSource(various))
            // Be sure to use the JAXP methods only!
            SAXParserFactory spfactory = SAXParserFactory.newInstance();
            spfactory.setNamespaceAware(true);
            SAXParser saxParser = spfactory.newSAXParser();
    	    XMLReader reader = saxParser.getXMLReader();
            // level0: one level up, with a character stream
            xslInpSrc = new InputSource(new InputStreamReader(new FileInputStream(testFileInfo.inputName), "UTF-8")); //@DEM
//            xslInpSrc = new InputSource(new FileReader(testFileInfo.inputName));  @DEM
            xslSource = new SAXSource(reader, xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "SAXSource(reader, inpSrc(charS)).systemId(level0: one up)");

            // level1: same systemId as actual file, with a systemId
            saxParser = spfactory.newSAXParser();
    	    reader = saxParser.getXMLReader();
            xslInpSrc = new InputSource(testFileInfo.inputName);
            xslSource = new SAXSource(reader, xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(testFileInfo.inputName), 
                                    xmlSource, goldFileLevel1,
                                    "SAXSource(reader, inpSrc(str)).systemId(level1: same level)");

            // level2: one level down, with a byte stream
            saxParser = spfactory.newSAXParser();
    	    reader = saxParser.getXMLReader();
            xslInpSrc = new InputSource(new FileInputStream(testFileInfo.inputName));
            xslSource = new SAXSource(reader, xslInpSrc);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel2,
                                    "SAXSource(reader, inpSrc(byteS)).systemId(level2: one down)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with SAXSources(2)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with SAXSources(2)");
        }

        reporter.testCaseClose();
        return true;
    }

    /**
     * Verify simple DOMSources with systemIds.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Verify simple DOMSources with systemIds");
        TransformerFactory factory = null;
        DocumentBuilderFactory dfactory = null;
        DocumentBuilder docBuilder = null;
        Node xslNode = null;
        Source xslSource = null;
        Source xmlSource = null;

        try
        {
            factory = TransformerFactory.newInstance();
            dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            docBuilder = dfactory.newDocumentBuilder();

            // Verify basic transforms with with various systemId
            //  and a DOMSource(InputSource(String))
            // level0: one level up
            reporter.logTraceMsg("about to parse(InputSource(" + QetestUtils.filenameToURL(testFileInfo.inputName) + "))");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            xslSource = new DOMSource(xslNode);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "DOMSource(inpSrc(str)).systemId(level0: one up)");

            // level1: same systemId as actual file
            reporter.logTraceMsg("about to parse(InputSource(" + QetestUtils.filenameToURL(testFileInfo.inputName) + "))");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            xslSource = new DOMSource(xslNode);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(testFileInfo.inputName), 
                                    xmlSource, goldFileLevel1,
                                    "DOMSource(inpSrc(str)).systemId(level1: same level)");

            // level2: one level down
            reporter.logTraceMsg("about to parse(InputSource(" + QetestUtils.filenameToURL(testFileInfo.inputName) + "))");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            xslSource = new DOMSource(xslNode);

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel2,
                                    "DOMSource(inpSrc(str)).systemId(level2: one down)");

            // Sample extra test: DOMSource that had systemId set 
            //  differently in the constructor - tests that you can 
            //  later call setSystemId and have it work
            // level0: one level up
            reporter.logTraceMsg("about to parse(InputSource(" + QetestUtils.filenameToURL(testFileInfo.inputName) + "))");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            // Set the original systemId to itself, or level1
            xslSource = new DOMSource(xslNode, QetestUtils.filenameToURL(testFileInfo.inputName));

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            // Test it with a level0, or one level up systemId
            checkSourceWithSystemId(xslSource, QetestUtils.filenameToURL(inputDir + File.separator + knownGoodBaseName + ".xsl"), 
                                    xmlSource, goldFileLevel0,
                                    "DOMSource(inpSrc(str),sysId-level1).systemId(level0: one up)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with DOMSources(1)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with DOMSources(1)");
        }



        reporter.testCaseClose();
        return true;
    }


    /**
     * Verify various simple Sources with http: systemIds.
     * Test may be commented out until we have a better way to 
     * maintain and check for existence and correct content of 
     * stylesheets on the http server.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Verify various simple Sources with http: systemIds");

        // This is the name of a directory on the apache server 
        //  that has impincl\SystemIdInclude.xsl and 
        //  impincl\SystemIdImport.xsl files on it - obviously, 
        //  your JVM must have access to this server to successfully
        //  run this portion of the test!
        String httpSystemIdBase = "http://xml.apache.org/xalan-j/test";

        // Verify http connectivity
        //  If your JVM environment can't connect to the http: 
        //  server, then we can't complete the test
        // This could happen due to various network problems, 
        //  not being connected, firewalls, etc.
        try
        {
            reporter.logInfoMsg("verifing http connectivity before continuing testCase");
            // Note hard-coded path to one of the two files we'll be relying on
            URL testURL = new URL(httpSystemIdBase + "/impincl/SystemIdInclude.xsl");
            URLConnection urlConnection = testURL.openConnection();
            // Ensure we don't get a cached copy
            urlConnection.setUseCaches(false);
            // Ensure we don't get asked interactive questions
            urlConnection.setAllowUserInteraction(false);
            // Actually connect to the document; will throw 
            //  IOException if anything goes wrong
            urlConnection.connect();
            // Convenience: log out when the doc was last modified
            reporter.logInfoMsg(testURL.toString() + " last modified: " 
                                  + urlConnection.getLastModified());
            int contentLen = urlConnection.getContentLength();
            reporter.logStatusMsg("URL.getContentLength() was: " + contentLen);
            if (contentLen < 1)
            {
                // if no content, throw 'fake' exception to 
                //  short-circut test case
                throw new IOException("URL.getContentLength() was: " + contentLen);
            }
            // Also verify that the file there contains (some of) the data we expect!
            reporter.logTraceMsg("calling urlConnection.getContent()...");
            Object content = urlConnection.getContent();
            if (null == content)
            {
                // if no content, throw 'fake' exception to 
                //  short-circut test case
                throw new IOException("URL.getContent() was null!");
            }
            reporter.logTraceMsg("getContent().toString() is now: " + content.toString());

            //@todo we should also verify some key strings in the 
            //  expected .xsl file here, if possible
        }
        catch (IOException ioe)
        {
            reporter.logThrowable(Logger.ERRORMSG, ioe, "Can't connect threw");
            reporter.logErrorMsg("Can't connect to: " + httpSystemIdBase 
                                 + "/impincl/SystemIdInclude.xsl, skipping testcase");
            reporter.checkPass("FAKE PASS RECORD; testCase was skipped");
            // Skip the rest of the testcase
            reporter.testCaseClose();
            return true;
        }


        TransformerFactory factory = null;
        Source xslSource = null;
        Source xmlSource = null;

        try
        {
            factory = TransformerFactory.newInstance();
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Verify StreamSource from local disk with a 
            //  http: systemId for imports/includes
            xslSource = new StreamSource(new FileInputStream(testFileInfo.inputName));

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            // Note that the systemId set (the second argument below)
            //  must be the path to the proper 'directory' level
            //  on the webserver: setting it to just ".../test"
            //  will fail, since it be considered a file of that 
            //  name, not the directory
            checkSourceWithSystemId(xslSource, httpSystemIdBase + "/", 
                                    xmlSource, goldFileHttp,
                                    "StreamSource().systemId(http:)");

            xslSource = new StreamSource(new FileInputStream(testFileInfo.inputName));

            xmlSource = new StreamSource(new FileInputStream(testFileInfo.xmlName));
            xmlSource.setSystemId(QetestUtils.filenameToURL(testFileInfo.xmlName));

            checkSourceWithSystemId(xslSource, httpSystemIdBase + "/" + knownGoodBaseName + ".xsl", 
                                    xmlSource, goldFileHttp,
                                    "StreamSource().systemId(http:)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with http systemIds(1)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with http systemIds(1)");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method to test setting SystemId and doing transform.
     * Simply does:<pre>
     * xslSrc.setSystemId(systemId);
     * templates = factory.newTemplates(xslSrc);
     * transformer = templates.newTransformer();
     * transformer.transform(xmlSrc, StreamResult(...));
     * fileChecker.check(... goldFileName, desc)
     * </pre>
     * Also catches any exceptions and logs them as fails.
     *
     * @param xslSrc Source to use for stylesheet
     * @param systemId systemId to set on the stylesheet
     * @param xmlSrc Source to use for XML input data
     * @param goldFileName name of expected file to compare with
     * @param desc description of this test
     */
    public void checkSourceWithSystemId(Source xslSrc, String systemId, 
                                        Source xmlSrc, String goldFileName,
                                        String desc)
    {
        reporter.logTraceMsg(desc + " (" + systemId + ")");
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            xslSrc.setSystemId(systemId);

            // Use the next available output name for result
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result outputResult = new StreamResult(fos);

            Templates templates = factory.newTemplates(xslSrc);
            Transformer transformer = templates.newTransformer();
            transformer.transform(xmlSrc, outputResult);
            fos.close(); // must close ostreams we own
            if (Logger.PASS_RESULT
                != fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(goldFileName), 
                              desc + " (" + systemId + ") into: " + outNames.currentName())
               )
                reporter.logInfoMsg(desc + "... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail(desc + " threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, desc + " threw");
        }
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by SystemIdImpInclTest:\n"
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
        SystemIdImpInclTest app = new SystemIdImpInclTest();
        app.doMain(args);
    }
}
