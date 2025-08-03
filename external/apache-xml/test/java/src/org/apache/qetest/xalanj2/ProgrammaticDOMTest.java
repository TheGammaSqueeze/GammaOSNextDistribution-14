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
 * ProgrammaticDOMTest.java
 *
 */
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

//-------------------------------------------------------------------------

/**
 * Functionality/system/integration tests for DOMSource.
 * Various kinds of DOM elements, documents used.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ProgrammaticDOMTest extends FileBasedTest
{

    /** Provides nextName(), currentName() functionality.  */
    protected OutputNameManager outNames;

    /** Simple DOMTest.xml/xsl file pair.  */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = File.separator + "trax" + File.separator;

    private static final String xslNamespace = "http://www.w3.org/1999/XSL/Transform";
    private static final String nsNamespace = "http://www.w3.org/XML/1998/namespace";

    /** Just initialize test name, comment, numTestCases. */
    public ProgrammaticDOMTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "ProgrammaticDOMTest";
        testComment = "Functionality/system/integration tests for DOMSource";
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

        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(DOMSource.FEATURE)
                  && tf.getFeature(DOMResult.FEATURE)))
            {   // The rest of this test relies on DOM
                reporter.logErrorMsg("DOM*.FEATURE not supported! Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; Some tests may be invalid!");
        }

        return true;
    }


    /**
     * Pass various forms of XML DOM's to a transform.
     * Reproduce Bugzilla 1361.
     * http://nagoya.apache.org/bugzilla/show_bug.cgi?id=1361
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Pass various forms of XML DOM's to a transform");

        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logTraceMsg("parsing xml, xsl files");
            Document xslDoc = docBuilder.parse(new InputSource(testFileInfo.inputName));
            Document xmlDoc = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            TransformerFactory factory = TransformerFactory.newInstance();

            // Try a transform with XSL Document and XML Document (common usage)
            Templates templates = factory.newTemplates(new DOMSource(xslDoc));
            Transformer transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xmlDoc,...) into " + outNames.currentName());

            // Programmatically build the XML file into a Document and transform
            Document xmlBuiltDoc = docBuilder.newDocument();
            appendIdentityDOMXML(xmlBuiltDoc, xmlBuiltDoc, true);
            transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlBuiltDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xmlBuiltDoc,...) into " + outNames.currentName());

            // Again, with identity transformer
            transformer = factory.newTransformer();
            reporter.logInfoMsg("About to identityTransform(xmlBuiltDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "identityTransform(xmlBuiltDoc,...) into " + outNames.currentName());


            // Programmatically build the XML file into a DocFrag and transform
            xmlBuiltDoc = docBuilder.newDocument();
            DocumentFragment xmlBuiltDocFrag = xmlBuiltDoc.createDocumentFragment();
            appendIdentityDOMXML(xmlBuiltDocFrag, xmlBuiltDoc, true);
            transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlBuiltDocFrag, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltDocFrag), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xmlBuiltDocFrag,...) into " + outNames.currentName());

            // Again, with identity transformer
            transformer = factory.newTransformer();
            reporter.logInfoMsg("About to identityTransform(xmlBuiltDocFrag, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltDocFrag), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "identityTransform(xmlBuiltDocFrag,...) into " + outNames.currentName());


            // Programmatically build the XML file into an Element and transform
            xmlBuiltDoc = docBuilder.newDocument();
            // Note: Here, we implicitly already have the outer list 
            //  element, so ensure the worker method doesn't add again
            Element xmlBuiltElem = xmlBuiltDoc.createElement("list");
            appendIdentityDOMXML(xmlBuiltElem, xmlBuiltDoc, false);
            transformer = templates.newTransformer();
            reporter.logInfoMsg("About to transform(xmlBuiltElem, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltElem), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xmlBuiltElem,...) into " + outNames.currentName());

            // Again, with identity transformer
            transformer = factory.newTransformer();
            reporter.logInfoMsg("About to identityTransform(xmlBuiltElem, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlBuiltElem), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "identityTransform(xmlBuiltElem,...) into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with various XML elems/documents");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with various XML elems/documents");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Build a stylesheet DOM programmatically and use it.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Build a stylesheet DOM programmatically and use it");

        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logTraceMsg("parsing xml file");
            Document xmlDoc = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = null;
 
            // Programmatically build the XSL file into a Document and transform
            Document xslBuiltDoc = docBuilder.newDocument();
            appendIdentityDOMXSL(xslBuiltDoc, xslBuiltDoc, true);
            // For debugging, write the generated stylesheet out
            //  Note this will not textually exactly match the identity.xsl file
            reporter.logInfoMsg("Writing out xslBuiltDoc to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltDoc), new StreamResult(outNames.currentName()));

            reporter.logInfoMsg("About to newTransformer(xslBuiltDoc)");
            transformer = factory.newTransformer(new DOMSource(xslBuiltDoc));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltDoc,...) into " + outNames.currentName());


            // Programmatically build the XSL file into a DocFrag and transform
            xslBuiltDoc = docBuilder.newDocument();
            DocumentFragment xslBuiltDocFrag = xslBuiltDoc.createDocumentFragment();
            appendIdentityDOMXSL(xslBuiltDocFrag, xslBuiltDoc, true);
            // For debugging, write the generated stylesheet out
            reporter.logInfoMsg("Writing out xslBuiltDocFrag to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltDocFrag), new StreamResult(outNames.currentName()));

            reporter.logCriticalMsg("//@todo Verify that this is even a valid operation!");
            reporter.logCriticalMsg("Bugzilla#5133 NPE below MOVED to SmoketestOuttakes.java 27-Nov-01 -sc");
/* @todo Bugzilla#5133 NPE below MOVED to SmoketestOuttakes.java 27-Nov-01 -sc
// Check that the DOM is actually correct, esp namespace nodes on top level
// java.lang.NullPointerException
//	at org.apache.xalan.transformer.TransformerImpl.createResultContentHandler(TransformerImpl.java, Compiled Code)


            reporter.logInfoMsg("About to newTransformer(xslBuiltDocFrag)");
            transformer = factory.newTransformer(new DOMSource(xslBuiltDocFrag));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltDocFrag,...) into " + outNames.currentName());
** @todo Bugzilla#5133 NPE above MOVED to SmoketestOuttakes.java 27-Nov-01 -sc */
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with various XSL1 elems/documents");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with various XSL1 elems/documents");
        }

/* @todo Bugzilla#5133 DOM003 Namespace error below MOVED to SmoketestOuttakes.java 27-Nov-01 -sc
//org.w3c.dom.DOMException: DOM003 Namespace error
//	at org.apache.xerces.dom.AttrNSImpl.&lt;init&gt;(AttrNSImpl.java:134)
//	at org.apache.xerces.dom.CoreDocumentImpl.createAttributeNS(CoreDocumentImpl.java:1363)
//	at org.apache.xerces.dom.ElementImpl.setAttributeNS(ElementImpl.java:596)
//	at org.apache.qetest.xalanj2.ProgrammaticDOMTest.testCase2(ProgrammaticDOMTest.java:355)

        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logTraceMsg("parsing xml file");
            Document xmlDoc = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = null;

            // Programmatically build the XSL file into an Element and transform
            Document xslBuiltDoc = docBuilder.newDocument();
            // Note: Here, we implicitly already have the outer list 
            //  element, so ensure the worker method doesn't add again
            Element xslBuiltElem = xslBuiltDoc.createElementNS(xslNamespace, "xsl:stylesheet");
            xslBuiltElem.setAttributeNS(null, "version", "1.0");
            xslBuiltElem.setAttributeNS(nsNamespace, "xmlns:xsl", xslNamespace);
            appendIdentityDOMXSL(xslBuiltElem, xslBuiltDoc, false);
            // For debugging, write the generated stylesheet out
            reporter.logInfoMsg("Writing out xslBuiltElem to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltElem), new StreamResult(outNames.currentName()));

            reporter.logCriticalMsg("//@todo Verify that this is even a valid operation!");
            reporter.logInfoMsg("About to newTransformer(xslBuiltElem)");
            transformer = factory.newTransformer(new DOMSource(xslBuiltElem));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltElem,...) into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with various XSL2 elems/documents");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with various XSL2 elems/documents");
        }
** @todo Bugzilla#5133 DOM003 Namespace error above MOVED to SmoketestOuttakes.java 27-Nov-01 -sc */

        reporter.testCaseClose();
        return true;
    }


    /**
     * Adds identity.xml elems to Node passed in.  
     * Subject to change; hackish for now
     * @author curcuru
     * @param n Node to append DOM elems to
     * @param factory Document providing createElement, etc. services
     * @param useOuterElem if we should append the top-level <list> elem
     */
    public void appendIdentityDOMXML(Node n, Document factory, boolean useOuterElem)
    {
        try
        {
            Node container = null;
            if (useOuterElem)
            {
                // If asked to, create and append top-level <list>
                container = factory.createElement("list");
                n.appendChild(container);
            }
            else
            {
                // Otherwise, just use their Node
                container = n;
            }
            container.appendChild(factory.createTextNode("\n  "));

            Element elemItem = factory.createElement("item");
            elemItem.appendChild(factory.createTextNode("Xalan-J 1.x"));
            container.appendChild(elemItem);
            container.appendChild(factory.createTextNode("\n  "));

            elemItem = factory.createElement("item");
            elemItem.appendChild(factory.createTextNode("Xalan-J 2.x"));
            container.appendChild(elemItem);
            container.appendChild(factory.createTextNode("\n  "));

            elemItem = factory.createElement("item");
            elemItem.appendChild(factory.createTextNode("Xalan-C 1.x"));
            container.appendChild(elemItem);
            container.appendChild(factory.createTextNode("\n  "));

            Element elemInnerList = factory.createElement("list");
            container.appendChild(elemInnerList);
            elemInnerList.appendChild(factory.createTextNode("\n    "));

            elemItem = factory.createElement("item");
            elemItem.appendChild(factory.createTextNode("Xalan documentation"));
            elemInnerList.appendChild(elemItem);
            elemInnerList.appendChild(factory.createTextNode("\n    "));

            elemItem = factory.createElement("item");
            elemItem.appendChild(factory.createTextNode("Xalan tests"));
            elemInnerList.appendChild(elemItem);
            elemInnerList.appendChild(factory.createTextNode("\n  "));

            container.appendChild(factory.createTextNode("\n"));
        }
        catch (Exception e)
        {
            reporter.logErrorMsg("appendDOMTestXML threw: " + e.toString());
            reporter.logThrowable(Logger.ERRORMSG, e, "appendDOMTestXML threw");
        }
    }    


    /**
     * Adds identity.xsl elems to Node passed in.  
     * Subject to change; hackish for now
     * @author curcuru
     * @param n Node to append DOM elems to
     * @param factory Document providing createElement, etc. services
     * @param useOuterElem if we should append the top-level <stylesheet> elem
     */
    public void appendIdentityDOMXSL(Node n, Document factory, boolean useOuterElem)
    {
        try
        {
            /// <xsl:template match="@*|node()">
            Element template = factory.createElementNS(xslNamespace, "xsl:template");
            template.setAttributeNS(null, "match", "@*|node()");

            /// <xsl:copy>
            Element copyElem = factory.createElementNS(xslNamespace, "xsl:copy");

            /// <xsl:apply-templates select="@*|node()"/>
            Element applyTemplatesElem = factory.createElementNS(xslNamespace, "xsl:apply-templates");
            applyTemplatesElem.setAttributeNS(null, "select", "@*|node()");

            // Stick it all together with faked-up newlines for readability
            copyElem.appendChild(factory.createTextNode("\n    "));
            copyElem.appendChild(applyTemplatesElem);
            copyElem.appendChild(factory.createTextNode("\n  "));

            template.appendChild(factory.createTextNode("\n  "));
            template.appendChild(copyElem);
            template.appendChild(factory.createTextNode("\n"));


            if (useOuterElem)
            {
                // If asked to, create and append top-level <stylesheet> elem
                /// <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
                Element stylesheetElem = factory.createElementNS(xslNamespace, "xsl:stylesheet");
                stylesheetElem.setAttributeNS(null, "version", "1.0");

                // Following is not officially needed by the DOM,  but may help 
                // less-sophisticated DOM readers downstream
                // Removed due to DOM003 Namespace error
                // stylesheetElem.setAttributeNS(nsNamespace, "xmlns:xsl", xslNamespace);
                stylesheetElem.appendChild(template);
                n.appendChild(stylesheetElem);
            }
            else
            {
                // Otherwise, just use their Node
                n.appendChild(template);
            }

        }
        catch (Exception e)
        {
            reporter.logErrorMsg("appendIdentityDOMXSL threw: " + e.toString());
            reporter.logThrowable(Logger.ERRORMSG, e, "appendIdentityDOMXSL threw");
        }
    }    


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by ProgrammaticDOMTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + "REPLACE_any_new_test_arguments\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        ProgrammaticDOMTest app = new ProgrammaticDOMTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
