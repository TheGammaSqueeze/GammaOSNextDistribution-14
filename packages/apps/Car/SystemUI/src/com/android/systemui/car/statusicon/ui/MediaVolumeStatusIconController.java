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

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * A controller for media volume status icon in the QC entry point area.
 */
public class MediaVolumeStatusIconController extends StatusIconController {

    private final Context mContext;
    private final Resources mResources;
    private final UserTracker mUserTracker;
    private final CarServiceProvider mCarServiceProvider;

    private CarAudioManager mCarAudioManager;
    private Drawable mMediaVolumeStatusIconDrawable;
    private int mMaxMediaVolume;
    private int mMinMediaVolume;
    private int mGroupId;
    private int mZoneId;

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            updateStatus(mZoneId, mGroupId);
        }
    };

    private final CarServiceProvider.CarServiceOnConnectedListener mCarOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    CarOccupantZoneManager.OccupantZoneInfo occupantZoneInfo = null;
                    CarOccupantZoneManager carOccupantZoneManager =
                            (CarOccupantZoneManager) car.getCarManager(
                                    Car.CAR_OCCUPANT_ZONE_SERVICE);
                    if (carOccupantZoneManager != null) {
                        occupantZoneInfo = carOccupantZoneManager.getMyOccupantZone();
                    }
                    mZoneId =
                            occupantZoneInfo != null ? occupantZoneInfo.zoneId : PRIMARY_AUDIO_ZONE;

                    mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);

                    mCarAudioManager.registerCarVolumeCallback(mVolumeChangeCallback);
                    mGroupId = mCarAudioManager.getVolumeGroupIdForUsage(mZoneId, USAGE_MEDIA);
                    mMaxMediaVolume = mCarAudioManager.getGroupMaxVolume(mZoneId, mGroupId);
                    mMinMediaVolume = mCarAudioManager.getGroupMinVolume(mZoneId, mGroupId);
                    updateStatus(mZoneId, mGroupId);
                }
            };

    @Inject
    MediaVolumeStatusIconController(Context context,
            UserTracker userTracker,
            @Main Resources resources,
            CarServiceProvider carServiceProvider) {
        mContext = context;
        mResources = resources;
        mUserTracker = userTracker;
        mCarServiceProvider = carServiceProvider;
        mUserTracker.addCallback(mUserTrackerCallback, mContext.getMainExecutor());
        mCarServiceProvider.addListener(mCarOnConnectedListener);
    }

    @Override
    protected void onDestroy() {
        mCarServiceProvider.removeListener(mCarOnConnectedListener);
        mUserTracker.removeCallback(mUserTrackerCallback);
        mCarAudioManager.unregisterCarVolumeCallback(mVolumeChangeCallback);
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mMediaVolumeStatusIconDrawable);
        onStatusUpdated();
    }

    private void updateStatus(int zoneId, int groupId) {
        if (mZoneId != zoneId || mGroupId != groupId) {
            return;
        }
        if (mCarAudioManager != null) {
            int currentMediaVolumeLevel = mCarAudioManager.getGroupVolume(mZoneId, mGroupId);
            if (currentMediaVolumeLevel == mMinMediaVolume) {
                mMediaVolumeStatusIconDrawable = mResources.getDrawable(
                        R.drawable.car_ic_media_volume_off, mContext.getTheme());
            } else if (currentMediaVolumeLevel <= (mMaxMediaVolume / 2)) {
                mMediaVolumeStatusIconDrawable = mResources.getDrawable(
                        R.drawable.car_ic_media_volume_down, mContext.getTheme());
            } else {
                mMediaVolumeStatusIconDrawable = mResources.getDrawable(
                        R.drawable.car_ic_media_volume_up, mContext.getTheme());
            }
            updateStatus();
        }
    }

    @Override
    protected int getId() {
        return R.id.qc_media_volume_status_icon;
    }

    private final CarAudioManager.CarVolumeCallback mVolumeChangeCallback =
            new CarAudioManager.CarVolumeCallback() {
                @Override
                public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
                    updateStatus(zoneId, groupId);
                }

                @Override
                public void onMasterMuteChanged(int zoneId, int flags) {
                    // Mute is not being used yet
                }

                @Override
                public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
                    updateStatus(zoneId, groupId);
                }
            };

    /**
     * Get the current media volume status icon in the QC entry point area.
     */
    @VisibleForTesting
    @Nullable
    Drawable getMediaVolumeStatusIconDrawable() {
        return mMediaVolumeStatusIconDrawable;
    }
}
