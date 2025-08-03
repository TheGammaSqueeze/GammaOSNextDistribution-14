/*
 * Copyright 2021 The Android Open Source Project
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

#include <cppbor.h>
#include <cppbor_parse.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/cppcose/cppcose.h>

namespace keymaster {

// These are the negations of the actual error codes
constexpr keymaster_error_t kStatusFailed = static_cast<keymaster_error_t>(-1);
constexpr keymaster_error_t kStatusInvalidMac = static_cast<keymaster_error_t>(-2);
constexpr keymaster_error_t kStatusProductionKeyInTestRequest = static_cast<keymaster_error_t>(-3);
constexpr keymaster_error_t kStatusTestKeyInProductionRequest = static_cast<keymaster_error_t>(-4);
constexpr keymaster_error_t kStatusInvalidEek = static_cast<keymaster_error_t>(-5);
constexpr keymaster_error_t kStatusRemoved = static_cast<keymaster_error_t>(-6);

template <typename T> class StatusOr {
  public:
    StatusOr(int32_t status_code)  // NOLINT(google-explicit-constructor)
        : status_code_(status_code) {}
    StatusOr(T val)  // NOLINT(google-explicit-constructor)
        : status_code_(0), value_(std::move(val)) {}

    bool isOk() { return status_code_ == 0; }

    T* operator->() & {
        assert(isOk());
        return &value_.value();
    }
    T& operator*() & {
        assert(isOk());
        return value_.value();
    }
    T&& operator*() && {
        assert(isOk());
        return std::move(value_).value();
    }

    int32_t moveError() {
        assert(!isOk());
        return status_code_;
    }

    T moveValue() { return std::move(value_).value(); }

  private:
    int32_t status_code_;
    std::optional<T> value_;
};

StatusOr<std::pair<std::vector<uint8_t> /* EEK pub */, std::vector<uint8_t> /* EEK ID */>>
validateAndExtractEekPubAndId(bool testMode, const KeymasterBlob& endpointEncryptionCertChain);

StatusOr<cppbor::Array /* pubkeys */>
validateAndExtractPubkeys(bool testMode, uint32_t numKeys, KeymasterBlob* keysToSign,
                          const cppcose::HmacSha256Function& macFunction);

cppbor::Array buildCertReqRecipients(const std::vector<uint8_t>& pubkey,
                                     const std::vector<uint8_t>& kid);

}  // namespace keymaster
