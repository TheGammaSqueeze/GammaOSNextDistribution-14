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

#![allow(missing_docs)]
#![no_main]

use binder_random_parcel_rs::fuzz_service;
use keystore2::authorization::AuthorizationManager;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let authorization_service = AuthorizationManager::new_native_binder().unwrap_or_else(|e| {
        panic!("Failed to create android.security.authorization service because of {:?}", e);
    });
    fuzz_service(&mut authorization_service.as_binder(), data);
});
