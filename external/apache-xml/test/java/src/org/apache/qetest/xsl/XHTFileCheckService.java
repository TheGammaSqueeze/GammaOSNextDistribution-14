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
 * XHTFileCheckService.java
 *
 */
package org.apache.qetest.xsl;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.qetest.CheckService;
import org.apache.qetest.ConsoleLogger;
import org.apache.qetest.Logger;
import org.apache.qetest.XMLFileLogger;

/**
 * Uses an XML/HTML/Text diff comparator to check or diff two files.
 * @see #check(Logger logger, Object actual, Object reference, String msg, String id)
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class XHTFileCheckService implements CheckService
{

    /** XHTComparator tool to diff two files. */
    protected XHTComparator comparator = new XHTComparator();

    /** Stores last checkFile calls printed output. */
    private StringWriter sw = null;

    /**
     * Compare two objects for equivalence, and return appropriate result.
     * Note that the order of actual, reference is important
     * important in determining the result.
     * <li>Typically:
     * <ul>any unexpected Exceptions thrown -> ERRR_RESULT</ul>
     * <ul>actual does not exist -> FAIL_RESULT</ul>
     * <ul>reference does not exist -> AMBG_RESULT</ul>
     * <ul>actual is equivalent to reference -> PASS_RESULT</ul>
     * <ul>actual is not equivalent to reference -> FAIL_RESULT</ul>
     * </li>
     * Equvalence is first checked by parsing both files as XML to 
     * a DOM; if that has problems, we parse as HTML to a DOM; if 
     * that has problems, we create a DOM with a single text node 
     * after reading the file as text.  We then compare the two DOM 
     * trees for equivalence. Note that in XML mode differences in 
     * the XML header itself (i.e. standalone=no/yes) are not caught,
     * and will still report a pass (this is a defect in our 
     * comparison method).
     * Side effect: every call to check() fills some additional 
     * info about how the check() call was processed which is then 
     * returned from getExtendedInfo() - this happens no matter what 
     * the result of the check() call was.
     *
     * @param logger to dump any output messages to
     * @param actual (current) Object to check
     * @param reference (gold, or expected) Object to check against
     * @param description of what you're checking
     * @param msg comment to log out with this test point
     * @param id ID tag to log out with this test point
     * @return Logger.*_RESULT code denoting status; each method may 
     * define it's own meanings for pass, fail, ambiguous, etc.
     */
	public  XHTFileCheckService()
	{
	  //No-op
	}

    public int check(Logger logger, Object actual, Object reference,
                     String msg, String id)
    {
        // Create our 'extended info' stuff now, so it will 
        //  always reflect the most recent call to this method
        sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        if (((null == actual) || (null == reference )))
        {
            pw.println("XHTFileCheckService actual or reference was null!");
            pw.flush();
            logFileCheckElem(logger, "null", "null", msg, id, sw.toString());
            logger.checkErr(msg, id);
            return logger.ERRR_RESULT;
        }
        if (!((actual instanceof File) & (reference instanceof File)))
        {
            // Must have File objects to continue
            pw.println("XHTFileCheckService only takes File objects!");
            pw.flush();
            logFileCheckElem(logger, actual.toString(), reference.toString(), msg, id, sw.toString());
            logger.checkErr(msg, id);
            return logger.ERRR_RESULT;
        }

        File actualFile = (File) actual;
        File referenceFile = (File) reference;

        // Fail if Actual file doesn't exist or is 0 len
        if ((!actualFile.exists()) || (actualFile.length() == 0))
        {
            pw.println("actual(" + actualFile.toString() + ") did not exist or was 0 len");
            pw.flush();
            logFileCheckElem(logger, actualFile.toString(), referenceFile.toString(), msg, id, sw.toString());
            logger.checkFail(msg, id);
            return logger.FAIL_RESULT;
        }

        // Ambiguous if gold file doesn't exist or is 0 len
        if ((!referenceFile.exists()) || (referenceFile.length() == 0))
        {
            pw.println("reference(" + referenceFile.toString() + ") did not exist or was 0 len");
            pw.flush();
            logFileCheckElem(logger, actualFile.toString(), referenceFile.toString(), msg, id, sw.toString());
            logger.checkAmbiguous(msg, id);
            return Logger.AMBG_RESULT;
        }

        boolean warning[] = new boolean[1];
        warning[0] = false;
        boolean isEqual = false;

        // Inefficient code to get around very rare spurious exception:
        // java.io.IOException: The process cannot access the file because it is being used by another process
	    //    at java.io.Win32FileSystem.canonicalize(Native Method)
	    //    at java.io.File.getCanonicalPath(File.java:442)
	    //    at org.apache.qetest.xsl.XHTFileCheckService.check(XHTFileCheckService.java:181)
        // So get filenames first separately, then call comparator
        String referenceFileName = referenceFile.getAbsolutePath();
        String actualFileName = actualFile.getAbsolutePath();
        try
        {
            referenceFileName = referenceFile.getCanonicalPath();
            // Occasional spurious exception happens here, perhaps 
            //  because sometimes the previous transform or whatever 
            //  hasn't quite closed the actualFile yet
            actualFileName = actualFile.getCanonicalPath();
        } 
        catch (Exception e) { /* no-op, ignore */ }
        
        try
        {
            // Note calling order (gold, act) is different than checkFiles()
            isEqual = comparator.compare(referenceFileName,
                                         actualFileName, pw,
                                         warning, attributes);
            // Side effect: fills in pw/sw with info about the comparison
        }
        catch (Throwable t)
        {
            // Add any exception info to pw/sw; this will automatically 
            //  get logged out later on via logFileCheckElem
            pw.println("XHTFileCheckService threw: " + t.toString());
            t.printStackTrace(pw);
            isEqual = false;
        }

        // If not equal at all, fail
        if (!isEqual)
        {
            pw.println("XHTFileCheckService files were not equal");
            pw.flush();
            logFileCheckElem(logger, actualFile.toString(), referenceFile.toString(), msg, id, sw.toString());
            logger.checkFail(msg, id);
            return Logger.FAIL_RESULT;
        }
        // If whitespace-only diffs, then pass/fail based on allowWhitespaceDiff
        else if (warning[0])
        {
            pw.println("XHTFileCheckService whitespace diff warning!");
            pw.flush();
            if (allowWhitespaceDiff)
            {
                logger.logMsg(Logger.STATUSMSG, "XHTFileCheckService whitespace diff warning, passing!");
                logger.checkPass(msg, id);
                return Logger.PASS_RESULT;
            }
            else
            {
                logFileCheckElem(logger, actualFile.toString(), referenceFile.toString(), msg, id, 
                        "XHTFileCheckService whitespace diff warning, failing!\n" + sw.toString());
                logger.checkFail(msg, id);
                return Logger.FAIL_RESULT;
            }
        }
        // Otherwise we were completely equal, so pass
        else
        {
            pw.println("XHTFileCheckService files were equal");
            pw.flush();
            // For pass case, we *dont* call logFileCheckElem
            logger.checkPass(msg, id);
            return Logger.PASS_RESULT;
        }
    }

    /**
     * Logs a custom element about the current check() call.  
     * <pre>
     * <fileCheck level="40"
     * reference="tests\conf-gold\match\match16.out"
     * reportedBy="XHTFileCheckService"
     * actual="results-alltest\dom\match\match16.out"
     * >
     * StylesheetTestlet match16.xsl(null) 
     * XHTFileCheckService threw: java.io.IOException: The process cannot access the file because it is being used by another process
     * java.io.IOException: The process cannot access the file because it is being used by another process
     * 	at java.io.Win32FileSystem.canonicalize(Native Method)
     *     etc...
     * XHTFileCheckService files were not equal
     * 
     * </fileCheck>
     * </pre>     
     * @param logger to dump any output messages to
     * @param name of actual (current) File to check
     * @param name of reference (gold, or expected) File to check against
     * @param msg comment to log out with this test point
     * @param id to log out with this test point
     * @param additional log info from PrintWriter/StringWriter
     */
    protected void logFileCheckElem(Logger logger, 
            String actualFile, String referenceFile,
            String msg, String id, String logs)
    {
        Hashtable attrs = new Hashtable();
        attrs.put("actual", actualFile);
        attrs.put("reference", referenceFile);
        attrs.put("reportedBy", "XHTFileCheckService");
        try
        {
            attrs.put("baseref", System.getProperty("user.dir"));
        } 
        catch (Exception e) { /* no-op, ignore */ }
        String elementBody = msg + "(" + id + ") \n" + logs;
        // HACK: escapeString(elementBody) so that it's legal XML
        //  for cases where we have XML output.  This isn't 
        //  necessarily a 'hack', I'm just not sure what the 
        //  cleanest place to put this is (here or some sort 
        //  of intelligent logic in XMLFileLogger)
        elementBody = XMLFileLogger.escapeString(elementBody);
		logger.logElement(Logger.STATUSMSG, "fileCheck", attrs, elementBody);
    }

    /**
     * Compare two objects for equivalence, and return appropriate result.
     *
     * @see #check(Logger logger, Object actual, Object reference, String msg, String id)
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
     * Prefix to all attrs we understand.  
     * Note: design-wise, it would be better to have these constants 
     * in the XHTComparator class, since we know we're tightly bound 
     * to them anyways, and they shouldn't really be bound to us.
     * But for my current purposes, it's simpler to put them here 
     * for documentation purposes.
     */
    public static final String URN_XHTFILECHECKSERVICE = "urn:XHTFileCheckService:";

    /** Whether whitespace differences will cause a fail or not.  */
    public static final String ALLOW_WHITESPACE_DIFF = URN_XHTFILECHECKSERVICE + "allowWhitespaceDiff";

    /** If we should call parser.setValidating().  */
    public static final String SETVALIDATING = URN_XHTFILECHECKSERVICE + "setValidating";

    /** If we should call parser.setIgnoringElementContentWhitespace().  */
    public static final String SETIGNORINGELEMENTCONTENTWHITESPACE = URN_XHTFILECHECKSERVICE + "setIgnoringElementContentWhitespace";

    /** If we should call parser.setExpandEntityReferences().  */
    public static final String SETEXPANDENTITYREFERENCES = URN_XHTFILECHECKSERVICE + "setExpandEntityReferences";

    /** If we should call parser.setIgnoringComments().  */
    public static final String SETIGNORINGCOMMENTS = URN_XHTFILECHECKSERVICE + "setIgnoringComments";

    /** If we should call parser.setCoalescing().  */
    public static final String SETCOALESCING = URN_XHTFILECHECKSERVICE + "setCoalescing";

    /**
     * Whether whitespace differences will cause a fail or not.  
     * setAttribute("allow-whitespace-diff", true|false)
     * true=whitespace-only diff will pass;
     * false, whitespace-only diff will fail
     */
    protected boolean allowWhitespaceDiff = false;  // default; backwards compatible

    /**
     * Properties/Hash of parser-like attributes that have been set.  
     */
    protected Properties attributes = null;

    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * Supports basic JAXP DocumentBuilder attributes, plus our own 
     * ALLOW_WHITESPACE_DIFF attribute.
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
        // Check for our own attributes first
        if (ALLOW_WHITESPACE_DIFF.equals(name))
        {
            try
            {
                allowWhitespaceDiff = (new Boolean((String)value)).booleanValue();
            } 
            catch (Throwable t)
            {
                // If it's an illegal value or type, ignore it
            }
        }
        else
        {
            if (null == attributes)
            {
                attributes = new Properties();
            }
            attributes.put(name, value);
        }
    }

    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * This method should attempt to set any applicable attributes 
     * found in the given attrs onto itself, and will ignore any and 
     * all attributes it does not recognize.  It should never 
     * throw exceptions.  This method will overwrite any previous 
     * attributes that were set.
     * This method will only set values that are Strings findable 
     * by the Properties.getProperty() method.
     * 
     * Future Work: this could be optimized by simply setting our 
     * Properties block to default from the passed-in one, but for 
     * now instead it only copies over the explicit values that 
     * we think are applicable.
     * 
     * @param attrs Props of various name, value attrs.
     */
    public void applyAttributes(Properties attrs)
    {
        attributes = null;
        for (Enumeration names = attrs.propertyNames();
                names.hasMoreElements(); /* no increment portion */ )
        {
            String key = (String)names.nextElement();
            if (key.startsWith(URN_XHTFILECHECKSERVICE))
            {
                setAttribute(key, attrs.getProperty(key));
            }
        }
    }

    /**
     * Allows the user to retrieve specific attributes on the testing 
     * utility or it's underlying product object under test.
     *
     * See applyAttributes for some limitations.
     *
     * @param name The name of the attribute.
     * @return value of supported attributes or null if not recognized.
     * @throws IllegalArgumentException thrown if the underlying
     * implementation doesn't recognize the attribute and wants to 
     * inform the user of this fact.
     */
    public Object getAttribute(String name)
        throws IllegalArgumentException
    {
        // Check for our own attributes first
        if (ALLOW_WHITESPACE_DIFF.equals(name))
        {
            return new Boolean(allowWhitespaceDiff);
        }
        else if (null != attributes)
        {
            return attributes.get(name);
        }
        else
            return null;
    }

    /**
     * Description of what this testing utility does.  
     * 
     * @return String description of extension
     */
    public String getDescription()
    {
        return ("Uses an XML/HTML/Text diff comparator to check or diff two files.");
    }

    /**
     * Gets extended information about the last check call.
     * This info is filled in for every call to check() with brief
     * descriptions of what happened; will return 
     * <code>XHTFileCheckService-no-info-available</code> if 
     * check() has never been called.
     * @return String describing any additional info about the 
     * last two files that were checked
     */
    public String getExtendedInfo()
    {

        if (sw != null)
            return sw.toString();
        else
            return "XHTFileCheckService-no-info-available";
    }

    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public  static void main(String[] args)
    {
		if (args.length < 2)
		{
			System.out.println("	Please provide two files to compare");
		}
		else
		{
			ConsoleLogger log = new ConsoleLogger();
			XHTFileCheckService app = new XHTFileCheckService();
			System.out.println("\nThank you for using XHTFileCheckService");
			System.out.println( app.getDescription() );
			System.out.println("We hope your results are satisfactory");
			System.out.println("\n" + args[0] + "  " + args[1]);

			File fAct = new File(args[0]);
			File fExp = new File(args[1]);
		
			try
			{
				app.check(log, fAct, fExp, "Check");
			}
			catch (Exception e) 
			{ 
			   System.out.println ("main() caught unexpected Exception"); 
			}
		}
    }
}  // end of class XHTFileCheckService

