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
package org.apache.qetest.xslwrapper;

import java.io.InputStream;
import java.util.Properties;

import org.apache.qetest.QetestUtils;

/**
 * Factory static class for creating TransformWrappers.
 *
 * Includes a 'wrapperMapper' functionality to simplify specifying 
 * the 'flavor' of a wrapper to create.  This is optional, but will 
 * allow a user to specify newWrapper("trax.file") and get back an 
 * instance of an org.apache.qetest.xslwrapper.TraxFileWrapper.
 *
 * @author Shane Curcuru
 * @version $Id$
 */
public abstract class TransformWrapperFactory
{

    /**
     * Currently known list of implemented wrappers.
     *
     * Allows specification of a simple name for each wrapper,
     * so clients don't necessarily have to know the FQCN or
     * fully qualified classname of their wrapper.</p>
     *
     * <ul>Where:
     * <li>key = String simple name = xalan|trax|etc...</li>
     * <li>value = String FQCN of wrapper implementation</li>
     * <li>Supports: See TransformWrapperFactory.properties</li>
     * </ul>
     */
    protected static Properties wrapperMapper = null;


    /** 
     * Static initializer for wrapperMapper.  
     * Attempts to load TransformWrapperFactory.properties into 
     * our wrapperMapper.
     */
    static
    {
        wrapperMapper = new Properties();

        // Add a default trax flavor, since it's widely used
        // This should be overwritten below if the properties 
        //  file can be loaded
        wrapperMapper.put("trax", "org.apache.qetest.xslwrapper.TraxFileWrapper");

        try
        {
            InputStream is =
                TransformWrapperFactory.class.getResourceAsStream(
                    "TransformWrapperFactory.properties");

            wrapperMapper.load(is);
            is.close();
        }
        catch (Exception e)
        {
            System.err.println(
                "TransformWrapperFactory.properties.load() threw: "
                + e.toString());
            e.printStackTrace();
        }
    };


    /**
     * Accessor for our wrapperMapper.
     *
     * @return Properties block of wrapper implementations
     * that we implicitly know about; may be null if we have none
     */
    public static final Properties getDescriptions()
    {
        return wrapperMapper;
    }


    /**
     * Return an instance of a TransformWrapper of requested flavor.
     *
     * This static factory method creates TransformWrappers for the 
     * user.  Our 'wrapperMapper' functionality allows users to 
     * either specify short names listed in 
     * TransformWrapperFactory.properties (which are mapped to the 
     * appropriate FQCN's) or to directly supply the FQCN of a 
     * wrapper to create.
     *
     * @param flavor to create, either a wrapperMapped one or FQCN
     *
     * @return instance of a wrapper; will throw an 
     * IllegalArgumentException if we cannot find the asked-for 
     * wrapper class anywhere
     */
    public static final TransformWrapper newWrapper(String flavor)
    {

        // Attempt to lookup the flavor: if found, use the 
        //  value we got, otherwise default to the same value
        String className = wrapperMapper.getProperty(flavor, flavor);
        
        try
        {
            // Allow people to use bare classnames in popular packages
            Class clazz = QetestUtils.testClassForName(className, 
                               QetestUtils.defaultPackages,
                               "Wrapper Not Found");
            
            return (TransformWrapper) clazz.newInstance();
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("newWrapper(" + flavor + ") threw: " + e.toString());
        }
    }


    /**
     * Simplistic command line support merely prints out wrapperMapper.
     *
     * @param args command line args - unused
     */
    public static void main(String[] args)
    {
        Properties p = getDescriptions();
        System.out.println("TransformWrapperFactory: installed flavors=wrappers");
        p.list(System.out);
    }
}
