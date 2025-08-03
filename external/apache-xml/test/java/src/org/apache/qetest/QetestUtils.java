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

import org.apache.test.android.AndroidFileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Hashtable;

/**
 * Static utility class for both general-purpose testing methods 
 * and a few XML-specific methods.  
 * Also provides a simplistic Test/Testlet launching helper 
 * functionality.  Simply execute this class from the command 
 * line with a full or partial classname (in the org.apache.qetest 
 * area, obviously) and we'll load and execute that class instead.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public abstract class QetestUtils
{
    // abstract class cannot be instantiated

    /**
     * Utility method to translate a String filename to URL.  
     *
     * Note: This method is not necessarily proven to get the 
     * correct URL for every possible kind of filename; it should 
     * be improved.  It handles the most common cases that we've 
     * encountered when running Conformance tests on Xalan.
     * Also note, this method does not handle other non-file:
     * flavors of URLs at all.
     *
     * If the name is null, return null.
     * If the name starts with a common URI scheme (namely the ones 
     * found in the examples of RFC2396), then simply return the 
     * name as-is (the assumption is that it's already a URL)
     * Otherwise we attempt (cheaply) to convert to a file:/// URL.
     * 
     * @param String local path\filename of a file
     * @return a file:/// URL, the same string if it appears to 
     * already be a URL, or null if error
     */
    public static String filenameToURL(String filename)
    {
        // null begets null - something like the commutative property
        if (null == filename)
            return null;

        // Don't translate a string that already looks like a URL
        if (isCommonURL(filename))
            return filename;

        // Android-added: Look up the file in the java resources.
        String androidUrl = AndroidFileUtils.getInputFileUrlString(filename);
        if (androidUrl != null) {
            return androidUrl;
        }

        File f = new File(filename);
        String tmp = null;
        try
        {
            // This normally gives a better path
            tmp = f.getCanonicalPath();
        }
        catch (IOException ioe)
        {
            // But this can be used as a backup, for cases 
            //  where the file does not exist, etc.
            tmp = f.getAbsolutePath();
        }

        // URLs must explicitly use only forward slashes
        if (File.separatorChar == '\\') {
            tmp = tmp.replace('\\', '/');
        }
        // Note the presumption that it's a file reference
        // Ensure we have the correct number of slashes at the 
        //  start: we always want 3 /// if it's absolute
        //  (which we should have forced above)
        if (tmp.startsWith("/"))
            return "file://" + tmp;
        else
            return "file:///" + tmp;

    }


    /**
     * Utility method to find a relative path.  
     *
     * <p>Attempt to find a relative path based from the current 
     * directory (usually user.dir property).</p>
     *
     * <p>If the name is null, return null.  If the name starts 
     * with a common URI scheme (namely the ones 
     * found in the examples of RFC2396), then simply return 
     * the name itself (future work could attempt to detect 
     * file: protocols if needed).</p>
     * 
     * @param String local path\filename of a file
     * @return a local path\file that is relative; if we can't 
     * find one, we return the original name
     */
    public static String filenameToRelative(String filename)
    {
        // null begets null - something like the commutative property
        if (null == filename)
            return null;

        // Don't translate a string that already looks like a URL
        if (isCommonURL(filename))
            return filename;

        String base = null;
        try
        {
            File userdir = new File(System.getProperty("user.dir"));
            // Note: use CanonicalPath, since this ensures casing
            //  will be identical between the two files
            base = userdir.getCanonicalPath();
        } 
        catch (Exception e)
        {
            // If we can't detect this, we can't determine 
            //  relativeness, so just return the name
            return filename;
        }
        File f = new File(filename);
        String tmp = null;
        try
        {
            tmp = f.getCanonicalPath();
        }
        catch (IOException ioe)
        {
            tmp = f.getAbsolutePath();
        }

        // If it's not relative to the base, just return as-is
        //  (note: this may not be the answer you expect)
        if (!tmp.startsWith(base))
            return tmp;

        // Strip off the base
        tmp = tmp.substring(base.length());
        // Also strip off any beginning file separator, since we 
        //  don't want it to be mistaken for an absolute path
        if (tmp.startsWith(File.separator))
            return tmp.substring(1);
        else
            return tmp;
    }


    /**
     * Worker method to detect common absolute URLs.  
     * 
     * @param s String path\filename or URL (or any, really)
     * @return true if s starts with a common URI scheme (namely 
     * the ones found in the examples of RFC2396); false otherwise
     */
    protected static boolean isCommonURL(String s)
    {
        if (null == s)
            return false;
            
        if (s.startsWith("file:")
            || s.startsWith("http:")
            || s.startsWith("ftp:")
            || s.startsWith("gopher:")
            || s.startsWith("mailto:")
            || s.startsWith("news:")
            || s.startsWith("telnet:")
           )
            return true;
        else
            return false;
    }            


    /**
     * Utility method to get a testing Class object.  
     * This is mainly a bit of syntactic sugar to allow users 
     * to specify only the end parts of a package.classname 
     * and still have it loaded.  It basically does a 
     * Class.forName() search, starting with the provided 
     * classname, and if not found, searching through a list 
     * of root packages to try to find the class.
     *
     * Note the inherent danger when there are same-named 
     * classes in different packages, where the behavior will 
     * depend on the order of searchPackages.
     *
     * Commonly called like: 
     * <code>testClassForName("PerformanceTestlet", 
     * new String[] {"org.apache.qetest", "org.apache.qetest.xsl" },
     * "org.apache.qetest.StylesheetTestlet");</code>
     * 
     * @param String classname FQCN or partially specified classname
     * that you wish to load
     * @param String[] rootPackages a list of packages to search 
     * for the classname specified in array order; if null then 
     * we don't search any additional packages
     * @param String defaultClassname a default known-good FQCN to 
     * return if the classname was not found
     *
     * @return Class object asked for if one found by combining 
     * clazz with one of the rootPackages; if none, a Class of 
     * defaultClassname; or null if an error occoured
     */
    public static Class testClassForName(String classname, 
                                         String[] rootPackages,
                                         String defaultClassname)
    {
        // Ensure we have a valid classname, and try it
        if ((null != classname) && (classname.length() > 0))
        {
            // Try just the specified classname, in case it's a FQCN
            try
            {
                return Class.forName(classname);
            }
            catch (Exception e)
            {
                /* no-op, fall through */
            }

            // Now combine each of the rootPackages with the classname
            //  and see if one of them gets loaded
            if (null != rootPackages)
            {
                for (int i = 0; i < rootPackages.length; i++)
                {
                    try
                    {
                        return Class.forName(rootPackages[i] + "." + classname);
                    }
                    catch (Exception e)
                    {
                        /* no-op, continue */
                    }
                } // end for
            } // end if rootPackages...
        } // end if classname...

        // If we fell out here, try the defaultClassname
        try
        {
            return Class.forName(defaultClassname);
        }
        catch (Exception e)
        {
            // You can't always get you what you want
            return null;
        }
    }


    /**
     * Utility method to get a class name of a test.  
     * This is mainly a bit of syntactic sugar built on 
     * top of testClassForName.
     *
     * @param String classname FQCN or partially specified classname
     * that you wish to load
     * @param String[] rootPackages a list of packages to search 
     * for the classname specified in array order; if null then 
     * we don't search any additional packages
     * @param String defaultClassname a default known-good FQCN to 
     * return if the classname was not found
     *
     * @return name of class that testClassForName returns; 
     * or null if an error occoured
     */
    public static String testClassnameForName(String classname, 
                                         String[] rootPackages,
                                         String defaultClassname)
    {
        Class clazz = testClassForName(classname, rootPackages, defaultClassname);
        if (null == clazz)
            return null;
        else
            return clazz.getName();
    }


    /**
     * Utility method to create a unique runId.  
     * 
     * This is used to construct a theoretically unique Id for 
     * each run of a test script.  It is used later in some results 
     * analysis stylesheets to create comparative charts showing 
     * differences in results and timing data from one run of 
     * a test to another.
     *
     * Current format: MMddHHmm[;baseId]
     * where baseId is not used if null.
     * 
     * @param String Id base to start with
     * 
     * @return String Id to use; will include a timestamp
     */
    public static String createRunId(String baseId)
    {
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat ("MMddHHmm");
        if (null != baseId)
            //return formatter.format(new java.util.Date())+ ";" + baseId;
            return baseId + ":" + formatter.format(new java.util.Date());
        else
            return formatter.format(new java.util.Date());
    }


    /**
     * Utility method to get info about the environment.  
     * 
     * This is a simple way to get a Hashtable about the current 
     * JVM's environment from either Xalan's EnvironmentCheck 
     * utility or from org.apache.env.Which.
     *
     * @return Hashtable with info about the environment
     */
    public static Hashtable getEnvironmentHash()
    {
        Hashtable hash = new Hashtable();
        // Attempt to use Which, which will be better supported
        Class clazz = testClassForName("org.apache.env.Which", null, null);

        try
        {
            if (null != clazz)
            {
                // Call Which's method to fill hash
                final Class whichSignature[] = 
                        { Hashtable.class, String.class, String.class };
                Method which = clazz.getMethod("which", whichSignature);
                String projects = "";
                String options = "";
                Object whichArgs[] = { hash, projects, options };
                which.invoke(null, whichArgs);
            }
            else
            {
                // Use Xalan's EnvironmentCheck
                clazz = testClassForName("org.apache.xalan.xslt.EnvironmentCheck", null, null);
                if (null != clazz)
                {
                    Object envCheck = clazz.newInstance();
                    final Class getSignature[] = { };
                    Method getHash = clazz.getMethod("getEnvironmentHash", getSignature);

                    Object getArgs[] = { }; // empty
                    hash = (Hashtable)getHash.invoke(envCheck, getArgs);
                }
            }
        } 
        catch (Throwable t)
        {
            hash.put("FATAL-ERROR", "QetestUtils.getEnvironmentHash no services available; " + t.toString());
            t.printStackTrace();
        }
        return hash;
    }


    /**
     * Main method to run from the command line - this acts 
     * as a cheap launching mechanisim for Xalan tests.  
     * 
     * Simply finds the class specified in the first argument, 
     * instantiates one, and passes it any remaining command 
     * line arguments we were given.  
     * The primary motivation here is to provide a simpler 
     * command line for inexperienced  users.  You can either 
     * pass the FQCN, or just the classname and it will still 
     * get run.  Note the one danger is the order of package 
     * lookups and the potential for the wrong class to run 
     * when we have identically named classes in different 
     * packages - but this will usually work!
     * 
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            System.err.println("QetestUtils.main() ERROR in usage: must have at least one arg: classname [options]");
            return;
        }

        // Get the class specified by the first arg...
        Class clazz = QetestUtils.testClassForName(
                args[0], defaultPackages, null); // null = no default class
        if (null == clazz)
        {
            System.err.println("QetestUtils.main() ERROR: Could not find class:" + args[0]);
            return;
        }

        try
        {
            // ...find the main() method...
            Class[] parameterTypes = new Class[1];
            parameterTypes[0] = java.lang.String[].class;
            java.lang.reflect.Method main = clazz.getMethod("main", parameterTypes);
            
            // ...copy over our remaining cmdline args...
            final String[] appArgs = new String[(args.length) == 1 ? 0 : args.length - 1];
            if (args.length > 1)
            {
                System.arraycopy(args, 1, 
                                 appArgs, 0, 
                                 args.length - 1);
            }

            // ...and execute the method!
            Object[] mainArgs = new Object[1];
            mainArgs[0] = appArgs;
            main.invoke(null, mainArgs);
        }
        catch (Throwable t)
        {
            System.err.println("QetestUtils.main() ERROR: running " + args[0] 
                    + ".main() threw: " + t.toString());
            t.printStackTrace();
        }        
    }


    /** 
     * Default list of packages for xml-xalan tests.  
     * Technically this is Xalan-specific and should really be 
     * in some other directory, but I'm being lazy tonight.
     * This looks for Xalan-related tests in the following 
     * packages in <b>this order</b>:
     * <ul>
     * <li>org.apache.qetest.xsl</li>
     * <li>org.apache.qetest.xalanj2</li>
     * <li>org.apache.qetest.trax</li>
     * <li>org.apache.qetest.trax.dom</li>
     * <li>org.apache.qetest.trax.sax</li>
     * <li>org.apache.qetest.trax.stream</li>
     * <li>org.apache.qetest.xslwrapper</li>
     * <li>org.apache.qetest.xalanj1</li>
     * <li>org.apache.qetest</li>
     * <li>org.apache.qetest.qetesttest</li>
     * </ul>
     * Note the normal naming convention for automated tests 
     * is either *Test.java or *Testlet.java; although this is 
     * not required, it will make it easier to write simple 
     * test discovery mechanisims.
     */
    public static final String[] defaultPackages = 
    {
        "org.apache.qetest.xsl", 
        "org.apache.qetest.xalanj2", 
        "org.apache.qetest.trax", 
        "org.apache.qetest.trax.dom", 
        "org.apache.qetest.trax.sax", 
        "org.apache.qetest.trax.stream", 
        "org.apache.qetest.xslwrapper", 
        "org.apache.qetest.dtm", 
        "org.apache.qetest.xalanj1", 
        "org.apache.qetest",
        "org.apache.qetest.qetesttest" 
    };

}
