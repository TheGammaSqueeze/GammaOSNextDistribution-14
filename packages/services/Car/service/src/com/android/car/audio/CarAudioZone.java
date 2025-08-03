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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneProto;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class encapsulates an audio zone in car.
 *
 * An audio zone can contain multiple {@link CarAudioZoneConfig}s, and each zone has its own
 * {@link CarAudioFocus} instance. Additionally, there may be dedicated hardware volume keys
 * attached to each zone.
 *
 * See also the unified car_audio_configuration.xml
 */
public class CarAudioZone {

    private final int mId;
    private final String mName;
    private final CarAudioContext mCarAudioContext;
    private final List<AudioDeviceAttributes> mInputAudioDevice;
    // zone configuration id to zone configuration mapping
    // We don't protect mCarAudioZoneConfigs by a lock because it's only written at XML parsing.
    private final SparseArray<CarAudioZoneConfig> mCarAudioZoneConfigs;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private int mCurrentConfigId;

    CarAudioZone(CarAudioContext carAudioContext, String name, int id) {
        mCarAudioContext = Objects.requireNonNull(carAudioContext,
                "Car audio context can not be null");
        mName = name;
        mId = id;
        mCurrentConfigId = 0;
        mInputAudioDevice = new ArrayList<>();
        mCarAudioZoneConfigs = new SparseArray<>();
    }

    private int getCurrentConfigId() {
        synchronized (mLock) {
            return mCurrentConfigId;
        }
    }

    int getId() {
        return mId;
    }

    String getName() {
        return mName;
    }

    boolean isPrimaryZone() {
        return mId == CarAudioManager.PRIMARY_AUDIO_ZONE;
    }

    CarAudioZoneConfig getCurrentCarAudioZoneConfig() {
        synchronized (mLock) {
            return mCarAudioZoneConfigs.get(mCurrentConfigId);
        }
    }

    @Nullable
    CarAudioZoneConfigInfo getDefaultAudioZoneConfigInfo() {
        for (int c = 0; c < mCarAudioZoneConfigs.size(); c++) {
            if (!mCarAudioZoneConfigs.valueAt(c).isDefault()) {
                continue;
            }
            return mCarAudioZoneConfigs.valueAt(c).getCarAudioZoneConfigInfo();
        }
        // Should not be able to get here, for fully validated configuration.
        Slogf.wtf(CarLog.TAG_AUDIO, "Audio zone " + mId
                + " does not have a default configuration");
        return null;
    }

    List<CarAudioZoneConfig> getAllCarAudioZoneConfigs() {
        List<CarAudioZoneConfig> zoneConfigList = new ArrayList<>(mCarAudioZoneConfigs.size());
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            zoneConfigList.add(mCarAudioZoneConfigs.valueAt(index));
        }
        return zoneConfigList;
    }

    @Nullable
    CarVolumeGroup getCurrentVolumeGroup(String groupName) {
        return getCurrentCarAudioZoneConfig().getVolumeGroup(groupName);
    }

    CarVolumeGroup getCurrentVolumeGroup(int groupId) {
        return getCurrentCarAudioZoneConfig().getVolumeGroup(groupId);
    }

    /**
     * @return Snapshot of available {@link AudioDeviceInfo}s in List.
     */
    List<AudioDeviceAttributes> getCurrentAudioDevices() {
        return getCurrentCarAudioZoneConfig().getAudioDevice();
    }

    List<AudioDeviceAttributes> getCurrentAudioDeviceSupportingDynamicMix() {
        return getCurrentCarAudioZoneConfig().getAudioDeviceSupportingDynamicMix();
    }

    int getCurrentVolumeGroupCount() {
        return getCurrentCarAudioZoneConfig().getVolumeGroupCount();
    }

    /**
     * @return Snapshot of available {@link CarVolumeGroup}s in array.
     */
    CarVolumeGroup[] getCurrentVolumeGroups() {
        return getCurrentCarAudioZoneConfig().getVolumeGroups();
    }

    boolean validateCanUseDynamicMixRouting(boolean useCoreAudioRouting) {
        return getCurrentCarAudioZoneConfig().validateCanUseDynamicMixRouting(useCoreAudioRouting);
    }

    /**
     * Constraints applied here:
     *
     * <ul>
     * <li>At least one zone configuration exists.
     * <li>Current zone configuration exists.
     * <li>The zone id of all zone configurations matches zone id of the zone.
     * <li>Exactly one zone configuration is default.
     * <li>Volume groups for each zone configuration is valid (see
     * {@link CarAudioZoneConfig#validateVolumeGroups(CarAudioContext, boolean)}).
     * </ul>
     */
    boolean validateZoneConfigs(boolean useCoreAudioRouting) {
        if (mCarAudioZoneConfigs.size() == 0) {
            Slogf.w(CarLog.TAG_AUDIO, "No zone configurations for zone %d", mId);
            return false;
        }
        int currentConfigId = getCurrentConfigId();
        if (!mCarAudioZoneConfigs.contains(currentConfigId)) {
            Slogf.w(CarLog.TAG_AUDIO, "Current zone configuration %d for zone %d does not exist",
                    currentConfigId, mId);
            return false;
        }
        boolean isDefaultConfigFound = false;
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            CarAudioZoneConfig zoneConfig = mCarAudioZoneConfigs.valueAt(index);
            if (zoneConfig.getZoneId() != mId) {
                Slogf.w(CarLog.TAG_AUDIO,
                        "Zone id %d of zone configuration %d does not match zone id %d",
                        zoneConfig.getZoneId(),
                        mCarAudioZoneConfigs.keyAt(index), mId);
                return false;
            }
            if (zoneConfig.isDefault()) {
                if (isDefaultConfigFound) {
                    Slogf.w(CarLog.TAG_AUDIO,
                            "Multiple default zone configurations exist in zone %d", mId);
                    return false;
                }
                isDefaultConfigFound = true;
            }
            if (!zoneConfig.validateVolumeGroups(mCarAudioContext,
                    useCoreAudioRouting)) {
                return false;
            }
        }
        if (!isDefaultConfigFound) {
            Slogf.w(CarLog.TAG_AUDIO, "No default zone configuration exists in zone %d", mId);
            return false;
        }
        return true;
    }

    boolean isCurrentZoneConfig(CarAudioZoneConfigInfo configInfoSwitchedTo) {
        synchronized (mLock) {
            return configInfoSwitchedTo.equals(mCarAudioZoneConfigs.get(mCurrentConfigId)
                    .getCarAudioZoneConfigInfo());
        }
    }

    void setCurrentCarZoneConfig(CarAudioZoneConfigInfo configInfoSwitchedTo) {
        synchronized (mLock) {
            if (mCurrentConfigId == configInfoSwitchedTo.getConfigId()) {
                return;
            }
            CarAudioZoneConfig previousConfig = mCarAudioZoneConfigs.get(mCurrentConfigId);
            previousConfig.setIsSelected(false);
            mCurrentConfigId = configInfoSwitchedTo.getConfigId();
            CarAudioZoneConfig current = mCarAudioZoneConfigs.get(mCurrentConfigId);
            current.setIsSelected(true);
            current.updateVolumeDevices(mCarAudioContext.useCoreAudioRouting());
        }
    }

    void init() {
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            CarAudioZoneConfig config = mCarAudioZoneConfigs.valueAt(index);
            config.synchronizeCurrentGainIndex();
            // mCurrentConfigId should be the default config, but this may change in the future
            // The configuration could be loaded from audio settings instead
            if (!config.isDefault()) {
                continue;
            }
            synchronized (mLock) {
                mCurrentConfigId = config.getZoneConfigId();
            }
            config.setIsSelected(true);
            config.updateVolumeDevices(mCarAudioContext.useCoreAudioRouting());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("CarAudioZone(%s:%d) isPrimary? %b\n", mName, mId,
                isPrimaryZone());
        writer.increaseIndent();
        writer.printf("Current Config Id: %d\n", getCurrentConfigId());
        writer.printf("Input Audio Device Addresses\n");
        writer.increaseIndent();
        for (int index = 0; index < mInputAudioDevice.size(); index++) {
            writer.printf("Device Address(%s)\n", mInputAudioDevice.get(index).getAddress());
        }
        writer.decreaseIndent();
        writer.println();
        writer.printf("Audio Zone Configurations[%d]\n", mCarAudioZoneConfigs.size());
        writer.increaseIndent();
        for (int i = 0; i < mCarAudioZoneConfigs.size(); i++) {
            mCarAudioZoneConfigs.valueAt(i).dump(writer);
        }
        writer.decreaseIndent();
        writer.println();
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long carAudioZonesToken = proto.start(CarAudioDumpProto.CAR_AUDIO_ZONES);
        proto.write(CarAudioZoneProto.NAME, mName);
        proto.write(CarAudioZoneProto.ID, mId);
        proto.write(CarAudioZoneProto.PRIMARY_ZONE, isPrimaryZone());
        proto.write(CarAudioZoneProto.CURRENT_ZONE_CONFIG_ID, getCurrentConfigId());
        for (int index = 0; index < mInputAudioDevice.size(); index++) {
            proto.write(CarAudioZoneProto.INPUT_AUDIO_DEVICE_ADDRESSES,
                    mInputAudioDevice.get(index).getAddress());
        }
        for (int i = 0; i < mCarAudioZoneConfigs.size(); i++) {
            mCarAudioZoneConfigs.valueAt(i).dumpProto(proto);
        }
        proto.end(carAudioZonesToken);
    }

    /**
     * Return the audio device address mapping to a car audio context
     */
    public String getAddressForContext(int audioContext) {
        mCarAudioContext.preconditionCheckAudioContext(audioContext);
        String deviceAddress = null;
        for (CarVolumeGroup volumeGroup : getCurrentVolumeGroups()) {
            deviceAddress = volumeGroup.getAddressForContext(audioContext);
            if (deviceAddress != null) {
                return deviceAddress;
            }
        }
        // This should not happen unless something went wrong.
        // Device address are unique per zone and all contexts are assigned in a zone.
        throw new IllegalStateException("Could not find output device in zone " + mId
                + " for audio context " + audioContext);
    }

    AudioDeviceAttributes getAudioDeviceForContext(int audioContext) {
        mCarAudioContext.preconditionCheckAudioContext(audioContext);
        for (CarVolumeGroup volumeGroup : getCurrentVolumeGroups()) {
            AudioDeviceAttributes audioDeviceAttributes =
                    volumeGroup.getAudioDeviceForContext(audioContext);
            if (audioDeviceAttributes != null) {
                return audioDeviceAttributes;
            }
        }
        // This should not happen unless something went wrong.
        // Device address are unique per zone and all contexts are assigned in a zone.
        throw new IllegalStateException("Could not find output device in zone " + mId
                + " for audio context " + audioContext);
    }

    /**
     * Update the volume groups for the new user
     * @param userId user id to update to
     */
    public void updateVolumeGroupsSettingsForUser(int userId) {
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            mCarAudioZoneConfigs.valueAt(index).updateVolumeGroupsSettingsForUser(userId);
        }
    }

    void addInputAudioDevice(AudioDeviceAttributes device) {
        mInputAudioDevice.add(device);
    }

    List<AudioDeviceAttributes> getInputAudioDevices() {
        return mInputAudioDevice;
    }

    void addZoneConfig(CarAudioZoneConfig zoneConfig) {
        mCarAudioZoneConfigs.put(zoneConfig.getZoneConfigId(), zoneConfig);
        if (zoneConfig.isDefault()) {
            synchronized (mLock) {
                mCurrentConfigId = zoneConfig.getZoneConfigId();
            }
        }
    }

    public List<AudioAttributes> findActiveAudioAttributesFromPlaybackConfigurations(
            List<AudioPlaybackConfiguration> configurations) {
        Objects.requireNonNull(configurations, "Audio playback configurations can not be null");
        List<AudioAttributes> audioAttributes = new ArrayList<>();
        for (int index = 0; index < configurations.size(); index++) {
            AudioPlaybackConfiguration configuration = configurations.get(index);
            if (configuration.isActive()) {
                if (isAudioDeviceInfoValidForZone(configuration.getAudioDeviceInfo())) {
                    // Note that address's context and the context actually supplied could be
                    // different
                    audioAttributes.add(configuration.getAudioAttributes());
                }
            }
        }
        return audioAttributes;
    }

    boolean isAudioDeviceInfoValidForZone(AudioDeviceInfo info) {
        return getCurrentCarAudioZoneConfig().isAudioDeviceInfoValidForZone(info);
    }

    @Nullable
    CarVolumeGroup getVolumeGroupForAudioAttributes(AudioAttributes audioAttributes) {
        return getCurrentCarAudioZoneConfig().getVolumeGroupForAudioAttributes(audioAttributes);
    }

    List<CarVolumeGroupEvent> onAudioGainChanged(List<Integer> halReasons,
            List<CarAudioGainConfigInfo> gainInfos) {
        List<CarVolumeGroupEvent> events = new ArrayList<>();
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            List<CarVolumeGroupEvent> eventsForZoneConfig = mCarAudioZoneConfigs.valueAt(index)
                    .onAudioGainChanged(halReasons, gainInfos);
            // use events for callback only if current zone configuration
            if (mCarAudioZoneConfigs.keyAt(index) == getCurrentConfigId()) {
                events.addAll(eventsForZoneConfig);
            }
        }
        return events;
    }

    List<CarVolumeGroupEvent> onAudioPortsChanged(List<HalAudioDeviceInfo> deviceInfos) {
        List<CarVolumeGroupEvent> events = new ArrayList<>();
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            List<CarVolumeGroupEvent> eventsForZoneConfig = mCarAudioZoneConfigs.valueAt(index)
                    .onAudioPortsChanged(deviceInfos);
            // Use events for callback only if current zone configuration
            if (mCarAudioZoneConfigs.keyAt(index) == getCurrentConfigId()) {
                events.addAll(eventsForZoneConfig);
            }
        }
        return events;
    }

    /**
     * Returns the car audio context set for the car audio zone
     */
    public CarAudioContext getCarAudioContext() {
        return mCarAudioContext;
    }

    /**
     * Returns the car volume infos for all the volume groups in the audio zone
     */
    List<CarVolumeGroupInfo> getCurrentVolumeGroupInfos() {
        return getCurrentCarAudioZoneConfig().getVolumeGroupInfos();
    }

    /**
     * Returns all audio zone config info in the audio zone
     */
    List<CarAudioZoneConfigInfo> getCarAudioZoneConfigInfos() {
        List<CarAudioZoneConfigInfo> zoneConfigInfos = new ArrayList<>(mCarAudioZoneConfigs.size());
        for (int index = 0; index < mCarAudioZoneConfigs.size(); index++) {
            zoneConfigInfos.add(mCarAudioZoneConfigs.valueAt(index).getCarAudioZoneConfigInfo());
        }

        return zoneConfigInfos;
    }

    boolean audioDevicesAdded(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        boolean updated = false;
        for (int c = 0; c < mCarAudioZoneConfigs.size(); c++) {
            if (!mCarAudioZoneConfigs.valueAt(c).audioDevicesAdded(devices)) {
                continue;
            }
            updated = true;
        }
        return updated;
    }

    boolean audioDevicesRemoved(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        boolean updated = false;
        for (int c = 0; c < mCarAudioZoneConfigs.size(); c++) {
            if (!mCarAudioZoneConfigs.valueAt(c).audioDevicesRemoved(devices)) {
                continue;
            }
            updated = true;
        }
        return updated;
    }
}
