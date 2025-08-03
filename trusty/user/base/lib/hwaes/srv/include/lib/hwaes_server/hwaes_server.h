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

#pragma once

#include <interface/hwaes/hwaes.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <sys/types.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

struct tipc_hset;

/**
 * struct hwaes_arg_in - Input argument struct for hwaes_aes_op
 * @data_ptr: Pointer to the argument data.
 * @len:      Length of the argument data.
 */
struct hwaes_arg_in {
    const uint8_t* data_ptr;
    size_t len;
};

/**
 * struct hwaes_arg_out - Output argument struct for hwaes_aes_op
 * @data_ptr: Pointer to the argument data.
 * @len:      Length of the argument data.
 */
struct hwaes_arg_out {
    uint8_t* data_ptr;
    size_t len;
};

/**
 * struct hwaes_aes_op_args - Arguments struct for hwaes_aes_op
 * @key:           The AES key.
 * @iv:            The initialization vector.
 * @aad:           The additional authenticated data (AAD).
 * @text_in:       The input text data.
 * @tag_in:        The input tag.
 * @text_out:      The output text data.
 * @tag_out:       The output tag.
 * @key_type:      The key_type, one of instances at enum hwaes_key_type.
 * @padding:       The padding type, one of instances at enum hwaes_padding.
 * @mode:          The AES mode, one of instances at enum hwaes_mode.
 * @encrypt:       Flag for encryption (true) or decryption (false).
 */
struct hwaes_aes_op_args {
    struct hwaes_arg_in key;
    struct hwaes_arg_in iv;
    struct hwaes_arg_in aad;
    struct hwaes_arg_in text_in;
    struct hwaes_arg_in tag_in;
    struct hwaes_arg_out text_out;
    struct hwaes_arg_out tag_out;
    uint32_t key_type;
    uint32_t padding;
    uint32_t mode;
    bool encrypt;
};

/**
 * hwaes_aes_op() - Perform AES operation
 * @args: Arguments for the AES operation
 *
 * Must be implemented by client of lib_hwaes_server.
 *
 * Return: 0 on success, or an error code (enum hwaes_err type) on failure.
 */
uint32_t hwaes_aes_op(const struct hwaes_aes_op_args* args);

/**
 * add_hwaes_service() - Add hwaes service
 * @hset: Handle set created by tipc_hset_create()
 * @allowed_clients: Array of pointers to allowed client UUIDs
 * @allowed_clients_len: Length of @allowed_clients
 *
 * Client should call tipc_run_event_loop at some point after this call returns.
 *
 * This function does not take ownership of @allowed_clients. The array must
 * live at least as long as the service.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int add_hwaes_service(struct tipc_hset* hset,
                      const uuid_t** allowed_clients,
                      size_t allowed_clients_len);

__END_CDECLS
