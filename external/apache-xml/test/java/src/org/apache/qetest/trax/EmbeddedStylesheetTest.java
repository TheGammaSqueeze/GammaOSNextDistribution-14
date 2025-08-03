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
 * EmbeddedStylesheetTest.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import javax.xml.transform.Source;
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

//-------------------------------------------------------------------------

/**
 * Test behavior of various kinds of embedded stylesheets.  
 * <b>Note:</b> This test is directory-dependent, so if there are 
 * any fails, check the code to see what the test file is expecting 
 * the path/directory/etc. to be.
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class EmbeddedStylesheetTest extends FileBasedTest
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

    /** Embedded identity test file for getEmbedded....  */
    protected XSLTestfileInfo embeddedFileInfo = new XSLTestfileInfo();

    /** Embedded fragment test file for getEmbedded....  */
    protected XSLTestfileInfo embeddedFragmentFileInfo = new XSLTestfileInfo();

    /** Embedded types test file for getEmbedded.... text/xsl  */
    protected String typeNameTextXsl = null;

    /** Embedded types test file for getEmbedded.... text/xml  */
    protected String typeNameTextXml = null;

    /** Embedded types test file for getEmbedded.... application/xml+xslt  */
    protected String typeNameApplicationXmlXslt = null;

    /** Embedded types gold file for all types  */
    protected String typeGoldName = null;

    /** Embedded relative path test file for getEmbedded....  */
    protected String embeddedRelativeXmlName = null;

    /** Gold embedded relative path test file for getEmbedded, at up level....  */
    protected String relativeGoldFileLevel0 = null;
    /** Gold embedded relative path test file for getEmbedded, at default level....  */
    protected String relativeGoldFileLevel1 = null;
    /** Gold embedded relative path test file for getEmbedded, at down level....  */
    protected String relativeGoldFileLevel2 = null;

    /** SystemId identity test file for getEmbedded....  */
    protected XSLTestfileInfo systemIdFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Convenience variable for user.dir - cached during test.  */
    protected String savedUserDir = null;

    /** Just initialize test name, comment, numTestCases. */
    public EmbeddedStylesheetTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "EmbeddedStylesheetTest";
        testComment = "Test behavior of various kinds of embedded stylesheets";
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
        testFileInfo.inputName = testBasePath + testName + ".xsl";
        testFileInfo.xmlName = testBasePath + testName + ".xml";
        testFileInfo.goldName = goldBasePath + testName + ".out";

        embeddedFileInfo.xmlName = testBasePath + "embeddedIdentity.xml";
        embeddedFileInfo.goldName = goldBasePath + "embeddedIdentity.out";

        typeNameTextXsl = testBasePath + "EmbeddedType-text-xsl.xml";
        typeNameTextXml = testBasePath + "EmbeddedType-text-xml.xml";
        typeNameApplicationXmlXslt = testBasePath + "EmbeddedType-application-xml-xslt.xml";
        typeGoldName = goldBasePath + "EmbeddedType.out";

        embeddedFragmentFileInfo.xmlName = testBasePath + "EmbeddedFragment.xml";
        embeddedFragmentFileInfo.goldName = goldBasePath + "EmbeddedFragment.out";

        embeddedRelativeXmlName = testBasePath + "EmbeddedRelative.xml";
        relativeGoldFileLevel0 = goldBasePath + "EmbeddedRelative0.out";
        relativeGoldFileLevel1 = goldBasePath + "EmbeddedRelative1.out";
        relativeGoldFileLevel2 = goldBasePath + "EmbeddedRelative2.out";

        systemIdFileInfo.xmlName = testBasePath + "SystemIdTest.xml";
        systemIdFileInfo.goldName = goldBasePath + "SystemIdTest.out";

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
     * Simple xml documents with xml-stylesheet PI's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Simple xml documents with xml-stylesheet PI's");

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
        String media= null;     // often ignored
        String title = null;    // often ignored
        String charset = null;  // often ignored
        try
        {
            // Verify you can process a simple embedded stylesheet 
            //  (also tested in TransformerFactoryAPITest)
            Source stylesheet = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(embeddedFileInfo.xmlName)), 
                                                                media, title, charset);
            reporter.logTraceMsg("got AssociatedStylesheet");
            Templates embedTemplates = factory.newTemplates(stylesheet);
            Transformer embedTransformer = embedTemplates.newTransformer();
            reporter.logTraceMsg("Got embedded templates, about to transform.");
            embedTransformer.transform(new StreamSource(QetestUtils.filenameToURL(embeddedFileInfo.xmlName)), 
                                       new StreamResult(outNames.nextName()));

            int result = fileChecker.check(reporter, 
                                           new File(outNames.currentName()), 
                                           new File(embeddedFileInfo.goldName), 
                                          "(1)embedded transform into " + outNames.currentName());
            if (result == Logger.FAIL_RESULT)
                reporter.logInfoMsg("(1)embedded transform failure reason:" + fileChecker.getExtendedInfo());

            // Verify the stylesheet you get from an embedded source 
            //  can be reused for other documents
            embedTransformer = embedTemplates.newTransformer();
            embedTransformer.transform(new StreamSource(QetestUtils.filenameToURL(embeddedFileInfo.xmlName)), 
                                       new StreamResult(outNames.nextName()));

            result = fileChecker.check(reporter, 
                                           new File(outNames.currentName()), 
                                           new File(embeddedFileInfo.goldName), 
                                          "(1a)embedded transform into " + outNames.currentName());
            if (result == Logger.FAIL_RESULT)
                reporter.logInfoMsg("(1a)embedded transform failure reason:" + fileChecker.getExtendedInfo());

            // Verify the transformer itself can be reused
            //  on a *different* document
            embedTransformer.transform(new StreamSource(QetestUtils.filenameToURL(systemIdFileInfo.xmlName)), 
                                       new StreamResult(outNames.nextName()));

            result = fileChecker.check(reporter, 
                                           new File(outNames.currentName()), 
                                           new File(systemIdFileInfo.goldName), 
                                          "(2)embedded transform into " + outNames.currentName());
            if (result == Logger.FAIL_RESULT)
                reporter.logInfoMsg("(2)embedded transform failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with simple embedded reuse");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with simple embedded reuse");
        }

        try
        {
            // Verify you can process an embedded stylesheet as fragment
            testEmbeddedTransform(new StreamSource(QetestUtils.filenameToURL(embeddedFragmentFileInfo.xmlName)),
                                  new StreamSource(QetestUtils.filenameToURL(embeddedFragmentFileInfo.xmlName)),
                                  "(10)embedded fragment transform",
                                  embeddedFragmentFileInfo.goldName);

            // Verify you can process an embedded stylesheet that 
            //  comes from a relative path - default systemId
            testEmbeddedTransform(new StreamSource(QetestUtils.filenameToURL(embeddedRelativeXmlName)),
                                  new StreamSource(QetestUtils.filenameToURL(embeddedRelativeXmlName)),
                                  "(11)embedded relative transform",
                                  relativeGoldFileLevel1);

            // ...Verify relative paths, explicit systemId up one level0
            // sysId for level0 up one: inputDir + File.separator + "EmbeddedRelative.xml"
            Source relativeXmlSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeXmlSrc.setSystemId(QetestUtils.filenameToURL(inputDir + File.separator + "EmbeddedRelative.xml"));
            Source relativeTransformSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeTransformSrc.setSystemId(QetestUtils.filenameToURL(inputDir + File.separator + "EmbeddedRelative.xml"));
            testEmbeddedTransform(relativeXmlSrc,
                                  relativeTransformSrc,
                                  "(12a)embedded relative, explicit sysId up level0",
                                  relativeGoldFileLevel0);
            // ...Verify relative paths, explicit systemId same level1
            relativeXmlSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeXmlSrc.setSystemId(QetestUtils.filenameToURL(embeddedRelativeXmlName));
            relativeTransformSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeTransformSrc.setSystemId(QetestUtils.filenameToURL(embeddedRelativeXmlName));
            testEmbeddedTransform(relativeXmlSrc,
                                  relativeTransformSrc,
                                  "(12b)embedded relative, explicit sysId same level1",
                                  relativeGoldFileLevel1);

            // ...Verify relative paths, explicit systemId down one level2
            // sysId for level2 down one: inputDir + "/trax/systemid/" + "EmbeddedRelative.xml"
            relativeXmlSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeXmlSrc.setSystemId(QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + "EmbeddedRelative.xml"));
            relativeTransformSrc = new StreamSource(new FileInputStream(embeddedRelativeXmlName));
            relativeTransformSrc.setSystemId(QetestUtils.filenameToURL(inputDir + "/trax/systemid/" + "EmbeddedRelative.xml"));
            testEmbeddedTransform(relativeXmlSrc,
                                  relativeTransformSrc,
                                  "(12c)embedded relative, explicit sysId down level2",
                                  relativeGoldFileLevel2);

            // Verify you can process various types of embedded stylesheets
            // This also verifies that a type of 'not/found' is skipped
            // Xalan-specific: text/xsl
            testEmbeddedTransform(new StreamSource(QetestUtils.filenameToURL(typeNameTextXsl)),
                                  new StreamSource(QetestUtils.filenameToURL(typeNameTextXsl)),
                                  "(20a)xml:stylesheet type=text/xsl",
                                  typeGoldName);

            // Proposed standard: text/xml
            testEmbeddedTransform(new StreamSource(QetestUtils.filenameToURL(typeNameTextXml)),
                                  new StreamSource(QetestUtils.filenameToURL(typeNameTextXml)),
                                  "(20b)xml:stylesheet type=text/xml",
                                  typeGoldName);

            // Proposed standard: application/xml+xslt
            testEmbeddedTransform(new StreamSource(QetestUtils.filenameToURL(typeNameApplicationXmlXslt)),
                                  new StreamSource(QetestUtils.filenameToURL(typeNameApplicationXmlXslt)),
                                  "(20b)xml:stylesheet type=application/xml+xslt",
                                  typeGoldName);

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with other embedded");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with other embedded");
        }

        reporter.testCaseClose();
        return true;
    }

    /**
     * Test media, title, charset types of xml-stylesheet PI's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Test media, title, charset types of xml-stylesheet PI's");

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
        String mediaTitleName = inputDir + File.separator 
                                + TRAX_SUBDIR + File.separator
                                + "EmbeddedMediaTitle.xml";
        try
        {
            String media= null;
            String title = null;
            String charset = null;
            media = "foo/media";
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")");
            Source xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            Transformer transformer = factory.newTransformer(xslSrc);
            reporter.logTraceMsg("Got embedded templates, media=" + media + " , about to transform.");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(relativeGoldFileLevel1), 
                                     "(20)embedded media=" + media + " transform into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(20)embedded media=" + media + " failure reason:" + fileChecker.getExtendedInfo());
            }


            media = "bar/media";
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")");
            xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            transformer = factory.newTransformer(xslSrc);
            reporter.logTraceMsg("Got embedded templates, media=" + media + " , about to transform.");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(relativeGoldFileLevel0), 
                                     "(20a)embedded media=" + media + " transform into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(20a)embedded media=" + media + " failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testcase(media)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testcase(media)");
        }

        try
        {
            String media= null;
            String title = null;
            String charset = null;
            title = "foo-title";
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", title=" + title + ")");
            Source xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            Transformer transformer = factory.newTransformer(xslSrc);
            reporter.logTraceMsg("Got embedded templates, title=" + title + " , about to transform.");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(relativeGoldFileLevel1), 
                                     "(21)embedded title=" + title + " transform into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(21)embedded title=" + title + " failure reason:" + fileChecker.getExtendedInfo());
            }


            title = "bar-title";
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", title=" + title + ")");
            xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            transformer = factory.newTransformer(xslSrc);
            reporter.logTraceMsg("Got embedded templates, title=" + title + " , about to transform.");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(relativeGoldFileLevel0), 
                                     "(21a)embedded title=" + title + " transform into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(21a)embedded title=" + title + " failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testcase(title)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testcase(title)");
        }

        try
        {
            String media= null;
            String title = null;
            String charset = null;
            media = "alt/media"; // Should use alternate, I think
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")");
            Source xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            Transformer transformer = factory.newTransformer(xslSrc);
            reporter.logTraceMsg("Got embedded templates, media=" + media + " , about to transform.");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(relativeGoldFileLevel2), 
                                     "(22)embedded media=" + media + " transform into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg("(22)embedded media=" + media + " failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testcase(alternate)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testcase(alternate)");
        }
        try
        {
            String media= null;
            String title = null;
            String charset = null;
            title = "title-not-found"; // negative test: there is no title like this
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", title=" + title + ")");
            Source xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            if (null == xslSrc)
            {
                reporter.checkPass("getAssociatedStylesheet returns null for not found title");
            }
            else
            {
                reporter.checkFail("getAssociatedStylesheet returns null for not found title");
                reporter.logErrorMsg("xslSrc is: " + xslSrc);
            }
            title = null;
            media = "media/notfound"; // negative test: there is no media like this
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")");
            xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            if (null == xslSrc)
            {
                reporter.checkPass("getAssociatedStylesheet returns null for not found media");
            }
            else
            {
                reporter.checkFail("getAssociatedStylesheet returns null for not found media");
                reporter.logErrorMsg("xslSrc is: " + xslSrc);
            }

            title = "alt-title";        // This title is in there, but
            media = "media/notfound"; // negative test: there is no media like this
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")"
                                 + ", title=" + title + ")");
            xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            if (null == xslSrc)
            {
                reporter.checkPass("getAssociatedStylesheet returns null bad media, good title");
            }
            else
            {
                reporter.checkFail("getAssociatedStylesheet returns null bad media, good title");
                reporter.logErrorMsg("xslSrc is: " + xslSrc);
            }

            title = "title-not-found"; // No title like this, but
            media = "alt/media"; // there is a media like this
            reporter.logTraceMsg("About to getAssociatedStylesheet(" + QetestUtils.filenameToURL(mediaTitleName)
                                 + ", media=" + media + ")"
                                 + ", title=" + title + ")");
            xslSrc = factory.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(mediaTitleName)), 
                                                                media, title, charset);
            if (null == xslSrc)
            {
                reporter.checkPass("getAssociatedStylesheet returns null bad title, good media");
            }
            else
            {
                reporter.checkFail("getAssociatedStylesheet returns null bad title, good media");
                reporter.logErrorMsg("xslSrc is: " + xslSrc);
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testcase(negative)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testcase(negative)");
        }

        reporter.logTraceMsg("//@todo testing with charset");
        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method to test transforming embedded stylesheets.
     * Calls getAssociatedStylesheet on the xml source, and then 
     * uses the resulting stylesheet to transform the other source.
     * Two sources used since StreamSources may not be re-useable.
     * Calls fileChecker.check to validate.
     * 
     * @param xmlSrc Source of XML file to use to get stylesheet from
     * @param transformSrc Source of XML file to transform
     * @param desc description of test, used in check() calls
     * @param goldName path\filename of gold file
     */
    protected void testEmbeddedTransform(Source xmlSrc, Source transformSrc, 
                                         String desc, String goldName)
    {
        TransformerFactory factory = null;
        String media= null;     // often ignored
        String title = null;    // often ignored
        String charset = null;  // often ignored
        try
        {
            factory = TransformerFactory.newInstance();
            Source stylesheet = factory.getAssociatedStylesheet(xmlSrc, 
                                                                media, title, charset);
            Templates embedTemplates = factory.newTemplates(stylesheet);
            Transformer embedTransformer = embedTemplates.newTransformer();

            reporter.logTraceMsg("Got embedded(" + xmlSrc.getSystemId() 
                                 + "), about to transform(" + transformSrc.getSystemId() + ").");
            embedTransformer.transform(transformSrc, 
                                       new StreamResult(outNames.nextName()));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(outNames.currentName()), 
                                     new File(goldName), 
                                     desc + " into " + outNames.currentName())
               )
            {
                reporter.logInfoMsg(desc + " failure reason:" + fileChecker.getExtendedInfo());
            }
        }
        catch (Throwable t)
        {
            // We only expect transforms that work
            reporter.checkFail("Transform(" + desc + ") threw:" + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Transform(" + desc + ") threw");
        }
    }

    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by EmbeddedStylesheetTest:\n"
                + "(Note: assumes inputDir=tests\\api)\n"
                + "(Note: test is directory-dependent!)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        EmbeddedStylesheetTest app = new EmbeddedStylesheetTest();
        app.doMain(args);
    }
}
