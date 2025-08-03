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

#define TLOG_TAG "hwrng_fake_srv"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <hwcrypto/hwrng_dev.h>
#include <trusty_log.h>

#pragma message "Compiling FAKE HWRNG provider"

int hwrng_dev_init(void) {
    TLOGE("Init FAKE!!!! HWRNG service provider\n");
    TLOGE("FAKE HWRNG service provider MUST be replaced with the REAL one\n");
    return NO_ERROR;
}

static size_t counter = 1;

__attribute__((no_sanitize("unsigned-integer-overflow"))) int
hwrng_dev_get_rng_data(uint8_t* buf, size_t buf_len) {
    for (uint8_t* end = buf + buf_len; buf < end; ++buf) {
        *buf = counter++ & 0xff;
    }
    return NO_ERROR;
}
