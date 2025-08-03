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
 * ErrorListenerAPITest.java
 *
 */
package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.util.Properties;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import org.apache.qetest.FileBasedTest;
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * API Coverage test for ErrorListener; defaults to Xalan impl.
 * Only very basic API coverage.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ErrorListenerAPITest extends FileBasedTest
{
    /** FQCN for Xalan-J 2.x impl.  */
    public static final String XALAN_ERRORLISTENER_IMPL = "org.apache.xml.utils.DefaultErrorHandler";

    /** FQCN for the Logging* impl that the tests provide.  */
    public static final String QETEST_ERRORLISTENER_IMPL = "org.apache.qetest.trax.LoggingErrorHandler";

    /** Name of ErrorListener implementation we're going to test.  */
    public String errorListenerClassname = XALAN_ERRORLISTENER_IMPL;

    /** Just initialize test name, comment, numTestCases. */
    public ErrorListenerAPITest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "ErrorListenerAPITest";
        testComment = "API Coverage test for ErrorListener; defaults to Xalan impl";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
     * //@todo read in name of alternate ErrorListener class!  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        reporter.logInfoMsg("//@todo allow user to change name of ErrorListener implementation used");
        return true;
    }


    /**
     * API Coverage of ErrorListener class, using Xalan-J 2.x impl.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("API Coverage of ErrorListener class, using Xalan-J 2.x impl");
        Class elClass = null;
        ErrorListener errorListener = null;
        try
        {
            elClass = Class.forName(errorListenerClassname);
            errorListener = (ErrorListener)elClass.newInstance();
        }
        catch (Exception e)
        {
            reporter.checkErr("Loading errorListener implementation " + errorListenerClassname
                              + " threw: " + e.toString());
            reporter.testCaseClose();
            return true;
        }

        Exception ex = new Exception("Exception-message-here");
        TransformerException tex = new TransformerException("TransformerException-message-here", ex);

        try
        {
            errorListener.warning(tex);
            reporter.checkPass("warning did not throw any exception");
            reporter.logTraceMsg("//@todo also validate System.err stream!");
        }
        catch (TransformerException te)
        {
            reporter.checkFail("warning threw TransformerException, threw: " + te.toString());
        }
        catch (Throwable t)
        {
            reporter.checkFail("warning threw non-TransformerException, threw: " + t.toString());
        }

        try
        {
            // Default error impl in Xalan throws exception
            errorListener.error(tex);
            reporter.checkFail("error did not throw any exception");
            reporter.logTraceMsg("//@todo also validate System.err stream!");
        }
        catch (TransformerException te)
        {
            reporter.checkPass("error expectedly threw TransformerException, threw: " + te.toString());
            reporter.check((te.toString().indexOf("TransformerException-message-here") > -1), 
                           true, "error's exception includes proper text");
        }
        catch (Throwable t)
        {
            reporter.checkFail("error threw non-TransformerException, threw: " + t.toString());
        }

        try
        {
            // Default fatalError impl in Xalan throws exception
            errorListener.error(tex);
            reporter.checkFail("fatalError did not throw any exception");
            reporter.logTraceMsg("//@todo also validate System.err stream!");
        }
        catch (TransformerException te)
        {
            reporter.checkPass("fatalError expectedly threw TransformerException, threw: " + te.toString());
            reporter.check((te.toString().indexOf("TransformerException-message-here") > -1), 
                           true, "fatalError's exception includes proper text");
        }
        catch (Throwable t)
        {
            reporter.checkFail("fatalError threw non-TransformerException, threw: " + t.toString());
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
        return ("Common [optional] options supported by ErrorListenerAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        ErrorListenerAPITest app = new ErrorListenerAPITest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
