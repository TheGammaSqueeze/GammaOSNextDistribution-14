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
 * TestableExtension.java
 *
 */
package org.apache.qetest.xsl;
import org.apache.qetest.Logger;


/**
 * Simple base class defining test hooks for Java extensions.  
 *
 * Currently provides generic but limited verification for 
 * basic extensions in the context of being called from standard 
 * transformations.  I would have preferred an interface, but then 
 * methods couldn't be static, which makes validation simpler 
 * since the test doesn't have to try to grab the same instance of 
 * an extension that the transformer used.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class TestableExtension
{
    /** 
     * Perform and log any pre-transformation info.  
     *
     * The extension should log out to the logger brief info 
     * about what the extension does.  It may also use items in 
     * the datalet to perform additional validation (like ensuring 
     * that counters are zeroed, etc.).
     *
     * TestableExtensions should validate any output files needed in 
     * their postCheck method; ExtensionTestlets simply rely on this 
     * method to both validate the internal actions of the extension 
     * as well as any output files, as needed.
     *
     * This should only return false if some horrendous error with 
     * the extension appears to have occoured; it will likely prevent 
     * any callers from completing the test.
     * 
     * @return true if OK; false if any fatal error occoured
     * @param logger Logger to dump any info to
     * @param datalet Datalet of current stylesheet test
     */
    public static boolean preCheck(Logger logger, StylesheetDatalet datalet) 
    {
        /* Must be overridden */
        return false;
    }
    

    /** 
     * Perform and log any post-transformation info.  
     * 
     * The extension should validate that it's extension was 
     * properly called.  It should also validate any output files
     * specified in the datalet, etc.  It may also 
     * validate against extension-specific data in the options 
     * (like validating that some extension call was hit a 
     * certain number of times or the like).
     * 
     * @param logger Logger to dump any info to
     * @param datalet Datalet of current stylesheet test
     */
    public static void postCheck(Logger logger, StylesheetDatalet datalet)
    {
        /* Must be overridden */
        return;
    }


    /**
     * Description of what this extension does.  
     * @return String description of extension
     */
    public static String getDescription()
    {
        return "Must be overridden";
    }

}  // end of class TestableExtension

