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

#include <stdlib.h>

#include "android-base/logging.h"
#include "android-base/macros.h"
#include "android/binder_interface_utils.h"
#include "android/binder_process.h"
#include "dexopt_chroot_setup.h"

int main([[maybe_unused]] int argc, char* argv[]) {
  android::base::InitLogging(argv);

  auto dexopt_chroot_setup =
      ndk::SharedRefBase::make<art::dexopt_chroot_setup::DexoptChrootSetup>();

  LOG(INFO) << "Starting dexopt_chroot_setup";

  if (android::base::Result<void> ret = dexopt_chroot_setup->Start(); !ret.ok()) {
    LOG(ERROR) << "Unable to start dexopt_chroot_setup: " << ret.error();
    exit(1);
  }

  ABinderProcess_joinThreadPool();

  LOG(INFO) << "dexopt_chroot_setup shutting down";

  return 0;
}
