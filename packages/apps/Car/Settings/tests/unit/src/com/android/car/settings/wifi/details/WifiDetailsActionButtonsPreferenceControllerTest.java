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

package com.android.car.settings.wifi.details;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiDetailsActionButtonsPreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ActionButtonsPreference mActionButtonsPreference;
    private WifiDetailsActionButtonsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private androidx.lifecycle.Lifecycle mMockLifecycle;
    @Mock
    private WifiEntry mMockWifiEntry;
    @Mock
    private WifiInfoProvider mMockWifiInfoProvider;
    @Mock
    private Network mMockNetwork;
    @Mock
    private NetworkInfo mMockNetworkInfo;
    @Mock
    private WifiInfo mMockWifiInfo;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mActionButtonsPreference = new ActionButtonsPreference(mContext);
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
        mPreferenceController = new WifiDetailsActionButtonsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.init(mMockWifiEntry, mMockWifiInfoProvider);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mActionButtonsPreference);

        when(mMockWifiInfoProvider.getNetwork()).thenReturn(mMockNetwork);
        when(mMockWifiInfoProvider.getNetworkInfo()).thenReturn(mMockNetworkInfo);
        when(mMockWifiInfoProvider.getWifiInfo()).thenReturn(mMockWifiInfo);
    }

    @Test
    public void updateState_connectNotNeeded_disconnectButtonShown() {
        when(mMockWifiEntry.isSaved()).thenReturn(true);
        when(mMockWifiEntry.canDisconnect()).thenReturn(true);
        when(mMockWifiEntry.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_CONNECTED);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON2).isVisible()).isTrue();
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON2).getText().toString())
                .isEqualTo(ResourceTestUtils.getString(mContext, "disconnect"));
    }

    @Test
    public void updateState_needConnect_connectButtonShown() {
        when(mMockWifiEntry.isSaved()).thenReturn(true);
        when(mMockWifiEntry.canConnect()).thenReturn(true);
        when(mMockWifiEntry.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_DISCONNECTED);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON2).isVisible()).isTrue();
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON2).getText().toString())
                .isEqualTo(ResourceTestUtils.getString(mContext, "wifi_setup_connect"));
    }

    @Test
    public void updateState_canForget_forgetButtonShown() {
        when(mMockWifiEntry.isSaved()).thenReturn(true);
        when(mMockWifiEntry.canForget()).thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON1).isVisible()).isTrue();
    }

    @Test
    public void updateState_canNotForget_forgetButtonHidden() {
        when(mMockWifiEntry.isSaved()).thenReturn(true);
        when(mMockWifiEntry.canForget()).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mActionButtonsPreference.getButton(ActionButtons.BUTTON1).isVisible()).isFalse();
    }
}
