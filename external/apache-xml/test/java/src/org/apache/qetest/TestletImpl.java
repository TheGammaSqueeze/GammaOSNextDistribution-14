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
 * TestletImpl.java
 *
 */
package org.apache.qetest;



/**
 * Simple implementation of a testlet, a sort of mini-test.
 * <p>A TestletImpl defines some common implementations that 
 * may be useful, including sample implementations that 
 * can be copied if you don't want to exend this class.</p>
 *
 * <p>The most useful implementation is of main(String[]), which 
 * allows a Testlet to be executed independently from the command 
 * line.  See the code comments for a way to get this behavior in 
 * your testlet without having to re-implement the whole main 
 * method - by just including a static{} initializer with the 
 * fully qualified classname of your class.</p>
 *
 * <b>Note:</b> Testlets based on this class are probably 
 * not threadsafe, and must be executed singly! 
 * See comments for thisClassName.
 * 
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class TestletImpl implements Testlet
{

    //-----------------------------------------------------
    //---- Implement Testlet interface methods
    //-----------------------------------------------------

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String "TestletImpl: default implementation, does nothing"
     */
    public String getDescription()
    {
        return "TestletImpl: default implementation, does nothing";
    }


    /**
     * Accesor methods for our Logger.
     *
     * @param l the Logger to have this test use for logging 
     * results; or null to use a default logger
     */
    public void setLogger(Logger l)
	{
        // if null, set a default one
        if (null == l)
            logger = getDefaultLogger();
        else
            logger = l;
	}


    /**
     * Accesor methods for our Logger.  
     *
     * @return Logger we tell all our secrets to.
     */
    public Logger getLogger()
	{
        return logger;
	}


    /**
     * Get a default Logger for use with this Testlet.  
     * Gets a default ConsoleLogger (only if a Logger isn't 
     * currently set!).  
     *
     * @return current logger; if null, then creates a 
     * Logger.DEFAULT_LOGGER and returns that; if it cannot
     * create one, throws a RuntimeException
     */
    public Logger getDefaultLogger()
    {
        if (logger != null)
            return logger;

        try
        {
            Class rClass = Class.forName(Logger.DEFAULT_LOGGER);
            return (Logger)rClass.newInstance();
        } 
        catch (Exception e)
        {
            // Must re-throw the exception, since returning 
            //  null or the like could lead to recursion
            e.printStackTrace();
            throw new RuntimeException(e.toString());
        }
    }


    /**
     * Return this TestletImpl's default Datalet.  
     * 
     * @return Datalet <code>defaultDatalet</code>.
     */
    public Datalet getDefaultDatalet()
	{
        return defaultDatalet;
	}


    /**
     * Run this TestletImpl: execute it's test and return.
     * This must (obviously) be overriden by subclasses.  Here, 
     * we simply log a message for debugging purposes.
     *
     * @param Datalet to use as data points for the test.
     */
    public void execute(Datalet datalet)
	{
        logger.logMsg(Logger.STATUSMSG, "TestletImpl.execute(" + datalet + ")");
	}


    //-----------------------------------------------------
    //---- Implement useful worker methods and main()
    //-----------------------------------------------------

    /**
     * Process default command line args.  
     * Provides simple usage functionality: given a first arg of 
     * -h, -H, -?, prints a usage statement based on getDescription()
     * and on getDefaultDatalet().getDescription()
     * 
     * @param args command line args from the JVM
     * @return true if we got and handled any default command line 
     * args (i.e. you can quit now); false otherwise (i.e. you 
     * should go ahead and execute)
     */
    protected boolean handledDefaultArgs(String[] args)
    {
        // We don't handle null or blank args
        if ((null == args) || (0 == args.length))
            return false;

        // Provide basic processing for help, usage cases
        if ("-h".equals(args[0])
            || "-H".equals(args[0])
            || "-?".equals(args[0])
           )
        {
            logger.logMsg(Logger.STATUSMSG, thisClassName + " usage:");
            logger.logMsg(Logger.STATUSMSG, "    Testlet: " + getDescription());
            logger.logMsg(Logger.STATUSMSG, "    Datalet: " + getDefaultDatalet().getDescription());
            return true;
        }

        // Otherwise, don't handle any other args
        return false;
    }


    /**
     * Default implementation for command line use.  
     * Note subclasses can easily get the functionality we provide 
     * here without having to copy this method merely by copying the 
     * static initalization block shown above, replacing the 
     * "thisClassName" with their own FQCN.
     *
     * This default implementation installs a default Logger, then 
     * checks for and handles a few default command line args, and 
     * then executes the Testlet.
     *
     * //@todo How do we easily specify alternate Datalets or 
     * data to load a Datalet from?  What about the Datalet that 
     * actually wants the first arg to be -h?
     *
     * @param args command line args from the JVM
     */
    public static void main(String[] args)
    {
        if (true)
            System.out.println("TestletImpl.main");
        TestletImpl t = null;
        try
        {
            // Create an instance of the specific class at runtime
            //  This relies on subclasses to reset 'thisClassName'
            //  in their own static[] initialization block!
            t = (TestletImpl)Class.forName(thisClassName).newInstance();

            // Set a default logger automatically
            t.setLogger(t.getDefaultLogger());

            // Process default -h, etc. args
            if (!t.handledDefaultArgs(args))
            {
                if (args.length > 0)
                {
                    // If we do have any args, then attempt to 
                    //  load the correct-typed Datalet from the args
                    Class dataletClass = t.getDefaultDatalet().getClass();
                    t.logger.logMsg(Logger.TRACEMSG, "Loading Datalet " 
                                  +  dataletClass.getName() + " from args");
                    Datalet d = (Datalet)dataletClass.newInstance();
                    d.load(args);
                    t.execute(d);
                }
                else
                {
                    // Otherwise, use the defaultDatalet for that Testlet
                    t.logger.logMsg(Logger.TRACEMSG, "Using defaultDatalet");
                    t.execute(t.getDefaultDatalet());
                }
            }
        }
        catch (Exception e)
        {
            if ((null != t) && (null != t.logger))
            {
                // Use the logger which is (hopefully) OK
                t.logger.checkErr("TestletImpl threw: " + e.toString());
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                t.logger.logArbitrary(Logger.ERRORMSG, sw.toString());
            }
            else
            {
                // Otherwise, just dump to System.err
                System.err.println("TestletImpl threw: " + e.toString());
                e.printStackTrace();
            }
        }
    }


    /**
     * The FQCN of the current class.  
     * See comments in main() and in the static initializer.
     * <b>Note:</b> Testlets based on this class are probably 
     * not threadsafe, and must be executed singly! 
     */
    protected static String thisClassName = null;


    /**
     * A static initializer setting the value of thisClassName.  
     * Subclasses must copy this static block, and replace the 
     * name of "...TestletImpl" with their own name.  Then, when 
     * a user executes the subclassed Testlet on the command line 
     * or calls it's main() method, the correct thing will happen.
     */
    static { thisClassName = "org.apache.qetest.TestletImpl"; }


    /**
     * Our Logger, who we tell all our secrets to.  
     */
    protected Logger logger = null;


    /**
     * Our deafult Datalet: in this case, not very interesting.  
     * Provide a default 'null' datalet so unsuspecting callers 
     * don't get NullPointerExceptions.
     */
    protected Datalet defaultDatalet = new NullDatalet();

}  // end of class TestletImpl
