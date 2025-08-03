/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define TLOG_TAG "hwkey_srv"

#include <assert.h>
#include <lk/list.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <interface/hwkey/hwkey.h>
#include <lib/tipc/tipc.h>
#include <openssl/evp.h>
#include <openssl/mem.h>
#include <trusty_log.h>

#include <hwcrypto/hwrng_dev.h>
#include <hwcrypto_consts.h>
#include "hwkey_srv_priv.h"

struct hwkey_chan_ctx {
    struct tipc_event_handler evt_handler;
    handle_t chan;
    uuid_t uuid;
};

/**
 * An opaque key access token.
 *
 * Clients can retrieve an opaque access token as a handle to a key they are
 * allowed to use but not read directly. This handle can then be passed to other
 * crypto services which can use the token to retrieve the actual key from
 * hwkey.
 */
typedef char access_token_t[HWKEY_OPAQUE_HANDLE_SIZE];

struct opaque_handle_node {
    const struct hwkey_keyslot* key_slot;
    struct hwkey_chan_ctx* owner;
    access_token_t token;
    struct list_node node;
};

/*
 * Global list of currently valid opaque handles. Each client may only have a
 * single entry in this list for a given key slot, and this entry will be
 * cleaned up when the connection it was created for is closed.
 */
static struct list_node opaque_handles = LIST_INITIAL_VALUE(opaque_handles);

static void hwkey_port_handler(const uevent_t* ev, void* priv);
static void hwkey_chan_handler(const uevent_t* ev, void* priv);

static struct tipc_event_handler hwkey_port_evt_handler = {
        .proc = hwkey_port_handler,
};

static uint8_t req_data[HWKEY_MAX_MSG_SIZE + 1];
static __attribute__((aligned(4))) uint8_t key_data[HWKEY_MAX_MSG_SIZE];

static unsigned int key_slot_cnt;
static const struct hwkey_keyslot* key_slots;

static bool is_opaque_handle(const struct hwkey_keyslot* key_slot) {
    assert(key_slot);
    return key_slot->handler == get_key_handle;
}

static void delete_opaque_handle(struct opaque_handle_node* node) {
    assert(node);

    /* Zero out the access token just in case the memory is reused */
    memset(node->token, 0, HWKEY_OPAQUE_HANDLE_SIZE);

    list_delete(&node->node);
    free(node);
}

/*
 * Close specified hwkey context
 */
static void hwkey_ctx_close(struct hwkey_chan_ctx* ctx) {
    struct opaque_handle_node* entry;
    struct opaque_handle_node* temp;
    list_for_every_entry_safe(&opaque_handles, entry, temp,
                              struct opaque_handle_node, node) {
        if (entry->owner == ctx) {
            delete_opaque_handle(entry);
        }
    }
    close(ctx->chan);
    free(ctx);
}

/*
 * Send response message
 */
static int hwkey_send_rsp(struct hwkey_chan_ctx* ctx,
                          struct hwkey_msg* rsp_hdr,
                          uint8_t* rsp_data,
                          size_t rsp_data_len) {
    rsp_hdr->header.cmd |= HWKEY_RESP_BIT;
    return tipc_send2(ctx->chan, rsp_hdr, sizeof(*rsp_hdr), rsp_data,
                      rsp_data_len);
}

static bool is_allowed_to_read_opaque_key(const uuid_t* uuid,
                                          const struct hwkey_keyslot* slot) {
    assert(slot);
    const struct hwkey_opaque_handle_data* handle = slot->priv;
    assert(handle);

    for (size_t i = 0; i < handle->allowed_uuids_len; ++i) {
        if (memcmp(handle->allowed_uuids[i], uuid, sizeof(uuid_t)) == 0) {
            return true;
        }
    }
    return false;
}

static struct opaque_handle_node* find_opaque_handle_for_slot(
        const struct hwkey_keyslot* slot) {
    struct opaque_handle_node* entry;
    list_for_every_entry(&opaque_handles, entry, struct opaque_handle_node,
                         node) {
        if (entry->key_slot == slot) {
            return entry;
        }
    }

    return NULL;
}

/*
 * If a handle doesn't exist yet for the given slot, create and insert a new one
 * in the global list.
 */
static uint32_t insert_handle_node(struct hwkey_chan_ctx* ctx,
                                   const struct hwkey_keyslot* slot) {
    struct opaque_handle_node* entry = find_opaque_handle_for_slot(slot);

    if (!entry) {
        entry = calloc(1, sizeof(struct opaque_handle_node));
        if (!entry) {
            TLOGE("Could not allocate new opaque_handle_node\n");
            return HWKEY_ERR_GENERIC;
        }

        entry->owner = ctx;
        entry->key_slot = slot;
        list_add_tail(&opaque_handles, &entry->node);
    }

    return HWKEY_NO_ERROR;
}

static uint32_t _handle_slots(struct hwkey_chan_ctx* ctx,
                              const char* slot_id,
                              const struct hwkey_keyslot* slots,
                              unsigned int slot_cnt,
                              uint8_t* kbuf,
                              size_t kbuf_len,
                              size_t* klen) {
    if (!slots)
        return HWKEY_ERR_NOT_FOUND;

    for (unsigned int i = 0; i < slot_cnt; i++, slots++) {
        /* check key id */
        if (strcmp(slots->key_id, slot_id))
            continue;

        /* Check if the caller is allowed to get that key */
        if (memcmp(&ctx->uuid, slots->uuid, sizeof(uuid_t)) == 0) {
            if (slots->handler) {
                if (is_opaque_handle(slots)) {
                    uint32_t rc = insert_handle_node(ctx, slots);
                    if (rc != HWKEY_NO_ERROR)
                        return rc;
                }
                return slots->handler(slots, kbuf, kbuf_len, klen);
            }
        }
    }

    /*
     * We couldn't match a key ID, so try to treat the id as an opaque access
     * handle
     */
    return get_opaque_key(&ctx->uuid, slot_id, kbuf, kbuf_len, klen);
}

uint32_t hwkey_get_derived_key(const struct hwkey_derived_keyslot_data* data,
                               uint8_t* kbuf,
                               size_t kbuf_len,
                               size_t* klen) {
    assert(kbuf);
    assert(klen);
    assert(data);
    assert(data->encrypted_key_size_ptr);

    uint8_t key_buffer[HWKEY_DERIVED_KEY_MAX_SIZE] = {0};
    size_t key_len;
    uint32_t rc =
            data->retriever(data, key_buffer, sizeof(key_buffer), &key_len);
    if (rc != HWKEY_NO_ERROR) {
        return rc;
    }

    const EVP_CIPHER* cipher;
    switch (key_len) {
    case 16:
        cipher = EVP_aes_128_cbc();
        break;
    case 32:
        cipher = EVP_aes_256_cbc();
        break;
    default:
        TLOGE("invalid key length: (%zd)\n", key_len);
        return HWKEY_ERR_GENERIC;
    }

    int evp_ret;
    int out_len = 0;
    uint8_t* iv = NULL;
    EVP_CIPHER_CTX* cipher_ctx = EVP_CIPHER_CTX_new();
    if (!cipher_ctx) {
        return HWKEY_ERR_GENERIC;
    }

    /* if we exit early */
    rc = HWKEY_ERR_GENERIC;

    evp_ret = EVP_DecryptInit_ex(cipher_ctx, cipher, NULL, NULL, NULL);
    if (evp_ret != 1) {
        TLOGE("Initializing decryption algorithm failed\n");
        goto out;
    }

    unsigned int iv_length = EVP_CIPHER_CTX_iv_length(cipher_ctx);

    /* encrypted key contains IV + ciphertext */
    if (iv_length >= *data->encrypted_key_size_ptr) {
        TLOGE("Encrypted key is too small\n");
        goto out;
    }

    if (kbuf_len < *data->encrypted_key_size_ptr - iv_length) {
        TLOGE("Not enough space in output buffer\n");
        rc = HWKEY_ERR_BAD_LEN;
        goto out;
    }

    evp_ret = EVP_DecryptInit_ex(cipher_ctx, cipher, NULL, key_buffer,
                                 data->encrypted_key_data);
    if (evp_ret != 1) {
        TLOGE("Initializing decryption algorithm failed\n");
        goto out;
    }

    evp_ret = EVP_CIPHER_CTX_set_padding(cipher_ctx, 0);
    if (evp_ret != 1) {
        TLOGE("EVP_CIPHER_CTX_set_padding failed\n");
        goto out;
    }

    evp_ret = EVP_DecryptUpdate(cipher_ctx, kbuf, &out_len,
                                data->encrypted_key_data + iv_length,
                                *data->encrypted_key_size_ptr - iv_length);
    if (evp_ret != 1) {
        TLOGE("EVP_DecryptUpdate failed\n");
        goto out;
    }

    /* We don't support padding so input length == output length */
    assert(out_len >= 0 &&
           (unsigned int)out_len == *data->encrypted_key_size_ptr - iv_length);

    evp_ret = EVP_DecryptFinal_ex(cipher_ctx, NULL, &out_len);
    if (evp_ret != 1) {
        TLOGE("EVP_DecryptFinal failed\n");
        goto out;
    }

    assert(out_len == 0);

    *klen = *data->encrypted_key_size_ptr - iv_length;

    /* Decryption was successful */
    rc = HWKEY_NO_ERROR;

out:
    if (iv) {
        free(iv);
    }
    EVP_CIPHER_CTX_free(cipher_ctx);
    return rc;
}

/*
 * Handle get key slot command
 */
static int hwkey_handle_get_keyslot_cmd(struct hwkey_chan_ctx* ctx,
                                        struct hwkey_msg* hdr,
                                        const char* slot_id) {
    int rc;
    size_t klen = 0;

    hdr->header.status = _handle_slots(ctx, slot_id, key_slots, key_slot_cnt,
                                       key_data, sizeof(key_data), &klen);

    rc = hwkey_send_rsp(ctx, hdr, key_data, klen);
    if (klen) {
        /* sanitize key buffer */
        memset(key_data, 0, klen);
    }
    return rc;
}

/* Shared implementation for the unversioned key derivation API */
static uint32_t hwkey_handle_derive_key_impl(uint32_t* kdf_version,
                                             const uuid_t* uuid,
                                             const uint8_t* context,
                                             size_t context_len,
                                             uint8_t* key,
                                             size_t key_len) {
    /* check requested key derivation function */
    if (*kdf_version == HWKEY_KDF_VERSION_BEST) {
        *kdf_version = HWKEY_KDF_VERSION_1;
    }

    if (!context || !key || key_len == 0) {
        return HWKEY_ERR_NOT_VALID;
    }

    switch (*kdf_version) {
    case HWKEY_KDF_VERSION_1:
        return derive_key_v1(uuid, context, context_len, key, key_len);

    default:
        TLOGE("%u is unsupported KDF function\n", *kdf_version);
        return HWKEY_ERR_NOT_IMPLEMENTED;
    }
}

/*
 * Handle Derive key cmd
 */
static int hwkey_handle_derive_key_cmd(struct hwkey_chan_ctx* ctx,
                                       struct hwkey_msg* hdr,
                                       const uint8_t* ikm_data,
                                       size_t ikm_len) {
    int rc;
    size_t key_len = ikm_len;
    if (key_len > HWKEY_MAX_MSG_SIZE - sizeof(*hdr)) {
        TLOGE("Key length exceeds message size: %zu\n", key_len);
        key_len = 0;
        hdr->header.status = HWKEY_ERR_BAD_LEN;
        goto send_response;
    }

    hdr->header.status = hwkey_handle_derive_key_impl(
            &hdr->arg1, &ctx->uuid, ikm_data, ikm_len, key_data, key_len);

send_response:
    rc = hwkey_send_rsp(ctx, hdr, key_data, key_len);
    if (key_len) {
        /* sanitize key buffer */
        memset(key_data, 0, sizeof(key_data));
    }
    return rc;
}

/**
 * hwkey_handle_derive_versioned_key_cmd() - Handle versioned key derivation
 * @ctx: client context
 * @msg: request/response message
 * @context: key derivation info input
 * @context_len: length in bytes of @context
 *
 * Derive a new key from an internal secret key, unique to the provided context,
 * UUID of the client, and requested rollback version. Rollback versions greater
 * than the current image or fused rollback version are not allowed. See &struct
 * hwkey_derive_versioned_msg for more details.
 *
 * Because key versions newer than the current image rollback version are not
 * available to clients, incrementing this version in the Trusty image results
 * in a new set of keys being available that previous Trusty apps never had
 * access to. This mechanism can be used to roll to new keys after patching a
 * Trusty app vulnerability that may have exposed old keys. If the key
 * derivation is implemented outside of Trusty entirely, then keys can be
 * refreshed after a potential Trusty kernel compromise.
 *
 * Return: A negative return value indicates an error occurred sending the IPC
 *         response back to the client.
 */
static int hwkey_handle_derive_versioned_key_cmd(
        struct hwkey_chan_ctx* ctx,
        struct hwkey_derive_versioned_msg* msg,
        const uint8_t* context,
        size_t context_len) {
    int i;
    int rc;
    bool shared = msg->key_options & HWKEY_SHARED_KEY_TYPE;
    uint32_t status;
    size_t key_len;

    key_len = msg->key_len;
    if (key_len > HWKEY_MAX_MSG_SIZE - sizeof(*msg)) {
        TLOGE("Key length (%zu) exceeds buffer length\n", key_len);
        status = HWKEY_ERR_BAD_LEN;
        goto send_response;
    }

    /*
     * make sure to retrieve the current OS version before calling
     * hwkey_derive_versioned_msg_compatible_with_unversioned() so that we
     * derive the same key with CURRENT == 0 and 0 passed explicitly.
     */

    int os_rollback_version =
            msg->rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX];
    if (os_rollback_version == HWKEY_ROLLBACK_VERSION_CURRENT) {
        os_rollback_version =
                get_current_os_rollback_version(msg->rollback_version_source);
        if (os_rollback_version < 0) {
            status = HWKEY_ERR_GENERIC;
            goto send_response;
        }
        msg->rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX] =
                os_rollback_version;
    }

    for (i = HWKEY_ROLLBACK_VERSION_SUPPORTED_COUNT;
         i < HWKEY_ROLLBACK_VERSION_INDEX_COUNT; ++i) {
        if (msg->rollback_versions[i] != 0) {
            TLOGE("Unsupported rollback version set: %d\n", i);
            status = HWKEY_ERR_NOT_VALID;
            goto send_response;
        }
    }

    if (hwkey_derive_versioned_msg_compatible_with_unversioned(msg)) {
        status = hwkey_handle_derive_key_impl(&msg->kdf_version, &ctx->uuid,
                                              context, context_len, key_data,
                                              key_len);
        if (key_len == 0) {
            /*
             * derive_key_v1() doesn't support an empty key length, but we still
             * want to allow querying key versions with NULL key and zero
             * length. Reset the status to ok in this case.
             */
            status = HWKEY_NO_ERROR;
        }
        goto send_response;
    }

    /* check requested key derivation function */
    if (msg->kdf_version == HWKEY_KDF_VERSION_BEST) {
        msg->kdf_version = HWKEY_KDF_VERSION_1;
    }

    switch (msg->kdf_version) {
    case HWKEY_KDF_VERSION_1:
        status = derive_key_versioned_v1(&ctx->uuid, shared,
                                         msg->rollback_version_source,
                                         msg->rollback_versions, context,
                                         context_len, key_data, key_len);
        break;

    default:
        TLOGE("%u is unsupported KDF function\n", msg->kdf_version);
        status = HWKEY_ERR_NOT_IMPLEMENTED;
    }

send_response:
    if (status != HWKEY_NO_ERROR) {
        msg->key_len = 0;
    }

    msg->header.status = status;
    msg->header.cmd |= HWKEY_RESP_BIT;
    rc = tipc_send2(ctx->chan, msg, sizeof(*msg), key_data, msg->key_len);
    if (msg->key_len) {
        /* sanitize key buffer */
        memset(key_data, 0, sizeof(key_data));
    }
    return rc;
}

/*
 *  Read and queue HWKEY request message
 */
static int hwkey_chan_handle_msg(struct hwkey_chan_ctx* ctx) {
    int rc;
    size_t req_data_len;
    struct hwkey_msg_header* hdr;

    rc = tipc_recv1(ctx->chan, sizeof(*hdr), req_data, sizeof(req_data) - 1);
    if (rc < 0) {
        TLOGE("failed (%d) to recv msg from chan %d\n", rc, ctx->chan);
        return rc;
    }

    req_data_len = (size_t)rc;

    if (req_data_len < sizeof(*hdr)) {
        TLOGE("Received too little data (%zu) from chan %d\n", req_data_len,
              ctx->chan);
        return ERR_BAD_LEN;
    }

    hdr = (struct hwkey_msg_header*)req_data;

    /* handle it */
    switch (hdr->cmd) {
    case HWKEY_GET_KEYSLOT:
        req_data[req_data_len] = 0; /* force zero termination */
        if (req_data_len < sizeof(struct hwkey_msg)) {
            TLOGE("Received too little data (%zu) from chan %d\n", req_data_len,
                  ctx->chan);
            return ERR_BAD_LEN;
        }
        rc = hwkey_handle_get_keyslot_cmd(
                ctx, (struct hwkey_msg*)req_data,
                (const char*)(req_data + sizeof(struct hwkey_msg)));
        break;

    case HWKEY_DERIVE:
        if (req_data_len < sizeof(struct hwkey_msg)) {
            TLOGE("Received too little data (%zu) from chan %d\n", req_data_len,
                  ctx->chan);
            return ERR_BAD_LEN;
        }
        rc = hwkey_handle_derive_key_cmd(
                ctx, (struct hwkey_msg*)req_data,
                req_data + sizeof(struct hwkey_msg),
                req_data_len - sizeof(struct hwkey_msg));
        memset(req_data, 0, req_data_len); /* sanitize request buffer */
        break;

    case HWKEY_DERIVE_VERSIONED:
        if (req_data_len < sizeof(struct hwkey_derive_versioned_msg)) {
            TLOGE("Received too little data (%zu) from chan %d\n", req_data_len,
                  ctx->chan);
            return ERR_BAD_LEN;
        }
        rc = hwkey_handle_derive_versioned_key_cmd(
                ctx, (struct hwkey_derive_versioned_msg*)req_data,
                req_data + sizeof(struct hwkey_derive_versioned_msg),
                req_data_len - sizeof(struct hwkey_derive_versioned_msg));
        memset(req_data, 0, req_data_len); /* sanitize request buffer */
        break;

    default:
        TLOGE("Unsupported request: %d\n", (int)hdr->cmd);
        hdr->status = HWKEY_ERR_NOT_IMPLEMENTED;
        hdr->cmd |= HWKEY_RESP_BIT;
        rc = tipc_send1(ctx->chan, hdr, sizeof(*hdr));
    }

    return rc;
}

/*
 *  HWKEY service channel event handler
 */
static void hwkey_chan_handler(const uevent_t* ev, void* priv) {
    struct hwkey_chan_ctx* ctx = priv;

    assert(ctx);
    assert(ev->handle == ctx->chan);

    tipc_handle_chan_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        /* closed by peer. */
        hwkey_ctx_close(ctx);
        return;
    }

    if (ev->event & IPC_HANDLE_POLL_MSG) {
        int rc = hwkey_chan_handle_msg(ctx);
        if (rc < 0) {
            /* report an error and close channel */
            TLOGE("failed (%d) to handle event on channel %d\n", rc,
                  ev->handle);
            hwkey_ctx_close(ctx);
        }
    }
}

/*
 * HWKEY service port event handler
 */
static void hwkey_port_handler(const uevent_t* ev, void* priv) {
    uuid_t peer_uuid;

    tipc_handle_port_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_READY) {
        /* incoming connection: accept it */
        int rc = accept(ev->handle, &peer_uuid);
        if (rc < 0) {
            TLOGE("failed (%d) to accept on port %d\n", rc, ev->handle);
            return;
        }

        handle_t chan = (handle_t)rc;
        if (!hwkey_client_allowed(&peer_uuid)) {
            TLOGE("access to hwkey service denied\n");
            close(chan);
            return;
        }

        struct hwkey_chan_ctx* ctx = calloc(1, sizeof(*ctx));
        if (!ctx) {
            TLOGE("failed (%d) to allocate context on chan %d\n", rc, chan);
            close(chan);
            return;
        }

        /* init channel state */
        ctx->evt_handler.priv = ctx;
        ctx->evt_handler.proc = hwkey_chan_handler;
        ctx->chan = chan;
        ctx->uuid = peer_uuid;

        rc = set_cookie(chan, &ctx->evt_handler);
        if (rc < 0) {
            TLOGE("failed (%d) to set_cookie on chan %d\n", rc, chan);
            hwkey_ctx_close(ctx);
            return;
        }
    }
}

/*
 *  Install Key slot provider
 */
void hwkey_install_keys(const struct hwkey_keyslot* keys, unsigned int kcnt) {
    assert(key_slots == NULL);
    assert(key_slot_cnt == 0);
    assert(keys && kcnt);

    key_slots = keys;
    key_slot_cnt = kcnt;
}

static bool is_empty_token(const char* access_token) {
    for (int i = 0; i < HWKEY_OPAQUE_HANDLE_SIZE; i++) {
        if (access_token[i] != 0) {
            assert(strnlen(access_token, HWKEY_OPAQUE_HANDLE_SIZE) ==
                   HWKEY_OPAQUE_HANDLE_SIZE - 1);
            return false;
        }
    }
    return true;
}

uint32_t get_key_handle(const struct hwkey_keyslot* slot,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen) {
    assert(kbuf);
    assert(klen);

    const struct hwkey_opaque_handle_data* handle = slot->priv;
    assert(handle);
    assert(kbuf_len >= HWKEY_OPAQUE_HANDLE_SIZE);

    struct opaque_handle_node* entry = find_opaque_handle_for_slot(slot);
    /* _handle_slots should have already created an entry for this slot */
    assert(entry);

    if (!is_empty_token(entry->token)) {
        /*
         * We do not allow fetching a token again for the same slot again after
         * the token is first created and returned
         */
        return HWKEY_ERR_ALREADY_EXISTS;
    }

    /*
     * We want to generate a null-terminated opaque handle with no interior null
     * bytes, so we generate extra randomness and only use the non-zero bytes.
     */
    uint8_t random_buf[HWKEY_OPAQUE_HANDLE_SIZE + 2];
    while (1) {
        int rc = hwrng_dev_get_rng_data(random_buf, sizeof(random_buf));
        if (rc != NO_ERROR) {
            /* Don't leave an empty entry if we couldn't generate a token */
            delete_opaque_handle(entry);
            return rc;
        }

        size_t token_offset = 0;
        for (size_t i = 0; i < sizeof(random_buf) &&
                           token_offset < HWKEY_OPAQUE_HANDLE_SIZE - 1;
             ++i) {
            if (random_buf[i] != 0) {
                entry->token[token_offset] = random_buf[i];
                token_offset++;
            }
        }
        if (token_offset == HWKEY_OPAQUE_HANDLE_SIZE - 1) {
            break;
        }
    }

    /* ensure that token is properly null-terminated */
    assert(entry->token[HWKEY_OPAQUE_HANDLE_SIZE - 1] == 0);

    memcpy(kbuf, entry->token, HWKEY_OPAQUE_HANDLE_SIZE);
    *klen = HWKEY_OPAQUE_HANDLE_SIZE;

    return HWKEY_NO_ERROR;
}

uint32_t get_opaque_key(const uuid_t* uuid,
                        const char* access_token,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen) {
    struct opaque_handle_node* entry;
    list_for_every_entry(&opaque_handles, entry, struct opaque_handle_node,
                         node) {
        /* get_key_handle should never leave an empty token in the list */
        assert(!is_empty_token(entry->token));

        if (!is_allowed_to_read_opaque_key(uuid, entry->key_slot))
            continue;

        /*
         * We are using a constant-time memcmp here to avoid side-channel
         * leakage of the access token. Even if we trust the service that is
         * allowed to retrieve this key, one of its clients may be trying to
         * brute force the token, so this comparison must be constant-time.
         */
        if (CRYPTO_memcmp(entry->token, access_token,
                          HWKEY_OPAQUE_HANDLE_SIZE) == 0) {
            const struct hwkey_opaque_handle_data* handle =
                    entry->key_slot->priv;
            assert(handle);
            return handle->retriever(handle, kbuf, kbuf_len, klen);
        }
    }

    return HWKEY_ERR_NOT_FOUND;
}

/*
 *  Initialize HWKEY service
 */
int hwkey_start_service(void) {
    int rc;
    handle_t port;

    TLOGD("Start HWKEY service\n");

    /* Initialize service */
    rc = port_create(HWKEY_PORT, 1, HWKEY_MAX_MSG_SIZE,
                     IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGE("Failed (%d) to create port %s\n", rc, HWKEY_PORT);
        return rc;
    }

    port = (handle_t)rc;
    rc = set_cookie(port, &hwkey_port_evt_handler);
    if (rc) {
        TLOGE("failed (%d) to set_cookie on port %d\n", rc, port);
        close(port);
        return rc;
    }

    return NO_ERROR;
}
