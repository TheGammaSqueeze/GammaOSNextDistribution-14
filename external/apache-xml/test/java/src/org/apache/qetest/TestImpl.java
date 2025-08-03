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
 * TestImpl.java
 *
 */
package org.apache.qetest;

import java.util.Properties;

/**
 * Minimal class defining a test implementation, using a Reporter.
 * <p>TestImpls generally interact with a Reporter, which reports
 * out in various formats the results from this test.
 * Most test classes should subclass from this test, as it adds
 * structure that helps to define the conceptual logic of running
 * a 'test'.  It also provides useful default implementations.</p>
 * <p>Users wishing a much simpler testing framework can simply
 * implement the minimal methods in the Test interface, and use a
 * Logger to report results instead of a Reporter.</p>
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class TestImpl implements Test
{

    /**
     * Name (and description) of the current test.
     * <p>Note that these are merely convenience variables - you do not need
     * to use them.  If you do use them, they should be initialized at
     * construction time.</p>
     */
    protected String testName = null;

    /**
     * Accesor method for the name of this test.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String getTestName()
    {
        return testName;
    }

    /** (Name and) description of the current test. */
    protected String testComment = null;

    /**
     * Accesor method for a brief description of this test.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String getTestDescription()
    {
        return testComment;
    }

    /**
     * Default constructor - initialize testName, Comment.
     */
    public TestImpl()
    {

        // Only set them if they're not set
        if (testName == null)
            testName = "TestImpl.defaultName";

        if (testComment == null)
            testComment = "TestImpl.defaultComment";
    }

    /** Our Logger, who we tell all our secrets to. */
    protected Logger logger = null;

    /**
     * Accesor methods for our Logger.  
     *
     * NEEDSDOC @param l
     */
    public void setLogger(Logger l)
    {  // no-op: our implementation always uses a Reporter
    }

    /**
     * Accesor methods for our Logger.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Logger getLogger()
    {
        return null;
    }

    /** Our Reporter, who we tell all our secrets to. */
    protected Reporter reporter;

    /**
     * Accesor methods for our Reporter.  
     *
     * NEEDSDOC @param r
     */
    public void setReporter(Reporter r)
    {
        if (r != null)
            reporter = r;
    }

    /**
     * Accesor methods for our Reporter.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Reporter getReporter()
    {
        return reporter;
    }

    /** Flag to indicate a serious enough error that we should just give up. */
    protected boolean abortTest = false;

    /**
     * Accesor methods for our abort flag.  
     *
     * NEEDSDOC @param a
     */
    public void setAbortTest(boolean a)
    {
        abortTest = a;
    }

    /**
     * Accesor methods for our abort flag.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean getAbortTest()
    {
        return (abortTest);
    }

    /**
     * Run this test: main interface to cause the test to run itself.
     * <p>A major goal of the TestImpl class is to separate the act and process
     * of writing a test from it's actual runtime implementation.  Testwriters
     * should not need to know how their test is being executed.</p>
     * <ul>They should simply focus on defining:
     * <li>doTestFileInit: what setup has to be done before running the test</li>
     * <li>testCase1, 2, ... n: individual, independent test cases</li>
     * <li>doTestFileClose: what cleanup has to be done after running the test</li>
     * </ul>
     * <p>This method returns a simple boolean status as a convenience.  In cases
     * where you have a harness that runs a great many tests that normally pass, the
     * harness can simply check this value for each test: if it's true, you could
     * even delete any result logs then, and simply print out a meta-log stating
     * that the test passed.  Note that this does not provide any information about
     * why a test failed (or caused an error, or whatever) - that's what the info in
     * any Reporter's logs are for.</p>
     * <p>If a test is aborted, then any containing harness needs not
     * finish executing the test.  Otherwise, even if part of a test fails,
     * you should let the whole test run through.</p>
     * <p>Harnesses should generally simply call runTest() to ask the
     * test to run itself.  In some cases a Harness might want to control
     * the process more closely, in which case it should call:
     * <code>
     *  test.setReporter(); // optional, depending on the test
     *  test.testFileInit();
     *  test.runTestCases();
     *  test.testFileClose();
     * </code>  instead.
     * @todo return TestResult instead of boolean flag
     * @author Shane_Curcuru@lotus.com
     *
     * NEEDSDOC @param p
     * @return status - true if test ran to completion and <b>all</b>
     * cases passed, false otherwise
     */
    public boolean runTest(Properties p)
    {

        boolean status = testFileInit(p);

        if (getAbortTest())
            return status;

        status &= runTestCases(p);

        if (getAbortTest())
            return status;

        status &= testFileClose(p);

        return status;
    }

    /**
     * Initialize this test - called once before running testcases.
     * Predefined behavior - subclasses should <b>not</b> override this method.
     * <p>This method is basically a composite that masks the most common
     * implementation: creating a reporter or logger first, then initializing
     * any data or product settings the test needs setup first. It does this
     * by separating this method into three methods:
     * <code>
     *   preTestFileInit(); // Create/initialize Reporter
     *   doTestFileInit();  // User-defined: initialize product under test
     *   postTestFileInit() // Report out we've completed initialization
     * </code>
     * </p>
     * @author Shane_Curcuru@lotus.com
     * @see #preTestFileInit(java.util.Properties)
     * @see #doTestFileInit(java.util.Properties)
     * @see #postTestFileInit(java.util.Properties)
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean testFileInit(Properties p)
    {

        // Note: we don't want to use shortcut operators here,
        //       since we want each method to get called
        // Pass the Property block to each method, so that 
        //       subclasses can do initialization whenever 
        //       is best for their design
        return preTestFileInit(p) & doTestFileInit(p) & postTestFileInit(p);
    }

    /**
     * Initialize this test - called once before running testcases.
     * <p>Create and initialize a Reporter here.</p>
     * <p>This implementation simply creates a default Reporter
     * and adds a ConsoleLogger. Most test groups will want to override
     * this method to create custom Reporters or Loggers.</p>
     * @author Shane_Curcuru@lotus.com
     * @see #testFileInit(java.util.Properties)
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean preTestFileInit(Properties p)
    {

        // Pass our properties block directly to the reporter
        //  so it can use the same values in initialization
        setReporter(new Reporter(p));
        reporter.addDefaultLogger();
        reporter.testFileInit(testName, testComment);

        return true;
    }

    /**
     * Initialize this test - called once before running testcases.
     * <p>Subclasses <b>must</b> override this to do whatever specific
     * processing they need to initialize their product under test.</p>
     * <p>If for any reason the test should not continue, it <b>must</b>
     * return false from this method.</p>
     * @author Shane_Curcuru@lotus.com
     * @see #testFileInit(java.util.Properties)
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean doTestFileInit(Properties p)
    {

        // @todo implement in your subclass
        reporter.logTraceMsg(
            "TestImpl.doTestFileInit() default implementation - please override");

        return true;
    }

    /**
     * Initialize this test - called once before running testcases.
     * <p>Simply log out that our initialization has completed,
     * so that structured-style logs will make it clear where startup
     * code ends and testCase code begins.</p>
     * @author Shane_Curcuru@lotus.com
     * @see #testFileInit(java.util.Properties)
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean postTestFileInit(Properties p)
    {

        reporter.logTraceMsg(
            "TestImpl.postTestFileInit() initialization complete");

        return true;
    }

    /**
     * Run all of our testcases.
     * Subclasses must override this method.  It should cause each testCase
     * in the test to be executed independently, and then return true if and
     * only if all testCases passed successfully.  If any testCase failed or
     * caused any unexpected errors, exceptions, etc., it should return false.
     * @author Shane_Curcuru@lotus.com
     *
     * NEEDSDOC @param p
     * @return true if all testCases passed, false otherwise
     */
    public boolean runTestCases(Properties p)
    {

        // @todo implement in your subclass
        reporter.logTraceMsg(
            "TestImpl.runTestCases() default implementation - please override");

        return true;
    }

    /**
     * Cleanup this test - called once after running testcases.
     * @author Shane_Curcuru@lotus.com
     *
     * NEEDSDOC @param p
     * @return true if cleanup successful, false otherwise
     */
    public boolean testFileClose(Properties p)
    {

        // Note: we don't want to use shortcut operators here,
        //       since we want each method to get called
        return preTestFileClose(p) & doTestFileClose(p)
               & postTestFileClose(p);
    }

    /**
     * Log a trace message - called once after running testcases.
     * <p>Predefined behavior - subclasses should <B>not</B> override this method.</p>
     * @todo currently is primarily here to mark that we're closing
     * the test, in case doTestFileClose() blows up somehow.  May not be needed.
     * @author Shane_Curcuru@lotus.com
     * @see #testFileClose()
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    protected boolean preTestFileClose(Properties p)
    {

        // Have the reporter log a trace that the test is about to cleanup
        reporter.logTraceMsg("TestImpl.preTestFileClose()");

        return true;
    }

    /**
     * Cleanup this test - called once after running testcases.
     * <p>Subclasses <b>must</b> override this to do whatever specific
     * processing they need to cleanup after all testcases are run.</p>
     * @author Shane_Curcuru@lotus.com
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    public boolean doTestFileClose(Properties p)
    {

        // @todo implement in your subclass
        reporter.logTraceMsg(
            "TestImpl.doTestFileClose() default implementation - please override");

        return true;
    }

    /**
     * Mark the test complete - called once after running testcases.
     * <p>Predefined behavior - subclasses should <b>not</b> override
     * this method. Currently just tells our reporter to log the
     * testFileClose. This will calculate final results, and complete
     * logging for any structured output logs (like XML files).</p>
     * @author Shane_Curcuru@lotus.com
     * @see #testFileClose()
     *
     * NEEDSDOC @param p
     *
     * NEEDSDOC ($objectName$) @return
     */
    protected boolean postTestFileClose(Properties p)
    {

        // Have the reporter log out our completion
        reporter.testFileClose();

        return true;
    }

    /**
     * Main method to run test from the command line.
     * Test subclasses <B>must</B> override, obviously.
     * @author Shane Curcuru
     *
     * NEEDSDOC @param args
     */
    public static void main(String[] args)
    {

        TestImpl app = new TestImpl();
        Properties p = new Properties();

        p.put(MAIN_CMDLINE, args);
        app.runTest(p);
    }
}  // end of class Test

