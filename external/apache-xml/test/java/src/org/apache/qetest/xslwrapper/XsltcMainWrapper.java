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
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.xalan.xsltc.cmdline.Compile;
import org.apache.xalan.xsltc.cmdline.Transform;

/**
 * Implementation of TransformWrapper that uses the command line 
 * wrappers of XSLTC - interim use only.
 *
 * <b>Important!</b> This wrapper is for temporary use only: we 
 * should really focus on getting XSLTC to use JAXP 1.1 as it's 
 * interface, and then (as needed) write another custom wrapper 
 * that calls XSLTC API's directly.
 * 
 * @author Shane Curcuru
 * @version $Id$
 */
public class XsltcMainWrapper extends TransformWrapperHelper
{

    private static final char CLEAN_CHAR = '_';
    protected static final String XSLTC_COMPILER_CLASS = "org.apache.xalan.xsltc.cmdline.Compile";
    protected static final String XSLTC_RUNTIME_CLASS = "org.apache.xalan.xsltc.cmdline.Transform";

    private static final char FILE_SEPARATOR = System.getProperty("file.separator").charAt(0);
    
    /**
     * Cached copy of newProcessor() Hashtable.
     */
    protected Hashtable newProcessorOpts = null;

    /**
     * Get a general description of this wrapper itself.
     *
     * @return Uses XSLTC command line to perform transforms
     */
    public String getDescription()
    {
        return "Uses XSLTC command line to perform transforms";
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
        p.put("traxwrapper.method", "streams");
        p.put("traxwrapper.desc", getDescription());
        return p;
    }


    /**
     * Actually create/initialize an underlying processor or factory.
     * 
     * Effectively a no-op; returns null.  Just forces a reset().
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
        return null;
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
/* TWA - temp hack; use results dir to get path for to use for a dir to put
the translets
*/
     /*
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
        long startTime = 0;
        long xslBuild = 0;
        long transform = 0;

        // java org.apache.xalan.xlstc.cmdline.Compile play1.xsl
        // java org.apache.xalan.xlstc.cmdline.Transform play.xml play1 >stdout

        // Timed: compile stylesheet class from XSL file
//        String[] args1 = new String[2];
/* TWA - commented out the following for short-term
Problem when local path/file is being used, somewhere a file://// prefix is 
being appended to the filename and xsltc can't find the file even with the -u
So I strip off the protocol prefix and pass the local path/file
        args1[0] = "-u"; // Using URIs
        args1[1] = xslName;
*/
/* TWA - temporay hack to construct and pass a directory for translets */
        int last = resultName.lastIndexOf(FILE_SEPARATOR);
        String tdir = resultName.substring(0, last);
        int next = tdir.lastIndexOf(FILE_SEPARATOR);
        String transletsdirName = tdir.substring(0, next);

        String[] args1 = new String[3];
        args1[0] = "-d";
        args1[1] = transletsdirName;
        args1[2] = xslName;
        int idx = xslName.indexOf("file:////");
        if (idx != -1){
               xslName = new String(xslName.substring(8));
               args1[2] = xslName;
        }
        startTime = System.currentTimeMillis();
        /// Transformer transformer = factory.newTransformer(new StreamSource(xslName));
        Compile.main(args1);
        xslBuild = System.currentTimeMillis() - startTime;

        // Verify output file was created
        // WARNING: assumption of / here, which means we assume URI not local path - needs revisiting
        int nameStart = xslName.lastIndexOf(FILE_SEPARATOR) + 1;
        String baseName = xslName.substring(nameStart);
        int extStart = baseName.lastIndexOf('.');
        if (extStart > 0) {
            baseName = baseName.substring(0, extStart);
        }
        
        // Replace illegal class name chars with underscores.
        StringBuffer sb = new StringBuffer(baseName.length());
        char charI = baseName.charAt(0);
        sb.append(Character.isJavaLetter(charI) ? charI :CLEAN_CHAR);
        for (int i = 1; i < baseName.length(); i++) {
            charI = baseName.charAt(i);
            sb.append(Character.isJavaLetterOrDigit(charI) ? charI :CLEAN_CHAR);
        }
        baseName = sb.toString();
        
        // Untimed: Apply any parameters needed
        // applyParameters(transformer);

        // Timed: read/build xml, transform, and write results

/* TWA - I don't see how this could have worked, there is no -s option in DefaultRun
so passing it in the args2 caused usage messages to be output.
Also, we shouldn't use the -u option unless we are really using URLs, 
I'm just trying to get it to work with local path/files. With or without the 
-u option, the files were getting a file://// prefix with caused them to be not found
        String[] args2 = new String[3];
        args2[0] = "-s"; // Don't allow System.exit
        args2[1] = "-u"; // Using URIs
        args2[2] = xmlName;
        args2[3] = baseName;    // Just basename of the .class file, without the .class
                                // Note that . must be on CLASSPATH to work!
*/
        
        String[] tempParam = makeParamArray();
        String[] args2 = new String[2 + tempParam.length];
        args2[0] = xmlName;
        int idx2 = xmlName.indexOf("file:////");
        if (idx2 != -1){
               args2[0] = new String(xmlName.substring(8));
        }
        args2[1] = baseName;
        System.arraycopy(tempParam, 0, args2, 2, tempParam.length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream newSystemOut = new PrintStream(baos);
        PrintStream saveSystemOut = System.out;
        startTime = System.currentTimeMillis();
        // transformer.transform(new StreamSource(xmlName), new StreamResult(resultName));
        try
        {
            // Capture System.out into our byte array
            System.setOut(new PrintStream(baos));
            Transform.main(args2);
        }
        finally
        {
            // Be sure to restore System stuff!
            System.setOut(saveSystemOut);
        }
        // Writing data should really go in separate timing loop
        FileOutputStream fos = new FileOutputStream(resultName);
        fos.write(baos.toByteArray());
        fos.close();
        transform = System.currentTimeMillis() - startTime;

        File compiledXslClass = new File(baseName + ".class");
        //@todo WARNING! We REALLY need to clean up the name*.class files when we're done!!! -sc
        // TWA - we should probably use the -d option to put them in a desired directory first
        // I commented out the delete, to see if the translets were getting compiled
//        if (compiledXslClass.exists())
//             compiledXslClass.delete();

        long[] times = getTimeArray();
        times[IDX_OVERALL] = xslBuild + transform;
        times[IDX_XSLBUILD] = xslBuild;
        times[IDX_TRANSFORM] = transform;
        return times;
    }

    private String[] makeParamArray() {
        if (m_params == null) {
            return new String[0];
        }
        String[] params = new String[m_params.size()];
        Iterator iter = m_params.entrySet().iterator();
        int i = 0;
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            params[i++] = entry.getKey() + "=" + entry.getValue();
        }
        return params;
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
        throw new RuntimeException("buildStylesheet not implemented yet!");
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

        throw new RuntimeException("transformWithStylesheet not implemented yet!");
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
        throw new RuntimeException("transformEmbedded not implemented yet!");
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
        // builtTemplates = null;
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
     * WARNING: Not implemented! 27-Apr-01
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
        /* WARNING: Not implemented! 27-Apr-01 */
    }
}
