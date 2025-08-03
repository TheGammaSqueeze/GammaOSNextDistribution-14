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
import java.util.Hashtable;

/**
 * Simple file filter; returns *.xsl non-dir files that start with the directory name.
 * Has crude support for an excludes list of filename bases.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class GoldFileRules extends ConformanceFileRules
{

    /** Initialize for defaults (not using inclusion list) no-op. */
    public GoldFileRules(){}

    /**
     * Initialize with a case-sensitive Hash of file names to exclude.  
     *
     * NEEDSDOC @param excludesHash
     */
    public GoldFileRules(Hashtable excludesHash)
    {
    	super(excludesHash);
    }

    /**
     * Initialize with a case-insensitive semicolon-delimited String of file names to exclude.  
     *
     * NEEDSDOC @param excludesStr
     */
    public GoldFileRules(String excludesStr)
    {
        super(excludesStr);
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

        return (!file.isDirectory()) && name.toLowerCase().endsWith(".out")
               && name.toLowerCase().startsWith(dir.getName().toLowerCase());
    }
}
