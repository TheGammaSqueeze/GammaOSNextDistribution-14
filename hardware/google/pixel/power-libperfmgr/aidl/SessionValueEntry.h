/*
 * Copyright 2023 The Android Open Source Project
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

#include <ostream>

#include "AdpfTypes.h"
#include "UClampVoter.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

// Per-power-session values (equivalent to original PowerHintSession)
// Responsible for maintaining the state of the power session via attributes
// Primarily this means actual uclamp value and whether session is active
// (i.e. whether to include this power session uclmap when setting task uclamp)
struct SessionValueEntry {
    int64_t sessionId{0};
    // Thread group id
    int64_t tgid{0};
    uid_t uid{0};
    std::string idString;
    bool isActive{true};
    bool isAppSession{false};
    std::chrono::steady_clock::time_point lastUpdatedTime;
    std::shared_ptr<Votes> votes;

    // Write info about power session to ostream for logging and debugging
    std::ostream &dump(std::ostream &os) const;
};

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
