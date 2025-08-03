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
import java.util.Properties;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.dtm.Axis;
import org.apache.xml.dtm.DTM;


public class TimeDTMIterDeep extends FileBasedTest
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
	public static final String TIME_Prefix = "TimeID_";

	int   lastNode;
	String lastNodeName;

    /** Just initialize test name, comment, numTestCases. */
    public TimeDTMIterDeep()
    {
        numTestCases = 4;
        testName = "TimeDTMIterDeep";
        testComment = "Time the creation and various axis iterations with a deep tree";
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
                              + TIME_Prefix;

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

public boolean testCase1()
{	  
	reporter.testCaseInit("Time Iteration of Descendant:: axis");
	StringBuffer buf = new StringBuffer();
	FileOutputStream fos = QeDtmUtils.openFileStream(outNames.nextName(), reporter);
	String gold = testFileInfo.goldName + "testcase1.out";

	buf.append("\nAxis is DESCENDANT");
	DTM dtm = QeDtmUtils.createDTM(0, QeDtmUtils.deepFile, buf);

	// Get various nodes to use as context nodes.
	int dtmRoot = dtm.getDocument();				// #document
	String dtmRootName = dtm.getNodeName(dtmRoot);	// Used for output
	int DNode = dtm.getFirstChild(dtmRoot);			// <Doc>
	String DNodeName = dtm.getNodeName(DNode);
	int[] rtData = {0,0,0};		// returns Iteration time, last node, number of nodes traversed 

	// Get a iterator for Descendant:: axis.
	buf.append("\n\tSTARTING from: "+ DNodeName);
	QeDtmUtils.timeAxisIterator(dtm, Axis.DESCENDANT, DNode, rtData);
	buf.append("\n\tTime="+rtData[0] + " : " + "LastNode="+rtData[1]+" nodes="+rtData[2]);
	
	// Write results and close output file.
	QeDtmUtils.writeClose(fos, buf, reporter);

    // Verify results
    fileChecker.check(reporter, new File(outNames.currentName()),
       							new File(gold),
       							"Testcase1"); 
    reporter.testCaseClose();
    return true;

}

public boolean testCase2()
{	  
	reporter.testCaseInit("Time Iteration of DESCENDANT-OR-SELF:: axis");
	StringBuffer buf = new StringBuffer();
	FileOutputStream fos = QeDtmUtils.openFileStream(outNames.nextName(), reporter);
	String gold = testFileInfo.goldName + "testcase2.out";

	buf.append("\nAxis is DESCENDANT-OR-SELF");
	DTM dtm = QeDtmUtils.createDTM(0, QeDtmUtils.deepFile, buf);

	// Get various nodes to use as context nodes.
	int dtmRoot = dtm.getDocument();				// #document
	String dtmRootName = dtm.getNodeName(dtmRoot);	// Used for output
	int DNode = dtm.getFirstChild(dtmRoot);			// <Doc>
	String DNodeName = dtm.getNodeName(DNode);
	int[] rtData = {0,0,0};		// returns Iteration time, last node, number of nodes traversed 

	// Get a iterator for Descendant:: axis.
	buf.append("\n\tSTARTING from: "+ DNodeName);
	QeDtmUtils.timeAxisIterator(dtm, Axis.DESCENDANTORSELF, DNode, rtData);
	buf.append("\n\tTime="+rtData[0] + " : " + "LastNode="+rtData[1]+" nodes="+rtData[2]);
	lastNode = rtData[1];
	lastNodeName = dtm.getNodeName(lastNode);

	// Write results and close output file.
	QeDtmUtils.writeClose(fos, buf, reporter);

    // Verify results
    fileChecker.check(reporter, new File(outNames.currentName()),
       							new File(gold),
       							"Testcase2"); 
    reporter.testCaseClose();
    return true;

}

public boolean testCase3()
{	  
	reporter.testCaseInit("Time Iteration of ANCESTOR:: axis");
	StringBuffer buf = new StringBuffer();
	FileOutputStream fos = QeDtmUtils.openFileStream(outNames.nextName(), reporter);
	String gold = testFileInfo.goldName + "testcase3.out";

	buf.append("\nAxis is ANCESTOR");
	DTM dtm = QeDtmUtils.createDTM(0, QeDtmUtils.deepFile, buf);
	buf.append("\n\tSTARTING from: "+ lastNodeName);
	int[] rtData = {0,0,0};		// returns Iteration time, last node, number of nodes traversed 

	// Get a iterator for ANCESTOR:: axis.
	QeDtmUtils.timeAxisIterator(dtm, Axis.ANCESTOR, lastNode, rtData);
	buf.append("\n\tTime="+rtData[0] + " : " + "LastNode="+rtData[1]+" nodes="+rtData[2]);
	
	// Write results and close output file.
	QeDtmUtils.writeClose(fos, buf, reporter);

    // Verify results
    fileChecker.check(reporter, new File(outNames.currentName()),
       							new File(gold),
       							"Testcase3"); 
    reporter.testCaseClose();
    return true;

}

public boolean testCase4()
{	  
	reporter.testCaseInit("Time Iteration of ANCESTOR-or-Self:: axis");
	StringBuffer buf = new StringBuffer();
	FileOutputStream fos = QeDtmUtils.openFileStream(outNames.nextName(), reporter);
	String gold = testFileInfo.goldName + "testcase4.out";

	buf.append("\nAxis is ANCESTOR-or-Self");
	DTM dtm = QeDtmUtils.createDTM(0, QeDtmUtils.deepFile, buf);
	int[] rtData = {0,0,0};		// returns Iteration time, last node, number of nodes traversed 

	// Get a iterator for ANCESTORORSELF:: axis.
	buf.append("\n\tSTARTING from: "+ lastNodeName);
	QeDtmUtils.timeAxisIterator(dtm, Axis.ANCESTORORSELF, lastNode, rtData);
	buf.append("\n\tTime="+rtData[0] + " : " + "LastNode="+rtData[1]+" nodes="+rtData[2]);
	
	// Write results and close output file.
	QeDtmUtils.writeClose(fos, buf, reporter);

    // Verify results
    fileChecker.check(reporter, new File(outNames.currentName()),
       							new File(gold),
       							"Testcase4"); 
    reporter.testCaseClose();
    return true;

}


/**
* Main method to run test from the command line - can be left alone.  
* @param args command line argument array
*/
public static void main(String[] args)
{
	TimeDTMIterDeep app = new TimeDTMIterDeep();
	app.doMain(args);
}
 
}
