/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <chrono>
#include <thread>

#include "apexd_lifecycle.h"

#include <android-base/logging.h>
#include <android-base/properties.h>

#include "apexd_utils.h"

using android::base::GetProperty;
using android::base::Result;
using android::base::WaitForProperty;

namespace android {
namespace apex {

bool ApexdLifecycle::IsBooting() {
  auto status = GetProperty(kApexStatusSysprop, "");
  return status != kApexStatusReady && status != kApexStatusActivated;
}

void ApexdLifecycle::WaitForBootStatus(
    Result<void> (&revert_fn)(const std::string&, const std::string&)) {
  while (!boot_completed_) {
    // Check for change in either crashing property or sys.boot_completed
    // Wait for updatable_crashing property change for most of the time
    // (arbitrary 10s), briefly check if boot has completed successfully,
    // if not continue waiting for updatable_crashing.
    // We use this strategy so that we can quickly detect if an updatable
    // process is crashing.
    if (WaitForProperty("sys.init.updatable_crashing", "1",
                        std::chrono::seconds(10))) {
      auto name = GetProperty("sys.init.updatable_crashing_process_name", "");
      LOG(ERROR) << "Native process '" << (name.empty() ? "[unknown]" : name)
                 << "' is crashing. Attempting a revert";
      auto result = revert_fn(name, "");
      if (!result.ok()) {
        LOG(ERROR) << "Revert failed : " << result.error();
        return WaitForBootStatus();
      } else {
        // This should never be reached, since revert_fn should've rebooted
        // the device.
        LOG(FATAL) << "Active sessions were reverted, but reboot wasn't "
                      "triggered.";
      }
    }
  }
}

void ApexdLifecycle::WaitForBootStatus() {
  while (!boot_completed_) {
    std::this_thread::sleep_for(std::chrono::seconds(1));
  }
}

void ApexdLifecycle::MarkBootCompleted() { boot_completed_ = true; }

}  // namespace apex
}  // namespace android
