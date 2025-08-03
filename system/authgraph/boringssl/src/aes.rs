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

//! BoringSSL-based implementation for the AuthGraph AES traits.
use crate::ossl;
use authgraph_core::{
    ag_err,
    error::Error,
    key::{AesKey, Nonce12},
    traits::AesGcm,
    vec_try, FallibleAllocExt,
};
use authgraph_wire::ErrorCode;
use openssl::symm::{Cipher, Crypter, Mode};

/// Tag size for AES-GCM-256.
const TAG_LEN: usize = 16;

/// BoringSSL-based implementation of the [`AesGcm`] trait.
#[derive(Clone)]
pub struct BoringAes;

impl AesGcm for BoringAes {
    fn encrypt(
        &self,
        key: &AesKey,
        payload: &[u8],
        aad: &[u8],
        nonce: &Nonce12,
    ) -> Result<Vec<u8>, Error> {
        let cipher = Cipher::aes_256_gcm();
        let mut crypter = ossl!(Crypter::new(cipher, Mode::Encrypt, &key.0, Some(&nonce.0)))?;

        ossl!(crypter.aad_update(aad))?;
        let mut ct = vec_try![0; payload.len() + cipher.block_size()]?;
        let mut count = ossl!(crypter.update(payload, &mut ct))?;
        count += ossl!(crypter.finalize(&mut ct))?;
        ct.truncate(count);

        let mut tag = [0; TAG_LEN];
        ossl!(crypter.get_tag(&mut tag))?;
        ct.try_extend_from_slice(&tag)?;

        Ok(ct)
    }

    fn decrypt(
        &self,
        key: &AesKey,
        ct: &[u8],
        aad: &[u8],
        nonce: &Nonce12,
    ) -> Result<Vec<u8>, Error> {
        let cipher = Cipher::aes_256_gcm();
        let mut crypter = ossl!(Crypter::new(cipher, Mode::Decrypt, &key.0, Some(&nonce.0)))?;

        let split_idx =
            ct.len().checked_sub(TAG_LEN).ok_or_else(|| ag_err!(InternalError, "too short"))?;
        let (ct, tag) = ct.split_at(split_idx);

        ossl!(crypter.aad_update(aad))?;
        let mut pt = vec_try![0; ct.len() + cipher.block_size()]?;
        let mut count = ossl!(crypter.update(ct, &mut pt))?;

        ossl!(crypter.set_tag(tag))?;
        count += ossl!(crypter.finalize(&mut pt))?;
        pt.truncate(count);

        Ok(pt)
    }
    fn box_clone(&self) -> Box<dyn AesGcm> {
        Box::new(self.clone())
    }
}
