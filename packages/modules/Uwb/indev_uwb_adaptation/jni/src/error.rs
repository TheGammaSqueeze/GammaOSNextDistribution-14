// Copyright 2022, The Android Open Source Project
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

//! Defines errors used by the crate
use uwb_core::error::Error as UwbError;

/// Combined error enum representing either a JNI error, Parse error or UWB service error
#[derive(Debug, thiserror::Error)]
pub enum Error {
    /// JNI error returned from calls to the JNI crate
    #[error("JNI Error: {0}")]
    Jni(#[from] jni::errors::Error),
    /// Parse error returned when failing to convert primitives or objects
    #[error("Parse error: {0}")]
    Parse(String),
    /// Error returned from UWB service
    #[error("Uwb service error: {0}")]
    Uwb(#[from] UwbError),
}

pub type Result<T> = std::result::Result<T, Error>;
