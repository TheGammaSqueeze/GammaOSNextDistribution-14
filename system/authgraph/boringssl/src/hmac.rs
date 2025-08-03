// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

//! BoringSSL-based implementation for the AuthGraph HMAC-related traits.
use authgraph_core::{
    error::Error,
    key::{EcdhSecret, HmacKey, PseudoRandKey, SHA_256_LEN},
    traits::{Hkdf, Hmac, Sha256},
    vec_try,
};

/// BoringSSL-based implementation of the [`Hmac`] trait.  Note that this implementation relies on
/// the Android-specific `openssl::hmac` extension to the `rust-openssl` crate.
pub struct BoringHmac;

impl Hmac for BoringHmac {
    fn compute_hmac(&self, key: &HmacKey, data: &[u8]) -> Result<Vec<u8>, Error> {
        let md = openssl::md::Md::sha256();
        let mut out = vec_try![0; md.size()]?;
        ossl!(openssl::hmac::hmac(md, &key.0, data, &mut out))?;
        Ok(out)
    }
}

/// BoringSSL-based implementation of the [`Hkdf`] trait. Note that this implementation relies on
/// the Android-specific `openssl::hkdf` extension to the `rust-openssl` crate.
pub struct BoringHkdf;

impl Hkdf for BoringHkdf {
    fn extract(&self, salt: &[u8], ikm: &EcdhSecret) -> Result<PseudoRandKey, Error> {
        let md = openssl::md::Md::sha256();
        let mut out = PseudoRandKey([0; 32]);
        ossl!(openssl::hkdf::hkdf_extract(&mut out.0, md, &ikm.0, salt))?;
        Ok(out)
    }

    fn expand(&self, prk: &PseudoRandKey, context: &[u8]) -> Result<PseudoRandKey, Error> {
        let md = openssl::md::Md::sha256();
        let mut out = PseudoRandKey([0; 32]);
        ossl!(openssl::hkdf::hkdf_expand(&mut out.0, md, &prk.0, context))?;
        Ok(out)
    }
}

/// BoringSSL-based implementation of the [`Sha256`] trait.
pub struct BoringSha256;

impl Sha256 for BoringSha256 {
    fn compute_sha256(&self, data: &[u8]) -> Result<[u8; SHA_256_LEN], Error> {
        let mut sha256 = openssl::sha::Sha256::new();
        sha256.update(data);
        Ok(sha256.finish())
    }
}
