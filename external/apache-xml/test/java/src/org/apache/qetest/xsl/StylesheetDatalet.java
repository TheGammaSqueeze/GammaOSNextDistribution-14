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
 * StylesheetDatalet.java
 *
 */
package org.apache.qetest.xsl;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.qetest.Datalet;

/**
 * Datalet for conformance testing of xsl stylesheet files.
 * Should serve as a base class for other XSLT related Datalets.
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class StylesheetDatalet implements Datalet
{
    /** URL of the stylesheet; default:.../identity.xsl.  */
    public String inputName = "tests/api/trax/identity.xsl";

    /** URL of the stylesheet params; default:.../identity.xsl.  */
    public String paramName = "tests/api/trax/identity.param";

    /** URL of the xml document; default:.../identity.xml.  */
    public String xmlName = "tests/api/trax/identity.xml";

    /** URL to put output into; default:StylesheetDatalet.out.  */
    public String outputName = "StylesheetDatalet.out";

    /** URL of the a gold file or data; default:.../identity.out.  */
    public String goldName = "tests/api-gold/trax/identity.out";

    /** Flavor of a ProcessorWrapper to use; default:trax.  */
    public String flavor = "trax"; //@todo should be ProcessorWrapper.DEFAULT_FLAVOR

    /** 
     * Generic placeholder for any additional options.  
     * I'm still undecided if I like this idea or not.  
     * This allows StylesheetDatalets to support additional kinds 
     * of tests, like performance tests, without having to change 
     * this data model.  These options can serve as a catch-all 
     * for any new properties or options or what-not that new 
     * tests need, without having to change how the most basic 
     * member variables here work.
     * Note that while this needs to be a Properties object to 
     * take advantage of the parent/default behavior in 
     * getProperty(), this doesn't necessarily mean they can only 
     * store Strings.
     */
    public Properties options = new Properties();

    public Map params = new HashMap();

    /** Description of what this Datalet tests.  */
    protected String description = "StylesheetDatalet: String inputName, String xmlName, String outputName, String goldName, String flavor, String paramName";


    /**
     * No argument constructor is a no-op.  
     */
    public StylesheetDatalet() { /* no-op */ }


    /**
     * Initialize this datalet from a string, perhaps from 
     * a command line.  
     * We will parse the command line with whitespace and fill
     * in our member variables in order:
     * <pre>inputName, xmlName, outputName, goldName, flavor</pre>, 
     * if there are too few tokens, remaining variables will default.
     */
    public StylesheetDatalet(String args)
    {
        load(args);
        setDescription("inputName=" + inputName 
                       + " xmlName=" + xmlName 
                       + " outputName=" + outputName 
                       + " goldName=" + goldName 
                       + " flavor=" + flavor
                       + " paramName=" + paramName);
    }


    /**
     * Initialize this datalet from a string, plus a Properties 
     * block to use as our default options.  
     * We will parse the command line with whitespace and fill
     * in our member variables in order:
     * <pre>inputName, xmlName, outputName, goldName, flavor</pre>, 
     * if there are too few tokens, remaining variables will default.
     */
    public StylesheetDatalet(String args, Properties defaults)
    {
        // First set our defaults and our flavor member
        options = new Properties(defaults);
        flavor = options.getProperty("flavor", flavor);
        // Then set all other items from the string, potentially 
        //  overriding the flavor set above
        load(args);
        setDescription("inputName=" + inputName 
                       + " xmlName=" + xmlName 
                       + " outputName=" + outputName 
                       + " goldName=" + goldName 
                       + " flavor=" + flavor
                       + " paramName=" + paramName);
    }


    /**
     * Accesor method for a brief description of this Datalet.  
     *
     * @return String describing the specific set of data 
     * this Datalet contains (can often be used as the description
     * of any check() calls made from the Testlet).
     */
    public String getDescription()
    {
        return description;
    }


    /**
     * Accesor method for a brief description of this Datalet.  
     *
     * @param s description to use for this Datalet.
     */
    public void setDescription(String s)
    {
        description = s;
    }


    /**
     * Load fields of this Datalet from a Hashtable.  
     * Caller must provide data for all of our fields.
     * //@todo design decision: only have load(Hashtable)
     * or load(Properties), not both.
     * 
     * @param Hashtable to load
     */
    public void load(Hashtable h)
    {
        if (null == h)
            return; //@todo should this have a return val or exception?

        inputName = (String)h.get("inputName");
        paramName = (String)h.get("paramName");
        xmlName = (String)h.get("xmlName");
        outputName = (String)h.get("outputName");
        goldName = (String)h.get("goldName");
        flavor = (String)h.get("flavor");
    }


    /**
     * Load fields of this Datalet from a Properties.  
     * Caller must provide data for all of our fields.
     * //@todo design decision: only have load(Hashtable)
     * or load(Properties), not both.
     * 
     * @param Hashtable to load
     */
    public void load(Properties p)
    {
        if (null == p)
            return; //@todo should this have a return val or exception?

        inputName = (String)p.getProperty("inputName");
        paramName = (String)p.getProperty("paramName");
        xmlName = (String)p.getProperty("xmlName");
        outputName = (String)p.getProperty("outputName");
        goldName = (String)p.getProperty("goldName");
        flavor = (String)p.getProperty("flavor");
        // Also set our internal options to default to this Properties
        options = new Properties(p);
    }

    /**
     * Load fields of this Datalet from an array.  
     * Order: inputName, xmlName, outputName, goldName, flavor
     * If too few args, then fields at end of list are left at default value.
     * @param args array of Strings
     */
    public void load(String[] args)
    {
        if (null == args)
            return; //@todo should this have a return val or exception?

        try
        {
            inputName = args[0];
            xmlName = args[1];
            outputName = args[2];
            goldName = args[3];
            flavor = args[4];
            if (args.length > 4) {
                paramName = args[5];
            }
        }
        catch (ArrayIndexOutOfBoundsException  aioobe)
        {
            // No-op, leave remaining items as default
        }
    }


    /**
     * Load fields of this Datalet from a whitespace-delimited String.  
     * Order: inputName, xmlName, outputName, goldName, flavor
     * If too few args, then fields at end of list are left at default value.
     * EXPERIMENTAL: takes any extra args as name value pairs and 
     * attempts to add them to our options
     * @param args array of Strings
     */
    public void load(String str)
    {
        if (null == str)
            return; //@todo should this have a return val or exception?

        StringTokenizer st = new StringTokenizer(str);

        // Fill in as many items as we can; leave as default otherwise
        // Note that order is important!
        if (st.hasMoreTokens())
        {
            inputName = st.nextToken();
            if (st.hasMoreTokens())
            {
                xmlName = st.nextToken();
                if (st.hasMoreTokens())
                {
                    outputName = st.nextToken();
                    if (st.hasMoreTokens())
                    {
                        goldName = st.nextToken();
            
                        if (st.hasMoreTokens())
                        {
                            flavor = st.nextToken();

                            if (st.hasMoreTokens())
                            {
                                paramName = st.nextToken();
                            }
                        
                        }
                    }
                }
            }
        }
        // EXPERIMENTAL add extra name value pairs to our options
        while (st.hasMoreTokens())
        {
            String name = st.nextToken();
            if (st.hasMoreTokens())
            {
                options.put(name, st.nextToken());
            }
            else
            {
                // Just put it as 'true' for boolean options
                options.put(name, "true");
            }
        }

    }
}  // end of class StylesheetDatalet

