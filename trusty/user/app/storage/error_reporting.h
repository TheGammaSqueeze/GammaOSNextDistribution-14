/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "fs.h"

#if defined(STORAGE_ENABLE_ERROR_REPORTING)

#include <android/frameworks/stats/atoms.h>

#else

/*
 * Copied from auto-generated atoms.h from the trusty/user/base/lib/atoms
 * library. We can't include this header for host tests because it would require
 * somehow overhauling the protoc.mk build type to work for host tests.
 */
enum stats_trusty_storage_error_type {
    TRUSTY_STORAGE_ERROR_UNKNOWN,
    TRUSTY_STORAGE_ERROR_SUPERBLOCK_INVALID,
    TRUSTY_STORAGE_ERROR_BLOCK_MAC_MISMATCH,
    TRUSTY_STORAGE_ERROR_BLOCK_HEADER_INVALID,
    TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH,
    TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED,
    TRUSTY_STORAGE_ERROR_RPMB_COUNTER_READ_FAILURE,
    TRUSTY_STORAGE_ERROR_RPMB_MAC_MISMATCH,
    TRUSTY_STORAGE_ERROR_RPMB_ADDR_MISMATCH,
    TRUSTY_STORAGE_ERROR_RPMB_FAILURE_RESPONSE,
    TRUSTY_STORAGE_ERROR_RPMB_UNKNOWN,
    TRUSTY_STORAGE_ERROR_RPMB_SCSI_ERROR,
    TRUSTY_STORAGE_ERROR_IO_ERROR,
    TRUSTY_STORAGE_ERROR_PROXY_COMMUNICATION_FAILURE,
};

#endif /* STORAGE_ENABLE_ERROR_REPORTING */

enum error_report_block_type {
    BLOCK_TYPE_UNKNOWN,
    BLOCK_TYPE_FILES_ROOT,
    BLOCK_TYPE_FREE_ROOT,
    BLOCK_TYPE_FILES_INTERNAL,
    BLOCK_TYPE_FREE_INTERNAL,
    BLOCK_TYPE_FILE_ENTRY,
    BLOCK_TYPE_FILE_BLOCK_MAP,
    BLOCK_TYPE_FILE_DATA,
    BLOCK_TYPE_CHECKPOINT_ROOT,
    BLOCK_TYPE_CHECKPOINT_FILES_ROOT,
    BLOCK_TYPE_CHECKPOINT_FREE_ROOT,
};

void do_error_report(enum stats_trusty_storage_error_type type,
                     const char* fs_name,
                     enum error_report_block_type block_type);

static inline void error_report_superblock_invalid(const char* fs_name) {
    do_error_report(TRUSTY_STORAGE_ERROR_SUPERBLOCK_INVALID, fs_name,
                    BLOCK_TYPE_UNKNOWN);
}

static inline void error_report_block_mac_mismatch(
        const char* fs_name,
        enum error_report_block_type block_type) {
    do_error_report(TRUSTY_STORAGE_ERROR_BLOCK_MAC_MISMATCH, fs_name,
                    block_type);
}

static inline void error_report_rpmb_counter_mismatch(void) {
    do_error_report(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH, "unknown",
                    BLOCK_TYPE_UNKNOWN);
}

static inline void error_report_rpmb_counter_mismatch_recovered(void) {
    do_error_report(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED,
                    "unknown", BLOCK_TYPE_UNKNOWN);
}

static inline void error_report_rpmb_counter_read_failure(void) {
    do_error_report(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_READ_FAILURE, "unknown",
                    BLOCK_TYPE_UNKNOWN);
}

static inline void error_report_rpmb_mac_mismatch(void) {
    do_error_report(TRUSTY_STORAGE_ERROR_RPMB_MAC_MISMATCH, "unknown",
                    BLOCK_TYPE_UNKNOWN);
}

static inline void error_report_rpmb_addr_mismatch(void) {
    do_error_report(TRUSTY_STORAGE_ERROR_RPMB_ADDR_MISMATCH, "unknown",
                    BLOCK_TYPE_UNKNOWN);
}
