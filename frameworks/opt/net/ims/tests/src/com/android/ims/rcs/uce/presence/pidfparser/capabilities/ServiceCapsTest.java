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
import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;
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
public class ServiceCapsTest extends ImsTestBase {

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
        ServiceCaps serviceCaps = new ServiceCaps();

        assertEquals(CapsConstant.NAMESPACE, serviceCaps.getNamespace());
        assertEquals(ServiceCaps.ELEMENT_NAME, serviceCaps.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        Audio audio = new Audio(true);
        Video video = new Video(true);
        Duplex duplex = new Duplex();
        duplex.addSupportedType(Duplex.DUPLEX_FULL);

        ServiceCaps serviceCaps = new ServiceCaps();
        serviceCaps.addElement(audio);
        serviceCaps.addElement(video);
        serviceCaps.addElement(duplex);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        serviceCaps.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        String verificationAudio = "<caps:audio>true</caps:audio>";
        String verificationVideo = "<caps:video>true</caps:video>";
        StringBuilder verificationDuplex = new StringBuilder();
        verificationDuplex.append("<caps:duplex>")
                .append("<caps:supported>")
                .append("<caps:full />")
                .append("</caps:supported>")
                .append("</caps:duplex>");

        assertTrue(result.contains(verificationAudio));
        assertTrue(result.contains(verificationVideo));
        assertTrue(result.contains(verificationDuplex.toString()));
    }


    @Test
    @SmallTest
    public void testParsing() throws Exception {
        StringBuilder serviceCapsExample = new StringBuilder();
        serviceCapsExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<caps:servcaps xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<caps:audio>true</caps:audio>")
                .append("<caps:video>true</caps:video>")
                .append("<caps:duplex><caps:supported>")
                .append("<caps:full />")
                .append("</caps:supported></caps:duplex>")
                .append("</caps:servcaps>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(serviceCapsExample.toString());
        parser.setInput(reader);

        ServiceCaps serviceCaps = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && ServiceCaps.ELEMENT_NAME.equals(parser.getName())) {
                serviceCaps = new ServiceCaps();
                serviceCaps.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(serviceCaps);

        List<ElementBase> elements = serviceCaps.getElements();
        Audio resultAudio = null;
        Video resultVideo = null;
        Duplex resultDuplex = null;
        for (ElementBase element : elements) {
            String elementName = element.getElementName();
            if (Audio.ELEMENT_NAME.equals(elementName)) {
                resultAudio = (Audio) element;
            } else if (Video.ELEMENT_NAME.equals(elementName)) {
                resultVideo = (Video) element;
            } else if (Duplex.ELEMENT_NAME.equals(elementName)) {
                resultDuplex = (Duplex) element;
            }
        }

        assertNotNull(resultAudio);
        assertTrue(resultAudio.isAudioSupported());

        assertNotNull(resultVideo);
        assertTrue(resultVideo.isVideoSupported());

        assertNotNull(resultDuplex);

        List<String> supportedTypes = resultDuplex.getSupportedTypes();
        assertEquals(1, supportedTypes.size());
        assertEquals(Duplex.DUPLEX_FULL, supportedTypes.get(0));
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
