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

#define TLOG_TAG "hwbcc-test"

#include <cppbor.h>
#include <cppbor_parse.h>
#include <dice/config.h>
#include <dice/dice.h>
#include <lib/hwbcc/client/hwbcc.h>
#include <lib/hwbcc/common/common.h>
#include <lib/hwbcc/common/swbcc.h>
#include <lib/system_state/system_state.h>
#include <openssl/curve25519.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#include <array>
#include <vector>

typedef struct {
    swbcc_session_t s;
} swbcc_t;

/* UUID of this test TA: {0e109d31-8bbe-47d6-bb47-e1dd08910e16} */
static const struct uuid self_uuid = {
        0x0e109d31,
        0x8bbe,
        0x47d6,
        {0xbb, 0x47, 0xe1, 0xdd, 0x08, 0x91, 0x0e, 0x16},
};

static const std::array<uint8_t, HWBCC_MAX_DATA_TO_SIGN_SIZE> test_data = {};

static const uint8_t test_aad[] = {0xcf, 0xe1, 0x89, 0x39, 0xb1,
                                   0x72, 0xbf, 0x4f, 0xa8, 0x0f};

TEST_F_SETUP(swbcc) {
    _state->s = 0;
    int rc = swbcc_init(&_state->s, &self_uuid);
    ASSERT_EQ(rc, 0);

test_abort:;
}

TEST_F_TEARDOWN(swbcc) {
    swbcc_close(_state->s);
}

TEST_F(swbcc, mac) {
    int rc;
    uint8_t cose_sign1[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t cose_sign1_size = 0;

    memset(cose_sign1, 0, sizeof(cose_sign1));

    rc = swbcc_sign_key(_state->s, true, HWBCC_ALGORITHM_ED25519,
                        test_data.data(), test_data.size(), test_aad,
                        sizeof(test_aad), cose_sign1, sizeof(cose_sign1),
                        &cose_sign1_size);
    ASSERT_EQ(rc, 0);

    ASSERT_GT(cose_sign1_size, 0);
    /* TODO: Check contents of cose_sign1. */

test_abort:;
}

TEST_F(swbcc, bcc) {
    int rc;
    uint8_t bcc[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t bcc_size = 0;
    std::vector<PubKey> keys;
    uint8_t* dk_pub_key;
    uint8_t* km_pub_key;

    memset(bcc, 0, sizeof(bcc));

    rc = swbcc_get_bcc(_state->s, true, bcc, sizeof(bcc), &bcc_size);
    ASSERT_EQ(rc, 0);
    ASSERT_GT(bcc_size, 0);

    ASSERT_EQ(validate_bcc_impl(bcc, bcc_size, &keys), true);

    /* Only a degenerate self-signed BCC is currently supported. */
    ASSERT_EQ(keys.size(), 2);

    dk_pub_key = keys[0].data();
    km_pub_key = keys[1].data();
    ASSERT_EQ(memcmp(dk_pub_key, km_pub_key, ED25519_PUBLIC_KEY_LEN), 0);

test_abort:;
}

/* Check that test mode yields different output every time */
TEST(hwbcc, protected_data_test_mode) {
    int rc;
    uint8_t cose_sign1[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t cose_sign1_size;
    uint8_t bcc[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t bcc_size;
    std::vector<PubKey> keys1;
    std::vector<PubKey> keys2;

    /* Get first set of keys */
    memset(cose_sign1, 0, sizeof(cose_sign1));
    memset(bcc, 0, sizeof(bcc));

    rc = hwbcc_get_protected_data(
            true, HWBCC_ALGORITHM_ED25519, test_data.data(), test_data.size(),
            test_aad, sizeof(test_aad), cose_sign1, sizeof(cose_sign1),
            &cose_sign1_size, bcc, sizeof(bcc), &bcc_size);
    ASSERT_EQ(rc, 0);

    ASSERT_GT(cose_sign1_size, 0);
    ASSERT_GT(bcc_size, 0);

    ASSERT_EQ(validate_bcc_impl(bcc, bcc_size, &keys1), true);

    /* Get second set of keys */
    memset(cose_sign1, 0, sizeof(cose_sign1));
    memset(bcc, 0, sizeof(bcc));

    rc = hwbcc_get_protected_data(
            true, HWBCC_ALGORITHM_ED25519, test_data.data(), test_data.size(),
            test_aad, sizeof(test_aad), cose_sign1, sizeof(cose_sign1),
            &cose_sign1_size, bcc, sizeof(bcc), &bcc_size);
    ASSERT_EQ(rc, 0);

    ASSERT_GT(cose_sign1_size, 0);
    ASSERT_GT(bcc_size, 0);

    ASSERT_EQ(validate_bcc_impl(bcc, bcc_size, &keys2), true);

    /* The two sets of keys must be different in test mode. */
    ASSERT_NE(memcmp(keys1[0].data(), keys2[0].data(), ED25519_PUBLIC_KEY_LEN),
              0);
    ASSERT_NE(memcmp(keys1[1].data(), keys2[1].data(), ED25519_PUBLIC_KEY_LEN),
              0);

test_abort:;
}

/*
 * Macro to enable test cases for generic ARM64 platform only.
 * (This includes generic_arm32 targets too).
 */
#if defined(PLATFORM_GENERIC_ARM64)
#define GENERIC_ARM64_PLATFORM_ONLY_TEST(name) name
#else
#define GENERIC_ARM64_PLATFORM_ONLY_TEST(name) DISABLED_##name
#endif

/*
 * Device key is hard-coded on emulator targets, i.e. BCC keys are fixed too.
 * We test that BCC keys don't change to make sure that we don't accidentally
 * change the key derivation procedure.
 */
static const uint8_t emulator_pub_key[ED25519_PUBLIC_KEY_LEN] = {
        0xc0, 0xdd, 0x6c, 0xf4, 0x3e, 0x66, 0x15, 0xc7, 0x4d, 0x23, 0xb8,
        0x96, 0x11, 0x11, 0xc9, 0x88, 0x07, 0x92, 0x2c, 0x8f, 0x32, 0xf6,
        0x79, 0x85, 0x86, 0x36, 0xad, 0xbd, 0x20, 0xf0, 0x9b, 0x21};

TEST(hwbcc, GENERIC_ARM64_PLATFORM_ONLY_TEST(protected_data)) {
    int rc;
    uint8_t cose_sign1[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t cose_sign1_size;
    uint8_t bcc[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t bcc_size;
    std::vector<PubKey> keys;
    uint8_t* dk_pub_key;
    uint8_t* km_pub_key;

    memset(cose_sign1, 0, sizeof(cose_sign1));
    memset(bcc, 0, sizeof(bcc));

    rc = hwbcc_get_protected_data(
            false, HWBCC_ALGORITHM_ED25519, test_data.data(), test_data.size(),
            test_aad, sizeof(test_aad), cose_sign1, sizeof(cose_sign1),
            &cose_sign1_size, bcc, sizeof(bcc), &bcc_size);
    ASSERT_EQ(rc, 0);

    ASSERT_GT(cose_sign1_size, 0);
    ASSERT_GT(bcc_size, 0);

    ASSERT_EQ(validate_bcc_impl(bcc, bcc_size, &keys), true);
    ASSERT_EQ(keys.size(), 2);

    dk_pub_key = keys[0].data();
    km_pub_key = keys[1].data();
    ASSERT_EQ(memcmp(emulator_pub_key, dk_pub_key, ED25519_PUBLIC_KEY_LEN), 0);
    ASSERT_EQ(memcmp(dk_pub_key, km_pub_key, ED25519_PUBLIC_KEY_LEN), 0);

test_abort:;
}

static const uint8_t emulator_cdi_attest[DICE_CDI_SIZE] = {
        0x44, 0x26, 0x69, 0x94, 0x02, 0x34, 0x1c, 0xc8, 0x1d, 0x93, 0xc7,
        0xb8, 0x47, 0xaf, 0x55, 0xe8, 0xde, 0x8e, 0x79, 0x4c, 0x1b, 0x0f,
        0xea, 0x99, 0x7f, 0x91, 0x83, 0x83, 0x7f, 0x26, 0x7f, 0x93};

static const uint8_t emulator_cdi_seal[DICE_CDI_SIZE] = {
        0xf7, 0xe5, 0xb0, 0x2b, 0xd0, 0xfa, 0x4d, 0x5b, 0xfa, 0xd8, 0x16,
        0x24, 0xfa, 0xc8, 0x50, 0xac, 0x4f, 0x1a, 0x3d, 0xb4, 0xbc, 0x02,
        0xc9, 0xfd, 0xeb, 0xfe, 0x26, 0xfc, 0x28, 0x98, 0x5b, 0xe8,
};

/**
 * Test the two CDIs: CDI_Attest and CDI_Seal; and the UDS included
 * in the BCC, all of which are retrieved from get_dice_artifacts, with those
 * values specific to ARM64 emulator, given that the hwkey for the emulator
 * is hardcoded.
 */
TEST(hwbcc, GENERIC_ARM64_PLATFORM_ONLY_TEST(test_get_dice_artifacts)) {
    int rc;
    uint8_t dice_artifacts[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t dice_artifacts_size;
    CDI next_cdi_attest;
    CDI next_cdi_seal;

    /**
     * dice_artifacts expects the following CBOR encoded structure.
     * Since the implementation of hwbcc_get_dice_artifacts serves only the
     * non-secure world, Bcc is not present in the returned dice_artifacts.
     * We calculate the expected size, including CBOR header sizes.
     * BccHandover = {
     *      1 : bstr .size 32,	// CDI_Attest
     *      2 : bstr .size 32,	// CDI_Seal
     *      ? 3 : Bcc,          // Cert_Chain
     * }
     * Bcc = [
     *      PubKeyEd25519, // UDS
     *      + BccEntry,    // Root -> leaf
     *  ]
     */
    size_t bcc_handover_size = 2 * DICE_CDI_SIZE + 7 /*CBOR tags*/;

    memset(dice_artifacts, 0, sizeof(dice_artifacts));

    rc = hwbcc_get_dice_artifacts(0, dice_artifacts, sizeof(dice_artifacts),
                                  &dice_artifacts_size);

    ASSERT_EQ(rc, 0);
    ASSERT_GT(dice_artifacts_size, 0);

    ASSERT_EQ(dice_artifacts_size, bcc_handover_size);

    ASSERT_EQ(validate_bcc_handover_impl(dice_artifacts, dice_artifacts_size,
                                         &next_cdi_attest, &next_cdi_seal),
              true);
    if (system_state_app_loading_unlocked()) {
        ASSERT_EQ(memcmp(emulator_cdi_attest, next_cdi_attest.data(),
                         DICE_CDI_SIZE),
                  0);
        ASSERT_EQ(
                memcmp(emulator_cdi_seal, next_cdi_seal.data(), DICE_CDI_SIZE),
                0);
    }

test_abort:;
}

/**
 * Test that ns_deprivilege does not block the calls to hwbcc from Trusty Apps
 * such as this test TA.
 */
TEST(hwbcc, test_ns_deprivilege) {
    int rc;
    uint8_t dice_artifacts[HWBCC_MAX_RESP_PAYLOAD_SIZE];
    size_t dice_artifacts_size;

    rc = hwbcc_ns_deprivilege();
    ASSERT_EQ(rc, 0);

    /* ns_deprivilege should not block calls from secure world. */
    memset(dice_artifacts, 0, sizeof(dice_artifacts));
    dice_artifacts_size = 0;
    rc = hwbcc_get_dice_artifacts(0, dice_artifacts, sizeof(dice_artifacts),
                                  &dice_artifacts_size);
    ASSERT_EQ(rc, 0);

test_abort:;
}

PORT_TEST(hwbcc, "com.android.trusty.hwbcc.test");
