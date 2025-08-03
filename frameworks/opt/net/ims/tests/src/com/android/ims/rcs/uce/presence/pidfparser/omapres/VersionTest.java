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
public class VersionTest extends ImsTestBase {

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
        Version version = new Version();

        assertEquals(OmaPresConstant.NAMESPACE, version.getNamespace());
        assertEquals(Version.ELEMENT_NAME, version.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        final int majorVersion = 1;
        final int minorVersion = 0;
        Version version = new Version(majorVersion, minorVersion);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        version.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        StringBuilder verificationBuilder = new StringBuilder();
        verificationBuilder.append("<op:version")
                .append(" xmlns=\"").append(PidfConstant.NAMESPACE).append("\"")
                .append(" xmlns:op=\"").append(OmaPresConstant.NAMESPACE).append("\">")
                .append(majorVersion + "." + minorVersion)
                .append("</op:version>");

         assertTrue(result.contains(verificationBuilder.toString()));
    }

    @Test
    @SmallTest
    public void testParsing() throws Exception {
        final String versionValue = "1.0";

        StringBuilder versionExample = new StringBuilder();
        versionExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<op:version xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\">")
                .append(versionValue)
                .append("</op:version>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(versionExample.toString());
        parser.setInput(reader);

        Version version = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Version.ELEMENT_NAME.equals(parser.getName())) {
                version = new Version();
                version.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(version);
        assertEquals(versionValue, version.getValue());
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
