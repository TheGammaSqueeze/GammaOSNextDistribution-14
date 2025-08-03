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

import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;

import static com.android.car.CarLog.TAG_AUDIO;
import static com.android.car.audio.CoreAudioHelper.getProductStrategyForAudioAttributes;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.util.Slogf;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.Arrays;
import java.util.List;

/**
 * A class encapsulates a volume group in car.
 *
 * Volume in a car is controlled by group. A group holds one or more car audio contexts.
 * Call {@link CarAudioManager#getVolumeGroupCount()} to get the count of {@link CarVolumeGroup}
 * supported in a car.
 */
final class CoreAudioVolumeGroup extends CarVolumeGroup {
    static final String TAG = TAG_AUDIO + ".CoreAudioVolumeGroup";
    /**
     * For all volume operations, attributes are required
     */
    private final AudioAttributes mAudioAttributes;
    private final AudioManager mAudioManager;
    @GuardedBy("mLock")
    private int mAmCurrentGainIndex;
    private final int mAmId;
    private final int mDefaultGainIndex;
    private final int mMaxGainIndex;
    private final int mMinGainIndex;
    private boolean mAmGroupMuted;
    private int mAmLastAudibleGainIndex;

    CoreAudioVolumeGroup(AudioManager audioManager, CarAudioContext carAudioContext,
            CarAudioSettings settingsManager, SparseArray<CarAudioDeviceInfo> contextToDevices,
            int zoneId, int configId, int volumeGroupId, String name,
            boolean useCarVolumeGroupMute, int maxActivationVolumePercentage,
            int minActivationVolumePercentage) {
        super(carAudioContext, settingsManager, contextToDevices, zoneId, configId, volumeGroupId,
                name, useCarVolumeGroupMute, maxActivationVolumePercentage,
                minActivationVolumePercentage);
        mAudioManager = audioManager;
        mAudioAttributes = CoreAudioHelper.selectAttributesForVolumeGroupName(name);
        mAmId = CoreAudioHelper.getVolumeGroupIdForAudioAttributes(mAudioAttributes);
        mAmCurrentGainIndex = getAmCurrentGainIndex();
        mMinGainIndex = mAudioManager.getMinVolumeIndexForAttributes(mAudioAttributes);
        mMaxGainIndex = mAudioManager.getMaxVolumeIndexForAttributes(mAudioAttributes);
        mAmGroupMuted = isAmGroupMuted();
        mAmLastAudibleGainIndex = getAmLastAudibleIndex();
        // Unfortunately core groups do not have defaults
        mDefaultGainIndex = (mMaxGainIndex - mMinGainIndex) / 3 + mMinGainIndex;
        mLimitedGainIndex = mMaxGainIndex;
    }

    @Override
    public int getMaxGainIndex() {
        return mMaxGainIndex;
    }

    @Override
    public int getMinGainIndex() {
        synchronized (mLock) {
            return mMinGainIndex;
        }
    }

    int getAmCurrentGainIndex() {
        synchronized (mLock) {
            return mAudioManager.getVolumeIndexForAttributes(mAudioAttributes);
        }
    }

    boolean isAmGroupMuted() {
        return AudioManagerHelper.isVolumeGroupMuted(mAudioManager, mAmId);
    }

    int getAmLastAudibleIndex() {
        return AudioManagerHelper.getLastAudibleVolumeGroupVolume(mAudioManager, mAmId);
    }

    @Override
    @GuardedBy("mLock")
    @SuppressWarnings("GuardedBy")
    protected void setCurrentGainIndexLocked(int gainIndex) {
        // TODO(b/260298113): wait for orthogonal mute/volume in AM to bypass check of muted state
        if (!isBlockedLocked() && getAmLastAudibleIndex() != gainIndex) {
            setCurrentGainIndexLocked(gainIndex, /* canChangeMuteState= */ false);
        }
        super.setCurrentGainIndexLocked(gainIndex);
    }

    @GuardedBy("mLock")
    @SuppressWarnings("GuardedBy")
    private void setCurrentGainIndexLocked(int gainIndex, boolean canChangeMuteState) {
        int flags = 0;
        if (canChangeMuteState || !isUserMutedLocked()) {
            mAudioManager.setVolumeGroupVolumeIndex(mAmId, gainIndex, flags);
        }
    }

    @Override
    @GuardedBy("mLock")
    @SuppressWarnings("GuardedBy")
    protected boolean isValidGainIndexLocked(int gainIndex) {
        return gainIndex >= mMinGainIndex && gainIndex <= mMaxGainIndex;
    }

    /**
     * As per AudioService logic, a mutable group is a group which min index is zero
     * @return true if group is mutable, false otherwise
     */
    private boolean isMutable() {
        return mMinGainIndex == 0;
    }

    @Override
    @GuardedBy("mLock")
    @SuppressWarnings("GuardedBy")
    protected void applyMuteLocked(boolean mute) {
        if (!isMutable()) {
            return;
        }
        if (isAmGroupMuted() != mute) {
            if (mute) {
                AudioManagerHelper.adjustVolumeGroupVolume(mAudioManager, mAmId,
                        AudioManager.ADJUST_MUTE, /* flags= */ 0);
            } else if (!isBlockedLocked() && !isHalMutedLocked()) {
                // Unmute shall not break any pending attenuation / limitation
                int index = getRestrictedGainForIndexLocked(getCurrentGainIndexLocked());
                // Sync index if needed before unmuting
                if (getAmLastAudibleIndex() != index) {
                    setCurrentGainIndexLocked(index, /* canChangeMuteState= */ true);
                }
                // TODO(b/260298113): index 0 mutes Am Group, wait for orthogonal mute/volume in AM
                AudioManagerHelper.adjustVolumeGroupVolume(mAudioManager, mAmId,
                        AudioManager.ADJUST_UNMUTE, /* flags= */ 0);
            }
        }
    }

    /**
     * Allowing using {@link AudioManager#setVolumeIndexForAudioAttributes} implies that some volume
     * change may not be originated by CarAudioManager itself.
     * Hence, it requires synchronization of the indexes.
     */
    @Override
    @SuppressWarnings("GuardedBy")
    public int onAudioVolumeGroupChanged(int flags) {
        int returnedFlags = 0;
        synchronized (mLock) {
            int previousAudibleIndex = getRestrictedGainForIndexLocked(mCurrentGainIndex);
            int previousIndex = isMutedLocked() ? getMinGainIndex() : previousAudibleIndex;
            mAmGroupMuted = isAmGroupMuted();
            mAmLastAudibleGainIndex = getAmLastAudibleIndex();
            mAmCurrentGainIndex = getAmCurrentGainIndex();
            boolean volumeChanged = previousIndex != mAmCurrentGainIndex;
            boolean muteChanged = mAmGroupMuted != isUserMutedLocked();
            boolean lastAudibleIndexChanged = previousAudibleIndex != mAmLastAudibleGainIndex;
            if (isBlockedLocked())  {
                if (!mAmGroupMuted) {
                    Slogf.i(TAG, "onAudioVolumeGroupChanged group(%s) blocked by HAL,"
                            + " unmuted on AudioManager, force mute sync", getName());
                    applyMuteLocked(/* mute= */ true);
                }
                Slogf.i(TAG, "onAudioVolumeGroupChanged group(%s) volume change not "
                        + "permitted while blocked changes reported by Gain callback", getName());
                return returnedFlags;
            }
            if (!muteChanged && !lastAudibleIndexChanged && !volumeChanged) {
                Slogf.i(TAG, "onAudioVolumeGroupChanged no change for group(%s).", getName());
                return returnedFlags;
            }
            // check first if need to align mute state with AudioService
            returnedFlags |= syncMuteState();
            if (returnedFlags != 0 || isUserMutedLocked()) {
                return returnedFlags;
            }
            returnedFlags |= syncGainIndex();
        }
        return returnedFlags;
    }

    @SuppressWarnings("GuardedBy")
    int syncMuteState() {
        int returnedFlags = 0;
        synchronized (mLock) {
            boolean isAmMutedByVolumeZero = mAmGroupMuted && (mAmLastAudibleGainIndex == 0);
            if (isUserMutedLocked() != mAmGroupMuted && !isAmMutedByVolumeZero) {
                Slogf.i(TAG, "syncMuteState group(%s) muted(%b) synced from AudioService",
                        getName(), mAmGroupMuted);
                super.setMuteLocked(mAmGroupMuted);
                returnedFlags |= EVENT_TYPE_MUTE_CHANGED;
                // When unmuting, ensure not breaking restrictions
                if (!mAmGroupMuted) {
                    // If thermal/attenuation while muted, am reports same index before restriction
                    if (mAmCurrentGainIndex == mCurrentGainIndex) {
                        if (isOverLimitLocked(mAmCurrentGainIndex)) {
                            setCurrentGainIndexLocked(mLimitedGainIndex,
                                    /* canChangeMuteState= */ false);
                        } else if (isAttenuatedLocked()) {
                            setCurrentGainIndexLocked(mAttenuatedGainIndex,
                                    /* canChangeMuteState= */ false);
                        }
                    } else {
                        returnedFlags |= syncGainIndex();
                    }
                }
            }
            if (isAmMutedByVolumeZero) {
                if (getCurrentGainIndex() != 0) {
                    Slogf.i(TAG, "syncMuteState group(%s) muted at 0 on AM, sync index", getName());
                    mCurrentGainIndex = 0;
                    returnedFlags |=
                            isFullyMutedLocked() ? 0 : EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
                }
                if (!isFullyMutedLocked()) {
                    applyMuteLocked(/* mute= */ false);
                }
            }
        }
        return returnedFlags;
    }

    int syncGainIndex() {
        int returnedFlags = 0;
        synchronized (mLock) {
            // check if a limitation or ducking is active to prevent sync with am index
            if (isOverLimitLocked(mAmCurrentGainIndex)) {
                Slogf.i(TAG, "syncGainIndex group(%s) index(%d) over limit(%d)", getName(),
                        mAmCurrentGainIndex,
                        mLimitedGainIndex);
                // AM Reports an overlimitation, if not already at the limit, set index as limit.
                if (mCurrentGainIndex != mLimitedGainIndex) {
                    mCurrentGainIndex = mLimitedGainIndex;
                    returnedFlags |= EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
                }
                // Force a volume sync on AudioManager as overlimit
                setCurrentGainIndexLocked(mLimitedGainIndex, /* canChangeMuteState= */ false);
            } else if (isAttenuatedLocked() && mAmCurrentGainIndex != mAttenuatedGainIndex) {
                Slogf.i(TAG, "syncGainIndex group(%s) index(%d) reset attenuation(%d)",
                        getName(), mAmCurrentGainIndex, mAttenuatedGainIndex);
                // reset attenuation
                resetAttenuationLocked();
                // Refresh the current gain with new AudioManager index
                mCurrentGainIndex = mAmCurrentGainIndex;
                returnedFlags |=
                        EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED | EVENT_TYPE_ATTENUATION_CHANGED;
            } else if (getRestrictedGainForIndexLocked(mCurrentGainIndex) != mAmCurrentGainIndex) {
                Slogf.i(TAG, "syncGainIndex group(%s) index(%d) synced from AudioService",
                        getName(),
                        mAmCurrentGainIndex);
                // Refresh the current gain with new AudioManager index
                mCurrentGainIndex = mAmCurrentGainIndex;
                returnedFlags |= EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
            } else {
                Slogf.i(TAG, "syncGainIndex group(%s) index(%d) ack from AudioService", getName(),
                        mAmCurrentGainIndex);
            }
        }
        return returnedFlags;
    }

    @Override
    void updateDevices(boolean useCoreAudioRouting) {
        // If not using core audio routing, than device need to be updated to match the information
        // for audio attributes to core volume groups.
        if (useCoreAudioRouting) {
            return;
        }

        int[] contexts = getContexts();
        for (int c = 0; c < contexts.length; c++) {
            int context = contexts[c];
            AudioAttributes[] audioAttributes = getAudioAttributesForContext(context);
            AudioDeviceAttributes device = getAudioDeviceForContext(context);
            setPreferredDeviceForAudioAttribute(Arrays.asList(audioAttributes), device);
        }

    }

    private void setPreferredDeviceForAudioAttribute(List<AudioAttributes> audioAttributes,
            AudioDeviceAttributes audioDeviceAttributes) {
        ArraySet<Integer> strategiesSet = new ArraySet<>();
        for (int c = 0; c < audioAttributes.size(); c++) {
            AudioProductStrategy strategy =
                    getProductStrategyForAudioAttributes(audioAttributes.get(c));
            if (strategy == null) {
                continue;
            }
            if (!strategiesSet.add(strategy.getId())) {
                continue;
            }
            mAudioManager.setPreferredDeviceForStrategy(strategy, audioDeviceAttributes);
        }
    }


    @Override
    protected int getDefaultGainIndex() {
        return mDefaultGainIndex;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    @GuardedBy("mLock")
    protected void dumpLocked(IndentingPrintWriter writer) {
        writer.printf("AudioManager Gain index (current): %d\n", mAmCurrentGainIndex);
    }
}
