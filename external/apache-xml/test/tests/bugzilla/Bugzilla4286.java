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

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.File;
import java.io.StringWriter;

/**
 * For not well formed XML document, the Transformer hangs(May be due to threading issues)
 * @author manoranjan_das@hotmail.com
 * @author shane_curcuru@lotus.com
 */
public class Bugzilla4286 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla4286"; }

    /**
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#4286 For not well formed XML document, the Transformer hangs");
        logger.logMsg(Logger.CRITICALMSG, "WARNING! THIS TEST MAY HANG! (i.e. don't run in automation)");
        try
        {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer(new StreamSource("identity.xsl"));
            StringWriter stringWriter = new StringWriter();
            logger.logMsg(Logger.STATUSMSG, "About to transform error.xml into StringWriter");
            transformer.transform(new StreamSource(new File("error.xml")), 
                                  new StreamResult(stringWriter));
            logger.checkPass("Transform completed and returned (crash test)");
            logger.logMsg(Logger.STATUSMSG, "To-do: validate output!");
            logger.logMsg(Logger.STATUSMSG, "StringWriter is: " + stringWriter.toString());
        }
        catch (TransformerException te)
        {
            // Since the XML is invalid, we should get a TransformerException
            logger.logThrowable(Logger.ERRORMSG, te, "Transform threw expected");
            logger.checkPass("Transform threw expected: " + te.toString());
        }
        catch (Exception e)
        {
            logger.logThrowable(Logger.ERRORMSG, e, "Transform threw non-expected");
            logger.checkFail("Transform threw non-expected: " + e.toString());
        }
        logger.logMsg(Logger.CRITICALMSG, "Bug occours now: system hangs");
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=4286">
     * Link to Bugzilla report</a>
     * @return For not well formed XML document, the Transformer hangs.
     */
    public String getDescription()
    {
        return "#4286 For not well formed XML document, the Transformer hangs";
    }

}  // end of class Bugzilla4286
