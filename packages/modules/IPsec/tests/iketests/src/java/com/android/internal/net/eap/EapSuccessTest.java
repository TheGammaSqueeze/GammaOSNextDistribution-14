/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.net.eap.test;

import static com.android.internal.net.TestUtils.hexStringToByteArray;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import com.android.internal.net.eap.test.EapResult.EapSuccess;

import org.junit.Test;

public class EapSuccessTest {
    public static final byte[] MSK =
            hexStringToByteArray(
                    "c6543c0eff90dd4ed59df6ce4b28629f2459cc256cec313af9865deb38e3ee7c"
                            + "d991a4aa4fa3d0e97b40e377c5e8cb0d2b75b7635c4bca4ac50c96bf012fabe0");
    public static final byte[] EMSK =
            hexStringToByteArray(
                    "00e72d558d3ff15724ab7f9fc57a6c7881f6669a7425e02431a2333e3a12af30"
                            + "9fca3b68cce7e39d0273a0a36dad31e574bfd254ff2c8bc5c02027f4ce8ce753");
    @Test
    public void testEapSuccessConstructor() {
        EapSuccess eapSuccess = new EapSuccess(MSK, EMSK);
        assertArrayEquals(MSK, eapSuccess.msk);
        assertArrayEquals(EMSK, eapSuccess.emsk);
    }

    @Test
    public void testEapSuccessConstructorNullMsk() {
        try {
            new EapSuccess(null, EMSK);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testEapSuccessConstructorNullEmsk() {
        try {
            new EapSuccess(MSK, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
