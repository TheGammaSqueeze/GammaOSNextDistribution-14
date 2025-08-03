// Copyright (C) 2022 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "aemu/base/AndroidHealthMonitorConsumerBasic.h"

#include <log/log.h>

#include "aemu/base/Process.h"

namespace gfxstream {
namespace guest {

void logEventHangMetadata(const EventHangMetadata* metadata) {
    ALOGE("Metadata:");
    ALOGE("\t file: %s", metadata->file);
    ALOGE("\t function: %s", metadata->function);
    ALOGE("\t line: %d", metadata->line);
    ALOGE("\t msg: %s", metadata->msg);
    ALOGE("\t thread: %lld (0x%08llx)", (long long)metadata->threadId,
          (long long)metadata->threadId);
    ALOGE("\t process name: %s", getProcessName().c_str());
    if (metadata->data) {
        ALOGE("\t Additional information:");
        for (auto& [key, value] : *metadata->data) {
            ALOGE("\t \t %s: %s", key.c_str(), value.c_str());
        }
    }
}

// HealthMonitorConsumerBasic
void HealthMonitorConsumerBasic::consumeHangEvent(uint64_t,
                                                  const EventHangMetadata* metadata,
                                                  int64_t otherHungTasks) {
    ALOGE("Logging hang event. Number of tasks already hung: %lld", (long long)otherHungTasks);
    logEventHangMetadata(metadata);
}

void HealthMonitorConsumerBasic::consumeUnHangEvent(uint64_t,
                                                    const EventHangMetadata* metadata,
                                                    int64_t hungMs) {
    ALOGE("Logging unhang event. Hang time: %lld ms", (long long)hungMs);
    logEventHangMetadata(metadata);
}

} // namespace guest
} // namespace gfxstream
