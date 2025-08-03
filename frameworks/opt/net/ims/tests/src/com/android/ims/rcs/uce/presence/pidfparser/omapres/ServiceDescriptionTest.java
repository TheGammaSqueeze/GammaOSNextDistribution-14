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

package com.android.ims.rcs.uce.presence.pidfparser.omapres;

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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

@RunWith(AndroidJUnit4.class)
public class ServiceDescriptionTest extends ImsTestBase {

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
        ServiceDescription serviceDescription = new ServiceDescription();

        assertEquals(OmaPresConstant.NAMESPACE, serviceDescription.getNamespace());
        assertEquals(ServiceDescription.ELEMENT_NAME, serviceDescription.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        final ServiceId serviceId = new ServiceId("service_id_001");
        final Version version = new Version(1, 0);
        final Description description = new Description("description_test");

        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceId(serviceId);
        serviceDescription.setVersion(version);
        serviceDescription.setDescription(description);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        serviceDescription.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        assertTrue(result.contains(serviceId.getValue()));
        assertTrue(result.contains(version.getValue()));
        assertTrue(result.contains(description.getValue()));
    }

    @Test
    @SmallTest
    public void testParsing() throws Exception {
        final String serviceIdValue = "service_id_001";
        final String version = "1.0";
        final String description = "description test";

        StringBuilder serviceDescriptionExample = new StringBuilder();
        serviceDescriptionExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<op:service-description xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\">")
                .append("<op:service-id>").append(serviceIdValue).append("</op:service-id>")
                .append("<op:version>").append(version).append("</op:version>")
                .append("<op:description>").append(description).append("</op:description>")
                .append("</op:service-description>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(serviceDescriptionExample.toString());
        parser.setInput(reader);

        ServiceDescription serviceDescription = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && ServiceDescription.ELEMENT_NAME.equals(parser.getName())) {
                serviceDescription = new ServiceDescription();
                serviceDescription.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(serviceDescription);
        assertNotNull(serviceDescription.getServiceId());
        assertNotNull(serviceDescription.getVersion());
        assertNotNull(serviceDescription.getDescription());

        assertEquals(serviceIdValue, serviceDescription.getServiceId().getValue());
        assertEquals(version, serviceDescription.getVersion().getValue());
        assertEquals(description, serviceDescription.getDescription().getValue());
    }

    private XmlSerializer getXmlSerializer(StringWriter writer)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        serializer.setOutput(writer);
        serializer.setPrefix("", PidfConstant.NAMESPACE);
        serializer.setPrefix("op", OmaPresConstant.NAMESPACE);
        return serializer;
    }
}
