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

package com.android.systemui.car.statusicon.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.policy.BluetoothController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class BluetoothStatusIconControllerTest extends SysuiTestCase {

    @Mock
    Resources mResources;
    @Mock
    BluetoothController mBluetoothController;
    @Mock
    CachedBluetoothDevice mDevice;
    private BluetoothStatusIconController mBluetoothStatusIconController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mBluetoothStatusIconController = new BluetoothStatusIconController(mContext, mResources,
                mBluetoothController);
    }

    @Test
    public void onInit_registersBluetoothCallback() {
        verify(mBluetoothController).addCallback(any());
    }

    @Test
    public void onDestroy_removeBluetoothCallback() {
        mBluetoothStatusIconController.onDestroy();
        verify(mBluetoothController).removeCallback(any());
    }

    @Test
    public void onBluetoothStateChange_notEnabled_showsBluetoothOff() {
        mBluetoothStatusIconController.onBluetoothStateChange(/* enabled= */ false);

        assertThat(mBluetoothStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mBluetoothStatusIconController.getBluetoothOffDrawable());
    }

    @Test
    public void onBluetoothDevicesChanged_enabled_deviceConnected_showsBluetoothOnConnected() {
        List<CachedBluetoothDevice> connectedDevices = new ArrayList<>();
        connectedDevices.add(mDevice);
        when(mBluetoothController.getConnectedDevices()).thenReturn(connectedDevices);
        mBluetoothStatusIconController.onBluetoothStateChange(/* enabled= */ true);
        mBluetoothStatusIconController.onBluetoothDevicesChanged();

        assertThat(mBluetoothStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mBluetoothStatusIconController.getBluetoothOnConnectedDrawable());
    }

    @Test
    public void onBluetoothDevicesChanged_enabled_noDeviceConnected_showsBluetoothOnDisconnected() {
        List<CachedBluetoothDevice> connectedDevices = new ArrayList<>();
        when(mBluetoothController.getConnectedDevices()).thenReturn(connectedDevices);
        mBluetoothStatusIconController.onBluetoothStateChange(/* enabled= */ true);
        mBluetoothStatusIconController.onBluetoothDevicesChanged();

        assertThat(mBluetoothStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mBluetoothStatusIconController.getBluetoothOnDisconnectedDrawable());
    }
}
