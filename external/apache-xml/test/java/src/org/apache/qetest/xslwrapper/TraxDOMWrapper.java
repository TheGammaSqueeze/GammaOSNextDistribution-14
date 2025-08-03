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
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.QetestUtils;
import org.apache.xml.utils.DefaultErrorHandler;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Implementation of TransformWrapper that uses the TrAX API and 
 * uses DOMs for it's sources.
 *
 * This implementation records separate times for xslRead (time to 
 * parse the xsl and build a DOM) and xslBuild (time to take the 
 * DOMSource object until it's built the templates); xmlRead (time 
 * to parse the xml and build a DOM).  Note xmlBuild is not timed 
 * since it's not easily measureable in TrAX.
 * The transform time is just the time to create the DOMResult 
 * object; the resultsWrite is the separate time it takes to 
 * serialize that to disk.
 *
 * <b>Important!</b>  The underlying System property of 
 * javax.xml.transform.TransformerFactory will determine the actual 
 * TrAX implementation used.  This value will be reported out in 
 * our getProcessorInfo() method.
 * 
 * //@todo add in checks for factory.getFeature(DOMSource.FEATURE)
 * 
 * @author Shane Curcuru
 * @version $Id$
 */
public class TraxDOMWrapper extends TransformWrapperHelper
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
     * @return Uses TrAX to perform transforms from DOMSource(node)
     */
    public String getDescription()
    {
        return "Uses TrAX to perform transforms from DOMSource(node)";
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
        p.put("traxwrapper.method", "dom");
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
        // Verify the factory supports DOM!
        if (!(factory.getFeature(DOMSource.FEATURE)
              && factory.getFeature(DOMResult.FEATURE)))
        {   
            throw new TransformerConfigurationException("TraxDOMWrapper.newProcessor: factory does not support DOM!");
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
     * @return array of longs denoting timing of all parts of 
     * our operation
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
        long xslRead = 0;
        long xslBuild = 0;
        long xmlRead = 0;
        long transform = 0;
        long resultWrite = 0;
   
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

        // Timed: read xsl into a DOM
        startTime = System.currentTimeMillis();
        Node xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xslName)));
        xslRead = System.currentTimeMillis() - startTime;

        // Untimed: create DOMSource and setSystemId
        DOMSource xslSource = new DOMSource(xslNode);
        xslSource.setSystemId(QetestUtils.filenameToURL(xslName));

        // Timed: build Transformer from DOMSource
        startTime = System.currentTimeMillis();
        Transformer transformer = factory.newTransformer(xslSource);
        transformer.setErrorListener(new DefaultErrorHandler());
        xslBuild = System.currentTimeMillis() - startTime;

        // Timed: read xml into a DOM
        startTime = System.currentTimeMillis();
        Node xmlNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));
        xmlRead = System.currentTimeMillis() - startTime;

        // Untimed: create DOMSource and setSystemId
        DOMSource xmlSource = new DOMSource(xmlNode);
        xmlSource.setSystemId(QetestUtils.filenameToURL(xmlName));

        // Untimed: create DOMResult
        Document outDoc = docBuilder.newDocument();                
        DocumentFragment outNode = outDoc.createDocumentFragment();
        DOMResult domResult = new DOMResult(outNode);
        
        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: build xml (so to speak) and transform
        startTime = System.currentTimeMillis();
        transformer.transform(xmlSource, domResult);
        transform = System.currentTimeMillis() - startTime;

        // Untimed: prepare serializer with outputProperties 
        //  from the stylesheet
        Transformer resultSerializer = factory.newTransformer();
        resultSerializer.setErrorListener(new DefaultErrorHandler());
        Properties serializationProps = transformer.getOutputProperties();
        resultSerializer.setOutputProperties(serializationProps);
        
        // Timed: writeResults from the DOMResult
        startTime = System.currentTimeMillis();
        resultSerializer.transform(new DOMSource(outNode), 
                             new StreamResult(resultName));
        resultWrite = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslRead + xslBuild + xmlRead 
                             + transform + resultWrite;
        times[IDX_XSLREAD] = xslRead;
        times[IDX_XSLBUILD] = xslBuild;
        times[IDX_XMLREAD] = xmlRead;
        times[IDX_TRANSFORM] = transform;
        times[IDX_RESULTWRITE] = resultWrite;
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
     * our operation: IDX_OVERALL, IDX_XSLREAD, IDX_XSLBUILD
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
        long xslRead = 0;
        long xslBuild = 0;
        
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

        // Timed: read xsl into a DOM
        startTime = System.currentTimeMillis();
        Node xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xslName)));
        xslRead = System.currentTimeMillis() - startTime;

        // Untimed: create DOMSource and setSystemId
        DOMSource xslSource = new DOMSource(xslNode);
        xslSource.setSystemId(QetestUtils.filenameToURL(xslName));

        // Timed: build Templates from DOMSource
        startTime = System.currentTimeMillis();
        builtTemplates = factory.newTemplates(xslSource);
        xslBuild = System.currentTimeMillis() - startTime;

        m_stylesheetReady = true;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslRead + xslBuild;
        times[IDX_XSLREAD] = xslRead;
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
     * our operation: IDX_OVERALL, IDX_XMLREAD, 
     * IDX_TRANSFORM, IDX_RESULTWRITE
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
        long xmlRead = 0;
        long transform = 0;
        long resultWrite = 0;
        
        // Untimed: get Transformer from Templates
        Transformer transformer = builtTemplates.newTransformer();
        transformer.setErrorListener(new DefaultErrorHandler());

        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

         // Timed: read xml into a DOM
        startTime = System.currentTimeMillis();
        Node xmlNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));
        xmlRead = System.currentTimeMillis() - startTime;

        // Untimed: create DOMSource and setSystemId
        DOMSource xmlSource = new DOMSource(xmlNode);
        xmlSource.setSystemId(QetestUtils.filenameToURL(xmlName));

        // Untimed: create DOMResult
        Document outNode = docBuilder.newDocument();
        DOMResult domResult = new DOMResult(outNode);
        
        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: build xml (so to speak) and transform
        startTime = System.currentTimeMillis();
        transformer.transform(xmlSource, domResult);
        transform = System.currentTimeMillis() - startTime;

        // Untimed: prepare serializer with outputProperties 
        //  from the stylesheet
        Transformer resultSerializer = factory.newTransformer();
        resultSerializer.setErrorListener(new DefaultErrorHandler());
        Properties serializationProps = transformer.getOutputProperties();
        resultSerializer.setOutputProperties(serializationProps);
        
        // Timed: writeResults from the DOMResult
        startTime = System.currentTimeMillis();
        resultSerializer.transform(new DOMSource(outNode), 
                             new StreamResult(resultName));
        resultWrite = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xmlRead + transform + resultWrite;
        times[IDX_XMLREAD] = xmlRead;
        times[IDX_TRANSFORM] = transform;
        times[IDX_RESULTWRITE] = resultWrite;
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
     * to then build the Transformer therefrom), IDX_TRANSFORM, 
     * and IDX_RESULTWRITE
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     */
    public long[] transformEmbedded(String xmlName, String resultName)
        throws Exception
    {
        long startTime = 0;
        long xslRead = 0;
        long xslBuild = 0;
        long xmlRead = 0;
        long transform = 0;
        long resultWrite = 0;
   
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

        // Timed: read xml into a DOM
        startTime = System.currentTimeMillis();
        Node xmlNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xmlName)));
        xmlRead = System.currentTimeMillis() - startTime;

        // Untimed: create DOMSource and setSystemId
        DOMSource xmlSource = new DOMSource(xmlNode);
        xmlSource.setSystemId(QetestUtils.filenameToURL(xmlName));

        // Timed: readxsl from the xml document
        startTime = System.currentTimeMillis();
        Source xslSource = factory.getAssociatedStylesheet(xmlSource, 
                                                           null, null, null);
        xslRead = System.currentTimeMillis() - startTime;

        // Timed: build Transformer from Source
        startTime = System.currentTimeMillis();
        Transformer transformer = factory.newTransformer(xslSource);
        transformer.setErrorListener(new DefaultErrorHandler());
        xslBuild = System.currentTimeMillis() - startTime;

        // Untimed: create DOMResult
        Document outNode = docBuilder.newDocument();
        DOMResult domResult = new DOMResult(outNode);
        
        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(transformer, newProcessorOpts);

        // Untimed: Apply any parameters needed
        applyParameters(transformer);

        // Timed: build xml (so to speak) and transform
        startTime = System.currentTimeMillis();
        transformer.transform(xmlSource, domResult);
        transform = System.currentTimeMillis() - startTime;

        // Untimed: prepare serializer with outputProperties 
        //  from the stylesheet
        Transformer resultSerializer = factory.newTransformer();
        resultSerializer.setErrorListener(new DefaultErrorHandler());
        Properties serializationProps = transformer.getOutputProperties();
        resultSerializer.setOutputProperties(serializationProps);
        
        // Timed: writeResults from the DOMResult
        startTime = System.currentTimeMillis();
        resultSerializer.transform(new DOMSource(outNode), 
                             new StreamResult(resultName));
        resultWrite = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslRead + xslBuild + xmlRead 
                             + transform + resultWrite;
        times[IDX_XSLREAD] = xslRead;
        times[IDX_XSLBUILD] = xslBuild;
        times[IDX_XMLREAD] = xmlRead;
        times[IDX_TRANSFORM] = transform;
        times[IDX_RESULTWRITE] = resultWrite;
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
