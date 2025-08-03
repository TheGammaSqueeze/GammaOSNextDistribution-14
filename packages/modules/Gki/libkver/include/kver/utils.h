//
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#pragma once

#include <optional>
#include <string>

#include <kver/kernel_release.h>
#include <kver/kmi_version.h>

namespace android::kver {

// Check if new_release is a valid update against old_release.
bool IsKernelUpdateValid(const std::optional<KernelRelease>& old_kernel_release,
                         const std::optional<KernelRelease>& new_kernel_release);

// These functions defines how kernel releases and KMI versions are related to
// APEX package names and versions.

// Defines how kernel release is mapped to APEX package names.
// e.g. "com.android.gki.kmi_5_4_android12_0"
std::string GetApexName(const KmiVersion& kmi_version);

// Return the APEX version for a GKI APEX built from source.
uint64_t GetFactoryApexVersion();

}  // namespace android::kver
