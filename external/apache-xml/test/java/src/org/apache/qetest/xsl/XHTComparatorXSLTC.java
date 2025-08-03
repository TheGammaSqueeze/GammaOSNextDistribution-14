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

package org.apache.qetest.xsl;

import java.io.PrintWriter;
import java.util.Vector;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Defines XSLTC's XML/HTML/Text diff comparator to check or diff two files.
 * This comparator uses the expanded name instead of the qname to compare
 * element names. Also, for simplicity, it ignores NS declaration attributes.
 *
 * //@todo Use expanded name for attributes as well
 *
 * @author Scott_Boag@lotus.com
 * @author Shane_Curcuru@lotus.com
 * @author Santiago.PericasGeertsen@sun.com
 * @version $Id$
 */
public class XHTComparatorXSLTC extends XHTComparator
{

    /**
     * The contract is: when you enter here the gold and test nodes are the same type,
     * both non-null, and both in the same basic position in the tree.
     * //@todo verify caller really performs for the contract -sc
     *
     * This overridden method does additional checking of namespaces 
     * and local names, instead of just getNodeName().
     *
     * @param gold gold or expected node
     * @param test actual node
     * @param reporter PrintWriter to dump status info to
     * @param warning[] if any whitespace diffs found
     *
     * @return true if pass, false if any problems encountered
     */
    boolean diff(Node gold, Node test, PrintWriter reporter,
                 boolean[] warning)
    {

        String name1 = gold.getLocalName();
        String name2 = test.getLocalName();

        // If both there but not equal, fail
        if ((null != name1) && (null != name2) &&!name1.equals(name2))
        {
            reporter.println(MISMATCH_NODE + nodeTypeString(gold) + SEPARATOR
                           + nodeTypeString(test) + SEPARATOR
                           + "name does not equal test node");

            return false;
        }
        else if ((null != name1) && (null == name2))
        {
            reporter.println(MISSING_TEST_NODE + nodeTypeString(gold)
                           + SEPARATOR + nodeTypeString(test) + SEPARATOR
                           + "name missing on test");

            return false;
        }
        else if ((null == name1) && (null != name2))
        {
            reporter.println(MISSING_GOLD_NODE + nodeTypeString(gold)
                           + SEPARATOR + nodeTypeString(test) + SEPARATOR
                           + "name missing on gold");

            return false;
        }

	String uri1 = gold.getNamespaceURI();
	String uri2 = test.getNamespaceURI();

        // If both there but not equal, fail
        if ((null != uri1) && (null != uri2) && !uri1.equals(uri2))
        {
            reporter.println(MISMATCH_NODE + nodeTypeString(gold) + SEPARATOR
                           + nodeTypeString(test) + SEPARATOR
                           + "namespace URI does not equal test node");

            return false;
        }
        else if ((null != uri1) && (null == uri2))
        {
            reporter.println(MISSING_TEST_NODE + nodeTypeString(gold)
                           + SEPARATOR + nodeTypeString(test) + SEPARATOR
                           + "namespace URI missing on test");

            return false;
        }
        else if ((null == uri1) && (null != uri2))
        {
            reporter.println(MISSING_GOLD_NODE + nodeTypeString(gold)
                           + SEPARATOR + nodeTypeString(test) + SEPARATOR
                           + "namespace URI missing on gold");

            return false;
        }

        String value1 = gold.getNodeValue();
        String value2 = test.getNodeValue();

        if ((null != value1) && (null != value2) &&!value1.equals(value2))
        {
            reporter.println(MISMATCH_VALUE + nodeTypeString(gold) + "len="
                           + value1.length() + SEPARATOR
                           + nodeTypeString(test) + "len=" + value2.length()
                           + SEPARATOR + "lengths do not match");

            // Limit length we output to logs; extremely long values 
            //  are more hassle than they're worth (at that point, 
            //  it's either obvious what the problem is, or it's 
            //  such a small problem that you'll need to manually
            //  compare the files separately
            if (value1.length() > maxDisplayLen)
                value1 = value1.substring(0, maxDisplayLen);
            if (value2.length() > maxDisplayLen)
                value2 = value2.substring(0, maxDisplayLen);
            reporter.println(MISMATCH_VALUE_GOLD + nodeTypeString(gold) + SEPARATOR + "\n" + value1);
            reporter.println(MISMATCH_VALUE_TEXT + nodeTypeString(test) + SEPARATOR + "\n" + value2);

            return false;
        }
        else if ((null != value1) && (null == value2))
        {
            reporter.println(MISSING_TEST_VALUE + nodeTypeString(gold) + "-"
                           + value1 + SEPARATOR + nodeTypeString(test)
                           + SEPARATOR + "test no value");

            return false;
        }
        else if ((null == value1) && (null != value2))
        {
            reporter.println(MISSING_GOLD_VALUE + nodeTypeString(gold)
                           + SEPARATOR + nodeTypeString(test) + "-" + value2
                           + SEPARATOR + "gold no value");

            return false;
        }

        switch (gold.getNodeType())
        {
        case Node.DOCUMENT_NODE :
        {

            // Why don't we do anything here? -sc
        }
        break;
        case Node.ELEMENT_NODE :
        {

            // Explicitly ignore attribute ordering
            // TODO do we need to make this settable for testing purposes? -sc
            NamedNodeMap goldAttrs = gold.getAttributes();
            NamedNodeMap testAttrs = test.getAttributes();

            if ((null != goldAttrs) && (null == testAttrs))
            {
                reporter.println(MISMATCH_ATTRIBUTE + nodeTypeString(gold)
                               + SEPARATOR + nodeTypeString(test) + SEPARATOR
                               + "test no attrs");

                return false;
            }
            else if ((null == goldAttrs) && (null != testAttrs))
            {
                reporter.println(MISMATCH_ATTRIBUTE + nodeTypeString(gold)
                               + SEPARATOR + nodeTypeString(test) + SEPARATOR
                               + "gold no attrs");

                return false;
            }

	    // Remove NS declarations from gold attribute list
	    Vector nsDeclarations = new Vector();
	    int length = goldAttrs.getLength();
	    for (int i = 0; i < length; i++) {
		final String name = goldAttrs.item(i).getNodeName();
		if (name.startsWith("xmlns")) {
		    nsDeclarations.addElement(name);
		}
	    }
	    length = nsDeclarations.size();
	    for (int i = 0; i < length; i++) {
		goldAttrs.removeNamedItem((String) nsDeclarations.elementAt(i));
	    }

	    // Remove NS declarations from test attribute list
	    nsDeclarations.removeAllElements(); // Use JDK 1.1.x methods
	    length = testAttrs.getLength();
	    for (int i = 0; i < length; i++) {
		final String name = testAttrs.item(i).getNodeName();
		if (name.startsWith("xmlns")) {
		    nsDeclarations.addElement(name);
		}
	    }
	    length = nsDeclarations.size();
	    for (int i = 0; i < length; i++) {
		testAttrs.removeNamedItem((String) nsDeclarations.elementAt(i));
	    }

            int gn = goldAttrs.getLength();
            int tn = testAttrs.getLength();

            if (gn != tn)
            {
                reporter.println(MISMATCH_ATTRIBUTE + nodeTypeString(gold)
                               + "-" + gn + SEPARATOR + nodeTypeString(test)
                               + "-" + tn + SEPARATOR
                               + "attribte count mismatch");

                // TODO: add output of each set of attrs for comparisons
                return false;
            }

            // TODO verify this checks the full list of attributes both ways, 
            //      from gold->test and from test->gold -sc
            for (int i = 0; i < gn; i++)
            {
                Attr goldAttr = (Attr) goldAttrs.item(i);
                String goldAttrLocalName = goldAttr.getLocalName();
                String goldAttrNamespaceURI = goldAttr.getNamespaceURI();
                String goldAttrName = goldAttr.getName();
                Node testAttr;
                if (goldAttrNamespaceURI != null && goldAttrLocalName != null) {
                        testAttr = testAttrs.getNamedItemNS(goldAttrNamespaceURI,goldAttrLocalName);
                } else {
                        testAttr = testAttrs.getNamedItem(goldAttrName);
                }

                if (null == testAttr)
                {
                    reporter.println(MISMATCH_ATTRIBUTE + nodeTypeString(gold)
                                   + "-" + goldAttrName + SEPARATOR
                                   + nodeTypeString(test) + SEPARATOR
                                   + "missing attribute on test");

                    return false;
                }

                if (!diff(goldAttr, testAttr, reporter, warning))
                {
                    return false;
                }
            }
        }
        break;
        case Node.CDATA_SECTION_NODE :{}
        break;
        case Node.ENTITY_REFERENCE_NODE :{}
        break;
        case Node.ATTRIBUTE_NODE :{}
        break;
        case Node.COMMENT_NODE :{}
        break;
        case Node.ENTITY_NODE :{}
        break;
        case Node.NOTATION_NODE :{}
        break;
        case Node.PROCESSING_INSTRUCTION_NODE :{}
        break;
        case Node.TEXT_NODE :{}
        break;
        default :{}
        }

        Node try2[] = new Node[2];
        Node goldChild = gold.getFirstChild();
        Node testChild = test.getFirstChild();

        if (!basicChildCompare(goldChild, testChild, reporter, warning, try2))
            return false;

        goldChild = try2[0];
        testChild = try2[1];

        while (null != goldChild)
        {
            if (!diff(goldChild, testChild, reporter, warning))
                return false;

            goldChild = goldChild.getNextSibling();
            testChild = testChild.getNextSibling();

            if (!basicChildCompare(goldChild, testChild, reporter, warning,
                                   try2))
                return false;

            goldChild = try2[0];
            testChild = try2[1];
        }

        return true;
    }  // end of diff()
}
