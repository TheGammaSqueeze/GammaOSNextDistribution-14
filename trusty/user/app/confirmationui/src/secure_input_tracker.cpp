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

#include "secure_input_tracker.h"
#include "trusty_operation.h"

#include <secure_input/secure_input_proto.h>

#include <lib/rng/trusty_rng.h>

#include <inttypes.h>
#include <stdio.h>

#include <teeui/utils.h>

#include <trusty_log.h>

#include <uapi/err.h>

#define TLOG_TAG "confirmationui"

using namespace secure_input;
using teeui::Array;
using teeui::AuthTokenKey;
using teeui::bytesCast;
using teeui::HMac;
using teeui::optional;
using teeui::ResponseCode;

inline auto mtsNow() {
    return monotonic_time_stamper::now();
}

static optional<Nonce> getNonce() {
    Nonce result;
    if (trusty_rng_secure_rand(result.data(), result.size()) == NO_ERROR) {
        return result;
    } else {
        return {};
    }
}

ResponseCode InputTracker::newSession() {
    state_ = InputState::Fresh;
    event_ = InputEvent::None;
    auto now = mtsNow();
    // Initialize all timestamps to something sane.
    for (auto& t : timestamps_) {
        t = now;
    }
    return ResponseCode::OK;
}

// input Handshake
std::tuple<ResponseCode, Nonce> InputTracker::beginHandshake() {
    ResponseCode rc;
    auto now = mtsNow();
    if ((state_ == InputState::Fresh &&
         (now - timestamps_[uint32_t(InputState::Fresh)]) >=
                 kUserPreInputGracePeriodMillis) ||
        state_ == InputState::InputDeliveredMorePending) {
        auto nonce = getNonce();
        if (nonce) {
            input_nonce_ = *nonce;
            TLOGD("%u", uint32_t(state_));
            state_ = InputState::HandshakeOutstanding;
            timestamps_[uint32_t(state_)] = now;
            return {ResponseCode::OK, input_nonce_};
        } else {
            rc = ResponseCode::SystemError;
        }
    }
    rc = ResponseCode::Unexpected;
    state_ = InputState::None;
    return {rc, {}};
}

// input Handshake finalize
ResponseCode InputTracker::finalizeHandshake(const Nonce& nCi,
                                             const Signature& signature,
                                             const AuthTokenKey& key) {
    ResponseCode rc;
    if (state_ == InputState::HandshakeOutstanding) {
        using HMacer = HMac<TrustyOperation>;
        auto hmac = HMacer::hmac256(key, kConfirmationUIHandshakeLabel,
                                    input_nonce_, nCi);
        if (hmac) {
            if (*hmac == signature) {
                // we can forget the nCo and input_nonce now becomes nCi
                input_nonce_ = nCi;
                state_ = InputState::HandshakeComplete;
                timestamps_[uint32_t(state_)] = mtsNow();
                TLOGD("%u", uint32_t(state_));
                return ResponseCode::OK;
            } else {
                rc = ResponseCode::Aborted;
            }
        } else {
            rc = ResponseCode::SystemError;
        }
    } else {
        rc = ResponseCode::Unexpected;
    }
    state_ = InputState::None;
    return rc;
}

// process input event
std::tuple<ResponseCode, InputResponse> InputTracker::processInputEvent(
        DTupKeyEvent keyEvent,
        const Signature& signature,
        const AuthTokenKey& key) {
    std::tuple<ResponseCode, InputResponse> result = {ResponseCode::OK,
                                                      InputResponse::TIMED_OUT};
    ResponseCode& rc = std::get<0>(result);
    InputResponse& ir = std::get<1>(result);
    using HMacer = HMac<TrustyOperation>;
    auto now = mtsNow();

    if (state_ != InputState::HandshakeComplete) {
        state_ = InputState::None;
        rc = ResponseCode::Unexpected;
        return result;
    }
    uint32_t keyEventBE = htobe32(static_cast<uint32_t>(keyEvent));
    auto hmac = HMacer::hmac256(key, kConfirmationUIEventLabel,
                                bytesCast(keyEventBE), input_nonce_);
    if (!hmac) {
        state_ = InputState::None;
        rc = ResponseCode::SystemError;
        return result;
    }

    if (!(*hmac == signature)) {
        state_ = InputState::None;
        rc = ResponseCode::Aborted;
        TLOGE("signature on input event did not check out");
        return result;
    }

    switch (keyEvent) {
    // fall through intended
    case DTupKeyEvent::VOL_DOWN:
    case DTupKeyEvent::VOL_UP:
        event_ = InputEvent::UserCancel;
        state_ = InputState::InputDeliveredFinal;
        ir = InputResponse::OK;
        break;
    case DTupKeyEvent::PWR:
        if (state_ == InputState::HandshakeComplete &&
            now - timestamps_[uint32_t(
                          InputState::InputDeliveredMorePending)] <=
                    kUserDoupleClickTimeoutMillis) {
            state_ = InputState::InputDeliveredFinal;
            ir = InputResponse::OK;
            event_ = InputEvent::UserConfirm;
        } else {
            state_ = InputState::InputDeliveredMorePending;
            ir = InputResponse::PENDING_MORE;
        }
        break;
    case DTupKeyEvent::RESERVED:
    default:
        TLOGW("got RESERVED event");
        rc = ResponseCode::Aborted;
        state_ = InputState::None;
        return result;
    }
    timestamps_[uint32_t(state_)] = now;
    TLOGD("%u", uint32_t(state_));
    return result;
}

ResponseCode InputTracker::fetchInputEvent() {
    if (state_ == InputState::InputDeliveredFinal) {
        state_ = InputState::InputFetched;
        if (event_ == InputEvent::UserConfirm)
            return ResponseCode::OK;
        else
            return ResponseCode::Canceled;
    } else {
        TLOGD("%u", uint32_t(state_));
        state_ = InputState::None;
        return ResponseCode::Unexpected;
    }
}

ResponseCode InputTracker::reportVerifiedInput(InputEvent event) {
    auto now = mtsNow();
    if (state_ == InputState::Fresh &&
        (now - timestamps_[uint32_t(InputState::Fresh)]) >=
                kUserPreInputGracePeriodMillis) {
        state_ = InputState::InputDeliveredFinal;
        event_ = event;
    }
    return ResponseCode::OK;
}
