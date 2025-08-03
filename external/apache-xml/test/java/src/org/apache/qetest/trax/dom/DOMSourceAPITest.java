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
 * DOMSourceAPITest.java
 *
 */
package org.apache.qetest.trax.dom;

import java.io.File;
import java.io.FileOutputStream;
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
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

//-------------------------------------------------------------------------

/**
 * API Coverage test for the DOMSource class of TRAX.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class DOMSourceAPITest extends FileBasedTest
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
    public static final String TRAX_DOM_SUBDIR = "trax" + File.separator + "dom";


    /** Just initialize test name, comment, numTestCases. */
    public DOMSourceAPITest()
    {
        numTestCases = 3;  // REPLACE_num
        testName = "DOMSourceAPITest";
        testComment = "API Coverage test for the DOMSource class of TRAX";
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
        File outSubDir = new File(outputDir + File.separator + TRAX_DOM_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_DOM_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_DOM_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_DOM_SUBDIR
                              + File.separator;

        testFileInfo.inputName = QetestUtils.filenameToURL(testBasePath + "DOMTest.xsl");
        testFileInfo.xmlName = QetestUtils.filenameToURL(testBasePath + "DOMTest.xml");
        testFileInfo.goldName = goldBasePath + "DOMTest.out";

        impInclFileInfo.inputName = QetestUtils.filenameToURL(testBasePath + "DOMImpIncl.xsl");
        impInclFileInfo.xmlName = QetestUtils.filenameToURL(testBasePath + "DOMImpIncl.xml");
        impInclFileInfo.goldName = goldBasePath + "DOMImpIncl.out";

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
     * Basic API coverage, constructor and set/get methods.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Basic API coverage, constructor and set/get methods");

        // Default no-arg ctor sets nothing (but needs special test for 
        //  creating new doc when being transformed)
        DOMSource defaultDOM = new DOMSource();
        reporter.checkObject(defaultDOM.getNode(), null, "Default DOMSource should have null Node");
        reporter.check(defaultDOM.getSystemId(), null, "Default DOMSource should have null SystemId");

        try
        {
            // ctor(Node) with a simple node
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            Node n = docBuilder.newDocument();
            DOMSource nodeDOM = new DOMSource(n);
            reporter.checkObject(nodeDOM.getNode(), n, "DOMSource(n) has Node: " + nodeDOM.getNode());
            reporter.check(nodeDOM.getSystemId(), null, "DOMSource(n) should have null SystemId");

            DOMSource nodeDOMid = new DOMSource(n, "this-is-system-id");
            reporter.checkObject(nodeDOMid.getNode(), n, "DOMSource(n,id) has Node: " + nodeDOMid.getNode());
            reporter.check(nodeDOMid.getSystemId(), "this-is-system-id", "DOMSource(n,id) has SystemId: " + nodeDOMid.getSystemId());

            DOMSource wackyDOM = new DOMSource();
            Node n2 = docBuilder.newDocument();
            wackyDOM.setNode(n2);
            reporter.checkObject(wackyDOM.getNode(), n2, "set/getNode API coverage");
            
            wackyDOM.setSystemId("another-system-id");
            reporter.checkObject(wackyDOM.getSystemId(), "another-system-id", "set/getSystemId API coverage");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with DOMSource set/get API");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with DOMSource set/get API");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Basic functionality of DOMSources.
     * Use them in simple transforms, with/without systemId set.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Basic functionality of DOMSources");

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformerXSL = null;
        DocumentBuilder docBuilder = null;
        Node xmlNode = null;
        Node xslNode = null;
        Node xslImpInclNode = null;
        Node xmlImpInclNode = null;
        try
        {
            // Startup a factory, create some nodes/DOMs
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            docBuilder = dfactory.newDocumentBuilder();
            reporter.logTraceMsg("parsing xml, xsl files");
            xslNode = docBuilder.parse(new InputSource(testFileInfo.inputName));
            xmlNode = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            xslImpInclNode = docBuilder.parse(new InputSource(impInclFileInfo.inputName));
            xmlImpInclNode = docBuilder.parse(new InputSource(impInclFileInfo.xmlName));
        }
        catch (Throwable t)
        {
            reporter.checkErr("Problem with factory; testcase may not work");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with factory; testcase may not work");
        }
        try
        {
            // A blank DOM as an input stylesheet - what should happen?
            DOMSource blankXSLDOM = new DOMSource();
            reporter.logTraceMsg("About to newTemplates(blankXSLDOM)");
            Templates blankTemplates = factory.newTemplates(blankXSLDOM); // SPR SCUU4R5JYZ throws npe; 0b29CVS now returns null
            // Note: functionality (and hopefully Javadoc too) have 
            //  been updated to make it illegal to use a DOMSource 
            //  with a null node as the XSL document -sc 18-Dec-00
            reporter.checkFail("blankXSLDOM should throw exception per Resolved bug", "SCUU4R5JYZ");
        }
        catch (Throwable t)
        {
            reporter.checkPass("blankXSLDOM should throw exception per Resolved bug", "SCUU4R5JYZ");
            reporter.logThrowable(reporter.ERRORMSG, t, "blankXSLDOM(1) should throw exception");
        }
        try
        {
            // A blank DOM as an input stylesheet - what should happen?
            DOMSource blankXSLDOM = new DOMSource();
            reporter.logTraceMsg("About to newTransformer(blankXSLDOM)");
            Transformer blankTransformer = factory.newTransformer(blankXSLDOM); // SPR SCUU4R5JYZ throws npe
            reporter.checkFail("blankXSLDOM should throw exception per Resolved bug", "SCUU4R5JYZ");
        }
        catch (Throwable t)
        {
            reporter.checkPass("blankXSLDOM should throw exception per Resolved bug", "SCUU4R5JYZ");
            reporter.logThrowable(reporter.ERRORMSG, t, "blankXSLDOM(2) should throw exception");
        }

        try
        {
            // Try to get templates, transformerXSL from node
            DOMSource xslDOM = new DOMSource(xslNode);
            templates = factory.newTemplates(xslDOM);
            reporter.check((templates != null), true, "factory.newTemplates(DOMSource) is non-null");
            transformerXSL = factory.newTransformer(xslDOM);
            transformerXSL.setErrorListener(new DefaultErrorHandler());
            reporter.check((transformerXSL != null), true, "factory.newTransformer(DOMSource) is non-null");
            
            // A simple DOM-DOM-DOM transform
            DOMSource xmlDOM = new DOMSource(xmlNode);
            Node outNode = docBuilder.newDocument();
            DOMResult outDOM = new DOMResult(outNode);
            transformerXSL.transform(xmlDOM, outDOM);
            Node gotNode = outDOM.getNode();
            reporter.check((gotNode != null), true, "transform(xmlDOM, outDOM) has non-null outNode");
            serializeDOMAndCheck(gotNode, testFileInfo.goldName, "transform(xmlDOM, outDOM)");
            reporter.logTraceMsg("@todo validate the dom in memory as well");

            // A blank DOM as source doc of the transform - should 
            // create an empty source Document using 
            // DocumentBuilder.newDocument(). 
            DOMSource blankSource = new DOMSource();
            Node emptyNode = docBuilder.newDocument();
            DOMResult emptyNodeDOM = new DOMResult(emptyNode);
            reporter.logTraceMsg("About to transform(blankSource, emptyNodeDOM)");
            transformerXSL.transform(blankSource, emptyNodeDOM); 

            reporter.check(emptyNodeDOM.getNode().toString(),"[#document: null]","transform(blankDOM, result) should create an empty document");
        }
        catch (Throwable t)
        {
            reporter.checkPass("transform(blankDOM, result) should throw exception", "SCUU4R5KLL");
            reporter.logThrowable(reporter.ERRORMSG, t, "transform(blankDOM, result) should throw exception");
        }
        try
        {
            // A blank DOM as an output of the transform - should 
            //  auto-create a source Document
            DOMSource xmlDOM = new DOMSource(xmlNode);
            Node outNode = docBuilder.newDocument();
            DOMResult outResult = new DOMResult(outNode);
            reporter.logTraceMsg("About to transform(xmlDOM, emptyResult)");
            transformerXSL.transform(xmlDOM, outResult);
            outNode = outResult.getNode();
            reporter.check((outNode != null), true, "transform(xmlDOM, outResult) has non-null node");
            serializeDOMAndCheck(outNode, testFileInfo.goldName, "transform(xmlDOM, outResult)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with transform(doms 2)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with transform(doms 2)");
        }

        try
        {
            // Setting SystemId with imports/inclues
            DOMSource xslDOM = new DOMSource(xslImpInclNode);
            // Note that inputName, xmlName are already URL'd
            xslDOM.setSystemId(impInclFileInfo.inputName);
            transformerXSL = factory.newTransformer(xslDOM);
            transformerXSL.setErrorListener(new DefaultErrorHandler());
            DOMSource xmlDOM = new DOMSource(xmlImpInclNode);
            // Do we really need to set SystemId on both XML and XSL?
            xmlDOM.setSystemId(impInclFileInfo.xmlName);
            DOMResult emptyResult = new DOMResult();
            reporter.logTraceMsg("About to transformXSLImpIncl(xmlDOM, emptyResult)");
            transformerXSL.transform(xmlDOM, emptyResult);
            Node outNode = emptyResult.getNode();
            reporter.check((outNode != null), true, "transformXSLImpIncl(xmlDOM, emptyResult) has non-null node");
            serializeDOMAndCheck(outNode, impInclFileInfo.goldName, "transformXSLImpIncl(xmlDOM, emptyResult)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with SystemId");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with SystemId");
        }
        try
        {
            // Do a transform without systemId set
            // Note: is affected by user.dir property; if we're 
            //  already in the correct place, this won't be different
            // But: most people will run from xml-xalan\test, so this should fail
            try
            {
                reporter.logStatusMsg("System.getProperty(user.dir) = " + System.getProperty("user.dir"));
            }
            catch (SecurityException e) // in case of Applet context
            {
                reporter.logTraceMsg("System.getProperty(user.dir) threw SecurityException");
            }
            DOMSource xslDOM = new DOMSource(xslImpInclNode);
            transformerXSL = factory.newTransformer(xslDOM);
            transformerXSL.setErrorListener(new DefaultErrorHandler());
            DOMSource xmlDOM = new DOMSource(xmlImpInclNode);
            DOMResult emptyResult = new DOMResult();
            reporter.logStatusMsg("About to transform without systemID; probably throws exception");
            transformerXSL.transform(xmlDOM, emptyResult);
            reporter.checkFail("The above transform should probably have thrown an exception");
        }
        catch (Throwable t)
        {
            reporter.checkPass("Transforming with include/import and wrong SystemId throws exception");
            reporter.logThrowable(reporter.ERRORMSG, t, "Transforming with include/import and wrong SystemId");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * More advanced functionality of DOMSources.
     * Re-using DOMSource objects for multiple transforms; setNode and reuse; etc.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("More advanced functionality of DOMSources");

        TransformerFactory factory = null;
        DocumentBuilder docBuilder = null;
        Node xmlNode = null;
        Node xslNode = null;
        Node xslImpInclNode = null;
        Node xmlImpInclNode = null;

        try
        {
            // Startup a factory, create some nodes/DOMs
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            docBuilder = dfactory.newDocumentBuilder();
            reporter.logTraceMsg("parsing xml, xsl files");
            xslNode = docBuilder.parse(new InputSource(testFileInfo.inputName));
            xmlNode = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            xslImpInclNode = docBuilder.parse(new InputSource(impInclFileInfo.inputName));
            xmlImpInclNode = docBuilder.parse(new InputSource(impInclFileInfo.xmlName));
        }
        catch (Throwable t)
        {
            reporter.checkErr("Problem with factory; testcase may not work");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with factory; testcase may not work");
        }
        try
        {
            // Re-use DOMSource for stylesheet
            DOMSource xmlSource1 = new DOMSource(xmlNode);
            DOMResult result1 = new DOMResult(docBuilder.newDocument());
            DOMSource xslSource = new DOMSource(xslNode);
            Transformer transformer1 = factory.newTransformer(xslSource);
            transformer1.setErrorListener(new DefaultErrorHandler());
            transformer1.transform(xmlSource1, result1);
            Node node1 = result1.getNode();
            serializeDOMAndCheck(node1, testFileInfo.goldName, "transform first time xslSource worked");
            // Use same Source for the stylesheet
            DOMSource xmlSource2 = new DOMSource(xmlNode);
            DOMResult result2 = new DOMResult(docBuilder.newDocument());
            Transformer transformer2 = factory.newTransformer(xslSource);
            transformer2.setErrorListener(new DefaultErrorHandler());
            transformer2.transform(xmlSource2, result2);
            Node node2 = result2.getNode();
            serializeDOMAndCheck(node2, testFileInfo.goldName, "transform second time xslSource worked");

            // Re-use DOMSource for XML doc; with the same stylesheet
            DOMResult result3 = new DOMResult(docBuilder.newDocument());
            Transformer transformer3 = factory.newTransformer(xslSource);
            transformer3.setErrorListener(new DefaultErrorHandler());
            transformer3.transform(xmlSource2, result3);
            Node node3 = result3.getNode();
            serializeDOMAndCheck(node3, testFileInfo.goldName, "transform reusing both xsl/xml Sources");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with reuse xslSource");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem reuse xslSource");
        }

        try
        {
            // Re-use DOMSource after setNode to different one
            DOMSource xmlSource = new DOMSource(xmlNode);
            DOMSource xslSource = new DOMSource(xslNode);
            Transformer transformer1 = factory.newTransformer(xslSource);
            transformer1.setErrorListener(new DefaultErrorHandler());
            DOMResult result1 = new DOMResult(docBuilder.newDocument());
            transformer1.transform(xmlSource, result1);
            Node node1 = result1.getNode();
            serializeDOMAndCheck(node1, testFileInfo.goldName, "transform with original Sources");

            // Use same Sources, but change Nodes for xml,xsl
            xmlSource.setNode(xmlImpInclNode);
            xmlSource.setSystemId(impInclFileInfo.xmlName);
            xslSource.setNode(xslImpInclNode);
            xslSource.setSystemId(impInclFileInfo.inputName);
            Transformer transformer2 = factory.newTransformer(xslSource);
            transformer2.setErrorListener(new DefaultErrorHandler());
            DOMResult result2 = new DOMResult(docBuilder.newDocument());
            transformer2.transform(xmlSource, result2);
            Node node2 = result2.getNode();
            serializeDOMAndCheck(node2, impInclFileInfo.goldName, "transform after xml/xslSource.setNode, setSystemId");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with reuse after setNode");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with reuse after setNode");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method to serialize DOM and fileChecker.check().  
     * @return true if pass, false otherwise
     */
    public boolean serializeDOMAndCheck(Node dom, String goldFileName, String comment)
    {
        if ((dom == null) || (goldFileName == null))
        {
            reporter.logWarningMsg("serializeDOMAndCheck of null dom or goldFileName!");
            return false;
        }
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            if (factory.getFeature(StreamResult.FEATURE))
            {
                // Use identity transformer to serialize
                Transformer identityTransformer = factory.newTransformer();
                identityTransformer.setErrorListener(new DefaultErrorHandler());
                String outName = outNames.nextName();
                FileOutputStream fos = new FileOutputStream(outName);
                StreamResult streamResult = new StreamResult(fos);
                DOMSource nodeSource = new DOMSource(dom);
                reporter.logTraceMsg("serializeDOMAndCheck() into " + outNames.currentName());
                identityTransformer.transform(nodeSource, streamResult);
                fos.close();
                fileChecker.check(reporter, 
                                  new File(outNames.currentName()), 
                                  new File(goldFileName), 
                                  comment + " into " + outNames.currentName()+" gold is "+
                                  goldFileName+" xsl is identity transform");
                return true;    // Note: should check return from fileChecker.check!
            }
            else
            {   // We should try another method to serialize the data
                reporter.logWarningMsg("getFeature(StreamResult.FEATURE), can't validate serialized data");
                return false;
            }
            
        }
        catch (Throwable t)
        {
            reporter.checkFail("serializeDOMAndCheckFile threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "serializeDOMAndCheckFile threw:");
            return false;
        }
    }


    /**
     * Worker method to create factory objects.  
     * Changes caller's copy of passed arguments; passes-through
     * any underlying exceptions; dfactory.setNamespaceAware(true)
     */
    public void createFactoryAndDocBuilder(TransformerFactory f, DocumentBuilder d)
        throws Exception
    {
        f = TransformerFactory.newInstance();
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        d = dfactory.newDocumentBuilder();
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by DOMSourceAPITest:\n"
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

        DOMSourceAPITest app = new DOMSourceAPITest();

        app.doMain(args);
    }
}
