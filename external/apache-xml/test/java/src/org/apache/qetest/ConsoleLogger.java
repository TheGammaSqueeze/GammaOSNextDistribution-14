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
 * ConsoleLogger.java
 *
 */
package org.apache.qetest;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

/**
 * Logger that prints human-readable output to System.out.
 * As an experiment, the ConsoleLogger supports an independent
 * loggingLevel that can be more restrictive than a loggingLevel 
 * set in any enclosing Reporter.  
 * Note this isn't quite as well architected as I would like, 
 * but it does address what seems to be the 
 * most common usage case: where you're running tests automatically, 
 * and likely will be using some file-based output for results 
 * analysis.  This allows you to set loggingLevel for your Reporter
 * high, so that most/all output is sent to the file, but set this 
 * ConsoleLogger's loggingLevel low, so only critical problems 
 * are displayed on the screen (since most of the time users will 
 * never be watching the console in this situation).
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class ConsoleLogger implements Logger
{

    //-----------------------------------------------------
    //-------- Class members --------
    //-----------------------------------------------------

    /** Our output stream - currently hard-coded to System.out. */
    protected PrintStream outStream = System.out;

    /** If we're ready to start outputting yet. */
    protected boolean ready = false;

    /** If we should indent sub-results or not. */
    protected boolean indent = true;

    /** Level (number of spaces?) to indent sub-results. */
    protected StringBuffer sIndent = new StringBuffer();

    /** Generic properties for this Logger; sort-of replaces instance variables. */
    protected Properties loggerProps = null;

    /** 
    * Special LoggingLevel for just this instance.  
    * May be set from "ConsoleLogger.loggingLevel" property;
    * defaults to 100 which should be larger than any other 
    * loggingLevels in use currently.
    * Note that different levels here will even restrict output 
    * from control messages like testCaseInit; see individual 
    * javadocs for what controls what.  This may affect the level 
    * of indenting you see as well; I traded off a little speed
    * (don't calc indent if not using that message) for prettiness.
    */
    protected int consoleLoggingLevel = 100;
    
    //-----------------------------------------------------
    //-------- Control and utility routines --------
    //-----------------------------------------------------

    /** Simple constructor, does not perform initialization. */
    public ConsoleLogger()
    { /* no-op */
    }

    /**
     * Constructor calls initialize(p).
     * @param p Properties block to initialize us with.
     */
    public ConsoleLogger(Properties p)
    {
        ready = initialize(p);
    }

    /**
     * Return a description of what this Logger does.
     * @return "reports results to System.out".
     */
    public String getDescription()
    {
        return ("org.apache.qetest.ConsoleLogger - reports results to System.out.");
    }

    /**
     * Returns information about the Property name=value pairs that
     * are understood by this Logger/Reporter.
     * @return same as {@link java.applet.Applet.getParameterInfo}.
     */
    public String[][] getParameterInfo()
    {

        String pinfo[][] =
        {
            { OPT_INDENT, "boolean", "If reporter should indent sub-results" },
            { "ConsoleLogger.loggingLevel", "String", "loggingLevel for just ConsoleLogger; only if more restrictive than other loggingLevels" }
        };

        return pinfo;
    }

    /**
     * Accessor methods for our properties block.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Properties getProperties()
    {
        return loggerProps;
    }

    /**
     * Accessor methods for our properties block.
     * @param p Properties to set (is cloned).
     */
    public void setProperties(Properties p)
    {

        if (p != null)
        {
            loggerProps = (Properties) p.clone();
        }
    }

    /**
     * Call once to initialize this Logger/Reporter from Properties.
     * @param Properties block to initialize from.
     * @param status, true if OK, false if an error occoured.
     *
     * @param p Properties block to initialize from
     * @return true if OK; currently always returns true
     */
    public boolean initialize(Properties p)
    {

        setProperties(p);

        String i = loggerProps.getProperty(OPT_INDENT);

        if (i != null)
        {
            if (i.toLowerCase().equals("no")
                    || i.toLowerCase().equals("false"))
                indent = false;
            else if (i.toLowerCase().equals("yes")
                     || i.toLowerCase().equals("true"))
                indent = true;
        }

        // Grab our specific loggingLevel and set if needed
        String logLvl = loggerProps.getProperty("ConsoleLogger.loggingLevel");
        if (logLvl != null)
        {
            // Note: if present, we'll attempt to set it
            // It doesn't really make much sense to set it if 
            //  this value is larger than an enclosing Reporter's 
            //  loggingLevel, but it won't hurt either
            try
            {
                consoleLoggingLevel = Integer.parseInt(logLvl);
            }
            catch (NumberFormatException numEx)
            { /* no-op */
            }
        }

        ready = true;

        return true;
    }

    /**
     * Is this Logger/Reporter ready to log results?
     * @return status - true if it's ready to report, false otherwise
     */
    public boolean isReady()
    {
        return ready;
    }

    /**
     * Flush this Logger/Reporter - no-op for ConsoleLogger.
     */
    public void flush()
    { /* no-op */
    }

    /**
     * Close this Logger/Reporter - essentially no-op for ConsoleLogger.
     */
    public void close()
    {

        flush();

        ready = false;
    }

    /** Simplistic indenting - two spaces. */
    protected void indent()
    {
        if (indent)
            sIndent.append("  ");
    }

    /** Simplistic outdenting - two spaces. */
    protected void outdent()
    {
        if ((indent) && (sIndent.length() >= 2))
            sIndent.setLength(sIndent.length() - 2);
    }

    //-----------------------------------------------------
    //-------- Testfile / Testcase start and stop routines --------
    //-----------------------------------------------------

    /**
     * Report that a testfile has started.
     * Output only when ConsoleLogger.loggingLevel >= ERRORMSG
     *
     * @param name file name or tag specifying the test.
     * @param comment comment about the test.
     */
    public void testFileInit(String name, String comment)
    {
        if (consoleLoggingLevel < ERRORMSG)
            return;
            
        outStream.println(sIndent + "TestFileInit " + name + ":" + comment);
        indent();
    }

    /**
     * Report that a testfile has finished, and report it's result.
     * Output only when ConsoleLogger.loggingLevel >= ERRORMSG
     *
     * @param msg message or name of test to log out
     * @param result result of testfile
     */
    public void testFileClose(String msg, String result)
    {
        if (consoleLoggingLevel < ERRORMSG)
            return;
            
        outdent();
        outStream.println(sIndent + "TestFileClose(" + result + ") " + msg);
    }

    /**
     * Report that a testcase has started.
     * Output only when ConsoleLogger.loggingLevel >= WARNINGMSG
     *
     * @param comment short description of this test case's objective.
     */
    public void testCaseInit(String comment)
    {
        if (consoleLoggingLevel < WARNINGMSG)
            return;
            
        outStream.println(sIndent + "TestCaseInit " + comment);
        indent();
    }

    /**
     * Report that a testcase has finished, and report it's result.
     * Output only when ConsoleLogger.loggingLevel >= WARNINGMSG
     *
     * @param msg message of name of test case to log out
     * @param result result of testfile
     */
    public void testCaseClose(String msg, String result)
    {
        if (consoleLoggingLevel < WARNINGMSG)
            return;
            
        outdent();
        outStream.println(sIndent + "TestCaseClose(" + result + ") " + msg);
    }

    //-----------------------------------------------------
    //-------- Test results logging routines --------
    //-----------------------------------------------------

    /**
     * Report a comment to result file with specified severity.
     * Output only when ConsoleLogger.loggingLevel >= level
     *
     * @param level severity or class of message.
     * @param msg comment to log out.
     */
    public void logMsg(int level, String msg)
    {
        if (consoleLoggingLevel < level)
            return;
            
        outStream.println(sIndent + msg);
    }

    /**
     * Report an arbitrary String to result file with specified severity.
     * Log out the String provided exactly as-is.
     * Output only when ConsoleLogger.loggingLevel >= level
     *
     * @param level severity or class of message.
     * @param msg arbitrary String to log out.
     */
    public void logArbitrary(int level, String msg)
    {
        if (consoleLoggingLevel < level)
            return;
            
        outStream.println(msg);
    }

    /**
     * Logs out statistics to result file with specified severity.
     * Output only when ConsoleLogger.loggingLevel >= level
     *
     * @param level severity of message.
     * @param lVal statistic in long format.
     * @param dVal statistic in double format.
     * @param msg comment to log out.
     */
    public void logStatistic(int level, long lVal, double dVal, String msg)
    {
        if (consoleLoggingLevel < level)
            return;
            
        outStream.println(sIndent + msg + " l: " + lVal + " d: " + dVal);
    }

    /**
     * Logs out Throwable.toString() and a stack trace of the 
     * Throwable with the specified severity.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param throwable throwable/exception to log out.
     * @param msg description of the throwable.
     */
    public void logThrowable(int level, Throwable throwable, String msg)
    {
        if (consoleLoggingLevel < level)
            return;

        StringWriter sWriter = new StringWriter();

        sWriter.write(msg + "\n");
        sWriter.write(throwable.toString() + "\n");

        PrintWriter pWriter = new PrintWriter(sWriter);
        throwable.printStackTrace(pWriter);

        outStream.println(sWriter.toString());
    }

    /**
     * Logs out a element to results with specified severity.
     * Simply indents and dumps output as string like so:
     * <pre>
     *    element
     *    attr1=value1
     *    ...
     *    msg.toString()
     * </pre>
     * Output only when ConsoleLogger.loggingLevel >= level
     *
     * @param level severity of message.
     * @param element name of enclosing element
     * @param attrs hash of name=value attributes
     * @param msg Object to log out; up to reporters to handle
     * processing of this; usually logs just .toString().
     */
    public void logElement(int level, String element, Hashtable attrs,
                           Object msg)
    {
        if (consoleLoggingLevel < level)
            return;
            
        if ((element == null)
           || (attrs == null))
        {
            // Bail if either element name or attr list is null
            // Note: we should really handle this case more elegantly
            return;
        }

        indent();
        outStream.println(sIndent + element);
        indent();

        for (Enumeration keys = attrs.keys();
                keys.hasMoreElements(); /* no increment portion */ )
        {
            Object key = keys.nextElement();

            outStream.println(sIndent + key.toString() + "="
                              + attrs.get(key).toString());
        }

        outdent();
        if (msg != null)
            outStream.println(sIndent + msg.toString());
        outdent();
    }

    /**
     * Logs out contents of a Hashtable with specified severity.
     * Output only when ConsoleLogger.loggingLevel >= level
     *
     * @param level severity or class of message.
     * @param hash Hashtable to log the contents of.
     * @param msg decription of the Hashtable.
     */
    public void logHashtable(int level, Hashtable hash, String msg)
    {
        if (consoleLoggingLevel < level)
            return;
            
        indent();
        outStream.println(sIndent + "HASHTABLE: " + msg);
        indent();

        if (hash == null)
        {
            outStream.println(sIndent + "hash == null, no data");
        }
        else
        {
            try
            {

                // Fake the Properties-like output
                for (Enumeration keys = hash.keys();
                        keys.hasMoreElements(); /* no increment portion */ )
                {
                    Object key = keys.nextElement();

                    outStream.println(sIndent + key.toString() + "="
                                      + hash.get(key).toString());
                }
            }
            catch (Exception e)
            {

                // No-op: should ensure we have clean output
            }
        }

        outdent();
        outdent();
    }

    //-----------------------------------------------------
    //-------- Test results reporting check* routines --------
    //-----------------------------------------------------

    /**
     * Writes out a Pass record with comment.
     * Output only when ConsoleLogger.loggingLevel > FAILSONLY
     * 
     * @param comment comment to log with the pass record.
     */
    public void checkPass(String comment)
    {
        // Note <=, since FAILSONLY is a special level
        if (consoleLoggingLevel <= FAILSONLY)
            return;
            
        outStream.println(sIndent + "PASS!  " + comment);
    }

    /**
     * Writes out an ambiguous record with comment.
     * Output only when ConsoleLogger.loggingLevel > FAILSONLY
     *
     * @param comment comment to log with the ambg record.
     */
    public void checkAmbiguous(String comment)
    {
        // Note <=, since FAILSONLY is a special level
        if (consoleLoggingLevel <= FAILSONLY)
            return;

        outStream.println(sIndent + "AMBG   " + comment);
    }

    /**
     * Writes out a Fail record with comment.
     * Output only when ConsoleLogger.loggingLevel >= FAILSONLY
     *
     * @param comment comment to log with the fail record.
     */
    public void checkFail(String comment)
    {
        if (consoleLoggingLevel < FAILSONLY)
            return;

        outStream.println(sIndent + "FAIL   " + comment);
    }

    /**
     * Writes out a Error record with comment.
     * Output only when ConsoleLogger.loggingLevel >= ERRORMSG
     *
     * @param comment comment to log with the error record.
     */
    public void checkErr(String comment)
    {
        if (consoleLoggingLevel < ERRORMSG)
            return;

        outStream.println(sIndent + "ERROR  " + comment);
    }

    /* EXPERIMENTAL: have duplicate set of check*() methods 
       that all output some form of ID as well as comment. 
       Leave the non-ID taking forms for both simplicity to the 
       end user who doesn't care about IDs as well as for 
       backwards compatibility.
    */

    /**
     * Writes out a Pass record with comment and ID.
     * Output only when ConsoleLogger.loggingLevel > FAILSONLY
     * 
     * @param comment comment to log with the pass record.
     * @param ID token to log with the pass record.
     */
    public void checkPass(String comment, String id)
    {
        // Note <=, since FAILSONLY is a special level
        if (consoleLoggingLevel <= FAILSONLY)
            return;
            
        if (id != null)
            outStream.println(sIndent + "PASS!  (" + id + ") " + comment);
        else
            outStream.println(sIndent + "PASS!  " + comment);
    }

    /**
     * Writes out an ambiguous record with comment and ID.
     * Output only when ConsoleLogger.loggingLevel > FAILSONLY
     * 
     * @param comment to log with the ambg record.
     * @param ID token to log with the pass record.
     */
    public void checkAmbiguous(String comment, String id)
    {
        // Note <=, since FAILSONLY is a special level
        if (consoleLoggingLevel <= FAILSONLY)
            return;
            
        if (id != null)
            outStream.println(sIndent + "AMBG   (" + id + ") " + comment);
        else
            outStream.println(sIndent + "AMBG   " + comment);
    }

    /**
     * Writes out a Fail record with comment and ID.
     * Output only when ConsoleLogger.loggingLevel >= FAILSONLY
     * 
     * @param comment comment to log with the fail record.
     * @param ID token to log with the pass record.
     */
    public void checkFail(String comment, String id)
    {
        if (consoleLoggingLevel < FAILSONLY)
            return;

        if (id != null)
            outStream.println(sIndent + "FAIL!  (" + id + ") " + comment);
        else
            outStream.println(sIndent + "FAIL!  " + comment);
    }

    /**
     * Writes out an Error record with comment and ID.
     * Output only when ConsoleLogger.loggingLevel >= ERRORMSG
     * 
     * @param comment comment to log with the error record.
     * @param ID token to log with the pass record.
     */
    public void checkErr(String comment, String id)
    {
        if (consoleLoggingLevel < ERRORMSG)
            return;

        if (id != null)
            outStream.println(sIndent + "ERROR  (" + id + ") " + comment);
        else
            outStream.println(sIndent + "ERROR  " + comment);
    }
}  // end of class ConsoleLogger

