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

//! Unit tests for testing serialization and deserialization of internal types.

use ciborium::value::Value;
use coset::{AsCborValue, CborSerializable};
use rdroidtest::rdroidtest;
use secretkeeper_comm::wire::{
    AidlErrorCode, ApiError, OpCode, PerformOpReq, PerformOpResponse, PerformOpSuccessRsp,
};

#[rdroidtest]
fn wire_req_roundtrip() {
    let tests = [
        PerformOpReq::SecretManagement(vec![]),
        PerformOpReq::SecretManagement(vec![1, 2, 3]),
        PerformOpReq::DeleteIds(vec![]),
        PerformOpReq::DeleteIds(vec![[1; 64], [2; 64]]),
        PerformOpReq::DeleteAll,
    ];

    for input in tests {
        let data = input.clone().to_vec().unwrap();
        let recovered = PerformOpReq::from_slice(&data).unwrap();
        assert_eq!(input, recovered);
    }
}

#[rdroidtest]
fn wire_rsp_roundtrip() {
    let tests = [
        PerformOpResponse::Success(PerformOpSuccessRsp::ProtectedResponse(vec![])),
        PerformOpResponse::Success(PerformOpSuccessRsp::ProtectedResponse(vec![1, 2, 3])),
        PerformOpResponse::Success(PerformOpSuccessRsp::Empty),
        PerformOpResponse::Failure(ApiError {
            err_code: AidlErrorCode::InternalError,
            msg: "msg".to_string(),
        }),
    ];

    for input in tests {
        let data = input.clone().to_vec().unwrap();
        let recovered = PerformOpResponse::from_slice(&data).unwrap();
        assert_eq!(input, recovered);
    }
}

#[rdroidtest]
fn wire_req_deserialize_fail() {
    let bogus_data = [0x99, 0x99];
    let result = PerformOpReq::from_slice(&bogus_data);
    assert!(result.is_err());
}

#[rdroidtest]
fn wire_rsp_deserialize_fail() {
    let bogus_data = [0x99, 0x99];
    let result = PerformOpReq::from_slice(&bogus_data);
    assert!(result.is_err());
}

#[rdroidtest]
fn wire_opcode_out_of_range() {
    let bignum = Value::Integer(999.into());
    let result = OpCode::from_cbor_value(bignum);
    assert!(result.is_err());
}

#[rdroidtest]
fn wire_rsp_errcode_out_of_range() {
    let bogus_data = [
        0x82, // 2-arr
        0x19, 0x03, 0xe7, // int, value 999
        0x60, // 0-tstr
    ];
    // Invalid error codes get mapped to `InternalError`.
    let result = PerformOpResponse::from_slice(&bogus_data).unwrap();
    assert_eq!(
        result,
        PerformOpResponse::Failure(ApiError {
            err_code: AidlErrorCode::InternalError,
            msg: "".to_string(),
        })
    );
}
