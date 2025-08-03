/*
 * Copyright (C) 2016-2017 The Android Open Source Project
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
#include <sys/types.h>
#include <uapi/trusty_uuid.h>

struct hwkey_keyslot {
    const char* key_id;
    const uuid_t* uuid;
    const void* priv;
    uint32_t (*handler)(const struct hwkey_keyslot* slot,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen);
};

__BEGIN_CDECLS

void hwkey_init_srv_provider(void);

void hwkey_install_keys(const struct hwkey_keyslot* keys, unsigned int kcnt);

int hwkey_start_service(void);

uint32_t derive_key_v1(const uuid_t* uuid,
                       const uint8_t* ikm_data,
                       size_t ikm_len,
                       uint8_t* key_data,
                       size_t* key_len);

__END_CDECLS
