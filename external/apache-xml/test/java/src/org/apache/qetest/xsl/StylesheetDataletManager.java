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

package org.apache.qetest.xsl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

import org.apache.qetest.Logger;

/**
 * Simple services for getting lists of StylesheetDatalets.
 * 
 *
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public abstract class StylesheetDataletManager // provide static services only
{

    /** 
     * Token in xsl file denoting the text of an expected exception.  
     * Used in getInfoItem.
     */
    public static final String INFOITEM_EXPECTED_EXCEPTION = "ExpectedException:";

    /**
     * Get specified information about the datalet.
     * 
     * <p>Note that different kinds of information may be read 
     * or discovered in different ways.</p>
     *
     * <p>Currently only implemented for 
     * INFOITEM_EXPECTED_EXCEPTION.</p>
     *
     * @param logger to report problems to
     * @param datalet to get information about
     * @param infoItem to get information about
     * @return Vector of object(s) of appropriate type; 
     * or null if error
     */
    public static Vector getInfoItem(Logger logger, StylesheetDatalet datalet, String infoItem)
    {
        if ((null == datalet) || (null == infoItem))
        {
            logger.logMsg(Logger.ERRORMSG, "getTestInfo called with null datalet or infoItem!");
            return null;
        }
        if (INFOITEM_EXPECTED_EXCEPTION.equals(infoItem))
        {
            return getExpectedException(logger, datalet);
        }
        else
        {
            logger.logMsg(Logger.WARNINGMSG, "getTestInfo unsupported infoItem: " + infoItem);
            return null;
        }
    }


    /**
     * Worker method to get expected exception text about a stylesheet.
     * 
     * Currently parses the inputDir stylesheet for a line that contains 
     * EXPECTED_EXCEPTION inside an xsl comment, on a single line, and 
     * trims off the closing comment -->.
     * Future work: allow options on datalet to specify some other 
     * expected data in another format - a whole Throwable object to 
     * compare to, or a stacktrace, etc.
     * 
     * @author Shane Curcuru
     * @param d Datalet that contains info about the exception
     * @return Vector of Strings denoting toString of exception(s)
     * we might expect - any one of them will pass; null if error
     */
    protected static Vector getExpectedException(Logger logger, StylesheetDatalet d)
    {
        final String EXPECTED_EXCEPTION_END = "-->";
        Vector v = null;
        // Read in the testName file to see if it's expecting something        
        try
        {
            FileReader fr = new FileReader(d.inputName);
            BufferedReader br = new BufferedReader(fr);
            for (;;)
            {
                String inbuf = br.readLine();

                if (inbuf == null)
                    break;  // end of file, break out and return

                int idx = inbuf.indexOf(INFOITEM_EXPECTED_EXCEPTION);

                if (idx < 0)
                    continue;  // not on this line, keep going

                // The expected exception.getMessage is the rest of the line...
                String expExc = inbuf.substring(idx + INFOITEM_EXPECTED_EXCEPTION.length(),
                                         inbuf.length());

                // ... less the trailing " -->" comment end; trimmed
                int endComment = expExc.indexOf(EXPECTED_EXCEPTION_END);
                if (endComment > -1)
                    expExc = expExc.substring(0, endComment).trim();
                else
                    expExc = expExc.trim();

                if (null == v)
                    v = new Vector(); // only create if needed
                v.addElement(expExc);

                // Continue reading the file for more potential
                //  expected exception strings - read them all
                //@todo optimization: stop parsing after xx lines?

            }  // end for (;;)
        }
        catch (java.io.IOException ioe)
        {
            logger.logMsg(Logger.ERRORMSG, "getExpectedException() threw: "
                                   + ioe.toString());
            return null;
        }
        return v;
    }


    /** '[' character, first char in first line of xsltmark fileList.  */
    public static final String XSLTMARK_CHAR = "[";

    /** '#' character, comment char in qetest fileList.  */
    public static final String QETEST_COMMENT_CHAR = "#";

    /**
     * Read in a file specifying a list of files to test.
     * <p>File format is determined from first line in file, 
     * which is a little bit dangerous!</p>
     * <p>If first line starts with '[', it's an xsltmark-style 
     * fileList, otherwise it's a qetest-style fileList.</p>
     *
     * @param logger to report problems to
     * @param fileName String; name of the file
     * @param desc description; caller's copy changed
     * @param defaults default properties to potentially add to each datalet
     * @return Vector of StylesheetDatalets, or null if error
     */
    public static Vector readFileList(Logger logger, String fileName, String desc, Properties defaults)
    {
        // Verify the file is there
        File f = new File(fileName);
        if (!f.exists())
        {
            logger.logMsg(Logger.ERRORMSG, "readFileList: " + fileName  
                          + " does not exist!");
            return null;
        }

        BufferedReader br = null;
        String line = null;
        try
        {
            br = new BufferedReader(new FileReader(f));
            line = br.readLine(); // read just first line
        }
        catch (IOException ioe)
        {
            logger.logMsg(Logger.ERRORMSG, "readFileList: " + fileName 
                          + " threw: " + ioe.toString());
            return null;
        }

        // Verify the first line
        if (line == null)
        {
            logger.logMsg(Logger.ERRORMSG, "readFileList: " + fileName
                          + " appears to be blank!");
            return null;
        }

        // Determine which kind of fileList this is
        //  we support the 'native' org.apache.qetest format, and 
        //  alternately the .ini file format used in xsltmark
        Vector vec = null;
        if (line.startsWith(XSLTMARK_CHAR))
        {
            // This is an xsltmark .ini style file
            vec = readXsltmarkFileList(logger, br, line, fileName, desc, defaults);
        }
        else if (line.startsWith(QETEST_COMMENT_CHAR))
        {
            // This is a native qetest style file
            vec = readQetestFileList(logger, br, line, fileName, desc, defaults);
        }
        else
        {
            logger.logMsg(Logger.WARNINGMSG, "readFileList: " + fileName
                          + " could not determine file type; assuming qetest!");
            vec = readQetestFileList(logger, br, line, fileName, desc, defaults);
        }

        if (vec.size() == 0)
        {
            logger.logMsg(Logger.ERRORMSG, "readFileList: " + fileName
                          + " did not have any non-comment lines!");
            return null;
        }
        return vec;
    }
    
    /**
     * Read in a qetest fileList specifying a list of files to test.
     * <p>File format is pretty simple:</p>
     * <ul>
     * <li># first line of comments is copied into desc</li>
     * <li># beginning a line is a comment</li>
     * <li># rest of lines are whitespace delimited filenames and options</li>
     * <li>inputName xmlName outName goldName flavor options...</li>
     * <li><b>Note:</b> see {@link StylesheetDatalet} for
     * details on how the file lines are parsed!</li>
     * </ul>
     * <p>Most items are optional, but not having them may result 
     * in validation oddities.  Future work would be to coordinate 
     * this with various Datalet's implementations of .load() so 
     * that Datalets can do better defaulting of non-provided 
     * items; or maybe so that a user can specific a default 'mask' 
     * of values to use for unspecified items.</p>
     *
     * @param logger to report problems to
     * @param br BufferedReader to read from
     * @param firstLine already read from br
     * @param fileName String; name of the file
     * @param desc to use of this file
     * @param defaults default properties to potentially add to each datalet
     * @return Vector of StylesheetDatalets, or null if error
     */
    protected static Vector readQetestFileList(Logger logger, BufferedReader br, 
                                               String firstLine, String fileName, 
                                               String desc, Properties defaults)
    {
        final String ABSOLUTE = "absolute";
        final String RELATIVE = "relative";

        Vector vec = new Vector();
        String line = firstLine;
        // Check if the first line is a comment 
        if (line.startsWith(QETEST_COMMENT_CHAR))
        {
            // Save it as the description
            desc = line;
            // Parse the next line
            try
            {
                line = br.readLine();
            } 
            catch (IOException ioe)
            {
                logger.logMsg(Logger.ERRORMSG, "readQetestFileList: " 
                              + fileName + " threw: " + ioe.toString());
                return null;
            }
        }

        // Load each line into a StylesheetDatalet
        for (;;)
        {
            // Skip any lines beginning with # comment char or that are blank
            if ((!line.startsWith(QETEST_COMMENT_CHAR)) && (line.length() > 0))
            {
                // Create a Datalet and initialize with the line's 
                //  contents and default properties
                StylesheetDatalet d = new StylesheetDatalet(line, defaults);

                // Also pass over the global runId, if set
                d.options.put("runId", defaults.getProperty("runId"));

                //@todo Avoid spurious passes when output & gold not specified
                //  needs to detect when StylesheetDatalet doesn't 
                //  properly have outputName and goldName set

                // Add it to our vector
                vec.addElement(d);
            }

            // Read next line and loop
            try
            {
                line = br.readLine();
            }
            catch (IOException ioe2)
            {
                // Just force us out of the loop; if we've already 
                //  read part of the file, fine
                logger.logMsg(Logger.WARNINGMSG, "readQetestFileList: " 
                              + fileName + " threw: " + ioe2.toString());
                break;
            }

            if (line == null)
                break;
        } // end of for (;;)
        return vec;
    }

    /**
     * Read in an xsltmark fileList specifying a list of files to test.
     * <p>File format is an .ini file like so:</p>
     * <pre>
     * [avts]
     * input=db100.xml
     * stylesheet=avts.xsl
     * output=avts.out
     * reference=avts.ref
     * iterations=100
     * </pre>
     * <p>Note that additional attributes will be logged as warnings
     * and will be ignored.</p>
     *
     * @param logger to report problems to
     * @param br BufferedReader to read from
     * @param firstLine already read from br
     * @param fileName String; name of the file
     * @param desc to use of this file
     * @param defaults default properties to potentially add to each datalet
     * @return Vector of StylesheetDatalets, or null if error
     */
    protected static Vector readXsltmarkFileList(Logger logger, BufferedReader br, 
                                                 String firstLine, String fileName, 
                                                 String desc, Properties defaults)
    {
        Vector vec = new Vector();
        String line = firstLine;
        // Parse each line and build a datalet
        for (;;)
        {
            // If we're starting a section, parse the section to a datalet
            if (line.startsWith(XSLTMARK_CHAR))
            {
                StylesheetDatalet d = readXsltmarkDatalet(logger, br, line, fileName, desc, defaults);

                // Also pass over the global runId, if set
                d.options.put("runId", defaults.getProperty("runId"));

                // Add datalet to our vector
                vec.addElement(d);
            }
            // Skip blank lines
            else if (line.length() == 0)
            {
                /* no-op */
            }
            // Ooops, readXsltmarkDatalet didn't work right
            else
            {
                logger.logMsg(Logger.WARNINGMSG, "readXsltmarkFileList parse error, unknown line: " 
                              + line);
            }

            // Read next line and loop
            try
            {
                line = br.readLine();
            }
            catch (IOException ioe2)
            {
                // Just force us out of the loop; if we've already 
                //  read part of the file, fine
                logger.logMsg(Logger.WARNINGMSG, "readXsltmarkFileList: " 
                              + fileName + " threw: " + ioe2.toString());
                break;
            }

            if (line == null)
                break;
        } // end of for (;;)
        return vec;
    }

    /**
     * Read in an xsltmark fileList specifying a list of files to test.
     * <p>File format is an .ini file</p>
     *
     * @param logger to report problems to
     * @param br BufferedReader to read from
     * @param firstLine already read from br
     * @param fileName String; name of the file
     * @param desc to use of this file
     * @param defaults default properties to potentially add to each datalet
     * @return StylesheetDatalet with appropriate data, or null if error
     */
    private static StylesheetDatalet readXsltmarkDatalet(Logger logger, BufferedReader br, 
                                                String firstLine, String fileName, 
                                                String desc, Properties defaults)
    {
        final String STYLESHEET_MARKER = "stylesheet=";
        final String INPUT_MARKER = "input=";
        final String OUTPUT_MARKER = "output=";
        final String REFERENCE_MARKER = "reference=";
        final String ITERATIONS_MARKER = "iterations=";
        
        String line = firstLine;
        StylesheetDatalet d = new StylesheetDatalet();

        // Also pass over the default properties as well
        d.options = new Properties(defaults);

        // Parse lines throughout the section to build the datalet
        for (;;)
        {
            // Each .ini file line starts with name of item to fill
            if (line.startsWith(STYLESHEET_MARKER))
            {
                d.inputName = line.substring(STYLESHEET_MARKER.length());
            } 
            else if (line.startsWith(INPUT_MARKER))
            {
                d.xmlName = line.substring(INPUT_MARKER.length());
            } 
            else if (line.startsWith(OUTPUT_MARKER))
            {
                d.outputName = line.substring(OUTPUT_MARKER.length());
            } 
            else if (line.startsWith(REFERENCE_MARKER))
            {
                d.goldName = line.substring(REFERENCE_MARKER.length());
            } 
            else if (line.startsWith(XSLTMARK_CHAR))
            {
                d.setDescription(line);
            } 
            else if (line.startsWith(ITERATIONS_MARKER))
            {
                d.options.put("iterations", line.substring(ITERATIONS_MARKER.length()));
            } 
            else if (line.length() == 0)
            {
                // Blank lines mean end-of-section; return datalet
                // This is the primary exit point for this method
                return d;
            }
            else
            {
                logger.logMsg(Logger.WARNINGMSG, "readXsltmarkDatalet, unknown line: " 
                              + line);
            }

            // Read next line and loop
            try
            {
                line = br.readLine();
            }
            catch (IOException ioe2)
            {
                // Just force us out of the loop; if we've already 
                //  read part of the file, fine
                logger.logMsg(Logger.WARNINGMSG, "readXsltmarkDatalet: " 
                              + fileName + " threw: " + ioe2.toString());
                break;
            }

            if (line == null)
                break;
        } // end of for (;;)
        logger.logMsg(Logger.ERRORMSG, "readXsltmarkDatalet: " + fileName
                      + " no data found!");
        return null;
    }
}
