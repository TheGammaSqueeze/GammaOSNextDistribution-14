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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A utility class, to do XML Schema 1.0 validation.
 *
 * @author mukulg@apache.org
 * @version $Id$
 */
public class XSValidate implements ErrorHandler {

	private String xmlFilePath = null;
	private String xsdFilePath = null;
	private List validationMessages = null;

	private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = 
	                                          "http://apache.org/xml/features/validation/schema-full-checking";

	public XSValidate(String xmlFilePath, String xsdFilePath) {
		this.xmlFilePath = xmlFilePath;
		this.xsdFilePath = xsdFilePath;
		validationMessages = new ArrayList();
	}

	public List validate() {
		try {
			SchemaFactory sfFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			sfFactory.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, true);
			sfFactory.setErrorHandler(this);
			Schema schema = sfFactory.newSchema(new SAXSource(new InputSource(xsdFilePath)));
			Validator validator = schema.newValidator();
			validator.setErrorHandler(this);
			validator.validate(new SAXSource(new InputSource(xmlFilePath)));
		}
		catch (SAXException ex) {
			validationMessages.add("SAXException: " + ex.getMessage());
		}
		catch (IOException ex) {
			validationMessages.add("IOException: " + ex.getMessage());
		}

		return validationMessages;
	}

	public void error(SAXParseException saxParseEx) throws SAXException {
		String systemId = saxParseEx.getSystemId();
		String[] sysIdParts = systemId.split("/");
		String saxParseMesg = saxParseEx.getMessage();		
		validationMessages.add("[Error] " + sysIdParts[sysIdParts.length - 1] + ":" + saxParseEx.getLineNumber() + ":" + 
		                                    saxParseEx.getColumnNumber() + ":" + saxParseMesg);
	}

	public void fatalError(SAXParseException saxParseEx) throws SAXException {
		String systemId = saxParseEx.getSystemId();
		String[] sysIdParts = systemId.split("/");
		validationMessages.add("[Fatal Error] " + sysIdParts[sysIdParts.length - 1] + ":" + saxParseEx.getLineNumber() + 
		                                          ":" + saxParseEx.getColumnNumber() + ":" + saxParseEx.getMessage());
	}

	public void warning(SAXParseException saxParseEx) throws SAXException {
	    // NO OP
	}

}
