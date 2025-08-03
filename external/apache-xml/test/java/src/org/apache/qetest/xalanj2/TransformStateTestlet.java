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
 * TransformStateTestlet.java
 *
 */
package org.apache.qetest.xalanj2;

import java.util.Hashtable;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.qetest.Datalet;
import org.apache.qetest.Logger;
import org.apache.qetest.LoggingHandler;
import org.apache.qetest.QetestUtils;
import org.apache.qetest.TestletImpl;
import org.apache.qetest.XMLFileLogger;
import org.apache.xalan.templates.ElemTemplate;
import org.apache.xalan.templates.ElemTemplateElement;
import org.apache.xalan.transformer.TransformState;
import org.apache.xalan.transformer.TransformerClient;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Testlet for testing TransformState of a stylesheet.
 *
 * In progress - data-driven tests for tooling API's.
 * Currently uses cheap-o validation method
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class TransformStateTestlet extends TestletImpl
        implements ContentHandler, TransformerClient

{
    // Initialize our classname for TestletImpl's main() method
    static { thisClassName = "org.apache.qetest.xsl.TransformStateTestlet"; }

    // Initialize our defaultDatalet
    { defaultDatalet = (Datalet)new TransformStateDatalet(); }

    /** 
     * Class-wide copy of TransformStateDatalet.  
     * This is used in execute() and in various worker methods 
     * underneath the ContentHandler interface.
     */
    protected TransformStateDatalet tsDatalet = null;

    /**
     * Accesor method for a brief description of this test.  
     *
     * @return String describing what this TransformStateTestlet does.
     */
    public String getDescription()
    {
        return "TransformStateTestlet";
    }


    /**
     * Run this TransformStateTestlet: execute it's test and return.
     *
     * @param Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        try
        {
            tsDatalet = (TransformStateDatalet)d;
        }
        catch (ClassCastException e)
        {
            logger.checkErr("Datalet provided is not a TransformStateDatalet; cannot continue with " + d);
            return;
        }

        logger.logMsg(Logger.STATUSMSG, "About to test: " 
                      + (null == tsDatalet.inputName
                         ? tsDatalet.xmlName
                         : tsDatalet.inputName));
        try
        {
            // Perform the transform
            TransformerFactory factory = TransformerFactory.newInstance();
            logger.logMsg(Logger.TRACEMSG, "---- About to newTransformer " + QetestUtils.filenameToURL(tsDatalet.inputName));
            Transformer transformer = factory.newTransformer(new StreamSource(QetestUtils.filenameToURL(tsDatalet.inputName)));
            logger.logMsg(Logger.TRACEMSG, "---- About to transform " + QetestUtils.filenameToURL(tsDatalet.xmlName) + " into: SAXResult(this-no disk output)");

            // Note most validation happens here: we get ContentHandler 
            //  callbacks from being in the SAXResult, and that's where 
            //  we do our validation
            transformer.transform(new StreamSource(QetestUtils.filenameToURL(tsDatalet.xmlName)),
                                  new SAXResult(this)); // use us to handle result

            logger.logMsg(Logger.INFOMSG, "---- Afterwards, this.transformState=" + transformState);
        }
        catch (Throwable t)
        {
            // Put the logThrowable first, so it appears before 
            //  the Fail record, and gets color-coded
            logger.logThrowable(Logger.ERRORMSG, t, getDescription() + " " + tsDatalet.getDescription());
            logger.checkFail(getDescription() + " " + tsDatalet.getDescription() 
                             + " threw: " + t.toString());
            return;
        }
	}
    ////////////////// partially Implement LoggingHandler ////////////////// 
    /** Cheap-o string representation of last event we got.  */
    protected String lastItem = LoggingHandler.NOTHING_HANDLED;


    /**
     * Accessor for string representation of last event we got.  
     * @param s string to set
     */
    protected void setLastItem(String s)
    {
        lastItem = s;
    }


    /**
     * Accessor for string representation of last event we got.  
     * @return last event string we had
     */
    public String getLast()
    {
        return lastItem;
    }

    /** 
     * Worker routine to validate a TransformState based on an event/value.  
     * Note: this may not be threadsafe!
     * //@todo actually add validation code - just logs out now
     * @param ts TransformState to validate, if null, just logs it
     * @param event our String constant of START_ELEMENT, etc.
     * @param value any String value of the current event 
     */
    protected void validateTransformState(TransformState ts, String event, String value)
    {
        if(null == transformState)
        {
            // We should never have a null TransformState since the 
            //  transformer should have always filled it in
            logger.checkErr("validateTransformState(ts-NULL!, " + event + ")=" + value);
            return;
        }
        logTransformStateDump(logger, Logger.INFOMSG, ts, event, value);

        // Cheap-o validation: only validate items on column 99
        if (99 == ts.getCurrentElement().getColumnNumber())
        {
            int line = ts.getCurrentElement().getLineNumber();
            // Get cheap-o validation from the datalet for this line..
            String exp = (String)tsDatalet.validate99.get(line + ".current.name");
            // .. If there's an expected value for this line's property..
            if (null != exp)
                // .. Then check if it's equal and report pass/fail
                checkString(ts.getCurrentTemplate().getName().toString(), exp, 
                            "Validate L" + line + "C99 .current.name");

            exp = (String)tsDatalet.validate99.get(line + ".current.match");
            if (null != exp)
                checkString(ts.getCurrentTemplate().getMatch().getPatternString(), exp, 
                            "Validate L" + line + "C99 .current.match");

            exp = (String)tsDatalet.validate99.get(line + ".current.mode");
            if (null != exp)
                checkString(ts.getCurrentTemplate().getMode().toString(), exp, 
                            "Validate L" + line + "C99 .current.mode");


            exp = (String)tsDatalet.validate99.get(line + ".matched.name");
            if (null != exp)
                checkString(ts.getMatchedTemplate().getName().toString(), exp, 
                            "Validate L" + line + "C99 .matched.name");

            exp = (String)tsDatalet.validate99.get(line + ".matched.match");
            if (null != exp)
                checkString(ts.getMatchedTemplate().getMatch().getPatternString(), exp, 
                            "Validate L" + line + "C99 .matched.match");

            exp = (String)tsDatalet.validate99.get(line + ".matched.mode");
            if (null != exp)
                checkString(ts.getMatchedTemplate().getMode().toString(), exp, 
                            "Validate L" + line + "C99 .matched.mode");
        }

/***********************************************
// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
        // See if we have a matching expected state for this event
        //@todo use event string as part of hashkey!!!
        String marker = ExpectedTransformState.getHashKey(ts);
        // Add on the event as well; see ExpectedTransformState.getHashKey()
        //  for why we have to do this separately
        marker += ExpectedTransformState.SEP + event;
        ExpectedTransformState ets = (ExpectedTransformState)tsDatalet.expectedTransformStates.get(marker);
        logger.logMsg(Logger.TRACEMSG, "ETS-HACK:" + marker + "=" + ets);
        if (null != ets)
        {
            // Ask it to validate itself as needed
            synchronized(ets) // voodoo: attempt to solve hang problems
            {
                ExpectedObjectCheckService.check(logger, ts, ets, "Compare ExpectedTransformState of " + marker);
            }
        }
// Comment out validation using ExpectedObjects since they hang 28-Jun-01 -sc
***********************************************/
    }

    private void checkString(String act, String exp, String comment)
    {
        if (exp.equals(act))
            logger.checkPass(comment);
        else
            logger.checkFail(comment + "; act(" + act + ") exp(" + exp + ")");
    }

    ////////////////// Utility methods for TransformState ////////////////// 
    /**
     * Utility method to dump data from TransformState.  
     * @return String describing various bits of the state
     */
    protected void logTransformStateDump(Logger logger, int traceLoggingLevel, 
            TransformState ts, String event, String value)
    {
        String elemName = "transformStateDump";
        Hashtable attrs = new Hashtable();
        attrs.put("event", event);
        if (null != value)
            attrs.put("value", value);
        attrs.put("location", "L" + ts.getCurrentElement().getLineNumber()
                  + "C" + ts.getCurrentElement().getColumnNumber());

        StringBuffer buf = new StringBuffer();
        ElemTemplateElement elem = ts.getCurrentElement(); // may be actual or default template
        buf.append("  <currentElement>" 
                + XMLFileLogger.escapeString(XalanDumper.dump(elem, XalanDumper.DUMP_DEFAULT)) + "</currentElement>");

        ElemTemplate currentTempl = ts.getCurrentTemplate(); // Actual current template
        buf.append("\n  <currentTemplate>" 
                + XMLFileLogger.escapeString(XalanDumper.dump(currentTempl, XalanDumper.DUMP_DEFAULT)) + "</currentTemplate>");

        ElemTemplate matchTempl = ts.getMatchedTemplate(); // Actual matched template
        if (matchTempl != currentTempl)
            buf.append("\n  <matchedTemplate>" 
                + XMLFileLogger.escapeString(XalanDumper.dump(matchTempl, XalanDumper.DUMP_DEFAULT)) + "</matchedTemplate>");

        // Optimization: skip most logging when on endElement
        if (!END_ELEMENT.equals(event))
        {
            Node n = ts.getCurrentNode();   // current context node in source tree
            buf.append("\n  <currentNode>" 
                    + XMLFileLogger.escapeString(XalanDumper.dump(n, XalanDumper.DUMP_DEFAULT)) + "</currentNode>");

            Node matchedNode = ts.getMatchedNode(); // node in source matched via getMatchedTemplate
            // Optimization: only output if different
            if (n != matchedNode)
                buf.append("\n  <matchedNode>" 
                        + XMLFileLogger.escapeString(XalanDumper.dump(matchedNode, XalanDumper.DUMP_DEFAULT)) + "</matchedNode>");

            NodeIterator contextNodeList = ts.getContextNodeList(); // current context node list
            Node rootNode = contextNodeList.getRoot();
            // Optimization: only output if different
            if (n != rootNode)
                buf.append("\n  <contextNodeListGetRoot>" 
                        + XMLFileLogger.escapeString(XalanDumper.dump(rootNode, XalanDumper.DUMP_DEFAULT)) + "</contextNodeListGetRoot>");

            Transformer transformer = ts.getTransformer(); // current transformer working
            // Optimization: only dump transformer at startElement to save space
            if (START_ELEMENT.equals(event))
            {
                buf.append("\n  <transformer>" 
                        + XMLFileLogger.escapeString(XalanDumper.dump(transformer, XalanDumper.DUMP_DEFAULT)) + "</transformer>");
            }
            else
            {
                // Just log error case if transformer is ever null
                if (null == transformer) {
                    buf.append("\n  <transformer>"
                            + "ERROR! Transformer was null!" + "</transformer>");
                }
            }
        }

        logger.logElement(traceLoggingLevel, elemName, attrs, buf.toString());
    }

    //-----------------------------------------------------------
    //---- Implement the TransformerClient interface
    //-----------------------------------------------------------
    /**
     * A TransformState object that we use to log state data.
     * This is the equivalent of the defaultHandler, even though 
     * that's not really the right metaphor.  This class could be 
     * upgraded to have both a default ContentHandler and a 
     * defaultTransformerClient in the future.
     */
    protected TransformState transformState = null;


    /**
     * Implement TransformerClient.setTransformState interface.  
     * Pass in a reference to a TransformState object, which
     * can be used during SAX ContentHandler events to obtain
     * information about he state of the transformation. This
     * method will be called before each startDocument event.
     *
     * @param ts A reference to a TransformState object
     */
    public void setTransformState(TransformState ts)
    {
        transformState = ts;
    }

    //-----------------------------------------------------------
    //---- Implement the ContentHandler interface
    //-----------------------------------------------------------
    protected final String START_ELEMENT = "startElement:";
    protected final String END_ELEMENT = "endElement:";
    protected final String CHARACTERS = "characters:";

    // String Locator.getPublicId() null if none available
    // String Locator.getPublicId() null if none available
    // int Locator.getLineNumber() -1 if none available
    // int Locator.getColumnNumber() -1 if none available
    protected Locator ourLocator = null;
    
    /** 
     * Implement ContentHandler.setDocumentLocator.  
     * If available, this should always be called prior to a 
     * startDocument event.
     */
    public void setDocumentLocator (Locator locator)
    {
        // Note: this implies this class is !not! threadsafe
        ourLocator = locator; // future use
        if (null != locator)
            setLastItem("setDocumentLocator.getSystemId():" + locator.getSystemId());
        else
            setLastItem("setDocumentLocator:NULL");
        logger.logMsg(Logger.INFOMSG, getLast());
    }


    /** Cached TransformState object during lifetime startDocument -> endDocument.  */
    // Note: is this correct? Will it always be the same object?
    protected TransformState docCachedTransformState = null;
    /** Implement ContentHandler.startDocument.  */
    public void startDocument ()
        throws SAXException
    {
        setLastItem("startDocument");
        logger.logMsg(Logger.INFOMSG, getLast());
        // Comment out check call since the spec'd functionality
        //  is very likely to change to *not* be in startDocument 19-Jun-01 -sc 
        // logger.check((null != transformState), true, "transformState non-null in startDocument");
        logger.logMsg(Logger.STATUSMSG, "transformState in startDocument is: " + transformState);
        docCachedTransformState = transformState; // see endDocument
    }


    /** Implement ContentHandler.endDocument.  */
    public void endDocument()
        throws SAXException
    {
        setLastItem("endDocument");
        logger.logMsg(Logger.INFOMSG, getLast());
        // Comment out check call since the spec'd functionality
        //  is very likely to change to *not* be in startDocument 19-Jun-01 -sc 
        // logger.checkObject(docCachedTransformState, transformState, 
        //               "transformState same in endDocument as startDocument"); // see startDocument
        logger.logMsg(Logger.STATUSMSG, "transformState in endDocument is: " + transformState);
        docCachedTransformState = null;
    }


    /** Implement ContentHandler.startPrefixMapping.  */
    public void startPrefixMapping (String prefix, String uri)
        throws SAXException
    {
        setLastItem("startPrefixMapping: " + prefix + ", " + uri);
        logger.logMsg(Logger.INFOMSG, getLast());
    }


    /** Implement ContentHandler.endPrefixMapping.  */
    public void endPrefixMapping (String prefix)
        throws SAXException
    {
        setLastItem("endPrefixMapping: " + prefix);
        logger.logMsg(Logger.INFOMSG, getLast());
    }


    /** Implement ContentHandler.startElement.  */
    public void startElement (String namespaceURI, String localName,
                              String qName, Attributes atts)
        throws SAXException
    {
        StringBuffer buf = new StringBuffer();
        buf.append(namespaceURI + ", " 
                   + localName + ", " + qName + ";");
                   
        int n = atts.getLength();
        for(int i = 0; i < n; i++)
        {
            buf.append(", " + atts.getQName(i));
        }
        setLastItem(START_ELEMENT + buf.toString());

        validateTransformState(transformState, START_ELEMENT, buf.toString());
    }


    /** Implement ContentHandler.endElement.  */
    public void endElement (String namespaceURI, String localName, String qName)
        throws SAXException
    {
        setLastItem(END_ELEMENT + namespaceURI + ", " + localName + ", " + qName);

        validateTransformState(transformState, END_ELEMENT, null);
    }


    /** Implement ContentHandler.characters.  */
    public void characters (char ch[], int start, int length)
        throws SAXException
    {
        String s = new String(ch, start, length);
        setLastItem(CHARACTERS + "\"" + s + "\"");

        validateTransformState(transformState, CHARACTERS, s);
    }


    /** Implement ContentHandler.ignorableWhitespace.  */
    public void ignorableWhitespace (char ch[], int start, int length)
        throws SAXException
    {
        setLastItem("ignorableWhitespace: len " + length);
        logger.logMsg(Logger.INFOMSG, getLast());
    }


    /** Implement ContentHandler.processingInstruction.  */
    public void processingInstruction (String target, String data)
        throws SAXException
    {
        setLastItem("processingInstruction: " + target + ", " + data);
        logger.logMsg(Logger.INFOMSG, getLast());
    }


    /** Implement ContentHandler.skippedEntity.  */
    public void skippedEntity (String name)
        throws SAXException
    {
        setLastItem("skippedEntity: " + name);
        logger.logMsg(Logger.INFOMSG, getLast());
    }

}  // end of class TransformStateTestlet

