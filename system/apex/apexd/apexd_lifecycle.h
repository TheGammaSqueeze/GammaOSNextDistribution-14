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

#ifndef ANDROID_APEXD_APEXD_LIFECYCLE_H_
#define ANDROID_APEXD_APEXD_LIFECYCLE_H_

#include <android-base/result.h>

namespace android {
namespace apex {

class ApexdLifecycle {
 private:
  ApexdLifecycle(){};
  std::atomic<bool> boot_completed_;

  // Non-copyable && non-moveable.
  ApexdLifecycle(const ApexdLifecycle&) = delete;
  ApexdLifecycle& operator=(const ApexdLifecycle&) = delete;
  ApexdLifecycle& operator=(ApexdLifecycle&&) = delete;

  void WaitForBootStatus();

 public:
  static ApexdLifecycle& GetInstance() {
    static ApexdLifecycle instance;
    return instance;
  }
  bool IsBooting();
  void MarkBootCompleted();
  void WaitForBootStatus(android::base::Result<void> (&rollback_fn)(
      const std::string&, const std::string&));
};
}  // namespace apex
}  // namespace android

#endif  // ANDROID_APEXD_APEXD_LIFECYCLE_H
