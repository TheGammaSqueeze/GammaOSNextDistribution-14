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

#pragma once

#include <lk/compiler.h>
#include <trusty_ipc.h>

#include <hardware/hw_auth_token.h>
__BEGIN_CDECLS

typedef handle_t keymaster_session_t;

/**
 * keymaster_open() - Opens a Keymaster session
 *
 * Return: a keymaster_session_t >= 0 on success, or an error code < 0
 * on failure.
 */
int keymaster_open(void);

/**
 * keymaster_close() - Opens a Keymaster session
 * @session: the keymaster_session_t to close.
 *
 */
void keymaster_close(keymaster_session_t session);

/**
 * Deprecated; use the appropriate token specific functions below if possible.
 *
 * keymaster_get_auth_token_key() - Retrieves the auth token signature key
 * @session: the keymaster_session_t to close.
 * @key_buf_p: pointer to buffer pointer to be allocated and filled with auth
 *             token key. Ownership of this pointer is transferred to the caller
 *             and must be deallocated with a call to free().
 * @size_p: set to the allocated size of key_buf
 *
 */
int keymaster_get_auth_token_key(keymaster_session_t session,
                                 uint8_t** key_buf_p,
                                 uint32_t* size_p);

/**
 * keymaster_sign_auth_token() - Sign the 'token' by populating the HMAC field
 *                                using the keymaster auth token.
 * @session: An open keymaster_session_t.
 * @token: The token for signing
 *
 * @return: NO_ERROR if token was signed successfully
 */
int keymaster_sign_auth_token(keymaster_session_t session,
                              hw_auth_token_t* token);

/**
 * keymaster_validate_auth_token() - Validate the incoming token against the
 *                                keymaster auth token.
 * @session: An open keymaster_session_t.
 * @token: The token to validate
 *
 * @return: NO_ERROR if the token is trusted, otherwise rejection reason.
 */
int keymaster_validate_auth_token(keymaster_session_t session,
                                  hw_auth_token_t* token);

/**
 * keymaster_get_device_ids() - Return non-unique device IDs (product,
 *                              manufacturer, etc).
 * @session: An open keymaster_session_t.
 * @info_buffer_p: A CBOR map to be populated with the canonicalized device
 *                 info that a caller needs in order to be spec compliant with
 *                 the IRemotelyProvisionedComponent HAL. Ownership of this
 *                 pointer is transferred to the caller and must be
 *                 deallocated with a call to free().
 * @size_p: Set to the allocated size of info_buffer_p.
 * @return: NO_ERROR on success.
 */
int keymaster_get_device_info(keymaster_session_t session,
                              uint8_t** info_buffer_p,
                              uint32_t* size_p);
__END_CDECLS
