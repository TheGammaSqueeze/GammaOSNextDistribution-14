/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.mapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EventReportTest {

    @Test
    public void fromStream() throws Exception {
        EventReport.Type type = EventReport.Type.PARTICIPANT_CHAT_STATE_CHANGED;
        String handle = "FFAB";
        String folder = "test_folder";
        String oldFolder = "old_folder";
        Bmessage.Type msgType = Bmessage.Type.MMS;

        final StringBuilder xml = new StringBuilder();
        xml.append("<event\n");
        xml.append("type=\"" + type.toString() + "\"\n");
        xml.append("handle=\"" + handle + "\"\n");
        xml.append("folder=\"" + folder + "\"\n");
        xml.append("old_folder=\"" + oldFolder + "\"\n");
        xml.append("msg_type=\"" + msgType + "\"\n");
        xml.append("/>\n");
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.toString().getBytes());

        EventReport report = EventReport.fromStream(new DataInputStream(stream));

        assertThat(report.getType()).isEqualTo(type);
        assertThat(report.getDateTime()).isNull();
        assertThat(report.getTimestamp()).isNull();
        assertThat(report.getHandle()).isEqualTo(handle);
        assertThat(report.getFolder()).isEqualTo(folder);
        assertThat(report.getOldFolder()).isEqualTo(oldFolder);
        assertThat(report.getMsgType()).isEqualTo(msgType);
        assertThat(report.toString()).isNotEmpty();
    }

    @Test
    public void fromStream_withInvalidXml_doesNotCrash_andReturnNull() {
        final StringBuilder xml = new StringBuilder();
        xml.append("<<event>>\n");
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.toString().getBytes());

        EventReport report = EventReport.fromStream(new DataInputStream(stream));

        assertThat(report).isNull();
    }

    @Test
    public void fromStreamWithDateTime() throws Exception {
        EventReport.Type type = EventReport.Type.PARTICIPANT_CHAT_STATE_CHANGED;
        String handle = "FFAB";
        String dateTime = "20190101T121314";
        String folder = "test_folder";
        String oldFolder = "old_folder";
        Bmessage.Type msgType = Bmessage.Type.MMS;

        final StringBuilder xml = new StringBuilder();
        xml.append("<event\n");
        xml.append("type=\"" + type.toString() + "\"\n");
        xml.append("datetime=\"" + dateTime + "\"\n");
        xml.append("handle=\"" + handle + "\"\n");
        xml.append("folder=\"" + folder + "\"\n");
        xml.append("old_folder=\"" + oldFolder + "\"\n");
        xml.append("msg_type=\"" + msgType + "\"\n");
        xml.append("/>\n");
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.toString().getBytes());

        EventReport report = EventReport.fromStream(new DataInputStream(stream));

        assertThat(report.getType()).isEqualTo(type);
        assertThat(report.getDateTime()).isEqualTo(dateTime);
        assertThat(report.getTimestamp()).isEqualTo(
                new ObexTime(dateTime).getInstant().toEpochMilli());
        assertThat(report.getHandle()).isEqualTo(handle);
        assertThat(report.getFolder()).isEqualTo(folder);
        assertThat(report.getOldFolder()).isEqualTo(oldFolder);
        assertThat(report.getMsgType()).isEqualTo(msgType);
        assertThat(report.toString()).isNotEmpty();
    }

    @Test
    public void fromStream_withIOException_doesNotCrash_andReturnNull() throws Exception {
        InputStream stream = mock(InputStream.class);
        doThrow(new IOException()).when(stream).read(any());

        EventReport report = EventReport.fromStream(new DataInputStream(stream));

        assertThat(report).isNull();
    }

    @Test
    public void fromStream_withIllegalArgumentException_doesNotCrash_andReturnNull() {
        final StringBuilder xml = new StringBuilder();
        xml.append("<event\n");
        xml.append("type=\"" + "some_random_type" + "\"\n");
        xml.append("/>\n");
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.toString().getBytes());

        EventReport report = EventReport.fromStream(new DataInputStream(stream));

        assertThat(report).isNull();
    }
}
