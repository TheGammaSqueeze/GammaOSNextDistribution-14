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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/**
 * A utility class, to check well-formedness of an XML document,
 * using an XML DOM parser.
 *
 * @author mukulg@apache.org
 * @version $Id$
 */
public class XMLParse {

	private String xmlFilePath = null;

	public XMLParse(String xmlFilePath) {
		this.xmlFilePath = xmlFilePath;
	}

	public boolean parse() {
	   boolean isDocumentWellFormedXml = false;
	   try {
		  DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		  DocumentBuilder builder = factory.newDocumentBuilder();
		  Document document = builder.parse(xmlFilePath);
		  if (document != null) {
			 isDocumentWellFormedXml = true;
		  }
       }
	   catch (Exception ex) {
		  // NO OP
	   }

	   return isDocumentWellFormedXml;
	}

}
