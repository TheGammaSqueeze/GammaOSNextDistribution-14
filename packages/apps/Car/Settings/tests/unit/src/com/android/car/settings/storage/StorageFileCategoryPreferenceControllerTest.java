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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.ProgressBarPreference;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.deviceinfo.StorageVolumeProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class StorageFileCategoryPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private ProgressBarPreference mProgressBarPreference;
    private StorageFileCategoryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private UserHandle mUserHandle;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private StorageVolumeProvider mMockStorageVolumeProvider;
    @Mock
    private VolumeInfo mMockVolumeInfo;
    @Mock
    private Intent mMockIntent;
    @Mock
    private ComponentName mMockComponentName;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        int myUserId = UserHandle.myUserId();
        mUserHandle = UserHandle.of(myUserId);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mProgressBarPreference = new ProgressBarPreference(mContext);
        mPreferenceController = new StorageFileCategoryPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions,
                mMockStorageVolumeProvider);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mProgressBarPreference);

        when(mMockStorageVolumeProvider.findEmulatedForPrivate(any())).thenReturn(mMockVolumeInfo);
    }

    @Test
    public void onCreate_nonResolvableIntent_notSelectable() {
        when(mMockVolumeInfo.buildBrowseIntent()).thenReturn(mMockIntent);
        when(mMockIntent.resolveActivity(mContext.getPackageManager())).thenReturn(null);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mProgressBarPreference.isSelectable()).isFalse();
    }

    @Test
    public void onCreate_resolvableIntent_selectable() {
        when(mMockVolumeInfo.buildBrowseIntent()).thenReturn(mMockIntent);
        when(mMockIntent.resolveActivity(mContext.getPackageManager()))
                .thenReturn(mMockComponentName);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mProgressBarPreference.isSelectable()).isTrue();
    }

    @Test
    public void handlePreferenceClicked_currentUserAndNoActivityToHandleIntent_doesNotThrow() {
        when(mMockVolumeInfo.buildBrowseIntent()).thenReturn(mMockIntent);
        when(mMockIntent.resolveActivity(mContext.getPackageManager())).thenReturn(null);

        mProgressBarPreference.performClick();

        verify(mContext, never()).startActivityAsUser(mMockIntent, mUserHandle);
    }

    @Test
    public void handlePreferenceClicked_currentUserAndActivityToHandleIntent_startsNewActivity() {
        when(mMockVolumeInfo.buildBrowseIntent()).thenReturn(mMockIntent);
        when(mMockIntent.resolveActivity(mContext.getPackageManager()))
                .thenReturn(mMockComponentName);

        doNothing().when(mContext).startActivityAsUser(mMockIntent, mUserHandle);

        mPreferenceController.onCreate(mLifecycleOwner);
        mProgressBarPreference.performClick();

        verify(mContext).startActivityAsUser(mMockIntent, mUserHandle);
    }
}
