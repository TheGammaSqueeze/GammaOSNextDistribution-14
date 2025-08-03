/*
 * Copyright (C) 2022 The Android Open Source Project
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

#pragma once

#include "FileSystem.h"
#include "PropertyFetcher.h"

#include <optional>
#include <string>
#include <vector>

namespace android {
namespace vintf {

// APEX VINTF interface
class ApexInterface {
   public:
    virtual ~ApexInterface() = default;
    // Check if there is an update for the given type of APEX files in the system
    virtual bool HasUpdate(FileSystem* fileSystem, PropertyFetcher* propertyFetcher) const = 0;
    // Get device VINTF directories
    virtual status_t DeviceVintfDirs(FileSystem* fileSystem, PropertyFetcher* propertyFetcher,
                                     std::vector<std::string>* out, std::string* error) = 0;
};

namespace details {

// Provide default implementation for ApexInterface
class Apex : public ApexInterface {
   public:
    Apex() = default;
    bool HasUpdate(FileSystem* fileSystem, PropertyFetcher* propertyFetcher) const override;
    status_t DeviceVintfDirs(FileSystem* fileSystem, PropertyFetcher* propertyFetcher,
                             std::vector<std::string>* out, std::string* error) override;

   private:
    std::optional<TimeSpec> mtime_;
};

}  // namespace details
}  // namespace vintf
}  // namespace android
