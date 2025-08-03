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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ResetNetworkItemsPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private ResetNetworkItemsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private PackageManager mMockPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mContext.getPackageManager()).thenReturn(mMockPm);

        mPreference = new Preference(mContext);
        mPreferenceController = new ResetNetworkItemsPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onCreate_containsDefaultDescription() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ false,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_desc));
    }

    @Test
    public void onCreate_noWifi_doesNotContainWifiText() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ false,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertDoesNotContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_wifi));
    }

    @Test
    public void onCreate_noTelephony_doesNotContainTelephonyText() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ false,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertDoesNotContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_mobile));
    }

    @Test
    public void onCreate_noBluetooth_doesNotContainBluetoothText() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ false,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertDoesNotContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_bluetooth));
    }

    @Test
    public void onCreate_hasWifi_containsWifiText() {
        setSystemFeatures(/* hasWifi= */ true, /* hasTelephony= */ false,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_wifi));
    }

    @Test
    public void onCreate_hasTelephony_containsTelephonyText() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ true,
                /* hasBluetooth= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_mobile));
    }

    @Test
    public void onCreate_hasBluetooth_containsBluetoothText() {
        setSystemFeatures(/* hasWifi= */ false, /* hasTelephony= */ false,
                /* hasBluetooth= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertContainsString(mPreference.getTitle(),
                mContext.getString(R.string.reset_network_item_bluetooth));
    }

    private void setSystemFeatures(boolean hasWifi, boolean hasTelephony, boolean hasBluetooth) {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(hasWifi);
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(hasTelephony);
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).thenReturn(hasBluetooth);
    }

    private void assertContainsString(CharSequence source, String pattern) {
        assertThat(source).isNotNull();
        String sourceString = source.toString();
        assertThat(sourceString.contains(pattern)).isTrue();
    }

    private void assertDoesNotContainsString(CharSequence source, String pattern) {
        assertThat(source).isNotNull();
        String sourceString = source.toString();
        assertThat(sourceString.contains(pattern)).isFalse();
    }
}
