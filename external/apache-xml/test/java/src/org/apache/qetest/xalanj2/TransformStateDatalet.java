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
 * TransformStateDatalet.java
 *
 */
package org.apache.qetest.xalanj2;

import java.util.Hashtable;
import java.util.Properties;

import org.apache.qetest.Datalet;

/**
 * Datalet for holding ExpectedTransformState objects.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class TransformStateDatalet implements Datalet
{
    //// Items associated with the normal test
    /** URL of the stylesheet; default:.../identity.xsl.  */
    public String inputName = "tests/api/trax/identity.xsl";

    /** URL of the xml document; default:.../identity.xml.  */
    public String xmlName = "tests/api/trax/identity.xml";

    /** URL to put output into; default:TransformStateDatalet.out.  */
    public String outputName = "TransformStateDatalet.out";

    /** URL of the a gold file or data; default:.../identity.out.  */
    public String goldName = "tests/api-gold/trax/identity.out";

    /** 
     * A Hashtable of ExpectedTransformState objects to validate.  
     * Users should put ExpectedTransformState objects in here with 
     * some sort of hashKey that they want to use.
     */
/***********************************************
// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
    public Hashtable expectedTransformStates = new Hashtable();
// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
***********************************************/

    /** 
     * Cheap-o hash of items to validate for column 99.  
     * Temporary use until we solve problem in TransformStateTestlet:
     * "Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc".
     */
    public Hashtable validate99 = new Hashtable();


    /** Description of what this Datalet tests.  */
    protected String description = "TransformStateDatalet: String inputName, String xmlName, String outputName, String goldName, String flavor; plus second Templates object";


    /**
     * No argument constructor is a no-op.  
     */
    public TransformStateDatalet() { /* no-op */ }


    /**
     * Initialize this datalet from a string, perhaps from 
     * a command line.  
     * We will parse the command line with whitespace and fill
     * in our member variables in order:
     * <pre>inputName, xmlName, outputName, goldName, flavor</pre>, 
     * if there are too few tokens, remaining variables will default.
     */
    public TransformStateDatalet(String args)
    {
        load(args);
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
     * //@todo NOT FULLY IMPLEMENTED!.
     * 
     * @param Hashtable to load
     */
    public void load(Hashtable h)
    {
        if (null == h)
            return; //@todo should this have a return val or exception?

        inputName = (String)h.get("inputName");
        xmlName = (String)h.get("xmlName");
        outputName = (String)h.get("outputName");
        goldName = (String)h.get("goldName");
    }


    /**
     * Load fields of this Datalet from a Properties.  
     * Caller must provide data for all of our fields.
     * //@todo NOT FULLY IMPLEMENTED!.
     * 
     * @param Hashtable to load
     */
    public void load(Properties p)
    {
        if (null == p)
            return; //@todo should this have a return val or exception?

        inputName = (String)p.getProperty("inputName");
        xmlName = (String)p.getProperty("xmlName");
        outputName = (String)p.getProperty("outputName");
        goldName = (String)p.getProperty("goldName");
    }
    /**
     * Load fields of this Datalet from a String.  
     * NOT IMPLEMENTED! No easy way to load the Templates from string.  
     * 
     * @param s String to load
     */
    public void load(String s)
    {
        throw new RuntimeException("TransformStateDatalet.load(String) not implemented!");
    }
    /**
     * Load fields of this Datalet from a String[].  
     * NOT IMPLEMENTED! No easy way to load the Templates from string.  
     * 
     * @param s String array to load
     */
    public void load(String[] s)
    {
        throw new RuntimeException("TransformStateDatalet.load(String[]) not implemented!");
    }
}  // end of class TransformStateDatalet

