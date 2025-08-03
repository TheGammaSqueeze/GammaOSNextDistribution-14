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

#define LOG_TAG "hwc-backend"

#include "BackendManager.h"

#include "utils/log.h"
#include "utils/properties.h"

namespace android {

// NOLINTNEXTLINE(cert-err58-cpp)
const std::vector<std::string> BackendManager::kClientDevices = {
    "kirin",
    "mediatek-drm",
};

BackendManager &BackendManager::GetInstance() {
  static BackendManager backend_manager;

  return backend_manager;
}

int BackendManager::RegisterBackend(const std::string &name,
                                    BackendConstructorT backend_constructor) {
  available_backends_[name] = std::move(backend_constructor);
  return 0;
}

int BackendManager::SetBackendForDisplay(HwcDisplay *display) {
  auto driver_name(display->GetPipe().device->GetName());
  char backend_override[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.backend_override", backend_override,
               driver_name.c_str());
  std::string backend_name(backend_override);

  display->set_backend(GetBackendByName(backend_name));
  if (display->backend() == nullptr) {
    ALOGE("Failed to set backend '%s' for '%s' and driver '%s'",
          backend_name.c_str(),
          display->GetPipe().connector->Get()->GetName().c_str(),
          driver_name.c_str());
    return -EINVAL;
  }

  ALOGI("Backend '%s' for '%s' and driver '%s' was successfully set",
        backend_name.c_str(),
        display->GetPipe().connector->Get()->GetName().c_str(),
        driver_name.c_str());

  return 0;
}

std::unique_ptr<Backend> BackendManager::GetBackendByName(std::string &name) {
  if (available_backends_.empty()) {
    ALOGE("No backends are specified");
    return nullptr;
  }

  auto it = available_backends_.find(name);
  if (it == available_backends_.end()) {
    auto it = std::find(kClientDevices.begin(), kClientDevices.end(), name);
    name = it == kClientDevices.end() ? "generic" : "client";
  }

  return available_backends_[name]();
}
}  // namespace android
