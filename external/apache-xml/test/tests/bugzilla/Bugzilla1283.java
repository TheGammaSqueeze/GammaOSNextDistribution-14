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
import org.apache.qetest.trax.LoggingErrorListener;

// REPLACE_imports needed for reproducing the bug
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.File;


/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * @author Antti.Valtokari@iocore.fi
 * @author shane_curcuru@lotus.com
 */
public class Bugzilla1283 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla1283"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     */
    public void execute(Datalet d)
    {
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#1283 Xalan hangs if javax.xml.transform.TransformerException thrown when invoked through JAXP");
        logger.logMsg(Logger.CRITICALMSG, "WARNING! THIS TEST MAY HANG! (i.e. don't run in automation)");
        try
        {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Source transformerSource = new StreamSource(new File("identity.xsl"));
            Transformer transformer =
              transformerFactory.newTransformer(transformerSource);
            // Use nifty utility from testxsl.jar
            LoggingErrorListener loggingErrorListener = new LoggingErrorListener(logger);
            transformer.setErrorListener(loggingErrorListener); // default is to throw when fatalError

            Source input = new StreamSource(new File("error.xml"));
            Result output = new StreamResult(new File("Bugzilla1283.out"));
            logger.logMsg(Logger.STATUSMSG, "About to transform error.xml into Bugzilla1283.out");
            transformer.transform(input, output);
            logger.checkFail("Transform should have had fatalError which threw Exception");
        }
        catch (Exception e)
        {
            logger.logThrowable(Logger.ERRORMSG, e, "Transform properly had fatalError and threw");
            logger.checkPass("Transform properly had fatalError and threw: " + e.toString());
        }
        logger.logMsg(Logger.CRITICALMSG, "Bug occours now: system hangs");
        logger.checkPass("if we got here, we didn't hang!");
    }

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=1283">
     * Link to Bugzilla report</a>
     * @return Xalan hangs if javax.xml.transform.TransformerException thrown when invoked through JAXP.
     */
    public String getDescription()
    {
        return "Xalan hangs if javax.xml.transform.TransformerException thrown when invoked through JAXP";
    }

}  // end of class Bugzilla1283

