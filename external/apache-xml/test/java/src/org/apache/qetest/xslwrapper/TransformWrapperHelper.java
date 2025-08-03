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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

/**
 * A few default implementations of TransformWrapper methods.  
 *
 * A TransformWrapperHelper implements a few of the common methods 
 * from TransformWrapper that don't directly interact with the 
 * underlying processor.  Individual wrapper implementations are 
 * free to extend this class to get some free code.
 * 
 * @author Shane Curcuru
 * @version $Id$
 */
public abstract class TransformWrapperHelper implements TransformWrapper
{

    /** Constant denoting that indent should not be set.  */ 
    protected static final int NO_INDENT = -2;


    /** 
     * Current number of spaces to indent, default: NO_INDENT.  
     * Users call setAttribute(ATTRIBUTE_INDENT, int) to set this. 
     * If it is set, it will be applied to an underlying processor 
     * during each transform operation, where supported.
     */ 
    protected int m_indent = NO_INDENT;


    /**
     * Allows the user to ask the wrapper to set specific 
     * attributes on the underlying implementation.  
     *
     * Supported attributes in this class include:
     * ATTRIBUTE_INDENT
     *
     * //@todo define behavior for subclasses who want our default 
     * behavior but also want to throw the exception for 
     * attributes that are not recognized.
     *
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     *
     * @throws IllegalArgumentException thrown only if we can't 
     * parse an int from the value
     * 
     * @see #ATTRIBUTE_INDENT
     */
    public void setAttribute(String name, Object value)
        throws IllegalArgumentException
    {
        if (ATTRIBUTE_INDENT.equals(name))
        {
            try
            {
                m_indent = (new Integer((String)value)).intValue();
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("setAttribute: bad value: " + value);
            }
        }
    }


    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * This method should attempt to set any applicable attributes 
     * found in the given attrs onto itself, and will ignore any and 
     * all attributes it does not recognize.  It should never 
     * throw exceptions.  This method may overwrite any previous 
     * attributes that were set.  Currently since this takes a 
     * Properties block you may only be able to set objects that 
     * are Strings, although individual implementations may 
     * attempt to use Hashtable.get() on only the local part.
     * 
     * Currently unimplemented; no-op.
     *
     * @param attrs Props of various name, value attrs.
     */
    public void applyAttributes(Properties attrs)
    {
        /* no-op */;
    }


    /**
     * Allows the user to retrieve specific attributes on the 
     * underlying implementation.  
     * 
     * This class merely returns the indent for ATTRIBUTE_INDENT.
     *
     * //@todo define behavior for subclasses who want our default 
     * behavior but also want to throw the exception for 
     * attributes that are not recognized.
     *
     * @param name The name of the attribute.
     * @return value The value of the attribute.
     * @throws IllegalArgumentException not thrown from this class
     * 
     * @see #ATTRIBUTE_INDENT
     */
    public Object getAttribute(String name) throws IllegalArgumentException
    {
        if (ATTRIBUTE_INDENT.equals(name))
        {
            return new Integer(m_indent);
        }
        return null;
    }


    /** If our wrapper has a built stylesheet ready.  */ 
    protected boolean m_stylesheetReady = false;


    /**
     * Reports if a pre-built/pre-compiled stylesheet is ready; 
     * presumably built by calling buildStylesheet(xslName).
     *
     * @return true if one is ready; false otherwise
     *
     * @see #buildStylesheet(String xslName)
     */
    public boolean isStylesheetReady()
    {
        return m_stylesheetReady;
    }


    /** Set of stylesheet parameters for use in transforms.  */ 
    protected Hashtable m_params = null;


    /**
     * Set a stylesheet parameter for use in later transforms.  
     *
     * This method merely stores the triple for use later in a 
     * transform operation.  Note that the actual mechanisims for 
     * setting parameters in implementation differ, especially with 
     * regards to namespaces.
     *
     * Note that the namespace may not contain the "{" or "}"
     * characters, since these would be illegal XML namespaces 
     * anyways; an IllegalArgumentException will be thrown.
     * Note that the name may not begin with the "{" 
     * character, since it would likely be an illegal XML name
     * anyways; an IllegalArgumentException will be thrown.
     *
     * @param namespace for the parameter
     * @param name of the parameter
     * @param value of the parameter
     *
     * @throws IllegalArgumentException thrown if the namespace
     * appears to be illegal.
     */
    public void setParameter(String namespace, String name, Object value)
        throws IllegalArgumentException
    {
        if (null != namespace)
        {
            if ((namespace.indexOf("{") > -1) 
                || (namespace.indexOf("}") > -1))
                throw new IllegalArgumentException(
                    "setParameter: illegal namespace includes brackets: " + namespace);
        }
        if (null != name)
        {
            if (name.startsWith("{"))
                throw new IllegalArgumentException(
                    "setParameter: illegal name begins with bracket: " + name);
        }
        
        if (null == m_params)
            m_params = new Hashtable();

        if (null != namespace)
        {
            m_params.put("{" + namespace + "}" + name, value);
        }
        else
        {
            m_params.put(name, value);
        }
    }


    /**
     * Get a parameter that was set with setParameter.  
     *
     * Only returns parameters set locally, not parameters exposed 
     * by the underlying processor implementation.  Not terribly useful 
     * but I always like providing gets for any sets I define.
     *
     * @param namespace for the parameter
     * @param name of the parameter
     *
     * @param value of the parameter; null if not found
     */
    public Object getParameter(String namespace, String name)
    {
        if (null == m_params)
            return null;

        if (null != namespace)
        {
            return m_params.get("{" + namespace + "}" + name);
        }
        else
        {
            return m_params.get(name);
        }
    }


    /**
     * Apply the parameters that were set with setParameter to
     * our underlying processor implementation.  
     *
     * Subclasses may call this to apply all set parameters during 
     * each transform if they override the applyParameter() method 
     * to set a single parameter.
     *
     * This is a convenience method for getting data out of 
     * m_params that was encoded by our setParameter().
     *
     * @param passThru to be passed to each applyParameter() method 
     * call - for TrAX, you might pass a Transformer object.
     */
    protected void applyParameters(Object passThru)
    {
        if (null == m_params)
            return;

        for (Enumeration keys = m_params.keys();
             keys.hasMoreElements(); 
             /* no increment portion */ )
        {
            String namespace = null;
            String name = null;
            String key = keys.nextElement().toString();
            //@todo compare with TransformerImpl.setParameter's use of a StringTokenizer(..., "{}"...
            // Decode the namespace, if present
            if (key.startsWith("{"))
            {
                int idx = key.indexOf("}");
                namespace = key.substring(1, idx);
                name = key.substring(idx + 1); //@todo check for out of range?
            }
            else
            {
                // namespace stays null
                name = key;
            }
            // Call subclassed worker method for each parameter
            applyParameter(passThru, namespace, name, m_params.get(key));
        }
    }


    /**
     * Apply a single parameter to our underlying processor 
     * implementation: must be overridden.
     *
     * Subclasses must override; this class will throw an 
     * IllegalStateException since we can't do anything.
     *
     * @param passThru to be passed to each applyParameter() method 
     * call - for TrAX, you might pass a Transformer object.
     * @param namespace for the parameter, may be null
     * @param name for the parameter, should not be null
     * @param value for the parameter, may be null
     */
    protected void applyParameter(Object passThru, String namespace, 
                                  String name, Object value)
    {
        throw new IllegalStateException("TransformWrapperHelper.applyParameter must be overridden!");
    }


    /**
     * Reset our parameters and wrapper state, and optionally 
     * force creation of a new underlying processor implementation.
     *
     * This class clears the indent and any parameters. 
     * Subclasses are free to call us to get this default behavior
     * or not.  Note that subclasses must clear m_stylesheetReady
     * themselves if needed.  
     *
     * @param newProcessor ignored in this class
     */
    public void reset(boolean newProcessor)
    {
        m_params = null;
        m_indent = NO_INDENT;
    }


    /**
     * Static worker method to return default array of longs.
     *
     * Simply returns long[] pre-filled to TIME_UNUSED, suitable 
     * for returning from various transform API's.  May be called 
     * by external callers to get pre-sized array.
     *
     * @return long[] = TIME_UNUSED
     */
    public static long[] getTimeArray()
    {
        return new long[]
        {
            TIME_UNUSED, /* IDX_OVERALL */
            TIME_UNUSED, /* IDX_XSLREAD */
            TIME_UNUSED, /* IDX_XSLBUILD */
            TIME_UNUSED, /* IDX_XMLREAD */
            TIME_UNUSED, /* IDX_XMLBUILD */
            TIME_UNUSED, /* IDX_TRANSFORM */
            TIME_UNUSED, /* IDX_RESULTWRITE */
            TIME_UNUSED  /* IDX_FIRSTLATENCY */
        };
    }


    /**
     * Static worker method to return description of timing slots.
     *
     * @return String describing this idx slot in a getTimeArray
     */
    public static String getTimeArrayDesc(int idx)
    {
        switch (idx)
        {
            case IDX_OVERALL:
                return "OVERALL";

            case IDX_XSLREAD:
                return "XSLREAD";
                
            case IDX_XSLBUILD:
                return "XSLBUILD";
                
            case IDX_XMLREAD:
                return "XMLREAD";
                
            case IDX_XMLBUILD:
                return "XMLBUILD";
                
            case IDX_TRANSFORM:
                return "TRANSFORM";
                
            case IDX_RESULTWRITE:
                return "RESULTWRITE";
                
            case IDX_FIRSTLATENCY:
                return "FIRSTLATENCY";
                
            default:
                return "ERROR:unknown-getTimeArrayDesc-idx";
        }
    }
}
