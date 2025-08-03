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
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Hashtable;

/**
 * Base class for testing commandline driven products.  
 *
 * This class provides a default algorithim for testing any
 * command line based tool.  Subclasses define the 
 * exact command line args, etc. used for different products.
 * Subclasses can also either shell an external process or can 
 * just construct a class and call main().
 *
 * @author Shane_Curcuru@us.ibm.com
 * @version $Id$
 */
public abstract class ExecTestlet extends FileTestlet
{
    /**
     * Parameter: Actual name of external program to call.  
     */
    public static final String OPT_PROGNAME = "progName";

    /**
     * Timing data: how long process takes to exec.  
     * Default is -1 to represent a bogus number.  
     */
    protected long timeExec = -1;

    /**
     * Default path/name of external program to call, OR 
     * actual name of class to call.  
     * @return foo, must be overridden.
     */
    public abstract String getProgram();

    /**
     * If the program should be shelled out or if it is a Java 
     * class to call main on.  
     * @return foo, must be overridden.
     */
    public abstract boolean isExternal();

    /**
     * Worker method to get list of arguments specific to this program.  
     * 
     * <p>Should construct whole list of arguments needed to call 
     * this program, including any options and args needed to 
     * process the files in the datalet.  Must be overriden.</p>
     *
     * <p>If isExternal is true, this should presumably put the 
     * name of the program first, since we just shell that as a 
     * command line.  If isExternal is false, this should <b>not</b> 
     * include the Java classname.</p>
     * 
     * @param datalet that defined the test data
     * @return String array of arguments suitable to pass to 
     * Runtime.exec() or main()
     */
    public abstract String[] getArguments(FileDatalet datalet);

    /** 
     * Worker method to actually perform the test; 
     * overriden to use command line processing.  
     *
     * Logs out applicable info; attempts to perform transformation.
     *
     * @param datalet to test with
     * @throws allows any underlying exception to be thrown
     */
    protected void testDatalet(FileDatalet datalet)
            throws Exception
    {
        String[] args = getArguments(datalet);
    
        StringBuffer argBuf = new StringBuffer();
        for (int i = 0; i < args.length; i++)
        {
            argBuf.append(args[i]);
            argBuf.append(" ");
        }
        logger.logMsg(Logger.TRACEMSG, "testDatalet executing: " + argBuf.toString());

        // Use one of two worker methods to execute the process, either
        //  by shelling an external process, or by constructing the 
        //  Java object and then calling main() 
        if (isExternal())
            execProcess(datalet, args);
        else
            execMain(datalet, args);
    }

    /**
     * Worker method to call a Java class' main() method.  
     * 
     * <p>Simply calls a no-arg constructor and then passes the 
     * args to the main() method.  May be overridden.</p>
     * 
     * @param datalet that defined the test data
     * @param cmdline actual command line to run, including program name
     * @param environment passed as-is to Process.run
     * @return return value from program
     * @exception Exception may be thrown by Runtime.exec
     */
    public void execMain(FileDatalet datalet, String[] cmdline)
            throws Exception
    {
        // Default implementation; may be overriden
        Class clazz = Class.forName(getProgram());
        if (null == clazz)
        {
            logger.checkErr("Can't find classname: " + getProgram());
            return;
        }

        try
        {
            // ...find the main() method...
            Class[] parameterTypes = new Class[1];
            parameterTypes[0] = java.lang.String[].class;
            Method main = clazz.getMethod("main", parameterTypes);
            
            // ...and execute the method!
            Object[] mainArgs = new Object[1];
            mainArgs[0] = cmdline;
            final long startTime = System.currentTimeMillis();
            main.invoke(null, mainArgs);
            timeExec = System.currentTimeMillis() - startTime;

            // Also log out a perf element by default
            Hashtable attrs = new Hashtable();
            attrs.put("program", getProgram());
            attrs.put("isExternal", "false");
            attrs.put("timeExec", new Long(timeExec));
            logPerf(datalet, attrs);
            attrs = null;
        }
        catch (Throwable t)
        {
            logger.logThrowable(Logger.ERRORMSG, t, "Javaclass.main() threw");
            logger.checkErr(getProgram() + ".main() threw: " + t.toString());
        }        
    }

    /**
     * Worker method to shell out an external process.  
     * 
     * <p>Does a simple capturing of the out and err streams from
     * the process and logs them out.  Inherits the same environment 
     * that the current JVM is in.  No need to override</p>
     * 
     * @param datalet that defined the test data
     * @param cmdline actual command line to run, including program name
     * @param environment passed as-is to Process.run
     * @return return value from program
     * @exception Exception may be thrown by Runtime.exec
     */
    public void execProcess(FileDatalet datalet, String[] cmdline)
            throws Exception
    {
        if ((cmdline == null) || (cmdline.length < 1))
        {
            logger.checkFail("execProcess called with null/blank arguments!");
            return;
        }

        int bufSize = 2048; // Arbitrary bufSize seems to work well
        ThreadedStreamReader outReader = new ThreadedStreamReader();
        ThreadedStreamReader errReader = new ThreadedStreamReader();
        Runtime r = Runtime.getRuntime();
        java.lang.Process proc = null;

        // Actually begin executing the program
        logger.logMsg(Logger.TRACEMSG, "execProcess starting " + cmdline[0]);

        //@todo Note: we should really provide a way for the datalet
        //  to specify any additional environment needed for the 
        //  second arg to exec();
        String[] environment = null;
        final long startTime = System.currentTimeMillis();
        proc = r.exec(cmdline, environment);

        // Immediately begin capturing any output therefrom
        outReader.setInputStream(
            new BufferedReader(
                new InputStreamReader(proc.getInputStream()), bufSize));
        errReader.setInputStream(
            new BufferedReader(
                new InputStreamReader(proc.getErrorStream()), bufSize));

        // Start two threads off on reading the System.out and System.err from proc
        outReader.start();
        errReader.start();
        int processReturnVal = -2; // HACK the default
        try
        {
            // Wait for the process to exit normally
            processReturnVal = proc.waitFor();
            // Record time we finally rejoin, i.e. when the process is done
            timeExec = System.currentTimeMillis() - startTime;
        }
        catch (InterruptedException ie1)
        {
            logger.logThrowable(Logger.ERRORMSG, ie1, 
                                  "execProcess proc.waitFor() threw");
        }

        // Now that we're done, presumably the Readers are also done
        StringBuffer sysOut = null;
        StringBuffer sysErr = null;
        try
        {
            outReader.join();
            sysOut = outReader.getBuffer();
        }
        catch (InterruptedException ie2)
        {
            logger.logThrowable(Logger.ERRORMSG, ie2, "Joining outReader threw");
        }

        try
        {
            errReader.join();
            sysErr = errReader.getBuffer();
        }
        catch (InterruptedException ie3)
        {
            logger.logThrowable(Logger.ERRORMSG, ie3, "Joining errReader threw");
        }

        logAndCheckStreams(datalet, cmdline, sysOut, sysErr, processReturnVal);
    }


    /** 
     * Worker method to evaluate the System.out/.err streams of 
     * a particular processor. 
     *
     * Logs out the streams if available, then calls a worker method 
     * to actually call check() if specific validation needed. 
     *
     * @param datalet that defined the test data
     * @param cmdline that was used for execProcess
     * @param outBuf buffer from execProcess' System.out
     * @param errBuf buffer from execProcess' System.err
     * @param processReturnVal from execProcess
     */
    protected void logAndCheckStreams(FileDatalet datalet, String[] cmdline, 
            StringBuffer outBuf, StringBuffer errBuf, int processReturnVal)
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
        buf = null;

        // Also log out a perf element by default
        attrs = new Hashtable();
        attrs.put("program", cmdline[0]);
        attrs.put("isExternal", "true");
        attrs.put("timeExec", new Long(timeExec));
        logPerf(datalet, attrs);
        attrs = null;

        // Also call worker method to allow subclasses to 
        //  override checking of the output streams, as available
        checkStreams(datalet, cmdline, outBuf, errBuf, processReturnVal);
    }


    /** 
     * Worker method to validate the System.out/.err streams.  
     * 
     * Default implementation does nothing; override if you wish 
     * to actually validate the specific streams. 
     *
     * @param datalet that defined the test data
     * @param cmdline that was used for execProcess
     * @param outBuf buffer from execProcess' System.out
     * @param errBuf buffer from execProcess' System.err
     * @param processReturnVal from execProcess
     */
    protected void checkStreams(FileDatalet datalet, String[] cmdline, 
            StringBuffer outBuf, StringBuffer errBuf, int processReturnVal)
    {
        // Default impl is no-op
        return;
    }


    /** 
     * Worker method to write performance data in standard format.  
     *
     * Writes out a perf elem with standardized idref, testlet, 
     * input/output, and fileSize params.
     *
     * @param datalet to use for idref, etc.  
     * @param hash of extra attributes to log.  
     */
    protected void logPerf(FileDatalet datalet, Hashtable hash)
    {
        if (null == hash)
            hash = new Hashtable();

        File f = new File(datalet.getInput());
            
        hash.put("idref", f.getName());
        hash.put("input", datalet.getInput());
        hash.put("output", datalet.getOutput());
        hash.put("testlet", thisClassName);
        try
        {
            // Attempt to store size of input file, since overall 
            //  amount of data affects performance
            hash.put("fileSize", new Long(f.length()));
        } 
        catch (Exception e)
        {
            hash.put("fileSize", "threw: " + e.toString());
        }

        logger.logElement(Logger.STATUSMSG, "perf", hash, getCheckDescription(datalet));
    }

}  // end of class ExecTestlet

