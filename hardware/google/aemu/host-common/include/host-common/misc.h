// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "host-common/multi_display_agent.h"
#include "host-common/window_agent.h"

#ifdef _MSC_VER
# ifdef BUILDING_EMUGL_COMMON_SHARED
#  define EMUGL_COMMON_API __declspec(dllexport)
# else
#  define EMUGL_COMMON_API __declspec(dllimport)
#endif
#else
# define EMUGL_COMMON_API
#endif

// List of values used to identify a clockwise 90-degree rotation.
typedef enum {
    SKIN_ROTATION_0,
    SKIN_ROTATION_90,
    SKIN_ROTATION_180,
    SKIN_ROTATION_270
} SkinRotation;

#ifdef __cplusplus
namespace android {

namespace base {

class CpuUsage;
class MemoryTracker;

} // namespace base
} // namespace android

namespace emugl {

    // Set and get API version of system image.
    EMUGL_COMMON_API void setAvdInfo(bool isPhone, int apiLevel);
    EMUGL_COMMON_API void getAvdInfo(bool* isPhone, int* apiLevel);

    // CPU usage get/set.
    EMUGL_COMMON_API void setCpuUsage(android::base::CpuUsage* usage);
    EMUGL_COMMON_API android::base::CpuUsage* getCpuUsage();

    // Memory usage get/set
    EMUGL_COMMON_API void setMemoryTracker(android::base::MemoryTracker* usage);
    EMUGL_COMMON_API android::base::MemoryTracker* getMemoryTracker();

    // Window operation agent
    EMUGL_COMMON_API void set_emugl_window_operations(const QAndroidEmulatorWindowAgent &voperations);
    EMUGL_COMMON_API const QAndroidEmulatorWindowAgent &get_emugl_window_operations();

    // MultiDisplay operation agent
    EMUGL_COMMON_API void set_emugl_multi_display_operations(const QAndroidMultiDisplayAgent &operations);
    EMUGL_COMMON_API const QAndroidMultiDisplayAgent &get_emugl_multi_display_operations();

}
#endif  //__cplusplus
