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
 * REPLACE_template_for_new_tests.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.util.Properties;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.XSLTestfileInfo;

//-------------------------------------------------------------------------

/**
 * REPLACE_overall_test_comment.
 * INSTRUCTIONS:
 * - save as YourNewFilename,java
 * - search-and-replace 'REPLACE_template_for_new_tests' with 'YourNewFilename'
 * - search-and-replace all 'REPLACE_*' strings as appropriate
 * - remove this block of comments and replace with your javadoc
 * - implement sequentially numbered testCase1, testCase2, ... for 
 *   each API or group or related API's
 * - update the line: numTestCases = 2;  // REPLACE_num
 * - execute 'build package.trax', 'traxapitest REPLACE_template_for_new_tests'
 * - a bunch of convenience variables/initializers are included, 
 *   use or delete as is useful
 * @author REPLACE_authorname
 * @version $Id$
 */
public class REPLACE_template_for_new_tests extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Information about an xsl/xml file pair for transforming.  
     * Public members include inputName (for xsl); xmlName; goldName; etc.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";


    /** Just initialize test name, comment, numTestCases. */
    public REPLACE_template_for_new_tests()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "REPLACE_template_for_new_tests";
        testComment = "REPLACE_overall_test_comment";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
     * REPLACE_other_test_file_init.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // NOTE: 'reporter' variable is already initialized at this point

        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;

        testFileInfo.inputName = testBasePath + "REPLACE_xslxml_filename.xsl";
        testFileInfo.xmlName = testBasePath + "REPLACE_xslxml_filename.xml";
        testFileInfo.goldName = goldBasePath + "REPLACE_xslxml_filename.out";

        return true;
    }


    /**
     * Cleanup this test - REPLACE_other_test_file_cleanup.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileClose(Properties p)
    {
        // Often will be a no-op
        return true;
    }


    /**
     * REPLACE_brief_description_testCase1.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("REPLACE_brief_description_testCase1");

        // REPLACE_init_data_for_this_independent_test_case
        
        // REPLACE_call_trax_apis_here_to_use_product
        reporter.logInfoMsg("Use reporter.log*Msg() to report information about what the test is doing, as needed");
        // REPLACE_call_trax_apis_here_to_verify_test_point
        reporter.check(true, true, "use reporter.check(actual, gold, description) to report test point results");
        reporter.logTraceMsg("Multiple test points per test case are fine; results are summed automatically");

        // REPLACE_cleanup_data_for_this_independent_test_case

        reporter.testCaseClose();
        return true;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by REPLACE_template_for_new_tests:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + "REPLACE_any_new_test_arguments\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {

        REPLACE_template_for_new_tests app = new REPLACE_template_for_new_tests();

        app.doMain(args);
    }
}
