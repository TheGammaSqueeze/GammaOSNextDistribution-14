package org.apache.qetest.xsl;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.CommandlineJava;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.Path;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

/*
   This ant task can, check whether XalanJ's XSLTC processor 
   can compile XSLT stylesheets correctly.

   @author <a href="mailto:mukulg@apache.org">Mukul Gandhi</a>
*/
public class XsltcTestsTask extends Task {
    
    private String inputDir;

    private String resultDir;

    private static final String XSLT_FILE_EXT = ".xsl";

    private static final String expectedTestResultsDoc = "expected_test_results.xml";

    private static final String testResultsDoc = "actual_test_results.xml";

    private static final String STYLESHEET_COMPILATION_ERR_MESG = "Could not compile stylesheet";

    private static final String PASS = "pass";

    private static final String FAIL = "fail";

    private CommandlineJava commandLineJava = new CommandlineJava();

    // method to run this, ant build task
    public void execute() throws BuildException {

        System.setProperty("javax.xml.transform.TransformerFactory", 
		                        "org.apache.xalan.xsltc.trax.TransformerFactoryImpl");

        Document expectedTestResultsDocument = getExpectedTestResultsDocument();
        
        Map<String, Boolean> testResults = new HashMap<String, Boolean>();

        File dirObj = new File(this.inputDir);
        File[] fileList = dirObj.listFiles();
        
        boolean isXsltcTestsPassed = true;

        try {
            for (int idx = 0; idx < fileList.length; idx++) {
               String fileName = fileList[idx].getName();
               if (fileName.endsWith(XSLT_FILE_EXT)) {                  
                  XsltcTestsErrorListener xsltcTestsErrorListener = new XsltcTestsErrorListener();                      
                  compileXslDocument(fileName, xsltcTestsErrorListener);
                  boolean isCompileErrorOccured = STYLESHEET_COMPILATION_ERR_MESG.
                                                          equals(xsltcTestsErrorListener.getErrListenerMesg());                                                                               
                  boolean hasTestPassed = isTestPassed(expectedTestResultsDocument, 
                                                              fileName, isCompileErrorOccured);
                  if (hasTestPassed) {
                     testResults.put(fileName, Boolean.TRUE);
                  }
                  else {
                     testResults.put(fileName, Boolean.FALSE);
                     isXsltcTestsPassed = false;
                  }                                      
               }
            }

            serializeTestResultsToXmlDocument(testResults);
        }
        catch (Exception ex) {
           throw new BuildException(ex.getMessage(), location);
        }
        
        if (!isXsltcTestsPassed) {
           throw new BuildException("One or more XSLTC tests have failed. Please fix any XSLTC tests problems, " + 
                                      "before checking in! XSLTC test results are available at " + resultDir + "/" + 
                                        testResultsDoc , location);
        }
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public void setResultDir(String resultDir) {
        this.resultDir = resultDir;
    }

    /**
     * Set the bootclasspathref to be used for this test.
     *
     * @param bootclasspathref used for running the test
     */
    public void setBootclasspathref(Reference ref)
    {
        String jdkRelease =
                   System.getProperty("java.version", "0.0").substring(0,3);
        if (!jdkRelease.equals("1.1")
                && !jdkRelease.equals("1.2")
                && !jdkRelease.equals("1.3")) {
            Path p = (Path)ref.getReferencedObject(this.getProject());
            createJvmarg().setValue("-Xbootclasspath/p:" + p);
        }
    }

    /**
     * Creates a nested jvmarg element.
     *
     * @return Argument to send to our JVM if forking
     */
    public Commandline.Argument createJvmarg()
    {
        return commandLineJava.createVmArgument();
    } 

    /*
       This method, attempts to compile an XSLT stylesheet. If the stylesheet
       cannot be compiled, the registered error listener sets the resulting
       error message within a variable.
    */
    private void compileXslDocument(String xslName, XsltcTestsErrorListener 
                                                        xsltcTestsErrorListener) {         
         try {
            TransformerFactory trfFactory = TransformerFactory.newInstance();         
            trfFactory.setErrorListener(xsltcTestsErrorListener);
            Transformer transformer = trfFactory.newTransformer(
                                                new StreamSource(this.inputDir + "/" + xslName));
         }
         catch (Exception ex) {
            // NO OP
         }                                                      
    }

    /*
       An expected tests results document, is already available before running
       this ant task. This method, returns an XML DOM representation of that
       XML document.
    */
    private Document getExpectedTestResultsDocument() { 
       Document xmlResultDocument = null;

       try {
          DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
          DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
          xmlResultDocument = docBuilder.parse(this.inputDir + "/" + expectedTestResultsDoc);
       }
       catch (Exception ex) {
          throw new BuildException(ex.getMessage(), location);
       }
      
       return xmlResultDocument; 
    }

    /*
       This method, checks whether an XSLTC test that has been run, 
       has passed or not.
    */
    private boolean isTestPassed(Document expectedTestResultsDoc, 
                                    String xslFileName, boolean isCompileErrorOccured) 
                                                                      throws Exception {
       XPath xPath = XPathFactory.newInstance().newXPath();
       String isCompileErrorOccuredStr = (new Boolean(isCompileErrorOccured)).toString();
       String xpathExprStr = "/expectedResults/file[@name = '" + xslFileName + "' and " +
                                  "compileError = '" + isCompileErrorOccuredStr + "']/expected";                                 
       String xpathNodeStrValue = (String)((xPath.compile(xpathExprStr)).
                                                    evaluate(expectedTestResultsDoc, 
                                                                 XPathConstants.STRING));
       return PASS.equals(xpathNodeStrValue) ? true : false;                                                                
    }

    /*
       Given XSLTC overall test results, produced by this class (available as Map object),
       this method serializes those results to an XML document.
    */
    private void serializeTestResultsToXmlDocument(Map testResultsMap) 
                                                              throws Exception {
       Set<Map.Entry<String,Boolean>> mapEntries = testResultsMap.entrySet();
       Iterator<Map.Entry<String,Boolean>> iter = mapEntries.iterator();
       
       DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
       DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
       Document resultDocument = docBuilder.newDocument();

       Element testResultsElem = resultDocument.createElement("testResults");       
       while (iter.hasNext()) {
          Map.Entry<String,Boolean> mapEntry = iter.next();
          String fileName = mapEntry.getKey();
          Boolean passStatus = mapEntry.getValue();
          
          Element resultElem = resultDocument.createElement("result");
          Element fileNameElem = resultDocument.createElement("fileName");
          fileNameElem.appendChild(resultDocument.createTextNode(fileName));
          resultElem.appendChild(fileNameElem);
          Element statusElem = resultDocument.createElement("status");
          statusElem.appendChild(resultDocument.createTextNode(passStatus == 
                                                   Boolean.TRUE ? "pass" : "fail"));
          resultElem.appendChild(statusElem);
          
          testResultsElem.appendChild(resultElem);
       }

       resultDocument.appendChild(testResultsElem);

       DOMImplementationLS domImplementation = (DOMImplementationLS) 
                                                         resultDocument.getImplementation();
       LSSerializer lsSerializer = domImplementation.createLSSerializer();
       (lsSerializer.getDomConfig()).setParameter("format-pretty-print", Boolean.TRUE);
       String resultDocumentXmlStr = lsSerializer.writeToString(resultDocument);

       Files.write(Paths.get(resultDir + "/" + testResultsDoc), 
                                                    resultDocumentXmlStr.getBytes());
    }

}
