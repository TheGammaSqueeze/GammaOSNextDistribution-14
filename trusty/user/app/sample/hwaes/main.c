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

#define TLOG_TAG "hwaes_srv"

#include <assert.h>
#include <lib/hwaes_server/hwaes_server.h>
#include <lib/hwkey/hwkey.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <openssl/evp.h>

#include <hwaes_consts.h>

static EVP_CIPHER_CTX* cipher_ctx;

static void crypt_init(void) {
    assert(!cipher_ctx);

    cipher_ctx = EVP_CIPHER_CTX_new();
    assert(cipher_ctx);
}

static void crypt_shutdown(void) {
    EVP_CIPHER_CTX_free(cipher_ctx);
    cipher_ctx = NULL;
}

static uint32_t hwaes_check_arg_helper(size_t len, const uint8_t* data_ptr) {
    if (len == 0 || data_ptr == NULL) {
        return HWAES_ERR_INVALID_ARGS;
    }
    return HWAES_NO_ERROR;
}

static uint32_t hwaes_check_arg_in(const struct hwaes_arg_in* arg) {
    return hwaes_check_arg_helper(arg->len, arg->data_ptr);
}

static uint32_t hwaes_check_arg_out(const struct hwaes_arg_out* arg) {
    return hwaes_check_arg_helper(arg->len, arg->data_ptr);
}

uint32_t hwaes_aes_op(const struct hwaes_aes_op_args* args) {
    int evp_ret;
    uint32_t rc;
    const EVP_CIPHER* cipher;
    int out_data_size;

    if (args->padding != HWAES_NO_PADDING) {
        TLOGE("the padding type is not implemented yet\n");
        return HWAES_ERR_NOT_IMPLEMENTED;
    }

    rc = hwaes_check_arg_in(&args->key);
    if (rc != HWAES_NO_ERROR) {
        TLOGE("key argument is missing\n");
        return rc;
    }

    rc = hwaes_check_arg_in(&args->text_in);
    if (rc != HWAES_NO_ERROR) {
        TLOGE("text_in argument is missing\n");
        return rc;
    }

    rc = hwaes_check_arg_out(&args->text_out);
    if (rc != HWAES_NO_ERROR) {
        TLOGE("text_out argument is missing\n");
        return rc;
    }

    /*
     * The current implementation does not support padding.
     * So the size of input buffer is the same as output buffer.
     */
    if (args->text_in.len != args->text_out.len) {
        TLOGE("text_in_len (%zd) is not equal to text_out_len (%zd)\n",
              args->text_in.len, args->text_out.len);
        return HWAES_ERR_INVALID_ARGS;
    }

    uint8_t key_buffer[AES_KEY_MAX_SIZE] = {0};
    struct hwaes_arg_in key = args->key;

    /* Fetch the real key contents if needed */
    if (args->key_type == HWAES_OPAQUE_HANDLE) {
        if (key.len > HWKEY_OPAQUE_HANDLE_MAX_SIZE) {
            TLOGE("Wrong opaque handle length: %zu\n", key.len);
            return HWAES_ERR_INVALID_ARGS;
        }
        if (key.data_ptr[key.len - 1] != 0) {
            TLOGE("Opaque handle is not null-terminated\n");
            return HWAES_ERR_INVALID_ARGS;
        }
        long ret = hwkey_open();
        if (ret < 0) {
            TLOGE("Failed to open connection to hwkey service\n");
            return HWAES_ERR_GENERIC;
        }
        hwkey_session_t session = (hwkey_session_t)ret;
        uint32_t key_len = sizeof(key_buffer);
        ret = hwkey_get_keyslot_data(session, (const char*)key.data_ptr,
                                     key_buffer, &key_len);
        hwkey_close(session);
        if (ret != NO_ERROR) {
            TLOGE("Failed to retrieve opaque key: %ld\n", ret);
            return HWAES_ERR_IO;
        }

        key.data_ptr = key_buffer;
        key.len = key_len;
    }

    if (args->mode == HWAES_CBC_MODE) {
        switch (key.len) {
        case 16:
            cipher = EVP_aes_128_cbc();
            break;
        case 32:
            cipher = EVP_aes_256_cbc();
            break;
        default:
            TLOGE("invalid key length: (%zd)\n", key.len);
            return HWAES_ERR_INVALID_ARGS;
        }

        if (hwaes_check_arg_in(&args->aad) == HWAES_NO_ERROR) {
            TLOGE("AAD is not supported in CBC mode\n");
            return HWAES_ERR_INVALID_ARGS;
        }

        if (hwaes_check_arg_in(&args->tag_in) == HWAES_NO_ERROR ||
            hwaes_check_arg_out(&args->tag_out) == HWAES_NO_ERROR) {
            TLOGE("Authentication tag is not supported in CBC mode\n");
            return HWAES_ERR_INVALID_ARGS;
        }
    } else if (args->mode == HWAES_GCM_MODE) {
        switch (key.len) {
        case 16:
            cipher = EVP_aes_128_gcm();
            break;
        case 32:
            cipher = EVP_aes_256_gcm();
            break;
        default:
            TLOGE("invalid key length: (%zd)\n", key.len);
            return HWAES_ERR_INVALID_ARGS;
        }

        if (args->encrypt) {
            if (hwaes_check_arg_in(&args->tag_in) == HWAES_NO_ERROR) {
                TLOGE("Input authentication tag set while encrypting in GCM mode\n");
                return HWAES_ERR_INVALID_ARGS;
            }
            if (hwaes_check_arg_out(&args->tag_out) != HWAES_NO_ERROR) {
                TLOGE("Missing output authentication tag in GCM mode\n");
                return HWAES_ERR_INVALID_ARGS;
            }
        } else {
            if (hwaes_check_arg_in(&args->tag_in) != HWAES_NO_ERROR) {
                TLOGE("Missing input authentication tag in GCM mode\n");
                return HWAES_ERR_INVALID_ARGS;
            }
            if (hwaes_check_arg_out(&args->tag_out) == HWAES_NO_ERROR) {
                TLOGE("Output authentication tag set while decrypting in GCM mode\n");
                return HWAES_ERR_INVALID_ARGS;
            }
        }
    } else {
        TLOGE("AES mode %d is not implemented yet\n", args->mode);
        return HWAES_ERR_NOT_IMPLEMENTED;
    }

    assert(cipher_ctx);
    EVP_CIPHER_CTX_reset(cipher_ctx);

    evp_ret = EVP_CipherInit_ex(cipher_ctx, cipher, NULL, NULL, NULL,
                                args->encrypt);
    if (!evp_ret) {
        TLOGE("EVP_CipherInit_ex failed\n");
        return HWAES_ERR_GENERIC;
    }

    if (args->text_in.len % EVP_CIPHER_CTX_block_size(cipher_ctx)) {
        TLOGE("text_in_len (%zd) is not block aligned\n", args->text_in.len);
        return HWAES_ERR_INVALID_ARGS;
    }

    if (EVP_CIPHER_CTX_iv_length(cipher_ctx) != args->iv.len) {
        TLOGE("invalid iv length: (%zd)\n", args->iv.len);
        return HWAES_ERR_INVALID_ARGS;
    }

    evp_ret = EVP_CipherInit_ex(cipher_ctx, cipher, NULL, key.data_ptr,
                                args->iv.data_ptr, args->encrypt);
    if (!evp_ret) {
        TLOGE("EVP_CipherInit_ex failed\n");
        return HWAES_ERR_GENERIC;
    }

    evp_ret = EVP_CIPHER_CTX_set_padding(cipher_ctx, 0);
    if (!evp_ret) {
        TLOGE("EVP_CIPHER_CTX_set_padding failed\n");
        return HWAES_ERR_GENERIC;
    }

    if (hwaes_check_arg_in(&args->aad) == HWAES_NO_ERROR) {
        evp_ret = EVP_CipherUpdate(cipher_ctx, NULL, &out_data_size,
                                   args->aad.data_ptr, args->aad.len);
        if (evp_ret != 1) {
            TLOGE("EVP CipherUpdate for AAD failed\n");
            return HWAES_ERR_GENERIC;
        }
    }

    if (hwaes_check_arg_in(&args->tag_in) == HWAES_NO_ERROR) {
        evp_ret = EVP_CIPHER_CTX_ctrl(cipher_ctx, EVP_CTRL_AEAD_SET_TAG,
                                      args->tag_in.len,
                                      (void*)args->tag_in.data_ptr);
        if (evp_ret != 1) {
            TLOGE("EVP set AEAD tag failed\n");
            return HWAES_ERR_GENERIC;
        }
    }

    evp_ret = EVP_CipherUpdate(cipher_ctx, args->text_out.data_ptr,
                               &out_data_size, args->text_in.data_ptr,
                               args->text_in.len);
    if (!evp_ret) {
        TLOGE("EVP_CipherUpdate failed\n");
        return HWAES_ERR_GENERIC;
    }

    /*
     * The assert fails if the memory corruption happens.
     */
    assert(out_data_size == (int)args->text_out.len);

    /*
     * Currently we don't support padding.
     */
    evp_ret = EVP_CipherFinal_ex(cipher_ctx, NULL, &out_data_size);
    if (!evp_ret) {
        TLOGE("EVP_CipherFinal_ex failed\n");
        return HWAES_ERR_GENERIC;
    }

    if (hwaes_check_arg_out(&args->tag_out) == HWAES_NO_ERROR) {
        evp_ret =
                EVP_CIPHER_CTX_ctrl(cipher_ctx, EVP_CTRL_AEAD_GET_TAG,
                                    args->tag_out.len, args->tag_out.data_ptr);
        if (evp_ret != 1) {
            TLOGE("EVP get AEAD tag failed\n");
            return HWAES_ERR_GENERIC;
        }
    }

    return HWAES_NO_ERROR;
}

static const uuid_t apploader_uuid = APPLOADER_APP_UUID;

static const uuid_t hwaes_unittest_uuid = HWAES_UNITTEST_APP_UUID;

static const uuid_t hwaes_bench_uuid = HWAES_BENCH_APP_UUID;

static const uuid_t internal_app_uuid = INTERNAL_APP_UUID;

static const uuid_t* allowed_clients[] = {
        &apploader_uuid,
        &hwaes_bench_uuid,
        &hwaes_unittest_uuid,
        &internal_app_uuid,
};

int main(void) {
    int rc;
    struct tipc_hset* hset;
    TLOGE("hwaes server is using SW Crypto\n");

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    rc = add_hwaes_service(hset, allowed_clients, countof(allowed_clients));
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize hwaes service\n", rc);
        return EXIT_FAILURE;
    }

    crypt_init();
    rc = tipc_run_event_loop(hset);

    TLOGE("hwaes server going down: (%d)\n", rc);
    crypt_shutdown();
    if (rc != NO_ERROR) {
        return EXIT_FAILURE;
    }
    EXIT_SUCCESS;
}
