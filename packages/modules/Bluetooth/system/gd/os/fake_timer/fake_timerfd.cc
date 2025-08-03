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

#include "os/fake_timer/fake_timerfd.h"

#include <sys/eventfd.h>
#include <unistd.h>

#include <map>

namespace bluetooth {
namespace os {
namespace fake_timer {

class FakeTimerFd {
 public:
  int fd;
  bool active;
  uint64_t trigger_ms;
  uint64_t period_ms;
};

static std::map<int, FakeTimerFd*> fake_timers;
static uint64_t clock = 0;
static uint64_t max_clock = UINT64_MAX;

static uint64_t timespec_to_ms(const timespec* t) {
  return t->tv_sec * 1000 + t->tv_nsec / 1000000;
}

int fake_timerfd_create(int /* clockid */, int /* flags */) {
  int fd = eventfd(0, EFD_SEMAPHORE);
  if (fd == -1) {
    return fd;
  }

  FakeTimerFd* entry = new FakeTimerFd();
  fake_timers[fd] = entry;
  entry->fd = fd;
  return fd;
}

int fake_timerfd_settime(
    int fd,
    int /* flags */,
    const struct itimerspec* new_value,
    struct itimerspec* /* old_value */) {
  if (fake_timers.find(fd) == fake_timers.end()) {
    return -1;
  }

  FakeTimerFd* entry = fake_timers[fd];

  uint64_t trigger_delta_ms = timespec_to_ms(&new_value->it_value);
  entry->active = trigger_delta_ms != 0;
  if (!entry->active) {
    return 0;
  }

  uint64_t period_ms = timespec_to_ms(&new_value->it_interval);
  entry->trigger_ms = clock + trigger_delta_ms;
  entry->period_ms = period_ms;
  return 0;
}

int fake_timerfd_close(int fd) {
  auto timer_iterator = fake_timers.find(fd);
  if (timer_iterator != fake_timers.end()) {
    delete timer_iterator->second;
    fake_timers.erase(timer_iterator);
  }
  return close(fd);
}

void fake_timerfd_reset() {
  clock = 0;
  max_clock = UINT64_MAX;
  // if there are entries still here, it is a failure of our users to clean up
  // so let them leak and trigger errors
  fake_timers.clear();
}

static bool fire_next_event(uint64_t new_clock) {
  uint64_t earliest_time = new_clock;
  FakeTimerFd* to_fire = nullptr;
  for (auto it = fake_timers.begin(); it != fake_timers.end(); it++) {
    FakeTimerFd* entry = it->second;
    if (!entry->active) {
      continue;
    }

    if (entry->trigger_ms > clock && entry->trigger_ms <= new_clock) {
      if (to_fire == nullptr || entry->trigger_ms < earliest_time) {
        to_fire = entry;
        earliest_time = entry->trigger_ms;
      }
    }
  }

  if (to_fire == nullptr) {
    return false;
  }

  bool is_periodic = to_fire->period_ms != 0;
  if (is_periodic) {
    to_fire->trigger_ms += to_fire->period_ms;
  }
  to_fire->active = is_periodic;
  uint64_t value = 1;
  write(to_fire->fd, &value, sizeof(uint64_t));
  return true;
}

void fake_timerfd_advance(uint64_t ms) {
  uint64_t new_clock = clock + ms;
  if (new_clock < clock) {
    new_clock = max_clock;
  }
  while (fire_next_event(new_clock)) {
  }
  clock = new_clock;
}

void fake_timerfd_cap_at(uint64_t ms) {
  max_clock = ms;
}

uint64_t fake_timerfd_get_clock() {
  return clock;
}

}  // namespace fake_timer
}  // namespace os
}  // namespace bluetooth
