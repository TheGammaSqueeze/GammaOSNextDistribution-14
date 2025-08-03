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

use core::fmt;
use serde::{Deserialize, Serialize};

type BsslReasonCode = i32;
type BsslLibraryCode = i32;

/// BoringSSL reason code.
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum ReasonCode {
    NoError,
    Global(GlobalError),
    Cipher(CipherError),
    Ec(EcError),
    Ecdsa(EcdsaError),
    Unknown(BsslReasonCode, BsslLibraryCode),
}

impl fmt::Display for ReasonCode {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Self::NoError => write!(f, "No error in the BoringSSL error queue."),
            Self::Unknown(code, lib) => {
                write!(f, "Unknown reason code '{code}' from the library '{lib}'")
            }
            other => write!(f, "{other:?}"),
        }
    }
}

/// Global errors may occur in any library.
///
/// The values are from:
/// boringssl/src/include/openssl/err.h
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum GlobalError {
    Fatal,
    MallocFailure,
    ShouldNotHaveBeenCalled,
    PassedNullParameter,
    InternalError,
    Overflow,
}

impl fmt::Display for GlobalError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "A global error occurred: {self:?}")
    }
}

/// Errors occurred in the Cipher functions.
///
/// The values are from:
/// boringssl/src/include/openssl/cipher.h
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum CipherError {
    AesKeySetupFailed,
    BadDecrypt,
    BadKeyLength,
    BufferTooSmall,
    CtrlNotImplemented,
    CtrlOperationNotImplemented,
    DataNotMultipleOfBlockLength,
    InitializationError,
    InputNotInitialized,
    InvalidAdSize,
    InvalidKeyLength,
    InvalidNonceSize,
    InvalidOperation,
    IvTooLarge,
    NoCipherSet,
    OutputAliasesInput,
    TagTooLarge,
    TooLarge,
    WrongFinalBlockLength,
    NoDirectionSet,
    InvalidNonce,
}

impl From<CipherError> for ReasonCode {
    fn from(e: CipherError) -> ReasonCode {
        ReasonCode::Cipher(e)
    }
}

impl fmt::Display for CipherError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "An error occurred in a Cipher function: {self:?}")
    }
}

/// Errors occurred in the EC functions.
///
/// The values are from:
/// boringssl/src/include/openssl/ec.h
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum EcError {
    BufferTooSmall,
    CoordinatesOutOfRange,
    D2IEcpkparametersFailure,
    EcGroupNewByNameFailure,
    Group2PkparametersFailure,
    I2DEcpkparametersFailure,
    IncompatibleObjects,
    InvalidCompressedPoint,
    InvalidCompressionBit,
    InvalidEncoding,
    InvalidField,
    InvalidForm,
    InvalidGroupOrder,
    InvalidPrivateKey,
    MissingParameters,
    MissingPrivateKey,
    NonNamedCurve,
    NotInitialized,
    Pkparameters2GroupFailure,
    PointAtInfinity,
    PointIsNotOnCurve,
    SlotFull,
    UndefinedGenerator,
    UnknownGroup,
    UnknownOrder,
    WrongOrder,
    BignumOutOfRange,
    WrongCurveParameters,
    DecodeError,
    EncodeError,
    GroupMismatch,
    InvalidCofactor,
    PublicKeyValidationFailed,
    InvalidScalar,
}

impl From<EcError> for ReasonCode {
    fn from(e: EcError) -> ReasonCode {
        ReasonCode::Ec(e)
    }
}

impl fmt::Display for EcError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "An error occurred in an EC function: {self:?}")
    }
}

/// Errors occurred in the ECDSA functions.
///
/// The values are from:
/// boringssl/src/include/openssl/ecdsa.h
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum EcdsaError {
    BadSignature,
    MissingParameters,
    NeedNewSetupValues,
    NotImplemented,
    RandomNumberGenerationFailed,
    EncodeError,
    TooManyIterations,
}

impl From<EcdsaError> for ReasonCode {
    fn from(e: EcdsaError) -> ReasonCode {
        ReasonCode::Ecdsa(e)
    }
}

impl fmt::Display for EcdsaError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "An error occurred in an ECDSA function: {self:?}")
    }
}
