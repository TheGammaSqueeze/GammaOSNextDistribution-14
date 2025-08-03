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
 * SAXResultAPITest.java
 *
 */
package org.apache.qetest.trax.sax;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
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
import org.apache.qetest.xsl.LoggingContentHandler;
import org.apache.qetest.xsl.LoggingLexicalHandler;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.ext.LexicalHandler;

//-------------------------------------------------------------------------

/**
 * API Coverage test for the SAXResult class of TRAX.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class SAXResultAPITest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** 
     * Information about an xsl/xml file pair for transforming.  
     * Public members include inputName (for xsl); xmlName; goldName; etc.
     */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** 
     * Information about an xsl/xml file pair for transforming with import/include.  
     */
    protected XSLTestfileInfo impInclFileInfo = new XSLTestfileInfo();

    /** 
     * Information about an xsl/xml file pair for transforming with DTD, etc.  
     */
    protected XSLTestfileInfo dtdFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SAX_SUBDIR = "trax" + File.separator + "sax";

    /** Nonsense systemId for various tests.  */
    public static final String NONSENSE_SYSTEMID = "file:///nonsense-system-id";

    /** Just initialize test name, comment, numTestCases. */
    public SAXResultAPITest()
    {
        numTestCases = 3;  // REPLACE_num
        testName = "SAXResultAPITest";
        testComment = "API Coverage test for the SAXResult class of TRAX";
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
        File outSubDir = new File(outputDir + File.separator + TRAX_SAX_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SAX_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_SAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_SAX_SUBDIR
                              + File.separator;

        // Note these are initialized as strings denoting filenames,
        //  and *not* as URL/URI's
        testFileInfo.inputName = testBasePath + "SAXTest.xsl";
        testFileInfo.xmlName = testBasePath + "SAXTest.xml";
        testFileInfo.goldName = goldBasePath + "SAXTest.out";

        impInclFileInfo.inputName = testBasePath + "SAXImpIncl.xsl";
        impInclFileInfo.xmlName = testBasePath + "SAXImpIncl.xml";
        impInclFileInfo.goldName = goldBasePath + "SAXImpIncl.out";

        dtdFileInfo.inputName = testBasePath + "SAXdtd.xsl";
        dtdFileInfo.xmlName = testBasePath + "SAXdtd.xml";
        dtdFileInfo.goldName = goldBasePath + "SAXdtd.out";
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(SAXSource.FEATURE)
                  && tf.getFeature(SAXResult.FEATURE)))
            {   // The rest of this test relies on SAX
                reporter.logErrorMsg("SAX*.FEATURE not supported! Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem creating factory; Some tests may be invalid!");
        }

        return true;
    }


    /**
     * Basic API coverage, constructor and set/get methods.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Basic API coverage, constructor and set/get methods");

        // Default no-arg ctor sets nothing 
        SAXResult defaultSAX = new SAXResult();
        reporter.checkObject(defaultSAX.getHandler(), null, "Default SAXResult should have null Handler");
        reporter.checkObject(defaultSAX.getLexicalHandler(), null, "Default SAXResult should have null LexicalHandler");
        reporter.checkObject(defaultSAX.getSystemId(), null, "Default SAXResult should have null SystemId");

        try
        {
            TransformerFactory factory = null;
            SAXTransformerFactory saxFactory = null;
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;

            // ctor(Handler) with identity transformer, which is both
            //  a ContentHandler and LexicalHandler
            TransformerHandler tHandler = saxFactory.newTransformerHandler();  // identity transformer
            ContentHandler handler = (ContentHandler)tHandler;
            SAXResult handlerSAX = new SAXResult(handler);
            reporter.checkObject(handlerSAX.getHandler(), handler, "SAXResult(handler) has Handler: " + handlerSAX.getHandler());
            reporter.checkObject(handlerSAX.getLexicalHandler(), null, "SAXResult(handler) should have null LexicalHandler");
            reporter.checkObject(handlerSAX.getSystemId(), null, "SAXResult(handler) should have null SystemId");

            // ctor(Handler) with LoggingContentHandler, which not
            //  a LexicalHandler, so it can't be cast
            ContentHandler nonLexHandler = new LoggingContentHandler(reporter);
            SAXResult otherHandlerSAX = new SAXResult(nonLexHandler);
            reporter.checkObject(otherHandlerSAX.getHandler(), nonLexHandler, "SAXResult(non-lexhandler) has Handler: " + otherHandlerSAX.getHandler());
            reporter.checkObject(otherHandlerSAX.getLexicalHandler(), null, "SAXResult(non-lexhandler) should have null LexicalHandler when ContentHandler!=LexicalHandler");
            reporter.checkObject(otherHandlerSAX.getSystemId(), null, "SAXResult(non-lexhandler) should have null SystemId");

            // Note the Javadoc in SAXResult which talks about 
            //  automatically casting the ContentHandler into 
            //  a LexicalHandler: this cannot be tested alone 
            //  here, since it's the Transformer that does that 
            //  internally if necessary, and it may not get set 
            //  back into the SAXResult object itself

            // set/getHandler API coverage
            SAXResult wackySAX = new SAXResult();
            wackySAX.setHandler(handler); // isa LexicalHandler also
            reporter.checkObject(wackySAX.getHandler(), handler, "set/getHandler API coverage");
            reporter.checkObject(wackySAX.getLexicalHandler(), null, "getLexicalHandler after set/getHandler");

            // set/getLexicalHandler API coverage
            LexicalHandler lexHandler = new LoggingLexicalHandler(reporter);
            reporter.logTraceMsg("lexHandler is " + lexHandler);
            wackySAX.setLexicalHandler(lexHandler); // SCUU4SPPMV - does not work
            LexicalHandler gotLH = wackySAX.getLexicalHandler();
            if (gotLH == lexHandler)
            {
                reporter.checkPass("set/getLexicalHandler API coverage is: " + wackySAX.getLexicalHandler(), "SCUU4SPPMV");
            }
            else
            {
                reporter.checkFail("set/getLexicalHandler API coverage is: " + wackySAX.getLexicalHandler(), "SCUU4SPPMV");
            }
            reporter.checkObject(wackySAX.getHandler(), handler, "set/getLexicalHandler API coverage, does not affect ContentHandler");

            // set/getHandler API coverage, setting to null, which 
            //  should work here but can't be used legally
            wackySAX.setHandler(null);
            reporter.checkObject(wackySAX.getHandler(), null, "set/getHandler API coverage to null (possibly illegal)");
            gotLH = wackySAX.getLexicalHandler();
            if (gotLH == lexHandler)
            {
                reporter.checkPass("getLexicalHandler unaffected by setHandler(null), is: " + wackySAX.getLexicalHandler(), "SCUU4SPPMV");
            }
            else
            {
                reporter.checkFail("getLexicalHandler unaffected by setHandler(null), is: " + wackySAX.getLexicalHandler(), "SCUU4SPPMV");
            }
            wackySAX.setLexicalHandler(null);
            reporter.checkObject(wackySAX.getLexicalHandler(), null, "set/getHandler API coverage to null (possibly illegal)");

            // set/getSystemId API coverage
            wackySAX.setSystemId(NONSENSE_SYSTEMID);
            reporter.checkObject(wackySAX.getSystemId(), NONSENSE_SYSTEMID, "set/getSystemId API coverage");
            wackySAX.setSystemId(null);
            reporter.checkObject(wackySAX.getSystemId(), null, "set/getSystemId API coverage to null");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with SAXResult set/get API");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with SAXResult set/get API");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Basic functionality of SAXResults.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Basic functionality of SAXResults");
        // Provide local copies of URLized filenames, so that we can
        //  later run tests with either Strings or URLs
        String xslURI = QetestUtils.filenameToURL(testFileInfo.inputName);
        String xmlURI = QetestUtils.filenameToURL(testFileInfo.xmlName);
        String xslImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.inputName);
        String xmlImpInclURI = QetestUtils.filenameToURL(impInclFileInfo.xmlName);

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        Templates streamTemplates;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;
            // Process a simple stylesheet for use later
            reporter.logTraceMsg("factory.newTemplates(new StreamSource(" + xslURI + "))");
            streamTemplates = factory.newTemplates(new StreamSource(xslURI));
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Verify very simple use of just a SAXResult
            // Use simple Xalan serializer for disk output, setting 
            //  the stylesheet's output properties into it
            Properties outProps = streamTemplates.getOutputProperties();
            // Use a TransformerHandler for serialization: this 
            //  supports ContentHandler and can replace the 
            //  Xalan/Xerces specific Serializers we used to use
            TransformerHandler tHandler = saxFactory.newTransformerHandler();
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            //Serializer serializer = SerializerFactory.getSerializer(outProps);
            //reporter.logTraceMsg("serializer.setOutputStream(new FileOutputStream(" + outNames.currentName() + ")");
            //serializer.setOutputStream(fos);
            //SAXResult saxResult = new SAXResult(serializer.asContentHandler()); // use other ContentHandler 
            Result realResult = new StreamResult(fos);
            tHandler.setResult(realResult);
            SAXResult saxResult = new SAXResult(tHandler);
            
            // Just do a normal transform to this result
            Transformer transformer = streamTemplates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("transform(new StreamSource(" + xmlURI + "), saxResult)");
            transformer.transform(new StreamSource(xmlURI), saxResult);
            fos.close(); // must close ostreams we own
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "simple transform into SAXResult into: " + outNames.currentName());

        }
        catch (Throwable t)
        {
            reporter.checkFail("Basic functionality of SAXResults threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Basic functionality of SAXResults");
        }

        try
        {
            // Negative test: SAXResult without a handler should throw
            SAXResult saxResult = new SAXResult();
            
            // Just do a normal transform to this result
            Transformer transformer = streamTemplates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("transform(..., nullsaxResult)");
            transformer.transform(new StreamSource(xmlURI), saxResult);
            reporter.checkFail("transform(..., nullsaxResult) should have thrown exception");
        }
        catch (IllegalArgumentException iae)
        {
            // This is the exception we expect, so pass (and don't 
            //  bother displaying the full logThrowable)
            reporter.checkPass("transform(..., nullsaxResult) properly threw: " + iae.toString());
        }
        catch (Throwable t)
        {
            reporter.checkFail("transform(..., nullsaxResult) unexpectedly threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "transform(..., nullsaxResult) threw");
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Detailed functionality of SAXResults: setLexicalHandler.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Detailed functionality of SAXResults: setLexicalHandler");
        String xslURI = QetestUtils.filenameToURL(dtdFileInfo.inputName);
        String xmlURI = QetestUtils.filenameToURL(dtdFileInfo.xmlName);

        TransformerFactory factory = null;
        SAXTransformerFactory saxFactory = null;
        Templates streamTemplates;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            saxFactory = (SAXTransformerFactory)factory;
            // Process a simple stylesheet for use later
            reporter.logTraceMsg("factory.newTemplates(new StreamSource(" + xslURI + "))");
            streamTemplates = factory.newTemplates(new StreamSource(xslURI));
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,"Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Validate a StreamSource to a logging SAXResult, 
            //  where we validate the actual events passed 
            //  through the SAXResult's ContentHandler and 
            //  LexicalHandler

            // Have an actual handler that does the physical output
            reporter.logInfoMsg("TransformerHandler.setResult(StreamResult)");
            TransformerHandler tHandler = saxFactory.newTransformerHandler();
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            tHandler.setResult(realResult);

            SAXResult saxResult = new SAXResult();
            // Add a contentHandler that logs out info about the 
            //  transform, and that passes-through calls back 
            //  to the original tHandler
            reporter.logInfoMsg("loggingSaxResult.setHandler(loggingContentHandler)");
            LoggingContentHandler lch = new LoggingContentHandler((Logger)reporter);
            lch.setDefaultHandler(tHandler);
            saxResult.setHandler(lch);

            // Add a lexicalHandler that logs out info about the 
            //  transform, and that passes-through calls back 
            //  to the original tHandler
            reporter.logInfoMsg("loggingSaxResult.setLexicalHandler(loggingLexicalHandler)");
            LoggingLexicalHandler llh = new LoggingLexicalHandler((Logger)reporter);
            llh.setDefaultHandler(tHandler);
            saxResult.setLexicalHandler(llh);
            
            // Just do a normal transform to this result
            Transformer transformer = streamTemplates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("transform(new StreamSource(" + xmlURI + "), loggingSaxResult)");
            transformer.transform(new StreamSource(xmlURI), saxResult);
            fos.close(); // must close ostreams we own
            reporter.logStatusMsg("Closed result stream from loggingSaxResult, about to check result");
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(dtdFileInfo.goldName), 
                              "transform loggingSaxResult into: " + outNames.currentName());
            reporter.logWarningMsg("//@todo validate that llh got lexical events: Bugzilla#888");
            reporter.logWarningMsg("//@todo validate that lch got content events");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Basic functionality1 of SAXResults threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Basic functionality1 of SAXResults");
        }
        try
        {
            // Same as above, with identityTransformer
            reporter.logInfoMsg("TransformerHandler.setResult(StreamResult)");
            TransformerHandler tHandler = saxFactory.newTransformerHandler();
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            tHandler.setResult(realResult);

            SAXResult saxResult = new SAXResult();
            reporter.logInfoMsg("loggingSaxResult.setHandler(loggingContentHandler)");
            LoggingContentHandler lch = new LoggingContentHandler((Logger)reporter);
            lch.setDefaultHandler(tHandler);
            saxResult.setHandler(lch);

            reporter.logInfoMsg("loggingSaxResult.setLexicalHandler(loggingLexicalHandler)");
            LoggingLexicalHandler llh = new LoggingLexicalHandler((Logger)reporter);
            llh.setDefaultHandler(tHandler);
            saxResult.setLexicalHandler(llh);
            
            // Do an identityTransform to this result
            Transformer identityTransformer = TransformerFactory.newInstance().newTransformer();
            identityTransformer.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("identityTransform(new StreamSource(" + xmlURI + "), loggingSaxResult)");
            identityTransformer.transform(new StreamSource(xmlURI), saxResult);
            fos.close(); // must close ostreams we own
            reporter.logStatusMsg("Closed result stream from loggingSaxResult, about to check result");
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(dtdFileInfo.xmlName), 
                              "identity transform loggingSaxResult into: " + outNames.currentName());
            reporter.logWarningMsg("//@todo validate that llh got lexical events: Bugzilla#888");
            reporter.logWarningMsg("//@todo validate that lch got content events");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Basic functionality2 of SAXResults threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Basic functionality2 of SAXResults");
        }
        try
        {
            // Validate a DOMSource to a logging SAXResult, as above 
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            reporter.logTraceMsg("docBuilder.parse(" + xmlURI + ")");
            Node xmlNode = docBuilder.parse(new InputSource(xmlURI));


            // Have an actual handler that does the physical output
            TransformerHandler tHandler = saxFactory.newTransformerHandler();
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            Result realResult = new StreamResult(fos);
            tHandler.setResult(realResult);

            SAXResult saxResult = new SAXResult();
            // Add a contentHandler that logs out info about the 
            //  transform, and that passes-through calls back 
            //  to the original tHandler
            LoggingContentHandler lch = new LoggingContentHandler((Logger)reporter);
            lch.setDefaultHandler(tHandler);
            saxResult.setHandler(lch);

            // Add a lexicalHandler that logs out info about the 
            //  transform, and that passes-through calls back 
            //  to the original tHandler
            LoggingLexicalHandler llh = new LoggingLexicalHandler((Logger)reporter);
            llh.setDefaultHandler(tHandler);
            saxResult.setLexicalHandler(llh);
            
            // Just do a normal transform to this result
            Transformer transformer = streamTemplates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("transform(new DOMSource(" + xmlURI + "), loggingSaxResult)");
            transformer.transform(new DOMSource(xmlNode), saxResult);
            fos.close(); // must close ostreams we own
            reporter.logStatusMsg("Closed result stream from loggingSaxResult, about to check result");
            fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(dtdFileInfo.goldName), 
                              "transform DOM-loggingSaxResult into: " + outNames.currentName());
            reporter.logWarningMsg("//@todo validate that llh got lexical events: Bugzilla#888");
            reporter.logWarningMsg("//@todo validate that lch got content events");
        }
        catch (Throwable t)
        {
            reporter.checkFail("Basic functionality3 of SAXResults threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "Basic functionality3 of SAXResults");
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by SAXResultAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        SAXResultAPITest app = new SAXResultAPITest();
        app.doMain(args);
    }
}
