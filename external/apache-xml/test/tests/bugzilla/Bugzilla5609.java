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
import org.apache.qetest.CheckService;
import org.apache.qetest.xsl.XHTFileCheckService;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.File;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 * @author howejr77@yahoo.com
 * @author shane_curcuru@us.ibm.com
 */
public class Bugzilla5609 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla5609"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#5609: Global Variable Initialization across Multiple Transformations");
        CheckService fileChecker = new XHTFileCheckService();
        try
        {
            // Reproduce bug as-is: re-using transformer with global variable decl uses wrong value
            TransformerFactory factory = TransformerFactory.newInstance();
            logger.logMsg(Logger.STATUSMSG, "About to newTransformer(Bugzilla5609.xsl)");
            Transformer transformer = factory.newTransformer(new StreamSource(new File("Bugzilla5609.xsl")));
            logger.logMsg(Logger.STATUSMSG, "About to transform#1 Bugzilla5609.xml into .out");
            transformer.transform(new StreamSource(new File("Bugzilla5609.xml")), 
                                  new StreamResult(new File("Bugzilla5609.out")));
            fileChecker.check(logger, 
                    new File("Bugzilla5609.out"), 
                    new File("Bugzilla5609.gold"), 
                    "transform#1 into Bugzilla5609.out");


            logger.logMsg(Logger.STATUSMSG, "About to transform#2 ParamBugzilla5609a.xml into .out");
            transformer.transform(new StreamSource(new File("Bugzilla5609a.xml")), 
                                  new StreamResult(new File("Bugzilla5609a.out")));
            fileChecker.check(logger, 
                    new File("Bugzilla5609a.out"), 
                    new File("Bugzilla5609a.gold"), 
                    "transform#2 into Bugzilla5609a.out; but is wrong var num is used");
            
        } 
        catch (Throwable t)
        {
            logger.logThrowable(Logger.WARNINGMSG, t, "Bugzilla#5609 threw");
            logger.checkErr("Bugzilla#5609 threw " + t.toString());
        }

        try
        {
            // Reproduce bug when getting single transformer from templates
            TransformerFactory factory = TransformerFactory.newInstance();
            logger.logMsg(Logger.STATUSMSG, "About to newTemplates(Bugzilla5609.xsl)");
            Templates templates = factory.newTemplates(new StreamSource(new File("Bugzilla5609.xsl")));
            logger.logMsg(Logger.STATUSMSG, "About to Templates.newTransformer()");
            Transformer transformer = templates.newTransformer();
            logger.logMsg(Logger.STATUSMSG, "About to transform#1 Bugzilla5609.xml into .out");
            transformer.transform(new StreamSource(new File("Bugzilla5609.xml")), 
                                  new StreamResult(new File("Bugzilla5609.out")));
            fileChecker.check(logger, 
                    new File("Bugzilla5609.out"), 
                    new File("Bugzilla5609.gold"), 
                    "transform#1 into Bugzilla5609.out");


            logger.logMsg(Logger.STATUSMSG, "About to transform#2 Bugzilla5609a.xml into .out");
            transformer.transform(new StreamSource(new File("Bugzilla5609a.xml")), 
                                  new StreamResult(new File("Bugzilla5609a.out")));
            fileChecker.check(logger, 
                    new File("Bugzilla5609a.out"), 
                    new File("Bugzilla5609a.gold"), 
                    "transform#2 into Bugzilla5609a.out; but is wrong var num is used");
            
        } 
        catch (Throwable t)
        {
            logger.logThrowable(Logger.WARNINGMSG, t, "Bugzilla#5609 threw");
            logger.checkErr("Bugzilla#5609 threw " + t.toString());
        }
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=5609">
     * Link to Bugzilla report</a>
     * @return Global Variable Initialization across Multiple Transformations.
     */
    public String getDescription()
    {
        return "Global Variable Initialization across Multiple Transformations";
    }

}  // end of class Bugzilla5609

