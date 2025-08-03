// Copyright (C) 2022 The Android Open Source Project
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
#pragma once

#include <inttypes.h>

#include <memory>
#include <string>
#include <thread>
#include <unordered_map>
#include <variant>

#include "aemu/base/threads/AndroidThread.h"

// Interface for consuming events from HealthMonitor

namespace gfxstream {
namespace guest {

// Struct for hanging events
struct EventHangMetadata {
    const char* file;
    const char* function;
    const char* msg;
    const int line;
    const unsigned long threadId;
    // Field for adding custom key value annotations
    using HangAnnotations = std::unordered_map<std::string, std::string>;
    // Field for adding custom key value annotations
    std::unique_ptr<HangAnnotations> data;

    // TODO: willho@ replace this enum with a generic string field embedded in the
    // proto and replace the individual event codes with a general hang event
    // Requires a new callback to be passed from the vm to gfxstream_backend_init
    enum class HangType { kRenderThread, kSyncThread, kOther };
    HangType hangType;

    EventHangMetadata(const char* file, const char* function, const char* msg, int line,
                      HangType hangType, std::unique_ptr<HangAnnotations> data)
        : file(file),
          function(function),
          msg(msg),
          line(line),
          threadId(getCurrentThreadId()),
          data(std::move(data)),
          hangType(hangType) {}

    EventHangMetadata()
        : EventHangMetadata(nullptr, nullptr, nullptr, 0, HangType::kRenderThread, nullptr) {}

    void mergeAnnotations(std::unique_ptr<HangAnnotations> annotations) {
        if (!data) {
            data = std::make_unique<HangAnnotations>();
        }
        data->merge(*annotations);
    }
};

class HealthMonitorConsumer {
public:
    virtual void consumeHangEvent(uint64_t taskId, const EventHangMetadata* metadata,
                                  int64_t otherHungTasks) = 0;
    virtual void consumeUnHangEvent(uint64_t taskId, const EventHangMetadata* metadata,
                                    int64_t hungMs) = 0;
    virtual ~HealthMonitorConsumer() {}
};

} // namespace guest
} // namespace gfxstream
