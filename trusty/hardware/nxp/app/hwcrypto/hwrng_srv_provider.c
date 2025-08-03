/*
 * Copyright (C) 2016-2017 The Android Open Source Project
 * Copyright 2017 NXP
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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include "caam.h"
#include "common.h"
#include "hwrng_srv_priv.h"

#define TLOG_TAG "hwrng_caam"
#include <trusty_log.h>

void hwrng_dev_get_rng_data(uint8_t* buf, size_t buf_len) {
    uint32_t res = caam_hwrng(buf, buf_len);
    assert(res == CAAM_SUCCESS);
}

void hwrng_init_srv_provider(void) {
    int rc;

    TLOGE("Init HWRNG service provider\n");
    /* Nothing to initialize here, just start service */
    rc = hwrng_start_service();
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to start HWRNG service\n", rc);
    }
}
