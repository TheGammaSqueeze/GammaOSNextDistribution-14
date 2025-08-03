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
 * LoggingTraceListenerEx.java
 *
 */
package org.apache.qetest.xalanj2;
import java.util.Hashtable;

import org.apache.qetest.Logger;
import org.apache.xalan.templates.ElemTemplateElement;
import org.apache.xalan.trace.EndSelectionEvent;
import org.apache.xalan.trace.TraceListenerEx;
import org.apache.xpath.axes.ContextNodeList;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

/**
 * Logging TraceListenerEx interface.
 * Implementation of the TraceListenerEx interface that
 * prints each event to our logger as it occurs; simply adds 
 * impl of new selectedEnd event to LoggingTraceListener.
 * Future improvements: allow you to specify a set of 
 * expected events to validate.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingTraceListenerEx extends LoggingTraceListener 
       implements TraceListenerEx
{

    /**
     * Accesor method for a brief description of this service.  
     * @return String "LoggingTraceListenerEx: logs and counts trace and end events"
     */
    public String getDescription()
    {
        return "LoggingTraceListenerEx: logs and counts trace and end events";
    }


    /** No-op sets logger to default.  */
    public LoggingTraceListenerEx()
    {
        setLogger(getDefaultLogger());
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param r Logger we should log to
     */
    public LoggingTraceListenerEx(Logger l)
    {
        setLogger(l);
    }


    /** Constant for getCounters()[]: selected events.  */
    public static final int TYPE_SELECTED_END = 3;

    /** 
     * Counters for how many events we've handled.  
     * Index into array are the TYPE_* constants.
     */
    protected int[] counters = 
    {
        0, /* trace */
        0, /* generated */
        0, /* selected */
        0  /* selectedEnd */
    };

    public int getCounterEx()
    {
        return counters[TYPE_SELECTED_END];
        
    }
    /** setExpected, etc. not yet implemented.  */

    ////////////////// Implement TraceListenerEx ////////////////// 

    /**
     * Method that is called after an xsl:apply-templates or 
     * xsl:for-each selection occurs.
     *
     * @param ev the generate event.
     *
     * @throws javax.xml.transform.TransformerException
     */
    public void selectEnd(EndSelectionEvent endSelectionEvent) 
          throws javax.xml.transform.TransformerException
    {
        counters[TYPE_SELECTED_END]++;
        Hashtable attrs = new Hashtable();
        attrs.put("event", "selectEnd");
        attrs.put("location", "L" + endSelectionEvent.m_styleNode.getLineNumber()
                  + "C" + endSelectionEvent.m_styleNode.getColumnNumber());

        StringBuffer buf = new StringBuffer("  <styleNode>");
        ElemTemplateElement styleNodeElem = (ElemTemplateElement) endSelectionEvent.m_styleNode;
        ElemTemplateElement parent = (ElemTemplateElement) styleNodeElem.getParentNode();
        if (parent == styleNodeElem.getStylesheetRoot().getDefaultRootRule())
        {
            buf.append("[default-root-rule]");
        }
        else if (parent == styleNodeElem.getStylesheetRoot().getDefaultTextRule())
        {
            buf.append("[default-text-rule]");
        }
        else if (parent == styleNodeElem.getStylesheetRoot().getDefaultRule())
        {
            buf.append("[default-rule]");
        }
        else
            buf.append(XalanDumper.dump(styleNodeElem, XalanDumper.DUMP_DEFAULT));
            
        buf.append("  </styleNode>\n");

        buf.append("  <m_xpath>" + endSelectionEvent.m_attributeName + "="
                   + endSelectionEvent.m_xpath.getPatternString() + "</m_xpath>\n");

        buf.append("  <m_selection>");
        if (endSelectionEvent.m_selection.getType() == endSelectionEvent.m_selection.CLASS_NODESET)
        {
            NodeIterator nl = endSelectionEvent.m_selection.nodeset();

            if (nl instanceof ContextNodeList)
            {
                try
                {
                    nl = ((ContextNodeList)nl).cloneWithReset();
                }
                catch(CloneNotSupportedException cnse)
                {
                    buf.append("[Can't trace nodelist, threw: CloneNotSupportedException]");
                }
                Node pos = nl.nextNode();

                if (null == pos)
                {
                    buf.append("[empty node list]");
                }
                else // (null == pos)
                {
                    while (null != pos)
                    {
                        buf.append(" " + pos);
                        pos = nl.nextNode();
                    }
                }
            }
            else // (nl instanceof ContextNodeList)
            {
                buf.append("[Can't trace nodelist: it isn't a ContextNodeList]");
            }
        }
        else // (selectionEvent.m_selection.getType() == selectionEvent.m_selection.CLASS_NODESET)
        {
            buf.append("[" + endSelectionEvent.m_selection.str() +"]");
        }
        buf.append("</m_selection>\n");

        buf.append("  <m_sourceNode>" + XalanDumper.dump(endSelectionEvent.m_sourceNode, XalanDumper.DUMP_DEFAULT) + "</m_sourceNode>\n");

        setLastItem(buf.toString());
        logger.logElement(level, TRACE_LISTENER_DUMP, attrs, buf.toString());
    }
}
