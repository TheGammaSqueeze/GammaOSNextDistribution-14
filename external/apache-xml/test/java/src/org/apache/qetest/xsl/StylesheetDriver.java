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

package org.apache.qetest.xsl;

import java.io.File;
import java.util.Properties;
import java.util.Vector;

import org.apache.qetest.Logger;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

/**
 * Test driver for running a single XSLT test.
 * 
 * This is a simplification of the generic StylesheetTestletDriver 
 * that simply executes a single stylesheet test using minimal 
 * parameters but still supporting all the normal testing options.
 *
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class StylesheetDriver extends StylesheetTestletDriver
{
    /** Just initialize test name, comment; numTestCases is not used. */
    public StylesheetDriver()
    {
        testName = "StylesheetDriver";
        testComment = "Test driver for single XSLT stylesheets";
    }


    /**
     * Override default processing to simply create a fileList 
     * based on the single input file desired.
     *
     * @param p Properties block of options to use - unused
     * @return true if OK, false if we should abort
     */
    public boolean runTestCases(Properties p)
    {
        // First log out any other runtime information, like the 
        //  actual flavor of ProcessorWrapper, etc.
        try
        {
            // Just grab all the info from the TransformWrapper...
            Properties runtimeProps = TransformWrapperFactory.newWrapper(flavor).getProcessorInfo();
            // ... and add a few extra things ourselves
            runtimeProps.put("actual.testlet", getTestlet());
            runtimeProps.put("actual.dirFilter", getDirFilter());
            runtimeProps.put("actual.fileFilter", getFileFilter());
            reporter.logHashtable(Logger.CRITICALMSG, runtimeProps, 
                                  "actual.runtime information");
        }
        catch (Exception e)
        {
            reporter.logThrowable(Logger.WARNINGMSG, e, "Logging actual.runtime threw");
        }

        // Get the baseName of the test: axes01, boolean34, etc.
        String testBaseName = testProps.getProperty("test", "processorinfo01"); // HACK some default test

        // Create the datalet to run for this test
        Vector datalets = buildDatalet(testBaseName);

        // Actually process the specified file(s) in a testCase as normal
        processFileList(datalets, "test1:" + testBaseName);
        return true;
    }


    /**
     * Create datalet(s) from a single user-supplied test name 
     * in the format of 'axes44' or 'string132'.
     *
     * @param baseName of just one stylesheet to test
     * @return Vector of local path\filenames of tests to run;
     * the tests themselves will exist; null if error
     */
    public Vector buildDatalet(String baseName)
    {
        // Calculate directory name; pattern assumed to be 
        //  [chars]N... where N... is numeric
        String subdirName = baseName.substring(0, (baseName.length() - 2)); // HACK must actually calculate name, not assume 2 digits
        reporter.logTraceMsg("buildDatalet:" + baseName + " baseName " + subdirName);

        File subTestDir = new File(new File(inputDir), subdirName);
        File subOutDir = new File(outputDir, subdirName);
        File subGoldDir = new File(goldDir, subdirName);
        // Validate that each of the specified dirs exists
        // Returns directory references like so:
        //  testDirectory = 0, outDirectory = 1, goldDirectory = 2
        File[] dirs = validateDirs(new File[] { subTestDir }, 
                                   new File[] { subOutDir, subGoldDir });

        String testFilename = baseName + XSL_EXTENSION; // HACK should allow for xml embedded as well
        File testFile = new File(subTestDir.getPath() + File.separator + testFilename);
        if (!testFile.exists())
        {
            reporter.checkErr("test1: file does not exist: " + testFilename);
            return null;
        }
        // Go and construct the datalet with all info
        Vector v = new Vector(1);
        // Check if it's a normal .xsl file, or a .xml file
        //  (we assume .xml files are embedded tests!)
        StylesheetDatalet d = new StylesheetDatalet();
        if (testFilename.endsWith(XML_EXTENSION))
        {
            d.xmlName = subTestDir.getPath() + File.separator + testFilename;

            String fileNameRoot = testFilename.substring(0, testFilename.indexOf(XML_EXTENSION));
            d.inputName = null;
            d.outputName = subOutDir.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
            d.goldName = subGoldDir.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
        }
        else if (testFilename.endsWith(XSL_EXTENSION))
        {
            d.inputName = subTestDir.getPath() + File.separator + testFilename;

            String fileNameRoot = testFilename.substring(0, testFilename.indexOf(XSL_EXTENSION));
            d.xmlName = subTestDir.getPath() + File.separator + fileNameRoot + XML_EXTENSION;
            d.outputName = subOutDir.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
            d.goldName = subGoldDir.getPath() + File.separator + fileNameRoot + OUT_EXTENSION;
        }
        else
        {
            // Hmmm - I'm not sure what we should do here
            reporter.logWarningMsg("Unexpected test file found, skipping: " + testFilename);
        }
        d.setDescription(testFilename);
        d.flavor = flavor;
        // Also copy over our own testProps as it's 
        //  options: this allows for future expansion
        //  of values in the datalet
        d.options = new Properties(testProps);
        // Optimization: put in a copy of our fileChecker, so 
        //  that each testlet doesn't have to create it's own
        //  fileCheckers should not store state, so this 
        //  shouldn't affect the testing at all
        d.options.put("fileCheckerImpl", fileChecker);
        v.addElement(d);

        return v;
    }



    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by StylesheetDriver:\n"
                + "    -" + "test"
                + "  <baseName of tests to run - axes01 >\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        StylesheetDriver app = new StylesheetDriver();
        app.doMain(args);
    }
}
