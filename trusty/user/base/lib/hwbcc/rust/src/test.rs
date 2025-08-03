/*
 * Copyright (C) 2022 The Android Open Source Project
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

use super::*;
use ::test::{assert, assert_ne};

#[cfg(feature = "generic-arm-unittest")]
use ::test::assert_eq;

#[cfg(feature = "generic-arm-unittest")]
use system_state::{SystemState, SystemStateFlag};

::test::init!();

const TEST_MAC_KEY: &'static [u8; HWBCC_MAC_KEY_SIZE as usize] = &[
    0xf4, 0xe2, 0xd2, 0xbb, 0x2d, 0x07, 0x16, 0xb9, 0x66, 0x4b, 0x73, 0xe8, 0x56, 0xd3, 0x6e, 0xfb,
    0x08, 0xb4, 0x01, 0xd8, 0x86, 0x38, 0xa7, 0x9a, 0x97, 0xb3, 0x98, 0x4f, 0x63, 0xdc, 0xef, 0xed,
];

const TEST_AAD: &'static [u8] = &[0xcf, 0xe1, 0x89, 0x39, 0xb1, 0x72, 0xbf, 0x4f, 0xa8, 0x0f];

#[cfg(feature = "generic-arm-unittest")]
#[test]
fn test_get_dice_artifacts() {
    const EMULATOR_CDI_ATTEST: &'static [u8; DICE_CDI_SIZE as usize] = &[
        0x44, 0x26, 0x69, 0x94, 0x02, 0x34, 0x1c, 0xc8, 0x1d, 0x93, 0xc7, 0xb8, 0x47, 0xaf, 0x55,
        0xe8, 0xde, 0x8e, 0x79, 0x4c, 0x1b, 0x0f, 0xea, 0x99, 0x7f, 0x91, 0x83, 0x83, 0x7f, 0x26,
        0x7f, 0x93,
    ];

    const EMULATOR_CDI_SEAL: &'static [u8; DICE_CDI_SIZE as usize] = &[
        0xf7, 0xe5, 0xb0, 0x2b, 0xd0, 0xfa, 0x4d, 0x5b, 0xfa, 0xd8, 0x16, 0x24, 0xfa, 0xc8, 0x50,
        0xac, 0x4f, 0x1a, 0x3d, 0xb4, 0xbc, 0x02, 0xc9, 0xfd, 0xeb, 0xfe, 0x26, 0xfc, 0x28, 0x98,
        0x5b, 0xe8,
    ];

    let dice_artifacts_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    /*
       dice_artifacts expects the following CBOR encoded structure.
       Since the implementation of hwbcc_get_dice_artifacts serves only the
       non-secure world, Bcc is not present in the returned dice_artifacts.
       We calculate the expected size, including CBOR header sizes.
       BccHandover = {
           1 : bstr .size 32,	// CDI_Attest
           2 : bstr .size 32,	// CDI_Seal
           ? 3 : Bcc,          // Cert_Chain
       }
       Bcc = [
           PubKeyEd25519, // UDS
           + BccEntry,    // Root -> leaf
       ]
    */
    let bcc_handover_size: usize = 2 * DICE_CDI_SIZE as usize + 7 /*CBOR tags*/;

    let DiceArtifacts { artifacts } =
        get_dice_artifacts(0, dice_artifacts_buf).expect("could not get protected data");

    assert!(artifacts.len() > 0);
    assert_eq!(artifacts.len(), bcc_handover_size);

    let next_cdi_attest = &mut [0u8; DICE_CDI_SIZE as usize];
    let next_cdi_seal = &mut [0u8; DICE_CDI_SIZE as usize];

    assert!(unsafe {
        // SAFETY: the artifact bytes will be deserialized from CBOR
        // and validated via copy, and not in-place. The original
        // bytestring will remain valid after the check.
        validate_bcc_handover(
            artifacts.as_ptr(),
            artifacts.len(),
            next_cdi_attest as *mut [u8; DICE_CDI_SIZE as usize],
            next_cdi_seal as *mut [u8; DICE_CDI_SIZE as usize],
        )
    });

    let system_state_session =
        SystemState::try_connect().expect("could not connect to system state service");
    if system_state_session.get_flag(SystemStateFlag::AppLoadingUnlocked).unwrap() != 0 {
        assert_eq!(EMULATOR_CDI_ATTEST, next_cdi_attest);
        assert_eq!(EMULATOR_CDI_SEAL, next_cdi_seal);
    }
}

#[test]
fn test_ns_deprivilege() {
    ns_deprivilege().expect("could not execute ns deprivilege");

    // ns_deprivilege should not block calls from secure world
    let dice_artifacts_buf = &mut [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];
    assert!(get_dice_artifacts(0, dice_artifacts_buf).is_ok());
}

#[test]
fn test_get_bcc_test_mode() {
    let mut bcc_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];

    let bcc = get_bcc(HwBccMode::Test, &mut bcc_buf).expect("could not get bcc");

    assert!(bcc.len() > 0);

    let dk_pub_key = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];
    let km_pub_key = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];

    assert!(unsafe {
        // SAFETY: the bcc bytes will be deserialized from CBOR
        // and validated via copy, and not in-place. The original
        // bytestring will remain valid after the check.
        validate_bcc(
            bcc.as_ptr(),
            bcc.len(),
            dk_pub_key as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
            km_pub_key as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
        )
    });

    // get second set of keys
    bcc_buf.fill(0);

    let bcc = get_bcc(HwBccMode::Test, &mut bcc_buf).expect("could not get bcc");

    assert!(bcc.len() > 0);

    let dk_pub_key2 = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];
    let km_pub_key2 = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];

    assert!(unsafe {
        // SAFETY: the bcc bytes will be deserialized from CBOR
        // and validated via copy, and not in-place. The original
        // bytestring will remain valid after the check.
        validate_bcc(
            bcc.as_ptr(),
            bcc.len(),
            dk_pub_key2 as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
            km_pub_key2 as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
        )
    });

    /* the two sets of keys must be different in test mode */
    assert_ne!(dk_pub_key, dk_pub_key2);
    assert_ne!(km_pub_key, km_pub_key2);
}

#[cfg(feature = "generic-arm-unittest")]
#[test]
fn test_get_bcc() {
    /*
     * Device key is hard-coded on emulator targets, i.e. BCC keys are fixed too.
     * We test that BCC keys don't change to make sure that we don't accidentally
     * change the key derivation procedure. Function of test TA app UUID.
     */
    const EMULATOR_PUB_KEY: &'static [u8; ED25519_PUBLIC_KEY_LEN as usize] = &[
        0xc3, 0xfc, 0x8c, 0x92, 0x1d, 0x52, 0xb2, 0x34, 0x9f, 0x6d, 0x59, 0xa3, 0xcd, 0xcd, 0x4a,
        0x8b, 0x1f, 0x97, 0xb6, 0x7b, 0xde, 0x2a, 0x7e, 0x2a, 0x46, 0xae, 0x98, 0x91, 0x47, 0xff,
        0x5a, 0xef,
    ];
    let mut bcc_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];

    let bcc = get_bcc(HwBccMode::Release, &mut bcc_buf).expect("could not get bcc");

    assert!(bcc.len() > 0);

    let dk_pub_key = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];
    let km_pub_key = &mut [0u8; ED25519_PUBLIC_KEY_LEN as usize];

    assert!(unsafe {
        // SAFETY: the bcc bytes will be deserialized from CBOR
        // and validated via copy, and not in-place. The original
        // bytestring will remain valid after the check.
        validate_bcc(
            bcc.as_ptr(),
            bcc.len(),
            dk_pub_key as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
            km_pub_key as *mut [u8; ED25519_PUBLIC_KEY_LEN as usize],
        )
    });

    assert_eq!(EMULATOR_PUB_KEY, dk_pub_key);
    assert_eq!(dk_pub_key, km_pub_key);
}

#[test]
fn test_sign_data_test_mode() {
    let mut cose_sign1_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];

    let cose_sign1 = sign_data(
        HwBccMode::Test,
        SigningAlgorithm::ED25519,
        TEST_MAC_KEY,
        TEST_AAD,
        &mut cose_sign1_buf,
    )
    .expect("could not sign data");

    assert!(cose_sign1.len() > 0);
}

#[cfg(feature = "generic-arm-unittest")]
#[test]
fn test_sign_data() {
    let mut cose_sign1_buf = [0u8; HWBCC_MAX_RESP_PAYLOAD_LENGTH];

    let cose_sign1 = sign_data(
        HwBccMode::Release,
        SigningAlgorithm::ED25519,
        TEST_MAC_KEY,
        TEST_AAD,
        &mut cose_sign1_buf,
    )
    .expect("could not sign data");

    assert!(cose_sign1.len() > 0);
}
