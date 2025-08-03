/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <fuzzbinder/libbinder_driver.h>

#include <binderwrapper/binder_wrapper.h>

#include "update_engine/aosp/daemon_android.h"
#include "update_engine/aosp/daemon_state_android.h"

using chromeos_update_engine::BinderUpdateEngineAndroidService;
using chromeos_update_engine::BinderUpdateEngineAndroidStableService;
using chromeos_update_engine::DaemonStateAndroid;

using android::fuzzService;
using android::sp;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  android::BinderWrapper::Create();

  brillo::BaseMessageLoop message_loop_;
  message_loop_.SetAsCurrent();

  auto daemonStateAndroid = std::make_unique<DaemonStateAndroid>();
  daemonStateAndroid->Initialize();

  auto binderService = sp<BinderUpdateEngineAndroidService>::make(
      daemonStateAndroid->service_delegate());
  auto stableBinderService = sp<BinderUpdateEngineAndroidStableService>::make(
      daemonStateAndroid->service_delegate());
  // TODO(b/287253479) - Add seed corpus/dicts for this fuzzer which has valid
  // urls
  fuzzService({binderService, stableBinderService},
              FuzzedDataProvider(data, size));

  android::BinderWrapper::Destroy();
  return 0;
}
