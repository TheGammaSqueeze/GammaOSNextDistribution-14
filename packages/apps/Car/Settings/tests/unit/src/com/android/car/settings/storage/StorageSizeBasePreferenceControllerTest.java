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

import static org.mockito.Mockito.verify;

import android.app.usage.StorageStats;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class StorageSizeBasePreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private StorageAppDetailPreference mStorageAppDetailPreference;
    private TestStorageSizeBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AppsStorageStatsManager mAppsStorageStatsManager;

    private static class TestStorageSizeBasePreferenceController extends
            StorageSizeBasePreferenceController {

        TestStorageSizeBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected long getSize() {
            return 1_000_000_000;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mStorageAppDetailPreference = new StorageAppDetailPreference(mContext);
        mPreferenceController = new TestStorageSizeBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mStorageAppDetailPreference);
    }

    @Test
    public void onCreate_defaultState_nothingIsSet() {
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mStorageAppDetailPreference.getDetailText()).isNull();
        assertThat(mPreferenceController.isCachedCleared()).isFalse();
        assertThat(mPreferenceController.isDataCleared()).isFalse();
        assertThat(mPreferenceController.getAppStorageStats()).isNull();
    }

    @Test
    public void setAppsStorageStatsManager_shouldRegisterController() {
        mPreferenceController.setAppsStorageStatsManager(mAppsStorageStatsManager);
        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mAppsStorageStatsManager).registerListener(mPreferenceController);
    }

    @Test
    public void onDataLoaded_shouldUpdateCachedAndDataClearedState() {
        mPreferenceController.onDataLoaded(null, true, true);

        assertThat(mPreferenceController.isCachedCleared()).isTrue();
        assertThat(mPreferenceController.isDataCleared()).isTrue();
    }

    @Test
    public void onDataLoaded_appStorageStatsNotSet_shouldNotUpdateDetailText() {
        mPreferenceController.onDataLoaded(null, true, true);

        assertThat(mPreferenceController.getAppStorageStats()).isNull();
        assertThat(mStorageAppDetailPreference.getDetailText()).isNull();
    }

    @Test
    public void onDataLoaded_appStorageStatsSet_shouldUpdateDetailText() {
        mPreferenceController.onCreate(mLifecycleOwner);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);
        mPreferenceController.setAppStorageStats(storageStats);
        mPreferenceController.onDataLoaded(null, true, true);

        assertThat(mPreferenceController.getAppStorageStats()).isNotNull();
        assertThat(mStorageAppDetailPreference.getDetailText()).isEqualTo("1.00 GB");
    }
}
