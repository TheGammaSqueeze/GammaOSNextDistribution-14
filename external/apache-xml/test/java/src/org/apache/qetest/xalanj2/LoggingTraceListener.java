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
 * LoggingTraceListener.java
 *
 */
package org.apache.qetest.xalanj2;
import java.util.Hashtable;

import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.apache.xalan.templates.Constants;
import org.apache.xalan.templates.ElemTemplate;
import org.apache.xalan.templates.ElemTemplateElement;
import org.apache.xalan.templates.ElemTextLiteral;
import org.apache.xalan.trace.GenerateEvent;
import org.apache.xalan.trace.SelectionEvent;
import org.apache.xalan.trace.TraceListener;
import org.apache.xalan.trace.TracerEvent;
import org.apache.xml.serializer.SerializerTrace;
import org.apache.xpath.axes.ContextNodeList;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

/**
 * Logging TraceListener interface.
 * Implementation of the TraceListener interface that
 * prints each event to our logger as it occurs.
 * Future improvements: allow you to specify a set of 
 * expected events to validate.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingTraceListener extends LoggingHandler 
       implements TraceListener
{

    /**
     * Accesor method for a brief description of this service.  
     * @return String "LoggingTraceListener: logs and counts trace events"
     */
    public String getDescription()
    {
        return "LoggingTraceListener: logs and counts trace events";
    }


    /** No-op sets logger to default.  */
    public LoggingTraceListener()
    {
        setLogger(getDefaultLogger());
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param r Logger we should log to
     */
    public LoggingTraceListener(Logger l)
    {
        setLogger(l);
    }


    /**
     * Set a default handler for us to wrapper - no-op.
     * Since you can add multiple TraceListeners, there's no sense 
     * in us wrappering another one.
     * @param default Object unused
     */
    public void setDefaultHandler(Object noop)
    {
        /* no-op */
    }


    /**
     * Accessor method for our default handler - no-op.
     * @return null
     */
    public Object getDefaultHandler()
    {
        return null;
    }


    /** Cheap-o string representation of last event we got.  */
    protected String lastItem = NOTHING_HANDLED;


    /**
     * Accessor for string representation of last trace event.  
     * @param s string to set
     */
    protected void setLastItem(String s)
    {
        lastItem = s;
    }


    /**
     * Accessor for string representation of last trace event.  
     * @return last event string we had
     */
    public String getLast()
    {
        return lastItem;
    }


    /** Constant for getCounters()[]: trace events.  */
    public static final int TYPE_TRACE = 0;

    /** Constant for getCounters()[]: generated events.  */
    public static final int TYPE_GENERATED = 1;

    /** Constant for getCounters()[]: selected events.  */
    public static final int TYPE_SELECTED = 2;

    /** 
     * Counters for how many events we've handled.  
     * Index into array are the TYPE_* constants.
     */
    protected int[] counters = 
    {
        0, /* trace */
        0, /* generated */
        0  /* selected */
    };


    /**
     * Get a list of counters of all items we've logged.
     * Returned as trace, generated, selected
     * Index into array are the TYPE_* constants.
     *
     * @return array of int counters for each item we log
     */
    public int[] getCounters()
    {
        return this.counters;
    }

    /**
     * Reset all items or counters we've handled.  
     */
    public void reset()
    {
        setLastItem(NOTHING_HANDLED);
        for (int i = 0; i < this.counters.length; i++)
        {
            this.counters[i] = 0;
        }
    }

    /** setExpected, etc. not yet implemented.  */

    ////////////////// Implement TraceListener ////////////////// 

    /** Name of custom logElement each event outputs: traceListenerDump.  */
    public static final String TRACE_LISTENER_DUMP = "traceListenerDump";

    /**
     * Logging implementation of TraceListener method.
     * Method that is called when a trace event occurs.
     * The method is blocking.  It must return before processing continues.
     *
     * @param tracerEvent the trace event.
     */
    public void trace(TracerEvent tracerEvent)
    {
        counters[TYPE_TRACE]++;

        Hashtable attrs = new Hashtable();
        attrs.put("event", "trace");
        attrs.put("location", "L" + tracerEvent.m_styleNode.getLineNumber()
                  + "C" + tracerEvent.m_styleNode.getColumnNumber());

        StringBuffer buf = new StringBuffer("  <styleNode>");
        switch (tracerEvent.m_styleNode.getXSLToken())
        {
            // Specific handling for most common 'interesting' items
            case Constants.ELEMNAME_TEXTLITERALRESULT :
                buf.append(XalanDumper.dump((ElemTextLiteral) tracerEvent.m_styleNode, XalanDumper.DUMP_DEFAULT));
                break;

            case Constants.ELEMNAME_TEMPLATE :
                buf.append(XalanDumper.dump((ElemTemplate) tracerEvent.m_styleNode, XalanDumper.DUMP_DEFAULT));
                break;

            default :
                buf.append(XalanDumper.dump((ElemTemplateElement) tracerEvent.m_styleNode, XalanDumper.DUMP_DEFAULT));
        }
        buf.append("  </styleNode>\n");
        // Always add the mode value; will either use toString() 
        //  automatically or will print 'null'
        buf.append("  <m_mode>" + tracerEvent.m_mode + "</m_mode>\n");

        // Also dump the sourceNode too!
        buf.append("  <m_sourceNode>" + XalanDumper.dump(tracerEvent.m_sourceNode, XalanDumper.DUMP_DEFAULT) + "</m_sourceNode>\n");

        setLastItem(buf.toString());
        logger.logElement(level, TRACE_LISTENER_DUMP, attrs, buf.toString());
    }

    /**
     * Logging implementation of TraceListener method.
     * Method that is called just after the formatter listener is called.
     *
     * @param selectionEvent the selected event.
     * @throws javax.xml.transform.TransformerException never thrown
     */
    public void selected(SelectionEvent selectionEvent) 
            throws javax.xml.transform.TransformerException
    {
        counters[TYPE_SELECTED]++;

        Hashtable attrs = new Hashtable();
        attrs.put("event", "selected");
        attrs.put("location", "L" + selectionEvent.m_styleNode.getLineNumber()
                  + "C" + selectionEvent.m_styleNode.getColumnNumber());

        StringBuffer buf = new StringBuffer("  <styleNode>");
        ElemTemplateElement styleNodeElem = (ElemTemplateElement) selectionEvent.m_styleNode;
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

        buf.append("  <m_xpath>" + selectionEvent.m_attributeName + "="
                   + selectionEvent.m_xpath.getPatternString() + "</m_xpath>\n");

        buf.append("  <m_selection>");
        if (selectionEvent.m_selection.getType() == selectionEvent.m_selection.CLASS_NODESET)
        {
            NodeIterator nl = selectionEvent.m_selection.nodeset();

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
            buf.append("[" + selectionEvent.m_selection.str() +"]");
        }
        buf.append("</m_selection>\n");
        buf.append("  <m_sourceNode>" + XalanDumper.dump(selectionEvent.m_sourceNode, XalanDumper.DUMP_DEFAULT) + "</m_sourceNode>\n");
        setLastItem(buf.toString());
        logger.logElement(level, TRACE_LISTENER_DUMP, attrs, buf.toString());
    }

    /**
     * Logging implementation of TraceListener method.
     * Method that is called just after the formatter listener is called.
     *
     * @param generateEvent the generate event.
     */
    public void generated(GenerateEvent generateEvent)
    {
        counters[TYPE_GENERATED]++;

        Hashtable attrs = new Hashtable();
        attrs.put("event", "generated");

        StringBuffer buf = new StringBuffer("  <eventtype ");
        switch (generateEvent.m_eventtype)
        {
            case SerializerTrace.EVENTTYPE_STARTDOCUMENT :
                buf.append("type=\"STARTDOCUMENT\">");
            break;

            case SerializerTrace.EVENTTYPE_ENDDOCUMENT :
                buf.append("type=\"ENDDOCUMENT\">");
            break;

            case SerializerTrace.EVENTTYPE_STARTELEMENT :
                buf.append("type=\"STARTELEMENT\">" + generateEvent.m_name);
            break;

            case SerializerTrace.EVENTTYPE_ENDELEMENT :
                buf.append("type=\"ENDELEMENT\">" + generateEvent.m_name);
            break;

            case SerializerTrace.EVENTTYPE_CHARACTERS :
                String chars1 = new String(generateEvent.m_characters, generateEvent.m_start, generateEvent.m_length);
                buf.append("type=\"CHARACTERS\">" + chars1);
            break;

            case SerializerTrace.EVENTTYPE_CDATA :
                String chars2 = new String(generateEvent.m_characters, generateEvent.m_start, generateEvent.m_length);
                buf.append("type=\"CDATA\">" + chars2);
            break;

            case SerializerTrace.EVENTTYPE_COMMENT :
                buf.append("type=\"COMMENT\">" + generateEvent.m_data);
            break;

            case SerializerTrace.EVENTTYPE_PI :
                buf.append("type=\"PI\">" + generateEvent.m_name + ", " + generateEvent.m_data);
            break;

            case SerializerTrace.EVENTTYPE_ENTITYREF :
                buf.append("type=\"ENTITYREF\">" + generateEvent.m_name);
            break;

            case SerializerTrace.EVENTTYPE_IGNORABLEWHITESPACE :
                buf.append("type=\"IGNORABLEWHITESPACE\">");
            break;
        }
        buf.append("</eventtype>\n");
        setLastItem(buf.toString());
        logger.logElement(level, TRACE_LISTENER_DUMP, attrs, buf.toString());
    }
}
