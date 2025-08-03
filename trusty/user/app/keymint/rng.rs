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
//! Trusty implementation of `kmr_common::crypto::Rng`.
use crate::ffi_bindings::trusty_rng_add_entropy;
use kmr_common::crypto;

/// [`crypto::Rng`] implementation for Trusty.
#[derive(Default)]
pub struct TrustyRng;

impl crypto::Rng for TrustyRng {
    fn add_entropy(&mut self, data: &[u8]) {
        trusty_rng_add_entropy(data);
    }
    fn fill_bytes(&mut self, dest: &mut [u8]) {
        openssl::rand::rand_bytes(dest).unwrap(); // safe: BoringSSL's RAND_bytes() never fails
    }
}
