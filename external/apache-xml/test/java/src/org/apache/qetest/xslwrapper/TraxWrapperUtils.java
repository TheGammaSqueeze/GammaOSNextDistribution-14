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
package org.apache.qetest.xslwrapper;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.apache.xalan.trace.PrintTraceListener;
import org.apache.xalan.trace.TraceManager;
import org.apache.xalan.transformer.TransformerImpl;

/**
 * Cheap-o utilities for Trax*Wrapper implementations.
 *
 * @author Shane Curcuru
 * @version $Id$
 */
public abstract class TraxWrapperUtils
{

    /**
     * Get a generic description of the TrAX related info.  
     *
     * @return Properties block with basic info about any TrAX 
     * implementing processor, plus specific version information
     * about Xalan-J 2.x or Xerces-J 1.x if found
     */
    public static Properties getTraxInfo()
    {
        Properties p = new Properties();
        p.put("traxwrapper.api", "trax");
        p.put("traxwrapper.language", "java");
        try
        {
            Properties sysProps = System.getProperties();
            p.put("traxwrapper.jaxp.transform", 
                  sysProps.getProperty("javax.xml.transform.TransformerFactory", "unset"));
            p.put("traxwrapper.jaxp.parser.dom", 
                  sysProps.getProperty("javax.xml.parsers.DocumentBuilderFactory", "unset"));
            p.put("traxwrapper.jaxp.parser.sax", 
                  sysProps.getProperty("javax.xml.parsers.SAXParserFactory", "unset"));
        }
        // In case we're in an Applet
        catch (SecurityException se) { /* no-op, ignore */ }

        // Look for some Xalan/Xerces specific version info
        try
        {
            Class clazz = Class.forName("org.apache.xerces.framework.Version");
            // Found 1.x, grab it's version fields
            Field f = clazz.getField("fVersion");
            p.put("traxwrapper.xerces.version", (String)f.get(null));
        }
        catch (Exception e1) { /* no-op, ignore */ }

        try
        {
            Class clazz = Class.forName("org.apache.xalan.processor.XSLProcessorVersion");
            Field f = clazz.getField("S_VERSION");
            p.put("traxwrapper.xalan.version", (String)f.get(null));
        }
        catch (Exception e2) { /* no-op, ignore */ }

        return p;
    }


    /**
     * Apply specific Attributes to a TransformerFactory OR 
     * Transformer, OR call specific setFoo() API's on a 
     * TransformerFactory OR Transformer.  
     *
     * Filters on hashkeys.startsWith("Processor.setAttribute.")
     * Most Attributes are simply passed to factory.setAttribute(), 
     * however certain special cases are handled:
     * setURIResolver, setErrorListener.
     * Exceptions thrown by underlying transformer are propagated.
     * 
     * This takes an Object so that an underlying worker method can 
     * process either a TransformerFactory or a Transformer.
     *
     * @param factory TransformerFactory to call setAttributes on.
     * @param attrs Hashtable of potential attributes to set.
     */
    public static void setAttributes(Object setPropsOn, 
                                     Hashtable attrs)
                                     throws IllegalArgumentException
    {
        if ((null == setPropsOn) || (null == attrs))
            return;

        Enumeration attrKeys = null;
        try
        {
            // Attempt to use as a Properties block..
            attrKeys = ((Properties)attrs).propertyNames();
        }
        catch (ClassCastException cce)
        {
            // .. but fallback to get as Hashtable instead
            attrKeys = attrs.keys();
        }

        while (attrKeys.hasMoreElements())
        {
            String key = (String) attrKeys.nextElement();
            // Only attempt to set the attr if it matches our marker
            if ((null != key)
                && (key.startsWith(TransformWrapper.SET_PROCESSOR_ATTRIBUTES)))
            {
                Object value = null;
                try
                {
                    // Attempt to use as a Properties block..
                    value = ((Properties)attrs).getProperty(key);
                    // But, if null, then try getting as hash anyway
                    if (null == value)
                    {
                        value = attrs.get(key);
                    }
                }
                catch (ClassCastException cce)
                {
                    // .. but fallback to get as Hashtable instead
                    value = attrs.get(key);
                }
                // Strip off our marker for the property name
                String processorKey = key.substring(TransformWrapper.SET_PROCESSOR_ATTRIBUTES.length());
                // Ugly, but it works -sc
                if (setPropsOn instanceof TransformerFactory)
                    setAttribute((TransformerFactory)setPropsOn, processorKey, value);
                else if (setPropsOn instanceof Transformer)
                    setAttribute((Transformer)setPropsOn, processorKey, value);
                // else - ignore it, no-op    
            }
        }

    }


    /** Token specifying a call to setURIResolver.  */
    public static String SET_URI_RESOLVER = "setURIResolver";

    /** Token specifying a call to setErrorListener.  */
    public static String SET_ERROR_LISTENER = "setErrorListener";

    /** Token specifying a call to setup a trace listener.  */
    public static String SET_TRACE_LISTENER = "setTraceListener";

    /**
     * Apply specific Attributes to a TransformerFactory OR call 
     * specific setFoo() API's on a TransformerFactory.  
     *
     * Filters on hashkeys.startsWith("Processor.setAttribute.")
     * Most Attributes are simply passed to factory.setAttribute(), 
     * however certain special cases are handled:
     * setURIResolver, setErrorListener.
     * Exceptions thrown by underlying transformer are propagated.
     *
     * @see setAttribute(Transformer, String, Object)
     * @param factory TransformerFactory to call setAttributes on.
     * @param key specific attribute or special case attr.
     * @param value to set for key.
     */
    private static void setAttribute(TransformerFactory factory, 
                                     String key, 
                                     Object value)
                                     throws IllegalArgumentException
    {
        if ((null == factory) || (null == key))
            return;

        // Note: allow exceptions to propagate here

        // Check if this is a special case to call a specific 
        //  API, or the general case to call setAttribute(key...)
        if (SET_URI_RESOLVER.equals(key))
        {
            factory.setURIResolver((URIResolver)value);
        }
        else if (SET_ERROR_LISTENER.equals(key))
        {
            factory.setErrorListener((ErrorListener)value);
        }
        else if (SET_TRACE_LISTENER.equals(key))
        {
            // no-op
        }
        else
        {
            // General case; just call setAttribute
            factory.setAttribute(key, value);
        }
    }


    /**
     * Apply specific Attributes to a Transformer OR call 
     * specific setFoo() API's on a Transformer.  
     *
     * Filters on hashkeys.startsWith("Processor.setAttribute.")
     * Only certain special cases are handled:
     * setURIResolver, setErrorListener.
     * Exceptions thrown by underlying transformer are propagated.
     *
     * @see setAttribute(TransformerFactory, String, Object)
     * @param factory TransformerFactory to call setAttributes on.
     * @param key specific attribute or special case attr.
     * @param value to set for key.
     */
    private static void setAttribute(Transformer transformer, 
                                     String key, 
                                     Object value)
                                     throws IllegalArgumentException
    {
        if ((null == transformer) || (null == key))
            return;

        // Note: allow exceptions to propagate here

        // Check if this is a special case to call a specific 
        //  API, or the general case to call setAttribute(key...)
        if (SET_URI_RESOLVER.equals(key))
        {
            transformer.setURIResolver((URIResolver)value);
        }
        else if (SET_ERROR_LISTENER.equals(key))
        {
            transformer.setErrorListener((ErrorListener)value);
        }
        else if (SET_TRACE_LISTENER.equals(key) && transformer instanceof TransformerImpl)
        {
            // Android-changed: TransformerImpl in 2.7.1 doesn't have getTraceManager() method.
            // TraceManager traceManager = ((TransformerImpl)transformer).getTraceManager();
            try {
                FileOutputStream writeStream = new FileOutputStream((String)value);
                PrintWriter printWriter = new PrintWriter(writeStream, true);
                PrintTraceListener traceListener = new PrintTraceListener(printWriter);
                traceListener.m_traceElements = true;
                traceListener.m_traceGeneration = true;
                traceListener.m_traceSelection = true;
                traceListener.m_traceTemplates = true;
                // Android-changed: TransformerImpl in 2.7.1 doesn't have getTraceManager() method.
                // traceManager.addTraceListener(traceListener);
            } catch (FileNotFoundException fnfe) {
                System.out.println("File not found: " + fnfe);
            } catch (Exception e) {
                System.out.println("Exception: " + e);
            }
        }
        else
        {
            // General case; no-op; no equivalent
        }
    }
}
