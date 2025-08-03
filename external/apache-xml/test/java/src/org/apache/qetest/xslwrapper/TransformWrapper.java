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

import org.apache.qetest.Configurable;

/**
 * Helper interface to wrapper various XSLT processors for testing.
 *
 * A TransformWrapper wraps a particular 'flavor' of XSLT processing. 
 * This includes both a particular XSLT implementation, such as 
 * Xalan or Saxon, as well as a particular method to perform 
 * processing, like using streams or DOMs to perform transforms.
 *
 * As an important side effect, this class should return timing 
 * information about the steps done to perform the transformation.
 * Note that exactly what is timed and how it's timed should be 
 * clearly documented in implementing classes!
 * 
 * This is not a general-purpose wrapper for doing XSLT 
 * transformations: for that, you might as well use the real 
 * javax.xml.transform package itself.  However this does allow 
 * many conformance and performance tests to run comparatively 
 * between different flavors of processors.
 *
 * TransformWrapper is a bit of an awkward name, but I wanted to 
 * keep it separate from the pre-existing ProcessorWrapper that 
 * this replaces so we can ensure stability for Xalan testing 
 * while updating to this new class.
 * 
 * @author Shane Curcuru
 * @version $Id$
 */
public interface TransformWrapper extends Configurable
{

    /**
     * Timing constant: this operation did not attempt to time this 
     * item, or the item is not appropriate for this case.  
     * 
     * Currently we return a similar array of longs from every 
     * timed call, however buildStylesheet() would obviously not 
     * have entries for xmlread/xmlbuild times.  This value will 
     * be used for any times not used, since a negative number is 
     * clearly inappropriate for a time duration (until someone 
     * does some heavy relativistic optimizations).
     */
    public static final long TIME_UNUSED = -2L;


    /**
     * Timing index: overall time to complete operation.  
     * 
     * This time, which is always the first long[] array item in 
     * any timed call, is the overall amount of time taken to 
     * complete the whole operation, from start to finish.  This 
     * may include time in reading the inputs from disk and 
     * writing the outputs to disk, as well as any other time 
     * taken by the processor to complete other portions of the 
     * task.
     *
     * In general, this should not include overhead for the wrapper 
     * itself, and notably will not include time taken up while 
     * setting parameters or other attributes into the underlying 
     * processor (which are hopefully minor compared to other items).
     *
     * Note that other timing indexes may be filled in as 
     * TIME_UNUSED by calls that don't time anything useful.  E.g. 
     * buildStylesheet will not return data for xml-related times.
     */
    public static final int IDX_OVERALL = 0;


    /**
     * Timing index: time spend reading an XSL file into memory.  
     * 
     * This should be the time spent just reading the XSL file from 
     * disk into memory, to attempt to isolate disk I/O issues.  
     * Note that implementations should carefully document exactly 
     * what this operation does: just reads the data into a 
     * ByteStream, or actually builds a DOM, or whatever.
     */
    public static final int IDX_XSLREAD = 1;


    /**
     * Timing index: time to build the stylesheet.  
     * 
     * This should include the time it takes the processor 
     * implementation to take an XSL source and turn it into 
     * whatever useable form it wants.
     * 
     * In TrAX, this would normally correspond to the time it 
     * takes the newTemplates() call to return when handed a 
     * StreamSource(ByteStream) object.  Normally this would 
     * be a ByteStream already in memory, but some wrappers 
     * may not allow separating the xslread time (which would 
     * be reading the file from disk into a ByteStream in memory) 
     * from this xslbuild time.
     */
    public static final int IDX_XSLBUILD = 2;


    /**
     * Timing index: time spend reading an XML file into memory.  
     * 
     * This should be the time spent just reading the XML file from 
     * disk into memory, to attempt to isolate disk I/O issues.  
     * Note that implementations should carefully document exactly 
     * what this operation does: just reads the data into a 
     * ByteStream, or actually builds a DOM, or whatever.
     */
    public static final int IDX_XMLREAD = 3;


    /**
     * Timing index: time to build the XML document.  
     * 
     * This should include the time it takes the processor 
     * implementation to take an XML source and turn it into 
     * whatever useable form it wants.
     * 
     * In TrAX, this would normally only correspond to the time 
     * taken to create a DOMSource from an in-memory ByteStream.  
     * The Xalan-C version may use this more than the Java version.
     */
    public static final int IDX_XMLBUILD = 4;


    /**
     * Timing index: time to complete the transform from sources.  
     * 
     * Normally this should be just the time to complete outputting 
     * the whole result tree from a transform from in-memory sources.
     * Obviously different wrapper implementations will measure 
     * slightly different things in this field, which needs to be 
     * clearly documented.  
     *
     * For example, in TrAX, this would correspond to doing a 
     * transformer.transform(xmlSource, StreamResult(ByteStream))
     * where the ByteStream is in memory.  A separate time should be 
     * recorded for resultwrite that actually puts the stream out 
     * to disk.
     *
     * Note that some wrappers may not be able to separate the 
     * transform time from the resultwrite time: for example a 
     * wrapper that wants to test with new StreamResult(URL).
     */
    public static final int IDX_TRANSFORM = 5;


    /**
     * Timing index: time to write the result tree to disk.  
     * 
     * See discussion in IDX_TRANSFORM.  This may not always 
     * be used.
     */
    public static final int IDX_RESULTWRITE = 6;


    /**
     * Timing index: Time from beginning of transform from sources 
     * until the first bytes of the result tree come out.  
     * 
     * Future use.  This is for testing pipes and server 
     * applications, where the user is concerned about latency and 
     * throughput.  This will measure how responsive the processor 
     * appears to be at first (but not necessarily how long it 
     * takes to write the whole the result tree).
     */
    public static final int IDX_FIRSTLATENCY = 7;


    /**
     * URL for set/getAttribute: should be an integer to set 
     * for the output indent of the processor.
     *
     * This is a common enough attribute that most wrapper 
     * implementations should be able to support it in a 
     * straightforward manner.
     */
    public static final String ATTRIBUTE_INDENT =
        "http://xml.apache.org/xalan/wrapper/indent";


    /** 
     * URL for set/getAttribute: should be an Object to attempt 
     * to set as a diagnostic log/stream for the processor.
     *
     * //@todo see if there's a fairly generic way to implement 
     * this that will get at least some data from all common 
     * processor implementations: either by using some native 
     * diagnostics PrintStream the processor provides, or by 
     * implementing a simple LoggingErrorListener, etc.
     */
    public static final String ATTRIBUTE_DIAGNOSTICS =
        "http://xml.apache.org/xalan/wrapper/diagnostics";


    /**
     * Marker for Attributes to set on Processors.  
     * 
     * Options that startWith() this constant will actually be 
     * attempted to be set onto our underlying processor/transformer.
     */
    public static final String SET_PROCESSOR_ATTRIBUTES = 
        "Processor.setAttribute.";


    /**
     * Get a specific description of the wrappered processor.  
     *
     * @return specific description of the underlying processor or 
     * transformer implementation: this should include both the 
     * general product name, as well as specific version info.  If 
     * possible, should be implemented without actively creating 
     * an underlying processor.
     */
    public Properties getProcessorInfo();


    /**
     * Actually create/initialize an underlying processor or factory.
     * 
     * For TrAX/javax.xml.transform implementations, this creates 
     * a new TransformerFactory.  For Xalan-J 1.x this creates an 
     * XSLTProcessor.  Other implmentations may or may not actually 
     * do any work in this method.
     *
     * @param options Hashtable of options, possibly specific to 
     * that implementation.  For future use.
     *
     * @return (Object)getProcessor() as a side-effect, this will 
     * be null if there was any problem creating the processor OR 
     * if the underlying implementation doesn't use this
     *
     * @throws Exception covers any underlying exceptions thrown 
     * by the actual implementation
     */
    public Object newProcessor(Hashtable options) throws Exception;


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
     * @return array of longs denoting timing of various parts of 
     * our operation; [0] is always the total end-to-end time, and 
     * other array indicies are represented by IDX_* constants
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     */
    public long[] transform(String xmlName, String xslName, String resultName)
        throws Exception;


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
     * @return array of longs denoting timing of various parts of 
     * our operation; [0] is always the total end-to-end time, and 
     * other array indicies are represented by constants
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     *
     * @see #transformWithStylesheet(String xmlName, String resultName)
     */
    public long[] buildStylesheet(String xslName) throws Exception;


    /**
     * Reports if a pre-built/pre-compiled stylesheet is ready; 
     * presumably built by calling buildStylesheet(xslName).
     *
     * @return true if one is ready; false otherwise
     *
     * @see #buildStylesheet(String xslName)
     */
    public boolean isStylesheetReady();


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
     * @return array of longs denoting timing of various parts of 
     * our operation; [0] is always the total end-to-end time, and 
     * other array indicies are represented by constants
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
        throws Exception;


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
     * @return array of longs denoting timing of various parts of 
     * our operation; [0] is always the total end-to-end time, and 
     * other array indicies are represented by constants
     *
     * @throws Exception any underlying exceptions from the 
     * wrappered processor are simply allowed to propagate; throws 
     * a RuntimeException if any other problems prevent us from 
     * actually completing the operation
     */
    public long[] transformEmbedded(String xmlName, String resultName)
        throws Exception;


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
     * anyways; an IllegalArgumentException will be thrown; also 
     * the name must not be null.
     *
     * @param namespace for the parameter, must not contain {}
     * @param name of the parameter, must not be null
     * @param value of the parameter
     *
     * @throws IllegalArgumentException thrown if the namespace
     * or name appears to be illegal.
     */
    public void setParameter(String namespace, String name, Object value)
        throws IllegalArgumentException;


    /**
     * Get a parameter that was set with setParameter.  
     *
     * Only returns parameters set locally, not parameters exposed 
     * by the underlying processor implementation.  Not terribly 
     * useful but I like providing gets for any sets I define.
     *
     * @param namespace for the parameter, must not contain {}
     * @param name of the parameter, must not be null
     *
     * @return value of the parameter; null if not found
     */
    public Object getParameter(String namespace, String name);


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
    public void reset(boolean newProcessor);
}
