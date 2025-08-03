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
// This file uses 2 space indents, no tabs.

/*
 *
 * TestXPathAPI.java
 *
 */
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.test.android.AndroidFileUtils;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMIterator;
import org.apache.xml.dtm.DTMManager;
import org.apache.xml.utils.PrefixResolverDefault;
import org.apache.xpath.XPathAPI;
import org.apache.xpath.objects.XObject;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;

/**
 * Basic functionality test of the public XPathAPI methods.
 * 
 * Very basic coverage/smoketest level test.
 * Applies a number of XPaths to some sample documents 
 * and checks output from the various public XPathAPI mehods.
 * @see XPathAPI
 * @author myriam_midy@lotus.com
 * @author shane_curcuru@lotus.com
 */
public class TestXPathAPI extends FileBasedTest
{
  /** Array of sample XPaths to test.  */
  protected String[] xpath;
  
  /** Base path/name of all output files.  */
  protected String baseOutName = null;

  protected XSLTestfileInfo testFileInfo1 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo2 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo3 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo4 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo5 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo6 = new XSLTestfileInfo();
  protected XSLTestfileInfo testFileInfo7 = new XSLTestfileInfo();


  /** Provides nextName(), currentName() functionality.  */
  protected OutputNameManager outNames; 

  /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String X2J_SUBDIR = "xalanj2";

    /** Just initialize test name, comment, numTestCases. */
    public TestXPathAPI()
    {
        numTestCases = 7;  // REPLACE_num
        testName = "TestXPathAPI";
        testComment = "API coverage testing of XPathAPI";
    }
    
    /**
     * Initialize this test - Set names of xml/xsl test files etc.  
     * Also initializes an array of sample xpaths to test.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + X2J_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Output name manager initialized in each testCase
        baseOutName = outputDir + File.separator + X2J_SUBDIR + File.separator + testName;
       
        String testBasePath = inputDir 
                              + File.separator 
                              + X2J_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + X2J_SUBDIR
                              + File.separator;

        // Gold names are appended onto for each test
        testFileInfo1.xmlName = testBasePath + "testXPath.xml";
        testFileInfo1.goldName = goldBasePath;

        testFileInfo2.xmlName = testBasePath + "testXPath.xml";
        testFileInfo2.goldName = goldBasePath;

        testFileInfo3.xmlName = testBasePath + "testXPath.xml";
        testFileInfo3.goldName = goldBasePath;

        testFileInfo4.xmlName = testBasePath + "testXPath.xml";
        testFileInfo4.goldName = goldBasePath;
        
        testFileInfo5.xmlName = testBasePath + "testXPath.xml";
        testFileInfo5.goldName = goldBasePath;
        
        testFileInfo6.xmlName = testBasePath + "testXPath2.xml";
        testFileInfo6.goldName = goldBasePath;
        
        testFileInfo7.xmlName = testBasePath + "testXPath3.xml";
        testFileInfo7.goldName = goldBasePath;
        
        // Initialize xpath test data
        // Note: when adding new xpaths, update array ctor and 
        //  also need to update each testCase's gold files
        xpath = new String[25];
        xpath[0] = "/doc/a/@test";
        xpath[1] = "//."; 
        xpath[2] = "/doc"; 
        xpath[3] = "/doc/a"; 
        xpath[4] = "//@*";
        xpath[5] = ".";
        xpath[6] = "//ancestor-or-self::*";
        xpath[7] = "./child::*[1]";
        xpath[8] = "//descendant-or-self::*/@*[1]";
        xpath[9] = "//@* | * | node()";
        xpath[10] = "//*";
        xpath[11] = "/doc/namespace::*";
        xpath[12] = "//descendant::comment()";        
        xpath[13] = "//*[local-name()='a']";
        xpath[14] = "//*[current()]/@*";
        xpath[15] = "//*[last()]";
        xpath[16] = "doc/*[last()]";
        xpath[17] = "/doc/a/*[current()]/@*";
        xpath[18] = "doc/descendant::node()";
        xpath[19] = "doc/a/@*";
        xpath[20] = "doc/b/a/ancestor-or-self::*";
        xpath[21] = "doc/b/a/preceding::*";
        xpath[22] = "doc/a/following::*";
        xpath[23] = "/doc/b/preceding-sibling::*";
        xpath[24] = "/doc/a/following-sibling::*";
               
        return true;
    }


  
  /** Quick test of XPathAPI.selectNodeIterator().  */
  public boolean testCase1()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectNodeIterator()");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");
    
    Document doc = parseToDOM(testFileInfo1.xmlName);
    Transformer serializer = getSerializer();
    
    for (int i=0;i<xpath.length; i++) 
    {
      // Use the simple XPath API to select a nodeIterator.
      NodeIterator nl = XPathAPI.selectNodeIterator(doc, xpath[i]);
      
      Node n;
      while ((n = nl.nextNode())!= null)
      { 
        serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
        File f = new File(outNames.currentName()); 
        fileChecker.check(reporter, 
                           f, 
                           new File(testFileInfo1.goldName + f.getName()), 
                          "selectNodeIterator() of "+xpath[i] + " into " + outNames.currentName());
      }
    } // of for...
    reporter.testCaseClose();
    return true;
  }
  
  /** Quick test of XPathAPI.selectNodeList().  */
  public boolean testCase2()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectNodeList()");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");

    // Note: parsed file and gold file don't match - why? 10-Oct-01 -sc
    Document doc = parseToDOM(testFileInfo1.xmlName);
    Transformer serializer = getSerializer();

    for (int i=0;i<xpath.length; i++) 
    {
      NodeList nl = XPathAPI.selectNodeList(doc, xpath[i]);
      
      Node n;
      int j = 0;
      while (j < nl.getLength())
      {        
        n = nl.item(j++);
        serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
        File f = new File(outNames.currentName()); 
        fileChecker.check(reporter, 
                          f, 
                          new File(testFileInfo2.goldName + f.getName()), 
                          "selectNodeList() of "+xpath[i] + " into " + outNames.currentName());
      }
    } // of for...
    reporter.testCaseClose();
    return true;
  }
  
  /** Quick test of XPathAPI.selectSingleNode().  */
  public boolean testCase3()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectSingleNode()");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");

    // Note: parsed file and gold file don't match - why? 10-Oct-01 -sc
    Document doc = parseToDOM(testFileInfo1.xmlName);
    Transformer serializer = getSerializer();
    
    for (int i=0;i<xpath.length; i++) 
    {
      Node n = XPathAPI.selectSingleNode(doc, xpath[i]);
      
      if (n != null)
      {
        serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
        File f = new File(outNames.currentName()); 
        fileChecker.check(reporter, 
                           f, 
                           new File(testFileInfo3.goldName + f.getName()), 
                          "selectSingleNode() of "+xpath[i] + " into " + outNames.currentName());
      }
      else
      {
        reporter.logWarningMsg("No node found with selectSingleNode() of "+xpath[i]);
      }
    } // of for...
    reporter.testCaseClose();
    return true;
  }
  
  
  /** Quick test of XPathAPI.eval().  */
  public boolean testCase4()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.eval()");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");

    // Note: parsed file and gold file don't match - why? 10-Oct-01 -sc
    Document doc = parseToDOM(testFileInfo1.xmlName);
    Transformer serializer = getSerializer();
    
    //Document d1 =(Document) doc.getDocumentElement();
    // Node d = (doc.getNodeType() == Node.DOCUMENT_NODE)
    // ? (Document) doc.getDocumentElement() : doc;
    reporter.logInfoMsg("Creating a PrefixResolverDefault(...)");
    PrefixResolverDefault prefixResolver = 
               new PrefixResolverDefault(doc.getDocumentElement());

    for (int i=0;i<xpath.length; i++) 
    {
      XObject list = XPathAPI.eval(doc, xpath[i], prefixResolver);
      
      int n;
      DTMIterator nl = list.iter();
      DTMManager dtmManager = nl.getDTMManager();
      while ((n = nl.nextNode())!= DTM.NULL)
      { 
        Node node = dtmManager.getDTM(n).getNode(n);
        serializer.transform(new DOMSource(node), new StreamResult(outNames.nextName()));
        File f = new File(outNames.currentName()); 
        fileChecker.check(reporter, 
                           f, 
                           new File(testFileInfo4.goldName + f.getName()), 
                          "eval() of "+xpath[i] + " into " + outNames.currentName());
      }
    } // of for...
    reporter.testCaseClose();
    return true;
  }
  
  /** Quick test of XPathAPI.selectNodeList() and doc.getFirstChild().  */
  public boolean testCase5()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectNodeList() and doc.getFirstChild()");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");

    Document doc = parseToDOM(testFileInfo5.xmlName);
    Transformer serializer = getSerializer();
    
    // Use the simple XPath API to select a nodeIterator.
    reporter.logStatusMsg("Querying DOM using selectNodeList(doc.getFirstChild(), 'a')");
    NodeList nl = XPathAPI.selectNodeList(doc.getFirstChild(), "a");
    
    Node n;
    int j = 0;
    while (j < nl.getLength())
    {        
      n = nl.item(j++);
      serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
      File f = new File(outNames.currentName()); 
      fileChecker.check(reporter, 
                        f, 
                        new File(testFileInfo5.goldName + f.getName()), 
                        "selectNodeList(doc.getFirstChild(), 'a') into " + outNames.currentName());
    }
    reporter.testCaseClose();
    return true;
  }
  
  /** Quick test of XPathAPI.selectNodeIterator() and non-document node.  */
  public boolean testCase6()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectNodeIterator() and non-document node");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");
    String filename = testFileInfo6.xmlName;
    String xpathStr = "*[local-name()='sitemap' and namespace-uri()='http://apache.org/xalan/test/sitemap']"; 

    // Set up a DOM tree to query.
    reporter.logInfoMsg("Parsing input file "+filename);
    // Android-added: Look up the file in the java resources.
    // InputSource in = new InputSource(new FileInputStream(filename));
    InputSource in = new InputSource(AndroidFileUtils.getInputFileUrl(filename).openStream());
    DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
    dfactory.setNamespaceAware(true);
    Document doc = dfactory.newDocumentBuilder().parse(in);

    Transformer serializer = getSerializer();
    
    // Create DocumentFragment
    DocumentFragment frag = doc.createDocumentFragment();
    frag.appendChild(doc.getFirstChild());
    
    // Use the simple XPath API to select a nodeIterator.
    reporter.logStatusMsg("selectNodeIterator(" + xpathStr + ") and a non document node");
    NodeIterator nl = XPathAPI.selectNodeIterator(frag, xpathStr);
    
    Node n;
    while ((n = nl.nextNode())!= null)
    {        
      serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
      File f = new File(outNames.currentName()); 
      fileChecker.check(reporter, 
                        f, 
                        new File(testFileInfo6.goldName + f.getName()), 
                        "selectNodeIterator(...) into " + outNames.currentName());
    }
    reporter.testCaseClose();
    return true;
  }
  
  /** Quick test of XPathAPI.selectNodeList using 'id(a)'.  */
  public boolean testCase7()
    throws Exception
  {        
    reporter.testCaseInit("Quick test of XPathAPI.selectNodeList using 'id(a)'");
    outNames = new OutputNameManager(baseOutName + reporter.getCurrentCaseNum(), ".out");

    // Note: parsed file and gold file don't match - why? 10-Oct-01 -sc
    Document doc = parseToDOM(testFileInfo7.xmlName);
    Transformer serializer = getSerializer();
    
    // Use the simple XPath API to select a nodeIterator.
    reporter.logStatusMsg("selectNodeList using 'id(a)' ");
    NodeList nl = XPathAPI.selectNodeList(doc, "id('a')");
    
    Node n;
    int j = 0;
    while (j < nl.getLength())
    {        
      n = nl.item(j++);
      serializer.transform(new DOMSource(n), new StreamResult(outNames.nextName()));
      File f = new File(outNames.currentName()); 
      fileChecker.check(reporter, 
                        f, 
                        new File(testFileInfo5.goldName + f.getName()), 
                        "selectNodeList using 'id(a)' into " + outNames.currentName());
    }
    reporter.testCaseClose();
    return true;
  }

  /** Worker method to return a transformer for serializing.  */
  protected Transformer getSerializer() throws TransformerException
  {
    // Set up an identity transformer to use as serializer.
    Transformer serializer = TransformerFactory.newInstance().newTransformer();
    serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    return serializer;
  }
  
  /** Worker method to parse file into a DOM.  */
  protected Document parseToDOM(String filename) throws Exception
  {
    reporter.logInfoMsg("Parsing input file "+filename);
    
    // Set up a DOM tree to query.
    // Android-added: Look up the file in the java resources.
    // InputSource in = new InputSource(new FileInputStream(filename));
    InputSource in = new InputSource(AndroidFileUtils.getInputFileUrl(filename).openStream());
    DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
    Document doc = dfactory.newDocumentBuilder().parse(in);
    return doc;
  }

  //-----------------------------------------------------------
  //---- Basic XSLProcessorTestBase utility methods
  //-----------------------------------------------------------
  /**
   * Convenience method to print out usage information - update if needed.  
   * @return String denoting usage of this test class
   */
  public String usage()
  {
    return ("Common [optional] options supported by TestXPathAPI:\n"
            + "(Note: assumes inputDir=.\\tests\\api)\n"
            + super.usage());   // Grab our parent classes usage as well
  }
  
  
  /** Main method to run from the command line.    */
  public static void main (String[] args)
  {
    TestXPathAPI app = new TestXPathAPI();
    app.doMain(args);
  }

  // Android-added: Run main method as a JUnit test case.
  @FlakyTest(bugId = 292520220)
  @Test
  public void main() {
    main(new String[0]);
  }
} // end of class ApplyXPath

