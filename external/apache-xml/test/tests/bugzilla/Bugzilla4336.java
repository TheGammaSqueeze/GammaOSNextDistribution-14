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
// Common Qetest / Xalan testing imports
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.TestletImpl;

// REPLACE_imports needed for reproducing the bug
import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;
import org.apache.xpath.XPathAPI;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * @author geuer-pollmann@nue.et-inf.uni-siegen.de
 */
public class Bugzilla4336 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla4336"; }

    static final String _nodeSetInput1 = "<?xml version=\"1.0\"?>\n"
                                        + "<!DOCTYPE doc [\n"
                                        + "<!ELEMENT doc (n+)>\n"
                                        + "<!ELEMENT n (#PCDATA)>\n" + "]>\n"
                                        + "<!-- full document with decl -->"
                                        + "<doc><n>1</n></doc>";

    static final String _xpath = "(.//. | .//@* | .//namespace::*)";
    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#4336: Xalan 2.2.D11 adds a strange Attribute");

        // Comment out ref to Xerces 1.x class for upcoming Xerces 2.x checkin
        // When running via 'build bugzilla.classes bugzilla', the Xerces 
        //  version will already be reported by the test harness
        // logger.logMsg(Logger.STATUSMSG, "Apache Xerces "
        //              + org.apache.xerces.framework.Version.fVersion);
        logger.logMsg(Logger.STATUSMSG, "Apache Xalan  "
                      + org.apache.xalan.Version.getVersion());
        try
        {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();

            dfactory.setValidating(false);
            dfactory.setNamespaceAware(true);

            DocumentBuilder db = dfactory.newDocumentBuilder();
            Document document =
            db.parse(new ByteArrayInputStream(_nodeSetInput1.getBytes()));
            NodeList nl = XPathAPI.selectNodeList(document, _xpath);

            for (int i = 0; i < nl.getLength(); i++) 
            {
                logger.logMsg(Logger.STATUSMSG, i + " " 
                              + getNodeTypeString(nl.item(i)) + " " + nl.item(i));

                if (nl.item(i).getNodeType() == Node.ATTRIBUTE_NODE) 
                {
                    Attr a = (Attr) nl.item(i);

                    logger.logMsg(Logger.STATUSMSG, i + " " + a.getNodeName() + " "
                                       + a.getNodeValue());
                    logger.logMsg(Logger.STATUSMSG, i + " specified " + a.getSpecified());
                    logger.logMsg(Logger.STATUSMSG, i + " owner document: " + a.getOwnerDocument());
                }
            }
        } 
        catch (Throwable t)
        {
            logger.logThrowable(Logger.ERRORMSG, t, "Iterating document");
            logger.checkFail("Iterating document threw: " + t.toString());
        }
   	}

    static String[] nodeTypeString = new String[]{ "", "ELEMENT", "ATTRIBUTE",
                                      "TEXT_NODE", "CDATA_SECTION",
                                      "ENTITY_REFERENCE", "ENTITY",
                                      "PROCESSING_INSTRUCTION",
                                      "COMMENT", "DOCUMENT",
                                      "DOCUMENT_TYPE",
                                      "DOCUMENT_FRAGMENT",
                                      "NOTATION" };

    public static String getNodeTypeString(short nodeType) 
    {
        if ((nodeType > 0) && (nodeType < 13)) 
        {
            return nodeTypeString[nodeType];
        } else 
        {
            return "";
        }
    }

    public static String getNodeTypeString(Node n) 
    {
        return getNodeTypeString(n.getNodeType());
    }

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=4336">
     * Link to Bugzilla report</a>
     * @return Xalan 2.2.D11 adds a strange Attribute.
     */
    public String getDescription()
    {
        return "Xalan 2.2.D11 adds a strange Attribute";
    }

}  // end of class Bugzilla4336

