/*
 * Copyright 2017 The Android Open Source Project
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

#ifndef TRUSTY_APP_KEYMASTER_TRUSTY_KEYMASTER_MESSAGES_H_
#define TRUSTY_APP_KEYMASTER_TRUSTY_KEYMASTER_MESSAGES_H_

#include <keymaster/android_keymaster_messages.h>

namespace keymaster {

static inline bool copy_keymaster_algorithm_from_buf(
        const uint8_t** buf_ptr,
        const uint8_t* end,
        keymaster_algorithm_t* state) {
    uint32_t val;
    if (copy_uint32_from_buf(buf_ptr, end, &val)) {
        switch (val) {
        case KM_ALGORITHM_RSA:
        case KM_ALGORITHM_EC:
        case KM_ALGORITHM_AES:
        case KM_ALGORITHM_TRIPLE_DES:
        case KM_ALGORITHM_HMAC:
            *state = static_cast<keymaster_algorithm_t>(val);
            return true;
        default:
            return false;
        }
    }

    return false;
}

static inline bool copy_keymaster_verified_boot_from_buf(
        const uint8_t** buf_ptr,
        const uint8_t* end,
        keymaster_verified_boot_t* state) {
    uint32_t val;
    if (copy_uint32_from_buf(buf_ptr, end, &val)) {
        switch (val) {
        case KM_VERIFIED_BOOT_VERIFIED:
        case KM_VERIFIED_BOOT_SELF_SIGNED:
        case KM_VERIFIED_BOOT_UNVERIFIED:
        case KM_VERIFIED_BOOT_FAILED:
            *state = static_cast<keymaster_verified_boot_t>(val);
            return true;
        default:
            return false;
        }
    }

    return false;
}

/**
 * Generic struct for Keymaster requests which hold a single raw buffer.
 */
struct RawBufferRequest : public KeymasterMessage {
    explicit RawBufferRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return data.SerializedSize(); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return data.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return data.Deserialize(buf_ptr, end);
    }

    Buffer data;
};

/**
 * Generic struct for Keymaster responses which hold a single raw buffer.
 */
struct RawBufferResponse : public KeymasterResponse {
    explicit RawBufferResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override {
        return data.SerializedSize();
    }
    uint8_t* NonErrorSerialize(uint8_t* buf,
                               const uint8_t* end) const override {
        return data.Serialize(buf, end);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr,
                             const uint8_t* end) override {
        return data.Deserialize(buf_ptr, end);
    }

    Buffer data;
};

struct SetBootParamsRequest : public KeymasterMessage {
    explicit SetBootParamsRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override {
        return (sizeof(os_version) + sizeof(os_patchlevel) +
                sizeof(device_locked) + sizeof(verified_boot_state) +
                verified_boot_key.SerializedSize() +
                verified_boot_hash.SerializedSize());
    }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, os_version);
        buf = append_uint32_to_buf(buf, end, os_patchlevel);
        buf = append_uint32_to_buf(buf, end, device_locked);
        buf = append_uint32_to_buf(buf, end, verified_boot_state);
        buf = verified_boot_key.Serialize(buf, end);
        return verified_boot_hash.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &os_version) &&
               copy_uint32_from_buf(buf_ptr, end, &os_patchlevel) &&
               copy_uint32_from_buf(buf_ptr, end, &device_locked) &&
               copy_keymaster_verified_boot_from_buf(buf_ptr, end,
                                                     &verified_boot_state) &&
               verified_boot_key.Deserialize(buf_ptr, end) &&
               verified_boot_hash.Deserialize(buf_ptr, end);
    }

    uint32_t os_version;
    uint32_t os_patchlevel;
    uint32_t device_locked;
    keymaster_verified_boot_t verified_boot_state;
    Buffer verified_boot_key;
    Buffer verified_boot_hash;
};

using SetBootParamsResponse = EmptyKeymasterResponse;
struct SetAttestationKeyRequest : public KeymasterMessage {
    explicit SetAttestationKeyRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override {
        return sizeof(uint32_t) + key_data.SerializedSize();
    }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, algorithm);
        return key_data.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_keymaster_algorithm_from_buf(buf_ptr, end, &algorithm) &&
               key_data.Deserialize(buf_ptr, end);
    }

    keymaster_algorithm_t algorithm;
    Buffer key_data;
};

using SetAttestationKeyResponse = EmptyKeymasterResponse;

struct ClearAttestationCertChainRequest : public KeymasterMessage {
    explicit ClearAttestationCertChainRequest(int32_t ver)
            : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint32_t); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, algorithm);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_keymaster_algorithm_from_buf(buf_ptr, end, &algorithm);
    }

    keymaster_algorithm_t algorithm;
};
using ClearAttestationCertChainResponse = EmptyKeymasterResponse;

struct AppendAttestationCertChainRequest : public KeymasterMessage {
    explicit AppendAttestationCertChainRequest(int32_t ver)
            : KeymasterMessage(ver) {}

    size_t SerializedSize() const override {
        return sizeof(uint32_t) + cert_data.SerializedSize();
    }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, algorithm);
        return cert_data.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_keymaster_algorithm_from_buf(buf_ptr, end, &algorithm) &&
               cert_data.Deserialize(buf_ptr, end);
    }

    keymaster_algorithm_t algorithm;
    Buffer cert_data;
};
using AppendAttestationCertChainResponse = EmptyKeymasterResponse;

/**
 * For Android Things Attestation Provisioning (ATAP), the GetCaRequest message
 * in the protocol are raw opaque messages for the purposes of this IPC call.
 * Since the SetCaResponse message will be very large (> 10k), SetCaResponse is
 * split into *Begin, *Update, and *Finish operations.
 */
using AtapGetCaRequestRequest = RawBufferRequest;
using AtapGetCaRequestResponse = RawBufferResponse;

struct AtapSetCaResponseBeginRequest : public KeymasterMessage {
    explicit AtapSetCaResponseBeginRequest(int32_t ver)
            : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint32_t); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, ca_response_size);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &ca_response_size);
    }

    uint32_t ca_response_size;
};
using AtapSetCaResponseBeginResponse = EmptyKeymasterResponse;

using AtapSetCaResponseUpdateRequest = RawBufferRequest;
using AtapSetCaResponseUpdateResponse = EmptyKeymasterResponse;

using AtapSetCaResponseFinishRequest = EmptyKeymasterRequest;
using AtapSetCaResponseFinishResponse = EmptyKeymasterResponse;

using AtapSetProductIdRequest = RawBufferRequest;
using AtapSetProductIdResponse = EmptyKeymasterResponse;

using AtapReadUuidRequest = EmptyKeymasterRequest;
using AtapReadUuidResponse = RawBufferResponse;

}  // namespace keymaster

#endif  // TRUSTY_APP_KEYMASTER_TRUSTY_KEYMASTER_MESSAGES_H_
