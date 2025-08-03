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
package org.apache.qetest.dtm;

import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.xml.dtm.Axis;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMAxisIterator;
import org.apache.xml.dtm.DTMManager;
import org.apache.xml.dtm.ref.DTMManagerDefault;
import org.apache.xpath.objects.XMLStringFactoryImpl;


/**
 * Unit test for DTMManager/DTM
 *
 * Loads an XML document from a file (or, if no filename is supplied,
 * an internal string), then dumps its contents. Replaces the old
 * version, which was specific to the ultra-compressed implementation.
 * (Which, by the way, we probably ought to revisit as part of our ongoing
 * speed/size performance evaluation.)
 *
 * %REVIEW% Extend to test DOM2DTM, incremental, DOM view of the DTM, 
 * whitespace-filtered, indexed/nonindexed, ...
 * */
public class TestDTMIterator {

static final String[] TYPENAME=
  { "NULL",
    "ELEMENT",
    "ATTRIBUTE",
    "TEXT",
    "CDATA_SECTION",
    "ENTITY_REFERENCE",
    "ENTITY",
    "PROCESSING_INSTRUCTION",
    "COMMENT",
    "DOCUMENT",
    "DOCUMENT_TYPE",
    "DOCUMENT_FRAGMENT",
    "NOTATION",
    "NAMESPACE"
  };

  public static void main(String argv[])
  {
  	System.out.println("\nHELLO THERE AND WELCOME TO THE WACKY WORLD OF ITERATORS \n");
    try
    {
	    // Pick our input source
		Source source=null;
		if(argv.length<1)
		{
			String defaultSource=
 		"<?xml version=\"1.0\"?>\n"+
 		"<Document xmlns:d=\"www.d.com\" a1=\"hello\" a2=\"goodbye\">"+
 		"<!-- Default test document -->"+
 		"<?api a1=\"yes\" a2=\"no\"?>"+
 		"<A><!-- A Subtree --><B><C><D><E><F xmlns:f=\"www.f.com\" a1=\"down\" a2=\"up\"/></E></D></C></B></A>"+
 		"<Aa/><Ab/><Ac><Ac1/></Ac>"+
 		"<Ad xmlns:Ad=\"www.Ad.com\" xmlns:y=\"www.y.com\" xmlns:z=\"www.z.com\">"+
 		"<Ad1/></Ad>"+
 		"</Document>";

			source=new StreamSource(new StringReader(defaultSource));
		}
		else if (argv.length>1 &&  "X".equalsIgnoreCase(argv[1]))
		{
			// XNI stream startup goes here
			// Remember to perform Schema validation, to obtain PSVI annotations
		}
		else
		{
			// Read from a URI via whatever mechanism the DTMManager prefers
			source=new StreamSource(argv[0]);
		}
	
      // Get a DTM manager, and ask it to load the DTM "uniquely",
      // with no whitespace filtering, nonincremental, but _with_
      // indexing (a fairly common case, and avoids the special
      // mode used for RTF DTMs).

	  // For testing with some of David Marston's files I do want to strip whitespace.
	  dtmWSStripper stripper = new dtmWSStripper();

      DTMManager manager= new DTMManagerDefault().newInstance(new XMLStringFactoryImpl());
      DTM dtm=manager.getDTM(source, true, stripper, false, true);

	  // Get various nodes to use as context nodes.
	  int dtmRoot = dtm.getDocument();					// #document
	  String dtmRootName = dtm.getNodeName(dtmRoot);	// Used for output
	  int DNode = dtm.getFirstChild(dtmRoot);			// <Document>
	  String DNodeName = dtm.getNodeName(DNode);
	  int CNode = dtm.getFirstChild(DNode);				// <Comment>
	  int PINode = dtm.getNextSibling(CNode);			// <PI>
	  int ANode = dtm.getNextSibling(PINode);			// <A>
	  String ANodeName = dtm.getNodeName(ANode);
	  int lastNode = 0;
      

	  // Get a Iterator for CHILD:: axis.
      DTMAxisIterator iter = dtm.getAxisIterator(Axis.CHILD);
      iter.setStartNode(DNode);

	  System.out.println("#### CHILD from "+"<"+DNodeName+">, Reverse Axis:" + iter.isReverse());			   
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		{ printNode(dtm, itNode, " ");
		  lastNode = itNode;
		}
	  
	  String lastNodeName = dtm.getNodeName(lastNode);

	  // Get iterator for PARENT:: Axis
	  iter = dtm.getAxisIterator(Axis.PARENT);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### PARENT from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get iterator for SELF:: Axis
	  iter = dtm.getAxisIterator(Axis.SELF);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### SELF from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

/**** Not Implemented
	  // Get iterator for NAMESPACEDECLS:: Axis
	  iter = dtm.getAxisIterator(Axis.NAMESPACEDECLS);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### NAMESPACEDECLS from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");
****/

	  // Get iterator for NAMESPACE:: Axis
	  iter = dtm.getAxisIterator(Axis.NAMESPACE);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### NAMESPACE from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get iterator for PRECEDING:: Axis
	  iter = dtm.getAxisIterator(Axis.PRECEDING);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### PRECEDING from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get iterator for PRECEDINGSIBLING:: Axis
	  iter = dtm.getAxisIterator(Axis.PRECEDINGSIBLING);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### PRECEDINGSIBLING from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

/**** ArrayIndexOutOfBoundsException
	  // Get iterator for ATTRIBUTE:: Axis
	  iter = dtm.getAxisIterator(Axis.ATTRIBUTE);
	  iter.setStartNode(DNode);
	  System.out.println("\n#### ATTRIBUTE from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");
****/

	  // Get iterator for FOLLOWING:: Axis
	  iter = dtm.getAxisIterator(Axis.FOLLOWING);
	  iter.setStartNode(ANode);
	  System.out.println("\n#### FOLLOWING from "+"<"+ANodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get iterator for FOLLOWINGSIBLING:: Axis
	  iter = dtm.getAxisIterator(Axis.FOLLOWINGSIBLING);
	  iter.setStartNode(ANode);
	  System.out.println("\n#### FOLLOWINGSIBLING from "+"<"+ANodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get a iterator for  DESCENDANT:: axis.
	  iter = dtm.getAxisIterator(Axis.DESCENDANT);
	  iter.setStartNode(ANode);
	  System.out.println("\n#### DESCENDANT from "+"<"+ANodeName+">, Reverse Axis:" + iter.isReverse());

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  {
		  	printNode(dtm, itNode, " ");
			lastNode = itNode;
		  }

	  // Get iterator for DESCENDANTORSELF:: Axis
	  iter = dtm.getAxisIterator(Axis.DESCENDANTORSELF);
	  iter.setStartNode(ANode);
	  System.out.println("\n#### DESCENDANT-OR-SELF from "+"<"+ANodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		{
		  printNode(dtm, itNode, " ");
		  lastNode = itNode;
		}

	  //lastNode = iter.getLast();	 // Uncomment for Bugzilla 7885.
      lastNodeName = dtm.getNodeName(lastNode);
	  
	  // The output from Ancestor and Ancestor-or-self is the topic
	  // of Bugzilla 7886
	  // Get iterator for ANCESTOR:: Axis
	  iter = dtm.getAxisIterator(Axis.ANCESTOR);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### ANCESTOR from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

	  // Get iterator for ANCESTORORSELF:: Axis
	  iter = dtm.getAxisIterator(Axis.ANCESTORORSELF);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### ANCESTOR-OR-SELF from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   
  
	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");

/**** Absolute axis (ALL, DESCENDANTSFROMROOT, or DESCENDANTSORSELFFROMROOT) not implemented.  
	  // Get itertor for ALL:: Axis
	  // of previous iterator, i.e. lastNode.
	  iter = dtm.getAxisIterator(Axis.ALL);
	  iter.setStartNode(lastNode);
	  System.out.println("\n#### ALL from "+"<"+lastNodeName+">, Reverse Axis:" + iter.isReverse());	   

	  // Iterate the axis and print out node info.
      for (int itNode = iter.next(); DTM.NULL != itNode;
              itNode = iter.next())
		  printNode(dtm, itNode, " ");
****/
    }
    catch(Exception e)
      {
        e.printStackTrace();
      }
  }
  
static void printNode(DTM dtm, int nodeHandle, String indent)
  {
    // Briefly display this node
    // Don't bother displaying namespaces or attrs; we do that at the
    // next level up.
    // %REVIEW% Add namespace info, type info, ...

    // Formatting hack -- suppress quotes when value is null, to distinguish
    // it from "null".
    String value=dtm.getNodeValue(nodeHandle);
    String vq = (value==null) ? "" : "\"";

    // Skip outputing of text nodes. In most cases they clutter the output, 
	// besides I'm only interested in the elemental structure of the dtm. 
    if( TYPENAME[dtm.getNodeType(nodeHandle)] != "TEXT" )
	{
    	System.out.println(indent+
		       +nodeHandle+": "+
		       TYPENAME[dtm.getNodeType(nodeHandle)]+" "+
			   dtm.getNodeName(nodeHandle)+" "+
			   " Level=" + dtm.getLevel(nodeHandle)+" "+
		       "\tValue=" + vq + value + vq
		       ); 
	}
  }

}
