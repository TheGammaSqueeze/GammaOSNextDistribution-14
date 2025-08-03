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

package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class LocationServicesPreferenceControllerTest {
    private static final int PROFILE_ID = UserHandle.USER_CURRENT;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceCategory mPreference;
    private LocationServicesPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private CarLocationSettingsInjector mSettingsInjector;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new PreferenceCategory(mContext);
        screen.addPreference(mPreference);
        mPreferenceController = new LocationServicesPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.setSettingsInjector(mSettingsInjector);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void onStart_registersBroadcastReceiver() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(captor.capture(), any(),
                eq(Context.RECEIVER_EXPORTED));

        captor.getValue().onReceive(mContext, new Intent());
        verify(mSettingsInjector).reloadStatusMessages();
    }

    @Test
    public void onStop_unregistersBroadcastReceiver() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(captor.capture(), any(),
                eq(Context.RECEIVER_EXPORTED));

        mPreferenceController.onStop(mLifecycleOwner);
        verify(mContext).unregisterReceiver(captor.getValue());
    }

    @Test
    public void updateState_addsInjectedSettingsToPreferenceCategory() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Map<Integer, List<Preference>> samplePrefs = getSamplePreferences();
        doReturn(samplePrefs).when(mSettingsInjector)
                .getInjectedSettings(any(Context.class), anyInt());

        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(samplePrefs.get(PROFILE_ID).size());
    }

    @Test
    public void updateState_updatesPreferenceSummary() {
        Map<Integer, List<Preference>> samplePrefs = getSamplePreferences();
        doReturn(samplePrefs).when(mSettingsInjector)
                .getInjectedSettings(any(Context.class), anyInt());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        String summary = "Summary";
        samplePrefs.get(PROFILE_ID).get(0).setSummary(summary);

        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.getPreference(0).getSummary()).isEqualTo(summary);
    }

    @Test
    public void preferenceCategory_isVisibleIfThereAreInjectedSettings() {
        doReturn(getSamplePreferences()).when(mSettingsInjector)
                .getInjectedSettings(any(Context.class), anyInt());
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void preferenceCategory_isHiddenIfThereAreNoInjectedSettings() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    private Map<Integer, List<Preference>> getSamplePreferences() {
        Map<Integer, List<Preference>> preferences = new ArrayMap<>();
        preferences.put(PROFILE_ID,
                Arrays.asList(new Preference(mContext), new Preference(mContext),
                        new Preference(mContext)));
        return preferences;
    }
}
