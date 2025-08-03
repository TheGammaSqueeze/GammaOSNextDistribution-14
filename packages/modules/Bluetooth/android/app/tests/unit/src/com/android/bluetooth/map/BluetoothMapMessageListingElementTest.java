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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.util.Xml;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapMessageListingElementTest {
    private static final long TEST_CP_HANDLE = 1;
    private static final String TEST_SUBJECT = "test_subject";
    private static final long TEST_DATE_TIME = 2;
    private static final String TEST_SENDER_NAME = "test_sender_name";
    private static final String TEST_SENDER_ADDRESSING = "test_sender_addressing";
    private static final String TEST_REPLY_TO_ADDRESSING = "test_reply_to_addressing";
    private static final String TEST_RECIPIENT_NAME = "test_recipient_name";
    private static final String TEST_RECIPIENT_ADDRESSING = "test_recipient_addressing";
    private static final TYPE TEST_TYPE = TYPE.EMAIL;
    private static final boolean TEST_MSG_TYPE_APP_PARAM_SET = true;
    private static final int TEST_SIZE = 0;
    private static final String TEST_TEXT = "test_text";
    private static final String TEST_RECEPTION_STATUS = "test_reception_status";
    private static final String TEST_DELIVERY_STATUS = "test_delivery_status";
    private static final int TEST_ATTACHMENT_SIZE = 0;
    private static final String TEST_PRIORITY = "test_priority";
    private static final boolean TEST_READ = true;
    private static final String TEST_SENT = "test_sent";
    private static final String TEST_PROTECT = "test_protect";
    private static final String TEST_FOLDER_TYPE = "test_folder_type";
    private static final long TEST_THREAD_ID = 1;
    private static final String TEST_THREAD_NAME = "test_thread_name";
    private static final String TEST_ATTACHMENT_MIME_TYPES = "test_attachment_mime_types";
    private static final boolean TEST_REPORT_READ = true;
    private static final int TEST_CURSOR_INDEX = 1;

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    private BluetoothMapMessageListingElement mMessageListingElement;

    @Before
    public void setUp() throws Exception {
        mMessageListingElement = new BluetoothMapMessageListingElement();

        mMessageListingElement.setHandle(TEST_CP_HANDLE);
        mMessageListingElement.setSubject(TEST_SUBJECT);
        mMessageListingElement.setDateTime(TEST_DATE_TIME);
        mMessageListingElement.setSenderName(TEST_SENDER_NAME);
        mMessageListingElement.setSenderAddressing(TEST_SENDER_ADDRESSING);
        mMessageListingElement.setReplytoAddressing(TEST_REPLY_TO_ADDRESSING);
        mMessageListingElement.setRecipientName(TEST_RECIPIENT_NAME);
        mMessageListingElement.setRecipientAddressing(TEST_RECIPIENT_ADDRESSING);
        mMessageListingElement.setType(TEST_TYPE, TEST_MSG_TYPE_APP_PARAM_SET);
        mMessageListingElement.setSize(TEST_SIZE);
        mMessageListingElement.setText(TEST_TEXT);
        mMessageListingElement.setReceptionStatus(TEST_RECEPTION_STATUS);
        mMessageListingElement.setDeliveryStatus(TEST_DELIVERY_STATUS);
        mMessageListingElement.setAttachmentSize(TEST_ATTACHMENT_SIZE);
        mMessageListingElement.setPriority(TEST_PRIORITY);
        mMessageListingElement.setRead(TEST_READ, TEST_REPORT_READ);
        mMessageListingElement.setSent(TEST_SENT);
        mMessageListingElement.setProtect(TEST_PROTECT);
        mMessageListingElement.setFolderType(TEST_FOLDER_TYPE);
        mMessageListingElement.setThreadId(TEST_THREAD_ID, TEST_TYPE);
        mMessageListingElement.setThreadName(TEST_THREAD_NAME);
        mMessageListingElement.setAttachmentMimeTypes(TEST_ATTACHMENT_MIME_TYPES);
        mMessageListingElement.setCursorIndex(TEST_CURSOR_INDEX);
    }

    @Test
    public void getters() {
        assertThat(mMessageListingElement.getHandle()).isEqualTo(TEST_CP_HANDLE);
        assertThat(mMessageListingElement.getSubject()).isEqualTo(TEST_SUBJECT);
        assertThat(mMessageListingElement.getDateTime()).isEqualTo(TEST_DATE_TIME);
        assertThat(mMessageListingElement.getDateTimeString()).isEqualTo(
                format.format(TEST_DATE_TIME));
        assertThat(mMessageListingElement.getSenderName()).isEqualTo(TEST_SENDER_NAME);
        assertThat(mMessageListingElement.getSenderAddressing()).isEqualTo(TEST_SENDER_ADDRESSING);
        assertThat(mMessageListingElement.getReplyToAddressing()).isEqualTo(
                TEST_REPLY_TO_ADDRESSING);
        assertThat(mMessageListingElement.getRecipientName()).isEqualTo(TEST_RECIPIENT_NAME);
        assertThat(mMessageListingElement.getRecipientAddressing()).isEqualTo(
                TEST_RECIPIENT_ADDRESSING);
        assertThat(mMessageListingElement.getType()).isEqualTo(TEST_TYPE);
        assertThat(mMessageListingElement.getSize()).isEqualTo(TEST_SIZE);
        assertThat(mMessageListingElement.getText()).isEqualTo(TEST_TEXT);
        assertThat(mMessageListingElement.getReceptionStatus()).isEqualTo(TEST_RECEPTION_STATUS);
        assertThat(mMessageListingElement.getDeliveryStatus()).isEqualTo(TEST_DELIVERY_STATUS);
        assertThat(mMessageListingElement.getAttachmentSize()).isEqualTo(TEST_ATTACHMENT_SIZE);
        assertThat(mMessageListingElement.getPriority()).isEqualTo(TEST_PRIORITY);
        assertThat(mMessageListingElement.getRead()).isEqualTo("yes");
        assertThat(mMessageListingElement.getReadBool()).isEqualTo(TEST_READ);
        assertThat(mMessageListingElement.getSent()).isEqualTo(TEST_SENT);
        assertThat(mMessageListingElement.getProtect()).isEqualTo(TEST_PROTECT);
        assertThat(mMessageListingElement.getFolderType()).isEqualTo(TEST_FOLDER_TYPE);
        assertThat(mMessageListingElement.getThreadName()).isEqualTo(TEST_THREAD_NAME);
        assertThat(mMessageListingElement.getAttachmentMimeTypes()).isEqualTo(
                TEST_ATTACHMENT_MIME_TYPES);
        assertThat(mMessageListingElement.getCursorIndex()).isEqualTo(TEST_CURSOR_INDEX);
    }

    @Test
    public void encode() throws Exception {
        mMessageListingElement.setSubject(null);

        final XmlSerializer serializer = Xml.newSerializer();
        final StringWriter writer = new StringWriter();

        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", true);
        mMessageListingElement.encode(serializer, true);
        serializer.endDocument();

        final XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        final XmlPullParser parser;
        parser = parserFactory.newPullParser();

        parser.setInput(new StringReader(writer.toString()));
        parser.next();

        int count = parser.getAttributeCount();
        assertThat(count).isEqualTo(21);

        for (int i = 0; i < count; i++) {
            String attributeName = parser.getAttributeName(i).trim();
            String attributeValue = parser.getAttributeValue(i);
            if (attributeName.equalsIgnoreCase("handle")) {
                assertThat(attributeValue).isEqualTo(
                        BluetoothMapUtils.getMapHandle(TEST_CP_HANDLE, TEST_TYPE));
            } else if (attributeName.equalsIgnoreCase("datetime")) {
                assertThat(attributeValue).isEqualTo(
                        BluetoothMapUtils.getDateTimeString(TEST_DATE_TIME));
            } else if (attributeName.equalsIgnoreCase("sender_name")) {
                assertThat(attributeValue).isEqualTo(
                        BluetoothMapUtils.stripInvalidChars(TEST_SENDER_NAME));
            } else if (attributeName.equalsIgnoreCase("sender_addressing")) {
                assertThat(attributeValue).isEqualTo(TEST_SENDER_ADDRESSING);
            } else if (attributeName.equalsIgnoreCase("replyto_addressing")) {
                assertThat(attributeValue).isEqualTo(TEST_REPLY_TO_ADDRESSING);
            } else if (attributeName.equalsIgnoreCase("recipient_name")) {
                assertThat(attributeValue).isEqualTo(TEST_RECIPIENT_NAME);
            } else if (attributeName.equalsIgnoreCase("recipient_addressing")) {
                assertThat(attributeValue).isEqualTo(TEST_RECIPIENT_ADDRESSING);
            } else if (attributeName.equalsIgnoreCase("type")) {
                assertThat(attributeValue).isEqualTo(TEST_TYPE.name());
            } else if (attributeName.equalsIgnoreCase("size")) {
                assertThat(attributeValue).isEqualTo(Integer.toString(TEST_SIZE));
            } else if (attributeName.equalsIgnoreCase("text")) {
                assertThat(attributeValue).isEqualTo(TEST_TEXT);
            } else if (attributeName.equalsIgnoreCase("reception_status")) {
                assertThat(attributeValue).isEqualTo(TEST_RECEPTION_STATUS);
            } else if (attributeName.equalsIgnoreCase("delivery_status")) {
                assertThat(attributeValue).isEqualTo(TEST_DELIVERY_STATUS);
            } else if (attributeName.equalsIgnoreCase("attachment_size")) {
                assertThat(attributeValue).isEqualTo(Integer.toString(TEST_ATTACHMENT_SIZE));
            } else if (attributeName.equalsIgnoreCase("attachment_mime_types")) {
                assertThat(attributeValue).isEqualTo(TEST_ATTACHMENT_MIME_TYPES);
            } else if (attributeName.equalsIgnoreCase("priority")) {
                assertThat(attributeValue).isEqualTo(TEST_PRIORITY);
            } else if (attributeName.equalsIgnoreCase("read")) {
                assertThat(attributeValue).isEqualTo(mMessageListingElement.getRead());
            } else if (attributeName.equalsIgnoreCase("sent")) {
                assertThat(attributeValue).isEqualTo(TEST_SENT);
            } else if (attributeName.equalsIgnoreCase("protected")) {
                assertThat(attributeValue).isEqualTo(TEST_PROTECT);
            } else if (attributeName.equalsIgnoreCase("conversation_id")) {
                assertThat(attributeValue).isEqualTo(
                        BluetoothMapUtils.getMapConvoHandle(TEST_THREAD_ID, TEST_TYPE));
            } else if (attributeName.equalsIgnoreCase("conversation_name")) {
                assertThat(attributeValue).isEqualTo(TEST_THREAD_NAME);
            } else if (attributeName.equalsIgnoreCase("folder_type")) {
                assertThat(attributeValue).isEqualTo(TEST_FOLDER_TYPE);
            } else {
                throw new Exception("Test fails with unknown XML attribute");
            }
        }
    }

    @Test
    public void compareTo_withLaterDateTime_ReturnsOne() {
        BluetoothMapMessageListingElement elementWithLaterDateTime =
                new BluetoothMapMessageListingElement();
        elementWithLaterDateTime.setDateTime(TEST_DATE_TIME + 1);
        assertThat(mMessageListingElement.compareTo(elementWithLaterDateTime)).isEqualTo(1);
    }

    @Test
    public void compareTo_withFasterDateTime_ReturnsNegativeOne() {
        BluetoothMapMessageListingElement elementWithFasterDateTime =
                new BluetoothMapMessageListingElement();
        elementWithFasterDateTime.setDateTime(TEST_DATE_TIME - 1);
        assertThat(mMessageListingElement.compareTo(elementWithFasterDateTime)).isEqualTo(-1);
    }

    @Test
    public void compareTo_withEqualDateTime_ReturnsZero() {
        BluetoothMapMessageListingElement elementWithEqualDateTime =
                new BluetoothMapMessageListingElement();
        elementWithEqualDateTime.setDateTime(TEST_DATE_TIME);
        assertThat(mMessageListingElement.compareTo(elementWithEqualDateTime)).isEqualTo(0);
    }
}