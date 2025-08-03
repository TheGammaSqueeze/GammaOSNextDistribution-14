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
import java.util.Hashtable;

import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;

/**
 * Testlet for conformance testing of xsl stylesheet files using 
 * the MSXSL command line instead of a TransformWrapper.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class MSXSLTestlet extends CmdlineTestlet
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.MSXSLTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String describing what this MSXSLTestlet does.
     */
    public String getDescription()
    {
        return "MSXSLTestlet";
    }


    /**
     * Default Actual name of external program to call.  
     * @return msxsl, the MSXSL 4.0 command line.
     */
    public String getDefaultProgName()
    {
        return "msxsl";
    }


    /**
     * Worker method to get list of arguments specific to this program.  
     * 
     * <p>Must be overridden for different processors, obviously.  
     * This implementation returns the args for Xalan-C TestXSLT</p>
     * 
     * @param program path\name of program to Runtime.exec()
     * @param defaultArgs any additional arguments to pass
     * @return String array of arguments suitable to pass to 
     * Runtime.exec()
     */
    public String[] getProgramArguments(StylesheetDatalet datalet, String[] defaultArgs)
    {
        final int NUMARGS = 6;
        String[] args = new String[defaultArgs.length + NUMARGS];
        String progName = datalet.options.getProperty(OPT_PROGNAME, getDefaultProgName());
        String progPath = datalet.options.getProperty(OPT_PROGPATH);
        if ((null != progPath) && (progPath.length() > 0))
        {
            args[0] = progPath + File.separator + progName;
        }
        else
        {
            // Pesume the program is on the PATH already...
            args[0] = progName;
        }
    
        // Default args for MSXSL 4.0
        args[1] = datalet.xmlName;
        args[2] = datalet.inputName;
        args[3] = "-o"; // Write output to named file
        args[4] = datalet.outputName;
        args[5] = "-t"; // Show load and transformation timings

        if (defaultArgs.length > 0)
            System.arraycopy(defaultArgs, 0, args, NUMARGS, defaultArgs.length);

        return args;
    }


    /** 
     * Worker method to evaluate the System.out/.err streams of 
     * a particular processor.  
     *
     * Overridden to parse out -t timings from msxsl output.
     *
     * @param cmdline that was used for execProcess
     * @param outBuf buffer from execProcess' System.out
     * @param errBuf buffer from execProcess' System.err
     * @param processReturnVal from execProcess
     */
    protected void checkOutputStreams(String[] cmdline, StringBuffer outBuf, 
            StringBuffer errBuf, int processReturnVal)
    {
        Hashtable attrs = new Hashtable();
        attrs.put("program", cmdline[0]);
        attrs.put("returnVal", String.valueOf(processReturnVal));

        StringBuffer buf = new StringBuffer();
        if ((null != errBuf) && (errBuf.length() > 0))
        {
            buf.append("<system-err>");
            buf.append(errBuf);
            buf.append("</system-err>\n");
        }
        if ((null != outBuf) && (outBuf.length() > 0))
        {
            buf.append("<system-out>");
            buf.append(outBuf);
            buf.append("</system-out>\n");
        }
        logger.logElement(Logger.INFOMSG, "checkOutputStreams", attrs, buf.toString());
        attrs = null;
        buf = null;

        try
        {
            final String SOURCE_LOAD = "Source document load time:";
            final String STYLESHEET_LOAD = "Stylesheet document load time:";
            final String STYLESHEET_COMPILE = "Stylesheet compile time:";
            final String STYLESHEET_EXECUTE = "Stylesheet execution time:";
            final String MILLISECONDS = "milliseconds";
            double sourceLoad;
            double stylesheetLoad;
            double stylesheetCompile;
            double stylesheetExecute;
            // Now actually parse the system-err stream for performance
            // This is embarassingly messy, but I'm not investing 
            //  more time here until I'm sure it'll be worth it
            String tmp = errBuf.toString();
            String ms = null;
            int tmpIdx = 0;
            // Advance to source loading time
            tmp = tmp.substring(tmp.indexOf(SOURCE_LOAD) + SOURCE_LOAD.length());
            // Time is spaces & numbers up to the 'milliseconds' marker
            ms = tmp.substring(0, tmp.indexOf(MILLISECONDS)).trim();
            sourceLoad = Double.parseDouble(ms);

            // Advance to stylesheet loading time
            tmp = tmp.substring(tmp.indexOf(STYLESHEET_LOAD) + STYLESHEET_LOAD.length());
            // Time is spaces & numbers up to the 'milliseconds' marker
            ms = tmp.substring(0, tmp.indexOf(MILLISECONDS)).trim();
            stylesheetLoad = Double.parseDouble(ms);
            
            // Advance to stylesheet compile time
            tmp = tmp.substring(tmp.indexOf(STYLESHEET_COMPILE) + STYLESHEET_COMPILE.length());
            // Time is spaces & numbers up to the 'milliseconds' marker
            ms = tmp.substring(0, tmp.indexOf(MILLISECONDS)).trim();
            stylesheetCompile = Double.parseDouble(ms);
            
            // Advance to stylesheet execute time
            tmp = tmp.substring(tmp.indexOf(STYLESHEET_EXECUTE) + STYLESHEET_EXECUTE.length());
            // Time is spaces & numbers up to the 'milliseconds' marker
            ms = tmp.substring(0, tmp.indexOf(MILLISECONDS)).trim();
            stylesheetExecute = Double.parseDouble(ms);

            // Log out approximate timing data
            // Log special performance element with our timing
            attrs = new Hashtable();
            // UniqRunid is an Id that our TestDriver normally sets 
            //  with some unique code, so that results analysis 
            //  stylesheets can compare different test runs
            attrs.put("UniqRunid", "runId;notImplemented-MSXSLTestlet");
            // processor is the 'flavor' of processor we're testing
            attrs.put("processor", getDescription());
            // idref is the individual filename
            attrs.put("idref", (new File(cmdline[2])).getName());
            // inputName is the actual name we gave to the processor
            attrs.put("inputName", cmdline[2]);
            attrs.put("iterations", new Integer(1));
            attrs.put("sourceLoad", new Double(sourceLoad));
            attrs.put("stylesheetLoad", new Double(stylesheetLoad));
            attrs.put("stylesheetCompile", new Double(stylesheetCompile));
            attrs.put("stylesheetExecute", new Double(stylesheetExecute));

            logger.logElement(Logger.STATUSMSG, "perf", attrs, "PItr;");
        } 
        catch (Exception e)
        {
            logger.logThrowable(Logger.WARNINGMSG, e, "checkOutputStreams threw");
        }
    }
}  // end of class MSXSLTestlet

