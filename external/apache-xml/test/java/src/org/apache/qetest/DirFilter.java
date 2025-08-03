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
 * FilenameFilter supporting includes/excludes returning directories.
 *
 * <p>Returns directories that match our includes/excludes.</p>
 *
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class DirFilter extends IncludeExcludeFilter
{

    /** Initialize for defaults (not using inclusion list).  */
    public DirFilter()
    {
        // Default is to not use includes; just get all dirs
        setUseIncludesOnly(false);
    }

    /**
     * Initialize with some include(s)/exclude(s).  
     *
     * @param inc semicolon-delimited string of inclusion name(s)
     * @param exc semicolon-delimited string of exclusion name(s)
     */
    public DirFilter(String inc, String exc)
    {
        setIncludes(inc);
        setExcludes(exc);
        // Only use includes only if they're set
        //refactor: logic might better be put in IncludeExcludeFilter
        if ((null != inc) && (inc.length() > 0))
            setUseIncludesOnly(true);
        else
            setUseIncludesOnly(false);
    }

    /**
     * Return only directories.
     *
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if the name should be included in the file list; 
     * <code>false</code> otherwise.
     * @since JDK1.0
     */
    public boolean acceptOverride(File dir, String name)
    {
        // Only return directories
        if ((new File(dir, name)).isDirectory())
            return true;
        else
            return false;
    }
}
