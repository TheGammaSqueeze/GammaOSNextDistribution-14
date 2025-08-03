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

#include "chre/core/system_health_monitor.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/util/macros.h"

namespace chre {

void SystemHealthMonitor::onFailure(HealthCheckId id) {
  EventLoopManagerSingleton::get()->getSystemHealthMonitor().onCheckFailureImpl(
      id);
}

void SystemHealthMonitor::onCheckFailureImpl(HealthCheckId id) {
  auto index = asBaseType(id);
  if (mShouldCheckCrash) {
    FATAL_ERROR("HealthMonitor check failed for type %" PRIu16, index);
  } else {
    constexpr auto kMaxCount = std::numeric_limits<
        std::remove_reference_t<decltype(mCheckIdOccurrenceCounter[0])>>::max();
    CHRE_ASSERT(index < ARRAY_SIZE(mCheckIdOccurrenceCounter));
    if (mCheckIdOccurrenceCounter[index] == kMaxCount) {
      LOGD("Cannot record one more HealthCheckId %" PRIu16
           "occurrence: overflow",
           index);
    } else {
      mCheckIdOccurrenceCounter[index]++;
    }
    LOGE("HealthMonitor check failed for type %" PRIu16
         ", occurrence: %" PRIu16,
         index, mCheckIdOccurrenceCounter[index]);
  }
}

}  // namespace chre
