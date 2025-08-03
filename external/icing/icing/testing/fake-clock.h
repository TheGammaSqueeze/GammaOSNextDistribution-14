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

#ifndef ICING_TESTING_FAKE_CLOCK_H_
#define ICING_TESTING_FAKE_CLOCK_H_

#include "icing/util/clock.h"

namespace icing {
namespace lib {

// A fake timer class for tests. It makes sure that the elapsed time changes
// every time it's requested.
class FakeTimer : public Timer {
 public:
  int64_t GetElapsedMilliseconds() const override {
    return fake_elapsed_milliseconds_;
  }

  void SetElapsedMilliseconds(int64_t elapsed_milliseconds) {
    fake_elapsed_milliseconds_ = elapsed_milliseconds;
  }

 private:
  int64_t fake_elapsed_milliseconds_ = 0;
};

// Wrapper around real-time clock functions. This is separated primarily so
// tests can override this clock and inject it into the class under test.
class FakeClock : public Clock {
 public:
  int64_t GetSystemTimeMilliseconds() const override { return milliseconds_; }

  void SetSystemTimeMilliseconds(int64_t milliseconds) {
    milliseconds_ = milliseconds;
  }

  std::unique_ptr<Timer> GetNewTimer() const override {
    return std::make_unique<FakeTimer>(fake_timer_);
  }

  void SetTimerElapsedMilliseconds(int64_t timer_elapsed_milliseconds) {
    fake_timer_.SetElapsedMilliseconds(timer_elapsed_milliseconds);
  }

 private:
  int64_t milliseconds_ = 0;
  FakeTimer fake_timer_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_FAKE_CLOCK_H_
