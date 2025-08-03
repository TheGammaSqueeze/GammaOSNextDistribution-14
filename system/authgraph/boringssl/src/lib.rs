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

//! This crate provides the default implementations for the Authgraph traits using
//! BoringSSL via the `openssl` crate.

extern crate alloc; // Needed for use of `ag_err!` from `authgraph_core`

use authgraph_core::traits;

/// Macro to auto-generate error mapping around invocations of `openssl` methods.
/// An invocation like:
///
/// ```ignore
/// let x = ossl!(y.func(a, b))?;
/// ```
///
/// will map to:
///
/// ```ignore
/// let x = y.func(a, b).map_err(ag_err!(Internal, "failed to perform: y.func(a, b)"))?;
/// ```
///
/// Requires local `use authgraph_core::{Error, ag_err}`.
#[macro_export]
macro_rules! ossl {
    { $e:expr } => {
        $e.map_err(|err| authgraph_core::error::Error(
            authgraph_wire::ErrorCode::InternalError,
            format!(concat!("failed to perform: ", stringify!($e), ": {:?}"), err)
        ))
    }
}

mod aes;
pub use aes::BoringAes;
pub mod ec;
pub use ec::BoringEcDh;
pub use ec::BoringEcDsa;
mod hmac;
pub use hmac::{BoringHkdf, BoringHmac, BoringSha256};
mod rng;
pub use rng::BoringRng;
pub mod test_device;

#[cfg(test)]
mod tests;

/// Return a populated [`traits::CryptoTraitImpl`] structure that uses BoringSSL implementations for
/// cryptographic traits.
pub fn crypto_trait_impls() -> traits::CryptoTraitImpl {
    traits::CryptoTraitImpl {
        aes_gcm: Box::new(BoringAes),
        ecdh: Box::new(BoringEcDh),
        ecdsa: Box::new(BoringEcDsa),
        hmac: Box::new(BoringHmac),
        hkdf: Box::new(BoringHkdf),
        sha256: Box::new(BoringSha256),
        rng: Box::new(BoringRng),
    }
}
