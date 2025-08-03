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
#pragma once

#include <aidl/android/hardware/contexthub/ContextHubMessage.h>
#include <aidl/android/hardware/contexthub/NanoappBinary.h>
#include <optional>
#include <string>

#include "chre/util/array_queue.h"
#include "chre/util/non_copyable.h"
#include "chre_host/generated/host_messages_generated.h"

namespace aidl::android::hardware::contexthub {

/**
 * Logs HAL events.
 *
 * The logged events are store in a fixed size queue. When the number of logged
 * events exceed the size of the queue, older events are deleted.
 */
class EventLogger {
 public:
  /** Maximum number of load and unload events to store. */
  static constexpr int kMaxNanoappEvents = 20;
  /** Maximum number of Context Hub restart events to store. */
  static constexpr int kMaxRestartEvents = 20;
  /** Maximum number of message events to store. */
  static constexpr int kMaxMessageEvents = 20;

  void logNanoappLoad(const NanoappBinary &app, bool success);

  void logNanoappUnload(int64_t appId, bool success);

  void logContextHubRestart();

  void logMessageToNanoapp(const ContextHubMessage &message, bool success);

  void logMessageFromNanoapp(const ::chre::fbs::NanoappMessageT &message);

  void logMessageFromNanoapp(const ContextHubMessage &message);

  /** Returns a textual representation of the logged events. */
  std::string dump() const;

 protected:
  struct NanoappLoad {
    int64_t timestampMs;
    int64_t id;
    int32_t version;
    size_t sizeBytes;
    bool success;
  };

  struct NanoappUnload {
    int64_t timestampMs;
    int64_t id;
    bool success;
  };

  struct NanoappMessage {
    int64_t timestampMs;
    int64_t id;
    size_t sizeBytes;
    bool success;
  };

  ::chre::ArrayQueue<NanoappLoad, kMaxNanoappEvents> mNanoappLoads;
  ::chre::ArrayQueue<NanoappUnload, kMaxNanoappEvents> mNanoappUnloads;
  ::chre::ArrayQueue<int64_t, kMaxRestartEvents> mContextHubRestarts;
  ::chre::ArrayQueue<NanoappMessage, kMaxMessageEvents> mMsgToNanoapp;
  ::chre::ArrayQueue<NanoappMessage, kMaxMessageEvents> mMsgFromNanoapp;

  /**
   * Current time in milliseconds.
   * Override the current time when a value is provided.
   * Used for tests.
   */
  std::optional<int64_t> mNowMs;

 private:
  /** Protects concurrent reads and writes to the queue. */
  mutable std::mutex mQueuesMutex;

  /** Returns the current time in milliseconds */
  int64_t getTimeMs() const;
};

}  // namespace aidl::android::hardware::contexthub
