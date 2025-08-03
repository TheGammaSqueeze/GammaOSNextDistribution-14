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

#include <secure_input/secure_input_proto.h>
#include <stdint.h>
#include "trusty_time_stamper.h"

#include <teeui/common_message_types.h>

class InputTracker {
public:
    enum class InputState : uint32_t {
        None,
        Fresh,
        HandshakeOutstanding,
        HandshakeComplete,
        InputDeliveredFinal,
        InputDeliveredMorePending,
        InputFetched,
        // insert new states above
        Count,
    };
    enum class InputEvent : uint32_t {
        None,
        UserCancel,
        UserConfirm,
    };

    InputTracker() : state_(InputState::None), event_(InputEvent::None) {}
    // new Session
    teeui::ResponseCode newSession();

    // input Handshake
    std::tuple<teeui::ResponseCode, secure_input::Nonce> beginHandshake();

    // input Handshake fianlize
    teeui::ResponseCode finalizeHandshake(
            const secure_input::Nonce& nCi,
            const secure_input::Signature& signature,
            const teeui::AuthTokenKey& key);

    // input event
    std::tuple<teeui::ResponseCode, secure_input::InputResponse>
    processInputEvent(secure_input::DTupKeyEvent keyEvent,
                      const secure_input::Signature& signature,
                      const teeui::AuthTokenKey& key);

    // fetch result
    teeui::ResponseCode fetchInputEvent();

    teeui::ResponseCode reportVerifiedInput(InputEvent event);
    void abort() {
        state_ = InputState::None;
        event_ = InputEvent::None;
    }

private:
    InputState state_;
    InputEvent event_;
    secure_input::Nonce input_nonce_;
    monotonic_time_stamper::TimeStamp timestamps_[uint32_t(InputState::Count)];
};
