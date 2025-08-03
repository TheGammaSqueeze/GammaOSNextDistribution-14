/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "dexopt_chroot_setup.h"

#include "aidl/com/android/server/art/BnDexoptChrootSetup.h"
#include "android-base/errors.h"
#include "android-base/result.h"
#include "android/binder_auto_utils.h"
#include "android/binder_manager.h"
#include "android/binder_process.h"

namespace art {
namespace dexopt_chroot_setup {

namespace {

using ::android::base::Error;
using ::android::base::Result;
using ::ndk::ScopedAStatus;

constexpr const char* kServiceName = "dexopt_chroot_setup";

}  // namespace

Result<void> DexoptChrootSetup::Start() {
  ScopedAStatus status = ScopedAStatus::fromStatus(
      AServiceManager_registerLazyService(this->asBinder().get(), kServiceName));
  if (!status.isOk()) {
    return Error() << status.getDescription();
  }

  ABinderProcess_startThreadPool();

  return {};
}

}  // namespace dexopt_chroot_setup
}  // namespace art
