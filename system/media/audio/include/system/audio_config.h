/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_AUDIO_CONFIG_H
#define ANDROID_AUDIO_CONFIG_H

#ifdef __cplusplus

#include <string>
#include <unistd.h>
#include <vector>

#include <cutils/properties.h>

namespace android {

// Returns a vector of paths where audio configuration files
// must be searched, in the provided order.
static inline std::vector<std::string> audio_get_configuration_paths() {
    static const std::vector<std::string> paths = []() {
        char value[PROPERTY_VALUE_MAX] = {};
        bool va_aosp = property_get_bool("ro.vendor.qti.va_aosp.support", false);
        std::vector<std::string> ret;
        if (property_get("ro.boot.product.vendor.sku", value, "") <= 0) {
            ret = std::vector<std::string>({"/odm/etc", "/vendor/etc", "/system/etc"});
        } else {
            ret = std::vector<std::string>({
                    "/odm/etc",
                    std::string("/vendor/etc/audio/sku_") + value +
                           (va_aosp ? "_qssi" : ""),
                    std::string("/vendor/etc/audio/sku_") + value,
                    "/vendor/etc", "/system/etc"});
        }
        if (va_aosp) {
            ret.insert(ret.end() - 2, "/vendor/etc/audio");
        }
        return ret;
    }();
    return paths;
}

static inline bool audio_is_readable_configuration_file(const char* filePath) {
    return (access(filePath, R_OK) == 0);
}

static inline std::string audio_find_readable_configuration_file(const char* fileName) {
    for (const auto& path : audio_get_configuration_paths()) {
        std::string tryPath = path + "/" + fileName;
        if (audio_is_readable_configuration_file(tryPath.c_str())) {
            return tryPath;
        }
    }
    return {};
}
/*
 * audio_get_audio_policy_config_file() Rules
 * 1) A2DP offload NOT supported IMPLIES LE Audio offload NOT supported
 * 2) A2DP offload disabled is ignored if A2DP offload is NOT supported
 * 3) LE Audio disabled is ignored if LE audio offload is NOT supported
 * 4) A2DP offload disabled IMPLIES LE audio offload disabled
 * 5) LE Audio offload NOT supported is possible with A2DP offload supported
 * 6) LE Audio offload disabled is possible with A2DP offload NOT disabled
 */
static inline std::string audio_get_audio_policy_config_file() {
    static constexpr const char *apmXmlConfigFileName = "audio_policy_configuration.xml";
    static constexpr const char *apmA2dpOffloadDisabledXmlConfigFileName =
            "audio_policy_configuration_a2dp_offload_disabled.xml";
    static constexpr const char *apmLeOffloadDisabledXmlConfigFileName =
            "audio_policy_configuration_le_offload_disabled.xml";
    static constexpr const char *apmBluetoothLegacyHalXmlConfigFileName =
            "audio_policy_configuration_bluetooth_legacy_hal.xml";

    std::string audioPolicyXmlConfigFile;
    // First try alternative files if needed
    if (property_get_bool("ro.bluetooth.a2dp_offload.supported", false)) {
        if (property_get_bool("persist.bluetooth.bluetooth_audio_hal.disabled", false) &&
            property_get_bool("persist.bluetooth.a2dp_offload.disabled", false)) {
            // Both BluetoothAudio@2.0 and BluetoothA2dp@1.0 (Offload) are disabled, and uses
            // the legacy hardware module for A2DP and hearing aid.
            audioPolicyXmlConfigFile = audio_find_readable_configuration_file(
                    apmBluetoothLegacyHalXmlConfigFileName);
        } else if (property_get_bool("persist.bluetooth.a2dp_offload.disabled", false)) {
            // A2DP offload supported but disabled: try to use special XML file
            // assume that if a2dp offload is not supported, le offload is not supported as well
            audioPolicyXmlConfigFile = audio_find_readable_configuration_file(
                    apmA2dpOffloadDisabledXmlConfigFileName);
        } else if (!property_get_bool("ro.bluetooth.leaudio_offload.supported", false) ||
            property_get_bool("persist.bluetooth.leaudio_offload.disabled", false)) {
            // A2DP offload supported but LE offload disabled: try to use special XML file
            audioPolicyXmlConfigFile = audio_find_readable_configuration_file(
                    apmLeOffloadDisabledXmlConfigFileName);
        }
    } else if (property_get_bool("persist.bluetooth.bluetooth_audio_hal.disabled", false)) {
        audioPolicyXmlConfigFile = audio_find_readable_configuration_file(
                apmBluetoothLegacyHalXmlConfigFileName);
    } else {
        audioPolicyXmlConfigFile = audio_find_readable_configuration_file(
                apmA2dpOffloadDisabledXmlConfigFileName);
    }
    return audioPolicyXmlConfigFile.empty() ?
            audio_find_readable_configuration_file(apmXmlConfigFileName) : audioPolicyXmlConfigFile;
}

}  // namespace android

#endif  // __cplusplus

#endif  // ANDROID_AUDIO_CONFIG_H
