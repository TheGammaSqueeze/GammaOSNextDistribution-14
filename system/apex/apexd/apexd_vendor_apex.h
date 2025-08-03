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

#ifndef ANDROID_APEXD_VENDOR_APEX_H_
#define ANDROID_APEXD_VENDOR_APEX_H_

#include <android-base/result.h>

#include <string>

#include "apex_file.h"

using android::base::Result;

namespace android {
namespace apex {

// Determines if an incoming apex is a vendor apex
bool IsVendorApex(const ApexFile& apex_file);

// For incoming vendor apex updates.  Adds the data from its
//   vintf_fragment(s) and tests compatibility.
Result<void> CheckVendorApexUpdate(const ApexFile& apex_file,
                                   const std::string& apex_mount_point);

}  // namespace apex
}  // namespace android

#endif  // ANDROID_APEXD_VENDOR_APEX_H_
