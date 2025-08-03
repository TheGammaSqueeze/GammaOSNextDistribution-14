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
 * LoggingSAXErrorHandler.java
 *
 */
package org.apache.qetest.xsl;

import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

//-------------------------------------------------------------------------

/**
 * Cheap-o ErrorHandler for use by API tests.
 * <p>Implements org.xml.sax.ErrorHandler and dumps everything to a Reporter.</p>
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingSAXErrorHandler extends LoggingHandler implements ErrorHandler
{
    /** No-op ctor seems useful. */
    public LoggingSAXErrorHandler()
    {
        setLogger(getDefaultLogger());
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param l Logger we should log to
     */
    public LoggingSAXErrorHandler(Logger l)
    {
        setLogger(l);
    }


    /** 
     * Constants determining when we should throw exceptions.
     * <ul>Flags are combineable like a bitfield.
     * <li>THROW_NEVER - never ever (always continue - note this 
     * may have unexpected effects when fatalErrors happen, see 
     * {@link javax.xml.transform.ErrorListener#fatalError(javax.xml.transform.TransformerException)}</li>
     * <li>THROW_ON_WARNING - throw only on warnings</li>
     * <li>THROW_ON_ERROR - throw only on errors</li>
     * <li>THROW_ON_FATAL - throw only on fatalErrors - default</li>
     * <li>THROW_ALWAYS - always throw exceptions</li>
     * </ul>
     */
    public static final int THROW_NEVER = 0;

    /** THROW_ON_WARNING - throw only on warnings.  */
    public static final int THROW_ON_WARNING = 1;

    /** THROW_ON_ERROR - throw only on errors.  */
    public static final int THROW_ON_ERROR = 2;

    /** THROW_ON_FATAL - throw only on fatalErrors - default.  */
    public static final int THROW_ON_FATAL = 4;

    /** THROW_ALWAYS - always throw exceptions.   */
    public static final int THROW_ALWAYS = THROW_ON_WARNING & THROW_ON_ERROR
                                           & THROW_ON_FATAL;

    /** If we should throw an exception for each message type. */
    protected int throwWhen = THROW_ON_FATAL;

    /**
     * Tells us when we should re-throw exceptions.  
     *
     * @param t THROW_WHEN_* constant as to when we should re-throw 
     * an exception when we are called
     */
    public void setThrowWhen(int t)
    {
        throwWhen = t;
    }

    /**
     * Tells us when we should re-throw exceptions.  
     *
     * @return THROW_WHEN_* constant as to when we should re-throw 
     * an exception when we are called
     */
    public int getThrowWhen()
    {
        return throwWhen;
    }

    /** Constant for items returned in getCounters: messages.  */
    public static final int TYPE_WARNING = 0;

    /** Constant for items returned in getCounters: errors.  */
    public static final int TYPE_ERROR = 1;

    /** Constant for items returned in getCounters: fatalErrors.  */
    public static final int TYPE_FATALERROR = 2;

    /** 
     * Counters for how many events we've handled.  
     * Index into array are the TYPE_* constants.
     */
    protected int[] counters = 
    {
        0, /* warning */
        0, /* error */
        0  /* fatalError */
    };


    /**
     * Get a list of counters of all items we've logged.
     * Returned as warnings, errors, fatalErrors
     * Index into array are the TYPE_* constants.
     *
     * @return array of int counters for each item we log
     */
    public int[] getCounters()
    {
        return counters;
    }

    /** Prefixed to all logger msg output. */
    public static final String prefix = "SEH:";


    /**
     * Really Cheap-o string representation of our state.  
     *
     * @return String of getCounters() rolled up in minimal space
     */
    public String getQuickCounters()
    {
        return (prefix + "(" + counters[TYPE_WARNING] + ", "
                + counters[TYPE_ERROR] + ", " + counters[TYPE_FATALERROR] + ")");
    }


    /** Cheap-o string representation of last warn/error/fatal we got. */
    protected String lastItem = NOTHING_HANDLED;

    /**
     * Sets a String representation of last item we handled. 
     *
     * @param s set into lastItem for retrieval with getLast()
     */
    protected void setLastItem(String s)
    {
        lastItem = s;
    }

    /**
     * Get a string representation of last item we logged.  
     *
     * @return String of the last item handled
     */
    public String getLast()
    {
        return lastItem;
    }


    /** Expected values for events we may handle, default=ITEM_DONT_CARE. */
    protected String[] expected = 
    {
        ITEM_DONT_CARE, /* warning */
        ITEM_DONT_CARE, /* error */
        ITEM_DONT_CARE  /* fatalError */
    };


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
     * Note that any time we handle a particular event that was 
     * expected, we un-set the expected value for that item.  This 
     * means that you can only ask us to validate one occourence 
     * of any particular event; all events after that one will 
     * be treated as ITEM_DONT_CARE.  Callers can of course call 
     * setExpected again, of course, but this covers the case where 
     * we handle multiple events in a single block, perhaps out of 
     * the caller's direct control. 
     * Note that we first store the event via setLast(), then we 
     * validate the event as above, and then we potentially 
     * re-throw the exception as by setThrowWhen().
     *
     * @param itemType which of the various types of items we might 
     * handle; should be defined as a constant by subclasses
     * @param containsString a string to look for within whatever 
     * item we handle - usually checked for by seeing if the actual 
     * item we handle contains the containsString
     */
    public void setExpected(int itemType, String containsString)
    {
        // Default to don't care on null
        if (null == containsString)
            containsString = ITEM_DONT_CARE;

        try
        {
            expected[itemType] = containsString;
        }
        catch (ArrayIndexOutOfBoundsException aioobe)
        {
            // Just log it for callers reference and continue anyway
            logger.logMsg(level, prefix + " setExpected called with illegal type:" + itemType);
        }
    }


    /**
     * Reset all items or counters we've handled.  
     */
    public void reset()
    {
        setLastItem(NOTHING_HANDLED);
        for (int i = 0; i < counters.length; i++)
        {
            counters[i] = 0;
        }
        for (int j = 0; j < expected.length; j++)
        {
            expected[j] = ITEM_DONT_CARE;
        }
    }


    /**
     * Grab basic info out of a SAXParseException.
     *
     * @param exception the SAXParseException to get info from
     * @return condensed string of important info therefrom
     */
    public String getParseExceptionInfo(SAXParseException exception)
    {

        if (exception == null)
            return null;

        String retVal = new String("");
        String tmp;

        tmp = exception.getPublicId();

        if (tmp != null)
            retVal += " publicID:" + tmp;

        tmp = exception.getSystemId();

        if (tmp != null)
            retVal += " systemId:" + tmp;

        try
        {
            tmp = Integer.toString(exception.getLineNumber());
        }
        catch (NumberFormatException nfe)
        {
            tmp = null;
        }

        if (tmp != null)
            retVal += " lineNumber:" + tmp;

        try
        {
            tmp = Integer.toString(exception.getColumnNumber());
        }
        catch (NumberFormatException nfe)
        {
            tmp = null;
        }

        if (tmp != null)
            retVal += " columnNumber:" + tmp;

        tmp = exception.getMessage();  // Will grab inner message if needed

        if (tmp != null)
            retVal += " message:" + tmp;

        return retVal;
    }



    /////////////////// Implement SAXErrorHandler ///////////////////
    /**
     * Implementation of warning; calls logMsg with info contained in exception.
     *
     * @param exception provided by Transformer
     * @exception TransformerException thrown only if asked to or if loggers are bad
     */
    public void warning(SAXParseException exception) throws SAXException
    {

        // Increment counter and save the exception
        counters[TYPE_WARNING]++;

        String exInfo = getParseExceptionInfo(exception);

        setLastItem(exInfo);

        // Log or validate the exception
        logOrCheck(TYPE_WARNING, "warning", exInfo);

        // Also re-throw the exception if asked to
        if ((throwWhen & THROW_ON_WARNING) == THROW_ON_WARNING)
        {
            throw new SAXException(exception);
        }
    }


    /**
     * Implementation of error; calls logMsg with info contained in exception.
     * Only ever throws an exception itself if asked to or if loggers are bad.
     *
     * @param exception provided by Transformer
     * @exception TransformerException thrown only if asked to or if loggers are bad
     */
    public void error(SAXParseException exception) throws SAXException
    {

        // Increment counter, save the exception, and log what we got
        counters[TYPE_ERROR]++;

        String exInfo = getParseExceptionInfo(exception);

        setLastItem(exInfo);

        // Log or validate the exception
        logOrCheck(TYPE_ERROR, "error", exInfo);

        // Also re-throw the exception if asked to
        if ((throwWhen & THROW_ON_ERROR) == THROW_ON_ERROR)
        {
            throw new SAXException(exception);
        }
    }

    /**
     * Implementation of error; calls logMsg with info contained in exception.
     * Only ever throws an exception itself if asked to or if loggers are bad.
     * Note that this may cause unusual behavior since we may not actually
     * re-throw the exception, even though it was 'fatal'.
     *
     * @param exception provided by Transformer
     * @exception TransformerException thrown only if asked to or if loggers are bad
     */
    public void fatalError(SAXParseException exception) throws SAXException
    {

        // Increment counter, save the exception, and log what we got
        counters[TYPE_FATALERROR]++;

        String exInfo = getParseExceptionInfo(exception);

        setLastItem(exInfo);

        // Log or validate the exception
        logOrCheck(TYPE_FATALERROR, "fatalError", exInfo);

        // Also re-throw the exception if asked to
        if ((throwWhen & THROW_ON_FATAL) == THROW_ON_FATAL)
        {
            throw new SAXException(exception);
        }
    }


    /**
     * Worker method to either log or call check* for this event.  
     * A simple way to validate for any kind of event.
     *
     * @param type of message (warning/error/fatalerror)
     * @param desc description of this kind of message
     * @param exInfo String representation of current exception
     */
    protected void logOrCheck(int type, String desc, String exInfo)
    {
        String tmp = getQuickCounters() + " " + desc;
        // Either log the exception or call checkPass/checkFail 
        //  as requested by setExpected for this type
        if (ITEM_DONT_CARE == expected[type])
        {
            // We don't care about this, just log it
            logger.logMsg(level, desc + " threw: " + exInfo);
        }
        else if (ITEM_CHECKFAIL == expected[type])
        {
            // We shouldn't have been called here, so fail
            logger.checkFail(desc + " threw-unexpected: " + exInfo);
        }
        else if (exInfo.indexOf(expected[type]) > -1)
        {   
            // We got a warning the user expected, so pass
            logger.checkPass(desc + " threw-matching: " + exInfo);
            // Also reset this counter
            //@todo needswork: this is very state-dependent, and 
            //  might not be what the user expects, but at least it 
            //  won't give lots of extra false fails or passes
            expected[type] = ITEM_DONT_CARE;
        }
        else
        {
            // We got a warning the user didn't expect, so fail
            logger.checkFail(desc + " threw-notmatching: " + exInfo);
            // Also reset this counter
            expected[type] = ITEM_DONT_CARE;
        }
    }
}
