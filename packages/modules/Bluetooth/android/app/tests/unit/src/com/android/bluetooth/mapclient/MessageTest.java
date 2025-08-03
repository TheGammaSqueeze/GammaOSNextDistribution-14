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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MessageTest {

    @Test
    public void constructor() throws Exception {
        HashMap<String, String> attrs = new HashMap<>();

        String handle = "FFAB";
        attrs.put("handle", handle);

        String subject = "test_subject";
        attrs.put("subject", subject);

        String dateTime = "20221220T165048";
        attrs.put("datetime", dateTime);

        String senderName = "test_sender_name";
        attrs.put("sender_name", senderName);

        String senderAddr = "test_sender_addressing";
        attrs.put("sender_addressing", senderAddr);

        String replytoAddr = "test_replyto_addressing";
        attrs.put("replyto_addressing", replytoAddr);

        String recipientName = "test_recipient_name";
        attrs.put("recipient_name", recipientName);

        String recipientAddr = "test_recipient_addressing";
        attrs.put("recipient_addressing", recipientAddr);

        String type = "MMS";
        attrs.put("type", type);

        int size = 23;
        attrs.put("size", Integer.toString(size));

        String text = "yes";
        attrs.put("text", text);

        String receptionStatus = "notification";
        attrs.put("reception_status", receptionStatus);

        int attachmentSize = 15;
        attrs.put("attachment_size", Integer.toString(attachmentSize));

        String isPriority = "yes";
        attrs.put("priority", isPriority);

        String isRead = "yes";
        attrs.put("read", isRead);

        String isSent = "yes";
        attrs.put("sent", isSent);

        String isProtected = "yes";
        attrs.put("protected", isProtected);

        Message msg = new Message(attrs);

        assertThat(msg.getHandle()).isEqualTo(handle);
        assertThat(msg.getSubject()).isEqualTo(subject);
        // TODO: Compare the Date class properly.
        // assertThat(msg.getDateTime()).isEqualTo(expectedTime);
        assertThat(msg.getDateTime()).isNotNull();
        assertThat(msg.getSenderName()).isEqualTo(senderName);
        assertThat(msg.getSenderAddressing()).isEqualTo(senderAddr);
        assertThat(msg.getReplytoAddressing()).isEqualTo(replytoAddr);
        assertThat(msg.getRecipientName()).isEqualTo(recipientName);
        assertThat(msg.getRecipientAddressing()).isEqualTo(recipientAddr);
        assertThat(msg.getType()).isEqualTo(Message.Type.MMS);
        assertThat(msg.getSize()).isEqualTo(size);
        assertThat(msg.getReceptionStatus()).isEqualTo(Message.ReceptionStatus.NOTIFICATION);
        assertThat(msg.getAttachmentSize()).isEqualTo(attachmentSize);
        assertThat(msg.isText()).isTrue();
        assertThat(msg.isPriority()).isTrue();
        assertThat(msg.isRead()).isTrue();
        assertThat(msg.isSent()).isTrue();
        assertThat(msg.isProtected()).isTrue();
        assertThat(msg.toString()).isNotEmpty();
    }
}
