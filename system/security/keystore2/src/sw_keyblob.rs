// Copyright 2023, The Android Open Source Project
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

//! Code for parsing software-backed keyblobs, as emitted by the C++ reference implementation of
//! KeyMint.

use crate::error::Error;
use crate::ks_err;
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
    ErrorCode::ErrorCode, HardwareAuthenticatorType::HardwareAuthenticatorType,
    KeyFormat::KeyFormat, KeyOrigin::KeyOrigin, KeyParameter::KeyParameter,
    KeyParameterValue::KeyParameterValue, KeyPurpose::KeyPurpose, PaddingMode::PaddingMode,
    Tag::Tag, TagType::TagType,
};
use anyhow::Result;
use keystore2_crypto::hmac_sha256;
use std::mem::size_of;

/// Root of trust value.
const SOFTWARE_ROOT_OF_TRUST: &[u8] = b"SW";

/// Error macro.
macro_rules! bloberr {
    { $($arg:tt)+ } => {
        anyhow::Error::new(Error::Km(ErrorCode::INVALID_KEY_BLOB)).context(ks_err!($($arg)+))
    };
}

/// Get the `KeyParameterValue` associated with a tag from a collection of `KeyParameter`s.
fn get_tag_value(params: &[KeyParameter], tag: Tag) -> Option<&KeyParameterValue> {
    params.iter().find_map(|kp| if kp.tag == tag { Some(&kp.value) } else { None })
}

/// Get the [`TagType`] for a [`Tag`].
fn tag_type(tag: &Tag) -> TagType {
    TagType((tag.0 as u32 & 0xf0000000) as i32)
}

/// Extract key material and combined key characteristics from a legacy authenticated keyblob.
pub fn export_key(
    data: &[u8],
    params: &[KeyParameter],
) -> Result<(KeyFormat, Vec<u8>, Vec<KeyParameter>)> {
    let hidden = hidden_params(params, &[SOFTWARE_ROOT_OF_TRUST]);
    let KeyBlob { key_material, hw_enforced, sw_enforced } =
        KeyBlob::new_from_serialized(data, &hidden)?;

    let mut combined = hw_enforced;
    combined.extend_from_slice(&sw_enforced);

    let algo_val =
        get_tag_value(&combined, Tag::ALGORITHM).ok_or_else(|| bloberr!("No algorithm found!"))?;

    let format = match algo_val {
        KeyParameterValue::Algorithm(Algorithm::AES)
        | KeyParameterValue::Algorithm(Algorithm::TRIPLE_DES)
        | KeyParameterValue::Algorithm(Algorithm::HMAC) => KeyFormat::RAW,
        KeyParameterValue::Algorithm(Algorithm::RSA)
        | KeyParameterValue::Algorithm(Algorithm::EC) => KeyFormat::PKCS8,
        _ => return Err(bloberr!("Unexpected algorithm {:?}", algo_val)),
    };

    let key_material = match (format, algo_val) {
        (KeyFormat::PKCS8, KeyParameterValue::Algorithm(Algorithm::EC)) => {
            // Key material format depends on the curve.
            let curve = get_tag_value(&combined, Tag::EC_CURVE)
                .ok_or_else(|| bloberr!("Failed to determine curve for EC key!"))?;
            match curve {
                KeyParameterValue::EcCurve(EcCurve::CURVE_25519) => key_material,
                KeyParameterValue::EcCurve(EcCurve::P_224) => {
                    pkcs8_wrap_nist_key(&key_material, EcCurve::P_224)?
                }
                KeyParameterValue::EcCurve(EcCurve::P_256) => {
                    pkcs8_wrap_nist_key(&key_material, EcCurve::P_256)?
                }
                KeyParameterValue::EcCurve(EcCurve::P_384) => {
                    pkcs8_wrap_nist_key(&key_material, EcCurve::P_384)?
                }
                KeyParameterValue::EcCurve(EcCurve::P_521) => {
                    pkcs8_wrap_nist_key(&key_material, EcCurve::P_521)?
                }
                _ => {
                    return Err(bloberr!("Unexpected EC curve {curve:?}"));
                }
            }
        }
        (KeyFormat::RAW, _) => key_material,
        (format, algo) => {
            return Err(bloberr!(
                "Unsupported combination of {format:?} format for {algo:?} algorithm"
            ));
        }
    };
    Ok((format, key_material, combined))
}

/// DER-encoded `AlgorithmIdentifier` for a P-224 key.
const DER_ALGORITHM_ID_P224: &[u8] = &[
    0x30, 0x10, // SEQUENCE (AlgorithmIdentifier) {
    0x06, 0x07, // OBJECT IDENTIFIER (algorithm)
    0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, // 1.2.840.10045.2.1 (ecPublicKey)
    0x06, 0x05, // OBJECT IDENTIFIER (param)
    0x2b, 0x81, 0x04, 0x00, 0x21, //  1.3.132.0.33 (secp224r1) }
];

/// DER-encoded `AlgorithmIdentifier` for a P-256 key.
const DER_ALGORITHM_ID_P256: &[u8] = &[
    0x30, 0x13, // SEQUENCE (AlgorithmIdentifier) {
    0x06, 0x07, // OBJECT IDENTIFIER (algorithm)
    0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, // 1.2.840.10045.2.1 (ecPublicKey)
    0x06, 0x08, // OBJECT IDENTIFIER (param)
    0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, //  1.2.840.10045.3.1.7 (secp256r1) }
];

/// DER-encoded `AlgorithmIdentifier` for a P-384 key.
const DER_ALGORITHM_ID_P384: &[u8] = &[
    0x30, 0x10, // SEQUENCE (AlgorithmIdentifier) {
    0x06, 0x07, // OBJECT IDENTIFIER (algorithm)
    0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, // 1.2.840.10045.2.1 (ecPublicKey)
    0x06, 0x05, // OBJECT IDENTIFIER (param)
    0x2b, 0x81, 0x04, 0x00, 0x22, //  1.3.132.0.34 (secp384r1) }
];

/// DER-encoded `AlgorithmIdentifier` for a P-384 key.
const DER_ALGORITHM_ID_P521: &[u8] = &[
    0x30, 0x10, // SEQUENCE (AlgorithmIdentifier) {
    0x06, 0x07, // OBJECT IDENTIFIER (algorithm)
    0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, // 1.2.840.10045.2.1 (ecPublicKey)
    0x06, 0x05, // OBJECT IDENTIFIER (param)
    0x2b, 0x81, 0x04, 0x00, 0x23, //  1.3.132.0.35 (secp521r1) }
];

/// DER-encoded integer value zero.
const DER_VERSION_0: &[u8] = &[
    0x02, // INTEGER
    0x01, // len
    0x00, // value 0
];

/// Given a NIST curve EC key in the form of a DER-encoded `ECPrivateKey`
/// (RFC 5915 s3), wrap it in a DER-encoded PKCS#8 format (RFC 5208 s5).
fn pkcs8_wrap_nist_key(nist_key: &[u8], curve: EcCurve) -> Result<Vec<u8>> {
    let der_alg_id = match curve {
        EcCurve::P_224 => DER_ALGORITHM_ID_P224,
        EcCurve::P_256 => DER_ALGORITHM_ID_P256,
        EcCurve::P_384 => DER_ALGORITHM_ID_P384,
        EcCurve::P_521 => DER_ALGORITHM_ID_P521,
        _ => return Err(bloberr!("unknown curve {curve:?}")),
    };

    // Output format is:
    //
    //    PrivateKeyInfo ::= SEQUENCE {
    //        version                   INTEGER,
    //        privateKeyAlgorithm       AlgorithmIdentifier,
    //        privateKey                OCTET STRING,
    //    }
    //
    // Start by building the OCTET STRING so we know its length.
    let mut nist_key_octet_string = Vec::new();
    nist_key_octet_string.push(0x04); // OCTET STRING
    add_der_len(&mut nist_key_octet_string, nist_key.len())?;
    nist_key_octet_string.extend_from_slice(nist_key);

    let mut buf = Vec::new();
    buf.push(0x30); // SEQUENCE
    add_der_len(&mut buf, DER_VERSION_0.len() + der_alg_id.len() + nist_key_octet_string.len())?;
    buf.extend_from_slice(DER_VERSION_0);
    buf.extend_from_slice(der_alg_id);
    buf.extend_from_slice(&nist_key_octet_string);
    Ok(buf)
}

/// Append a DER-encoded length value to the given buffer.
fn add_der_len(buf: &mut Vec<u8>, len: usize) -> Result<()> {
    if len <= 0x7f {
        buf.push(len as u8)
    } else if len <= 0xff {
        buf.push(0x81); // One length octet to come
        buf.push(len as u8);
    } else if len <= 0xffff {
        buf.push(0x82); // Two length octets to come
        buf.push((len >> 8) as u8);
        buf.push((len & 0xff) as u8);
    } else {
        return Err(bloberr!("Unsupported DER length {len}"));
    }
    Ok(())
}

/// Plaintext key blob, with key characteristics.
#[derive(PartialEq, Eq)]
struct KeyBlob {
    /// Raw key material.
    key_material: Vec<u8>,
    /// Hardware-enforced key characteristics.
    hw_enforced: Vec<KeyParameter>,
    /// Software-enforced key characteristics.
    sw_enforced: Vec<KeyParameter>,
}

impl KeyBlob {
    /// Key blob version.
    const KEY_BLOB_VERSION: u8 = 0;

    /// Hard-coded HMAC key used for keyblob authentication.
    const LEGACY_HMAC_KEY: &'static [u8] = b"IntegrityAssuredBlob0\0";

    /// Size (in bytes) of appended MAC.
    const MAC_LEN: usize = 8;

    /// Parse a serialized [`KeyBlob`].
    fn new_from_serialized(mut data: &[u8], hidden: &[KeyParameter]) -> Result<Self> {
        // Keyblob needs to be at least long enough for:
        // - version byte,
        // - 4-byte len for key material
        // - 4-byte len for hw_enforced params
        // - 4-byte len for sw_enforced params
        // - MAC tag.
        if data.len() < (1 + 3 * size_of::<u32>() + Self::MAC_LEN) {
            return Err(bloberr!("blob not long enough (len = {})", data.len()));
        }

        // Check the HMAC in the last 8 bytes before doing anything else.
        let mac = &data[data.len() - Self::MAC_LEN..];
        let computed_mac = Self::compute_hmac(&data[..data.len() - Self::MAC_LEN], hidden)?;
        if mac != computed_mac {
            return Err(bloberr!("invalid key blob"));
        }

        let version = consume_u8(&mut data)?;
        if version != Self::KEY_BLOB_VERSION {
            return Err(bloberr!("unexpected blob version {}", version));
        }
        let key_material = consume_vec(&mut data)?;
        let hw_enforced = deserialize_params(&mut data)?;
        let sw_enforced = deserialize_params(&mut data)?;

        // Should just be the (already-checked) MAC left.
        let rest = &data[Self::MAC_LEN..];
        if !rest.is_empty() {
            return Err(bloberr!("extra data (len {})", rest.len()));
        }
        Ok(KeyBlob { key_material, hw_enforced, sw_enforced })
    }

    /// Compute the authentication HMAC for a KeyBlob. This is built as:
    ///   HMAC-SHA256(HK, data || serialize(hidden))
    /// with HK = b"IntegrityAssuredBlob0\0".
    fn compute_hmac(data: &[u8], hidden: &[KeyParameter]) -> Result<Vec<u8>> {
        let hidden_data = serialize_params(hidden)?;
        let mut combined = data.to_vec();
        combined.extend_from_slice(&hidden_data);
        let mut tag = hmac_sha256(Self::LEGACY_HMAC_KEY, &combined)?;
        tag.truncate(Self::MAC_LEN);
        Ok(tag)
    }
}

/// Build the parameters that are used as the hidden input to HMAC calculations:
/// - `ApplicationId(data)` if present
/// - `ApplicationData(data)` if present
/// - (repeated) `RootOfTrust(rot)` where `rot` is a hardcoded piece of root of trust information.
fn hidden_params(params: &[KeyParameter], rots: &[&[u8]]) -> Vec<KeyParameter> {
    let mut results = Vec::new();
    if let Some(app_id) = get_tag_value(params, Tag::APPLICATION_ID) {
        results.push(KeyParameter { tag: Tag::APPLICATION_ID, value: app_id.clone() });
    }
    if let Some(app_data) = get_tag_value(params, Tag::APPLICATION_DATA) {
        results.push(KeyParameter { tag: Tag::APPLICATION_DATA, value: app_data.clone() });
    }
    for rot in rots {
        results.push(KeyParameter {
            tag: Tag::ROOT_OF_TRUST,
            value: KeyParameterValue::Blob(rot.to_vec()),
        });
    }
    results
}

/// Retrieve a `u8` from the start of the given slice, if possible.
fn consume_u8(data: &mut &[u8]) -> Result<u8> {
    match data.first() {
        Some(b) => {
            *data = &(*data)[1..];
            Ok(*b)
        }
        None => Err(bloberr!("failed to find 1 byte")),
    }
}

/// Move past a bool value from the start of the given slice, if possible.
/// Bool values should only be included if `true`, so fail if the value
/// is anything other than 1.
fn consume_bool(data: &mut &[u8]) -> Result<bool> {
    let b = consume_u8(data)?;
    if b == 0x01 {
        Ok(true)
    } else {
        Err(bloberr!("bool value other than 1 encountered"))
    }
}

/// Retrieve a (host-ordered) `u32` from the start of the given slice, if possible.
fn consume_u32(data: &mut &[u8]) -> Result<u32> {
    const LEN: usize = size_of::<u32>();
    if data.len() < LEN {
        return Err(bloberr!("failed to find {LEN} bytes"));
    }
    let chunk: [u8; LEN] = data[..LEN].try_into().unwrap(); // safe: just checked
    *data = &(*data)[LEN..];
    Ok(u32::from_ne_bytes(chunk))
}

/// Retrieve a (host-ordered) `i32` from the start of the given slice, if possible.
fn consume_i32(data: &mut &[u8]) -> Result<i32> {
    const LEN: usize = size_of::<i32>();
    if data.len() < LEN {
        return Err(bloberr!("failed to find {LEN} bytes"));
    }
    let chunk: [u8; LEN] = data[..LEN].try_into().unwrap(); // safe: just checked
    *data = &(*data)[4..];
    Ok(i32::from_ne_bytes(chunk))
}

/// Retrieve a (host-ordered) `i64` from the start of the given slice, if possible.
fn consume_i64(data: &mut &[u8]) -> Result<i64> {
    const LEN: usize = size_of::<i64>();
    if data.len() < LEN {
        return Err(bloberr!("failed to find {LEN} bytes"));
    }
    let chunk: [u8; LEN] = data[..LEN].try_into().unwrap(); // safe: just checked
    *data = &(*data)[LEN..];
    Ok(i64::from_ne_bytes(chunk))
}

/// Retrieve a vector of bytes from the start of the given slice, if possible,
/// with the length of the data expected to appear as a host-ordered `u32` prefix.
fn consume_vec(data: &mut &[u8]) -> Result<Vec<u8>> {
    let len = consume_u32(data)? as usize;
    if len > data.len() {
        return Err(bloberr!("failed to find {} bytes", len));
    }
    let result = data[..len].to_vec();
    *data = &(*data)[len..];
    Ok(result)
}

/// Retrieve the contents of a tag of `TagType::Bytes`.  The `data` parameter holds
/// the as-yet unparsed data, and a length and offset are read from this (and consumed).
/// This length and offset refer to a location in the combined `blob_data`; however,
/// the offset is expected to be the next unconsumed chunk of `blob_data`, as indicated
/// by `next_blob_offset` (which itself is updated as a result of consuming the data).
fn consume_blob(
    data: &mut &[u8],
    next_blob_offset: &mut usize,
    blob_data: &[u8],
) -> Result<Vec<u8>> {
    let data_len = consume_u32(data)? as usize;
    let data_offset = consume_u32(data)? as usize;
    // Expect the blob data to come from the next offset in the initial blob chunk.
    if data_offset != *next_blob_offset {
        return Err(bloberr!("got blob offset {} instead of {}", data_offset, next_blob_offset));
    }
    if (data_offset + data_len) > blob_data.len() {
        return Err(bloberr!(
            "blob at offset [{}..{}+{}] goes beyond blob data size {}",
            data_offset,
            data_offset,
            data_len,
            blob_data.len(),
        ));
    }

    let slice = &blob_data[data_offset..data_offset + data_len];
    *next_blob_offset += data_len;
    Ok(slice.to_vec())
}

/// Deserialize a collection of [`KeyParam`]s in legacy serialized format. The provided slice is
/// modified to contain the unconsumed part of the data.
fn deserialize_params(data: &mut &[u8]) -> Result<Vec<KeyParameter>> {
    let blob_data_size = consume_u32(data)? as usize;
    if blob_data_size > data.len() {
        return Err(bloberr!(
            "blob data size {} bigger than data (len={})",
            blob_data_size,
            data.len()
        ));
    }

    let blob_data = &data[..blob_data_size];
    let mut next_blob_offset = 0;

    // Move past the blob data.
    *data = &data[blob_data_size..];

    let param_count = consume_u32(data)? as usize;
    let param_size = consume_u32(data)? as usize;
    if param_size > data.len() {
        return Err(bloberr!(
            "size mismatch 4+{}+4+4+{} > {}",
            blob_data_size,
            param_size,
            data.len()
        ));
    }

    let mut results = Vec::new();
    for _i in 0..param_count {
        let tag_num = consume_u32(data)? as i32;
        let tag = Tag(tag_num);
        let value = match tag_type(&tag) {
            TagType::INVALID => return Err(bloberr!("invalid tag {:?} encountered", tag)),
            TagType::ENUM | TagType::ENUM_REP => {
                let val = consume_i32(data)?;
                match tag {
                    Tag::ALGORITHM => KeyParameterValue::Algorithm(Algorithm(val)),
                    Tag::BLOCK_MODE => KeyParameterValue::BlockMode(BlockMode(val)),
                    Tag::PADDING => KeyParameterValue::PaddingMode(PaddingMode(val)),
                    Tag::DIGEST | Tag::RSA_OAEP_MGF_DIGEST => {
                        KeyParameterValue::Digest(Digest(val))
                    }
                    Tag::EC_CURVE => KeyParameterValue::EcCurve(EcCurve(val)),
                    Tag::ORIGIN => KeyParameterValue::Origin(KeyOrigin(val)),
                    Tag::PURPOSE => KeyParameterValue::KeyPurpose(KeyPurpose(val)),
                    Tag::USER_AUTH_TYPE => {
                        KeyParameterValue::HardwareAuthenticatorType(HardwareAuthenticatorType(val))
                    }
                    _ => KeyParameterValue::Integer(val),
                }
            }
            TagType::UINT | TagType::UINT_REP => KeyParameterValue::Integer(consume_i32(data)?),
            TagType::ULONG | TagType::ULONG_REP => {
                KeyParameterValue::LongInteger(consume_i64(data)?)
            }
            TagType::DATE => KeyParameterValue::DateTime(consume_i64(data)?),
            TagType::BOOL => KeyParameterValue::BoolValue(consume_bool(data)?),
            TagType::BIGNUM | TagType::BYTES => {
                KeyParameterValue::Blob(consume_blob(data, &mut next_blob_offset, blob_data)?)
            }
            _ => return Err(bloberr!("unexpected tag type for {:?}", tag)),
        };
        results.push(KeyParameter { tag, value });
    }

    Ok(results)
}

/// Serialize a collection of [`KeyParameter`]s into a format that is compatible with previous
/// implementations:
///
/// ```text
/// [0..4]              Size B of `TagType::Bytes` data, in host order.
/// [4..4+B]      (*)   Concatenated contents of each `TagType::Bytes` tag.
/// [4+B..4+B+4]        Count N of the number of parameters, in host order.
/// [8+B..8+B+4]        Size Z of encoded parameters.
/// [12+B..12+B+Z]      Serialized parameters one after another.
/// ```
///
/// Individual parameters are serialized in the last chunk as:
///
/// ```text
/// [0..4]              Tag number, in host order.
/// Followed by one of the following depending on the tag's `TagType`; all integers in host order:
///   [4..5]            Bool value (`TagType::Bool`)
///   [4..8]            i32 values (`TagType::Uint[Rep]`, `TagType::Enum[Rep]`)
///   [4..12]           i64 values, in host order (`TagType::UlongRep`, `TagType::Date`)
///   [4..8] + [8..12]  Size + offset of data in (*) above (`TagType::Bytes`, `TagType::Bignum`)
/// ```
fn serialize_params(params: &[KeyParameter]) -> Result<Vec<u8>> {
    // First 4 bytes are the length of the combined [`TagType::Bytes`] data; come back to set that
    // in a moment.
    let mut result = vec![0; 4];

    // Next append the contents of all of the [`TagType::Bytes`] data.
    let mut blob_size = 0u32;
    for param in params {
        let tag_type = tag_type(&param.tag);
        if let KeyParameterValue::Blob(v) = &param.value {
            if tag_type != TagType::BIGNUM && tag_type != TagType::BYTES {
                return Err(bloberr!("unexpected tag type for tag {:?} with blob", param.tag));
            }
            result.extend_from_slice(v);
            blob_size += v.len() as u32;
        }
    }
    // Go back and fill in the combined blob length in native order at the start.
    result[..4].clone_from_slice(&blob_size.to_ne_bytes());

    result.extend_from_slice(&(params.len() as u32).to_ne_bytes());

    let params_size_offset = result.len();
    result.extend_from_slice(&[0u8; 4]); // placeholder for size of elements
    let first_param_offset = result.len();
    let mut blob_offset = 0u32;
    for param in params {
        result.extend_from_slice(&(param.tag.0 as u32).to_ne_bytes());
        match &param.value {
            KeyParameterValue::Invalid(_v) => {
                return Err(bloberr!("invalid tag found in {:?}", param))
            }

            // Enum-holding variants.
            KeyParameterValue::Algorithm(v) => {
                result.extend_from_slice(&(v.0 as u32).to_ne_bytes())
            }
            KeyParameterValue::BlockMode(v) => {
                result.extend_from_slice(&(v.0 as u32).to_ne_bytes())
            }
            KeyParameterValue::PaddingMode(v) => {
                result.extend_from_slice(&(v.0 as u32).to_ne_bytes())
            }
            KeyParameterValue::Digest(v) => result.extend_from_slice(&(v.0 as u32).to_ne_bytes()),
            KeyParameterValue::EcCurve(v) => result.extend_from_slice(&(v.0 as u32).to_ne_bytes()),
            KeyParameterValue::Origin(v) => result.extend_from_slice(&(v.0 as u32).to_ne_bytes()),
            KeyParameterValue::KeyPurpose(v) => {
                result.extend_from_slice(&(v.0 as u32).to_ne_bytes())
            }
            KeyParameterValue::HardwareAuthenticatorType(v) => {
                result.extend_from_slice(&(v.0 as u32).to_ne_bytes())
            }

            // Value-holding variants.
            KeyParameterValue::Integer(v) => result.extend_from_slice(&(*v as u32).to_ne_bytes()),
            KeyParameterValue::BoolValue(_v) => result.push(0x01u8),
            KeyParameterValue::LongInteger(v) | KeyParameterValue::DateTime(v) => {
                result.extend_from_slice(&(*v as u64).to_ne_bytes())
            }
            KeyParameterValue::Blob(v) => {
                let blob_len = v.len() as u32;
                result.extend_from_slice(&blob_len.to_ne_bytes());
                result.extend_from_slice(&blob_offset.to_ne_bytes());
                blob_offset += blob_len;
            }

            _ => return Err(bloberr!("unknown value found in {:?}", param)),
        }
    }
    let serialized_size = (result.len() - first_param_offset) as u32;

    // Go back and fill in the total serialized size.
    result[params_size_offset..params_size_offset + 4]
        .clone_from_slice(&serialized_size.to_ne_bytes());
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
        Algorithm::Algorithm, BlockMode::BlockMode, Digest::Digest, EcCurve::EcCurve,
        KeyOrigin::KeyOrigin, KeyParameter::KeyParameter,
        KeyParameterValue::KeyParameterValue as KPV, KeyPurpose::KeyPurpose,
        PaddingMode::PaddingMode, Tag::Tag,
    };

    macro_rules! expect_err {
        ($result:expr, $err_msg:expr) => {
            assert!(
                $result.is_err(),
                "Expected error containing '{}', got success {:?}",
                $err_msg,
                $result
            );
            let err = $result.err();
            assert!(
                format!("{:?}", err).contains($err_msg),
                "Unexpected error {:?}, doesn't contain '{}'",
                err,
                $err_msg
            );
        };
    }

    #[test]
    fn test_consume_u8() {
        let buffer = [1, 2];
        let mut data = &buffer[..];
        assert_eq!(1u8, consume_u8(&mut data).unwrap());
        assert_eq!(2u8, consume_u8(&mut data).unwrap());
        let result = consume_u8(&mut data);
        expect_err!(result, "failed to find 1 byte");
    }

    #[test]
    fn test_consume_u32() {
        // All supported platforms are little-endian.
        let buffer = [
            0x01, 0x02, 0x03, 0x04, // little-endian u32
            0x04, 0x03, 0x02, 0x01, // little-endian u32
            0x11, 0x12, 0x13,
        ];
        let mut data = &buffer[..];
        assert_eq!(0x04030201u32, consume_u32(&mut data).unwrap());
        assert_eq!(0x01020304u32, consume_u32(&mut data).unwrap());
        let result = consume_u32(&mut data);
        expect_err!(result, "failed to find 4 bytes");
    }

    #[test]
    fn test_consume_i64() {
        // All supported platforms are little-endian.
        let buffer = [
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, // little-endian i64
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, // little-endian i64
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        ];
        let mut data = &buffer[..];
        assert_eq!(0x0807060504030201i64, consume_i64(&mut data).unwrap());
        assert_eq!(0x0102030405060708i64, consume_i64(&mut data).unwrap());
        let result = consume_i64(&mut data);
        expect_err!(result, "failed to find 8 bytes");
    }

    #[test]
    fn test_consume_vec() {
        let buffer = [
            0x01, 0x00, 0x00, 0x00, 0xaa, //
            0x00, 0x00, 0x00, 0x00, //
            0x01, 0x00, 0x00, 0x00, 0xbb, //
            0x07, 0x00, 0x00, 0x00, 0xbb, // not enough data
        ];
        let mut data = &buffer[..];
        assert_eq!(vec![0xaa], consume_vec(&mut data).unwrap());
        assert_eq!(Vec::<u8>::new(), consume_vec(&mut data).unwrap());
        assert_eq!(vec![0xbb], consume_vec(&mut data).unwrap());
        let result = consume_vec(&mut data);
        expect_err!(result, "failed to find 7 bytes");

        let buffer = [
            0x01, 0x00, 0x00, //
        ];
        let mut data = &buffer[..];
        let result = consume_vec(&mut data);
        expect_err!(result, "failed to find 4 bytes");
    }

    #[test]
    fn test_key_new_from_serialized() {
        let hidden = hidden_params(&[], &[SOFTWARE_ROOT_OF_TRUST]);
        // Test data originally generated by instrumenting Cuttlefish C++ KeyMint while running VTS
        // tests.
        let tests = [
            (
                concat!(
                    "0010000000d43c2f04f948521b81bdbf001310f5920000000000000000000000",
                    "00000000000c0000006400000002000010200000000300003080000000010000",
                    "2000000000010000200100000004000020020000000600002001000000be0200",
                    "1000000000c1020030b0ad0100c20200307b150300bd020060a8bb52407b0100",
                    "00ce02003011643401cf020030000000003b06b13ae6ae6671",
                ),
                KeyBlob {
                    key_material: hex::decode("d43c2f04f948521b81bdbf001310f592").unwrap(),
                    hw_enforced: vec![],
                    sw_enforced: vec![
                        KeyParameter { tag: Tag::ALGORITHM, value: KPV::Algorithm(Algorithm::AES) },
                        KeyParameter { tag: Tag::KEY_SIZE, value: KPV::Integer(128) },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::ENCRYPT),
                        },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::DECRYPT),
                        },
                        KeyParameter {
                            tag: Tag::BLOCK_MODE,
                            value: KPV::BlockMode(BlockMode::CBC),
                        },
                        KeyParameter {
                            tag: Tag::PADDING,
                            value: KPV::PaddingMode(PaddingMode::NONE),
                        },
                        KeyParameter { tag: Tag::ORIGIN, value: KPV::Origin(KeyOrigin::GENERATED) },
                        KeyParameter { tag: Tag::OS_VERSION, value: KPV::Integer(110000) },
                        KeyParameter { tag: Tag::OS_PATCHLEVEL, value: KPV::Integer(202107) },
                        KeyParameter {
                            tag: Tag::CREATION_DATETIME,
                            value: KPV::DateTime(1628871769000),
                        },
                        KeyParameter { tag: Tag::VENDOR_PATCHLEVEL, value: KPV::Integer(20210705) },
                        KeyParameter { tag: Tag::BOOT_PATCHLEVEL, value: KPV::Integer(0) },
                    ],
                },
                Some(KeyFormat::RAW),
            ),
            (
                concat!(
                    "00df0000003081dc020101044200b6ce876b947e263d61b8e3998d50dc0afb6b",
                    "a14e46ab7ca532fbe2a379b155d0a5bb99265402857b1601fb20be6c244bf654",
                    "e9e79413cd503eae3d9cf68ed24f47a00706052b81040023a181890381860004",
                    "006b840f0db0b12f074ab916c7773cfa7d42967c9e5b4fae09cf999f7e116d14",
                    "0743bdd028db0a3fcc670e721b9f00bc7fb70aa401c7d6de6582fc26962a29b7",
                    "45e30142e90685646661550344113aaf28bdee6cb02d19df1faab4398556a909",
                    "7d6f64b95209601a549389a311231c6cce78354f2cdbc3a904abf70686f5f0c3",
                    "b877984d000000000000000000000000000000000c0000006400000002000010",
                    "030000000a000010030000000100002002000000010000200300000005000020",
                    "000000000300003009020000be02001000000000c1020030b0ad0100c2020030",
                    "7b150300bd02006018d352407b010000ce02003011643401cf02003000000000",
                    "2f69002e55e9b0a3"
                ),
                KeyBlob {
                    key_material: hex::decode(concat!(
                        "3081dc020101044200b6ce876b947e263d61b8e3998d50dc0afb6ba14e46ab7c",
                        "a532fbe2a379b155d0a5bb99265402857b1601fb20be6c244bf654e9e79413cd",
                        "503eae3d9cf68ed24f47a00706052b81040023a181890381860004006b840f0d",
                        "b0b12f074ab916c7773cfa7d42967c9e5b4fae09cf999f7e116d140743bdd028",
                        "db0a3fcc670e721b9f00bc7fb70aa401c7d6de6582fc26962a29b745e30142e9",
                        "0685646661550344113aaf28bdee6cb02d19df1faab4398556a9097d6f64b952",
                        "09601a549389a311231c6cce78354f2cdbc3a904abf70686f5f0c3b877984d",
                    ))
                    .unwrap(),
                    hw_enforced: vec![],
                    sw_enforced: vec![
                        KeyParameter { tag: Tag::ALGORITHM, value: KPV::Algorithm(Algorithm::EC) },
                        KeyParameter { tag: Tag::EC_CURVE, value: KPV::EcCurve(EcCurve::P_521) },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::SIGN),
                        },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::VERIFY),
                        },
                        KeyParameter { tag: Tag::DIGEST, value: KPV::Digest(Digest::NONE) },
                        KeyParameter { tag: Tag::KEY_SIZE, value: KPV::Integer(521) },
                        KeyParameter { tag: Tag::ORIGIN, value: KPV::Origin(KeyOrigin::GENERATED) },
                        KeyParameter { tag: Tag::OS_VERSION, value: KPV::Integer(110000) },
                        KeyParameter { tag: Tag::OS_PATCHLEVEL, value: KPV::Integer(202107) },
                        KeyParameter {
                            tag: Tag::CREATION_DATETIME,
                            value: KPV::DateTime(1628871775000),
                        },
                        KeyParameter { tag: Tag::VENDOR_PATCHLEVEL, value: KPV::Integer(20210705) },
                        KeyParameter { tag: Tag::BOOT_PATCHLEVEL, value: KPV::Integer(0) },
                    ],
                },
                Some(KeyFormat::PKCS8),
            ),
            (
                concat!(
                    "0037000000541d4c440223650d5f51753c1abd80c725034485551e874d62327c",
                    "65f6247a057f1218bd6c8cd7d319103ddb823fc11fb6c2c7268b5acc00000000",
                    "0000000000000000000000000c00000064000000020000108000000003000030",
                    "b801000001000020020000000100002003000000050000200400000008000030",
                    "00010000be02001000000000c1020030b0ad0100c20200307b150300bd020060",
                    "00d752407b010000ce02003011643401cf0200300000000036e6986ffc45fbb0",
                ),
                KeyBlob {
                    key_material: hex::decode(concat!(
                        "541d4c440223650d5f51753c1abd80c725034485551e874d62327c65f6247a05",
                        "7f1218bd6c8cd7d319103ddb823fc11fb6c2c7268b5acc"
                    ))
                    .unwrap(),
                    hw_enforced: vec![],
                    sw_enforced: vec![
                        KeyParameter {
                            tag: Tag::ALGORITHM,
                            value: KPV::Algorithm(Algorithm::HMAC),
                        },
                        KeyParameter { tag: Tag::KEY_SIZE, value: KPV::Integer(440) },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::SIGN),
                        },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::VERIFY),
                        },
                        KeyParameter { tag: Tag::DIGEST, value: KPV::Digest(Digest::SHA_2_256) },
                        KeyParameter { tag: Tag::MIN_MAC_LENGTH, value: KPV::Integer(256) },
                        KeyParameter { tag: Tag::ORIGIN, value: KPV::Origin(KeyOrigin::GENERATED) },
                        KeyParameter { tag: Tag::OS_VERSION, value: KPV::Integer(110000) },
                        KeyParameter { tag: Tag::OS_PATCHLEVEL, value: KPV::Integer(202107) },
                        KeyParameter {
                            tag: Tag::CREATION_DATETIME,
                            value: KPV::DateTime(1628871776000),
                        },
                        KeyParameter { tag: Tag::VENDOR_PATCHLEVEL, value: KPV::Integer(20210705) },
                        KeyParameter { tag: Tag::BOOT_PATCHLEVEL, value: KPV::Integer(0) },
                    ],
                },
                Some(KeyFormat::RAW),
            ),
            (
                concat!(
                    "00a8040000308204a40201000282010100bc47b5c71116766669b91fa747df87",
                    "a1963df83956569d4ac232aeba8a246c0ec73bf606374a6d07f30c2162f97082",
                    "825c7c6e482a2841dfeaec1429d84e52c54a6b2f760dec952c9c44a3c3a80f31",
                    "c1ced84878edd4858059071c4d20d9ab0aae978bd68c1eb448e174a9736c3973",
                    "6838151642eda8215107375865a99a57f29467c74c40f37b0221b93ec3f4f22d",
                    "5337c8bf9245d56936196a92b1dea315ecce8785f9fa9b7d159ca207612cc0de",
                    "b0957d61dbba5d9bd38784f4fecbf233b04e686a340528665ecd03db8e8a09b2",
                    "540c84e45c4a99fb338b76bba7722856b5113341c349708937228f167d238ed8",
                    "efb9cc19547dd620f6a90d95f07e50bfe102030100010282010002f91b69d9af",
                    "59fe87421af9ba60f15c77f9c1c90effd6634332876f8ee5a116b126f55d3703",
                    "8bf9f588ae20c8d951d842e35c9ef35a7822d3ebf72c0b7c3e229b289ae2e178",
                    "a848e06d558c2e03d26871ee98a35f370d461ff1c4acc39d684de680a25ec88e",
                    "e610260e406c400bdeb2893b2d0330cb483e662fa5abd24c2b82143e85dfe30a",
                    "e7a31f8262da2903d882b35a34a26b699ff2d812bad4b126a0065ec0e101d73a",
                    "e6f8b29a9144eb83f54940a371fc7416c2c0370df6a41cb5391f17ba33239e1b",
                    "4217c8db50db5c6bf77ccf621354ecc652a4f7196054c254566fd7b3bc0f3817",
                    "d9380b190bd382aaffa37785759f285194c11a188bccde0e2e2902818100fb23",
                    "3335770c9f3cbd4b6ede5f12d03c449b1997bce06a8249bc3de99972fd0d0a63",
                    "3f7790d1011bf5eedee16fa45a9107a910656ecaee364ce9edb4369843be71f2",
                    "7a74852d6c7215a6cc60d9803bcac544922f806d8e5844e0ddd914bd78009490",
                    "4c2856d2b944fade3fb1d67d4a33fb7663a9ab660ab372c2e4868a0f45990281",
                    "8100bfecf2bb4012e880fd065a0b088f2d757af2878d3f1305f21ce7a7158458",
                    "18e01181ff06b2f406239fc50808ce3dbe7b68ec01174913c0f237feb3c8c7eb",
                    "0078b77fb5b8f214b72f6d3835b1a7ebe8b132feb6cb34ab09ce22b98160fc84",
                    "20fcbf48d1eee49f874e902f049b206a61a095f0405a4935e7c5e49757ab7b57",
                    "298902818100ec0049383e16f3716de5fc5b2677148efe5dceb02483b43399bd",
                    "3765559994a9f3900eed7a7e9e8f3b0eee0e660eca392e3cb736cae612f39e55",
                    "dad696d3821def10d1f8bbca52f5e6d8e7893ffbdcb491aafdc17bebf86f84d2",
                    "d8480ed07a7bf9209d20ef6e79429489d4cb7768281a2f7e32ec1830fd6f6332",
                    "38f521ba764902818100b2c3ce5751580b4e51df3fb175387f5c24b79040a4d6",
                    "603c6265f70018b441ff3aef7d8e4cd2f480ec0906f1c4c0481304e8861f9d46",
                    "93fa48e3a9abc362859eeb343e1c5507ac94b5439ce7ac04154a2fb886a4819b",
                    "2a57e18a2e131b412ac4a09b004766959cdf357745f003e272aab3de02e2d5bc",
                    "2af4ed75760858ab181902818061d19c2a8dcacde104b97f7c4fae11216157c1",
                    "c0a258d882984d12383a73dc56fe2ac93512bb321df9706ecdb2f70a44c949c4",
                    "340a9fae64a0646cf51f37c58c08bebde91667b3b2fa7c895f7983d4786c5526",
                    "1941b3654533b0598383ebbcffcdf28b6cf13d376e3a70b49b14d8d06e8563a2",
                    "47f56a337e3b9845b4f2b61356000000000000000000000000000000000d0000",
                    "007000000002000010010000000300003000080000c800005001000100000000",
                    "0001000020020000000100002003000000050000200000000006000020010000",
                    "00be02001000000000c1020030b0ad0100c20200307b150300bd020060a8bb52",
                    "407b010000ce02003011643401cf02003000000000544862e9c961e857",
                ),
                KeyBlob {
                    key_material: hex::decode(concat!(
                        "308204a40201000282010100bc47b5c71116766669b91fa747df87a1963df839",
                        "56569d4ac232aeba8a246c0ec73bf606374a6d07f30c2162f97082825c7c6e48",
                        "2a2841dfeaec1429d84e52c54a6b2f760dec952c9c44a3c3a80f31c1ced84878",
                        "edd4858059071c4d20d9ab0aae978bd68c1eb448e174a9736c39736838151642",
                        "eda8215107375865a99a57f29467c74c40f37b0221b93ec3f4f22d5337c8bf92",
                        "45d56936196a92b1dea315ecce8785f9fa9b7d159ca207612cc0deb0957d61db",
                        "ba5d9bd38784f4fecbf233b04e686a340528665ecd03db8e8a09b2540c84e45c",
                        "4a99fb338b76bba7722856b5113341c349708937228f167d238ed8efb9cc1954",
                        "7dd620f6a90d95f07e50bfe102030100010282010002f91b69d9af59fe87421a",
                        "f9ba60f15c77f9c1c90effd6634332876f8ee5a116b126f55d37038bf9f588ae",
                        "20c8d951d842e35c9ef35a7822d3ebf72c0b7c3e229b289ae2e178a848e06d55",
                        "8c2e03d26871ee98a35f370d461ff1c4acc39d684de680a25ec88ee610260e40",
                        "6c400bdeb2893b2d0330cb483e662fa5abd24c2b82143e85dfe30ae7a31f8262",
                        "da2903d882b35a34a26b699ff2d812bad4b126a0065ec0e101d73ae6f8b29a91",
                        "44eb83f54940a371fc7416c2c0370df6a41cb5391f17ba33239e1b4217c8db50",
                        "db5c6bf77ccf621354ecc652a4f7196054c254566fd7b3bc0f3817d9380b190b",
                        "d382aaffa37785759f285194c11a188bccde0e2e2902818100fb233335770c9f",
                        "3cbd4b6ede5f12d03c449b1997bce06a8249bc3de99972fd0d0a633f7790d101",
                        "1bf5eedee16fa45a9107a910656ecaee364ce9edb4369843be71f27a74852d6c",
                        "7215a6cc60d9803bcac544922f806d8e5844e0ddd914bd780094904c2856d2b9",
                        "44fade3fb1d67d4a33fb7663a9ab660ab372c2e4868a0f459902818100bfecf2",
                        "bb4012e880fd065a0b088f2d757af2878d3f1305f21ce7a715845818e01181ff",
                        "06b2f406239fc50808ce3dbe7b68ec01174913c0f237feb3c8c7eb0078b77fb5",
                        "b8f214b72f6d3835b1a7ebe8b132feb6cb34ab09ce22b98160fc8420fcbf48d1",
                        "eee49f874e902f049b206a61a095f0405a4935e7c5e49757ab7b572989028181",
                        "00ec0049383e16f3716de5fc5b2677148efe5dceb02483b43399bd3765559994",
                        "a9f3900eed7a7e9e8f3b0eee0e660eca392e3cb736cae612f39e55dad696d382",
                        "1def10d1f8bbca52f5e6d8e7893ffbdcb491aafdc17bebf86f84d2d8480ed07a",
                        "7bf9209d20ef6e79429489d4cb7768281a2f7e32ec1830fd6f633238f521ba76",
                        "4902818100b2c3ce5751580b4e51df3fb175387f5c24b79040a4d6603c6265f7",
                        "0018b441ff3aef7d8e4cd2f480ec0906f1c4c0481304e8861f9d4693fa48e3a9",
                        "abc362859eeb343e1c5507ac94b5439ce7ac04154a2fb886a4819b2a57e18a2e",
                        "131b412ac4a09b004766959cdf357745f003e272aab3de02e2d5bc2af4ed7576",
                        "0858ab181902818061d19c2a8dcacde104b97f7c4fae11216157c1c0a258d882",
                        "984d12383a73dc56fe2ac93512bb321df9706ecdb2f70a44c949c4340a9fae64",
                        "a0646cf51f37c58c08bebde91667b3b2fa7c895f7983d4786c55261941b36545",
                        "33b0598383ebbcffcdf28b6cf13d376e3a70b49b14d8d06e8563a247f56a337e",
                        "3b9845b4f2b61356",
                    ))
                    .unwrap(),
                    hw_enforced: vec![],
                    sw_enforced: vec![
                        KeyParameter { tag: Tag::ALGORITHM, value: KPV::Algorithm(Algorithm::RSA) },
                        KeyParameter { tag: Tag::KEY_SIZE, value: KPV::Integer(2048) },
                        KeyParameter {
                            tag: Tag::RSA_PUBLIC_EXPONENT,
                            value: KPV::LongInteger(65537),
                        },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::SIGN),
                        },
                        KeyParameter {
                            tag: Tag::PURPOSE,
                            value: KPV::KeyPurpose(KeyPurpose::VERIFY),
                        },
                        KeyParameter { tag: Tag::DIGEST, value: KPV::Digest(Digest::NONE) },
                        KeyParameter {
                            tag: Tag::PADDING,
                            value: KPV::PaddingMode(PaddingMode::NONE),
                        },
                        KeyParameter { tag: Tag::ORIGIN, value: KPV::Origin(KeyOrigin::GENERATED) },
                        KeyParameter { tag: Tag::OS_VERSION, value: KPV::Integer(110000) },
                        KeyParameter { tag: Tag::OS_PATCHLEVEL, value: KPV::Integer(202107) },
                        KeyParameter {
                            tag: Tag::CREATION_DATETIME,
                            value: KPV::DateTime(1628871769000),
                        },
                        KeyParameter { tag: Tag::VENDOR_PATCHLEVEL, value: KPV::Integer(20210705) },
                        KeyParameter { tag: Tag::BOOT_PATCHLEVEL, value: KPV::Integer(0) },
                    ],
                },
                // No support for RSA keys in export_key().
                None,
            ),
        ];

        for (input, want, want_format) in tests {
            let input = hex::decode(input).unwrap();
            let got = KeyBlob::new_from_serialized(&input, &hidden).expect("invalid keyblob!");
            assert!(got == want);

            if let Some(want_format) = want_format {
                let (got_format, _key_material, params) =
                    export_key(&input, &[]).expect("invalid keyblob!");
                assert_eq!(got_format, want_format);
                // All the test cases are software-only keys.
                assert_eq!(params, got.sw_enforced);
            }
        }
    }

    #[test]
    fn test_add_der_len() {
        let tests = [
            (0, "00"),
            (1, "01"),
            (126, "7e"),
            (127, "7f"),
            (128, "8180"),
            (129, "8181"),
            (255, "81ff"),
            (256, "820100"),
            (257, "820101"),
            (65535, "82ffff"),
        ];
        for (input, want) in tests {
            let mut got = Vec::new();
            add_der_len(&mut got, input).unwrap();
            assert_eq!(hex::encode(got), want, " for input length {input}");
        }
    }

    #[test]
    fn test_pkcs8_wrap_key_p256() {
        // Key material taken from `ec_256_key` in
        // hardware/interfaces/security/keymint/aidl/vts/function/KeyMintTest.cpp
        let input = hex::decode(concat!(
            "3025",   // SEQUENCE (ECPrivateKey)
            "020101", // INTEGER length 1 value 1 (version)
            "0420",   // OCTET STRING (privateKey)
            "737c2ecd7b8d1940bf2930aa9b4ed3ff",
            "941eed09366bc03299986481f3a4d859",
        ))
        .unwrap();
        let want = hex::decode(concat!(
            // RFC 5208 s5
            "3041",             // SEQUENCE (PrivateKeyInfo) {
            "020100",           // INTEGER length 1 value 0 (version)
            "3013",             // SEQUENCE length 0x13 (AlgorithmIdentifier) {
            "0607",             // OBJECT IDENTIFIER length 7 (algorithm)
            "2a8648ce3d0201",   // 1.2.840.10045.2.1 (ecPublicKey)
            "0608",             // OBJECT IDENTIFIER length 8 (param)
            "2a8648ce3d030107", //  1.2.840.10045.3.1.7 (secp256r1)
            // } end SEQUENCE (AlgorithmIdentifier)
            "0427",   // OCTET STRING (privateKey) holding...
            "3025",   // SEQUENCE (ECPrivateKey)
            "020101", // INTEGER length 1 value 1 (version)
            "0420",   // OCTET STRING length 0x20 (privateKey)
            "737c2ecd7b8d1940bf2930aa9b4ed3ff",
            "941eed09366bc03299986481f3a4d859",
            // } end SEQUENCE (ECPrivateKey)
            // } end SEQUENCE (PrivateKeyInfo)
        ))
        .unwrap();
        let got = pkcs8_wrap_nist_key(&input, EcCurve::P_256).unwrap();
        assert_eq!(hex::encode(got), hex::encode(want), " for input {}", hex::encode(input));
    }

    #[test]
    fn test_pkcs8_wrap_key_p521() {
        // Key material taken from `ec_521_key` in
        // hardware/interfaces/security/keymint/aidl/vts/function/KeyMintTest.cpp
        let input = hex::decode(concat!(
            "3047",   // SEQUENCE length 0xd3 (ECPrivateKey)
            "020101", // INTEGER length 1 value 1 (version)
            "0442",   // OCTET STRING length 0x42 (privateKey)
            "0011458c586db5daa92afab03f4fe46a",
            "a9d9c3ce9a9b7a006a8384bec4c78e8e",
            "9d18d7d08b5bcfa0e53c75b064ad51c4",
            "49bae0258d54b94b1e885ded08ed4fb2",
            "5ce9",
            // } end SEQUENCE (ECPrivateKey)
        ))
        .unwrap();
        let want = hex::decode(concat!(
            // RFC 5208 s5
            "3060",           // SEQUENCE (PrivateKeyInfo) {
            "020100",         // INTEGER length 1 value 0 (version)
            "3010",           // SEQUENCE length 0x10 (AlgorithmIdentifier) {
            "0607",           // OBJECT IDENTIFIER length 7 (algorithm)
            "2a8648ce3d0201", // 1.2.840.10045.2.1 (ecPublicKey)
            "0605",           // OBJECT IDENTIFIER length 5 (param)
            "2b81040023",     //  1.3.132.0.35 (secp521r1)
            // } end SEQUENCE (AlgorithmIdentifier)
            "0449",   // OCTET STRING (privateKey) holding...
            "3047",   // SEQUENCE (ECPrivateKey)
            "020101", // INTEGER length 1 value 1 (version)
            "0442",   // OCTET STRING length 0x42 (privateKey)
            "0011458c586db5daa92afab03f4fe46a",
            "a9d9c3ce9a9b7a006a8384bec4c78e8e",
            "9d18d7d08b5bcfa0e53c75b064ad51c4",
            "49bae0258d54b94b1e885ded08ed4fb2",
            "5ce9",
            // } end SEQUENCE (ECPrivateKey)
            // } end SEQUENCE (PrivateKeyInfo)
        ))
        .unwrap();
        let got = pkcs8_wrap_nist_key(&input, EcCurve::P_521).unwrap();
        assert_eq!(hex::encode(got), hex::encode(want), " for input {}", hex::encode(input));
    }
}
