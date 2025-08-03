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
 * LoggingHandler.java
 *
 */
package org.apache.qetest;


/**
 * Base class defining common functionality of logging handlers.  
 * <p>Common functionality used for testing when implementing 
 * various Handlers and Listeners.  Provides common ways to set 
 * Loggers and levels, reset, and set expected errors or the 
 * like.  Likely used to implement testing wrapper classes for 
 * things like javax.xml.transform.ErrorListener and 
 * org.xml.sax.ErrorHandler</p>
 * <p>The best description for this class can be seen in an 
 * example; see trax.LoggingErrorHandler.java for one.</p>
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingHandler
{

    /**
     * Set a default handler for us to wrapper.
     * Often you may want to keep the default handler for some 
     * operation while you're logging.  For subclasses that support 
     * this call, they will log every operation, and then simply 
     * call-through to this default handler.
     *
     * Subclasses should implement this and verify that the default 
     * is of an appropriate type to use.
     *
     * @param default Object of the correct type to pass-through to;
     * throws IllegalArgumentException if null or incorrect type
     */
    public void setDefaultHandler(Object defaultHandler)
    {
        throw new java.lang.IllegalArgumentException("LoggingHandler.setDefaultHandler() is unimplemented!");
    }


    /**
     * Accessor method for our default handler; here returns null.
     *
     * @return default (Object) our default handler; null if unset
     */
    public Object getDefaultHandler()
    {
        return null;
    }


    /**
     * Get a list of counters of all items we've logged.
     * LoggingHandlers each will have various kinds or types of 
     * things they handle (errors, warnings, messages, URIs, etc.).
     * They should keep a running tally of how many of each kind of 
     * item they've 'handled'.  
     *
     * @return array of int counters for each item we log; 
     * subclasses should define constants for what each slot is
     */
    public int[] getCounters()
    {
        return NOTHING_HANDLED_CTR;
    }


    /**
     * Get a string representation of last item we logged.  
     * For every item that we handle, subclasses should store a 
     * String representation and return it here if asked.  Normally 
     * this will only be a copy of the very last item we logged - 
     * not necessarily what users might want, but the simplest to 
     * implement everywhere.
     *
     * @return String of the last item handled
     */
    public String getLast()
    {
        return NOTHING_HANDLED;
    }


    /**
     * Reset any items or counters we've handled.  
     * Resets any data about what we've handled or logged so far, 
     * like getLast() and getCounters() data, as well as any 
     * expected items from setExpected().  Does not change our 
     * Logger or loggingLevel.
     */
    public void reset()
    {
        /* no-op */;
    }


    /**
     * Ask us to report checkPass/Fail for certain events we handle.
     * Since we may have to handle many events between when a test 
     * will be able to call us, testers can set this to have us 
     * automatically call checkPass when we see an item that matches, 
     * or to call checkFail when we get an unexpected item.
     * Generally, we only call check* methods when:
     * <ul>
     * <li>containsString is not set, reset, or is ITEM_DONT_CARE, 
     * we do nothing (i.e. never call check* for this item)</li>
     * <li>containsString is ITEM_CHECKFAIL, we will always call 
     * checkFail with the contents of any item if it occours</li>
     * <li>containsString is anything else, we will grab a String 
     * representation of every item of that type that comes along, 
     * and if the containsString is found, case-sensitive, within 
     * the handled item's string, call checkPass, otherwise 
     * call checkFail</li>
     * <ul>
     * Note that most implementations will only validate the first 
     * event of a specific type, and then will reset validation for 
     * that event type.  This is because many events may be raised
     * in between the time that a calling Test class could tell us 
     * of the 'next' expected event.
     * //@todo improve this paradigm to allow users to specify an 
     * expected sequence of events.
     *
     * @param itemType which of the various types of items we might 
     * handle; should be defined as a constant by subclasses
     * @param containsString a string to look for within whatever 
     * item we handle - usually checked for by seeing if the actual 
     * item we handle contains the containsString
     */
    public void setExpected(int itemType, String containsString)
    {
        /* no-op */;
    }


    /**
     * Accesor method for a brief description of this service.  
     * Subclasses should obviously override to provide useful info.
     *
     * @return String "LoggingHandler: default implementation, does nothing"
     */
    public String getDescription()
    {
        return "LoggingHandler: default implementation, does nothing";
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
     * Get a default Logger for use with this Handler.  
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
     * Accesor methods for our loggingLevel.  
     *
     * @param l what level we should call logger.logMsg()
     */
    public void setLoggingLevel(int l)
    {
        level = l;
    }


    /**
     * Accesor methods for our loggingLevel.  
     *
     * @return what level we call logger.logMsg()
     */
    public int getLoggingLevel()
    {
        return level;
    }

    //-----------------------------------------------------------
    //---- Instance Variables and Constants
    //-----------------------------------------------------------

    /** Our Logger, who we tell all our secrets to. */
    protected Logger logger = null;

    /** What loggingLevel to use for reporter.logMsg(). */
    protected int level = Logger.DEFAULT_LOGGINGLEVEL;

    /** Default return value from getCounters() after a reset(). */
    public static final int[] NOTHING_HANDLED_CTR = { 0 };

    /** Default return value from getLast() after a reset(). */
    public static final String NOTHING_HANDLED = "NOTHING_HANDLED";

    /** Token for setExpected that we don't care (default value) about the event. */
    public static final String ITEM_DONT_CARE = "ITEM_DONT_CARE";

    /** Token for setExpected that we should call checkFail if we get the event. */
    public static final String ITEM_CHECKFAIL = "ITEM_CHECKFAIL";
}
