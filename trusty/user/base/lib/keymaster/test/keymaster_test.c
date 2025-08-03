/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <stdint.h>
#include <stdlib.h>
#include <trusty_unittest.h>

#include <endian.h>
#include <trusty/time.h>
#include <trusty_unittest.h>

#include <dice/cbor_reader.h>

#include <lib/keymaster/keymaster.h>
#include <uapi/err.h>

#define TLOG_TAG "keymaster-test"

typedef struct fixture {
    int km_handle;
} keymaster_t;

TEST_F_SETUP(keymaster) {
    _state->km_handle = keymaster_open();
}

TEST_F_TEARDOWN(keymaster) {
    keymaster_close(_state->km_handle);
}

TEST(keymaster, open_handle) {
    int km_handle = keymaster_open();
    ASSERT_GE(km_handle, 0);
test_abort:
    keymaster_close(km_handle);
}

TEST_F(keymaster, get_key) {
    uint8_t* key_buf = NULL;
    uint32_t key_buf_size;

    int ret = keymaster_get_auth_token_key(_state->km_handle, &key_buf,
                                           &key_buf_size);
    ASSERT_EQ(ret, NO_ERROR);
    ASSERT_NE(key_buf, NULL);
    ASSERT_EQ(key_buf_size, sizeof(((hw_auth_token_t*)0)->hmac));

test_abort:
    free(key_buf);
}

TEST_F(keymaster, get_ids) {
    uint8_t* id_buf = NULL;
    uint32_t id_buf_size;

    int ret =
            keymaster_get_device_info(_state->km_handle, &id_buf, &id_buf_size);
    ASSERT_EQ(ret, NO_ERROR);
    ASSERT_NE(id_buf, NULL);
    ASSERT_GT(id_buf_size, 0);

    struct CborIn in;
    CborInInit(id_buf, id_buf_size, &in);
    size_t val;
    ASSERT_EQ(CBOR_READ_RESULT_OK, CborReadMap(&in, &val));
    // Assert that there is at least one pair. Further verification
    // is hard to do due to lack of device IDs on the QEMU test platform.
    ASSERT_GT(val, 0);

test_abort:
    free(id_buf);
}

static uint64_t calculate_checksum(void* bytes, size_t len) {
    uint64_t checksum = 0;
    for (size_t i = 0; i < len; i += sizeof(checksum)) {
        uint64_t tmp;
        /* Copy word to satisfy alignment requirements */
        memcpy(&tmp, bytes + i, sizeof(tmp));
        checksum ^= tmp;
    }
    return checksum;
}

TEST_F(keymaster, sign_and_auth_token) {
    int64_t secure_time_ns = 0;

    int ret = trusty_gettime(0, &secure_time_ns);
    ASSERT_EQ(ret, NO_ERROR);

    hw_auth_token_t token = {0,
                             0xdeadbeef,
                             0xdeadbeef,
                             0,
                             HW_AUTH_NONE,
                             htobe64(secure_time_ns),
                             {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
    uint64_t checksum = calculate_checksum(token.hmac, sizeof(token.hmac));
    ASSERT_EQ(checksum, 0);

    ret = keymaster_sign_auth_token(_state->km_handle, &token);
    ASSERT_EQ(ret, NO_ERROR);

    /* Add all the bytes into a checksum to see if this was actually set. In
       reality, this might collide with zero rarely, but only for 1/2^64
       possible hmacs. If this test fails due to collision maybe buy a
       lottery ticket. */
    checksum = calculate_checksum(token.hmac, sizeof(token.hmac));
    ASSERT_NE((uint64_t)checksum, (uint64_t)0);

    ret = keymaster_validate_auth_token(_state->km_handle, &token);
    ASSERT_EQ(ret, NO_ERROR);

    /* set the time again, so we get a different hmac */
    ret = trusty_gettime(0, &secure_time_ns);
    ASSERT_EQ(ret, NO_ERROR);

    hw_auth_token_t token2 = {0,
                              0xdeadbeef,
                              0xdeadbeef,
                              0,
                              HW_AUTH_NONE,
                              htobe64(secure_time_ns),
                              {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                               0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};

    ret = keymaster_sign_auth_token(_state->km_handle, &token2);

    uint64_t checksum2 = calculate_checksum(token2.hmac, sizeof(token2.hmac));
    ASSERT_NE(checksum2, checksum);

    ret = keymaster_validate_auth_token(_state->km_handle, &token2);
    ASSERT_EQ(ret, NO_ERROR);
test_abort:;
}

PORT_TEST(keymaster, "com.android.trusty.keymaster.secure.test")
