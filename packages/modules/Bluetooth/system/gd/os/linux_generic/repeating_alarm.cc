/*
 * Copyright 2019 The Android Open Source Project
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

#include "os/repeating_alarm.h"

#include <sys/timerfd.h>
#include <unistd.h>

#include <cstring>

#include "common/bind.h"
#include "os/linux_generic/linux.h"
#include "os/log.h"
#include "os/utils.h"

#ifdef __ANDROID__
#define ALARM_CLOCK CLOCK_BOOTTIME_ALARM
#else
#define ALARM_CLOCK CLOCK_BOOTTIME
#endif

namespace bluetooth {
namespace os {
using common::Closure;

RepeatingAlarm::RepeatingAlarm(Handler* handler) : handler_(handler), fd_(TIMERFD_CREATE(ALARM_CLOCK, 0)) {
  ASSERT(fd_ != -1);

  token_ = handler_->thread_->GetReactor()->Register(
      fd_, common::Bind(&RepeatingAlarm::on_fire, common::Unretained(this)), common::Closure());
}

RepeatingAlarm::~RepeatingAlarm() {
  handler_->thread_->GetReactor()->Unregister(token_);

  int close_status;
  RUN_NO_INTR(close_status = TIMERFD_CLOSE(fd_));
  ASSERT(close_status != -1);
}

void RepeatingAlarm::Schedule(Closure task, std::chrono::milliseconds period) {
  std::lock_guard<std::mutex> lock(mutex_);
  long period_ms = period.count();
  itimerspec timer_itimerspec{{period_ms / 1000, period_ms % 1000 * 1000000},
                              {period_ms / 1000, period_ms % 1000 * 1000000}};
  int result = TIMERFD_SETTIME(fd_, 0, &timer_itimerspec, nullptr);
  ASSERT(result == 0);

  task_ = std::move(task);
}

void RepeatingAlarm::Cancel() {
  std::lock_guard<std::mutex> lock(mutex_);
  itimerspec disarm_itimerspec{/* disarm timer */};
  int result = TIMERFD_SETTIME(fd_, 0, &disarm_itimerspec, nullptr);
  ASSERT(result == 0);
}

void RepeatingAlarm::on_fire() {
  std::unique_lock<std::mutex> lock(mutex_);
  auto task = task_;
  uint64_t times_invoked;
  auto bytes_read = read(fd_, &times_invoked, sizeof(uint64_t));
  lock.unlock();
  task.Run();
  ASSERT(bytes_read == static_cast<ssize_t>(sizeof(uint64_t)));
}

}  // namespace os
}  // namespace bluetooth
