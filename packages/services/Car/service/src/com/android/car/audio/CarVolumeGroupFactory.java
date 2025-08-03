/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.car.audio;

import android.media.AudioManager;
import android.util.SparseArray;

import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Objects;

final class CarVolumeGroupFactory {
    private static final int UNSET_STEP_SIZE = -1;

    private final String mName;
    private final int mId;
    private final int mZoneId;
    private final int mConfigId;
    private final boolean mUseCarVolumeGroupMute;
    private final CarAudioSettings mCarAudioSettings;
    private final SparseArray<CarAudioDeviceInfo> mContextToDeviceInfo = new SparseArray<>();
    private final CarAudioContext mCarAudioContext;
    private final AudioManager mAudioManager;
    private final int mMinActivationVolumePercentage;
    private final int mMaxActivationVolumePercentage;

    private int mStepSize = UNSET_STEP_SIZE;
    private int mDefaultGain = Integer.MIN_VALUE;
    private int mMaxGain = Integer.MIN_VALUE;
    private int mMinGain = Integer.MAX_VALUE;

    CarVolumeGroupFactory(AudioManager audioManager, CarAudioSettings carAudioSettings,
            CarAudioContext carAudioContext, int zoneId, int configId, int volumeGroupId,
            String name, boolean useCarVolumeGroupMute, int maxActivationVolumePercentage,
            int minActivationVolumePercentage) {
        mAudioManager = audioManager;
        mCarAudioSettings = Objects.requireNonNull(carAudioSettings,
                "Car audio settings can not be null");
        mCarAudioContext = Objects.requireNonNull(carAudioContext,
                "Car audio context can not be null");
        mZoneId = zoneId;
        mConfigId = configId;
        mId = volumeGroupId;
        mName = Objects.requireNonNull(name, "Car Volume Group name can not be null");
        mUseCarVolumeGroupMute = useCarVolumeGroupMute;
        mMaxActivationVolumePercentage = maxActivationVolumePercentage;
        mMinActivationVolumePercentage = minActivationVolumePercentage;
    }

    CarVolumeGroup getCarVolumeGroup(boolean useCoreAudioVolume) {
        Preconditions.checkArgument(mContextToDeviceInfo.size() != 0,
                "setDeviceInfoForContext has to be called at least once before building");
        CarVolumeGroup group;
        if (useCoreAudioVolume) {
            group = new CoreAudioVolumeGroup(mAudioManager, mCarAudioContext, mCarAudioSettings,
                    mContextToDeviceInfo, mZoneId, mConfigId, mId, mName, mUseCarVolumeGroupMute,
                    mMaxActivationVolumePercentage, mMinActivationVolumePercentage);
        } else {
            group = new CarAudioVolumeGroup(mCarAudioContext, mCarAudioSettings,
                    mContextToDeviceInfo, mZoneId, mConfigId, mId, mName, mStepSize, mDefaultGain,
                    mMinGain, mMaxGain, mUseCarVolumeGroupMute, mMaxActivationVolumePercentage,
                    mMinActivationVolumePercentage);
        }
        group.init();
        return group;
    }

    void setDeviceInfoForContext(int carAudioContextId, CarAudioDeviceInfo info) {
        Preconditions.checkArgument(mContextToDeviceInfo.get(carAudioContextId) == null,
                "Context %s has already been set to %s",
                mCarAudioContext.toString(carAudioContextId),
                mContextToDeviceInfo.get(carAudioContextId));

        if (mContextToDeviceInfo.size() == 0) {
            mStepSize = info.getStepValue();
        } else {
            Preconditions.checkArgument(
                    info.getStepValue() == mStepSize,
                    "Gain controls within one group must have same step value");
        }

        mContextToDeviceInfo.put(carAudioContextId, info);

        // TODO(b/271749259) - this logic is redundant now. clean up
        if (info.getDefaultGain() > mDefaultGain) {
            // We're arbitrarily selecting the highest
            // device default gain as the group's default.
            mDefaultGain = info.getDefaultGain();
        }
        if (info.getMaxGain() > mMaxGain) {
            mMaxGain = info.getMaxGain();
        }
        if (info.getMinGain() < mMinGain) {
            mMinGain = info.getMinGain();
        }
    }

    void setNonLegacyContexts(CarAudioDeviceInfo info) {
        List<Integer> nonLegacyCarSystemContexts = CarAudioContext.getCarSystemContextIds();
        for (int index = 0; index < nonLegacyCarSystemContexts.size(); index++) {
            @AudioContext int audioContext = nonLegacyCarSystemContexts.get(index);
            setDeviceInfoForContext(audioContext, info);
        }
    }

    @VisibleForTesting
    int getMinGain() {
        return mMinGain;
    }

    @VisibleForTesting
    int getMaxGain() {
        return mMaxGain;
    }

    @VisibleForTesting
    int getDefaultGain() {
        return mDefaultGain;
    }
}
