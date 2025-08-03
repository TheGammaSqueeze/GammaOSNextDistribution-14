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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintWriter;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Scans a directory tree of result.xml and assorted files 
 * and creates a rolled-up overview of test problems.
 * 
 * <p>Currently fairly tightly coupled to it's stylesheet and to 
 * XMLFileLogger and Reporter.  Scans a directory tree (default is 
 * results-alltest) looking for Pass-/Fail-*.xml results marker 
 * files from test runs.  Then runs the stylesheet over those, 
 * which produces a single output .html file that lists just fails 
 * and related messages.</p>
 *  
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ResultScanner
{

    /**
     * Scan a directory tree for result files and create report.  
     *
     * @param resultDir directory to start scanning in
     * @param baseName of output filename (.xml/.html added)
     * @throws any underlying exceptions, including 
     * FileNotFoundException if if can't find any results
     */
    public void scanResults(String resultName, String baseName)
            throws Exception
    {
        File resultDir = new File(resultName);
        if (!resultDir.exists() || !resultDir.isDirectory())
        {
            throw new FileNotFoundException("ResultScanner: resultDir " + resultDir + " does not exist!");
        }
        
        // Create file to hold list of all results found
        String listFilename = baseName + ".xml";
        FileOutputStream fos = new FileOutputStream(listFilename);
        PrintWriter writer = new PrintWriter(fos);
        
        // Scan for result files in the dir tree
        try
        {
            logStartTag(writer, listFilename);
            scanDir(resultDir, writer, 1);  // 1 = starting recursion
        } 
        catch (Exception e)
        {
            logError(writer, "scanResults threw: " + e.toString());
        }
        finally
        {
            logEndTag(writer);
            writer.close();
        }

        // Transform the list into a master overview of fails
        transformScan(resultDir, listFilename, baseName + ".html");
    }


    /**
     * Scan a directory tree for result files and create report.  
     *
     *
     * @param resultDir directory to start scanning in
     * @param writer to println results to
     * @param recursion depth of this method
     * @throws any underlying exceptions, including 
     * FileNotFoundException if if can't find any results
     */
    protected void scanDir(File resultDir, PrintWriter writer, int depth)
            throws Exception
    {
        depth++;
        if (depth > MAX_DEPTH)
        {
            logError(writer, "scanDir: MAX_DEPTH exceeded, returning!");
            return;
        }

        if (!resultDir.exists() || !resultDir.isDirectory())
        {
            logError(writer, "scanDir: attempt to scan non-directory, returning!");
            return;
        }
        
        // Get list of teststatus files that definitely have problems
        String badResults[] = resultDir.list(
                new FilenameFilter() // anonymous class
                {
                    public boolean accept(File dir, String name)
                    {
                        // Shortcuts for bogus filenames and dirs
                        if (name == null || dir == null)
                            return false;
                        // Skip already-rolledup Harness reports
                        if (name.endsWith("Harness.xml"))
                            return false;
                        return (name.startsWith("Fail-") 
                                || name.startsWith("Errr-") 
                                || name.startsWith("Incp-"));
                    }
                }
            );

        // Get list of teststatus files that passed or were 
        //  ambiguous, which means they didn't fail
        String okResults[] = resultDir.list(
                new FilenameFilter() // anonymous class
                {
                    public boolean accept(File dir, String name)
                    {
                        // Shortcuts for bogus filenames and dirs
                        if (name == null || dir == null)
                            return false;
                        return (name.startsWith("Pass-") 
                                || name.startsWith("Ambg-"));
                    }
                }
            );

        // Output references to both sets of files if needed
        if ((null != okResults) && (null != badResults)
            && (okResults.length + badResults.length > 0))
        {
            logTestGroup(writer, resultDir.getPath(), okResults, badResults);
        }
        
        // Traverse down directories
        String subdirs[] = resultDir.list(
                new FilenameFilter() // anonymous class
                {
                    public boolean accept(File dir, String name)
                    {
                        // Shortcuts for bogus filenames and dirs and CVS junk
                        if (null == name || null == dir || "CVS".equals(name))
                            return false;
                        return (new File(dir, name)).isDirectory();
                    }
                }
            );
        if (null != subdirs)
        {
            for (int i=0; i < subdirs.length; i++)
            {
                scanDir(new File(resultDir, subdirs[i]), writer, depth + 1);
            }
        }

    }


    /**
     * Transform a resultfilelist into a real report.  
     *
     *
     * @param resultDir directory to start scanning in
     * @param filename name of listfile
     * @throws any underlying exceptions
     */
    protected void transformScan(File resultDir, String xmlName, String outName)
            throws TransformerException
    {
        Templates templates = getTemplates();
        Transformer transformer = templates.newTransformer();
        transformer.transform(new StreamSource(xmlName), 
                              new StreamResult(outName));        
    }
    

    /**
     * Find the appropriate stylesheet to use.  
     *
     * Worker method for future expansion
     *
     * @return Templates object to use for transform
     * @throws any underlying TransformerException
     */
    public Templates getTemplates()
            throws TransformerException
    {
        //@todo assumption: it's in current dir
        String xslName = "ResultScanner.xsl";
        StreamSource xslSource = new StreamSource(xslName);
        TransformerFactory factory = TransformerFactory.newInstance();
        return factory.newTemplates(xslSource);
    }

    
    /**
     * Write an xml decl and start tag to our list file.
     *
     * @param writer where to write to
     */
    public static void logStartTag(PrintWriter writer, String name)
    {
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<resultfilelist logFile=\"" + name + "\">");
        writer.flush();
    }


    /**
     * Write the end tag to our list file.
     *
     * @param writer where to write to
     */
    public static void logEndTag(PrintWriter writer)
    {
        writer.println("</resultfilelist>");
        writer.flush();
    }


    /**
     * Write an group of tests to our list file.
     *
     * @param writer where to write to
     * @param base basedir where these are found
     * @param okTests list of passing tests to write
     * @param badTests list of failing tests to write
     */
    public static void logTestGroup(PrintWriter writer, String base, 
            String[] okTests, String[] badTests)
    {
        writer.println("<testgroup href=\"" + base + "\" >");
        if ((null != okTests) && (okTests.length > 0))
        {
            for (int i = 0; i < okTests.length; i++)
            {
                writer.println("<teststatus href=\"" + okTests[i] + "\" status=\"ok\" />");
            }
        }
        if ((null != badTests) && (badTests.length > 0))
        {
            for (int i = 0; i < badTests.length; i++)
            {
                writer.println("<teststatus href=\"" + badTests[i] + "\" status=\"notok\" />");
            }
        }
        writer.println("</testgroup>");
    }


    /**
     * Write an error message to our list file.
     *
     * @param writer where to write to
     * @param error string to write out
     */
    public static void logError(PrintWriter writer, String error)
    {
        writer.println("<error>" + error + "</error>");
        writer.flush();
    }


    /**
     * Defensive coding: limit directory depth recursion.
     */
    protected static final int MAX_DEPTH = 10;


    /**
     * Bottleneck output for future redirection.
     */
    protected PrintWriter outWriter = new PrintWriter(System.out);
    
    
    /**
     * Main method to run from the command line; sample usage.
     * @param args cmd line arguments
     */
    public static void main(String[] args)
    {
        String resultDir = "results-alltest";
        if (args.length >= 1)
        {
            resultDir = args[0];
        }
        String logFile = resultDir + File.separator + "ResultReport";
        if (args.length >= 2)
        {
            logFile = args[1];
        }
        //@todo add more arguments; filtering, options 
        //  to pass to stylesheets etc.
        try
        {
            ResultScanner app = new ResultScanner();
            app.scanResults(resultDir, logFile);
            System.out.println("ResultScanner of " + resultDir + " complete in: " + logFile + ".html");
        } 
        catch (Exception e)
        {
            System.out.println("ResultScanner error on: " + resultDir);
            e.printStackTrace();
        }
    }

}
