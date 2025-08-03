/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.car.feature.Flags.carAudioDynamicDevices;
import static android.car.feature.Flags.carAudioMinMaxActivationVolume;
import static android.car.feature.Flags.carAudioMuteAmbiguity;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_BLOCKED_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
import static android.media.AudioDeviceInfo.TYPE_AUX_LINE;
import static android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST;
import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_HDMI;
import static android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY;
import static android.media.AudioDeviceInfo.TYPE_USB_DEVICE;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;

import static com.android.car.audio.hal.HalAudioGainCallback.reasonToString;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.builtin.util.Slogf;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneConfigProto;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupProto;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupProto.ContextToAddress;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupProto.GainInfo;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A class encapsulates a volume group in car.
 *
 * Interface holding volume interface APIs and also common code for:
 *
 * -volume groups using {@link AudioManager#setAudioPortGain} to control the volume
 * while the audioserver resource config_useFixedVolume is set.
 *
 * -volume groups relying on audioserver to control the volume and access using
 * {@link AudioManager#setVolumeIndexForAttributes(AudioAttributes, int, int)} and all other
 * related volume APIs.
 * Gain may either be controlled on hardware amplifier using Audio HAL setaudioPortConfig if the
 * correlated audio device port defines a gain controller with attribute name="useForVolume" set
 * or in software using the port id in Audio flinger.
 * Gains are set only when activity is detected on the given audio device port (Mixer thread, or
 * {@link android.media.HwAudioSource} realized through a software bridge or hardware bridge.
 *
 */
/* package */ abstract class CarVolumeGroup {
    public static final int UNINITIALIZED = -1;

    private final boolean mUseCarVolumeGroupMute;
    private final boolean mHasCriticalAudioContexts;
    private final CarAudioSettings mSettingsManager;
    protected final int mId;
    private final String mName;
    protected final int mZoneId;
    protected final int mConfigId;
    protected final SparseArray<CarAudioDeviceInfo> mContextToDevices;

    protected final Object mLock = new Object();
    private final CarAudioContext mCarAudioContext;

    private final int mMinActivationVolumePercentage;
    private final int mMaxActivationVolumePercentage;

    @GuardedBy("mLock")
    protected final SparseArray<String> mContextToAddress;
    @GuardedBy("mLock")
    protected final ArrayMap<String, CarAudioDeviceInfo> mAddressToCarAudioDeviceInfo;
    @GuardedBy("mLock")
    protected int mStoredGainIndex;

    @GuardedBy("mLock")
    protected int mCurrentGainIndex = UNINITIALIZED;

    /**
     * Mute state for requests coming from clients. See {@link #mIsHalMuted} for state of requests
     * coming from HAL.
     */
    @GuardedBy("mLock")
    protected boolean mIsMuted;
    @GuardedBy("mLock")
    protected @UserIdInt int mUserId = UserHandle.CURRENT.getIdentifier();

    /**
     * Attenuated gain is set to {@link #UNINITIALIZED} till attenuation explicitly reported by
     * {@link com.android.car.audio.hal.HalAudioGainCallback#onAudioDeviceGainsChanged} for one or
     * more {@link android.hardware.automotive.audiocontrol.Reasons}. When the reason is cleared,
     * it returns back to {@link #UNINITIALIZED}.
     */
    @GuardedBy("mLock")
    protected int mAttenuatedGainIndex = UNINITIALIZED;

    /**
     * Limitation gain is set to max gain value till limitation explicitly reported by {@link
     * com.android.car.audio.hal.HalAudioGainCallback#onAudioDeviceGainsChanged} for one or more
     * {@link android.hardware.automotive.audiocontrol.Reasons}. When the reason is cleared, it
     * returns back to max.
     */
    @GuardedBy("mLock")
    protected int mLimitedGainIndex;

    /**
     * Blocked gain is set to {@link #UNINITIALIZED} till blocking case explicitly reported by
     * {@link com.android.car.audio.hal.HalAudioGainCallback#onAudioDeviceGainsChanged} for one or
     * more {@link android.hardware.automotive.audiocontrol.Reasons}. When the reason is cleared,
     * it returns back to {@link #UNINITIALIZED}.
     */
    @GuardedBy("mLock")
    protected int mBlockedGainIndex = UNINITIALIZED;

    /**
     * The default state of HAL mute is {@code false} until HAL explicitly reports through
     * {@link com.android.car.audio.hal.HalAudioGainCallback#onAudioDeviceGainsChanged} for one or
     * more {@link android.hardware.automotive.audiocontrol.Reasons}. When the reason
     * is cleared, it is reset. See {@link #mIsMuted} for state of requests coming from clients.
     */
    @GuardedBy("mLock")
    private boolean mIsHalMuted = false;

    /**
     * Reasons list currently reported for this port by {@link
     * com.android.car.audio.hal.HalAudioGainCallback#onAudioDeviceGainsChanged}.
     */
    protected List<Integer> mReasons = new ArrayList<>();

    protected CarVolumeGroup(CarAudioContext carAudioContext, CarAudioSettings settingsManager,
            SparseArray<CarAudioDeviceInfo> contextToDevices, int zoneId, int configId,
            int volumeGroupId, String name, boolean useCarVolumeGroupMute,
            int maxActivationVolumePercentage, int minActivationVolumePercentage) {
        mSettingsManager = settingsManager;
        mCarAudioContext = carAudioContext;
        mContextToDevices = contextToDevices;
        mZoneId = zoneId;
        mConfigId = configId;
        mId = volumeGroupId;
        mName = Objects.requireNonNull(name, "Volume group name cannot be null");
        mUseCarVolumeGroupMute = useCarVolumeGroupMute;
        mContextToAddress = new SparseArray<>(contextToDevices.size());
        mAddressToCarAudioDeviceInfo = new ArrayMap<>(contextToDevices.size());
        List<AudioAttributes> volumeAttributes = new ArrayList<>();
        for (int index = 0; index <  contextToDevices.size(); index++) {
            int context = contextToDevices.keyAt(index);
            CarAudioDeviceInfo info = contextToDevices.valueAt(index);
            List<AudioAttributes> audioAttributes =
                    Arrays.asList(mCarAudioContext.getAudioAttributesForContext(context));
            volumeAttributes.addAll(audioAttributes);
            mContextToAddress.put(context, info.getAddress());
            mAddressToCarAudioDeviceInfo.put(info.getAddress(), info);
        }

        mHasCriticalAudioContexts = containsCriticalAttributes(volumeAttributes);
        mMaxActivationVolumePercentage = maxActivationVolumePercentage;
        mMinActivationVolumePercentage = minActivationVolumePercentage;
    }

    void init() {
        synchronized (mLock) {
            mStoredGainIndex = mSettingsManager.getStoredVolumeGainIndexForUser(
                    mUserId, mZoneId, mConfigId, mId);
            updateCurrentGainIndexLocked();
        }
    }

    @GuardedBy("mLock")
    protected boolean hasPendingAttenuationReasonsLocked() {
        return !mReasons.isEmpty();
    }

    @GuardedBy("mLock")
    protected void setBlockedLocked(int blockedIndex) {
        mBlockedGainIndex = blockedIndex;
    }

    @GuardedBy("mLock")
    protected void resetBlockedLocked() {
        setBlockedLocked(UNINITIALIZED);
    }

    @GuardedBy("mLock")
    protected boolean isBlockedLocked() {
        return mBlockedGainIndex != UNINITIALIZED;
    }

    @GuardedBy("mLock")
    protected void setLimitLocked(int limitIndex) {
        int minActivationGainIndex = getMinActivationGainIndex();
        if (limitIndex < minActivationGainIndex) {
            Slogf.w(CarLog.TAG_AUDIO, "Limit cannot be set lower than min activation volume index",
                    minActivationGainIndex);
        }
        mLimitedGainIndex = limitIndex;
    }

    @GuardedBy("mLock")
    protected void resetLimitLocked() {
        setLimitLocked(getMaxGainIndex());
    }

    @GuardedBy("mLock")
    protected boolean isLimitedLocked() {
        return mLimitedGainIndex != getMaxGainIndex();
    }

    @GuardedBy("mLock")
    protected boolean isOverLimitLocked() {
        return isOverLimitLocked(mCurrentGainIndex);
    }

    @GuardedBy("mLock")
    protected boolean isOverLimitLocked(int index) {
        return isLimitedLocked() && (index > mLimitedGainIndex);
    }

    @GuardedBy("mLock")
    protected void setAttenuatedGainLocked(int attenuatedGainIndex) {
        mAttenuatedGainIndex = attenuatedGainIndex;
    }

    @GuardedBy("mLock")
    protected void resetAttenuationLocked() {
        setAttenuatedGainLocked(UNINITIALIZED);
    }

    @GuardedBy("mLock")
    protected boolean isAttenuatedLocked() {
        return mAttenuatedGainIndex != UNINITIALIZED;
    }

    @GuardedBy("mLock")
    private void setHalMuteLocked(boolean mute) {
        mIsHalMuted = mute;
    }

    @GuardedBy("mLock")
    protected boolean isHalMutedLocked() {
        return mIsHalMuted;
    }

    boolean isHalMuted() {
        synchronized (mLock) {
            return isHalMutedLocked();
        }
    }

    void setBlocked(int blockedIndex) {
        synchronized (mLock) {
            setBlockedLocked(blockedIndex);
        }
    }

    void resetBlocked() {
        synchronized (mLock) {
            resetBlockedLocked();
        }
    }

    boolean isBlocked() {
        synchronized (mLock) {
            return isBlockedLocked();
        }
    }

    void setLimit(int limitIndex) {
        synchronized (mLock) {
            setLimitLocked(limitIndex);
        }
    }

    void resetLimit() {
        synchronized (mLock) {
            resetLimitLocked();
        }
    }

    boolean isLimited() {
        synchronized (mLock) {
            return isLimitedLocked();
        }
    }

    boolean isOverLimit() {
        synchronized (mLock) {
            return isOverLimitLocked();
        }
    }

    void setAttenuatedGain(int attenuatedGainIndex) {
        synchronized (mLock) {
            setAttenuatedGainLocked(attenuatedGainIndex);
        }
    }

    void resetAttenuation() {
        synchronized (mLock) {
            resetAttenuationLocked();
        }
    }

    boolean isAttenuated() {
        synchronized (mLock) {
            return isAttenuatedLocked();
        }
    }

    @Nullable
    CarAudioDeviceInfo getCarAudioDeviceInfoForAddress(String address) {
        synchronized (mLock) {
            return mAddressToCarAudioDeviceInfo.get(address);
        }
    }

    int[] getContexts() {
        int[] carAudioContexts = new int[mContextToDevices.size()];
        for (int i = 0; i < mContextToDevices.size(); i++) {
            carAudioContexts[i] = mContextToDevices.keyAt(i);
        }
        return carAudioContexts;
    }

    protected AudioAttributes[] getAudioAttributesForContext(int context) {
        return mCarAudioContext.getAudioAttributesForContext(context);
    }

    /**
     * Returns the id of the volume group.
     * <p> Note that all clients are already developed in the way that when they get the number of
     * volume group, they will then address a given volume group using its id as if the id was the
     * index of the array of group (aka 0 to length - 1).
     */
    int getId() {
        return mId;
    }

    String getName() {
        return mName;
    }

    /**
     * Returns the devices address for the given context
     * or {@code null} if the context does not exist in the volume group
     */
    @Nullable
    String getAddressForContext(int audioContext) {
        synchronized (mLock) {
            return mContextToAddress.get(audioContext);
        }
    }

    /**
     * Returns the audio devices for the given context
     * or {@code null} if the context does not exist in the volume group
     */
    @Nullable
    AudioDeviceAttributes getAudioDeviceForContext(int audioContext) {
        String address = getAddressForContext(audioContext);
        if (address == null) {
            return null;
        }

        CarAudioDeviceInfo info;
        synchronized (mLock) {
            info = mAddressToCarAudioDeviceInfo.get(address);
        }
        if (info == null) {
            return null;
        }

        return info.getAudioDevice();
    }

    @AudioContext
    List<Integer> getContextsForAddress(@NonNull String address) {
        List<Integer> carAudioContexts = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mContextToAddress.size(); i++) {
                String value = mContextToAddress.valueAt(i);
                if (address.equals(value)) {
                    carAudioContexts.add(mContextToAddress.keyAt(i));
                }
            }
        }
        return carAudioContexts;
    }

    List<String> getAddresses() {
        synchronized (mLock) {
            return new ArrayList<>(mAddressToCarAudioDeviceInfo.keySet());
        }
    }

    List<Integer> getAllSupportedUsagesForAddress(@NonNull String address) {
        List<Integer> supportedUsagesForAddress = new ArrayList<>();
        List<Integer> contextsForAddress = getContextsForAddress(address);
        for (int contextIndex = 0; contextIndex < contextsForAddress.size(); contextIndex++) {
            int contextId = contextsForAddress.get(contextIndex);
            AudioAttributes[] attributes =
                    mCarAudioContext.getAudioAttributesForContext(contextId);
            for (int attrIndex = 0; attrIndex < attributes.length; attrIndex++) {
                int usage = attributes[attrIndex].getSystemUsage();
                if (!supportedUsagesForAddress.contains(usage)) {
                    supportedUsagesForAddress.add(usage);
                }
            }
        }
        return supportedUsagesForAddress;
    }

    abstract int getMaxGainIndex();

    abstract int getMinGainIndex();

    int getMaxActivationGainIndex() {
        int maxGainIndex = getMaxGainIndex();
        int minGainIndex = getMinGainIndex();
        return minGainIndex + (int) Math.round(mMaxActivationVolumePercentage / 100.0
                * (maxGainIndex - minGainIndex));
    }

    int getMinActivationGainIndex() {
        int maxGainIndex = getMaxGainIndex();
        int minGainIndex = getMinGainIndex();
        return minGainIndex + (int) Math.round(mMinActivationVolumePercentage / 100.0
                * (maxGainIndex - minGainIndex));
    }

    int getCurrentGainIndex() {
        synchronized (mLock) {
            if (isMutedLocked()) {
                return getMinGainIndex();
            }

            return getRestrictedGainForIndexLocked(getCurrentGainIndexLocked());
        }
    }

    @GuardedBy("mLock")
    protected int getCurrentGainIndexLocked() {
        return mCurrentGainIndex;
    }

    @GuardedBy("mLock")
    protected int getRestrictedGainForIndexLocked(int index) {
        if (isBlockedLocked()) {
            return mBlockedGainIndex;
        }
        if (isOverLimitLocked()) {
            return mLimitedGainIndex;
        }
        if (isAttenuatedLocked()) {
            // Need to figure out if attenuation shall be hidden to end user
            // as while ducked from IAudioControl
            // TODO(b/) clarify in case of volume adjustment if the reference index is the
            // ducked index or the current index. Taking current may lead to gap of index > 1.
            return mAttenuatedGainIndex;
        }
        return index;
    }

    /**
     * Sets the gain on this group, gain will be set on all devices within volume group.
     */
    void setCurrentGainIndex(int gainIndex) {
        synchronized (mLock) {
            int currentgainIndex = gainIndex;
            Preconditions.checkArgument(isValidGainIndexLocked(gainIndex),
                    "Gain out of range (%d:%d) index %d", getMinGainIndex(), getMaxGainIndex(),
                    gainIndex);
            if (isBlockedLocked()) {
                // prevent any volume change while {@link IAudioGainCallback} reported block event.
                return;
            }
            if (isOverLimitLocked(currentgainIndex)) {
                currentgainIndex = mLimitedGainIndex;
            }
            if (isAttenuatedLocked()) {
                resetAttenuationLocked();
            }
            // In case of attenuation/Limitation, requested index is now the new reference for
            // cached current index.
            mCurrentGainIndex = currentgainIndex;

            if (mIsMuted) {
                setMuteLocked(false);
            }
            setCurrentGainIndexLocked(mCurrentGainIndex);
        }
    }

    @GuardedBy("mLock")
    protected void setCurrentGainIndexLocked(int gainIndex) {
        storeGainIndexForUserLocked(gainIndex, mUserId);
    }

    boolean handleActivationVolume() {
        if (!carAudioMinMaxActivationVolume()) {
            return false;
        }
        boolean invokeVolumeGainIndexChanged = true;
        synchronized (mLock) {
            int minActivationGainIndex = getMinActivationGainIndex();
            int maxActivationGainIndex = getMaxActivationGainIndex();
            int curGainIndex = getCurrentGainIndexLocked();
            int activationVolume;
            if (curGainIndex > maxActivationGainIndex) {
                activationVolume = maxActivationGainIndex;
            } else if (curGainIndex < minActivationGainIndex) {
                activationVolume = minActivationGainIndex;
            } else {
                return false;
            }
            if (isMutedLocked() || isBlockedLocked()) {
                invokeVolumeGainIndexChanged = false;
            } else {
                if (isOverLimitLocked(activationVolume)) {
                    // Limit index is used as min activation gain index if limit is lower than min
                    // activation gain index.
                    invokeVolumeGainIndexChanged = !isOverLimitLocked(curGainIndex);
                }
                if (isAttenuatedLocked()) {
                    // Attenuation state should be maintained and not reset for min/max activation.
                    invokeVolumeGainIndexChanged = false;
                }
            }
            mCurrentGainIndex = activationVolume;
            setCurrentGainIndexLocked(mCurrentGainIndex);
        }
        return invokeVolumeGainIndexChanged;
    }

    boolean hasCriticalAudioContexts() {
        return mHasCriticalAudioContexts;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public String toString() {
        synchronized (mLock) {
            return "CarVolumeGroup id: " + mId
                    + " currentGainIndex: " + mCurrentGainIndex
                    + " contexts: " + Arrays.toString(getContexts())
                    + " addresses: " + String.join(", ", getAddresses());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    protected abstract void dumpLocked(IndentingPrintWriter writer);

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.printf("CarVolumeGroup(%d)\n", mId);
            writer.increaseIndent();
            writer.printf("Name(%s)\n", mName);
            writer.printf("Zone Id(%d)\n", mZoneId);
            writer.printf("Configuration Id(%d)\n", mConfigId);
            writer.printf("Is Muted(%b)\n", isMutedLocked());
            writer.printf("UserId(%d)\n", mUserId);
            writer.printf("Persist Volume Group Mute(%b)\n",
                    mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId));
            dumpLocked(writer);
            writer.printf("Gain indexes (min / max / default / current): %d %d %d %d\n",
                    getMinGainIndex(), getMaxGainIndex(), getDefaultGainIndex(),
                    mCurrentGainIndex);
            writer.printf("Activation gain indexes (min / max): %d %d\n",
                    getMinActivationGainIndex(), getMaxActivationGainIndex());
            for (int i = 0; i < mContextToAddress.size(); i++) {
                writer.printf("Context: %s -> Address: %s\n",
                        mCarAudioContext.toString(mContextToAddress.keyAt(i)),
                        mContextToAddress.valueAt(i));
            }
            for (int i = 0; i < mContextToDevices.size(); i++) {
                CarAudioDeviceInfo info = mContextToDevices.valueAt(i);
                info.dump(writer);
            }
            writer.printf("Reported reasons:\n");
            writer.increaseIndent();
            for (int index = 0; index < mReasons.size(); index++) {
                int reason = mReasons.get(index);
                writer.printf("%s\n", reasonToString(reason));
            }
            writer.decreaseIndent();
            writer.printf("Gain infos:\n");
            writer.increaseIndent();
            writer.printf(
                    "Blocked: %b%s\n",
                    isBlockedLocked(),
                    (isBlockedLocked() ? " (at: " + mBlockedGainIndex + ")" : ""));
            writer.printf(
                    "Limited: %b%s\n",
                    isLimitedLocked(),
                    (isLimitedLocked() ? " (at: " + mLimitedGainIndex + ")" : ""));
            writer.printf(
                    "Attenuated: %b%s\n",
                    isAttenuatedLocked(),
                    (isAttenuatedLocked() ? " (at: " + mAttenuatedGainIndex + ")" : ""));
            writer.printf("Muted by HAL: %b\n", isHalMutedLocked());
            writer.decreaseIndent();
            // Empty line for comfortable reading
            writer.println();
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long volumeGroupToken = proto.start(CarAudioZoneConfigProto.VOLUME_GROUPS);
        synchronized (mLock) {
            proto.write(CarVolumeGroupProto.ID, mId);
            proto.write(CarVolumeGroupProto.NAME, mName);
            proto.write(CarVolumeGroupProto.ZONE_ID, mZoneId);
            proto.write(CarVolumeGroupProto.CONFIG_ID, mConfigId);
            proto.write(CarVolumeGroupProto.MUTED, isMutedLocked());
            proto.write(CarVolumeGroupProto.USER_ID, mUserId);
            proto.write(CarVolumeGroupProto.PERSIST_VOLUME_GROUP_MUTE_ENABLED,
                    mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId));

            long volumeGainToken = proto.start(CarVolumeGroupProto.VOLUME_GAIN);
            proto.write(CarAudioDumpProto.CarVolumeGain.MIN_GAIN_INDEX, getMinGainIndex());
            proto.write(CarAudioDumpProto.CarVolumeGain.MAX_GAIN_INDEX, getMaxGainIndex());
            proto.write(CarAudioDumpProto.CarVolumeGain.DEFAULT_GAIN_INDEX, getDefaultGainIndex());
            proto.write(CarAudioDumpProto.CarVolumeGain.CURRENT_GAIN_INDEX, mCurrentGainIndex);
            proto.write(CarAudioDumpProto.CarVolumeGain.MIN_ACTIVATION_GAIN_INDEX,
                    getMinActivationGainIndex());
            proto.write(CarAudioDumpProto.CarVolumeGain.MAX_ACTIVATION_GAIN_INDEX,
                    getMaxActivationGainIndex());
            proto.end(volumeGainToken);

            for (int i = 0; i < mContextToAddress.size(); i++) {
                long contextToAddressMappingToken = proto.start(CarVolumeGroupProto
                        .CONTEXT_TO_ADDRESS_MAPPINGS);
                proto.write(ContextToAddress.CONTEXT,
                        mCarAudioContext.toString(mContextToAddress.keyAt(i)));
                proto.write(ContextToAddress.ADDRESS, mContextToAddress.valueAt(i));
                proto.end(contextToAddressMappingToken);
            }

            for (int i = 0; i < mContextToDevices.size(); i++) {
                CarAudioDeviceInfo info = mContextToDevices.valueAt(i);
                info.dumpProto(CarVolumeGroupProto.CAR_AUDIO_DEVICE_INFOS, proto);
            }

            for (int index = 0; index < mReasons.size(); index++) {
                int reason = mReasons.get(index);
                proto.write(CarVolumeGroupProto.REPORTED_REASONS, reasonToString(reason));
            }

            long gainInfoToken = proto.start(CarVolumeGroupProto.GAIN_INFOS);
            proto.write(GainInfo.BLOCKED, isBlockedLocked());
            if (isBlockedLocked()) {
                proto.write(GainInfo.BLOCKED_GAIN_INDEX, mBlockedGainIndex);
            }
            proto.write(GainInfo.LIMITED, isLimitedLocked());
            if (isLimitedLocked()) {
                proto.write(GainInfo.LIMITED_GAIN_INDEX, mLimitedGainIndex);
            }
            proto.write(GainInfo.ATTENUATED, isAttenuatedLocked());
            if (isAttenuatedLocked()) {
                proto.write(GainInfo.ATTENUATED_GAIN_INDEX, mAttenuatedGainIndex);
            }
            proto.write(GainInfo.HAL_MUTED, isHalMutedLocked());
            proto.write(GainInfo.IS_ACTIVE, isActive());
            proto.end(gainInfoToken);

        }
        proto.end(volumeGroupToken);
    }

    void loadVolumesSettingsForUser(@UserIdInt int userId) {
        synchronized (mLock) {
            //Update the volume for the new user
            updateUserIdLocked(userId);
            //Update the current gain index
            updateCurrentGainIndexLocked();
            setCurrentGainIndexLocked(getCurrentGainIndexLocked());
            //Reset devices with current gain index
            updateGroupMuteLocked();
        }
    }

    /**
     * Set the mute state of the Volume Group
     *
     * @param mute state requested
     * @return true if mute state has changed, false otherwiser (already set or change not allowed)
     */
    boolean setMute(boolean mute) {
        synchronized (mLock) {
            // if hal muted the audio devices, then do not allow other incoming requests
            // to perform unmute.
            if (!mute && isHalMutedLocked()) {
                Slogf.e(CarLog.TAG_AUDIO, "Un-mute request cannot be processed due to active "
                        + "hal mute restriction!");
                return false;
            }
            applyMuteLocked(mute);
            return setMuteLocked(mute);
        }
    }

    @GuardedBy("mLock")
    protected boolean setMuteLocked(boolean mute) {
        boolean hasChanged = mIsMuted != mute;
        mIsMuted = mute;
        if (mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId)) {
            mSettingsManager.storeVolumeGroupMuteForUser(mUserId, mZoneId, mConfigId, mId, mute);
        }
        return hasChanged;
    }

    @GuardedBy("mLock")
    protected void applyMuteLocked(boolean mute) {
    }

    boolean isMuted() {
        synchronized (mLock) {
            return isMutedLocked();
        }
    }

    @GuardedBy("mLock")
    protected boolean isMutedLocked() {
        // if either of the mute states is set, it results in group being muted.
        return isUserMutedLocked() || isHalMutedLocked();
    }

    @GuardedBy("mLock")
    protected boolean isUserMutedLocked() {
        return mIsMuted;
    }

    @GuardedBy("mLock")
    protected boolean isFullyMutedLocked() {
        return isUserMutedLocked() || isHalMutedLocked() || isBlockedLocked();
    }

    private static boolean containsCriticalAttributes(List<AudioAttributes> volumeAttributes) {
        for (int index = 0; index < volumeAttributes.size(); index++) {
            if (CarAudioContext.isCriticalAudioAudioAttribute(volumeAttributes.get(index))) {
                return true;
            }
        }
        return false;
    }

    @GuardedBy("mLock")
    private void updateUserIdLocked(@UserIdInt int userId) {
        mUserId = userId;
        mStoredGainIndex = getCurrentGainIndexForUserLocked();
    }

    @GuardedBy("mLock")
    private int getCurrentGainIndexForUserLocked() {
        int gainIndexForUser = mSettingsManager.getStoredVolumeGainIndexForUser(mUserId, mZoneId,
                mConfigId, mId);
        Slogf.i(CarLog.TAG_AUDIO, "updateUserId userId " + mUserId
                + " gainIndexForUser " + gainIndexForUser);
        return gainIndexForUser;
    }

    /**
     * Update the current gain index based on the stored gain index
     */
    @GuardedBy("mLock")
    private void updateCurrentGainIndexLocked() {
        if (isValidGainIndexLocked(mStoredGainIndex)) {
            mCurrentGainIndex = mStoredGainIndex;
        } else {
            mCurrentGainIndex = getDefaultGainIndex();
        }
    }

    protected boolean isValidGainIndex(int gainIndex) {
        synchronized (mLock) {
            return isValidGainIndexLocked(gainIndex);
        }
    }
    protected abstract boolean isValidGainIndexLocked(int gainIndex);

    protected abstract int getDefaultGainIndex();

    @GuardedBy("mLock")
    private void storeGainIndexForUserLocked(int gainIndex, @UserIdInt int userId) {
        mSettingsManager.storeVolumeGainIndexForUser(userId,
                mZoneId, mConfigId, mId, gainIndex);
    }

    @GuardedBy("mLock")
    private void updateGroupMuteLocked() {
        if (!mUseCarVolumeGroupMute) {
            return;
        }
        if (!mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId)) {
            mIsMuted = false;
            return;
        }
        mIsMuted = mSettingsManager.getVolumeGroupMuteForUser(mUserId, mZoneId, mConfigId, mId);
        applyMuteLocked(isFullyMutedLocked());
    }

    /**
     * Updates volume group states (index, mute, blocked etc) on callback from audio control hal.
     *
     * <p>If gain config info carries duplicate info, do not generate events (i.e. eventType = 0)
     * @param halReasons reasons for change to gain config info
     * @param gain updated gain config info
     * @return one or more of {@link android.car.media.CarVolumeGroupEvent.EventTypeEnum} or 0 for
     * duplicate gain config info
     */
    int onAudioGainChanged(List<Integer> halReasons, CarAudioGainConfigInfo gain) {
        int eventType = 0;
        int halIndex = gain.getVolumeIndex();
        if (getCarAudioDeviceInfoForAddress(gain.getDeviceAddress()) == null
                || !isValidGainIndex(halIndex)) {
            Slogf.e(CarLog.TAG_AUDIO,
                    "onAudioGainChanged invalid CarAudioGainConfigInfo: " + gain
                    + " for group id: " + mId);
            return eventType;
        }
        synchronized (mLock) {
            int previousRestrictedIndex = getRestrictedGainForIndexLocked(mCurrentGainIndex);
            mReasons = new ArrayList<>(halReasons);

            boolean shouldBlock = CarAudioGainMonitor.shouldBlockVolumeRequest(halReasons);
            if ((shouldBlock != isBlockedLocked())
                    || (shouldBlock && (halIndex != mBlockedGainIndex))) {
                setBlockedLocked(shouldBlock ? halIndex : UNINITIALIZED);
                eventType |= EVENT_TYPE_VOLUME_BLOCKED_CHANGED;
            }

            boolean shouldLimit = CarAudioGainMonitor.shouldLimitVolume(halReasons);
            if ((shouldLimit != isLimitedLocked())
                    || (shouldLimit && (halIndex != mLimitedGainIndex))) {
                setLimitLocked(shouldLimit ? halIndex : getMaxGainIndex());
                eventType |= EVENT_TYPE_ATTENUATION_CHANGED;
            }

            boolean shouldDuck = CarAudioGainMonitor.shouldDuckGain(halReasons);
            if ((shouldDuck != isAttenuatedLocked())
                    || (shouldDuck && (halIndex != mAttenuatedGainIndex))) {
                setAttenuatedGainLocked(shouldDuck ? halIndex : UNINITIALIZED);
                eventType |= EVENT_TYPE_ATTENUATION_CHANGED;
            }

            // Accept mute callbacks from hal only if group mute is enabled.
            // If disabled, such callbacks will be considered as blocking restriction only.
            boolean shouldMute = CarAudioGainMonitor.shouldMuteVolumeGroup(halReasons);
            if (mUseCarVolumeGroupMute && (shouldMute != isHalMutedLocked())) {
                setHalMuteLocked(shouldMute);
                eventType |= EVENT_TYPE_MUTE_CHANGED;
            }

            if (CarAudioGainMonitor.shouldUpdateVolumeIndex(halReasons)
                    && (halIndex != getRestrictedGainForIndexLocked(mCurrentGainIndex))) {
                mCurrentGainIndex = halIndex;
                eventType |= EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
            }

            // Blocked/Attenuated index shall have been already apply by Audio HAL on HW.
            // However, keep in sync & broadcast to all ports this volume group deals with.
            //
            // Do not update current gain cache, keep it for restoring rather using reported index
            // when the event is cleared.
            int newRestrictedIndex = getRestrictedGainForIndexLocked(mCurrentGainIndex);
            setCurrentGainIndexLocked(newRestrictedIndex);
            // Hal or user mute state can change (only user mute enabled while hal muted allowed).
            // Force a sync of mute application.
            applyMuteLocked(isFullyMutedLocked());

            if (newRestrictedIndex != previousRestrictedIndex) {
                eventType |= EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
            }
        }
        return eventType;
    }

    CarVolumeGroupInfo getCarVolumeGroupInfo() {
        int gainIndex;
        boolean isMuted;
        boolean isHalMuted;
        boolean isBlocked;
        boolean isAttenuated;
        synchronized (mLock) {
            gainIndex = getRestrictedGainForIndexLocked(mCurrentGainIndex);
            isMuted = isMutedLocked();
            isHalMuted = isHalMutedLocked();
            isBlocked = isBlockedLocked();
            isAttenuated = isAttenuatedLocked() || isLimitedLocked();
        }

        String name = mName.isEmpty() ? "group id " + mId : mName;

        CarVolumeGroupInfo.Builder builder = new CarVolumeGroupInfo.Builder(name, mZoneId, mId)
                .setVolumeGainIndex(gainIndex).setMaxVolumeGainIndex(getMaxGainIndex())
                .setMinVolumeGainIndex(getMinGainIndex()).setMuted(isMuted).setBlocked(isBlocked)
                .setAttenuated(isAttenuated).setAudioAttributes(getAudioAttributes());

        if (carAudioDynamicDevices()) {
            builder.setAudioDeviceAttributes(getAudioDeviceAttributes());
        }

        if (carAudioMinMaxActivationVolume()) {
            builder.setMaxActivationVolumeGainIndex(getMaxActivationGainIndex())
                    .setMinActivationVolumeGainIndex(getMinActivationGainIndex());
        }

        if (carAudioMuteAmbiguity()) {
            builder.setMutedBySystem(isHalMuted);
        }

        return builder.build();
    }

    private List<AudioDeviceAttributes> getAudioDeviceAttributes() {
        ArraySet<AudioDeviceAttributes> set = new ArraySet<>();
        int[] contexts = getContexts();
        for (int index = 0; index < contexts.length; index++) {
            AudioDeviceAttributes device = getAudioDeviceForContext(contexts[index]);
            if (device == null) {
                Slogf.w(CarLog.TAG_AUDIO,
                        "getAudioDeviceAttributes: Could not find audio device for context "
                                + mCarAudioContext.toString(contexts[index]));
                continue;
            }
            set.add(device);
        }
        return new ArrayList<>(set);
    }

    boolean hasAudioAttributes(AudioAttributes audioAttributes) {
        synchronized (mLock) {
            for (int index = 0; index < mContextToAddress.size(); index++) {
                int context = mContextToAddress.keyAt(index);
                AudioAttributes[] contextAttributes =
                        mCarAudioContext.getAudioAttributesForContext(context);
                for (int attrIndex = 0; attrIndex < contextAttributes.length; attrIndex++) {
                    if (Objects.equals(contextAttributes[attrIndex], audioAttributes)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    List<AudioAttributes> getAudioAttributes() {
        List<AudioAttributes> audioAttributes = new ArrayList<>();
        synchronized (mLock) {
            for (int index = 0; index < mContextToAddress.size(); index++) {
                int context = mContextToAddress.keyAt(index);
                AudioAttributes[] contextAttributes =
                        mCarAudioContext.getAudioAttributesForContext(context);
                for (int attrIndex = 0; attrIndex < contextAttributes.length; attrIndex++) {
                    audioAttributes.add(contextAttributes[attrIndex]);
                }
            }
        }
        return audioAttributes;
    }

    /**
     * @return one or more {@link android.car.media.CarVolumeGroupEvent.EventTypeEnum}
     */
    public int onAudioVolumeGroupChanged(int flags) {
        return 0;
    }

    /**
     * Updates car audio device info with the hal audio device info
     */
    void updateAudioDeviceInfo(HalAudioDeviceInfo halDeviceInfo) {
        synchronized (mLock) {
            CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.get(halDeviceInfo.getAddress());
            if (info == null) {
                Slogf.w(CarLog.TAG_AUDIO, "No matching car audio device info found for address: %s",
                        halDeviceInfo.getAddress());
                return;
            }
            info.updateAudioDeviceInfo(halDeviceInfo);
        }
    }

    void updateDevices(boolean useCoreAudioRouting) {
    }

    /**
     * Calculates the new gain stages from list of assigned audio device infos
     *
     * <p>Used to update audio device gain stages dynamically.
     *
     * @return  one or more of {@link android.car.media.CarVolumeGroupEvent.EventTypeEnum}, or 0 if
     * dynamic updates are not supported
     */
    int calculateNewGainStageFromDeviceInfos() {
        return 0;
    }

    boolean isActive() {
        synchronized (mLock) {
            for (int c = 0; c < mAddressToCarAudioDeviceInfo.size(); c++) {
                CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.valueAt(c);
                if (info.isActive()) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    public boolean audioDevicesAdded(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        if (isActive()) {
            return false;
        }

        boolean updated = false;
        for (int c = 0; c < mContextToDevices.size(); c++) {
            if (!mContextToDevices.valueAt(c).audioDevicesAdded(devices)) {
                continue;
            }
            updated = true;
        }
        if (!updated) {
            return false;
        }
        synchronized (mLock) {
            updateAudioDevicesMappingLocked();
        }
        return true;
    }

    public boolean audioDevicesRemoved(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        boolean updated = false;
        for (int c = 0; c < mContextToDevices.size(); c++) {
            if (!mContextToDevices.valueAt(c).audioDevicesRemoved(devices)) {
                continue;
            }
            updated = true;
        }
        if (!updated) {
            return false;
        }
        synchronized (mLock) {
            updateAudioDevicesMappingLocked();
        }
        return true;
    }

    @GuardedBy("mLock")
    private void updateAudioDevicesMappingLocked() {
        mAddressToCarAudioDeviceInfo.clear();
        mContextToAddress.clear();
        for (int c = 0; c < mContextToDevices.size(); c++) {
            CarAudioDeviceInfo info = mContextToDevices.valueAt(c);
            int audioContext = mContextToDevices.keyAt(c);
            mAddressToCarAudioDeviceInfo.put(info.getAddress(), info);
            mContextToAddress.put(audioContext, info.getAddress());
        }
    }

    /**
     * Determines if device types assign to volume groups are valid based on the following rules:
     * <ul>
     * <li>Dynamic device types (non BUS) for this group should not appear in the
     * {@code dynamicDeviceTypesInConfig} passed in parameter</li>
     * <li>Dynamic device types should appear alone in volume group</li>
     * </ul>
     *
     * @param dynamicDeviceTypesInConfig Devices already seen in other volume groups for the same
     * configuration, groups checks if the device types for the volume group already exists here
     * and return {@code false} if so. Also adds any non-existing device types for the group.
     * @return {@code true} if the rules defined above are valid for the group, {@code false}
     * otherwise
     */
    boolean validateDeviceTypes(Set<Integer> dynamicDeviceTypesInConfig) {
        List<AudioDeviceAttributes> devices = getAudioDeviceAttributes();
        boolean hasNonBusDevice = false;
        for (int c = 0; c < devices.size(); c++) {
            int deviceType = devices.get(c).getType();
            // BUS devices are handled by address name check
            if (deviceType == TYPE_BUS) {
                continue;
            }
            hasNonBusDevice = true;
            int convertedType = convertDeviceType(deviceType);
            if (dynamicDeviceTypesInConfig.add(convertedType)) {
                continue;
            }
            Slogf.e(CarLog.TAG_AUDIO, "Car volume groups defined in"
                    + " car_audio_configuration.xml shared the dynamic device type "
                    + DebugUtils.constantToString(AudioDeviceInfo.class, /* prefix= */ "TYPE_",
                    deviceType) + " in multiple volume groups in the same configuration");
            return false;
        }
        if (!hasNonBusDevice || devices.size() == 1) {
            return true;
        }
        Slogf.e(CarLog.TAG_AUDIO, "Car volume group " + getName()
                + " defined in car_audio_configuration.xml"
                + " has multiple devices for a dynamic device group."
                + " Groups with dynamic devices can only have a single device.");
        return false;
    }

    // Given the current limitation in BT stack where there can only be one BT device available
    // of any type, we need to consider all BT types as the same, we are picking TYPE_BLUETOOTH_A2DP
    // for verification purposes, could pick any of them.
    private static int convertDeviceType(int type) {
        switch (type) {
            case TYPE_BLUETOOTH_A2DP: // fall through
            case TYPE_BLE_HEADSET: // fall through
            case TYPE_BLE_SPEAKER: // fall through
            case TYPE_BLE_BROADCAST:
                return TYPE_BLUETOOTH_A2DP;
            case TYPE_BUILTIN_SPEAKER: // fall through
            case TYPE_WIRED_HEADSET: // fall through
            case TYPE_WIRED_HEADPHONES: // fall through
            case TYPE_HDMI: // fall through
            case TYPE_USB_ACCESSORY: // fall through
            case TYPE_USB_DEVICE: // fall through
            case TYPE_USB_HEADSET: // fall through
            case TYPE_AUX_LINE: // fall through
            case TYPE_BUS:
            default:
                return type;
        }
    }
}
