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
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.StringReader;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.qetest.FileBasedTest;
import org.apache.xml.utils.PrefixResolver;
import org.apache.xml.utils.PrefixResolverDefault;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

//-------------------------------------------------------------------------

/**
 * Functionality/system/integration tests for PrefixResolver.
 *
 * Very simple coverage test.
 * 
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class PrefixResolverAPITest extends FileBasedTest
{
    /** Just initialize test name, comment, numTestCases. */
    public PrefixResolverAPITest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "PrefixResolverAPITest";
        testComment = "Functionality/system/integration tests for PrefixResolver";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        /* no-op */
        return true;
    }

    /** XML prefixes and namespaces in our DOM to test.  */
    protected static String[][] XMLDOC_PREFIXES = 
    {
        { "", "urn://doc.level.attr/xmlns" } , /* default ns */
        { "pre", "urn://doc.level.attr/preNS" }, 
        { "other", "urn://doc.level.attr/otherNS" }, 
        { "subNS", "urn://doc.subElement/subNS" }/* ns only on subElement */
    };

    /** XML string for our DOM to test.  */
    protected static String XMLDOC = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\n" + 
        "<pre:document " + "\n" + 
        "docLevelAttr=\"urn://doc.level.attr/notNS\"" + "\n" + 
        "xmlns=\"" + XMLDOC_PREFIXES[0][1] + "\"" + "\n" + 
        "xmlns:" + XMLDOC_PREFIXES[1][0] + "=\"" + XMLDOC_PREFIXES[1][1] + "\" " + "\n" + 
        "xmlns:" + XMLDOC_PREFIXES[2][0] + "=\"" + XMLDOC_PREFIXES[2][1] + "\" " + "\n" + 
        ">" + "\n" + 
        "<pre:element elementAttr=\"elementAttrVal\" elementAttrNS=\"pre:elementAttrValNS\" xml:lang=\"en\">" + "\n" + 
        "    <pre:subElement subElementAttr=\"subElementAttrVal\" xmlns:" + XMLDOC_PREFIXES[3][0] + "=\"" + XMLDOC_PREFIXES[3][1] + "\" subElementAttrNS=\"other:subElementAttrValNS\">" + "\n" + 
        "        <pre:subSubElement />" + "\n" + 
        "    </pre:subElement>" + "\n" + 
        "</pre:element>" + "\n" + 
        "</pre:document>" ;

    /**
     * Read in hard-coded dom and try resolving some namespaces.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Read in hard-coded dom and try resolving some namespaces");

        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logArbitrary(reporter.TRACEMSG, "hardcoded XML text is:" + XMLDOC);
            reporter.logTraceMsg("parsing hardcoded XML");
            Document doc = docBuilder.parse(new InputSource(new StringReader(XMLDOC)));
            Element docElem = doc.getDocumentElement();

            // Test from the root element
            PrefixResolver docResolver = new PrefixResolverDefault(docElem);
            reporter.logStatusMsg("new PrefixResolver(" + docElem.getNodeName() + ") is: " + docResolver);
            // -1 because only subelem has last prefix available
            for (int i = 0; i < (XMLDOC_PREFIXES.length - 1); i++)
            {
                String ns = docResolver.getNamespaceForPrefix(XMLDOC_PREFIXES[i][0]);
                reporter.check(ns, XMLDOC_PREFIXES[i][1], "getNamespaceForPrefix(" + XMLDOC_PREFIXES[i][0] + ") = " + ns);
            }

            // Try again, from further down the tree (* is match any ns)
            Node elemElem = doc.getElementsByTagNameNS("*", "element").item(0);
            PrefixResolver elemResolver = new PrefixResolverDefault(elemElem);
            reporter.logStatusMsg("new PrefixResolver(" + elemElem.getNodeName() + ") is: " + elemResolver);
            // -1 because only subelem has last prefix available
            for (int i = 0; i < (XMLDOC_PREFIXES.length - 1); i++)
            {
                String ns = elemResolver.getNamespaceForPrefix(XMLDOC_PREFIXES[i][0]);
                reporter.check(ns, XMLDOC_PREFIXES[i][1], "getNamespaceForPrefix(" + XMLDOC_PREFIXES[i][0] + ") = " + ns);
            }

            // Try again, from further down the tree with additional ns (* is match any ns)
            Node subElem = doc.getElementsByTagNameNS("*", "subElement").item(0);
            PrefixResolver subResolver = new PrefixResolverDefault(subElem);
            reporter.logStatusMsg("new PrefixResolver(" + subElem.getNodeName() + ") is: " + subResolver);
            for (int i = 0; i < XMLDOC_PREFIXES.length; i++)
            {
                String ns = subResolver.getNamespaceForPrefix(XMLDOC_PREFIXES[i][0]);
                reporter.check(ns, XMLDOC_PREFIXES[i][1], "getNamespaceForPrefix(" + XMLDOC_PREFIXES[i][0] + ") = " + ns);
            }

            // Try again, from further down the tree with additional ns (* is match any ns)
            Node subSubElem = doc.getElementsByTagNameNS("*", "subSubElement").item(0);
            PrefixResolver subSubResolver = new PrefixResolverDefault(subSubElem);
            reporter.logStatusMsg("new PrefixResolver(" + subSubElem.getNodeName() + ") is: " + subSubResolver);
            for (int i = 0; i < XMLDOC_PREFIXES.length; i++)
            {
                String ns = subSubResolver.getNamespaceForPrefix(XMLDOC_PREFIXES[i][0]);
                reporter.check(ns, XMLDOC_PREFIXES[i][1], "getNamespaceForPrefix(" + XMLDOC_PREFIXES[i][0] + ") = " + ns);
            }
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "testCase1 threw");
            reporter.checkFail("testCase1 threw: " + t.toString());
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
        return ("Common [optional] options supported by PrefixResolverAPITest:\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        PrefixResolverAPITest app = new PrefixResolverAPITest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
