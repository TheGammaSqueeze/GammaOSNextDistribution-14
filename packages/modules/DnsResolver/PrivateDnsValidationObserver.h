/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "PrivateDnsCommon.h"

namespace android::net {

// The observer is notified of onValidationStateUpdate 1) when a validation is
// about to begin or 2) when a validation finishes. If a validation finishes when in OFF mode
// or when the network has been destroyed, |validation| will be Validation::fail.
// WARNING: The Observer is notified while the lock is being held. Be careful not to call
// any method of PrivateDnsConfiguration from the observer to cause reentrancy problem.
class PrivateDnsValidationObserver {
  public:
    virtual ~PrivateDnsValidationObserver(){};
    virtual void onValidationStateUpdate(const std::string& serverIp, Validation validation,
                                         uint32_t netId) = 0;
};

}  // namespace android::net
