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
 * ExamplesTest.java
 *
 */
package org.apache.qetest.trax;

// Support for test reporting and harness classes
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
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
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.serializer.OutputPropertiesFactory;
import org.apache.xml.serializer.Serializer;
import org.apache.xml.serializer.SerializerFactory;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

//-------------------------------------------------------------------------

/**
 * Test version of xml-xalan/java/samples/trax/Examples.java.
 * <p>This file is essentially copied from the Examples for TRAX, or 
 * javax.xml.transform; however this file actually validates most 
 * output and behavior for correctness.  Hopefully, we can get this 
 * file updated at the same time as Examples.java in the future.</p>
 * <p>In general, I merely copied each method from Examples.java
 * and made minor updates (try...catch within methods, call to 
 * reporter.logBlah to output messages, etc.) then added validation 
 * of actual output files.  Note that each method validates it's 
 * output by calling fileChecker.check(...) explicitly, so we can't 
 * change the input files without carefully changing the gold files 
 * for each area.</p>
 * <p>Note that some tests may use NOT_DEFINED for their gold file 
 * if we haven't yet validated what the 'correct' output should be 
 * for each case - these should be updated as time permits.</p>
 * @author shane_curcuru@lotus.com
 * @author scott_boag@lotus.com
 * @version $Id$
 */
public class ExamplesTest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files, for the gold files
     * NOTE: Gold names must match the test cases in order, and 
     * must match the checked-in gold files ExamplesTest*.out!
     */
    protected OutputNameManager goldNames;

    /** Sample test stylesheet to use for transformations, includes gold file.  */
    protected XSLTestfileInfo fooFile = new XSLTestfileInfo();

    /** Sample test stylesheet to use for transformations, includes gold file.  */
    protected XSLTestfileInfo bazFile = new XSLTestfileInfo();

    /** Sample test stylesheet name to use for multi-transformations.  */
    protected String foo2File;

    /** Sample test stylesheet name to use for multi-transformations.  */
    protected String foo3File;

    /** Sample gold files used for specific transforms - with params.  */
    // protected String param1GoldName;

    /** Sample gold files used for specific transforms - with params and output format.  */
    // protected String param2GoldName;

    /** Sample gold files used for specific transforms - with output format.  */
    // protected String outputGoldName;

    /** Sample gold files used for specific transforms - ContentHandler.  */
    // protected String sax2GoldName;

    /** Sample gold files used for specific transforms - XMLFilter/Reader.  */
    // protected String saxGoldName;

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";
    
    /** Just initialize test name, comment, numTestCases. */
    public ExamplesTest()
    {
        numTestCases = 1;  // REPLACE_num
        testName = "ExamplesTest";
        testComment = "Test various combinations of Source and Result types";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
     * REPLACE_other_test_file_init.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // NOTE: 'reporter' variable is already initialized at this point

        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_SUBDIR
                              + File.separator;
        
        goldNames = new OutputNameManager(goldBasePath
                                         + File.separator + testName, ".out");

        reporter.logTraceMsg("NOTE! This file is very sensitive to pathing issues!");
        fooFile.inputName = swapSlash(testBasePath + "xsl/foo.xsl");
        fooFile.xmlName = swapSlash(testBasePath + "xml/foo.xml");

        bazFile.xmlName = swapSlash(testBasePath + "xml/baz.xml");

        foo2File = swapSlash(testBasePath + "xsl/foo2.xsl");

        foo3File = swapSlash(testBasePath + "xsl/foo3.xsl");

        // param1GoldName = goldBasePath + "param1.out";
        // param2GoldName = goldBasePath + "param2.out";
        // outputGoldName = goldBasePath + "output.out";
        // saxGoldName = goldBasePath + "fooSAX.out";
        // sax2GoldName = goldBasePath + "fooSAX2.out";
        return true;
    }


    /**
     * Worker method to swap / for \ in Strings.  
     */
    public String swapSlash(String s)
    {
        return (new String(s)).replace('\\', '/');
    }


    /**
     * Call each of the methods found in Examples.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Call each of the methods found in Examples");

        // Note: the tests must be used with the same input files, 
        //  since they hard-code the gold files within the methods
        String tmpFooNames = fooFile.xmlName + ", " + fooFile.inputName;
        reporter.logStatusMsg("exampleSimple1(" + tmpFooNames + ")");
        exampleSimple1(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleSimple2(" + tmpFooNames + ")");
        exampleSimple2(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleFromStream(" + tmpFooNames + ")");
        exampleFromStream(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleFromReader(" + tmpFooNames + ")");
        exampleFromReader(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleUseTemplatesObj(" + fooFile.xmlName + ", " + bazFile.xmlName + ", " + fooFile.inputName + ")");
        exampleUseTemplatesObj(fooFile.xmlName, bazFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleContentHandlerToContentHandler(" + tmpFooNames + ")");
        reporter.logErrorMsg("exampleContentHandlerToContentHandler(" + tmpFooNames + ") NOTE: See SmoketestOuttakes instead!");
        String unused = goldNames.nextName(); // must increment out, gold names to simulate actual test
        unused = outNames.nextName(); // must increment out, gold names to simulate actual test
//        exampleContentHandlerToContentHandler(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleXMLReader(" + tmpFooNames + ")");
        exampleXMLReader(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleXMLFilter(" + tmpFooNames + ")");
        exampleXMLFilter(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleXMLFilterChain(" + tmpFooNames 
                              + ", " + foo2File + ", " + foo3File + ")");
        exampleXMLFilterChain(fooFile.xmlName, fooFile.inputName, foo2File, foo3File);

        reporter.logStatusMsg("exampleDOM2DOM(" + tmpFooNames + ")");
        exampleDOM2DOM(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleParam(" + tmpFooNames + ")");
        exampleParam(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleTransformerReuse(" + tmpFooNames + ")");
        exampleTransformerReuse(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleOutputProperties(" + tmpFooNames + ")");
        exampleOutputProperties(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleUseAssociated(" + fooFile.xmlName +")");
        exampleUseAssociated(fooFile.xmlName);

        reporter.logStatusMsg("exampleContentHandler2DOM(" + tmpFooNames + ")");
        reporter.logErrorMsg("exampleContentHandler2DOM(" + tmpFooNames + ") NOTE: See SmoketestOuttakes instead!");
        unused = goldNames.nextName(); // must increment out, gold names to simulate actual test
        unused = outNames.nextName(); // must increment out, gold names to simulate actual test
        //exampleContentHandler2DOM(fooFile.xmlName, fooFile.inputName);

        reporter.logStatusMsg("exampleAsSerializer(" + tmpFooNames + ")");
        exampleAsSerializer(fooFile.xmlName, fooFile.inputName);

        reporter.testCaseClose();
        return true;
    }

  /**
   * Show the simplest possible transformation from system id 
   * to output stream.
   */
  public void exampleSimple1(String sourceID, String xslID)
  {
    try
    {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();
    
        // Create a transformer for the stylesheet.
        reporter.logTraceMsg("newTransformer(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Transformer transformer 
          = tfactory.newTransformer(new StreamSource(QetestUtils.filenameToURL(xslID)));
        // No need to setSystemId, the transformer can get it from the URL
    
        // Transform the source XML to System.out.
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer.transform( new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        String goldname = goldNames.nextName();
        System.out.println("fooFile.goldName: "+goldname);
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldname),                
                          "exampleSimple1 fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleSimple1 threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleSimple1 threw");
    }
  }
  
  /**
   * Show the simplest possible transformation from File 
   * to a File.
   */
  public void exampleSimple2(String sourceID, String xslID)
  {
    try
    {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();
    
        // Create a transformer for the stylesheet.
        reporter.logTraceMsg("newTransformer(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Transformer transformer 
          = tfactory.newTransformer(new StreamSource(new File(xslID)));
        // No need to setSystemId, the transformer can get it from the File
    
        // Transform the source XML to System.out.
        reporter.logTraceMsg("new StreamSource(new File(" + sourceID);
        transformer.transform( new StreamSource(new File(sourceID)),
                               new StreamResult(new File(outNames.nextName())));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleSimple2 fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleSimple2 threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleSimple2 threw");
    }
  }

  
  /**
   * Show simple transformation from input stream to output stream.
   */
  public void exampleFromStream(String sourceID, String xslID)
 {
    try
    {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        reporter.logTraceMsg("new BufferedInputStream(new FileInputStream(" + xslID);
        InputStream xslIS = new BufferedInputStream(new FileInputStream(xslID));
        StreamSource xslSource = new StreamSource(xslIS);
        // Note that if we don't do this, relative URLs can not be resolved correctly!
        xslSource.setSystemId(QetestUtils.filenameToURL(xslID));

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(xslSource);
    
        reporter.logTraceMsg("new BufferedInputStream(new FileInputStream(" + sourceID);
        InputStream xmlIS = new BufferedInputStream(new FileInputStream(sourceID));
        StreamSource xmlSource = new StreamSource(xmlIS);
        // Note that if we don't do this, relative URLs can not be resolved correctly!
        xmlSource.setSystemId(QetestUtils.filenameToURL(sourceID));
    
        // Transform the source XML to System.out.
        transformer.transform( xmlSource, new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleFromStream fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleFromStream threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleFromStream threw");
    }
  }
  
  /**
   * Show simple transformation from reader to output stream.  In general 
   * this use case is discouraged, since the XML encoding can not be 
   * processed.
   */
  public void exampleFromReader(String sourceID, String xslID)
  {
    try
    {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Note that in this case the XML encoding can not be processed!
        reporter.logTraceMsg("new BufferedReader(new InputStreamReader(new FileInputStream(" + xslID);
        Reader xslReader = new BufferedReader(new InputStreamReader(new FileInputStream(xslID), "UTF-8")); //@DEM
//        Reader xslReader = new BufferedReader(new FileReader(xslID));  @DEM
        StreamSource xslSource = new StreamSource(xslReader);
        // Note that if we don't do this, relative URLs can not be resolved correctly!
        xslSource.setSystemId(QetestUtils.filenameToURL(xslID));

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(xslSource);
    
        // Note that in this case the XML encoding can not be processed!
        reporter.logTraceMsg("new BufferedReader(new FileReader(" + sourceID);
        Reader xmlReader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceID), "UTF-8")); //@DEM
//        Reader xmlReader = new BufferedReader(new FileReader(sourceID));  @DEM
        StreamSource xmlSource = new StreamSource(xmlReader);
        // Note that if we don't do this, relative URLs can not be resolved correctly!
        xmlSource.setSystemId(QetestUtils.filenameToURL(sourceID));
    
        // Transform the source XML to System.out.
        transformer.transform( xmlSource, new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleFromReader fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleFromReader threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleFromReader threw");
    }
  }


 
  /**
   * Show the simplest possible transformation from system id to output stream.
   */
  public void exampleUseTemplatesObj(String sourceID1, 
                                    String sourceID2, 
                                    String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();
    
        // Create a templates object, which is the processed, 
        // thread-safe representation of the stylesheet.
        reporter.logTraceMsg("newTemplates(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Templates templates = tfactory.newTemplates(new StreamSource(QetestUtils.filenameToURL(xslID)));

        // Illustrate the fact that you can make multiple transformers 
        // from the same template.
        Transformer transformer1 = templates.newTransformer();
        Transformer transformer2 = templates.newTransformer();
    
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID1));
        transformer1.transform(new StreamSource(QetestUtils.filenameToURL(sourceID1)),
                              new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleUseTemplatesObj(1) fileChecker of:" + outNames.currentName());
    
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID2));
        transformer2.transform(new StreamSource(QetestUtils.filenameToURL(sourceID2)),
                              new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleUseTemplatesObj(2) fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleUseTemplatesObj threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleUseTemplatesObj threw");
    }
  }
  

  /**
   * Show the Transformer using SAX events in and SAX events out.
   */
  public void exampleContentHandlerToContentHandler(String sourceID, 
                                                           String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Does this factory support SAX features?
        if (!tfactory.getFeature(SAXSource.FEATURE))
        {
            reporter.logErrorMsg("exampleContentHandlerToContentHandler:Processor does not support SAX");
            return;
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
                          new File(goldNames.nextName()),                
                          "exampleContentHandlerToContentHandler fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleContentHandlerToContentHandler threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleContentHandlerToContentHandler threw");
    }

  }
  
  /**
   * Show the Transformer as a SAX2 XMLReader.  An XMLFilter obtained 
   * from newXMLFilter should act as a transforming XMLReader if setParent is not
   * called.  Internally, an XMLReader is created as the parent for the XMLFilter.
   */
  public void exampleXMLReader(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        if(!tfactory.getFeature(SAXSource.FEATURE))
        {
            reporter.logErrorMsg("exampleXMLReader:Processor does not support SAX");
            return;
        }
          reporter.logTraceMsg("newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslID));
          XMLReader reader 
            = ((SAXTransformerFactory) tfactory).newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslID)));
          
          // Set the result handling to be a serialization to the file output stream.
          Serializer serializer = SerializerFactory.getSerializer
                                  (OutputPropertiesFactory.getDefaultMethodProperties("xml"));
          FileOutputStream fos = new FileOutputStream(outNames.nextName());                        
          serializer.setOutputStream(fos);
          reporter.logStatusMsg("Test-output-to: new FileOutputStream(" + outNames.currentName());
      
          reader.setContentHandler(serializer.asContentHandler());

          reporter.logTraceMsg("reader.parse(new InputSource(" + QetestUtils.filenameToURL(sourceID));
          reader.parse(new InputSource(QetestUtils.filenameToURL(sourceID)));
          fos.close();

        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleXMLReader fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleXMLReader threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleXMLReader threw");
    }

  }

  /**
   * Show the Transformer as a simple XMLFilter.  This is pretty similar
   * to exampleXMLReader, except that here the parent XMLReader is created 
   * by the caller, instead of automatically within the XMLFilter.  This 
   * gives the caller more direct control over the parent reader.
   */
  public void exampleXMLFilter(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();

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

          // Set the result handling to be a serialization to the file output stream.
          Serializer serializer = SerializerFactory.getSerializer
                                  (OutputPropertiesFactory.getDefaultMethodProperties("xml"));
          FileOutputStream fos = new FileOutputStream(outNames.nextName());
          serializer.setOutputStream(fos);
          reporter.logStatusMsg("Test-output-to: new FileOutputStream(" + outNames.currentName());
          reader.setContentHandler(serializer.asContentHandler());

        try
        {
          reader.setFeature("http://xml.org/sax/features/namespace-prefixes",
                            true);
          reader.setFeature("http://apache.org/xml/features/validation/dynamic",
                            true);
        }
        catch (SAXException se)
        {
            reporter.logErrorMsg("exampleXMLFilter: reader threw :" + se.toString());
          // What can we do?
          // TODO: User diagnostics.
        }

        reporter.logTraceMsg("newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        XMLFilter filter 
          = ((SAXTransformerFactory) tfactory).newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslID)));

        filter.setParent(reader);

        // Now, when you call transformer.parse, it will set itself as 
        // the content handler for the parser object (it's "parent"), and 
        // will then call the parse method on the parser.
          reporter.logTraceMsg("filter.parse(new InputSource(" + QetestUtils.filenameToURL(sourceID));
        filter.parse(new InputSource(QetestUtils.filenameToURL(sourceID)));
        
        fos.close();

        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleXMLFilter fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleXMLFilter threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleXMLFilter threw");
    }
  }

  /**
   * This example shows how to chain events from one Transformer
   * to another transformer, using the Transformer as a
   * SAX2 XMLFilter/XMLReader.
   */
  public void exampleXMLFilterChain(String sourceID, String xslID_1, 
                                    String xslID_2, String xslID_3)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();
    
        Templates stylesheet1 = tfactory.newTemplates(new StreamSource(QetestUtils.filenameToURL(xslID_1)));
        Transformer transformer1 = stylesheet1.newTransformer();
    
         // If one success, assume all will succeed.
        if (!tfactory.getFeature(SAXSource.FEATURE))
        {
            reporter.logErrorMsg("exampleXMLFilterChain:Processor does not support SAX");
            return;
        }
          SAXTransformerFactory stf = (SAXTransformerFactory)tfactory;
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

          reporter.logTraceMsg("newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslID_1));
          XMLFilter filter1 = stf.newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslID_1)));

          reporter.logTraceMsg("newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslID_2));
          XMLFilter filter2 = stf.newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslID_2)));

          reporter.logTraceMsg("newXMLFilter(new StreamSource(" + QetestUtils.filenameToURL(xslID_3));
          XMLFilter filter3 = stf.newXMLFilter(new StreamSource(QetestUtils.filenameToURL(xslID_3)));

          if (null == filter1) // If one success, assume all were success.
          {
              reporter.checkFail("exampleXMLFilterChain: filter is null");
              return;
          }

            // transformer1 will use a SAX parser as it's reader.    
            filter1.setParent(reader);

            // transformer2 will use transformer1 as it's reader.
            filter2.setParent(filter1);

            // transform3 will use transform2 as it's reader.
            filter3.setParent(filter2);

          // Set the result handling to be a serialization to the file output stream.
          Serializer serializer = SerializerFactory.getSerializer
                                  (OutputPropertiesFactory.getDefaultMethodProperties("xml"));
          FileOutputStream fos = new FileOutputStream(outNames.nextName());
          serializer.setOutputStream(fos);
          reporter.logStatusMsg("Test-output-to: new FileOutputStream(" + outNames.currentName());
          filter3.setContentHandler(serializer.asContentHandler());

            // Now, when you call transformer3 to parse, it will set  
            // itself as the ContentHandler for transform2, and 
            // call transform2.parse, which will set itself as the 
            // content handler for transform1, and call transform1.parse, 
            // which will set itself as the content listener for the 
            // SAX parser, and call parser.parse(new InputSource(fooFile.xmlName)).
          reporter.logTraceMsg("filter3.parse(new InputSource(" + QetestUtils.filenameToURL(sourceID));
            filter3.parse(new InputSource(QetestUtils.filenameToURL(sourceID)));
            fos.close();
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleXMLFilterChain fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleXMLFilterChain threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleXMLFilterChain threw");
    }
  }

  /**
   * Show how to transform a DOM tree into another DOM tree.
   * This uses the javax.xml.parsers to parse an XML file into a
   * DOM, and create an output DOM.
   */
  public Node exampleDOM2DOM(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();

        if (!tfactory.getFeature(DOMSource.FEATURE))
        {
            reporter.logErrorMsg("exampleDOM2DOM:Processor does not support SAX");
            return null;
        }

          Templates templates;

          {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            org.w3c.dom.Document outNode = docBuilder.newDocument();
            reporter.logTraceMsg("docBuilder.parse(new InputSource(" + QetestUtils.filenameToURL(xslID));
            Node doc = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(xslID)));
     
            DOMSource dsource = new DOMSource(doc);
            // If we don't do this, the transformer won't know how to 
            // resolve relative URLs in the stylesheet.
            dsource.setSystemId(QetestUtils.filenameToURL(xslID));

            templates = tfactory.newTemplates(dsource);
          }

          Transformer transformer = templates.newTransformer();
          DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
          dfactory.setNamespaceAware(true); // must have namespaces for xsl files!
          DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
          org.w3c.dom.Document outNode = docBuilder.newDocument();
          reporter.logTraceMsg("docBuilder.parse(new InputSource(" + QetestUtils.filenameToURL(sourceID));
          Node doc = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(sourceID)));

          transformer.transform(new DOMSource(doc), new DOMResult(outNode));
          
          Transformer serializer = tfactory.newTransformer();
          serializer.transform(new DOMSource(outNode), new StreamResult(outNames.nextName()));
          reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());

        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleDOM2DOM fileChecker of:" + outNames.currentName());
          return outNode;
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleDOM2DOM threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleDOM2DOM threw");
        return null;
        
    }
  } 

  /**
   * This shows how to set a parameter for use by the templates. Use 
   * two transformers to show that different parameters may be set 
   * on different transformers.
   */
  public void exampleParam(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        reporter.logTraceMsg("newTemplates(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Templates templates = tfactory.newTemplates(new StreamSource(QetestUtils.filenameToURL(xslID)));
        Transformer transformer1 = templates.newTransformer();
        Transformer transformer2 = templates.newTransformer();

        transformer1.setParameter("a-param",
                                  "hello to you!");
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer1.transform(new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleParam(1) fileChecker of:" + outNames.currentName());
    
    
        transformer2.setOutputProperty(OutputKeys.INDENT, "yes");
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer2.transform(new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleParam(2) fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleParam threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleParam threw");
    }
  }
  
  /**
   * Show the that a transformer can be reused, and show resetting 
   * a parameter on the transformer.
   */
  public void exampleTransformerReuse(String sourceID, String xslID)
  {
    try
    {
        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();
    
        // Create a transformer for the stylesheet.
        reporter.logTraceMsg("newTemplates(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Transformer transformer 
          = tfactory.newTransformer(new StreamSource(QetestUtils.filenameToURL(xslID)));
    
        transformer.setParameter("a-param",
                                  "hello to you!");
    
        // Transform the source XML to System.out.
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer.transform( new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleTransformerReuse(1) fileChecker of:" + outNames.currentName());

        transformer.setParameter("a-param",
                                  "hello to me!");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        // Transform the source XML to System.out.
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer.transform( new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleTransformerReuse(2) fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleTransformerReuse threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleTransformerReuse threw");
    }
  }

  /**
   * Show how to override output properties.
   */
  public void exampleOutputProperties(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        reporter.logTraceMsg("newTemplates(new StreamSource(" + QetestUtils.filenameToURL(xslID));
        Templates templates = tfactory.newTemplates(new StreamSource(QetestUtils.filenameToURL(xslID)));
        Properties oprops = templates.getOutputProperties();

        oprops.put(OutputKeys.INDENT, "yes");

        Transformer transformer = templates.newTransformer();

        transformer.setOutputProperties(oprops);
        reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
        transformer.transform(new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleOutputProperties fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleOutputProperties threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleOutputProperties threw");
    }
  }

  /**
   * Show how to get stylesheets that are associated with a given
   * xml document via the xml-stylesheet PI (see http://www.w3.org/TR/xml-stylesheet/).
   */
  public void exampleUseAssociated(String sourceID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // The DOM tfactory will have it's own way, based on DOM2, 
        // of getting associated stylesheets.
        if (!(tfactory instanceof SAXTransformerFactory))
        {
            reporter.logErrorMsg("exampleUseAssociated:Processor does not support SAX");
            return;
        }
          SAXTransformerFactory stf = ((SAXTransformerFactory) tfactory);
          reporter.logTraceMsg("getAssociatedStylesheet(new StreamSource(" + QetestUtils.filenameToURL(sourceID));
          Source sources =
            stf.getAssociatedStylesheet(new StreamSource(QetestUtils.filenameToURL(sourceID)),
              null, null, null);

          if(null == sources)
          {
            reporter.checkFail("exampleUseAssociated:problem with source objects");
            return;
          }
            Transformer transformer = tfactory.newTransformer(sources);

            reporter.logTraceMsg("new StreamSource(" + QetestUtils.filenameToURL(sourceID));
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(sourceID)),
                               new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleUseAssociated fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleUseAssociated threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleUseAssociated threw");
    }
  }
  
  /**
   * Show the Transformer using SAX events in and DOM nodes out.
   */
  public void exampleContentHandler2DOM(String sourceID, String xslID)
  {
    try
    {
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Make sure the transformer factory we obtained supports both
        // DOM and SAX.
        if (!(tfactory.getFeature(SAXSource.FEATURE)
            && tfactory.getFeature(DOMSource.FEATURE)))
        {
            reporter.logErrorMsg("exampleContentHandler2DOM:Processor does not support SAX/DOM");
            return;
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
                          new File(goldNames.nextName()),                
                          "exampleContentHandler2DOM fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleContentHandler2DOM threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleContentHandler2DOM threw");
    }
  }
  
  /**
   * Serialize a node to System.out.
   */
  public void exampleSerializeNode(Node node)
    throws TransformerException, TransformerConfigurationException, SAXException, IOException,
    ParserConfigurationException
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
   * A fuller example showing how the TrAX interface can be used 
   * to serialize a DOM tree.
   */
  public void exampleAsSerializer(String sourceID, String xslID)
  {
    try
    {
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
        org.w3c.dom.Document outNode = docBuilder.newDocument();
        reporter.logTraceMsg("docBuilder.parse(new InputSource(" + QetestUtils.filenameToURL(sourceID));
        Node doc = docBuilder.parse(new InputSource(QetestUtils.filenameToURL(sourceID)));

        TransformerFactory tfactory = TransformerFactory.newInstance(); 
    
        // This creates a transformer that does a simple identity transform, 
        // and thus can be used for all intents and purposes as a serializer.
        Transformer serializer = tfactory.newTransformer();
    
        Properties oprops = new Properties();
        oprops.put("method", "html");
        oprops.put("{http://xml.apache.org/xslt}indent-amount", "2");
        serializer.setOutputProperties(oprops);
        serializer.transform(new DOMSource(doc), 
                             new StreamResult(outNames.nextName()));
        reporter.logStatusMsg("Test-output-to: new StreamResult(" + outNames.currentName());
        fileChecker.check(reporter, new File(outNames.currentName()),
                          new File(goldNames.nextName()),                
                          "exampleAsSerializer fileChecker of:" + outNames.currentName());
    } 
    catch (Throwable t)
    {
        reporter.checkFail("exampleAsSerializer threw: " + t.toString());
        reporter.logThrowable(reporter.ERRORMSG, t, "exampleAsSerializer threw");
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
        return ("Common [optional] options supported by ExamplesTest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {

        ExamplesTest app = new ExamplesTest();

        app.doMain(args);
    }
}
