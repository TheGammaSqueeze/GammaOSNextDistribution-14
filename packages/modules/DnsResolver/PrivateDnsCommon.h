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

namespace android::net {

static constexpr int kDohPort = 443;
static constexpr int kDotPort = 853;

enum class PrivateDnsTransport : uint8_t {
    kDot,  // DNS over TLS.
    kDoh,  // DNS over HTTPS.
};

// Validation status of a private DNS server on a specific netId.
enum class Validation : uint8_t {
    in_process,
    success,
    success_but_expired,
    fail,
    unknown_server,
    unknown_netid,
};

// The private DNS mode on a specific netId.
enum class PrivateDnsMode : uint8_t {
    OFF,
    OPPORTUNISTIC,
    STRICT,
};

constexpr const char* validationStatusToString(Validation value) {
    switch (value) {
        case Validation::in_process:
            return "in_process";
        case Validation::success:
            return "success";
        case Validation::success_but_expired:
            return "success_but_expired";
        case Validation::fail:
            return "fail";
        case Validation::unknown_server:
            return "unknown_server";
        case Validation::unknown_netid:
            return "unknown_netid";
        default:
            return "unknown_status";
    }
}

constexpr const char* getPrivateDnsModeString(PrivateDnsMode mode) {
    switch (mode) {
        case PrivateDnsMode::OFF:
            return "OFF";
        case PrivateDnsMode::OPPORTUNISTIC:
            return "OPPORTUNISTIC";
        case PrivateDnsMode::STRICT:
            return "STRICT";
    }
}

}  // namespace android::net
