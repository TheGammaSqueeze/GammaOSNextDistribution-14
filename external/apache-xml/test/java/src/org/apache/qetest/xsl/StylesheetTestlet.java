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
 * StylesheetTestlet.java
 *
 */
package org.apache.qetest.xsl;

import java.io.File;
import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.qetest.CheckService;
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.QetestFactory;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.TestletImpl;
import org.apache.qetest.xslwrapper.TransformWrapper;
import org.apache.qetest.xslwrapper.TransformWrapperFactory;

/**
 * Testlet for conformance testing of xsl stylesheet files.
 *
 * This class provides the default algorithm used for verifying 
 * Xalan's conformance to the XSLT spec.  It works in conjunction 
 * with StylesheetTestletDriver, which supplies the logic for 
 * choosing the testfiles to iterate over, and with 
 * TransformWrapper, which provides an XSL  processor- and 
 * method-independent way to process files (i.e. different 
 * flavors of TransformWrapper may be different products, as well 
 * as different processing models, like SAX, DOM or Streams).
 *
 * This class is broken up into common worker methods to make 
 * subclassing easier for alternate testing algorithm.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class StylesheetTestlet extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.StylesheetTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String describing what this StylesheetTestlet does.
     */
    public String getDescription()
    {
        return "StylesheetTestlet";
    }


    /**
     * Run this StylesheetTestlet: execute it's test and return.
     *
     * @param Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Ensure we have the correct kind of datalet
        StylesheetDatalet datalet = null;
        try
        {
            datalet = (StylesheetDatalet)d;
        }
        catch (ClassCastException e)
        {
            logger.checkErr("Datalet provided is not a StylesheetDatalet; cannot continue with " + d);
            return;
        }

        // Perform any other general setup needed
        testletInit(datalet);
        try
        {
            // Get a TransformWrapper of the appropriate flavor
            TransformWrapper transformWrapper = getTransformWrapper(datalet);
            // Transform our supplied input file...
            testDatalet(datalet, transformWrapper);
            transformWrapper = null;
            // ...and compare with gold data
            checkDatalet(datalet);
        }
        // Handle any exceptions from the testing
        catch (Throwable t)
        {
            handleException(datalet, t);
            return;
        }
	}


    /** 
     * Worker method to perform any pre-processing needed.  
     *
     * @param datalet to test with
     */
    protected void testletInit(StylesheetDatalet datalet)
    {
        //@todo validate our Datalet - ensure it has valid 
        //  and/or existing files available.

        // Cleanup outName only if asked to - delete the file on disk
        // Optimization: this takes extra time and often is not 
        //  needed, so only do this if the option is set
        if ("true".equalsIgnoreCase(datalet.options.getProperty("deleteOutFile")))
        {
            try
            {
                boolean btmp = (new File(datalet.outputName)).delete();
                logger.logMsg(Logger.TRACEMSG, "Deleting OutFile of::" + datalet.outputName
                                     + " status: " + btmp);
            }
            catch (SecurityException se)
            {
                logger.logMsg(Logger.WARNINGMSG, "Deleting OutFile of::" + datalet.outputName
                                       + " threw: " + se.toString());
            }
        }
    }


    /** 
     * Worker method to get a TransformWrapper.  
     *
     * @param datalet to test with
     * @return TransformWrapper to use with this datalet
     */
    protected TransformWrapper getTransformWrapper(StylesheetDatalet datalet)
    {
        TransformWrapper transformWrapper = null;
        try
        {
            transformWrapper = TransformWrapperFactory.newWrapper(datalet.flavor);
            // Set our datalet's options as options in the wrapper
            //@todo this is inefficient, since our datalet may 
            //  have many options that don't pertain to the wrapper, 
            //  but it does allow users to simply pass new options 
            //  without us having to change code
            transformWrapper.newProcessor(datalet.options);
        }
        catch (Throwable t)
        {
            logger.logThrowable(Logger.ERRORMSG, t, getDescription() + " newWrapper/newProcessor threw");
            logger.checkErr(getCheckDescription(datalet) + " newWrapper/newProcessor threw: " + t.toString());
            return null;
        }
        return transformWrapper;
    }


    /** 
     * Worker method to actually perform the transform.  
     *
     * Logs out applicable info; attempts to perform transformation.
     *
     * @param datalet to test with
     * @param transformWrapper to have perform the transform
     * @throws allows any underlying exception to be thrown
     */
    protected void testDatalet(StylesheetDatalet datalet, TransformWrapper transformWrapper)
            throws Exception
    {
        //@todo Should we log a custom logElement here instead?
        logger.logMsg(Logger.TRACEMSG, "executing with: inputName=" + datalet.inputName
                      + " xmlName=" + datalet.xmlName + " outputName=" + datalet.outputName
                      + " goldName=" + datalet.goldName + " flavor="  + datalet.flavor
                      + " paramName="  + datalet.paramName);

        // Optional: configure a test with XSLT parameters.
        final File paramFile = new File(datalet.paramName);
        if (paramFile.exists()) {
            Properties params = new Properties();
            final FileInputStream inStream = new FileInputStream(paramFile);
            try {
                params.load(inStream);
                final Iterator iter = params.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    transformWrapper.setParameter(null, entry.getKey().toString(), entry.getValue());
                }
            } finally {
                inStream.close();
            }
        }
        
        // Simply have the wrapper do all the transforming
        //  or processing for us - we handle either normal .xsl 
        //  stylesheet tests or just .xml embedded tests
        long retVal = 0L;
        if (null == datalet.inputName)
        {
            // presume it's an embedded test
            long [] times = transformWrapper.transformEmbedded(datalet.xmlName, datalet.outputName);
            retVal = times[TransformWrapper.IDX_OVERALL];
        }
        else
        {
            // presume it's a normal stylesheet test
            long[] times = transformWrapper.transform(datalet.xmlName, datalet.inputName, datalet.outputName);
            retVal = times[TransformWrapper.IDX_OVERALL];
        }
    }


    /** 
     * Worker method to validate output file with gold.  
     *
     * Logs out applicable info while validating output file.
     *
     * @param datalet to test with
     * @throws allows any underlying exception to be thrown
     */
    protected void checkDatalet(StylesheetDatalet datalet)
            throws Exception
    {
        // See if the datalet already has a fileChecker to use...
        CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");

        // ...if not, construct a default one with attributes
        if (null == fileChecker) {
	    String fcName = datalet.options.getProperty("fileChecker");
	    Class fcClazz = QetestUtils.testClassForName(fcName,
							 QetestUtils.defaultPackages, 
							 null);
	    if (null != fcClazz) {
		fileChecker = (CheckService) fcClazz.newInstance();
		fileChecker.applyAttributes(datalet.options);
	    }
	}
	
        if (null == fileChecker)
        {
            fileChecker = QetestFactory.newCheckService(logger, QetestFactory.TYPE_FILES);
            // Apply any testing options to the fileChecker
            fileChecker.applyAttributes(datalet.options);    
        }

        // Validate the file            
        if (Logger.PASS_RESULT
            != fileChecker.check(logger,
                                 new File(datalet.outputName), 
                                 new File(datalet.goldName), 
                                 getCheckDescription(datalet))
           )
        {
            // Log a custom element with all the file refs
            // Closely related to viewResults.xsl select='fileref"
            //@todo check that these links are valid when base 
            //  paths are either relative or absolute!
            Hashtable attrs = new Hashtable();
            attrs.put("idref", (new File(datalet.inputName)).getName());
            try
            {
                attrs.put("baseref", System.getProperty("user.dir"));
            } 
            catch (Exception e) { /* no-op, ignore */ }
            
            attrs.put("inputName", datalet.inputName);
            attrs.put("xmlName", datalet.xmlName);
            attrs.put("outputName", datalet.outputName);
            attrs.put("goldName", datalet.goldName);
            logger.logElement(Logger.STATUSMSG, "fileref", attrs, "Conformance test file references");
        }
    }


    /** 
     * Worker method to validate or log exceptions thrown by testDatalet.  
     *
     * Provided so subclassing is simpler; our implementation merely 
     * calls checkErr and logs the exception.
     *
     * @param datalet to test with
     * @param e Throwable that was thrown
     */
    protected void handleException(StylesheetDatalet datalet, Throwable t)
    {
        // Put the logThrowable first, so it appears before 
        //  the Fail record, and gets color-coded
        logger.logThrowable(Logger.ERRORMSG, t, getDescription() + " " + datalet.getDescription());
        logger.checkErr(getCheckDescription(datalet) 
                         + " threw: " + t.toString());
    }


    /** 
     * Worker method to construct a description.  
     *
     * Simply concatenates useful info to override getDescription().
     *
     * @param datalet to test with
     * @param e Throwable that was thrown
     */
    protected String getCheckDescription(StylesheetDatalet datalet)
    {
        return getDescription() 
                + "{" + datalet.flavor + "} "
                + datalet.getDescription();
    }
}  // end of class StylesheetTestlet

