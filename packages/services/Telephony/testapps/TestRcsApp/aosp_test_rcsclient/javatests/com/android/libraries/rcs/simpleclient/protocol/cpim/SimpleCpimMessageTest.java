/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.protocol.cpim;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SimpleCpimMessageTest {
    private static final String SAMPLE_CPIM =
            "From: MR SANDERS <im:piglet@100akerwood.com>\r\n"
                    + "To: Depressed Donkey <im:eeyore@100akerwood.com>\r\n"
                    + "DateTime: 2000-12-13T13:40:00-08:00\r\n"
                    + "Subject: the weather will be fine today\r\n"
                    + "Subject:;lang=fr beau temps prevu pour aujourd'hui\r\n"
                    + "NS: MyFeatures <mid:MessageFeatures@id.foo.com>\r\n"
                    + "Require: MyFeatures.VitalMessageOption\r\n"
                    + "MyFeatures.VitalMessageOption: Confirmation-requested\r\n"
                    + "MyFeatures.WackyMessageOption: Use-silly-font\r\n"
                    + "\r\n"
                    + "Content-type: text/plain; charset=utf-8\r\n"
                    + "Content-ID: <1234567890@foo.com>\r\n"
                    + "\r\n"
                    + "body";

    @Test
    public void parse_successful() throws Exception {
        SimpleCpimMessage cpim = SimpleCpimMessage.parse(SAMPLE_CPIM.getBytes(UTF_8));

        assertThat(cpim.namespaces()).containsEntry("MyFeatures", "mid:MessageFeatures@id.foo.com");
        assertThat(cpim.headers()).containsEntry("Require", "MyFeatures.VitalMessageOption");
        assertThat(cpim.contentType()).isEqualTo("text/plain; charset=utf-8");
        assertThat(cpim.content()).isEqualTo("body");
    }
}
