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
import org.apache.qetest.*;
import org.apache.qetest.trax.*;
import org.apache.qetest.xsl.*;

// Import all relevant TRAX packages
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.stream.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

// Needed SAX, DOM, JAXP classes
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.w3c.dom.Node;

// java classes
import java.io.File;
import java.io.FilenameFilter;
import java.util.Properties;


/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * @author shane_curcuru@lotus.com
 * @author wjboukni@eos.ncsu.edu
 */
public class Bugzilla1266 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla1266"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#1266");
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(logger);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        logger.logMsg(Logger.STATUSMSG, "loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            logger.logMsg(Logger.STATUSMSG, "About to factory.newTemplates(" + QetestUtils.filenameToURL("identity.xsl") + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL("identity.xsl")));
            transformer = templates.newTransformer();

            // Set the errorListener and validate it
            transformer.setErrorListener(loggingErrorListener);
            if (transformer.getErrorListener() == loggingErrorListener)
                logger.checkPass("set/getErrorListener on transformer");
            else
                logger.checkFail("set/getErrorListener on transformer");

            logger.logMsg(Logger.STATUSMSG, "Reproduce Bugzilla1266 - warning due to bad output props not propagated");
            logger.logMsg(Logger.STATUSMSG, "transformer.setOutputProperty(encoding, illegal-encoding-value)");
            transformer.setOutputProperty("encoding", "illegal-encoding-value");

            logger.logMsg(Logger.STATUSMSG, "about to transform(...)");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL("identity.xml")), 
                                  new StreamResult("Bugzilla1266.out"));
            logger.logMsg(Logger.STATUSMSG, "after transform(...)");
            logger.logMsg(Logger.STATUSMSG, "loggingErrorListener after transform:" + loggingErrorListener.getQuickCounters());

            // Validate that one warning (about illegal-encoding-value) should have been reported
            int[] errCtr = loggingErrorListener.getCounters();
            if (errCtr[LoggingErrorListener.TYPE_WARNING] > 0)
                logger.checkPass("At least one Warning listned to for illegal-encoding-value");
            else
                logger.checkFail("At least one Warning listned to for illegal-encoding-value");
                
            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            CheckService fileChecker = new XHTFileCheckService();
            fileChecker.check(logger, 
                    new File("Bugzilla1266.out"), 
                    new File("identity.gold"), 
                    "transform of good xsl w/bad output props into: " + "Bugzilla1266.out");
            
        }
        catch (Throwable t)
        {
            logger.checkFail("Bugzilla1266 unexpectedly threw: " + t.toString());
            logger.logThrowable(Logger.ERRORMSG, t, "Bugzilla1266 unexpectedly threw");
        }

	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=1266">
     * Link to Bugzilla report</a>
     * @return Warning Event not being fired from Transformer when using invalid Encoding String.
     */
    public String getDescription()
    {
        return "Warning Event not being fired from Transformer when using invalid Encoding String";
    }

}  // end of class Bugzilla1266

