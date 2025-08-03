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
 * LoggingLexicalHandler.java
 *
 */
package org.apache.qetest.xsl;

import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 * Cheap-o LexicalHandler for use by API tests.
 * <p>Implements LexicalHandler and dumps simplistic info 
 * everything to a Logger; a way to debug SAX stuff.</p>
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingLexicalHandler extends LoggingHandler implements LexicalHandler
{

    /** No-op sets logger to default.  */
    public LoggingLexicalHandler()
    {
        setLogger(getDefaultLogger());
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param l Logger we should log to
     */
    public LoggingLexicalHandler(Logger l)
    {
        setLogger(l);
    }


    /**
     * Our default handler that we pass all events through to.
     */
    protected LexicalHandler defaultHandler = null;


    /**
     * Set a default handler for us to wrapper.
     * Set a LexicalHandler for us to use.
     *
     * @param default Object of the correct type to pass-through to;
     * throws IllegalArgumentException if null or incorrect type
     */
    public void setDefaultHandler(Object defaultH)
    {
        try
        {
            defaultHandler = (LexicalHandler)defaultH;
        }
        catch (Throwable t)
        {
            throw new java.lang.IllegalArgumentException("setDefaultHandler illegal type: " + t.toString());
        }
    }


    /**
     * Accessor method for our default handler.
     *
     * @return default (Object) our default handler; null if unset
     */
    public Object getDefaultHandler()
    {
        return (Object)defaultHandler;
    }


    /** Prefixed to all logger msg output.  */
    public static final String prefix = "LLH:";

    /** Constant for items returned in getCounters: startDTD.  */
    public static final int TYPE_STARTDTD = 0;

    /** Constant for items returned in getCounters: endDTD.  */
    public static final int TYPE_ENDDTD = 1;

    /** Constant for items returned in getCounters: startEntity.  */
    public static final int TYPE_STARTENTITY = 2;

    /** Constant for items returned in getCounters: endEntity.  */
    public static final int TYPE_ENDENTITY = 3;

    /** Constant for items returned in getCounters: startCDATA.  */
    public static final int TYPE_STARTCDATA = 4;

    /** Constant for items returned in getCounters: endCDATA.  */
    public static final int TYPE_ENDCDATA = 5;

    /** Constant for items returned in getCounters: comment.  */
    public static final int TYPE_COMMENT = 6;


    /** 
     * Counters for how many events we've handled.  
     * Index into array are the TYPE_* constants.
     */
    protected int[] counters = 
    {
        0, /* startDTD */
        0, /* endDTD */
        0, /* startEntity */
        0, /* endEntity */
        0, /* startCDATA */
        0, /* endCDATA */
        0  /* comment */
    };


    /**
     * Get a list of counters of all items we've logged.
     * Returned in order as startDTD, endDTD, startEntity,
     * endEntity, startCDATA, endCDATA, comment.
     * Index into array are the TYPE_* constants.
     *
     * @return array of int counters for each item we log
     */
    public int[] getCounters()
    {
        return counters;
    }


    /**
     * Really Cheap-o string representation of our state.  
     *
     * @return String of getCounters() rolled up in minimal space
     */
    public String getQuickCounters()
    {
        return (prefix + "(" 
                + counters[TYPE_STARTDTD] + ", " + counters[TYPE_ENDDTD] + "; " 
                + counters[TYPE_STARTENTITY] + ", " + counters[TYPE_ENDENTITY] + "; " 
                + counters[TYPE_STARTCDATA] + ", " + counters[TYPE_ENDCDATA] + "; " 
                + counters[TYPE_COMMENT] + ")");
    }


    /** Expected values for events we may handle, default=ITEM_DONT_CARE. */
    protected String[] expected = 
    {
        ITEM_DONT_CARE, /* startDTD */
        ITEM_DONT_CARE, /* endDTD */
        ITEM_DONT_CARE, /* startEntity */
        ITEM_DONT_CARE, /* endEntity */
        ITEM_DONT_CARE, /* startCDATA */
        ITEM_DONT_CARE, /* endCDATA */
        ITEM_DONT_CARE  /* comment */
    };


    /** Cheap-o string representation of last event we got.  */
    protected String lastItem = NOTHING_HANDLED;


    /**
     * Accessor for string representation of last event we got.  
     * @param s string to set
     */
    protected void setLastItem(String s)
    {
        lastItem = s;
    }


    /**
     * Accessor for string representation of last event we got.  
     * @return last event string we had
     */
    public String getLast()
    {
        return lastItem;
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
     * Worker method to either log or call check* for this event.  
     * A simple way to validate for any kind of event.
     * Note that various events may store the various arguments 
     * they get differently, so you should check the code to 
     * ensure you're specifying the correct containsString.
     *
     * @param type of event (startdtd|enddtd|etc)
     * @param desc detail info from this kind of message
     */
    protected void logOrCheck(int type, String desc)
    {
        String tmp = getQuickCounters() + " " + desc;
        // Either log the exception or call checkPass/checkFail 
        //  as requested by setExpected for this type
        if (ITEM_DONT_CARE == expected[type])
        {
            // We don't care about this, just log it
            logger.logMsg(level, tmp);
        }
        else if (ITEM_CHECKFAIL == expected[type])
        {
            // We shouldn't have been called here, so fail
            logger.checkFail(tmp + " was unexpected");
        }
        else if ((null != desc) 
                  && (desc.indexOf(expected[type]) > -1))
        {   
            // We got a warning the user expected, so pass
            logger.checkPass(tmp + " matched");
            // Also reset this counter
            //@todo needswork: this is very state-dependent, and 
            //  might not be what the user expects, but at least it 
            //  won't give lots of extra false fails or passes
            expected[type] = ITEM_DONT_CARE;
        }
        else
        {
            // We got a warning the user didn't expect, so fail
            logger.checkFail(tmp + " did not match");
            // Also reset this counter
            expected[type] = ITEM_DONT_CARE;
        }
    }


    ////////////////// Implement LexicalHandler ////////////////// 
    public void startDTD (String name, String publicId, String systemId)
    	throws SAXException
    {
        // Note: this implies this class is !not! threadsafe
        // Increment counter and save info
        counters[TYPE_STARTDTD]++;
        setLastItem("startDTD: " + name + ", " + publicId + ", " + systemId);
        logOrCheck(TYPE_STARTDTD, getLast());
        if (null != defaultHandler)
            defaultHandler.startDTD(name, publicId, systemId);
    }

    public void endDTD ()
	    throws SAXException
    {
        counters[TYPE_ENDDTD]++;
        setLastItem("endDTD");
        logOrCheck(TYPE_ENDDTD, getLast());
        if (null != defaultHandler)
            defaultHandler.endDTD();
    }

    public void startEntity (String name)
    	throws SAXException
    {
        counters[TYPE_STARTENTITY]++;
        setLastItem("startEntity: " + name);
        logOrCheck(TYPE_STARTENTITY, getLast());
        if (null != defaultHandler)
            defaultHandler.startEntity(name);
    }

    public void endEntity (String name)
	    throws SAXException
    {
        counters[TYPE_ENDENTITY]++;
        setLastItem("endEntity: " + name);
        logOrCheck(TYPE_ENDENTITY, getLast());
        if (null != defaultHandler)
            defaultHandler.endEntity(name);
    }

    public void startCDATA ()
    	throws SAXException
    {
        counters[TYPE_STARTCDATA]++;
        setLastItem("startCDATA");
        logOrCheck(TYPE_STARTCDATA, getLast());
        if (null != defaultHandler)
            defaultHandler.startCDATA();
    }

    public void endCDATA ()
	    throws SAXException
    {
        counters[TYPE_ENDCDATA]++;
        setLastItem("endCDATA");
        logOrCheck(TYPE_ENDCDATA, getLast());
        if (null != defaultHandler)
            defaultHandler.endCDATA();
    }

    public void comment (char ch[], int start, int length)
    	throws SAXException
    {
        counters[TYPE_COMMENT]++;
        StringBuffer buf = new StringBuffer("comment: ");
        buf.append(ch);
        buf.append(", ");
        buf.append(start);
        buf.append(", ");
        buf.append(length);

        setLastItem(buf.toString());
        logOrCheck(TYPE_COMMENT, getLast());
        if (null != defaultHandler)
            defaultHandler.comment(ch, start, length);
    }

}
