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

#pragma once

#include <sys/timerfd.h>

#include <cstdint>

namespace bluetooth {
namespace os {
namespace fake_timer {

int fake_timerfd_create(int clockid, int flags);

int fake_timerfd_settime(int fd, int flags, const struct itimerspec* new_value, struct itimerspec* old_value);

int fake_timerfd_close(int fd);

void fake_timerfd_reset();

void fake_timerfd_advance(uint64_t ms);

void fake_timerfd_cap_at(uint64_t ms);

uint64_t fake_timerfd_get_clock();

}  // namespace fake_timer
}  // namespace os
}  // namespace bluetooth
