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

package com.android.server.nearby.managers;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY;
import static com.android.server.nearby.NearbyConfiguration.NEARBY_SUPPORT_TEST_APP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.Context;
import android.nearby.BroadcastCallback;
import android.nearby.BroadcastRequest;
import android.nearby.IBroadcastListener;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;
import android.nearby.PrivateCredential;
import android.os.IBinder;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.provider.BleBroadcastProvider;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

/**
 * Unit test for {@link com.android.server.nearby.managers.BroadcastProviderManager}.
 */
public class BroadcastProviderManagerTest {
    private static final String NAMESPACE = NearbyConfiguration.getNamespace();
    private static final byte[] IDENTITY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final int MEDIUM_TYPE_BLE = 0;
    private static final byte[] SALT = {2, 3};
    private static final byte TX_POWER = 4;
    private static final int PRESENCE_ACTION = 123;
    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{12, 13, 14};
    private static final String DEVICE_NAME = "test_device";

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    IBroadcastListener mBroadcastListener;
    @Mock
    BleBroadcastProvider mBleBroadcastProvider;
    @Mock
    IBinder mBinder;
    private Context mContext;
    private BroadcastProviderManager mBroadcastProviderManager;
    private BroadcastRequest mBroadcastRequest;
    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        when(mBroadcastListener.asBinder()).thenReturn(mBinder);
        mUiAutomation.adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG, READ_DEVICE_CONFIG);
        DeviceConfig.setProperty(
                NAMESPACE, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY, "true", false);
        DeviceConfig.setProperty(
                NAMESPACE, NEARBY_SUPPORT_TEST_APP, "true", false);

        mContext = ApplicationProvider.getApplicationContext();
        mBroadcastProviderManager = new BroadcastProviderManager(
                MoreExecutors.directExecutor(),
                mBleBroadcastProvider);

        PrivateCredential privateCredential =
                new PrivateCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, IDENTITY, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mBroadcastRequest =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        SALT, privateCredential)
                        .setTxPower(TX_POWER)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V0)
                        .addAction(PRESENCE_ACTION).build();
    }

    @Test
    public void testStartAdvertising() throws Exception {
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        verify(mBleBroadcastProvider).start(eq(BroadcastRequest.PRESENCE_VERSION_V0),
                any(byte[].class), any(BleBroadcastProvider.BroadcastListener.class));
        verify(mBinder).linkToDeath(any(), eq(0));
    }

    @Test
    public void testStopAdvertising() {
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        mBroadcastProviderManager.stopBroadcast(mBroadcastListener);
        verify(mBinder).unlinkToDeath(any(), eq(0));
    }

    @Test
    public void testRegisterAdvertising_twoTimes_fail() throws Exception {
        IBroadcastListener newListener = mock(IBroadcastListener.class);
        IBinder newBinder = mock(IBinder.class);
        when(newListener.asBinder()).thenReturn(newBinder);
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, newListener);
        verify(newListener).onStatusChanged(eq(BroadcastCallback.STATUS_FAILURE));
    }

    @Test
    public void testStartAdvertising_featureDisabled() throws Exception {
        DeviceConfig.setProperty(
                NAMESPACE, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY, "false", false);
        DeviceConfig.setProperty(
                NAMESPACE, NEARBY_SUPPORT_TEST_APP, "false", false);

        mBroadcastProviderManager = new BroadcastProviderManager(MoreExecutors.directExecutor(),
                mBleBroadcastProvider);
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        verify(mBroadcastListener).onStatusChanged(eq(BroadcastCallback.STATUS_FAILURE));
    }

    @Test
    public void testOnStatusChanged() throws Exception {
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        mBroadcastProviderManager.onStatusChanged(BroadcastCallback.STATUS_OK);
        verify(mBroadcastListener).onStatusChanged(eq(BroadcastCallback.STATUS_OK));
    }
}
