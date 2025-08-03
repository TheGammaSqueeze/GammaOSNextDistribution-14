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

package com.android.car.settings.qc;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class BluetoothSwitchWorkerTest {
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private BluetoothSwitchWorker mWorker;

    @Before
    public void setUp() {
        mWorker = new BluetoothSwitchWorker(mContext, SettingsQCRegistry.BLUETOOTH_SWITCH_URI);
    }

    @Test
    public void onSubscribe_registersReceiver() {
        ArgumentCaptor<IntentFilter> captor = ArgumentCaptor.forClass(IntentFilter.class);
        mWorker.onQCItemSubscribe();
        verify(mContext).registerReceiver(any(BroadcastReceiver.class), captor.capture());
        assertThat(captor.getValue().hasAction(BluetoothAdapter.ACTION_STATE_CHANGED)).isTrue();
    }

    @Test
    public void onUnsubscribe_unregistersReceiver() {
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        mWorker.onQCItemSubscribe();
        verify(mContext).registerReceiver(captor.capture(), any(IntentFilter.class));
        mWorker.onQCItemUnsubscribe();
        verify(mContext).unregisterReceiver(captor.getValue());
    }
}
