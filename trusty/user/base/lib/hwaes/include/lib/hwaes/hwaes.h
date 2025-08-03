/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lk/compiler.h>
#include <stdbool.h>
#include <sys/types.h>

#include <interface/hwaes/hwaes.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

typedef handle_t hwaes_session_t;

/**
 * hwaes_open() - Opens a trusty hwaes session.
 * @session: pointer to the returned session handle.
 *
 * Return: NO_ERROR on success, error code less than 0 on error.
 */
int hwaes_open(hwaes_session_t* session);

/**
 * struct hwcrypt_shm_hd - Handle descriptor for a shared memory.
 * @handle: handle to the shared memory.
 * @base:   base address (on client virtual address space) of the shared memory.
 * @size:   size of the shared memory region.
 */
struct hwcrypt_shm_hd {
    handle_t handle;
    const void* base;
    size_t size;
};

/**
 * struct hwcrypt_arg_in - Input argument struct for hwcrypt.
 * @data_ptr:   pointer to the argument data.
 * @len:        length of the argument data.
 * @shm_hd_ptr: pointer to the shared memory descriptor handler.
 *              It is only set when the argument is stored on shared memory.
 *              It is an optional field, which shall be null if not used.
 *
 * If shared memory is not used, the data will be copied into TIPC message
 * and sent to the server.
 */
struct hwcrypt_arg_in {
    const void* data_ptr;
    size_t len;
    struct hwcrypt_shm_hd* shm_hd_ptr;
};

/**
 * struct hwcrypt_arg_out - Output argument struct for hwcrypt.
 * @data_ptr:   pointer to the argument data.
 * @len:        length of the argument data.
 * @shm_hd_ptr: pointer to the shared memory descriptor handler.
 *              It is only set when the argument is stored on shared memory.
 *              It is an optional field, which shall be null if not used.
 */
struct hwcrypt_arg_out {
    void* data_ptr;
    size_t len;
    struct hwcrypt_shm_hd* shm_hd_ptr;
};

/**
 * struct hwcrypt_args - Arguments struct for hwcrypt.
 * @key:      key of the crypt operation.
 * @iv:       iv of the crypt operation.
 * @aad:      aad of the crypt operation.
 * @text_in:  input text of the crypt operation.
 * @tag_in:   input tag of the crypt operation.
 *            It is an optional field.
 * @text_out: output text of the crypt operation.
 * @tag_out:  output tag of the crypt operation.
 *            It is an optional field.
 * @padding:  the type of padding.
 * @key_type: the type of key.
 * @mode:     the mode of the crypt operation.
 */
struct hwcrypt_args {
    struct hwcrypt_arg_in key;
    struct hwcrypt_arg_in iv;
    struct hwcrypt_arg_in aad;
    struct hwcrypt_arg_in text_in;
    struct hwcrypt_arg_in tag_in;
    struct hwcrypt_arg_out text_out;
    struct hwcrypt_arg_out tag_out;
    uint32_t key_type;
    uint32_t padding;
    uint32_t mode;
};

/**
 * hwaes_encrypt() - Perform AES encryption.
 * @session: session handle retrieved from hwaes_open.
 * @args:    arguments for the AES encryption.
 *
 * Return: NO_ERROR on success, error code less than 0 on error.
 *
 */
int hwaes_encrypt(hwaes_session_t session, const struct hwcrypt_args* args);

/**
 * hwaes_decrypt() - Perform AES decryption.
 * @session: session handle retrieved from hwaes_open.
 * @args:    arguments for the AES decryption.
 *
 * Return: NO_ERROR on success, error code less than 0 on error.
 *
 */
int hwaes_decrypt(hwaes_session_t session, const struct hwcrypt_args* args);

/**
 * hwaes_close() - Closes the session.
 */
void hwaes_close(hwaes_session_t session);

__END_CDECLS
