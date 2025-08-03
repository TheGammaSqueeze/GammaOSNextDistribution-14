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

//! Support for explicit key chain format & conversion from legacy DiceArtifacts.

use crate::Error;
use ciborium::Value;
use coset::{AsCborValue, CborOrdering, CborSerializable, CoseKey};
use diced_open_dice::{DiceArtifacts, OwnedDiceArtifacts, CDI_SIZE};
use std::fmt;

const EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION: u64 = 1;

/// An OwnedDiceArtifactsWithExplicitKey is an OwnedDiceArtifacts that also exposes its
/// DICE chain (BCC) in explicit key format.
pub struct OwnedDiceArtifactsWithExplicitKey {
    artifacts: OwnedDiceArtifacts,
    explicit_key_dice_chain: Option<Vec<u8>>,
}

impl OwnedDiceArtifactsWithExplicitKey {
    /// Create an OwnedDiceArtifactsWithExplicitKey from OwnedDiceArtifacts.
    pub fn from_owned_artifacts(artifacts: OwnedDiceArtifacts) -> Result<Self, Error> {
        let explicit_key_dice_chain = artifacts.bcc().map(to_explicit_chain).transpose()?;
        Ok(Self { artifacts, explicit_key_dice_chain })
    }

    /// Get the dice chain in Explicit-key DiceCertChain format.
    ///
    /// ExplicitKeyDiceCertChain = [
    ///     1, ; version,
    ///     DiceCertChainInitialPayload,
    ///     * DiceChainEntry
    /// ]
    /// ; Encoded in accordance with Core Deterministic Encoding Requirements [RFC 8949 s4.2.1]
    /// DiceCertChainInitialPayload = bstr .cbor PubKeyEd25519 /
    ///     bstr .cbor PubKeyECDSA256 /
    ///     bstr .cbor PubKeyECDSA384 ; subjectPublicKey
    /// Note: Extracted from the spec at ExplicitKeyDiceCertChain.cddl. Keep in sync!
    pub fn explicit_key_dice_chain(&self) -> Option<&[u8]> {
        self.explicit_key_dice_chain.as_deref()
    }
}

impl fmt::Debug for OwnedDiceArtifactsWithExplicitKey {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Sensitive information omitted")
    }
}

impl DiceArtifacts for OwnedDiceArtifactsWithExplicitKey {
    fn cdi_attest(&self) -> &[u8; CDI_SIZE] {
        self.artifacts.cdi_attest()
    }

    fn cdi_seal(&self) -> &[u8; CDI_SIZE] {
        self.artifacts.cdi_seal()
    }

    fn bcc(&self) -> Option<&[u8]> {
        self.artifacts.bcc()
    }
}

// Convert a DICE chain to explicit key format. Note that this method checks if the input is
// already in the Explicit-key format & returns it if so. The check is lightweight though.
// A twisted incorrect dice chain input may produce incorrect output.
fn to_explicit_chain(dice_chain_bytes: &[u8]) -> Result<Vec<u8>, Error> {
    let dice_chain = deserialize_cbor_array(dice_chain_bytes)?;
    // Check if the dice_chain is already in explicit key format
    if matches!(&&dice_chain[..], [Value::Integer(_version), Value::Bytes(_public_key), ..]) {
        return Ok(dice_chain_bytes.to_vec());
    }
    let mut res: Vec<Value> = Vec::with_capacity(dice_chain.len() + 1);
    let mut it = dice_chain.into_iter();
    res.push(Value::from(EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION));
    let root_key = it
        .next()
        .ok_or(Error::UnexpectedItem("Empty dice chain", "dice_chain with at least 1 node"))?;

    // Canonicalize the root public key as per Core Deterministic Encoding Requirements
    let mut root_key = CoseKey::from_cbor_value(root_key)?;
    root_key.canonicalize(CborOrdering::Lexicographic);
    // Converts to .bstr .cbor COSE_KEY
    let root_key = root_key.to_vec()?;
    res.push(Value::Bytes(root_key));
    res.extend(it);
    Ok(Value::Array(res).to_vec()?)
}

fn deserialize_cbor_array(cbor_array: &[u8]) -> Result<Vec<Value>, Error> {
    let value = Value::from_slice(cbor_array)?;
    value.into_array().map_err(|_| Error::UnexpectedItem("-", "Array"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use rdroidtest::rdroidtest;

    #[rdroidtest]
    fn implicit_to_explicit_dice_chain_conversion() {
        const COMPOS_CHAIN_SIZE_LEGACY: usize = 5;
        let implicit_dice = include_bytes!("../testdata/compos_chain_legacy");
        let explicit_chain = to_explicit_chain(implicit_dice).unwrap();
        let chain = Value::from_slice(&explicit_chain).unwrap();
        let chain_arr = chain.into_array().unwrap();
        // There is an added node for version in the explicit key format
        assert_eq!(chain_arr.len(), COMPOS_CHAIN_SIZE_LEGACY + 1);
        assert_eq!(chain_arr[0].as_integer().unwrap(), EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION.into());
        assert!(chain_arr[1].is_bytes());
    }

    #[rdroidtest]
    fn explicit_to_explicit_dice_chain_conversion() {
        let implicit_dice = include_bytes!("../testdata/compos_chain_legacy");
        let explicit_chain = to_explicit_chain(implicit_dice).unwrap();
        let converted_chain = to_explicit_chain(&explicit_chain).unwrap();
        assert_eq!(explicit_chain, converted_chain);
    }

    #[rdroidtest]
    fn root_key_deterministic_encoding() {
        // Encoding of
        // [{"a": 1, 3: -7, 1234: 1, 1: 1}]
        // Notice the root key map are not ordered (in lexicographic order)!
        const IMPLICIT_CHAIN: &str = "81A461610103261904D2010101";
        // Encoding of
        // [1, h'A4010103261904D201616101']
        // where A4010103261904D201616101 =
        //      .cbor {1: 1, 3: -7, 1234: 1, "a": 1}
        // Notice the root key labels are in lexicographic order!
        const EXPECTED_EXPLICIT_CHAIN: &str = "82014ca4010103261904d201616101";

        let explicit_chain = to_explicit_chain(&hex::decode(IMPLICIT_CHAIN).unwrap()).unwrap();
        assert_eq!(hex::encode(explicit_chain), EXPECTED_EXPLICIT_CHAIN);
    }
}
