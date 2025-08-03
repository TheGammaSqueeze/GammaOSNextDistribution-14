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

//! Unit tests

use crate::cbor::AsCborValue;
use alloc::vec;

#[test]
fn test_init_req_round_trip() {
    let want = crate::InitRequest {
        peer_pub_key: vec![1, 2, 3],
        peer_id: vec![2, 3, 4],
        peer_nonce: vec![4, 5, 6],
        peer_version: 1,
    };
    let data = want.clone().into_vec().unwrap();
    let got = crate::InitRequest::from_slice(&data).unwrap();
    assert_eq!(got, want);
}

#[test]
fn test_init_rsp_round_trip() {
    let want = crate::PerformOpResponse {
        error_code: crate::ErrorCode::Ok,
        rsp: Some(crate::PerformOpRsp::Init(crate::InitResponse {
            ret: crate::KeInitResult {
                session_init_info: crate::SessionInitiationInfo {
                    ke_key: crate::Key {
                        pub_key: Some(vec![10, 11]),
                        arc_from_pbk: Some(vec![12, 13]),
                    },
                    identity: vec![9],
                    nonce: vec![8, 7, 6],
                    version: 1,
                },
                session_info: crate::SessionInfo {
                    shared_keys: [vec![1], vec![2]],
                    session_id: vec![3, 4, 5],
                    session_id_signature: vec![5, 6, 7],
                },
            },
        })),
    };
    let data = want.clone().into_vec().unwrap();
    let got = crate::PerformOpResponse::from_slice(&data).unwrap();
    assert_eq!(got, want);
}
