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

#include "trusty_operation.h"
#include <secure_input/secure_input_proto.h>
#include <teeui/msg_formatting.h>

#include <stdio.h>

#include <openssl/hmac.h>
#include <openssl/sha.h>

#include <trusty_log.h>

#define TLOG_TAG "confirmationui"

using teeui::AuthTokenKey;
using teeui::ByteBufferProxy;
using teeui::Hmac;
using teeui::optional;
using teeui::Protocol;
using teeui::read;
using teeui::ReadStream;
using teeui::ResponseCode;
using teeui::write;
using teeui::WriteStream;

using teeui::Command;
using teeui::Message;
using teeui::TestModeCommands;

using secure_input::InputResponse;

optional<Hmac> TrustyOperation::hmac256(
        const AuthTokenKey& key,
        std::initializer_list<ByteBufferProxy> buffers) {
    HMAC_CTX hmacCtx;
    HMAC_CTX_init(&hmacCtx);
    if (!HMAC_Init_ex(&hmacCtx, key.data(), key.size(), EVP_sha256(),
                      nullptr)) {
        return {};
    }
    for (auto& buffer : buffers) {
        if (!HMAC_Update(&hmacCtx, buffer.data(), buffer.size())) {
            return {};
        }
    }
    Hmac result;
    if (!HMAC_Final(&hmacCtx, result.data(), nullptr)) {
        return {};
    }
    return result;
}

int TrustyOperation::handleMsg(void* msg,
                               uint32_t msglen,
                               void* reponse,
                               uint32_t* responselen) {
    ReadStream in(reinterpret_cast<uint8_t*>(msg), msglen);
    WriteStream out(reinterpret_cast<uint8_t*>(reponse), *responselen);

    TLOGI("proto: %u cmd: %u\n", reinterpret_cast<uint32_t*>(msg)[0],
          reinterpret_cast<uint32_t*>(msg)[1]);

    auto result = dispatchCommandMessage(in, out);
    if (!result) {
        /*
         * We failed to serialize the response,
         * Make an attempt to write an error code to the stream, indicating that
         * serialization failed.
         */
        TLOGE("response buffer to small\n");
        result = write(Message<ResponseCode>(), out, ResponseCode::SystemError);
    }
    *responselen = result.pos() - reinterpret_cast<uint8_t*>(reponse);
    return 0;
}

ResponseCode TrustyOperation::initHook() {
    auto rc = gui_.start(getPrompt().data(), languageIdBuffer_,
                         invertedColorModeRequested_, maginifiedViewRequested_);
    if (rc != ResponseCode::OK) {
        TLOGE("GUI start returned: %d\n", rc);
    } else {
        input_tracker_.newSession();
    }
    TLOGI("initHook: %u\n", rc);
    return rc;
}

void TrustyOperation::abortHook() {
    input_tracker_.abort();
    gui_.stop();
}

void TrustyOperation::finalizeHook() {
    gui_.stop();
}

ResponseCode TrustyOperation::testCommandHook(TestModeCommands testCmd) {
    switch (testCmd) {
    case TestModeCommands::OK_EVENT:
        return input_tracker_.reportVerifiedInput(
                InputTracker::InputEvent::UserConfirm);
    case TestModeCommands::CANCEL_EVENT:
        return input_tracker_.reportVerifiedInput(
                InputTracker::InputEvent::UserCancel);
    default:
        /* we don't want to veto any unknown test commands. */
        return ResponseCode::OK;
    }
}

WriteStream TrustyOperation::extendedProtocolHook(Protocol proto,
                                                  ReadStream in,
                                                  WriteStream out) {
    using namespace secure_input;
    if (proto != kSecureInputProto) {
        /* this write ResponseCodeU::Unimplemented to the output stream */
        return this->Operation::extendedProtocolHook(proto, in, out);
    }
    auto [in_cmd, cmd] = teeui::readCmd<SecureInputCommand>(in);
    switch (cmd) {
    case SecureInputCommand::InputHandshake: {
        auto [rc, nonce] = input_tracker_.beginHandshake();
        if (rc != ResponseCode::OK) {
            TLOGE("beginHandshake failed\n");
            abort();
        } else if ((rc = gui_.showInstructions(true /*enable*/)) !=
                   ResponseCode::OK) {
            TLOGE("showInstructions failed\n");
            abort();
        }
        return write(InputHandshakeResponse(), out, rc, nonce);
    }
    case SecureInputCommand::FinalizeInputSession: {
        auto [in_msg, nCi, signature] =
                read(FinalizeInputSessionHandshake(), in_cmd);
        auto rc = ResponseCode::Unexpected;
        if (in_msg) {
            rc = input_tracker_.finalizeHandshake(nCi, signature, *hmacKey());
        } else {
            TLOGE("Message Parse Error\n");
        }
        if (rc != ResponseCode::OK)
            abort();
        return write(FinalizeInputSessionHandshakeResponse(), out, rc);
    }
    case SecureInputCommand::DeliverInputEvent: {
        auto [in_msg, event, signature] = read(DeliverInputEvent(), in_cmd);
        InputResponse ir;
        auto rc = ResponseCode::Unexpected;
        if (in_msg) {
            std::tie(rc, ir) = input_tracker_.processInputEvent(
                    event, signature, *hmacKey());
        }
        if (rc != ResponseCode::OK)
            abort();
        else if (ir == InputResponse::OK) {
            switch (input_tracker_.fetchInputEvent()) {
            case ResponseCode::OK:
                signConfirmation(*hmacKey());
                break;
            case ResponseCode::Canceled:
                userCancel();
                break;
            default:
                break;
            }
        }
        return write(DeliverInputEventResponse(), out, rc, ir);
    }
    case SecureInputCommand::Invalid:
    default:
        return write(Message<ResponseCode>(), out, ResponseCode::Unimplemented);
    }
}
