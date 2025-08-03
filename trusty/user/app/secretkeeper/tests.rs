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
//! Unit tests.

use super::*;
use alloc::vec;
use coset::CborSerializable;
use secretkeeper_core::ta::bootloader as bl;
use test::expect;
use tipc::Handle;
use trusty_std::ffi::{CString, FallibleCString};

test::init!();

fn port_connect(port_name: &str, secure: SecureConnections) {
    let port = CString::try_new(port_name).unwrap();
    let result = Handle::connect(port.as_c_str());
    // The test app generates secure connections, so only secure ports should work.
    if secure.0 {
        expect!(result.is_ok(), "failed to connect to secure {port_name}: {result:?}");
    } else {
        expect!(
            result.is_err(),
            "unexpected success connecting to nonsecure {port_name}: {result:?}"
        );
    }
}

#[test]
fn secretkeeper_connection_test() {
    port_connect(AG_PORT_NAME, SecureConnections(false));
    port_connect(SK_PORT_NAME, SecureConnections(false));
    port_connect(BL_PORT_NAME, SecureConnections(true));
}

#[test]
fn bootloader_retrieve_key() {
    let port = CString::try_new(BL_PORT_NAME).unwrap();
    let session = Handle::connect(port.as_c_str()).unwrap();

    // Manually build a `GetIdentityKey` request.
    let req = SkMessage(vec![0x00, 0x00, 0x00, 0x01]);

    session.send(&req).unwrap();
    let mut buf = [0; MAX_MSG_SIZE];
    let rsp: SkMessage = session.recv(&mut buf).unwrap();

    let rsp = bl::Response::from_slice(&rsp.0).unwrap();
    expect!(matches!(rsp, bl::Response::IdentityKey(_)));
    if let bl::Response::IdentityKey(key) = rsp {
        // Check the key parses as a COSE_Key.
        expect!(coset::CoseKey::from_slice(&key).is_ok());
    }
}
