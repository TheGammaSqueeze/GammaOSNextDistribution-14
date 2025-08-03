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
package org.apache.qetest;

import java.util.List;

/**
 * A test driver class, to invoke XML Schema 1.0 validation.
 *
 * @author mukulg@apache.org
 * @version $Id$
 */
 public class XSValidationTestDriver {

    private static String SUCCESS_MESG = "The test case passed";

    private static String FAIL_MESG = "Test failed. Please solve this, before checking in";

    private static String FILE_EXT_SEPARATOR = ".";

    public static void main(String[] args) {
        String xmlFilePath = args[0];
        String xsdFilePath = args[1];
        String contextProcessor = args[2];
        xsValidationTest(xmlFilePath, xsdFilePath, contextProcessor);
    }

    private static void xsValidationTest(String xmlFilePath, String xsdFilePath, String contextProcessor) {
       XSValidate xsValidate = new XSValidate(xmlFilePath, xsdFilePath);
       List validationMessages = xsValidate.validate();
       if (validationMessages.size() == 0) {
          System.out.println(SUCCESS_MESG + " [" + contextProcessor + " : " + xsdFilePath.substring(0, 
                                                      xsdFilePath.indexOf(FILE_EXT_SEPARATOR)) + "]!");
       }
       else {
          System.out.println(FAIL_MESG + " [" + contextProcessor + " : " +  xsdFilePath.substring(0, 
                                                   xsdFilePath.indexOf(FILE_EXT_SEPARATOR)) + "]!");
       } 
    }

 }
