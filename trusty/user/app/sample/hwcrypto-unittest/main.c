/*
 * Copyright (C) 2015 The Android Open Source Project
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

/*
 * Tests:
 * generic:
 * - no session / invalid session
 * - closed session
 *
 * hwkey:
 * - derive twice to same result
 * - derive different, different result
 * - keyslot, invalid slot
 *
 */

#define TLOG_TAG "hwcrypto_unittest"

#include <math.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <interface/hwkey/hwkey.h>
#include <lib/hwkey/hwkey.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define RPMB_STORAGE_AUTH_KEY_ID "com.android.trusty.storage_auth.rpmb"
#define HWCRYPTO_UNITTEST_KEYBOX_ID "com.android.trusty.hwcrypto.unittest.key32"
#define HWCRYPTO_UNITTEST_DERIVED_KEYBOX_ID \
    "com.android.trusty.hwcrypto.unittest.derived_key32"
#define HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID \
    "com.android.trusty.hwcrypto.unittest.opaque_handle"
#define HWCRYPTO_UNITTEST_OPAQUE_HANDLE2_ID \
    "com.android.trusty.hwcrypto.unittest.opaque_handle2"
#define HWCRYPTO_UNITTEST_OPAQUE_HANDLE_NOACCESS_ID \
    "com.android.trusty.hwcrypto.unittest.opaque_handle_noaccess"
#define HWCRYPTO_UNITTEST_OPAQUE_DERIVED_ID \
    "com.android.trusty.hwcrypto.unittest.opaque_derived"

#define STORAGE_AUTH_KEY_SIZE 32

static const uint8_t UNITTEST_KEYSLOT[] = "unittestkeyslotunittestkeyslotun";
static const uint8_t UNITTEST_DERIVED_KEYSLOT[] =
        "unittestderivedkeyslotunittestde";
#if WITH_HWCRYPTO_UNITTEST
#define DISABLED_WITHOUT_HWCRYPTO_UNITTEST(name) name
#else
#pragma message                                                                          \
        "hwcrypto-unittest is built with the WITH_HWCRYPTO_UNITTEST define not enabled." \
        "Hwkey tests will not test anything."
#define DISABLED_WITHOUT_HWCRYPTO_UNITTEST(name) DISABLED_##name
#endif

static bool keys_are_sufficiently_distinct(uint8_t* buf1,
                                           size_t buf1_len,
                                           uint8_t* buf2,
                                           size_t buf2_len) {
    size_t differing_bytes = 0;
    for (size_t i = 0; i < MIN(buf1_len, buf2_len); ++i) {
        if (buf1[i] ^ buf2[i]) {
            differing_bytes++;
        }
    }
    return MIN(buf1_len, buf2_len) - differing_bytes <= 4;
}

/*
 * Implement this hook for device specific hwkey tests
 */
__WEAK void run_device_hwcrypto_unittest(void) {}

TEST(hwcrypto, device_hwcrypto_unittest) {
    run_device_hwcrypto_unittest();
}

typedef struct hwkey {
    hwkey_session_t hwkey_session;
} hwkey_t;

TEST_F_SETUP(hwkey) {
    int rc;

    _state->hwkey_session = INVALID_IPC_HANDLE;
    rc = hwkey_open();
    ASSERT_GE(rc, 0);
    _state->hwkey_session = (hwkey_session_t)rc;

test_abort:;
}

TEST_F_TEARDOWN(hwkey) {
    close(_state->hwkey_session);
}

TEST_F(hwkey, generic_invalid_session) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    static const size_t size = sizeof(src_data);
    uint8_t dest[sizeof(src_data)];

    hwkey_session_t invalid = INVALID_IPC_HANDLE;
    uint32_t kdf_version = HWKEY_KDF_VERSION_BEST;

    // should fail immediately
    long rc = hwkey_derive(invalid, &kdf_version, src_data, dest, size);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "generic - bad handle");
}

TEST_F(hwkey, generic_closed_session) {
    static const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    static const uint32_t size = sizeof(src_data);
    uint8_t dest[sizeof(src_data)];
    uint32_t kdf_version = HWKEY_KDF_VERSION_BEST;

    long rc = hwkey_open();
    EXPECT_GE(rc, 0, "generic - open");

    hwkey_session_t session = (hwkey_session_t)rc;
    hwkey_close(session);

    // should fail immediately
    rc = hwkey_derive(session, &kdf_version, src_data, dest, size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "generic - closed handle");
}

TEST_F(hwkey, derive_repeatable) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    uint8_t dest[32];
    uint8_t dest2[sizeof(dest)];
    static const size_t size = sizeof(dest);
    uint32_t kdf_version = HWKEY_KDF_VERSION_BEST;
    struct hwkey_versioned_key_options versioned_args = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = 0,
            .context = src_data,
            .context_len = size,
    };

    memset(dest, 0, size);
    memset(dest2, 0, size);

    /* derive key once */
    long rc = hwkey_derive(_state->hwkey_session, &kdf_version, src_data, dest,
                           size);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, kdf_version,
              "derive repeatable - kdf version");

    /* derive key again */
    rc = hwkey_derive(_state->hwkey_session, &kdf_version, src_data, dest2,
                      size);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable - second derivation");

    /* ensure they are the same */
    rc = memcmp(dest, dest2, size);
    EXPECT_EQ(0, rc, "derive repeatable - equal");
    rc = memcmp(dest, src_data, size);
    EXPECT_NE(0, rc, "derive repeatable - same as seed");

    /* derive the same key using the versioned API fallback */
    memset(dest2, 0, size);
    versioned_args.key = dest2;
    versioned_args.key_len = size;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable - versioned API");

    /* ensure they are the same */
    rc = memcmp(dest, dest2, size);
    EXPECT_EQ(0, rc, "derive repeatable - equal");

    /* ensure that we don't derive the same key if deriving a shared key */
    memset(dest2, 0, size);
    versioned_args.shared_key = true;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable - versioned API");

    rc = memcmp(dest, dest2, size);
    EXPECT_NE(0, rc, "derive repeatable - equal");
    EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
              "derived keys share too many bytes");

    /*
     * ensure that we don't derive the same key if deriving a versioned,
     * device-unique key
     */
    memset(dest2, 0, size);
    versioned_args.shared_key = false;
    versioned_args.os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable - versioned API");

    rc = memcmp(dest, dest2, size);
    if (versioned_args.os_rollback_version > 0) {
        EXPECT_NE(0, rc, "derive repeatable - equal");
        EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
                  "derived keys share too many bytes");
    } else {
        EXPECT_EQ(0, rc, "derive repeatable - not equal");
    }
}

TEST_F(hwkey, derive_repeatable_versioned) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    uint8_t dest[128];
    uint8_t dest2[128];
    static const size_t key_len = sizeof(dest);
    long rc;
    struct hwkey_versioned_key_options versioned_args = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data,
            .context_len = sizeof(src_data),
            .key_len = key_len,
    };

    memset(dest, 0, key_len);
    memset(dest2, 0, key_len);

    versioned_args.key = dest;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable versioned - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, versioned_args.kdf_version,
              "derive repeatable versioned - kdf version");

    rc = memcmp(dest, dest2, key_len);
    EXPECT_NE(0, rc, "derive repeatable versioned - not zeroed");
    EXPECT_NE(versioned_args.os_rollback_version,
              HWKEY_ROLLBACK_VERSION_CURRENT,
              "derive repeatable current version");

    memset(dest2, 0, key_len);
    versioned_args.key = dest2;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable versioned - second derivation");

    /* ensure they are the same */
    rc = memcmp(dest, dest2, key_len);
    EXPECT_EQ(0, rc, "derive repeatable versioned - equal");
    rc = memcmp(dest, src_data, sizeof(src_data));
    EXPECT_NE(0, rc, "derive repeatable versioned - same as seed");

    /* repeat for shared keys */
    versioned_args.shared_key = true;
    memset(dest, 0, key_len);
    memset(dest2, 0, key_len);

    versioned_args.key = dest;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable versioned - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, versioned_args.kdf_version,
              "derive repeatable versioned - kdf version");

    rc = memcmp(dest, dest2, key_len);
    EXPECT_NE(0, rc, "derive repeatable versioned - not zeroed");

    memset(dest2, 0, key_len);
    versioned_args.key = dest2;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "derive repeatable versioned - second derivation");

    /* ensure they are the same */
    rc = memcmp(dest, dest2, key_len);
    EXPECT_EQ(0, rc, "derive repeatable versioned - equal");
    rc = memcmp(dest, src_data, sizeof(src_data));
    EXPECT_NE(0, rc, "derive repeatable versioned - same as seed");
}

TEST_F(hwkey, derive_different) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    const uint8_t src_data2[] = "thirtytwo-byt3s-of-nons3ns3-data";

    uint8_t dest[32];
    uint8_t dest2[sizeof(dest)];
    static const uint32_t size = sizeof(dest);
    uint32_t kdf_version = HWKEY_KDF_VERSION_BEST;

    memset(dest, 0, size);
    memset(dest2, 0, size);

    /* derive key once */
    long rc = hwkey_derive(_state->hwkey_session, &kdf_version, src_data, dest,
                           size);
    EXPECT_EQ(NO_ERROR, rc, "derive not repeatable - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, kdf_version,
              "derive not repeatable - kdf version");

    /* derive key again, with different source data */
    rc = hwkey_derive(_state->hwkey_session, &kdf_version, src_data2, dest2,
                      size);
    EXPECT_EQ(NO_ERROR, rc, "derive not repeatable - second derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");
    rc = memcmp(dest2, src_data2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
              "derived keys share too many bytes");
}

TEST_F(hwkey, derive_different_versioned) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";
    const uint8_t src_data2[] = "thirtytwo-byt3s-of-nons3ns3-data";

    uint8_t dest[32];
    uint8_t dest2[sizeof(dest)];
    uint8_t dest_shared[sizeof(dest)];
    uint8_t dest_shared2[sizeof(dest)];
    static const uint32_t size = sizeof(dest);
    long rc;
    struct hwkey_versioned_key_options versioned_args;

    memset(dest, 0, size);
    memset(dest2, 0, size);
    memset(dest_shared, 0, size);
    memset(dest_shared2, 0, size);

    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = false,
            .key = dest,
            .key_len = sizeof(dest),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, versioned_args.kdf_version,
              "derive not repeatable versioned - kdf version");

    /* derive with the same input but an older OS version */
    if (versioned_args.os_rollback_version >= 1) {
        versioned_args = (struct hwkey_versioned_key_options){
                .kdf_version = HWKEY_KDF_VERSION_BEST,
                .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
                .os_rollback_version = versioned_args.os_rollback_version - 1,
                .context = src_data,
                .context_len = sizeof(src_data),
                .shared_key = false,
                .key = dest2,
                .key_len = sizeof(dest2),
        };
        rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
        EXPECT_EQ(NO_ERROR, rc,
                  "derive not repeatable versioned - second derivation");

        /* ensure they are not the same */
        rc = memcmp(dest, dest2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal");
        rc = memcmp(dest, src_data, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal to source");
        rc = memcmp(dest2, src_data2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal to source");

        EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
                  "derived keys share too many bytes");
    }

    /* derive with different input */
    memset(dest2, 0, size);
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data2,
            .context_len = sizeof(src_data2),
            .shared_key = false,
            .key = dest2,
            .key_len = sizeof(dest2),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - second derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");
    rc = memcmp(dest2, src_data2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
              "derived keys share too many bytes");

    /* derive a shared key from the same input and ensure different */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = true,
            .key = dest_shared,
            .key_len = sizeof(dest_shared),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - second derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest_shared, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest2, dest_shared, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest, size, dest_shared, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest2, size, dest_shared, size),
              "derived keys share too many bytes");

    /* shared key, different input */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data2,
            .context_len = sizeof(src_data2),
            .shared_key = true,
            .key = dest_shared2,
            .key_len = sizeof(dest_shared2),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - second derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest2, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared2, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest, size, dest_shared, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest2, size, dest_shared, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest_shared, size, dest_shared2,
                                             size),
              "derived keys share too many bytes");

    /* derive a shared key with the same input but an older OS version */
    if (versioned_args.os_rollback_version >= 1) {
        versioned_args = (struct hwkey_versioned_key_options){
                .kdf_version = HWKEY_KDF_VERSION_BEST,
                .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
                .os_rollback_version = versioned_args.os_rollback_version - 1,
                .context = src_data,
                .context_len = sizeof(src_data),
                .shared_key = true,
                .key = dest_shared2,
                .key_len = sizeof(dest_shared2),
        };
        rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
        EXPECT_EQ(NO_ERROR, rc,
                  "derive not repeatable versioned - second derivation");

        /* ensure they are not the same */
        rc = memcmp(dest, dest_shared2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal");
        rc = memcmp(dest2, dest_shared2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal");
        rc = memcmp(dest_shared, dest_shared2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal");
        rc = memcmp(dest_shared2, src_data, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal to source");

        EXPECT_EQ(true,
                  keys_are_sufficiently_distinct(dest, size, dest_shared, size),
                  "derived keys share too many bytes");
        EXPECT_EQ(
                true,
                keys_are_sufficiently_distinct(dest2, size, dest_shared, size),
                "derived keys share too many bytes");
        EXPECT_EQ(true,
                  keys_are_sufficiently_distinct(dest_shared, size,
                                                 dest_shared2, size),
                  "derived keys share too many bytes");
    }
}

TEST_F(hwkey, derive_different_version_source) {
    const uint8_t src_data[] = "thirtytwo-bytes-of-nonsense-data";

    uint8_t dest[32];
    uint8_t dest2[sizeof(dest)];
    uint8_t dest_shared[sizeof(dest)];
    uint8_t dest_shared2[sizeof(dest)];
    static const uint32_t size = sizeof(dest);
    long rc;
    struct hwkey_versioned_key_options versioned_args;

    memset(dest, 0, size);
    memset(dest2, 0, size);
    memset(dest_shared, 0, size);
    memset(dest_shared2, 0, size);

    /* derive with current committed version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = false,
            .key = dest,
            .key_len = sizeof(dest),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - initial derivation");
    EXPECT_NE(HWKEY_KDF_VERSION_BEST, versioned_args.kdf_version,
              "derive not repeatable versioned - kdf version");

    /*
     * derive with the same input and rollback version but a different version
     * source
     */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = versioned_args.os_rollback_version,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = false,
            .key = dest2,
            .key_len = sizeof(dest2),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - different source");

    if (versioned_args.os_rollback_version > 0) {
        /* not backwards compatible derivation */
        rc = memcmp(dest, dest2, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal");
        rc = memcmp(dest, src_data, size);
        EXPECT_NE(0, rc, "derive not repeatable - equal to source");

        EXPECT_EQ(true, keys_are_sufficiently_distinct(dest, size, dest2, size),
                  "derived keys share too many bytes");
    } else {
        /* backwards compatible derivation, should be the same */
        rc = memcmp(dest, dest2, size);
        EXPECT_EQ(0, rc, "derive not repeatable - not equal");
    }

    /* derive shared key with 0 committed version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = 0,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = true,
            .key = dest_shared,
            .key_len = sizeof(dest_shared),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - initial derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest_shared, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest2, dest_shared, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest, size, dest_shared, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest2, size, dest_shared, size),
              "derived keys share too many bytes");

    /* derive shared key with 0 running version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = 0,
            .context = src_data,
            .context_len = sizeof(src_data),
            .shared_key = true,
            .key = dest_shared2,
            .key_len = sizeof(dest_shared2),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc,
              "derive not repeatable versioned - initial derivation");

    /* ensure they are not the same */
    rc = memcmp(dest, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest2, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared, dest_shared2, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal");
    rc = memcmp(dest_shared2, src_data, size);
    EXPECT_NE(0, rc, "derive not repeatable - equal to source");

    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest, size, dest_shared2, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest2, size, dest_shared2, size),
              "derived keys share too many bytes");
    EXPECT_EQ(true,
              keys_are_sufficiently_distinct(dest_shared, size, dest_shared2,
                                             size),
              "derived keys share too many bytes");
}

TEST_F(hwkey, derive_zero_length) {
    static const uint32_t size = 0;
    const uint8_t* src_data = NULL;
    uint8_t* dest = NULL;
    uint32_t kdf_version = HWKEY_KDF_VERSION_BEST;

    /* derive key once */
    long rc = hwkey_derive(_state->hwkey_session, &kdf_version, src_data, dest,
                           size);
    EXPECT_EQ(ERR_NOT_VALID, rc, "derive zero length");
}

TEST_F(hwkey, derive_version_query) {
    struct hwkey_versioned_key_options versioned_args = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = NULL,
            .context_len = 0,
            .key = NULL,
            .key_len = 0,
    };

    /* NULL key and context can be used to query the current rollback version */
    long rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "versioned derive with zero length");
    EXPECT_NE(HWKEY_ROLLBACK_VERSION_CURRENT,
              versioned_args.os_rollback_version,
              "running rollback version not updated");

    versioned_args.rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION;
    versioned_args.os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "versioned derive with zero length");
    EXPECT_NE(HWKEY_ROLLBACK_VERSION_CURRENT,
              versioned_args.os_rollback_version,
              "running rollback version not updated");

    versioned_args.key_len = 42;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(ERR_NOT_VALID, rc,
              "versioned derive with null key but non-zero length");
}

TEST_F(hwkey, derive_null_context) {
    uint8_t key[32];
    struct hwkey_versioned_key_options versioned_args = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = NULL,
            .context_len = 0,
            .key = key,
            .key_len = sizeof(key),
    };

    long rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_NE(NO_ERROR, rc, "versioned derive with empty context");

    versioned_args.context_len = 42;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(ERR_NOT_VALID, rc,
              "versioned derive with null context but non-zero length");
}

TEST_F(hwkey, derive_newer_versions) {
    uint8_t key[32];
    long rc;
    struct hwkey_versioned_key_options versioned_args;

    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "versioned derive query failed");

    /* request a newer version */

    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = versioned_args.os_rollback_version + 1,
            .key = key,
            .key_len = sizeof(key),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_NE(NO_ERROR, rc, "versioned derive with too new running version");

    /* query committed version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "versioned derive query failed");

    /* request a newer version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = versioned_args.os_rollback_version + 1,
            .key = key,
            .key_len = sizeof(key),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_NE(NO_ERROR, rc, "versioned derive with too new committed version");

    /* try a very large version */
    versioned_args = (struct hwkey_versioned_key_options){
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = INT32_MAX,
            .key = key,
            .key_len = sizeof(key),
    };
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_NE(NO_ERROR, rc, "versioned derive with far too large version");
}

TEST_F(hwkey, derive_large_payload) {
    const size_t max_payload_size =
            HWKEY_MAX_MSG_SIZE - sizeof(struct hwkey_derive_versioned_msg);
    uint8_t context[HWKEY_MAX_MSG_SIZE];
    uint8_t key[HWKEY_MAX_MSG_SIZE];
    long rc;
    struct hwkey_versioned_key_options versioned_args = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .rollback_version_source = HWKEY_ROLLBACK_RUNNING_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = context,
            .context_len = 128,
            .key = key,
            .key_len = 128,
    };

    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(NO_ERROR, rc, "versioned derive with large context and key");

    versioned_args.context_len = max_payload_size + 1;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(ERR_BAD_LEN, rc, "versioned derive with too large context");

    versioned_args.context_len = 128;
    versioned_args.key_len = max_payload_size + 1;
    rc = hwkey_derive_versioned(_state->hwkey_session, &versioned_args);
    EXPECT_EQ(ERR_BAD_LEN, rc, "versioned derive with too large key");
}

TEST_F(hwkey, get_storage_auth) {
    uint32_t actual_size = STORAGE_AUTH_KEY_SIZE;
    uint8_t storage_auth_key[STORAGE_AUTH_KEY_SIZE];
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     RPMB_STORAGE_AUTH_KEY_ID, storage_auth_key,
                                     &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "auth key accessible when it shouldn't be");
}

TEST_F(hwkey, get_keybox) {
    uint8_t dest[sizeof(HWCRYPTO_UNITTEST_KEYBOX_ID)];
    uint32_t actual_size = sizeof(dest);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_KEYBOX_ID, dest,
                                     &actual_size);

#if WITH_HWCRYPTO_UNITTEST
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest keybox");
    rc = memcmp(UNITTEST_KEYSLOT, dest, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "get storage auth key invalid");
#else
    EXPECT_EQ(ERR_NOT_FOUND, rc, "get hwcrypto-unittest keybox");
#endif
}

/*
 * The derived key slot should return UNITTEST_DERIVED_KEYSLOT after decrypting
 * it with the UNITTEST_KEYSLOT key.
 */
TEST_F(hwkey, get_derived_keybox) {
    uint8_t dest[sizeof(UNITTEST_DERIVED_KEYSLOT) - 1];
    uint32_t actual_size = sizeof(dest);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_DERIVED_KEYBOX_ID, dest,
                                     &actual_size);

#if WITH_HWCRYPTO_UNITTEST
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest derived keybox");
    rc = memcmp(UNITTEST_DERIVED_KEYSLOT, dest,
                sizeof(UNITTEST_DERIVED_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "get derived invalid");
#else
    EXPECT_EQ(ERR_NOT_FOUND, rc, "get hwcrypto-unittest derived keybox");
#endif
}

TEST_F(hwkey, get_opaque_handle) {
    uint8_t dest[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t actual_size = sizeof(dest);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID, dest,
                                     &actual_size);
#if WITH_HWCRYPTO_UNITTEST
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    rc = strnlen((const char*)dest, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    EXPECT_LT(rc, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "opaque handle is unexpected size");
#else
    EXPECT_EQ(ERR_NOT_FOUND, rc, "hwcrypto-unittest not enabled");
#endif
}

/* The following tests require hwcrpyto-unittest to do anything useful. */

TEST_F(hwkey, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(get_opaque_key)) {
    uint8_t handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t actual_size = sizeof(handle);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID, handle,
                                     &actual_size);

    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    rc = strnlen((const char*)handle, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    EXPECT_LT(rc, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "Unexpected opaque handle size");

    uint8_t key_buf[sizeof(UNITTEST_KEYSLOT) - 1] = {0};
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque key failed");

    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");
}

TEST_F(hwkey, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(get_multiple_opaque_handles)) {
    uint8_t handle1[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t actual_size = sizeof(handle1);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID,
                                     handle1, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    uint8_t handle2[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    actual_size = sizeof(handle2);
    rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                HWCRYPTO_UNITTEST_OPAQUE_HANDLE_NOACCESS_ID,
                                handle2, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    rc = memcmp(handle1, handle2, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    EXPECT_NE(0, rc, "opaque handles should not be the same");

    uint8_t key_buf[sizeof(UNITTEST_KEYSLOT)] = {0};
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle1,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "handle was not valid");
    EXPECT_EQ(actual_size, sizeof(UNITTEST_KEYSLOT) - 1, "wrong key length");
    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");

    /* we are not allowed to retrieve key material for the NOACCESS handle */
    memset(key_buf, 0, sizeof(UNITTEST_KEYSLOT));
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle2,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc,
              "should not be able to retrieve key for second handle");

    /*
     * We need to reconnect to ensure that the tokens have been dropped and
     * cleared.
     */
    hwkey_close(_state->hwkey_session);
    int new_sess = hwkey_open();
    ASSERT_GE(new_sess, 0);
    _state->hwkey_session = (hwkey_session_t)new_sess;

    /* Has the keyslot data been cleared? */
    memset(key_buf, 0, sizeof(UNITTEST_KEYSLOT));
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle1,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "handle was still valid");

    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle2,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "handle was still valid");

test_abort:;
}

/*
 * Make sure that attempting to get the same handle from multiple concurrent
 * sessions doesn't break things.
 */
TEST_F(hwkey,
       DISABLED_WITHOUT_HWCRYPTO_UNITTEST(opaque_handle_multiple_sessions)) {
    uint8_t handle1[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t actual_size = sizeof(handle1);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID,
                                     handle1, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    int new_sess = hwkey_open();
    ASSERT_GE(new_sess, 0);

    uint8_t handle2[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    actual_size = sizeof(handle2);
    rc = hwkey_get_keyslot_data(new_sess, HWCRYPTO_UNITTEST_OPAQUE_HANDLE_ID,
                                handle2, &actual_size);
    EXPECT_EQ(ERR_ALREADY_EXISTS, rc, "retrieve same handle twice");

    /* Fetch a new handle with a different keyslot from the second session */
    actual_size = sizeof(handle2);
    rc = hwkey_get_keyslot_data(new_sess, HWCRYPTO_UNITTEST_OPAQUE_HANDLE2_ID,
                                handle2, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque keybox");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    uint8_t key_buf[sizeof(UNITTEST_KEYSLOT)] = {0};

    /* Fetch the keys via the first session */
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle1,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "handle was not valid");
    EXPECT_EQ(actual_size, sizeof(UNITTEST_KEYSLOT) - 1, "wrong key length");
    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");

    memset(key_buf, 0, sizeof(UNITTEST_KEYSLOT));
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle2,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "handle was not valid");
    EXPECT_EQ(actual_size, sizeof(UNITTEST_KEYSLOT) - 1, "wrong key length");
    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");

    /* Fetch the same key via the second session */
    memset(key_buf, 0, sizeof(UNITTEST_KEYSLOT));
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(new_sess, (const char*)handle1, key_buf,
                                &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "handle was not valid");
    EXPECT_EQ(actual_size, sizeof(UNITTEST_KEYSLOT) - 1, "wrong key length");
    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");

    memset(key_buf, 0, sizeof(UNITTEST_KEYSLOT));
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(new_sess, (const char*)handle2, key_buf,
                                &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "handle was not valid");
    EXPECT_EQ(actual_size, sizeof(UNITTEST_KEYSLOT) - 1, "wrong key length");
    rc = memcmp(UNITTEST_KEYSLOT, key_buf, sizeof(UNITTEST_KEYSLOT) - 1);
    EXPECT_EQ(0, rc, "opaque key did not match expected value");

    hwkey_close(new_sess);

    /* Has the keyslot data been cleared? */
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle1,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "first session handle wasn't valid");

    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle2,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "second session handle was still valid");

    /* Disconnect the original session which retrieved the handle */
    hwkey_close(_state->hwkey_session);
    new_sess = hwkey_open();
    ASSERT_GE(new_sess, 0);
    _state->hwkey_session = (hwkey_session_t)new_sess;

    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle1,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "handle was still valid");

    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle2,
                                key_buf, &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "handle was still valid");

test_abort:;
}

TEST_F(hwkey, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(try_empty_opaque_handle)) {
    /* Reconnect just to make sure there is no spurious handles remaining. */
    hwkey_close(_state->hwkey_session);
    int new_sess = hwkey_open();
    ASSERT_GE(new_sess, 0);
    _state->hwkey_session = (hwkey_session_t)new_sess;

    uint8_t key_buf[sizeof(UNITTEST_KEYSLOT) - 1] = {0};
    uint32_t actual_size = sizeof(key_buf);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session, "", key_buf,
                                     &actual_size);
    EXPECT_EQ(ERR_NOT_FOUND, rc,
              "retrieving a key with an empty access token succeeded");

test_abort:;
}

TEST_F(hwkey, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(get_opaque_derived_key)) {
    uint8_t handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t actual_size = sizeof(handle);
    long rc = hwkey_get_keyslot_data(_state->hwkey_session,
                                     HWCRYPTO_UNITTEST_OPAQUE_DERIVED_ID,
                                     handle, &actual_size);

    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest opaque derived key");
    EXPECT_LE(actual_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    rc = strnlen((const char*)handle, HWKEY_OPAQUE_HANDLE_MAX_SIZE);
    EXPECT_EQ(rc, actual_size - 1, "Unexpected opaque handle size");

    uint8_t key_buf[sizeof(UNITTEST_DERIVED_KEYSLOT) - 1];
    actual_size = sizeof(key_buf);
    rc = hwkey_get_keyslot_data(_state->hwkey_session, (const char*)handle,
                                key_buf, &actual_size);
    EXPECT_EQ(NO_ERROR, rc, "get hwcrypto-unittest derived key failed");
    EXPECT_EQ(actual_size, sizeof(key_buf), "Unexpected opaque handle size");

    rc = memcmp(UNITTEST_DERIVED_KEYSLOT, key_buf, sizeof(key_buf));
    EXPECT_EQ(0, rc, "get derived invalid");
}

PORT_TEST(hwcrypto, "com.android.trusty.hwcrypto.test")
