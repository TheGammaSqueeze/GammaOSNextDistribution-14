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

package com.android.car.bluetooth;

import static android.bluetooth.BluetoothProfile.A2DP_SINK;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link BluetoothProfileInhibitManager}
 *
 * Run:
 * atest BluetoothProfileInhibitManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class BluetoothProfileInhibitManagerTest {

    @Mock
    private ICarBluetoothUserService mMockCarBluetoothUserService;

    @Mock
    private BluetoothDevice mMockBluetoothDevice;
    private BluetoothProfileInhibitManager mBluetoothProfileInhibitManager;

    private IBinder mToken = new Binder();

    //-------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        mBluetoothProfileInhibitManager = new BluetoothProfileInhibitManager(context,
                /* userId= */ 10,
                mMockCarBluetoothUserService);
        when(mMockCarBluetoothUserService.isBluetoothConnectionProxyAvailable(anyInt()))
                .thenReturn(true);
        when(mMockCarBluetoothUserService.getConnectionPolicy(anyInt(), any()))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mBluetoothProfileInhibitManager.start();
    }

    @After
    public void tearDown() {
        mBluetoothProfileInhibitManager.stop();
    }

    @Test
    public void testIsProfileInhibited_default_isNotInhibited() {
        assertThat(
                mBluetoothProfileInhibitManager.isProfileInhibited(mMockBluetoothDevice, A2DP_SINK,
                        mToken)).isFalse();
    }

    @Test
    public void testIsProfileInhibited_inhibitRequested_isInhibited() throws Exception {
        mBluetoothProfileInhibitManager.requestProfileInhibit(mMockBluetoothDevice, A2DP_SINK,
                mToken);
        when(mMockCarBluetoothUserService.getConnectionPolicy(A2DP_SINK, mMockBluetoothDevice))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        assertThat(
                mBluetoothProfileInhibitManager.isProfileInhibited(mMockBluetoothDevice, A2DP_SINK,
                        mToken)).isTrue();
    }

    @Test
    public void testIsProfileInhibited_proxyNotAvailable_isNotInhibited() throws Exception {
        mBluetoothProfileInhibitManager.requestProfileInhibit(mMockBluetoothDevice, A2DP_SINK,
                mToken);
        when(mMockCarBluetoothUserService.isBluetoothConnectionProxyAvailable(
                A2DP_SINK)).thenReturn(false);

        assertThat(
                mBluetoothProfileInhibitManager.isProfileInhibited(mMockBluetoothDevice, A2DP_SINK,
                        mToken)).isFalse();
    }

    @Test
    public void testIsProfileInhibited_profileAllowed_isNotInhibited() throws Exception {
        mBluetoothProfileInhibitManager.requestProfileInhibit(mMockBluetoothDevice, A2DP_SINK,
                mToken);
        when(mMockCarBluetoothUserService.getConnectionPolicy(A2DP_SINK, mMockBluetoothDevice))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        assertThat(
                mBluetoothProfileInhibitManager.isProfileInhibited(mMockBluetoothDevice, A2DP_SINK,
                        mToken)).isFalse();
    }

    @Test
    public void testIsProfileInhibited_inhibitReleased_isNotInhibited() {
        mBluetoothProfileInhibitManager.requestProfileInhibit(mMockBluetoothDevice, A2DP_SINK,
                mToken);
        mBluetoothProfileInhibitManager.releaseProfileInhibit(mMockBluetoothDevice, A2DP_SINK,
                mToken);

        assertThat(
                mBluetoothProfileInhibitManager.isProfileInhibited(mMockBluetoothDevice, A2DP_SINK,
                        mToken)).isFalse();
    }
}
