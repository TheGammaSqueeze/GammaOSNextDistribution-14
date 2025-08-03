/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.presence.pidfparser.capabilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParserConstant;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.PidfConstant;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

@RunWith(AndroidJUnit4.class)
public class DuplexTest extends ImsTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testElementName() throws Exception {
        Duplex duplex = new Duplex();

        assertEquals(CapsConstant.NAMESPACE, duplex.getNamespace());
        assertEquals(Duplex.ELEMENT_NAME, duplex.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        Duplex duplex = new Duplex();
        duplex.addSupportedType(Duplex.DUPLEX_FULL);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        duplex.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        StringBuilder verificationBuilder = new StringBuilder();
        verificationBuilder.append("<caps:duplex")
                .append(" xmlns=\"").append(PidfConstant.NAMESPACE).append("\"")
                .append(" xmlns:caps=\"").append(CapsConstant.NAMESPACE).append("\">")
                .append("<caps:supported>")
                .append("<caps:full />")
                .append("</caps:supported>").append("</caps:duplex>");

        assertTrue(result.contains(verificationBuilder.toString()));
    }


    @Test
    @SmallTest
    public void testParsing() throws Exception {
        StringBuilder duplexExample = new StringBuilder();
        duplexExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<caps:duplex xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<caps:supported>")
                .append("<caps:full />")
                .append("</caps:supported>").append("</caps:duplex>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(duplexExample.toString());
        parser.setInput(reader);

        Duplex duplex = null;
        int nextType = parser.next();

        do {
            // Find the start tag
            if (nextType == XmlPullParser.START_TAG
                    && Duplex.ELEMENT_NAME.equals(parser.getName())) {
                duplex = new Duplex();
                duplex.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(duplex);

        List<String> supportedTypes = duplex.getSupportedTypes();
        assertEquals(1, supportedTypes.size());
        assertEquals(Duplex.DUPLEX_FULL, supportedTypes.get(0));

        List<String> notSupportedTypes = duplex.getNotSupportedTypes();
        assertTrue(notSupportedTypes.isEmpty());
    }

    private XmlSerializer getXmlSerializer(StringWriter writer)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        serializer.setOutput(writer);
        serializer.setPrefix("", PidfConstant.NAMESPACE);
        serializer.setPrefix("caps", CapsConstant.NAMESPACE);
        return serializer;
    }
}
