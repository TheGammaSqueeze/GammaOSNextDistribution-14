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
package com.android.car.audio;

import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

/**
 * A class encapsulates a volume group in car.
 *
 * Volume in a car is controlled by group. A group holds one or more car audio contexts.
 * Call {@link CarAudioManager#getVolumeGroupCount()} to get the count of {@link CarVolumeGroup}
 * supported in a car.
 */
final class CarAudioVolumeGroup extends CarVolumeGroup {
    private static final int UNSET_STEP_SIZE = -1;
    private static final int EVENT_TYPE_NONE = 0;
    @GuardedBy("mLock")
    private int mDefaultGain = Integer.MAX_VALUE;
    @GuardedBy("mLock")
    private int mMaxGain = Integer.MIN_VALUE;
    @GuardedBy("mLock")
    private int mMinGain = Integer.MAX_VALUE;
    @GuardedBy("mLock")
    private int mStepSize = UNSET_STEP_SIZE;

    CarAudioVolumeGroup(CarAudioContext carAudioContext,
            CarAudioSettings settingsManager,
            SparseArray<CarAudioDeviceInfo> contextToDeviceInfo, int zoneId, int configId,
            int volumeGroupId, String name, int stepSize, int defaultGain, int minGain, int maxGain,
            boolean useCarVolumeGroupMute, int maxActivationVolumePercentage,
            int minActivationVolumePercentage) {
        super(carAudioContext, settingsManager, contextToDeviceInfo, zoneId, configId,
                volumeGroupId, name, useCarVolumeGroupMute, maxActivationVolumePercentage,
                minActivationVolumePercentage);
        Preconditions.checkArgument(stepSize != 0, "Step Size must not be zero");
        mStepSize = stepSize;
        mDefaultGain = defaultGain;
        mMinGain = minGain;
        mMaxGain = maxGain;
        mLimitedGainIndex = getIndexForGainLocked(mMaxGain);
    }

    @Override
    public int getMaxGainIndex() {
        synchronized (mLock) {
            return getIndexForGainLocked(mMaxGain);
        }
    }

    @Override
    public int getMinGainIndex() {
        synchronized (mLock) {
            return getIndexForGainLocked(mMinGain);
        }
    }

    @Override
    @GuardedBy("mLock")
    @SuppressWarnings("GuardedBy")
    protected void setCurrentGainIndexLocked(int gainIndex) {
        int gainInMillibels = getGainForIndexLocked(gainIndex);
        for (int index = 0; index < mAddressToCarAudioDeviceInfo.size(); index++) {
            CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.valueAt(index);
            info.setCurrentGain(gainInMillibels);
        }
        super.setCurrentGainIndexLocked(gainIndex);
    }

    @Override
    @GuardedBy("mLock")
    protected boolean isValidGainIndexLocked(int gainIndex) {
        return gainIndex >= getIndexForGainLocked(mMinGain)
                && gainIndex <= getIndexForGainLocked(mMaxGain);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    @GuardedBy("mLock")
    protected void dumpLocked(IndentingPrintWriter writer) {
        writer.printf("Step size: %d\n", mStepSize);
        writer.printf("Gain values (min / max / default/ current): %d %d %d %d\n", mMinGain,
                mMaxGain, mDefaultGain, getGainForIndexLocked(mCurrentGainIndex));
    }

    @Override
    protected int getDefaultGainIndex() {
        synchronized (mLock) {
            return getIndexForGainLocked(mDefaultGain);
        }
    }

    @GuardedBy("mLock")
    private int getGainForIndexLocked(int gainIndex) {
        return mMinGain + gainIndex * mStepSize;
    }

    @GuardedBy("mLock")
    private int getIndexForGainLocked(int gainInMillibel) {
        return (gainInMillibel - mMinGain) / mStepSize;
    }

    @Override
    int calculateNewGainStageFromDeviceInfos() {
        int minGain = Integer.MAX_VALUE;
        int maxGain = Integer.MIN_VALUE;
        int defaultGain = Integer.MAX_VALUE;
        int stepSize = UNSET_STEP_SIZE;

        int eventType = EVENT_TYPE_NONE;
        synchronized (mLock) {
            // compute the new volume group gain stage from scratch
            for (int index = 0; index < mAddressToCarAudioDeviceInfo.size(); index++) {
                CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.valueAt(index);
                if (stepSize == UNSET_STEP_SIZE) {
                    stepSize = info.getStepValue();
                } else {
                    Preconditions.checkArgument(
                            info.getStepValue() == stepSize,
                            "Gain stages within one group must have same step value");
                }
                if (info.getDefaultGain() < defaultGain) {
                    // We're arbitrarily selecting the lowest
                    // device default gain as the group's default.
                    defaultGain = info.getDefaultGain();
                }
                if (info.getMaxGain() > maxGain) {
                    maxGain = info.getMaxGain();
                }
                if (info.getMinGain() < minGain) {
                    minGain = info.getMinGain();
                }
            }

            // update the new gain stage and return event types so that callback can be triggered.
            // get the current and restricted gains in mb before updating the volume bounds. These
            // will be used for extrapolating to new volume ranges
            int epCurrentGainInMb = getGainForIndexLocked(getCurrentGainIndexLocked());
            int epLimitedGainInMb = getGainForIndexLocked(mLimitedGainIndex);
            int epBlockedGainInMb = getGainForIndexLocked(mBlockedGainIndex);
            int epAttenuatedGainInMb = getGainForIndexLocked(mAttenuatedGainIndex);
            boolean isLimited = isLimitedLocked();

            // update the volume ranges
            if (minGain != mMinGain) {
                mMinGain = minGain;
                eventType |= (EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED
                        | EVENT_TYPE_VOLUME_MIN_INDEX_CHANGED);
            }
            if (maxGain != mMaxGain) {
                mMaxGain = maxGain;
                eventType |= (EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED
                        | EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED);
            }
            // if only default gain changes, no impact to volume gain stages (i.e. no event).
            if (defaultGain != mDefaultGain) {
                mDefaultGain = defaultGain;
            }
            if (stepSize != mStepSize) {
                mStepSize = stepSize;
                eventType |= (EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED
                        | EVENT_TYPE_VOLUME_MAX_INDEX_CHANGED);
            }
            // if no change to indexes, return
            if (eventType == EVENT_TYPE_NONE) {
                return eventType;
            }

            // if min/max/step values change, we shall try to maintain the same volume level
            // through simple extrapolation, i.e., the new  gain index is calculated from the
            // previous {@code mCurrentGainIndex} in millibels.
            // caution: before updating, check if the previous gain is out of bound in the
            //          new gain stage. If yes, use the safe value (i.e default gain)
            //          provided by the hal implementations.
            mCurrentGainIndex = getIndexForGainLocked(epCurrentGainInMb);
            if (!isValidGainIndexLocked(mCurrentGainIndex)) {
                mCurrentGainIndex = getIndexForGainLocked(mDefaultGain);
            }

            // similar extrapolation is tried for restricted gains: limited, blocked, attenuated.
            // Note: Even after best effort, it is possible that some or all of the old restriction
            // indexes are invalid and therefore reset.
            int newLimitedGainIndex = getIndexForGainLocked(epLimitedGainInMb);
            if (isLimited && isValidGainIndexLocked(newLimitedGainIndex)) {
                setLimitLocked(newLimitedGainIndex);
            } else {
                resetLimitLocked();
            }

            int newBlockedGainIndex = getIndexForGainLocked(epBlockedGainInMb);
            if (isBlockedLocked() && isValidGainIndexLocked(newBlockedGainIndex)) {
                setBlockedLocked(newBlockedGainIndex);
            } else {
                resetBlockedLocked();
            }

            int newAttenuatedGainIndex = getIndexForGainLocked(epAttenuatedGainInMb);
            if (isAttenuatedLocked() && isValidGainIndexLocked(newAttenuatedGainIndex)) {
                setAttenuatedGainLocked(newAttenuatedGainIndex);
            } else {
                resetAttenuationLocked();
            }

            // Notes:
            // (1) Setting current gain index will trigger Audio HAL. If restrictions are still
            //     valid but were reset above, we expect AudioControl HAL to resend the restrictions
            //     in a callback.
            // (2) Audio HAL will be responsible to ensure consistent speaker output during this
            //     transition.
            setCurrentGainIndexLocked(getRestrictedGainForIndexLocked(mCurrentGainIndex));
        }
        return eventType;
    }
}
