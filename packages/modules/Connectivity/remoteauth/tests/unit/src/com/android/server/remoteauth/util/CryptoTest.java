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
package com.android.server.remoteauth.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Crypto}. */
@RunWith(AndroidJUnit4.class)
public class CryptoTest {
    private static final byte[] TEST_IKM =
            new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    private static final byte[] TEST_SALT =
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x00};
    private static final int TEST_SIZE = 40;

    @Test
    public void testComputeHkdf_exceedMaxSize() {
        // Max size is 32*255
        assertNull(Crypto.computeHkdf(TEST_IKM, TEST_SALT, /* size= */ 10000));
    }

    @Test
    public void testComputeHkdf_emptySalt() {
        assertNull(Crypto.computeHkdf(TEST_IKM, new byte[] {}, TEST_SIZE));
    }

    @Test
    public void testComputeHkdf_emptyIkm() {
        assertNull(Crypto.computeHkdf(new byte[] {}, TEST_SALT, TEST_SIZE));
    }

    @Test
    public void testComputeHkdf_success() {
        assertNotNull(Crypto.computeHkdf(TEST_IKM, TEST_SALT, TEST_SIZE));
    }
}
