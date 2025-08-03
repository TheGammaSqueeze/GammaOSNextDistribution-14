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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LimitedWifiEntryListPreferenceControllerTest {
    private static final int SIGNAL_LEVEL = 1;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private LimitedWifiEntryListPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private CarWifiManager mMockCarWifiManager;
    @Mock
    private WifiEntry mMockWifiEntry1;
    @Mock
    private WifiEntry mMockWifiEntry2;
    @Mock
    private WifiEntry mMockWifiEntry3;
    @Mock
    private WifiEntry mMockWifiEntry4;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
        mPreferenceController = new TestLimitedWifiEntryListPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry1.getLevel()).thenReturn(SIGNAL_LEVEL);
        when(mMockWifiEntry2.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry2.getLevel()).thenReturn(SIGNAL_LEVEL);
        when(mMockWifiEntry3.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry3.getLevel()).thenReturn(SIGNAL_LEVEL);
        when(mMockWifiEntry4.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry4.getLevel()).thenReturn(SIGNAL_LEVEL);
    }

    @Test
    public void connected_oneSavedNetworks_twoWifiEntries() {
        List<WifiEntry> wifiEntryList = new ArrayList<>(Arrays.asList(mMockWifiEntry1));
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getConnectedWifiEntries())
                .thenReturn(Collections.singletonList(mMockWifiEntry1));

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void notConnected_oneSavedNetworks_oneWifiEntry() {
        List<WifiEntry> wifiEntryList = Collections.singletonList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getConnectedWifiEntries()).thenReturn(Collections.emptyList());

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void connected_moreThanTwoSavedNetworks_maxWifiEntries() {
        int maxWifiEntryCount = mContext.getResources().getInteger(
                R.integer.limited_wifi_entry_list_count);
        List<WifiEntry> wifiEntryList = new ArrayList<>(
                Arrays.asList(mMockWifiEntry1, mMockWifiEntry2, mMockWifiEntry3));
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getConnectedWifiEntries())
                .thenReturn(Collections.singletonList(mMockWifiEntry1));

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(maxWifiEntryCount);
    }

    @Test
    public void notConnected_moreThanThreeSavedNetworks_maxWifiEntries() {
        int maxWifiEntryCount = mContext.getResources().getInteger(
                R.integer.limited_wifi_entry_list_count);
        List<WifiEntry> wifiEntryList = new ArrayList<>(
                Arrays.asList(mMockWifiEntry1, mMockWifiEntry2, mMockWifiEntry3, mMockWifiEntry4));
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getConnectedWifiEntries()).thenReturn(Collections.emptyList());

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(maxWifiEntryCount);
    }

    private class TestLimitedWifiEntryListPreferenceController
            extends LimitedWifiEntryListPreferenceController {

        TestLimitedWifiEntryListPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected CarWifiManager getCarWifiManager() {
            return mMockCarWifiManager;
        }
    }
}
