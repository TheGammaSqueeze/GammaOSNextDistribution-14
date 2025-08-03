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
import java.net.URL;
import java.util.Hashtable;
import java.util.Properties;

import org.xml.sax.InputSource;

/**
 * Implementation of TransformWrapper that uses the simplest method 
 * possible to use James Clark's XT processor.
 *
 * @author Shane Curcuru
 * @version $Id$
 */
public class XTWrapper extends TransformWrapperHelper
{

    /**
     * Get a general description of this wrapper itself.
     *
     * @return Uses XT in simplest manner possible
     */
    public String getDescription()
    {
        return "Uses XT in simplest manner possible";
    }


    /** No-op Ctor for the Xalan-J 1.x wrapper. */
    public XTWrapper(){}

    /** Reference to current processor - XT flavor - convenience method. */
    protected com.jclark.xsl.sax.XSLProcessorImpl processor = null;

    /**
     * Cached copy of newProcessor() Hashtable.
     */
    protected Hashtable newProcessorOpts = null;

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
        Properties p = new Properties();
        p.put("traxwrapper.method", "simple");
        p.put("traxwrapper.desc", getDescription());
        //@todo call XT to find version info
        return p;
    }

    /**
     * Actually create/initialize an underlying processor or factory.
     * This creates a com.jclark.xsl.sax.XSLProcessorImpl.  
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
        // Cleanup any prior objects 
        reset(false);

        processor = new com.jclark.xsl.sax.XSLProcessorImpl();

        String liaisonClassName = "com.jclark.xml.sax.CommentDriver";  // default

        try
        {
            Object parserObj = Class.forName(liaisonClassName).newInstance();

            if (parserObj instanceof XMLProcessorEx)
                processor.setParser((XMLProcessorEx) parserObj);
            else
                processor.setParser((org.xml.sax.Parser) parserObj);
        }
        catch (Exception e)
        {
            System.err.println("createNewProcesor(xt) threw: "
                               + e.toString());
            e.printStackTrace();

            processor = null;
        }

        return (Object)processor;
    }

    /**
     * Transform supplied xmlName file with the stylesheet in the 
     * xslName file into a resultName file using XT.
     *
     * @param xmlName local path\filename of XML file to transform
     * @param xslName local path\filename of XSL stylesheet to use
     * @param resultName local path\filename to put result in
     *
     * @return array of longs denoting timing of all parts of 
     * our operation: IDX_OVERALL, IDX_XSLREAD, IDX_XSLBUILD, 
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
        // Declare variables ahead of time to minimize latency
        long startTime = 0;
        long endTime = 0;

        // Create XT-specific sources
        OutputMethodHandlerImpl outHandler =
            new OutputMethodHandlerImpl(processor);

        outHandler.setDestination(new FileDestination(new File(resultName)));

        InputSource xmlIS = xtInputSourceFromString(xmlName);
        InputSource xslIS = xtInputSourceFromString(xslName);

        // Begin timing the process: stylesheet, output, and process
        startTime = System.currentTimeMillis();

        processor.loadStylesheet(xslIS);
        processor.setOutputMethodHandler(outHandler);
        processor.parse(xmlIS);

        endTime = System.currentTimeMillis();

        long[] times = getTimeArray();
        times[IDX_OVERALL] = endTime - startTime;
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
        // Declare variables ahead of time to minimize latency
        long startTime = 0;
        long endTime = 0;

        // Create XT-specific source
        InputSource xslIS = xtInputSourceFromString(xslName);

        // Begin timing loading the stylesheet
        startTime = System.currentTimeMillis();

        processor.loadStylesheet(xslIS);  // side effect: also sets the stylesheet

        endTime = System.currentTimeMillis();
        m_stylesheetReady = true;

        long[] times = getTimeArray();
        times[IDX_OVERALL] = endTime - startTime;
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

        // Declare variables ahead of time to minimize latency
        long startTime = 0;
        long endTime = 0;

        // Create XT-specific sources
        OutputMethodHandlerImpl outHandler =
            new OutputMethodHandlerImpl(processor);

        outHandler.setDestination(new FileDestination(new File(resultName)));

        InputSource xmlIS = xtInputSourceFromString(xmlName);

        // Begin timing the process: stylesheet, output, and process
        startTime = System.currentTimeMillis();

        processor.setOutputMethodHandler(outHandler);
        processor.parse(xmlIS);

        endTime = System.currentTimeMillis();

        long[] times = getTimeArray();
        times[IDX_OVERALL] = endTime - startTime;
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
        throw new RuntimeException("XTWrapper.transformEmbedded not implemented yet!");
    }

    /**
     * Worker method for using XT to process.  
     *
     * @param name local name of file
     *
     * @return InputSource for XT after munging name as needed
     */
    private InputSource xtInputSourceFromString(String name)
    {

        File file = new File(name);
        String path = file.getAbsolutePath();

        // Add absolute / to beginning if needed
        if (path.charAt(0) != '/')
            path = '/' + path;

        try
        {
            java.net.URL temp = new URL("file", "", path);

            return (new InputSource(temp.toString()));
        }
        catch (Exception e)
        {
            System.err.println("xtInputSourceFromString(xt) of: " + name
                               + " threw: " + e.toString());
            e.printStackTrace();

            return (null);
        }
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
     * Overridden for XT to call setParameter().
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
            XSLProcessorImpl p = (XSLProcessorImpl)passThru;
            //@todo: HACK: smash the namespace in - not sure if this is correct
            if (null != namespace)
            {
                name = namespace + ":" + name;
            }
            p.setParameter(name, value);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("applyParameter threw: " + e.toString());
        }
    }

}  // end of class XTWrapper

