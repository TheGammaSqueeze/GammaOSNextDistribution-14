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
import org.apache.xml.dtm.DTMAxisTraverser;
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
public class TestDTMTraverser {

/*class myWSStripper implements DTMWSFilter {

void myWWStripper()
  { }

public short getShouldStripSpace(int elementHandle, DTM dtm)
  {
 	return DTMWSFilter.STRIP;
  }

}
*/
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
  	System.out.println("\nHELLO THERE AND WELCOME TO THE WACKY WORLD OF TRAVERSERS \n");
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

      
	  // Get a traverser for Child:: axis.
	  System.out.println("\n#### CHILD from "+"<"+DNodeName+">");			   
      DTMAxisTraverser at = dtm.getAxisTraverser(Axis.CHILD);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(DNode); DTM.NULL != atNode;
              atNode = at.next(DNode, atNode))
		{  printNode(dtm, atNode, " ");
		   lastNode = atNode;
		}
      
	  // Get a traverser for Parent:: axis.
	  String lastNodeName = dtm.getNodeName(lastNode);
	  System.out.println("\n#### PARENT from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.PARENT);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		  printNode(dtm, atNode, " ");
		
	  // Get a from Self:: axis.
	  System.out.println("\n#### SELF from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.SELF);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		  printNode(dtm, atNode, " ");
		
	  // Get a traverser for NameSpaceDecls:: axis.
	  System.out.println("\n#### NAMESPACEDECLS from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.NAMESPACEDECLS);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		  printNode(dtm, atNode, " ");
		
	  // Get a traverser for Namespace:: axis.
	  System.out.println("\n#### NAMESPACE from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.NAMESPACE);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		  printNode(dtm, atNode, " ");
      
	  // Get a traverser for Preceding:: axis.
	  System.out.println("\n#### PRECEDING from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.PRECEDING);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for PRECEDING-SIBLING:: axis.
	  System.out.println("\n#### PRECEDINGSIBLING from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.PRECEDINGSIBLING);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for PRECEDINGANDANCESTOR:: axis.
	  System.out.println("\n#### PRECEDINGANDANCESTOR from "+"<"+lastNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.PRECEDINGANDANCESTOR);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");
      
	  // Get a traverser for Attribute:: axis.
	  System.out.println("\n#### ATTRIBUTE from "+"<"+DNodeName+">");			   
      at = dtm.getAxisTraverser(Axis.ATTRIBUTE);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(DNode); DTM.NULL != atNode;
              atNode = at.next(DNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for Following:: axis.
	  System.out.println("\n#### FOLLOWING from "+"<"+ANodeName+">");
      at = dtm.getAxisTraverser(Axis.FOLLOWING);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(ANode); DTM.NULL != atNode;
              atNode = at.next(ANode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for FollowingSibling:: axis.
	  System.out.println("\n#### FOLLOWINGSIBLING from "+"<"+ANodeName+">");
      at = dtm.getAxisTraverser(Axis.FOLLOWINGSIBLING);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(ANode); DTM.NULL != atNode;
              atNode = at.next(ANode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for  DESCENDANT:: axis.
	  System.out.println("\n#### DESCENDANT from "+"<"+ANodeName+">");
      at = dtm.getAxisTraverser(Axis.DESCENDANT);

	  // Traverse the axis and print out node info.
	  for (int atNode = at.first(ANode); DTM.NULL != atNode;
              atNode = at.next(ANode, atNode))
		  printNode(dtm, atNode, " ");


	  // Get a traverser for  DESCENDANTORSELF:: axis.
	  System.out.println("\n#### DESCENDANT-OR-SELF from "+"<"+ANodeName+">");
      at = dtm.getAxisTraverser(Axis.DESCENDANTORSELF);

	  // Traverse the axis and print out node info.
	  for (int atNode = at.first(ANode); DTM.NULL != atNode;
              atNode = at.next(ANode, atNode))
		{
			printNode(dtm, atNode, " ");
			lastNode = atNode;
		}

	  // Get a traverser for ANCESTOR:: axis.
	  lastNodeName = dtm.getNodeName(lastNode);
	  System.out.println("\n#### ANCESTOR from "+"<"+lastNodeName+">");
      at = dtm.getAxisTraverser(Axis.ANCESTOR);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for ANCESTORORSELF:: axis.
	  System.out.println("\n#### ANCESTOR-OR-SELF from "+"<"+lastNodeName+">");
      at = dtm.getAxisTraverser(Axis.ANCESTORORSELF);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for ALLFROMNODE:: axis.
	  System.out.println("\n#### ALL-FROM-NODE from "+"<"+lastNodeName+">");
      at = dtm.getAxisTraverser(Axis.ALLFROMNODE);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // ABSOLUTE AXIS TESTS
	  // These next axis are all Absolute. They all default to the root of the dtm
	  // tree,  regardless of what we call first() with. 
	  // Get a traverser for ALL:: axis. 
	  System.out.println("\n#### ALL(absolute) from "+"<"+dtmRootName+">");
      at = dtm.getAxisTraverser(Axis.ALL);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for DESCENDANTSFROMROOT:: axis.
	  System.out.println("\n#### DESCENDANTSFROMROOT(absolute) from "+"<"+dtmRootName+">");
      at = dtm.getAxisTraverser(Axis.DESCENDANTSFROMROOT);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

	  // Get a traverser for DESCENDANTSORSELFFROMROOT:: axis.
	  System.out.println("\n#### DESCENDANTSORSELFFROMROOT(absolute) from "+"<"+dtmRootName+">");
      at = dtm.getAxisTraverser(Axis.DESCENDANTSORSELFFROMROOT);

	  // Traverse the axis and print out node info.
      for (int atNode = at.first(lastNode); DTM.NULL != atNode;
              atNode = at.next(lastNode, atNode))
		printNode(dtm, atNode, " ");

    }
    catch(Exception e)
      {
        e.printStackTrace();
      }
  }
  
  static void printNode(DTM dtm,int nodeHandle,String indent)
  {
    // Briefly display this node
    // Don't bother displaying namespaces or attrs; we do that at the
    // next level up.
    // %REVIEW% Add namespace info, type info, ...

    // Formatting hack -- suppress quotes when value is null, to distinguish
    // it from "null".
    String value=dtm.getNodeValue(nodeHandle);
    String vq=(value==null) ? "" : "\"";

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
