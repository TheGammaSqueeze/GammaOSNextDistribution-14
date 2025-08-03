/*
 * Copyright 2023 The Android Open Source Project
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

import java.io.ByteArrayInputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MessagesListingTest {

    @Test
    public void constructor() {
        String handle = "FFAB";
        String subject = "test_subject";
        final StringBuilder xml = new StringBuilder();
        xml.append("<msg\n");
        xml.append("handle=\"" + handle + "\"\n");
        xml.append("subject=\"" + subject + "\"\n");
        xml.append("/>\n");
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.toString().getBytes());

        MessagesListing listing = new MessagesListing(stream);

        assertThat(listing.getList()).hasSize(1);
        Message msg = listing.getList().get(0);
        assertThat(msg.getHandle()).isEqualTo(handle);
        assertThat(msg.getSubject()).isEqualTo(subject);
    }
}
