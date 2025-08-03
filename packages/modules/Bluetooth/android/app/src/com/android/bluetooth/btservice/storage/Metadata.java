/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.bluetooth.btservice.storage;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothA2dp.OptionalCodecsPreferenceStatus;
import android.bluetooth.BluetoothA2dp.OptionalCodecsSupportStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUtils;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
@Entity(tableName = "metadata")
@VisibleForTesting
public class Metadata {
    @PrimaryKey
    @NonNull
    private String address;

    public boolean migrated;

    @Embedded
    public ProfilePrioritiesEntity profileConnectionPolicies;

    @Embedded
    @NonNull
    public CustomizedMetadataEntity publicMetadata;

    public @OptionalCodecsSupportStatus int a2dpSupportsOptionalCodecs;
    public @OptionalCodecsPreferenceStatus int a2dpOptionalCodecsEnabled;

    public long last_active_time;
    public boolean is_active_a2dp_device;

    public boolean isActiveHfpDevice;

    @Embedded
    public AudioPolicyEntity audioPolicyMetadata;

    /**
     * The preferred profile to be used for {@link BluetoothDevice#AUDIO_MODE_OUTPUT_ONLY}. This can
     * be either {@link BluetoothProfile#A2DP} or {@link BluetoothProfile#LE_AUDIO}. This value is
     * only used if the remote device supports both A2DP and LE Audio and both transports are
     * connected and active.
     */
    public int preferred_output_only_profile;

    /**
     * The preferred profile to be used for {@link BluetoothDevice#AUDIO_MODE_DUPLEX}. This can
     * be either {@link BluetoothProfile#HEADSET} or {@link BluetoothProfile#LE_AUDIO}. This value
     * is only used if the remote device supports both HFP and LE Audio and both transports are
     * connected and active.
     */
    public int preferred_duplex_profile;

    /** This is used to indicate whether device's active audio policy */
    public int active_audio_device_policy;

    Metadata(String address) {
        this(address, false, false);
    }

    private Metadata(String address, boolean isActiveA2dp, boolean isActiveHfp) {
        this.address = address;
        migrated = false;
        profileConnectionPolicies = new ProfilePrioritiesEntity();
        publicMetadata = new CustomizedMetadataEntity();
        a2dpSupportsOptionalCodecs = BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
        a2dpOptionalCodecsEnabled = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
        last_active_time = MetadataDatabase.sCurrentConnectionNumber++;
        is_active_a2dp_device = isActiveA2dp;
        isActiveHfpDevice = isActiveHfp;
        audioPolicyMetadata = new AudioPolicyEntity();
        preferred_output_only_profile = 0;
        preferred_duplex_profile = 0;
        active_audio_device_policy = BluetoothDevice.ACTIVE_AUDIO_DEVICE_POLICY_DEFAULT;
    }

    static final class Builder {
        final String mAddress;
        boolean mIsActiveA2dpDevice = false;
        boolean mIsActiveHfpDevice = false;

        Builder(String address) {
            mAddress = address;
        }

        Builder setActiveA2dp() {
            mIsActiveA2dpDevice = true;
            return this;
        }

        Builder setActiveHfp() {
            mIsActiveHfpDevice = true;
            return this;
        }

        Metadata build() {
            return new Metadata(mAddress, mIsActiveA2dpDevice, mIsActiveHfpDevice);
        }
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public String getAddress() {
        return address;
    }

    /**
     * Returns the anonymized hardware address. The first three octets will be suppressed for
     * anonymization.
     * <p> For example, "XX:XX:XX:AA:BB:CC".
     *
     * @return Anonymized bluetooth hardware address as string
     */
    @NonNull
    public String getAnonymizedAddress() {
        return BluetoothUtils.toAnonymizedAddress(address);
    }

    void setProfileConnectionPolicy(int profile, int connectionPolicy) {
        // We no longer support BluetoothProfile.PRIORITY_AUTO_CONNECT and are merging it into
        // BluetoothProfile.CONNECTION_POLICY_ALLOWED
        if (connectionPolicy > BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        }

        switch (profile) {
            case BluetoothProfile.A2DP:
                profileConnectionPolicies.a2dp_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.A2DP_SINK:
                profileConnectionPolicies.a2dp_sink_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.HEADSET:
                profileConnectionPolicies.hfp_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                profileConnectionPolicies.hfp_client_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.HID_HOST:
                profileConnectionPolicies.hid_host_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.PAN:
                profileConnectionPolicies.pan_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.PBAP:
                profileConnectionPolicies.pbap_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.PBAP_CLIENT:
                profileConnectionPolicies.pbap_client_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.MAP:
                profileConnectionPolicies.map_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.MAP_CLIENT:
                profileConnectionPolicies.map_client_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.SAP:
                profileConnectionPolicies.sap_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.HEARING_AID:
                profileConnectionPolicies.hearing_aid_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.HAP_CLIENT:
                profileConnectionPolicies.hap_client_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.LE_AUDIO:
                profileConnectionPolicies.le_audio_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.VOLUME_CONTROL:
                profileConnectionPolicies.volume_control_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.CSIP_SET_COORDINATOR:
                profileConnectionPolicies.csip_set_coordinator_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.LE_CALL_CONTROL:
                profileConnectionPolicies.le_call_control_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                profileConnectionPolicies.bass_client_connection_policy = connectionPolicy;
                break;
            case BluetoothProfile.BATTERY:
                profileConnectionPolicies.battery_connection_policy = connectionPolicy;
                break;
            default:
                throw new IllegalArgumentException("invalid profile " + profile);
        }
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public int getProfileConnectionPolicy(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                return profileConnectionPolicies.a2dp_connection_policy;
            case BluetoothProfile.A2DP_SINK:
                return profileConnectionPolicies.a2dp_sink_connection_policy;
            case BluetoothProfile.HEADSET:
                return profileConnectionPolicies.hfp_connection_policy;
            case BluetoothProfile.HEADSET_CLIENT:
                return profileConnectionPolicies.hfp_client_connection_policy;
            case BluetoothProfile.HID_HOST:
                return profileConnectionPolicies.hid_host_connection_policy;
            case BluetoothProfile.PAN:
                return profileConnectionPolicies.pan_connection_policy;
            case BluetoothProfile.PBAP:
                return profileConnectionPolicies.pbap_connection_policy;
            case BluetoothProfile.PBAP_CLIENT:
                return profileConnectionPolicies.pbap_client_connection_policy;
            case BluetoothProfile.MAP:
                return profileConnectionPolicies.map_connection_policy;
            case BluetoothProfile.MAP_CLIENT:
                return profileConnectionPolicies.map_client_connection_policy;
            case BluetoothProfile.SAP:
                return profileConnectionPolicies.sap_connection_policy;
            case BluetoothProfile.HEARING_AID:
                return profileConnectionPolicies.hearing_aid_connection_policy;
            case BluetoothProfile.HAP_CLIENT:
                return profileConnectionPolicies.hap_client_connection_policy;
            case BluetoothProfile.LE_AUDIO:
                return profileConnectionPolicies.le_audio_connection_policy;
            case BluetoothProfile.VOLUME_CONTROL:
                return profileConnectionPolicies.volume_control_connection_policy;
            case BluetoothProfile.CSIP_SET_COORDINATOR:
                return profileConnectionPolicies.csip_set_coordinator_connection_policy;
            case BluetoothProfile.LE_CALL_CONTROL:
                return profileConnectionPolicies.le_call_control_connection_policy;
            case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                return profileConnectionPolicies.bass_client_connection_policy;
            case BluetoothProfile.BATTERY:
                return profileConnectionPolicies.battery_connection_policy;
        }
        return BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
    }

    void setCustomizedMeta(int key, byte[] value) {
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                publicMetadata.manufacturer_name = value;
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                publicMetadata.model_name = value;
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                publicMetadata.software_version = value;
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                publicMetadata.hardware_version = value;
                break;
            case BluetoothDevice.METADATA_COMPANION_APP:
                publicMetadata.companion_app = value;
                break;
            case BluetoothDevice.METADATA_MAIN_ICON:
                publicMetadata.main_icon = value;
                break;
            case BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET:
                publicMetadata.is_untethered_headset = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON:
                publicMetadata.untethered_left_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON:
                publicMetadata.untethered_right_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_ICON:
                publicMetadata.untethered_case_icon = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY:
                publicMetadata.untethered_left_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY:
                publicMetadata.untethered_right_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY:
                publicMetadata.untethered_case_battery = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING:
                publicMetadata.untethered_left_charging = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING:
                publicMetadata.untethered_right_charging = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING:
                publicMetadata.untethered_case_charging = value;
                break;
            case BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI:
                publicMetadata.enhanced_settings_ui_uri = value;
                break;
            case BluetoothDevice.METADATA_DEVICE_TYPE:
                publicMetadata.device_type = value;
                break;
            case BluetoothDevice.METADATA_MAIN_BATTERY:
                publicMetadata.main_battery = value;
                break;
            case BluetoothDevice.METADATA_MAIN_CHARGING:
                publicMetadata.main_charging = value;
                break;
            case BluetoothDevice.METADATA_MAIN_LOW_BATTERY_THRESHOLD:
                publicMetadata.main_low_battery_threshold = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_LOW_BATTERY_THRESHOLD:
                publicMetadata.untethered_left_low_battery_threshold = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_LOW_BATTERY_THRESHOLD:
                publicMetadata.untethered_right_low_battery_threshold = value;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_LOW_BATTERY_THRESHOLD:
                publicMetadata.untethered_case_low_battery_threshold = value;
                break;
            case BluetoothDevice.METADATA_SPATIAL_AUDIO:
                publicMetadata.spatial_audio = value;
                break;
            case BluetoothDevice.METADATA_FAST_PAIR_CUSTOMIZED_FIELDS:
                publicMetadata.fastpair_customized = value;
                break;
            case BluetoothDevice.METADATA_LE_AUDIO:
                publicMetadata.le_audio = value;
                break;
            case BluetoothDevice.METADATA_GMCS_CCCD:
                publicMetadata.gmcs_cccd = value;
                break;
            case BluetoothDevice.METADATA_GTBS_CCCD:
                publicMetadata.gtbs_cccd = value;
                break;
            case BluetoothDevice.METADATA_EXCLUSIVE_MANAGER:
                publicMetadata.exclusive_manager = value;
                break;
        }
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public byte[] getCustomizedMeta(int key) {
        byte[] value = null;
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                value = publicMetadata.manufacturer_name;
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                value = publicMetadata.model_name;
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                value = publicMetadata.software_version;
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                value = publicMetadata.hardware_version;
                break;
            case BluetoothDevice.METADATA_COMPANION_APP:
                value = publicMetadata.companion_app;
                break;
            case BluetoothDevice.METADATA_MAIN_ICON:
                value = publicMetadata.main_icon;
                break;
            case BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET:
                value = publicMetadata.is_untethered_headset;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON:
                value = publicMetadata.untethered_left_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON:
                value = publicMetadata.untethered_right_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_ICON:
                value = publicMetadata.untethered_case_icon;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY:
                value = publicMetadata.untethered_left_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY:
                value = publicMetadata.untethered_right_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY:
                value = publicMetadata.untethered_case_battery;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING:
                value = publicMetadata.untethered_left_charging;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING:
                value = publicMetadata.untethered_right_charging;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING:
                value = publicMetadata.untethered_case_charging;
                break;
            case BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI:
                value = publicMetadata.enhanced_settings_ui_uri;
                break;
            case BluetoothDevice.METADATA_DEVICE_TYPE:
                value = publicMetadata.device_type;
                break;
            case BluetoothDevice.METADATA_MAIN_BATTERY:
                value = publicMetadata.main_battery;
                break;
            case BluetoothDevice.METADATA_MAIN_CHARGING:
                value = publicMetadata.main_charging;
                break;
            case BluetoothDevice.METADATA_MAIN_LOW_BATTERY_THRESHOLD:
                value = publicMetadata.main_low_battery_threshold;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_LOW_BATTERY_THRESHOLD:
                value = publicMetadata.untethered_left_low_battery_threshold;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_LOW_BATTERY_THRESHOLD:
                value = publicMetadata.untethered_right_low_battery_threshold;
                break;
            case BluetoothDevice.METADATA_UNTETHERED_CASE_LOW_BATTERY_THRESHOLD:
                value = publicMetadata.untethered_case_low_battery_threshold;
                break;
            case BluetoothDevice.METADATA_SPATIAL_AUDIO:
                value = publicMetadata.spatial_audio;
                break;
            case BluetoothDevice.METADATA_FAST_PAIR_CUSTOMIZED_FIELDS:
                value = publicMetadata.fastpair_customized;
                break;
            case BluetoothDevice.METADATA_LE_AUDIO:
                value = publicMetadata.le_audio;
                break;
            case BluetoothDevice.METADATA_GMCS_CCCD:
                value = publicMetadata.gmcs_cccd;
                break;
            case BluetoothDevice.METADATA_GTBS_CCCD:
                value = publicMetadata.gtbs_cccd;
                break;
            case BluetoothDevice.METADATA_EXCLUSIVE_MANAGER:
                value = publicMetadata.exclusive_manager;
                break;
        }
        return value;
    }

    List<Integer> getChangedCustomizedMeta() {
        List<Integer> list = new ArrayList<>();
        for (int key = 0; key <= BluetoothDevice.getMaxMetadataKey(); key++) {
            if (getCustomizedMeta(key) != null) {
                list.add(key);
            }
        }
        return list;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getAnonymizedAddress())
                .append(" last_active_time=" + last_active_time)
                .append(" {profile connection policy(")
                .append(profileConnectionPolicies)
                .append("), optional codec(support=")
                .append(a2dpSupportsOptionalCodecs)
                .append("|enabled=")
                .append(a2dpOptionalCodecsEnabled)
                .append("), isActiveHfpDevice (")
                .append(isActiveHfpDevice)
                .append("), custom metadata(")
                .append(publicMetadata)
                .append("), hfp client audio policy(")
                .append(audioPolicyMetadata)
                .append(")}");

        return builder.toString();
    }
}
