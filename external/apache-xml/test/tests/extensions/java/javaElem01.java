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

// explicitly packageless

import org.apache.qetest.CheckService;
import org.apache.qetest.Logger;
import org.apache.qetest.xsl.StylesheetDatalet;
import org.apache.qetest.xsl.TestableExtension;
import org.apache.qetest.xsl.XHTFileCheckService;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;

/**
 * Extension for testing xml-xalan/samples/extensions.
 * Various tests of extension elements.  
 */
public class javaElem01 extends TestableExtension
{

    /** Extension method - element called from stylesheet.  */
    public static String putString(XSLProcessorContext context,
                                   ElemExtensionCall extensionElement)
    {
        counter++;
        String attrVal = extensionElement.getAttribute("attr");
        if (null != attrVal)
            return attrVal;
        else
            return "javaElem01.putString";
    }

    /** Extension method - element called from stylesheet.  */
    public static Boolean putBoolean(XSLProcessorContext context,
                                   ElemExtensionCall extensionElement)
    {
        counter++;
        return new Boolean(true);
    }

    /** Extension method - element called from stylesheet.  */
    public static Double putDouble(XSLProcessorContext context,
                                   ElemExtensionCall extensionElement)
    {
        counter++;
        return new Double(1.1);
    }

    /** Extension method - element called from stylesheet.  */
    public static Node putNode(XSLProcessorContext context,
                                   ElemExtensionCall extensionElement)
    {
        counter++;
        String attrVal = extensionElement.getAttribute("attr");
        
        Node n = null;
        try
        {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            if (null != attrVal)
                n = doc.createTextNode(attrVal);
            else
                n = doc.createTextNode("This is a text node");
        }
        catch (Exception e)
        {
            // No-op: no easy way to report this
        }
        return n;
    }

    //// Implementations of TestableExtension
    /** Simple counter of number of times called.  */    
    private static int counter = 0;
    
    /**
     * Description of what this extension does.  
     * @return String description of extension
     */
    public static int getCounter()
    {
        return counter;
    }

    /** 
     * Perform and log any pre-transformation info.  
     * @return true if OK; false if any fatal error occoured
     * @param datalet Datalet of current stylesheet test
     */
    public static boolean preCheck(Logger logger, StylesheetDatalet datalet)
    {
        logger.logMsg(Logger.INFOMSG, "javaElem01.preCheck; counter=" + counter);
        return true;        
    }
    

    /** 
     * Perform and log any post-transformation info.  
     * 
     * The extension should validate that it's extension was 
     * properly called; we also validate output file.
     * 
     * @param logger Logger to dump any info to
     * @param datalet Datalet of current stylesheet test
     */
    public static void postCheck(Logger logger, StylesheetDatalet datalet)
    {
        // Verify that we've been called at least once
        if (counter > 0)
            logger.checkPass("javaElem01 has been called " + counter + " times");
        else
            logger.checkFail("javaElem01 has not been called");

        // We also validate the output file the normal way
        CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
        // Supply default value
        if (null == fileChecker)
            fileChecker = new XHTFileCheckService();
        fileChecker.check(logger,
                          new File(datalet.outputName), 
                          new File(datalet.goldName), 
                          "Extension test of " + datalet.getDescription());
    }


    /**
     * Description of what this extension does.  
     * @return String description of extension
     */
    public static String getDescription()
    {
        return "Basic extension element test";
    }
}

