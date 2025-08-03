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

//! Arc related definitions and operations.

use crate::{
    ag_err,
    error::Error,
    key::{AesKey, Identity, Nonce12},
    traits::{AesGcm, Rng},
    try_to_vec, FallibleAllocExt,
};
use alloc::vec::Vec;
use authgraph_wire::ErrorCode;
use coset::{
    cbor::value::Value, iana, AsCborValue, CborSerializable, CoseEncrypt0, CoseEncrypt0Builder,
    CoseError, HeaderBuilder, Label,
};
use zeroize::ZeroizeOnDrop;

/// Protected header label for permissions
pub const PERMISSIONS: Label = Label::Int(-70001);
/// Protected header label for timestamp added at the time of creating the arc
pub const TIMESTAMP: Label = Label::Int(-70003);
/// Protected header label for the nonce used for key agreement
pub const KE_NONCE: Label = Label::Int(-70004);
/// Protected header label for the type of the payload encrypted in the arc
pub const PAYLOAD_TYPE: Label = Label::Int(-70005);
/// Protected header label for the version of the payload encrypted in the arc, if applicable
pub const PAYLOAD_VERSION: Label = Label::Int(-70006);
/// Protected header label for sequence number, if used to prevent replay attacks
pub const SEQUENCE_NUMBER: Label = Label::Int(-70007);
/// Protected header label for direction which indicates the usage of the shared encryption key
pub const DIRECTION: Label = Label::Int(-70008);
/// Protected header label for "authentication completed" indicator used during key agreement
pub const AUTHENTICATION_COMPLETE: Label = Label::Int(-70009);
/// Protected header label for session id which is computed during key agreement
pub const SESSION_ID: Label = Label::Int(-70010);

/// Permission key for source identity
pub const SOURCE_ID_KEY: i32 = -4770552;
/// Permission key for sink identity
pub const SINK_ID_KEY: i32 = -4770553;
/// Permission key for `minting allowed`
pub const MINTING_ALLOWED_KEY: i32 = -4770555;
/// Permission key for `deleted on biometric change`
pub const DELETED_ON_BIOMETRIC_CHANGE_KEY: i32 = -4770556;

/// Authentication status of an arc containing the shared keys from an authenticated key agreement.
pub struct AuthenticationCompleted(pub bool);

/// Direction of shared encryption key usage
pub enum DirectionOfEncryption {
    /// Incoming messages
    In = 1,
    /// Outgoing messages
    Out = 2,
}

/// Payload of an arc in plain text
#[derive(Default, ZeroizeOnDrop)]
pub struct ArcPayload(pub Vec<u8>);

/// The structure containing the contents of an arc.
#[derive(Default)]
pub struct ArcContent {
    /// Payload in plain text
    pub payload: ArcPayload,
    /// Protocol specific protected headers
    pub protected_headers: Vec<(Label, Value)>,
    /// Protocol specific unprotected headers
    pub unprotected_headers: Vec<(Label, Value)>,
}

/// A function to create arcs given the inputs.
/// Note: This method only inserts the headers that are relevant for CoseEncrypt0
/// (e.g. algorithm, iv).
/// It is left up to the caller to insert all the other necessary protocol specific headers.
pub fn create_arc(
    encrypting_key: &AesKey,
    arc_content: ArcContent,
    cipher: &dyn AesGcm,
    rng: &mut dyn Rng,
) -> Result<Vec<u8>, Error> {
    // Create a nonce for the encryption operation
    let nonce_for_enc = Nonce12::new(rng);
    let mut protected_hdr = HeaderBuilder::new().algorithm(iana::Algorithm::A256GCM);
    for (lbl, val) in arc_content.protected_headers {
        match lbl {
            Label::Int(int_lbl) => {
                protected_hdr = protected_hdr.value(int_lbl, val);
            }
            Label::Text(txt_lbl) => {
                protected_hdr = protected_hdr.text_value(txt_lbl, val);
            }
        }
    }
    let mut unprotected_hdr = HeaderBuilder::new();
    unprotected_hdr = unprotected_hdr.iv(try_to_vec(&nonce_for_enc.0)?);

    for (lbl, val) in arc_content.unprotected_headers {
        match lbl {
            Label::Int(int_lbl) => {
                unprotected_hdr = unprotected_hdr.value(int_lbl, val);
            }
            Label::Text(txt_lbl) => {
                unprotected_hdr = unprotected_hdr.text_value(txt_lbl, val);
            }
        }
    }
    let arc = CoseEncrypt0Builder::new()
        .protected(protected_hdr.build())
        .unprotected(unprotected_hdr.build())
        .try_create_ciphertext(&arc_content.payload.0.clone(), &[], |input, aad| {
            cipher.encrypt(encrypting_key, input, aad, &nonce_for_enc)
        })?
        .build();
    Ok(arc.to_vec()?)
}

/// A function to deconstruct an encoded arc and return the arc content
pub fn decipher_arc(
    decrypting_key: &AesKey,
    arc: &[u8],
    cipher: &dyn AesGcm,
) -> Result<ArcContent, Error> {
    let arc = CoseEncrypt0::from_slice(arc)?;
    let nonce = Nonce12(
        arc.unprotected
            .iv
            .clone()
            .try_into()
            .map_err(|e| ag_err!(InternalError, "failed to decode iv {:?}", e))?,
    );
    let payload = arc.decrypt(&[], |cipher_text, aad| {
        cipher.decrypt(decrypting_key, cipher_text, aad, &nonce)
    })?;
    let (protected_headers, unprotected_headers) =
        (arc.protected.header.rest, arc.unprotected.rest);
    Ok(ArcContent { payload: ArcPayload(payload), protected_headers, unprotected_headers })
}

/// A structure encapsulating the (optional) permissions added to an arc as a protected header.
#[derive(Default)]
pub struct Permissions {
    /// Identity of the source
    pub source_id: Option<Identity>,
    /// Identity of the sink
    pub sink_id: Option<Identity>,
    /// Set of identities to which the secret encrypted in the arc is allowed to be minted
    pub minting_allowed: Option<Vec<Identity>>,
    /// Indicates whether an auth key issued from a biometric TA is invalidated on new enrollment or
    /// removal of biometrics.
    pub deleted_on_biometric_change: Option<bool>,
}

impl AsCborValue for Permissions {
    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let permissions_vec: Vec<(Value, Value)> = match value {
            Value::Map(permissions) => permissions,
            _ => return Err(CoseError::UnexpectedItem("_", "Map")),
        };
        let mut permissions = Permissions::default();
        for (key, val) in permissions_vec {
            if let Value::Integer(k) = key {
                if k == SOURCE_ID_KEY.into() {
                    let id = val.as_bytes().cloned().ok_or(CoseError::UnexpectedItem(
                        "None",
                        "Encoded source identity in the permissions",
                    ))?;
                    permissions.source_id = Some(Identity::from_slice(&id)?);
                }
                if k == SINK_ID_KEY.into() {
                    let id = val.as_bytes().cloned().ok_or(CoseError::UnexpectedItem(
                        "None",
                        "Encoded sink identity in the permissions",
                    ))?;
                    permissions.sink_id = Some(Identity::from_slice(&id)?);
                }
                if k == MINTING_ALLOWED_KEY.into() {
                    let id_array = val.as_array().cloned().ok_or(CoseError::UnexpectedItem(
                        "None",
                        "An array of encoded identity for minting allowed in the permissions",
                    ))?;
                    let mut allowed_ids = Vec::new();
                    for a in id_array {
                        let id = a.as_bytes().cloned().ok_or(CoseError::UnexpectedItem(
                            "None",
                            "An encoded identity for minting in the permissions",
                        ))?;
                        allowed_ids
                            .try_push(Identity::from_slice(&id)?)
                            .map_err(|_| CoseError::EncodeFailed)?;
                    }
                    permissions.minting_allowed = Some(allowed_ids);
                }
                if k == DELETED_ON_BIOMETRIC_CHANGE_KEY.into() {
                    permissions.deleted_on_biometric_change = val.as_bool();
                }
            }
        }
        Ok(permissions)
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        let mut cbor_permissions = Vec::<(Value, Value)>::new();
        if let Some(source_id) = self.source_id {
            let key = Value::Integer(SOURCE_ID_KEY.into());
            let val = Value::Bytes(source_id.to_vec()?);
            cbor_permissions.try_push((key, val)).map_err(|_| CoseError::EncodeFailed)?;
        }
        if let Some(sink_id) = self.sink_id {
            let key = Value::Integer(SINK_ID_KEY.into());
            let val = Value::Bytes(sink_id.to_vec()?);
            cbor_permissions.try_push((key, val)).map_err(|_| CoseError::EncodeFailed)?;
        }
        if let Some(minting_allowed) = self.minting_allowed {
            let key = Value::Integer(MINTING_ALLOWED_KEY.into());
            let mut array = Vec::new();
            for a in minting_allowed {
                array.try_push(Value::Bytes(a.to_vec()?)).map_err(|_| CoseError::EncodeFailed)?;
            }
            let val = Value::Array(array);
            cbor_permissions.try_push((key, val)).map_err(|_| CoseError::EncodeFailed)?;
        }
        if let Some(del_on_biometric_change) = self.deleted_on_biometric_change {
            let key = Value::Integer(DELETED_ON_BIOMETRIC_CHANGE_KEY.into());
            let val = Value::Bool(del_on_biometric_change);
            cbor_permissions.try_push((key, val)).map_err(|_| CoseError::EncodeFailed)?;
        }
        Ok(Value::Map(cbor_permissions))
    }
}
