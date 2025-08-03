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

import java.io.ByteArrayInputStream;
import org.apache.xpath.CachedXPathAPI;
import org.w3c.dom.*;
import javax.xml.parsers.*;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * @author geuerp@apache.org
 * @author shane_curcuru@us.ibm.com
 */
public class Bugzilla6329 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla6329"; }

    /**
     * The following program tries to select all nodes in the document using an
     * XPath expression but the XPath misses the CDATA section.
     * User reported output is:
     * <PRE>
     * Xerces-J 2.0.0
     * Xalan Java 2.2.0
     * 0 (DOCUMENT): [#document: null]
     * 1 (ELEMENT): [svg: null]
     * 2 (ATTRIBUTE): onload="thisInit()"
     * 3 (ATTRIBUTE): width="106.786pt"
     * 4 (ATTRIBUTE): xml:space="preserve"
     * 5 (ATTRIBUTE): org.apache.xml.dtm.ref.dom2dtm.DOM2DTMdefaultNamespaceDeclarationNode@5b699b
     * 6 (TEXT_NODE): [#text:
     * ]
     * 7 (ELEMENT): [style: null]
     * 8 (ATTRIBUTE): type="text/css"
     * 9 (ATTRIBUTE): xml:space=""
     * 10 (TEXT_NODE): [#text:
     * ]
     * 11 (TEXT_NODE): [#text:
     * ]
     * </PRE>
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#6329");

        String input =
        "<svg  width='106.786pt' xml:space='preserve' onload='thisInit()'>\n" +
        "<style type='text/css' xml:space=''>\n" +
        "<![CDATA[\n" +
        "    @font-face{font-family:'RussellSquare-Oblique';src:url(Arial.cef)}\n" +
        "]]>\n" +
        "</style>\n" +
        "</svg>\n";

        // Note: please avoid calling these directly, or at least use 
        //    reflection to find the classes: they do change with 
        //    different Xerces and Xalan builds! -sc
        //logger.logMsg(logger.STATUSMSG, org.apache.xerces.impl.Version.fVersion);
        //logger.logMsg(logger.STATUSMSG, org.apache.xalan.processor.XSLProcessorVersion.S_VERSION);

        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(input.getBytes()));
            CachedXPathAPI xp = new CachedXPathAPI();
            logger.logMsg(logger.STATUSMSG, "User case: xp.selectNodeList(doc, (//. | //@* | //namespace::*))");
            NodeList nl = xp.selectNodeList(doc, "(//. | //@* | //namespace::*)");

            for (int i = 0; i < nl.getLength(); i++) 
            {
                // logger.logMsg(logger.STATUSMSG, i + " parent: " + nl.item(i).getParentNode());
                // logger.logMsg(logger.STATUSMSG, i + " ("+org.apache.xml.security.utils.XMLUtils.getNodeTypeString(nl.item(i))+"): " + nl.item(i));
                logger.logMsg(logger.STATUSMSG, i + ": " + nl.item(i));
            }

            logger.logMsg(logger.STATUSMSG, "dave case: xp.selectNodeList(doc, (//.))");
            nl = xp.selectNodeList(doc, "(//.)");

            for (int i = 0; i < nl.getLength(); i++) 
            {
                // logger.logMsg(logger.STATUSMSG, i + " parent: " + nl.item(i).getParentNode());
                // logger.logMsg(logger.STATUSMSG, i + " ("+org.apache.xml.security.utils.XMLUtils.getNodeTypeString(nl.item(i))+"): " + nl.item(i));
                logger.logMsg(logger.STATUSMSG, i + ": " + nl.item(i));
            }


            logger.checkAmbiguous("Test needs manual validation! (But Joe hints it may be invalid)");
        } 
        catch (Throwable t)
        {
            logger.logThrowable(logger.ERRORMSG, t, "Unexpected exception");
            logger.checkErr("Unexpected exception: " + t.toString());
        }
    }

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=6329">
     * Link to Bugzilla report</a>
     * @return XPath does not catch CDATA Nodes.
     */
    public String getDescription()
    {
        return "XPath does not catch CDATA Nodes";
    }

}  // end of class Bugzilla6329

