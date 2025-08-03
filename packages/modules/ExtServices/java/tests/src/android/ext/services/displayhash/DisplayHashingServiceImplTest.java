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

import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM;
import static android.view.displayhash.DisplayHashResultCallback.DISPLAY_HASH_ERROR_UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.displayhash.DisplayHash;
import android.view.displayhash.DisplayHashResultCallback;
import android.view.displayhash.VerifiedDisplayHash;

import androidx.annotation.NonNull;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class DisplayHashingServiceImplTest {
    private final DisplayHashingServiceImpl mService = new DisplayHashingServiceImpl();

    private final byte[] mSalt = "SALT".getBytes();
    private final String mHashAlgorithm = "PHASH";
    private final HardwareBuffer mBuffer = HardwareBuffer.create(10, 10,
            HardwareBuffer.RGBX_8888, 1, HardwareBuffer.USAGE_CPU_READ_RARELY);
    private final Rect mBounds = new Rect(0, 0, 10, 10);

    private SyncDisplayHashResultCallback mDisplayHashResultCallback;

    @Before
    public void setUp() throws Exception {
        mService.onCreate();
        mService.setImageHashManager(new MockImageHashManager());
        mDisplayHashResultCallback = new SyncDisplayHashResultCallback();
    }

    @Test
    public void testOnGenerateAndOnVerifyDisplayHash() {
        mService.onGenerateDisplayHash(mSalt, mBuffer, mBounds, mHashAlgorithm,
                mDisplayHashResultCallback);
        DisplayHash DisplayHash = mDisplayHashResultCallback.getDisplayHash();
        assertNotNull(DisplayHash);

        VerifiedDisplayHash verifiedDisplayHash = mService.onVerifyDisplayHash(mSalt,
                DisplayHash);
        assertNotNull(verifiedDisplayHash);

        assertEquals(DisplayHash.getBoundsInWindow(), verifiedDisplayHash.getBoundsInWindow());
        assertEquals(DisplayHash.getHashAlgorithm(), verifiedDisplayHash.getHashAlgorithm());
        assertEquals(DisplayHash.getTimeMillis(), verifiedDisplayHash.getTimeMillis());
        assertEquals(DisplayHash.getImageHash(), verifiedDisplayHash.getImageHash());
    }

    @Test
    public void testOnGenerateDisplayHash_nullSalt() {
        mService.onGenerateDisplayHash(null, mBuffer, mBounds, mHashAlgorithm,
                mDisplayHashResultCallback);
        int errorCode = mDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_UNKNOWN, errorCode);
    }

    @Test
    public void testOnGenerateDisplayHash_invalidHashAlgorithm() {
        mService.onGenerateDisplayHash(mSalt, mBuffer, mBounds, null /* hashAlgorithm */,
                mDisplayHashResultCallback);
        int errorCode = mDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM, errorCode);

        mDisplayHashResultCallback.reset();
        mService.onGenerateDisplayHash(mSalt, mBuffer, mBounds, "fake hash",
                mDisplayHashResultCallback);
        errorCode = mDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM, errorCode);
    }

    @Test
    public void testOnGenerateDisplayHash_nullBuffer() {
        mService.onGenerateDisplayHash(mSalt, null /* buffer */, mBounds, mHashAlgorithm,
                mDisplayHashResultCallback);
        int errorCode = mDisplayHashResultCallback.getError();
        assertEquals(DISPLAY_HASH_ERROR_UNKNOWN, errorCode);
    }

    @Test
    public void testOnVerifyDisplayHash_nullSalt() {
        mService.onGenerateDisplayHash(mSalt, mBuffer, mBounds, mHashAlgorithm,
                mDisplayHashResultCallback);
        DisplayHash DisplayHash = mDisplayHashResultCallback.getDisplayHash();
        assertNotNull(DisplayHash);

        VerifiedDisplayHash verifiedDisplayHash = mService.onVerifyDisplayHash(null /* salt */,
                DisplayHash);
        assertNull(verifiedDisplayHash);
    }

    @Test
    public void testOnVerifyDisplayHash_nullDisplayHash() {
        mService.onGenerateDisplayHash(mSalt, mBuffer, mBounds, mHashAlgorithm,
                mDisplayHashResultCallback);
        DisplayHash DisplayHash = mDisplayHashResultCallback.getDisplayHash();
        assertNotNull(DisplayHash);

        VerifiedDisplayHash verifiedDisplayHash = mService.onVerifyDisplayHash(mSalt,
                null /* DisplayHash */);
        assertNull(verifiedDisplayHash);
    }

    @Test
    public void testOnGetIntervalBetweenRequestsMillis() {
        int intervalBetweenRequests = mService.onGetIntervalBetweenRequestsMillis();
        assertEquals(10000, intervalBetweenRequests);
    }

    private static class SyncDisplayHashResultCallback implements DisplayHashResultCallback {
        private static final int DISPLAY_HASH_WAIT_TIME_S = 1;
        private DisplayHash mDisplayHash;
        private int mError;
        private CountDownLatch mCountDownLatch = new CountDownLatch(1);

        public void reset() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public DisplayHash getDisplayHash() {
            try {
                mCountDownLatch.await(DISPLAY_HASH_WAIT_TIME_S, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
            return mDisplayHash;
        }

        public int getError() {
            try {
                mCountDownLatch.await(DISPLAY_HASH_WAIT_TIME_S, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
            return mError;
        }

        @Override
        public void onDisplayHashResult(@NonNull DisplayHash displayHash) {
            mDisplayHash = displayHash;
            mCountDownLatch.countDown();
        }

        @Override
        public void onDisplayHashError(int errorCode) {
            mError = errorCode;
            mCountDownLatch.countDown();
        }
    }
}
