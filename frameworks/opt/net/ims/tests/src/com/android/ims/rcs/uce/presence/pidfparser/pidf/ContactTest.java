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

import android.net.Uri;
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
public class ContactTest extends ImsTestBase {

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
        Contact contact = new Contact();

        assertEquals(PidfConstant.NAMESPACE, contact.getNamespace());
        assertEquals(Contact.ELEMENT_NAME, contact.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        String testedContact = Uri.fromParts("sip", "test", null).toString();

        Contact contact = new Contact();
        contact.setContact(testedContact);
        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        contact.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        final String result = writer.toString();

        StringBuilder verificationBuilder = new StringBuilder();
        verificationBuilder.append("<contact xmlns=\"").append(PidfConstant.NAMESPACE).append("\">")
                .append(testedContact).append("</contact>");
        assertTrue(result.contains(verificationBuilder.toString()));
    }

    @Test
    @SmallTest
    public void testParsing() throws Exception {
        final String testedContact = Uri.fromParts("sip", "test", null).toString();

        StringBuilder contactExample = new StringBuilder();
        contactExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<contact xmlns=\"urn:ietf:params:xml:ns:pidf\">")
                .append(testedContact)
                .append("</contact>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(contactExample.toString());
        parser.setInput(reader);

        Contact contact = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Contact.ELEMENT_NAME.equals(parser.getName())) {
                contact = new Contact();
                contact.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(contact);
        assertEquals(testedContact, contact.getContact());
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
