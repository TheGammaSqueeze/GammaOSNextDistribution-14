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
import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.xml.utils.DefaultErrorHandler;

/**
 * Implementation of TransformWrapper that uses the TrAX API and 
 * uses files for it's sources.
 *
 * This is the second most common usage:
 * transformer = factory.newTransformer(new StreamSource(new File(xslName)));
 * transformer.transform(new StreamSource(new File(xmlName)), new StreamResult(resultFileName));
 *
 * <b>Important!</b>  The underlying System property of 
 * javax.xml.transform.TransformerFactory will determine the actual 
 * TrAX implementation used.  This value will be reported out in 
 * our getProcessorInfo() method.
 * 
 * @author Shane Curcuru
 * @version $Id$
 */
public class TraxFileWrapper extends TransformWrapperHelper
{

    /**
     * TransformerFactory to use; constructed in newProcessor().
     */
    protected TransformerFactory factory = null;


    /**
     * Templates to use for buildStylesheet().
     */
    protected Templates builtTemplates = null;


    /**
     * Cached copy of newProcessor() Hashtable.
     */
    protected Hashtable newProcessorOpts = null;


    /**
     * Get a general description of this wrapper itself.
     *
     * @return Uses TrAX to perform transforms from StreamSource(systemId)
     */
    public String getDescription()
    {
        return "Uses TrAX to perform transforms from StreamSource(new File(filename))";
    }


    /**
     * Get a specific description of the wrappered processor.  
     *
     * @return specific description of the underlying processor or 
     * transformer implementation: this should include both the 
     * general product name, as well as specific version info.  If 
     * possible, should be implemented without actively creating 
     * an underlying processor.
     */
    public Properties getProcessorInfo()
    {
        Properties p = TraxWrapperUtils.getTraxInfo();
        p.put("traxwrapper.method", "file");
        p.put("traxwrapper.desc", getDescription());
        return p;
    }


    /**
     * Actually create/initialize an underlying processor or factory.
     * 
     * For TrAX/javax.xml.transform implementations, this creates 
     * a new TransformerFactory.  For Xalan-J 1.x this creates an 
     * XSLTProcessor.  Other implmentations may or may not actually 
     * do any work in this method.
     *
     * @param options Hashtable of options, unused.
     *
     * @return (Object)getProcessor() as a side-effect, this will 
     * be null if there was any problem creating the processor OR 
     * if the underlying implementation doesn't use this
     *
     * @throws Exception covers any underlying exceptions thrown 
     * by the actual implementation
     */
    public Object newProcessor(Hashtable options) throws Exception
    {
        newProcessorOpts = options;
        //@todo do we need to do any other cleanup?
        reset(false);
        factory = TransformerFactory.newInstance();
        factory.setErrorListener(new DefaultErrorHandler());
        // Verify the factory supports Streams!
        if (!(factory.getFeature(StreamSource.FEATURE)
              && factory.getFeature(StreamResult.FEATURE)))
        {   
            throw new TransformerConfigurationException("TraxFileWrapper.newProcessor: factory does not support Streams!");
        }
        // Set any of our options as Attributes on the factory
        TraxWrapperUtils.setAttributes(factory, options);
        return (Object)factory;
    }


    /**
     * Transform supplied xmlName file with the stylesheet in the 
     * xslName file into a resultName file.
     *
     * Names are assumed to be local path\filename references, and 
     * will be converted to URLs as needed for any underlying 
     * processor implementation.
     *
     * @param xmlName local path\filename of XML file to transform
     * @param xslName local path\filename of XSL stylesheet to use
     * @param resultName local path\filename to put result in
     *
     * @return array of longs denoting timing of only these parts of 
     * our operation: IDX_OVERALL, IDX_XSLBUILD, IDX_TRANSFORM
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     */
    public long[] transform(String xmlName, String xslName, String resultName)
        throws Exception
    {
        preventFootShooting();
        long startTime = 0;
        long xslBuild = 0;
        long transform = 0;
        
        // Timed: read/build xsl from a File
        startTime = System.currentTimeMillis();
        Transformer transformer = factory.newTransformer(new StreamSource(new File(xslName)));
        transformer.setErrorListener(new DefaultErrorHandler());
        xslBuild = System.currentTimeMillis() - startTime;

        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: read/build xml, transform, and write results
        startTime = System.currentTimeMillis();
        transformer.transform(new StreamSource(new File(xmlName)), new StreamResult(resultName));
        transform = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslBuild + transform;
        times[IDX_XSLBUILD] = xslBuild;
        times[IDX_TRANSFORM] = transform;
        return times;
    }


    /**
     * Pre-build/pre-compile a stylesheet.
     *
     * Although the actual mechanics are implementation-dependent, 
     * most processors have some method of pre-setting up the data 
     * needed by the stylesheet itself for later use in transforms.
     * In TrAX/javax.xml.transform, this equates to creating a 
     * Templates object.
     * 
     * Sets isStylesheetReady() to true if it succeeds.  Users can 
     * then call transformWithStylesheet(xmlName, resultName) to 
     * actually perform a transformation with this pre-built 
     * stylesheet.
     *
     * @param xslName local path\filename of XSL stylesheet to use
     *
     * @return array of longs denoting timing of only these parts of 
     * our operation: IDX_OVERALL, IDX_XSLBUILD
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     *
     * @see #transformWithStylesheet(String xmlName, String resultName)
     */
    public long[] buildStylesheet(String xslName) throws Exception
    {
        preventFootShooting();
        long startTime = 0;
        long xslBuild = 0;
        
        // Timed: read/build xsl from a URL
        startTime = System.currentTimeMillis();
        builtTemplates = factory.newTemplates(new StreamSource(new File(xslName)));
        xslBuild = System.currentTimeMillis() - startTime;
        m_stylesheetReady = true;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslBuild;
        times[IDX_XSLBUILD] = xslBuild;
        return times;
    }


    /**
     * Transform supplied xmlName file with a pre-built/pre-compiled 
     * stylesheet into a resultName file.  
     *
     * User must have called buildStylesheet(xslName) beforehand,
     * obviously.
     * Names are assumed to be local path\filename references, and 
     * will be converted to URLs as needed.
     *
     * @param xmlName local path\filename of XML file to transform
     * @param resultName local path\filename to put result in
     *
     * @return array of longs denoting timing of only these parts of 
     * our operation: IDX_OVERALL, IDX_XSLBUILD, IDX_TRANSFORM
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation; throws an 
     * IllegalStateException if isStylesheetReady() == false.
     *
     * @see #buildStylesheet(String xslName)
     */
    public long[] transformWithStylesheet(String xmlName, String resultName)
        throws Exception
    {
        if (!isStylesheetReady())
            throw new IllegalStateException("transformWithStylesheet() when isStylesheetReady() == false");

        preventFootShooting();
        long startTime = 0;
        long transform = 0;
        
        // UNTimed: get Transformer from Templates
        Transformer transformer = builtTemplates.newTransformer();
        transformer.setErrorListener(new DefaultErrorHandler());

        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: read/build xml, transform, and write results
        startTime = System.currentTimeMillis();
        transformer.transform(new StreamSource(new File(xmlName)), 
                              new StreamResult(resultName));
        transform = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = transform;
        times[IDX_TRANSFORM] = transform;
        return times;
    }


    /**
     * Transform supplied xmlName file with a stylesheet found in an 
     * xml-stylesheet PI into a resultName file.
     *
     * Names are assumed to be local path\filename references, and 
     * will be converted to URLs as needed.  Implementations will 
     * use whatever facilities exist in their wrappered processor 
     * to fetch and build the stylesheet to use for the transform.
     *
     * @param xmlName local path\filename of XML file to transform
     * @param resultName local path\filename to put result in
     *
     * @return array of longs denoting timing of only these parts of 
     * our operation: IDX_OVERALL, IDX_XSLREAD (time to find XSL
     * reference from the xml-stylesheet PI), IDX_XSLBUILD, (time 
     * to then build the Transformer therefrom), IDX_TRANSFORM
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     */
    public long[] transformEmbedded(String xmlName, String resultName)
        throws Exception
    {
        preventFootShooting();
        long startTime = 0;
        long xslRead = 0;
        long xslBuild = 0;
        long transform = 0;
        
        // Timed: readxsl from the xml document
        startTime = System.currentTimeMillis();
        Source xslSource = factory.getAssociatedStylesheet(new StreamSource(new File(xmlName)), 
                                                              null, null, null);
        xslRead = System.currentTimeMillis() - startTime;

        // Timed: build xsl from a URL
        startTime = System.currentTimeMillis();
        Transformer transformer = factory.newTransformer(xslSource);
        transformer.setErrorListener(new DefaultErrorHandler());
        xslBuild = System.currentTimeMillis() - startTime;

        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: read/build xml, transform, and write results
        startTime = System.currentTimeMillis();
        transformer.transform(new StreamSource(new File(xmlName)), 
                              new StreamResult(resultName));
        transform = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslRead + xslBuild + transform;
        times[IDX_XSLREAD] = xslRead;
        times[IDX_XSLBUILD] = xslBuild;
        times[IDX_TRANSFORM] = transform;
        return times;
    }


    /**
     * Reset our parameters and wrapper state, and optionally 
     * force creation of a new underlying processor implementation.
     *
     * This always clears our built stylesheet and any parameters 
     * that have been set.  If newProcessor is true, also forces a 
     * re-creation of our underlying processor as if by calling 
     * newProcessor().
     *
     * @param newProcessor if we should reset our underlying 
     * processor implementation as well
     */
    public void reset(boolean newProcessor)
    {
        super.reset(newProcessor); // clears indent and parameters
        m_stylesheetReady = false;
        builtTemplates = null;
        if (newProcessor)
        {
            try
            {
                newProcessor(newProcessorOpts);
            }
            catch (Exception e)
            {
                //@todo Hmm: what should we do here?
            }
        }
    }


    /**
     * Apply a single parameter to a Transformer.
     *
     * Overridden to take a Transformer and call setParameter().
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
        try
        {
            Transformer t = (Transformer)passThru;
            // Munge the namespace into the name per 
            //  javax.xml.transform.Transformer.setParameter()
            if (null != namespace)
            {
                name = "{" + namespace + "}" + name;
            }
            t.setParameter(name, value);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("applyParameter threw: " + e.toString());
        }
    }


    /**
     * Ensure newProcessor has been called when needed.
     *
     * Prevent users from shooting themselves in the foot by 
     * calling a transform* API before newProcessor().
     *
     * (Sorry, I couldn't resist)
     */
    public void preventFootShooting() throws Exception
    {
        if (null == factory)
            newProcessor(newProcessorOpts);
    }
}
