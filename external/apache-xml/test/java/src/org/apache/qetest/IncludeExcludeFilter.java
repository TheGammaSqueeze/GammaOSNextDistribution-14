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
import java.io.FilenameFilter;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Generic FilenameFilter supporting includes/excludes.
 *
 * <p>The default behavior is to accept all files, except: 
 * any name on our excludes list is never accepted; any 
 * name on our includes list is accepted as long as any subclass 
 * will accept it as well; and some 
 * default excludes are never accepted (like "CVS").</p>
 *
 * <p>Subclasses can provide custom functionality by overriding 
 * the acceptOverride(dir, name) method; we call that at the 
 * appropriate time in handling the includes/excludes.</p>
 * 
 * @author shane_curcuru@us.ibm.com
 * @version $Id$
 */
public class IncludeExcludeFilter implements FilenameFilter
{

    /** Initialize for defaults (not using inclusion list) no-op. */
    public IncludeExcludeFilter(){}


    /**
     * Initialize with some include(s)/exclude(s).  
     *
     * @param inc semicolon-delimited string of inclusion name(s)
     * @param exc semicolon-delimited string of inclusion name(s)
     * @param only use inclusion list only: if it's not on the list, 
     * then don't return it
     */
    public IncludeExcludeFilter(String inc, String exc)
    {
        setIncludes(inc);
        setExcludes(exc);
    }

    /**
     * If we should only accept files on the includes list, 
     * or if they should be additional to any acceptOverride().  
     *
     * <p>If true, we first exclude any set excludes, then 
     * we simply return names that match includes.  If false, 
     * We will return names that match inlcudes <b>or</b> names 
     * that match acceptOverride()</p>
     */
    protected boolean useIncludesOnly = true;

    /**
     * @return useIncludesOnly value
     */
    public boolean getUseIncludesOnly()
    {
        return useIncludesOnly;
    }
        
    /**
     * @param useIncludesOnly value
     */
    public void setUseIncludesOnly(boolean use)
    {
        useIncludesOnly = use;
    }

    /**
     * Hash of base names to include.  
     *
     * <p>Keys are names, values in hash are ignored. Note that
     * names are case-sensitive, and are compared to just the name 
     * of the file, ignoring parent directories.</p>
     */
    protected Hashtable includes = null;

    /**
     * Hash of base names to exclude.  
     *
     * <p>Keys are names, values in hash are ignored. Note that
     * names are case-sensitive, and are compared to just the name 
     * of the file, ignoring parent directories.</p>
     */
    protected Hashtable excludes = null;

    /** Exclude CVS repository dirs always. */
    public static final String DEFAULT_EXCLUDES_CVS = "CVS";

    /**
     * Accessor methods for case-sensitive Hash of name(s) to include.  
     *
     * @return clone of our hash of inclusion name(s); null if not set
     */
    public Hashtable getIncludes()
    {
        if (null != includes)
        {
            return (Hashtable) includes.clone();
        }
        else
        {
            return null;
        }
    }

    /**
     * Accessor method to set a list of name(s) to include.  
     *
     * @param inc semicolon-delimited string of inclusion names(s); 
     * if null or blank, unsets any of our includes
     */
    public void setIncludes(String inc)
    {
        if ((null != inc) && ("" != inc))
        {
            includes = new Hashtable();

            StringTokenizer st = new StringTokenizer(inc, ";");
            while (st.hasMoreTokens())
            {
                // Value in hash is ignored
                includes.put(st.nextToken(), Boolean.TRUE);
            }
        }
        else
        {
            includes = null;
        }
    }

    /**
     * Accessor methods for case-sensitive Hash of name(s) to exclude.  
     *
     * @return clone of our hash of exclusion name(s); null if not set
     */
    public Hashtable getExcludes()
    {
        if (null != excludes)
        {
            return (Hashtable) excludes.clone();
        }
        else
        {
            return null;
        }
    }

    /**
     * Accessor method to set a list of name(s) to exclude.  
     *
     * @param inc semicolon-delimited string of exclusion name(s); 
     * if null or blank, unsets any of our excludes
     */
    public void setExcludes(String exc)
    {
        if ((null != exc) && ("" != exc))
        {
            excludes = new Hashtable();

            StringTokenizer st = new StringTokenizer(exc, ";");
            while (st.hasMoreTokens())
            {
                // Value in hash is ignored
                excludes.put(st.nextToken(), Boolean.TRUE);
            }
        }
        else
        {
            excludes = null;
        }
    }

    /**
     * Tests if a specified file is on our inclusion list.  
     * 
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if the name is on our include list; 
     * <code>false</code> otherwise.
     */
    public boolean isInclude(File dir, String name)
    {
        // If we have an inclusion list, check there
        if ((includes != null) && includes.containsKey(name))
            return true;
        // Otherwise, our default behavior is to ignore it
        else
            return false;
    }

    /**
     * Tests if a specified file is on our exclusion list.  
     * 
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if the name is on our exclude list; 
     * <code>false</code> otherwise.
     */
    public boolean isExclude(File dir, String name)
    {
        // Always exclude defaults
        if (DEFAULT_EXCLUDES_CVS.equals(name))
            return true;

        // If we have an exclusion list, check there
        if ((excludes != null) && excludes.containsKey(name))
            return true;
        // Otherwise, our default behavior is to ignore it
        else
            return false;
    }

    /**
     * Overridden method: this default implementation simply 
     * returns true.  
     * 
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> always; should be overridden.
     */
    public boolean acceptOverride(File dir, String name)
    {
        return true;
    }

    /**
     * Tests if a specified file should be included in a file list.  
     * 
     * Uses our includes and excludes lists by calling out to 
     * worker methods.  Subclasses can merely override acceptOverride() 
     * to get the proper behavior.
     *
     * We never return names on our exclusion list; we then always 
     * return names on our inclusion list; then we simply return
     * the value of acceptOverride(...).
     *
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if the name should be included in the file list; 
     * <code>false</code> otherwise.
     * @since JDK1.0
     */
    public boolean accept(File dir, String name)
    {
        // Never return excluded files
        if (isExclude(dir, name))
            return false;

        boolean included = isInclude(dir, name);

        // If we should be exclusive, then only return included 
        //  files immaterial of acceptOverride()
        if (getUseIncludesOnly())
        {
            return included;
        }
        // Otherwise, return files either in the inclusion list 
        //  or that are selected by acceptOverride()
        else
        {
            return (included || acceptOverride(dir, name));
        }
    }
}
