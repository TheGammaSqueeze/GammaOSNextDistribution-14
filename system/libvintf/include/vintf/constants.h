/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_VINTF_CONSTANTS_H
#define ANDROID_VINTF_CONSTANTS_H

#include "Version.h"

namespace android {
namespace vintf {

/* libvintf meta-version */
constexpr Version kMetaVersion{8, 0};

// Some legacy metaversion constants
// The metaversion where inet transport is added to AIDL HALs
constexpr Version kMetaVersionAidlInet{5, 0};

// The metaversion that treats <interface> x <instance> in <hal>
// as an error tag.
constexpr Version kMetaVersionNoHalInterfaceInstance{6, 0};

// Default version for an AIDL HAL if no version is specified.
constexpr size_t kDefaultAidlMinorVersion = 1;

}  // namespace vintf
}  // namespace android

#endif  // ANDROID_VINTF_CONSTANTS_H
