package org.apache.qetest.xsl;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/*
   This ant task checks, whether ant target "api"'s result 
   is pass or fail, by inspecting content within test results 
   XML documents.

   @author <a href="mailto:mukulg@apache.org">Mukul Gandhi</a>
*/
public class XSLApiTestsResultTask extends Task {
    
    private String resultDir;

    private String fileNamePrefix;    

    private static final String PASS = "Pass";

    // method to run this, ant build task
    public void execute() throws BuildException {
        File dirObj = new File(this.resultDir);
        File[] fileList = dirObj.listFiles();
        for (int idx = 0; idx < fileList.length; idx++) {
           String fileName = fileList[idx].getName();
           if (fileName.startsWith(this.fileNamePrefix)) {
               String testResultFilePassStatus  = getTestResultFilePassStatus(
                                          fileList[idx].getAbsolutePath());
               if (!PASS.equals(testResultFilePassStatus)) {
                  String[] dirNameParts = (this.resultDir).split("/"); 
                  String errorContextFileName = dirNameParts[dirNameParts.length - 1] + "/" + fileName;
                  throw new BuildException("One or more tests in an 'api' target failed. Test failure was found, " + 
                                           "while inspecting the file " + errorContextFileName + ". Please fix any api tests " + 
                                           "problems before checking in!", location);
               }
           } 
        }
    }

    public void setResultDir(String resultDir) {
        this.resultDir = resultDir;
    }

    public void setFileNamePrefix(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    /*
       Read the test pass status value, within test result's XML document.
    */
    private String getTestResultFilePassStatus(String testResultFilePath) {
       String resultStr = "";
       
       try {	   
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document xmlDocument = docBuilder.parse(testResultFilePath);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String xpathExprStr = "/teststatus";
            String xpathNodeValue = (String)((xPath.compile(xpathExprStr)).
                                                    evaluate(xmlDocument, 
                                                            XPathConstants.STRING));
            resultStr = xpathNodeValue.trim();
       }
       catch (Exception ex) {
          String[] filePathParts = testResultFilePath.split("/");
          throw new BuildException("Exception occured, processing api test result XML document " + filePathParts[filePathParts.length - 1], location);
       }

       return resultStr;
	}

}
