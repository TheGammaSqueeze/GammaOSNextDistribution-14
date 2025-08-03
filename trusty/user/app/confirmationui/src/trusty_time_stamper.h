/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdint.h>

namespace monotonic_time_stamper {

class TimeStamp {
public:
    TimeStamp(uint64_t ts) : timestamp_(ts), ok_(true) {}
    TimeStamp() : timestamp_(0), ok_(false) {}
    bool isOk() const { return ok_; }
    operator const uint64_t() const { return timestamp_; }

private:
    uint64_t timestamp_;
    bool ok_;
};

TimeStamp now();

}  // namespace monotonic_time_stamper
