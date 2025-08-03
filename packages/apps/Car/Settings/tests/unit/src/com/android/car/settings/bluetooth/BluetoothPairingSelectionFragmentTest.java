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

package com.android.car.settings.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class BluetoothPairingSelectionFragmentTest {
    private BluetoothPairingSelectionFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MockitoSession mSession;

    @Mock
    private LocalBluetoothManager mMockManager;
    @Mock
    private BluetoothEventManager mMockEventManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                BluetoothUtils.class, withSettings().lenient()).startMocking();
        when(BluetoothUtils.getLocalBtManager(any())).thenReturn(
                mMockManager);
        when(mMockManager.getEventManager()).thenReturn(mMockEventManager);
        CachedBluetoothDeviceManager cbdm = mock(CachedBluetoothDeviceManager.class);
        when(mMockManager.getCachedDeviceManager()).thenReturn(cbdm);
        setUpFragment();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onStart_setsBluetoothManagerForegroundActivity() {
        verify(mMockManager).setForegroundActivity(mFragment.requireActivity());
    }

    @Test
    public void onStart_registersEventListener() {
        verify(mMockEventManager).registerCallback(mFragment.mCallback);
    }

    @Test
    public void onStart_showsProgressBar() {
        ToolbarController toolbar = mActivity.getToolbar();

        assertThat(toolbar.getProgressBar().isVisible()).isTrue();
    }

    @Test
    public void onStop_clearsBluetoothManagerForegroundActivity() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            mFragment.onStop();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockManager).setForegroundActivity(null);
    }

    @Test
    public void onStop_unregistersEventListener() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            mFragment.onStop();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockEventManager).registerCallback(mFragment.mCallback);
        verify(mMockEventManager).unregisterCallback(mFragment.mCallback);
    }

    @Test
    public void onStop_hidesProgressBar() throws Throwable {
        ToolbarController toolbar = mActivity.getToolbar();
        toolbar.getProgressBar().setVisible(true);

        mActivityTestRule.runOnUiThread(() -> {
            mFragment.onStop();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(toolbar.getProgressBar().isVisible()).isFalse();
    }

    @Test
    public void onDeviceBondStateChanged_deviceBonded_goesBack() throws Throwable {
        verify(mMockEventManager).registerCallback(mFragment.mCallback);

        mActivityTestRule.runOnUiThread(() -> {
            mFragment.mCallback.onDeviceBondStateChanged(mock(CachedBluetoothDevice.class),
                    BluetoothDevice.BOND_BONDED);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mActivity.getOnBackPressedFlag()).isTrue();
    }

    @Test
    public void onBluetoothDisabled_goesBack() throws Throwable {
        verify(mMockEventManager).registerCallback(mFragment.mCallback);

        mActivityTestRule.runOnUiThread(() -> {
            mFragment.mCallback.onBluetoothStateChanged(BluetoothAdapter.STATE_OFF);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mActivity.getOnBackPressedFlag()).isTrue();
    }

    private void setUpFragment() throws Throwable {
        String bluetoothPairingSelectionFragmentTag = "bluetooth_pairing_selection_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new BluetoothPairingSelectionFragment(),
                            bluetoothPairingSelectionFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (BluetoothPairingSelectionFragment)
                mFragmentManager.findFragmentByTag(bluetoothPairingSelectionFragmentTag);
    }
}
