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
 * URIResolverTest.java
 *
 */
package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
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
 * Verify that URIResolvers are called properly.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class URIResolverTest extends FileBasedTest
{

    /** Provide sequential output names automatically.   */
    protected OutputNameManager outNames;


    /** 
     * A simple stylesheet with errors for testing in various flavors.  
     * Must be coordinated with templatesExpectedType/Value,
     * transformExpectedType/Value.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Subdir name under test\tests\api for files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Just initialize test name, comment, numTestCases. */
    public URIResolverTest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "URIResolverTest";
        testComment = "Verify that URIResolvers are called properly.";
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
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
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

        testFileInfo.inputName = testBasePath + "URIResolverTest.xsl";
        testFileInfo.xmlName = testBasePath + "URIResolverTest.xml";
        testFileInfo.goldName = goldBasePath + "URIResolverTest.out";

        return true;
    }


    /**
     * Build a stylesheet/do a transform with lots of URIs to resolve.
     * Verify that the URIResolver is called properly.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Build a stylesheet/do a transform with lots of URIs to resolve");
        LoggingURIResolver loggingURIResolver = new LoggingURIResolver((Logger)reporter);
        reporter.logTraceMsg("loggingURIResolver originally setup:" + loggingURIResolver.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Set the URIResolver and validate it
            factory.setURIResolver(loggingURIResolver);
            reporter.check((factory.getURIResolver() == loggingURIResolver),
                           true, "set/getURIResolver on factory");

            // Validate various URI's to be resolved during stylesheet 
            //  build with the loggingURIResolver
            String[] expectedXslUris = 
            {
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "impincl/SystemIdImport.xsl",
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "impincl/SystemIdInclude.xsl"
            };
            loggingURIResolver.setExpected(expectedXslUris);
            // Note that we don't currently have a default URIResolver, 
            //  so the LoggingURIResolver class will just attempt 
            //  to use the SystemIDResolver class instead
            // loggingURIResolver.setDefaultHandler(savedURIResolver);
            reporter.logInfoMsg("About to factory.newTemplates(" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            reporter.logTraceMsg("loggingURIResolver after newTemplates:" + loggingURIResolver.getQuickCounters());

            // Clear out any setExpected or counters
            loggingURIResolver.reset();

            transformer = templates.newTransformer();
            reporter.logTraceMsg("default transformer's getURIResolver is: " + transformer.getURIResolver());
            // Set the URIResolver and validate it
            transformer.setURIResolver(loggingURIResolver);
            reporter.check((transformer.getURIResolver() == loggingURIResolver),
                           true, "set/getURIResolver on transformer"); 

            // Validate various URI's to be resolved during transform
            //  time with the loggingURIResolver
            String[] expectedXmlUris = 
            {
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "../impincl/SystemIdImport.xsl",
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "impincl/SystemIdImport.xsl",
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "systemid/impincl/SystemIdImport.xsl",
            };
            
            //@todo Bugzilla#2425 every document() call is resolved twice twice - two fails caused below MOVED to SmoketestOuttakes.java 02-Nov-01 -sc
            reporter.logWarningMsg("Bugzilla#2425 every document() call is resolved twice twice - two fails caused below MOVED to SmoketestOuttakes.java 02-Nov-01 -sc");
            // loggingURIResolver.setExpected(expectedXmlUris);
            //@todo Bugzilla#2425 every document() call is resolved twice twice - two fails caused below MOVED to SmoketestOuttakes.java 02-Nov-01 -sc

            reporter.logTraceMsg("about to transform(...)");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(testFileInfo.xmlName)), 
                                  new StreamResult(outNames.nextName()));
            reporter.logTraceMsg("after transform(...)");
            // Clear out any setExpected or counters
            loggingURIResolver.reset();

            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            fileChecker.check(reporter, 
                    new File(outNames.currentName()), 
                    new File(testFileInfo.goldName), 
                    "transform of URI-filled xsl into: " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("URIResolver test unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "URIResolver test unexpectedly threw");
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
        return ("Common [optional] options supported by URIResolverTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        URIResolverTest app = new URIResolverTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
