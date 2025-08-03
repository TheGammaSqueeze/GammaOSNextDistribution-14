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

//! Helper functions and macros

use jni::sys::{jboolean, jbyte};
use log::error;
use uwb_core::error::{Error, Result};
use uwb_uci_packets::StatusCode;

pub(crate) fn boolean_result_helper<T>(result: Result<T>, error_msg: &str) -> jboolean {
    match result {
        Ok(_) => true,
        Err(e) => {
            error!("{} failed with {:?}", error_msg, &e);
            false
        }
    }
    .into()
}

pub(crate) fn byte_result_helper<T>(result: Result<T>, error_msg: &str) -> jbyte {
    // StatusCode do not overflow i8
    u8::from(result_to_status_code(result, error_msg)) as i8
}

/// helper function to convert Result to StatusCode
fn result_to_status_code<T>(result: Result<T>, error_msg: &str) -> StatusCode {
    let result = result.map_err(|e| {
        error!("{} failed with {:?}", error_msg, &e);
        e
    });
    match result {
        Ok(_) => StatusCode::UciStatusOk,
        Err(Error::BadParameters) => StatusCode::UciStatusInvalidParam,
        Err(Error::MaxSessionsExceeded) => StatusCode::UciStatusMaxSessionsExceeded,
        Err(Error::CommandRetry) => StatusCode::UciStatusCommandRetry,
        Err(Error::RegulationUwbOff) => StatusCode::UciStatusRegulationUwbOff,
        // For other Error, only generic fail can be given.
        Err(_) => StatusCode::UciStatusFailed,
    }
}

pub(crate) fn option_result_helper<T>(result: Result<T>, error_msg: &str) -> Option<T> {
    result
        .map_err(|e| {
            error!("{} failed with {:?}", error_msg, &e);
            e
        })
        .ok()
}
