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

package com.android.libraries.rcs.simpleclient.protocol.cpim;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/** Collections of utility functions for CPIM */
public class CpimUtils {
    private static final String ANONYMOUS_URI = "<sip:anonymous@anonymous.invalid>";
    public static final String CPIM_CONTENT_TYPE = "message/cpim";

    private CpimUtils() {
    }

    @SuppressWarnings("AndroidJdkLibsChecker")
    public static SimpleCpimMessage createForText(String text) {
        return SimpleCpimMessage.newBuilder()
                .addNamespace("imdn", "urn:ietf:params:imdn")
                .addHeader("imdn.Message-ID", generateImdnMessageId())
                .addHeader("imdn.Disposition-Notification", "positive-delivery, display")
                .addHeader("To", ANONYMOUS_URI)
                .addHeader("From", ANONYMOUS_URI)
                .addHeader("DateTime", ZonedDateTime.now(ZoneId.systemDefault()).format(
                        DateTimeFormatter.ISO_INSTANT))
                .setContentType("text/plain")
                .setContent(text)
                .build();
    }

    private static String generateImdnMessageId() {
        Random random = new Random();
        return "Test_" + random.nextInt(1000000);
    }
}
