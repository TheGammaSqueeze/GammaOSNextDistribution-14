/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.lifecycle.InstantTaskExecutorRule;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class QuickControlsStatusIconListControllerTest extends SysuiTestCase {
    private QuickControlsStatusIconListController mQuickControlsStatusIconListController;
    private final String[] mSubControllers = new String[] {
            "com.android.systemui.car.statusicon.ui.MediaVolumeStatusIconController"};

    @Mock
    Provider<StatusIconController> mProvider;
    @Mock
    private Map mIconControllerCreators;
    @Mock
    private StatusIconController mStatusIconController;
    @Mock
    CarServiceProvider mCarServiceProvider;
    @Mock
    UserTracker mUserTracker;

    private MediaVolumeStatusIconController mMediaVolumeStatusIconController;
    private Resources mResources;

    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    private ArgumentCaptor<StatusIconController.OnStatusUpdatedListener> mCaptor =
            ArgumentCaptor.forClass(StatusIconController.OnStatusUpdatedListener.class);
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mResources = mContext.getOrCreateTestableResources().getResources();
        spyOn(mResources);
        mMediaVolumeStatusIconController = new MediaVolumeStatusIconController(mContext,
                mUserTracker, mResources, mCarServiceProvider);
        spyOn(mMediaVolumeStatusIconController);
        doReturn(mMediaVolumeStatusIconController).when(mProvider).get();
        doReturn(mProvider).when(mIconControllerCreators).get(any());
        doReturn(mSubControllers).when(mResources).getStringArray(anyInt());

        mQuickControlsStatusIconListController = new QuickControlsStatusIconListController(
                mContext, mResources, mIconControllerCreators);
        spyOn(mQuickControlsStatusIconListController);
    }

    @Test
    public void updateStatus_iconDrawableisNotNull() {
        mQuickControlsStatusIconListController.updateStatus();

        assertThat(mQuickControlsStatusIconListController.getIconDrawableToDisplay()).isNotNull();
    }

    @Test
    public void onStatusUpdated_listContorollerUpdatesStatus() {
        verify(mMediaVolumeStatusIconController).setOnStatusUpdatedListener(
                mCaptor.capture());

        mCaptor.getValue().onStatusUpdated(mMediaVolumeStatusIconController);

        verify(mQuickControlsStatusIconListController).updateStatus();
    }
}
