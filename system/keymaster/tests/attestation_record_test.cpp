/*
 * Copyright 2016 The Android Open Source Project
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

#include <fstream>

#include <cppbor_parse.h>
#include <gtest/gtest.h>

#include <keymaster/contexts/soft_attestation_context.h>
#include <keymaster/keymaster_context.h>
#include <keymaster/km_openssl/attestation_record.h>

#include "android_keymaster_test_utils.h"

// Use TAG_KDF as an 'unknown tag', as it is not deliberately thrown out
// in attestation_record.cpp, but still among the keymaster tag types.
#define UNKNOWN_TAG static_cast<keymaster_tag_t>(KM_ULONG_REP | 50)
#define UNKNOWN_TAG_VALUE 0

namespace keymaster {
namespace test {

TypedTag<KM_ULONG_REP, UNKNOWN_TAG> UNKNOWN_TAG_T;

class TestContext : public SoftAttestationContext {
  public:
    TestContext(KmVersion version) : SoftAttestationContext(version) {}

    keymaster_security_level_t GetSecurityLevel() const override {
        return KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
    }
    Buffer GenerateUniqueId(uint64_t /* creation_date_time */,
                            const keymaster_blob_t& application_id, bool /* reset_since_rotation */,
                            keymaster_error_t* error) const override {
        // Use the application ID directly as the unique ID.
        *error = KM_ERROR_OK;
        return {application_id.data, application_id.data_length};
    }
    const VerifiedBootParams* GetVerifiedBootParams(keymaster_error_t* error) const override {
        static VerifiedBootParams params{};
        params.verified_boot_key = {vboot_key_, sizeof(vboot_key_)};
        params.verified_boot_state = KM_VERIFIED_BOOT_VERIFIED;
        params.device_locked = true;
        *error = KM_ERROR_OK;
        return &params;
    }

    void VerifyRootOfTrust(const keymaster_blob_t& verified_boot_key,
                           keymaster_verified_boot_t verified_boot_state, bool device_locked) {
        EXPECT_EQ(sizeof(vboot_key_), verified_boot_key.data_length);
        if (sizeof(vboot_key_) == verified_boot_key.data_length) {
            EXPECT_EQ(0, memcmp(verified_boot_key.data, vboot_key_, sizeof(vboot_key_)));
        }
        EXPECT_TRUE(device_locked);
        EXPECT_EQ(KM_VERIFIED_BOOT_VERIFIED, verified_boot_state);
    }

  private:
    uint8_t vboot_key_[32]{"test_vboot_key"};
};

class KeymintTestContext : public TestContext {
  public:
    KeymintTestContext() : TestContext(KmVersion::KEYMINT_1) {}
};

class KeymasterTestContext : public TestContext {
  public:
    KeymasterTestContext() : TestContext(KmVersion::KEYMASTER_4_1) {}  // Last Keymaster version
};

TEST(AttestAsn1Test, Simple) {
    const char* fake_app_id = "fake_app_id";
    const char* fake_app_data = "fake_app_data";
    const char* fake_challenge = "fake_challenge";
    const char* fake_attest_app_id = "fake_attest_app_id";
    KeymasterTestContext context;
    AuthorizationSet hw_set(AuthorizationSetBuilder()
                                .RsaSigningKey(512, 3)
                                .Digest(KM_DIGEST_SHA_2_256)
                                .Digest(KM_DIGEST_SHA_2_384)
                                .Authorization(TAG_OS_VERSION, 60000)
                                .Authorization(TAG_OS_PATCHLEVEL, 201512));
    AuthorizationSet sw_set(
        AuthorizationSetBuilder()
            .Authorization(TAG_ACTIVE_DATETIME, 10)
            .Authorization(TAG_CREATION_DATETIME, 10)
            .Authorization(TAG_APPLICATION_ID, fake_app_id, strlen(fake_app_id))
            .Authorization(TAG_APPLICATION_DATA, fake_app_data, strlen(fake_app_data)));

    UniquePtr<uint8_t[]> asn1;
    size_t asn1_len = 0;
    AuthorizationSet attest_params(
        AuthorizationSetBuilder()
            .Authorization(TAG_INCLUDE_UNIQUE_ID)
            .Authorization(TAG_ATTESTATION_CHALLENGE, fake_challenge, strlen(fake_challenge))
            .Authorization(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                           strlen(fake_attest_app_id)));
    ASSERT_EQ(KM_ERROR_OK,
              build_attestation_record(attest_params, sw_set, hw_set, context, &asn1, &asn1_len));
    EXPECT_GT(asn1_len, 0U);

    std::ofstream output("attest.der",
                         std::ofstream::out | std::ofstream::binary | std::ofstream::trunc);
    if (output) output.write(reinterpret_cast<const char*>(asn1.get()), asn1_len);
    output.close();

    AuthorizationSet parsed_hw_set;
    AuthorizationSet parsed_sw_set;
    uint32_t attestation_version;
    uint32_t keymaster_version;
    keymaster_security_level_t attestation_security_level;
    keymaster_security_level_t keymaster_security_level;
    keymaster_blob_t attestation_challenge = {};
    keymaster_blob_t unique_id = {};
    EXPECT_EQ(KM_ERROR_OK,
              parse_attestation_record(asn1.get(), asn1_len, &attestation_version,
                                       &attestation_security_level, &keymaster_version,
                                       &keymaster_security_level, &attestation_challenge,
                                       &parsed_sw_set, &parsed_hw_set, &unique_id));

    // Check that the challenge is consistent across build and parse.
    EXPECT_EQ(std::string(fake_challenge),
              std::string(reinterpret_cast<const char*>(attestation_challenge.data),
                          attestation_challenge.data_length));
    delete[] attestation_challenge.data;

    // Check that the unique id was populated as expected.
    EXPECT_EQ(std::string(fake_attest_app_id),
              std::string(reinterpret_cast<const char*>(unique_id.data), unique_id.data_length));
    delete[] unique_id.data;

    // The attestation ID is expected to appear in parsed_sw_set.
    sw_set.push_back(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                     strlen(fake_attest_app_id));

    // The TAG_INCLUDE_UNIQUE_ID tag is not expected to appear in parsed_hw_set.
    hw_set.erase(hw_set.find(TAG_INCLUDE_UNIQUE_ID));

    // Application data is not expected to appear in parsed_sw_set.
    sw_set.erase(sw_set.find(TAG_APPLICATION_ID));
    sw_set.erase(sw_set.find(TAG_APPLICATION_DATA));

    // Check that the list of tags is consistent across build and parse.
    hw_set.Sort();
    sw_set.Sort();
    parsed_hw_set.Sort();
    parsed_sw_set.Sort();
    EXPECT_EQ(hw_set, parsed_hw_set);
    EXPECT_EQ(sw_set, parsed_sw_set);

    // Check the root of trust values.
    keymaster_blob_t verified_boot_key;
    keymaster_verified_boot_t verified_boot_state;
    bool device_locked;
    EXPECT_EQ(KM_ERROR_OK, parse_root_of_trust(asn1.get(), asn1_len, &verified_boot_key,
                                               &verified_boot_state, &device_locked));
    context.VerifyRootOfTrust(verified_boot_key, verified_boot_state, device_locked);
    delete[] verified_boot_key.data;
}

TEST(EatTest, Simple) {
    const char* fake_imei = "490154203237518";
    const char* fake_app_id = "fake_app_id";
    const char* fake_app_data = "fake_app_data";
    const char* fake_challenge = "fake_challenge";
    const char* fake_attest_app_id = "fake_attest_app_id";
    KeymintTestContext context;
    AuthorizationSet hw_set(
        AuthorizationSetBuilder()
            .RsaSigningKey(512, 3)
            .Digest(KM_DIGEST_SHA_2_256)
            .Digest(KM_DIGEST_SHA_2_384)
            .Authorization(TAG_OS_VERSION, 60000)
            .Authorization(TAG_OS_PATCHLEVEL, 201512)
            .Authorization(TAG_ATTESTATION_ID_IMEI, fake_imei, strlen(fake_imei)));
    AuthorizationSet sw_set(
        AuthorizationSetBuilder()
            .Authorization(TAG_ACTIVE_DATETIME, 10)
            .Authorization(TAG_CREATION_DATETIME, 10)
            .Authorization(TAG_APPLICATION_ID, fake_app_id, strlen(fake_app_id))
            .Authorization(TAG_APPLICATION_DATA, fake_app_data, strlen(fake_app_data)));

    std::vector<uint8_t> eat;
    AuthorizationSet attest_params(
        AuthorizationSetBuilder()
            .Authorization(TAG_INCLUDE_UNIQUE_ID)
            .Authorization(TAG_ATTESTATION_CHALLENGE, fake_challenge, strlen(fake_challenge))
            .Authorization(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                           strlen(fake_attest_app_id)));
    ASSERT_EQ(KM_ERROR_OK, build_eat_record(attest_params, sw_set, hw_set, context, &eat));
    EXPECT_GT(eat.size(), 0U);

    std::ofstream output("eat.der",
                         std::ofstream::out | std::ofstream::binary | std::ofstream::trunc);
    if (output) output.write(reinterpret_cast<const char*>(&eat[0]), eat.size() * sizeof(uint8_t));
    output.close();

    AuthorizationSet parsed_hw_set;
    AuthorizationSet parsed_sw_set;
    uint32_t attestation_version;
    uint32_t keymaster_version;
    keymaster_security_level_t attestation_security_level;
    keymaster_security_level_t keymaster_security_level;
    keymaster_blob_t attestation_challenge = {};
    keymaster_blob_t unique_id = {};
    keymaster_blob_t verified_boot_key = {};
    keymaster_verified_boot_t verified_boot_state;
    bool device_locked;
    std::vector<int64_t> unexpected_claims;
    EXPECT_EQ(KM_ERROR_OK,
              parse_eat_record(eat.data(), eat.size(), &attestation_version,
                               &attestation_security_level, &keymaster_version,
                               &keymaster_security_level, &attestation_challenge, &parsed_sw_set,
                               &parsed_hw_set, &unique_id, &verified_boot_key, &verified_boot_state,
                               &device_locked, &unexpected_claims));

    // Check that there were no unexpected claims when parsing.
    EXPECT_EQ(std::vector<int64_t>(), unexpected_claims);

    // Check that the challenge is consistent across build and parse.
    EXPECT_EQ(std::string(fake_challenge),
              std::string(reinterpret_cast<const char*>(attestation_challenge.data),
                          attestation_challenge.data_length));
    delete[] attestation_challenge.data;

    // Check that the unique id was populated as expected.
    EXPECT_EQ(std::string(fake_attest_app_id),
              std::string(reinterpret_cast<const char*>(unique_id.data), unique_id.data_length));
    delete[] unique_id.data;

    // The attestation ID is expected to appear in parsed_sw_set.
    sw_set.push_back(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                     strlen(fake_attest_app_id));

    // The TAG_INCLUDE_UNIQUE_ID tag is not expected to appear in parsed_hw_set.
    hw_set.erase(hw_set.find(TAG_INCLUDE_UNIQUE_ID));

    // Application data is not expected to appear in parsed_sw_set.
    sw_set.erase(sw_set.find(TAG_APPLICATION_ID));
    sw_set.erase(sw_set.find(TAG_APPLICATION_DATA));

    // Check that the list of tags is consistent across build and parse.
    hw_set.Sort();
    sw_set.Sort();
    parsed_hw_set.Sort();
    parsed_sw_set.Sort();
    EXPECT_EQ(hw_set, parsed_hw_set);
    EXPECT_EQ(sw_set, parsed_sw_set);

    // Check the root of trust values.
    context.VerifyRootOfTrust(verified_boot_key, verified_boot_state, device_locked);
    delete[] verified_boot_key.data;
}

TEST(BadImeiTest, Simple) {
    const char* fake_challenge = "fake_challenge";
    const char* fake_attest_app_id = "fake_attest_app_id";
    const char* invalid_imei = "1234567890123456";
    KeymintTestContext context;
    AuthorizationSet hw_set(AuthorizationSetBuilder().Authorization(
        TAG_ATTESTATION_ID_IMEI, invalid_imei, strlen(invalid_imei)));
    AuthorizationSet attest_params(
        AuthorizationSetBuilder()
            .Authorization(TAG_ATTESTATION_CHALLENGE, fake_challenge, strlen(fake_challenge))
            .Authorization(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                           strlen(fake_attest_app_id)));
    AuthorizationSet sw_set;

    std::vector<uint8_t> eat;
    ASSERT_EQ(KM_ERROR_INVALID_TAG, build_eat_record(attest_params, sw_set, hw_set, context, &eat));
}

TEST(MissingAuthChallengeTest, Simple) {
    const char* fake_attest_app_id = "fake_attest_app_id";
    KeymintTestContext context;
    AuthorizationSet hw_set(AuthorizationSetBuilder().Authorization(TAG_OS_PATCHLEVEL, 201512));
    AuthorizationSet attest_params(AuthorizationSetBuilder().Authorization(
        TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id, strlen(fake_attest_app_id)));
    AuthorizationSet sw_set;

    std::vector<uint8_t> eat;
    ASSERT_EQ(KM_ERROR_ATTESTATION_CHALLENGE_MISSING,
              build_eat_record(attest_params, sw_set, hw_set, context, &eat));
}

TEST(UnknownTagTest, Simple) {
    const char* fake_challenge = "fake_challenge";
    const char* fake_attest_app_id = "fake_attest_app_id";
    KeymintTestContext context;
    AuthorizationSet unknown_tag_set(
        AuthorizationSetBuilder().Authorization(UNKNOWN_TAG_T, UNKNOWN_TAG_VALUE));

    // Test adding an unknown tag to both sets. The tag should be retained only in the software
    // submod.
    std::vector<uint8_t> eat;
    AuthorizationSet attest_params(
        AuthorizationSetBuilder()
            .Authorization(TAG_ATTESTATION_CHALLENGE, fake_challenge, strlen(fake_challenge))
            .Authorization(TAG_ATTESTATION_APPLICATION_ID, fake_attest_app_id,
                           strlen(fake_attest_app_id)));
    ASSERT_EQ(KM_ERROR_OK,
              build_eat_record(attest_params, unknown_tag_set, unknown_tag_set, context, &eat));
    EXPECT_GT(eat.size(), 0U);

    AuthorizationSet parsed_hw_set;
    AuthorizationSet parsed_sw_set;
    uint32_t attestation_version;
    uint32_t keymaster_version;
    keymaster_security_level_t attestation_security_level;
    keymaster_security_level_t keymaster_security_level;
    keymaster_blob_t attestation_challenge = {};
    keymaster_blob_t unique_id = {};
    keymaster_blob_t verified_boot_key = {};
    keymaster_verified_boot_t verified_boot_state;
    bool device_locked;
    std::vector<int64_t> unexpected_claims;
    // Parsing should fail, because the software submod retains the unknown tag.
    EXPECT_EQ(KM_ERROR_INVALID_TAG,
              parse_eat_record(eat.data(), eat.size(), &attestation_version,
                               &attestation_security_level, &keymaster_version,
                               &keymaster_security_level, &attestation_challenge, &parsed_sw_set,
                               &parsed_hw_set, &unique_id, &verified_boot_key, &verified_boot_state,
                               &device_locked, &unexpected_claims));

    // Perform a manual inspection of the EAT token, checking that the tag is retained in the
    // software submod, but not in the hardware submod.
    auto [top_level_item, next_pos, error] = cppbor::parse(eat.data(), eat.size());
    ASSERT_NE(top_level_item, nullptr);
    const cppbor::Map* eat_map = top_level_item->asMap();
    ASSERT_NE(eat_map, nullptr);
    bool found_in_software_submod = false;
    bool found_in_hardware_submod = false;
    for (size_t i = 0; i < eat_map->size(); i++) {
        auto& [eat_key, eat_value] = (*eat_map)[i];
        const cppbor::Int* root_key = eat_key->asInt();
        if ((EatClaim)root_key->value() == EatClaim::SUBMODS) {
            const cppbor::Map* submods_map = eat_value->asMap();
            // Check for each submod whether it contains the expected value.
            for (size_t j = 0; j < submods_map->size(); j++) {
                auto& [submod_key, submod_value] = (*submods_map)[j];
                const cppbor::Map* submod_map = submod_value->asMap();
                bool found_in_submod = false;
                EatSecurityLevel submod_security_level;
                for (size_t k = 0; k < submod_map->size(); k++) {
                    auto& [key_item, value_item] = (*submod_map)[k];
                    const cppbor::Int* key_int = key_item->asInt();
                    if (key_int->value() == convert_to_eat_claim(UNKNOWN_TAG_T)) {
                        found_in_submod = true;
                    } else if ((EatClaim)key_int->value() == EatClaim::SECURITY_LEVEL) {
                        submod_security_level = (EatSecurityLevel)value_item->asInt()->value();
                    }
                }
                if (submod_security_level == EatSecurityLevel::UNRESTRICTED) {
                    found_in_software_submod = found_in_submod;
                } else if (submod_security_level == EatSecurityLevel::SECURE_RESTRICTED) {
                    found_in_hardware_submod = found_in_submod;
                }
            }
        }
    }
    EXPECT_FALSE(found_in_hardware_submod);
    EXPECT_TRUE(found_in_software_submod);
}

}  // namespace test
}  // namespace keymaster
