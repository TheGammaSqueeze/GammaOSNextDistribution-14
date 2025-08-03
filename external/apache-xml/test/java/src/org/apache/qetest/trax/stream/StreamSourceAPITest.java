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
 * StreamSourceAPITest.java
 *
 */
package org.apache.qetest.trax.stream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xml.utils.DefaultErrorHandler;
import org.apache.qetest.FileBasedTest;
import org.apache.qetest.OutputNameManager;
import org.apache.qetest.xsl.XSLTestfileInfo;

//-------------------------------------------------------------------------

/**
 * API Coverage test for the StreamSource class of TRAX..
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class StreamSourceAPITest extends FileBasedTest
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

    /** Subdirectory under test\tests\api for our xsl/xml files.  */
    public static final String TRAX_STREAM_SUBDIR = "trax" + File.separator + "stream";


    /** Just initialize test name, comment, numTestCases. */
    public StreamSourceAPITest()
    {
        numTestCases = 2;  // REPLACE_num
        testName = "StreamSourceAPITest";
        testComment = "API Coverage test for the StreamSource class of TRAX.";
    }


    /**
     * Initialize this test - Set names of xml/xsl test files.
     *
     * @param p Properties to initialize from (if needed)
     * @return false if we should abort the test; true otherwise
     */
    public boolean doTestFileInit(Properties p)
    {
        // NOTE: 'reporter' variable is already initialized at this point

        // Used for all tests; just dump files in trax subdir
        File outSubDir = new File(outputDir + File.separator + TRAX_STREAM_SUBDIR);
        if (!outSubDir.mkdirs())
            reporter.logWarningMsg("Could not create output dir: " + outSubDir);
        // Initialize an output name manager to that dir with .out extension
        outNames = new OutputNameManager(outputDir + File.separator + TRAX_STREAM_SUBDIR
                                         + File.separator + testName, ".out");

        String testBasePath = inputDir 
                              + File.separator 
                              + TRAX_STREAM_SUBDIR
                              + File.separator;
        String goldBasePath = goldDir 
                              + File.separator 
                              + TRAX_STREAM_SUBDIR
                              + File.separator;

        testFileInfo.inputName = testBasePath + "StreamImpIncl.xsl";
        testFileInfo.xmlName = testBasePath + "StreamImpIncl.xml";
        testFileInfo.goldName = goldBasePath + "StreamImpIncl.out";
        try
        {
            TransformerFactory tf = TransformerFactory.newInstance();
            if (!(tf.getFeature(StreamSource.FEATURE)
                  && tf.getFeature(StreamResult.FEATURE)))
            {   // The rest of this test relies on Streams
                reporter.logErrorMsg("Streams not supported! Some tests may be invalid!");
            }
        }
        catch (Throwable t)
        {
            reporter.checkFail(
                "Problem creating factory; Some tests may be invalid!");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; Some tests may be invalid!");
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

        reporter.logWarningMsg("public StreamSource(File f) not yet tested");
        reporter.logWarningMsg("public void setSystemId(File f) not yet tested");

        // Default no-arg ctor sets nothing
        StreamSource defaultStream = new StreamSource();
        reporter.checkObject(defaultStream.getInputStream(), null, "Default StreamSource should have null ByteStream");
        reporter.checkObject(defaultStream.getReader(), null, "Default StreamSource should have null CharacterStream");
        reporter.check(defaultStream.getPublicId(), null, "Default StreamSource should have null PublicId");
        reporter.check(defaultStream.getSystemId(), null, "Default StreamSource should have null SystemId");

        byte[] bytes = { 0, 0, 0, 0 };  // just a few zeroes, not really needed
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        StreamSource byteSource1 = new StreamSource(bais);
        reporter.checkObject(byteSource1.getInputStream(), bais, "StreamSource(is) has ByteStream " + byteSource1.getInputStream());
        reporter.checkObject(byteSource1.getReader(), null, "StreamSource(is) should have null CharacterStream");
        reporter.check(byteSource1.getPublicId(), null, "StreamSource(is) should have null PublicId");
        reporter.check(byteSource1.getSystemId(), null, "StreamSource(is) should have null SystemId");

        StreamSource byteSource2 = new StreamSource(bais, "some-system-id");
        reporter.checkObject(byteSource2.getInputStream(), bais, "StreamSource(is, sysID) has ByteStream " + byteSource2.getInputStream());
        reporter.checkObject(byteSource2.getReader(), null, "StreamSource(is, sysID) should have null CharacterStream");
        reporter.check(byteSource2.getPublicId(), null, "StreamSource(is, sysID) should have null PublicId");
        reporter.check(byteSource2.getSystemId(), "some-system-id", "StreamSource(is, sysID) has SystemId " + byteSource2.getSystemId());

        StringReader strReader = new StringReader("this is not your parent's XML data");
        StreamSource readerSource1 = new StreamSource(strReader);
        reporter.checkObject(readerSource1.getInputStream(), null, "StreamSource(reader) should have null ByteStream");
        reporter.checkObject(readerSource1.getReader(), strReader, "StreamSource(reader) has CharacterStream " + readerSource1.getReader());
        reporter.check(readerSource1.getPublicId(), null, "StreamSource(reader) should have null PublicId");
        reporter.check(readerSource1.getSystemId(), null, "StreamSource(reader) should have null SystemId");

        StreamSource readerSource2 = new StreamSource(strReader, "some-system-id");
        reporter.checkObject(readerSource2.getInputStream(), null, "StreamSource(reader, sysID) should have null ByteStream");
        reporter.checkObject(readerSource2.getReader(), strReader, "StreamSource(reader, sysID) has CharacterStream " + readerSource2.getReader());
        reporter.check(readerSource2.getPublicId(), null, "StreamSource(reader, sysID) should have null PublicId");
        reporter.check(readerSource2.getSystemId(), "some-system-id", "StreamSource(reader, sysID) has SystemId " + readerSource2.getSystemId());

        StreamSource sysIDStream = new StreamSource("real-system-id");
        reporter.checkObject(sysIDStream.getInputStream(), null, "StreamSource(sysID) should have null ByteStream");
        reporter.checkObject(sysIDStream.getReader(), null, "StreamSource(sysID) should have null CharacterStream");
        reporter.check(sysIDStream.getPublicId(), null, "StreamSource(sysID) should have null PublicId");
        reporter.check(sysIDStream.getSystemId(), "real-system-id", "StreamSource(sysID) has SystemId " + sysIDStream.getSystemId());

        StreamSource wackyStream = new StreamSource();
        wackyStream.setInputStream(bais);
        InputStream gotStream = wackyStream.getInputStream();
        reporter.checkObject(gotStream, bais, "set/getInputStream API coverage");

        wackyStream.setReader(strReader);
        Reader gotReader = wackyStream.getReader();
        reporter.checkObject(gotReader, strReader, "set/getReader API coverage");

        wackyStream.setSystemId("new-system-id");
        String gotSystemId = wackyStream.getSystemId();
        reporter.check(gotSystemId, "new-system-id", "set/getSystemId API coverage");

        wackyStream.setPublicId("new-public-id");
        String gotPublicId = wackyStream.getPublicId();
        reporter.check(gotPublicId, "new-public-id", "set/getPublicId API coverage");

        reporter.testCaseClose();
        return true;
    }


    /**
     * Basic functionality of StreamSources.
     *
     * @return false if we should abort the test; true otherwise
     */
    public boolean testCase2()
    {
        reporter.testCaseInit("Basic functionality of StreamSources");

        TransformerFactory factory = null;
        String xslID = testFileInfo.inputName;
        String xmlID = testFileInfo.xmlName;
        try
        {
            factory = TransformerFactory.newInstance();
            factory.setErrorListener(new DefaultErrorHandler());
            // Create URLs for the filenames
            // What's the simplest way to do this?!? i.e. as a solution 
            //  to the general problem of having a String denoting a 
            //  path/filename, that may be absolute or relative,
            //  and may or may not exist, and to get a valid file: URL?
            reporter.logTraceMsg("Original names xslID=" + xslID + ", xmlID=" + xmlID);
            xslID = filenameToURI(xslID);
            xmlID = filenameToURI(xmlID);
            reporter.logTraceMsg("URL-ized names xslID=" + xslID + ", xmlID=" + xmlID);
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem creating factory; can't continue testcase");
            reporter.logThrowable(reporter.ERRORMSG, t,
                                  "Problem creating factory; can't continue testcase");
            return true;
        }
        try
        {
            // Verify we can do basic transforms with readers/streams
            reporter.logTraceMsg("Create stream sources and setSystemId separately");
            InputStream xslStream1 = new FileInputStream(testFileInfo.inputName);
            Source xslSource1 = new StreamSource(xslStream1);
            xslSource1.setSystemId(xslID);
            InputStream xmlStream1 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource1 = new StreamSource(xmlStream1);
            xmlSource1.setSystemId(xmlID);

            reporter.logTraceMsg("Create FileOutputStream to " + outNames.nextName());
            FileOutputStream fos1 = new FileOutputStream(outNames.currentName());
            Result result1 = new StreamResult(fos1);
            Templates templates1 = factory.newTemplates(xslSource1);
            Transformer transformer1 = templates1.newTransformer();
            transformer1.setErrorListener(new DefaultErrorHandler());
            reporter.logTraceMsg("about to transform to streams after setSystemId");
            transformer1.transform(xmlSource1, result1);
            fos1.close(); // must close ostreams we own
            int result = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform to streams after setSystemId into " + outNames.currentName());
            if (result == reporter.FAIL_RESULT)
                reporter.logInfoMsg("transform to streams... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with transform-streams(1)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with transform-streams(1)");
        }
        try
        {
            reporter.logTraceMsg("Create stream sources with setSystemId in ctor");
            InputStream xslStream2 = new FileInputStream(testFileInfo.inputName);
            Source xslSource2 = new StreamSource(xslStream2, xslID);
            InputStream xmlStream2 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource2 = new StreamSource(xmlStream2, xmlID);
            FileOutputStream fos2 = new FileOutputStream(outNames.nextName());
            Result result2 = new StreamResult(fos2);

            reporter.logInfoMsg("Transform into " + outNames.currentName());
            Templates templates2 = factory.newTemplates(xslSource2);
            Transformer transformer2 = templates2.newTransformer();
            transformer2.setErrorListener(new DefaultErrorHandler());
            transformer2.transform(xmlSource2, result2);
            fos2.close(); // must close ostreams we own
            int result = fileChecker.check(reporter, 
                              new File(outNames.currentName()), 
                              new File(testFileInfo.goldName), 
                              "transform to streams after SystemId in ctor into " + outNames.currentName());
            if (result == reporter.FAIL_RESULT)
                reporter.logInfoMsg("transform to streams... failure reason:" + fileChecker.getExtendedInfo());
        }
        catch (Throwable t)
        {
            reporter.checkFail("Problem with transform-streams(2)");
            reporter.logThrowable(reporter.ERRORMSG, t, "Problem with transform-streams(2)");
        }

        try
        {
            // Do a transform without systemId set
            // Note: is affected by user.dir property; if we're 
            //  already in the correct place, this won't be different
            // But: most people will run from xml-xalan\test, so this should fail
            try
            {
                reporter.logStatusMsg("System.getProperty(user.dir) = " + System.getProperty("user.dir"));
            }
            catch (SecurityException e) // in case of Applet context
            {
                reporter.logTraceMsg("System.getProperty(user.dir) threw SecurityException");
            }
            reporter.logTraceMsg("Create stream sources without setSystemId set");
            InputStream xslStream3 = new FileInputStream(testFileInfo.inputName);
            Source xslSource3 = new StreamSource(xslStream3);
            InputStream xmlStream3 = new FileInputStream(testFileInfo.xmlName);
            Source xmlSource3 = new StreamSource(xmlStream3);
            FileOutputStream fos3 = new FileOutputStream(outNames.nextName());
            Result result3 = new StreamResult(fos3);

            Templates templates3 = factory.newTemplates(xslSource3);
            Transformer transformer3 = templates3.newTransformer();
            transformer3.setErrorListener(new DefaultErrorHandler());
            reporter.logStatusMsg("About to transform without systemID; probably throws exception");
            transformer3.transform(xmlSource3, result3);
            reporter.checkFail("The above transform should probably have thrown an exception; into " + outNames.currentName());
        }
        catch (Throwable t)
        {
            reporter.checkPass("Transforming with include/import and wrong SystemId throws exception; into " + outNames.currentName());
            reporter.logThrowable(reporter.ERRORMSG, t, "Transforming with include/import and wrong SystemId");
        }

        reporter.testCaseClose();
        return true;
    }


    /**
     * Worker method to translate String to URI.  
     * Note: Xerces and Crimson appear to handle some URI references 
     * differently - this method needs further work once we figure out 
     * exactly what kind of format each parser wants (esp. considering 
     * relative vs. absolute references).
     * @param String path\filename of test file
     * @return URL to pass to SystemId
     */
    public String filenameToURI(String filename)
    {
        File f = new File(filename);
        String tmp = f.getAbsolutePath();
	    if (File.separatorChar == '\\') {
	        tmp = tmp.replace('\\', '/');
	    }
        return "file:///" + tmp;
    }


    /**
     * Convenience method to print out usage information - update if needed.  
     * @return String denoting usage of this test class
     */
    public String usage()
    {
        return ("Common [optional] options supported by StreamSourceAPITest:\n"
                + "(Note: assumes inputDir=.\\tests\\api)\n"
                + super.usage());   // Grab our parent classes usage as well
    }


    /**
     * Main method to run test from the command line - can be left alone.  
     * @param args command line argument array
     */
    public static void main(String[] args)
    {

        StreamSourceAPITest app = new StreamSourceAPITest();

        app.doMain(args);
    }
}
