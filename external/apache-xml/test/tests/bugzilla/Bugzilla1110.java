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
import java.io.StringReader;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xpath.XPath;
import org.apache.xpath.XPathAPI;
import org.apache.xpath.objects.XObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 * @author kent@hauN.org
 * @author shane_curcuru@lotus.com
 */
public class Bugzilla1110 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla1110"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla1110");
        logger.logMsg(Logger.STATUSMSG, "User reports: I expect the following program prints ' e1\n null\n'.");
        logger.logMsg(Logger.STATUSMSG, "User reports: But it actually prints ' null\n e1\n'.");
        
        try
        {
            DOMParser domp = new DOMParser();
            final String docStr = 
            "<!DOCTYPE doc []>\n"
            +"<doc>\n"
            +"   <e1>\n"
            +"      <e2/>\n"
            +"   </e1>\n"
            +"</doc>\n";

            logger.logMsg(Logger.STATUSMSG, "---- about to parse document");
            logger.logMsg(Logger.STATUSMSG, docStr);
            logger.logMsg(Logger.STATUSMSG, "----");
            domp.parse(new InputSource(new StringReader(docStr)));
            Document doc = domp.getDocument();

            final String xpathStr = "(//.)[self::e1]";
            logger.logMsg(Logger.STATUSMSG, "about to eval '" + xpathStr + "'");
            XObject xobj = XPathAPI.eval(doc, xpathStr);
            if (xobj.getType() != XObject.CLASS_NODESET) {
                logger.checkFail("XObject returned is NOT a nodeset, is:" + xobj.str());
            } else {
                NodeIterator iter = xobj.nodeset();
                logger.logMsg(Logger.STATUSMSG, "XObject returned Class is: " + iter.getClass().getName());
                logger.logMsg(Logger.STATUSMSG, "---- XObject returned value is");

                Node n = iter.nextNode();
                logger.logMsg(Logger.STATUSMSG, n == null ? " null" : " " + n.getNodeName());
                n = iter.nextNode();
                logger.logMsg(Logger.STATUSMSG, n == null ? " null" : " " + n.getNodeName());
                while ((n = iter.nextNode()) != null)
                {
                    logger.logMsg(Logger.STATUSMSG, " " + n.getNodeName());
                }
                iter.detach();
                logger.logMsg(Logger.WARNINGMSG, "NOTE: still need to validate expected error!");
            }            
        } 
        catch (Throwable t)
        {
            logger.logThrowable(Logger.ERRORMSG, t, "execute threw");
            logger.checkFail("execute threw: " + t.toString());
        }
    }

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=NNNN">
     * Link to Bugzilla report</a>
     * @return REPLACE_Bugzilla1110_description.
     */
    public String getDescription()
    {
        return "REPLACE_Bugzilla1110_description";
    }

}  // end of class Bugzilla1110

