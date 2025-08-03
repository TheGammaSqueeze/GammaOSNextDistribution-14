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
public class javaRedir2 extends TestableExtension
{
    /** Note: no actual extension methods here; this class just does validation.  */

    /** Copied from javaRedir2.xml[/doc/list/item].  */
    public static final String APPEND_WRITE1_NAME = "javaRedir2a-write1.out";

    /** Copied from javaRedir2.xml[/doc/list/item].  */
    public static final String APPEND_WRITE2_NAME = "javaRedir2a-write2.out";

    /** 
     * Perform and log any pre-transformation info.  
     * @return true if OK; false if any fatal error occoured
     * @param datalet Datalet of current stylesheet test
     */
    public static boolean preCheck(Logger logger, StylesheetDatalet datalet)
    {
        logger.logMsg(Logger.TRACEMSG, "javaRedir2.preCheck");
        return true;        
    }
    

    /** 
     * Perform and log any post-transformation info.  
     * 
     * The extension should validate that it's extension was 
     * properly called; we also validate output file(s).
     * 
     * @param logger Logger to dump any info to
     * @param datalet Datalet of current stylesheet test
     */
    public static void postCheck(Logger logger, StylesheetDatalet datalet)
    {
        logger.logMsg(Logger.TRACEMSG, "javaRedir2.postCheck");

        // First, validate the normal output file the normal way
        CheckService fileChecker = (CheckService)datalet.options.get("fileCheckerImpl");
        // Supply default value
        if (null == fileChecker)
            fileChecker = new XHTFileCheckService();
        fileChecker.check(logger,
                          new File(datalet.outputName), 
                          new File(datalet.goldName), 
                          "Extension test of " + datalet.getDescription());

        // Now, also validate the redirected output from multiple files!
        // REPEAT for inner redirected file
        String goldRedir = (new File(datalet.goldName)).getParent() 
                           + File.separator + APPEND_WRITE1_NAME;
        String outRedir = (new File(datalet.outputName)).getParent() 
                          + File.separator + APPEND_WRITE1_NAME;
        fileChecker.check(logger, 
                          new File(outRedir), 
                          new File(goldRedir), 
                          "Redir-Append-Inner-Extension test of " + datalet.getDescription());

        // REPEAT for outer redirected file
        goldRedir = (new File(datalet.goldName)).getParent() 
                           + File.separator + APPEND_WRITE2_NAME;
        outRedir = (new File(datalet.outputName)).getParent() 
                          + File.separator + APPEND_WRITE2_NAME;
        fileChecker.check(logger, 
                          new File(outRedir), 
                          new File(goldRedir), 
                          "Redir-AppendOuter-Extension test of " + datalet.getDescription());
    }


    /**
     * Description of what this extension does.  
     * @return String description of extension
     */
    public static String getDescription()
    {
        return "No extension methods - just validation";
    }
}

