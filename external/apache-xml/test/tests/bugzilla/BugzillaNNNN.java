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

// REPLACE_imports needed for reproducing the bug


/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * INSTRUCTIONS:
 * <ul>Given your Bugzilla bugnumber:
 * <li>Save this file under a different name like 
 * Bugzilla<i>bugnumber</li> and search-and-replace 
 * 'NNNN' to your Bugzilla bugnumber</li>
 * <li>Search-and-replace all REPLACE_* strings with something appropriate</li>
 * <li>javac BugzillaNNNN.java</li>
 * <li>java BugzillaNNNN</li>
 * <li>Attach the .java file to your Bugzilla report (or, checkin 
 * to xml-xalan/test/tests/Bugzilla if committer)</li>
 * </ul>
 * Using this common format may allow us in the future to automate 
 * verifying Bugzilla bugs to prevent regressions!
 * @author REPLACE_your_email_address
 */
public class BugzillaNNNN extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "BugzillaNNNN"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#NNNN");

        // Optional: use the Datalet d if supplied

        // Call code to reproduce the bug here

        // Call logger.checkFail("desc") (like Junit's assert(true, "desc")
        //  or logger.checkPass("desc")  (like Junit's assert(false, "desc")
        //  to report the actual bug fail/pass status
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=NNNN">
     * Link to Bugzilla report</a>
     * @return REPLACE_BugzillaNNNN_description.
     */
    public String getDescription()
    {
        return "REPLACE_BugzillaNNNN_description";
    }

}  // end of class BugzillaNNNN

