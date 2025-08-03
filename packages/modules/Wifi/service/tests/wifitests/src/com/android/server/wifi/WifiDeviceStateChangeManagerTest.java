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

package com.android.server.wifi;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class WifiDeviceStateChangeManagerTest extends WifiBaseTest {

    @Mock Context mContext;
    @Mock WifiDeviceStateChangeManager.StateChangeCallback mStateChangeCallback;
    @Mock PowerManager mPowerManager;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    private TestLooper mLooper;
    private Handler mHandler;
    private WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mPowerManager.isInteractive()).thenReturn(true);
        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        mWifiDeviceStateChangeManager = new WifiDeviceStateChangeManager(mContext, mHandler);

    }

    @Test
    public void testRegisterBeforeBootCompleted() {
        mWifiDeviceStateChangeManager.registerStateChangeCallback(mStateChangeCallback);
        // Should be no callback event before the boot completed
        verify(mStateChangeCallback, never()).onScreenStateChanged(anyBoolean());
        mWifiDeviceStateChangeManager.handleBootCompleted();
        verify(mContext, atLeastOnce())
                .registerReceiver(mBroadcastReceiverCaptor.capture(), any(), any(), any());
        verify(mStateChangeCallback).onScreenStateChanged(true);
        reset(mStateChangeCallback);
        setScreenState(true);
        verify(mStateChangeCallback).onScreenStateChanged(true);
        setScreenState(false);
        verify(mStateChangeCallback).onScreenStateChanged(false);
        reset(mStateChangeCallback);
        mWifiDeviceStateChangeManager.unregisterStateChangeCallback(mStateChangeCallback);
        setScreenState(true);
        verify(mStateChangeCallback, never()).onScreenStateChanged(anyBoolean());
    }

    @Test
    public void testRegisterAfterBootCompleted() {
        mWifiDeviceStateChangeManager.handleBootCompleted();
        verify(mContext, atLeastOnce())
                .registerReceiver(mBroadcastReceiverCaptor.capture(), any(), any(), any());
        mWifiDeviceStateChangeManager.registerStateChangeCallback(mStateChangeCallback);
        // Register after boot completed should immediately get a callback
        verify(mStateChangeCallback).onScreenStateChanged(true);
    }

    private void setScreenState(boolean screenOn) {
        BroadcastReceiver broadcastReceiver = mBroadcastReceiverCaptor.getValue();
        assertNotNull(broadcastReceiver);
        Intent intent = new Intent(screenOn ? ACTION_SCREEN_ON : ACTION_SCREEN_OFF);
        broadcastReceiver.onReceive(mContext, intent);
    }
}
