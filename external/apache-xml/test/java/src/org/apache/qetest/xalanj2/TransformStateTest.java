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
 * TransformStateTest.java
 *
 */
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.util.Properties;
import java.util.Vector;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * Basic functionality testing of TransformState interface.
 * This is basically just a test driver class for an explicit 
 * list of TransformStateTestlet/Datalets.  In the future we 
 * should enable better data-driven testing by being able to 
 * read in a list of TransformStateDatalets somehow.
 * 
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class TransformStateTest extends FileBasedTest
{

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String X2J_SUBDIR = "xalanj2";


    /** Just initialize test name, comment, numTestCases. */
    public TransformStateTest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "TransformStateTest";
        testComment = "Basic functionality testing of TransformState interface";
    }


    /**
     * Initialize this test - create output dir.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // NOTE: 'reporter' variable is already initialized at this point

        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + X2J_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);

        return true;
    }


    /**
     * Use a TransformStateTestlet to test some datalets.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        // Use a 'fake' testCase1 to initialize everything
        reporter.testCaseInit("Use a TransformStateTestlet to test some datalets");
        reporter.logWarningMsg("Note: limited validation: only certain events checked.");

        // First arg is list of files; currently unused
        // Note: several method signatures are copied from 
        //  StylesheetTestletDriver for future growth
        Vector datalets = buildDatalets(null, 
                                        new File(inputDir + File.separator + X2J_SUBDIR), 
                                        new File(outputDir + File.separator + X2J_SUBDIR), 
                                        new File(goldDir + File.separator + X2J_SUBDIR));

        // Validate datalets
        if ((null == datalets) || (0 == datalets.size()))
        {
            // No datalets to test, report it as an error
            reporter.checkErr("Testlet or datalets are null/blank, nothing to test!");
            return true;
        }
        else
        {
            reporter.checkPass("Found " + datalets.size() + " datalets to test...");
        }

        // Now just go through the list and process each set
        int numDatalets = 0;
        numDatalets = datalets.size();
        reporter.logInfoMsg("processFileList-equivalent() with " + numDatalets
                            + " potential tests");
        // Close out our 'fake' initialization testCase1
        reporter.testCaseClose();
        // Iterate over every datalet and test it
        for (int ctr = 0; ctr < numDatalets; ctr++)
        {
            try
            {
                reporter.testCaseInit("Testing datalet(" + ctr + ") validation size=" 
                        + ((TransformStateDatalet)datalets.elementAt(ctr)).validate99.size());
                // Create a Testlet to execute a test with this 
                //  next datalet - the Testlet will log all info 
                //  about the test, including calling check*()
                getTestlet().execute((TransformStateDatalet)datalets.elementAt(ctr));
            } 
            catch (Throwable t)
            {
                // Log any exceptions as fails and keep going
                //@todo improve the below to output more useful info
                reporter.checkFail("Datalet num " + ctr + " threw: " + t.toString());
                reporter.logThrowable(Logger.ERRORMSG, t, "Datalet threw");
            }
            reporter.testCaseClose();
        }  // of while...

        return true;
    }


    /**
     * Transform a vector of individual test names into a Vector 
     * of filled-in datalets to be tested
     *
     * This currently is hard-coded to return a static Vector
     * of Datalets that are simply constructed here.
     * In the future we should add a way to read them in from disk.
     * 
     * @param files Vector of local path\filenames to be tested
     * This is currently ignored
     * @param testLocation File denoting directory where all 
     * .xml/.xsl tests are found
     * @param outLocation File denoting directory where all 
     * output files should be put
     * @param goldLocation File denoting directory where all 
     * gold files are found
     * @return Vector of StylesheetDatalets that are fully filled in,
     * i.e. outputName, goldName, etc are filled in respectively 
     * to inputName
     */
    public Vector buildDatalets(Vector files, File testLocation, 
                                File outLocation, File goldLocation)
    {
        Vector v = new Vector();
        
/***********************************************
// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
        // A simple datalet for identity transform
        TransformStateDatalet d = new TransformStateDatalet();
        String testFileName = "identity";
        d.inputName = testLocation.getPath() + File.separator + testFileName + ".xsl";
        d.xmlName = testLocation.getPath() + File.separator + testFileName + ".xml";
        d.outputName = outLocation.getPath() + File.separator + testFileName + ".out";
        d.goldName = goldLocation.getPath() + File.separator + testFileName + ".out";
        // Validation TransformStates for RootTemplate
        ExpectedTransformState ets = new ExpectedTransformState();
        ets.setName("doc");
        ets.set("line", ExpectedObject.MUST_EQUAL, new Integer(6));
        ets.set("column", ExpectedObject.MUST_EQUAL, new Integer(8));
        ets.set("event", ExpectedObject.MUST_EQUAL, "startElement:");
        ets.set("current.match", ExpectedObject.MUST_EQUAL, "@*|node()");
        ets.set("matched.match", ExpectedObject.MUST_EQUAL, "@*|node()");
        // Add the expected object(s) to the datalet..
        d.expectedTransformStates.put(ets.getHashKey(), ets);

        ets = new ExpectedTransformState();
        ets.setName("copy");
        ets.set("line", ExpectedObject.MUST_EQUAL, new Integer(7));
        ets.set("column", ExpectedObject.MUST_EQUAL, new Integer(15));
        ets.set("event", ExpectedObject.MUST_EQUAL, "startElement:");
        ets.set("current.match", ExpectedObject.MUST_EQUAL, "@*|node()");
        // Add the expected object(s) to the datalet..
        d.expectedTransformStates.put(ets.getHashKey(), ets);

// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
***********************************************/
        // Simple single file with call-template, modes
        TransformStateDatalet d = new TransformStateDatalet();
        String testFileName = "TransformState99a";
        d.inputName = testLocation.getPath() + File.separator + testFileName + ".xsl";
        d.xmlName = testLocation.getPath() + File.separator + testFileName + ".xml";
        d.outputName = outLocation.getPath() + File.separator + testFileName + ".out";
        d.goldName = goldLocation.getPath() + File.separator + testFileName + ".out";
        d.validate99.put("42.current.name", "apple");
        d.validate99.put("42.current.match", "pies-are-good");
        d.validate99.put("42.matched.name", "template-1-root");
        d.validate99.put("42.matched.match", "/");

        // .. and Add the datalet to the vector
        v.addElement(d);

        // Simple included file
        d = new TransformStateDatalet();
        testFileName = "TransformState99b"; // and TransformState99binc.xsl
        d.inputName = testLocation.getPath() + File.separator + testFileName + ".xsl";
        d.xmlName = testLocation.getPath() + File.separator + testFileName + ".xml";
        d.outputName = outLocation.getPath() + File.separator + testFileName + ".out";
        d.goldName = goldLocation.getPath() + File.separator + testFileName + ".out";
        // Note this should still be cross-checked for line numbers when using included files!
        d.validate99.put("27.current.name", "apple");
        d.validate99.put("27.current.match", "pies-are-good");
        d.validate99.put("27.matched.name", "template-1-root");
        d.validate99.put("27.matched.match", "/");

        // .. and Add the datalet to the vector
        v.addElement(d);

        // Simple imported file
        d = new TransformStateDatalet();
        testFileName = "TransformState99c"; // and TransformState99cimp.xsl
        d.inputName = testLocation.getPath() + File.separator + testFileName + ".xsl";
        d.xmlName = testLocation.getPath() + File.separator + testFileName + ".xml";
        d.outputName = outLocation.getPath() + File.separator + testFileName + ".out";
        d.goldName = goldLocation.getPath() + File.separator + testFileName + ".out";
        // Note this should still be cross-checked for line numbers when using included files!
        d.validate99.put("32.current.name", "apple");
        d.validate99.put("32.current.match", "pies-are-good");
        d.validate99.put("32.matched.name", "template-1-root");
        d.validate99.put("32.matched.match", "/");

        // .. and Add the datalet to the vector
        v.addElement(d);

        // All done: return full vector
        return v;
    }


    /**
     * Convenience method to get a Testlet to use.  
     * Hard-coded to return a TransformStateTestlet.
     * 
     * @return Testlet for use in this test; null if error
     */
    public TransformStateTestlet getTestlet()
    {
        try
        {
            // Create it and set our reporter into it
            TransformStateTestlet t = new TransformStateTestlet();
            t.setLogger((Logger)reporter);
            return t;
        }
        catch (Exception e)
        {
            // Ooops, problem, should get logged somehow
            return null;
        }
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by TransformStateTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        TransformStateTest app = new TransformStateTest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
