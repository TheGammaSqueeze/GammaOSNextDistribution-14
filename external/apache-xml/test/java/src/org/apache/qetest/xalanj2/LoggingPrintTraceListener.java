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
 * LoggingPrintTraceListener.java
 *
 */
package org.apache.qetest.xalanj2;
import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.apache.xalan.templates.Constants;
import org.apache.xalan.templates.ElemTemplate;
import org.apache.xalan.templates.ElemTemplateElement;
import org.apache.xalan.templates.ElemTextLiteral;
import org.apache.xalan.trace.GenerateEvent;
import org.apache.xalan.trace.PrintTraceListener;
import org.apache.xalan.trace.SelectionEvent;
import org.apache.xalan.trace.TracerEvent;
import org.apache.xml.serializer.SerializerTrace;
import org.apache.xpath.axes.ContextNodeList;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

/**
 * Logging TraceListener interface.
 * Implementation of the TraceListener interface that
 * prints each event to our logger as it occurs.
 * Future improvements: also implement LoggingHandler properly 
 * so we can do both the PrintTraceListener-specific stuff while 
 * still looking like other LoggingHandlers.
 * @author shane_curcuru@lotus.com
 * @author myriam_midy@lotus.com
 * @version $Id$
 */
public class LoggingPrintTraceListener extends PrintTraceListener       
{

    /**
     * Accesor method for a brief description of this service.  
     * @return String "LoggingPrintTraceListener: logs and counts trace events"
     */
    public String getDescription()
    {
        return "LoggingPrintTraceListener: logs and counts trace events";
    }


    /** No-op sets logger to default.  */
    public LoggingPrintTraceListener()
    {
        super(new java.io.PrintWriter(System.err, true));
        setLogger(getDefaultLogger());
        initTrace();
    }

    /**
     * Ctor that calls setLogger automatically.  
     *
     * @param r Logger we should log to
     */
    public LoggingPrintTraceListener(Logger l)
    {
        super(new java.io.PrintWriter(System.err, true));
        setLogger(l);
        initTrace();
    }
    

    /**
     * Initialize trace fields from PrintTraceListener.
     */
    public void initTrace()
	{
        m_traceTemplates = true;
        m_traceElements = true;
        m_traceGeneration = true;
        m_traceSelection = true;
	}


     /** Our Logger, who we tell all our secrets to. */
    protected Logger logger = null;
    
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
    
    /** What loggingLevel to use for reporter.logMsg(). */
    protected int level = Logger.DEFAULT_LOGGINGLEVEL;
    

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


    /** Prefixed to all logger msg output for TraceListener.  */
    public final String prefix = "LPTL:";


    /** Cheap-o string representation of last event we got.  */
    protected String lastItem = LoggingHandler.NOTHING_HANDLED;


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
        return counters;
    }

    /**
     * Reset all items or counters we've handled.  
     */
    public void reset()
    {
        setLastItem(LoggingHandler.NOTHING_HANDLED);
        for (int i = 0; i < counters.length; i++)
        {
            counters[i] = 0;
        }
    }

    /** setExpected, etc. not yet implemented.  */

    ////////////////// Implement TraceListener ////////////////// 
    /**
     * Logging implementation of TraceListener method.
     * Method that is called when a trace event occurs.
     * The method is blocking.  It must return before processing continues.
     *
     * @param tracerEvent the trace event.
     */
    public void trace(TracerEvent tracerEvent)
    {
      super.trace(tracerEvent);
        counters[TYPE_TRACE]++;

        StringBuffer buf = new StringBuffer("trace:");
        int dumpLevel = XalanDumper.DUMP_DEFAULT;
        if (null != tracerEvent.m_mode) // not terribly elegant way to do it
            dumpLevel = XalanDumper.DUMP_NOCLOSE;
        switch (tracerEvent.m_styleNode.getXSLToken())
        {
            // Specific handling for most common 'interesting' items
            case Constants.ELEMNAME_TEXTLITERALRESULT :
                buf.append(XalanDumper.dump((ElemTextLiteral) tracerEvent.m_styleNode, dumpLevel));
                break;

            case Constants.ELEMNAME_TEMPLATE :
                buf.append(XalanDumper.dump((ElemTemplate) tracerEvent.m_styleNode, dumpLevel));
                break;

            default :
                buf.append(XalanDumper.dump((ElemTemplateElement) tracerEvent.m_styleNode, dumpLevel));
        }
        if (null != tracerEvent.m_mode)
            buf.append(XalanDumper.SEP + "m_mode=" + tracerEvent.m_mode + XalanDumper.RBRACKET);

        setLastItem(buf.toString());
        logger.logMsg(level, prefix + getLast());
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
      super.selected(selectionEvent);
        counters[TYPE_SELECTED]++;

        StringBuffer buf = new StringBuffer("selected:");
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
            buf.append(XalanDumper.dump(styleNodeElem, XalanDumper.DUMP_NOCLOSE));

        buf.append(selectionEvent.m_attributeName + "="
                   + selectionEvent.m_xpath.getPatternString() + ";");

        if (selectionEvent.m_selection.getType() == selectionEvent.m_selection.CLASS_NODESET)
        {
            // Must create as DTMNodeIterator for DTM_EXP merge 13-Jun-01
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
        buf.append(XalanDumper.RBRACKET);   // Since we said DUMP_NOCLOSE above
        setLastItem(buf.toString());
        logger.logMsg(level, prefix + getLast());
    }

    /**
     * Logging implementation of TraceListener method.
     * Method that is called just after the formatter listener is called.
     *
     * @param generateEvent the generate event.
     */
    public void generated(GenerateEvent generateEvent)
    {
      super.generated(generateEvent);
        counters[TYPE_GENERATED]++;

        StringBuffer buf = new StringBuffer("generated:");
        switch (generateEvent.m_eventtype)
        {
            case SerializerTrace.EVENTTYPE_STARTDOCUMENT :
                buf.append("STARTDOCUMENT");
            break;

            case SerializerTrace.EVENTTYPE_ENDDOCUMENT :
                buf.append("ENDDOCUMENT");
            break;

            case SerializerTrace.EVENTTYPE_STARTELEMENT :
                buf.append("STARTELEMENT[" + generateEvent.m_name + "]"); // just hardcode [ LBRACKET ] RBRACKET here
            break;

            case SerializerTrace.EVENTTYPE_ENDELEMENT :
                buf.append("ENDELEMENT[" + generateEvent.m_name + "]");
            break;

            case SerializerTrace.EVENTTYPE_CHARACTERS :
                String chars1 = new String(generateEvent.m_characters, generateEvent.m_start, generateEvent.m_length);
                buf.append("CHARACTERS[" + chars1 + "]");
            break;

            case SerializerTrace.EVENTTYPE_CDATA :
                String chars2 = new String(generateEvent.m_characters, generateEvent.m_start, generateEvent.m_length);
                buf.append("CDATA[" + chars2 + "]");
            break;

            case SerializerTrace.EVENTTYPE_COMMENT :
                buf.append("COMMENT[" + generateEvent.m_data + "]");
            break;

            case SerializerTrace.EVENTTYPE_PI :
                buf.append("PI[" + generateEvent.m_name + ", " + generateEvent.m_data + "]");
            break;

            case SerializerTrace.EVENTTYPE_ENTITYREF :
                buf.append("ENTITYREF[" + generateEvent.m_name + "]");
            break;

            case SerializerTrace.EVENTTYPE_IGNORABLEWHITESPACE :
                buf.append("IGNORABLEWHITESPACE");
            break;
        }
        setLastItem(buf.toString());
        logger.logMsg(level, prefix + getLast());
    }
}
