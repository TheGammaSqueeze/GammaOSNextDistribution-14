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

#include <inttypes.h>
#include <sys/utsname.h>

#include <android-base/stringprintf.h>
#include <android-base/logging.h>
#include <kver/kernel_release.h>
#include <kver/utils.h>

namespace android::kver {

bool IsKernelUpdateValid(const std::optional<KernelRelease>& old_kernel_release,
                         const std::optional<KernelRelease>& new_kernel_release) {
  // Allow update from non-GKI to non-GKI for legacy devices, or non-GKI to GKI for retrofit
  // devices.
  if (!old_kernel_release.has_value()) {
    LOG(INFO) << "Current build does not contain GKI, permit update to \""
              << (new_kernel_release.has_value() ? new_kernel_release->string() : "")
              << "\" anyways.";
    return true;
  }

  if (!new_kernel_release.has_value()) {
    LOG(ERROR) << "Cannot update from GKI \"" << old_kernel_release->string()
               << "\" to non-GKI build.";
    return false;
  }

  // Check that KMI version does not downgrade; i.e. the tuple(w, x, z, k) does
  // not decrease.
  if (old_kernel_release->kmi_version().tuple() > new_kernel_release->kmi_version().tuple()) {
    LOG(ERROR) << "Cannot update from " << old_kernel_release->string() << " to "
               << new_kernel_release->string() << ": KMI version decreases.";
    return false;
  }

  // This ensures that Android release does not downgrade, e.g. you cannot go
  // from 5.10-android13-0 to 5.15-android12-0.
  if (old_kernel_release->android_release() > new_kernel_release->android_release()) {
    LOG(ERROR) << "Cannot update from " << old_kernel_release->string() << " to "
               << new_kernel_release->string() << ": Android release decreases.";
    return false;
  }

  // This ensures that w.x.y does not downgrade; e.g. you cannot go
  // from 5.4.43 to 5.4.42, but you can go from 5.4.43 to 5.10.5.
  if (old_kernel_release->kernel_version_tuple() > new_kernel_release->kernel_version_tuple()) {
    LOG(ERROR) << "Cannot update from " << old_kernel_release->string() << " to "
               << new_kernel_release->string() << ": Kernel version decreases.";
    return false;
  }

  LOG(INFO) << "Allow to update from " << old_kernel_release->string() << " to "
            << new_kernel_release->string();
  return true;
}

// Keep in sync with kmi.go.
std::string GetApexName(const KmiVersion& kmi_version) {
  static constexpr const char* KMI_VERSION_APEX_NAME_FORMAT{
      "com.android.gki.kmi_%" PRIu64 "_%" PRIu64 "_android%" PRIu64 "_%" PRIu64};
  return android::base::StringPrintf(KMI_VERSION_APEX_NAME_FORMAT, kmi_version.version(), kmi_version.patch_level(),
                                     kmi_version.android_release(), kmi_version.generation());
}

uint64_t GetFactoryApexVersion() {
  return 300000000;
}

}  // namespace android::kver
