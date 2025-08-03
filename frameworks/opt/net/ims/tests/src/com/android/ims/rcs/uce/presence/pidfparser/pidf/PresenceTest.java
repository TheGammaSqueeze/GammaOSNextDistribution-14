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
import com.android.ims.rcs.uce.presence.pidfparser.PidfParserUtils;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.CapsConstant;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Description;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.OmaPresConstant;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceDescription;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceId;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Version;

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
public class PresenceTest extends ImsTestBase {

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
        Presence presence = new Presence();

        assertEquals(PidfConstant.NAMESPACE, presence.getNamespace());
        assertEquals(Presence.ELEMENT_NAME, presence.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        String contact = Uri.fromParts("sip", "test", null).toString();

        String serviceId1 = "service_id_01";
        String description1 = "description_test1";
        Tuple tuple1 = getTuple(Basic.OPEN, serviceId1, description1, contact);

        String serviceId2 = "service_id_02";
        String description2 = "description_test2";
        Tuple tuple2 = getTuple(Basic.OPEN, serviceId2, description2, contact);

        Presence presence = new Presence();
        presence.setEntity(contact);
        presence.addTuple(tuple1);
        presence.addTuple(tuple2);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        presence.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        String verificationServiceId1 = "<op:service-id>service_id_01</op:service-id>";
        String verificationDescription1 = "<op:description>description_test1</op:description>";
        String verificationServiceId2 = "<op:service-id>service_id_02</op:service-id>";
        String verificationDescription2 = "<op:description>description_test2</op:description>";
        String verificationContact = "<contact>sip:test</contact>";

        assertTrue(result.contains(verificationServiceId1));
        assertTrue(result.contains(verificationDescription1));
        assertTrue(result.contains(verificationServiceId2));
        assertTrue(result.contains(verificationDescription2));
        assertTrue(result.contains(verificationContact));
    }

    @Test
    @SmallTest
    public void testParsing() throws Exception {
        final String contact = Uri.fromParts("sip", "test", null).toString();
        final String serviceId = "service_id_01";
        final String version = "1.0";
        final String description = "description_test";

        StringBuilder presenceExample = new StringBuilder();
        presenceExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"").append(contact).append("\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>").append(serviceId).append("</op:service-id>")
                .append("<op:version>").append(version).append("</op:version>")
                .append("<op:description>").append(description).append("</op:description>")
                .append("</op:service-description>")
                .append("<contact>sip:test</contact></tuple></presence>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(presenceExample.toString());
        parser.setInput(reader);

        Presence presence = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Presence.ELEMENT_NAME.equals(parser.getName())) {
                presence = new Presence();
                presence.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(presence);
        assertEquals(contact, presence.getEntity());

        List<Tuple> tupleList = presence.getTupleList();
        assertNotNull(tupleList);
        assertEquals(1, tupleList.size());

        Tuple tuple = tupleList.get(0);
        assertEquals(serviceId, PidfParserUtils.getTupleServiceId(tuple));
        assertEquals(version, PidfParserUtils.getTupleServiceVersion(tuple));
        assertEquals(description, PidfParserUtils.getTupleServiceDescription(tuple));
        assertEquals(contact, PidfParserUtils.getTupleContact(tuple));
    }

    @Test
    @SmallTest
    public void testMalformedParsing() throws Exception {
        final String contact = Uri.fromParts("sip", "test", null).toString();
        final String serviceId = "service_id_01";
        final String version = "1.0";
        final String description = "description_test";
        final String serviceId2 = "service_id_02";
        final String version2 = "2.0";
        final String description2 = "description_test2";
        final String serviceId3 = "service_id_03";
        final String version3 = "3.0";
        final String description3 = "description_test3";

        StringBuilder presenceExample = new StringBuilder();
        presenceExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"").append(contact).append("\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>").append(serviceId).append("</op:service-id>")
                .append("<op:version>").append(version).append("</op:version>")
                .append("<op:description>").append(description).append("</op:description>")
                .append("</op:service-description>")
                .append("<contact>sip:test</contact></tuple>")
                .append("<tuple id=\"tid1\"><status><basic>open</basic></status>")
                .append("<op:service-ddescription>")
                .append("<op:service-id>").append(serviceId2).append("</op:service-id>")
                .append("<op:version>").append(version2).append("</op:version>")
                .append("<op:description>").append(description2).append("</op:description>")
                .append("</op:service-description>")
                .append("<contact>sip:test</contact></tuple>")
                .append("<tuple id=\"tid3\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>").append(serviceId3).append("</op:service-id>")
                .append("<op:version>").append(version3).append("</op:version>")
                .append("<op:description>").append(description3).append("</op:description>")
                .append("</op:service-description>")
                .append("<contact>sip:test</contact></tuple></presence>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(presenceExample.toString());
        parser.setInput(reader);

        Presence presence = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Presence.ELEMENT_NAME.equals(parser.getName())) {
                presence = new Presence();
                presence.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(presence);
        assertEquals(contact, presence.getEntity());

        List<Tuple> tupleList = presence.getTupleList();
        assertNotNull(tupleList);
        assertEquals(3, tupleList.size());
        assertNotNull(tupleList.get(0)); // tuple of tid0
        assertNotNull(tupleList.get(1)); // tuple of tid1. tid1 is a tuple that failed to parse.
        assertNotNull(tupleList.get(2)); // tuple of tid2.

        Tuple tuple = tupleList.get(0);
        assertEquals(contact, PidfParserUtils.getTupleContact(tuple));

        assertEquals(serviceId, PidfParserUtils.getTupleServiceId(tuple));
        assertEquals(version, PidfParserUtils.getTupleServiceVersion(tuple));
        assertEquals(description, PidfParserUtils.getTupleServiceDescription(tuple));

        Tuple tuple1 = tupleList.get(1);
        assertTrue(PidfParserUtils.getTupleMalformedStatus(tuple1));

        Tuple tuple3 = tupleList.get(2);
        assertEquals(serviceId3, PidfParserUtils.getTupleServiceId(tuple3));
        assertEquals(version3, PidfParserUtils.getTupleServiceVersion(tuple3));
        assertEquals(description3, PidfParserUtils.getTupleServiceDescription(tuple3));
    }

    private Tuple getTuple(String statusValue, String serviceIdValue, String descValue,
            String contactValue) {
        Basic basic = new Basic(statusValue);
        Status status = new Status();
        status.setBasic(basic);

        ServiceId serviceId = new ServiceId(serviceIdValue);
        Version version = new Version(1, 0);
        Description description = new Description(descValue);
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceId(serviceId);
        serviceDescription.setVersion(version);
        serviceDescription.setDescription(description);

        Contact contact = new Contact();
        contact.setContact(contactValue);

        Tuple tuple = new Tuple();
        tuple.setStatus(status);
        tuple.setServiceDescription(serviceDescription);
        tuple.setContact(contact);

        return tuple;
    }

    private XmlSerializer getXmlSerializer(StringWriter writer)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        serializer.setOutput(writer);
        serializer.setPrefix("", PidfConstant.NAMESPACE);
        serializer.setPrefix("op", OmaPresConstant.NAMESPACE);
        serializer.setPrefix("caps", CapsConstant.NAMESPACE);
        return serializer;
    }
}
