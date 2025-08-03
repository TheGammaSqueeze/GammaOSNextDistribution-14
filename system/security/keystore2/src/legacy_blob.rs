// Copyright 2020, The Android Open Source Project
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

//! This module implements methods to load legacy keystore key blob files.

use crate::ks_err;
use crate::{
    error::{Error as KsError, ResponseCode},
    key_parameter::{KeyParameter, KeyParameterValue},
    utils::uid_to_android_user,
    utils::AesGcm,
};
use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    SecurityLevel::SecurityLevel, Tag::Tag, TagType::TagType,
};
use anyhow::{Context, Result};
use keystore2_crypto::{aes_gcm_decrypt, Password, ZVec};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::{convert::TryInto, fs::File, path::Path, path::PathBuf};
use std::{
    fs,
    io::{ErrorKind, Read, Result as IoResult},
};

const SUPPORTED_LEGACY_BLOB_VERSION: u8 = 3;

mod flags {
    /// This flag is deprecated. It is here to support keys that have been written with this flag
    /// set, but we don't create any new keys with this flag.
    pub const ENCRYPTED: u8 = 1 << 0;
    /// This flag is deprecated. It indicates that the blob was generated and thus owned by a
    /// software fallback Keymaster implementation. Keymaster 1.0 was the last Keymaster version
    /// that could be accompanied by a software fallback. With the removal of Keymaster 1.0
    /// support, this flag is obsolete.
    pub const FALLBACK: u8 = 1 << 1;
    /// KEYSTORE_FLAG_SUPER_ENCRYPTED is for blobs that are already encrypted by KM but have
    /// an additional layer of password-based encryption applied. The same encryption scheme is used
    /// as KEYSTORE_FLAG_ENCRYPTED. The latter is deprecated.
    pub const SUPER_ENCRYPTED: u8 = 1 << 2;
    /// KEYSTORE_FLAG_CRITICAL_TO_DEVICE_ENCRYPTION is for blobs that are part of device encryption
    /// flow so it receives special treatment from keystore. For example this blob will not be super
    /// encrypted, and it will be stored separately under a unique UID instead. This flag should
    /// only be available to system uid.
    pub const CRITICAL_TO_DEVICE_ENCRYPTION: u8 = 1 << 3;
    /// The blob is associated with the security level Strongbox as opposed to TEE.
    pub const STRONGBOX: u8 = 1 << 4;
}

/// Lagacy key blob types.
mod blob_types {
    /// A generic blob used for non sensitive unstructured blobs.
    pub const GENERIC: u8 = 1;
    /// This key is a super encryption key encrypted with AES128
    /// and a password derived key.
    pub const SUPER_KEY: u8 = 2;
    // Used to be the KEY_PAIR type.
    const _RESERVED: u8 = 3;
    /// A KM key blob.
    pub const KM_BLOB: u8 = 4;
    /// A legacy key characteristics file. This has only a single list of Authorizations.
    pub const KEY_CHARACTERISTICS: u8 = 5;
    /// A key characteristics cache has both a hardware enforced and a software enforced list
    /// of authorizations.
    pub const KEY_CHARACTERISTICS_CACHE: u8 = 6;
    /// Like SUPER_KEY but encrypted with AES256.
    pub const SUPER_KEY_AES256: u8 = 7;
}

/// Error codes specific to the legacy blob module.
#[derive(thiserror::Error, Debug, Eq, PartialEq)]
pub enum Error {
    /// Returned by the legacy blob module functions if an input stream
    /// did not have enough bytes to read.
    #[error("Input stream had insufficient bytes to read.")]
    BadLen,
    /// This error code is returned by `Blob::decode_alias` if it encounters
    /// an invalid alias filename encoding.
    #[error("Invalid alias filename encoding.")]
    BadEncoding,
    /// A component of the requested entry other than the KM key blob itself
    /// was encrypted and no super key was provided.
    #[error("Locked entry component.")]
    LockedComponent,
    /// The uids presented to move_keystore_entry belonged to different
    /// Android users.
    #[error("Cannot move keys across Android users.")]
    AndroidUserMismatch,
}

/// The blob payload, optionally with all information required to decrypt it.
#[derive(Debug, Eq, PartialEq)]
pub enum BlobValue {
    /// A generic blob used for non sensitive unstructured blobs.
    Generic(Vec<u8>),
    /// A legacy key characteristics file. This has only a single list of Authorizations.
    Characteristics(Vec<u8>),
    /// A legacy key characteristics file. This has only a single list of Authorizations.
    /// Additionally, this characteristics file was encrypted with the user's super key.
    EncryptedCharacteristics {
        /// Initialization vector.
        iv: Vec<u8>,
        /// Aead tag for integrity verification.
        tag: Vec<u8>,
        /// Ciphertext.
        data: Vec<u8>,
    },
    /// A key characteristics cache has both a hardware enforced and a software enforced list
    /// of authorizations.
    CharacteristicsCache(Vec<u8>),
    /// A password encrypted blob. Includes the initialization vector, the aead tag, the
    /// ciphertext data, a salt, and a key size. The latter two are used for key derivation.
    PwEncrypted {
        /// Initialization vector.
        iv: Vec<u8>,
        /// Aead tag for integrity verification.
        tag: Vec<u8>,
        /// Ciphertext.
        data: Vec<u8>,
        /// Salt for key derivation.
        salt: Vec<u8>,
        /// Key sise for key derivation. This selects between AES128 GCM and AES256 GCM.
        key_size: usize,
    },
    /// An encrypted blob. Includes the initialization vector, the aead tag, and the
    /// ciphertext data. The key can be selected from context, i.e., the owner of the key
    /// blob.
    Encrypted {
        /// Initialization vector.
        iv: Vec<u8>,
        /// Aead tag for integrity verification.
        tag: Vec<u8>,
        /// Ciphertext.
        data: Vec<u8>,
    },
    /// An encrypted blob. Includes the initialization vector, the aead tag, and the
    /// ciphertext data. The key can be selected from context, i.e., the owner of the key
    /// blob. This is a special case for generic encrypted blobs as opposed to key blobs.
    EncryptedGeneric {
        /// Initialization vector.
        iv: Vec<u8>,
        /// Aead tag for integrity verification.
        tag: Vec<u8>,
        /// Ciphertext.
        data: Vec<u8>,
    },
    /// Holds the plaintext key blob either after unwrapping an encrypted blob or when the
    /// blob was stored in "plaintext" on disk. The "plaintext" of a key blob is not actual
    /// plaintext because all KeyMint blobs are encrypted with a device bound key. The key
    /// blob in this Variant is decrypted only with respect to any extra layer of encryption
    /// that Keystore added.
    Decrypted(ZVec),
}

/// Keystore used two different key characteristics file formats in the past.
/// The key characteristics cache which superseded the characteristics file.
/// The latter stored only one list of key parameters, while the former stored
/// a hardware enforced and a software enforced list. This Enum indicates which
/// type was read from the file system.
#[derive(Debug, Clone, Eq, PartialEq, Ord, PartialOrd)]
pub enum LegacyKeyCharacteristics {
    /// A characteristics cache was read.
    Cache(Vec<KeyParameter>),
    /// A characteristics file was read.
    File(Vec<KeyParameter>),
}

/// Represents a loaded legacy key blob file.
#[derive(Debug, Eq, PartialEq)]
pub struct Blob {
    flags: u8,
    value: BlobValue,
}

/// This object represents a path that holds a legacy Keystore blob database.
pub struct LegacyBlobLoader {
    path: PathBuf,
}

fn read_bool(stream: &mut dyn Read) -> Result<bool> {
    const SIZE: usize = std::mem::size_of::<bool>();
    let mut buffer: [u8; SIZE] = [0; SIZE];
    stream.read_exact(&mut buffer).map(|_| buffer[0] != 0).context("In read_ne_bool.")
}

fn read_ne_u32(stream: &mut dyn Read) -> Result<u32> {
    const SIZE: usize = std::mem::size_of::<u32>();
    let mut buffer: [u8; SIZE] = [0; SIZE];
    stream.read_exact(&mut buffer).map(|_| u32::from_ne_bytes(buffer)).context("In read_ne_u32.")
}

fn read_ne_i32(stream: &mut dyn Read) -> Result<i32> {
    const SIZE: usize = std::mem::size_of::<i32>();
    let mut buffer: [u8; SIZE] = [0; SIZE];
    stream.read_exact(&mut buffer).map(|_| i32::from_ne_bytes(buffer)).context("In read_ne_i32.")
}

fn read_ne_i64(stream: &mut dyn Read) -> Result<i64> {
    const SIZE: usize = std::mem::size_of::<i64>();
    let mut buffer: [u8; SIZE] = [0; SIZE];
    stream.read_exact(&mut buffer).map(|_| i64::from_ne_bytes(buffer)).context("In read_ne_i64.")
}

impl Blob {
    /// Creates a new blob from flags and value.
    pub fn new(flags: u8, value: BlobValue) -> Self {
        Self { flags, value }
    }

    /// Return the raw flags of this Blob.
    pub fn get_flags(&self) -> u8 {
        self.flags
    }

    /// This blob was generated with a fallback software KM device.
    pub fn is_fallback(&self) -> bool {
        self.flags & flags::FALLBACK != 0
    }

    /// This blob is encrypted and needs to be decrypted with the user specific master key
    /// before use.
    pub fn is_encrypted(&self) -> bool {
        self.flags & (flags::SUPER_ENCRYPTED | flags::ENCRYPTED) != 0
    }

    /// This blob is critical to device encryption. It cannot be encrypted with the super key
    /// because it is itself part of the key derivation process for the key encrypting the
    /// super key.
    pub fn is_critical_to_device_encryption(&self) -> bool {
        self.flags & flags::CRITICAL_TO_DEVICE_ENCRYPTION != 0
    }

    /// This blob is associated with the Strongbox security level.
    pub fn is_strongbox(&self) -> bool {
        self.flags & flags::STRONGBOX != 0
    }

    /// Returns the payload data of this blob file.
    pub fn value(&self) -> &BlobValue {
        &self.value
    }

    /// Consume this blob structure and extract the payload.
    pub fn take_value(self) -> BlobValue {
        self.value
    }
}

impl LegacyBlobLoader {
    const IV_SIZE: usize = keystore2_crypto::LEGACY_IV_LENGTH;
    const GCM_TAG_LENGTH: usize = keystore2_crypto::TAG_LENGTH;
    const SALT_SIZE: usize = keystore2_crypto::SALT_LENGTH;

    // The common header has the following structure:
    // version (1 Byte)
    // blob_type (1 Byte)
    // flags (1 Byte)
    // info (1 Byte) Size of an info field appended to the blob.
    // initialization_vector (16 Bytes)
    // integrity (MD5 digest or gcm tag) (16 Bytes)
    // length (4 Bytes)
    //
    // The info field is used to store the salt for password encrypted blobs.
    // The beginning of the info field can be computed from the file length
    // and the info byte from the header: <file length> - <info> bytes.
    const COMMON_HEADER_SIZE: usize = 4 + Self::IV_SIZE + Self::GCM_TAG_LENGTH + 4;

    const VERSION_OFFSET: usize = 0;
    const TYPE_OFFSET: usize = 1;
    const FLAGS_OFFSET: usize = 2;
    const SALT_SIZE_OFFSET: usize = 3;
    const LENGTH_OFFSET: usize = 4 + Self::IV_SIZE + Self::GCM_TAG_LENGTH;
    const IV_OFFSET: usize = 4;
    const AEAD_TAG_OFFSET: usize = Self::IV_OFFSET + Self::IV_SIZE;
    const _DIGEST_OFFSET: usize = Self::IV_OFFSET + Self::IV_SIZE;

    /// Construct a new LegacyBlobLoader with a root path of `path` relative to which it will
    /// expect legacy key blob files.
    pub fn new(path: &Path) -> Self {
        Self { path: path.to_owned() }
    }

    /// Encodes an alias string as ascii character sequence in the range
    /// ['+' .. '.'] and ['0' .. '~'].
    /// Bytes with values in the range ['0' .. '~'] are represented as they are.
    /// All other bytes are split into two characters as follows:
    ///
    ///      msb a a | b b b b b b
    ///
    /// The most significant bits (a) are encoded:
    ///   a a  character
    ///   0 0     '+'
    ///   0 1     ','
    ///   1 0     '-'
    ///   1 1     '.'
    ///
    /// The 6 lower bits are represented with the range ['0' .. 'o']:
    ///   b(hex)  character
    ///   0x00     '0'
    ///       ...
    ///   0x3F     'o'
    ///
    /// The function cannot fail because we have a representation for each
    /// of the 256 possible values of each byte.
    pub fn encode_alias(name: &str) -> String {
        let mut acc = String::new();
        for c in name.bytes() {
            match c {
                b'0'..=b'~' => {
                    acc.push(c as char);
                }
                c => {
                    acc.push((b'+' + (c >> 6)) as char);
                    acc.push((b'0' + (c & 0x3F)) as char);
                }
            };
        }
        acc
    }

    /// This function reverses the encoding described in `encode_alias`.
    /// This function can fail, because not all possible character
    /// sequences are valid code points. And even if the encoding is valid,
    /// the result may not be a valid UTF-8 sequence.
    pub fn decode_alias(name: &str) -> Result<String> {
        let mut multi: Option<u8> = None;
        let mut s = Vec::<u8>::new();
        for c in name.bytes() {
            multi = match (c, multi) {
                // m is set, we are processing the second part of a multi byte sequence
                (b'0'..=b'o', Some(m)) => {
                    s.push(m | (c - b'0'));
                    None
                }
                (b'+'..=b'.', None) => Some((c - b'+') << 6),
                (b'0'..=b'~', None) => {
                    s.push(c);
                    None
                }
                _ => {
                    return Err(Error::BadEncoding).context(ks_err!("could not decode filename."));
                }
            };
        }
        if multi.is_some() {
            return Err(Error::BadEncoding).context(ks_err!("could not decode filename."));
        }

        String::from_utf8(s).context(ks_err!("encoded alias was not valid UTF-8."))
    }

    fn new_from_stream(stream: &mut dyn Read) -> Result<Blob> {
        let mut buffer = Vec::new();
        stream.read_to_end(&mut buffer).context(ks_err!())?;

        if buffer.len() < Self::COMMON_HEADER_SIZE {
            return Err(Error::BadLen).context(ks_err!())?;
        }

        let version: u8 = buffer[Self::VERSION_OFFSET];

        let flags: u8 = buffer[Self::FLAGS_OFFSET];
        let blob_type: u8 = buffer[Self::TYPE_OFFSET];
        let is_encrypted = flags & (flags::ENCRYPTED | flags::SUPER_ENCRYPTED) != 0;
        let salt = match buffer[Self::SALT_SIZE_OFFSET] as usize {
            Self::SALT_SIZE => Some(&buffer[buffer.len() - Self::SALT_SIZE..buffer.len()]),
            _ => None,
        };

        if version != SUPPORTED_LEGACY_BLOB_VERSION {
            return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                .context(ks_err!("Unknown blob version: {}.", version));
        }

        let length = u32::from_be_bytes(
            buffer[Self::LENGTH_OFFSET..Self::LENGTH_OFFSET + 4].try_into().unwrap(),
        ) as usize;
        if buffer.len() < Self::COMMON_HEADER_SIZE + length {
            return Err(Error::BadLen).context(ks_err!(
                "Expected: {} got: {}.",
                Self::COMMON_HEADER_SIZE + length,
                buffer.len()
            ));
        }
        let value = &buffer[Self::COMMON_HEADER_SIZE..Self::COMMON_HEADER_SIZE + length];
        let iv = &buffer[Self::IV_OFFSET..Self::IV_OFFSET + Self::IV_SIZE];
        let tag = &buffer[Self::AEAD_TAG_OFFSET..Self::AEAD_TAG_OFFSET + Self::GCM_TAG_LENGTH];

        match (blob_type, is_encrypted, salt) {
            (blob_types::GENERIC, false, _) => {
                Ok(Blob { flags, value: BlobValue::Generic(value.to_vec()) })
            }
            (blob_types::GENERIC, true, _) => Ok(Blob {
                flags,
                value: BlobValue::EncryptedGeneric {
                    iv: iv.to_vec(),
                    tag: tag.to_vec(),
                    data: value.to_vec(),
                },
            }),
            (blob_types::KEY_CHARACTERISTICS, false, _) => {
                Ok(Blob { flags, value: BlobValue::Characteristics(value.to_vec()) })
            }
            (blob_types::KEY_CHARACTERISTICS, true, _) => Ok(Blob {
                flags,
                value: BlobValue::EncryptedCharacteristics {
                    iv: iv.to_vec(),
                    tag: tag.to_vec(),
                    data: value.to_vec(),
                },
            }),
            (blob_types::KEY_CHARACTERISTICS_CACHE, _, _) => {
                Ok(Blob { flags, value: BlobValue::CharacteristicsCache(value.to_vec()) })
            }
            (blob_types::SUPER_KEY, _, Some(salt)) => Ok(Blob {
                flags,
                value: BlobValue::PwEncrypted {
                    iv: iv.to_vec(),
                    tag: tag.to_vec(),
                    data: value.to_vec(),
                    key_size: keystore2_crypto::AES_128_KEY_LENGTH,
                    salt: salt.to_vec(),
                },
            }),
            (blob_types::SUPER_KEY_AES256, _, Some(salt)) => Ok(Blob {
                flags,
                value: BlobValue::PwEncrypted {
                    iv: iv.to_vec(),
                    tag: tag.to_vec(),
                    data: value.to_vec(),
                    key_size: keystore2_crypto::AES_256_KEY_LENGTH,
                    salt: salt.to_vec(),
                },
            }),
            (blob_types::KM_BLOB, true, _) => Ok(Blob {
                flags,
                value: BlobValue::Encrypted {
                    iv: iv.to_vec(),
                    tag: tag.to_vec(),
                    data: value.to_vec(),
                },
            }),
            (blob_types::KM_BLOB, false, _) => Ok(Blob {
                flags,
                value: BlobValue::Decrypted(value.try_into().context("In new_from_stream.")?),
            }),
            (blob_types::SUPER_KEY, _, None) | (blob_types::SUPER_KEY_AES256, _, None) => {
                Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                    .context(ks_err!("Super key without salt for key derivation."))
            }
            _ => Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED)).context(ks_err!(
                "Unknown blob type. {} {}",
                blob_type,
                is_encrypted
            )),
        }
    }

    /// Parses a legacy key blob file read from `stream`. A `decrypt` closure
    /// must be supplied, that is primed with the appropriate key.
    /// The callback takes the following arguments:
    ///  * ciphertext: &[u8] - The to-be-deciphered message.
    ///  * iv: &[u8] - The initialization vector.
    ///  * tag: Option<&[u8]> - AEAD tag if AES GCM is selected.
    ///  * salt: Option<&[u8]> - An optional salt. Used for password key derivation.
    ///  * key_size: Option<usize> - An optional key size. Used for pw key derivation.
    ///
    /// If no super key is available, the callback must return
    /// `Err(KsError::Rc(ResponseCode::LOCKED))`. The callback is only called
    /// if the to-be-read blob is encrypted.
    pub fn new_from_stream_decrypt_with<F>(mut stream: impl Read, decrypt: F) -> Result<Blob>
    where
        F: FnOnce(&[u8], &[u8], &[u8], Option<&[u8]>, Option<usize>) -> Result<ZVec>,
    {
        let blob = Self::new_from_stream(&mut stream).context(ks_err!())?;

        match blob.value() {
            BlobValue::Encrypted { iv, tag, data } => Ok(Blob {
                flags: blob.flags,
                value: BlobValue::Decrypted(decrypt(data, iv, tag, None, None).context(ks_err!())?),
            }),
            BlobValue::PwEncrypted { iv, tag, data, salt, key_size } => Ok(Blob {
                flags: blob.flags,
                value: BlobValue::Decrypted(
                    decrypt(data, iv, tag, Some(salt), Some(*key_size)).context(ks_err!())?,
                ),
            }),
            BlobValue::EncryptedGeneric { iv, tag, data } => Ok(Blob {
                flags: blob.flags,
                value: BlobValue::Generic(
                    decrypt(data, iv, tag, None, None).context(ks_err!())?[..].to_vec(),
                ),
            }),

            _ => Ok(blob),
        }
    }

    fn tag_type(tag: Tag) -> TagType {
        TagType((tag.0 as u32 & 0xFF000000u32) as i32)
    }

    /// Read legacy key parameter file content.
    /// Depending on the file type a key characteristics file stores one (TYPE_KEY_CHARACTERISTICS)
    /// or two (TYPE_KEY_CHARACTERISTICS_CACHE) key parameter lists. The format of the list is as
    /// follows:
    ///
    /// +------------------------------+
    /// | 32 bit indirect_size         |
    /// +------------------------------+
    /// | indirect_size bytes of data  |     This is where the blob data is stored
    /// +------------------------------+
    /// | 32 bit element_count         |     Number of key parameter entries.
    /// | 32 bit elements_size         |     Total bytes used by entries.
    /// +------------------------------+
    /// | elements_size bytes of data  |     This is where the elements are stored.
    /// +------------------------------+
    ///
    /// Elements have a 32 bit header holding the tag with a tag type encoded in the
    /// four most significant bits (see android/hardware/secruity/keymint/TagType.aidl).
    /// The header is immediately followed by the payload. The payload size depends on
    /// the encoded tag type in the header:
    ///      BOOLEAN                          :    1 byte
    ///      ENUM, ENUM_REP, UINT, UINT_REP   :    4 bytes
    ///      ULONG, ULONG_REP, DATETIME       :    8 bytes
    ///      BLOB, BIGNUM                     :    8 bytes see below.
    ///
    /// Bignum and blob payload format:
    /// +------------------------+
    /// | 32 bit blob_length     |    Length of the indirect payload in bytes.
    /// | 32 bit indirect_offset |    Offset from the beginning of the indirect section.
    /// +------------------------+
    pub fn read_key_parameters(stream: &mut &[u8]) -> Result<Vec<KeyParameterValue>> {
        let indirect_size = read_ne_u32(stream).context(ks_err!("While reading indirect size."))?;

        let indirect_buffer = stream
            .get(0..indirect_size as usize)
            .ok_or(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
            .context(ks_err!("While reading indirect buffer."))?;

        // update the stream position.
        *stream = &stream[indirect_size as usize..];

        let element_count = read_ne_u32(stream).context(ks_err!("While reading element count."))?;
        let element_size = read_ne_u32(stream).context(ks_err!("While reading element size."))?;

        let mut element_stream = stream
            .get(0..element_size as usize)
            .ok_or(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
            .context(ks_err!("While reading elements buffer."))?;

        // update the stream position.
        *stream = &stream[element_size as usize..];

        let mut params: Vec<KeyParameterValue> = Vec::new();
        for _ in 0..element_count {
            let tag = Tag(read_ne_i32(&mut element_stream).context(ks_err!())?);
            let param = match Self::tag_type(tag) {
                TagType::ENUM | TagType::ENUM_REP | TagType::UINT | TagType::UINT_REP => {
                    KeyParameterValue::new_from_tag_primitive_pair(
                        tag,
                        read_ne_i32(&mut element_stream).context("While reading integer.")?,
                    )
                    .context("Trying to construct integer/enum KeyParameterValue.")
                }
                TagType::ULONG | TagType::ULONG_REP | TagType::DATE => {
                    KeyParameterValue::new_from_tag_primitive_pair(
                        tag,
                        read_ne_i64(&mut element_stream).context("While reading long integer.")?,
                    )
                    .context("Trying to construct long KeyParameterValue.")
                }
                TagType::BOOL => {
                    if read_bool(&mut element_stream).context("While reading long integer.")? {
                        KeyParameterValue::new_from_tag_primitive_pair(tag, 1)
                            .context("Trying to construct boolean KeyParameterValue.")
                    } else {
                        Err(anyhow::anyhow!("Invalid."))
                    }
                }
                TagType::BYTES | TagType::BIGNUM => {
                    let blob_size = read_ne_u32(&mut element_stream)
                        .context("While reading blob size.")?
                        as usize;
                    let indirect_offset = read_ne_u32(&mut element_stream)
                        .context("While reading indirect offset.")?
                        as usize;
                    KeyParameterValue::new_from_tag_primitive_pair(
                        tag,
                        indirect_buffer
                            .get(indirect_offset..indirect_offset + blob_size)
                            .context("While reading blob value.")?
                            .to_vec(),
                    )
                    .context("Trying to construct blob KeyParameterValue.")
                }
                TagType::INVALID => Err(anyhow::anyhow!("Invalid.")),
                _ => {
                    return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                        .context(ks_err!("Encountered bogus tag type."));
                }
            };
            if let Ok(p) = param {
                params.push(p);
            }
        }

        Ok(params)
    }

    /// This function takes a Blob and an optional AesGcm. Plain text blob variants are
    /// passed through as is. If a super key is given an attempt is made to decrypt the
    /// blob thereby mapping BlobValue variants as follows:
    /// BlobValue::Encrypted => BlobValue::Decrypted
    /// BlobValue::EncryptedGeneric => BlobValue::Generic
    /// BlobValue::EncryptedCharacteristics => BlobValue::Characteristics
    /// If now super key is given or BlobValue::PwEncrypted is encountered,
    /// Err(Error::LockedComponent) is returned.
    fn decrypt_if_required(super_key: &Option<Arc<dyn AesGcm>>, blob: Blob) -> Result<Blob> {
        match blob {
            Blob { value: BlobValue::Generic(_), .. }
            | Blob { value: BlobValue::Characteristics(_), .. }
            | Blob { value: BlobValue::CharacteristicsCache(_), .. }
            | Blob { value: BlobValue::Decrypted(_), .. } => Ok(blob),
            Blob { value: BlobValue::EncryptedCharacteristics { iv, tag, data }, flags }
                if super_key.is_some() =>
            {
                Ok(Blob {
                    value: BlobValue::Characteristics(
                        super_key
                            .as_ref()
                            .unwrap()
                            .decrypt(&data, &iv, &tag)
                            .context(ks_err!("Failed to decrypt EncryptedCharacteristics"))?[..]
                            .to_vec(),
                    ),
                    flags,
                })
            }
            Blob { value: BlobValue::Encrypted { iv, tag, data }, flags }
                if super_key.is_some() =>
            {
                Ok(Blob {
                    value: BlobValue::Decrypted(
                        super_key
                            .as_ref()
                            .unwrap()
                            .decrypt(&data, &iv, &tag)
                            .context(ks_err!("Failed to decrypt Encrypted"))?,
                    ),
                    flags,
                })
            }
            Blob { value: BlobValue::EncryptedGeneric { iv, tag, data }, flags }
                if super_key.is_some() =>
            {
                Ok(Blob {
                    value: BlobValue::Generic(
                        super_key
                            .as_ref()
                            .unwrap()
                            .decrypt(&data, &iv, &tag)
                            .context(ks_err!("Failed to decrypt Encrypted"))?[..]
                            .to_vec(),
                    ),
                    flags,
                })
            }
            // This arm catches all encrypted cases where super key is not present or cannot
            // decrypt the blob, the latter being BlobValue::PwEncrypted.
            _ => Err(Error::LockedComponent)
                .context(ks_err!("Encountered encrypted blob without super key.")),
        }
    }

    fn read_characteristics_file(
        &self,
        uid: u32,
        prefix: &str,
        alias: &str,
        hw_sec_level: SecurityLevel,
        super_key: &Option<Arc<dyn AesGcm>>,
    ) -> Result<LegacyKeyCharacteristics> {
        let blob = Self::read_generic_blob(&self.make_chr_filename(uid, alias, prefix))
            .context(ks_err!())?;

        let blob = match blob {
            None => return Ok(LegacyKeyCharacteristics::Cache(Vec::new())),
            Some(blob) => blob,
        };

        let blob = Self::decrypt_if_required(super_key, blob)
            .context(ks_err!("Trying to decrypt blob."))?;

        let (mut stream, is_cache) = match blob.value() {
            BlobValue::Characteristics(data) => (&data[..], false),
            BlobValue::CharacteristicsCache(data) => (&data[..], true),
            _ => {
                return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                    .context(ks_err!("Characteristics file does not hold key characteristics."));
            }
        };

        let hw_list = match blob.value() {
            // The characteristics cache file has two lists and the first is
            // the hardware enforced list.
            BlobValue::CharacteristicsCache(_) => Some(
                Self::read_key_parameters(&mut stream)
                    .context(ks_err!())?
                    .into_iter()
                    .map(|value| KeyParameter::new(value, hw_sec_level)),
            ),
            _ => None,
        };

        let sw_list = Self::read_key_parameters(&mut stream)
            .context(ks_err!())?
            .into_iter()
            .map(|value| KeyParameter::new(value, SecurityLevel::KEYSTORE));

        let params: Vec<KeyParameter> = hw_list.into_iter().flatten().chain(sw_list).collect();
        if is_cache {
            Ok(LegacyKeyCharacteristics::Cache(params))
        } else {
            Ok(LegacyKeyCharacteristics::File(params))
        }
    }

    // This is a list of known prefixes that the Keystore 1.0 SPI used to use.
    //  * USRPKEY was used for private and secret key material, i.e., KM blobs.
    //  * USRSKEY was used for secret key material, i.e., KM blobs, before Android P.
    //  * CACERT  was used for key chains or free standing public certificates.
    //  * USRCERT was used for public certificates of USRPKEY entries. But KeyChain also
    //            used this for user installed certificates without private key material.

    const KNOWN_KEYSTORE_PREFIXES: &'static [&'static str] =
        &["USRPKEY_", "USRSKEY_", "USRCERT_", "CACERT_"];

    fn is_keystore_alias(encoded_alias: &str) -> bool {
        // We can check the encoded alias because the prefixes we are interested
        // in are all in the printable range that don't get mangled.
        Self::KNOWN_KEYSTORE_PREFIXES.iter().any(|prefix| encoded_alias.starts_with(prefix))
    }

    fn read_km_blob_file(&self, uid: u32, alias: &str) -> Result<Option<(Blob, String)>> {
        let mut iter = ["USRPKEY", "USRSKEY"].iter();

        let (blob, prefix) = loop {
            if let Some(prefix) = iter.next() {
                if let Some(blob) =
                    Self::read_generic_blob(&self.make_blob_filename(uid, alias, prefix))
                        .context("In read_km_blob_file.")?
                {
                    break (blob, prefix);
                }
            } else {
                return Ok(None);
            }
        };

        Ok(Some((blob, prefix.to_string())))
    }

    fn read_generic_blob(path: &Path) -> Result<Option<Blob>> {
        let mut file = match Self::with_retry_interrupted(|| File::open(path)) {
            Ok(file) => file,
            Err(e) => match e.kind() {
                ErrorKind::NotFound => return Ok(None),
                _ => return Err(e).context(ks_err!()),
            },
        };

        Ok(Some(Self::new_from_stream(&mut file).context(ks_err!())?))
    }

    fn read_generic_blob_decrypt_with<F>(path: &Path, decrypt: F) -> Result<Option<Blob>>
    where
        F: FnOnce(&[u8], &[u8], &[u8], Option<&[u8]>, Option<usize>) -> Result<ZVec>,
    {
        let mut file = match Self::with_retry_interrupted(|| File::open(path)) {
            Ok(file) => file,
            Err(e) => match e.kind() {
                ErrorKind::NotFound => return Ok(None),
                _ => return Err(e).context(ks_err!()),
            },
        };

        Ok(Some(Self::new_from_stream_decrypt_with(&mut file, decrypt).context(ks_err!())?))
    }

    /// Read a legacy keystore entry blob.
    pub fn read_legacy_keystore_entry<F>(
        &self,
        uid: u32,
        alias: &str,
        decrypt: F,
    ) -> Result<Option<Vec<u8>>>
    where
        F: FnOnce(&[u8], &[u8], &[u8], Option<&[u8]>, Option<usize>) -> Result<ZVec>,
    {
        let path = match self.make_legacy_keystore_entry_filename(uid, alias) {
            Some(path) => path,
            None => return Ok(None),
        };

        let blob = Self::read_generic_blob_decrypt_with(&path, decrypt)
            .context(ks_err!("Failed to read blob."))?;

        Ok(blob.and_then(|blob| match blob.value {
            BlobValue::Generic(blob) => Some(blob),
            _ => {
                log::info!("Unexpected legacy keystore entry blob type. Ignoring");
                None
            }
        }))
    }

    /// Remove a legacy keystore entry by the name alias with owner uid.
    pub fn remove_legacy_keystore_entry(&self, uid: u32, alias: &str) -> Result<bool> {
        let path = match self.make_legacy_keystore_entry_filename(uid, alias) {
            Some(path) => path,
            None => return Ok(false),
        };

        if let Err(e) = Self::with_retry_interrupted(|| fs::remove_file(path.as_path())) {
            match e.kind() {
                ErrorKind::NotFound => return Ok(false),
                _ => return Err(e).context(ks_err!()),
            }
        }

        let user_id = uid_to_android_user(uid);
        self.remove_user_dir_if_empty(user_id)
            .context(ks_err!("Trying to remove empty user dir."))?;
        Ok(true)
    }

    /// List all entries belonging to the given uid.
    pub fn list_legacy_keystore_entries_for_uid(&self, uid: u32) -> Result<Vec<String>> {
        let mut path = self.path.clone();
        let user_id = uid_to_android_user(uid);
        path.push(format!("user_{}", user_id));
        let uid_str = uid.to_string();
        let dir = match Self::with_retry_interrupted(|| fs::read_dir(path.as_path())) {
            Ok(dir) => dir,
            Err(e) => match e.kind() {
                ErrorKind::NotFound => return Ok(Default::default()),
                _ => {
                    return Err(e)
                        .context(ks_err!("Failed to open legacy blob database: {:?}", path));
                }
            },
        };
        let mut result: Vec<String> = Vec::new();
        for entry in dir {
            let file_name = entry.context(ks_err!("Trying to access dir entry"))?.file_name();
            if let Some(f) = file_name.to_str() {
                let encoded_alias = &f[uid_str.len() + 1..];
                if f.starts_with(&uid_str) && !Self::is_keystore_alias(encoded_alias) {
                    result.push(
                        Self::decode_alias(encoded_alias)
                            .context(ks_err!("Trying to decode alias."))?,
                    )
                }
            }
        }
        Ok(result)
    }

    fn extract_legacy_alias(encoded_alias: &str) -> Option<String> {
        if !Self::is_keystore_alias(encoded_alias) {
            Self::decode_alias(encoded_alias).ok()
        } else {
            None
        }
    }

    /// Lists all keystore entries belonging to the given user. Returns a map of UIDs
    /// to sets of decoded aliases. Only returns entries that do not begin with
    /// KNOWN_KEYSTORE_PREFIXES.
    pub fn list_legacy_keystore_entries_for_user(
        &self,
        user_id: u32,
    ) -> Result<HashMap<u32, HashSet<String>>> {
        let user_entries = self.list_user(user_id).context(ks_err!("Trying to list user."))?;

        let result =
            user_entries.into_iter().fold(HashMap::<u32, HashSet<String>>::new(), |mut acc, v| {
                if let Some(sep_pos) = v.find('_') {
                    if let Ok(uid) = v[0..sep_pos].parse::<u32>() {
                        if let Some(alias) = Self::extract_legacy_alias(&v[sep_pos + 1..]) {
                            let entry = acc.entry(uid).or_default();
                            entry.insert(alias);
                        }
                    }
                }
                acc
            });
        Ok(result)
    }

    /// This function constructs the legacy blob file name which has the form:
    /// user_<android user id>/<uid>_<alias>. Legacy blob file names must not use
    /// known keystore prefixes.
    fn make_legacy_keystore_entry_filename(&self, uid: u32, alias: &str) -> Option<PathBuf> {
        // Legacy entries must not use known keystore prefixes.
        if Self::is_keystore_alias(alias) {
            log::warn!(
                "Known keystore prefixes cannot be used with legacy keystore -> ignoring request."
            );
            return None;
        }

        let mut path = self.path.clone();
        let user_id = uid_to_android_user(uid);
        let encoded_alias = Self::encode_alias(alias);
        path.push(format!("user_{}", user_id));
        path.push(format!("{}_{}", uid, encoded_alias));
        Some(path)
    }

    /// This function constructs the blob file name which has the form:
    /// user_<android user id>/<uid>_<prefix>_<alias>.
    fn make_blob_filename(&self, uid: u32, alias: &str, prefix: &str) -> PathBuf {
        let user_id = uid_to_android_user(uid);
        let encoded_alias = Self::encode_alias(&format!("{}_{}", prefix, alias));
        let mut path = self.make_user_path_name(user_id);
        path.push(format!("{}_{}", uid, encoded_alias));
        path
    }

    /// This function constructs the characteristics file name which has the form:
    /// user_<android user id>/.<uid>_chr_<prefix>_<alias>.
    fn make_chr_filename(&self, uid: u32, alias: &str, prefix: &str) -> PathBuf {
        let user_id = uid_to_android_user(uid);
        let encoded_alias = Self::encode_alias(&format!("{}_{}", prefix, alias));
        let mut path = self.make_user_path_name(user_id);
        path.push(format!(".{}_chr_{}", uid, encoded_alias));
        path
    }

    fn make_super_key_filename(&self, user_id: u32) -> PathBuf {
        let mut path = self.make_user_path_name(user_id);
        path.push(".masterkey");
        path
    }

    fn make_user_path_name(&self, user_id: u32) -> PathBuf {
        let mut path = self.path.clone();
        path.push(&format!("user_{}", user_id));
        path
    }

    /// Returns if the legacy blob database is empty, i.e., there are no entries matching "user_*"
    /// in the database dir.
    pub fn is_empty(&self) -> Result<bool> {
        let dir = Self::with_retry_interrupted(|| fs::read_dir(self.path.as_path()))
            .context(ks_err!("Failed to open legacy blob database."))?;
        for entry in dir {
            if (*entry.context(ks_err!("Trying to access dir entry"))?.file_name())
                .to_str()
                .map_or(false, |f| f.starts_with("user_"))
            {
                return Ok(false);
            }
        }
        Ok(true)
    }

    /// Returns if the legacy blob database is empty for a given user, i.e., there are no entries
    /// matching "user_*" in the database dir.
    pub fn is_empty_user(&self, user_id: u32) -> Result<bool> {
        let mut user_path = self.path.clone();
        user_path.push(format!("user_{}", user_id));
        if !user_path.as_path().is_dir() {
            return Ok(true);
        }
        Ok(Self::with_retry_interrupted(|| user_path.read_dir())
            .context(ks_err!("Failed to open legacy user dir."))?
            .next()
            .is_none())
    }

    fn extract_keystore_alias(encoded_alias: &str) -> Option<String> {
        // We can check the encoded alias because the prefixes we are interested
        // in are all in the printable range that don't get mangled.
        for prefix in Self::KNOWN_KEYSTORE_PREFIXES {
            if let Some(alias) = encoded_alias.strip_prefix(prefix) {
                return Self::decode_alias(alias).ok();
            }
        }
        None
    }

    /// List all entries for a given user. The strings are unchanged file names, i.e.,
    /// encoded with UID prefix.
    fn list_user(&self, user_id: u32) -> Result<Vec<String>> {
        let path = self.make_user_path_name(user_id);
        let dir = match Self::with_retry_interrupted(|| fs::read_dir(path.as_path())) {
            Ok(dir) => dir,
            Err(e) => match e.kind() {
                ErrorKind::NotFound => return Ok(Default::default()),
                _ => {
                    return Err(e)
                        .context(ks_err!("Failed to open legacy blob database. {:?}", path));
                }
            },
        };
        let mut result: Vec<String> = Vec::new();
        for entry in dir {
            let file_name = entry.context(ks_err!("Trying to access dir entry"))?.file_name();
            if let Some(f) = file_name.to_str() {
                result.push(f.to_string())
            }
        }
        Ok(result)
    }

    /// List all keystore entries belonging to the given user. Returns a map of UIDs
    /// to sets of decoded aliases.
    pub fn list_keystore_entries_for_user(
        &self,
        user_id: u32,
    ) -> Result<HashMap<u32, HashSet<String>>> {
        let user_entries = self.list_user(user_id).context(ks_err!("Trying to list user."))?;

        let result =
            user_entries.into_iter().fold(HashMap::<u32, HashSet<String>>::new(), |mut acc, v| {
                if let Some(sep_pos) = v.find('_') {
                    if let Ok(uid) = v[0..sep_pos].parse::<u32>() {
                        if let Some(alias) = Self::extract_keystore_alias(&v[sep_pos + 1..]) {
                            let entry = acc.entry(uid).or_default();
                            entry.insert(alias);
                        }
                    }
                }
                acc
            });
        Ok(result)
    }

    /// List all keystore entries belonging to the given uid.
    pub fn list_keystore_entries_for_uid(&self, uid: u32) -> Result<Vec<String>> {
        let user_id = uid_to_android_user(uid);

        let user_entries = self.list_user(user_id).context(ks_err!("Trying to list user."))?;

        let uid_str = format!("{}_", uid);

        let mut result: Vec<String> = user_entries
            .into_iter()
            .filter_map(|v| {
                if !v.starts_with(&uid_str) {
                    return None;
                }
                let encoded_alias = &v[uid_str.len()..];
                Self::extract_keystore_alias(encoded_alias)
            })
            .collect();

        result.sort_unstable();
        result.dedup();
        Ok(result)
    }

    fn with_retry_interrupted<F, T>(f: F) -> IoResult<T>
    where
        F: Fn() -> IoResult<T>,
    {
        loop {
            match f() {
                Ok(v) => return Ok(v),
                Err(e) => match e.kind() {
                    ErrorKind::Interrupted => continue,
                    _ => return Err(e),
                },
            }
        }
    }

    /// Deletes a keystore entry. Also removes the user_<uid> directory on the
    /// last migration.
    pub fn remove_keystore_entry(&self, uid: u32, alias: &str) -> Result<bool> {
        let mut something_was_deleted = false;
        let prefixes = ["USRPKEY", "USRSKEY"];
        for prefix in &prefixes {
            let path = self.make_blob_filename(uid, alias, prefix);
            if let Err(e) = Self::with_retry_interrupted(|| fs::remove_file(path.as_path())) {
                match e.kind() {
                    // Only a subset of keys are expected.
                    ErrorKind::NotFound => continue,
                    // Log error but ignore.
                    _ => log::error!("Error while deleting key blob entries. {:?}", e),
                }
            }
            let path = self.make_chr_filename(uid, alias, prefix);
            if let Err(e) = Self::with_retry_interrupted(|| fs::remove_file(path.as_path())) {
                match e.kind() {
                    ErrorKind::NotFound => {
                        log::info!("No characteristics file found for legacy key blob.")
                    }
                    // Log error but ignore.
                    _ => log::error!("Error while deleting key blob entries. {:?}", e),
                }
            }
            something_was_deleted = true;
            // Only one of USRPKEY and USRSKEY can be present. So we can end the loop
            // if we reach this point.
            break;
        }

        let prefixes = ["USRCERT", "CACERT"];
        for prefix in &prefixes {
            let path = self.make_blob_filename(uid, alias, prefix);
            if let Err(e) = Self::with_retry_interrupted(|| fs::remove_file(path.as_path())) {
                match e.kind() {
                    // USRCERT and CACERT are optional either or both may or may not be present.
                    ErrorKind::NotFound => continue,
                    // Log error but ignore.
                    _ => log::error!("Error while deleting key blob entries. {:?}", e),
                }
                something_was_deleted = true;
            }
        }

        if something_was_deleted {
            let user_id = uid_to_android_user(uid);
            self.remove_user_dir_if_empty(user_id)
                .context(ks_err!("Trying to remove empty user dir."))?;
        }

        Ok(something_was_deleted)
    }

    /// This function moves a keystore file if it exists. It constructs the source and destination
    /// file name using the make_filename function with the arguments uid, alias, and prefix.
    /// The function overwrites existing destination files silently. If the source does not exist,
    /// this function has no side effect and returns successfully.
    fn move_keystore_file_if_exists<F>(
        src_uid: u32,
        dest_uid: u32,
        src_alias: &str,
        dest_alias: &str,
        prefix: &str,
        make_filename: F,
    ) -> Result<()>
    where
        F: Fn(u32, &str, &str) -> PathBuf,
    {
        let src_path = make_filename(src_uid, src_alias, prefix);
        let dest_path = make_filename(dest_uid, dest_alias, prefix);
        match Self::with_retry_interrupted(|| fs::rename(&src_path, &dest_path)) {
            Err(e) if e.kind() == ErrorKind::NotFound => Ok(()),
            r => r.context(ks_err!("Trying to rename.")),
        }
    }

    /// Moves a keystore entry from one uid to another. The uids must have the same android user
    /// component. Moves across android users are not permitted.
    pub fn move_keystore_entry(
        &self,
        src_uid: u32,
        dest_uid: u32,
        src_alias: &str,
        dest_alias: &str,
    ) -> Result<()> {
        if src_uid == dest_uid {
            // Nothing to do in the trivial case.
            return Ok(());
        }

        if uid_to_android_user(src_uid) != uid_to_android_user(dest_uid) {
            return Err(Error::AndroidUserMismatch).context(ks_err!());
        }

        let prefixes = ["USRPKEY", "USRSKEY", "USRCERT", "CACERT"];
        for prefix in prefixes {
            Self::move_keystore_file_if_exists(
                src_uid,
                dest_uid,
                src_alias,
                dest_alias,
                prefix,
                |uid, alias, prefix| self.make_blob_filename(uid, alias, prefix),
            )
            .with_context(|| ks_err!("Trying to move blob file with prefix: \"{}\"", prefix))?;
        }

        let prefixes = ["USRPKEY", "USRSKEY"];

        for prefix in prefixes {
            Self::move_keystore_file_if_exists(
                src_uid,
                dest_uid,
                src_alias,
                dest_alias,
                prefix,
                |uid, alias, prefix| self.make_chr_filename(uid, alias, prefix),
            )
            .with_context(|| {
                ks_err!(
                    "Trying to move characteristics file with \
                     prefix: \"{}\"",
                    prefix
                )
            })?;
        }

        Ok(())
    }

    fn remove_user_dir_if_empty(&self, user_id: u32) -> Result<()> {
        if self.is_empty_user(user_id).context(ks_err!("Trying to check for empty user dir."))? {
            let user_path = self.make_user_path_name(user_id);
            Self::with_retry_interrupted(|| fs::remove_dir(user_path.as_path())).ok();
        }
        Ok(())
    }

    /// Load a legacy key blob entry by uid and alias.
    pub fn load_by_uid_alias(
        &self,
        uid: u32,
        alias: &str,
        super_key: &Option<Arc<dyn AesGcm>>,
    ) -> Result<(Option<(Blob, LegacyKeyCharacteristics)>, Option<Vec<u8>>, Option<Vec<u8>>)> {
        let km_blob = self.read_km_blob_file(uid, alias).context("In load_by_uid_alias.")?;

        let km_blob = match km_blob {
            Some((km_blob, prefix)) => {
                let km_blob = match km_blob {
                    Blob { flags: _, value: BlobValue::Decrypted(_) }
                    | Blob { flags: _, value: BlobValue::Encrypted { .. } } => km_blob,
                    _ => {
                        return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                            .context(ks_err!("Found wrong blob type in legacy key blob file."))
                    }
                };

                let hw_sec_level = match km_blob.is_strongbox() {
                    true => SecurityLevel::STRONGBOX,
                    false => SecurityLevel::TRUSTED_ENVIRONMENT,
                };
                let key_parameters = self
                    .read_characteristics_file(uid, &prefix, alias, hw_sec_level, super_key)
                    .context(ks_err!())?;
                Some((km_blob, key_parameters))
            }
            None => None,
        };

        let user_cert_blob =
            Self::read_generic_blob(&self.make_blob_filename(uid, alias, "USRCERT"))
                .context(ks_err!("While loading user cert."))?;

        let user_cert = if let Some(blob) = user_cert_blob {
            let blob = Self::decrypt_if_required(super_key, blob)
                .context(ks_err!("While decrypting user cert."))?;

            if let Blob { value: BlobValue::Generic(data), .. } = blob {
                Some(data)
            } else {
                return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                    .context(ks_err!("Found unexpected blob type in USRCERT file"));
            }
        } else {
            None
        };

        let ca_cert_blob = Self::read_generic_blob(&self.make_blob_filename(uid, alias, "CACERT"))
            .context(ks_err!("While loading ca cert."))?;

        let ca_cert = if let Some(blob) = ca_cert_blob {
            let blob = Self::decrypt_if_required(super_key, blob)
                .context(ks_err!("While decrypting ca cert."))?;

            if let Blob { value: BlobValue::Generic(data), .. } = blob {
                Some(data)
            } else {
                return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                    .context(ks_err!("Found unexpected blob type in CACERT file"));
            }
        } else {
            None
        };

        Ok((km_blob, user_cert, ca_cert))
    }

    /// Returns true if the given user has a super key.
    pub fn has_super_key(&self, user_id: u32) -> bool {
        self.make_super_key_filename(user_id).is_file()
    }

    /// Load and decrypt legacy super key blob.
    pub fn load_super_key(&self, user_id: u32, pw: &Password) -> Result<Option<ZVec>> {
        let path = self.make_super_key_filename(user_id);
        let blob = Self::read_generic_blob(&path).context(ks_err!("While loading super key."))?;

        let blob = match blob {
            Some(blob) => match blob {
                Blob { flags, value: BlobValue::PwEncrypted { iv, tag, data, salt, key_size } } => {
                    if (flags & flags::ENCRYPTED) != 0 {
                        let key = pw
                            .derive_key_pbkdf2(&salt, key_size)
                            .context(ks_err!("Failed to derive key from password."))?;
                        let blob = aes_gcm_decrypt(&data, &iv, &tag, &key)
                            .context(ks_err!("while trying to decrypt legacy super key blob."))?;
                        Some(blob)
                    } else {
                        // In 2019 we had some unencrypted super keys due to b/141955555.
                        Some(data.try_into().context(ks_err!("Trying to convert key into ZVec"))?)
                    }
                }
                _ => {
                    return Err(KsError::Rc(ResponseCode::VALUE_CORRUPTED))
                        .context(ks_err!("Found wrong blob type in legacy super key blob file."));
                }
            },
            None => None,
        };

        Ok(blob)
    }

    /// Removes the super key for the given user from the legacy database.
    /// If this was the last entry in the user's database, this function removes
    /// the user_<uid> directory as well.
    pub fn remove_super_key(&self, user_id: u32) {
        let path = self.make_super_key_filename(user_id);
        Self::with_retry_interrupted(|| fs::remove_file(path.as_path())).ok();
        if self.is_empty_user(user_id).ok().unwrap_or(false) {
            let path = self.make_user_path_name(user_id);
            Self::with_retry_interrupted(|| fs::remove_dir(path.as_path())).ok();
        }
    }
}

/// This module implements utility apis for creating legacy blob files.
#[cfg(feature = "keystore2_blob_test_utils")]
pub mod test_utils {
    #![allow(dead_code)]

    /// test vectors for legacy key blobs
    pub mod legacy_blob_test_vectors;

    use crate::legacy_blob::blob_types::{
        GENERIC, KEY_CHARACTERISTICS, KEY_CHARACTERISTICS_CACHE, KM_BLOB, SUPER_KEY,
        SUPER_KEY_AES256,
    };
    use crate::legacy_blob::*;
    use anyhow::{anyhow, Result};
    use keystore2_crypto::{aes_gcm_decrypt, aes_gcm_encrypt};
    use std::convert::TryInto;
    use std::fs::OpenOptions;
    use std::io::Write;

    /// This function takes a blob and synchronizes the encrypted/super encrypted flags
    /// with the blob type for the pairs Generic/EncryptedGeneric,
    /// Characteristics/EncryptedCharacteristics and Encrypted/Decrypted.
    /// E.g. if a non encrypted enum variant is encountered with flags::SUPER_ENCRYPTED
    /// or flags::ENCRYPTED is set, the payload is encrypted and the corresponding
    /// encrypted variant is returned, and vice versa. All other variants remain untouched
    /// even if flags and BlobValue variant are inconsistent.
    pub fn prepare_blob(blob: Blob, key: &[u8]) -> Result<Blob> {
        match blob {
            Blob { value: BlobValue::Generic(data), flags } if blob.is_encrypted() => {
                let (ciphertext, iv, tag) = aes_gcm_encrypt(&data, key).unwrap();
                Ok(Blob { value: BlobValue::EncryptedGeneric { data: ciphertext, iv, tag }, flags })
            }
            Blob { value: BlobValue::Characteristics(data), flags } if blob.is_encrypted() => {
                let (ciphertext, iv, tag) = aes_gcm_encrypt(&data, key).unwrap();
                Ok(Blob {
                    value: BlobValue::EncryptedCharacteristics { data: ciphertext, iv, tag },
                    flags,
                })
            }
            Blob { value: BlobValue::Decrypted(data), flags } if blob.is_encrypted() => {
                let (ciphertext, iv, tag) = aes_gcm_encrypt(&data, key).unwrap();
                Ok(Blob { value: BlobValue::Encrypted { data: ciphertext, iv, tag }, flags })
            }
            Blob { value: BlobValue::EncryptedGeneric { data, iv, tag }, flags }
                if !blob.is_encrypted() =>
            {
                let plaintext = aes_gcm_decrypt(&data, &iv, &tag, key).unwrap();
                Ok(Blob { value: BlobValue::Generic(plaintext[..].to_vec()), flags })
            }
            Blob { value: BlobValue::EncryptedCharacteristics { data, iv, tag }, flags }
                if !blob.is_encrypted() =>
            {
                let plaintext = aes_gcm_decrypt(&data, &iv, &tag, key).unwrap();
                Ok(Blob { value: BlobValue::Characteristics(plaintext[..].to_vec()), flags })
            }
            Blob { value: BlobValue::Encrypted { data, iv, tag }, flags }
                if !blob.is_encrypted() =>
            {
                let plaintext = aes_gcm_decrypt(&data, &iv, &tag, key).unwrap();
                Ok(Blob { value: BlobValue::Decrypted(plaintext), flags })
            }
            _ => Ok(blob),
        }
    }

    /// Legacy blob header structure.
    pub struct LegacyBlobHeader {
        version: u8,
        blob_type: u8,
        flags: u8,
        info: u8,
        iv: [u8; 12],
        tag: [u8; 16],
        blob_size: u32,
    }

    /// This function takes a Blob and writes it to out as a legacy blob file
    /// version 3. Note that the flags field and the values field may be
    /// inconsistent and could be sanitized by this function. It is intentionally
    /// not done to enable tests to construct malformed blobs.
    pub fn write_legacy_blob(out: &mut dyn Write, blob: Blob) -> Result<usize> {
        let (header, data, salt) = match blob {
            Blob { value: BlobValue::Generic(data), flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: GENERIC,
                    flags,
                    info: 0,
                    iv: [0u8; 12],
                    tag: [0u8; 16],
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::Characteristics(data), flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: KEY_CHARACTERISTICS,
                    flags,
                    info: 0,
                    iv: [0u8; 12],
                    tag: [0u8; 16],
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::CharacteristicsCache(data), flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: KEY_CHARACTERISTICS_CACHE,
                    flags,
                    info: 0,
                    iv: [0u8; 12],
                    tag: [0u8; 16],
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::PwEncrypted { iv, tag, data, salt, key_size }, flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: if key_size == keystore2_crypto::AES_128_KEY_LENGTH {
                        SUPER_KEY
                    } else {
                        SUPER_KEY_AES256
                    },
                    flags,
                    info: 0,
                    iv: iv.try_into().unwrap(),
                    tag: tag[..].try_into().unwrap(),
                    blob_size: data.len() as u32,
                },
                data,
                Some(salt),
            ),
            Blob { value: BlobValue::Encrypted { iv, tag, data }, flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: KM_BLOB,
                    flags,
                    info: 0,
                    iv: iv.try_into().unwrap(),
                    tag: tag[..].try_into().unwrap(),
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::EncryptedGeneric { iv, tag, data }, flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: GENERIC,
                    flags,
                    info: 0,
                    iv: iv.try_into().unwrap(),
                    tag: tag[..].try_into().unwrap(),
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::EncryptedCharacteristics { iv, tag, data }, flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: KEY_CHARACTERISTICS,
                    flags,
                    info: 0,
                    iv: iv.try_into().unwrap(),
                    tag: tag[..].try_into().unwrap(),
                    blob_size: data.len() as u32,
                },
                data,
                None,
            ),
            Blob { value: BlobValue::Decrypted(data), flags } => (
                LegacyBlobHeader {
                    version: 3,
                    blob_type: KM_BLOB,
                    flags,
                    info: 0,
                    iv: [0u8; 12],
                    tag: [0u8; 16],
                    blob_size: data.len() as u32,
                },
                data[..].to_vec(),
                None,
            ),
        };
        write_legacy_blob_helper(out, &header, &data, salt.as_deref())
    }

    /// This function takes LegacyBlobHeader, blob payload and writes it to out as a legacy blob file
    /// version 3.
    pub fn write_legacy_blob_helper(
        out: &mut dyn Write,
        header: &LegacyBlobHeader,
        data: &[u8],
        info: Option<&[u8]>,
    ) -> Result<usize> {
        if 1 != out.write(&[header.version])? {
            return Err(anyhow!("Unexpected size while writing version."));
        }
        if 1 != out.write(&[header.blob_type])? {
            return Err(anyhow!("Unexpected size while writing blob_type."));
        }
        if 1 != out.write(&[header.flags])? {
            return Err(anyhow!("Unexpected size while writing flags."));
        }
        if 1 != out.write(&[header.info])? {
            return Err(anyhow!("Unexpected size while writing info."));
        }
        if 12 != out.write(&header.iv)? {
            return Err(anyhow!("Unexpected size while writing iv."));
        }
        if 4 != out.write(&[0u8; 4])? {
            return Err(anyhow!("Unexpected size while writing last 4 bytes of iv."));
        }
        if 16 != out.write(&header.tag)? {
            return Err(anyhow!("Unexpected size while writing tag."));
        }
        if 4 != out.write(&header.blob_size.to_be_bytes())? {
            return Err(anyhow!("Unexpected size while writing blob size."));
        }
        if data.len() != out.write(data)? {
            return Err(anyhow!("Unexpected size while writing blob."));
        }
        if let Some(info) = info {
            if info.len() != out.write(info)? {
                return Err(anyhow!("Unexpected size while writing inof."));
            }
        }
        Ok(40 + data.len() + info.map(|v| v.len()).unwrap_or(0))
    }

    /// Create encrypted characteristics file using given key.
    pub fn make_encrypted_characteristics_file<P: AsRef<Path>>(
        path: P,
        key: &[u8],
        data: &[u8],
    ) -> Result<()> {
        let mut file = OpenOptions::new().write(true).create_new(true).open(path).unwrap();
        let blob =
            Blob { value: BlobValue::Characteristics(data.to_vec()), flags: flags::ENCRYPTED };
        let blob = prepare_blob(blob, key).unwrap();
        write_legacy_blob(&mut file, blob).unwrap();
        Ok(())
    }

    /// Create encrypted user certificate file using given key.
    pub fn make_encrypted_usr_cert_file<P: AsRef<Path>>(
        path: P,
        key: &[u8],
        data: &[u8],
    ) -> Result<()> {
        let mut file = OpenOptions::new().write(true).create_new(true).open(path).unwrap();
        let blob = Blob { value: BlobValue::Generic(data.to_vec()), flags: flags::ENCRYPTED };
        let blob = prepare_blob(blob, key).unwrap();
        write_legacy_blob(&mut file, blob).unwrap();
        Ok(())
    }

    /// Create encrypted CA certificate file using given key.
    pub fn make_encrypted_ca_cert_file<P: AsRef<Path>>(
        path: P,
        key: &[u8],
        data: &[u8],
    ) -> Result<()> {
        let mut file = OpenOptions::new().write(true).create_new(true).open(path).unwrap();
        let blob = Blob { value: BlobValue::Generic(data.to_vec()), flags: flags::ENCRYPTED };
        let blob = prepare_blob(blob, key).unwrap();
        write_legacy_blob(&mut file, blob).unwrap();
        Ok(())
    }

    /// Create encrypted user key file using given key.
    pub fn make_encrypted_key_file<P: AsRef<Path>>(path: P, key: &[u8], data: &[u8]) -> Result<()> {
        let mut file = OpenOptions::new().write(true).create_new(true).open(path).unwrap();
        let blob = Blob {
            value: BlobValue::Decrypted(ZVec::try_from(data).unwrap()),
            flags: flags::ENCRYPTED,
        };
        let blob = prepare_blob(blob, key).unwrap();
        write_legacy_blob(&mut file, blob).unwrap();
        Ok(())
    }

    /// Create user or ca cert blob file.
    pub fn make_cert_blob_file<P: AsRef<Path>>(path: P, data: &[u8]) -> Result<()> {
        let mut file = OpenOptions::new().write(true).create_new(true).open(path).unwrap();
        let blob = Blob { value: BlobValue::Generic(data.to_vec()), flags: 0 };
        let blob = prepare_blob(blob, &[]).unwrap();
        write_legacy_blob(&mut file, blob).unwrap();
        Ok(())
    }
}

#[cfg(test)]
mod test {
    #![allow(dead_code)]
    use super::*;
    use crate::legacy_blob::test_utils::legacy_blob_test_vectors::*;
    use crate::legacy_blob::test_utils::*;
    use anyhow::{anyhow, Result};
    use keystore2_crypto::aes_gcm_decrypt;
    use keystore2_test_utils::TempDir;
    use rand::Rng;
    use std::convert::TryInto;
    use std::ops::Deref;
    use std::string::FromUtf8Error;

    #[test]
    fn decode_encode_alias_test() {
        static ALIAS: &str = "#({}test[])😗";
        static ENCODED_ALIAS: &str = "+S+X{}test[]+Y.`-O-H-G";
        // Second multi byte out of range ------v
        static ENCODED_ALIAS_ERROR1: &str = "+S+{}test[]+Y";
        // Incomplete multi byte ------------------------v
        static ENCODED_ALIAS_ERROR2: &str = "+S+X{}test[]+";
        // Our encoding: ".`-O-H-G"
        // is UTF-8: 0xF0 0x9F 0x98 0x97
        // is UNICODE: U+1F617
        // is 😗
        // But +H below is a valid encoding for 0x18 making this sequence invalid UTF-8.
        static ENCODED_ALIAS_ERROR_UTF8: &str = ".`-O+H-G";

        assert_eq!(ENCODED_ALIAS, &LegacyBlobLoader::encode_alias(ALIAS));
        assert_eq!(ALIAS, &LegacyBlobLoader::decode_alias(ENCODED_ALIAS).unwrap());
        assert_eq!(
            Some(&Error::BadEncoding),
            LegacyBlobLoader::decode_alias(ENCODED_ALIAS_ERROR1)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>()
        );
        assert_eq!(
            Some(&Error::BadEncoding),
            LegacyBlobLoader::decode_alias(ENCODED_ALIAS_ERROR2)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>()
        );
        assert!(LegacyBlobLoader::decode_alias(ENCODED_ALIAS_ERROR_UTF8)
            .unwrap_err()
            .root_cause()
            .downcast_ref::<FromUtf8Error>()
            .is_some());

        for _i in 0..100 {
            // Any valid UTF-8 string should be en- and decoded without loss.
            let alias_str = rand::thread_rng().gen::<[char; 20]>().iter().collect::<String>();
            let random_alias = alias_str.as_bytes();
            let encoded = LegacyBlobLoader::encode_alias(&alias_str);
            let decoded = match LegacyBlobLoader::decode_alias(&encoded) {
                Ok(d) => d,
                Err(_) => panic!("random_alias: {:x?}\nencoded {}", random_alias, encoded),
            };
            assert_eq!(random_alias.to_vec(), decoded.bytes().collect::<Vec<u8>>());
        }
    }

    #[test]
    fn read_golden_key_blob_test() -> anyhow::Result<()> {
        let blob = LegacyBlobLoader::new_from_stream_decrypt_with(&mut &*BLOB, |_, _, _, _, _| {
            Err(anyhow!("should not be called"))
        })
        .unwrap();
        assert!(!blob.is_encrypted());
        assert!(!blob.is_fallback());
        assert!(!blob.is_strongbox());
        assert!(!blob.is_critical_to_device_encryption());
        assert_eq!(blob.value(), &BlobValue::Generic([0xde, 0xed, 0xbe, 0xef].to_vec()));

        let blob = LegacyBlobLoader::new_from_stream_decrypt_with(
            &mut &*REAL_LEGACY_BLOB,
            |_, _, _, _, _| Err(anyhow!("should not be called")),
        )
        .unwrap();
        assert!(!blob.is_encrypted());
        assert!(!blob.is_fallback());
        assert!(!blob.is_strongbox());
        assert!(!blob.is_critical_to_device_encryption());
        assert_eq!(
            blob.value(),
            &BlobValue::Decrypted(REAL_LEGACY_BLOB_PAYLOAD.try_into().unwrap())
        );
        Ok(())
    }

    #[test]
    fn read_aes_gcm_encrypted_key_blob_test() {
        let blob = LegacyBlobLoader::new_from_stream_decrypt_with(
            &mut &*AES_GCM_ENCRYPTED_BLOB,
            |d, iv, tag, salt, key_size| {
                assert_eq!(salt, None);
                assert_eq!(key_size, None);
                assert_eq!(
                    iv,
                    &[
                        0xbd, 0xdb, 0x8d, 0x69, 0x72, 0x56, 0xf0, 0xf5, 0xa4, 0x02, 0x88, 0x7f,
                        0x00, 0x00, 0x00, 0x00,
                    ]
                );
                assert_eq!(
                    tag,
                    &[
                        0x50, 0xd9, 0x97, 0x95, 0x37, 0x6e, 0x28, 0x6a, 0x28, 0x9d, 0x51, 0xb9,
                        0xb9, 0xe0, 0x0b, 0xc3
                    ][..]
                );
                aes_gcm_decrypt(d, iv, tag, AES_KEY).context("Trying to decrypt blob.")
            },
        )
        .unwrap();
        assert!(blob.is_encrypted());
        assert!(!blob.is_fallback());
        assert!(!blob.is_strongbox());
        assert!(!blob.is_critical_to_device_encryption());

        assert_eq!(blob.value(), &BlobValue::Decrypted(DECRYPTED_PAYLOAD.try_into().unwrap()));
    }

    #[test]
    fn read_golden_key_blob_too_short_test() {
        let error =
            LegacyBlobLoader::new_from_stream_decrypt_with(&mut &BLOB[0..15], |_, _, _, _, _| {
                Err(anyhow!("should not be called"))
            })
            .unwrap_err();
        assert_eq!(Some(&Error::BadLen), error.root_cause().downcast_ref::<Error>());
    }

    #[test]
    fn test_is_empty() {
        let temp_dir = TempDir::new("test_is_empty").expect("Failed to create temp dir.");
        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert!(legacy_blob_loader.is_empty().expect("Should succeed and be empty."));

        let _db = crate::database::KeystoreDB::new(temp_dir.path(), None)
            .expect("Failed to open database.");

        assert!(legacy_blob_loader.is_empty().expect("Should succeed and still be empty."));

        std::fs::create_dir(&*temp_dir.build().push("user_0")).expect("Failed to create user_0.");

        assert!(!legacy_blob_loader.is_empty().expect("Should succeed but not be empty."));

        std::fs::create_dir(&*temp_dir.build().push("user_10")).expect("Failed to create user_10.");

        assert!(!legacy_blob_loader.is_empty().expect("Should succeed but still not be empty."));

        std::fs::remove_dir_all(&*temp_dir.build().push("user_0"))
            .expect("Failed to remove user_0.");

        assert!(!legacy_blob_loader.is_empty().expect("Should succeed but still not be empty."));

        std::fs::remove_dir_all(&*temp_dir.build().push("user_10"))
            .expect("Failed to remove user_10.");

        assert!(legacy_blob_loader.is_empty().expect("Should succeed and be empty again."));
    }

    #[test]
    fn test_legacy_blobs() -> anyhow::Result<()> {
        let temp_dir = TempDir::new("legacy_blob_test").unwrap();
        std::fs::create_dir(&*temp_dir.build().push("user_0")).unwrap();

        std::fs::write(&*temp_dir.build().push("user_0").push(".masterkey"), SUPERKEY).unwrap();

        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push(".10223_chr_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND_CHR,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRCERT_authbound"),
            USRCERT_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_CACERT_authbound"),
            CACERT_AUTHBOUND,
        )
        .unwrap();

        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRPKEY_non_authbound"),
            USRPKEY_NON_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push(".10223_chr_USRPKEY_non_authbound"),
            USRPKEY_NON_AUTHBOUND_CHR,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRCERT_non_authbound"),
            USRCERT_NON_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_CACERT_non_authbound"),
            CACERT_NON_AUTHBOUND,
        )
        .unwrap();

        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        if let (Some((Blob { flags, value }, _params)), Some(cert), Some(chain)) =
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &None)?
        {
            assert_eq!(flags, 4);
            assert_eq!(
                value,
                BlobValue::Encrypted {
                    data: USRPKEY_AUTHBOUND_ENC_PAYLOAD.to_vec(),
                    iv: USRPKEY_AUTHBOUND_IV.to_vec(),
                    tag: USRPKEY_AUTHBOUND_TAG.to_vec()
                }
            );
            assert_eq!(&cert[..], LOADED_CERT_AUTHBOUND);
            assert_eq!(&chain[..], LOADED_CACERT_AUTHBOUND);
        } else {
            panic!("");
        }

        if let (Some((Blob { flags, value: _ }, _params)), Some(cert), Some(chain)) =
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &None)?
        {
            assert_eq!(flags, 4);
            //assert_eq!(value, BlobValue::Encrypted(..));
            assert_eq!(&cert[..], LOADED_CERT_AUTHBOUND);
            assert_eq!(&chain[..], LOADED_CACERT_AUTHBOUND);
        } else {
            panic!("");
        }
        if let (Some((Blob { flags, value }, _params)), Some(cert), Some(chain)) =
            legacy_blob_loader.load_by_uid_alias(10223, "non_authbound", &None)?
        {
            assert_eq!(flags, 0);
            assert_eq!(value, BlobValue::Decrypted(LOADED_USRPKEY_NON_AUTHBOUND.try_into()?));
            assert_eq!(&cert[..], LOADED_CERT_NON_AUTHBOUND);
            assert_eq!(&chain[..], LOADED_CACERT_NON_AUTHBOUND);
        } else {
            panic!("");
        }

        legacy_blob_loader.remove_keystore_entry(10223, "authbound").expect("This should succeed.");
        legacy_blob_loader
            .remove_keystore_entry(10223, "non_authbound")
            .expect("This should succeed.");

        assert_eq!(
            (None, None, None),
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &None)?
        );
        assert_eq!(
            (None, None, None),
            legacy_blob_loader.load_by_uid_alias(10223, "non_authbound", &None)?
        );

        // The database should not be empty due to the super key.
        assert!(!legacy_blob_loader.is_empty()?);
        assert!(!legacy_blob_loader.is_empty_user(0)?);

        // The database should be considered empty for user 1.
        assert!(legacy_blob_loader.is_empty_user(1)?);

        legacy_blob_loader.remove_super_key(0);

        // Now it should be empty.
        assert!(legacy_blob_loader.is_empty_user(0)?);
        assert!(legacy_blob_loader.is_empty()?);

        Ok(())
    }

    struct TestKey(ZVec);

    impl crate::utils::AesGcmKey for TestKey {
        fn key(&self) -> &[u8] {
            &self.0
        }
    }

    impl Deref for TestKey {
        type Target = [u8];
        fn deref(&self) -> &Self::Target {
            &self.0
        }
    }

    #[test]
    fn test_with_encrypted_characteristics() -> anyhow::Result<()> {
        let temp_dir = TempDir::new("test_with_encrypted_characteristics").unwrap();
        std::fs::create_dir(&*temp_dir.build().push("user_0")).unwrap();

        let pw: Password = PASSWORD.into();
        let pw_key = TestKey(pw.derive_key_pbkdf2(SUPERKEY_SALT, 32).unwrap());
        let super_key =
            Arc::new(TestKey(pw_key.decrypt(SUPERKEY_PAYLOAD, SUPERKEY_IV, SUPERKEY_TAG).unwrap()));

        std::fs::write(&*temp_dir.build().push("user_0").push(".masterkey"), SUPERKEY).unwrap();

        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND,
        )
        .unwrap();
        make_encrypted_characteristics_file(
            &*temp_dir.build().push("user_0").push(".10223_chr_USRPKEY_authbound"),
            &super_key,
            KEY_PARAMETERS,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRCERT_authbound"),
            USRCERT_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_CACERT_authbound"),
            CACERT_AUTHBOUND,
        )
        .unwrap();

        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert_eq!(
            legacy_blob_loader
                .load_by_uid_alias(10223, "authbound", &None)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>(),
            Some(&Error::LockedComponent)
        );

        assert_eq!(
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &Some(super_key)).unwrap(),
            (
                Some((
                    Blob {
                        flags: 4,
                        value: BlobValue::Encrypted {
                            data: USRPKEY_AUTHBOUND_ENC_PAYLOAD.to_vec(),
                            iv: USRPKEY_AUTHBOUND_IV.to_vec(),
                            tag: USRPKEY_AUTHBOUND_TAG.to_vec()
                        }
                    },
                    structured_test_params()
                )),
                Some(LOADED_CERT_AUTHBOUND.to_vec()),
                Some(LOADED_CACERT_AUTHBOUND.to_vec())
            )
        );

        legacy_blob_loader.remove_keystore_entry(10223, "authbound").expect("This should succeed.");

        assert_eq!(
            (None, None, None),
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &None).unwrap()
        );

        // The database should not be empty due to the super key.
        assert!(!legacy_blob_loader.is_empty().unwrap());
        assert!(!legacy_blob_loader.is_empty_user(0).unwrap());

        // The database should be considered empty for user 1.
        assert!(legacy_blob_loader.is_empty_user(1).unwrap());

        legacy_blob_loader.remove_super_key(0);

        // Now it should be empty.
        assert!(legacy_blob_loader.is_empty_user(0).unwrap());
        assert!(legacy_blob_loader.is_empty().unwrap());

        Ok(())
    }

    #[test]
    fn test_with_encrypted_certificates() -> anyhow::Result<()> {
        let temp_dir = TempDir::new("test_with_encrypted_certificates").unwrap();
        std::fs::create_dir(&*temp_dir.build().push("user_0")).unwrap();

        let pw: Password = PASSWORD.into();
        let pw_key = TestKey(pw.derive_key_pbkdf2(SUPERKEY_SALT, 32).unwrap());
        let super_key =
            Arc::new(TestKey(pw_key.decrypt(SUPERKEY_PAYLOAD, SUPERKEY_IV, SUPERKEY_TAG).unwrap()));

        std::fs::write(&*temp_dir.build().push("user_0").push(".masterkey"), SUPERKEY).unwrap();

        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push(".10223_chr_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND_CHR,
        )
        .unwrap();
        make_encrypted_usr_cert_file(
            &*temp_dir.build().push("user_0").push("10223_USRCERT_authbound"),
            &super_key,
            LOADED_CERT_AUTHBOUND,
        )
        .unwrap();
        make_encrypted_ca_cert_file(
            &*temp_dir.build().push("user_0").push("10223_CACERT_authbound"),
            &super_key,
            LOADED_CACERT_AUTHBOUND,
        )
        .unwrap();

        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert_eq!(
            legacy_blob_loader
                .load_by_uid_alias(10223, "authbound", &None)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>(),
            Some(&Error::LockedComponent)
        );

        assert_eq!(
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &Some(super_key)).unwrap(),
            (
                Some((
                    Blob {
                        flags: 4,
                        value: BlobValue::Encrypted {
                            data: USRPKEY_AUTHBOUND_ENC_PAYLOAD.to_vec(),
                            iv: USRPKEY_AUTHBOUND_IV.to_vec(),
                            tag: USRPKEY_AUTHBOUND_TAG.to_vec()
                        }
                    },
                    structured_test_params_cache()
                )),
                Some(LOADED_CERT_AUTHBOUND.to_vec()),
                Some(LOADED_CACERT_AUTHBOUND.to_vec())
            )
        );

        legacy_blob_loader.remove_keystore_entry(10223, "authbound").expect("This should succeed.");

        assert_eq!(
            (None, None, None),
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &None).unwrap()
        );

        // The database should not be empty due to the super key.
        assert!(!legacy_blob_loader.is_empty().unwrap());
        assert!(!legacy_blob_loader.is_empty_user(0).unwrap());

        // The database should be considered empty for user 1.
        assert!(legacy_blob_loader.is_empty_user(1).unwrap());

        legacy_blob_loader.remove_super_key(0);

        // Now it should be empty.
        assert!(legacy_blob_loader.is_empty_user(0).unwrap());
        assert!(legacy_blob_loader.is_empty().unwrap());

        Ok(())
    }

    #[test]
    fn test_in_place_key_migration() -> anyhow::Result<()> {
        let temp_dir = TempDir::new("test_in_place_key_migration").unwrap();
        std::fs::create_dir(&*temp_dir.build().push("user_0")).unwrap();

        let pw: Password = PASSWORD.into();
        let pw_key = TestKey(pw.derive_key_pbkdf2(SUPERKEY_SALT, 32).unwrap());
        let super_key =
            Arc::new(TestKey(pw_key.decrypt(SUPERKEY_PAYLOAD, SUPERKEY_IV, SUPERKEY_TAG).unwrap()));

        std::fs::write(&*temp_dir.build().push("user_0").push(".masterkey"), SUPERKEY).unwrap();

        std::fs::write(
            &*temp_dir.build().push("user_0").push("10223_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND,
        )
        .unwrap();
        std::fs::write(
            &*temp_dir.build().push("user_0").push(".10223_chr_USRPKEY_authbound"),
            USRPKEY_AUTHBOUND_CHR,
        )
        .unwrap();
        make_encrypted_usr_cert_file(
            &*temp_dir.build().push("user_0").push("10223_USRCERT_authbound"),
            &super_key,
            LOADED_CERT_AUTHBOUND,
        )
        .unwrap();
        make_encrypted_ca_cert_file(
            &*temp_dir.build().push("user_0").push("10223_CACERT_authbound"),
            &super_key,
            LOADED_CACERT_AUTHBOUND,
        )
        .unwrap();

        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert_eq!(
            legacy_blob_loader
                .load_by_uid_alias(10223, "authbound", &None)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>(),
            Some(&Error::LockedComponent)
        );

        let super_key: Option<Arc<dyn AesGcm>> = Some(super_key);

        assert_eq!(
            legacy_blob_loader.load_by_uid_alias(10223, "authbound", &super_key).unwrap(),
            (
                Some((
                    Blob {
                        flags: 4,
                        value: BlobValue::Encrypted {
                            data: USRPKEY_AUTHBOUND_ENC_PAYLOAD.to_vec(),
                            iv: USRPKEY_AUTHBOUND_IV.to_vec(),
                            tag: USRPKEY_AUTHBOUND_TAG.to_vec()
                        }
                    },
                    structured_test_params_cache()
                )),
                Some(LOADED_CERT_AUTHBOUND.to_vec()),
                Some(LOADED_CACERT_AUTHBOUND.to_vec())
            )
        );

        legacy_blob_loader.move_keystore_entry(10223, 10224, "authbound", "boundauth").unwrap();

        assert_eq!(
            legacy_blob_loader
                .load_by_uid_alias(10224, "boundauth", &None)
                .unwrap_err()
                .root_cause()
                .downcast_ref::<Error>(),
            Some(&Error::LockedComponent)
        );

        assert_eq!(
            legacy_blob_loader.load_by_uid_alias(10224, "boundauth", &super_key).unwrap(),
            (
                Some((
                    Blob {
                        flags: 4,
                        value: BlobValue::Encrypted {
                            data: USRPKEY_AUTHBOUND_ENC_PAYLOAD.to_vec(),
                            iv: USRPKEY_AUTHBOUND_IV.to_vec(),
                            tag: USRPKEY_AUTHBOUND_TAG.to_vec()
                        }
                    },
                    structured_test_params_cache()
                )),
                Some(LOADED_CERT_AUTHBOUND.to_vec()),
                Some(LOADED_CACERT_AUTHBOUND.to_vec())
            )
        );

        legacy_blob_loader.remove_keystore_entry(10224, "boundauth").expect("This should succeed.");

        assert_eq!(
            (None, None, None),
            legacy_blob_loader.load_by_uid_alias(10224, "boundauth", &None).unwrap()
        );

        // The database should not be empty due to the super key.
        assert!(!legacy_blob_loader.is_empty().unwrap());
        assert!(!legacy_blob_loader.is_empty_user(0).unwrap());

        // The database should be considered empty for user 1.
        assert!(legacy_blob_loader.is_empty_user(1).unwrap());

        legacy_blob_loader.remove_super_key(0);

        // Now it should be empty.
        assert!(legacy_blob_loader.is_empty_user(0).unwrap());
        assert!(legacy_blob_loader.is_empty().unwrap());

        Ok(())
    }

    #[test]
    fn list_non_existing_user() -> Result<()> {
        let temp_dir = TempDir::new("list_non_existing_user").unwrap();
        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert!(legacy_blob_loader.list_user(20)?.is_empty());

        Ok(())
    }

    #[test]
    fn list_legacy_keystore_entries_on_non_existing_user() -> Result<()> {
        let temp_dir = TempDir::new("list_legacy_keystore_entries_on_non_existing_user").unwrap();
        let legacy_blob_loader = LegacyBlobLoader::new(temp_dir.path());

        assert!(legacy_blob_loader.list_legacy_keystore_entries_for_user(20)?.is_empty());

        Ok(())
    }

    #[test]
    fn test_move_keystore_entry() {
        let temp_dir = TempDir::new("test_move_keystore_entry").unwrap();
        std::fs::create_dir(&*temp_dir.build().push("user_0")).unwrap();

        const SOME_CONTENT: &[u8] = b"some content";
        const ANOTHER_CONTENT: &[u8] = b"another content";
        const SOME_FILENAME: &str = "some_file";
        const ANOTHER_FILENAME: &str = "another_file";

        std::fs::write(&*temp_dir.build().push("user_0").push(SOME_FILENAME), SOME_CONTENT)
            .unwrap();

        std::fs::write(&*temp_dir.build().push("user_0").push(ANOTHER_FILENAME), ANOTHER_CONTENT)
            .unwrap();

        // Non existent source id silently ignored.
        assert!(LegacyBlobLoader::move_keystore_file_if_exists(
            1,
            2,
            "non_existent",
            ANOTHER_FILENAME,
            "ignored",
            |_, alias, _| temp_dir.build().push("user_0").push(alias).to_path_buf()
        )
        .is_ok());

        // Content of another_file has not changed.
        let another_content =
            std::fs::read(&*temp_dir.build().push("user_0").push(ANOTHER_FILENAME)).unwrap();
        assert_eq!(&another_content, ANOTHER_CONTENT);

        // Check that some_file still exists.
        assert!(temp_dir.build().push("user_0").push(SOME_FILENAME).exists());
        // Existing target files are silently overwritten.

        assert!(LegacyBlobLoader::move_keystore_file_if_exists(
            1,
            2,
            SOME_FILENAME,
            ANOTHER_FILENAME,
            "ignored",
            |_, alias, _| temp_dir.build().push("user_0").push(alias).to_path_buf()
        )
        .is_ok());

        // Content of another_file is now "some content".
        let another_content =
            std::fs::read(&*temp_dir.build().push("user_0").push(ANOTHER_FILENAME)).unwrap();
        assert_eq!(&another_content, SOME_CONTENT);

        // Check that some_file no longer exists.
        assert!(!temp_dir.build().push("user_0").push(SOME_FILENAME).exists());
    }
}
