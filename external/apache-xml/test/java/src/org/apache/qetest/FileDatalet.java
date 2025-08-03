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

package org.apache.qetest;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Datalet representing set of paths to files: input, output, gold.
 * 
 * <p>This is a fairly simplistic Datalet implementation that's 
 * useful for the many programs where the test requires reading 
 * an input file, performing an operation with the program, and 
 * then verifying an output file.</p>
 *
 * <p>We normally operate on local path/filenames, since the Java 
 * SDK's implementation of URLs and File objects is so poor at 
 * handling proper URI/URL's according to the RFCs.  A potential 
 * future improvement is to add convenience accessor methods, like 
 * getInputName() (just the bare filename) etc. but I'm not quite 
 * convinced we need them yet.</p>
 * 
 * @see FileTestlet
 * @see FileTestletDriver
 * @see FileDataletManager
 * @author Shane_Curcuru@us.ibm.com
 * @version $Id$
 */
public class FileDatalet implements Datalet
{
    /** Path of the location to get input resources from.  */
    protected String input = "tests/defaultInput";

    /** Accessor method for input; never null.  */
    public String getInput() { return input; }

    /** Path to put actual output resources into.  */
    protected String output = "output/defaultOutput";

    /** Accessor method for output; never null.  */
    public String getOutput() { return output; }

    /** Path of the location to get gold or reference resources from.  */
    protected String gold = "gold/defaultGold";

    /** Accessor method for gold; never null.  */
    public String getGold() { return gold; }


    /** 
     * Worker method to validate the files/dirs we represent.  
     * 
     * <p>By default, ensures that the input already exists in some 
     * format; and attempts to create the output.  If asked to be 
     * strict, then we will fail if the output cannot be created, 
     * and we additionally will attempt to create the gold and 
     * will fail if it can't be created.</p>
     *
     * <p>Note that we only attempt to create the gold if asked to 
     * be strict, since users may simply want to run a 'crash test' 
     * and get all {@link org.apache.qetest.Logger#AMBG_RESULT AMBG}
     * results when prototyping new tests.</p>
     *
     * @param strict if true, requires that output and gold must 
     * be created; otherwise they're optional
     * @return false if input doesn't already exist; true otherwise
     */
    public boolean validate(boolean strict)
    {
        File f = new File(getInput());
        if (!f.exists())
            return false;

        f = new File(getOutput());
        if (!f.exists())
        {
            if (!f.mkdirs())
            {
                // Only fail if asked to be strict
                if (strict)
                    return false;
            }
        }        

        f = new File(getGold());
        if (!f.exists())
        {
            // For gold, only attempt to mkdirs if asked...
            if (strict)
            {
                // ...Only fail here if we can't
                if (!f.mkdirs())
                    return false;
            }
        }        
        // If we get here, we're happy either way
        return true;
    }


    /** 
     * Generic placeholder for any additional options.  
     * 
     * <p>This allows FileDatalets to support additional kinds 
     * of tests, like performance tests, without having to change 
     * this data model.  These options can serve as a catch-all 
     * for any new properties or options or what-not that new 
     * tests need, without having to change how the most basic 
     * member variables here work.</p>
     * <p>Note that while this needs to be a Properties object to 
     * take advantage of the parent/default behavior in 
     * getProperty(), this doesn't necessarily mean they can only 
     * store Strings; however only String-valued items can make 
     * use of the default properties mechanisim.</p>
     *
     * <p>Default is a null object; note that getOptions() will 
     * never return null, but will create a blank Properties 
     * block if needed.</p>
     */
    protected Properties options = null;

    /** 
     * Accessor method for optional properties.  
     * 
     * Should never return null; if it has no options then 
     * it will create a blank Properties block to return. 
     */
    public Properties getOptions() 
    { 
        if (null == options)
            options = new Properties();

        return options;
    }

    /** 
     * Accessor method for optional properties; settable.  
     * <p>Note this method creates a new Properties that simply 
     * defaults to the passed in properties instead of cloning it.</p>
     * <p>This used to simply assign this variable to the passed 
     * in variable, but that leads to problems when errant 
     * Testlets mutate their Datalet's Options, which can result 
     * in the master test's testProps getting changed.</p>
     *
     * @param p Properties to set as our defaults; if null, we 
     * do not change anything
     */
    public void setOptions(Properties p) 
    { 
        if (null != p)
            options = new Properties(p);
    }


    /** 
     * Accessor method for optional properties that are ints.  
     * Convenience method to take care of parse/cast to int. 
     *
     * @param name of property to try to get
     * @param defValue value if property not available
     * @return integer value of property, or the default
     */
    public int getIntOption(String name, int defValue)
    {
        if (null == options)
            return defValue;
            
        try
        {
            return Integer.parseInt(options.getProperty(name));
        }
        catch (Exception e) 
        {
            return defValue;
        }
    }


    /** Description of what this Datalet tests.  */
    protected String description = "FileDatalet: default description";


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
     * Worker method to auto-set the description of this Datalet.  
     * Conglomeration of input, output, gold values.
     */
    protected void setDescription()
    {
        setDescription("input=" + input 
                       + " output=" + output 
                       + " gold=" + gold); 
    }


    /**
     * No argument constructor is a no-op; not very useful.  
     */
    public FileDatalet() { /* no-op */ }


    /**
     * Initialize this datalet from another FileDatalet which 
     * serves as a 'base' location, and a filename.  
     * 
     * <p>We set each of our input, output, gold to be concatenations 
     * of the base + File.separator + fileName, and also copy 
     * over the options from the base.  Note we always attempt to 
     * deal with local path/filename conventions.</p>
     *
     * @param base FileDatalet object to serve as base directories
     * @param fileName to concatenate for each of input/output/gold
     */
    public FileDatalet(FileDatalet base, String fileName)
    {
        if ((null == base) || (null == fileName))
            throw new IllegalArgumentException("FileDatalet(base, fileName) called with null base!");

        StringBuffer buf = new StringBuffer(File.separator + fileName);
        input = base.getInput() + buf;
        output = base.getOutput() + buf;
        gold = base.getGold() + buf;
        setOptions(base.getOptions());
        
        setDescription(); 
    }


    /**
     * Initialize this datalet from a list of paths.
     *
     * <p>Our options are not initialized, and left as null.<p>
     * 
     * @param i path for input
     * @param o path for output
     * @param g path for gold
     */
    public FileDatalet(String i, String o, String g)
    {
        input = i;
        output = o;
        gold = g;
        
        setDescription(); 
    }


    /**
     * Initialize this datalet from a string and defaults.
     *
     * <p>Our options are created as a new Properties block that 
     * defaults to the existing one passed in, then 
     * we parse the string for input, output, gold, and 
     * optionally add additional args to the options.</p>
     * 
     * @param args command line to initialize from
     * @param defaults for our options
     */
    public FileDatalet(String args, Properties defaults)
    {
        options = new Properties(defaults);

        StringTokenizer st = new StringTokenizer(args);

        // Fill in as many items as we can; leave as default otherwise
        // Note that order is important!
        if (st.hasMoreTokens())
        {
            input = st.nextToken();
            if (st.hasMoreTokens())
            {
                output = st.nextToken();
                if (st.hasMoreTokens())
                {
                    gold = st.nextToken();
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


    /**
     * Load fields of this Datalet from a Hashtable.  
     * Caller must provide data for all of our fields.
     * Additionally, we set all values from the hash into 
     * our options block.
     * 
     * @param Hashtable to load
     */
    public void load(Hashtable h)
    {
        if (null == h)
            return; //@todo should this have a return val or exception?

        input = (String)h.get("input");
        output = (String)h.get("output");
        gold = (String)h.get("gold");

        // Also copy over all items in hash to options
        options = new Properties();
        for (Enumeration keys = h.keys(); 
             keys.hasMoreElements(); 
             /* no increment portion */)
        {
            String key = (String)keys.nextElement();
            options.put(key, h.get(key));
        }
    }


    /**
     * Load fields of this Datalet from an array.  
     * Order: input, output, gold.  Options are left null.
     * If too few args, then fields at end of list are left at default value.
     * @param args array of Strings
     */
    public void load(String[] args)
    {
        if (null == args)
            return; //@todo should this have a return val or exception?

        try
        {
            input = args[0];
            output = args[1];
            gold = args[2];
        }
        catch (ArrayIndexOutOfBoundsException  aioobe)
        {
            // No-op, leave remaining items as default
        }
    }

}  // end of class FileDatalet

