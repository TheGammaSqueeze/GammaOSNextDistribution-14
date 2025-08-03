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
 * NullDatalet.java
 *
 */
package org.apache.qetest;

import java.util.Hashtable;

/**
 * A default implementation of a Datalet with no data points.  
 * "Conversation... is the art of never appearing a bore, of 
 * knowing how to say everything interestingly, to entertain with 
 * no matter what, to be charming with nothing at all."  
 * -- Guy de Maupassant, <u>Sur l'Eau</u>
 * 
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class NullDatalet implements Datalet
{

    /**
     * Default no-arg, no-op constructor.  
     */
    public NullDatalet() { } // no-op


    /**
     * Accesor method for a brief description of this NullDatalet.  
     *
     * @return String "NullDatalet: no data contained".
     */
    public String getDescription()
    {
        return "NullDatalet: no data contained";
    }


    /**
     * Accesor method for a brief description of this NullDatalet.  
     *
     * @param s unused, you cannot set our description
     */
    public void setDescription(String s) { } // no-op


    /**
     * Load fields of this NullDatalet from a Hashtable.
     *
     * @param Hashtable unused, you cannot set our fields
     */
    public void load(Hashtable h) { } // no-op


    /**
     * Load fields of this NullDatalet from an array.
     *
     * @param args unused, you cannot set our fields
     */
    public void load(String[] args) { } // no-op


}  // end of class NullDatalet

