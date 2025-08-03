/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AdIdEncryptionTest {
    @Test
    public void encryptAdIdAndEnrollmentSha256_withKnownValues_success() {
        String testAdIdValue = "ab";
        String testEnrollmentId = "c";

        // This is the known output of SHA256("abc")
        String knownSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertEquals(
                knownSha256,
                AdIdEncryption.encryptAdIdAndEnrollmentSha256(testAdIdValue, testEnrollmentId));
    }

    @Test
    public void encryptAdIdAndEnrollmentSha256_withNullAdId_returnsNull() {
        assertNull(AdIdEncryption.encryptAdIdAndEnrollmentSha256(null, "c"));
    }
}
