/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ActionButtonInfo;
import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BluetoothTestUtils;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class BluetoothDeviceActionButtonsPreferenceControllerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private ActionButtonsPreference mActionButtonsPreference;
    private BluetoothDeviceActionButtonsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CachedBluetoothDevice mCachedDevice;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        // Ensure bluetooth is available and enabled.
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        BluetoothTestUtils.setBluetoothState(mContext, /* enable= */ true);

        String address = "00:11:22:33:AA:BB";
        when(mCachedDevice.getAddress()).thenReturn(address);

        mActionButtonsPreference = new ActionButtonsPreference(mContext);
        mPreferenceController = new BluetoothDeviceActionButtonsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.setCachedDevice(mCachedDevice);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mActionButtonsPreference);
    }

    @Test
    public void forgetButtonClicked_unpairsDevice() {
        mPreferenceController.onCreate(mLifecycleOwner);

        findForgetButton().getOnClickListener().onClick(/* view= */ null);

        verify(mCachedDevice).unpair();
    }

    @Test
    public void forgetButtonClicked_goesBack() {
        mPreferenceController.onCreate(mLifecycleOwner);

        findForgetButton().getOnClickListener().onClick(/* view= */ null);

        verify(mFragmentController).goBack();
    }

    @Test
    public void connectionButtonClicked_deviceConnected_disconnectsDevice() {
        when(mCachedDevice.isConnected()).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        findConnectionButton().getOnClickListener().onClick(/* view= */ null);

        verify(mCachedDevice).disconnect();
    }

    @Test
    public void connectionButtonClicked_deviceNotConnected_connectsDevice() {
        when(mCachedDevice.isConnected()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        findConnectionButton().getOnClickListener().onClick(/* view= */ null);

        verify(mCachedDevice).connect();
    }

    @Test
    public void deviceConnected_connectionButtonShowsDisconnect() {
        when(mCachedDevice.isConnected()).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(findConnectionButton().getText()).isEqualTo(
                ResourceTestUtils.getString(mContext, "disconnect"));
    }

    @Test
    public void deviceNotConnected_connectionButtonShowsConnect() {
        when(mCachedDevice.isConnected()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(findConnectionButton().getText()).isEqualTo(
                ResourceTestUtils.getString(mContext, "connect"));
    }

    @Test
    public void deviceBusy_connectionButtonDisabled() {
        when(mCachedDevice.isBusy()).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(findConnectionButton().isEnabled()).isFalse();
    }

    @Test
    public void deviceNotBusy_connectionButtonEnabled() {
        when(mCachedDevice.isBusy()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(findConnectionButton().isEnabled()).isTrue();
    }

    @Test
    public void deviceAttributesChanged_updatesConnectionButtonState() {
        when(mCachedDevice.isBusy()).thenReturn(true);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        assertThat(findConnectionButton().isEnabled()).isFalse();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        when(mCachedDevice.isBusy()).thenReturn(false);
        callbackCaptor.getValue().onDeviceAttributesChanged();

        assertThat(findConnectionButton().isEnabled()).isTrue();
    }

    private ActionButtonInfo findForgetButton() {
        return mActionButtonsPreference.getButton(ActionButtons.BUTTON2);
    }

    private ActionButtonInfo findConnectionButton() {
        return mActionButtonsPreference.getButton(ActionButtons.BUTTON1);
    }
}
