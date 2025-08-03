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
import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParserConstant;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParserUtils;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Audio;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.CapsConstant;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Duplex;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.ServiceCaps;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Video;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Description;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.OmaPresConstant;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceDescription;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceId;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Version;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
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
public class TupleTest extends ImsTestBase {

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
        Tuple tuple = new Tuple();

        assertEquals(PidfConstant.NAMESPACE, tuple.getNamespace());
        assertEquals(Tuple.ELEMENT_NAME, tuple.getElementName());
    }

    @Test
    @SmallTest
    public void testSerializing() throws Exception {
        Tuple tuple = new Tuple();

        Basic basic = new Basic(Basic.OPEN);
        Status status = new Status();
        status.setBasic(basic);
        tuple.setStatus(status);

        ServiceId serviceId = new ServiceId("service_id_001");
        Version version = new Version(1, 0);
        Description description = new Description("description test");
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceId(serviceId);
        serviceDescription.setVersion(version);
        serviceDescription.setDescription(description);
        tuple.setServiceDescription(serviceDescription);

        Audio audio = new Audio(true);
        Video video = new Video(true);
        Duplex duplex = new Duplex();
        duplex.addSupportedType(Duplex.DUPLEX_FULL);

        ServiceCaps serviceCaps = new ServiceCaps();
        serviceCaps.addElement(audio);
        serviceCaps.addElement(video);
        serviceCaps.addElement(duplex);
        tuple.setServiceCaps(serviceCaps);

        Note note = new Note("Note test");
        tuple.addNote(note);

        String nowTime = Instant.now().toString();
        Timestamp timestamp = new Timestamp(nowTime);
        tuple.setTimestamp(timestamp);

        StringWriter writer = new StringWriter();
        XmlSerializer serializer = getXmlSerializer(writer);

        // Serializing
        serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
        tuple.serialize(serializer);
        serializer.endDocument();
        serializer.flush();

        String result = writer.toString();

        String verificationStatus = "<status><basic>open</basic></status>";
        String verificationServiceId = "<op:service-id>service_id_001</op:service-id>";
        String verificationVersion = "<op:version>1.0</op:version>";
        String verificationDescription = "<op:description>description test</op:description>";
        String verificationAudio = "<caps:audio>true</caps:audio>";
        String verificationVideo = "<caps:video>true</caps:video>";
        String verificationNote = "<note>Note test</note>";
        String verificationTimestamp = "<timestamp>" + nowTime + "</timestamp>";

        assertTrue(result.contains(verificationStatus));
        assertTrue(result.contains(verificationServiceId));
        assertTrue(result.contains(verificationVersion));
        assertTrue(result.contains(verificationDescription));
        assertTrue(result.contains(verificationAudio));
        assertTrue(result.contains(verificationVideo));
        assertTrue(result.contains(verificationNote));
        assertTrue(result.contains(verificationTimestamp));
    }

    @Test
    @SmallTest
    public void testParsing() throws Exception {
        final String status = Basic.OPEN;
        final String serviceId = "service_id_001";
        final String version = "1.0";
        final String description = "description test";
        final boolean audio = true;
        final boolean video = true;
        final String note = "note test";
        final String nowTime = Instant.now().toString();

        StringBuilder tupleExample = new StringBuilder();
        tupleExample.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<tuple id=\"tid0\" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                .append("<status><basic>").append(status).append("</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>").append(serviceId).append("</op:service-id>")
                .append("<op:version>").append(version).append("</op:version>")
                .append("<op:description>").append(description).append("</op:description>")
                .append("</op:service-description>")
                .append("<caps:servcaps><caps:audio>").append(audio).append("</caps:audio>")
                .append("<caps:video>").append(video).append("</caps:video>")
                .append("<caps:duplex><caps:supported>")
                .append("<caps:full />")
                .append("</caps:supported></caps:duplex>")
                .append("</caps:servcaps>")
                .append("<note>").append(note).append("</note>")
                .append("<timestamp>").append(nowTime).append("</timestamp></tuple>");

        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        Reader reader = new StringReader(tupleExample.toString());
        parser.setInput(reader);

        Tuple tuple = null;
        int nextType = parser.next();

        // Find the start tag
        do {
            if (nextType == XmlPullParser.START_TAG
                    && Tuple.ELEMENT_NAME.equals(parser.getName())) {
                tuple = new Tuple();
                tuple.parse(parser);
                break;
            }
            nextType = parser.next();
        } while(nextType != XmlPullParser.END_DOCUMENT);

        reader.close();

        assertNotNull(tuple);
        assertEquals(Basic.OPEN, PidfParserUtils.getTupleStatus(tuple));
        assertEquals(serviceId, PidfParserUtils.getTupleServiceId(tuple));
        assertEquals(version, PidfParserUtils.getTupleServiceVersion(tuple));
        assertEquals(description, PidfParserUtils.getTupleServiceDescription(tuple));

        boolean resultAudio = false;
        boolean resultVideo = false;
        List<ElementBase> elements = tuple.getServiceCaps().getElements();
        for (ElementBase element : elements) {
            if (element instanceof Audio) {
                resultAudio = ((Audio) element).isAudioSupported();
            } else if (element instanceof Video) {
                resultVideo = ((Video) element).isVideoSupported();
            }
        }
        assertTrue(resultAudio);
        assertTrue(resultVideo);

        String resultNote = null;
        List<Note> noteList = tuple.getNoteList();
        if (noteList != null && !noteList.isEmpty()) {
            Note eachNote = noteList.get(0);
            if (eachNote != null) {
                resultNote = eachNote.getNote();
            }
        }

        assertTrue(note.equals(resultNote));
        assertEquals(nowTime, PidfParserUtils.getTupleTimestamp(tuple));
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
