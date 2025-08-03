/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright 2017 NXP
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

#include <assert.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <interface/hwkey/hwkey.h>
#include <nxp_hwcrypto_uuid_consts.h>
#include <openssl/aes.h>
#include <openssl/cipher.h>
#include <openssl/digest.h>
#include <openssl/err.h>
#include <openssl/hkdf.h>

#include "caam.h"
#include "common.h"
#include "hwkey_keyslots.h"
#include "hwkey_srv_priv.h"

#define TLOG_TAG "hwkey_caam"
#include <trusty_log.h>

static const uint8_t skeymod[16] __attribute__((aligned(16))) = {
        0x0f, 0x0e, 0x0d, 0x0c, 0x0b, 0x0a, 0x09, 0x08,
        0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, 0x00};

static uint8_t kdfv1_key[32] __attribute__((aligned(32)));

/*
 * Derive key V1 - HKDF based key derive.
 */
uint32_t derive_key_v1(const uuid_t* uuid,
                       const uint8_t* ikm_data,
                       size_t ikm_len,
                       uint8_t* key_buf,
                       size_t* key_len) {
    uint32_t res;

    *key_len = 0;

    if (!ikm_len)
        return HWKEY_ERR_BAD_LEN;

    if (!HKDF(key_buf, ikm_len, EVP_sha256(), (const uint8_t*)kdfv1_key,
              sizeof(kdfv1_key), (const uint8_t*)uuid, sizeof(uuid_t), ikm_data,
              ikm_len)) {
        TLOGE("HDKF failed 0x%x\n", ERR_get_error());
        memset(key_buf, 0, ikm_len);
        res = HWKEY_ERR_GENERIC;
        goto done;
    }
    *key_len = ikm_len;
    res = HWKEY_NO_ERROR;
done:
    return res;
}

/*
 *  RPMB Key support
 */
#define RPMB_SS_AUTH_KEY_SIZE 32
#define RPMB_SS_AUTH_KEY_ID "com.android.trusty.storage_auth.rpmb"

/* Secure storage service app uuid */
static const uuid_t ss_uuid = SECURE_STORAGE_SERVER_APP_UUID;
static size_t rpmb_keyblob_len;
static uint8_t rpmb_keyblob[RPMBKEY_LEN];

/*
 * Fetch RPMB Secure Storage Authentication key
 */
static uint32_t get_rpmb_ss_auth_key(const struct hwkey_keyslot* slot,
                                     uint8_t* kbuf,
                                     size_t kbuf_len,
                                     size_t* klen) {
    uint32_t res;
    assert(kbuf_len >= RPMB_SS_AUTH_KEY_SIZE);

    if (rpmb_keyblob_len != sizeof(rpmb_keyblob))
        return HWKEY_ERR_NOT_FOUND; /* no RPMB key */

    res = caam_decap_blob(skeymod, sizeof(skeymod), kbuf, rpmb_keyblob,
                          RPMB_SS_AUTH_KEY_SIZE);
    if (res == CAAM_SUCCESS) {
        *klen = RPMB_SS_AUTH_KEY_SIZE;
        return HWKEY_NO_ERROR;
    } else {
        /* wipe target buffer */
        TLOGE("%s: failed to unpack rpmb key\n", __func__);
        memset(kbuf, 0, RPMB_SS_AUTH_KEY_SIZE);
        return HWKEY_ERR_GENERIC;
    }
}

/*
 *  List of keys slots that hwkey service supports
 */
static const struct hwkey_keyslot _keys[] = {
        {
                .uuid = &ss_uuid,
                .key_id = RPMB_SS_AUTH_KEY_ID,
                .handler = get_rpmb_ss_auth_key,
        },
};

static void unpack_kbox(void) {
    uint32_t res;
    struct keyslot_package* kbox = caam_get_keybox();

    if (strncmp(kbox->magic, KEYPACK_MAGIC, 4)) {
        TLOGE("Invalid magic\n");
        abort();
    }

    /* Copy RPMB blob */
    assert(!rpmb_keyblob_len); /* key should be unset */
    if (kbox->rpmb_keyblob_len != sizeof(rpmb_keyblob)) {
        TLOGE("Unexpected RPMB key len: %u\n", kbox->rpmb_keyblob_len);
    } else {
        memcpy(rpmb_keyblob, kbox->rpmb_keyblob, kbox->rpmb_keyblob_len);
        rpmb_keyblob_len = kbox->rpmb_keyblob_len;
    }

    /* generate kdfv1 root it should never fail */
    res = caam_gen_kdfv1_root_key(kdfv1_key, sizeof(kdfv1_key));
    assert(res == CAAM_SUCCESS);

    /* copy pubkey blob */

    /* wipe kbox in sram */
    memset(kbox, 0, sizeof(*kbox));
}

/*
 *  Initialize Fake HWKEY service provider
 */
void hwkey_init_srv_provider(void) {
    int rc;

    TLOGD("Init HWKEY service provider\n");

    unpack_kbox();

    /* install key handlers */
    hwkey_install_keys(_keys, countof(_keys));

    /* start service */
    rc = hwkey_start_service();
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to start HWKEY service\n", rc);
    }
}
