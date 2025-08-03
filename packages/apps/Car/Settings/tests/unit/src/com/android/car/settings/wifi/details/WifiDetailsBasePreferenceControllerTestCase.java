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

package com.android.car.settings.wifi.details;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class WifiDetailsBasePreferenceControllerTestCase {

    protected Context mContext = ApplicationProvider.getApplicationContext();
    protected WifiDetailsPreference mPreference;
    protected LifecycleOwner mLifecycleOwner;
    protected CarUxRestrictions mCarUxRestrictions;

    @Mock
    protected FragmentController mFragmentController;
    @Mock
    protected WifiEntry mMockWifiEntry;
    @Mock
    protected WifiInfoProvider mMockWifiInfoProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new WifiDetailsPreference(mContext);
        getController().init(mMockWifiEntry, mMockWifiInfoProvider);
        PreferenceControllerTestUtil.assignPreference(getController(), mPreference);
        initTest();
    }

    @Test
    public void onCreate_connected_shouldHaveDetailTextSet() {
        setUpConnectedState();
        getController().onCreate(mLifecycleOwner);

        assertThat(mPreference.isVisible()).isTrue();
        assertThat(mPreference.getDetailText()).isEqualTo(getDesiredDetailText());
    }

    @Test
    public void onWifiChanged_isNotActive_preferenceNotVisible() {
        setUpDisconnectedState();
        getController().onCreate(mLifecycleOwner);

        assertThat(mPreference.isVisible()).isFalse();
    }

    protected void initTest() {
    }

    protected abstract WifiDetailsBasePreferenceController<WifiDetailsPreference> getController();

    protected void setUpConnectedState() {
        when(mMockWifiEntry.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_CONNECTED);
    }

    protected void setUpDisconnectedState() {
        when(mMockWifiEntry.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_DISCONNECTED);
    }

    protected abstract String getDesiredDetailText();
}
