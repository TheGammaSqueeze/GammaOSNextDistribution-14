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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.qetest.QetestUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Uses an XML/HTML/Text diff comparator to check or diff two files.
 * <p>Given two files, an actual test result and a known good or 'gold'
 * test result, diff the two files to see if they are equal; if not, provide
 * some very basic info on where they differ.</p>
 *
 * <p>Attempts to parse each file as an XML document using Xerces;
 * if that fails, attempt to parse each as an HTML document using
 * <i>NEED NEW HTML PARSER</i>; if that fails, pretend to parse each
 * doc as text and construct a faux document node; then do 
 * readLine() and construct a &lt;line> element for each line.</p>
 *
 * <p>The comparison routine then recursively compares the two 
 * documents node-by-node; see the code for exactly how each 
 * node type is handled.  Note that some node types are currently 
 * ignored.</p>
 *
 * //@todo document whitespace difference handling better -sc
 * //@todo check how XML decls are handled (or not) -sc
 * //@todo Allow param to define the type of parse we do (i.e. if a
 * testwriter knows their output file will be XML, we should only
 * attempt to parse it as XML, not other types)
 * @see XHTComparatorXSLTC for an alternate implementation of 
 * diff() which tests some things as QNames (which checks for the 
 * true namespace, instead of just the prefix)
 * @author Scott_Boag@lotus.com
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class XHTComparator
{

    /** 
     * Maximum output length we may log for differing values.  
     * When two nodes have mismatched values, we output the first 
     * two values that were mismatched.  In some cases, this may be 
     * extremely long, so limit how much we output for convenience.
     */
    protected int maxDisplayLen = 511;  // arbitrary length, for convenience

    /**
     * Accessor method for maxDisplayLen.
     * @param i maximum length we log out
     */
    public void setMaxDisplayLen(int i)
    {
        if (i > 0)
            maxDisplayLen = i;
    }

    /** Constants for reporting out reason for failed diffs. */
    public static final String SEPARATOR = ";";

    /** LBRACKET '['  */
    public static final String LBRACKET = "[";

    /** RBRACKET ']'  */
    public static final String RBRACKET = "]";

    /** TEST 'test', for the actual value.  */
    public static final String TEST = "test";

    /** GOLD 'gold' for the gold or expected value.  */
    public static final String GOLD = "gold";

    /** PARSE_TYPE '-parse-type' */
    public static final String PARSE_TYPE = "-parse-type" + SEPARATOR;  // postpended to TEST or GOLD

    /** OTHER_ERROR 'other-error'  */
    public static final String OTHER_ERROR = "other-error" + SEPARATOR;

    /** WARNING 'warning'  */
    public static final String WARNING = "warning" + SEPARATOR;

    /** MISMATCH_NODE  */
    public static final String MISMATCH_NODE = "mismatch-node" + SEPARATOR;

    /** MISSING_TEST_NODE  */
    public static final String MISSING_TEST_NODE = "missing-node-" + TEST
                                                       + SEPARATOR;

    /** MISSING_GOLD_NODE  */
    public static final String MISSING_GOLD_NODE = "missing-node-" + GOLD
                                                       + SEPARATOR;

    /** MISMATCH_ATTRIBUTE */
    public static final String MISMATCH_ATTRIBUTE = "mismatch-attribute"
                                                        + SEPARATOR;

    /** MISMATCH_VALUE  */
    public static final String MISMATCH_VALUE = "mismatch-value" + SEPARATOR;

    /** MISMATCH_VALUE  */
    public static final String MISMATCH_VALUE_GOLD = "mismatch-value-gold" + SEPARATOR;

    /** MISMATCH_VALUE  */
    public static final String MISMATCH_VALUE_TEXT = "mismatch-value-text" + SEPARATOR;

    /** MISSING_TEST_VALUE  */
    public static final String MISSING_TEST_VALUE = "missing-value-" + TEST
                                                        + SEPARATOR;

    /** MISSING_GOLD_VALUE  */
    public static final String MISSING_GOLD_VALUE = "missing-value-" + GOLD
                                                        + SEPARATOR;

    /** WHITESPACE_DIFF  */
    public static final String WHITESPACE_DIFF = "whitespace-diff;";

    /**
     * Compare two files by parsing into DOMs and comparing trees.
     *
     * <p>Parses the goldFileName by using the 
     * {@link #parse(String, PrintWriter, String, Properties) parse worker method}
     * - if null, we bail and return false.  If non-null, we parse the 
     * testFileName into a Document as well.  Then we call 
     * {@link #diff(Node, Node, PrintWriter, boolean[]) diff worker method} 
     * to do the real work of comparing.</p>
     *
     * @param goldFileName expected file
     * @param testFileName actual file
     * @param reporter PrintWriter to dump status info to
     * @param warning array of warning flags (for whitespace diffs, 
     * item[0] is set to true if we find whitespace-only diffs)
     * @param attributes to attempt to set onto parsers
     * @return true if they match, false otherwise
     */
    public boolean compare(String goldFileName, String testFileName,
                           PrintWriter reporter, boolean[] warning,
                           Properties attributes)
    {

        // parse the gold doc
        Document goldDoc = parse(goldFileName, reporter, GOLD, attributes);

        // parse the test doc only if gold doc was parsed OK
        //@todo Jun-02 -sc Note the logic here might be improveable to 
        //  actually report file missing problems better: i.e. 
        //  in theory, if the actual is missing, it's a fail; if 
        //  the gold (only) is missing, it's ambiguous
        Document testDoc = (null != goldDoc)
                           ? parse(testFileName, reporter, TEST, attributes) : null;

        if (null == goldDoc)
        {
            reporter.println(OTHER_ERROR + GOLD + SEPARATOR
                             + "document null");

            return false;
        }
        else if (null == testDoc)
        {
            reporter.println(OTHER_ERROR + TEST + SEPARATOR
                             + "document null");

            return false;
        }

        return diff(goldDoc, testDoc, reporter, warning);
    }

    // Reporter format:
    // REASON_CONSTANT;gold val;test val;reason description

    /**
     * Diff two Nodes recursively and report true if equal.  
     *
     * <p>The contract is: when you enter here the gold and test nodes are the same type,
     * both non-null, and both in the same basic position in the tree.
     * //@todo verify caller really performs for the contract -sc</p>
     *
     * <p>See the code for how it's done; note that not all node 
     * types are actually compared currently.  Also see 
     * {@link XHTComparatorXSLTC} for an alternate implementation.</p>
     *
     * @param gold or expected node
     * @param test actual node
     * @param reporter PrintWriter to dump status info to
     * @param warning[] if any whitespace diffs found
     *
     * @return true if pass, false if any problems encountered
     */
    boolean diff(Node gold, Node test, PrintWriter reporter,
                 boolean[] warning)
    {

        String name1 = gold.getNodeName();
        String name2 = test.getNodeName();

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

        String value1 = gold.getNodeValue();
        String value2 = test.getNodeValue();

        if ((null != value1) && (null != value2) &&!value1.equals(value2))
        {
            reporter.println(MISMATCH_VALUE + nodeTypeString(gold) + "len="
                           + value1.length() + SEPARATOR
                           + nodeTypeString(test) + "len=" + value2.length()
                           + SEPARATOR + "values do not match");
            printNodeDiff(gold, test, reporter);
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
                String goldAttrName = goldAttr.getName();
                Node testAttr = testAttrs.getNamedItem(goldAttrName);

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

    /**
     * Returns Character.isWhitespace
     * @param s String to check for whitespace
     * @return true if all whitespace; false otherwise
     */
    boolean isWhiteSpace(String s)
    {

        int n = s.length();

        for (int i = 0; i < n; i++)
        {
            if (!Character.isWhitespace(s.charAt(i)))
                return false;
        }

        return true;
    }  // end of isWhiteSpace()

    /**
     * NEEDSDOC Method tryToAdvancePastWhitespace 
     *
     *
     * @param n node to check if it's whitespace
     * @param reporter PrintWriter to dump status info to
     * @param warning set to true if we advance past a 
     * whitespace node; note that this logic isn't quite 
     * correct, I think (it should only be set if 
     * we advance past whitespace that isn't equal in 
     * both trees or something like that)
     * @param next array of nodes to continue thru
     * @param which index into next array
     *
     * @return Node we should be at after advancing
     */
    Node tryToAdvancePastWhitespace(Node n, PrintWriter reporter,
                                    boolean[] warning, Node next[], int which)
    {

        if (n.getNodeType() == Node.TEXT_NODE)
        {
            String data = n.getNodeValue();

            if (null != data)
            {
                if (isWhiteSpace(data))
                {
                    warning[0] = true;

                    reporter.print(WHITESPACE_DIFF + " ");  // TODO check the format of this; maybe use println -sc

                    n = n.getNextSibling();
                    next[which] = n;
                }
            }
        }

        return n;
    }  // end of tryToAdvancePastWhitespace()

    /**
     * NEEDSDOC Method basicChildCompare 
     *
     *
     * NEEDSDOC @param gold
     * NEEDSDOC @param test
     * @param reporter PrintWriter to dump status info to
     * NEEDSDOC @param warning
     * NEEDSDOC @param next
     *
     * NEEDSDOC (basicChildCompare) @return
     */
    boolean basicChildCompare(Node gold, Node test, PrintWriter reporter,
                              boolean[] warning, Node next[])
    {

        next[0] = gold;
        next[1] = test;

        boolean alreadyTriedToAdvance = false;

        if ((null != gold) && (null == test))
        {
            gold = tryToAdvancePastWhitespace(gold, reporter, warning, next,
                                              0);
            alreadyTriedToAdvance = true;

            if ((null != gold) && (null == test))
            {
                reporter.println(MISSING_TEST_NODE + nodeTypeString(gold)
                               + SEPARATOR + SEPARATOR
                               + "missing node on test");

                return false;
            }
        }
        else if ((null == gold) && (null != test))
        {
            test = tryToAdvancePastWhitespace(test, reporter, warning, next,
                                              1);
            alreadyTriedToAdvance = true;

            if ((null == gold) && (null != test))
            {
                reporter.println(MISSING_GOLD_NODE + SEPARATOR
                               + nodeTypeString(test) + SEPARATOR
                               + "missing node on gold");

                return false;
            }
        }

        if ((null != gold) && (gold.getNodeType() != test.getNodeType()))
        {
            Node savedGold = gold;
            Node savedTest = test;

            if (!alreadyTriedToAdvance)
            {
                gold = tryToAdvancePastWhitespace(gold, reporter, warning,
                                                  next, 0);

                if (gold == savedGold)
                {
                    test = tryToAdvancePastWhitespace(test, reporter,
                                                      warning, next, 1);
                }
            }

            if ((null != gold) && (gold.getNodeType() != test.getNodeType()))
            {
                gold = savedGold;
                test = savedTest;

                reporter.println(MISMATCH_NODE + nodeTypeString(gold)
                               + SEPARATOR + nodeTypeString(test) + SEPARATOR
                               + "node type mismatch");
                printNodeDiff(gold, test, reporter);

                return false;
            }
        }

        return true;
    }  // end of basicChildCompare()

    /**
     * Cheap-o text printout of a node.  By Scott.  
     *
     * @param n node to print info for
     * @return String of getNodeType plus getNodeName
     */
    public static String nodeTypeString(Node n)
    {
        switch (n.getNodeType())
        {
        case Node.DOCUMENT_NODE :
            return "DOCUMENT(" + n.getNodeName() + ")";
        case Node.ELEMENT_NODE :
            return "ELEMENT(" + n.getNodeName() + ")";
        case Node.CDATA_SECTION_NODE :
            return "CDATA_SECTION(" + n.getNodeName() + ")";
        case Node.ENTITY_REFERENCE_NODE :
            return "ENTITY_REFERENCE(" + n.getNodeName() + ")";
        case Node.ATTRIBUTE_NODE :
            return "ATTRIBUTE(" + n.getNodeName() + ")";
        case Node.COMMENT_NODE :
            return "COMMENT(" + n.getNodeName() + ")";
        case Node.ENTITY_NODE :
            return "ENTITY(" + n.getNodeName() + ")";
        case Node.NOTATION_NODE :
            return "NOTATION(" + n.getNodeName() + ")";
        case Node.PROCESSING_INSTRUCTION_NODE :
            return "PROCESSING_INSTRUCTION(" + n.getNodeName() + ")";
        case Node.TEXT_NODE :
            return "TEXT()"; // #text is all that's ever printed out, so skip it
        default :
            return "UNKNOWN(" + n.getNodeName() + ")";
        }
    }  // end of nodeTypeString()


    /**
     * Cheap-o text printout of two different nodes.  
     *
     * @param goldNode or expected node to print info
     * @param testNode or actual node to print info
     * @param n node to print info for
     * @param reporter PrintWriter to dump status info to
     */
    public void printNodeDiff(Node goldNode, Node testNode, PrintWriter reporter)
    {
        String goldValue = goldNode.getNodeValue();
        String testValue = testNode.getNodeValue();
        if (null == goldValue)
            goldValue = "null";
        if (null == testValue)
            testValue = "null";

        // Limit length we output to logs; extremely long values 
        //  are more hassle than they're worth (at that point, 
        //  it's either obvious what the problem is, or it's 
        //  such a small problem that you'll need to manually
        //  compare the files separately
        if (goldValue.length() > maxDisplayLen)
            goldValue = goldValue.substring(0, maxDisplayLen);
        if (testValue.length() > maxDisplayLen)
            testValue = testValue.substring(0, maxDisplayLen);
        reporter.println(MISMATCH_VALUE_GOLD + nodeTypeString(goldNode) + SEPARATOR + "\n" + goldValue);
        reporter.println(MISMATCH_VALUE_TEXT + nodeTypeString(testNode) + SEPARATOR + "\n" + testValue);
    }


    /**
     * Simple worker method to parse filename to a Document.  
     *
     * <p>Attempts XML parse, if that throws an exception, then 
     * we attempt an HTML parse (when parser available), if 
     * that throws an exception, then we parse as text: 
     * we construct a faux document element to hold it all, 
     * and then parse by readLine() and put each line of 
     * text into a &lt;line> element.</p>
     *
     * @param filename to parse as a local path
     * @param reporter PrintWriter to dump status info to
     * @param which either TEST or GOLD file being parsed
     * @param attributes name=value pairs to set on the 
     * DocumentBuilderFactory that we use to parse
     *
     * @return Document object with contents of the file; 
     * otherwise throws an unchecked RuntimeException if there 
     * is any fatal problem
     */
    Document parse(String filename, PrintWriter reporter, String which, Properties attributes)
    {
        // Force filerefs to be URI's if needed: note this is independent of any other files
        String docURI = QetestUtils.filenameToURL(filename);
        
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        // Always set namespaces on
        dfactory.setNamespaceAware(true);
        // Set other attributes here as needed
        applyAttributes(dfactory, attributes);
        
        // Local class: cheap non-printing ErrorHandler
        // This is used to suppress validation warnings which 
        //  would otherwise clutter up the console
        ErrorHandler nullHandler = new ErrorHandler() {
            public void warning(SAXParseException e) throws SAXException {}
            public void error(SAXParseException e) throws SAXException {}
            public void fatalError(SAXParseException e) throws SAXException 
            {
                throw e;
            }
        };

        String parseType = which + PARSE_TYPE + "[xml];";
        Document doc = null;
        try
        {
            // First, attempt to parse as XML (preferred)...
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            docBuilder.setErrorHandler(nullHandler);
            doc = docBuilder.parse(new InputSource(docURI));
        }
        catch (Throwable se)
        {
            // ... if we couldn't parse as XML, attempt parse as HTML...
            reporter.println(WARNING + se.toString());
            parseType = which + PARSE_TYPE + "[html];";

            try
            {
                // Use the copy of Tidy that the XSLTC team has checked in
                // Submitted by: Gunnar Klauberg <gklauberg@yahoo.de>
                // Alternate by: Santiago.PericasGeertsen@sun.com
    	        Tidy tidy = new Tidy();
    	        tidy.setXHTML(true);
    	        tidy.setTidyMark(false);
    	        tidy.setShowWarnings(false);
                tidy.setShowErrors(0);
    	        tidy.setQuiet(true);
    	        doc = tidy.parseDOM(new URL(docURI).openStream(), null);
            }
            catch (Exception e)
            {
                // ... if we can't parse as HTML, then just parse the text
                try
                {
                    reporter.println(WARNING + e.toString());
                    parseType = which + PARSE_TYPE + "[text];";

                    // First build a faux document with parent element
                    DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
                    doc = docBuilder.newDocument();
                    Element outElem = doc.createElement("out");

                    // Parse as text, line by line
                    //   Since we already know it should be text, this should 
                    //   work better than parsing by bytes.
                    FileReader fr = new FileReader(filename);
                    BufferedReader br = new BufferedReader(fr);
                    for (;;)
                    {
                        String tmp = br.readLine();

                        if (tmp == null)
                        {
                            break;
                        }
                        // An additional thing we could do would 
                        //  be to put in the line number in the 
                        //  file in here somehow, so when users 
                        //  view reports, they get that info
                        Element lineElem = doc.createElement("line");
                        outElem.appendChild(lineElem);
                        Text textNode = doc.createTextNode(tmp);
                        lineElem.appendChild(textNode);
                    }
                    // Now stick the whole element into the document to return
                    doc.appendChild(outElem);
                }
                catch (Throwable throwable)
                {
                    reporter.println(OTHER_ERROR + filename + SEPARATOR
                                   + "threw:" + throwable.toString());
                }
            }
        }

        // Output a newline here for readability
        reporter.println(parseType);

        return doc;
    }  // end of parse()
    
    /**
     * Pass applicable attributes onto our DocumentBuilderFactory.  
     *
     * Only passes thru attributes we explicitly know about and 
     * are constants from XHTFileCheckService.
     * 
     * @param dbf factory to attempt to set* onto
     * @param attrs various attributes we should try to set
     */
    protected void applyAttributes(DocumentBuilderFactory dfactory, Properties attributes)
    {
        if ((null == attributes) || (null == dfactory))
            return;

        String tmp = attributes.getProperty(XHTFileCheckService.SETVALIDATING);
        if (null != tmp)
        {
            dfactory.setValidating(new Boolean(tmp).booleanValue());
        }
        tmp = attributes.getProperty(XHTFileCheckService.SETIGNORINGELEMENTCONTENTWHITESPACE);
        if (null != tmp)
        {
            dfactory.setIgnoringElementContentWhitespace(new Boolean(tmp).booleanValue());
        }
        tmp = attributes.getProperty(XHTFileCheckService.SETEXPANDENTITYREFERENCES);
        if (null != tmp)
        {
            dfactory.setExpandEntityReferences(new Boolean(tmp).booleanValue());
        }
        tmp = attributes.getProperty(XHTFileCheckService.SETIGNORINGCOMMENTS);
        if (null != tmp)
        {
            dfactory.setIgnoringComments(new Boolean(tmp).booleanValue());
        }
        tmp = attributes.getProperty(XHTFileCheckService.SETCOALESCING);
        if (null != tmp)
        {
            dfactory.setCoalescing(new Boolean(tmp).booleanValue());
        }
        /* Unknown attributes are ignored! */
    }

}
