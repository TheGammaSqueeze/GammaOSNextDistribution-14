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
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.qetest.QetestUtils;
import org.apache.xml.utils.DefaultErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Implementation of TransformWrapper that uses the TrAX API and 
 * uses SAXSource/SAXResult whenever possible.
 *
 * <p>This implementation uses SAX to build the stylesheet and 
 * to perform the transformation.</p>
 * 
 * <p><b>Important!</b>  The underlying System property of 
 * javax.xml.transform.TransformerFactory will determine the actual 
 * TrAX implementation used.  This value will be reported out in 
 * our getProcessorInfo() method.</p>
 *
 * @author Shane Curcuru
 * @version $Id$
 */
public class TraxSAXWrapper extends TransformWrapperHelper
{

    /**
     * TransformerFactory to use; constructed in newProcessor().
     */
    protected TransformerFactory factory = null;


    /**
     * SAXTransformerFactory we actually use; constructed in newProcessor().
     */
    protected SAXTransformerFactory saxFactory = null;


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
     * @return Uses TrAX to perform transforms from SAXSource(systemId)
     */
    public String getDescription()
    {
        return "Uses TrAX to perform transforms from SAXSource(stream)";
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
        p.put("traxwrapper.method", "sax");
        p.put("traxwrapper.desc", getDescription());
        return p;
    }


    /**
     * Actually create/initialize an underlying processor or factory.
     * 
     * For TrAX/javax.xml.transform implementations, this creates 
     * a new TransformerFactory.  
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
        // Verify the factory supports SAX!
        if (!(factory.getFeature(SAXSource.FEATURE)
              && factory.getFeature(SAXResult.FEATURE)))
        {   
            throw new TransformerConfigurationException("TraxSAXWrapper.newProcessor: factory does not support SAX!");
        }
        // Set any of our options as Attributes on the factory
        TraxWrapperUtils.setAttributes(factory, options);
        saxFactory = (SAXTransformerFactory)factory;
        return (Object)saxFactory;
    }


    /**
     * Transform supplied xmlName file with the stylesheet in the 
     * xslName file into a resultName file using SAX.
     *
     * Pseudocode:
     * <code> 
     *   // Read/build stylesheet
     *   xslReader.setContentHandler(templatesHandler);
     *   xslReader.parse(xslName);
     *
     *   xslOutputProps = templates.getOutputProperties();
     *   // Set features and tie in DTD, lexical, etc. handling
     *   
     *   serializingHandler.getTransformer().setOutputProperties(xslOutputProps);
     *   serializingHandler.setResult(new StreamResult(outBytes));
     *   stylesheetHandler.setResult(new SAXResult(serializingHandler));
     *   xmlReader.setContentHandler(stylesheetHandler); 
     *   // Perform Transform
     *   xmlReader.parse(xmlName);
     *   // Separately: write bytes to disk
     * </code>
     *
     * @param xmlName local path\filename of XML file to transform
     * @param xslName local path\filename of XSL stylesheet to use
     * @param resultName local path\filename to put result in
     *
     * @return array of longs denoting timing of all parts of 
     * our operation: IDX_OVERALL, IDX_XSLBUILD, 
     * IDX_TRANSFORM, IDX_RESULTWRITE
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
        long resultWrite = 0;

        // Create a ContentHandler to handle parsing of the xsl
        TemplatesHandler templatesHandler = saxFactory.newTemplatesHandler();

        // Create an XMLReader and set its ContentHandler.
        // Be sure to use the JAXP methods only!
        XMLReader xslReader = getJAXPXMLReader();
        xslReader.setContentHandler(templatesHandler);

        // Timed: read/build Templates from StreamSource
        startTime = System.currentTimeMillis();
        xslReader.parse(QetestUtils.filenameToURL(xslName));
        xslBuild = System.currentTimeMillis() - startTime;

        // Get the Templates object from the ContentHandler.
        Templates templates = templatesHandler.getTemplates();
        // Get the outputProperties from the stylesheet, so 
        //  we can later use them for the serialization
        Properties xslOutputProps = templates.getOutputProperties();

        // Create a ContentHandler to handle parsing of the XML
        TransformerHandler stylesheetHandler = saxFactory.newTransformerHandler(templates);
        // Also set systemId to the stylesheet
        stylesheetHandler.setSystemId(QetestUtils.filenameToURL(xslName));

        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(stylesheetHandler.getTransformer(), newProcessorOpts);

        // Apply any parameters needed
        applyParameters(stylesheetHandler.getTransformer());

        // Use a new XMLReader to parse the XML document
        XMLReader xmlReader = getJAXPXMLReader();
        xmlReader.setContentHandler(stylesheetHandler); 

        // Set the ContentHandler to also function as LexicalHandler,
        // includes "lexical" events (e.g., comments and CDATA). 
        xmlReader.setProperty(
                "http://xml.org/sax/properties/lexical-handler", 
                stylesheetHandler);

        // Also attempt to set as a DeclHandler, which Xalan-J 
        //  supports even though it is not required by JAXP
        // Ignore exceptions for other processors since this 
        //  is not a required setting
        try
        {
            xmlReader.setProperty(
                    "http://xml.org/sax/properties/declaration-handler",
                    stylesheetHandler);
        } 
        catch (SAXException se) { /* no-op - ignore */ }
        
        // added by sb. Tie together DTD and other handling
        xmlReader.setDTDHandler(stylesheetHandler);
        try
        {
            xmlReader.setFeature(
                    "http://xml.org/sax/features/namespace-prefixes",
                    true);
        }
        catch (SAXException se) { /* no-op - ignore */ }
        try
        {
            xmlReader.setFeature(
                    "http://apache.org/xml/features/validation/dynamic",
                    true);
        }
        catch (SAXException se) { /* no-op - ignore */ }

        // Create a 'pipe'-like identity transformer, so we can 
        //  easily get our results serialized to disk
        TransformerHandler serializingHandler = saxFactory.newTransformerHandler();
        // Set the stylesheet's output properties into the output 
        //  via it's Transformer
        serializingHandler.getTransformer().setOutputProperties(xslOutputProps);

        // Create StreamResult to byte stream in memory
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        StreamResult byteResult = new StreamResult(outBytes);
        serializingHandler.setResult(byteResult);

        // Create a SAXResult dumping into our 'pipe' serializer
        //  and tie in lexical handling (is any other handling needed?)
        SAXResult saxResult = new SAXResult(serializingHandler);
        saxResult.setLexicalHandler(serializingHandler);

        // Set the original stylesheet to dump into our result
        stylesheetHandler.setResult(saxResult);

        // Timed: Parse the XML input document and do transform
        startTime = System.currentTimeMillis();
        xmlReader.parse(QetestUtils.filenameToURL(xmlName));
        transform = System.currentTimeMillis() - startTime;

        // Timed: writeResults from the byte array
        startTime = System.currentTimeMillis();
        byte[] writeBytes = outBytes.toByteArray(); // Should this be timed too or not?
                                                    // @see TraxStreamWrapper
        FileOutputStream writeStream = new FileOutputStream(resultName);
        writeStream.write(writeBytes);
        writeStream.close();
        resultWrite = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslBuild + transform + resultWrite;
        times[IDX_XSLBUILD] = xslBuild;
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
     * @return array of longs denoting timing of all parts of 
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

        // Create a ContentHandler to handle parsing of the xsl
        TemplatesHandler templatesHandler = saxFactory.newTemplatesHandler();

        // Create an XMLReader and set its ContentHandler.
        XMLReader xslReader = getJAXPXMLReader();
        xslReader.setContentHandler(templatesHandler);

        // Timed: read/build Templates from StreamSource
        startTime = System.currentTimeMillis();
        xslReader.parse(QetestUtils.filenameToURL(xslName));
        xslBuild = System.currentTimeMillis() - startTime;

        // Also set systemId to the stylesheet
        templatesHandler.setSystemId(QetestUtils.filenameToURL(xslName));

        // Get the Templates object from the ContentHandler.
        builtTemplates = templatesHandler.getTemplates();
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
     * @return array of longs denoting timing of all parts of 
     * our operation: IDX_OVERALL, 
     * IDX_XMLREAD, IDX_TRANSFORM, IDX_RESULTWRITE
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
        long xslRead = 0;
        long xslBuild = 0;
        long xmlRead = 0;
        long transform = 0;
        long resultWrite = 0;
   
        // Get the outputProperties from the stylesheet, so 
        //  we can later use them for the serialization
        Properties xslOutputProps = builtTemplates.getOutputProperties();

        // Create a ContentHandler to handle parsing of the XML
        TransformerHandler stylesheetHandler = saxFactory.newTransformerHandler(builtTemplates);

        // Untimed: Set any of our options as Attributes on the transformer
        TraxWrapperUtils.setAttributes(stylesheetHandler.getTransformer(), newProcessorOpts);

        // Apply any parameters needed
        applyParameters(stylesheetHandler.getTransformer());

        // Use a new XMLReader to parse the XML document
        XMLReader xmlReader = getJAXPXMLReader();
        xmlReader.setContentHandler(stylesheetHandler); 

        // Set the ContentHandler to also function as LexicalHandler,
        // includes "lexical" events (e.g., comments and CDATA). 
        xmlReader.setProperty(
                "http://xml.org/sax/properties/lexical-handler", 
                stylesheetHandler);
        xmlReader.setProperty(
                "http://xml.org/sax/properties/declaration-handler",
                stylesheetHandler);
        
        // added by sb. Tie together DTD and other handling
        xmlReader.setDTDHandler(stylesheetHandler);
        try
        {
            xmlReader.setFeature(
                    "http://xml.org/sax/features/namespace-prefixes",
                    true);
        }
        catch (SAXException se) { /* no-op - ignore */ }
        try
        {
            xmlReader.setFeature(
                    "http://apache.org/xml/features/validation/dynamic",
                    true);
        }
        catch (SAXException se) { /* no-op - ignore */ }

        // Create a 'pipe'-like identity transformer, so we can 
        //  easily get our results serialized to disk
        TransformerHandler serializingHandler = saxFactory.newTransformerHandler();
        // Set the stylesheet's output properties into the output 
        //  via it's Transformer
        serializingHandler.getTransformer().setOutputProperties(xslOutputProps);

        // Create StreamResult to byte stream in memory
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        StreamResult byteResult = new StreamResult(outBytes);
        serializingHandler.setResult(byteResult);

        // Create a SAXResult dumping into our 'pipe' serializer
        //  and tie in lexical handling (is any other handling needed?)
        SAXResult saxResult = new SAXResult(serializingHandler);
        saxResult.setLexicalHandler(serializingHandler);

        // Set the original stylesheet to dump into our result
        stylesheetHandler.setResult(saxResult);

        // Timed: Parse the XML input document and do transform
        startTime = System.currentTimeMillis();
        xmlReader.parse(QetestUtils.filenameToURL(xmlName));
        transform = System.currentTimeMillis() - startTime;

        // Timed: writeResults from the byte array
        startTime = System.currentTimeMillis();
        byte[] writeBytes = outBytes.toByteArray(); // Should this be timed too or not?
                                                    // @see TraxStreamWrapper
        FileOutputStream writeStream = new FileOutputStream(resultName);
        writeStream.write(writeBytes);
        writeStream.close();
        resultWrite = System.currentTimeMillis() - startTime;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = transform + resultWrite;
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

        throw new RuntimeException("TraxSAXWrapper.transformEmbedded not implemented yet!");
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


    /**
     * Worker method to get an XMLReader.
     *
     * Not the most efficient of methods, but makes the code simpler.
     *
     * @return a new XMLReader for use, with setNamespaceAware(true)
     */
    protected XMLReader getJAXPXMLReader()
            throws Exception
    {
        // Be sure to use the JAXP methods only!
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        return saxParser.getXMLReader();
    }
}
