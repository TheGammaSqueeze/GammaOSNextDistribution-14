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

package com.android.car.settings.storage;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.lifecycle.LifecycleOwner;
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
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class StorageMediaCategoryDetailPreferenceControllerTest {

    private static final String SOURCE = "source";
    private static final int UID = 12;
    private static final long EXTRA_AUDIO_BYTES = 100;
    private static final String LABEL = "label";
    private static final String SIZE_STR = "12.34 MB";
    private static final String PACKAGE_NAME = "com.google.packageName";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mLogicalPreferenceGroup;
    private StorageMediaCategoryDetailPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mLogicalPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mLogicalPreferenceGroup);
        mPreferenceController = new StorageMediaCategoryDetailPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mLogicalPreferenceGroup);

        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_defaultInitialize_hasNoPreference() {
        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void onDataLoaded_addPreference_hasTwoPreferences() {
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = UID;
        appInfo.sourceDir = SOURCE;

        ApplicationsState.AppEntry appEntry = new ApplicationsState.AppEntry(mContext, appInfo,
                1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        appEntry.info.packageName = PACKAGE_NAME;
        apps.add(appEntry);

        mPreferenceController.setExternalAudioBytes(EXTRA_AUDIO_BYTES);
        mPreferenceController.onDataLoaded(apps);
        // even when the manager notifies the controller again on data loaded the preference
        // count should remain the same if new appEntries are not added.
        mPreferenceController.onDataLoaded(apps);

        assertThat(mLogicalPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        assertThat(mLogicalPreferenceGroup.getPreference(0).getTitle()).isEqualTo(LABEL);
        assertThat(mLogicalPreferenceGroup.getPreference(0).getSummary()).isEqualTo(SIZE_STR);

        assertThat(mLogicalPreferenceGroup.getPreference(1).getTitle()).isEqualTo(
                mContext.getString(R.string.storage_audio_files_title));
        assertThat(mLogicalPreferenceGroup.getPreference(1).getSummary()).isEqualTo(
                Long.toString(EXTRA_AUDIO_BYTES));
    }
}
