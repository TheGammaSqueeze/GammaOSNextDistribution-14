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

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <hidl/HidlTransportSupport.h>

#include <optional>

#include "AudioProxyError.h"
#include "AudioProxyImpl.h"
#include "DevicesFactoryImpl.h"
#include "ServiceConfig.h"

using android::sp;
using android::status_t;

using namespace audio_proxy::service;

int main(int argc, char** argv) {
  auto config = parseServiceConfigFromCommandLine(argc, argv);
  if (!config) {
    return ERROR_INVALID_ARGS;
  }

  // Default stream config.
  StreamConfig defaultStreamConfig = {10, 10};
  config->streams.emplace("default", defaultStreamConfig);

  // Config thread pool.
  ABinderProcess_setThreadPoolMaxThreadCount(1);
  android::hardware::configureRpcThreadpool(1, false /* callerWillJoin */);

  // Register AudioProxy service.
  auto audioProxy = ndk::SharedRefBase::make<AudioProxyImpl>();
  const std::string audioProxyName =
      std::string(AudioProxyImpl::descriptor) + "/" + config->name;

  binder_status_t binder_status = AServiceManager_addService(
      audioProxy->asBinder().get(), audioProxyName.c_str());
  if (binder_status != STATUS_OK) {
    LOG(ERROR) << "Failed to start " << config->name
               << " AudioProxy service, status " << binder_status;
    return ERROR_AIDL_FAILURE;
  }

  // Register AudioProxy audio HAL.
  auto devicesFactory =
      sp<DevicesFactoryImpl>::make(audioProxy->getBusStreamProvider(), *config);
  status_t status = devicesFactory->registerAsService(config->name);
  if (status != android::OK) {
    LOG(ERROR) << "Failed to start " << config->name << " audio HAL, status "
               << status;
    return ERROR_HIDL_FAILURE;
  }

  ABinderProcess_joinThreadPool();

  // `ABinderProcess_joinThreadpool` should never return. Return -2 here for
  // unexpected process exit.
  return ERROR_UNEXPECTED;
}
