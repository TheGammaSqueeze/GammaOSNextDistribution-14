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
// Common Qetest / Xalan testing imports
import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.TestletImpl;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * @author dims@yahoo.com 
 * @author shane_curcuru@us.ibm.com
 */
public class Bugzilla6312 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla6312"; }

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#6312");

        // Set the TransformerFactory system property to generate and use a translet.
        // Note: To make this sample more flexible, load properties from a properties file.    
        // The setting for the Xalan Transformer is "org.apache.xalan.processor.TransformerFactoryImpl"
        String key = "javax.xml.transform.TransformerFactory";
        String value = "org.apache.xalan.xsltc.trax.TransformerFactoryImpl";
        Properties props = System.getProperties();
        props.put(key, value);
        System.setProperties(props);    

        String xslInURI = "Bugzilla6312.xsl";
        String xmlInURI = "Bugzilla6312.xml";
        String htmlOutURI = "Bugzilla6312.output";
        try
        {
            // Instantiate the TransformerFactory, and use it along with a SteamSource
            // XSL stylesheet to create a Transformer.
            SAXTransformerFactory tFactory = (SAXTransformerFactory)TransformerFactory.newInstance();

            //Transformer transformer = tFactory.newTransformer(new StreamSource(xslInURI));
            TemplatesHandler templatesHandler = tFactory.newTemplatesHandler();
            SAXParserFactory sFactory = SAXParserFactory.newInstance();
            sFactory.setNamespaceAware(true);
            XMLReader reader = sFactory.newSAXParser().getXMLReader();
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            reader.setContentHandler(templatesHandler);
            reader.parse(new InputSource(xslInURI));

            // Perform the transformation from a StreamSource to a StreamResult;
            templatesHandler.getTemplates().newTransformer().transform(new StreamSource(xmlInURI),
                                new StreamResult(new FileOutputStream(htmlOutURI)));  

            logger.logMsg(logger.STATUSMSG, "Successfully created " + htmlOutURI);
            logger.checkPass("Crash test: didn't throw exception (note: output file contents not validated");
        } 
        catch (Throwable t)
        {
            logger.logThrowable(logger.ERRORMSG, t, "Unexpected exception");
            logger.checkErr("Unexpected exception: " + t.toString());
        }
        
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=6312">
     * Link to Bugzilla report</a>
     * @return Problems with using JAXPTransletOneTransformation if we use XMLReader/TemplatesHandler.
     */
    public String getDescription()
    {
        return "Problems with using JAXPTransletOneTransformation if we use XMLReader/TemplatesHandler";
    }

}  // end of class Bugzilla6312

