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

/**
 * A test driver class, to invoke an XML document parser.
 *
 * @author mukulg@apache.org
 * @version $Id$
 */
 public class XMLParserTestDriver {

    private static String SUCCESS_MESG = "The test case passed";

    private static String FAIL_MESG = "Test failed. Please solve this, before checking in";

    private static String FILE_EXT_SEPARATOR = ".";

    public static void main(String[] args) {
        String xmlFilePath = args[0];
        String contextProcessor = args[1];
        documentParse(xmlFilePath, contextProcessor);
    }

    private static void documentParse(String xmlFilePath, String contextProcessor) {
       XMLParse xmlParse = new XMLParse(xmlFilePath);
       boolean isDocumentWellFormedXml = xmlParse.parse();
       if (isDocumentWellFormedXml) {
          System.out.println(SUCCESS_MESG + " [" + contextProcessor + " : " + xmlFilePath.substring(0,
                                                      xmlFilePath.indexOf(FILE_EXT_SEPARATOR)) + "]!");
       }
       else {
          System.out.println(FAIL_MESG + " [" + contextProcessor + " : " +  xmlFilePath.substring(0,
                                                      xmlFilePath.indexOf(FILE_EXT_SEPARATOR)) + "]!");
       }
    }

 }
