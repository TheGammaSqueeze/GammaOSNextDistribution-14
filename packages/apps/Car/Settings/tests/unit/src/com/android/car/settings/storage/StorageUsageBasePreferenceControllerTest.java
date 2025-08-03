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

import static com.android.car.settings.storage.FileSizeFormatter.MEGABYTE_IN_BYTES;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.util.SparseArray;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.ProgressBarPreference;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class StorageUsageBasePreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ProgressBarPreference mProgressBarPreference;
    private TestStorageUsageBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;

    private static class TestStorageUsageBasePreferenceController extends
            StorageUsageBasePreferenceController {

        TestStorageUsageBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        public long calculateCategoryUsage(
                SparseArray<StorageAsyncLoader.AppsStorageResult> result, long usedSizeBytes) {
            return 1_000_000_000;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mProgressBarPreference = new ProgressBarPreference(mContext);
        mPreferenceController = new TestStorageUsageBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mProgressBarPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_defaultInitialize_hasDefaultSummary() {
        assertThat(mProgressBarPreference.getSummary().toString())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));
    }

    @Test
    public void onDataLoaded_setsSummary() {
        SparseArray<StorageAsyncLoader.AppsStorageResult> results = new SparseArray<>();
        StorageAsyncLoader.AppsStorageResult result =
                new StorageAsyncLoader.AppsStorageResult(0, 0, 0, 0, 0);
        result.setExternalStats(
                new StorageStatsSource.ExternalStorageStats(
                        MEGABYTE_IN_BYTES * 500, // total
                        MEGABYTE_IN_BYTES * 100, // audio
                        MEGABYTE_IN_BYTES * 150, // video
                        MEGABYTE_IN_BYTES * 200, 0)); // image
        results.put(0, result);
        mPreferenceController.onDataLoaded(results, 100, 100);

        assertThat(mProgressBarPreference.getSummary().toString()).isEqualTo("1.0 GB");
    }

    @Test
    public void onDataLoaded_setProgressBarPercentage() {
        SparseArray<StorageAsyncLoader.AppsStorageResult> results = new SparseArray<>();
        StorageAsyncLoader.AppsStorageResult result =
                new StorageAsyncLoader.AppsStorageResult(0, 0, 0, 0, 0);
        result.setExternalStats(
                new StorageStatsSource.ExternalStorageStats(
                        MEGABYTE_IN_BYTES * 500, // total
                        MEGABYTE_IN_BYTES * 100, // audio
                        MEGABYTE_IN_BYTES * 150, // video
                        MEGABYTE_IN_BYTES * 200, 0)); // image
        results.put(0, result);
        mPreferenceController.onDataLoaded(results, 100, 2_000_000_000);
        // usage size is half the total size i.e percentage of storage used should be 50.
        assertThat(mProgressBarPreference.getProgress()).isEqualTo(50);
    }
}
