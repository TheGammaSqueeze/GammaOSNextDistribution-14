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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.WifiManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class WifiTetheringHandlerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private SwitchPreference mSwitchPreference;
    private TestWifiTetheringPreferenceController mPreferenceController;
    private WifiTetheringHandler mWifiTetheringHandler;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private CarWifiManager mCarWifiManager;
    @Mock
    private TetheringManager mTetheringManager;
    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new TestWifiTetheringPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mWifiTetheringHandler = new WifiTetheringHandler(mContext, mCarWifiManager,
                mTetheringManager, mPreferenceController, /* monitorRestarts= */ true);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @Test
    public void onCreate_tetherStateOn_shouldReturnSwitchStateOn() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mWifiTetheringHandler.isWifiTetheringEnabled()).isTrue();
    }

    @Test
    public void onCreate_tetherStateOff_shouldReturnSwitchStateOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mWifiTetheringHandler.isWifiTetheringEnabled()).isFalse();
    }

    @Test
    public void onSwitchOn_shouldAttemptTetherOn() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.updateWifiTetheringState(true);

        verify(mTetheringManager).startTethering(
                eq(ConnectivityManager.TETHERING_WIFI),
                any(Executor.class), any(TetheringManager.StartTetheringCallback.class)
        );
    }

    @Test
    public void onSwitchOff_shouldAttemptTetherOff() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.updateWifiTetheringState(false);

        verify(mTetheringManager).stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    @Test
    public void onTetherEnabling_shouldReturnSwitchStateDisabled() {
        mSwitchPreference.setEnabled(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.handleWifiApStateChanged(WifiManager.WIFI_AP_STATE_ENABLING);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void onTetherEnabled_shouldReturnSwitchStateEnabledAndOn() {
        mSwitchPreference.setEnabled(false);
        mSwitchPreference.setChecked(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.handleWifiApStateChanged(WifiManager.WIFI_AP_STATE_ENABLED);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onTetherDisabled_shouldReturnSwitchStateEnabledAndOff() {
        mSwitchPreference.setEnabled(false);
        mSwitchPreference.setChecked(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.handleWifiApStateChanged(WifiManager.WIFI_AP_STATE_DISABLED);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    @Test
    public void onEnableTetherFailed_shouldReturnSwitchStateEnabledAndOff() {
        mSwitchPreference.setEnabled(false);
        mSwitchPreference.setChecked(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mWifiTetheringHandler.handleWifiApStateChanged(WifiManager.WIFI_AP_STATE_ENABLING);
        mWifiTetheringHandler.handleWifiApStateChanged(WifiManager.WIFI_AP_STATE_FAILED);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
        assertThat(mSwitchPreference.isChecked()).isFalse();
    }

    private static class TestWifiTetheringPreferenceController
            extends PreferenceController<ColoredSwitchPreference>
            implements WifiTetheringHandler.WifiTetheringAvailabilityListener{

        TestWifiTetheringPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<ColoredSwitchPreference> getPreferenceType() {
            return ColoredSwitchPreference.class;
        }

        @Override
        public void onWifiTetheringAvailable() {
            getPreference().setChecked(true);
        }

        @Override
        public void onWifiTetheringUnavailable() {
            getPreference().setChecked(false);
        }

        @Override
        public void enablePreference() {
            getPreference().setEnabled(true);
        }

        @Override
        public void disablePreference() {
            getPreference().setEnabled(false);
        }
    }
}
