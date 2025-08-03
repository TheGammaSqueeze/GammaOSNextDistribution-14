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
 * SmoketestOuttakes.java
 *
 */
package org.apache.qetest.xalanj2;

// Support for test reporting and harness classes
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.trax.LoggingErrorListener;
import org.apache.qetest.trax.LoggingURIResolver;
import org.apache.qetest.xsl.LoggingSAXErrorHandler;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.serializer.OutputPropertiesFactory;
import org.apache.xml.serializer.Serializer;
import org.apache.xml.serializer.SerializerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * Individual test points taken out of other automation files.  
 * 
 * Although as a quality engineer I'm not sure I really like this 
 * idea, I'm temporarily moving test points with known and reported 
 * fail conditions out of a number of other automated tests into 
 * here.  In a distributed open source project like this, this 
 * should make it easier for developers to run a reliable smoketest 
 * before making any checkins (since the list of smoketest files 
 * will generally be kept to tests that we expect should pass; thus 
 * any fails when you run the smoketest when you run it are likely 
 * due to recent changes you have made).
 *
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SmoketestOuttakes extends FileBasedTest
{

    /** Provides nextName(), currentName() functionality.  */
    protected OutputNameManager outNames;


    /** Just initialize test name, comment, numTestCases. */
    public SmoketestOuttakes()
    {
        numTestCases = 6;  // REPLACE_num
        testName = "SmoketestOuttakes";
        testComment = "Individual test points taken out of other automation files";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + "trax");
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + "trax" + File.separator
                                         + testName, ".out");

        return true;
    }


    /**
     * Recreate ExamplesTest.exampleContentHandlerToContentHandler.  
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Recreate ExamplesTest.exampleContentHandlerToContentHandler");

        try
        {
        String xslID = inputDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "xsl"
                              + File.separator
                              + "foo.xsl";
        String sourceID = inputDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "xml"
                              + File.separator
                              + "foo.xml";
        String goldName = goldDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "ExamplesTest_7.out";

        reporter.logTraceMsg("NOTE! This file is very sensitive to pathing issues!");
        
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Does this factory support SAX features?
        if (!tfactory.getFeature(SAXSource.FEATURE))
        {
            reporter.logErrorMsg("exampleContentHandlerToContentHandler:Processor does not support SAX");
            return true;
        }
          // If so, we can safely cast.
          SAXTransformerFactory stfactory = ((SAXTransformerFactory) tfactory);
          
          // A TransformerHandler is a ContentHandler that will listen for 
          // SAX events, and transform them to the result.
          reporter.logTraceMsg("newTransformerHandler(new StreamSource(" + QetestUtils.filenameToURL(xslID));
          TransformerHandler handler 
            = stfactory.newTransformerHandler(new StreamSource(QetestUtils.filenameToURL(xslID)));

          // Set the result handling to be a serialization to the file output stream.
          Serializer serializer = SerializerFactory.getSerializer
            (OutputPropertiesFactory.getDefaultMethodProperties("xml"));
          FileOutputStream fos = new FileOutputStream(outNames.nextName());
          serializer.setOutputStream(fos);
          reporter.logStatusMsg("Test-output-to: new FileOutputStream(" + outNames.currentName());
          
          Result result = new SAXResult(serializer.asContentHandler());

          handler.setResult(result);
          
          // Create a reader, and set it's content handler to be the TransformerHandler.
          XMLReader reader=null;

          // Use JAXP1.1 ( if possible )
          try {
              javax.xml.parsers.SAXParserFactory factory=
                  javax.xml.parsers.SAXParserFactory.newInstance();
              factory.setNamespaceAware( true );
              javax.xml.parsers.SAXParser jaxpParser=
                  factory.newSAXParser();
              reader=jaxpParser.getXMLReader();
              
          } catch( javax.xml.parsers.ParserConfigurationException ex ) {
              throw new org.xml.sax.SAXException( ex );
          } catch( javax.xml.parsers.FactoryConfigurationError ex1 ) {
              throw new org.xml.sax.SAXException( ex1.toString() );
          } catch( NoSuchMethodError ex2 ) {
          }
          if( reader==null ) reader = getJAXPXMLReader();
          reader.setContentHandler(handler);
          
          // It's a good idea for the parser to send lexical events.
          // The TransformerHandler is also a LexicalHandler.
          reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
          
          // Parse the source XML, and send the parse events to the TransformerHandler.
          reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(sourceID));
          reader.parse(QetestUtils.filenameToURL(sourceID));
          fos.close();

          reporter.logTraceMsg("Note: See SPR SCUU4RZT78 for discussion as to why this output is different than XMLReader/XMLFilter");
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldName),                
                          "exampleContentHandlerToContentHandler fileChecker of:" + outNames.currentName());
        
        
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testCase1:");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testCase1");
        }
        reporter.testCaseClose();
        return true;
    }



    /**
     * Recreate ExamplesTest.exampleContentHandlerToContentHandler.  
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Recreate ExamplesTest.exampleContentHandlerToContentHandler");

        String xslID = inputDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "xsl"
                              + File.separator
                              + "foo.xsl";
        String sourceID = inputDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "xml"
                              + File.separator
                              + "foo.xml";
        String goldName = goldDir 
                              + File.separator 
                              + "trax"
                              + File.separator
                              + "ExamplesTest_18.out";

        try
        {

            TransformerFactory tfactory = TransformerFactory.newInstance();

            // Make sure the transformer factory we obtained supports both
            // DOM and SAX.
            if (!(tfactory.getFeature(SAXSource.FEATURE)
                && tfactory.getFeature(DOMSource.FEATURE)))
            {
                reporter.logErrorMsg("exampleContentHandler2DOM:Processor does not support SAX/DOM");
                return true;
            }
              // We can now safely cast to a SAXTransformerFactory.
              SAXTransformerFactory sfactory = (SAXTransformerFactory) tfactory;
              
              // Create an Document node as the root for the output.
              DocumentBuilderFactory dfactory 
                = DocumentBuilderFactory.newInstance();
              DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
              org.w3c.dom.Document outNode = docBuilder.newDocument();
              
              // Create a ContentHandler that can liston to SAX events 
              // and transform the output to DOM nodes.
              reporter.logTraceMsg("newTransformerHandler(new StreamSource(" + QetestUtils.filenameToURL(xslID));
              TransformerHandler handler 
                = sfactory.newTransformerHandler(new StreamSource(QetestUtils.filenameToURL(xslID)));
              handler.setResult(new DOMResult(outNode));
              
              // Create a reader and set it's ContentHandler to be the 
              // transformer.
              XMLReader reader=null;

              // Use JAXP1.1 ( if possible )
              try {
                  javax.xml.parsers.SAXParserFactory factory=
                      javax.xml.parsers.SAXParserFactory.newInstance();
                  factory.setNamespaceAware( true );
                  javax.xml.parsers.SAXParser jaxpParser=
                      factory.newSAXParser();
                  reader=jaxpParser.getXMLReader();
                  
              } catch( javax.xml.parsers.ParserConfigurationException ex ) {
                  throw new org.xml.sax.SAXException( ex );
              } catch( javax.xml.parsers.FactoryConfigurationError ex1 ) {
                  throw new org.xml.sax.SAXException( ex1.toString() );
              } catch( NoSuchMethodError ex2 ) {
              }
              if( reader==null ) reader= getJAXPXMLReader();
              reader.setContentHandler(handler);
              reader.setProperty("http://xml.org/sax/properties/lexical-handler",
                                 handler);
              
              // Send the SAX events from the parser to the transformer,
              // and thus to the DOM tree.
              reporter.logTraceMsg("reader.parse(" + QetestUtils.filenameToURL(sourceID));
              reader.parse(QetestUtils.filenameToURL(sourceID));
              
              // Serialize the node for diagnosis.
              //    This serializes to outNames.nextName()
              exampleSerializeNode(outNode);

            fileChecker.check(reporter, new File(outNames.currentName()),
                              new File(goldName),                
                              "exampleContentHandler2DOM fileChecker of:" + outNames.currentName());

        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with testCase2:");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with testCase2");
        }
        reporter.testCaseClose();
        return true;
    }


    /**
    * Serialize a node to System.out; 
    * used in ExamplesTest; testCase1, testCase2 above
    */
    public void exampleSerializeNode(Node node)
        throws TransformerException, TransformerConfigurationException, 
        SAXException, IOException, ParserConfigurationException
    {
        TransformerFactory tfactory = TransformerFactory.newInstance(); 

        // This creates a transformer that does a simple identity transform, 
        // and thus can be used for all intents and purposes as a serializer.
        Transformer serializer = tfactory.newTransformer();

        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        serializer.transform(new DOMSource(node), 
                             new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        // TEST UPDATE - Caller must validate outNames.currentName()
    }  


    /**
     * From ErrorListenerTest.java testCase2
     * Build a bad stylesheet/do a transform with SAX.
     * Verify that the ErrorListener is called properly.
     * Primarily using SAXSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Build a bad stylesheet/do a transform with SAX");
        XSLTestfileInfo testFileInfo = new XSLTestfileInfo();
        testFileInfo.inputName = inputDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.xsl";
        testFileInfo.xmlName = inputDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.xml";
        testFileInfo.goldName = goldDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.out";
        int templatesExpectedType = LoggingErrorListener.TYPE_FATALERROR;
        String templatesExpectedValue = "decimal-format names must be unique. Name \"myminus\" has been duplicated";
        int transformExpectedType = LoggingErrorListener.TYPE_WARNING;
        String transformExpectedValue = "ExpectedMessage from:list1";
        
        
        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        XMLReader reader = null;
        Templates templates = null;
        Transformer transformer = null;
        TransformerHandler handler = null;
        try
        {
            factory = TransformerFactory.newInstance();
            saxFactory = (SAXTransformerFactory)factory; // assumes SAXSource.feature!

            // Set the errorListener and validate it
            saxFactory.setErrorListener(loggingErrorListener);
            reporter.check((saxFactory.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on saxFactory");

            // Use the JAXP way to get an XMLReader
            reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            InputSource is = new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName));

            // Attempt to build templates from known-bad stylesheet
            // Validate known errors in stylesheet building 
            loggingErrorListener.setExpected(templatesExpectedType, 
                                             templatesExpectedValue);
            reporter.logTraceMsg("About to factory.newTransformerHandler(SAX:" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            handler = saxFactory.newTransformerHandler(new SAXSource(is));
            reporter.logTraceMsg("loggingErrorListener after newTransformerHandler:" + loggingErrorListener.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            reporter.checkPass("set ErrorListener prevented any exceptions in newTransformerHandler()");

            // This stylesheet will still work, even though errors 
            //  were detected during it's building.  Note that 
            //  future versions of Xalan or other processors may 
            //  not be able to continue here...

            // Create a result and setup SAX parsing 'tree'
            Result result = new StreamResult(outNames.nextName());
            handler.setResult(result);
            reader.setContentHandler(handler);

            LoggingSAXErrorHandler loggingSAXErrorHandler = new LoggingSAXErrorHandler(reporter);
            loggingSAXErrorHandler.setThrowWhen(LoggingSAXErrorHandler.THROW_NEVER);
            reporter.logTraceMsg("LoggingSAXErrorHandler originally setup:" + loggingSAXErrorHandler.getQuickCounters());
            reader.setErrorHandler(loggingSAXErrorHandler);
            
            // Validate the first xsl:message call in the stylesheet
            loggingErrorListener.setExpected(transformExpectedType, 
                                             transformExpectedValue);
            reporter.logInfoMsg("about to parse/transform(" + QetestUtils.filenameToURL(testFileInfo.xmlName) + ")");
            reader.parse(QetestUtils.filenameToURL(testFileInfo.xmlName));
            reporter.logTraceMsg("LoggingSAXErrorHandler after parse:" + loggingSAXErrorHandler.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            loggingSAXErrorHandler.reset();

            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            fileChecker.check(reporter, 
                    new File(outNames.currentName()), 
                    new File(testFileInfo.goldName), 
                    "Bugzilla#4044 SAX transform of error xsl into: " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener-SAX unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener-SAX unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * From ErrorListenerTest.java testCase3
     * Build a bad stylesheet/do a transform with DOMs.
     * Verify that the ErrorListener is called properly.
     * Primarily using DOMSources.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Build a bad stylesheet/do a transform with DOMs");
        XSLTestfileInfo testFileInfo = new XSLTestfileInfo();
        testFileInfo.inputName = inputDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.xsl";
        testFileInfo.xmlName = inputDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.xml";
        testFileInfo.goldName = goldDir 
                              + File.separator 
                              + "err"
                              + File.separator + "ErrorListenerTest.out";
        int templatesExpectedType = LoggingErrorListener.TYPE_FATALERROR;
        String templatesExpectedValue = "decimal-format names must be unique. Name \"myminus\" has been duplicated";
        int transformExpectedType = LoggingErrorListener.TYPE_WARNING;
        String transformExpectedValue = "ExpectedMessage from:list1";

        LoggingErrorListener loggingErrorListener = new LoggingErrorListener(reporter);
        loggingErrorListener.setThrowWhen(LoggingErrorListener.THROW_NEVER);
        reporter.logTraceMsg("loggingErrorListener originally setup:" + loggingErrorListener.getQuickCounters());

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        DocumentBuilderFactory dfactory = null;
        DocumentBuilder docBuilder = null;
        Node xmlNode = null;
        Node xslNode = null;
        try
        {
            // Startup a DOM factory, create some nodes/DOMs
            dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            docBuilder = dfactory.newDocumentBuilder();
            reporter.logInfoMsg("parsing xml, xsl files to DOMs");
            xslNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            xmlNode = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(testFileInfo.xmlName)));

            // Create a transformer factory with an error listener
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(loggingErrorListener);

            // Attempt to build templates from known-bad stylesheet
            // Validate known errors in stylesheet building 
            loggingErrorListener.setExpected(templatesExpectedType, 
                                             templatesExpectedValue);
            reporter.logTraceMsg("About to factory.newTemplates(DOM:" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            templates = factory.newTemplates(new DOMSource(xslNode));
            reporter.logTraceMsg("loggingErrorListener after newTemplates:" + loggingErrorListener.getQuickCounters());
            // Clear out any setExpected or counters
            loggingErrorListener.reset();
            reporter.checkPass("set ErrorListener prevented any exceptions in newTemplates()");

            // This stylesheet will still work, even though errors 
            //  were detected during it's building.  Note that 
            //  future versions of Xalan or other processors may 
            //  not be able to continue here...
            reporter.logErrorMsg("Bugzilla#1062 throws NPE below at templates.newTransformer()");
            transformer = templates.newTransformer();

            reporter.logTraceMsg("default transformer's getErrorListener is: " + transformer.getErrorListener());
            // Set the errorListener and validate it
            transformer.setErrorListener(loggingErrorListener);
            reporter.check((transformer.getErrorListener() == loggingErrorListener),
                           true, "set/getErrorListener on transformer");

            // Validate the first xsl:message call in the stylesheet
            loggingErrorListener.setExpected(transformExpectedType, 
                                             transformExpectedValue);
            reporter.logInfoMsg("about to transform(DOM, StreamResult)");
            transformer.transform(new DOMSource(xmlNode), 
                                  new StreamResult(outNames.nextName()));
            reporter.logTraceMsg("after transform(...)");
            // Clear out any setExpected or counters
            loggingErrorListener.reset();

            // Validate the actual output file as well: in this case, 
            //  the stylesheet should still work
            fileChecker.check(reporter, 
                    new File(outNames.currentName()), 
                    new File(testFileInfo.goldName), 
                    "DOM transform of error xsl into: " + outNames.currentName());
            
        }
        catch (Throwable t)
        {
            reporter.checkFail("errorListener-DOM unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "errorListener-DOM unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * From URIResolverTest.java testCase1
     * Build a stylesheet/do a transform with lots of URIs to resolve.
     * Verify that the URIResolver is called properly.
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase5()
    {
        reporter.testCaseInit("Build a stylesheet/do a transform with lots of URIs to resolve");

        XSLTestfileInfo testFileInfo = new XSLTestfileInfo();
        testFileInfo.inputName = inputDir + File.separator + "trax" + File.separator + "URIResolverTest.xsl";
        testFileInfo.xmlName = inputDir + File.separator + "trax" + File.separator + "URIResolverTest.xml";
        testFileInfo.goldName = goldDir + File.separator + "trax" + File.separator + "URIResolverTest.out";

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Set the URIResolver and validate it
            reporter.logInfoMsg("About to factory.newTemplates(" + QetestUtils.filenameToURL(testFileInfo.inputName) + ")");
            templates = factory.newTemplates(new StreamSource(QetestUtils.filenameToURL(testFileInfo.inputName)));
            transformer = templates.newTransformer();

            // Set the URIResolver and validate it
            LoggingURIResolver loggingURIResolver = new LoggingURIResolver((Logger)reporter);
            reporter.logTraceMsg("loggingURIResolver originally setup:" + loggingURIResolver.getQuickCounters());
            transformer.setURIResolver(loggingURIResolver);
            reporter.check((transformer.getURIResolver() == loggingURIResolver),
                           true, "set/getURIResolver on transformer"); 

            // Validate various URI's to be resolved during transform
            //  time with the loggingURIResolver
            reporter.logWarningMsg("Bugzilla#2425 every document() call is resolved twice twice - two fails caused below");
            String[] expectedXmlUris = 
            {
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "../impincl/SystemIdImport.xsl",
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "impincl/SystemIdImport.xsl",
                "{" + QetestUtils.filenameToURL(testFileInfo.inputName) + "}" + "systemid/impincl/SystemIdImport.xsl",
            };
            loggingURIResolver.setExpected(expectedXmlUris);
            reporter.logTraceMsg("about to transform(...)");
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(testFileInfo.xmlName)), 
                                  new StreamResult(outNames.nextName()));
            reporter.logTraceMsg("after transform(...)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("URIResolver test unexpectedly threw: " + t.toString());
            reporter.logThrowable(Logger.ERRORMSG, t, "URIResolver test unexpectedly threw");
        }

        reporter.testCaseClose();
        return true;
    }


       public static final String xslNamespace = "http://www.w3.org/1999/XSL/Transform";
      public static final String nsNamespace = "http://www.w3.org/XML/1998/namespace";
    /**
     * From ProgrammaticDOMTest.java testCase2 Bugzilla#5133
     * Build a stylesheet DOM programmatically and use it.
     * 
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase6()
    {
        reporter.testCaseInit("Build a stylesheet DOM programmatically and use it");

        XSLTestfileInfo testFileInfo = new XSLTestfileInfo();
        testFileInfo.inputName = inputDir + File.separator + "trax" + File.separator + "identity.xsl";
        testFileInfo.xmlName = inputDir + File.separator + "trax" + File.separator + "identity.xml";
        testFileInfo.goldName = goldDir + File.separator + "trax" + File.separator + "identity.out";
        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logTraceMsg("parsing xml file");
            Document xmlDoc = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = null;
 
            // Programmatically build the XSL file into a Document and transform
            Document xslBuiltDoc = docBuilder.newDocument();
            appendIdentityDOMXSL(xslBuiltDoc, xslBuiltDoc, true);
            // For debugging, write the generated stylesheet out
            //  Note this will not textually exactly match the identity.xsl file
            reporter.logInfoMsg("Writing out xslBuiltDoc to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltDoc), new StreamResult(outNames.currentName()));

            reporter.logInfoMsg("About to newTransformer(xslBuiltDoc)");
            transformer = factory.newTransformer(new DOMSource(xslBuiltDoc));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltDoc,...) into " + outNames.currentName());


            // Programmatically build the XSL file into a DocFrag and transform
            xslBuiltDoc = docBuilder.newDocument();
            DocumentFragment xslBuiltDocFrag = xslBuiltDoc.createDocumentFragment();
            appendIdentityDOMXSL(xslBuiltDocFrag, xslBuiltDoc, true);
            // For debugging, write the generated stylesheet out
            reporter.logInfoMsg("Writing out xslBuiltDocFrag to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltDocFrag), new StreamResult(outNames.currentName()));

            reporter.logCriticalMsg("//@todo Verify that this is even a valid operation!");
            reporter.logInfoMsg("About to newTransformer(xslBuiltDocFrag)");
            reporter.logCriticalMsg("Bugzilla#5133: will throw NPE");
            transformer = factory.newTransformer(new DOMSource(xslBuiltDocFrag));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltDocFrag,...) into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with various XSL1 elems/documents");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with various XSL1 elems/documents");
        }
        try
        {
            // Startup a factory and docbuilder, create some nodes/DOMs
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

            reporter.logTraceMsg("parsing xml file");
            Document xmlDoc = docBuilder.parse(new InputSource(testFileInfo.xmlName));
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = null;

            // Programmatically build the XSL file into an Element and transform
            Document xslBuiltDoc = docBuilder.newDocument();
            // Note: Here, we implicitly already have the outer list 
            //  element, so ensure the worker method doesn't add again
            reporter.logCriticalMsg("Bugzilla#5133: will throw DOM003 exception");
            Element xslBuiltElem = xslBuiltDoc.createElementNS(xslNamespace, "xsl:stylesheet");
            xslBuiltElem.setAttributeNS(null, "version", "1.0");
            appendIdentityDOMXSL(xslBuiltElem, xslBuiltDoc, false);
            // For debugging, write the generated stylesheet out
            reporter.logInfoMsg("Writing out xslBuiltElem to "+ outNames.nextName());
            transformer = factory.newTransformer();
            transformer.transform(new DOMSource(xslBuiltElem), new StreamResult(outNames.currentName()));

            reporter.logCriticalMsg("//@todo Verify that this is even a valid operation!");
            reporter.logInfoMsg("About to newTransformer(xslBuiltElem)");
            transformer = factory.newTransformer(new DOMSource(xslBuiltElem));
            reporter.logInfoMsg("About to transform(xmlDoc, StreamResult(" + outNames.nextName() + "))");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(outNames.currentName()));
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform(xslBuiltElem,...) into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with various XSL2 elems/documents");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with various XSL2 elems/documents");
        }

        reporter.testCaseClose();
        return true;
    }

    /**
     * Adds identity.xsl elems to Node passed in.  
     * Subject to change; hackish for now
     * @author curcuru
     * @param n Node to append DOM elems to
     * @param factory Document providing createElement, etc. services
     * @param useOuterElem if we should append the top-level <stylesheet> elem
     */
    public void appendIdentityDOMXSL(Node n, Document factory, boolean useOuterElem)
    {
        try
        {
            /// <xsl:template match="@*|node()">
            Element template = factory.createElementNS(xslNamespace, "xsl:template");
            template.setAttributeNS(null, "match", "@*|node()");

            /// <xsl:copy>
            Element copyElem = factory.createElementNS(xslNamespace, "xsl:copy");

            /// <xsl:apply-templates select="@*|node()"/>
            Element applyTemplatesElem = factory.createElementNS(xslNamespace, "xsl:apply-templates");
            applyTemplatesElem.setAttributeNS(null, "select", "@*|node()");

            // Stick it all together with faked-up newlines for readability
            copyElem.appendChild(factory.createTextNode("\n    "));
            copyElem.appendChild(applyTemplatesElem);
            copyElem.appendChild(factory.createTextNode("\n  "));

            template.appendChild(factory.createTextNode("\n  "));
            template.appendChild(copyElem);
            template.appendChild(factory.createTextNode("\n"));


            if (useOuterElem)
            {
                // If asked to, create and append top-level <stylesheet> elem
                /// <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
                Element stylesheetElem = factory.createElementNS(xslNamespace, "xsl:stylesheet");
                stylesheetElem.setAttributeNS(null, "version", "1.0");

                // Following is not officially needed by the DOM,  but may help 
                // less-sophisticated DOM readers downstream
                // Removed due to DOM003 Namespace error
                // stylesheetElem.setAttributeNS(nsNamespace, "xmlns:xsl", xslNamespace);
                stylesheetElem.appendChild(template);
                n.appendChild(stylesheetElem);
            }
            else
            {
                // Otherwise, just use their Node
                n.appendChild(template);
            }

        }
        catch (Exception e)
        {
            reporter.logErrorMsg("appendIdentityDOMXSL threw: " + e.toString());
            reporter.logThrowable(Logger.ERRORMSG, e, "appendIdentityDOMXSL threw");
        }
    }    

    /**
     * Worker method to get an XMLReader.
     *
     * Not the most efficient of methods, but makes the code simpler.
     *
     * @return a new XMLReader for use, with setNamespaceAware(true)
     */
    protected XMLReader getJAXPXMLReader()
            throws Exception
    {
        // Be sure to use the JAXP methods only!
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        return saxParser.getXMLReader();
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by SmoketestOuttakes:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SmoketestOuttakes app = new SmoketestOuttakes();
        app.doMain(args);
    }
}
