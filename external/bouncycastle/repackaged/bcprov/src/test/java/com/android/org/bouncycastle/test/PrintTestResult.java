/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.bouncycastle.test;


import java.util.Enumeration;

import junit.framework.TestResult;

/**
 * @hide This class is not part of the Android public SDK API
 */
public class PrintTestResult
{
    public static void printResult(TestResult result)
    {
        Enumeration e = result.failures();
        if (e != null)
        {
            while (e.hasMoreElements())
            {
                System.out.println(e.nextElement());
            }
        }

        e = result.errors();
        if (e != null)
        {
            while (e.hasMoreElements())
            {
                System.out.println(e.nextElement());
            }
        }

        if (!result.wasSuccessful())
        {
            System.exit(1);
        }
    }
}

