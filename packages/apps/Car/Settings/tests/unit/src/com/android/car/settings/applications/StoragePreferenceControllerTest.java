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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class StoragePreferenceControllerTest {

    private static final String PACKAGE_NAME = "Test Package Name";
    private static final String SIZE_STR = "1.4Mb";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private StoragePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private ApplicationsState.AppEntry mMockAppEntry;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new StoragePreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
    }

    @Test
    public void testCheckInitialized_noApplicationsEntry_throwException() {
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void testCheckInitialized_noApplicationsState_throwException() {
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void testCheckInitialized_noPackageName_throwException() {
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                        mPreference));
    }

    @Test
    public void onCreate_nullSize_calculatingSummary() {
        when(mMockApplicationsState.getEntry(PACKAGE_NAME, UserHandle.myUserId()))
                .thenReturn(mMockAppEntry);
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));
    }

    @Test
    public void onCreate_validApp_sizeSummary() {
        when(mMockApplicationsState.getEntry(PACKAGE_NAME, UserHandle.myUserId()))
                .thenReturn(mMockAppEntry);
        mMockAppEntry.sizeStr = SIZE_STR;
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.storage_type_internal, SIZE_STR));
    }

    @Test
    public void onCreate_lateAppLoad_updateSummary() {
        when(mMockApplicationsState.getEntry(PACKAGE_NAME, UserHandle.myUserId()))
                .thenReturn(mMockAppEntry);
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));

        mMockAppEntry.sizeStr = SIZE_STR;
        mPreferenceController.mApplicationStateCallbacks.onAllSizesComputed();
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.storage_type_internal, SIZE_STR));
    }

    @Test
    public void onCreate_packageSizeChange_updateSummary() {
        when(mMockApplicationsState.getEntry(PACKAGE_NAME, UserHandle.myUserId()))
                .thenReturn(mMockAppEntry);
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));

        mMockAppEntry.sizeStr = SIZE_STR;
        mPreferenceController.mApplicationStateCallbacks.onPackageSizeChanged(PACKAGE_NAME);
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.storage_type_internal, SIZE_STR));
    }

    @Test
    public void onCreate_otherPackageSizeChange_doesNotUpdateSummary() {
        when(mMockApplicationsState.getEntry(PACKAGE_NAME, UserHandle.myUserId()))
                .thenReturn(mMockAppEntry);
        mPreferenceController.setAppEntry(mMockAppEntry);
        mPreferenceController.setAppState(mMockApplicationsState);
        mPreferenceController.setPackageName(PACKAGE_NAME);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));

        mMockAppEntry.sizeStr = SIZE_STR;
        mPreferenceController.mApplicationStateCallbacks.onPackageSizeChanged("other_package");
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.memory_calculating_size));
    }
}
