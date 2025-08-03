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
#include <teeui/generic_operation.h>
#include <teeui/utils.h>

#include <secure_input/secure_input_proto.h>

#include "secure_input_tracker.h"
#include "trusty_confirmation_ui.h"
#include "trusty_time_stamper.h"

class TrustyOperation
        : public teeui::Operation<TrustyOperation,
                                  monotonic_time_stamper::TimeStamp> {
public:
    TrustyOperation()
            : Operation<TrustyOperation, monotonic_time_stamper::TimeStamp>() {}

    int handleMsg(void* msg,
                  uint32_t msglen,
                  void* reponse,
                  uint32_t* responselen);

    /*
     * teeui::Operation expects the following hooks to be implemented. See
     * teeui/generic_operation.h for more details.
     */
    teeui::ResponseCode initHook();
    void abortHook();
    void finalizeHook();
    teeui::ResponseCode testCommandHook(teeui::TestModeCommands testCmd);
    teeui::WriteStream extendedProtocolHook(teeui::Protocol proto,
                                            teeui::ReadStream in,
                                            teeui::WriteStream out);

    /*
     * teeui::Operation expects hmac256() and now() to be implemented.
     */
    static teeui::optional<teeui::Hmac> hmac256(
            const teeui::AuthTokenKey& key,
            std::initializer_list<teeui::ByteBufferProxy> buffers);
    using TimeStamp = monotonic_time_stamper::TimeStamp;
    static TimeStamp now() { return monotonic_time_stamper::now(); }

private:
    TrustyConfirmationUI gui_;
    InputTracker input_tracker_;
};
