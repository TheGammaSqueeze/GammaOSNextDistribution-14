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

package com.android.ims.rcs.uce.presence.pidfparser.pidf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParserConstant;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

@RunWith(AndroidJUnit4.class)
public class BasicTest extends ImsTestBase {

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
        Basic basic = new Basic();

        assertEquals(PidfConstant.NAMESPACE, basic.getNamespace());
        assertEquals(Basic.ELEMENT_NAME, basic.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializingWithBasicOpen() throws Exception {
        Basic basic = new Basic(Basic.OPEN);
        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        basic.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        final String result = writer.toString();
        final String basicElementWithOpenValue =
                "<basic xmlns=\"" + PidfConstant.NAMESPACE + "\">open</basic>";
        assertTrue(result.contains(basicElementWithOpenValue));
    }

    @Test
    @SmallTest
    public void testSerializingWithBasicClosed() throws Exception {
        Basic basic = new Basic(Basic.CLOSED);
        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        basic.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        final String result = writer.toString();
        final String basicElementWithClosedValue =
                "<basic xmlns=\"" + PidfConstant.NAMESPACE + "\">closed</basic>";
        assertTrue(result.contains(basicElementWithClosedValue));
    }

    @Test
    @SmallTest
    public void testParsingWithBasicOpen() throws Exception {
        StringBuilder basicExample = new StringBuilder();
        basicExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<basic xmlns=\"urn:ietf:params:xml:ns:pidf\">")
                .append(Basic.OPEN)
                .append("</basic>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(basicExample.toString());
        parser.setInput(reader);

        Basic basic = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Basic.ELEMENT_NAME.equals(parser.getName())) {
                basic = new Basic();
                basic.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(basic);
        assertEquals(Basic.OPEN, basic.getValue());
    }

    private XmlSerializer getXmlSerializer(StringWriter writer)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        serializer.setOutput(writer);
        serializer.setPrefix("", PidfConstant.NAMESPACE);
        return serializer;
    }
}
