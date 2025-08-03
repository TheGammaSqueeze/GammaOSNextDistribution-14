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

#ifndef ICING_LEGACY_CORE_ICING_TIMER_H_
#define ICING_LEGACY_CORE_ICING_TIMER_H_

#include <sys/time.h>

#include <ctime>

namespace icing {
namespace lib {

// A simple stop-watch timer for performance measurement.
class IcingTimer {
 public:
  static double WallTimeNow();

  IcingTimer();

  void Reset();

  // Elapsed wall time since last Reset().
  double Elapsed() const;

 private:
  static double ClockTime();

  double start_;
};

}  // namespace lib
}  // namespace icing
#endif  // ICING_LEGACY_CORE_ICING_TIMER_H_
