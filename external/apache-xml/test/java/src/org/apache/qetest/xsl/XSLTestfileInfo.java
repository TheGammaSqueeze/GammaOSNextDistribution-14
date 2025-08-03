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
 * XSLTestfileInfo.java
 *
 */
package org.apache.qetest.xsl;

import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.qetest.TestfileInfo;

/**
 * Simple data-holding class specifying info about one 'testfile'.
 * <p>Convenience class for XSL Processor testing, includes
 * additional fields and overrides some methods to change order
 * of initializer strings.</p>
 * <ul>Fields read in new order:
 * <li>inputName - xsl stylesheet</li>
 * <li>xmlName - xml data file <b>new</b></li>
 * <li>outputName - for results of transform</li>
 * <li>goldName</li>
 * <li>description</li>
 * <li>author</li>
 * <li>options</li>
 * </ul>
 * @author Shane Curcuru
 * @version $Id$
 */
public class XSLTestfileInfo extends TestfileInfo
{

    /** Name of the input XML data file. */
    public String xmlName = null;

    /** NEEDSDOC Field XMLNAME          */
    public static final String XMLNAME = "xmlName";

    /** No-arg constructor leaves everything null. */
    public XSLTestfileInfo(){}

    /**
     * Initialize members from name=value pairs in Properties block.
     * Default value for each field is null.
     * @param Properties block to initialize from
     *
     * NEEDSDOC @param p
     */
    public XSLTestfileInfo(Properties p)
    {
        load(p);
    }

    /**
     * Pass in a StringTokenizer-default-delimited string to initialize members.
     * <p>Members are read in order: inputName <i>xmlName</i> outputName goldName
     * author description options...
     * default value for each field is null</p>
     * @param String to initialize from
     *
     * NEEDSDOC @param inputStr
     */
    public XSLTestfileInfo(String inputStr)
    {

        StringTokenizer st = new StringTokenizer(inputStr);

        load(st, null);
    }

    /**
     * Pass in a StringTokenizer-default-delimited string to initialize members.
     * <p>Members are read in order: inputName <i>xmlName</i> outputName goldName
     * author description options...
     * default value for each field is user-specified String</p>
     * @param String to initialize from
     * @param String to use as default for any un-specified value
     *
     * NEEDSDOC @param inputStr
     * NEEDSDOC @param defaultVal
     */
    public XSLTestfileInfo(String inputStr, String defaultVal)
    {

        StringTokenizer st = new StringTokenizer(inputStr);

        load(st, defaultVal);
    }

    /**
     * Pass in a specified-delimited string to initialize members.
     * <p>Members are read in order: inputName <i>xmlName</i> outputName goldName
     * author description options...
     * default value for each field is user-specified String</p>
     * @param String to initialize from
     * @param String to use as default for any un-specified value
     * @param String to use as separator for StringTokenizer
     *
     * NEEDSDOC @param inputStr
     * NEEDSDOC @param defaultVal
     * NEEDSDOC @param separator
     */
    public XSLTestfileInfo(String inputStr, String defaultVal,
                           String separator)
    {

        StringTokenizer st = new StringTokenizer(inputStr, separator);

        load(st, defaultVal);
    }

    /**
     * Worker method to initialize members.
     *
     * NEEDSDOC @param st
     * NEEDSDOC @param defaultVal
     */
    public void load(StringTokenizer st, String defaultVal)
    {

        // Fill in as many items as are available; default the value otherwise
        // Note that order is important!
        if (st.hasMoreTokens())
            inputName = st.nextToken();
        else
            inputName = defaultVal;

        // Override from base class: put the xmlName next
        if (st.hasMoreTokens())
            xmlName = st.nextToken();
        else
            xmlName = defaultVal;

        if (st.hasMoreTokens())
            outputName = st.nextToken();
        else
            outputName = defaultVal;

        if (st.hasMoreTokens())
            goldName = st.nextToken();
        else
            goldName = defaultVal;

        if (st.hasMoreTokens())
            author = st.nextToken();
        else
            author = defaultVal;

        if (st.hasMoreTokens())
            description = st.nextToken();
        else
            description = defaultVal;

        if (st.hasMoreTokens())
        {
            options = st.nextToken();

            // For now, simply glom all additional tokens into the options, until the end of string
            // Leave separated with a single space char for readability
            while (st.hasMoreTokens())
            {
                options += " " + st.nextToken();
            }
        }
        else
            options = defaultVal;
    }

    /**
     * Initialize members from name=value pairs in Properties block.
     * Default value for each field is null.
     * @param Properties block to initialize from
     *
     * NEEDSDOC @param p
     */
    public void load(Properties p)
    {

        inputName = p.getProperty(INPUTNAME);
        xmlName = p.getProperty(XMLNAME);  // Override from base class
        outputName = p.getProperty(OUTPUTNAME);
        goldName = p.getProperty(GOLDNAME);
        author = p.getProperty(AUTHOR);
        description = p.getProperty(DESCRIPTION);
        options = p.getProperty(OPTIONS);
    }

    /**
     * Cheap-o debugging: return tab-delimited String of all our values.
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String dump()
    {

        return (inputName + '\t' + xmlName  // Override from base class
                + '\t' + outputName + '\t' + goldName + '\t' + author + '\t'
                + description + '\t' + options);
    }
}
