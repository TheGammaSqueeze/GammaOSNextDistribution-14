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

#pragma once

#include <stdint.h>
#include <trusty_ipc.h>

#include "BnCastAuth.h"

class CastAuthImpl : public BnCastAuth {
public:
    android::binder::Status ProvisionKey(
            const std::vector<uint8_t>& wrappedKey);
    android::binder::Status SignHash(const std::vector<uint8_t>& hash,
                                     std::vector<uint8_t>* signature);

    static int runService();

private:
    friend class android::sp<CastAuthImpl>;
    CastAuthImpl() = default;
    int SaveKey(const uint8_t* key, size_t length);
    int LoadKey(uint8_t* key, size_t* length);
};
