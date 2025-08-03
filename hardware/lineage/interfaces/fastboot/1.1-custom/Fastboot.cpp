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

#include "Fastboot.h"

#include <unordered_map>

#include <android-base/properties.h>
#include <android-base/strings.h>

namespace android {
namespace hardware {
namespace fastboot {
namespace V1_1 {
namespace implementation {

using OEMCommandHandler = std::function<Result(const std::vector<std::string>&)>;

// Methods from ::android::hardware::fastboot::V1_1::IFastboot follow.
Return<void> Fastboot::getPartitionType(const hidl_string& /* partitionName */,
                                        getPartitionType_cb _hidl_cb) {
    _hidl_cb(FileSystemType::RAW, {Status::SUCCESS, ""});
    return Void();
}

Result GetProp(const std::vector<std::string>& args) {
    if (!args.size()) {
        return { Status::INVALID_ARGUMENT, "Property unspecified" };
    }

    auto property = android::base::GetProperty(args[0], "");

    if (!property.empty()) {
        return { Status::SUCCESS, args[0] + ": " + property };
    }

    return { Status::FAILURE_UNKNOWN, "Unable to get property" };
}

Return<void> Fastboot::doOemCommand(const hidl_string& oemCmdArgs, doOemCommand_cb _hidl_cb) {
    const std::unordered_map<std::string, OEMCommandHandler> kOEMCmdMap = {
        {FB_OEM_GET_PROP, GetProp},
    };

    auto args = android::base::Split(oemCmdArgs, " ");
    if (args.size() < 2) {
        _hidl_cb({ Status::INVALID_ARGUMENT, "Invalid OEM command" });
        return Void();
    }

    // args[0] will be "oem", args[1] will be the command name
    auto cmd_handler = kOEMCmdMap.find(args[1]);
    if (cmd_handler != kOEMCmdMap.end()) {
        _hidl_cb(cmd_handler->second(std::vector<std::string>(args.begin() + 2, args.end())));
    } else {
        _hidl_cb({ Status::FAILURE_UNKNOWN, "Unknown OEM command" });
    }

    return Void();
}

Return<void> Fastboot::getVariant(getVariant_cb _hidl_cb) {
    _hidl_cb("NA", {Status::SUCCESS, ""});
    return Void();
}

Return<void> Fastboot::getOffModeChargeState(getOffModeChargeState_cb _hidl_cb) {
    _hidl_cb(false, {Status::SUCCESS, ""});
    return Void();
}

Return<void> Fastboot::getBatteryVoltageFlashingThreshold(
        getBatteryVoltageFlashingThreshold_cb _hidl_cb) {
    _hidl_cb(0, {Status::SUCCESS, ""});
    return Void();
}

Return<void> Fastboot::doOemSpecificErase(doOemSpecificErase_cb _hidl_cb) {
    _hidl_cb({Status::NOT_SUPPORTED, "Command not supported"});
    return Void();
}

extern "C" IFastboot* HIDL_FETCH_IFastboot(const char* /* name */) {
    return new Fastboot();
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace fastboot
}  // namespace hardware
}  // namespace android
