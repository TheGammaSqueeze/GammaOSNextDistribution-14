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

import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.media.CarAudioManager;
import android.graphics.drawable.Drawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class MediaVolumeStatusIconControllerTest extends SysuiTestCase {
    @Mock
    Car mCar;
    @Mock
    CarOccupantZoneManager mCarOccupantZoneManager;
    @Mock
    CarOccupantZoneManager.OccupantZoneInfo mInfo;
    @Mock
    CarAudioManager mCarAudioManager;
    @Mock
    CarServiceProvider mCarServiceProvider;
    @Mock
    UserTracker mUserTracker;

    private MediaVolumeStatusIconController mMediaVolumeStatusIconController;
    private MockitoSession mMockingSession;
    private CarAudioManager.CarVolumeCallback mVolumeChangeCallback;

    private final int mInitZoneId = 100;
    private final int mInitGroupId = 10;
    private final int mInitVolumeLevel = 50;
    private Drawable mInitialStatusIconDrawable;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(Car.class)
                .strictness(Strictness.WARN)
                .startMocking();

        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> listenerCaptor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);

        mMediaVolumeStatusIconController =
                new MediaVolumeStatusIconController(mContext, mUserTracker, mContext.getResources(),
                mCarServiceProvider);
        verify(mCarServiceProvider).addListener(listenerCaptor.capture());

        doReturn(mCarOccupantZoneManager).when(mCar).getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);
        mInfo.zoneId = mInitZoneId;
        doReturn(mInfo).when(mCarOccupantZoneManager).getMyOccupantZone();
        doReturn(mCarAudioManager).when(mCar).getCarManager(Car.AUDIO_SERVICE);
        doReturn(mInitGroupId).when(mCarAudioManager)
                .getVolumeGroupIdForUsage(mInitZoneId, USAGE_MEDIA);
        doReturn(mInitVolumeLevel).when(mCarAudioManager).getGroupVolume(mInitZoneId, mInitGroupId);

        listenerCaptor.getValue().onConnected(mCar);

        ArgumentCaptor<CarAudioManager.CarVolumeCallback> callbackCaptor =
                ArgumentCaptor.forClass(CarAudioManager.CarVolumeCallback.class);
        verify(mCarAudioManager).registerCarVolumeCallback(callbackCaptor.capture());
        mVolumeChangeCallback = callbackCaptor.getValue();
        mInitialStatusIconDrawable =
                mMediaVolumeStatusIconController.getMediaVolumeStatusIconDrawable();
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void onDestroy_unregistersListeners() {
        mMediaVolumeStatusIconController.onDestroy();
        verify(mCarServiceProvider).removeListener(any());
        verify(mCarAudioManager).unregisterCarVolumeCallback(any());
        verify(mUserTracker).removeCallback(any());
    }

    @Test
    public void onGroupVolumeChanged_whenZoneIdAndGroupIdAreSame_updateStatus() {
        int zoneId = 100;
        int groupId = 10;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isNotEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupVolumeChanged_whenZoneIdIsNotSame_doNotUpdateStatus() {
        int zoneId = 99;
        int groupId = 10;
        int volume = 0;
        doReturn(groupId).when(mCarAudioManager).getVolumeGroupIdForUsage(zoneId, USAGE_MEDIA);
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupVolumeChanged_whenGroupIdIsNotSame_doNotUpdateStatus() {
        int zoneId = 100;
        int groupId = 9;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupVolumeChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenZoneIdAndGroupIdAreSame_updateStatus() {
        int zoneId = 100;
        int groupId = 10;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isNotEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenZoneIdIsNotSame_doNotUpdateStatus() {
        int zoneId = 99;
        int groupId = 10;
        int volume = 0;
        doReturn(groupId).when(mCarAudioManager).getVolumeGroupIdForUsage(zoneId, USAGE_MEDIA);
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }

    @Test
    public void onGroupMuteChanged_whenGroupIdIsNotSame_doNotUpdateStatus() {
        int zoneId = 100;
        int groupId = 9;
        int volume = 0;
        doReturn(volume).when(mCarAudioManager).getGroupVolume(zoneId, groupId);

        mVolumeChangeCallback.onGroupMuteChanged(zoneId, groupId, /* flag= */ 0);

        assertThat(mInitialStatusIconDrawable).isEqualTo(
                mMediaVolumeStatusIconController.getIconDrawableToDisplay());
    }
}
