/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "utils.h"

#include <sstream>

#include "parse_string.h"

namespace android::vintf::details {

std::optional<FqInstance> convertLegacyInstanceIntoFqInstance(
    const std::string& package, const Version& version, const std::string& interface,
    const std::string& instance, HalFormat format, std::string* appendedError) {
    // Attempt to construct a good error message.
    std::stringstream ss;
    ss << "Invalid instance: '";
    if (format == HalFormat::AIDL) {
        ss << toAidlFqnameString(package, interface, instance) << " (@" << version.minorVer << ")";
    } else {
        ss << toFQNameString(package, version, interface, instance);
    }
    ss << "'. ";

    // Attempt to guess the source of error.
    bool foundError = false;
    details::FQName fqName;
    if (!fqName.setTo(package)) {
        ss << "Package '" << package
           << "' should have the format [a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*";
        foundError = true;
    }

    switch (format) {
        case HalFormat::HIDL:
        case HalFormat::AIDL: {
            if (!fqName.setTo(interface) || !fqName.isInterfaceName()) {
                ss << "Interface '" << interface << "' should have the format I[a-zA-Z0-9_]*";
                foundError = true;
            }
        } break;
        case HalFormat::NATIVE: {
            if (!interface.empty() && (!fqName.setTo(interface) || !fqName.isInterfaceName())) {
                ss << "Interface '" << interface << "' should have the format I[a-zA-Z0-9_]*";
                foundError = true;
            }
        } break;
    }

    if (foundError) {
        if (appendedError != nullptr) {
            *appendedError += ss.str() + "\n";
        }
        return std::nullopt;
    }

    // AIDL HAL <fqname> never contains version
    std::optional<FqInstance> parsed;
    switch (format) {
        case HalFormat::HIDL:
        case HalFormat::NATIVE:
            parsed = FqInstance::from(version.majorVer, version.minorVer, interface, instance);
            break;
        case HalFormat::AIDL:
            // AIDL HAL <fqname> never contains version
            parsed = FqInstance::from(interface, instance);
            break;
    }
    if (!parsed.has_value()) {
        if (appendedError != nullptr) {
            std::string debugString = format == HalFormat::AIDL
                                          ? toAidlFqnameString(package, interface, instance)
                                          : toFQNameString(package, version, interface, instance);

            *appendedError += "Invalid FqInstance: " + debugString + "\n";
        }
    }

    return parsed;
}

bool isCoreHal(const std::string& halName) {
    std::vector<std::string_view> allowedPrefixes{
        "android.hardware.",
        "android.frameworks.",
        "android.system.",
    };
    for (auto allowedPrefix : allowedPrefixes) {
        if (halName.substr(0, allowedPrefix.size()) == allowedPrefix) {
            return true;
        }
    }
    if (halName == "mapper") {
        return true;
    }
    return false;
}

}  // namespace android::vintf::details
