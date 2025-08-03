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

package com.android.car.settings.wifi.preferences;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.location.LocationSettingsFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class WifiWakeupTogglePreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private SwitchPreference mSwitchPreference;
    private WifiWakeupTogglePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private LocationManager mLocationManager;
    @Mock
    private WifiManager mWifiManager;
    @Mock
    private Toast mMockToast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);

        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new WifiWakeupTogglePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.mWifiManager = mWifiManager;
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);

        mSession = ExtendedMockito.mockitoSession().mockStatic(Toast.class,
                withSettings().lenient()).startMocking();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    @UiThreadTest
    public void handlePreferenceClicked_locationDisabled_launchFragment() {
        when(mLocationManager.isLocationEnabled()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        verify(mFragmentController).launchFragment(any(LocationSettingsFragment.class));
    }

    @Test
    public void handlePreferenceClicked_wifiWakeupEnabled_disablesWifiWakeup() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 1))
                .isEqualTo(0);
    }

    @Test
    public void handlePreferenceClicked_wifiScanningDisabled_showsDialog() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        verify(mFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void handlePreferenceClicked_wifiScanningEnabled_wifiWakeupDisabled_enablesWifiWakeup() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 0))
                .isEqualTo(1);
    }

    @Test
    public void onCreate_wifiWakeupEnabled_wifiScanningEnabled_locationEnabled_isChecked() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mSwitchPreference.setChecked(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onCreate_wifiWakeupDisabled_wifiScanningEnabled_locationEnabled_isNotChecked() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mSwitchPreference.setChecked(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void onCreate_wifiWakeupEnabled_wifiScanningDisabled_locationEnabled_isNotChecked() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mSwitchPreference.setChecked(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void onCreate_wifiWakeupEnabled_wifiScanningEnabled_locationDisabled_isNotChecked() {
        when(mLocationManager.isLocationEnabled()).thenReturn(false);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mSwitchPreference.setChecked(true);
        mPreferenceController.onCreate(mLifecycleOwner);


        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    @UiThreadTest
    public void onConfirmWifiScanning_setsWifiScanningOn() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        ExtendedMockito.when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.mConfirmListener.onConfirm(/* arguments= */ null);

        verify(mWifiManager).setScanAlwaysAvailable(true);
    }

    @Test
    @UiThreadTest
    public void onConfirmWifiScanning_showsToast() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        String toastString = mContext.getString(R.string.wifi_settings_scanning_required_enabled);
        ExtendedMockito.when(Toast.makeText(any(), eq(toastString), anyInt()))
                .thenReturn(mMockToast);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.mConfirmListener.onConfirm(/* arguments= */ null);

        verify(mMockToast).show();
    }

    @Test
    @UiThreadTest
    public void onConfirmWifiScanning_enablesWifiWakeup() {
        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        ExtendedMockito.when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);
        when(mWifiManager.isScanAlwaysAvailable()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.mConfirmListener.onConfirm(/* arguments= */ null);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 0)).isEqualTo(1);
    }
}
