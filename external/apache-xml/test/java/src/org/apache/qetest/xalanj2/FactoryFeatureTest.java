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
 * FactoryFeatureTest.java
 *
 */
package org.apache.qetest.xalanj2;

import java.io.File;
import java.util.Properties;
import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.TraxDatalet;
import org.apache.xalan.processor.TransformerFactoryImpl;
import org.apache.xml.utils.DefaultErrorHandler;

//-------------------------------------------------------------------------

/**
 * Basic functionality test of various Factory configuration APIs.
 * Testing TransformerFactoryImpl.setAttribute, getFeature, etc.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class FactoryFeatureTest extends FileBasedTest
{

    /** Provides nextName(), currentName() functionality.   */
    protected OutputNameManager outNames;

    /** Marker for datalet.options that notes setAttribute val.   */
    protected static final String SET_ATTRIBUTE = "setAttribute";

    /** Just initialize test name, comment, numTestCases. */
    public FactoryFeatureTest()
    {
        numTestCases = 4;  // REPLACE_num
        testName = "FactoryFeatureTest";
        testComment = "Basic functionality test of various Factory configuration APIs";
    }


    /**
     * Initialize this test - create output dir, outNames.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        File outSubDir = new File(outputDir + File.separator + "xalanj2");
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + "xalanj2"
                                         + File.separator + testName, ".out");
        return true;
    }


    /**
     * Default values/settings of known features; simple error cases.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Default values/settings of known features; simple error cases");

        TransformerFactory factory = null;
        try
        {
            factory = TransformerFactory.newInstance();

            try
            {
                reporter.logStatusMsg("Calling: factory.getAttribute(FEATURE_INCREMENTAL)");
                Object o = factory.getAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL);
                reporter.checkPass("factory.getAttribute(FEATURE_INCREMENTAL) returned a value");
                reporter.logWarningMsg("//@todo also validate default value:false");                
            }
            catch (IllegalArgumentException iae1)
            {
                // Note Xalan-J 2.2D06 does not fully implement this yet!
                reporter.checkPass("factory.getAttribute(FEATURE_INCREMENTAL) threw an expected IllegalArgumentException");
            }

            try
            {
                reporter.logStatusMsg("Calling: factory.getAttribute(FEATURE_OPTIMIZE)");
                Object o = factory.getAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE);
                reporter.checkPass("factory.getAttribute(FEATURE_OPTIMIZE) returned a value");
                reporter.logWarningMsg("//@todo also validate default value:true");                
            }
            catch (IllegalArgumentException iae1)
            {
                // Note Xalan-J 2.2D06 does not fully implement this yet!
                reporter.checkPass("factory.getAttribute(FEATURE_OPTIMIZE) threw an expected IllegalArgumentException");
            }

            // Set each value to it's non-default value and ensure no exceptions thrown
            reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_INCREMENTAL, Boolean.TRUE)");
            factory.setAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL, Boolean.TRUE);
            reporter.checkPass("factory.setAttribute(FEATURE_INCREMENTAL, Boolean.TRUE) returned OK");

            reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_OPTIMIZE, Boolean.FALSE)");
            factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, Boolean.FALSE);
            reporter.checkPass("factory.setAttribute(FEATURE_OPTIMIZE, Boolean.FALSE) returned OK");

            // Try setting a non-recognized attribute
            try
            {
                reporter.logTraceMsg("Calling: factory.setAttribute(unrecognized-attribute, Boolean.FALSE)");
                factory.setAttribute("unrecognized-attribute", Boolean.FALSE);
                reporter.checkFail("factory.setAttribute(unrecognized-attribute,...) did not throw expected exception");
            }
            catch (IllegalArgumentException iae1)
            {
                reporter.checkPass("factory.setAttribute(unrecognized-attribute,...) properly threw: " + iae1.toString());
            }

            // Try setting a recognized attribute to a bad value
            try
            {
                reporter.logTraceMsg("Calling: factory.setAttribute(..., bad-object-value)");
                // Note: pass any sort of non-String, non-Boolean Object, since 
                //  this attribute only accepts ture|false as Strings or Booleans
                factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, factory);
                reporter.checkFail("factory.setAttribute(..., bad-object-value) did not throw expected exception");
            }
            catch (IllegalArgumentException iae2)
            {
                reporter.checkPass("factory.setAttribute(..., bad-object-value) properly threw: " + iae2.toString());
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("setAttribute() tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "setAttribute() tests threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Validate transforms with FEATURE_INCREMENTAL on/off.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Validate transforms with FEATURE_INCREMENTAL on/off");

        // Get our list of xsl datalets to test
        Vector datalets = buildDatalets(null, new File(inputDir), 
                                        new File(outputDir), new File(goldDir));

        // Skip Validating datalets - they're hard-coded
        int numDatalets = datalets.size();
        reporter.logInfoMsg("processFileList-equivalent() with " + numDatalets
                            + " potential tests");

        TransformerFactory factory = null;
        // Iterate over every datalet and test it with both feature settings
        for (int ctr = 0; ctr < numDatalets; ctr++)
        {
            TraxDatalet datalet = (TraxDatalet)datalets.elementAt(ctr);
            try
            {
                // Get a new factory for each datalet - not strictly necessary
                factory = TransformerFactory.newInstance();

                // Test both a Boolean object..
                reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_INCREMENTAL, Boolean.TRUE)");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL, Boolean.TRUE);
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_INCREMENTAL, Boolean.TRUE");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_INCREMENTAL, Boolean.FALSE)");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL, Boolean.FALSE);
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_INCREMENTAL, Boolean.FALSE");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                // .. and a String representation of true|false
                reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_INCREMENTAL, 'true')");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL, "true");
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_INCREMENTAL, 'true'");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                reporter.logStatusMsg("Calling: factory.setAttribute(FEATURE_INCREMENTAL, 'false')");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_INCREMENTAL, "false");
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_INCREMENTAL, 'false'");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);
            } 
            catch (Throwable t)
            {
                reporter.logThrowable(Logger.ERRORMSG, t, datalet.getDescription() + "(" + ctr + ") threw");
                reporter.checkFail(datalet.getDescription() + "(" + ctr + ") threw: " + t.toString());
            }
        }  // of while...

        reporter.testCaseClose();
        return true;
    }


    /**
     * Validate transforms with FEATURE_OPTIMIZE on/off.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Validate transforms with FEATURE_OPTIMIZE on/off");

        // Get our list of xsl datalets to test
        Vector datalets = buildDatalets(null, new File(inputDir), 
                                        new File(outputDir), new File(goldDir));

        // Skip Validating datalets - they're hard-coded
        int numDatalets = datalets.size();
        reporter.logInfoMsg("processFileList-equivalent() with " + numDatalets
                            + " potential tests");

        TransformerFactory factory = null;
        // Iterate over every datalet and test it with both feature settings
        for (int ctr = 0; ctr < numDatalets; ctr++)
        {
            TraxDatalet datalet = (TraxDatalet)datalets.elementAt(ctr);
            try
            {
                // Get a new factory for each datalet - not strictly necessary
                factory = TransformerFactory.newInstance();

                // Test both a Boolean object..
                reporter.logInfoMsg("Calling: factory.setAttribute(FEATURE_OPTIMIZE, Boolean.TRUE)");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, Boolean.TRUE);
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_OPTIMIZE, Boolean.TRUE");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                reporter.logInfoMsg("Calling: factory.setAttribute(FEATURE_OPTIMIZE, Boolean.FALSE)");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, Boolean.FALSE);
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_OPTIMIZE, Boolean.FALSE");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                // .. and a String representation of true|false
                reporter.logInfoMsg("Calling: factory.setAttribute(FEATURE_OPTIMIZE, 'true')");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, "true");
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_OPTIMIZE, 'true'");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);

                reporter.logInfoMsg("Calling: factory.setAttribute(FEATURE_OPTIMIZE, 'false')");
                factory.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, "false");
                datalet.options.put(SET_ATTRIBUTE, "FEATURE_OPTIMIZE, 'false'");
                datalet.outputName = outNames.nextName();
                transformAndCheck(factory, datalet);
            } 
            catch (Throwable t)
            {
                reporter.logThrowable(Logger.ERRORMSG, t, datalet.getDescription() + "(" + ctr + ") threw");
                reporter.checkFail(datalet.getDescription() + "(" + ctr + ") threw: " + t.toString());
            }
        }  // of while...

        reporter.testCaseClose();
        return true;
    }
    
    /**
     * Validate transforms with FEATURE_SECURE_PROCESSING on/off.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Validate transforms with FEATURE_SECURE_PROCESSING on/off");
        
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();
            
            // The test xsl contains an extension function. The transformation is successful
            // when FEATURE_SECURE_PROCESSING is set to false.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            
            TraxDatalet datalet = new TraxDatalet();
            datalet.setDescription("Test secure processing feature:");
            datalet.setNames(inputDir + File.separator + "xalanj2", "SecureProcessingTest");
            datalet.goldName = goldDir + File.separator + "xalanj2" + File.separator + "SecureProcessingTest.out";
            datalet.outputName = outNames.nextName();
            transformAndCheck(factory, datalet);
                        
            try
            {
                // TransformerException is thrown when FEATURE_SECURE_PROCESSING is set to true.
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                Transformer transformer = factory.newTransformer(datalet.getXSLSource());
                transformer.setErrorListener(new DefaultErrorHandler());
                transformer.transform(datalet.getXMLSource(), new StreamResult(datalet.outputName));
                reporter.checkFail("Expected TransformerException not thrown when secure processing feature is set to true.");
            }
            catch (javax.xml.transform.TransformerException e)
            {
                reporter.checkPass("TransformerFactory with FEATURE_SECURE_PROCESSING set to true threw TransformerException:  " + e.toString());
            }
        }
        catch (Throwable t)
        {
            reporter.logThrowable(Logger.ERRORMSG, t, "Failed in secure processing feature test");
            reporter.checkFail("Failed in secure processing feature test");
        }
        
        reporter.testCaseClose();
        return true;        
    }


    /**
     * Create a vector of filled-in datalets to be tested.
     *
     * This currently is hard-coded to return a static Vector
     * of Datalets that are simply constructed here.
     * In the future we should add a way to read them in from disk.
     * The 'what' files we should test.
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
        // identity transform
        // Info about the minitest: note gold file naming is different
        TraxDatalet d = new TraxDatalet();
        d.setDescription("Identity transform and:");
        d.setNames(inputDir + File.separator + "trax", "identity");
        d.goldName = goldDir + File.separator + "trax" + File.separator + "identity.out";
        v.addElement(d);

        // Info about the minitest: note gold file naming is different
        d = new TraxDatalet();
        d.setDescription("Basic Minitest file and:");
        d.setNames(inputDir, "Minitest");
        d.goldName = goldDir + File.separator + "Minitest-xalanj2.out";
        v.addElement(d);

        // All done: return full vector
        return v;
    }


    /**
     * Convenience method to do a transform and validate output.  
     * The 'how' of a specific test.
     * @param factory to use to create transformers
     * @param datalet to use (TraxDatalet)
     */
    protected void transformAndCheck(TransformerFactory factory, 
                                     TraxDatalet datalet)
    {
        // Validate arguments
        if ((null == factory) || (null == datalet))
        {
            reporter.checkErr(datalet.getDescription() + " with null args!");
            return;
        }
        final String desc = datalet.getDescription() + datalet.options.get(SET_ATTRIBUTE);

        try
        {
            reporter.logStatusMsg("transformAndCheck of: " + desc);
            Transformer transformer = factory.newTransformer(datalet.getXSLSource());
            reporter.logTraceMsg("About to transform...");
            transformer.transform(datalet.getXMLSource(), new StreamResult(datalet.outputName));

            if (Logger.PASS_RESULT 
                != fileChecker.check(reporter, 
                                     new File(datalet.outputName), 
                                     new File(datalet.goldName), 
                                     desc + " into " + datalet.outputName)
               )
                reporter.logInfoMsg(desc + " failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.ERRORMSG, t, desc + " threw");
            reporter.checkFail(desc + " threw: " + t.toString());
        }        
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by FactoryFeatureTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        FactoryFeatureTest app = new FactoryFeatureTest();
        app.doMain(args);
    }
}
