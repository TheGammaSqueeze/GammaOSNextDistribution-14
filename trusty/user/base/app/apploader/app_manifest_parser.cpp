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

#define TLOG_TAG "apploader-app-manifest"

#include "app_manifest_parser.h"
#include <lib/app_manifest/app_manifest.h>
#include <trusty_log.h>
#include <uapi/err.h>

extern "C" bool apploader_parse_manifest_from_metadata(
        struct apploader_package_metadata* pkg_meta,
        struct manifest_extracts* manifest_extracts) {
    return apploader_parse_manifest((const char*)pkg_meta->manifest_start,
                                    pkg_meta->manifest_size, manifest_extracts);
}

extern "C" bool apploader_parse_manifest(
        const char* manifest_start,
        const size_t manifest_size,
        struct manifest_extracts* manifest_extracts) {
    struct app_manifest_iterator iter;
    app_manifest_iterator_reset(&iter, manifest_start, manifest_size);

    struct manifest_extracts out_ext = {
            /* Applications are critical by default */
            .non_critical_app = false,
            /* Apps without a version in the manifest get a default of 0 */
            .version = 0,
            /* Applications do not require encryption by default */
            .requires_encryption = false,
    };

    struct app_manifest_config_entry entry;
    int out_error;
    bool uuid_found = false;
    bool version_found = false;
    bool min_version_found = false;
    bool mgmt_flags_found = false;
    bool apploader_flags_found = false;

    while (app_manifest_iterator_next(&iter, &entry, &out_error)) {
        if (out_error != NO_ERROR) {
            TLOGE("Error iterating over manifest entries (%d)\n", out_error);
            return false;
        }
        switch (entry.key) {
        case APP_MANIFEST_CONFIG_KEY_UUID:
            if (uuid_found) {
                TLOGE("Manifest contained duplicate UUID entry");
                return false;
            }
            out_ext.uuid = entry.value.uuid;
            uuid_found = true;
            break;
        case APP_MANIFEST_CONFIG_KEY_MGMT_FLAGS:
            if (mgmt_flags_found) {
                TLOGE("Manifest contained duplicate mgmt_flags entry");
                return false;
            }
            if (entry.value.mgmt_flags &
                APP_MANIFEST_MGMT_FLAGS_NON_CRITICAL_APP) {
                out_ext.non_critical_app = true;
            }
            mgmt_flags_found = true;
            break;
        case APP_MANIFEST_CONFIG_KEY_VERSION:
            if (version_found) {
                TLOGE("Manifest contained duplicate version entry");
                return false;
            }
            out_ext.version = entry.value.version;
            version_found = true;
            break;
        case APP_MANIFEST_CONFIG_KEY_MIN_VERSION:
            if (min_version_found) {
                TLOGE("Manifest contained duplicate min_version entry");
                return false;
            }
            out_ext.min_version = entry.value.min_version;
            min_version_found = true;
            break;
        case APP_MANIFEST_CONFIG_KEY_APPLOADER_FLAGS:
            if (apploader_flags_found) {
                TLOGE("Manifest contained duplicate apploader_flags entry");
                return false;
            }
            if (entry.value.apploader_flags &
                APP_MANIFEST_APPLOADER_FLAGS_REQUIRES_ENCRYPTION) {
                out_ext.requires_encryption = true;
            }
            apploader_flags_found = true;
            break;
        case APP_MANIFEST_CONFIG_KEY_APP_NAME:
        case APP_MANIFEST_CONFIG_KEY_MIN_STACK_SIZE:
        case APP_MANIFEST_CONFIG_KEY_MIN_HEAP_SIZE:
        case APP_MANIFEST_CONFIG_KEY_MAP_MEM:
        case APP_MANIFEST_CONFIG_KEY_START_PORT:
        case APP_MANIFEST_CONFIG_KEY_PINNED_CPU:
        case APP_MANIFEST_CONFIG_KEY_PRIORITY:
        case APP_MANIFEST_CONFIG_KEY_MIN_SHADOW_STACK_SIZE:
            /* The apploader has no use for these values */
            break;
        }
    }

    /* min_version defaults to the app version if not specified */
    if (!min_version_found) {
        out_ext.min_version = out_ext.version;
    }

    if (out_ext.version < out_ext.min_version) {
        TLOGE("Manifest contained invalid min_version entry");
        return false;
    }

    if (uuid_found) {
        *manifest_extracts = out_ext;
    }

    return (uuid_found);
}
