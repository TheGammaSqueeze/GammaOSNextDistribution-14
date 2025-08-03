/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <keymaster/UniquePtr.h>

#include <gtest/gtest.h>

#include <keymaster/android_keymaster.h>
#include <keymaster/android_keymaster_utils.h>
#include <keymaster/keymaster_tags.h>

#include "android_keymaster_test_utils.h"
#include "fuzzers/serializable_types.h"

namespace keymaster {
namespace test {

/**
 * Serialize and deserialize a message.
 */
template <typename Message>
Message* round_trip(int32_t ver, const Message& message, size_t expected_size) {
    size_t size = message.SerializedSize();
    EXPECT_EQ(expected_size, size);
    if (size == 0) return nullptr;

    UniquePtr<uint8_t[]> buf(new uint8_t[size]);
    EXPECT_EQ(buf.get() + size, message.Serialize(buf.get(), buf.get() + size));

    Message* deserialized = new Message(ver);
    const uint8_t* p = buf.get();
    EXPECT_TRUE(deserialized->Deserialize(&p, p + size));
    EXPECT_EQ((ptrdiff_t)size, p - buf.get());
    return deserialized;
}

TEST(RoundTrip, EmptyKeymasterResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        EmptyKeymasterResponse msg(ver);
        msg.error = KM_ERROR_OK;

        UniquePtr<EmptyKeymasterResponse> deserialized(round_trip(ver, msg, 4));
    }
}

TEST(RoundTrip, EmptyKeymasterResponseError) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        EmptyKeymasterResponse msg(ver);
        msg.error = KM_ERROR_MEMORY_ALLOCATION_FAILED;

        UniquePtr<EmptyKeymasterResponse> deserialized(round_trip(ver, msg, 4));
    }
}

static keymaster_key_param_t params[] = {
    Authorization(TAG_PURPOSE, KM_PURPOSE_SIGN),
    Authorization(TAG_PURPOSE, KM_PURPOSE_VERIFY),
    Authorization(TAG_ALGORITHM, KM_ALGORITHM_RSA),
    Authorization(TAG_USER_ID, 7),
    Authorization(TAG_USER_AUTH_TYPE, HW_AUTH_PASSWORD),
    Authorization(TAG_APPLICATION_ID, "app_id", 6),
    Authorization(TAG_AUTH_TIMEOUT, 300),
};
uint8_t TEST_DATA[] = "a key blob";

TEST(RoundTrip, GenerateKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateKeyRequest req(ver);
        req.key_description.Reinitialize(params, array_length(params));
        req.attestation_signing_key_blob =
            KeymasterKeyBlob(reinterpret_cast<const uint8_t*>("foo"), 3);
        req.attest_key_params.Reinitialize(params, array_length(params));
        req.issuer_subject = KeymasterBlob(reinterpret_cast<const uint8_t*>("bar"), 3);

        UniquePtr<GenerateKeyRequest> deserialized(round_trip(ver, req, ver < 4 ? 78 : 170));
        EXPECT_EQ(deserialized->key_description, req.key_description);
        if (ver < 4) {
            EXPECT_EQ(0U, deserialized->attestation_signing_key_blob.key_material_size);
        } else {
            EXPECT_EQ(3U, deserialized->attestation_signing_key_blob.key_material_size);
            EXPECT_EQ(0, memcmp(req.attestation_signing_key_blob.key_material,
                                deserialized->attestation_signing_key_blob.key_material,
                                deserialized->attestation_signing_key_blob.key_material_size));
            EXPECT_EQ(deserialized->attest_key_params, req.attest_key_params);
            EXPECT_EQ(0, memcmp(req.issuer_subject.data, deserialized->issuer_subject.data,
                                deserialized->issuer_subject.data_length));
        }
    }
}

TEST(RoundTrip, GenerateKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateKeyResponse rsp(ver);
        rsp.error = KM_ERROR_OK;
        rsp.key_blob.key_material = dup_array(TEST_DATA);
        rsp.key_blob.key_material_size = array_length(TEST_DATA);
        rsp.enforced.Reinitialize(params, array_length(params));

        rsp.certificate_chain = CertificateChain(3);
        rsp.certificate_chain.entries[0] = {dup_buffer("foo", 3), 3};
        rsp.certificate_chain.entries[1] = {dup_buffer("bar", 3), 3};
        rsp.certificate_chain.entries[2] = {dup_buffer("baz", 3), 3};

        UniquePtr<GenerateKeyResponse> deserialized;
        if (ver < 4) {
            deserialized.reset(round_trip(ver, rsp, 109));
        } else {
            deserialized.reset(round_trip(ver, rsp, 134));
        }

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(deserialized->enforced, rsp.enforced);
        EXPECT_EQ(deserialized->unenforced, rsp.unenforced);

        keymaster_cert_chain_t* chain = &deserialized->certificate_chain;
        if (ver < 4) {
            EXPECT_EQ(nullptr, chain->entries);
        } else {
            EXPECT_NE(nullptr, chain->entries);
            EXPECT_EQ(3U, chain->entry_count);
            EXPECT_EQ(3U, chain->entries[0].data_length);
            EXPECT_EQ(0, memcmp("foo", chain->entries[0].data, 3));
            EXPECT_EQ(3U, chain->entries[1].data_length);
            EXPECT_EQ(0, memcmp("bar", chain->entries[1].data, 3));
            EXPECT_EQ(3U, chain->entries[2].data_length);
            EXPECT_EQ(0, memcmp("baz", chain->entries[2].data, 3));
        }
    }
}

TEST(RoundTrip, GenerateKeyResponseTestError) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateKeyResponse rsp(ver);
        rsp.error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        rsp.key_blob.key_material = dup_array(TEST_DATA);
        rsp.key_blob.key_material_size = array_length(TEST_DATA);
        rsp.enforced.Reinitialize(params, array_length(params));

        UniquePtr<GenerateKeyResponse> deserialized(round_trip(ver, rsp, 4));
        EXPECT_EQ(KM_ERROR_UNSUPPORTED_ALGORITHM, deserialized->error);
        EXPECT_EQ(0U, deserialized->enforced.size());
        EXPECT_EQ(0U, deserialized->unenforced.size());
        EXPECT_EQ(0U, deserialized->key_blob.key_material_size);
    }
}

TEST(RoundTrip, GenerateRkpKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateRkpKeyRequest req(ver);
        req.test_mode = true;

        UniquePtr<GenerateRkpKeyRequest> deserialized(round_trip(ver, req, 1));
        EXPECT_EQ(deserialized->test_mode, req.test_mode);
    }
}

TEST(RoundTrip, GenerateRkpKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateRkpKeyResponse rsp(ver);
        rsp.error = KM_ERROR_OK;
        rsp.key_blob.key_material = dup_array(TEST_DATA);
        rsp.key_blob.key_material_size = array_length(TEST_DATA);
        rsp.maced_public_key.data = dup_array(TEST_DATA);
        rsp.maced_public_key.data_length = array_length(TEST_DATA);

        UniquePtr<GenerateRkpKeyResponse> deserialized;
        deserialized.reset(round_trip(ver, rsp, 34));

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(deserialized->key_blob.key_material_size, rsp.key_blob.key_material_size);
        EXPECT_EQ(0, std::memcmp(deserialized->key_blob.key_material, rsp.key_blob.key_material,
                                 deserialized->key_blob.key_material_size));
        EXPECT_EQ(deserialized->maced_public_key.data_length, rsp.maced_public_key.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->maced_public_key.data, rsp.maced_public_key.data,
                                 deserialized->maced_public_key.data_length));
    }
}

TEST(RoundTrip, GenerateCsrRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateCsrRequest req(ver);
        req.test_mode = true;
        req.num_keys = 2;
        req.keys_to_sign_array = new KeymasterBlob[req.num_keys];
        for (size_t i = 0; i < req.num_keys; i++) {
            req.SetKeyToSign(i, dup_array(TEST_DATA), array_length(TEST_DATA));
        }
        req.SetEndpointEncCertChain(dup_array(TEST_DATA), array_length(TEST_DATA));
        req.SetChallenge(dup_array(TEST_DATA), array_length(TEST_DATA));
        UniquePtr<GenerateCsrRequest> deserialized(round_trip(ver, req, 65));
        EXPECT_EQ(deserialized->test_mode, req.test_mode);
        EXPECT_EQ(deserialized->num_keys, req.num_keys);
        for (int i = 0; i < (int)req.num_keys; i++) {
            EXPECT_EQ(deserialized->keys_to_sign_array[i].data_length,
                      req.keys_to_sign_array[i].data_length);
            EXPECT_EQ(0, std::memcmp(deserialized->keys_to_sign_array[i].data,
                                     req.keys_to_sign_array[i].data,
                                     req.keys_to_sign_array[i].data_length));
        }
        EXPECT_EQ(deserialized->endpoint_enc_cert_chain.data_length,
                  req.endpoint_enc_cert_chain.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->endpoint_enc_cert_chain.data,
                                 req.endpoint_enc_cert_chain.data,
                                 req.endpoint_enc_cert_chain.data_length));
        EXPECT_EQ(deserialized->challenge.data_length, req.challenge.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->challenge.data, req.challenge.data,
                                 req.challenge.data_length));
    }
}

TEST(RoundTrip, GenerateCsrResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateCsrResponse rsp(ver);
        rsp.error = KM_ERROR_OK;
        rsp.keys_to_sign_mac.data = dup_array(TEST_DATA);
        rsp.keys_to_sign_mac.data_length = array_length(TEST_DATA);
        rsp.device_info_blob.data = dup_array(TEST_DATA);
        rsp.device_info_blob.data_length = array_length(TEST_DATA);
        rsp.protected_data_blob.data = dup_array(TEST_DATA);
        rsp.protected_data_blob.data_length = array_length(TEST_DATA);

        UniquePtr<GenerateCsrResponse> deserialized;
        deserialized.reset(round_trip(ver, rsp, 49));

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(deserialized->keys_to_sign_mac.data_length, rsp.keys_to_sign_mac.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->keys_to_sign_mac.data, rsp.keys_to_sign_mac.data,
                                 deserialized->keys_to_sign_mac.data_length));
        EXPECT_EQ(deserialized->device_info_blob.data_length, rsp.device_info_blob.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->device_info_blob.data, rsp.device_info_blob.data,
                                 deserialized->device_info_blob.data_length));
        EXPECT_EQ(deserialized->protected_data_blob.data_length,
                  rsp.protected_data_blob.data_length);
        EXPECT_EQ(0,
                  std::memcmp(deserialized->protected_data_blob.data, rsp.protected_data_blob.data,
                              deserialized->protected_data_blob.data_length));
    }
}

TEST(RoundTrip, GenerateCsrV2Request) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateCsrV2Request req(ver);
        EXPECT_TRUE(req.InitKeysToSign(2));
        for (size_t i = 0; i < req.num_keys; i++) {
            req.SetKeyToSign(i, dup_array(TEST_DATA), array_length(TEST_DATA));
        }
        req.SetChallenge(dup_array(TEST_DATA), array_length(TEST_DATA));
        UniquePtr<GenerateCsrV2Request> deserialized(round_trip(ver, req, 49));
        EXPECT_EQ(deserialized->num_keys, req.num_keys);
        for (int i = 0; i < (int)req.num_keys; i++) {
            EXPECT_EQ(deserialized->keys_to_sign_array[i].data_length,
                      req.keys_to_sign_array[i].data_length);
            EXPECT_EQ(0, std::memcmp(deserialized->keys_to_sign_array[i].data,
                                     req.keys_to_sign_array[i].data,
                                     req.keys_to_sign_array[i].data_length));
        }
        EXPECT_EQ(deserialized->challenge.data_length, req.challenge.data_length);
        EXPECT_EQ(0, std::memcmp(deserialized->challenge.data, req.challenge.data,
                                 req.challenge.data_length));
    }
}

TEST(RoundTrip, GenerateCsrV2Response) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateCsrV2Response rsp(ver);
        rsp.error = KM_ERROR_OK;
        rsp.csr.data = dup_array(TEST_DATA);
        rsp.csr.data_length = array_length(TEST_DATA);

        UniquePtr<GenerateCsrV2Response> deserialized;
        deserialized.reset(round_trip(ver, rsp, 19));

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(deserialized->csr.data_length, rsp.csr.data_length);
        EXPECT_EQ(0,
                  std::memcmp(deserialized->csr.data, rsp.csr.data, deserialized->csr.data_length));
    }
}

TEST(RoundTrip, GetKeyCharacteristicsRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GetKeyCharacteristicsRequest req(ver);
        req.additional_params.Reinitialize(params, array_length(params));
        req.SetKeyMaterial("foo", 3);

        UniquePtr<GetKeyCharacteristicsRequest> deserialized(round_trip(ver, req, 85));
        EXPECT_EQ(7U, deserialized->additional_params.size());
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp(deserialized->key_blob.key_material, "foo", 3));
    }
}

TEST(RoundTrip, GetKeyCharacteristicsResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GetKeyCharacteristicsResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.enforced.Reinitialize(params, array_length(params));
        msg.unenforced.Reinitialize(params, array_length(params));

        UniquePtr<GetKeyCharacteristicsResponse> deserialized(round_trip(ver, msg, 160));
        EXPECT_EQ(msg.enforced, deserialized->enforced);
        EXPECT_EQ(msg.unenforced, deserialized->unenforced);
    }
}

TEST(RoundTrip, BeginOperationRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        BeginOperationRequest msg(ver);
        msg.purpose = KM_PURPOSE_SIGN;
        msg.SetKeyMaterial("foo", 3);
        msg.additional_params.Reinitialize(params, array_length(params));

        UniquePtr<BeginOperationRequest> deserialized(round_trip(ver, msg, 89));
        EXPECT_EQ(KM_PURPOSE_SIGN, deserialized->purpose);
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp(deserialized->key_blob.key_material, "foo", 3));
        EXPECT_EQ(msg.additional_params, deserialized->additional_params);
    }
}

TEST(RoundTrip, BeginOperationResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        BeginOperationResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.op_handle = 0xDEADBEEF;
        msg.output_params.push_back(Authorization(TAG_NONCE, "foo", 3));

        UniquePtr<BeginOperationResponse> deserialized;
        switch (ver) {
        case 0:
            deserialized.reset(round_trip(ver, msg, 12));
            break;
        case 1:
        case 2:
        case 3:
        case 4:
            deserialized.reset(round_trip(ver, msg, 39));
            break;
        default:
            FAIL();
        }

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(0xDEADBEEF, deserialized->op_handle);

        switch (ver) {
        case 0:
            EXPECT_EQ(0U, deserialized->output_params.size());
            break;
        case 1:
        case 2:
        case 3:
        case 4:
            EXPECT_EQ(msg.output_params, deserialized->output_params);
            break;
        default:
            FAIL();
        }
    }
}

TEST(RoundTrip, BeginOperationResponseError) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        BeginOperationResponse msg(ver);
        msg.error = KM_ERROR_INVALID_OPERATION_HANDLE;
        msg.op_handle = 0xDEADBEEF;

        UniquePtr<BeginOperationResponse> deserialized(round_trip(ver, msg, 4));
        EXPECT_EQ(KM_ERROR_INVALID_OPERATION_HANDLE, deserialized->error);
    }
}

TEST(RoundTrip, UpdateOperationRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        UpdateOperationRequest msg(ver);
        msg.op_handle = 0xDEADBEEF;
        msg.input.Reinitialize("foo", 3);

        UniquePtr<UpdateOperationRequest> deserialized;
        switch (ver) {
        case 0:
            deserialized.reset(round_trip(ver, msg, 15));
            break;
        case 1:
        case 2:
        case 3:
        case 4:
            deserialized.reset(round_trip(ver, msg, 27));
            break;
        default:
            FAIL();
        }
        EXPECT_EQ(3U, deserialized->input.available_read());
        EXPECT_EQ(0, memcmp(deserialized->input.peek_read(), "foo", 3));
    }
}

TEST(RoundTrip, UpdateOperationResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        UpdateOperationResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.output.Reinitialize("foo", 3);
        msg.input_consumed = 99;
        msg.output_params.push_back(TAG_APPLICATION_ID, "bar", 3);

        UniquePtr<UpdateOperationResponse> deserialized;
        switch (ver) {
        case 0:
            deserialized.reset(round_trip(ver, msg, 11));
            break;
        case 1:
            deserialized.reset(round_trip(ver, msg, 15));
            break;
        case 2:
        case 3:
        case 4:
            deserialized.reset(round_trip(ver, msg, 42));
            break;
        default:
            FAIL();
        }
        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(3U, deserialized->output.available_read());
        EXPECT_EQ(0, memcmp(deserialized->output.peek_read(), "foo", 3));

        switch (ver) {
        case 0:
            EXPECT_EQ(0U, deserialized->input_consumed);
            break;
        case 1:
            EXPECT_EQ(99U, deserialized->input_consumed);
            break;
        case 2:
        case 3:
        case 4:
            EXPECT_EQ(99U, deserialized->input_consumed);
            EXPECT_EQ(1U, deserialized->output_params.size());
            break;
        default:
            FAIL();
        }
    }
}

TEST(RoundTrip, FinishOperationRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        FinishOperationRequest msg(ver);
        msg.op_handle = 0xDEADBEEF;
        msg.signature.Reinitialize("bar", 3);
        msg.input.Reinitialize("baz", 3);

        UniquePtr<FinishOperationRequest> deserialized;
        switch (ver) {
        case 0:
            deserialized.reset(round_trip(ver, msg, 15));
            break;
        case 1:
        case 2:
            deserialized.reset(round_trip(ver, msg, 27));
            break;
        case 3:
        case 4:
            deserialized.reset(round_trip(ver, msg, 34));
            break;
        default:
            FAIL();
        }
        EXPECT_EQ(0xDEADBEEF, deserialized->op_handle);
        EXPECT_EQ(3U, deserialized->signature.available_read());
        EXPECT_EQ(0, memcmp(deserialized->signature.peek_read(), "bar", 3));
    }
}

TEST(Round_Trip, FinishOperationResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        FinishOperationResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.output.Reinitialize("foo", 3);

        UniquePtr<FinishOperationResponse> deserialized;
        switch (ver) {
        case 0:
        case 1:
            deserialized.reset(round_trip(ver, msg, 11));
            break;
        case 2:
        case 3:
        case 4:
            deserialized.reset(round_trip(ver, msg, 23));
            break;
        default:
            FAIL();
        }
        EXPECT_EQ(msg.error, deserialized->error);
        EXPECT_EQ(msg.output.available_read(), deserialized->output.available_read());
        EXPECT_EQ(0, memcmp(msg.output.peek_read(), deserialized->output.peek_read(),
                            msg.output.available_read()));
    }
}

TEST(RoundTrip, ImportKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ImportKeyRequest msg(ver);
        msg.key_description.Reinitialize(params, array_length(params));
        msg.key_format = KM_KEY_FORMAT_X509;
        msg.key_data = KeymasterKeyBlob(reinterpret_cast<const uint8_t*>("foo"), 3);
        msg.attestation_signing_key_blob =
            KeymasterKeyBlob(reinterpret_cast<const uint8_t*>("bar"), 3);
        msg.attest_key_params.Reinitialize(params, array_length(params));
        msg.issuer_subject = KeymasterBlob(reinterpret_cast<const uint8_t*>("bar"), 3);

        UniquePtr<ImportKeyRequest> deserialized(round_trip(ver, msg, ver < 4 ? 89 : 181));
        EXPECT_EQ(msg.key_description, deserialized->key_description);
        EXPECT_EQ(msg.key_format, deserialized->key_format);
        EXPECT_EQ(msg.key_data.key_material_size, deserialized->key_data.key_material_size);
        EXPECT_EQ(0, memcmp(msg.key_data.key_material, deserialized->key_data.key_material,
                            msg.key_data.key_material_size));
        if (ver < 4) {
            EXPECT_EQ(0U, deserialized->attestation_signing_key_blob.key_material_size);
        } else {
            EXPECT_EQ(3U, deserialized->attestation_signing_key_blob.key_material_size);
            EXPECT_EQ(0, memcmp(msg.attestation_signing_key_blob.key_material,
                                deserialized->attestation_signing_key_blob.key_material,
                                msg.attestation_signing_key_blob.key_material_size));
            EXPECT_EQ(deserialized->attest_key_params, msg.attest_key_params);
            EXPECT_EQ(0, memcmp(msg.issuer_subject.data, deserialized->issuer_subject.data,
                                deserialized->issuer_subject.data_length));
        }
    }
}

TEST(RoundTrip, ImportKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ImportKeyResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.SetKeyMaterial("foo", 3);
        msg.enforced.Reinitialize(params, array_length(params));
        msg.unenforced.Reinitialize(params, array_length(params));

        msg.certificate_chain = CertificateChain(3);
        msg.certificate_chain.entries[0] = {dup_buffer("foo", 3), 3};
        msg.certificate_chain.entries[1] = {dup_buffer("bar", 3), 3};
        msg.certificate_chain.entries[2] = {dup_buffer("baz", 3), 3};

        UniquePtr<ImportKeyResponse> deserialized;
        if (ver < 4) {
            deserialized.reset(round_trip(ver, msg, 167));
        } else {
            deserialized.reset(round_trip(ver, msg, 192));
        }

        EXPECT_EQ(msg.error, deserialized->error);
        EXPECT_EQ(msg.key_blob.key_material_size, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp(msg.key_blob.key_material, deserialized->key_blob.key_material,
                            msg.key_blob.key_material_size));
        EXPECT_EQ(msg.enforced, deserialized->enforced);
        EXPECT_EQ(msg.unenforced, deserialized->unenforced);

        keymaster_cert_chain_t* chain = &deserialized->certificate_chain;
        if (ver < 4) {
            EXPECT_EQ(nullptr, chain->entries);
        } else {
            EXPECT_NE(nullptr, chain->entries);
            EXPECT_EQ(3U, chain->entry_count);
            EXPECT_EQ(3U, chain->entries[0].data_length);
            EXPECT_EQ(0, memcmp("foo", chain->entries[0].data, 3));
            EXPECT_EQ(3U, chain->entries[1].data_length);
            EXPECT_EQ(0, memcmp("bar", chain->entries[1].data, 3));
            EXPECT_EQ(3U, chain->entries[2].data_length);
            EXPECT_EQ(0, memcmp("baz", chain->entries[2].data, 3));
        }
    }
}

TEST(RoundTrip, ExportKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ExportKeyRequest msg(ver);
        msg.additional_params.Reinitialize(params, array_length(params));
        msg.key_format = KM_KEY_FORMAT_X509;
        msg.SetKeyMaterial("foo", 3);

        UniquePtr<ExportKeyRequest> deserialized(round_trip(ver, msg, 89));
        EXPECT_EQ(msg.additional_params, deserialized->additional_params);
        EXPECT_EQ(msg.key_format, deserialized->key_format);
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp("foo", deserialized->key_blob.key_material, 3));
    }
}

TEST(RoundTrip, ExportKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ExportKeyResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.SetKeyMaterial("foo", 3);

        UniquePtr<ExportKeyResponse> deserialized(round_trip(ver, msg, 11));
        EXPECT_EQ(3U, deserialized->key_data_length);
        EXPECT_EQ(0, memcmp("foo", deserialized->key_data, 3));
    }
}

TEST(RoundTrip, DeleteKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        DeleteKeyRequest msg(ver);
        msg.SetKeyMaterial("foo", 3);

        UniquePtr<DeleteKeyRequest> deserialized(round_trip(ver, msg, 7));
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp("foo", deserialized->key_blob.key_material, 3));
    }
}

TEST(RoundTrip, DeleteAllKeysRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        DeleteAllKeysRequest msg(ver);
        UniquePtr<DeleteAllKeysRequest> deserialized(round_trip(ver, msg, 0));
    }
}

TEST(RoundTrip, GetVersionRequest) {
    GetVersionRequest msg(0);

    size_t size = msg.SerializedSize();
    ASSERT_EQ(0U, size);

    UniquePtr<uint8_t[]> buf(new uint8_t[size]);
    EXPECT_EQ(buf.get() + size, msg.Serialize(buf.get(), buf.get() + size));

    GetVersionRequest deserialized;
    const uint8_t* p = buf.get();
    EXPECT_TRUE(deserialized.Deserialize(&p, p + size));
    EXPECT_EQ((ptrdiff_t)size, p - buf.get());
}

TEST(RoundTrip, GetVersionResponse) {
    GetVersionResponse msg(0);
    msg.error = KM_ERROR_OK;
    msg.major_ver = 9;
    msg.minor_ver = 98;
    msg.subminor_ver = 38;

    size_t size = msg.SerializedSize();
    ASSERT_EQ(7U, size);

    UniquePtr<uint8_t[]> buf(new uint8_t[size]);
    EXPECT_EQ(buf.get() + size, msg.Serialize(buf.get(), buf.get() + size));

    GetVersionResponse deserialized;
    const uint8_t* p = buf.get();
    EXPECT_TRUE(deserialized.Deserialize(&p, p + size));
    EXPECT_EQ((ptrdiff_t)size, p - buf.get());
    EXPECT_EQ(9U, msg.major_ver);
    EXPECT_EQ(98U, msg.minor_ver);
    EXPECT_EQ(38U, msg.subminor_ver);
}

TEST(RoundTrip, GetVersion2Request) {
    GetVersion2Request msg;

    msg.max_message_version = 0xDEADBEEF;
    size_t size = msg.SerializedSize();
    ASSERT_EQ(4U, size);

    UniquePtr<uint8_t[]> buf(new uint8_t[size]);
    EXPECT_EQ(buf.get() + size, msg.Serialize(buf.get(), buf.get() + size));

    GetVersion2Request deserialized;
    const uint8_t* p = buf.get();
    EXPECT_TRUE(deserialized.Deserialize(&p, p + size));
    EXPECT_EQ((ptrdiff_t)size, p - buf.get());
    EXPECT_EQ(0xDEADBEEF, msg.max_message_version);
}

TEST(RoundTrip, GetVersion2Response) {
    GetVersion2Response msg;
    msg.error = KM_ERROR_OK;
    msg.km_version = KmVersion::KEYMINT_1;
    msg.km_date = 20121900;

    size_t size = msg.SerializedSize();
    ASSERT_EQ(16U, size);

    UniquePtr<uint8_t[]> buf(new uint8_t[size]);
    EXPECT_EQ(buf.get() + size, msg.Serialize(buf.get(), buf.get() + size));

    GetVersion2Response deserialized;
    const uint8_t* p = buf.get();
    EXPECT_TRUE(deserialized.Deserialize(&p, p + size));
    EXPECT_EQ((ptrdiff_t)size, p - buf.get());
    EXPECT_EQ(KmVersion::KEYMINT_1, msg.km_version);
    EXPECT_EQ(20121900U, msg.km_date);
}

TEST(RoundTrip, ConfigureRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureRequest req(ver);
        req.os_version = 1;
        req.os_patchlevel = 1;

        UniquePtr<ConfigureRequest> deserialized(round_trip(ver, req, 8));
        EXPECT_EQ(deserialized->os_version, req.os_version);
        EXPECT_EQ(deserialized->os_patchlevel, req.os_patchlevel);
    }
}

TEST(RoundTrip, ConfigureResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureResponse rsp(ver);
        UniquePtr<ConfigureResponse> deserialized(round_trip(ver, rsp, 4));
    }
}

TEST(RoundTrip, ConfigureVendorPatchlevelRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureVendorPatchlevelRequest req(ver);
        req.vendor_patchlevel = 2;

        UniquePtr<ConfigureVendorPatchlevelRequest> deserialized(round_trip(ver, req, 4));
        EXPECT_EQ(deserialized->vendor_patchlevel, req.vendor_patchlevel);
    }
}

TEST(RoundTrip, ConfigureVendorPatchlevelResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureVendorPatchlevelResponse rsp(ver);
        UniquePtr<ConfigureVendorPatchlevelResponse> deserialized(round_trip(ver, rsp, 4));
    }
}

TEST(RoundTrip, ConfigureBootPatchlevelRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureBootPatchlevelRequest req(ver);
        req.boot_patchlevel = 2;

        UniquePtr<ConfigureBootPatchlevelRequest> deserialized(round_trip(ver, req, 4));
        EXPECT_EQ(deserialized->boot_patchlevel, req.boot_patchlevel);
    }
}

TEST(RoundTrip, ConfigureBootPatchlevelResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureBootPatchlevelResponse rsp(ver);
        UniquePtr<ConfigureBootPatchlevelResponse> deserialized(round_trip(ver, rsp, 4));
    }
}

TEST(RoundTrip, ConfigureVerifiedBootInfoRequest) {
    for (int32_t ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureVerifiedBootInfoRequest req(ver, "super", "duper", {1, 2, 3, 4, 5, 6});

        UniquePtr<ConfigureVerifiedBootInfoRequest> deserialized(round_trip(ver, req, 28));
        ASSERT_NE(deserialized, nullptr);
        EXPECT_EQ(deserialized->boot_state, req.boot_state);
        EXPECT_EQ(deserialized->bootloader_state, req.bootloader_state);
        EXPECT_EQ(deserialized->vbmeta_digest, req.vbmeta_digest);
    }
}

TEST(RoundTrip, ConfigureVerifiedBootInfoResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        ConfigureVerifiedBootInfoResponse rsp(ver);
        UniquePtr<ConfigureVerifiedBootInfoResponse> deserialized(round_trip(ver, rsp, 4));
    }
}

TEST(RoundTrip, AddEntropyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        AddEntropyRequest msg(ver);
        msg.random_data.Reinitialize("foo", 3);

        UniquePtr<AddEntropyRequest> deserialized(round_trip(ver, msg, 7));
        EXPECT_EQ(3U, deserialized->random_data.available_read());
        EXPECT_EQ(0, memcmp("foo", deserialized->random_data.peek_read(), 3));
    }
}

TEST(RoundTrip, AbortOperationRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        AbortOperationRequest msg(ver);
        UniquePtr<AbortOperationRequest> deserialized(round_trip(ver, msg, 8));
    }
}

TEST(RoundTrip, AttestKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        AttestKeyRequest msg(ver);
        msg.SetKeyMaterial("foo", 3);
        msg.attest_params.Reinitialize(params, array_length(params));

        UniquePtr<AttestKeyRequest> deserialized(round_trip(ver, msg, 85));
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp("foo", deserialized->key_blob.key_material, 3));
        EXPECT_EQ(msg.attest_params, deserialized->attest_params);
    }
}

TEST(RoundTrip, AttestKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        AttestKeyResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.certificate_chain = CertificateChain(3);
        EXPECT_TRUE(!!msg.certificate_chain.entries);
        msg.certificate_chain.entries[0] = {dup_buffer("foo", 3), 3};
        msg.certificate_chain.entries[1] = {dup_buffer("bar", 3), 3};
        msg.certificate_chain.entries[2] = {dup_buffer("baz", 3), 3};

        UniquePtr<AttestKeyResponse> deserialized(round_trip(ver, msg, 29));
        keymaster_cert_chain_t* chain = &deserialized->certificate_chain;

        EXPECT_NE(nullptr, chain->entries);
        EXPECT_EQ(3U, chain->entry_count);
        EXPECT_EQ(3U, chain->entries[0].data_length);
        EXPECT_EQ(0, memcmp("foo", chain->entries[0].data, 3));
        EXPECT_EQ(3U, chain->entries[1].data_length);
        EXPECT_EQ(0, memcmp("bar", chain->entries[1].data, 3));
        EXPECT_EQ(3U, chain->entries[2].data_length);
        EXPECT_EQ(0, memcmp("baz", chain->entries[2].data, 3));
    }
}

TEST(RoundTrip, UpgradeKeyRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        UpgradeKeyRequest msg(ver);
        msg.SetKeyMaterial("foo", 3);
        msg.upgrade_params.Reinitialize(params, array_length(params));

        UniquePtr<UpgradeKeyRequest> deserialized(round_trip(ver, msg, 85));
        EXPECT_EQ(3U, deserialized->key_blob.key_material_size);
        EXPECT_EQ(0, memcmp("foo", deserialized->key_blob.key_material, 3));
        EXPECT_EQ(msg.upgrade_params, deserialized->upgrade_params);
    }
}

TEST(RoundTrip, UpgradeKeyResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        UpgradeKeyResponse req(ver);
        req.error = KM_ERROR_OK;
        req.upgraded_key.key_material = dup_array(TEST_DATA);
        req.upgraded_key.key_material_size = array_length(TEST_DATA);

        UniquePtr<UpgradeKeyResponse> deserialized(round_trip(ver, req, 19));
        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(req.upgraded_key.key_material_size, deserialized->upgraded_key.key_material_size);
        EXPECT_EQ(0, memcmp(req.upgraded_key.key_material, deserialized->upgraded_key.key_material,
                            req.upgraded_key.key_material_size));
    }
}

TEST(RoundTrip, GenerateTimestampTokenRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateTimestampTokenRequest msg(ver);
        msg.challenge = 1;
        UniquePtr<GenerateTimestampTokenRequest> deserialized(round_trip(ver, msg, 8));
        EXPECT_EQ(1U, deserialized->challenge);
    }
}

TEST(RoundTrip, GenerateTimestampTokenResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GenerateTimestampTokenResponse msg(ver);
        msg.error = KM_ERROR_OK;
        msg.token.challenge = 1;
        msg.token.timestamp = 2;
        msg.token.security_level = KM_SECURITY_LEVEL_SOFTWARE;
        msg.token.mac.data = dup_array(TEST_DATA);
        msg.token.mac.data_length = array_length(TEST_DATA);
        UniquePtr<GenerateTimestampTokenResponse> deserialized(round_trip(ver, msg, 39));
        EXPECT_EQ(1U, deserialized->token.challenge);
        EXPECT_EQ(2U, deserialized->token.timestamp);
        EXPECT_EQ(KM_SECURITY_LEVEL_SOFTWARE, deserialized->token.security_level);
        EXPECT_EQ(msg.token.mac.data_length, deserialized->token.mac.data_length);
        EXPECT_EQ(
            0, memcmp(msg.token.mac.data, deserialized->token.mac.data, msg.token.mac.data_length));
    }
}

TEST(RoundTrip, GetRootOfTrustRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        std::vector<uint8_t> challenge{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        GetRootOfTrustRequest msg(ver, challenge);

        UniquePtr<GetRootOfTrustRequest> deserialized(round_trip(ver, msg, 20));
        EXPECT_EQ(deserialized->challenge, challenge);
    }
}

TEST(RoundTrip, GetRootOfTrustResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        std::vector<uint8_t> rootOfTrust{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        GetRootOfTrustResponse msg(ver, rootOfTrust);
        msg.error = KM_ERROR_OK;

        UniquePtr<GetRootOfTrustResponse> deserialized(round_trip(ver, msg, 24));
        EXPECT_EQ(deserialized->rootOfTrust, rootOfTrust);
    }
}

TEST(RoundTrip, GetHwInfoResponse) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        GetHwInfoResponse rsp(ver);
        rsp.error = KM_ERROR_OK;
        rsp.version = 17;
        rsp.rpcAuthorName = "AAAAA";
        rsp.supportedEekCurve = 48;
        rsp.uniqueId = "BBBBB";
        rsp.supportedNumKeysInCsr = 549;

        UniquePtr<GetHwInfoResponse> deserialized;
        deserialized.reset(round_trip(ver, rsp, 34));

        EXPECT_EQ(KM_ERROR_OK, deserialized->error);
        EXPECT_EQ(deserialized->version, rsp.version);
        EXPECT_EQ(deserialized->rpcAuthorName, rsp.rpcAuthorName);
        EXPECT_EQ(deserialized->supportedEekCurve, rsp.supportedEekCurve);
        EXPECT_EQ(deserialized->uniqueId, rsp.uniqueId);
        EXPECT_EQ(deserialized->supportedNumKeysInCsr, rsp.supportedNumKeysInCsr);
    }
}

#define SET_ATTESTATION_ID(x) msg.x.Reinitialize(#x, strlen(#x))

void check_id(const Buffer& id, const char* value) {
    auto len = strlen(value);
    EXPECT_EQ(id.available_read(), len) << "On " << value;
    EXPECT_TRUE(memcmp(id.peek_read(), value, len) == 0) << "On " << value;
}

#define CHECK_ID(x) check_id(deserialized->x, #x);

TEST(RoundTrip, SetAttestationIdsRequest) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        SetAttestationIdsRequest msg(ver);
        SET_ATTESTATION_ID(brand);
        SET_ATTESTATION_ID(device);
        SET_ATTESTATION_ID(product);
        SET_ATTESTATION_ID(serial);
        SET_ATTESTATION_ID(imei);
        SET_ATTESTATION_ID(meid);
        SET_ATTESTATION_ID(manufacturer);
        SET_ATTESTATION_ID(model);

        UniquePtr<SetAttestationIdsRequest> deserialized(round_trip(ver, msg, 81));
        ASSERT_TRUE(deserialized);
        CHECK_ID(brand);
        CHECK_ID(device);
        CHECK_ID(product);
        CHECK_ID(serial);
        CHECK_ID(imei);
        CHECK_ID(model);
    }
}

TEST(RoundTrip, SetAttestationIdsKM3Request) {
    for (int ver = 0; ver <= kMaxMessageVersion; ++ver) {
        SetAttestationIdsKM3Request msg(ver);
        SET_ATTESTATION_ID(base.brand);
        SET_ATTESTATION_ID(base.device);
        SET_ATTESTATION_ID(base.product);
        SET_ATTESTATION_ID(base.serial);
        SET_ATTESTATION_ID(base.imei);
        SET_ATTESTATION_ID(base.meid);
        SET_ATTESTATION_ID(base.manufacturer);
        SET_ATTESTATION_ID(base.model);
        SET_ATTESTATION_ID(second_imei);

        UniquePtr<SetAttestationIdsKM3Request> deserialized(round_trip(ver, msg, 136));
        ASSERT_TRUE(deserialized);
        CHECK_ID(base.brand);
        CHECK_ID(base.device);
        CHECK_ID(base.product);
        CHECK_ID(base.serial);
        CHECK_ID(base.imei);
        CHECK_ID(base.model);
        CHECK_ID(second_imei);
    }
}

TEST(Serialize, ShortBuffer) {
    for (int typ = 0; typ <= static_cast<int>(keymaster::SerializableType::kMaxValue); typ++) {
        // Get a default-constructed object of the relevant serializable type, and ask it
        // how much space it needs for serialization.
        auto stype = static_cast<keymaster::SerializableType>(typ);
        std::unique_ptr<keymaster::Serializable> ser = keymaster::getSerializable(stype);
        uint16_t expected_size = ser->SerializedSize();

        // Perform serialization of the object into variously sized buffers, mostly too small.
        // There's no mechanism for indicating failure, so this test mostly just checks for
        // memory errors (and so is a good candidate for running under ASAN/HWASAN).
        for (uint16_t actual_size = 0; actual_size <= expected_size; actual_size++) {
            std::unique_ptr<uint8_t[]> out_buf =
                std::unique_ptr<uint8_t[]>(new uint8_t[actual_size]);
            uint8_t* next = ser->Serialize(out_buf.get(), out_buf.get() + actual_size);
            EXPECT_TRUE(next <= out_buf.get() + actual_size)
                << "Serialization of " << typ << " returned a next offset beyond end+1";
        }
    }
}

uint8_t msgbuf[] = {
    220, 88,  183, 255, 71,  1,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
    0,   173, 0,   0,   0,   228, 174, 98,  187, 191, 135, 253, 200, 51,  230, 114, 247, 151, 109,
    237, 79,  87,  32,  94,  5,   204, 46,  154, 30,  91,  6,   103, 148, 254, 129, 65,  171, 228,
    167, 224, 163, 9,   15,  206, 90,  58,  11,  205, 55,  211, 33,  87,  178, 149, 91,  28,  236,
    218, 112, 231, 34,  82,  82,  134, 103, 137, 115, 27,  156, 102, 159, 220, 226, 89,  42,  25,
    37,  9,   84,  239, 76,  161, 198, 72,  167, 163, 39,  91,  148, 191, 17,  191, 87,  169, 179,
    136, 10,  194, 154, 4,   40,  107, 109, 61,  161, 20,  176, 247, 13,  214, 106, 229, 45,  17,
    5,   60,  189, 64,  39,  166, 208, 14,  57,  25,  140, 148, 25,  177, 246, 189, 43,  181, 88,
    204, 29,  126, 224, 100, 143, 93,  60,  57,  249, 55,  0,   87,  83,  227, 224, 166, 59,  214,
    81,  144, 129, 58,  6,   57,  46,  254, 232, 41,  220, 209, 230, 167, 138, 158, 94,  180, 125,
    247, 26,  162, 116, 238, 202, 187, 100, 65,  13,  180, 44,  245, 159, 83,  161, 176, 58,  72,
    236, 109, 105, 160, 0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,
    0,   11,  0,   0,   0,   98,  0,   0,   0,   1,   0,   0,   32,  2,   0,   0,   0,   1,   0,
    0,   32,  3,   0,   0,   0,   2,   0,   0,   16,  1,   0,   0,   0,   3,   0,   0,   48,  0,
    1,   0,   0,   200, 0,   0,   80,  3,   0,   0,   0,   0,   0,   0,   0,   244, 1,   0,   112,
    1,   246, 1,   0,   112, 1,   189, 2,   0,   96,  144, 178, 236, 250, 255, 255, 255, 255, 145,
    1,   0,   96,  144, 226, 33,  60,  222, 2,   0,   0,   189, 2,   0,   96,  0,   0,   0,   0,
    0,   0,   0,   0,   190, 2,   0,   16,  1,   0,   0,   0,   12,  0,   0,   0,   0,   0,   0,
    0,   0,   0,   0,   0,   0,   0,   0,   0,   110, 0,   0,   0,   0,   0,   0,   0,   11,  0,
    0,   0,   98,  0,   0,   0,   1,   0,   0,   32,  2,   0,   0,   0,   1,   0,   0,   32,  3,
    0,   0,   0,   2,   0,   0,   16,  1,   0,   0,   0,   3,   0,   0,   48,  0,   1,   0,   0,
    200, 0,   0,   80,  3,   0,   0,   0,   0,   0,   0,   0,   244, 1,   0,   112, 1,   246, 1,
    0,   112, 1,   189, 2,   0,   96,  144, 178, 236, 250, 255, 255, 255, 255, 145, 1,   0,   96,
    144, 226, 33,  60,  222, 2,   0,   0,   189, 2,   0,   96,  0,   0,   0,   0,   0,   0,   0,
    0,   190, 2,   0,   16,  1,   0,   0,   0,
};

/*
 * These tests don't have any assertions or expectations. They just try to parse garbage, to see if
 * the result will be a crash.  This is especially informative when run under Valgrind memcheck.
 */

template <typename Message> void parse_garbage() {
    for (int32_t ver = 0; ver <= kMaxMessageVersion; ++ver) {
        Message msg(ver);
        const uint8_t* end = msgbuf + array_length(msgbuf);
        for (size_t i = 0; i < array_length(msgbuf); ++i) {
            const uint8_t* begin = msgbuf + i;
            const uint8_t* p = begin;
            msg.Deserialize(&p, end);
        }
    }

    time_t now = time(nullptr);
    std::cout << "Seeding rand() with " << now << " for fuzz test." << std::endl;
    srand(now);

    // Fill large buffer with random bytes.
    const int kBufSize = 10000;
    UniquePtr<uint8_t[]> buf(new uint8_t[kBufSize]);
    for (size_t i = 0; i < kBufSize; ++i)
        buf[i] = static_cast<uint8_t>(rand());

    for (uint32_t ver = 0; ver < kMaxMessageVersion; ++ver) {
        Message msg(ver);
        const uint8_t* end = buf.get() + kBufSize;
        for (size_t i = 0; i < kBufSize; ++i) {
            const uint8_t* begin = buf.get() + i;
            const uint8_t* p = begin;
            msg.Deserialize(&p, end);
        }
    }
}

#define GARBAGE_TEST(Message)                                                                      \
    TEST(GarbageTest, Message) {                                                                   \
        parse_garbage<Message>();                                                                  \
    }

GARBAGE_TEST(AbortOperationRequest);
GARBAGE_TEST(EmptyKeymasterResponse);
GARBAGE_TEST(AddEntropyRequest);
GARBAGE_TEST(BeginOperationRequest);
GARBAGE_TEST(BeginOperationResponse);
GARBAGE_TEST(DeleteAllKeysRequest);
GARBAGE_TEST(DeleteKeyRequest);
GARBAGE_TEST(ExportKeyRequest);
GARBAGE_TEST(ExportKeyResponse);
GARBAGE_TEST(FinishOperationRequest);
GARBAGE_TEST(FinishOperationResponse);
GARBAGE_TEST(GenerateKeyRequest);
GARBAGE_TEST(GenerateKeyResponse);
GARBAGE_TEST(GetKeyCharacteristicsRequest);
GARBAGE_TEST(GetKeyCharacteristicsResponse);
GARBAGE_TEST(ImportKeyRequest);
GARBAGE_TEST(ImportKeyResponse);
GARBAGE_TEST(UpdateOperationRequest);
GARBAGE_TEST(UpdateOperationResponse);
GARBAGE_TEST(AttestKeyRequest);
GARBAGE_TEST(AttestKeyResponse);
GARBAGE_TEST(UpgradeKeyRequest);
GARBAGE_TEST(UpgradeKeyResponse);
GARBAGE_TEST(GenerateTimestampTokenRequest);
GARBAGE_TEST(GenerateTimestampTokenResponse);
GARBAGE_TEST(SetAttestationIdsRequest);
GARBAGE_TEST(SetAttestationIdsKM3Request);
GARBAGE_TEST(ConfigureVerifiedBootInfoRequest);
GARBAGE_TEST(GetRootOfTrustRequest);
GARBAGE_TEST(GetRootOfTrustResponse);

}  // namespace test

}  // namespace keymaster
