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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class PairedBluetoothDevicesWorkerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private PairedBluetoothDevicesWorker mWorker;
    private MockitoSession mSession;

    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private BluetoothEventManager mBluetoothEventManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBluetoothManager.class, withSettings().lenient()).startMocking();
        when(LocalBluetoothManager.getInstance(any(), any())).thenReturn(mLocalBluetoothManager);
        when(mLocalBluetoothManager.getEventManager()).thenReturn(mBluetoothEventManager);
        mWorker = new PairedBluetoothDevicesWorker(mContext,
                SettingsQCRegistry.PAIRED_BLUETOOTH_DEVICES_URI);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onSubscribe_registersReceiver() {
        mWorker.onQCItemSubscribe();
        verify(mBluetoothEventManager).registerCallback(mWorker);
    }

    @Test
    public void onUnsubscribe_unregistersReceiver() {
        mWorker.onQCItemSubscribe();
        mWorker.onQCItemUnsubscribe();
        verify(mBluetoothEventManager).unregisterCallback(mWorker);
    }
}
