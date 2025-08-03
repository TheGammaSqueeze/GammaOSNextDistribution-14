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
 * XMLFileLogger.java
 *
 */
package org.apache.qetest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

/**
 * Logger that saves output to a simple XML-format file.
 * @todo improve escapeString so it's more rigorous about escaping
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class XMLFileLogger implements Logger
{

    //-----------------------------------------------------
    //-------- Constants for results file structure --------
    //-----------------------------------------------------

    /** XML root element tag: root of our output document.  */
    public static final String ELEM_RESULTSFILE = "resultsfile";

    /** XML element tag: testFileInit() parent element.  */
    public static final String ELEM_TESTFILE = "testfile";

    /** XML element tag: testFileClose() leaf element 
      * emitted immediately before closing ELEM_TESTFILE.
      */
    public static final String ELEM_FILERESULT = "fileresult";

    /** XML element tag: testCaseInit() parent element.  */
    public static final String ELEM_TESTCASE = "testcase";

    /** XML element tag: testCaseClose() leaf element 
      * emitted immediately before closing ELEM_TESTCASE.
      */
    public static final String ELEM_CASERESULT = "caseresult";

    /** XML element tag: check*() element.  */
    public static final String ELEM_CHECKRESULT = "checkresult";

    /** XML element tag: logStatistic() element.  */
    public static final String ELEM_STATISTIC = "statistic";

    /** XML element tag: logStatistic() child element.  */
    public static final String ELEM_LONGVAL = "longval";

    /** XML element tag: logStatistic() child element.  */
    public static final String ELEM_DOUBLEVAL = "doubleval";

    /** XML element tag: log*Msg() element.  */
    public static final String ELEM_MESSAGE = "message";

    /** XML element tag: logArbitrary() element.  */
    public static final String ELEM_ARBITRARY = "arbitrary";

    /** XML element tag: logHashtable() parent element.  */
    public static final String ELEM_HASHTABLE = "hashtable";

    /** XML element tag: logHashtable() child element.  */
    public static final String ELEM_HASHITEM = "hashitem";

    /** XML attribute tag: log*Msg(), etc. attribute denoting 
     * loggingLevel for that element.
     */
    public static final String ATTR_LEVEL = "level";

    /** XML attribute tag: comment value for various methods.  */
    public static final String ATTR_DESC = "desc";

    /** XML attribute tag: Date.toString() attribute on 
     *  ELEM_TESTFILE and ELEM_FILERESULT.  */
    public static final String ATTR_TIME = "time";

    /** XML attribute tag: PASS|FAIL|etc. attribute on 
     *  ELEM_CHECKRESULT, ELEM_CASERESULT, ELEM_FILERESULT.  
     */
    public static final String ATTR_RESULT = "result";

    /** XML attribute tag: key name for ELEM_HASHITEM.  */
    public static final String ATTR_KEY = "key";

    /** XML attribute tag: actual output filename on ELEM_RESULTSFILE.  */
    public static final String ATTR_FILENAME = OPT_LOGFILE;

    /** XML attribute tag: test filename on ELEM_TESTFILE.  */
    public static final String ATTR_TESTFILENAME = "filename";

    /** Parameter: if we should flush on every testCaseClose().  */
    public static final String OPT_FLUSHONCASECLOSE = "flushOnCaseClose";

    //-----------------------------------------------------
    //-------- Class members and accessors --------
    //-----------------------------------------------------

    /** If we're ready to start outputting yet. */
    protected boolean ready = false;

    /** If an error has occoured in this Logger. */
    protected boolean error = false;

    /** If we should flush on every testCaseClose(). */
    protected boolean flushOnCaseClose = true;

    /**
     * Accessor for flushing; is set from properties.  
     *
     * @return current flushing status
     */
    public boolean getFlushOnCaseClose()
    {
        return (flushOnCaseClose);
    }

    /**
     * Accessor for flushing; is set from properties.  
     *
     * @param b If we should flush on every testCaseClose() 
     */
    public void setFlushOnCaseClose(boolean b)
    {
        flushOnCaseClose = b;
    }

    /** If we have output anything yet. */
    protected boolean anyOutput = false;

    /** Name of the file we're outputing to. */
    protected String fileName = null;

    /** File reference and other internal convenience variables. */
    protected File reportFile;

    /** File reference and other internal convenience variables. */
    protected FileWriter reportWriter;

    /** File reference and other internal convenience variables. */
    protected PrintWriter reportPrinter;

    /** Generic properties for this logger; sort-of replaces instance variables. */
    protected Properties loggerProps = null;

    //-----------------------------------------------------
    //-------- Control and utility routines --------
    //-----------------------------------------------------

    /** Simple constructor, does not perform initialization. */
    public XMLFileLogger()
    { /* no-op */
    }

    /**
     * Constructor calls initialize(p).
     * @param p Properties block to initialize us with.
     */
    public XMLFileLogger(Properties p)
    {
        ready = initialize(p);
    }

    /**
     * Return a description of what this Logger does.
     * @return "reports results in XML to specified fileName".
     */
    public String getDescription()
    {
        return ("org.apache.qetest.XMLFileLogger - reports results in XML to specified fileName.");
    }

    /**
     * Returns information about the Property name=value pairs
     * that are understood by this Logger: fileName=filename.
     * @return same as {@link java.applet.Applet.getParameterInfo}.
     */
    public String[][] getParameterInfo()
    {

        String pinfo[][] =
        {
            { OPT_LOGFILE, "String",
              "Name of file to use for output; required" },
            { OPT_FLUSHONCASECLOSE, "boolean",
              "if we should flush on every testCaseClose(); optional; default:true" }
        };

        return pinfo;
    }

    /**
     * Accessor methods for our properties block.  
     *
     * @return our current properties; may be null
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
     * Initialize this XMLFileLogger.
     * Must be called before attempting to log anything.
     * Opens a FileWriter for our output, and logs Record format:
     * <pre>&lt;resultfile fileName="<i>name of result file</i>"&gt;</pre>
     *
     * If no name provided, supplies a default one in current dir.
     *
     * @param p Properties block to initialize from
     * @return true if we think we initialized OK
     */
    public boolean initialize(Properties p)
    {

        setProperties(p);

        fileName = loggerProps.getProperty(OPT_LOGFILE, fileName);

        if ((fileName == null) || fileName.equals(""))
        {
            // Make up a file name
            fileName = "XMLFileLogger-default-results.xml";
            loggerProps.put(OPT_LOGFILE, fileName);
        }

        // Create a file and ensure it has a place to live; be sure 
        //  to insist on an absolute path for later parent path creation
        reportFile = new File(fileName);
        try
        {
            reportFile = new File(reportFile.getCanonicalPath());
        } 
        catch (IOException ioe1)
        {
            reportFile = new File(reportFile.getAbsolutePath());
        }

        // Note: bare filenames may not have parents, so catch and ignore exceptions
        try
        {
            File parent = new File(reportFile.getParent());
            if ((!parent.mkdirs()) && (!parent.exists()))
            {

                // Couldn't create or find the directory for the file to live in, so bail
                error = true;
                ready = false;

                System.err.println(
                    "XMLFileLogger.initialize() WARNING: cannot create directories: "
                    + fileName);

                // Don't return yet: see if the reportWriter can still create the file later
                // return(false);
            }
        }
        catch (Exception e)
        {

            // No-op: ignore if the parent's not there; trust that the file will get created later
        }

        try
        {
            reportWriter = new FileWriter(reportFile);
        }
        catch (IOException e)
        {
            System.err.println("XMLFileLogger.initialize() EXCEPTION: "
                               + e.toString());
            e.printStackTrace();

            error = true;
            ready = false;

            return false;
        }

        String tmp = loggerProps.getProperty(OPT_FLUSHONCASECLOSE);
        if (null != tmp)
        {
            setFlushOnCaseClose((Boolean.valueOf(tmp)).booleanValue());
        }

        // Use BufferedWriter for better general performance
        reportPrinter = new PrintWriter(new BufferedWriter(reportWriter));
        ready = true;

        return startResultsFile();
    }

    /**
     * Is this Logger ready to log results?
     * @return status - true if it's ready to report, false otherwise
     */
    public boolean isReady()
    {

        // Ensure our underlying logger, if one, is still OK
        if ((reportPrinter != null) && reportPrinter.checkError())
        {

            // NEEDSWORK: should we set ready = false in this case?
            //            errors in the PrintStream are not necessarily fatal
            error = true;
            ready = false;
        }

        return ready;
    }

    /** Flush this logger - ensure our File is flushed. */
    public void flush()
    {

        if (isReady())
        {
            reportPrinter.flush();
        }
    }

    /**
     * Close this logger - ensure our File, etc. are closed.
     * Record format:
     * <pre>&lt;/resultfile&gt;</pre>
     */
    public void close()
    {

        flush();

        if (isReady())
        {
            closeResultsFile();
            reportPrinter.close();
        }

        ready = false;
    }

    /**
     * worker method to dump the xml header and open the resultsfile element.  
     *
     * @return true if ready/OK, false if not ready (meaning we may 
     * not have output anything!)
     */
    protected boolean startResultsFile()
    {

        if (isReady())
        {

            // Write out XML header and root test result element
            reportPrinter.println("<?xml version=\"1.0\"?>");

            // Note: this tag is closed in our .close() method, which the caller had better call!
            reportPrinter.println("<" + ELEM_RESULTSFILE + " "
                                  + ATTR_FILENAME + "=\"" + fileName + "\">");

            return true;
        }
        else
            return false;
    }

    /**
     * worker method to close the resultsfile element.  
     *
     * @return true if ready/OK, false if not ready (meaning we may 
     * not have output a closing tag!)
     */
    protected boolean closeResultsFile()
    {

        if (isReady())
        {
            reportPrinter.println("</" + ELEM_RESULTSFILE + ">");

            return true;
        }
        else
            return false;
    }

    //-----------------------------------------------------
    //-------- Testfile / Testcase start and stop routines --------
    //-----------------------------------------------------

    /**
     * Report that a testfile has started.
     * Begins a testfile element.  Record format:
     * <pre>&lt;testfile desc="<i>test description</i>" time="<i>timestamp</i>"&gt;</pre>
     * @param name file name or tag specifying the test.
     * @param comment comment about the test.
     */
    public void testFileInit(String name, String comment)
    {

        if (isReady())
        {
            reportPrinter.println("<" + ELEM_TESTFILE + " " 
                                  + ATTR_TESTFILENAME + "=\""
                                  + escapeString(name) + "\" " 
                                  + ATTR_DESC + "=\""
                                  + escapeString(comment) + "\" " 
                                  + ATTR_TIME + "=\"" 
                                  + (new Date()).toString() + "\">");
        }
    }

    /**
     * Report that a testfile has finished, and report it's result; flushes output.
     * Ends a testfile element.  Record format:
     * <pre>&lt;fileresult desc="<i>test description</i>" result="<i>pass/fail status</i>" time="<i>timestamp</i>"&gt;
     * &lt;/testfile&gt;</pre>
     * @param msg message to log out
     * @param result result of testfile
     */
    public void testFileClose(String msg, String result)
    {

        if (isReady())
        {
            reportPrinter.println("<" + ELEM_FILERESULT + " " + ATTR_DESC
                                  + "=\"" + escapeString(msg) + "\" "
                                  + ATTR_RESULT + "=\"" + result + "\" "
                                  + ATTR_TIME + "=\""
                                  + (new Date()).toString() + "\"/>");
            reportPrinter.println("</" + ELEM_TESTFILE + ">");
        }

        flush();
    }

    /** Optimization: for heavy use methods, form pre-defined constants to save on string concatenation. */
    private static final String TESTCASEINIT_HDR = "<" + ELEM_TESTCASE + " "
                                                       + ATTR_DESC + "=\"";

    /**
     * Report that a testcase has begun.
     * Begins a testcase element.  Record format:
     * <pre>&lt;testcase desc="<i>case description</i>"&gt;</pre>
     *
     * @param comment message to log out
     */
    public void testCaseInit(String comment)
    {

        if (isReady())
        {
            reportPrinter.println(TESTCASEINIT_HDR + escapeString(comment)
                                  + "\">");
        }
    }

    /** NEEDSDOC Field TESTCASECLOSE_HDR          */
    private static final String TESTCASECLOSE_HDR = "<" + ELEM_CASERESULT
                                                        + " " + ATTR_DESC
                                                        + "=\"";

    /**
     * Report that a testcase has finished, and report it's result.
     * Optionally flushes output. Ends a testcase element.   Record format:
     * <pre>&lt;caseresult desc="<i>case description</i>" result="<i>pass/fail status</i>"&gt;
     * &lt;/testcase&gt;</pre>
     * @param msg message of name of test case to log out
     * @param result result of testfile
     */
    public void testCaseClose(String msg, String result)
    {

        if (isReady())
        {
            reportPrinter.println(TESTCASECLOSE_HDR + escapeString(msg)
                                  + "\" " + ATTR_RESULT + "=\"" + result
                                  + "\"/>");
            reportPrinter.println("</" + ELEM_TESTCASE + ">");
        }

        if (getFlushOnCaseClose())
            flush();
    }

    //-----------------------------------------------------
    //-------- Test results logging routines --------
    //-----------------------------------------------------

    /** NEEDSDOC Field MESSAGE_HDR          */
    private static final String MESSAGE_HDR = "<" + ELEM_MESSAGE + " "
                                              + ATTR_LEVEL + "=\"";

    /**
     * Report a comment to result file with specified severity.
     * Record format: <pre>&lt;message level="##"&gt;msg&lt;/message&gt;</pre>
     * @param level severity or class of message.
     * @param msg comment to log out.
     */
    public void logMsg(int level, String msg)
    {

        if (isReady())
        {
            reportPrinter.print(MESSAGE_HDR + level + "\">");
            reportPrinter.print(escapeString(msg));
            reportPrinter.println("</" + ELEM_MESSAGE + ">");
        }
    }

    /** NEEDSDOC Field ARBITRARY_HDR          */
    private static final String ARBITRARY_HDR = "<" + ELEM_ARBITRARY + " "
                                                + ATTR_LEVEL + "=\"";

    /**
     * Report an arbitrary String to result file with specified severity.
     * Appends and prepends \\n newline characters at the start and
     * end of the message to separate it from the tags.
     * Record format: <pre>&lt;arbitrary level="##"&gt;&lt;![CDATA[
     * msg
     * ]]&gt;&lt;/arbitrary&gt;</pre>
     *
     * Note that arbitrary messages are always wrapped in CDATA
     * sections to ensure that any non-valid XML is wrapped.  This needs
     * to be investigated for other elements as well (i.e. we should set a
     * standard for what Logger calls must be well-formed or not).
     * @param level severity or class of message.
     * @param msg arbitrary String to log out.
     * @todo investigate <b>not</b> fully escaping this string, since
     * it does get wrappered in CDATA
     */
    public void logArbitrary(int level, String msg)
    {

        if (isReady())
        {
            reportPrinter.println(ARBITRARY_HDR + level + "\"><![CDATA[");
            reportPrinter.println(escapeString(msg));
            reportPrinter.println("]]></" + ELEM_ARBITRARY + ">");
        }
    }

    /** NEEDSDOC Field STATISTIC_HDR          */
    private static final String STATISTIC_HDR = "<" + ELEM_STATISTIC + " "
                                                + ATTR_LEVEL + "=\"";

    /**
     * Logs out statistics to result file with specified severity.
     * Record format: <pre>&lt;statistic level="##" desc="msg"&gt;&lt;longval&gt;1234&lt;/longval&gt;&lt;doubleval&gt;1.234&lt;/doubleval&gt;&lt;/statistic&gt;</pre>
     * @param level severity of message.
     * @param lVal statistic in long format.
     * @param dVal statistic in double format.
     * @param msg comment to log out.
     */
    public void logStatistic(int level, long lVal, double dVal, String msg)
    {

        if (isReady())
        {
            reportPrinter.print(STATISTIC_HDR + level + "\" " + ATTR_DESC
                                + "=\"" + escapeString(msg) + "\">");
            reportPrinter.print("<" + ELEM_LONGVAL + ">" + lVal + "</"
                                + ELEM_LONGVAL + ">");
            reportPrinter.print("<" + ELEM_DOUBLEVAL + ">" + dVal + "</"
                                + ELEM_DOUBLEVAL + ">");
            reportPrinter.println("</" + ELEM_STATISTIC + ">");
        }
    }

    /**
     * Logs out Throwable.toString() and a stack trace of the 
     * Throwable with the specified severity.
     * <p>This uses logArbitrary to log out your msg - message, 
     * a newline, throwable.toString(), a newline,
     * and then throwable.printStackTrace().</p>
     * //@todo future work to use logElement() to output 
     * a specific &lt;throwable&gt; element instead.
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param throwable throwable/exception to log out.
     * @param msg description of the throwable.
     */
    public void logThrowable(int level, Throwable throwable, String msg)
    {
        if (isReady())
        {
            StringWriter sWriter = new StringWriter();

            sWriter.write(msg + "\n");
            sWriter.write(throwable.toString() + "\n");

            PrintWriter pWriter = new PrintWriter(sWriter);

            throwable.printStackTrace(pWriter);
            logArbitrary(level, sWriter.toString());
        }
    }

    /**
     * Logs out a element to results with specified severity.
     * Uses user-supplied element name and attribute list.  Currently
     * attribute values and msg are forced .toString().  Also,
     * 'level' is forced to be the first attribute of the element.
     * Record format:
     * <pre>&lt;<i>element_text</i> level="##"
     * attribute1="value1"
     * attribute2="value2"
     * attribute<i>n</i>="value<i>n</i>"&gt;
     * msg
     * &lt;/<i>element_text</i>&gt;</pre>
     * @author Shane_Curcuru@lotus.com
     * @param level severity of message.
     * @param element name of enclosing element
     * @param attrs hash of name=value attributes; note that the
     * caller must ensure they're legal XML
     * @param msg Object to log out .toString(); caller should
     * ensure it's legal XML (no CDATA is supplied)
     */
    public void logElement(int level, String element, Hashtable attrs,
                           Object msg)
    {

        if (isReady()
            && (element != null)
            && (attrs != null)
           )
        {
            reportPrinter.println("<" + element + " " + ATTR_LEVEL + "=\""
                                  + level + "\"");

            for (Enumeration keys = attrs.keys();
                    keys.hasMoreElements(); /* no increment portion */ )
            {
                Object key = keys.nextElement();

                reportPrinter.println(key.toString() + "=\""
                                      + escapeString(attrs.get(key).toString()) + "\"");
            }

            reportPrinter.println(">");
            if (msg != null)
                reportPrinter.println(msg.toString());
            reportPrinter.println("</" + element + ">");
        }
    }

    /** NEEDSDOC Field HASHTABLE_HDR          */
    private static final String HASHTABLE_HDR = "<" + ELEM_HASHTABLE + " "
                                                + ATTR_LEVEL + "=\"";

    // Note the HASHITEM_HDR indent; must be updated if we ever switch to another indenting method.

    /** NEEDSDOC Field HASHITEM_HDR          */
    private static final String HASHITEM_HDR = "  <" + ELEM_HASHITEM + " "
                                               + ATTR_KEY + "=\"";

    /**
     * Logs out contents of a Hashtable with specified severity.
     * Indents each hashitem within the table.
     * Record format: <pre>&lt;hashtable level="##" desc="msg"/&gt;
     * &nbsp;&nbsp;&lt;hashitem key="key1"&gt;value1&lt;/hashitem&gt;
     * &nbsp;&nbsp;&lt;hashitem key="key2"&gt;value2&lt;/hashitem&gt;
     * &lt;/hashtable&gt;</pre>
     *
     * @param level severity or class of message.
     * @param hash Hashtable to log the contents of.
     * @param msg decription of the Hashtable.
     */
    public void logHashtable(int level, Hashtable hash, String msg)
    {

        if (isReady())
        {
            reportPrinter.println(HASHTABLE_HDR + level + "\" " + ATTR_DESC
                                  + "=\"" + escapeString(msg) + "\">");

            if (hash == null)
            {
                reportPrinter.print("<" + ELEM_HASHITEM + " " + ATTR_KEY
                                    + "=\"null\">");
                reportPrinter.println("</" + ELEM_HASHITEM + ">");
            }

            try
            {
                for (Enumeration keys = hash.keys();
                        keys.hasMoreElements(); /* no increment portion */ )
                {
                    Object key = keys.nextElement();

                    // Ensure we'll have clean output by pre-fetching value before outputting anything
                    String value = escapeString(hash.get(key).toString());

                    reportPrinter.print(HASHITEM_HDR + escapeString(key.toString())
                                        + "\">");
                    reportPrinter.print(value);
                    reportPrinter.println("</" + ELEM_HASHITEM + ">");
                }
            }
            catch (Exception e)
            {

                // No-op: should ensure we have clean output
            }

            reportPrinter.println("</" + ELEM_HASHTABLE + ">");
        }
    }

    //-----------------------------------------------------
    //-------- Test results reporting check* routines --------
    //-----------------------------------------------------

    /** NEEDSDOC Field CHECKPASS_HDR          */
    private static final String CHECKPASS_HDR = "<" + ELEM_CHECKRESULT + " "
                                                + ATTR_RESULT + "=\""
                                                + Reporter.PASS + "\" "
                                                + ATTR_DESC + "=\"";

    /**
     * Writes out a Pass record with comment.
     * Record format: <pre>&lt;checkresult result="PASS" desc="comment"/&gt;</pre>
     * @param comment comment to log with the pass record.
     */
    public void checkPass(String comment)
    {

        if (isReady())
        {
            reportPrinter.println(CHECKPASS_HDR + escapeString(comment)
                                  + "\"/>");
        }
    }

    /** NEEDSDOC Field CHECKAMBG_HDR          */
    private static final String CHECKAMBG_HDR = "<" + ELEM_CHECKRESULT + " "
                                                + ATTR_RESULT + "=\""
                                                + Reporter.AMBG + "\" "
                                                + ATTR_DESC + "=\"";

    /**
     * Writes out an ambiguous record with comment.
     * Record format: <pre>&lt;checkresult result="AMBG" desc="comment"/&gt;</pre>
     * @param comment comment to log with the ambg record.
     */
    public void checkAmbiguous(String comment)
    {

        if (isReady())
        {
            reportPrinter.println(CHECKAMBG_HDR + escapeString(comment)
                                  + "\"/>");
        }
    }

    /** NEEDSDOC Field CHECKFAIL_HDR          */
    private static final String CHECKFAIL_HDR = "<" + ELEM_CHECKRESULT + " "
                                                + ATTR_RESULT + "=\""
                                                + Reporter.FAIL + "\" "
                                                + ATTR_DESC + "=\"";

    /**
     * Writes out a Fail record with comment.
     * Record format: <pre>&lt;checkresult result="FAIL" desc="comment"/&gt;</pre>
     * @param comment comment to log with the fail record.
     */
    public void checkFail(String comment)
    {

        if (isReady())
        {
            reportPrinter.println(CHECKFAIL_HDR + escapeString(comment)
                                  + "\"/>");
        }
    }

    /** NEEDSDOC Field CHECKERRR_HDR          */
    private static final String CHECKERRR_HDR = "<" + ELEM_CHECKRESULT + " "
                                                + ATTR_RESULT + "=\""
                                                + Reporter.ERRR + "\" "
                                                + ATTR_DESC + "=\"";

    /**
     * Writes out a Error record with comment.
     * Record format: <pre>&lt;checkresult result="ERRR" desc="comment"/&gt;</pre>
     * @param comment comment to log with the error record.
     */
    public void checkErr(String comment)
    {

        if (isReady())
        {
            reportPrinter.println(CHECKERRR_HDR + escapeString(comment)
                                  + "\"/>");
        }
    }

    /* EXPERIMENTAL: have duplicate set of check*() methods 
       that all output some form of ID as well as comment. 
       Leave the non-ID taking forms for both simplicity to the 
       end user who doesn't care about IDs as well as for 
       backwards compatibility.
    */

    /** ID_ATTR optimization for extra ID attribute.  */
    private static final String ATTR_ID = "\" id=\"";
    /**
     * Writes out a Pass record with comment.
     * Record format: <pre>&lt;checkresult result="PASS" desc="comment"/&gt;</pre>
     * @param comment comment to log with the pass record.
     * @param ID token to log with the pass record.
     */
    public void checkPass(String comment, String id)
    {

        if (isReady())
        {
            StringBuffer tmp = new StringBuffer(CHECKPASS_HDR + escapeString(comment));
            if (id != null)
                tmp.append(ATTR_ID + escapeString(id));
                
            tmp.append("\"/>");
            reportPrinter.println(tmp);
        }
    }

    /**
     * Writes out an ambiguous record with comment.
     * Record format: <pre>&lt;checkresult result="AMBG" desc="comment"/&gt;</pre>
     * @param comment comment to log with the ambg record.
     * @param ID token to log with the pass record.
     */
    public void checkAmbiguous(String comment, String id)
    {

        if (isReady())
        {
            StringBuffer tmp = new StringBuffer(CHECKAMBG_HDR + escapeString(comment));
            if (id != null)
                tmp.append(ATTR_ID + escapeString(id));
                
            tmp.append("\"/>");
            reportPrinter.println(tmp);
        }
    }

    /**
     * Writes out a Fail record with comment.
     * Record format: <pre>&lt;checkresult result="FAIL" desc="comment"/&gt;</pre>
     * @param comment comment to log with the fail record.
     * @param ID token to log with the pass record.
     */
    public void checkFail(String comment, String id)
    {

        if (isReady())
        {
            StringBuffer tmp = new StringBuffer(CHECKFAIL_HDR + escapeString(comment));
            if (id != null)
                tmp.append(ATTR_ID + escapeString(id));
                
            tmp.append("\"/>");
            reportPrinter.println(tmp);
        }
    }

    /**
     * Writes out a Error record with comment.
     * Record format: <pre>&lt;checkresult result="ERRR" desc="comment"/&gt;</pre>
     * @param comment comment to log with the error record.
     * @param ID token to log with the pass record.
     */
    public void checkErr(String comment, String id)
    {

        if (isReady())
        {
            StringBuffer tmp = new StringBuffer(CHECKERRR_HDR + escapeString(comment));
            if (id != null)
                tmp.append(ATTR_ID + escapeString(id));
                
            tmp.append("\"/>");
            reportPrinter.println(tmp);
        }
    }

    //-----------------------------------------------------
    //-------- Worker routines for XML string escaping --------
    //-----------------------------------------------------

    /**
     * Lifted from org.apache.xml.serialize.transition.XMLSerializer
     *
     * @param ch character to get entity ref for
     * @return String of entity name
     */
    public static String getEntityRef(char ch)
    {

        // Encode special XML characters into the equivalent character references.
        // These five are defined by default for all XML documents.
        switch (ch)
        {
        case '<' :
            return "lt";
        case '>' :
            return "gt";
        case '"' :
            return "quot";
        case '\'' :
            return "apos";
        case '&' :
            return "amp";
        }

        return null;
    }

    /**
     * Identifies the last printable character in the Unicode range
     * that is supported by the encoding used with this serializer.
     * For 8-bit encodings this will be either 0x7E or 0xFF.
     * For 16-bit encodings this will be 0xFFFF. Characters that are
     * not printable will be escaped using character references.
     * Lifted from org.apache.xml.serialize.transition.BaseMarkupSerializer
     */
    public static int _lastPrintable = 0x7E;

    /**
     * Lifted from org.apache.xml.serialize.transition.BaseMarkupSerializer
     *
     * @param ch character to escape
     * @return String that is escaped
     */
    public static String printEscaped(char ch)
    {

        String charRef;

        // If there is a suitable entity reference for this
        // character, print it. The list of available entity
        // references is almost but not identical between
        // XML and HTML.
        charRef = getEntityRef(ch);

        if (charRef != null)
        {

            //_printer.printText( '&' );        // SC note we need to return a String for 
            //_printer.printText( charRef );    //    someone else to serialize
            //_printer.printText( ';' );
            return "&" + charRef + ";";
        }
        else if ((ch >= ' ' && ch <= _lastPrintable && ch != 0xF7)
                 || ch == '\n' || ch == '\r' || ch == '\t')
        {

            // If the character is not printable, print as character reference.
            // Non printables are below ASCII space but not tab or line
            // terminator, ASCII delete, or above a certain Unicode threshold.
            //_printer.printText( ch );
            return String.valueOf(ch);
        }
        else
        {

            //_printer.printText( "&#" );
            //_printer.printText( Integer.toString( ch ) );
            //_printer.printText( ';' );
            return "&#" + Integer.toString(ch) + ";";
        }
    }

    /**
     * Escapes a string so it may be printed as text content or attribute
     * value. Non printable characters are escaped using character references.
     * Where the format specifies a deault entity reference, that reference
     * is used (e.g. <tt>&amp;lt;</tt>).
     * Lifted from org.apache.xml.serialize.transition.BaseMarkupSerializer
     *
     * @param source The string to escape
     * @return String after escaping - needed for our application
     */
    public static String escapeString(String source)
    {
        // Check for null; just return null (callers shouldn't care)
        if (source == null)
        {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        final int n = source.length();

        for (int i = 0; i < n; ++i)
        {

            //char c = source.charAt( i );
            sb.append(printEscaped(source.charAt(i)));
        }

        return sb.toString();
    }
}  // end of class XMLFileLogger

