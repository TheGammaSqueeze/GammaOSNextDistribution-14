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
 * LoggingURIResolver.java
 *
 */
package org.apache.qetest.trax;

import java.util.Hashtable;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.apache.xml.utils.SystemIDResolver;
import org.xml.sax.InputSource;
//-------------------------------------------------------------------------

/**
 * Implementation of URIResolver that logs all calls.
 * Currently just provides default service; returns null.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingURIResolver extends LoggingHandler implements URIResolver
{

    /** No-op sets logger to default.  */
    public LoggingURIResolver()
    {
        setLogger(getDefaultLogger());
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param l Logger we should log to
     */
    public LoggingURIResolver(Logger l)
    {
        setLogger(l);
    }


    /**
     * Our default handler that we pass all events through to.
     */
    protected URIResolver defaultHandler = null;


    /**
     * Set a default handler for us to wrapper.
     * Set a URIResolver for us to use.
     * // Note that we don't currently have a default URIResolver, 
     * //  so the LoggingURIResolver class will just attempt 
     * //  to use the SystemIDResolver class instead
     *
     * @param default Object of the correct type to pass-through to;
     * throws IllegalArgumentException if null or incorrect type
     */
    public void setDefaultHandler(Object defaultU)
    {
        try
        {
            defaultHandler = (URIResolver)defaultU;
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
    public static final String prefix = "LUR:";


    /** 
     * Counter for how many URIs we've resolved.  
     */
    protected int[] counters = { 0 };


    /**
     * Get a list of counters of all items we've logged.
     * Only a single array item is returned.
     *
     * @return array of int counter for each item we log
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
        return (prefix + "(" + counters[0] + ")");
    }


    /** Cheap-o string representation of last URI we resolved.  */
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


    /** Expected value(s) for URIs we may resolve, default=ITEM_DONT_CARE. */
    protected String[] expected = { ITEM_DONT_CARE };


    /** Counter used when expected is an ordered array. */
    protected int expectedCtr = 0;


    /**
     * Ask us to report checkPass/Fail for certain URIs we resolve.
     *
     * @param itemType ignored, we only do one type
     * @param containsString a string to look for within whatever 
     * item we handle - usually checked for by seeing if the actual 
     * item we handle contains the containsString
     */
    public void setExpected(int itemType, String containsString)
    {
        // Default to don't care on null
        if (null == containsString)
            containsString = ITEM_DONT_CARE;

        expected = new String[1];
        expected[0] = containsString;
    }

    /**
     * Ask us to report checkPass/Fail for an ordered list of URIs 
     * we may resolve.
     *
     * Users can specify an array of expected URIs we should be 
     * resolving in order.  Both the specific items and the exact 
     * order must occour for us to call checkPass for each URI; 
     * we call checkFail for any URI that doesn't match or is out 
     * of order.  After we run off the end of the array, we 
     * go back to the defaul of ITEM_DONT_CARE.
     * Reset by reset(), of course.
     *
     * @param containsStrings[] and array of items to look for in 
     * order: this allows you to test a stylesheet that has 
     * three xsl:imports, for example
     */
    public void setExpected(String[] containsStrings)
    {
        // Default to don't care on null
        if ((null == containsStrings) || (0 == containsStrings.length))
        {
            expected = new String[1];
            expected[0] = ITEM_DONT_CARE;
        }
        else
        {
            expected = new String[containsStrings.length];
            System.arraycopy(containsStrings, 0, expected, 0, containsStrings.length);
        }
        expectedCtr = 0;
    }

    /**
     * Cheap-o worker method to get a string value.
     * //@todo improve string return value
     *
     * @param i InputSource to get a string from
     * @return some String representation thereof
     */
    private String getString(InputSource i)
    {
        return i.toString();
    }


    /**
     * Reset all items or counters we've handled.  
     */
    public void reset()
    {
        setLastItem(NOTHING_HANDLED);
        counters[0] = 0;
        expected = new String[1];
        expected[0] = ITEM_DONT_CARE;
        expectedCtr = 0;
    }


    /**
     * Worker method to either log or call check* for this event.  
     * A simple way to validate for any kind of event.
     *
     * @param desc detail info from this kind of message
     */
    protected void checkExpected(String desc, String resolvedTo)
    {
        // Note the order of logging is important, which is why
        //  we store these values and then log them later
        final int DONT_CARE = 0;
        final int PASS = 1;
        final int FAIL = 2;
        int checkResult = DONT_CARE;
        String checkDesc = null;
        StringBuffer extraInfo = new StringBuffer("");
        Hashtable attrs = new Hashtable();
        attrs.put("source", "LoggingURIResolver");
        attrs.put("counters", getQuickCounters());
        attrs.put("resolvedTo", resolvedTo);

        String tmp = getQuickCounters() + " " + desc;
        if (expectedCtr > expected.length)
        {
            // Sanity check: prevent AIOOBE 
            expectedCtr = expected.length;
            extraInfo.append(getQuickCounters() 
                          + " error: array overbounds " + expectedCtr + "\n");
        }
        // Either log the exception or call checkPass/checkFail 
        //  as requested by setExpected for this type
        if (ITEM_DONT_CARE == expected[expectedCtr])
        {
            // We don't care about this, just log it
            extraInfo.append("ITEM_DONT_CARE(" + expectedCtr + ") " + tmp + "\n");
        }
        else if (ITEM_CHECKFAIL == expected[expectedCtr])
        {
            // We shouldn't have been called here, so fail
            checkResult = FAIL;
            checkDesc = tmp + " was unexpected";
        }
        else if ((null != desc) 
                  && (desc.indexOf(expected[expectedCtr]) > -1))
        {   
            // We got a warning the user expected, so pass
            checkResult = PASS;
            checkDesc = tmp + " matched";
            // Also reset this counter
            expected[expectedCtr] = ITEM_DONT_CARE;
        }
        else
        {
            // We got a warning the user didn't expect, so fail
            checkResult = FAIL;
            checkDesc = tmp + " did not match";
            // Also reset this counter
            expected[expectedCtr] = ITEM_DONT_CARE;
        }
        // If we have a list of expected items, increment
        if (expected.length > 1)
        {
            expectedCtr++;
            // If we run off the end, reset all expected
            if (expectedCtr >= expected.length)
            {
                extraInfo.append("Ran off end of expected items, resetting\n");
                expected = new String[1];
                expected[0] = ITEM_DONT_CARE;
                expectedCtr = 0;
            }
        }
        logger.logElement(level, "loggingHandler", attrs, extraInfo);
        if (PASS == checkResult)
            logger.checkPass(checkDesc);
        else if (FAIL == checkResult)
            logger.checkFail(checkDesc);
        // else - DONT_CARE is no-op
    }


    ////////////////// Implement URIResolver ////////////////// 
    /**
     * This will be called by the processor when it encounters
     * an xsl:include, xsl:import, or document() function.
     *
     * @param href An href attribute, which may be relative or absolute.
     * @param base The base URI in effect when the href attribute was encountered.
     *
     * @return A non-null Source object.
     *
     * @throws TransformerException
     */
    public Source resolve(String href, String base) 
            throws TransformerException
    {
        counters[0]++;
        setLastItem("{" + base + "}" + href);
        // Store the source we're about to resolve - note that the 
        //  order of logging and calling checkExpected is important
        Source resolvedSource = null;
        String resolvedTo = null;    

        if (null != defaultHandler)
        {
            resolvedTo = "resolved by: " + defaultHandler;
            resolvedSource = defaultHandler.resolve(href, base);
        }
        else
        {
            // Note that we don't currently have a default URIResolver, 
            //  so the LoggingURIResolver class will just attempt 
            //  to use the SystemIDResolver class instead
            String sysId = SystemIDResolver.getAbsoluteURI(href, base);
            resolvedTo = "resolved into new StreamSource(" + sysId + ")";
            resolvedSource = new StreamSource(sysId);
        }

        // Call worker method to log out various info and then 
        //  call check for us if needed
        checkExpected(getLast(), resolvedTo);
        return resolvedSource;
    }
}
