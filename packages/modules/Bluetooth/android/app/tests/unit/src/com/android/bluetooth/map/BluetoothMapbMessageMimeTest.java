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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.text.util.Rfc822Token;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapbMessageMimeTest {
    private static final String TAG = BluetoothMapbMessageMimeTest.class.getSimpleName();

    private static final long TEST_DATE = 1;
    private static final String TEST_SUBJECT = "test_subject";
    private static final String TEST_MESSAGE_ID = "test_message_id";
    private static final String TEST_CONTENT_TYPE = "text/plain";
    private static final boolean TEST_TEXT_ONLY = true;
    private static final boolean TEST_INCLUDE_ATTACHMENTS = true;

    private final SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",
            Locale.US);
    private final Date date = new Date(TEST_DATE);

    private final ArrayList<Rfc822Token> TEST_FROM = new ArrayList<>(
            Arrays.asList(new Rfc822Token("from_name", "from_address", null)));
    private final ArrayList<Rfc822Token> TEST_SENDER = new ArrayList<>(
            Arrays.asList(new Rfc822Token("sender_name", "sender_address", null)));
    private static final ArrayList<Rfc822Token> TEST_TO = new ArrayList<>(
            Arrays.asList(new Rfc822Token("to_name", "to_address", null)));
    private static final ArrayList<Rfc822Token> TEST_CC = new ArrayList<>(
            Arrays.asList(new Rfc822Token("cc_name", "cc_address", null)));
    private final ArrayList<Rfc822Token> TEST_BCC = new ArrayList<>(
            Arrays.asList(new Rfc822Token("bcc_name", "bcc_address", null)));
    private final ArrayList<Rfc822Token> TEST_REPLY_TO = new ArrayList<>(
            Arrays.asList(new Rfc822Token("reply_to_name", "reply_to_address", null)));

    private BluetoothMapbMessageMime mMime;

    @Before
    public void setUp() {
        mMime = new BluetoothMapbMessageMime();

        mMime.setSubject(TEST_SUBJECT);
        mMime.setDate(TEST_DATE);
        mMime.setMessageId(TEST_MESSAGE_ID);
        mMime.setContentType(TEST_CONTENT_TYPE);
        mMime.setTextOnly(TEST_TEXT_ONLY);
        mMime.setIncludeAttachments(TEST_INCLUDE_ATTACHMENTS);

        mMime.setFrom(TEST_FROM);
        mMime.setSender(TEST_SENDER);
        mMime.setTo(TEST_TO);
        mMime.setCc(TEST_CC);
        mMime.setBcc(TEST_BCC);
        mMime.setReplyTo(TEST_REPLY_TO);

        mMime.addMimePart();
    }

    @Test
    public void testGetters() {
        assertThat(mMime.getSubject()).isEqualTo(TEST_SUBJECT);
        assertThat(mMime.getDate()).isEqualTo(TEST_DATE);
        assertThat(mMime.getMessageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(mMime.getDateString()).isEqualTo(format.format(date));
        assertThat(mMime.getContentType()).isEqualTo(TEST_CONTENT_TYPE);
        assertThat(mMime.getTextOnly()).isEqualTo(TEST_TEXT_ONLY);
        assertThat(mMime.getIncludeAttachments()).isEqualTo(TEST_INCLUDE_ATTACHMENTS);

        assertThat(mMime.getFrom()).isEqualTo(TEST_FROM);
        assertThat(mMime.getSender()).isEqualTo(TEST_SENDER);
        assertThat(mMime.getTo()).isEqualTo(TEST_TO);
        assertThat(mMime.getCc()).isEqualTo(TEST_CC);
        assertThat(mMime.getBcc()).isEqualTo(TEST_BCC);
        assertThat(mMime.getReplyTo()).isEqualTo(TEST_REPLY_TO);

        assertThat(mMime.getMimeParts().size()).isEqualTo(1);
    }

    @Test
    public void testGetSize() {
        mMime.getMimeParts().get(0).mData = new byte[10];
        assertThat(mMime.getSize()).isEqualTo(10);
    }

    @Test
    public void testUpdateCharset() {
        mMime.getMimeParts().get(0).mContentType = TEST_CONTENT_TYPE/*="text/plain*/;
        mMime.updateCharset();
        assertThat(mMime.mCharset).isEqualTo("UTF-8");
    }

    @Test
    public void testAddFrom() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addFrom(nameToAdd, addressToAdd);
        assertThat(mime.getFrom().get(0)).isEqualTo(new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testAddSender() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addSender(nameToAdd, addressToAdd);
        assertThat(mime.getSender().get(0)).isEqualTo(
                new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testAddTo() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addTo(nameToAdd, addressToAdd);
        assertThat(mime.getTo().get(0)).isEqualTo(new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testAddCc() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addCc(nameToAdd, addressToAdd);
        assertThat(mime.getCc().get(0)).isEqualTo(new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testAddBcc() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addBcc(nameToAdd, addressToAdd);
        assertThat(mime.getBcc().get(0)).isEqualTo(new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testAddReplyTo() {
        final BluetoothMapbMessageMime mime = new BluetoothMapbMessageMime();
        final String nameToAdd = "name_to_add";
        final String addressToAdd = "address_to_add";
        mime.addReplyTo(nameToAdd, addressToAdd);
        assertThat(mime.getReplyTo().get(0)).isEqualTo(
                new Rfc822Token(nameToAdd, addressToAdd, null));
    }

    @Test
    public void testEncode_ThenCreateByParsing_ReturnsCorrectly() throws Exception {
        mMime.setType(BluetoothMapUtils.TYPE.EMAIL);
        mMime.setFolder("placeholder");
        byte[] encodedMime = mMime.encodeMime();

        final BluetoothMapbMessageMime mimeToCreateByParsing = new BluetoothMapbMessageMime();
        mimeToCreateByParsing.parseMsgPart(new String(encodedMime));

        assertThat(mimeToCreateByParsing.getSubject()).isEqualTo(TEST_SUBJECT);
        assertThat(mimeToCreateByParsing.getMessageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(mimeToCreateByParsing.getContentType()).isEqualTo(TEST_CONTENT_TYPE);

        assertThat(mimeToCreateByParsing.getFrom().get(0).getName()).isEqualTo(
                TEST_FROM.get(0).getName());
        assertThat(mimeToCreateByParsing.getFrom().get(0).getAddress()).isEqualTo(
                TEST_FROM.get(0).getAddress());

        assertThat(mimeToCreateByParsing.getTo().get(0).getName()).isEqualTo(
                TEST_TO.get(0).getName());
        assertThat(mimeToCreateByParsing.getTo().get(0).getAddress()).isEqualTo(
                TEST_TO.get(0).getAddress());

        assertThat(mimeToCreateByParsing.getCc().get(0).getName()).isEqualTo(
                TEST_CC.get(0).getName());
        assertThat(mimeToCreateByParsing.getCc().get(0).getAddress()).isEqualTo(
                TEST_CC.get(0).getAddress());

        assertThat(mimeToCreateByParsing.getBcc().get(0).getName()).isEqualTo(
                TEST_BCC.get(0).getName());
        assertThat(mimeToCreateByParsing.getBcc().get(0).getAddress()).isEqualTo(
                TEST_BCC.get(0).getAddress());

        assertThat(mimeToCreateByParsing.getReplyTo().get(0).getName()).isEqualTo(
                TEST_REPLY_TO.get(0).getName());
        assertThat(mimeToCreateByParsing.getReplyTo().get(0).getAddress()).isEqualTo(
                TEST_REPLY_TO.get(0).getAddress());
    }

    @Test
    public void testParseNullMsgPart_NoExceptionsThrown() {
        BluetoothMapbMessageMime bMessageMime = new BluetoothMapbMessageMime();
        bMessageMime.parseMsgPart(null);
    }
}
