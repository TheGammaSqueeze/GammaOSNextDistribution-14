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
 * OutputSettingsTest.java
 *
 */
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.util.Properties;

import javax.xml.transform.Result;
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
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * Verify xalan:-specific output properties.
 * This test is similar to trax.OutputPropertiesTest but tests 
 * some Xalan-J 2.2.x+ specific features for the xalan: namespace, 
 * like: indent-amount, content-handler, entities,
 * use-url-escaping, and omit-meta-tag.
 * 
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class OutputSettingsTest extends FileBasedTest
{

    /** Used for generating names of actual output files.   */
    protected OutputNameManager outNames;

    /** Default OutputSettingsTest.xml/xsl file pair.   */
    protected XSLTestfileInfo xmlFileInfo = new XSLTestfileInfo();

    /** OutputEntities.xml/xsl/ent file pair.   */
    protected XSLTestfileInfo entFileInfo = new XSLTestfileInfo();

    /** Just initialize test name, comment, numTestCases. */
    public OutputSettingsTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "OutputSettingsTest";
        testComment = "Verify xalan:-specific output properties";
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
        File outSubDir = new File(outputDir + File.separator + "xalanj2");
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Possible problem creating output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + "xalanj2"
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + "xalanj2"
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + "xalanj2"
                              + File.separator;

        xmlFileInfo.inputName = testBasePath + "OutputSettingsXML.xsl";
        xmlFileInfo.xmlName = testBasePath + "OutputSettingsXML.xml";
        // Only root of the output gold name
        xmlFileInfo.goldName = goldBasePath + "OutputSettingsXML";

        // xsl file references OutputEntities.ent
        entFileInfo.inputName = testBasePath + "OutputEntities.xsl";
        entFileInfo.xmlName = testBasePath + "identity.xml";
        entFileInfo.goldName = goldBasePath + "OutputEntities.out";
        return true;
    }


    /**
     * Verify xalan:entities output property.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Verify xalan:entities output property");
        TransformerFactory factory = null;
        Templates templates = null;

        try
        {
            // Process stylesheet with replaced entities
            factory = TransformerFactory.newInstance();
            reporter.logInfoMsg("entFileInfo newTemplates(" + QetestUtils.filenameToURL(entFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(entFileInfo.inputName)));
            reporter.logHashtable(Logger.STATUSMSG, 
                                  templates.getOutputProperties(), "entFileInfo templates output properties");

            // Process the file once with default properties
            Transformer transformer = templates.newTransformer();
            Result result = new StreamResult(outNames.nextName());
            reporter.logInfoMsg("(1)replaced entities transform(" + QetestUtils.filenameToURL(entFileInfo.xmlName) 
                                + ", " + outNames.currentName() + ")");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(entFileInfo.xmlName)), result);
            // Validate the default transform to base gold file
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(entFileInfo.goldName), 
                              "(1)replaced entities transform into: " + outNames.currentName()
                              + " gold: " + entFileInfo.goldName);

        }
        catch (Throwable t)
        {
            reporter.checkErr("(1)replaced entities threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "(1)replaced entities threw ");
            return true;
        }
        reporter.testCaseClose();
        return true;
    }

    /**
     * Verify xalan:indent-amount output property.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Verify xalan:indent-amount output property");
        TransformerFactory factory = null;
        Templates templates = null;
        try
        {
            // Process simple XML output stylesheet
            factory = TransformerFactory.newInstance();
            reporter.logInfoMsg("xmlFileInfo newTemplates(" + QetestUtils.filenameToURL(xmlFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(xmlFileInfo.inputName)));
            reporter.logHashtable(Logger.STATUSMSG, 
                                  templates.getOutputProperties(), "xmlFileInfo templates output properties");

            // Process the file once with default properties
            Transformer transformer = templates.newTransformer();
            Result result = new StreamResult(outNames.nextName());
            reporter.logInfoMsg("(2)xml transform(" + QetestUtils.filenameToURL(xmlFileInfo.xmlName) 
                                + ", " + outNames.currentName() + ")");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(xmlFileInfo.xmlName)), result);
            // Validate the default transform to base gold file
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(xmlFileInfo.goldName + ".out"), 
                              "(2)xml transform into: " + outNames.currentName()
                              + " gold: " + xmlFileInfo.goldName + ".out");

            // Set Xalan-specific output property 
            reporter.logInfoMsg("setOutputProperty({http://xml.apache.org/xslt}indent-amount, 2)");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            result = new StreamResult(outNames.nextName());
            reporter.logInfoMsg("(2)xml-2 transform(" + QetestUtils.filenameToURL(xmlFileInfo.xmlName) 
                                + ", " + outNames.currentName() + ")");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(xmlFileInfo.xmlName)), result);
            // Validate the default transform to base gold file
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(xmlFileInfo.goldName + "-2.out"), 
                              "(2)xml-2 transform into: " + outNames.currentName()
                              + " gold: " + xmlFileInfo.goldName + "-2.out");
            reporter.logHashtable(Logger.STATUSMSG, 
                                  transformer.getOutputProperties(), "xml-2 transformer output properties");

            // Set Xalan-specific output property 
            reporter.logInfoMsg("setOutputProperty({http://xml.apache.org/xslt}indent-amount, 12)");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "12");

            result = new StreamResult(outNames.nextName());
            reporter.logInfoMsg("(2)xml-12 transform(" + QetestUtils.filenameToURL(xmlFileInfo.xmlName) 
                                + ", " + outNames.currentName() + ")");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(xmlFileInfo.xmlName)), result);
            // Validate the default transform to base gold file
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(xmlFileInfo.goldName + "-12.out"), 
                              "(2)xml-12 transform into: " + outNames.currentName()
                              + " gold: " + xmlFileInfo.goldName + "-12.out");
            reporter.logHashtable(Logger.STATUSMSG, 
                                  transformer.getOutputProperties(), "xml-12 transformer output properties");
        }
        catch (Throwable t)
        {
            reporter.checkErr("(2)xml stylesheet threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "(2)xml stylesheet threw ");
            return true;
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
        return ("Common [optional] options supported by OutputSettingsTest:\n"
                + "(Note: assumes inputDir=tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }

    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        OutputSettingsTest app = new OutputSettingsTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
