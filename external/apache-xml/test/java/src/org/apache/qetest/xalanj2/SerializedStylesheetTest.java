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
 * SerializedStylesheetTest.java
 *
 */
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;

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
 * Basic functional test of serialized Templates objects.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SerializedStylesheetTest extends FileBasedTest
{

    /** Provides nextName(), currentName() functionality.  */
    protected OutputNameManager outNames;

    /** Simple identity.xml/xsl file pair.  */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Complex minitest.xml/xsl file pair.  */
    protected XSLTestfileInfo minitestFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = File.separator + "trax" + File.separator;

    /** Just initialize test name, comment, numTestCases. */
    public SerializedStylesheetTest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "SerializedStylesheetTest";
        testComment = "Basic functional test of serialized Templates objects";
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
        File outSubDir = new File(outputDir + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + TRAX_SUBDIR
                                         + testName, ".out");

        testFileInfo.inputName = QetestUtils.filenameToURL(inputDir 
                              + TRAX_SUBDIR + "identity.xsl");
        testFileInfo.xmlName = QetestUtils.filenameToURL(inputDir
                              + TRAX_SUBDIR + "identity.xml");
        testFileInfo.goldName = goldDir + TRAX_SUBDIR + "identity.out";

        minitestFileInfo.inputName = QetestUtils.filenameToURL(inputDir 
                              + File.separator + "Minitest.xsl");
        minitestFileInfo.xmlName = QetestUtils.filenameToURL(inputDir
                              + File.separator + "Minitest.xml");
        minitestFileInfo.goldName = goldDir + File.separator + "Minitest-xalanj2.out";

        return true;
    }


    /**
     * Basic functional test of serialized Templates objects.
     * Reproduce Bugzilla 2005 by rob.stanley@geac.com
     * http://nagoya.apache.org/bugzilla/show_bug.cgi?id=2005
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Basic functional test of serialized Templates objects");

        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            // Create templates from normal stylesheet
            Templates origTemplates = factory.newTemplates(new StreamSource(testFileInfo.inputName));

            // Serialize the Templates to disk
            reporter.logInfoMsg("About to serialize " + testFileInfo.inputName + " templates to: " + outNames.nextName());
            FileOutputStream ostream = new FileOutputStream(outNames.currentName());
            ObjectOutputStream oos = new java.io.ObjectOutputStream(ostream);
            oos.writeObject(origTemplates);
            oos.flush();
            ostream.close();            
            
            // Read the Templates back in
            reporter.logInfoMsg("About to read templates back");
            FileInputStream istream = new FileInputStream(outNames.currentName());
            ObjectInputStream ois = new ObjectInputStream(istream);
            Templates templates = (Templates)ois.readObject();
            istream.close();
 
            // Use the Templates in a transform
            reporter.logInfoMsg("About to call newTransformer");
            Transformer transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new StreamSource(testFileInfo.xmlName), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "Using serialized Templates, transform into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with serialized Template");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with serialized Template");
        }

        try
        {
            reporter.logTraceMsg("Try again, using the Minitest (with imports/includes/etc.)");
            TransformerFactory factory = TransformerFactory.newInstance();
            // Create templates from normal stylesheet
            Templates origTemplates = factory.newTemplates(new StreamSource(minitestFileInfo.inputName));

            // Serialize the Templates to disk
            reporter.logInfoMsg("About to serialize " + minitestFileInfo.inputName + " templates to: " + outNames.nextName());
            FileOutputStream ostream = new FileOutputStream(outNames.currentName());
            ObjectOutputStream oos = new java.io.ObjectOutputStream(ostream);
            oos.writeObject(origTemplates);
            oos.flush();
            ostream.close();            
            
            // Read the Templates back in
            reporter.logInfoMsg("About to read templates back");
            FileInputStream istream = new FileInputStream(outNames.currentName());
            ObjectInputStream ois = new ObjectInputStream(istream);
            Templates templates = (Templates)ois.readObject();
            istream.close();
 
            // Use the Templates in a transform
            reporter.logInfoMsg("About to call newTransformer");
            Transformer transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new StreamSource(minitestFileInfo.xmlName), new StreamResult(outNames.currentName()));
            if (Logger.PASS_RESULT != 
                fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(minitestFileInfo.goldName), 
                        "Using serialized Templates, transform into " + outNames.currentName())
               )
                reporter.logStatusMsg("Using serialized Templates: failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with serialized Minitest Template");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with serialized Minitest Template");
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
        return ("Common [optional] options supported by SerializedStylesheetTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SerializedStylesheetTest app = new SerializedStylesheetTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
