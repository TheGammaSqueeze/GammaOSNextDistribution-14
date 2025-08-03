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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.traversal.NodeIterator;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 * @author jwalters@computer.org
 * @author shane_curcuru@us.ibm.com
 */
public class Bugzilla6181 extends TestletImpl
{

    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla6181"; }
    
    /** Cheap-o validation for extension call */
    static int extCounter = 0;

    /**
     * Write Minimal code to reproduce your Bugzilla bug report.
     * Many Bugzilla tests won't bother with a datalet; they'll 
     * just have the data to reproduce the bug encoded by default.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#6181");

        logger.logMsg(logger.STATUSMSG, "extCounter(before) = " + extCounter);    
        try
        {
            TransformerFactory factory = TransformerFactory.newInstance();

            // Simply transform our stylesheet..
            Transformer transformer = factory.newTransformer(new StreamSource("Bugzilla6181.xsl"));
            transformer.transform(new StreamSource("Bugzilla6181.xml"), 
                    new StreamResult("Bugzilla6181.output"));
            logger.checkPass("Crash test: produced unvalidated output into: Bugzilla6181.output");
        } 
        catch (Throwable t)
        {
            logger.logThrowable(logger.ERRORMSG, t, "Unexpected exception");
            logger.checkErr("Unexpected exception: " + t.toString());
        }
        // Then see how many times we've been called    
        logger.logMsg(logger.STATUSMSG, "extCounter(after) = " + extCounter);    
        
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=6181">
     * Link to Bugzilla report</a>
     * @return Problems with value-of and extension function.
     */
    public String getDescription()
    {
        return "Problems with value-of and extension function";
    }

  private String makeString( NodeIterator it ) {
    String source = null;
    StringBuffer sourceBuf = new StringBuffer();
	Node n;
	while((n = it.nextNode()) != null) {
      sourceBuf.append( n.getNodeValue() );
    }
    return sourceBuf.toString();
  }

  //  initcap
  //  Handles function nn:initcap(<XPath-Expr>)
  //
  public String initcap( NodeIterator it ) {
    String source = makeString( it );
    extCounter++;
    logger.logMsg(logger.INFOMSG, "initcap(ni) called with: " + source);
    //  Now do the initcap thing and return it...
    if( source.length() > 1 ) {
      return source.substring(0,1).toUpperCase() + source.substring( 1 );
    }
    return "";
  }

  private String makeString( DocumentFragment fragment ) {
      NodeList nodes = fragment.getChildNodes();
      StringBuffer sourceBuf = new StringBuffer();
      for( int i = 0; i < nodes.getLength(); ++i ) {
        String s = nodes.item(i).getNodeValue();
        if( s != null ) sourceBuf.append( s );
      }
      return sourceBuf.toString();
  }

  //  initcap
  //  Handles function nn:initcap(<XPath-Expr>)
  //
  public String initcap( DocumentFragment fragment ) {
    try {
      String source = makeString( fragment );
      extCounter++;
      logger.logMsg(logger.INFOMSG, "initcap(f) called with: " + source);
      //  Now do the initcap thing and return it...
      if( source.length() > 1 ) {
        return source.substring(0,1).toUpperCase() + source.substring( 1 );
      }
    } catch( Exception e ) {
      e.printStackTrace();
    }
    return "";
  }

}
