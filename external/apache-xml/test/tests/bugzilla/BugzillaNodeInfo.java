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
import org.apache.qetest.CheckService;
import org.apache.qetest.xsl.XHTFileCheckService;

import org.apache.xalan.processor.TransformerFactoryImpl;
import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xalan.transformer.XalanProperties;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Custom Testlet for testing NodeInfo extension.
 *
 * @author shane_curcuru@us.ibm.com
 */
public class BugzillaNodeInfo extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "BugzillaNodeInfo"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, getDescription());

        String xslName = "BugzillaNodeInfo.xsl";
        String xmlName = "BugzillaNodeInfo.xml";
        String outputName = "BugzillaNodeInfo.output";
        String goldName = "BugzillaNodeInfo.gold";
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            // Must set on both the factory impl..
            TransformerFactoryImpl factoryImpl = (TransformerFactoryImpl)factory;
            factoryImpl.setAttribute(XalanProperties.SOURCE_LOCATION, Boolean.TRUE);
            
            Transformer transformer = factory.newTransformer(new StreamSource(xslName));
            // ..and the transformer impl
            TransformerImpl impl = ((TransformerImpl) transformer);
            impl.setProperty(XalanProperties.SOURCE_LOCATION, Boolean.TRUE);
            transformer.transform(new StreamSource(xmlName), 
                                  new StreamResult(outputName));
        } 
        catch (Throwable t)
        {
            logger.logThrowable(logger.ERRORMSG, t, "Unexpectedly threw");
            logger.checkErr("Unexpectedly threw: " + t.toString());
            return;
        }    
    
        // CheckService fileChecker = new XHTFileCheckService();
        // fileChecker.check(logger,
        //                   new File(outputName), 
        //                   new File(goldName), 
        //                  getDescription());
        // Since the gold data isn't nailed down yet, do a simple validation
        // Just ensure the systemId of the xml file was included somewhere in the file
        checkFileContains(outputName, xmlName, "NodeInfo got partial systemID correct");
	}


    /**
     * Checks and reports if a file contains a certain string 
     * (all within one line).
     *
     * @param fName local path/name of file to check
     * @param checkStr String to look for in the file
     * @param comment to log with the check() call
     * @return true if pass, false otherwise
     */
    protected boolean checkFileContains(String fName, String checkStr,
                                        String comment)
    {
        boolean passFail = false;
        File f = new File(fName);

        if (!f.exists())
        {
            logger.checkFail("checkFileContains(" + fName
                               + ") does not exist: " + comment);
            return false;
        }

        try
        {
            FileReader fr = new FileReader(f);
            BufferedReader br = new BufferedReader(fr);

            for (;;)
            {
                String inbuf = br.readLine();
                if (inbuf == null)
                    break;

                if (inbuf.indexOf(checkStr) >= 0)
                {
                    passFail = true;
                    logger.logMsg(logger.TRACEMSG, 
                        "checkFileContains passes with line: " + inbuf);
                    break;
                }
            }
        }
        catch (IOException ioe)
        {
            logger.checkFail("checkFileContains(" + fName + ") threw: "
                               + ioe.toString() + " for: " + comment);

            return false;
        }

        if (passFail)
        {
            logger.checkPass(comment);
        }
        else
        {
            logger.logMsg(logger.ERRORMSG, "checkFileContains failed to find: " + checkStr);
            logger.checkFail(comment);
        }
        return passFail;
    }


    /**
     * @return Xalan custom extension NodeInfo.
     */
    public String getDescription()
    {
        return "Xalan custom extension NodeInfo";
    }

}  // end of class BugzillaNodeInfo

