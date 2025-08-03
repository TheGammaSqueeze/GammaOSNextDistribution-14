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

// Entry for microdroid-specific apexd. This should be kept as minimal as
// possible.

#define LOG_TAG "apexd-vm"

#include <android-base/logging.h>
#include <sys/stat.h>

#include "apexd.h"

static const android::apex::ApexdConfig kMicrodroidConfig = {
    android::apex::kApexStatusSysprop,
    {android::apex::kApexPackageSystemDir},
    // A bunch of things are not used in Microdroid, hence we use nullptr
    // instead of an actual value.
    nullptr, /* active_apex_data_dir */
    nullptr, /* decompression_dir */
    nullptr, /* ota_reserved_dir */
    nullptr, /* apex_hashtree_dir */
    nullptr, /* staged_session_dir */
    android::apex::kVmPayloadMetadataPartitionProp,
    nullptr, /* active_apex_selinux_ctx */
};

int main(int /*argc*/, char** argv) {
  android::base::InitLogging(argv);
  android::base::SetMinimumLogSeverity(android::base::INFO);

  // set umask to 022 so that files/dirs created are accessible to other
  // processes e.g.) /apex/apex-info-list.xml is supposed to be read by other
  // processes
  umask(022);

  android::apex::SetConfig(kMicrodroidConfig);
  return android::apex::OnStartInVmMode();
}
