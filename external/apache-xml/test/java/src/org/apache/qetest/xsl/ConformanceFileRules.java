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
import java.io.FilenameFilter;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Simple file filter; returns *.xsl non-dir files that start with the directory name.
 * Has crude support for an excludes list of filename bases.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class ConformanceFileRules implements FilenameFilter
{

    /** Initialize for defaults (not using inclusion list) no-op. */
    public ConformanceFileRules(){}

    /**
     * Initialize with a case-sensitive Hash of file names to exclude.  
     *
     * NEEDSDOC @param excludesHash
     */
    public ConformanceFileRules(Hashtable excludesHash)
    {
        setExcludes(excludesHash);
    }

    /**
     * Initialize with a case-insensitive semicolon-delimited String of file names to exclude.  
     *
     * NEEDSDOC @param excludesStr
     */
    public ConformanceFileRules(String excludesStr)
    {
        setExcludes(excludesStr);
    }

    /**
     * Hash of file name portions to exclude.
     * <p>Keys are base file names, values in hash are ignored. Note that
     * file names may be case-sensitive.</p>
     * <p>Note that we will exclude any filename in our excludes.</p>
     */
    protected Hashtable excludeFiles = null;

    /**
     * Accessor methods to set a case-sensitive Hash of file names to exclude.  
     *
     * NEEDSDOC @param exFiles
     */
    public void setExcludes(Hashtable exFiles)
    {

        if (exFiles != null)
            excludeFiles = (Hashtable) exFiles.clone();
        else
            excludeFiles = null;
    }

    /**
     * Accessor methods to set a case-sensitive Hash of file names to exclude.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Hashtable getExcludes()
    {

        if (excludeFiles != null)
        {
            Hashtable tempHash = (Hashtable) excludeFiles.clone();

            return tempHash;
        }
        else
        {
            return null;
        }
    }

    /**
     * Accessor method to set a list of case-insensitive String
     * directory name(s) to exclude.
     * Names should be separated by {@link #SEPARATOR semicolon}.
     *
     * NEEDSDOC @param exFiles
     */
    public void setExcludes(String exFiles)
    {
        setExcludes(exFiles, false);
    }

    /** Semicolon separator for {@link #setExcludes(java.lang.String)}. */
    public static final String SEPARATOR = ";";

    /**
     * Accessor method to set an optionally case-sensitive String file name(s) to exclude.
     * <p><b>Note:</b> simply uses .toUpperCase() and .toLowerCase() on the input string(s);
     * does not do full case-checking on the entire string!</p>
     *
     * NEEDSDOC @param exFiles
     * NEEDSDOC @param caseSensitive
     */
    public void setExcludes(String exFiles, boolean caseSensitive)
    {

        StringTokenizer st = new StringTokenizer(exFiles, SEPARATOR);

        excludeFiles = null;
        excludeFiles = new Hashtable();

        for (int i = 0; st.hasMoreTokens(); i++)
        {
            String fName = st.nextToken();

            excludeFiles.put(fName, "");

            if (!caseSensitive)
            {
                excludeFiles.put(fName.toUpperCase(), "");
                excludeFiles.put(fName.toLowerCase(), "");
            }
        }
    }

    /**
     * Tests if a specified file should be included in a file list.
     * <p>Returns true only for *.xsl files whose names start with
     * the name of the directory, case-insensitive (uses .toLowerCase()).
     * <b>Except:</b> if any filenames contain an item in excludeFiles.</p>
     * @param dir the directory in which the file was found.
     * @param name the name of the file.
     * @return <code>true</code> if the name should be included in the file list; <code>false</code> otherwise.
     * @since JDK1.0
     */
    public boolean accept(File dir, String name)
    {

        // Shortcuts for bogus filenames and dirs
        if (name == null || dir == null)
            return false;

        // Exclude any files that match an exclude rule
        if ((excludeFiles != null) && (excludeFiles.containsKey(name)))
            return false;

        File file = new File(dir, name);

        return (!file.isDirectory()) && name.toLowerCase().endsWith("xsl")
               && name.toLowerCase().startsWith(dir.getName().toLowerCase());
    }
}
