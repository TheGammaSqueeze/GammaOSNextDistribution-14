/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <sys/resource.h>

#include "utils.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

bool setThreadPriority(const SchedPolicy policy, const int prio) {
    int e = set_sched_policy(0, policy);
    if (e < 0) {
        return FAILURE_V(false, "set_sched_policy(%d) failed with %s (%d)",
                         static_cast<int>(policy), strerror(-e), -e);
    }

    if (setpriority(PRIO_PROCESS, 0, prio) < 0) {
        e = errno;
        return FAILURE_V(false, "setpriority(%d) failed with %s (%d)",
                         prio, strerror(e), e);
    }

    return true;
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
