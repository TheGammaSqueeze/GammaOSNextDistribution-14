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

package org.apache.qetest.xsl;

import java.io.File;

import org.apache.qetest.Datalet;

/**
 * Testlet for conformance testing of xsl stylesheet files using 
 * XalanC commandline instead of a TransformWrapper.
 *
 * Note: this class simply uses CmdlineTestlet's implementation.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class XalanCTestlet extends CmdlineTestlet
{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.XalanCTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new StylesheetDatalet(); }

    /**
     * Accesor method for a brief description of this test.  
     * @return String describing what this XalanCTestlet does.
     */
    public String getDescription()
    {
        return "XalanCTestlet";
    }

    /**
     * Default Actual name of external program to call.  
     * @return Xalan, the Xalan-C command line.
     */
    public String getDefaultProgName()
    {
        return "Xalan";
    }

    /**
     * Worker method to get list of arguments specific to this program.  
     * 
     * <p>Must be overridden for different processors, obviously.  
     * This implementation returns the args for Xalan-C TestXSLT</p>
     * 
     * @param program path\name of program to Runtime.exec()
     * @param defaultArgs any additional arguments to pass
     * @return String array of arguments suitable to pass to 
     * Runtime.exec()
     */
    public String[] getProgramArguments(StylesheetDatalet datalet, String[] defaultArgs)
    {
        final int NUMARGS = 5;
        String[] args = new String[defaultArgs.length + NUMARGS];
        String progName = datalet.options.getProperty(OPT_PROGNAME, getDefaultProgName());
        String progPath = datalet.options.getProperty(OPT_PROGPATH);
        if ((null != progPath) && (progPath.length() > 0))
        {
            args[0] = progPath + File.separator + progName;
        }
        else
        {
            // Pesume the program is on the PATH already...
            args[0] = progName;
        }
    
        // Default args for Xalan-C TestXSLT
        args[1] = "-o";
        args[2] = datalet.outputName;
        args[3] = datalet.xmlName;
        args[4] = datalet.inputName;

        if (defaultArgs.length > 0)
            System.arraycopy(defaultArgs, 0, args, NUMARGS, defaultArgs.length);

        return args;
    }


}  // end of class XalanCTestlet

