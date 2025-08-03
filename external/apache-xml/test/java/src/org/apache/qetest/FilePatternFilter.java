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

/**
 * FilenameFilter supporting includes/excludes returning files
 * that match a specified pattern.
 *
 * <p>Returns files of *ext that match our includes/excludes.</p>
 *
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class FilePatternFilter extends IncludeExcludeFilter
{

    /**
     * Initialize with some include(s)/exclude(s) and a pattern.  
     *
     * @param inc semicolon-delimited string of inclusion name(s)
     * @param exc semicolon-delimited string of exclusion name(s)
     * @param glob simple pattern to look for
     */
    public FilePatternFilter(String inc, String exc, String glob)
    {
        setIncludes(inc);
        setExcludes(exc);
        setPattern(glob);
        setUseIncludesOnly(false);
    }

    /**
     * Simple globbing char for pattern is "*".  
     */
    public static final String GLOB_CHAR = "*";

    /**
     * Simple starting pattern to include.  
     */
    private String startPattern = "";

    /**
     * Simple ending pattern to include.  
     */
    private String endPattern = "";

    /**
     * Accessor method to set file pattern we're looking for.  
     *
     * @param glob file pattern we're looking for; if null, we 
     * set it to a blank string
     */
    public void setPattern(String glob)
    {
        if (null != glob)
        {
            int idx = glob.indexOf(GLOB_CHAR);
            if (idx > -1)
            {
                startPattern = glob.substring(0, idx);
                endPattern = glob.substring(idx + 1, glob.length());
            }
            else
            {
                // Derivitave case: no glob char, so exact match 
                //  only - you might as well use includes
                startPattern = glob;
                endPattern = null; //special case
            }
        }
        else
        {
            // A blank string matches either
            startPattern = "";
            endPattern = "";
        }
    }

    /**
     * Return files that match our pattern.
     *
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if it's a file that has our pattern.
    */
    public boolean acceptOverride(File dir, String name)
    {
        // Only return files...
        if (!(new File(dir, name)).isFile())
            return false;

        if (null != endPattern)
        {
            return name.startsWith(startPattern)
                   && name.endsWith(endPattern);
        }
        else
        {
            // Derivitave case: no glob char, so exact match 
            //  only - you might as well use includes
            return name.equals(startPattern);
        }
    }
}
