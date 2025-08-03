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

/**
 * Interface for 'check'ing (validating) equivalence of two items.
 *
 * Implementers provide their own algorithims for determining
 * equivalence, including custom algorithims for complex objects. 
 * CheckServices should log out both any pertinent information about 
 * the checking they've performed, then log out a checkPass or 
 * checkFail (or Ambg, etc.) record, as well as returning the 
 * appropriate result constant to the caller.
 *
 * The Configurable interface also allows callers to attempt to 
 * set attributes on either the CheckService or on any 
 * underlying validation algorithims.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public interface CheckService extends Configurable
{

    /**
     * Compare two objects for equivalence, and return appropriate result.
     * Implementers should provide the details of their "equals"
     * algorithim in getDescription().  They must also call the 
     * appropriate checkPass()/checkFail()/etc. method on the 
     * supplied Logger.
     * 
     * Note that the order of actual, reference is usually 
     * important in determining the result.
     * <li>Typically:
     * <ul>any unexpected Exceptions thrown -> ERRR_RESULT</ul>
     * <ul>actual does not exist -> FAIL_RESULT</ul>
     * <ul>reference does not exist -> AMBG_RESULT</ul>
     * <ul>actual is equivalent to reference -> PASS_RESULT</ul>
     * <ul>actual is not equivalent to reference -> FAIL_RESULT</ul>
     * </li>
     *
     * @param logger to dump any output messages to
     * @param actual (current) Object to check
     * @param reference (gold, or expected) Object to check against
     * @param description of what you're checking
     * @param msg comment to log out with this test point
     * @return Logger.*_RESULT code denoting status; each method may 
     * define it's own meanings for pass, fail, ambiguous, etc.
     */
    public int check(Logger logger, Object actual,
                     Object reference, String msg);

    /**
     * Compare two objects for equivalence, and return appropriate result.
     *
     * @param logger to dump any output messages to
     * @param actual (current) Object to check
     * @param reference (gold, or expected) Object to check against
     * @param description of what you're checking
     * @param msg comment to log out with this test point
     * @param id ID tag to log out with this test point
     * @return Logger.*_RESULT code denoting status; each method may 
     * define it's own meanings for pass, fail, ambiguous, etc.
     */
    public int check(Logger logger, Object actual,
                     Object reference, String msg, String id);

    /**
     * Gets extended information about the last check call.
     * 
     * This is somewhat optional, and may be removed.  CheckServices 
     * should probably log out any additional info themselves to 
     * their logger before calling checkPass, etc., thus removing 
     * the need for callers to explicitly ask for this info.
     *
     * @return String describing any additional info about the last
     * two Objects that were checked, or null if none available
     */
    public String getExtendedInfo();

}  // end of class CheckService

