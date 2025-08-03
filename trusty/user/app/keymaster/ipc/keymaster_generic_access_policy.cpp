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

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/trusty_uuid.h>

#include <interface/keymaster/keymaster.h>

#define TLOG_TAG "KMAccessPolicy"
#include <trusty_log.h>

/*
 * Note: This access policy is replicated in
 * trusty/user/app/keymint/generic_access_policy/lib.rs for Keymint Rust. Any
 * updates to this access policy should be replicated there as well.
 */
static uuid_t accessible_uuids[] = {
        /* gatekeeper uuid */
        {0x38ba0cdc,
         0xdf0e,
         0x11e4,
         {0x98, 0x69, 0x23, 0x3f, 0xb6, 0xae, 0x47, 0x95}},
        /* confirmation ui uuid */
        {0x7dee2364,
         0xc036,
         0x425b,
         {0xb0, 0x86, 0xdf, 0x0f, 0x6c, 0x23, 0x3c, 0x1b}},
        /* unit test uuid */
        {0xf3ba7629,
         0xe8cc,
         0x44a0,
         {0x88, 0x4d, 0xf9, 0x16, 0xf7, 0x03, 0xa2, 0x00}},
};

bool keymaster_check_target_access_policy(uuid_t* uuid) {
    for (auto accessible_uuid : accessible_uuids) {
        if (memcmp(uuid, &accessible_uuid, sizeof(accessible_uuid)) == 0) {
            return true;
        }
    }
    return false;
}

bool keymaster_check_secure_target_access_policy_provisioning(
        const uuid_t* uuid) {
    /* Not Supported */
    return false;
}
