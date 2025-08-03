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
 * TransformerFactoryAPITest.java
 *
 */
package org.apache.qetest.trax;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;

import org.w3c.dom.Document;

//-------------------------------------------------------------------------

/**
 * API Coverage test for TransformerFactory class of TRAX.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class TransformerFactoryAPITest extends FileBasedTest
{

    /**
     * Provides nextName(), currentName() functionality for tests 
     * that may produce any number of output files.
     */
    protected OutputNameManager outNames;

    /** Basic identity test file for newTransformer, newTemplates.  */
    protected XSLTestfileInfo testFileInfo = new XSLTestfileInfo();

    /** Embedded identity test file for getEmbedded....  */
    protected XSLTestfileInfo embeddedFileInfo = new XSLTestfileInfo();

    /** Modern test for testCase6.  */
    protected XSLTestfileInfo embeddedCSSFileInfo = new XSLTestfileInfo();

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Cached system property.  */
    protected String cachedSysProp = null;

    /** System property name, from TransformerFactory (why is it private there?).  */
    public static final String defaultPropName = "javax.xml.transform.TransformerFactory";

    /** System property name for Xalan-J 2.x impl.  */
    public static final String XALAN_CLASSNAME = "org.apache.xalan.processor.TransformerFactoryImpl";

    /** Just initialize test name, comment, numTestCases. */
    public TransformerFactoryAPITest()
    {
        numTestCases = 7;  // REPLACE_num
        testName = "TransformerFactoryAPITest";
        testComment = "API Coverage test for TransformerFactory class of TRAX";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files,
     * cache system property javax.xml.transform.TransformerFactory.  
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

        testFileInfo.inputName = QetestUtils.filenameToURL(testBasePath + "identity.xsl");
        testFileInfo.xmlName = QetestUtils.filenameToURL(testBasePath + "identity.xml");
        testFileInfo.goldName = goldBasePath + "identity.out";

        embeddedFileInfo.xmlName = QetestUtils.filenameToURL(testBasePath + "embeddedIdentity.xml");
        embeddedFileInfo.goldName = goldBasePath + "embeddedIdentity.out";

        embeddedCSSFileInfo.xmlName = testBasePath + "TransformerFactoryAPIModern.xml"; // just the local path\filename
        // embeddedCSSFileInfo.optionalName = testBasePath + "TransformerFactoryAPIModern.css"; // other file required by XML file

        // Cache the system property; is reset in testFileClose
        cachedSysProp = System.getProperty(defaultPropName);
        return true;
    }


    /**
     * Cleanup this test - reset cached system property.  
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileClose(Properties p)
    {
        if (cachedSysProp == null)
            System.getProperties().remove(defaultPropName);
        else
            System.getProperties().put(defaultPropName, cachedSysProp);
        return true;
    }


    /**
     * Coverage tests for factory pattern API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase1()
    {
        reporter.testCaseInit("Coverage tests for factory pattern API's");

        // protected TransformerFactory(){} not normally accessible - not tested
        // public static TransformerFactory newInstance()
        TransformerFactory factory = null;
        // test when system property is user-set (i.e. whatever we started with)
        try
        {
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            // No verification: just log what happened for user to see
            reporter.logStatusMsg("factory.newInstance() is: " + factory.toString());
        }
        catch (Throwable t)
        {
            reporter.logStatusMsg("factory.newInstance() threw: " + t.toString());
        }

        // test when system property is null
        try
        {
            System.getProperties().remove(defaultPropName);
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            reporter.logStatusMsg("factory.newInstance() is: " + factory.toString());
        }
        catch (Throwable t)
        {
            reporter.logStatusMsg("factory.newInstance() threw: " + t.toString());
        }

        // test when system property is a bogus name
        try
        {
            System.getProperties().put(defaultPropName, "this.class.does.not.exist");
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            reporter.checkFail("factory.newInstance() with bogus name got: " + factory.toString());
        }
        catch (Throwable t)
        {
            reporter.checkPass("factory.newInstance() with bogus name properly threw: " + t.toString());
            // Could also verify specific type of exception
        }

        // test when system property is another kind of classname
        try
        {
            System.getProperties().put(defaultPropName, "java.lang.String");
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            reporter.checkFail("factory.newInstance() with bogus class got: " + factory.toString());
        }
        catch (Throwable t)
        {
            reporter.checkPass("factory.newInstance() with bogus class properly threw: " + t.toString());
            // Could also verify specific type of exception
        }

        // Reset the system property to what was cached previously
        try
        {
            // This should come last so it will stay set for the rest of the test
            // Note: this needs review, since in the future we may 
            //  not guaruntee order of testCase execution!
            if (cachedSysProp == null)
                System.getProperties().remove(defaultPropName);
            else
                System.getProperties().put(defaultPropName, cachedSysProp);

            reporter.logStatusMsg("System property (default) " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            reporter.checkPass("factory.newInstance() of default impl is: " + factory.toString());
        }
        catch (Throwable t)
        {
            reporter.checkFail("factory.newInstance() of default impl threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "factory.newInstance() of default impl threw:");
        }

        reporter.logStatusMsg("@todo code coverage for findFactory() method");

        reporter.testCaseClose();
        return true;
    }


    /**
     * Coverage tests for newTransformer() API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Coverage tests for newTransformer() API's");
        TransformerFactory factory = null;

        try
        {
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            Transformer identityTransformer = factory.newTransformer();
            reporter.check((identityTransformer != null), true, "newTransformer() APICoverage");

            if (factory.getFeature(StreamSource.FEATURE))
            {
                Transformer transformer = factory.newTransformer(new StreamSource(testFileInfo.inputName));
                reporter.check((transformer != null), true, "newTransformer(Source) APICoverage");
            }
            else
                reporter.logErrorMsg("NOTE: getFeature(StreamSource.FEATURE) false, can't test newTransformer(Source)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("newTransformer() tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "newTransformer() tests threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Coverage tests for newTemplates() API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("Coverage tests for newTemplates() API's");
        TransformerFactory factory = null;

        try
        {
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            if (factory.getFeature(StreamSource.FEATURE))
            {
                Templates templates = factory.newTemplates(new StreamSource(testFileInfo.inputName));
                reporter.check((templates != null), true, "newTemplates(Source) APICoverage");
            }
            else
                reporter.logErrorMsg("NOTE: getFeature(StreamSource.FEATURE) false, can't test newTemplates(Source)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("newTemplates() tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "newTemplates() tests threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Coverage tests for getAssociatedStylesheet() API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Coverage tests for getAssociatedStylesheet() API's");
        TransformerFactory factory = null;

        try
        {
            reporter.logStatusMsg("System property " + defaultPropName 
                                  + " is: " + System.getProperty(defaultPropName));
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            String media= null;     // currently untested
            String title = null;    // currently untested
            String charset = null;  // currently untested

            // May throw IOException
            FileOutputStream resultStream = new FileOutputStream(outNames.nextName());

            // Get the xml-stylesheet and process it
            Source stylesheet = factory.getAssociatedStylesheet(new StreamSource(embeddedFileInfo.xmlName), 
                                                                media, title, charset);
            reporter.check((stylesheet instanceof Source), true, "getAssociatedStylesheet returns instanceof Source");
            reporter.check((null != stylesheet), true, "getAssociatedStylesheet returns a non-null Source");

            Transformer transformer = factory.newTransformer(stylesheet);
            transformer.setErrorListener(new DefaultErrorHandler());
            reporter.logCriticalMsg("SPR SCUU4RXTSQ occours in below line, even though check reports pass (missing linefeed)");
            transformer.transform(new StreamSource(embeddedFileInfo.xmlName), new StreamResult(resultStream));
            resultStream.close();   // just in case
            int result = fileChecker.check(reporter, 
                                           new File(outNames.currentName()), 
                                           new File(embeddedFileInfo.goldName), 
                                          "transform of getAssociatedStylesheet into " + outNames.currentName());
            if (result == Logger.FAIL_RESULT)
                reporter.logInfoMsg("transform of getAssociatedStylesheet... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "getAssociatedStylesheet() tests threw:");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Coverage tests for get/setURIResolver(), get/setErrorListener() API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase5()
    {
        reporter.testCaseInit("Coverage tests for get/setURIResolver(), get/setErrorListener() API's");
        TransformerFactory factory = null;
        reporter.logStatusMsg("System property " + defaultPropName 
                              + " is: " + System.getProperty(defaultPropName));
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            URIResolver URIRes = factory.getURIResolver();
            reporter.logInfoMsg("factory.getURIResolver() default is: " + URIRes);
            
            LoggingURIResolver loggingURIRes = new LoggingURIResolver(reporter);
            factory.setURIResolver(loggingURIRes);
            reporter.checkObject(factory.getURIResolver(), loggingURIRes, "set/getURIResolver API coverage");
            factory.setURIResolver(null);
            if (factory.getURIResolver() == null)
            {
                reporter.checkPass("setURIResolver(null) is OK");
            }
            else
            {
                reporter.checkFail("setURIResolver(null) not OK, is: " + factory.getURIResolver());
            }
        }
        catch (Throwable t)
        {
            reporter.checkErr("Coverage of get/setURIResolver threw: " + t.toString());
            reporter.logThrowable(reporter.STATUSMSG, t, "Coverage of get/setURIResolver threw:");
        }
        reporter.logStatusMsg("//@todo feature testing for URIResolver: see URIResolverTest.java");

        try
        {
            factory = TransformerFactory.newInstance();
            ErrorListener errListener = factory.getErrorListener();
            if (errListener == null)
            {
                reporter.checkFail("getErrorListener() non-null by default");
            }
            else
            {
                reporter.checkPass("getErrorListener() non-null by default, is: " + errListener);
            }
            
            LoggingErrorListener loggingErrListener = new LoggingErrorListener(reporter);
            factory.setErrorListener(loggingErrListener);
            reporter.checkObject(factory.getErrorListener(), loggingErrListener, "set/getErrorListener API coverage(1)");
            try
            {
                factory.setErrorListener(null);                
                reporter.checkFail("setErrorListener(null) worked, should have thrown exception");
            }
            catch (IllegalArgumentException iae)
            {
                reporter.checkPass("setErrorListener(null) properly threw: " + iae.toString());
            }
            // Verify the previous ErrorListener is still set
            reporter.checkObject(factory.getErrorListener(), loggingErrListener, "set/getErrorListener API coverage(2)");
        }
        catch (Throwable t)
        {
            reporter.checkErr("Coverage of get/setErrorListener threw: " + t.toString());
            reporter.logThrowable(reporter.STATUSMSG, t, "Coverage of get/setErrorListener threw:");
        }
        reporter.logStatusMsg("//@todo feature testing for ErrorListener: see ErrorListenerAPITest.java, ErrorListenerTest.java");

        reporter.testCaseClose();
        return true;
    }


    /**
     * Miscellaneous tests.
     * Bug/tests submitted by Bhakti.Mehta@eng.sun.com
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase6()
    {
        reporter.testCaseInit("Miscellaneous getAssociatedStylesheets tests");
        TransformerFactory factory = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            // Note that embeddedCSSFileInfo has the following PI:
            //  <?xml-stylesheet href="TransformerFactoryAPIModern.css" title="Modern" media="screen" type="text/css"?>
            // Which we should return null from, since we only support 
            //  types that are properly mapped to xslt:
            //  text/xsl, text/xml, and application/xml+xslt
            //  see also EmbeddedStylesheetTest
            Document doc = db.parse(new File(embeddedCSSFileInfo.xmlName));
            DOMSource domSource = new DOMSource(doc);

            // TransformerFactory01.check01()
            try 
            {
                reporter.logInfoMsg("About to getAssociatedStylesheet(domsource-w/outsystemid, screen,Modern,null)");
                Source s = factory.getAssociatedStylesheet(domSource,"screen","Modern",null);
                reporter.check((null == s), true, "getAssociatedStylesheet returns null Source for text/css");
            }
            catch (Throwable t)
            {
                reporter.checkFail("TransformerFactory01.check01a threw: " + t.toString());
                reporter.logThrowable(reporter.STATUSMSG, t, "TransformerFactory01.check01a threw");
            }
            try 
            {
                domSource.setSystemId(QetestUtils.filenameToURL(embeddedCSSFileInfo.xmlName));
                reporter.logInfoMsg("About to getAssociatedStylesheet(domsource-w/systemid, screen,Modern,null)");
                Source s = factory.getAssociatedStylesheet(domSource,"screen","Modern",null);
                reporter.check((null == s), true, "getAssociatedStylesheet returns null Source for text/css");
            }
            catch (Throwable t)
            {
                reporter.checkFail("TransformerFactory01.check01b threw: " + t.toString());
                reporter.logThrowable(reporter.STATUSMSG, t, "TransformerFactory01.check01b threw");
            }

            // public void TransformerFactory02.check01(){
            try 
            {
                StreamSource ss = new StreamSource(new FileInputStream(embeddedCSSFileInfo.xmlName));
                reporter.logInfoMsg("About to getAssociatedStylesheet(streamsource-w/outsystemid, screen,Modern,null)");
                Source s = factory.getAssociatedStylesheet(ss,"screen","Modern",null);
                reporter.check((null == s), true, "getAssociatedStylesheet returns null Source for text/css");
            }
            catch (Throwable t)
            {
                reporter.checkFail("TransformerFactory02.check01a threw: " + t.toString());
                reporter.logThrowable(reporter.STATUSMSG, t, "TransformerFactory02.check01a threw");
            }
            try 
            {
                StreamSource ss = new StreamSource(new FileInputStream(embeddedCSSFileInfo.xmlName));
                ss.setSystemId(QetestUtils.filenameToURL(embeddedCSSFileInfo.xmlName));
                reporter.logInfoMsg("About to getAssociatedStylesheet(streamsource-w/systemid, screen,Modern,null)");
                Source s = factory.getAssociatedStylesheet(ss,"screen","Modern",null);
                reporter.check((null == s), true, "getAssociatedStylesheet returns null Source for text/css");
            }
            catch (Throwable t)
            {
                reporter.checkFail("TransformerFactory02.check01b threw: " + t.toString());
                reporter.logThrowable(reporter.STATUSMSG, t, "TransformerFactory02.check01b threw");
            }
        }
        catch (Throwable t)
        {
            reporter.checkErr("Miscellaneous getAssociatedStylesheets tests threw: " + t.toString());
            reporter.logThrowable(reporter.STATUSMSG, t, "Miscellaneous getAssociatedStylesheets tests threw");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Coverage tests for set/getFeature, set/getAttribute API's.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase7()
    {
        reporter.testCaseInit("Coverage tests for set/getFeature, set/getAttribute API's");
        // This test case should be JAXP-generic, and must not rely on Xalan-J 2.x functionality
        reporter.logInfoMsg("Note: only simple validation: most are negative tests");
        TransformerFactory factory = null;
        final String BOGUS_NAME = "fnord:this/feature/does/not/exist";

        try
        {
            factory = TransformerFactory.newInstance();
            try
            {
                reporter.logStatusMsg("Calling: factory.getFeature(BOGUS_NAME)");
                boolean b = factory.getFeature(BOGUS_NAME);
                reporter.checkPass("factory.getFeature(BOGUS_NAME) did not throw exception");
                reporter.logStatusMsg("factory.getFeature(BOGUS_NAME) = " + b);
            }
            catch (IllegalArgumentException iae1)
            {
                // This isn't documented, but is what I might expect
                reporter.checkPass("factory.getFeature(BOGUS_NAME) threw expected IllegalArgumentException");
            }

            try
            {
                reporter.logStatusMsg("Calling: factory.setAttribute(BOGUS_NAME,...)");
                factory.setAttribute(BOGUS_NAME, "on");
                reporter.checkFail("factory.setAttribute(BOGUS_NAME,...) did not throw expected exception");
            }
            catch (IllegalArgumentException iae2)
            {
                reporter.checkPass("factory.setAttribute(BOGUS_NAME,...) threw expected IllegalArgumentException");
            }

            try
            {
                reporter.logStatusMsg("Calling: factory.getAttribute(BOGUS_NAME)");
                Object o = factory.getAttribute(BOGUS_NAME);
                reporter.checkFail("factory.getAttribute(BOGUS_NAME) did not throw expected exception");
                reporter.logStatusMsg("factory.getAttribute(BOGUS_NAME) = " + o);
            }
            catch (IllegalArgumentException iae3)
            {
                reporter.checkPass("factory.getAttribute(BOGUS_NAME) threw expected IllegalArgumentException");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail("getFeature/Attribute() tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "getFeature/Attribute() tests threw");
        }

        try
        {
            reporter.logWarningMsg("Note testing assumption: all factories must support Streams");
            factory = TransformerFactory.newInstance();
            reporter.logStatusMsg("Calling: factory.getFeature(StreamSource.FEATURE)");
            boolean b = factory.getFeature(StreamSource.FEATURE);
            reporter.check(b, true, "factory.getFeature(StreamSource.FEATURE)");

            reporter.logStatusMsg("Calling: factory.getFeature(StreamResult.FEATURE)");
            b = factory.getFeature(StreamResult.FEATURE);
            reporter.check(b, true, "factory.getFeature(StreamResult.FEATURE)");
        }
        catch (Throwable t)
        {
            reporter.checkFail("getFeature/Attribute()2 tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "getFeature/Attribute()2 tests threw");
        }

        try
        {
            reporter.logStatusMsg("Calling: factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING)");
            factory = TransformerFactory.newInstance();
            try
            {
                // All implementations are required to support the XMLConstants.FEATURE_SECURE_PROCESSING feature
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                boolean b = factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING);
                reporter.check(b, true, "factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)");
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
                b = factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING);
                reporter.check(b, false, "factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)");
                
            }
            catch (TransformerConfigurationException tce)
            {
                reporter.checkFail("set/getFeature(XMLConstants.FEATURE_SECURE_PROCESSING) tests threw: " + tce.toString());
                reporter.logThrowable(reporter.ERRORMSG, tce, "set/getFeature(XMLConstants.FEATURE_SECURE_PROCESSING) tests threw");
            }
            
            try
            {
                factory.setFeature(BOGUS_NAME, true);
                reporter.checkFail("factory.setFeature(BOGUS_NAME) did not throw expected exception");
                
            }
            catch (TransformerConfigurationException tce)
            {
                reporter.checkPass("factory.setFeature(BOGUS_NAME) threw expected TransformerConfigurationException");
            }
            
            try
            {
                factory.setFeature(null, true);
                reporter.checkFail("factory.setFeature(null, true) did not throw expected exception");
            }
            catch (NullPointerException npe)
	    {
	        reporter.checkPass("factory.setFeature(null, true) threw expected NullPointerException");
            }
                
        }
        catch (Throwable t)
        {
            reporter.checkFail("set/getFeature() tests threw: " + t.toString());
            reporter.logThrowable(reporter.ERRORMSG, t, "set/getFeature() tests threw");
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
        return ("Common [optional] options supported by TransformerFactoryAPITest:\n"
                + "-transformerFactory <FQCN of TransformerFactoryImpl; default Xalan 2.x>\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        TransformerFactoryAPITest app = new TransformerFactoryAPITest();
        app.doMain(args);
    }
}
