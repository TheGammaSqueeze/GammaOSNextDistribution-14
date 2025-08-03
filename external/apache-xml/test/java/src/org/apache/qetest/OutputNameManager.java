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
 * OutputNameManager.java
 *
 */
package org.apache.qetest;

/**
 * Simple utility class to manage tests with multiple output names.
 * <p>Starts with a base name and extension, and returns
 * nextName()s like:<pre>
 * baseName_<i>1</i>.ext
 * baseName_<i>2</i>.ext
 * baseName_<i>3</i>.ext
 * ...<pre>
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class OutputNameManager
{

    // defaults are provided for everything for the terminally lazy

    /** NEEDSDOC Field extension          */
    protected String extension = ".out";

    /** NEEDSDOC Field baseName          */
    protected String baseName = "OutputFile";

    /** NEEDSDOC Field currentName          */
    protected String currentName = "currentUnset";

    /** NEEDSDOC Field previousName          */
    protected String previousName = "previousUnset";

    /** NEEDSDOC Field counter          */
    protected int counter = 0;

    /** NEEDSDOC Field SEPARATOR          */
    public static final String SEPARATOR = "_";

    /**
     * Construct with just a basename.  
     *
     * @param base basename of file; defaults counter, extension
     */
    public OutputNameManager(String base)
    {
        baseName = base;
    }

    /**
     * Construct with a basename and extension.  
     *
     * @param base basename of file; defaults counter
     * @param ext extension to use instead of .out
     */
    public OutputNameManager(String base, String ext)
    {
        baseName = base;
        extension = ext;
    }

    /**
     * Construct with a basename, extension, and set the counter.  
     *
     * @param base basename of file; defaults counter
     * @param ext extension to use instead of .out
     * @param ctr number to start output counting from
     */
    public OutputNameManager(String base, String ext, int ctr)
    {

        baseName = base;
        extension = ext;

        setCounter(ctr);
    }

    /** Reset the counter to zero and update current, previous names. */
    public void reset()
    {

        previousName = currentName;
        currentName = null;
        counter = 0;  // Set to 0 since we always call nextOutName() first
    }

    /**
     * Increment counter and get next name.  
     *
     * @return the next name in the series
     */
    public String nextName()
    {

        setCounter(counter + 1);  // Updates names

        return currentName();
    }

    /**
     * Just get the current name.  
     *
     * @return our current output name
     */
    public String currentName()
    {
        return currentName;
    }

    /**
     * Get the previous name, even past a reset().  
     *
     * @return last name we calculated
     */
    public String previousName()
    {
        return previousName;
    }

    /**
     * Get the current counter number.  
     *
     * @return counter
     */
    public int currentCounter()
    {
        return counter;
    }

    /**
     * Set the current counter number, including names.  
     *
     * @param ctr new counter number to set
     */
    public void setCounter(int ctr)
    {
        counter = ctr;
        previousName = currentName;
        currentName = baseName + SEPARATOR + counter + extension;
    }
}  // end of class OutputNameManager

