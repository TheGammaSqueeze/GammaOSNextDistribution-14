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
 * ResultsCompareTestlet.java
 *
 */
package org.apache.qetest.xsl;

import java.io.File;
import java.util.Hashtable;

import org.apache.qetest.CheckService;
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.xslwrapper.TransformWrapper;

/**
 * Testlet for just comparing results of test runs.
 *
 * This class provides a simple way to compare two result 
 * trees, without actually invoking the processor.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class ResultsCompareTestlet extends StylesheetTestlet
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.ResultsCompareTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String describing what this ResultsCompareTestlet does.
     */
    public String getDescription()
    {
        return "ResultsCompareTestlet";
    }

    /** 
     * Worker method merely compares results.  
     *
     * Logs out applicable info; ignores transformation, 
     * compares results in goldDir with outputDir.
     *
     * @param datalet to test with
     * @param transformWrapper to have perform the transform
     * @throws allows any underlying exception to be thrown
     */
    protected void testDatalet(StylesheetDatalet datalet, TransformWrapper transformWrapper)
            throws Exception
    {
        //@todo Should we log a custom logElement here instead?
        logger.logMsg(Logger.TRACEMSG, "ResultsCompare of: outputName=" + datalet.outputName
                      + " goldName=" + datalet.goldName);

        // If we get here, attempt to validate the contents of 
        //  the last outputFile created
        CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
        // Supply default value
        if (null == fileChecker)
            fileChecker = new XHTFileCheckService();
        // Apply any testing options to the fileChecker
        // Note: for the overall conformance test case, this is 
        //  a bit inefficient, but we don't necessarily know if 
        //  the checkService has already been setup or not    
        fileChecker.applyAttributes(datalet.options);    
        if (Logger.PASS_RESULT
            != fileChecker.check(logger,
                                 new File(datalet.outputName), 
                                 new File(datalet.goldName), 
                                 getDescription() + " " + datalet.getDescription())
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
            logger.logElement(Logger.STATUSMSG, "fileref", attrs, "Conformance test file references");
            // No longer need to log failure reason, this kind 
            //  of functionality should be kept in checkServices
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
        logger.checkFail(getDescription() + " " + datalet.getDescription() 
                         + " threw: " + t.toString());
    }
}  // end of class ResultsCompareTestlet

