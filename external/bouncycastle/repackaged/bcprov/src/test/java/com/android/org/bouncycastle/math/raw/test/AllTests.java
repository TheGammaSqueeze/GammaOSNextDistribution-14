/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.bouncycastle.math.raw.test;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import com.android.org.bouncycastle.test.PrintTestResult;

/**
 * @hide This class is not part of the Android public SDK API
 */
public class AllTests
    extends TestCase
{
    public static void main (String[] args) 
        throws Exception
    {
       PrintTestResult.printResult( junit.textui.TestRunner.run(suite()));
    }
    
    public static Test suite() 
        throws Exception
    {   
        TestSuite suite = new TestSuite("Raw math tests");

        suite.addTest(InterleaveTest.suite());

        return new BCTestSetup(suite);
    }

    static class BCTestSetup
        extends TestSetup
    {
        public BCTestSetup(Test test)
        {
            super(test);
        }

        protected void setUp()
        {

        }

        protected void tearDown()
        {

        }
    }
}
