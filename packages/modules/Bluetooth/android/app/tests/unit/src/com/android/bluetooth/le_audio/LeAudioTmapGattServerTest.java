/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bluetooth.le_audio;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothUuid;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LeAudioTmapGattServerTest {
    private static final int TEST_ROLE_MASK =
            LeAudioTmapGattServer.TMAP_ROLE_FLAG_CG | LeAudioTmapGattServer.TMAP_ROLE_FLAG_UMS;

    @Mock
    private LeAudioTmapGattServer.BluetoothGattServerProxy mGattServerProxy;

    private LeAudioTmapGattServer mServer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(true).when(mGattServerProxy).open(any());
        doReturn(true).when(mGattServerProxy).addService(any());
        mServer = new LeAudioTmapGattServer(mGattServerProxy);
    }

    @After
    public void tearDown() {
        mServer = null;
    }

    @Test
    public void testStartStopService() {
        ArgumentCaptor<BluetoothGattService> captor =
                ArgumentCaptor.forClass(BluetoothGattService.class);
        mServer.start(TEST_ROLE_MASK);
        verify(mGattServerProxy, times(1)).open(any());
        verify(mGattServerProxy, times(1)).addService(captor.capture());

        // verify primary service with TMAP UUID
        BluetoothGattService service = captor.getValue();
        assertThat(service).isNotNull();
        assertThat(service.getUuid()).isEqualTo(BluetoothUuid.TMAP.getUuid());
        assertThat(service.getType()).isEqualTo(BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // verify characteristic UUID, permission and property
        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(LeAudioTmapGattServer.UUID_TMAP_ROLE);
        assertThat(characteristic).isNotNull();
        assertThat(characteristic.getProperties()).isEqualTo(PROPERTY_READ);
        assertThat(characteristic.getPermissions()).isEqualTo(PERMISSION_READ);

        // verify characteristic value
        int value = characteristic.getIntValue(FORMAT_UINT16, 0);
        assertThat(value).isEqualTo(TEST_ROLE_MASK);

        // verify stop triggers stop method call
        mServer.stop();
        verify(mGattServerProxy, times(1)).close();
    }

    @Test
    public void testStartServiceFailed() {
        // Verify throw exception when failed to open GATT server
        doReturn(false).when(mGattServerProxy).open(any());
        assertThrows(IllegalStateException.class, () -> mServer.start(TEST_ROLE_MASK));
    }
}
