/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <cstdint>
#include <chrono>
#include <unordered_set>
#include <vector>

namespace aidl::android::hardware::biometrics::fingerprint {

struct Storage {
    enum class AuthResult {
        OK,
        FAILED,
        LOCKED_OUT_TIMED,
        LOCKED_OUT_PERMANENT,
    };

    struct AuthToken {
        int64_t userId = 0;
        int64_t authenticatorId = 0;
    };

    Storage(int32_t sensorId, int32_t userId);

    static constexpr int getMaxEnrollmentsPerUser() { return kMaxEnrollmentsPerUser; }
    int64_t getAuthenticatorId() const { return mAuthId; }
    int64_t invalidateAuthenticatorId(int64_t newAuthId);
    std::vector<int32_t> enumerateEnrollments() const;
    bool enroll(int enrollmentId, int64_t secureUserId, int64_t newAuthId);
    void removeEnrollments(const std::vector<int32_t>& enrollmentIds);
    std::tuple<AuthResult, int32_t, AuthToken> authenticate(int32_t enrollmentId);
    void resetLockout();
    bool checkIfLockoutCleared();

    std::vector<uint8_t> serialize() const;
    void save() const;

    static constexpr int kMaxEnrollmentsPerUser = 5;

    struct LockOut {
        enum class State {
            NO, TIMED, TIMED_LOCKED, PERMANENT
        };

        std::chrono::steady_clock::time_point nextAttempt;
        std::chrono::steady_clock::time_point expiration;
        int failedAttempts = 0;
        State state = State::NO;
    };

    const int32_t mSensorId;
    const int32_t mUserId;
    int64_t mAuthId = 0;
    int64_t mSecureUserId = 0;
    std::unordered_set<int32_t> mEnrollments;
    LockOut mLockOut;
};

} // namespace aidl::android::hardware::biometrics::fingerprint
