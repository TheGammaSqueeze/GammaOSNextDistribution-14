// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/legacy/core/icing-timer.h"

namespace icing {
namespace lib {

double IcingTimer::WallTimeNow() {
  struct timeval tv;
  gettimeofday(&tv, nullptr);
  return tv.tv_sec + tv.tv_usec / 1e6;
}

double IcingTimer::ClockTime() {
#ifdef __APPLE__
  // iOS targets can't rely on clock_gettime(). So, fallback to WallTime_Now().
  return WallTimeNow();
#else
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return ts.tv_sec + ts.tv_nsec / 1e9;
#endif  // __APPLE__
}

IcingTimer::IcingTimer() { Reset(); }

void IcingTimer::Reset() { start_ = ClockTime(); }

double IcingTimer::Elapsed() const { return ClockTime() - start_; }
}  // namespace lib
}  // namespace icing
