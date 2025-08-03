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
import org.apache.qetest.xsl.XHTFileCheckService;
import org.apache.qetest.CheckService;

import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import org.apache.xalan.templates.*;
import org.apache.xalan.extensions.*;
import org.apache.xalan.transformer.*;
import org.apache.xpath.*;
import org.apache.xpath.objects.*;

import org.apache.xml.dtm.*;
import org.apache.xml.dtm.ref.*;
import org.apache.xml.dtm.ref.sax2dtm.*;

import org.apache.xpath.XPathContext.XPathExpressionContext;
import org.apache.xpath.axes.OneStepIterator;

import java.io.File;

/**
 * Testlet for reproducing
 * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=2925">bug #2925</a>
 * @author scott_boag@lotus.com
 */
public class Bugzilla2925 extends TestletImpl
{

  // Initialize our classname for TestletImpl's main() method - must be updated!
  static
  {
    thisClassName = "Bugzilla2925";
  }

  /**
   * Write Minimal code to reproduce your Bugzilla bug report.
   * Many Bugzilla tests won't bother with a datalet; they'll
   * just have the data to reproduce the bug encoded by default.
   * @param d (optional) Datalet to use as data point for the test.
   *
   * NEEDSDOC @param datalet
   */
  public void execute(Datalet datalet)
  {

    // Use logger.logMsg(...) instead of System.out.println(...)
    logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#2925");

    try
    {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer t = tf.newTransformer(new StreamSource("Bugzilla2925.xsl"));
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

      dbf.setNamespaceAware(true);

      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse("Bugzilla2925Params.xml");

      t.setParameter("stylesheets", doc.getDocumentElement());
      t.transform(new StreamSource("bugzilla2925.xml"),
                  new StreamResult("bugzilla2925.xsr")
                  // new StreamResult(System.err)
                  );

      // If we get here, attempt to validate the contents of 
      //  the outputFile created
      CheckService fileChecker = new XHTFileCheckService();

      if (Logger.PASS_RESULT
              != fileChecker.check(logger, new File("bugzilla2925.xsr"),
                                   new File("bugzilla2925.out"),
                                   getDescription())){}
    }
    catch (Exception e)
    {
      logger.checkFail(e.getMessage());
    }
    
    // Optional: use the Datalet d if supplied
    // Call code to reproduce the bug here
    // Call logger.checkFail("desc") (like Junit's assert(true, "desc")
    //  or logger.checkPass("desc")  (like Junit's assert(false, "desc")
    //  to report the actual bug fail/pass status
  }
  
  public static DTM dtmTest(org.apache.xalan.extensions.ExpressionContext exprContext, 
                     String relativeURI)
  {
    XPathExpressionContext xpathExprContext = (XPathExpressionContext)exprContext;
    DTMManager dtmMgr = xpathExprContext.getDTMManager();
    
    DTM dtm = dtmMgr.getDTM(new StreamSource(relativeURI), true, null, false, true);
    // System.err.println("Returning a DTM: "+dtm);
    // ((DTMDefaultBase)dtm).dumpDTM();
    return dtm;
  }
  
  public static DTMAxisIterator DTMAxisIteratorTest(
                     org.apache.xalan.extensions.ExpressionContext exprContext, 
                     String relativeURI)
  {
    XPathExpressionContext xpathExprContext = (XPathExpressionContext)exprContext;
    DTMManager dtmMgr = xpathExprContext.getDTMManager();
    
    DTM dtm = dtmMgr.getDTM(new StreamSource(relativeURI), true, null, false, true);
    // System.err.println("Returning a DTM: "+dtm);
    // ((DTMDefaultBase)dtm).dumpDTM();
    
    DTMAxisIterator iter = dtm.getAxisIterator(Axis.SELF);
    iter.setStartNode(dtm.getDocument());
        
    return iter;
  }
  
  public static DTMIterator DTMIteratorTest(
                     org.apache.xalan.extensions.ExpressionContext exprContext, 
                     String relativeURI)
      throws Exception
  {
    XPathExpressionContext xpathExprContext = (XPathExpressionContext)exprContext;
    DTMManager dtmMgr = xpathExprContext.getDTMManager();
    
    DTM dtm = dtmMgr.getDTM(new StreamSource(relativeURI), true, null, false, true);
    // System.err.println("Returning a DTM: "+dtm);
    // ((DTMDefaultBase)dtm).dumpDTM();
    
/***************************
// Comment out compile error: Bugzilla2925.java:141: Wrong number of arguments in constructor.
    DTMIterator iterator = new OneStepIterator(dtm.getAxisIterator(Axis.SELF));
    iterator.setRoot(dtm.getDocument(), xpathExprContext.getXPathContext());

    return iterator;
// Comment out compile error: Bugzilla2925.java:141: Wrong number of arguments in constructor.
***************************/
    return null;
  }



  /**
   * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=2925">
   * Link to Bugzilla report</a>
   * @return "Parameter set from DOM Node, broken".
   */
  public String getDescription()
  {
    return "http://nagoya.apache.org/bugzilla/show_bug.cgi?id=2925";
  }
}  // end of class Bugzilla2925

