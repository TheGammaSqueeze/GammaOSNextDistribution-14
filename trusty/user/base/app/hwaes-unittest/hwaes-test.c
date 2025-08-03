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

#define TLOG_TAG "hwaes_unittest"

#include <stdlib.h>
#include <string.h>

#include <lib/hwaes/hwaes.h>
#include <lib/hwkey/hwkey.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define AUX_PAGE_SIZE() getauxval(AT_PAGESZ)
#define MAX_TRY_TIMES 1000
#define UNUSED_HWAES_ERROR_CODE HWAES_NO_ERROR

#define HWAES_GCM_IV_SIZE 12

#if WITH_HWCRYPTO_UNITTEST
#define DISABLED_WITHOUT_HWCRYPTO_UNITTEST(name) name
#else
#define DISABLED_WITHOUT_HWCRYPTO_UNITTEST(name) DISABLED_##name
#endif

/**
 * struct hwaes_iov - an wrapper of an array of iovec.
 * @iovs: array of iovec.
 * @num_iov: number of iovec.
 * @total_len: total length of the tipc message.
 */
struct hwaes_iov {
    struct iovec iov[TIPC_MAX_MSG_PARTS];
    size_t num_iov;
    size_t total_len;
};

/**
 * struct hwaes_shm - an wrapper of an array of shared memory handles.
 * @handles:     array of shared memory handles.
 * @num_handles: number of shared memory handles.
 */
struct hwaes_shm {
    handle_t handles[HWAES_MAX_NUM_HANDLES];
    size_t num_handles;
};

struct test_vector {
    uint32_t mode;
    struct hwcrypt_arg_in key;
    struct hwcrypt_arg_in iv;
    struct hwcrypt_arg_in aad;
    struct hwcrypt_arg_in tag;
    struct hwcrypt_arg_in plaintext;
    struct hwcrypt_arg_in ciphertext;
};

/*
 * Test vectors are from boringssl cipher_tests.txt which are in turn taken from
 * the GCM spec at
 * http://csrc.nist.gov/groups/ST/toolkit/BCM/documents/proposedmodes/gcm/gcm-spec.pdf
 *
 * We are intentionally not testing non-standard IV lengths because we don't
 * support this (yet).
 */
/* AES 128 GCM */
static const uint8_t gcm_test1_key[] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                        0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test1_iv[] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                       0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test1_plaintext[] = {
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test1_ciphertext[] = {
        0x03, 0x88, 0xda, 0xce, 0x60, 0xb6, 0xa3, 0x92,
        0xf3, 0x28, 0xc2, 0xb9, 0x71, 0xb2, 0xfe, 0x78};
static const uint8_t gcm_test1_aad[] = {};
static const uint8_t gcm_test1_tag[] = {0xab, 0x6e, 0x47, 0xd4, 0x2c, 0xec,
                                        0x13, 0xbd, 0xf5, 0x3a, 0x67, 0xb2,
                                        0x12, 0x57, 0xbd, 0xdf};

static const uint8_t gcm_test2_key[] = {0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65,
                                        0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94,
                                        0x67, 0x30, 0x83, 0x08};
static const uint8_t gcm_test2_iv[] = {0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce,
                                       0xdb, 0xad, 0xde, 0xca, 0xf8, 0x88};
static const uint8_t gcm_test2_plaintext[] = {
        0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5, 0xa5, 0x59, 0x09,
        0xc5, 0xaf, 0xf5, 0x26, 0x9a, 0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34,
        0xf7, 0xda, 0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72, 0x1c,
        0x3c, 0x0c, 0x95, 0x95, 0x68, 0x09, 0x53, 0x2f, 0xcf, 0x0e, 0x24,
        0x49, 0xa6, 0xb5, 0x25, 0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6,
        0x57, 0xba, 0x63, 0x7b, 0x39, 0x1a, 0xaf, 0xd2, 0x55};
static const uint8_t gcm_test2_ciphertext[] = {
        0x42, 0x83, 0x1e, 0xc2, 0x21, 0x77, 0x74, 0x24, 0x4b, 0x72, 0x21,
        0xb7, 0x84, 0xd0, 0xd4, 0x9c, 0xe3, 0xaa, 0x21, 0x2f, 0x2c, 0x02,
        0xa4, 0xe0, 0x35, 0xc1, 0x7e, 0x23, 0x29, 0xac, 0xa1, 0x2e, 0x21,
        0xd5, 0x14, 0xb2, 0x54, 0x66, 0x93, 0x1c, 0x7d, 0x8f, 0x6a, 0x5a,
        0xac, 0x84, 0xaa, 0x05, 0x1b, 0xa3, 0x0b, 0x39, 0x6a, 0x0a, 0xac,
        0x97, 0x3d, 0x58, 0xe0, 0x91, 0x47, 0x3f, 0x59, 0x85};
static const uint8_t gcm_test2_aad[] = {};
static const uint8_t gcm_test2_tag[] = {0x4d, 0x5c, 0x2a, 0xf3, 0x27, 0xcd,
                                        0x64, 0xa6, 0x2c, 0xf3, 0x5a, 0xbd,
                                        0x2b, 0xa6, 0xfa, 0xb4};

static const uint8_t gcm_test3_key[] = {0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65,
                                        0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94,
                                        0x67, 0x30, 0x83, 0x08};
static const uint8_t gcm_test3_iv[] = {0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce,
                                       0xdb, 0xad, 0xde, 0xca, 0xf8, 0x88};
static const uint8_t gcm_test3_plaintext[] = {
        0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5, 0xa5, 0x59, 0x09, 0xc5,
        0xaf, 0xf5, 0x26, 0x9a, 0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34, 0xf7, 0xda,
        0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72, 0x1c, 0x3c, 0x0c, 0x95,
        0x95, 0x68, 0x09, 0x53, 0x2f, 0xcf, 0x0e, 0x24, 0x49, 0xa6, 0xb5, 0x25,
        0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6, 0x57, 0xba, 0x63, 0x7b, 0x39};
static const uint8_t gcm_test3_ciphertext[] = {
        0x42, 0x83, 0x1e, 0xc2, 0x21, 0x77, 0x74, 0x24, 0x4b, 0x72, 0x21, 0xb7,
        0x84, 0xd0, 0xd4, 0x9c, 0xe3, 0xaa, 0x21, 0x2f, 0x2c, 0x02, 0xa4, 0xe0,
        0x35, 0xc1, 0x7e, 0x23, 0x29, 0xac, 0xa1, 0x2e, 0x21, 0xd5, 0x14, 0xb2,
        0x54, 0x66, 0x93, 0x1c, 0x7d, 0x8f, 0x6a, 0x5a, 0xac, 0x84, 0xaa, 0x05,
        0x1b, 0xa3, 0x0b, 0x39, 0x6a, 0x0a, 0xac, 0x97, 0x3d, 0x58, 0xe0, 0x91};
static const uint8_t gcm_test3_aad[] = {
        0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef, 0xfe, 0xed,
        0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef, 0xab, 0xad, 0xda, 0xd2};
static const uint8_t gcm_test3_tag[] = {0x5b, 0xc9, 0x4f, 0xbc, 0x32, 0x21,
                                        0xa5, 0xdb, 0x94, 0xfa, 0xe9, 0x5a,
                                        0xe7, 0x12, 0x1a, 0x47};

/* AES 256 GCM */
static const uint8_t gcm_test4_key[] = {
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test4_iv[] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                       0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test4_plaintext[] = {
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t gcm_test4_ciphertext[] = {
        0xce, 0xa7, 0x40, 0x3d, 0x4d, 0x60, 0x6b, 0x6e,
        0x07, 0x4e, 0xc5, 0xd3, 0xba, 0xf3, 0x9d, 0x18};
static const uint8_t gcm_test4_aad[] = {};
static const uint8_t gcm_test4_tag[] = {0xd0, 0xd1, 0xc8, 0xa7, 0x99, 0x99,
                                        0x6b, 0xf0, 0x26, 0x5b, 0x98, 0xb5,
                                        0xd4, 0x8a, 0xb9, 0x19};

static const uint8_t gcm_test5_key[] = {
        0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a, 0x8f,
        0x94, 0x67, 0x30, 0x83, 0x08, 0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65,
        0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30, 0x83, 0x08};
static const uint8_t gcm_test5_iv[] = {0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce,
                                       0xdb, 0xad, 0xde, 0xca, 0xf8, 0x88};
static const uint8_t gcm_test5_plaintext[] = {
        0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5, 0xa5, 0x59, 0x09,
        0xc5, 0xaf, 0xf5, 0x26, 0x9a, 0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34,
        0xf7, 0xda, 0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72, 0x1c,
        0x3c, 0x0c, 0x95, 0x95, 0x68, 0x09, 0x53, 0x2f, 0xcf, 0x0e, 0x24,
        0x49, 0xa6, 0xb5, 0x25, 0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6,
        0x57, 0xba, 0x63, 0x7b, 0x39, 0x1a, 0xaf, 0xd2, 0x55};
static const uint8_t gcm_test5_ciphertext[] = {
        0x52, 0x2d, 0xc1, 0xf0, 0x99, 0x56, 0x7d, 0x07, 0xf4, 0x7f, 0x37,
        0xa3, 0x2a, 0x84, 0x42, 0x7d, 0x64, 0x3a, 0x8c, 0xdc, 0xbf, 0xe5,
        0xc0, 0xc9, 0x75, 0x98, 0xa2, 0xbd, 0x25, 0x55, 0xd1, 0xaa, 0x8c,
        0xb0, 0x8e, 0x48, 0x59, 0x0d, 0xbb, 0x3d, 0xa7, 0xb0, 0x8b, 0x10,
        0x56, 0x82, 0x88, 0x38, 0xc5, 0xf6, 0x1e, 0x63, 0x93, 0xba, 0x7a,
        0x0a, 0xbc, 0xc9, 0xf6, 0x62, 0x89, 0x80, 0x15, 0xad};
static const uint8_t gcm_test5_aad[] = {};
static const uint8_t gcm_test5_tag[] = {0xb0, 0x94, 0xda, 0xc5, 0xd9, 0x34,
                                        0x71, 0xbd, 0xec, 0x1a, 0x50, 0x22,
                                        0x70, 0xe3, 0xcc, 0x6c};

static const uint8_t gcm_test6_key[] = {
        0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c, 0x6d, 0x6a, 0x8f,
        0x94, 0x67, 0x30, 0x83, 0x08, 0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65,
        0x73, 0x1c, 0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30, 0x83, 0x08};
static const uint8_t gcm_test6_iv[] = {0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce,
                                       0xdb, 0xad, 0xde, 0xca, 0xf8, 0x88};
static const uint8_t gcm_test6_plaintext[] = {
        0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5, 0xa5, 0x59, 0x09, 0xc5,
        0xaf, 0xf5, 0x26, 0x9a, 0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34, 0xf7, 0xda,
        0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72, 0x1c, 0x3c, 0x0c, 0x95,
        0x95, 0x68, 0x09, 0x53, 0x2f, 0xcf, 0x0e, 0x24, 0x49, 0xa6, 0xb5, 0x25,
        0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6, 0x57, 0xba, 0x63, 0x7b, 0x39};
static const uint8_t gcm_test6_ciphertext[] = {
        0x52, 0x2d, 0xc1, 0xf0, 0x99, 0x56, 0x7d, 0x07, 0xf4, 0x7f, 0x37, 0xa3,
        0x2a, 0x84, 0x42, 0x7d, 0x64, 0x3a, 0x8c, 0xdc, 0xbf, 0xe5, 0xc0, 0xc9,
        0x75, 0x98, 0xa2, 0xbd, 0x25, 0x55, 0xd1, 0xaa, 0x8c, 0xb0, 0x8e, 0x48,
        0x59, 0x0d, 0xbb, 0x3d, 0xa7, 0xb0, 0x8b, 0x10, 0x56, 0x82, 0x88, 0x38,
        0xc5, 0xf6, 0x1e, 0x63, 0x93, 0xba, 0x7a, 0x0a, 0xbc, 0xc9, 0xf6, 0x62};
static const uint8_t gcm_test6_aad[] = {
        0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef, 0xfe, 0xed,
        0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef, 0xab, 0xad, 0xda, 0xd2};
static const uint8_t gcm_test6_tag[] = {0x76, 0xfc, 0x6e, 0xce, 0x0f, 0x4e,
                                        0x17, 0x68, 0xcd, 0xdf, 0x88, 0x53,
                                        0xbb, 0x2d, 0x55, 0x1b};

/* clang-format off */
#define TV_DATA(name_, field_)                                                 \
    .field_ = {.data_ptr = &name_##_##field_, .len = sizeof(name_##_##field_)}

#define TV(mode_, name_)                                                       \
    {                                                                          \
        .mode = mode_,                                                         \
        TV_DATA(name_, key),                                                   \
        TV_DATA(name_, iv),                                                    \
        TV_DATA(name_, aad),                                                   \
        TV_DATA(name_, tag),                                                   \
        TV_DATA(name_, ciphertext),                                            \
        TV_DATA(name_, plaintext)                                              \
    }
/* clang-format on */

static const struct test_vector vectors[] = {
        TV(HWAES_GCM_MODE, gcm_test1), TV(HWAES_GCM_MODE, gcm_test2),
        TV(HWAES_GCM_MODE, gcm_test3), TV(HWAES_GCM_MODE, gcm_test4),
        TV(HWAES_GCM_MODE, gcm_test5), TV(HWAES_GCM_MODE, gcm_test6),
};

static void parse_vector(const struct test_vector* vector,
                         struct hwcrypt_shm_hd* shm_handle,
                         struct hwcrypt_args* args,
                         int encrypt) {
    args->key_type = HWAES_PLAINTEXT_KEY;
    args->padding = HWAES_NO_PADDING;
    args->mode = vector->mode;

    args->key = vector->key;
    args->iv = vector->iv;
    args->aad = vector->aad;
    if (encrypt) {
        args->text_in = vector->plaintext;
    } else {
        args->text_in = vector->ciphertext;
        args->tag_in = vector->tag;
    }

    uint8_t* base = (uint8_t*)shm_handle->base;

    args->text_out.data_ptr = base;
    args->text_out.len = args->text_in.len;
    args->text_out.shm_hd_ptr = shm_handle;

    if (encrypt && vector->tag.len > 0) {
        args->tag_out.data_ptr = base + args->text_out.len;
        args->tag_out.len = vector->tag.len;
        args->tag_out.shm_hd_ptr = shm_handle;
    }
}

static const uint8_t hwaes_key[32];
static const uint8_t hwaes_iv[16];
static const uint8_t hwaes_cbc_plaintext[] = {
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static const uint8_t hwaes_cbc_ciphertext[] = {
        0xdc, 0x95, 0xc0, 0x78, 0xa2, 0x40, 0x89, 0x89, 0xad, 0x48, 0xa2,
        0x14, 0x92, 0x84, 0x20, 0x87, 0x08, 0xc3, 0x74, 0x84, 0x8c, 0x22,
        0x82, 0x33, 0xc2, 0xb3, 0x4f, 0x33, 0x2b, 0xd2, 0xe9, 0xd3};

typedef struct hwaes {
    hwaes_session_t hwaes_session;
    handle_t memref;
    void* shm_base;
    size_t shm_len;
    struct hwcrypt_shm_hd shm_hd;
    struct hwcrypt_args args_encrypt;
    struct hwcrypt_args args_decrypt;
    struct hwaes_req req_hdr;
    struct hwaes_aes_req cmd_hdr;
    struct hwaes_shm_desc shm_descs[HWAES_MAX_NUM_HANDLES];
    struct hwaes_iov req_iov;
    struct hwaes_shm req_shm;
} hwaes_t;

static void make_bad_request(handle_t channel,
                             struct hwaes_iov* req_iov,
                             struct hwaes_shm* req_shm,
                             bool expect_reply,
                             uint32_t expect_error) {
    struct uevent event;
    ipc_msg_info_t msg_inf;
    bool got_msg = false;

    ipc_msg_t req_msg = {
            .iov = req_iov->iov,
            .num_iov = req_iov->num_iov,
            .handles = req_shm->handles,
            .num_handles = req_shm->num_handles,
    };

    int rc;
    rc = send_msg(channel, &req_msg);
    ASSERT_EQ((size_t)rc, req_iov->total_len);

    rc = wait(channel, &event, INFINITE_TIME);
    ASSERT_EQ(rc, NO_ERROR);

    if (expect_reply) {
        ASSERT_NE(event.event & IPC_HANDLE_POLL_MSG, 0);
    } else {
        ASSERT_EQ(event.event, IPC_HANDLE_POLL_HUP);
        return;
    }

    rc = get_msg(channel, &msg_inf);
    ASSERT_EQ(rc, NO_ERROR);

    got_msg = true;
    ASSERT_EQ(msg_inf.len, sizeof(struct hwaes_resp));

    struct hwaes_resp resp_hdr = {0};
    struct iovec resp_iov = {
            .iov_base = (void*)&resp_hdr,
            .iov_len = sizeof(resp_hdr),
    };
    ipc_msg_t resp_msg = {
            .iov = &resp_iov,
            .num_iov = 1,
            .handles = NULL,
            .num_handles = 0,
    };
    rc = read_msg(channel, msg_inf.id, 0, &resp_msg);
    ASSERT_EQ((size_t)rc, msg_inf.len);

    struct hwaes_req* req_hdr = (struct hwaes_req*)req_iov->iov[0].iov_base;
    ASSERT_EQ(resp_hdr.cmd, req_hdr->cmd | HWAES_RESP_BIT);

    put_msg(channel, msg_inf.id);
    EXPECT_EQ(expect_error, resp_hdr.result);
    return;

test_abort:
    if (got_msg) {
        put_msg(channel, msg_inf.id);
    }
    return;
}

TEST_F_SETUP(hwaes) {
    int rc;
    void* shm_base;
    size_t shm_len = AUX_PAGE_SIZE();
    _state->hwaes_session = INVALID_IPC_HANDLE;
    _state->memref = INVALID_IPC_HANDLE;
    _state->shm_base = NULL;

    rc = hwaes_open(&_state->hwaes_session);
    ASSERT_EQ(rc, 0);

    shm_base = memalign(AUX_PAGE_SIZE(), shm_len);
    ASSERT_NE(NULL, shm_base, "fail to allocate shared memory");

    rc = memref_create(shm_base, shm_len, PROT_READ | PROT_WRITE);
    ASSERT_GE(rc, 0);
    _state->memref = (handle_t)rc;
    _state->shm_base = shm_base;
    _state->shm_len = shm_len;
    memset(_state->shm_base, 0, _state->shm_len);
    memcpy(_state->shm_base, hwaes_cbc_plaintext, sizeof(hwaes_cbc_plaintext));

    _state->shm_hd = (struct hwcrypt_shm_hd){
            .handle = _state->memref,
            .base = _state->shm_base,
            .size = _state->shm_len,
    };

    _state->args_encrypt = (struct hwcrypt_args){
            .key =
                    {
                            .data_ptr = hwaes_key,
                            .len = sizeof(hwaes_key),
                    },
            .iv =
                    {
                            .data_ptr = hwaes_iv,
                            .len = sizeof(hwaes_iv),
                    },
            .text_in =
                    {
                            .data_ptr = _state->shm_base,
                            .len = sizeof(hwaes_cbc_plaintext),
                            .shm_hd_ptr = &_state->shm_hd,
                    },
            .text_out =
                    {
                            .data_ptr = _state->shm_base,
                            .len = sizeof(hwaes_cbc_ciphertext),
                            .shm_hd_ptr = &_state->shm_hd,
                    },
            .key_type = HWAES_PLAINTEXT_KEY,
            .padding = HWAES_NO_PADDING,
            .mode = HWAES_CBC_MODE,
    };

    _state->args_decrypt = (struct hwcrypt_args){
            .key =
                    {
                            .data_ptr = hwaes_key,
                            .len = sizeof(hwaes_key),
                    },
            .iv =
                    {
                            .data_ptr = hwaes_iv,
                            .len = sizeof(hwaes_iv),
                    },
            .text_in =
                    {
                            .data_ptr = _state->shm_base,
                            .len = sizeof(hwaes_cbc_ciphertext),
                            .shm_hd_ptr = &_state->shm_hd,
                    },
            .text_out =
                    {
                            .data_ptr = _state->shm_base,
                            .len = sizeof(hwaes_cbc_plaintext),
                            .shm_hd_ptr = &_state->shm_hd,
                    },
            .key_type = HWAES_PLAINTEXT_KEY,
            .padding = HWAES_NO_PADDING,
            .mode = HWAES_CBC_MODE,
    };

    _state->req_hdr = (struct hwaes_req){
            .cmd = HWAES_AES,
    };
    _state->cmd_hdr = (struct hwaes_aes_req){
            .key =
                    (struct hwaes_data_desc){
                            .len = sizeof(hwaes_key),
                            .shm_idx = 0,
                    },
            .num_handles = 1,
    };
    _state->shm_descs[0] = (struct hwaes_shm_desc){.size = _state->shm_len};
    _state->req_iov = (struct hwaes_iov){
            .iov =
                    {
                            {&_state->req_hdr, sizeof(_state->req_hdr)},
                            {&_state->cmd_hdr, sizeof(_state->cmd_hdr)},
                            {&_state->shm_descs, sizeof(struct hwaes_shm_desc)},
                    },
            .num_iov = 3,
            .total_len = sizeof(_state->req_hdr) + sizeof(_state->cmd_hdr) +
                         sizeof(struct hwaes_shm_desc),
    };
    _state->req_shm = (struct hwaes_shm){
            .handles = {_state->memref},
            .num_handles = 1,
    };

test_abort:;
}

TEST_F_TEARDOWN(hwaes) {
    close(_state->hwaes_session);
    close(_state->memref);
    free(_state->shm_base);
}

TEST_F(hwaes, GenericInvalidSession) {
    hwaes_session_t invalid = INVALID_IPC_HANDLE;
    struct hwcrypt_args args = {};

    // should fail immediately
    int rc = hwaes_encrypt(invalid, &args);

    EXPECT_EQ(ERR_BAD_HANDLE, rc, "generic - bad handle");
}

TEST_F(hwaes, RequestHeaderReservedNotZero) {
    _state->req_hdr.reserved = 1U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     false, UNUSED_HWAES_ERROR_CODE);
}

TEST_F(hwaes, CommandUnsupported) {
    _state->req_hdr.cmd = 0U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_NOT_IMPLEMENTED);
}

TEST_F(hwaes, CommandHeaderReservedNotZero) {
    _state->cmd_hdr.reserved = 1U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     false, UNUSED_HWAES_ERROR_CODE);
}

TEST_F(hwaes, SharedMemoryHandlesNumberConflict) {
    _state->cmd_hdr.num_handles += 1;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     false, UNUSED_HWAES_ERROR_CODE);
}

TEST_F(hwaes, SharedMemoryDescriptorReservedNotZero) {
    _state->shm_descs[0].reserved = 1U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_IO);
}

TEST_F(hwaes, SharedMemoryDescriptorWrongWriteFlag) {
    _state->shm_descs[0].write = 2U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_IO);
}

TEST_F(hwaes, SharedMemoryDescriptorBadSize) {
    /* size is not page aligned */
    _state->shm_descs[0].size = 4;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_INVALID_ARGS);
}

TEST_F(hwaes, DataDescriptorReservedNotZero) {
    _state->cmd_hdr.key.reserved = 1U;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_IO);
}

TEST_F(hwaes, DataDescriptorBadLength) {
    _state->cmd_hdr.key.len = _state->shm_len + 1ULL;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_INVALID_ARGS);
}

TEST_F(hwaes, DataDescriptorBadSharedMemoryHandleIndex) {
    _state->cmd_hdr.key.shm_idx = 4;
    make_bad_request(_state->hwaes_session, &_state->req_iov, &_state->req_shm,
                     true, HWAES_ERR_IO);
}
TEST_F(hwaes, InvalidSharedMemoryHandle) {
    struct hwcrypt_shm_hd bad_shm_hd = {
            .handle = INVALID_IPC_HANDLE,
            .base = _state->shm_base,
            .size = _state->shm_len,
    };

    _state->args_encrypt.text_in.shm_hd_ptr = &bad_shm_hd;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "expect bad handle error");
}

TEST_F(hwaes, BadSharedMemorySize) {
    struct hwcrypt_shm_hd bad_shm_hd = {
            .handle = _state->memref,
            .base = _state->shm_base,
            .size = 0,
    };

    _state->args_encrypt.text_in.shm_hd_ptr = &bad_shm_hd;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect bad length error");
}

TEST_F(hwaes, KeyArgumentNotSetEncrypt) {
    _state->args_encrypt.key.len = 0;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, IVArgumentNotSetEncrypt) {
    _state->args_encrypt.iv.len = 0;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, TextInArgumentNotSetEncrypt) {
    _state->args_encrypt.text_in.len = 0;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, TextOutArgumentNotSetEncrypt) {
    _state->args_encrypt.text_out.len = 0;

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, KeyArgumentNotSetDecrypt) {
    _state->args_decrypt.key.len = 0;

    int rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, IVArgumentNotSetDecrypt) {
    _state->args_decrypt.iv.len = 0;

    int rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, TextInArgumentNotSetDecrypt) {
    _state->args_decrypt.text_in.len = 0;

    int rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, TextOutArgumentNotSetDecrypt) {
    _state->args_decrypt.text_out.len = 0;

    int rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "expect invalid_args error");
}

TEST_F(hwaes, EncryptionDecryptionCBC) {
    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);

    EXPECT_EQ(NO_ERROR, rc, "encryption - cbc mode");
    rc = memcmp(_state->shm_base, hwaes_cbc_ciphertext,
                sizeof(hwaes_cbc_ciphertext));
    EXPECT_EQ(0, rc, "wrong encryption result");

    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);

    EXPECT_EQ(NO_ERROR, rc, "decryption - cbc mode");
    rc = memcmp(_state->shm_base, hwaes_cbc_plaintext,
                sizeof(hwaes_cbc_plaintext));
    EXPECT_EQ(0, rc, "wrong decryption result");
}

TEST_F(hwaes, EncryptionDecryptionCBCNoSHM) {
    uint8_t buf[sizeof(hwaes_cbc_plaintext)] = {0};
    memcpy(buf, hwaes_cbc_plaintext, sizeof(hwaes_cbc_plaintext));

    _state->args_encrypt.text_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    _state->args_encrypt.text_out = (struct hwcrypt_arg_out){
            .data_ptr = buf,
            .len = sizeof(buf),
    };

    _state->args_decrypt.text_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    _state->args_decrypt.text_out = (struct hwcrypt_arg_out){
            .data_ptr = buf,
            .len = sizeof(buf),
    };

    int rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);

    EXPECT_EQ(NO_ERROR, rc, "encryption - cbc mode");
    rc = memcmp(buf, hwaes_cbc_ciphertext, sizeof(hwaes_cbc_ciphertext));
    EXPECT_EQ(0, rc, "wrong encryption result");

    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);

    EXPECT_EQ(NO_ERROR, rc, "decryption - cbc mode");
    rc = memcmp(buf, hwaes_cbc_plaintext, sizeof(hwaes_cbc_plaintext));
    EXPECT_EQ(0, rc, "wrong decryption result");
}

TEST_F(hwaes, RunEncryptMany) {
    int rc;
    for (size_t i = 0; i < MAX_TRY_TIMES; i++) {
        rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
        ASSERT_EQ(NO_ERROR, rc, "encryption - in loop");
    }

    memcpy(_state->shm_base, hwaes_cbc_plaintext, sizeof(hwaes_cbc_plaintext));
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(NO_ERROR, rc, "encryption - final round");
    rc = memcmp(_state->shm_base, hwaes_cbc_ciphertext,
                sizeof(hwaes_cbc_ciphertext));
    EXPECT_EQ(0, rc, "wrong encryption result");

test_abort:;
}

TEST_F(hwaes, EncryptVectors) {
    const struct test_vector* vector = vectors;
    for (unsigned long i = 0; i < countof(vectors); ++i, ++vector) {
        memset(_state->shm_base, 0, _state->shm_len);
        struct hwcrypt_args args = {};

        struct hwcrypt_shm_hd shm_hd = {
                .handle = _state->memref,
                .base = _state->shm_base,
                .size = _state->shm_len,
        };
        parse_vector(vector, &shm_hd, &args, 1 /* encrypt */);

        int rc = hwaes_encrypt(_state->hwaes_session, &args);
        EXPECT_EQ(NO_ERROR, rc, "Encryption failed for test vector %lu\n", i);

        rc = memcmp(_state->shm_base, vector->ciphertext.data_ptr,
                    vector->ciphertext.len);
        EXPECT_EQ(0, rc, "wrong ciphertext result");

        if (vector->tag.len > 0) {
            rc = memcmp((uint8_t*)_state->shm_base + vector->ciphertext.len,
                        vector->tag.data_ptr, vector->tag.len);
            EXPECT_EQ(0, rc, "wrong encryption tag result");
        }
    }

test_abort:;
}

TEST_F(hwaes, DecryptVectors) {
    const struct test_vector* vector = vectors;
    for (unsigned long i = 0; i < countof(vectors); ++i, ++vector) {
        memset(_state->shm_base, 0, _state->shm_len);
        struct hwcrypt_args args = {};

        struct hwcrypt_shm_hd shm_hd = {
                .handle = _state->memref,
                .base = _state->shm_base,
                .size = _state->shm_len,
        };
        parse_vector(vector, &shm_hd, &args, 0 /* decrypt */);

        int rc = hwaes_decrypt(_state->hwaes_session, &args);
        EXPECT_EQ(NO_ERROR, rc, "Decryption failed for test vector %lu\n", i);

        rc = memcmp(_state->shm_base, vector->plaintext.data_ptr,
                    vector->plaintext.len);
        EXPECT_EQ(0, rc, "wrong decryption result");
    }

test_abort:;
}

TEST_F(hwaes, InvalidAEADArgsForCBC) {
    int rc;
    uint8_t buf[sizeof(hwaes_cbc_plaintext)] = {0};

    _state->args_encrypt.aad = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported AAD");

    _state->args_encrypt.aad = (struct hwcrypt_arg_in){};
    _state->args_encrypt.tag_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported tag");

    _state->args_encrypt.tag_in = (struct hwcrypt_arg_in){};
    _state->args_encrypt.tag_out = (struct hwcrypt_arg_out){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported tag");

    _state->args_decrypt.aad = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported AAD");

    _state->args_decrypt.aad = (struct hwcrypt_arg_in){};
    _state->args_decrypt.tag_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported tag");

    _state->args_decrypt.tag_in = (struct hwcrypt_arg_in){};
    _state->args_decrypt.tag_out = (struct hwcrypt_arg_out){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "unsupported tag");
}

TEST_F(hwaes, InvalidGCMTagArgs) {
    int rc;
    uint8_t buf[16] = {0};

    _state->args_encrypt.mode = HWAES_GCM_MODE;
    ASSERT_LE(HWAES_GCM_IV_SIZE, _state->args_encrypt.iv.len,
              "IV length too short");
    _state->args_encrypt.iv.len = HWAES_GCM_IV_SIZE;

    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "missing tag output for GCM");

    _state->args_encrypt.tag_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "wrong tag direction for encryption");

    _state->args_decrypt.mode = HWAES_GCM_MODE;
    ASSERT_LE(HWAES_GCM_IV_SIZE, _state->args_decrypt.iv.len,
              "IV length too short");
    _state->args_decrypt.iv.len = HWAES_GCM_IV_SIZE;

    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "missing tag input for GCM");

    _state->args_decrypt.tag_in = (struct hwcrypt_arg_in){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    _state->args_decrypt.tag_out = (struct hwcrypt_arg_out){
            .data_ptr = buf,
            .len = sizeof(buf),
    };
    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "wrong tag direction for decryption");

test_abort:;
}

static const uint8_t opaque_key_ciphertext[] = {
        0x8c, 0xc8, 0xc0, 0xc7, 0x76, 0x57, 0xb4, 0x4f, 0x22, 0xd3, 0x33,
        0x2e, 0x6f, 0x23, 0x92, 0x51, 0x21, 0x69, 0x30, 0xff, 0x84, 0x88,
        0x4c, 0x38, 0x04, 0xe5, 0x17, 0xad, 0x5c, 0x08, 0x87, 0xfc,
};

#define HWKEY_OPAQUE_KEY_ID "com.android.trusty.hwaes.unittest.opaque_handle"

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(EncryptWithOpaqueKey)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t key_handle_size = HWKEY_OPAQUE_HANDLE_MAX_SIZE;
    rc = hwkey_get_keyslot_data(hwkey_session, HWKEY_OPAQUE_KEY_ID, key_handle,
                                &key_handle_size);
    EXPECT_EQ(NO_ERROR, rc, "Could not get opaque key handle");
    EXPECT_LE(key_handle_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "Wrong handle size");

    _state->args_encrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_encrypt.key.data_ptr = key_handle;
    _state->args_encrypt.key.len = key_handle_size;
    _state->args_encrypt.key.shm_hd_ptr = NULL;

    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);

    EXPECT_EQ(NO_ERROR, rc, "opaque encryption - cbc mode");
    rc = memcmp(_state->shm_base, opaque_key_ciphertext,
                sizeof(opaque_key_ciphertext));
    EXPECT_EQ(0, rc, "wrong encryption result");

    hwkey_close(hwkey_session);
test_abort:;
}

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(DecryptWithOpaqueKey)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t key_handle_size = HWKEY_OPAQUE_HANDLE_MAX_SIZE;
    rc = hwkey_get_keyslot_data(hwkey_session, HWKEY_OPAQUE_KEY_ID, key_handle,
                                &key_handle_size);
    EXPECT_EQ(NO_ERROR, rc, "Could not get opaque key handle");
    EXPECT_LE(key_handle_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "Wrong handle size");

    _state->args_decrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_decrypt.key.data_ptr = key_handle;
    _state->args_decrypt.key.len = key_handle_size;
    _state->args_decrypt.key.shm_hd_ptr = NULL;

    memcpy(_state->shm_base, opaque_key_ciphertext,
           sizeof(opaque_key_ciphertext));
    _state->args_decrypt.text_in.data_ptr = _state->shm_base;
    _state->args_decrypt.text_in.len = sizeof(opaque_key_ciphertext);
    _state->args_decrypt.text_in.shm_hd_ptr = &_state->shm_hd;

    rc = hwaes_decrypt(_state->hwaes_session, &_state->args_decrypt);

    EXPECT_EQ(NO_ERROR, rc, "opaque decryption - cbc mode");
    rc = memcmp(_state->shm_base, hwaes_cbc_plaintext,
                sizeof(hwaes_cbc_plaintext));
    EXPECT_EQ(0, rc, "wrong decryption result");

    hwkey_close(hwkey_session);
test_abort:;
}

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(RunOpaqueEncryptMany)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t key_handle_size = HWKEY_OPAQUE_HANDLE_MAX_SIZE;
    rc = hwkey_get_keyslot_data(hwkey_session, HWKEY_OPAQUE_KEY_ID, key_handle,
                                &key_handle_size);
    EXPECT_EQ(NO_ERROR, rc, "Could not get opaque key handle");
    EXPECT_LE(key_handle_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "Wrong handle size");

    _state->args_encrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_encrypt.key.data_ptr = key_handle;
    _state->args_encrypt.key.len = key_handle_size;
    _state->args_encrypt.key.shm_hd_ptr = NULL;

    for (size_t i = 0; i < MAX_TRY_TIMES; i++) {
        rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
        ASSERT_EQ(NO_ERROR, rc, "encryption - in loop");
    }

    memcpy(_state->shm_base, hwaes_cbc_plaintext, sizeof(hwaes_cbc_plaintext));
    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(NO_ERROR, rc, "encryption - final round");
    rc = memcmp(_state->shm_base, opaque_key_ciphertext,
                sizeof(opaque_key_ciphertext));
    EXPECT_EQ(0, rc, "wrong encryption result");

    hwkey_close(hwkey_session);
test_abort:;
}

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(InvalidOpaqueKeySize)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE + 1] = {0};

    _state->args_encrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_encrypt.key.data_ptr = key_handle;
    _state->args_encrypt.key.len = sizeof(key_handle);
    _state->args_encrypt.key.shm_hd_ptr = NULL;

    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);

    EXPECT_EQ(ERR_INVALID_ARGS, rc, "Did not error on invalid opaque key size");

    hwkey_close(hwkey_session);
test_abort:;
}

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(InvalidOpaqueKeyTerminator)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE];

    /* Non-null terminated handle */
    memset(key_handle, 0xa, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

    _state->args_encrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_encrypt.key.data_ptr = key_handle;
    _state->args_encrypt.key.len = sizeof(key_handle);
    _state->args_encrypt.key.shm_hd_ptr = NULL;

    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);

    EXPECT_EQ(ERR_INVALID_ARGS, rc, "Did not error on invalid opaque key");

    hwkey_close(hwkey_session);
test_abort:;
}

TEST_F(hwaes, DISABLED_WITHOUT_HWCRYPTO_UNITTEST(OutdatedOpaqueHandle)) {
    long rc = hwkey_open();
    ASSERT_GE(rc, 0, "Could not connect to hwkey");
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    uint8_t key_handle[HWKEY_OPAQUE_HANDLE_MAX_SIZE] = {0};
    uint32_t key_handle_size = HWKEY_OPAQUE_HANDLE_MAX_SIZE;
    rc = hwkey_get_keyslot_data(hwkey_session, HWKEY_OPAQUE_KEY_ID, key_handle,
                                &key_handle_size);
    EXPECT_EQ(NO_ERROR, rc, "Could not get opaque key handle");
    EXPECT_LE(key_handle_size, HWKEY_OPAQUE_HANDLE_MAX_SIZE,
              "Wrong handle size");

    /* Close the session and invalidate the handle */
    hwkey_close(hwkey_session);

    _state->args_encrypt.key_type = HWAES_OPAQUE_HANDLE;
    _state->args_encrypt.key.data_ptr = key_handle;
    _state->args_encrypt.key.len = key_handle_size;
    _state->args_encrypt.key.shm_hd_ptr = NULL;

    rc = hwaes_encrypt(_state->hwaes_session, &_state->args_encrypt);
    EXPECT_EQ(ERR_IO, rc, "Should not be able to fetch key for opaque handle");

test_abort:;
}

PORT_TEST(hwaes, "com.android.trusty.hwaes.test")
