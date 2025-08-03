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

#include "error_reporting.h"

#ifdef STORAGE_ENABLE_ERROR_REPORTING

#include <android/frameworks/stats/atoms.h>
#include <android/trusty/stats/ports.h>
#include <trusty/uuid.h>
#include <trusty_log.h>

#include <storage_consts.h>
#include "block_device_tipc.h"

#define TLOG_TAG "ss-err_rep"

struct uuid storage_service_uuid = STORAGE_SERVICE_UUID;
char storage_service_uuid_str[UUID_STR_SIZE];

static void init_uuid_str(void) {
    if (storage_service_uuid_str[0] == '\0') {
        uuid_to_str(&storage_service_uuid, storage_service_uuid_str);
    }
}

static enum stats_trusty_file_system map_fs_name(const char* name) {
    if (name == file_system_id_tp) {
        return TRUSTY_FS_TP;
    }
    if (name == file_system_id_tdp) {
        return TRUSTY_FS_TDP;
    }
    if (name == file_system_id_td) {
        return TRUSTY_FS_TD;
    }
    if (name == file_system_id_tdea) {
        return TRUSTY_FS_TDEA;
    }
    if (name == file_system_id_nsp) {
        return TRUSTY_FS_NSP;
    }
    TLOGI("Unknown fs name: %s\n", name);
    return TRUSTY_FS_UNKNOWN;
}

static enum stats_trusty_block_type map_block_type(
        enum error_report_block_type block_type) {
    switch (block_type) {
    case BLOCK_TYPE_UNKNOWN:
        return TRUSTY_BLOCKTYPE_UNKNOWN;
    case BLOCK_TYPE_FILES_ROOT:
        return TRUSTY_BLOCKTYPE_FILES_ROOT;
    case BLOCK_TYPE_FREE_ROOT:
        return TRUSTY_BLOCKTYPE_FREE_ROOT;
    case BLOCK_TYPE_FILES_INTERNAL:
        return TRUSTY_BLOCKTYPE_FILES_INTERNAL;
    case BLOCK_TYPE_FREE_INTERNAL:
        return TRUSTY_BLOCKTYPE_FREE_INTERNAL;
    case BLOCK_TYPE_FILE_ENTRY:
        return TRUSTY_BLOCKTYPE_FILE_ENTRY;
    case BLOCK_TYPE_FILE_BLOCK_MAP:
        return TRUSTY_BLOCKTYPE_FILE_BLOCK_MAP;
    case BLOCK_TYPE_FILE_DATA:
        return TRUSTY_BLOCKTYPE_FILE_DATA;
    case BLOCK_TYPE_CHECKPOINT_ROOT:
        return TRUSTY_BLOCKTYPE_CHECKPOINT_ROOT;
    case BLOCK_TYPE_CHECKPOINT_FILES_ROOT:
        return TRUSTY_BLOCKTYPE_CHECKPOINT_FILES_ROOT;
    case BLOCK_TYPE_CHECKPOINT_FREE_ROOT:
        return TRUSTY_BLOCKTYPE_CHECKPOINT_FREE_ROOT;
    default:
        TLOGE("Unknown block type: %d\n", block_type);
        return TRUSTY_BLOCKTYPE_UNKNOWN;
    }
}

void do_error_report(enum stats_trusty_storage_error_type type,
                     const char* fs_name,
                     enum error_report_block_type block_type) {
    init_uuid_str();
    struct stats_trusty_storage_error atom = {
            .reverse_domain_name = "google.android.trusty",
            .reverse_domain_name_len = sizeof("google.android.trusty"),
            .error = TRUSTY_STORAGE_ERROR_UNKNOWN,
            .app_id = storage_service_uuid_str,
            .app_id_len = UUID_STR_SIZE,
            .client_app_id = "",
            .client_app_id_len = sizeof(""),
            .write = 0,
            .file_system = TRUSTY_FS_UNKNOWN,
            .file_path_hash = 0,
            .block_type = TRUSTY_BLOCKTYPE_UNKNOWN,
            .repair_counter = 0,
    };

    atom.error = type;
    atom.file_system = map_fs_name(fs_name);
    atom.block_type = map_block_type(block_type);

    stats_trusty_storage_error_report(METRICS_ISTATS_PORT,
                                      sizeof(METRICS_ISTATS_PORT), atom);
}

#else /* STORAGE_ENABLE_ERROR_REPORTING */

void do_error_report(enum stats_trusty_storage_error_type type,
                     const char* fs_name,
                     enum error_report_block_type block_type) {}

#endif /* STORAGE_ENABLE_ERROR_REPORTING */
