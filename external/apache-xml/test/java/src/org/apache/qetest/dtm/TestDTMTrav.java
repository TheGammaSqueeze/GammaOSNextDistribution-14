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
package org.apache.qetest.dtm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.dtm.Axis;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMAxisTraverser;
import org.apache.xml.dtm.DTMManager;
import org.apache.xml.dtm.ref.DTMManagerDefault;
import org.apache.xpath.objects.XMLStringFactoryImpl;

//-------------------------------------------------------------------------

/**
* This test creates a DTM and then walks it with AxisTraversers 
* for each axis within XPATH
* - execute 'build package.trax', 'traxapitest TestDTMTrav.java'
* - a bunch of convenience variables/initializers are included, 
*   use or delete as is useful
* @author Paul Dick
* @version $Id$
*/
public class TestDTMTrav extends FileBasedTest
{
    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Information about an xsl/xml file pair for transforming.  
     * Public members include inputName (for xsl); xmlName; goldName; etc.
     * If you don't use an .xml file on disk, you don't actually need this.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String DTM_SUBDIR = "dtm";
	public static final String TRAV_Prefix = "Trav_";

	private static final String defaultSource=
 		"<?xml version=\"1.0\"?>\n"+
 		"<Document xmlns:d=\"www.d.com\" a1=\"hello\" a2=\"goodbye\">"+
 		"<!-- Default test document -->"+
 		"<?api a1=\"yes\" a2=\"no\"?>"+
 		"<A><!-- A Subtree --><B><C><D><E><F xmlns:f=\"www.f.com\" a1=\"down\" a2=\"up\"/></E></D></C></B></A>"+
 		"<Aa/><Ab/><Ac><Ac1/></Ac>"+
 		"<Ad xmlns:Ad=\"www.Ad.com\" xmlns:y=\"www.y.com\" xmlns:z=\"www.z.com\">"+
 		"<Ad1/></Ad>"+
 		"</Document>";
 
	static final String[] TYPENAME=
 	{ 	"NULL",
    	"ELEMENT",
	    "ATTRIBUTE",
   		"TEXT",
   		"CDATA_SECTION",
	    "ENTITY_REFERENCE",
	    "ENTITY",
	    "PROCESSING_INSTRUCTION",
	    "COMMENT",
	    "DOCUMENT",
	    "DOCUMENT_TYPE",
	    "DOCUMENT_FRAGMENT",
	    "NOTATION",
	    "NAMESPACE"
	};

	private int lastNode = 0;	// Set by first axis,  used by subsequent axis.
	private String lastName;

	private int lastNode2 = 0;	// Set by DESCENDANTORSELF, used in 11 & 12 
	private String lastName2;

	private int ANode = 0;		// Used in testcase 7 - 10
	private String ANodeName;

	private int CNode = 0;
	private String CNodeName;

	private int DNode = 0;
	private String DNodeName;

	private int PINode = 0;
	private String PINodeName;

	private static dtmWSStripper stripper = new dtmWSStripper();

    /** Just initialize test name, comment, numTestCases. */
    public TestDTMTrav()
    {
        numTestCases = 20;
        testName = "TestDTMTrav";
        testComment = "Function test of DTM Traversers";
    }

    /**
     * Initialize this test - Set names of xml/xsl test files,
     * REPLACE_other_test_file_init.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in dtm subdir
        File outSubDir = new File(outputDir + File.separator + DTM_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);

        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + DTM_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + DTM_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + DTM_SUBDIR
                              + File.separator
                              + TRAV_Prefix;

        //testFileInfo.inputName = testBasePath + "REPLACE_xslxml_filename.xsl";
        //testFileInfo.xmlName = testBasePath + "REPLACE_xslxml_filename.xml";
        testFileInfo.goldName = goldBasePath;

        return true;
    }

    /**
     * Cleanup this test - REPLACE_other_test_file_cleanup.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileClose(Properties p)
    {
        // Often will be a no-op
        return true;
    }

   /**
    * Create AxisTraverser and walk CHILD axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase1()
    {
		reporter.testCaseInit("Walk CHILD AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase1.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

	  	// Get various nodes to use as context nodes.
	  	int dtmRoot = dtm.getDocument();				// #document
	  	String dtmRootName = dtm.getNodeName(dtmRoot);	// Used for output
	  	DNode = dtm.getFirstChild(dtmRoot);				// <Document>
	  	DNodeName = dtm.getNodeName(DNode);
	  	CNode = dtm.getFirstChild(DNode);				// <Comment>
		CNodeName = dtm.getNodeName(CNode);
	  	PINode = dtm.getNextSibling(CNode);				// <PI>
		PINodeName = dtm.getNodeName(PINode);
	  	ANode = dtm.getNextSibling(PINode);				// <A>
	  	ANodeName = dtm.getNodeName(ANode);

		// Get a Traverser for CHILD:: axis and query it's direction.
		buf.append("#### CHILD from " + DNodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.CHILD);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(DNode); DTM.NULL != atNode; 
      	     atNode = at.next(DNode,atNode))
		{ 
			buf.append(getNodeInfo(dtm, atNode, " "));
			lastNode = atNode;			// Setting this GLOBAL IS BAD, but easy. Investigate!!
		}
		lastName = dtm.getNodeName(lastNode);

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase1"); 
        reporter.testCaseClose();
        return true;
    }

    public boolean testCase2()
    {
		reporter.testCaseInit("Walk PARENT AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase2.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for PARENT:: axis.
		buf.append("#### PARENT from " + lastName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.PARENT);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));
		 		
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase2"); 

        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk SELF axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase3()
    {
		reporter.testCaseInit("Walk SELF AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase3.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for CHILD:: axis.
		buf.append("#### SELF from " + lastName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.SELF);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode))
		  buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase3"); 

        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk NAMESPACE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase4()
    {
		reporter.testCaseInit("Walk NAMESPACE AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase4.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for NAMESPACE:: axis.
		buf.append("#### NAMESPACE from " + lastName + "\n");
		DTMAxisTraverser at = dtm.getAxisTraverser(Axis.NAMESPACE);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase4"); 

        reporter.testCaseClose();
        return true;
    }
   /**
    * Create AxisTraverser and walk NAMESPACEDECLS axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase5()
    {
		reporter.testCaseInit("Walk NAMESPACEDECLS AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase5.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for NAMESPACEDECLS:: axis.
		buf.append("#### NAMESPACEDECLS from "+ lastName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.NAMESPACEDECLS);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase5"); 

        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk PRECEDING axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase6()
    {
		reporter.testCaseInit("Walk PRECEDING AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase6.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for PRECEDING:: axis.
		buf.append("#### PRECEDING from "+ lastName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.PRECEDING);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase5"); 

        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk PRECEDINGSIBLING axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase7()
    {
		reporter.testCaseInit("Walk PRECEDINGSIBLING AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase7.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for PRECEDINGSIBLING:: axis.
		buf.append("#### PRECEDINGSIBLING from "+ lastName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.PRECEDINGSIBLING);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode); DTM.NULL != atNode; 
      			atNode = at.next(lastNode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase7"); 
        reporter.testCaseClose();
        return true;
    }


   /**
    * Create AxisTraverser and walk FOLLOWING axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase8()
    {
		reporter.testCaseInit("Walk FOLLOWING AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase8.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for FOLLOWING:: axis.
		buf.append("#### FOLLOWING from "+ ANodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.FOLLOWING);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(ANode); DTM.NULL != atNode; 
      			atNode = at.next(ANode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase8"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk FOLLOWINGSIBLING axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase9()
    {
		reporter.testCaseInit("Walk FOLLOWINGSIBLING AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase9.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for FOLLOWINGSIBLING:: axis.
      	buf.append("#### FOLLOWINGSIBLING from "+ ANodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.FOLLOWINGSIBLING);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(ANode); DTM.NULL != atNode; 
      			atNode = at.next(ANode, atNode))
		 	buf.append(getNodeInfo(dtm, atNode, " "));
		
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase9"); 
        reporter.testCaseClose();
        return true;
    }


   /**
    * Create AxisTraverser and walk DESCENDANT axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase10()
    {
		reporter.testCaseInit("Walk DESCENDANT AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase10.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for DESCENDANT:: axis.
		buf.append("#### DESCENDANT from "+ ANodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.DESCENDANT);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(ANode); DTM.NULL != atNode; 
      			atNode = at.next(ANode, atNode)) {
            buf.append(getNodeInfo(dtm, atNode, " "));
        }
		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase10"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk DESCENDANTORSELF axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase11()
    {
		reporter.testCaseInit("Walk DESCENDANTORSELF AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase11.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for DESCENDANTORSELF:: axis.
		buf.append("#### DESCENDANTORSELF from "+ ANodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.DESCENDANTORSELF);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(ANode); DTM.NULL != atNode; 
      			atNode = at.next(ANode, atNode))
	   	{
	   		buf.append(getNodeInfo(dtm, atNode, " "));
		  	lastNode2 = atNode;
		}

		lastName2 = dtm.getNodeName(lastNode2);

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase11"); 
        reporter.testCaseClose();
        return true;
    }


   /**
    * Create AxisTraverser and walk ANCESTOR axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase12()
    {
		reporter.testCaseInit("Walk ANCESTOR AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase12.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ANCESTOR:: axis.
		buf.append("#### ANCESTOR from "+ lastName2 + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ANCESTOR);
		
	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase12"); 
        reporter.testCaseClose();
        return true;
    }


   /**
    * Create AxisTraverser and walk ANCESTORORSELF axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase13()
    {
		reporter.testCaseInit("Walk ANCESTORORSELF AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase13.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ANCESTORORSELF:: axis.
		buf.append("#### ANCESTORORSELF from "+ lastName2 + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ANCESTORORSELF);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase13"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk ALLFROMNODE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase14()
    {
		reporter.testCaseInit("Walk ALLFROMNODE AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase14.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ALLFROMNODE:: axis.
		buf.append("#### ALL-FROM-NODE from "+ lastName2 + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ALLFROMNODE);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase14"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk ATTRIBUTE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase15()
    {
		reporter.testCaseInit("Walk ATTRIBUTE AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase15.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ATTRIBUTE:: axis.
		buf.append("#### ATTRIBUTE from "+ DNodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ATTRIBUTE);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(DNode); DTM.NULL != atNode; 
      			atNode = at.next(DNode, atNode))
	   		buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase15"); 
        reporter.testCaseClose();
        return true;
    }

// ABSOLUTE AXIS TESTS
// These next axis are all Absolute. They all default to the root of the dtm
// tree,  regardless of what we call first() with. 


   /**
    * Create AxisTraverser and walk ALLFROMNODE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase16()
    {
		reporter.testCaseInit("Walk ALL(absolute) AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase16.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ALLFROMNODE:: axis.
		buf.append("#### ALL(absolute) from "+ lastName2 + "(root)\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ALL);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase16"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk ALLFROMNODE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase17()
    {
		reporter.testCaseInit("Walk DESCENDANTSFROMROOT(abs) AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase17.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for DESCENDANTSFROMROOT:: axis.
		buf.append("#### DESCENDANTSFROMROOT(abs) from "+ lastName2 + "(root)\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.DESCENDANTSFROMROOT);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase17"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk DESCENDANTSORSELFFROMROOT axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase18()
    {
		reporter.testCaseInit("Walk DESCENDANTSORSELFFROMROOT(abs) AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase18.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ALLFROMNODE:: axis.
		buf.append("#### DESCENDANTSORSELFFROMROOT(abs) from "+ lastName2 + "(root)\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.DESCENDANTSORSELFFROMROOT);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(lastNode2); DTM.NULL != atNode; 
      			atNode = at.next(lastNode2, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase18"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk ALLFROMNODE axis.
    * @return false if we should abort the test; true otherwise
    */
    public boolean testCase19()
    {
		reporter.testCaseInit("Walk ALLFROMNODE AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase19.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ALLFROMNODE:: axis.
		buf.append("#### ALL-FROM-NODE from "+ ANodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ALLFROMNODE);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(ANode); DTM.NULL != atNode; 
      			atNode = at.next(ANode, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase19"); 
        reporter.testCaseClose();
        return true;
    }

   /**
    * Create AxisTraverser and walk ALLFROMNODE axis.
    * @return false if we should abort the test; true otherwise
   */
   public boolean testCase20()
    {
		reporter.testCaseInit("Walk ALLFROMNODE AxisTraverser");
		StringBuffer buf = new StringBuffer();
		FileOutputStream fos = openFileStream(outNames.nextName());
        String gold = testFileInfo.goldName + "testcase20.out";

		// Create dtm and generate initial context
		DTM dtm = generateDTM();

		// Get a Traverser for ALLFROMNODE:: axis.
		buf.append("#### ALL-FROM-NODE from "+ CNodeName + "\n");
      	DTMAxisTraverser at = dtm.getAxisTraverser(Axis.ALLFROMNODE);

	  	// Traverse the axis and write node info to output file
      	for (int atNode = at.first(CNode); DTM.NULL != atNode; 
      			atNode = at.next(CNode, atNode))
			 buf.append(getNodeInfo(dtm, atNode, " "));

		// Write results and close output file.
		writeClose(fos, buf);

        // Verify results
        fileChecker.check(reporter, new File(outNames.currentName()),
        							new File(gold),
        							"Testcase20"); 
        reporter.testCaseClose();
        return true;
    }


public String usage()
{
	return ("Common [optional] options supported by TestDTMTrav:\n"
             + "(Note: assumes inputDir=.\\tests\\api)\n");
}

// This routine generates the output file stream.
FileOutputStream openFileStream(String name)
{
	FileOutputStream fos = null;

	try
	{  fos = new FileOutputStream(name); }

	catch (Exception e)
	{  reporter.checkFail("Failure opening output file."); }

	return fos;
}

// This routine generates a new DTM for each testcase
DTM generateDTM()
{
	// Create DTM and generate initial context
	Source source = new StreamSource(new StringReader(defaultSource));
	DTMManager manager= new DTMManagerDefault().newInstance(new XMLStringFactoryImpl());
	DTM dtm=manager.getDTM(source, true, stripper, false, true);
   
	return dtm;
}

// This routine writes the results to the output file.
void writeClose(FileOutputStream fos, StringBuffer buf)
{
	// Write results and close output file.
	try
	{
               fos.write(buf.toString().getBytes("UTF-8"));
		fos.close();
	}

	catch (Exception e)
	{  reporter.checkFail("Failure writing output."); 	}
 }
    
// This routine gathers up all the important info about a node, concatenates
// in all together into a single string and returns it. 
String getNodeInfo(DTM dtm, int nodeHandle, String indent)
{
    // Formatting hack -- suppress quotes when value is null, to distinguish
    // it from "null" (JK).
	String buf = new String("null");
    String value=dtm.getNodeValue(nodeHandle);
    String vq = (value==null) ? "" : "\"";

    // Skip outputing of text nodes. In most cases they clutter the output, 
	// besides I'm only interested in the elemental structure of the dtm. 
    if( TYPENAME[dtm.getNodeType(nodeHandle)] != "TEXT" )
	{
    	buf = new String(indent+
		       nodeHandle+": "+
		       TYPENAME[dtm.getNodeType(nodeHandle)]+" "+
			   dtm.getNodeName(nodeHandle)+" "+
			   " Level=" + dtm.getLevel(nodeHandle)+" "+
		       "\tValue=" + vq + value + vq	+ "\n"
		       ); 
	}
	return buf;
}

/**
* Main method to run test from the command line - can be left alone.  
* @param args command line argument array
*/
public static void main(String[] args)
{
	TestDTMTrav app = new TestDTMTrav();
	app.doMain(args);
}

}
