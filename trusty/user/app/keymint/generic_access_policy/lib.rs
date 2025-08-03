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
//! The library implementing the generic access policy for Keymint Rust.
//! This is a replication of `keymaster_generic_access_policy`.

use tipc::Uuid;

const ACCESSIBLE_UUIDS: [Uuid; 4] = [
    /* gatekeeper uuid */
    Uuid::new(0x38ba0cdc, 0xdf0e, 0x11e4, [0x98, 0x69, 0x23, 0x3f, 0xb6, 0xae, 0x47, 0x95]),
    /* confirmation UI uuid */
    Uuid::new(0x7dee2364, 0xc036, 0x425b, [0xb0, 0x86, 0xdf, 0x0f, 0x6c, 0x23, 0x3c, 0x1b]),
    /* keymaster unit test uuid */
    Uuid::new(0xf3ba7629, 0xe8cc, 0x44a0, [0x88, 0x4d, 0xf9, 0x16, 0xf7, 0x03, 0xa2, 0x00]),
    /* keymint unit test uuid */
    Uuid::new(0xd322eec9, 0x6d03, 0x49fa, [0x82, 0x1c, 0x1c, 0xcd, 0x27, 0x05, 0x71, 0x9c]),
];

pub fn keymint_check_target_access_policy(uuid: &Uuid) -> bool {
    if ACCESSIBLE_UUIDS.contains(uuid) {
        return true;
    }
    return false;
}

pub fn keymint_check_secure_target_access_policy_provisioning(_uuid: &Uuid) -> bool {
    /* Not Supported */
    return false;
}
