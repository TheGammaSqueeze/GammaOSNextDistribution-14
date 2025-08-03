/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.settings.sound;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.media.CarAudioManager;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AudioRouteSelectorControllerTest {
    private static final String ACTIVE_ADDRESS = "active_address";
    private static final String ACTIVE_NAME = "active_name";
    private static final String INACTIVE_NAME = "inactive_name";
    private static final String INACTIVE_ADDRESS = "inactive_address";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private AudioRouteSelectorController mPreferenceController;
    private ListPreference mPreference;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AudioRoutesManager mAudioRoutesManager;
    @Mock
    private CarAudioManager mCarAudioManager;
    @Mock
    private AudioRouteItem mAudioRouteItem1;
    @Mock
    private AudioRouteItem mAudioRouteItem2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new AudioRouteSelectorController(mContext,
                /* preferenceKey= */ "key", mFragmentController,
                mCarUxRestrictions);
        mPreference = new ListPreference(mContext);
        mPreferenceController.setAudioRoutesManager(mAudioRoutesManager);
        when(mAudioRoutesManager.getCarAudioManager()).thenReturn(mCarAudioManager);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        Map<String, AudioRouteItem> audioRouteItemMap = new HashMap<>();
        audioRouteItemMap.put(ACTIVE_ADDRESS, mAudioRouteItem1);
        audioRouteItemMap.put(INACTIVE_ADDRESS, mAudioRouteItem2);
        when(mAudioRouteItem1.getAddress()).thenReturn(ACTIVE_ADDRESS);
        when(mAudioRouteItem1.getName()).thenReturn(ACTIVE_NAME);
        when(mAudioRouteItem2.getAddress()).thenReturn(INACTIVE_ADDRESS);
        when(mAudioRouteItem2.getName()).thenReturn(INACTIVE_NAME);
        when(mAudioRoutesManager.getActiveDeviceAddress()).thenReturn(ACTIVE_ADDRESS);
        when(mAudioRoutesManager.getAudioRouteList()).thenReturn(
                Arrays.asList(ACTIVE_ADDRESS, INACTIVE_ADDRESS));
        when(mAudioRoutesManager.getAudioRouteItemMap()).thenReturn(audioRouteItemMap);
    }

    @After
    public void tearDown() {
        if (mAudioRoutesManager != null) {
            mAudioRoutesManager.tearDown();
        }
    }

    @Test
    public void onCreate_testUpdatePreferenceOptions() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(mPreference.getTitle().toString()).isEqualTo(
                mContext.getString(R.string.audio_route_selector_title));
        assertThat(mPreference.getSummary().toString()).isEqualTo(ACTIVE_NAME);
        assertThat(mPreference.getValue().toString()).isEqualTo(ACTIVE_ADDRESS);
        assertThat(mPreference.getEntries().length).isEqualTo(2);
        assertThat(mPreference.getEntries()[0].toString()).isEqualTo(ACTIVE_NAME);
        assertThat(mPreference.getEntries()[1].toString()).isEqualTo(INACTIVE_NAME);
        assertThat(mPreference.getEntryValues()[0].toString()).isEqualTo(ACTIVE_ADDRESS);
        assertThat(mPreference.getEntryValues()[1].toString()).isEqualTo(INACTIVE_ADDRESS);
    }

    @Test
    public void onCreate_testHandlePreferenceChanged() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mPreferenceController.handlePreferenceChanged(mPreference, INACTIVE_ADDRESS);

        verify(mAudioRoutesManager).updateAudioRoute(INACTIVE_ADDRESS);
    }
}
