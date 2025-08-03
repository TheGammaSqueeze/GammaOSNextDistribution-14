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

#define TLOG_TAG "keybox"

#include <assert.h>
#include <inttypes.h>
#include <lk/list.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <trusty_log.h>

#include "keybox.h"

/*
 * THIS DOES NOT PROVIDE ANY SECURITY
 *
 * This is not a useful wrapping system. This is just intended as enough to mock
 * that:
 * 1. The wrapped data and unwrapped data are not the same.
 * 2. The wrapped data will fail to unwrap if it is trivially tampered with.
 */
enum keybox_status keybox_unwrap(const uint8_t* wrapped_keybox,
                                 size_t wrapped_keybox_len,
                                 uint8_t* keybox_plaintext,
                                 size_t keybox_plaintext_buf_len,
                                 size_t* keybox_plaintext_len) {
    if (wrapped_keybox_len < 1) {
        TLOGE("Wrapped keybox too short: %zu\n", wrapped_keybox_len);
        return KEYBOX_STATUS_INVALID_REQUEST;
    }

    if (keybox_plaintext_buf_len < wrapped_keybox_len - 1) {
        TLOGE("Unwrapped keybox buffer too short: %zu\n",
              keybox_plaintext_buf_len);
        return KEYBOX_STATUS_INVALID_REQUEST;
    }

    /* Validate checksum */
    uint8_t checksum = 0;
    for (size_t i = 0; i < wrapped_keybox_len - 1; i++) {
        checksum ^= wrapped_keybox[i];
    }

    if (checksum != wrapped_keybox[wrapped_keybox_len - 1]) {
        TLOGE("Invalid checksum\n");
        return KEYBOX_STATUS_UNWRAP_FAIL;
    }

    /* Flip bits with masking byte */
    for (size_t i = 0; i < wrapped_keybox_len - 1; i++) {
        keybox_plaintext[i] = wrapped_keybox[i] ^ 0x42;
    }

    *keybox_plaintext_len = wrapped_keybox_len - 1;

    return KEYBOX_STATUS_SUCCESS;
}
