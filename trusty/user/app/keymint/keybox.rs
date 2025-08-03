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
//! Client used to connect to the keybox service.
use crate::ffi_bindings::{KeyboxUnwrapReq, KeyboxUnwrapResp, KEYBOX_PORT, KEYBOX_RESP_HDR_SIZE};
use core::ffi::CStr;
use kmr_common::{km_err, vec_try, Error};
use tipc::{Handle, TipcError};

/// A `KeyboxSession` is a `Handle`.
type KeyboxSession = Handle;
/// Connection to the keybox service.
#[derive(Debug, Eq, PartialEq)]
struct Keybox(KeyboxSession);

impl Keybox {
    /// Attempt to open a keybox session.
    ///
    /// # Examples
    ///
    /// ```
    /// let keybox = keybox::open().expect("could not open hwkey session");
    /// ```
    ///
    fn new() -> Result<Self, TipcError> {
        let port =
            CStr::from_bytes_with_nul(KEYBOX_PORT).expect("KEYBOX_PORT was not null terminated");
        KeyboxSession::connect(port).map(Self)
    }

    pub(crate) fn keybox_unwrap(&self, wrapped_keybox: &[u8]) -> Result<Vec<u8>, Error> {
        let req = KeyboxUnwrapReq::new(wrapped_keybox);
        self.0
            .send(&req)
            .map_err(|e| km_err!(SecureHwCommunicationFailed, "send unwrap cmd failed: {:?}", e))?;
        // This uses the same assumption as SetWrappedAttestationKey on the c++ code; which is that
        // the size of the unwrapped key won't be bigger than the size of the wrapped one.
        let mut buffer = vec_try![0; wrapped_keybox.len() + KEYBOX_RESP_HDR_SIZE]?;
        let response: KeyboxUnwrapResp = self
            .0
            .recv(&mut buffer)
            .map_err(|e| km_err!(SecureHwCommunicationFailed, "unwrap response error: {:?}", e))?;
        Ok(response.get_unwrapped_keybox())
    }
}

pub(crate) fn keybox_unwrap(wrapped_keybox: &[u8]) -> Result<Vec<u8>, Error> {
    let keybox = Keybox::new().map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "error opening keybox service: {:?}", e)
    })?;
    keybox.keybox_unwrap(wrapped_keybox)
}

#[cfg(test)]
mod tests {
    use super::*;
    use test::{expect, expect_eq, skip};

    // AOSP keybox test server just XORs data with a constant and checksum it, it is not intended to
    // be secure; just to be used to check the IPC commands.
    fn create_fake_wrapped_data(data: &[u8]) -> Vec<u8> {
        let mut wrapped_data = Vec::<u8>::new();
        let mut checksum: u8 = 0;
        for &element in data {
            let wrapped_element = element ^ 0x42;
            wrapped_data.push(wrapped_element);
            checksum ^= wrapped_element;
        }
        wrapped_data.push(checksum);
        wrapped_data
    }

    #[test]
    fn unwrap_fake_keybox_data() {
        if true {
            skip!("TODO: reinstate test");
        }
        let original_data = b"test_data_to_wrap".as_slice();
        let wrapped_data = create_fake_wrapped_data(original_data);
        let result = keybox_unwrap(&wrapped_data);
        expect!(result.is_ok(), "Failed to unwrap data: {:?}", result);
        let unwrapped_data = result.expect("Couldn't unwrap data");
        expect_eq!(original_data, unwrapped_data, "Unwrapped data do not match original one");
    }
}
