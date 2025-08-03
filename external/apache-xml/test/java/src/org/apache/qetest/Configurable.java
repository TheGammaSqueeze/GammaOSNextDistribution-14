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
import java.util.Properties;

/**
 * Interface with basic configuration API's.  
 *
 * A simple interface allowing configuration of testing utilities; 
 * generally by passing generic information to the testing 
 * utility and having it pass it to any underlying objects.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public interface Configurable
{
    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * This method may or may not throw an IllegalArgumentException 
     * if an attribute that it does not recognize is passed in.  
     * This decision is up to the individual testing utility, and 
     * may include deciding to either trap underlying product 
     * exceptions for unknown attributes, or may re-throw them as 
     * IllegalArgumentExceptions.
     * 
     * Modeled after JAXP's various setAttribute()/setFeature() 
     * methods.
     * 
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     * @throws IllegalArgumentException thrown if the underlying
     * implementation doesn't recognize the attribute and wants to 
     * inform the user of this fact.
     */
    public void setAttribute(String name, Object value)
        throws IllegalArgumentException;


    /**
     * Allows the user to set specific attributes on the testing 
     * utility or it's underlying product object under test.
     * 
     * This method should attempt to set any applicable attributes 
     * found in the given attrs onto itself, and will ignore any and 
     * all attributes it does not recognize.  It should never 
     * throw exceptions.  This method may overwrite any previous 
     * attributes that were set.  Currently since this takes a 
     * Properties block you may only be able to set objects that 
     * are Strings, although individual implementations may 
     * attempt to use Hashtable.get() on only the local part.
     * 
     * @param attrs Props of various name, value attrs.
     */
    public void applyAttributes(Properties attrs);


    /**
     * Allows the user to retrieve specific attributes on the testing 
     * utility or it's underlying product object under test.
     *
     * @param name The name of the attribute.
     * @return value The value of the attribute.
     * @throws IllegalArgumentException thrown if the underlying
     * implementation doesn't recognize the attribute and wants to 
     * inform the user of this fact.
     */
    public Object getAttribute(String name)
        throws IllegalArgumentException;


    /**
     * Description of what this testing utility does.  
     * 
     * @return String description of extension
     */
    public String getDescription();

}  // end of class Configurable

