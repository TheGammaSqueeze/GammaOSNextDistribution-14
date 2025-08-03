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

package com.android.server.remoteauth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.connectivity.Connection;
import com.android.server.remoteauth.jni.INativeRemoteAuthService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link com.android.server.remoteauth.RemoteAuthPlatform} */
@RunWith(AndroidJUnit4.class)
public class RemoteAuthPlatformTest {
    @Mock private Connection mConnection;
    @Mock private RemoteAuthConnectionCache mConnectionCache;
    private RemoteAuthPlatform mPlatform;
    private static final int CONNECTION_ID = 1;
    private static final byte[] REQUEST = new byte[] {(byte) 0x01};

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPlatform = new RemoteAuthPlatform(mConnectionCache);
    }

    @Test
    public void testSendRequest_connectionIsNull() {
        doReturn(null).when(mConnectionCache).getConnection(anyInt());
        assertFalse(
                mPlatform.sendRequest(
                        CONNECTION_ID,
                        REQUEST,
                        new INativeRemoteAuthService.IPlatform.ResponseCallback() {
                            @Override
                            public void onSuccess(byte[] response) {}

                            @Override
                            public void onFailure(int errorCode) {}
                        }));
    }

    @Test
    public void testSendRequest_connectionExists() {
        doReturn(mConnection).when(mConnectionCache).getConnection(anyInt());
        assertTrue(
                mPlatform.sendRequest(
                        CONNECTION_ID,
                        REQUEST,
                        new INativeRemoteAuthService.IPlatform.ResponseCallback() {
                            @Override
                            public void onSuccess(byte[] response) {}

                            @Override
                            public void onFailure(int errorCode) {}
                        }));
        verify(mConnection, times(1)).sendRequest(eq(REQUEST), anyObject());
    }
}
