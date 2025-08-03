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

import static android.car.media.CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.car.oem.CarAudioFadeConfiguration;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneConfigProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneProto;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A class encapsulates the configuration of an audio zone in car.
 *
 * An audio zone config can contain multiple {@link CarVolumeGroup}s.
 *
 * See also the unified car_audio_configuration.xml
 */
final class CarAudioZoneConfig {

    private static final int INVALID_GROUP_ID = -1;
    private static final int INVALID_EVENT_TYPE = 0;
    private final int mZoneId;
    private final int mZoneConfigId;
    private final String mName;
    private final boolean mIsDefault;
    private final List<CarVolumeGroup> mVolumeGroups;
    private final List<String> mGroupIdToNames;
    private final Map<String, Integer> mDeviceAddressToGroupId;
    private final CarAudioFadeConfiguration mDefaultCarAudioFadeConfiguration;
    private final Map<AudioAttributes,
            CarAudioFadeConfiguration> mAudioAttributesToCarAudioFadeConfiguration;
    private final boolean mIsFadeManagerConfigurationEnabled;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mIsSelected;

    private CarAudioZoneConfig(String name, int zoneId, int zoneConfigId, boolean isDefault,
            List<CarVolumeGroup> volumeGroups, Map<String, Integer> deviceAddressToGroupId,
            List<String> groupIdToNames, boolean isFadeManagerConfigEnabled,
            CarAudioFadeConfiguration defaultCarAudioFadeConfiguration,
            Map<AudioAttributes, CarAudioFadeConfiguration> attrToCarAudioFadeConfiguration) {
        mName = name;
        mZoneId = zoneId;
        mZoneConfigId = zoneConfigId;
        mIsDefault = isDefault;
        mVolumeGroups = volumeGroups;
        mDeviceAddressToGroupId = deviceAddressToGroupId;
        mGroupIdToNames = groupIdToNames;
        mIsSelected = false;
        mIsFadeManagerConfigurationEnabled = isFadeManagerConfigEnabled;
        mDefaultCarAudioFadeConfiguration = defaultCarAudioFadeConfiguration;
        mAudioAttributesToCarAudioFadeConfiguration = attrToCarAudioFadeConfiguration;
    }

    int getZoneId() {
        return mZoneId;
    }

    int getZoneConfigId() {
        return mZoneConfigId;
    }

    String getName() {
        return mName;
    }

    boolean isDefault() {
        return mIsDefault;
    }

    boolean isSelected() {
        synchronized (mLock) {
            return mIsSelected;
        }
    }

    void setIsSelected(boolean isSelected) {
        synchronized (mLock) {
            mIsSelected = isSelected;
        }
    }

    @Nullable
    CarVolumeGroup getVolumeGroup(String groupName) {
        int groupId = mGroupIdToNames.indexOf(groupName);
        if (groupId < 0) {
            return null;
        }
        return getVolumeGroup(groupId);
    }

    CarVolumeGroup getVolumeGroup(int groupId) {
        Preconditions.checkArgumentInRange(groupId, 0, mVolumeGroups.size() - 1,
                "groupId(" + groupId + ") is out of range");
        return mVolumeGroups.get(groupId);
    }

    /**
     * @return Snapshot of available {@link AudioDeviceAttributes}s in List.
     */
    List<AudioDeviceAttributes> getAudioDevice() {
        final List<AudioDeviceAttributes> devices = new ArrayList<>();
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            List<String> addresses = group.getAddresses();
            for (int addressIndex = 0; addressIndex < addresses.size(); addressIndex++) {
                devices.add(group.getCarAudioDeviceInfoForAddress(addresses.get(addressIndex))
                        .getAudioDevice());
            }
        }
        return devices;
    }

    List<AudioDeviceAttributes> getAudioDeviceSupportingDynamicMix() {
        List<AudioDeviceAttributes> devices = new ArrayList<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            List<String> addresses = group.getAddresses();
            for (int addressIndex = 0; addressIndex < addresses.size(); addressIndex++) {
                String address = addresses.get(addressIndex);
                CarAudioDeviceInfo info = group.getCarAudioDeviceInfoForAddress(address);
                if (info.canBeRoutedWithDynamicPolicyMix()) {
                    devices.add(info.getAudioDevice());
                }
            }
        }
        return devices;
    }

    int getVolumeGroupCount() {
        return mVolumeGroups.size();
    }

    /**
     * @return Snapshot of available {@link CarVolumeGroup}s in array.
     */
    CarVolumeGroup[] getVolumeGroups() {
        return mVolumeGroups.toArray(new CarVolumeGroup[0]);
    }

    /**
     * Constraints applied here for checking usage of Dynamic Mixes for routing:
     *
     * - One context with same AudioAttributes usage shall not be routed to 2 different devices
     * (Dynamic Mixes supports only match on usage, not on other AudioAttributes fields.
     *
     * - One address shall not appear in 2 groups. CarAudioService cannot establish Dynamic Routing
     * rules that address multiple groups.
     */
    boolean validateCanUseDynamicMixRouting(boolean useCoreAudioRouting) {
        ArraySet<String> addresses = new ArraySet<>();
        SparseArray<CarAudioDeviceInfo> usageToDevice = new SparseArray<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);

            List<String> groupAddresses = group.getAddresses();
            // Due to AudioPolicy Dynamic Mixing limitation, rules can be made only on usage and
            // not on audio attributes.
            // When using product strategies, AudioPolicy may not simply route on usage match.
            // Prevent using dynamic mixes if supporting Core Routing.
            for (int addressIndex = 0; addressIndex < groupAddresses.size(); addressIndex++) {
                String address = groupAddresses.get(addressIndex);
                CarAudioDeviceInfo info = group.getCarAudioDeviceInfoForAddress(address);
                List<Integer> usagesForAddress = group.getAllSupportedUsagesForAddress(address);

                if (!addresses.add(address) && !useCoreAudioRouting) {
                    Slogf.w(CarLog.TAG_AUDIO, "Address %s appears in two groups, prevents"
                            + " from using dynamic policy mixes for routing" , address);
                    return false;
                }
                for (int usageIndex = 0; usageIndex < usagesForAddress.size(); usageIndex++) {
                    int usage = usagesForAddress.get(usageIndex);
                    CarAudioDeviceInfo infoForAttr = usageToDevice.get(usage);
                    if (infoForAttr != null && !infoForAttr.getAddress().equals(address)) {
                        Slogf.e(CarLog.TAG_AUDIO, "Addresses %s and %s can be reached with same"
                                        + " usage %s, prevent from using dynamic policy mixes.",
                                infoForAttr.getAddress(), address,
                                AudioManagerHelper.usageToXsdString(usage));
                        if (useCoreAudioRouting) {
                            infoForAttr.resetCanBeRoutedWithDynamicPolicyMix();
                        } else {
                            return false;
                        }
                    } else {
                        usageToDevice.put(usage, info);
                    }
                }
                if (useCoreAudioRouting) {
                    info.resetCanBeRoutedWithDynamicPolicyMix();
                }
            }
        }
        return true;
    }

    /**
     * Constraints applied here:
     * <ul>
     * <li>One context should not appear in two groups if not relying on Core Audio for Volume
     * management. When using core Audio, mutual exclusive contexts may reach same devices,
     * AudioPolicyManager will apply the corresponding gain when the context is active on the common
     * device</li>
     * <li>All contexts are assigned</li>
     * <li>One device should not appear in two groups</li>
     * <li>All gain controllers in the same group have same step value</li>
     * <li>Device types can not repeat for multiple volume groups in a configuration, see
     * {@link CarVolumeGroup#validateDeviceTypes(Set)} for further information.
     * When using core audio routing, device types is not considered</li>
     * <li>Dynamic device types can only appear alone in volume group, see
     * {@link CarVolumeGroup#validateDeviceTypes(Set)} for further information.
     * When using core audio routing device types is not considered</li>
     * </ul>
     *
     * <p>Note that it is fine that there are devices which do not appear in any group.
     * Those devices may be reserved for other purposes. Step value validation is done in
     * {@link CarVolumeGroupFactory#setDeviceInfoForContext(int, CarAudioDeviceInfo)}
     */
    boolean validateVolumeGroups(CarAudioContext carAudioContext, boolean useCoreAudioRouting) {
        ArraySet<Integer> contexts = new ArraySet<>();
        ArraySet<String> addresses = new ArraySet<>();
        ArraySet<Integer> dynamicDeviceTypesInConfig = new ArraySet<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            // One context should not appear in two groups
            int[] groupContexts = group.getContexts();
            for (int groupIndex = 0; groupIndex < groupContexts.length; groupIndex++) {
                int contextId = groupContexts[groupIndex];
                if (!contexts.add(contextId)) {
                    Slogf.e(CarLog.TAG_AUDIO, "Context %d appears in two groups", contextId);
                    return false;
                }
            }
            // One address should not appear in two groups
            List<String> groupAddresses = group.getAddresses();
            for (int addressIndex = 0; addressIndex < groupAddresses.size(); addressIndex++) {
                String address = groupAddresses.get(addressIndex);
                if (!addresses.add(address)) {
                    if (useCoreAudioRouting) {
                        continue;
                    }
                    Slogf.w(CarLog.TAG_AUDIO, "Address appears in two groups: " + address);
                    return false;
                }
            }
            if (!useCoreAudioRouting && !group.validateDeviceTypes(dynamicDeviceTypesInConfig)) {
                Slogf.w(CarLog.TAG_AUDIO, "Failed to validate device types for config "
                        + getName());
                return false;
            }
        }

        List<Integer> allContexts = carAudioContext.getAllContextsIds();
        for (int index = 0; index < allContexts.size(); index++) {
            if (!contexts.contains(allContexts.get(index))) {
                Slogf.e(CarLog.TAG_AUDIO, "Audio context %s is not assigned to a group",
                        carAudioContext.toString(allContexts.get(index)));
                return false;
            }
        }

        List<Integer> contextList = new ArrayList<>(contexts);
        // All contexts are assigned
        if (!carAudioContext.validateAllAudioAttributesSupported(contextList)) {
            Slogf.e(CarLog.TAG_AUDIO, "Some audio attributes are not assigned to a group");
            return false;
        }
        return true;
    }

    void synchronizeCurrentGainIndex() {
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            // Synchronize the internal state
            group.setCurrentGainIndex(group.getCurrentGainIndex());
        }
    }

    boolean isFadeManagerConfigurationEnabled() {
        return mIsFadeManagerConfigurationEnabled;
    }

    @Nullable
    CarAudioFadeConfiguration getDefaultCarAudioFadeConfiguration() {
        return mDefaultCarAudioFadeConfiguration;
    }

    @Nullable
    CarAudioFadeConfiguration getCarAudioFadeConfigurationForAudioAttributes(
            AudioAttributes audioAttributes) {
        Objects.requireNonNull(audioAttributes, "Audio attributes cannot be null");
        return mAudioAttributesToCarAudioFadeConfiguration.get(audioAttributes);
    }

    Map<AudioAttributes, CarAudioFadeConfiguration> getAllTransientCarAudioFadeConfigurations() {
        return mAudioAttributesToCarAudioFadeConfiguration;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("CarAudioZoneConfig(%s:%d) of zone %d isDefault? %b\n", mName, mZoneConfigId,
                mZoneId, mIsDefault);
        writer.increaseIndent();
        writer.printf("Is active (%b)\n", isActive());
        writer.printf("Is selected (%b)\n", isSelected());
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            mVolumeGroups.get(index).dump(writer);
        }
        writer.printf("Is fade manager configuration enabled: %b\n",
                isFadeManagerConfigurationEnabled());
        if (isFadeManagerConfigurationEnabled()) {
            writer.printf("Default car audio fade manager config name: %s\n",
                    mDefaultCarAudioFadeConfiguration.getName());
            writer.printf("Transient car audio fade manager configurations: %d\n",
                    mAudioAttributesToCarAudioFadeConfiguration.size());
            writer.increaseIndent();
            for (Map.Entry<AudioAttributes, CarAudioFadeConfiguration> entry :
                    mAudioAttributesToCarAudioFadeConfiguration.entrySet()) {
                writer.printf("Name: " + entry.getValue().getName()
                        + ", Audio attribute: " + entry.getKey() + "\n");
            }
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long zoneConfigToken = proto.start(CarAudioZoneProto.ZONE_CONFIGS);
        proto.write(CarAudioZoneConfigProto.NAME, mName);
        proto.write(CarAudioZoneConfigProto.ID, mZoneConfigId);
        proto.write(CarAudioZoneConfigProto.ZONE_ID, mZoneId);
        proto.write(CarAudioZoneConfigProto.DEFAULT, mIsDefault);
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            mVolumeGroups.get(index).dumpProto(proto);
        }
        proto.write(CarAudioZoneConfigProto.IS_ACTIVE, isActive());
        proto.write(CarAudioZoneConfigProto.IS_SELECTED, isSelected());
        proto.write(CarAudioZoneConfigProto.IS_FADE_MANAGER_CONFIG_ENABLED,
                isFadeManagerConfigurationEnabled());
        if (isFadeManagerConfigurationEnabled()) {
            CarAudioProtoUtils.dumpCarAudioFadeConfigurationProto(mDefaultCarAudioFadeConfiguration,
                    CarAudioZoneConfigProto.DEFAULT_CAR_AUDIO_FADE_CONFIGURATION, proto);
            dumpAttributeToCarAudioFadeConfigProto(proto);
        }
        proto.end(zoneConfigToken);
    }

    private void dumpAttributeToCarAudioFadeConfigProto(ProtoOutputStream proto) {
        for (Map.Entry<AudioAttributes, CarAudioFadeConfiguration> entry :
                mAudioAttributesToCarAudioFadeConfiguration.entrySet()) {
            long token = proto.start(CarAudioZoneConfigProto.ATTR_TO_CAR_AUDIO_FADE_CONFIGURATION);
            CarAudioProtoUtils.dumpCarAudioAttributesProto(entry.getKey(), CarAudioZoneConfigProto
                    .AttrToCarAudioFadeConfiguration.ATTRIBUTES, proto);
            CarAudioProtoUtils.dumpCarAudioFadeConfigurationProto(entry.getValue(),
                    CarAudioZoneConfigProto.AttrToCarAudioFadeConfiguration
                            .CAR_AUDIO_FADE_CONFIGURATION, proto);
            proto.end(token);
        }
    }

    /**
     * Update the volume groups for the new user
     * @param userId user id to update to
     */
    void updateVolumeGroupsSettingsForUser(int userId) {
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            mVolumeGroups.get(index).loadVolumesSettingsForUser(userId);
        }
    }

    boolean isAudioDeviceInfoValidForZone(AudioDeviceInfo info) {
        return info != null
                && info.getAddress() != null
                && !info.getAddress().isEmpty()
                && containsDeviceAddress(info.getAddress());
    }

    @Nullable
    CarVolumeGroup getVolumeGroupForAudioAttributes(AudioAttributes audioAttributes) {
        for (int i = 0; i < mVolumeGroups.size(); i++) {
            if (mVolumeGroups.get(i).hasAudioAttributes(audioAttributes)) {
                return mVolumeGroups.get(i);
            }
        }
        return null;
    }

    private boolean containsDeviceAddress(String deviceAddress) {
        return mDeviceAddressToGroupId.containsKey(deviceAddress);
    }

    List<CarVolumeGroupEvent> onAudioGainChanged(List<Integer> halReasons,
            List<CarAudioGainConfigInfo> gainInfos) {
        // [key, value] -> [groupId, eventType]
        SparseIntArray groupIdsToEventType = new SparseIntArray();
        List<Integer> extraInfos = CarAudioGainMonitor.convertReasonsToExtraInfo(halReasons);

        // update volume-groups
        for (int index = 0; index < gainInfos.size(); index++) {
            CarAudioGainConfigInfo gainInfo = gainInfos.get(index);
            int groupId = mDeviceAddressToGroupId.getOrDefault(gainInfo.getDeviceAddress(),
                    INVALID_GROUP_ID);
            if (groupId == INVALID_GROUP_ID) {
                continue;
            }

            int eventType = mVolumeGroups.get(groupId).onAudioGainChanged(halReasons, gainInfo);
            if (eventType == INVALID_EVENT_TYPE) {
                continue;
            }
            if (groupIdsToEventType.get(groupId, INVALID_GROUP_ID) != INVALID_GROUP_ID) {
                eventType |= groupIdsToEventType.get(groupId);
            }
            groupIdsToEventType.put(groupId, eventType);
        }

        // generate events for updated groups
        List<CarVolumeGroupEvent> events = new ArrayList<>(groupIdsToEventType.size());
        for (int index = 0; index < groupIdsToEventType.size(); index++) {
            CarVolumeGroupEvent.Builder eventBuilder = new CarVolumeGroupEvent.Builder(List.of(
                    mVolumeGroups.get(groupIdsToEventType.keyAt(index)).getCarVolumeGroupInfo()),
                    groupIdsToEventType.valueAt(index));
            // ensure we have valid extra-infos
            if (!extraInfos.isEmpty()) {
                eventBuilder.setExtraInfos(extraInfos);
            }
            events.add(eventBuilder.build());
        }
        return events;
    }

    /**
     * @return The car volume infos for all the volume groups in the audio zone config
     */
    List<CarVolumeGroupInfo> getVolumeGroupInfos() {
        List<CarVolumeGroupInfo> groupInfos = new ArrayList<>(mVolumeGroups.size());
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            groupInfos.add(mVolumeGroups.get(index).getCarVolumeGroupInfo());
        }

        return groupInfos;
    }

    /**
     * Returns the car audio zone config info
     */
    CarAudioZoneConfigInfo getCarAudioZoneConfigInfo() {
        if (Flags.carAudioDynamicDevices()) {
            return new CarAudioZoneConfigInfo.Builder(mName, mZoneId, mZoneConfigId)
                    .setConfigVolumeGroups(getVolumeGroupInfos()).setIsActive(isActive())
                    .setIsSelected(isSelected()).setIsDefault(isDefault()).build();
        }
        // Keep legacy code till the flags becomes permanent
        return new CarAudioZoneConfigInfo(mName, mZoneId, mZoneConfigId);
    }

    boolean isActive() {
        for (int c = 0; c < mVolumeGroups.size(); c++) {
            if (mVolumeGroups.get(c).isActive()) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * For the list of {@link HalAudioDeviceInfo}, update respective {@link CarAudioDeviceInfo}.
     * If the volume group has new gains (min/max/default/current), add a
     * {@link CarVolumeGroupEvent}
     */
    List<CarVolumeGroupEvent> onAudioPortsChanged(List<HalAudioDeviceInfo> deviceInfos) {
        List<CarVolumeGroupEvent> events = new ArrayList<>();
        ArraySet<Integer> updatedGroupIds = new ArraySet<>();

        // iterate through the incoming hal device infos and update the respective groups
        // car audio device infos
        for (int index = 0; index < deviceInfos.size(); index++) {
            HalAudioDeviceInfo deviceInfo = deviceInfos.get(index);
            int groupId = mDeviceAddressToGroupId.getOrDefault(deviceInfo.getAddress(),
                    INVALID_GROUP_ID);
            if (groupId == INVALID_GROUP_ID) {
                continue;
            }
            mVolumeGroups.get(groupId).updateAudioDeviceInfo(deviceInfo);
            updatedGroupIds.add(groupId);
        }

        // for the updated groups, recalculate the gain stages. If new gain stage, create
        // an event to callback
        for (int index = 0; index < updatedGroupIds.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(updatedGroupIds.valueAt(index));
            int eventType = group.calculateNewGainStageFromDeviceInfos();
            if (eventType != INVALID_EVENT_TYPE) {
                events.add(new CarVolumeGroupEvent.Builder(List.of(group.getCarVolumeGroupInfo()),
                        eventType, List.of(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM))
                        .build());
            }
        }
        return events;
    }

    boolean audioDevicesAdded(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        // Consider that this may change in the future when multiple devices are supported
        // per device type. When that happens we may need a way determine where the devices
        // should be attached. The same pattern is followed in the method called from here on
        if (isActive()) {
            return false;
        }
        boolean updated = false;
        for (int c = 0; c < mVolumeGroups.size(); c++) {
            if (!mVolumeGroups.get(c).audioDevicesAdded(devices)) {
                continue;
            }
            updated = true;
        }
        return updated;
    }

    boolean audioDevicesRemoved(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        boolean updated = false;
        for (int c = 0; c < mVolumeGroups.size(); c++) {
            if (!mVolumeGroups.get(c).audioDevicesRemoved(devices)) {
                continue;
            }
            updated = true;
        }
        return updated;
    }

    void updateVolumeDevices(boolean useCoreAudioRouting) {
        for (int c = 0; c < mVolumeGroups.size(); c++) {
            mVolumeGroups.get(c).updateDevices(useCoreAudioRouting);
        }
    }

    static final class Builder {
        private final int mZoneId;
        private final int mZoneConfigId;
        private final String mName;
        private final boolean mIsDefault;
        private final List<CarVolumeGroup> mVolumeGroups = new ArrayList<>();
        private final Map<String, Integer> mDeviceAddressToGroupId = new ArrayMap<>();
        private final List<String> mGroupIdToNames = new ArrayList<>();
        private final Map<AudioAttributes,
                CarAudioFadeConfiguration> mAudioAttributesToCarAudioFadeConfiguration =
                new ArrayMap<>();
        private CarAudioFadeConfiguration mDefaultCarAudioFadeConfiguration;
        private boolean mIsFadeManagerConfigurationEnabled;

        Builder(String name, int zoneId, int zoneConfigId, boolean isDefault) {
            mName = Objects.requireNonNull(name, "Car audio zone config name cannot be null");
            mZoneId = zoneId;
            mZoneConfigId = zoneConfigId;
            mIsDefault = isDefault;
        }

        Builder addVolumeGroup(CarVolumeGroup volumeGroup) {
            mVolumeGroups.add(volumeGroup);
            mGroupIdToNames.add(volumeGroup.getName());
            addGroupAddressesToMap(volumeGroup.getAddresses(), volumeGroup.getId());
            return this;
        }

        Builder setFadeManagerConfigurationEnabled(boolean enabled) {
            mIsFadeManagerConfigurationEnabled = enabled;
            return this;
        }

        Builder setDefaultCarAudioFadeConfiguration(
                CarAudioFadeConfiguration carAudioFadeConfiguration) {
            mDefaultCarAudioFadeConfiguration = Objects.requireNonNull(carAudioFadeConfiguration,
                    "Car audio fade configuration for default cannot be null");
            return this;
        }

        Builder setCarAudioFadeConfigurationForAudioAttributes(AudioAttributes audioAttributes,
                CarAudioFadeConfiguration carAudioFadeConfiguration) {
            Objects.requireNonNull(audioAttributes, "Audio attributes cannot be null");
            Objects.requireNonNull(carAudioFadeConfiguration,
                    "Car audio fade configuration for audio attributes cannot be null");
            mAudioAttributesToCarAudioFadeConfiguration.put(audioAttributes,
                    carAudioFadeConfiguration);
            return this;
        }

        int getZoneId() {
            return mZoneId;
        }

        int getZoneConfigId() {
            return mZoneConfigId;
        }

        CarAudioZoneConfig build() {
            if (!mIsFadeManagerConfigurationEnabled) {
                mDefaultCarAudioFadeConfiguration = null;
                mAudioAttributesToCarAudioFadeConfiguration.clear();
            }
            return new CarAudioZoneConfig(mName, mZoneId, mZoneConfigId, mIsDefault, mVolumeGroups,
                    mDeviceAddressToGroupId, mGroupIdToNames, mIsFadeManagerConfigurationEnabled,
                    mDefaultCarAudioFadeConfiguration, mAudioAttributesToCarAudioFadeConfiguration);
        }

        private void addGroupAddressesToMap(List<String> addresses, int groupId) {
            for (int index = 0; index < addresses.size(); index++) {
                mDeviceAddressToGroupId.put(addresses.get(index), groupId);
            }
        }
    }
}
