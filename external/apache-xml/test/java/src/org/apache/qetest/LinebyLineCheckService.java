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

package org.apache.qetest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Simply does .readLine of each file into string buffers and then String.equals().
 * @author Paul_Dick@us.ibm.com
 * @version $Id$
 */
public class LinebyLineCheckService implements CheckService
{

    /**
     * Compare two objects for equivalence, and return appropriate result.
     *
     * @param logger to dump any output messages to
     * @param actual (current) File to check
     * @param reference (gold, or expected) File to check against
     * @param description of what you're checking
     * @param msg comment to log out with this test point
     * @param id ID tag to log out with this test point
     * @return Logger.*_RESULT code denoting status; each method may 
     * define it's own meanings for pass, fail, ambiguous, etc.
     */
    public int check(Logger logger, Object actual, Object expected,
                     String msg, String id)
    {	
        if (!((actual instanceof File) & (expected instanceof File)))
        {
            // Must have File objects to continue
            logger.checkErr(msg + " :check() objects were not Files", id);
            return Logger.ERRR_RESULT;
        }

		if (doCompare(logger, (File) actual, (File) expected))
        {
            logger.checkPass(msg, id);
            return Logger.PASS_RESULT;
        }
        else
        {
            logger.checkFail(msg, id);
            return Logger.FAIL_RESULT;
        }
    }

    /**
     * Compare two objects for equivalence, and return appropriate result.
     *
     * @param logger to dump any output messages to
     * @param actual (current) File to check
     * @param reference (gold, or expected) File to check against
     * @param description of what you're checking
     * @param msg comment to log out with this test point
     * @return Logger.*_RESULT code denoting status; each method may 
     * define it's own meanings for pass, fail, ambiguous, etc.
     */
    public int check(Logger logger, Object actual, Object reference,
                     String msg)
    {	
        return check(logger, actual, reference, msg, null);
    }

    /**
     * Read text files line-by-line and do comparison
     * @param logger to dump any messages to
     * @param act File object to read
     * @param exp File object to read
     * @return True or False based on comparison
     */
    private boolean doCompare(Logger logger, File act, File exp)
    {
        StringBuffer sb_act = new StringBuffer();
		StringBuffer sb_exp = new StringBuffer();

        try
        {
            FileInputStream in_act = new FileInputStream(act);
            FileInputStream in_exp = new FileInputStream(exp);
			// Create necessary I/O objects
            InputStreamReader fra = new InputStreamReader(in_act, "UTF-8");
                       InputStreamReader fre = new InputStreamReader(in_exp, "UTF-8");

            BufferedReader bra = new BufferedReader(fra);
			BufferedReader bre = new BufferedReader(fre);

			int line = 0;	// Line number to report in case of failure
            for (;;)
            {
				String inbufe = bre.readLine();	 // read files, line by line
				String inbufa = bra.readLine();
				line += 1;						 // Keep track of line number

				if (inbufe == null)		// Is expected done?
				{
					if (inbufa == null)	// If so, is actual done?
					{ 
						break;			// If so, we're done!!
					}
					else				// If not report error
					{
						logger.logArbitrary(logger.FAILSONLY, "Actual("+line+"): " + inbufa);
						logger.logArbitrary(logger.FAILSONLY, "Expect("+line+"): " + inbufe);
						return false;
					}
				}
				else
				{						// Still data to compare 
				    if ( !(inbufa.equals(inbufe)) )
				    {
						logger.logArbitrary(logger.FAILSONLY, "Actual("+line+"): " + inbufa);
						logger.logArbitrary(logger.FAILSONLY, "Expect("+line+"): " + inbufe);
						return false;
				    }
				}
            }
        }
        catch (Exception e)				//  Report any file I/O exceptions
        {
            if (logger != null)
            {
                logger.logMsg(Logger.ERRORMSG, "LinebyLineCheckService() threw:" + 
                				e.toString());
            }
            else
                System.err.println("LinebyLineCheckService() threw:" + e.toString());

            return false;
        }
        return true;
    }

    /**
     * Gets extended information about the last checkFiles call: NONE AVAILABLE.
     * @return null, since we don't support this
     */
    public String getExtendedInfo()
    {
        return null;
    }

    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * No-op; this class does not have any supported attributes.
     * 
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     * @throws IllegalArgumentException thrown if the underlying
     * implementation doesn't recognize the attribute and wants to 
     * inform the user of this fact.
     */
    public void setAttribute(String name, Object value)
        throws IllegalArgumentException
    {
        /* no-op */        
    }

    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * No-op; this class does not have any supported attributes.
     * 
     * @param attrs Props of various name, value attrs.
     */
    public void applyAttributes(Properties attrs)
    {
        /* no-op */        
    }

    /**
     * Allows the user to retrieve specific attributes on the testing 
     * utility or it's underlying product object under test.
     *
     * @param name The name of the attribute.
     * @return null, no attributes supported.
     * @throws IllegalArgumentException thrown if the underlying
     * implementation doesn't recognize the attribute and wants to 
     * inform the user of this fact.
     */
    public Object getAttribute(String name)
        throws IllegalArgumentException
    {
        return null;
    }

    /**
     * Description of what this testing utility does.  
     * @return String description of extension
     */
    public String getDescription()
    {
        return ("Reads in text files line-by-line as strings (ignoring newlines) and does String.equals()");
    }

}  // end of class LinebyLineCheckService
