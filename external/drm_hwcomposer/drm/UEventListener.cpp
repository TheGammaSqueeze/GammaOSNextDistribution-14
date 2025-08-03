/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "hwc-uevent-listener"

#include "UEventListener.h"

#include <thread>

#include "utils/log.h"

namespace android {

auto UEventListener::CreateInstance() -> std::shared_ptr<UEventListener> {
  auto uel = std::shared_ptr<UEventListener>(new UEventListener());

  uel->uevent_ = UEvent::CreateInstance();
  if (!uel->uevent_)
    return {};

  std::thread(&UEventListener::ThreadFn, uel.get(), uel).detach();

  return uel;
}

void UEventListener::ThreadFn(const std::shared_ptr<UEventListener> &uel) {
  // TODO(nobody): Rework code to allow stopping the thread (low priority)
  while (true) {
    if (uel.use_count() == 1)
      break;

    auto uevent_str = uel->uevent_->ReadNext();

    if (!hotplug_handler_ || !uevent_str)
      continue;

    auto drm_event = uevent_str->find("DEVTYPE=drm_minor") != std::string::npos;
    auto hotplug_event = uevent_str->find("HOTPLUG=1") != std::string::npos;

    if (drm_event && hotplug_event) {
      constexpr useconds_t kDelayAfterUeventUs = 200000;
      /* We need some delay to ensure DrmConnector::UpdateModes() will query
       * correct modes list, otherwise at least RPI4 board may report 0 modes */
      usleep(kDelayAfterUeventUs);
      hotplug_handler_();
    }
  }

  ALOGI("UEvent thread exit");
}
}  // namespace android
