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
 * DTMDumpTest.java
 *
 */
package org.apache.qetest.xalanj2;

import java.io.File;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.TraxDatalet;
import org.apache.xalan.extensions.ExpressionContext;
import org.apache.xml.dtm.ref.DTMNodeProxy;
import org.apache.xpath.NodeSet;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

//-------------------------------------------------------------------------

/**
 * Simple unit test of various DTM and related apis.  
 * This class acts as it's own Xalan extension.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class DTMDumpTest extends FileBasedTest
{

    /** Provides nextName(), currentName() functionality.   */
    protected OutputNameManager outNames;

    /** Simple test with dumpDTM extension calls in.  */
    protected TraxDatalet testFileInfo = new TraxDatalet();

    /** Just initialize test name, comment, numTestCases. */
    public DTMDumpTest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "DTMDumpTest";
        testComment = "Simple unit test of various DTM and related apis";
    }


    /**
     * Initialize this test - create output dir, outNames.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        final String XALANJ2 = "xalanj2";
        File outSubDir = new File(outputDir + File.separator + XALANJ2);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + XALANJ2
                                         + File.separator + testName, ".out");
                                         
        testFileInfo.setDescription("Simple transform with dumpDTM extension call");
        testFileInfo.setNames(inputDir + File.separator + XALANJ2, "DTMDumpTest");
        testFileInfo.goldName = goldDir + File.separator + XALANJ2 + File.separator + "DTMDumpTest.out";
        return true;
    }


    /**
     * Simple dumping of DTM info from nodes.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Simple dumping of DTM info from nodes");

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Transform a file that calls us as an extension
            templates = factory.newTemplates(testFileInfo.getXSLSource());
            transformer = templates.newTransformer();
            reporter.logInfoMsg("Before dtmBuf: " + dtmBuf.toString());
            reporter.logInfoMsg("About to create output: " + outNames.nextName()); 
            transformer.transform(testFileInfo.getXMLSource(),
                                  new StreamResult(outNames.currentName()));
            reporter.checkPass("Crash test only: returned from transform() call");
            reporter.logInfoMsg("After dtmBuf: " + dtmBuf.toString());
                
        }
        catch (Throwable t)
        {
            reporter.logThrowable(Logger.ERRORMSG, t, "Simple DTM test threw:");
            reporter.checkFail("Simple DTM test threw:");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Validate transforms with FEATURE_INCREMENTAL on/off.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Unused");
        reporter.checkPass("Unused");
        reporter.testCaseClose();
        return true;
    }


    /** Cheap way to pass info from extension methods to test.  */
    protected static StringBuffer dtmBuf = new StringBuffer();
    
    /** Cheap way to pass info from extension methods to test.  */
    protected static final String DTMBUFSEP = ";";

    /**
     * Implements a simple Xalan extension method.  
     *
     * Just a way to implement an extension and the test that calls 
     * it together in the same class.  Watch out for thread safety.
     * @param ExpressionContext from the transformer
     * @return String describing actions
     */
    public static String dumpDTM(ExpressionContext context)
    {
        Node contextNode = context.getContextNode();
        DTMNodeProxy proxy = (DTMNodeProxy)contextNode;
        dtmBuf.append(XalanDumper.dump(proxy, XalanDumper.DUMP_DEFAULT) + DTMBUFSEP);
        return XalanDumper.dump(proxy, XalanDumper.DUMP_NOIDS);
    }

    /**
     * Implements a simple Xalan extension method.  
     *
     * Just a way to implement an extension and the test that calls 
     * it together in the same class.  Watch out for thread safety.
     * @param context from the transformer
     * @param obj object to test; presumably an RTF
     * @return String describing actions
     */
    public static String dumpDTM(ExpressionContext context, Object rtf)
    {
        if (rtf instanceof NodeIterator)
        {
            NodeSet ns = new NodeSet((NodeIterator) rtf);
            Node first = ns.nextNode();
            DTMNodeProxy proxy = (DTMNodeProxy)first;
            dtmBuf.append("NI:" + XalanDumper.dump(proxy, XalanDumper.DUMP_DEFAULT) + DTMBUFSEP);
            return XalanDumper.dump(proxy, XalanDumper.DUMP_NOIDS);
        }
        else if (rtf instanceof NodeSet)
        {
            NodeSet ns = (NodeSet)rtf;
            Node first = ns.nextNode();
            DTMNodeProxy proxy = (DTMNodeProxy)first;
            dtmBuf.append("NS:" + XalanDumper.dump(proxy, XalanDumper.DUMP_DEFAULT) + DTMBUFSEP);
            return XalanDumper.dump(proxy, XalanDumper.DUMP_NOIDS);
        }
        else
        {
            dtmBuf.append("UK:" + rtf.toString() + DTMBUFSEP);
            return "UK:" + rtf.toString();
        }
    }

    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by DTMDumpTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        DTMDumpTest app = new DTMDumpTest();
        app.doMain(args);
    }
}
