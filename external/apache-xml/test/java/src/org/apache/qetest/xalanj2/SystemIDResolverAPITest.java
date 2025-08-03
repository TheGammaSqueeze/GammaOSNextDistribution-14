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
package org.apache.qetest.xalanj2;

import android.platform.test.annotations.FlakyTest;
import org.apache.qetest.FileBasedTest;
import org.apache.xml.utils.SystemIDResolver;
import org.junit.Test;

import java.nio.file.Paths;

/**
 * Functionality/system/integration tests for SystemIDResolver.
 *
 * Very simple coverage test.
 * 
 * @author shane_curcuru@us.ibm.com,
 *         Joe Kesselman  
 * @version $Id$
 */
public class SystemIDResolverAPITest extends FileBasedTest
{
    /** Just initialize test name, comment, numTestCases. */
    public SystemIDResolverAPITest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "SystemIDResolverAPITest";
        testComment = "Functionality/system/integration tests for SystemIDResolver";
    }

    /** Separator for hierarchical URLs /.  */
    private static final char URL_SEP = '/';

    /** Default file:// scheme header.  */
    private static final String FILE_SCHEME = "file://";

    /** 
     * Test strings and expected data for getAbsoluteURIFromRelative(String uri).  
     * NOTE: We really need a more definitive and thorough set of 
     * test strings: this requires really reading a number of RFC's...
     */
    protected static String[][] ABS_URI_FROM_REL = 
    { /* Assumption: test prepends expected user.dir stuff */
        { "foo.out", "foo.out" },
        { "bar/foo.out", "bar/foo.out" },
        { "bar\\foo.out", "bar/foo.out" },
        { "foo.out", "foo.out" }
    };

    /** Test strings and expected data for getAbsoluteURI(String url) 
     * assuming they're relative paths from a baseURL of file:///'user.dir'.  
     * NOTE: We really need a more definitive and thorough set of 
     * test strings: this requires really reading a number of RFC's...
     */
    protected static String[][] ABS_URI_REL = 
    { /* Assumption: test prepends expected user.dir stuff */
        { "foo.out", "foo.out" },
        { "bar/foo.out", "bar/foo.out" },
        { "bar\\foo.out", "bar/foo.out" },
        { "foo.out", "foo.out" }
    };

    /** Test strings and expected data for getAbsoluteURI(String url) 
     * assuming they're absolute paths with their own scheme:.  
     * NOTE: We really need a more definitive and thorough set of 
     * test strings: this requires really reading a number of RFC's...
     */
    protected static String[][] ABS_URI_ABS = 
    {
        { "http://server.com/foo.out", "http://server.com/foo.out" },
        { "http://server.com/bar/foo.out", "http://server.com/bar/foo.out" },
        { "http://server.com/bar/foo.out#fragment", "http://server.com/bar/foo.out#fragment" },
        { "http://server.com/bar/foo/?param=value&name=value", "http://server.com/bar/foo/?param=value&name=value" }, 
        { "http://127.0.0.1/bar/foo.out#fragment", "http://127.0.0.1/bar/foo.out#fragment" },
        { "file://server.com/bar/foo.out", "file://server.com/bar/foo.out" }
    };

    /** Test strings and expected data for getAbsoluteURI(String urlString, String base).  */
    protected static String[][] ABS_URI_WITH_BASE = 
    { /*  "urlString", "base", "expected result" */
        { "foo.out", "file:///bar", "file:///foo.out" }, /* Note that trailing /bar is dropped since it's effectively a file reference */
        { "foo.out", "file:///bar/", "file:///bar/foo.out" },
        { "foo.out", "http://server.com/bar", "http://server.com/foo.out" },
        { "foo.out", "http://server.com/bar/", "http://server.com/bar/foo.out" },
        { "../foo.out", "http://server.com/bar/", "http://server.com/foo.out" }
    };


    /**
     * Using our data set of URLs, try various resolver calls.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Using our data set of URLs, try various resolver calls");

        try
        {
            String prevUserDir = System.getProperty("user.dir");
            String baseURL = ((Paths.get(prevUserDir)).toUri()).toString();

            reporter.logStatusMsg("user.dir baseURI is: " + baseURL);

            reporter.logTraceMsg("Now testing getAbsoluteURIFromRelative...");
            for (int i = 0; i < ABS_URI_FROM_REL.length; i++)
            {
                String val = SystemIDResolver.getAbsoluteURIFromRelative(ABS_URI_FROM_REL[i][0]);
                // Automatically prepend the baseURI to expected data
                reporter.check(val, baseURL + ABS_URI_FROM_REL[i][1], "getAbsoluteURIFromRelative(" + ABS_URI_FROM_REL[i][0] + ") = " + val);
            }

            reporter.logTraceMsg("Now testing getAbsoluteURI with relative paths and default baseURL");
            for (int i = 0; i < ABS_URI_REL.length; i++)
            {
                String val = SystemIDResolver.getAbsoluteURI(ABS_URI_REL[i][0]);
                // Automatically prepend the baseURI to expected data
                reporter.check(val, baseURL + ABS_URI_REL[i][1], "getAbsoluteURI(" + ABS_URI_REL[i][0] + ") = " + val);
            }

            reporter.logTraceMsg("Now testing getAbsoluteURI with absolute paths");
            for (int i = 0; i < ABS_URI_ABS.length; i++)
            {
                String val = SystemIDResolver.getAbsoluteURI(ABS_URI_ABS[i][0]);
                reporter.check(val, ABS_URI_ABS[i][1], "getAbsoluteURI(" + ABS_URI_ABS[i][0] + ") = " + val);
            }

            reporter.logTraceMsg("Now testing getAbsoluteURI with a user-supplied baseURL");
            for (int i = 0; i < ABS_URI_WITH_BASE.length; i++)
            {
                String val = SystemIDResolver.getAbsoluteURI(ABS_URI_WITH_BASE[i][0], ABS_URI_WITH_BASE[i][1]);
                reporter.check(val, ABS_URI_WITH_BASE[i][2], "getAbsoluteURI(" + ABS_URI_WITH_BASE[i][0]
                        + ", " + ABS_URI_WITH_BASE[i][1] + ") = " + val);
            }
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "testCase1 threw");
            reporter.checkFail("testCase1 threw: " + t.toString());
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
        return ("Common [optional] options supported by SystemIDResolverAPITest:\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SystemIDResolverAPITest app = new SystemIDResolverAPITest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
