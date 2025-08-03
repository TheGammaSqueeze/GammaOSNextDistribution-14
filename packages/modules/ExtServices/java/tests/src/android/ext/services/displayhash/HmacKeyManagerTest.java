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

package android.ext.services.displayhash;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;

import androidx.core.os.BuildCompat;

import org.junit.Before;
import org.junit.Test;

public class HmacKeyManagerTest {
    private final byte[] mSalt = "SALT".getBytes();
    private final long mTimestamp = 1000;
    private final Rect mBounds = new Rect(0, 0, 100, 200);
    private final int mHashAlgorithm = 1;
    private final byte[] mImageHash = {0, 3, 4, 3, 5, 2, 5, 1};

    private final HmacKeyManager mHmacKeyManager = new HmacKeyManager();

    @Before
    public void setup() {
        // HmacKeyManager was only introduced in S.
        assumeTrue(BuildCompat.isAtLeastS());
    }

    @Test
    public void testGenerateAndVerifyHmac() {
        byte[] hmac = generateHmac();

        assertTrue(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                mHashAlgorithm, mImageHash, hmac));
    }

    @Test
    public void testVerifyHmac_differentSalt() {
        byte[] hmac = generateHmac();

        assertFalse(
                mHmacKeyManager.verifyHmac("fake salt".getBytes(), mTimestamp, mBounds,
                        mHashAlgorithm, mImageHash, hmac));
    }

    @Test
    public void testVerifyHmac_differentTimestamp() {
        byte[] hmac = generateHmac();

        assertFalse(mHmacKeyManager.verifyHmac(mSalt, 2000 /* timestamp */, mBounds,
                mHashAlgorithm, mImageHash, hmac));
    }

    @Test
    public void testVerifyHmac_differentBounds() {
        byte[] hmac = generateHmac();

        assertFalse(
                mHmacKeyManager.verifyHmac(mSalt, mTimestamp, new Rect(10, 10, 110, 210),
                        mHashAlgorithm, mImageHash, hmac));
    }

    @Test
    public void testVerifyHmac_differentHashAlgorithm() {
        byte[] hmac = generateHmac();

        assertFalse(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                2 /* hashAlgorithm */, mImageHash, hmac));
    }

    @Test
    public void testVerifyHmac_differentImageHash() {
        byte[] hmac = generateHmac();

        assertFalse(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                mHashAlgorithm, new byte[]{0, 3, 4, 3, 5, 2, 5, 2}, hmac));
    }

    @Test
    public void testVerifyHmac_differentHmac() {
        assertFalse(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                mHashAlgorithm, mImageHash, new byte[32]));
    }

    @Test
    public void testVerifyHmac_invalidHmac() {
        assertFalse(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                mHashAlgorithm, mImageHash, null));

        assertFalse(mHmacKeyManager.verifyHmac(mSalt, mTimestamp, mBounds,
                mHashAlgorithm, mImageHash, new byte[8]));
    }

    private byte[] generateHmac() {
        byte[] hmac = mHmacKeyManager.generateHmac(mSalt, mTimestamp, mBounds, mHashAlgorithm,
                mImageHash);
        assertNotNull(hmac);
        return hmac;
    }
}
