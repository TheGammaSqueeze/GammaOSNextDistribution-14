/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "BtStopWatch"

#include "common/stop_watch.h"

#include <iomanip>
#include <mutex>
#include <sstream>
#include <utility>

#include "common/init_flags.h"
#include "os/log.h"

namespace bluetooth {
namespace common {

static const int LOG_BUFFER_LENGTH = 10;
static std::array<StopWatchLog, LOG_BUFFER_LENGTH> stopwatch_logs;
static int current_buffer_index;
static std::recursive_mutex stopwatch_log_mutex;

void StopWatch::RecordLog(StopWatchLog log) {
  std::unique_lock<std::recursive_mutex> lock(stopwatch_log_mutex, std::defer_lock);
  if (!lock.try_lock()) {
    LOG_INFO("try_lock fail. log content: %s, took %zu us", log.message.c_str(),
             static_cast<size_t>(
                 std::chrono::duration_cast<std::chrono::microseconds>(
                     stopwatch_logs[current_buffer_index].end_timestamp -
                     stopwatch_logs[current_buffer_index].start_timestamp)
                     .count()));
    return;
  }
  if (current_buffer_index >= LOG_BUFFER_LENGTH) {
    current_buffer_index = 0;
  }
  stopwatch_logs[current_buffer_index] = std::move(log);
  current_buffer_index++;
  lock.unlock();
}

void StopWatch::DumpStopWatchLog() {
  std::lock_guard<std::recursive_mutex> lock(stopwatch_log_mutex);
  LOG_INFO("=-----------------------------------=");
  LOG_INFO("bluetooth stopwatch log history:");
  for (int i = 0; i < LOG_BUFFER_LENGTH; i++) {
    if (current_buffer_index >= LOG_BUFFER_LENGTH) {
      current_buffer_index = 0;
    }
    if (stopwatch_logs[current_buffer_index].message.empty()) {
      current_buffer_index++;
      continue;
    }
    std::stringstream ss;
    auto now = stopwatch_logs[current_buffer_index].timestamp;
    auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(
                      now.time_since_epoch()) %
                  1000;
    auto now_time_t = std::chrono::system_clock::to_time_t(now);
    ss << std::put_time(std::localtime(&now_time_t), "%Y-%m-%d %H:%M:%S");
    ss << '.' << std::setfill('0') << std::setw(3) << millis.count();
    std::string start_timestamp = ss.str();
    LOG_INFO(
        "%s: %s: took %zu us",
        start_timestamp.c_str(),
        stopwatch_logs[current_buffer_index].message.c_str(),
        static_cast<size_t>(std::chrono::duration_cast<std::chrono::microseconds>(
                                stopwatch_logs[current_buffer_index].end_timestamp -
                                stopwatch_logs[current_buffer_index].start_timestamp)
                                .count()));
    current_buffer_index++;
  }
  LOG_INFO("=-----------------------------------=");
}

StopWatch::StopWatch(std::string text)
    : text_(std::move(text)),
      timestamp_(std::chrono::system_clock::now()),
      start_timestamp_(std::chrono::high_resolution_clock::now()) {}

StopWatch::~StopWatch() {
  StopWatchLog sw_log;
  sw_log.timestamp = timestamp_;
  sw_log.start_timestamp = start_timestamp_;
  sw_log.end_timestamp = std::chrono::high_resolution_clock::now();
  sw_log.message = std::move(text_);

  RecordLog(std::move(sw_log));
}

}  // namespace common
}  // namespace bluetooth
