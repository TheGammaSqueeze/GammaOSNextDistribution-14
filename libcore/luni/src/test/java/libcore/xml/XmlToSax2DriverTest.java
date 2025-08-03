/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package libcore.xml;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import junit.framework.TestCase;
import org.junit.Assert;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.sax2.Driver;

public class XmlToSax2DriverTest extends TestCase {

    private static final String XML =
            "<note type=\"email\" foo=\"bar\">"
            + "<to>John</to>"
            + "<from>Smith</from>"
            + "<heading>Lunch today</heading>"
            + "<body>Hi, shall we go to lunch at 12?</body>"
            + "</note>";

    private Driver driver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        driver = new Driver();
    }

    public void testConstructor() {
        Driver driver = null;
        try {
            driver = new Driver();
        } catch (XmlPullParserException e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        assertEquals(0, driver.getLength());
        assertEquals(1, driver.getColumnNumber());
        assertEquals(1, driver.getLineNumber());
    }

    public void testConstructor_parametrized() {
        XmlPullParserFactory factory;
        XmlPullParser parser = null;

        try {
            factory = XmlPullParserFactory.newInstance(null, null);
            parser = factory.newPullParser();
        } catch (XmlPullParserException e) {
            fail("Couldn't create factory and parser");
        }
        Driver driver = null;

        try {
             driver = new Driver(parser);
        } catch (XmlPullParserException e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        assertEquals(0, driver.getLength());
        assertEquals(1, driver.getColumnNumber());
        assertEquals(1, driver.getLineNumber());
    }

    public void testGetColumnNumber() {
        assertEquals(driver.getColumnNumber(), 1);
    }

    public void testSetProperty() throws Exception {
        assertThrows(SAXNotSupportedException.class , () -> driver.setProperty(
                "http://xml.org/sax/properties/declaration-handler", // DECLARATION_HANDLER_PROPERTY
                ""));

        assertThrows(SAXNotSupportedException.class ,() -> driver.setProperty(
                "http://xml.org/sax/properties/lexical-handler", // LEXICAL_HANDLER_PROPERTY
                ""));

        // This may be the only key accpeted by the KXmlParser.
        String key = "http://xmlpull.org/v1/doc/properties.html#location";
        driver.setProperty(key, "123");
        assertEquals("123", driver.getProperty(key));

        assertThrows(SAXNotSupportedException.class ,() -> driver.setProperty("abc", ""));
    }

    public void testGetSetContentHandler() throws XmlPullParserException {
        assertTrue(driver.getContentHandler() instanceof DefaultHandler);

        ContentHandler handler = new DefaultHandler();
        driver.setContentHandler(handler);
        assertEquals(driver.getContentHandler(), handler);

        driver.setContentHandler(null);
        assertNull(driver.getContentHandler());
    }

    public void testGetSetDTDHandler() {
        assertNull(driver.getDTDHandler());

        driver.setDTDHandler(new DefaultHandler());
        assertNull(driver.getDTDHandler());

        driver.setDTDHandler(null);
        assertNull(driver.getDTDHandler());
    }

    public void testGetSetEntityResolver() {
        assertNull(driver.getEntityResolver());

        driver.setEntityResolver(new DefaultHandler());
        assertNull(driver.getEntityResolver());

        driver.setEntityResolver((publicId, systemId) -> null);
        assertNull(driver.getEntityResolver());

        driver.setEntityResolver(null);
        assertNull(driver.getEntityResolver());
    }

    public void testGetSetErrorHandler() {
        assertTrue(driver.getContentHandler() instanceof DefaultHandler);

        ErrorHandler handler = new DefaultHandler();
        driver.setErrorHandler(handler);
        assertEquals(handler, driver.getErrorHandler());

        driver.setErrorHandler(null);
        assertNull(driver.getErrorHandler());
    }

    public void testGetSetFeature() throws SAXNotSupportedException, SAXNotRecognizedException {
        final String NAMESPACES_FEATURE = "http://xml.org/sax/features/namespaces";
        final String NAMESPACE_PREFIXES_FEATURE = "http://xml.org/sax/features/namespace-prefixes";
        final String VALIDATION_FEATURE = "http://xml.org/sax/features/validation";
        final String PROCESS_DOCDECL_FEATURE =
                "http://xmlpull.org/v1/doc/features.html#process-docdecl";
        final String REPORT_NAMESPACE_ATTRIBUTES_FEATURE =
                "http://xmlpull.org/v1/doc/features.html#report-namespace-prefixes";
        final String RELAXED_FEATURE = "http://xmlpull.org/v1/doc/features.html#relaxed";

        final Object[][] expectations = {
                {NAMESPACE_PREFIXES_FEATURE, false},
                {VALIDATION_FEATURE, false},
                {PROCESS_DOCDECL_FEATURE, false},
                {REPORT_NAMESPACE_ATTRIBUTES_FEATURE, false},
                {NAMESPACES_FEATURE, true},
        };

        for (Object[] f : expectations) {
            final String feature = (String) f[0];
            final boolean result = (boolean) f[1];
            try {
                assertEquals(result, driver.getFeature(feature));
            } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
                fail("Unexpected exception: " + e.getMessage());
            }
        }

        final String[] settable = {
                NAMESPACES_FEATURE,
                PROCESS_DOCDECL_FEATURE,
                RELAXED_FEATURE,
        };
        for (String feature : settable) {
            for (boolean value : new boolean[]{ false, true }) {
                driver.setFeature(feature, value);
                assertEquals(feature, value, driver.getFeature(feature));
            }
        }
    }

    public void testGetIndex() throws NoSuchFieldException, IllegalAccessException {
        assertEquals(-1, driver.getIndex("hello"));
        assertEquals(-1, driver.getIndex("encoding"));
        assertEquals(-1, driver.getIndex("version"));
    }

    public void testGetIndex_namespaced() {
        assertEquals(-1, driver.getIndex("", "version"));
    }

    public void testGetLength() {
        assertEquals(0, driver.getLength());
    }

    public void testGetLineNumber() {
        assertEquals(1, driver.getLineNumber());
    }

    public void testGetLocalName() {
        try {
            driver.getLocalName(0);
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    public void testGetProperty() {
        try {
            driver.getProperty("");
        } catch (IndexOutOfBoundsException e) {
            // expected
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    public void testGetPublicId() {
        assertNull(driver.getPublicId());
    }

    public void testGetQName() {
        try {
            driver.getQName(0);
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    public void testGetSystemId() {
        assertNull(driver.getSystemId());
    }

    public void testGetType() {
        assertEquals("CDATA", driver.getType(0));
        assertNull(driver.getType("value"));
        assertNull(driver.getType("", "value"));
    }

    public void testGetUri() {
        try {
            driver.getURI(0);
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    public void testGetValue() {
        try {
            driver.getValue(0);
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
        assertNull("CDATA", driver.getValue("value"));
        assertNull("CDATA", driver.getValue("", "value"));
    }

    public void testParse_String() {
        try {
            driver.parse("systemId");
        } catch (SAXException | IOException e) {
            // expected
        }

        String systemId = null;
        try {
            driver.parse(systemId);
        } catch (SAXException | IOException e) {
            // expected
        }
    }

    public void testParse_InputSource() throws IOException, SAXException {
        InputSource source = new InputSource();
        source.setCharacterStream(new StringReader(XML));
        source.setSystemId("systemId");

        final int[] errors = {0, 0, 0};
        driver.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                errors[0]++;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                errors[1]++;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                errors[2]++;
            }
        });

        // Four events counter: { "startDocument", "endDocument", "startElement", "endElement" }
        final int[] events = {0, 0, 0, 0};
        final ArrayList<String> tagsEncountered = new ArrayList<>();
        final ArrayList<String> textsEncountered = new ArrayList<>();
        driver.setContentHandler(new DefaultHandler() {
            @Override
            public void startDocument() throws SAXException {
                super.startDocument();
                events[0]++;
            }

            @Override
            public void endDocument() throws SAXException {
                super.endDocument();
                events[1]++;
            }

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) throws SAXException {
                super.startElement(uri, localName, qName, attributes);
                events[2]++;
                tagsEncountered.add(localName);
                if ("note".equals(localName)) {
                    assertEquals(2, attributes.getLength());
                    assertEquals("type", attributes.getLocalName(0));
                    assertEquals("email", attributes.getValue(0));
                    assertEquals("foo", attributes.getLocalName(1));
                    assertEquals("bar", attributes.getValue(1));
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                super.characters(ch, start, length);
                textsEncountered.add(StringFactory.newStringFromChars(ch, start, length));
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                super.endElement(uri, localName, qName);
                events[3]++;
            }
        });
        driver.parse(source);

        assertEquals("systemId", driver.getSystemId());
        Assert.assertArrayEquals(new int[]{0, 0, 0}, errors);
        Assert.assertArrayEquals(new int[]{1, 1, 5, 5}, events);
        Assert.assertArrayEquals(new String[]{"note", "to", "from", "heading", "body"},
                tagsEncountered.toArray());
        Assert.assertArrayEquals(new String[]{
                "John", "Smith", "Lunch today", "Hi, shall we go to lunch at 12?"
        }, textsEncountered.toArray());
    }

    public void testParseSubtree() throws XmlPullParserException, IOException {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(XML));

        final int[] errors = {0, 0, 0};
        driver.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                errors[0]++;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                errors[1]++;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                errors[2]++;
            }
        });

        // Four events counter: { "startDocument", "endDocument", "startElement", "endElement" }
        final int[] events = {0, 0, 0, 0};
        final ArrayList<String> tagsEncountered = new ArrayList<>();
        final ArrayList<String> textsEncountered = new ArrayList<>();
        driver.setContentHandler(new DefaultHandler() {
            @Override
            public void startDocument() throws SAXException {
                super.startDocument();
                events[0]++;
            }

            @Override
            public void endDocument() throws SAXException {
                super.endDocument();
                events[1]++;
            }

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) throws SAXException {
                super.startElement(uri, localName, qName, attributes);
                events[2]++;
                tagsEncountered.add(localName);
                if ("note".equals(localName)) {
                    assertEquals(2, attributes.getLength());
                    assertEquals("type", attributes.getLocalName(0));
                    assertEquals("email", attributes.getValue(0));
                    assertEquals("foo", attributes.getLocalName(1));
                    assertEquals("bar", attributes.getValue(1));
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                super.characters(ch, start, length);
                textsEncountered.add(StringFactory.newStringFromChars(ch, start, length));
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                super.endElement(uri, localName, qName);
                events[3]++;
            }
        });

        try {
            driver.parseSubTree(parser);
        } catch (SAXException e) {
            // expected, as START_TAG should have been read already
        } catch (IOException e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertEquals(XmlPullParser.START_TAG, parser.next());
        try {
            driver.parseSubTree(parser);
        } catch (SAXException | IOException e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        Assert.assertArrayEquals(new int[]{0, 0, 0}, errors);
        Assert.assertArrayEquals(new int[]{0, 0, 5, 5}, events);
        Assert.assertArrayEquals(new String[]{"note", "to", "from", "heading", "body"},
                tagsEncountered.toArray());
        Assert.assertArrayEquals(new String[]{
                "John", "Smith", "Lunch today", "Hi, shall we go to lunch at 12?"
        }, textsEncountered.toArray());
    }

    public void testStartElement() throws XmlPullParserException, IOException, SAXException {
        boolean[] called = {false};
        ContentHandler handler = new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) throws SAXException {
                super.startElement(uri, localName, qName, attributes);
                called[0] = true;
            }
        };
        ExtendsDriver d = new ExtendsDriver(handler);
        d.setContentHandler(handler);
        d.parse(new InputSource(new StringReader(XML)));

        assertTrue(called[0]);
    }

    private static class ExtendsDriver extends Driver {

        private final ContentHandler handler;

        public ExtendsDriver(ContentHandler handler) throws XmlPullParserException {
            this.handler = handler;
        }

        @Override
        protected void startElement(String namespace, String localName, String qName)
                throws SAXException {
            super.startElement(namespace, localName, qName);
            handler.startElement(namespace, localName, qName, this);
        }
    }
}
