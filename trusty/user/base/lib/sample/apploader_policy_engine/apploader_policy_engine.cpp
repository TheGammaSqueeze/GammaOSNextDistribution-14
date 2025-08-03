/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define TLOG_TAG "apploader_policy_engine"

#include <lib/apploader_policy_engine/apploader_policy_engine.h>

#include <interface/hwkey/hwkey.h>
#include <inttypes.h>
#include <lib/hwkey/hwkey.h>
#include <stddef.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <uapi/trusty_uuid.h>
#include <string>

/*
 * Copied from apploader.c
 */
/*
 * Maximum size of any key we could possibly get from hwkey.
 * If the latter returns a key larger than this, validation fails.
 * For now, 128 bytes should be enough since the apploader only
 * supports 256-bit (P-256) ECDSA signatures which only need
 * about 90 bytes for their public keys. If other curves or algorithms
 * e.g., P-521 or RSS, are supported by the apploader at a later time,
 * this value will need to increase.
 */
constexpr uint32_t kMaximumKeySize =
        std::max(128, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

static int get_key(hwkey_session_t hwkey_session,
                   std::string_view op,
                   uint8_t key_id,
                   const uint8_t** public_key,
                   unsigned int* public_key_size) {
    std::string key_slot{"com.android.trusty.apploader."};
    key_slot += op;
    key_slot += ".key.";
    key_slot += std::to_string(static_cast<unsigned>(key_id));

    unsigned int key_size = kMaximumKeySize;
    uint8_t* key_bytes = (uint8_t*)malloc(key_size * sizeof(uint8_t));
    if (!key_bytes) {
        TLOGE("Failed to allocate memory for key\n");
        return ERR_NO_MEMORY;
    }

    long rc = hwkey_get_keyslot_data(hwkey_session, key_slot.c_str(), key_bytes,
                                     &key_size);
    if (rc < 0) {
        TLOGE("Failed to get key %" PRIu8 " from hwkey (%ld)\n", key_id, rc);
        free(key_bytes);
        return rc;
    }

    *public_key = key_bytes;
    *public_key_size = key_size;

    return NO_ERROR;
}

static int get_sign_key(uint8_t key_id,
                        const uint8_t** public_key,
                        unsigned int* public_key_size) {
    long rc = hwkey_open();
    if (rc < 0) {
        TLOGE("Failed to connect to hwkey (%ld)\n", rc);
        return rc;
    }

    hwkey_session_t hwkey_session = static_cast<hwkey_session_t>(rc);

    rc = get_key(hwkey_session, "sign", key_id, public_key, public_key_size);
    hwkey_close(hwkey_session);

    return rc;
}

int apploader_policy_engine_get_key(uint8_t kid,
                                    const uint8_t** public_key_ptr,
                                    unsigned int* public_key_size_ptr) {
    return get_sign_key(kid, public_key_ptr, public_key_size_ptr);
}

void apploader_policy_engine_put_key(const uint8_t* public_key) {
    if (public_key) {
        free((void*)public_key);
    }
}

bool apploader_policy_engine_validate(struct apploader_policy_data* data) {
    return true;
}
