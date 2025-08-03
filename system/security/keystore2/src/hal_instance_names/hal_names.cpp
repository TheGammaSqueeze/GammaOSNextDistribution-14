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

#include <hidl/ServiceManagement.h>

#include "rust/cxx.h"

rust::Vec<rust::String> convert(const std::vector<std::string>& names) {
    rust::Vec<rust::String> result;
    std::copy(names.begin(), names.end(), std::back_inserter(result));
    return result;
}

rust::Vec<rust::String> get_hidl_instances(rust::Str package, size_t major_version,
                                           size_t minor_version, rust::Str interfaceName) {
    std::string version = std::to_string(major_version) + "." + std::to_string(minor_version);
    std::string factoryName = static_cast<std::string>(package) + "@" + version +
                              "::" + static_cast<std::string>(interfaceName);

    const auto halNames = android::hardware::getAllHalInstanceNames(factoryName);
    return convert(halNames);
}
