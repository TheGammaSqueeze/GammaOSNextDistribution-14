// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use anyhow::{ensure, Result};

pub const ENTROPY_LEN: usize = bssl_sys::CTR_DRBG_ENTROPY_LEN as usize;

pub type Entropy = [u8; ENTROPY_LEN];

pub struct Drbg(*mut bssl_sys::CTR_DRBG_STATE);

impl Drbg {
    pub fn new(entropy: &Entropy) -> Result<Drbg> {
        // SAFETY: entropy must be a valid pointer because it comes from a reference, and a null
        // pointer is allowed for personalization. CTR_DRBG_new doesn't retain the entropy pointer
        // for use after it returns.
        let p = unsafe { bssl_sys::CTR_DRBG_new(entropy.as_ptr(), std::ptr::null(), 0) };
        ensure!(!p.is_null(), "CTR_DRBG_new failed");
        Ok(Drbg(p))
    }

    pub fn reseed(&mut self, entropy: &Entropy) -> Result<()> {
        ensure!(
            // SAFETY: We know that self.0 is valid because it was initialised from CTR_DRBG_new in
            // Drbg::new above. The entropy pointer must be valid because it comes from a reference,
            // and CTR_DRBG_reseed doesn't retain it after it returns.
            unsafe { bssl_sys::CTR_DRBG_reseed(self.0, entropy.as_ptr(), std::ptr::null(), 0) }
                == 1,
            "CTR_DRBG_reseed failed"
        );
        Ok(())
    }

    pub fn generate(&mut self, buf: &mut [u8]) -> Result<()> {
        ensure!(
            // SAFETY: We know that self.0 is valid because it was initialised from CTR_DRBG_new in
            // Drbg::new above. The out pointer and length must be valid and unaliased because they
            // come from a mutable slice reference, and CTR_DRBG_generate doesn't retain them after
            // it returns.
            unsafe {
                bssl_sys::CTR_DRBG_generate(
                    self.0,
                    buf.as_mut_ptr(),
                    buf.len(),
                    std::ptr::null(),
                    0,
                )
            } == 1,
            "CTR_DRBG_generate failed"
        );
        Ok(())
    }
}

impl Drop for Drbg {
    fn drop(&mut self) {
        // SAFETY: We know that self.0 is valid because it was initialised from CTR_DRBG_new in
        // Drbg::new above, and this is the only place that frees it.
        unsafe {
            bssl_sys::CTR_DRBG_free(self.0);
        }
    }
}

// SAFETY: CTR_DRBG functions can be called from any thread.
unsafe impl Send for Drbg {}
