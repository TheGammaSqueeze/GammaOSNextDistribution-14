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
import java.util.Hashtable;

/**
 * Extension for testing xml-xalan/samples/extensions.  
 */
public class javaSample4 extends TestableExtension
{
    static Hashtable counters = new Hashtable ();

    /** Simple extension method to setup hashtable.  */
    public void init(org.apache.xalan.extensions.XSLProcessorContext context, 
                       org.w3c.dom.Element elem)
    {
        counter++; // every method call increments plain counter
        String name = elem.getAttribute("name");
        String value = elem.getAttribute("value");
        int val;
        try
        {
            val = Integer.parseInt (value);
        } 
        catch (NumberFormatException e)
        {
            e.printStackTrace ();
            val = 0;
        }
        counters.put (name, new Integer (val));
    }


    /** Simple extension method to get a value from the hashtable.  */
    public int read(String name)
    { 
        counter++; // every method call increments plain counter
        Integer cval = (Integer)counters.get(name);
        return (cval == null) ? 0 : cval.intValue();
    }


    /** Simple extension method to increment a value in the hashtable.  */
    public void incr(org.apache.xalan.extensions.XSLProcessorContext context,  
                     org.w3c.dom.Element elem)
    {
        counter++; // every method call increments plain counter
        String name = elem.getAttribute("name");
        Integer cval = (Integer) counters.get(name);
        int nval = (cval == null) ? 0 : (cval.intValue () + 1);
        counters.put (name, new Integer (nval));
    }


    //// Implementations of TestableExtension
    /** Plain counter of number of times called.  */    
    private static int counter = 0;
    

    /** 
     * Perform and log any pre-transformation info.  
     * @return true if OK; false if any fatal error occoured
     * @param datalet Datalet of current stylesheet test
     */
    public static boolean preCheck(Logger logger, StylesheetDatalet datalet)
    {
        logger.logMsg(Logger.INFOMSG, "javaSample4.preCheck; counter=" + counter);
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
        // Dump out our hashtable for user analysis
        logger.logHashtable(Logger.STATUSMSG, counters, "javaSample4.postCheck() counters");

        // Verify that we've been called at least once
        //@todo update to verify specific number of calls and hash entries
        if (counter > 0)
            logger.checkPass("javaSample4 has been called " + counter + " times");
        else
            logger.checkFail("javaSample4 has not been called");

        // We also validate the output file the normal way
        CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
        // Supply default value
        if (null == fileChecker)
            fileChecker = new XHTFileCheckService();
        if (Logger.PASS_RESULT
            != fileChecker.check(logger,
                                 new File(datalet.outputName), 
                                 new File(datalet.goldName), 
                                 "Extension test of " + datalet.getDescription())
           )
        {
            // Log a custom element with all the file refs first
            // Closely related to viewResults.xsl select='fileref"
            //@todo check that these links are valid when base 
            //  paths are either relative or absolute!
            Hashtable attrs = new Hashtable();
            attrs.put("idref", (new File(datalet.inputName)).getName());
            attrs.put("inputName", datalet.inputName);
            attrs.put("xmlName", datalet.xmlName);
            attrs.put("outputName", datalet.outputName);
            attrs.put("goldName", datalet.goldName);
            logger.logElement(Logger.STATUSMSG, "fileref", attrs, "Extension test file references");
        }
    }


    /**
     * Description of what this extension does.  
     * @return String description of extension
     */
    public static String getDescription()
    {
        return "Simple hashtable lookup and counter";
    }
}
