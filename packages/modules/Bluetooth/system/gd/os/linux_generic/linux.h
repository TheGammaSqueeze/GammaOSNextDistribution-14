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

#pragma once

#ifndef EFD_SEMAPHORE
#define EFD_SEMAPHORE 1
#endif

#ifdef USE_FAKE_TIMERS
#include "os/fake_timer/fake_timerfd.h"
#define TIMERFD_CREATE ::bluetooth::os::fake_timer::fake_timerfd_create
#define TIMERFD_SETTIME ::bluetooth::os::fake_timer::fake_timerfd_settime
#define TIMERFD_CLOSE ::bluetooth::os::fake_timer::fake_timerfd_close
#else
#define TIMERFD_CREATE timerfd_create
#define TIMERFD_SETTIME timerfd_settime
#define TIMERFD_CLOSE close
#endif
