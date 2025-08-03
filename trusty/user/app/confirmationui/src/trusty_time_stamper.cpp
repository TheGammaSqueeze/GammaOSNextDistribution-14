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

#include "trusty_time_stamper.h"

#include <limits>

#include <inttypes.h>
#include <stdio.h>
#include <trusty/time.h>

#include <trusty_log.h>

#define TLOG_TAG "confirmationui"

namespace monotonic_time_stamper {

TimeStamp now() {
    int rv;
    int64_t secure_time_ns = 0;
    rv = trusty_gettime(0, &secure_time_ns);
    if (rv || secure_time_ns < 0) {
        TLOGE("Error getting time. Error: %d, time: %" PRIu64, rv,
              secure_time_ns);
        return 0;  // 0 is considered invalid. see TimeStamp::isOk()
    }
    return static_cast<uint64_t>(secure_time_ns) / 1000000;
}

}  // namespace monotonic_time_stamper
