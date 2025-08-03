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

#include "event_logger.h"

#include <inttypes.h>
#include <stdio.h>
#include <chrono>
#include <cstddef>
#include <ctime>
#include <string>

namespace aidl::android::hardware::contexthub {

namespace {

/**
 * Returns the time formatted in the local timezone.
 * The format is similar to the adb logcat format, i.e. `01-31 18:22:51.275`.
 */
std::string formatLocalTime(int64_t ms) {
  const std::chrono::milliseconds duration(ms);
  const std::chrono::time_point<std::chrono::system_clock> timePoint(duration);
  time_t time = std::chrono::system_clock::to_time_t(timePoint);
  constexpr int kBufferSize = 50;
  char buffer[kBufferSize];
  std::strftime(buffer, kBufferSize, "%m-%d %H:%M:%S.", std::localtime(&time));
  return std::string(buffer) + std::to_string(1000 + ms % 1000).substr(1);
}

}  // namespace

void EventLogger::logNanoappLoad(const NanoappBinary &app, bool success) {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mNanoappLoads.kick_push({
      .timestampMs = getTimeMs(),
      .id = app.nanoappId,
      .version = app.nanoappVersion,
      .sizeBytes = app.customBinary.size(),
      .success = success,
  });
}

void EventLogger::logNanoappUnload(int64_t appId, bool success) {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mNanoappUnloads.kick_push({
      .timestampMs = getTimeMs(),
      .id = appId,
      .success = success,
  });
}

void EventLogger::logContextHubRestart() {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mContextHubRestarts.kick_push(getTimeMs());
}

void EventLogger::logMessageToNanoapp(const ContextHubMessage &message,
                                      bool success) {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mMsgToNanoapp.kick_push({
      .timestampMs = getTimeMs(),
      .id = message.nanoappId,
      .sizeBytes = message.messageBody.size(),
      .success = success,
  });
}

void EventLogger::logMessageFromNanoapp(
    const ::chre::fbs::NanoappMessageT &message) {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mMsgFromNanoapp.kick_push({
      .timestampMs = getTimeMs(),
      .id = static_cast<int64_t>(message.app_id),
      .sizeBytes = message.message.size(),
  });
}

void EventLogger::logMessageFromNanoapp(const ContextHubMessage &message) {
  std::lock_guard<std::mutex> lock(mQueuesMutex);
  mMsgFromNanoapp.kick_push({
      .timestampMs = getTimeMs(),
      .id = message.nanoappId,
      .sizeBytes = message.messageBody.size(),
  });
}

std::string EventLogger::dump() const {
  constexpr int kBufferSize = 100;
  char buffer[kBufferSize];
  std::string logs;
  std::lock_guard<std::mutex> lock(mQueuesMutex);

  logs.append("\nNanoapp loads:\n");
  for (const NanoappLoad &load : mNanoappLoads) {
    // Use snprintf because std::format is not available and fmtlib {:x} format
    // crashes.
    if (snprintf(buffer, kBufferSize,
                 "  %s id 0x%" PRIx64 " version 0x%" PRIx32 " size %zu"
                 " status %s\n",
                 formatLocalTime(load.timestampMs).c_str(), load.id,
                 load.version, load.sizeBytes,
                 load.success ? "ok" : "fail") > 0) {
      logs.append(buffer);
    }
  }

  logs.append("\nNanoapp unloads:\n");
  for (const NanoappUnload &unload : mNanoappUnloads) {
    if (snprintf(buffer, kBufferSize, "  %s id 0x%" PRIx64 " status %s\n",
                 formatLocalTime(unload.timestampMs).c_str(), unload.id,
                 unload.success ? "ok" : "fail") > 0) {
      logs.append(buffer);
    }
  }

  logs.append("\nMessages to Nanoapps:\n");
  for (const NanoappMessage &msg : mMsgToNanoapp) {
    if (snprintf(buffer, kBufferSize,
                 "  %s to id 0x%" PRIx64 " size %zu status %s\n",
                 formatLocalTime(msg.timestampMs).c_str(), msg.id,
                 msg.sizeBytes, msg.success ? "ok" : "fail") > 0) {
      logs.append(buffer);
    }
  }

  logs.append("\nMessages from Nanoapps:\n");
  for (const NanoappMessage &msg : mMsgFromNanoapp) {
    if (snprintf(buffer, kBufferSize, "  %s from id 0x%" PRIx64 " size %zu\n",
                 formatLocalTime(msg.timestampMs).c_str(), msg.id,
                 msg.sizeBytes) > 0) {
      logs.append(buffer);
    }
  }

  logs.append("\nContext hub restarts:\n");
  for (const int64_t &ms : mContextHubRestarts) {
    if (snprintf(buffer, kBufferSize, "  %s\n", formatLocalTime(ms).c_str()) >
        0) {
      logs.append(buffer);
    }
  }

  return logs;
}

int64_t EventLogger::getTimeMs() const {
  if (mNowMs.has_value()) {
    return mNowMs.value();
  }
  struct timespec now;
  clock_gettime(CLOCK_REALTIME, &now);
  return 1000 * now.tv_sec + static_cast<int64_t>(now.tv_nsec / 1e6);
}

}  // namespace aidl::android::hardware::contexthub