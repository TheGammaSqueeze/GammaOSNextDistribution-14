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

package com.android.server.ondevicepersonalization;

import static com.android.server.ondevicepersonalization.OnDevicePersonalizationSystemService.KEY_NOT_FOUND_ERROR;
import static com.android.server.ondevicepersonalization.OnDevicePersonalizationSystemService.PERSONALIZATION_STATUS_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemService;
import android.ondevicepersonalization.IOnDevicePersonalizationSystemServiceCallback;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OdpSystemServiceImplTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_CONFIG_FILE_IDENTIFIER = "TEST_CONFIG";
    private static final String BAD_TEST_KEY = "non-exist-key";
    private final BooleanFileDataStore mTestDataStore =
                    new BooleanFileDataStore(mContext.getFilesDir().getAbsolutePath(),
                                    TEST_CONFIG_FILE_IDENTIFIER);
    private boolean mOnResultCalled;
    private boolean mOnErrorCalled;
    private Bundle mResult;
    private int mErrorCode;
    private CountDownLatch mLatch;
    private OnDevicePersonalizationSystemService mService;
    private IOnDevicePersonalizationSystemService mBinder;
    private IOnDevicePersonalizationSystemServiceCallback mCallback;

    @Before
    public void setUp() throws Exception {
        mService = new OnDevicePersonalizationSystemService(mContext, mTestDataStore);
        mBinder = IOnDevicePersonalizationSystemService.Stub.asInterface(mService);
        mOnResultCalled = false;
        mOnErrorCalled = false;
        mResult = null;
        mErrorCode = 0;
        mLatch = new CountDownLatch(1);
        mCallback = new IOnDevicePersonalizationSystemServiceCallback.Stub() {
            @Override
            public void onResult(Bundle bundle) {
                mOnResultCalled = true;
                mResult = bundle;
                mLatch.countDown();
            }

            @Override
            public void onError(int errorCode) {
                mOnErrorCalled = true;
                mErrorCode = errorCode;
                mLatch.countDown();
            }
        };
        assertNotNull(mBinder);
        assertNotNull(mCallback);
    }

    @Test
    public void testSystemServerServiceOnRequest() throws Exception {
        if (!SdkLevel.isAtLeastU()) {
            return;
        }
        mBinder.onRequest(new Bundle(), mCallback);
        mLatch.await();
        assertTrue(mOnResultCalled);
        assertNull(mResult);
    }

    @Test
    public void testSystemServerServiceSetPersonalizationStatus() throws Exception {
        if (!SdkLevel.isAtLeastU()) {
            return;
        }
        mBinder.setPersonalizationStatus(true, mCallback);
        mLatch.await();
        assertTrue(mOnResultCalled);
        assertNotNull(mResult);
        boolean inputBool = mResult.getBoolean(PERSONALIZATION_STATUS_KEY);
        assertTrue(inputBool);
    }

    @Test
    public void testSystemServerServiceReadPersonalizationStatusSuccess() throws Exception {
        if (!SdkLevel.isAtLeastU()) {
            return;
        }
        mTestDataStore.put(PERSONALIZATION_STATUS_KEY, true);
        mBinder.readPersonalizationStatus(mCallback);
        assertTrue(mOnResultCalled);
        assertNotNull(mResult);
        boolean inputBool = mResult.getBoolean(PERSONALIZATION_STATUS_KEY);
        assertTrue(inputBool);
    }

    @Test
    public void testSystemServerServiceReadPersonalizationStatusNotFound() throws Exception {
        if (!SdkLevel.isAtLeastU()) {
            return;
        }
        mTestDataStore.put(BAD_TEST_KEY, true);
        mBinder.readPersonalizationStatus(mCallback);
        assertTrue(mOnErrorCalled);
        assertNull(mResult);
        assertEquals(mErrorCode, KEY_NOT_FOUND_ERROR);
    }

    @After
    public void cleanUp() {
        mTestDataStore.tearDownForTesting();
    }
}
