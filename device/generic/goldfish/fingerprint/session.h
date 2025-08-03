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
#include <memory>
#include <mutex>
#include <random>
#include <thread>
#include <unordered_set>
#include <aidl/android/hardware/biometrics/fingerprint/BnSession.h>
#include <aidl/android/hardware/biometrics/fingerprint/ISessionCallback.h>
#include <android-base/unique_fd.h>

#include "storage.h"

namespace aidl::android::hardware::biometrics::fingerprint {

struct Session : public BnSession {
    enum class State {
        IDLE,
        ENROLLING_START,
        ENROLLING_END,
        AUTHENTICATING,
        DETECTING_INTERACTION,
    };

    enum class ErrorCode {
        OK,
        E_HAT_MAC_EMPTY,
        E_HAT_WRONG_CHALLENGE,
        E_INCORRECT_STATE,
        E_ENROLL_FAILED,
    };

    Session(const int32_t sensorId, const int32_t userId,
            std::shared_ptr<ISessionCallback> scb);
    ~Session();

    ndk::ScopedAStatus generateChallenge() override;
    ndk::ScopedAStatus revokeChallenge(const int64_t challenge) override;
    ndk::ScopedAStatus enroll(const keymaster::HardwareAuthToken& hat,
                              std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus authenticate(const int64_t operationId,
                                    std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus detectInteraction(
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus enumerateEnrollments() override;
    ndk::ScopedAStatus removeEnrollments(const std::vector<int32_t>& enrollmentIds) override;
    ndk::ScopedAStatus getAuthenticatorId() override;
    ndk::ScopedAStatus invalidateAuthenticatorId() override;
    ndk::ScopedAStatus resetLockout(const keymaster::HardwareAuthToken& hat) override;
    ndk::ScopedAStatus close() override;

    int64_t generateInt64();
    ErrorCode validateHat(const keymaster::HardwareAuthToken& hat) const;
    bool sensorListenerFuncImpl();
    void sensorListenerFunc() { while (sensorListenerFuncImpl()) {} }
    void onSenserEventOn(int fid);
    void onSenserEventOff();
    void cancellEnroll();
    void cancellAuthenticate();
    void cancellDetectInteraction();

    ndk::ScopedAStatus onPointerDown(const int32_t /*pointerId*/,
                                     const int32_t /*x*/, const int32_t /*y*/,
                                     const float /*minor*/,
                                     const float /*major*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus onPointerUp(const int32_t /*pointerId*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus onUiReady() override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus authenticateWithContext(
            int64_t operationId, const common::OperationContext& /*context*/,
            std::shared_ptr<common::ICancellationSignal>* out) override {
        return authenticate(operationId, out);
    }
    ndk::ScopedAStatus enrollWithContext(
            const keymaster::HardwareAuthToken& hat,
            const common::OperationContext& /*context*/,
            std::shared_ptr<common::ICancellationSignal>* out) override {
        return enroll(hat, out);
    }
    ndk::ScopedAStatus detectInteractionWithContext(
            const common::OperationContext& /*context*/,
            std::shared_ptr<common::ICancellationSignal>* out) override {
        return detectInteraction(out);
    }
    ndk::ScopedAStatus onPointerDownWithContext(const PointerContext& /*context*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus onPointerUpWithContext(const PointerContext& /*context*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus onContextChanged(const common::OperationContext& /*context*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus onPointerCancelWithContext(const PointerContext& /*context*/) override {
        return ndk::ScopedAStatus::ok();
    }
    ndk::ScopedAStatus setIgnoreDisplayTouches(bool /*shouldIgnore*/) override {
        return ndk::ScopedAStatus::ok();
    }

    const std::shared_ptr<ISessionCallback> mSessionCb;
    Storage mStorage;                   // mMutex
    std::mt19937_64 mRandom;            // mMutex
    int64_t mEnrollingSecUserId = 0;    // mMutex
    int64_t mAuthChallenge = 0;         // mMutex
    ::android::base::unique_fd mCallerFd;
    ::android::base::unique_fd mSensorThreadFd;
    std::thread mSensorListener;
    std::unordered_set<int64_t> mChallenges;
    State mState = State::IDLE; // mMutex
    mutable std::mutex mMutex;
};

} // namespace aidl::android::hardware::biometrics::fingerprint
