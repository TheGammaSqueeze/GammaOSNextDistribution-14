/*
 * Copyright 2023 The Android Open Source Project
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

#include "le_audio_log_history.h"

#include <base/logging.h>
#include <check.h>

#include <cstdint>
#include <memory>
#include <string>

#include "common/circular_buffer.h"
#include "common/strings.h"
#include "main/shim/dumpsys.h"
#include "os/log.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"

constexpr size_t kMaxLogSize = 255;
constexpr size_t kLeAudioLogHistoryBufferSize = 200;

class TimestampedStringCircularBuffer
    : public bluetooth::common::TimestampedCircularBuffer<std::string> {
 public:
  explicit TimestampedStringCircularBuffer(size_t size)
      : bluetooth::common::TimestampedCircularBuffer<std::string>(size) {}

  void Push(const std::string& s) {
    bluetooth::common::TimestampedCircularBuffer<std::string>::Push(
        s.substr(0, kMaxLogSize));
  }

  template <typename... Args>
  void Push(Args... args) {
    char buf[kMaxLogSize];
    std::snprintf(buf, sizeof(buf), args...);
    bluetooth::common::TimestampedCircularBuffer<std::string>::Push(
        std::string(buf));
  }
};

class LeAudioLogHistoryImpl;
LeAudioLogHistoryImpl* instance;

constexpr size_t kMaxLogHistoryTagLength = 14;
constexpr size_t kMaxLogHistoryMsgLength = 44;
const std::string kTimeFormat("%Y-%m-%d %H:%M:%S");

using Record = bluetooth::common::TimestampedEntry<std::string>;

class LeAudioLogHistoryImpl : public LeAudioLogHistory {
 public:
  ~LeAudioLogHistoryImpl(void) { history_.reset(); }

  LeAudioLogHistoryImpl(void) {
    history_ = std::make_shared<TimestampedStringCircularBuffer>(
        kLeAudioLogHistoryBufferSize);
    CHECK(history_ != nullptr);
    history_->Push(std::string("Initialized le_audio history"));
  }

  void Dump(int fd) {
#define DUMPSYS_TAG "::le_audio"

    LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);
    if (history_ == nullptr) {
      return;
    }
    std::vector<Record> history = history_->Pull();
    for (auto& record : history) {
      time_t then = record.timestamp / 1000;
      struct tm tm;
      localtime_r(&then, &tm);
      auto s2 = bluetooth::common::StringFormatTime(kTimeFormat, tm);
      LOG_DUMPSYS(fd, " %s.%03u %s", s2.c_str(),
                  static_cast<unsigned int>(record.timestamp % 1000),
                  record.entry.c_str());
    }
#undef DUMPSYS_TAG
  }

  void AddLogHistory(const std::string& tag, int group_id,
                     const RawAddress& addr, const std::string& msg,
                     const std::string& extra) {
    add_logs_history_common(tag, group_id, addr, msg, extra);
  }

  void AddLogHistory(const std::string& tag, int group_id,
                     const RawAddress& addr, const std::string& msg) {
    AddLogHistory(tag, group_id, addr, msg, std::string());
  }

 private:
  void add_logs_history_common(const std::string& tag, int group_id,
                               const RawAddress& addr, const std::string& msg,
                               const std::string& extra) {
    if (history_ == nullptr) {
      LOG_ERROR(
          "LeAudioLogHistory has not been constructed or already destroyed !");
      return;
    }

    history_->Push("%-*s GID %-3d  %-*s: %-22s %s", kMaxLogHistoryTagLength,
                   tag.substr(0, kMaxLogHistoryTagLength).c_str(), group_id,
                   kMaxLogHistoryMsgLength,
                   msg.substr(0, kMaxLogHistoryMsgLength).c_str(),
                   ADDRESS_TO_LOGGABLE_CSTR(addr), extra.c_str());
  }

  std::shared_ptr<TimestampedStringCircularBuffer> history_{nullptr};
};

LeAudioLogHistory* LeAudioLogHistory::Get(void) {
  if (instance) {
    return instance;
  }
  instance = new LeAudioLogHistoryImpl();
  return instance;
}

void LeAudioLogHistory::DebugDump(int fd) {
  if (instance) {
    instance->Dump(fd);
  }
}

void LeAudioLogHistory::Cleanup(void) {
  if (!instance) {
    return;
  }
  auto ptr = instance;
  instance = nullptr;
  delete ptr;
}
