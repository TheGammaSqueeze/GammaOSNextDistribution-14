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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include <dice/cbor_reader.h>
#include <interface/keymaster/keymaster.h>
#include <lib/keymaster/keymaster.h>

#include <openssl/hmac.h>

#define LOG_TAG "libkeymaster"
#define TLOGE(fmt, ...) \
    fprintf(stderr, "%s: %d: " fmt, LOG_TAG, __LINE__, ##__VA_ARGS__)

#define HMAC_LEN (sizeof(((hw_auth_token_t*)0)->hmac))

#define AUTH_TOKEN_KEY_LEN (32)

static long send_req(keymaster_session_t session, uint32_t cmd) {
    struct keymaster_message msg = {
            .cmd = cmd,
    };

    struct iovec tx_iov = {
            .iov_base = &msg,
            .iov_len = sizeof(msg),
    };
    ipc_msg_t tx_msg = {
            .iov = &tx_iov,
            .num_iov = 1,
    };

    long rc = send_msg(session, &tx_msg);
    if (rc < 0) {
        TLOGE("%s: failed (%ld) to send_msg\n", __func__, rc);
        return rc;
    }

    if (((size_t)rc) != sizeof(msg)) {
        TLOGE("%s: msg invalid size (%zu != %zu)", __func__, (size_t)rc,
              sizeof(msg));
        return ERR_IO;
    }

    return NO_ERROR;
}

static long await_response(keymaster_session_t session,
                           struct ipc_msg_info* inf) {
    uevent_t uevt;
    long rc = wait(session, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("%s: interrupted waiting for response (%ld)\n", __func__, rc);
        return rc;
    }

    rc = get_msg(session, inf);
    if (rc != NO_ERROR) {
        TLOGE("%s: failed to get_msg (%ld)\n", __func__, rc);
    }

    return rc;
}

static long read_response(keymaster_session_t session,
                          uint32_t msg_id,
                          uint32_t cmd,
                          uint8_t* buf,
                          uint32_t size) {
    struct keymaster_message msg;

    struct iovec rx_iov[2] = {
            {.iov_base = &msg, .iov_len = sizeof(msg)},
            {.iov_base = buf, .iov_len = size},
    };
    struct ipc_msg rx_msg = {
            .iov = rx_iov,
            .num_iov = 2,
    };

    long rc = read_msg(session, msg_id, 0, &rx_msg);
    put_msg(session, msg_id);

    if ((cmd | KM_RESP_BIT) != (msg.cmd & ~(KM_STOP_BIT))) {
        TLOGE("%s: invalid response (0x%x) for cmd (0x%x)\n", __func__, msg.cmd,
              cmd);
        return ERR_NOT_VALID;
    }

    return rc;
}

int keymaster_open(void) {
    return connect(KEYMASTER_SECURE_PORT, IPC_CONNECT_WAIT_FOR_PORT);
}

void keymaster_close(keymaster_session_t session) {
    close(session);
}

int keymaster_send_command(keymaster_session_t session,
                           uint8_t command,
                           uint8_t** data_buf_p,
                           uint32_t* size_p) {
    if (size_p == NULL || data_buf_p == NULL) {
        return ERR_NOT_VALID;
    }

    long rc = send_req(session, command);
    if (rc < 0) {
        TLOGE("%s: failed (%ld) to send req\n", __func__, rc);
        return rc;
    }

    struct ipc_msg_info inf;
    rc = await_response(session, &inf);
    if (rc < 0) {
        TLOGE("%s: failed (%ld) to await response\n", __func__, rc);
        return rc;
    }

    if (inf.len <= sizeof(struct keymaster_message)) {
        TLOGE("%s: invalid response len (%zu)\n", __func__, inf.len);
        put_msg(session, inf.id);
        return ERR_NOT_FOUND;
    }

    size_t size = inf.len - sizeof(struct keymaster_message);
    uint8_t* data_buf = malloc(size);
    if (data_buf == NULL) {
        TLOGE("%s: out of memory (%zu)\n", __func__, inf.len);
        put_msg(session, inf.id);
        return ERR_NO_MEMORY;
    }

    rc = read_response(session, inf.id, command, data_buf, size);
    if (rc < 0) {
        goto err_bad_read;
    }

    size_t read_len = (size_t)rc;
    if (read_len != inf.len) {
        // data read in does not match message length
        TLOGE("%s: invalid read length: (%zu != %zu)\n", __func__, read_len,
              inf.len);
        rc = ERR_IO;
        goto err_bad_read;
    }

    *size_p = (uint32_t)size;
    *data_buf_p = data_buf;
    return NO_ERROR;

err_bad_read:
    free(data_buf);
    TLOGE("%s: failed read_msg (%ld)\n", __func__, rc);
    return rc;
}

int keymaster_get_auth_token_key(keymaster_session_t session,
                                 uint8_t** key_buf_p,
                                 uint32_t* size_p) {
    long rc = keymaster_send_command(session, KM_GET_AUTH_TOKEN_KEY, key_buf_p,
                                     size_p);
    /*
     * TODO: Return message of this API contains an error if one happened and a
     * key on success. It may be impossible to distinguish the two if they are
     * the same size. A proper fix would require changing the layout of the
     * return message. However, that changes the ABI. So, just assume that the
     * key is 32 bytes. We know that from KM code.
     */
    if (rc == NO_ERROR && *size_p != AUTH_TOKEN_KEY_LEN) {
        TLOGE("%s: auth token key wrong length: %u, expected %d\n", __func__,
              *size_p, AUTH_TOKEN_KEY_LEN);
        rc = ERR_BAD_LEN;
        free(*key_buf_p);
        *key_buf_p = NULL;
    }
    return rc;
}

int keymaster_get_device_info(keymaster_session_t session,
                              uint8_t** info_buffer_p,
                              uint32_t* size_p) {
    long rc = keymaster_send_command(session, KM_GET_DEVICE_INFO, info_buffer_p,
                                     size_p);
    /*
     * TODO: Return message of this API contains an error if one happened and a
     * key on success. It may be impossible to distinguish the two if they are
     * the same size. A proper fix would require changing the layout of the
     * return message. However, that changes the ABI. So, attempt to parse the
     * message as a valid CBOR map with non-zero entries. If this fails, it's
     * an error.
     */
    if (rc == NO_ERROR) {
        struct CborIn in;
        CborInInit(*info_buffer_p, *size_p, &in);
        size_t pair_count;
        if (*size_p == 0 ||
            CborReadMap(&in, &pair_count) != CBOR_READ_RESULT_OK) {
            TLOGE("%s: device info byte stream is not valid CBOR or a map.\n",
                  __func__);
            goto err_bad_cbor;
        }
        // Each entry would require at least two bytes.
        if (*size_p < pair_count * 2) {
            TLOGE("%s: Device info is malformed. Size is %u, expected > %zu\n",
                  __func__, *size_p, pair_count * 2);
            goto err_bad_cbor;
        }
    }
    return rc;

err_bad_cbor:
    rc = ERR_FAULT;
    free(*info_buffer_p);
    *info_buffer_p = NULL;
    return rc;
}

static int mint_hmac(uint8_t* key,
                     size_t key_size,
                     uint8_t* message,
                     size_t message_size,
                     uint8_t* hmac) {
    unsigned int tok_size;
    unsigned char* ret;
    memset(hmac, 0, HMAC_LEN);
    ret = HMAC(EVP_sha256(), (void*)key, key_size, message, message_size, hmac,
               &tok_size);
    if (ret == NULL || tok_size != HMAC_LEN) {
        TLOGE("Failed to execute HMAC()!\n");
        return ERR_FAULT;
    }

    return NO_ERROR;
}

int keymaster_sign_auth_token(keymaster_session_t session,
                              hw_auth_token_t* token) {
    int ret = NO_ERROR;

    if (token == NULL) {
        TLOGE("Invalid token!\n");
        return ERR_NOT_VALID;
    }

    uint8_t* key_buf;
    uint32_t key_buf_size;
    ret = keymaster_get_auth_token_key(session, &key_buf, &key_buf_size);
    if (ret) {
        return ret;
    }

    /* Initialize the token and message size */
    size_t message_size = sizeof(hw_auth_token_t) - sizeof(token->hmac);
    /* Mint the token key with the given HMAC key and message */
    ret = mint_hmac(key_buf, key_buf_size, (uint8_t*)token, message_size,
                    token->hmac);

free_mem:
    free(key_buf);
    return ret;
}

int keymaster_validate_auth_token(keymaster_session_t session,
                                  hw_auth_token_t* token) {
    int ret = NO_ERROR;

    if (token == NULL) {
        TLOGE("Invalid token!\n");
        return ERR_NOT_VALID;
    }

    uint8_t* key_buf;
    uint32_t key_buf_size;
    ret = keymaster_get_auth_token_key(session, &key_buf, &key_buf_size);
    if (ret) {
        return ret;
    }

    /* compute the expected token hmac */
    uint8_t expected_hmac[HMAC_LEN];
    size_t message_size = sizeof(hw_auth_token_t) - sizeof(token->hmac);

    ret = mint_hmac(key_buf, key_buf_size, (uint8_t*)token, message_size,
                    expected_hmac);
    if (ret) {
        goto free_mem;
    }

    /* Compare the expected hmac with the provided hmac */
    ret = memcmp(expected_hmac, token->hmac, sizeof(expected_hmac));

free_mem:
    free(key_buf);
    return ret;
}
