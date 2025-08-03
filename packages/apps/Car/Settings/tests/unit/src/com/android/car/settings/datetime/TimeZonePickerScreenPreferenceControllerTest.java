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

package com.android.car.settings.datetime;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.timezonedetector.ManualTimeZoneSuggestion;
import android.app.timezonedetector.TimeZoneDetector;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.datetime.ZoneGetter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class TimeZonePickerScreenPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private TimeZonePickerScreenPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private TimeZoneDetector mTimeZoneDetector;

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
        mPreferenceController = new TimeZonePickerScreenPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        // Test setup.
        mPreferenceController.mTimeZoneDetector = mTimeZoneDetector;
    }

    @Test
    public void testOnCreate_hasElements() {
        List<Map<String, Object>> testTimeZones = new ArrayList<>();
        testTimeZones.add(
                createTimeZoneMap("testKey1", "Midway", "GMT-11:00", -1100));
        testTimeZones.add(
                createTimeZoneMap("testKey2", "Tijuana", "GMT-07:00", -700));
        testTimeZones.add(
                createTimeZoneMap("testKey3", "Coordinated Universal Time", "GMT+00:00", 0));
        testTimeZones.add(
                createTimeZoneMap("testKey4", "Kabul", "GMT+04:30", 430));
        mPreferenceController.setZonesList(testTimeZones);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(4);
    }

    @Test
    public void testOnPreferenceClick_setTimeZoneCalled() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mPreferenceController.setZonesList(testTimeZone);
        mPreferenceController.onCreate(mLifecycleOwner);

        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();
        ManualTimeZoneSuggestion suggestion =
                TimeZoneDetector.createManualTimeZoneSuggestion("testKey", "Test debug info");
        verify(mTimeZoneDetector).suggestManualTimeZone(suggestion);
    }

    @Test
    public void testOnPreferenceClick_fragmentControllerGoBack() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mPreferenceController.setZonesList(testTimeZone);
        mPreferenceController.onCreate(mLifecycleOwner);

        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();
        verify(mFragmentController).goBack();
    }

    @Test
    public void testOnPreferenceClick_broadcastFired() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mPreferenceController.setZonesList(testTimeZone);
        mPreferenceController.onCreate(mLifecycleOwner);

        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(captor.capture());
        List<Intent> intentsFired = captor.getAllValues();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testTimeZonesComparator() {
        List<Map<String, Object>> testTimeZones = new ArrayList<>();
        testTimeZones.add(createTimeZoneMap("testKey1", "Oral",
                "GMT+05:00", 500));
        testTimeZones.add(createTimeZoneMap("testKey2", "Kathmandu",
                "GMT+05:45", 545));
        testTimeZones.add(createTimeZoneMap("testKey3", "Brazzaville",
                "GMT+01:00", 100));
        testTimeZones.add(createTimeZoneMap("testKey4", "Casablanca",
                "GMT+01:00", 100));
        testTimeZones.add(createTimeZoneMap("testKey5", "Nuuk",
                "GMT-02:00", -200));
        testTimeZones.add(createTimeZoneMap("testKey6", "St. John's",
                "GMT-02:30", -230));
        mPreferenceController.setZonesList(testTimeZones);
        mPreferenceController.onCreate(mLifecycleOwner);

        List<String> computedOrder = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            computedOrder.add(mPreferenceGroup.getPreference(i).getTitle().toString());
        }

        assertThat(computedOrder).containsExactly("St. John's", "Nuuk", "Brazzaville", "Casablanca",
                "Oral", "Kathmandu");
    }

    private Map<String, Object> createTimeZoneMap(String key, String timeZone, String offset,
            int offsetValue) {
        Map<String, Object> map = new HashMap<>();
        map.put(ZoneGetter.KEY_ID, key);
        map.put(ZoneGetter.KEY_DISPLAY_LABEL, timeZone);
        map.put(ZoneGetter.KEY_OFFSET_LABEL, offset);
        map.put(ZoneGetter.KEY_OFFSET, offsetValue);
        return map;
    }
}
