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

#ifndef CPP_EVS_MANAGER_AIDL_INCLUDE_SERVICENAME_H
#define CPP_EVS_MANAGER_AIDL_INCLUDE_SERVICENAME_H

#include <string_view>

// This is the name as which we'll register ourselves
inline constexpr std::string_view kManagedEnumeratorName = "default";

// This is the name of the hardware provider to which we'll bind by default
inline constexpr std::string_view kHardwareEnumeratorName = "hw/1";

// This is the name of the mock hardware provider selectable via command line.
// (should match .../hardware/interfaces/automotive/evs/1.1/default/ServiceNames.h)
inline constexpr std::string_view kMockEnumeratorName = "EvsEnumeratorHw-Mock";

#endif  // CPP_EVS_MANAGER_AIDL_INCLUDE_SERVICENAME_H
