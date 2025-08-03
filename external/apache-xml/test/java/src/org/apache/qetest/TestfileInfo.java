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
 * TestfileInfo.java
 *
 */
package org.apache.qetest;

import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Simple data-holding class specifying info about one 'testfile'.
 * <p>This is purely a convenience class for tests that rely on
 * external datafiles. Tests will very commonly need input data,
 * will output operations to files, and compare those outputs to
 * known good or 'gold' files. A generic description and author
 * field are also provided.  A freeform String field of options
 * is included for easy extensibility.</p>
 * <ul>
 * <li>inputName</li>
 * <li>outputName</li>
 * <li>goldName</li>
 * <li>description</li>
 * <li>author</li>
 * <li>options</li>
 * </ul>
 * <p>Note that String representations are used, since this allows
 * for testing of how applications translate the names to File
 * objects, or whatever they use.</p>
 * @author Shane Curcuru
 * @version $Id$
 * @todo Leave everything public for now for simplicity
 * Later, if this is a useful construct, we should improve on it's services:
 * allow it to verify it's own files, change absolute refs fo relative, etc.
 */
public class TestfileInfo
{

    /** Name of the input data file. */
    public String inputName = null;

    /** NEEDSDOC Field INPUTNAME          */
    public static final String INPUTNAME = "inputName";

    /** Name of the output file to be created. */
    public String outputName = null;

    /** NEEDSDOC Field OUTPUTNAME          */
    public static final String OUTPUTNAME = "outputName";

    /** Name of the gold file to compare output to. */
    public String goldName = null;

    /** NEEDSDOC Field GOLDNAME          */
    public static final String GOLDNAME = "goldName";

    /** Author or copyright info for the testfile. */
    public String author = null;

    /** NEEDSDOC Field AUTHOR          */
    public static final String AUTHOR = "author";

    /** Basic description of the testfile. */
    public String description = null;

    /** NEEDSDOC Field DESCRIPTION          */
    public static final String DESCRIPTION = "description";

    /** Any additional options (for future expansion). */
    public String options = null;

    /** NEEDSDOC Field OPTIONS          */
    public static final String OPTIONS = "options";

    /** No-arg constructor leaves everything null. */
    public TestfileInfo(){}

    /**
     * Initialize members from name=value pairs in Properties block.
     * Default value for each field is null.
     * @param Properties block to initialize from
     *
     * NEEDSDOC @param p
     */
    public TestfileInfo(Properties p)
    {
        load(p);
    }

    /**
     * Pass in a StringTokenizer-default-delimited string to initialize members.
     * <p>Members are read in order: inputName outputName goldName
     * author description options...
     * default value for each field is null</p>
     * @param String to initialize from
     *
     * NEEDSDOC @param inputStr
     */
    public TestfileInfo(String inputStr)
    {

        StringTokenizer st = new StringTokenizer(inputStr);

        load(st, null);
    }

    /**
     * Pass in a StringTokenizer-default-delimited string to initialize members.
     * <p>Members are read in order: inputName outputName goldName
     * author description options...
     * default value for each field is user-specified String</p>
     * @param String to initialize from
     * @param String to use as default for any un-specified value
     *
     * NEEDSDOC @param inputStr
     * NEEDSDOC @param defaultVal
     */
    public TestfileInfo(String inputStr, String defaultVal)
    {

        StringTokenizer st = new StringTokenizer(inputStr);

        load(st, defaultVal);
    }

    /**
     * Pass in a specified-delimited string to initialize members.
     * <p>Members are read in order: inputName outputName goldName
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
    public TestfileInfo(String inputStr, String defaultVal, String separator)
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
        return (inputName + '\t' + outputName + '\t' + goldName + '\t'
                + author + '\t' + description + '\t' + options);
    }
}
