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

package org.apache.qetest.trax;

import android.platform.test.annotations.FlakyTest;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.Datalet;
import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.TestletImpl;
import org.apache.qetest.xsl.XSLTestfileInfo;
import org.apache.xml.utils.DefaultErrorHandler;
import org.junit.Test;

//-------------------------------------------------------------------------

/**
 * Basic API coverage test for the Transformer class of TRAX.
 * This test focuses on coverage testing for the API's, and 
 * very brief functional testing.  Also see tests in the 
 * trax\sax, trax\dom, and trax\stream directories for specific 
 * coverage of Transformer API's in those usage cases.
 * @author shane_curcuru@lotus.com
 */
public class TransformerAPITest extends FileBasedTest
{

    /** Cheap-o filename for various output files.  */
    protected OutputNameManager outNames;

    /** Cheap-o filename set for general API tests. */
    protected XSLTestfileInfo simpleTest = new XSLTestfileInfo();

    /** TransformerAPIParam.xsl used for set/getParameter related tests  */
    protected XSLTestfileInfo paramTest = new XSLTestfileInfo();

    /** Parameter names from TransformerAPIParam.xsl  */
    public static final String PARAM1S = "param1s";
    public static final String PARAM2S = "param2s";
    public static final String PARAM3S = "param3s";
    public static final String PARAM1N = "param1n";
    public static final String PARAM2N = "param2n";
    public static final String PARAM3N = "param3n";

    /** TransformerAPIOutputFormat.xsl used for set/getOutputFormat related tests  */
    protected XSLTestfileInfo outputFormatTest = new XSLTestfileInfo();

    /** Just goldName for outputFormatTest with UTF-8 */
    protected String outputFormatTestUTF8 = null;

    /** TransformerAPIHTMLFormat.xsl.xsl used for set/getOutputFormat related tests  */
    protected XSLTestfileInfo htmlFormatTest = new XSLTestfileInfo();

    /** Known outputFormat values from TransformerAPIOutputFormat.xsl  */
    public static final String METHOD_VALUE = "xml";
    public static final String VERSION_VALUE ="123.45";
    public static final String ENCODING_VALUE ="UTF-16";
    public static final String STANDALONE_VALUE = "yes";
    public static final String DOCTYPE_PUBLIC_VALUE = "this-is-doctype-public";
    public static final String DOCTYPE_SYSTEM_VALUE = "this-is-doctype-system";
    public static final String CDATA_SECTION_ELEMENTS_VALUE = "cdataHere";
    public static final String INDENT_VALUE  =  "yes";
    public static final String MEDIA_TYPE_VALUE = "text/test/xml";
    public static final String OMIT_XML_DECLARATION_VALUE = "yes";

    /** Cheap-o filename set(s) for multiple transform tests. */
    protected XSLTestfileInfo multiTest = new XSLTestfileInfo();
    protected XSLTestfileInfo multi2Test = new XSLTestfileInfo();

    /** Subdir name under test\tests\api for files.  */
    public static final String TRAX_SUBDIR = "trax";

    /** Default ctor initializes test name, comment, numTestCases. */
    public TransformerAPITest()
    {

        numTestCases = 6;  // REPLACE_num
        testName = "TransformerAPITest";
        testComment = "Basic API coverage test for the Transformer class";
    }

    /**
     * Initialize this test - Set names of xml/xsl test files, cache system property.  
     *
     * @param p Properties to initialize with (may be unused)
     * @return false if test should be aborted, true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {

        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_SUBDIR);

        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: "
                                   + outSubDir);

        outNames = new OutputNameManager(outputDir + File.separator + TRAX_SUBDIR
                                         + File.separator + testName, ".out");

        // We assume inputDir=...tests\api, and use the trax subdir
        //  also assume inputDir, etc. exist already
        String testBasePath = inputDir + File.separator + TRAX_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir + File.separator + TRAX_SUBDIR
                              + File.separator;

        simpleTest.xmlName = QetestUtils.filenameToURL(testBasePath + "identity.xml");
        simpleTest.inputName = QetestUtils.filenameToURL(testBasePath + "identity.xsl");
        simpleTest.goldName = goldBasePath + "identity.out";

        paramTest.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIParam.xml");
        paramTest.inputName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIParam.xsl");
        paramTest.goldName = goldBasePath + "TransformerAPIParam.out";
        
        outputFormatTest.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIOutputFormat.xml");
        outputFormatTest.inputName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIOutputFormat.xsl");
        outputFormatTest.goldName = goldBasePath + "TransformerAPIOutputFormatUTF16.out";
        outputFormatTestUTF8 = goldBasePath + "TransformerAPIOutputFormatUTF8.out";

        htmlFormatTest.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIHTMLFormat.xml");
        htmlFormatTest.inputName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIHTMLFormat.xsl");
        htmlFormatTest.goldName = goldBasePath + "TransformerAPIHTMLFormat.out";

        multiTest.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIVar.xml");
        multiTest.inputName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIVar.xsl");
        multiTest.goldName = goldBasePath + "TransformerAPIVar.out";

        multi2Test.xmlName = QetestUtils.filenameToURL(testBasePath + "TransformerAPIVar2.xml");
        multi2Test.goldName = goldBasePath + "TransformerAPIVar2.out";
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(StreamSource.FEATURE)
                  && tf.getFeature(StreamResult.FEATURE)))
            {   // The rest of this test relies on Streams only
                reporter.logErrorMsg("Streams not supported! Some tests may be invalid!");
            }
        }
        catch (Exception e)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, e,
                                  "Problem creating factory; Some tests may be invalid!");
        }

        return true;
    }


    /**
     * TRAX Transformer: cover basic get/setParameter(s) APIs.
     * See {@link ParameterTest ParameterTest} for more 
     * functional test coverage on setting different kinds 
     * and types of parameters, etc.
     * 
     * @return false if we should abort the test
     */
    public boolean testCase1()
    {

        reporter.testCaseInit(
            "TRAX Transformer: cover basic get/setParameter(s) APIs");

        TransformerFactory factory = null;
        Templates templates = null;
        Transformer transformer = null;
        Transformer identityTransformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            identityTransformer = factory.newTransformer();
            identityTransformer.setErrorListener(new DefaultErrorHandler());
            templates = factory.newTemplates(new StreamSource(paramTest.inputName));
        }
        catch (Exception e)
        {
            reporter.checkFail("Problem creating Templates; cannot continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, e, 
                                  "Problem creating Templates; cannot continue testcase");
            return true;
        }
        // Note: large number of try...catch blocks so that early 
        // exceptions won't blow out the whole testCase
        try
        {
            // See what the default 'identity' transform has by default
            Object tmp = identityTransformer.getParameter("This-param-does-not-exist");
            reporter.checkObject(tmp, null, "This-param-does-not-exist is null by default identityTransformer");
            // Can you set properties on this transformer?
            identityTransformer.setParameter("foo", "bar");
            tmp = identityTransformer.getParameter("foo");
            if (tmp == null)
            {
                reporter.checkFail("identityTransformer set/getParameter is:" + tmp);
            }
            else
            {
                reporter.checkString((String)tmp, "bar", "identityTransformer set/getParameter value: " + tmp);
                reporter.check((tmp instanceof String), true, "identityTransformer set/getParameter datatype");
            }
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(1) with identity parameters");
            reporter.checkFail("Problem(1) with identity parameters");
        }

        try
        {
            transformer = templates.newTransformer(); // may throw TransformerConfigurationException
            transformer.setErrorListener(new DefaultErrorHandler());
            // Default Transformer should not have any parameters..
            Object tmp = transformer.getParameter("This-param-does-not-exist");
            reporter.checkObject(tmp, null, "This-param-does-not-exist is null by default");
            //  .. including params in the stylesheet
            tmp = transformer.getParameter(PARAM1S);
            if (tmp == null)
            {   // @todo should use checkObject instead of this if... construct
                reporter.checkPass(PARAM1S + " is null by default");
            }
            else
            {
                reporter.checkFail(PARAM1S + " is " + tmp + " by default");
            }

            // Verify simple set/get of a single parameter - String
            transformer.setParameter(PARAM1S, "new value1s");
            reporter.logTraceMsg("Just reset " + PARAM1S + " to new value1s");
            tmp = transformer.getParameter(PARAM1S);    // SPR SCUU4QWTVZ - returns an XString - fixed
            if (tmp == null)
            {
                reporter.checkFail(PARAM1S + " is still set to null!");
            }
            else
            {   // Validate SPR SCUU4QWTVZ - should return the same type you set
                if (tmp instanceof String)
                {
                    reporter.checkString((String)tmp, "new value1s", PARAM1S + " is now set to ?" + tmp + "?");
                }
                else
                {
                    reporter.checkFail(PARAM1S + " is now ?" + tmp + "?, isa " + tmp.getClass().getName());
                }
            }
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(2) set/getParameter testing");
            reporter.checkFail("Problem(2) set/getParameter testing");
        }

        try
        {
            transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            // Verify simple set/get of a single parameter - Integer
            transformer.setParameter(PARAM3S, new Integer(1234));
            reporter.logTraceMsg("Just set " + PARAM3S + " to Integer(1234)");
            Object tmp = transformer.getParameter(PARAM3S);    // SPR SCUU4QWTVZ - returns an XObject - fixed
            if (tmp == null)
            {
                reporter.checkFail(PARAM3S + " is still set to null!");
            }
            else
            {   // Validate SPR SCUU4QWTVZ - should return the same type you set
                if (tmp instanceof Integer)
                {
                    reporter.checkObject(tmp, new Integer(1234), PARAM3S + " is now set to ?" + tmp + "?");
                }
                else
                {
                    reporter.checkFail(PARAM3S + " is now ?" + tmp + "?, isa " + tmp.getClass().getName());
                }
            }

            // Verify simple re-set/get of a single parameter - new Integer
            transformer.setParameter(PARAM3S, new Integer(99));   // SPR SCUU4R3JGY - can't re-set
            reporter.logTraceMsg("Just reset " + PARAM3S + " to new Integer(99)");
            tmp = null;
            tmp = transformer.getParameter(PARAM3S);
            if (tmp == null)
            {
                reporter.checkFail(PARAM3S + " is still set to null!");
            }
            else
            {   // Validate SPR SCUU4QWTVZ - should return the same type you set
                if (tmp instanceof Integer)
                {
                    reporter.checkObject(tmp, new Integer(99), PARAM3S + " is now set to ?" + tmp + "?");
                }
                else
                {
                    reporter.checkFail(PARAM3S + " is now ?" + tmp + "?, isa " + tmp.getClass().getName());
                }
            }

            // Verify simple re-set/get of a single parameter - now a new String
            transformer.setParameter(PARAM3S, "new value3s");
            reporter.logTraceMsg("Just reset " + PARAM3S + " to new value3s");
            tmp = null;
            tmp = transformer.getParameter(PARAM3S);
            if (tmp == null)
            {
                reporter.checkFail(PARAM3S + " is still set to null!");
            }
            else
            {   // Validate SPR SCUU4QWTVZ - should return the same type you set
                if (tmp instanceof String)
                {
                    reporter.checkString((String)tmp, "new value3s", PARAM3S + " is now set to ?" + tmp + "?");
                }
                else
                {
                    reporter.checkFail(PARAM3S + " is now ?" + tmp + "?, isa " + tmp.getClass().getName());
                }
            }
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(3) set/getParameters testing");
            reporter.checkFail("Problem(3) set/getParameters testing");
        }

        try
        {
            transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            transformer.setParameter(PARAM1S, "'test-param-1s'"); // note single quotes
            transformer.setParameter(PARAM1N, new Integer(1234));
            // Verify basic params actually affect transformation
            //   Use the transformer we set the params onto above!
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            if (doTransform(transformer,
                            new StreamSource(paramTest.xmlName), 
                            new StreamResult(fos)))
            {
                fos.close(); // must close ostreams we own
                // @todo should update goldFile!
                if (Logger.PASS_RESULT
                    != fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(paramTest.goldName), 
                        "transform with param1s,param1n into: " + outNames.currentName())
                   )
                    reporter.logInfoMsg("transform with param1s,param1n failure reason:" + fileChecker.getExtendedInfo());
            }
            String gotStr = (String)transformer.getParameter(PARAM1S);
            reporter.check(gotStr, "'test-param-1s'", 
                           PARAM1S + " is still set after transform to ?" + gotStr + "?");
            Integer gotInt = (Integer)transformer.getParameter(PARAM1N);
            reporter.checkInt(gotInt.intValue(), 1234, 
                           PARAM1N + " is still set after transform to ?" + gotInt + "?");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(4) with parameter transform");
            reporter.checkFail("Problem(4) with parameter transform");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * API coverage test of Transformer.set/getOutputProperty()
     * See {@link OutputPropertiesTest} for more coverage on setting 
     * different kinds of outputs, etc.
     * 
     * @return false if we should abort the test
     */
    public boolean testCase2()
    {
        //@todo I can't decide how to split tests up between 
        //  testCase2/testCase3 - they really should be reorganized
        reporter.testCaseInit("API coverage test of Transformer.set/getOutputProperty()");
        TransformerFactory factory = null;
        Templates outputTemplates = null;
        Transformer outputTransformer = null;
        Templates htmlTemplates = null;
        Transformer htmlTransformer = null;
        Templates identityTemplates = null;
        Transformer identityTransformer = null; // an .xsl file defining an identity transform
        Transformer defaultTransformer = null; // the default 'identity' transform
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            outputTemplates = factory.newTemplates(new StreamSource(outputFormatTest.inputName));
            outputTransformer = outputTemplates.newTransformer();
            outputTransformer.setErrorListener(new DefaultErrorHandler());

            htmlTemplates = factory.newTemplates(new StreamSource(htmlFormatTest.inputName));
            htmlTransformer = htmlTemplates.newTransformer();
            htmlTransformer.setErrorListener(new DefaultErrorHandler());

            identityTemplates = factory.newTemplates(new StreamSource(simpleTest.inputName));
            identityTransformer = identityTemplates.newTransformer();
            identityTransformer.setErrorListener(new DefaultErrorHandler());

            defaultTransformer = factory.newTransformer();
            defaultTransformer.setErrorListener(new DefaultErrorHandler());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating Templates; cannot continue");
            reporter.logThrowable(reporter.ERRORMSG, t, 
                                  "Problem creating Templates; cannot continue");
            return true;
        }

        try
        {
            // See what the default 'identity' transform has by default
            Properties defaultProps = defaultTransformer.getOutputProperties(); // SPR SCUU4RXQYH throws npe
            reporter.logHashtable(reporter.STATUSMSG, defaultProps, 
                                  "default defaultTransformer.getOutputProperties()");

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("xml".equals(defaultProps.getProperty(OutputKeys.METHOD))), true, "defaultTransformer.op.getProperty(" 
                           + OutputKeys.METHOD + ") is default value, act: " + defaultProps.getProperty(OutputKeys.METHOD));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == defaultProps.get(OutputKeys.METHOD)), true, "defaultTransformer.op.get(" 
                           + OutputKeys.METHOD + ") is null value, act: " + defaultProps.get(OutputKeys.METHOD));

            // Can you set properties on this transformer?
            defaultTransformer.setOutputProperty(OutputKeys.METHOD, "text");
            reporter.logTraceMsg("Just defaultTransformer setOutputProperty(method,text)");
            String tmp = defaultTransformer.getOutputProperty(OutputKeys.METHOD); // SPR SCUU4R3JPH - throws npe
            reporter.check(tmp, "text", "defaultTransformer set/getOutputProperty, is ?" + tmp + "?");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(1) with default output property");
            reporter.checkFail("Problem(1) with default output property", "SCUU4RXQYH");
        }

        try
        {
            // See what the our .xsl file 'identity' transform has
            Properties identityProps = identityTransformer.getOutputProperties();
            reporter.logHashtable(reporter.STATUSMSG, identityProps, 
                                  "default identityTransformer.getOutputProperties()");

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("xml".equals(identityProps.getProperty(OutputKeys.METHOD))), true, "identityTransformer.op.getProperty(" 
                           + OutputKeys.METHOD + ") is default value, act: " + identityProps.getProperty(OutputKeys.METHOD));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == identityProps.get(OutputKeys.METHOD)), true, "identityTransformer.op.get(" 
                           + OutputKeys.METHOD + ") is null value, act: " + identityProps.get(OutputKeys.METHOD));

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("no".equals(identityProps.getProperty(OutputKeys.INDENT))), true, "identityTransformer.op.getProperty(" 
                           + OutputKeys.INDENT + ") is default value, act: " + identityProps.getProperty(OutputKeys.INDENT));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == (identityProps.get(OutputKeys.INDENT))), true, "identityTransformer.op.get(" 
                           + OutputKeys.INDENT + ") is default value, act: " + identityProps.get(OutputKeys.INDENT));

            // Can you set properties on this transformer?
            defaultTransformer.setOutputProperty(OutputKeys.METHOD, "text");
            reporter.logTraceMsg("Just identityTransformer setOutputProperty(method,text)");
            String tmp = defaultTransformer.getOutputProperty(OutputKeys.METHOD); // SPR SCUU4R3JPH - throws npe
            reporter.check(tmp, "text", "identityTransformer set/getOutputProperty, is ?" + tmp + "?");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(2) with identity output property");
            reporter.checkFail("Problem(2) with identity output property");
        }

        try
        {
            // See what the our html-format output has
            Properties htmlProps = htmlTransformer.getOutputProperties();
            reporter.logHashtable(reporter.STATUSMSG, htmlProps, 
                                  "default htmlTransformer.getOutputProperties()");

            // Check that the local stylesheet.getProperty has stylesheet val set, cf. getOutputProperties javadoc
            reporter.check(("html".equals(htmlProps.getProperty(OutputKeys.METHOD))), true, "htmlTransformer.op.getProperty(" 
                           + OutputKeys.METHOD + ") is stylesheet value, act: " + htmlProps.getProperty(OutputKeys.METHOD));
            // Check that the local stylesheet.get has stylesheet val set, cf. getOutputProperties javadoc
            reporter.check(("html".equals(htmlProps.get(OutputKeys.METHOD))), true, "htmlTransformer.op.get(" 
                           + OutputKeys.METHOD + ") is stylesheet value, act: " + htmlProps.get(OutputKeys.METHOD));

            // Check that the local stylesheet.getProperty has default set, cf. getOutputProperties javadoc
            reporter.check(("yes".equals(htmlProps.getProperty(OutputKeys.INDENT))), true, "htmlTransformer.op.getProperty(" 
                           + OutputKeys.INDENT + ") is default value, act: " + htmlProps.getProperty(OutputKeys.INDENT));
            // Check that the local stylesheet.get has nothing set, cf. getOutputProperties javadoc
            reporter.check((null == (htmlProps.get(OutputKeys.INDENT))), true, "htmlTransformer.op.get(" 
                           + OutputKeys.INDENT + ") is default value, act: " + htmlProps.get(OutputKeys.INDENT));

            // Can you set properties on this transformer?
            defaultTransformer.setOutputProperty(OutputKeys.METHOD, "text");
            reporter.logTraceMsg("Just htmlTransformer setOutputProperty(method,text)");
            String tmp = defaultTransformer.getOutputProperty(OutputKeys.METHOD); // SPR SCUU4R3JPH - throws npe
            reporter.check(tmp, "text", "htmlTransformer set/getOutputProperty, is ?" + tmp + "?");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e, "Problem(3) with html output property");
            reporter.checkFail("Problem(3) with html output property");
        }

        try
        {
            // See what our outputTemplates parent has
            Properties tmpltProps = outputTemplates.getOutputProperties();
            reporter.logHashtable(reporter.STATUSMSG, tmpltProps, 
                                  "default outputTemplates.getOutputProperties()");

            // See what we have by default, from our testfile
            outputTransformer = outputTemplates.newTransformer();
            outputTransformer.setErrorListener(new DefaultErrorHandler());
            try
            {
                // Inner try-catch
                Properties outProps = outputTransformer.getOutputProperties(); // SPR SCUU4RXQYH throws npe
                reporter.logHashtable(reporter.STATUSMSG, outProps, 
                                      "default outputTransformer.getOutputProperties()");

                // Validate the two have the same properties (which they 
                //  should, since we just got the templates now)
                for (Enumeration names = tmpltProps.propertyNames();
                        names.hasMoreElements(); /* no increment portion */ )
                {
                    String key = (String)names.nextElement();
                    String value = tmpltProps.getProperty(key);
                    reporter.check(value, outProps.getProperty(key), 
                                   "Template, transformer identical outProp: " + key);
                }
            
                // Validate known output properties from our testfile
                String knownOutputProps[][] =
                {
                    { OutputKeys.METHOD, METHOD_VALUE },
                    { OutputKeys.VERSION, VERSION_VALUE },
                    { OutputKeys.ENCODING, ENCODING_VALUE },
                    { OutputKeys.STANDALONE, STANDALONE_VALUE },
                    { OutputKeys.DOCTYPE_PUBLIC, DOCTYPE_PUBLIC_VALUE }, // SPR SCUU4R3JRR - not returned
                    { OutputKeys.DOCTYPE_SYSTEM, DOCTYPE_SYSTEM_VALUE }, // SPR SCUU4R3JRR - not returned
                    { OutputKeys.CDATA_SECTION_ELEMENTS, CDATA_SECTION_ELEMENTS_VALUE }, // SPR SCUU4R3JRR - not returned
                    { OutputKeys.INDENT, INDENT_VALUE },
                    { OutputKeys.MEDIA_TYPE, MEDIA_TYPE_VALUE },
                    { OutputKeys.OMIT_XML_DECLARATION, OMIT_XML_DECLARATION_VALUE }
                };

                for (int i = 0; i < knownOutputProps.length; i++)
                {
                    String item = outProps.getProperty(knownOutputProps[i][0]);
                    reporter.check(item, knownOutputProps[i][1], 
                                   "Known prop(1) " + knownOutputProps[i][0] 
                                   + " is: ?" + item + "?");
                }
                reporter.logStatusMsg("@todo validate getting individual properties");
            }
            catch (Exception e)
            {
                reporter.logThrowable(reporter.ERRORMSG, e, "Problem(a1) with set/get output properties");
                reporter.checkFail("Problem(a1) with set/get output properties", "SCUU4RXQYH");
            }

            /*
            NOTE (SB):
            Shane omits the xml-decl in the stylesheet, which I don't think 
            will create a valid XML with UTF-16 encoding (I could be wrong).  
            Also, Xerces 1.2.3 is pretty broken for UTF-16 right now.
            So just comment this out for the moment.
            
            // Try doing a transform (will be UTF-16), to get some output
            if (doTransform(outputTransformer, 
                            new StreamSource(outputFormatTest.xmlName), 
                            new StreamResult(new FileOutputStream(outNames.nextName()))))
            {
                // @todo should update goldFile!
                fileChecker.check(reporter, 
                                  new File(outNames.currentName()), 
                                  new File(outputFormatTest.goldName), 
                                  "transform(UTF-16,1) outputParams into: " + outNames.currentName());
            }
            */

            // Change a single property (makes for simpler encoding output!)
            outputTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            String encoding = outputTransformer.getOutputProperty(OutputKeys.ENCODING);
            reporter.check(encoding, "UTF-8", "outputTransformer set/getOutputProperty value to ?" + encoding + "?");
            // Try doing another transform (will be UTF-8), to get some output
            // Verify that other output properties stay the same
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            if (doTransform(outputTransformer, 
                            new StreamSource(outputFormatTest.xmlName), 
                            new StreamResult(fos)))
            {
                fos.close(); // must close ostreams we own
                // @todo should update goldFile!
                if (Logger.PASS_RESULT
                    != fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(outputFormatTestUTF8), 
                        "transform(UTF-8) outputParams into: " + outNames.currentName())
                   )
                    reporter.logInfoMsg("transform(UTF-8) outputParams failure reason:" + fileChecker.getExtendedInfo());
            }
            // Try getting the whole block and logging it out, just to see what's there
            Properties moreOutProps = outputTransformer.getOutputProperties();
            reporter.logHashtable(reporter.STATUSMSG, moreOutProps, 
                                  "After several transforms getOutputProperties()");

            try
            {   // Inner try-catch
                // Simple set/getOutputProperty
                outputTransformer = outputTemplates.newTransformer();
                outputTransformer.setErrorListener(new DefaultErrorHandler());
                String tmp = outputTransformer.getOutputProperty(OutputKeys.OMIT_XML_DECLARATION); // SPR SCUU4RXR6E
                    // SPR SCUU4R3JZ7 - throws npe
                reporter.logTraceMsg(OutputKeys.OMIT_XML_DECLARATION + " is currently: " + tmp);
                outputTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                tmp = outputTransformer.getOutputProperty(OutputKeys.OMIT_XML_DECLARATION);
                reporter.check(tmp, "no", "outputTransformer set/getOutputProperty value to ?" + tmp + "?");
            }
            catch (Exception e)
            {
                reporter.logThrowable(reporter.ERRORMSG, e, "Problem(a2) with set/get output properties");
                reporter.checkFail("Problem(a2) with set/get output properties", "SCUU4RXR6E");
            }
            try
            {   // Inner try-catch
                // Try getting the whole properties block, so we can see what it thinks it has
                outputTransformer = outputTemplates.newTransformer();
                outputTransformer.setErrorListener(new DefaultErrorHandler());
                Properties newOutProps = outputTransformer.getOutputProperties();
                reporter.logHashtable(reporter.STATUSMSG, newOutProps, 
                                      "Another getOutputProperties()");

                // Simple set/getOutputProperty
                String tmp = outputTransformer.getOutputProperty(OutputKeys.ENCODING);
                reporter.logTraceMsg(OutputKeys.ENCODING + " is currently: " + tmp);
                outputTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                tmp = outputTransformer.getOutputProperty(OutputKeys.ENCODING);
                reporter.check(tmp, "UTF-8", "outputTransformer set/getOutputProperty value to ?" + tmp + "?");
            }
            catch (Exception e)
            {
                reporter.logThrowable(reporter.ERRORMSG, e,
                                      "Problem(a3) with set/get output property");
            }

            // OutputKeys.METHOD = xml|html|text|qname-but-not-ncname
            // OutputKeys.VERSION = number
            // OutputKeys.ENCODING = string
            // OutputKeys.OMIT_XML_DECLARATION = yes|no
            // OutputKeys.STANDALONE = yes|no
            // OutputKeys.DOCTYPE_PUBLIC = string
            // OutputKeys.DOCTYPE_SYSTEM = string
            // OutputKeys.CDATA_SECTION_ELEMENTS = qnames
            // OutputKeys.INDENT = qnames
            // OutputKeys.MEDIA_TYPE = qnames
            // OutputKeys.CDATA_SECTION_ELEMENTS = qnames

            reporter.logTraceMsg("//@todo Cover setOutputProperties(Properties oformat)");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e,
                                  "Problem(4) with set/get output properties");
            reporter.checkFail("Problem(4) with set/get output properties");
        }

        reporter.logTraceMsg("//@todo: Negative testing: various illegal arguments, etc. - in separate testcase");

        reporter.testCaseClose();
        return true;
    }

    /**
     * API coverage test of Transformer.set/getOutputProperties()
     * See {@link OutputPropertiesTest} for more coverage on setting 
     * different kinds of outputs, etc.
     * 
     * @return false if we should abort the test
     */
    public boolean testCase3()
    {
        reporter.testCaseInit("API coverage test of Transformer.set/getOutputProperties()");
        TransformerFactory factory = null;
        Templates outputTemplates = null;
        Transformer outputTransformer = null;
        Transformer identityTransformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            identityTransformer = factory.newTransformer();
            identityTransformer.setErrorListener(new DefaultErrorHandler());
            outputTemplates = factory.newTemplates(new StreamSource(outputFormatTest.inputName));
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating Templates; cannot continue");
            reporter.logThrowable(reporter.ERRORMSG, t, 
                                  "Problem creating Templates; cannot continue");
            return true;
        }
        try
        {
            // See what the default 'identity' transform has by default
            Properties identityProps = identityTransformer.getOutputProperties(); // SPR SCUU4RXQYH throws npe
            reporter.check((null != identityProps), true, "identityTransformer.getOutputProperties() is non-null");
            reporter.logHashtable(reporter.STATUSMSG, identityProps, 
                                  "default identityTransformer.getOutputProperties()");
        } 
        catch (Exception e)
        {
            reporter.logThrowable(reporter.ERRORMSG, e,
                                  "Problem with identity OutputProperties");
            reporter.checkFail("Problem with identity OutputProperties", "SCUU4RXQYH");
        }

        reporter.logTraceMsg("//@todo: coverage of non-identity transformers and set/get of props");
        reporter.testCaseClose();
        return true;
    } // end testCase3


    /**
     * Negative tests of Transformer.set/getOutputProperty/ies()
     * 
     * @return false if we should abort the test
     */
    public boolean testCase4()
    {
        reporter.testCaseInit("Negative tests of Transformer.set/getOutputProperty/ies()");
        TransformerFactory factory = null;
        Templates outputTemplates = null;
        Transformer outputTransformer = null;
        Transformer identityTransformer = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            identityTransformer = factory.newTransformer();
            identityTransformer.setErrorListener(new DefaultErrorHandler());
            outputTemplates = factory.newTemplates(new StreamSource(outputFormatTest.inputName));
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating Templates; cannot continue");
            reporter.logThrowable(reporter.ERRORMSG, t, 
                                  "Problem creating Templates; cannot continue");
            return true;
        }

        GetOutputPropertyTestlet testlet = new GetOutputPropertyTestlet();
        reporter.logTraceMsg("Using GetOutputPropertyTestlet for negative tests");
        testlet.setLogger(reporter);

        // Negative tests of getOutputProperty()
        GetOutputPropertyDatalet datalet = new GetOutputPropertyDatalet();
        reporter.logTraceMsg("Using GetOutputPropertyDatalet for negative tests");

        try
        {
            datalet.transformer = identityTransformer;
            datalet.propName = "bogus-name";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("bogus-name identityTransformer throws IllegalArgumentException");
            testlet.execute(datalet);
            
            datalet.transformer = identityTransformer;
            datalet.propName = "bogus-{name}";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("bogus-{name} throws IllegalArgumentException");
            testlet.execute(datalet);

            datalet.transformer = identityTransformer;
            datalet.propName = "{bogus-name";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("{bogus-name throws IllegalArgumentException (bracket not closed)");
            testlet.execute(datalet); 
            
            datalet.transformer = identityTransformer;
            datalet.propName = "{some-namespace}bogus-name";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{some-namespace}bogus-name returns null");
            testlet.execute(datalet);

            datalet.transformer = identityTransformer;
            datalet.propName = "{just-some-namespace}";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{just-some-namespace}bogus-name returns null");
            testlet.execute(datalet);

            datalet.transformer = identityTransformer;
            datalet.propName = "{}no-namespace-at-all";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{}no-namespace-at-all returns null (is this correct?)");
            testlet.execute(datalet);

            datalet.transformer = identityTransformer;
            datalet.propName = OutputKeys.METHOD;
            datalet.expectedValue = "xml";
            datalet.expectedException = null;
            datalet.setDescription(OutputKeys.METHOD + " returns xml on identity transformer (is this really correct?)");
            testlet.execute(datalet);
            
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.STATUSMSG, t, "Problem(1) with negative identityTransformer getOutputProperty");
            reporter.checkErr("Problem(1) with negative identityTransformer getOutputProperty: " + t.toString());
        }
        try
        {
            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "bogus-name";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("bogus-name regular transformer throws IllegalArgumentException");
            testlet.execute(datalet);
            
            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "bogus-{name}";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("bogus-{name} throws IllegalArgumentException");
            testlet.execute(datalet);

            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "{bogus-name";
            datalet.expectedValue = null;
            datalet.expectedException = "java.lang.IllegalArgumentException";
            datalet.setDescription("{bogus-name throws IllegalArgumentException (bracket not closed)");
            /* testlet.execute(datalet); comment out 10-May-01 -sc Bugzilla 890 */
            /* This is a fairly unimportant bug that may well be 
               fixed in the javadoc, so I'm commenting this case 
               out so we can run this test in the smoketest and 
               get more regular coverage of it! */

            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "{some-namespace}bogus-name";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{some-namespace}bogus-name returns null");
            testlet.execute(datalet);

            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "{just-some-namespace}";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{just-some-namespace}bogus-name returns null");
            testlet.execute(datalet);

            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = "{}no-namespace-at-all";
            datalet.expectedValue = datalet.NULL_VALUE_EXPECTED;
            datalet.expectedException = null;
            datalet.setDescription("{}no-namespace-at-all returns null (is this correct?)");
            testlet.execute(datalet);

            datalet.transformer = outputTemplates.newTransformer();
            datalet.propName = OutputKeys.METHOD;
            datalet.expectedValue = METHOD_VALUE;
            datalet.expectedException = null;
            datalet.setDescription(OutputKeys.METHOD + " returns " + METHOD_VALUE + " on transformer");
            testlet.execute(datalet);
            
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.STATUSMSG, t, "Problem(2) with negative transformer getOutputProperty");
            reporter.checkErr("Problem(2) with negative transformer getOutputProperty: " + t.toString());
        }

        reporter.testCaseClose();
        return true;
    } // end testCase4


    /**
     * TRAX Transformer: cover transform() API and multiple 
     * Transformations from single transformer.  
     * 
     * Note: obviously as the most important API, transform() is 
     * also covered in many other API tests as well as in various 
     * Stylesheet*Testlet tests.
     * 
     * @return false if we should abort the test
     */
    public boolean testCase5()
    {
        reporter.testCaseInit(
            "TRAX Transformer: cover multiple calls to transform()");
        TransformerFactory factory = null;
        Templates templates = null;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.STATUSMSG, t, "Can't continue testcase, factory.newInstance threw:");
            reporter.checkErr("Can't continue testcase, factory.newInstance threw: " + t.toString());
            return true;
        }

        try
        {
            Transformer transformer = factory.newTransformer(new StreamSource(simpleTest.inputName));
            transformer.setErrorListener(new DefaultErrorHandler());
            // Re-use the transformer multiple times on identity
            transformer.transform(new StreamSource(simpleTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(simpleTest.goldName), 
                "transform(#1) identity into: " + outNames.currentName());

            transformer.transform(new StreamSource(simpleTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(simpleTest.goldName), 
                "transform(#2) identity into: " + outNames.currentName());

            transformer.transform(new StreamSource(simpleTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(simpleTest.goldName), 
                "transform(#3) identity into: " + outNames.currentName());
            
            transformer = factory.newTransformer(new StreamSource(multiTest.inputName));
            transformer.setErrorListener(new DefaultErrorHandler());
            // Re-use the transformer multiple times on file with variable
            transformer.transform(new StreamSource(multiTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(multiTest.goldName), 
                "transform(#1-a) var test into: " + outNames.currentName());

            transformer.transform(new StreamSource(multiTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(multiTest.goldName), 
                "transform(#2-a) var test into: " + outNames.currentName());

            // Reset the transformer to its original state
            transformer.reset();
            
            transformer.transform(new StreamSource(multiTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(multiTest.goldName), 
                "transform(#3-a) var test into: " + outNames.currentName());

            // Now re-use with different xml doc
            transformer.transform(new StreamSource(multi2Test.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(multi2Test.goldName), 
                "transform(#4-b) var test into: " + outNames.currentName());

            // Reset the transformer to its original state
            transformer.reset();

            // Now re-use with original xml doc
            transformer.transform(new StreamSource(multiTest.xmlName), 
                                  new StreamResult(outNames.nextName()));
            fileChecker.check(reporter, 
                new File(outNames.currentName()), 
                new File(multiTest.goldName), 
                "transform(#5-a) var test into: " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.WARNINGMSG, t, "Multiple transform() calls threw");
            reporter.checkErr("Multiple transform() calls threw: " + t.toString());
        }
        reporter.testCaseClose();
        return true;
    }

    /**
     * TRAX Transformer: cover set/getURIResolver() API; 
     * plus set/getErrorListener() API.
     *
     * Note: These are simply coverage tests for these api's, 
     * for feature testing see links below 
     * 
     * @see ErrorListenerTest
     * @see ErrorListenerAPITest
     * @see URIResolverTest
     * @return false if we should abort the test
     */
    public boolean testCase6()
    {
        reporter.testCaseInit(
            "TRAX Transformer: cover transform() and set/getURIResolver API and functionality");
        TransformerFactory factory = null;
        Templates templates = null;
        try
        {
            factory = TransformerFactory.newInstance();
            // Grab a stylesheet to use for this testcase
            factory.setErrorListener(new DefaultErrorHandler());
            templates = factory.newTemplates(new StreamSource(simpleTest.inputName));
        }
        catch (Throwable t)
        {
            reporter.checkErr("Can't continue testcase, factory.newInstance threw: " + t.toString());
            reporter.logThrowable(reporter.STATUSMSG, t, "Can't continue testcase, factory.newInstance threw:");
            return true;
        }

        try
        {
            Transformer transformer = templates.newTransformer();
            ErrorListener errListener = transformer.getErrorListener(); // SPR SCUU4R3K6G - is null
            if (errListener == null)
            {
                reporter.checkFail("getErrorListener() non-null by default");
            }
            else
            {
                reporter.checkPass("getErrorListener() non-null by default, is: " + errListener);
            }
            
            LoggingErrorListener loggingErrListener = new LoggingErrorListener(reporter);
            transformer.setErrorListener(loggingErrListener);
            reporter.checkObject(transformer.getErrorListener(), loggingErrListener, "set/getErrorListener API coverage(1)");
            try
            {
                transformer.setErrorListener(null);                
                reporter.checkFail("setErrorListener(null) worked, should have thrown exception");
            }
            catch (IllegalArgumentException iae)
            {
                reporter.checkPass("setErrorListener(null) properly threw: " + iae.toString());
            }
            // Verify the previous ErrorListener is still set
            reporter.checkObject(transformer.getErrorListener(), loggingErrListener, "set/getErrorListener API coverage(2)");
        }
        catch (Throwable t)
        {
            reporter.checkErr("Coverage of get/setErrorListener threw: " + t.toString());
            reporter.logThrowable(reporter.STATUSMSG, t, "Coverage of get/setErrorListener threw:");
        }
        reporter.logStatusMsg("@todo feature testing for ErrorListener; see ErrorListenerTest, ErrorListenerAPITest");

        try
        {
            Transformer transformer = templates.newTransformer();
            transformer.setErrorListener(new DefaultErrorHandler());
            // URIResolver should be null by default; try to set/get one
            reporter.checkObject(transformer.getURIResolver(), null, "getURIResolver is null by default");
            LoggingURIResolver myURIResolver = new LoggingURIResolver(reporter);
            transformer.setURIResolver(myURIResolver);
            reporter.checkObject(transformer.getURIResolver(), myURIResolver, "set/getURIResolver API coverage");
            reporter.logTraceMsg("myURIres.getQuickCounters = " + myURIResolver.getQuickCounters());

            // Assumes we support Streams
            FileOutputStream fos = new FileOutputStream(outNames.nextName());
            if (doTransform(transformer, 
                            new StreamSource(simpleTest.xmlName), 
                            new StreamResult(fos)))
            {
                fos.close(); // must close ostreams we own
                fileChecker.check(reporter, 
                        new File(outNames.currentName()), 
                        new File(simpleTest.goldName), 
                        "transform(Stream, Stream) into: " + outNames.currentName());
            }
            reporter.logTraceMsg("myURIres.getQuickCounters = " + myURIResolver.getQuickCounters());

            reporter.logStatusMsg("@todo feature testing for URIResolver; see URIResolverTest");
        }
        catch (Throwable t)
        {
            reporter.logThrowable(reporter.ERRORMSG, t, "TestCase threw:");
            reporter.checkFail("TestCase threw: " + t.toString());
        }
        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method performs transforms (and catches exceptions, etc.)
     * Side effect: checkFail() if exception thrown
     *
     * @param Transformer to use
     * @param Source to pull in XML from
     * @param Result to put output in; may be modified
     * @return false if exception thrown, true otherwise
     */
    public boolean doTransform(Transformer t, Source s, Result r)
    {
        try
        {
            t.transform(s, r);
            return true;
        } 
        catch (TransformerException e)
        {
            reporter.checkFail("doTransform threw: " + e.toString());
            reporter.logThrowable(reporter.ERRORMSG, e, "doTransform threw:");
            return false;
        }
    }

    /** 
     * Datalet for output property testing.   
     * Fields: transformer, propName, (expectedValue|expectedException)
     */
    public class GetOutputPropertyDatalet implements Datalet
    {
        public GetOutputPropertyDatalet() {} // no-op
        public GetOutputPropertyDatalet(String[] args)
        {
            load(args);
        }
        public final String IDENTITY = "identity";
        public final String NULL_VALUE_EXPECTED = "NULL_VALUE_EXPECTED";
        protected String description = "no data";
        public String getDescription() { return description; }
        public void setDescription(String d) { description = d; }
        public Transformer transformer = null;
        public String propName = null;
        public String expectedValue = null;
        public String expectedException = null;
        public void load(String[] args)
        {
            try
            {
                if (IDENTITY.equals(args[0]))
                    transformer = (TransformerFactory.newInstance()).newTransformer();
                else
                    transformer = (TransformerFactory.newInstance()).newTransformer(new StreamSource(args[0]));
                propName = args[1];
                String tmp = args[2];
                // Semi-hack: if it looks like the FQCN of a 
                //  Throwable derivative, then use one 
                //  of those; otherwise, assume it's the expected 
                //  value to get back from getOutputProperty
                if ((tmp.indexOf("Exception") >= 0) || (tmp.indexOf("Error") >= 0))
                    expectedException = tmp;
                else
                    expectedValue = tmp;
            }
            catch (Throwable t)
            { /* no-op, let it fail elsewhere */
            }
        }
        public void load(Hashtable h)
        {
            transformer = (Transformer)h.get("transformer");
            propName = (String)h.get("propName");
            expectedValue  = (String)h.get("expectedValue ");
            expectedException = (String)h.get("expectedException");
        }
        
    } // end class GetOutputPropertyDatalet

    /** 
     * Calls getOutputProperty() on the Transformer supplied, and 
     * then either validates the returned String, or the classname 
     * of any exception thrown.
     *
     * This is almost more complex to implement as a Testlet than
     * is really worth it, but I wanted to experiment with using one.
     */
    public class GetOutputPropertyTestlet extends TestletImpl
    {
        { thisClassName = "org.apache.qetest.xsl.GetOutputPropertyTestlet"; }
        public String getDescription() { return "gets OutputProperty and validates"; }
        public Datalet getDefaultDatalet()
        {
            return new GetOutputPropertyDatalet(new String[] { "identity", "method", "xml" });
        }
        public void execute(Datalet d)
        {
            GetOutputPropertyDatalet datalet = null;
            try
            {
                datalet = (GetOutputPropertyDatalet)d;
            }
            catch (ClassCastException e)
            {
                logger.checkErr("Datalet provided is not a GetOutputPropertyDatalet; cannot continue");
                return;
            }
            try
            {
                // Perform the test
                String val = datalet.transformer.getOutputProperty(datalet.propName);
                // Validate non-throwing of expected exceptions
                if (null != datalet.expectedException)
                {
                    logger.checkFail(datalet.getDescription() + ", did not throw:" + datalet.expectedException
                                     + ", act:" + val);
                    return;
                }

                // Validate any return data
                if (null != val)
                {
                    // Check for positive values that exist
                    if ((null != datalet.expectedValue) 
                        && datalet.expectedValue.equals(val))
                        logger.checkPass(datalet.getDescription());
                    else
                        logger.checkFail(datalet.getDescription() + " act:" + val 
                                         + ", exp:" + datalet.expectedValue);
                }
                else if (datalet.NULL_VALUE_EXPECTED == datalet.expectedValue)
                {
                    // If expectedValue is the special 'null' string, 
                    //  and we're not expecting an exception, then 
                    //  we pass here
                   logger.checkPass(datalet.getDescription());
                }
                else
                {
                    // Otherwise, we fail
                    logger.checkFail(datalet.getDescription() + " act:" + val 
                                     + ", exp:" + datalet.expectedValue);
                }
            }
            catch (Throwable t)
            {
                // Validate any Exceptions thrown
                if (null != datalet.expectedException)
                {
                    if (datalet.expectedException.equals(t.getClass().getName()))
                        logger.checkPass(datalet.getDescription());
                    else
                        logger.checkFail(datalet.getDescription() + ", threw:" + t.toString()
                                         + ", exp:" + datalet.expectedException);
                }
                else
                    logger.checkFail(datalet.getDescription() + ", threw: " + t.toString());
            }
        }
    } // end class GetOutputPropertyTestlet

    /**
     * Convenience method to print out usage information - update if needed.  
     * @return usage string
     */
    public String usage()
    {
        return ("Common [optional] options supported by TransformerAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + "-processorClassname classname.of.processor  (to override setPlatformDefaultProcessor to Xalan 2.x)\n"
                + super.usage());
    }

    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {
        TransformerAPITest app = new TransformerAPITest();
        app.doMain(args);
    }

    // Android-added: Run main method as a JUnit test case.
    @FlakyTest(bugId = 292520220)
    @Test
    public void main() {
        main(new String[0]);
    }
}
