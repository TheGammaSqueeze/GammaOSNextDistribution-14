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

// imports needed for reproducing the bug
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Testlet for reproducing Bugzilla reported bugs.
 *
 * Reported-by: slobo@matavnet.hu
 */
public class Bugzilla1251 extends TestletImpl
{
    // Initialize our classname for TestletImpl's main() method - must be updated!
    static { thisClassName = "Bugzilla1251"; }

    /**
     * Reproduce a Bugzilla bug report.
     * @param d (optional) Datalet to use as data point for the test.
     */
    public void execute(Datalet d)
	{
        // Use logger.logMsg(...) instead of System.out.println(...)
        logger.logMsg(Logger.STATUSMSG, "Reproducing Bugzilla#1251: TransformerHandler with SAXResult mishandles exceptions");

        try {
            logger.logMsg(Logger.STATUSMSG, "Using an identity transformer should work...");
            SAXTransformerFactory f = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
            TransformerHandler th = f.newTransformerHandler();
            th.setResult(new SAXResult(new Bugzilla1251Handler(logger, "CH1")));
            Transformer t = th.getTransformer();
            t.setErrorListener(new Bugzilla1251Handler(logger, "EL1"));
            th.startDocument();
            th.startElement("","foo","foo",new AttributesImpl());
            th.endElement("","foo","foo");
            th.endDocument();
        } catch(Throwable t) {
            logger.checkFail("Should not have thrown exception!");
            logger.logThrowable(Logger.ERRORMSG, t, "Should not have thrown exception!");
        }

        try {
            logger.logMsg(Logger.STATUSMSG, "But using a real transformer does not...");
            SAXTransformerFactory f = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
            TransformerHandler th = f.newTransformerHandler(new StreamSource("identity.xsl"));
            th.setResult(new SAXResult(new Bugzilla1251Handler(logger, "CH2")));
            Transformer t = th.getTransformer();
            t.setErrorListener(new Bugzilla1251Handler(logger, "EL2"));
            th.startDocument();
            th.startElement("","foo","foo",new AttributesImpl());
            th.endElement("","foo","foo");
            th.endDocument();
        } catch(Throwable t) {
            logger.checkFail("Should not have thrown exception!");
            logger.logThrowable(Logger.ERRORMSG, t, "Should not have thrown exception!");
        }
	}

    /**
     * <a href="http://nagoya.apache.org/bugzilla/show_bug.cgi?id=1251">
     * Link to Bugzilla</a>
     * @return TransformerHandler with SAXResult mishandles exceptions.
     */
    public String getDescription()
    {
        return "TransformerHandler with SAXResult mishandles exceptions";
    }

    class Bugzilla1251Handler extends DefaultHandler implements ErrorListener {

        private Logger m_logger = null;
        private String m_id = null; // Not strictly needed, I've over-instrumented this a bit -sc
        private int m_ctr = 0;
        public Bugzilla1251Handler(Logger l, String id) {
            m_logger = l;
            m_id = id;
        }

        // Moved to separate class: for main() method, see Bugzilla1251.execute()
            public void startElement(String namespaceURI,String localName,String qName,Attributes atts) throws SAXException {
                m_logger.logMsg(Logger.STATUSMSG, "Entering(" + m_id + ") startElement(" 
                                + namespaceURI + ", "
                                + localName + ", "
                                + qName + ", "
                                + ") and throwing an Exception..");
                m_ctr++;
                if (m_ctr > 1)
                    m_logger.checkFail("Entered(" + m_id + ") startElement more than once! " + m_ctr);

                // This is really what's being tested: if a Handler 
                //  throws an exception, does Xalan propagate it 
                //  to the correct places?
                throw new SAXException("Should stop processing");
            }

            public void warning(TransformerException e) throws TransformerException 
            {
                m_logger.checkPass("Entering(" + m_id + ") warning()");
                m_logger.logThrowable(Logger.WARNINGMSG, e, "Entering(" + m_id + ") warning()");
                //throw e;
            }

            public void error(TransformerException e) throws TransformerException {
                m_logger.checkPass("Entering(" + m_id + ") error()");
                m_logger.logThrowable(Logger.WARNINGMSG, e, "Entering(" + m_id + ") error()");
                //throw e;
            }

            public void fatalError(TransformerException e) throws TransformerException {
                m_logger.checkPass("Entering(" + m_id + ") fatalError()");
                m_logger.logThrowable(Logger.WARNINGMSG, e, "Entering(" + m_id + ") fatalError()");
                //throw e;
            }
    }

}  // end of class BugzillaTestlet

