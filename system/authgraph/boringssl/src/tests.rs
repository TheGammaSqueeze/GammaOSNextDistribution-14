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
//
////////////////////////////////////////////////////////////////////////////////

//! Tests for BoringSSL-based implementations of AuthGraph traits.
use alloc::rc::Rc;
use authgraph_core::{
    key::{
        CertChain, EcSignKey, EcVerifyKey, Identity, EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION,
        IDENTITY_VERSION,
    },
    keyexchange,
};
use authgraph_core_test as ag_test;
use core::cell::RefCell;
use coset::{cbor::value::Value, iana, CborOrdering, CborSerializable, Label};

#[test]
fn test_rng() {
    let mut rng = crate::BoringRng;
    ag_test::test_rng(&mut rng);
}

#[test]
fn test_sha256() {
    ag_test::test_sha256(&crate::BoringSha256);
}

#[test]
fn test_hmac() {
    ag_test::test_hmac(&crate::BoringHmac);
}

#[test]
fn test_hkdf() {
    ag_test::test_hkdf(&crate::BoringHkdf);
}

#[test]
fn test_aes_gcm_keygen() {
    ag_test::test_aes_gcm_keygen(&crate::BoringAes, &mut crate::BoringRng);
}

#[test]
fn test_aes_gcm_roundtrip() {
    ag_test::test_aes_gcm_roundtrip(&crate::BoringAes, &mut crate::BoringRng);
}

#[test]
fn test_aes_gcm() {
    ag_test::test_aes_gcm(&crate::BoringAes);
}

#[test]
fn test_ecdh() {
    ag_test::test_ecdh(&crate::BoringEcDh);
}

#[test]
fn test_ecdsa() {
    ag_test::test_ecdsa(&crate::BoringEcDsa);
}

#[test]
fn test_ed25519_round_trip() {
    ag_test::test_ed25519_round_trip(&crate::BoringEcDsa);
}

#[test]
fn test_p256_round_trip() {
    ag_test::test_p256_round_trip(&crate::BoringEcDsa);
}

#[test]
fn test_p384_round_trip() {
    ag_test::test_p384_round_trip(&crate::BoringEcDsa);
}

#[test]
fn test_key_exchange_protocol() {
    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(crate::test_device::AgDevice::default())),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_create(&mut source);
    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(crate::test_device::AgDevice::default())),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_init(&mut source, &mut sink);
    ag_test::test_key_exchange_finish(&mut source, &mut sink);
    ag_test::test_key_exchange_auth_complete(&mut source, &mut sink);
}

#[test]
fn test_ke_with_newer_source() {
    let source_device = crate::test_device::AgDevice::default();
    source_device.set_version(2);

    let sink_device = crate::test_device::AgDevice::default();
    sink_device.set_version(1);

    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(source_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(sink_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    ag_test::test_ke_with_newer_source(&mut source, &mut sink);
}

#[test]
fn test_ke_with_newer_sink() {
    let source_device = crate::test_device::AgDevice::default();
    source_device.set_version(1);

    let sink_device = crate::test_device::AgDevice::default();
    sink_device.set_version(2);

    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(source_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(sink_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    ag_test::test_ke_with_newer_sink(&mut source, &mut sink);
}

#[test]
fn test_ke_for_protocol_downgrade() {
    let source_device = crate::test_device::AgDevice::default();
    source_device.set_version(2);

    let sink_device = crate::test_device::AgDevice::default();
    sink_device.set_version(2);

    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(source_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(sink_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();

    ag_test::test_ke_for_version_downgrade(&mut source, &mut sink);
}

#[test]
fn test_ke_for_replay() {
    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(crate::test_device::AgDevice::default())),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(crate::test_device::AgDevice::default())),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_ke_for_replay(&mut source, &mut sink);
}

#[test]
fn test_identity_validation() {
    ag_test::validate_identity(&crate::BoringEcDsa);
}

#[test]
fn test_key_exchange_with_non_empty_dice_chains() {
    // Both parties have identities containing a DICE certificate chains of non-zero length.
    let source_device = crate::test_device::AgDevice::default();
    let (source_pvt_sign_key, source_cbor_identity) = ag_test::create_identity(4).unwrap();
    let source_identity = Identity::from_slice(&source_cbor_identity).unwrap();
    source_device.set_identity((source_pvt_sign_key, source_identity), iana::Algorithm::EdDSA);
    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(source_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_create(&mut source);
    let sink_device = crate::test_device::AgDevice::default();
    let (sink_pvt_sign_key, sink_cbor_identity) = ag_test::create_identity(5).unwrap();
    let sink_identity = Identity::from_slice(&sink_cbor_identity).unwrap();
    sink_device.set_identity((sink_pvt_sign_key, sink_identity), iana::Algorithm::EdDSA);
    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(sink_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_init(&mut source, &mut sink);
    ag_test::test_key_exchange_finish(&mut source, &mut sink);
    ag_test::test_key_exchange_auth_complete(&mut source, &mut sink);
}

#[test]
fn test_key_exchange_with_mixed_dice_chains() {
    // One party has an identity with an empty DICE certificate chain and the other party has an
    // identity with DICE certificate chainss of non-zero length.
    let source_device = crate::test_device::AgDevice::default();
    let (source_pvt_sign_key, source_cbor_identity) = ag_test::create_identity(0).unwrap();
    let source_identity = Identity::from_slice(&source_cbor_identity).unwrap();
    source_device.set_identity((source_pvt_sign_key, source_identity), iana::Algorithm::EdDSA);
    let mut source = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(source_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_create(&mut source);
    let sink_device = crate::test_device::AgDevice::default();
    let (sink_pvt_sign_key, sink_cbor_identity) = ag_test::create_identity(5).unwrap();
    let sink_identity = Identity::from_slice(&sink_cbor_identity).unwrap();
    sink_device.set_identity((sink_pvt_sign_key, sink_identity), iana::Algorithm::EdDSA);
    let mut sink = keyexchange::AuthGraphParticipant::new(
        crate::crypto_trait_impls(),
        Rc::new(RefCell::new(sink_device)),
        keyexchange::MAX_OPENED_SESSIONS,
    )
    .unwrap();
    ag_test::test_key_exchange_init(&mut source, &mut sink);
    ag_test::test_key_exchange_finish(&mut source, &mut sink);
    ag_test::test_key_exchange_auth_complete(&mut source, &mut sink);
}

#[test]
#[should_panic(expected = "root key is not in the required canonical form")]
fn test_get_identity_with_root_key_in_incorrect_canonical_form() {
    // Check that the `Identity` returned from `get_identity` in the `Device` trait fails the
    // validation, given that the root key is in incorrect canonical form.
    let test_device = crate::test_device::AgDevice::default();
    let (priv_key, mut pub_key) = crate::ec::create_p256_key_pair(iana::Algorithm::ES256).unwrap();
    let mut test_params = Vec::<(Label, Value)>::new();
    for param in pub_key.params {
        test_params.push(param);
    }
    test_params.push((Label::Int(23), Value::Text("test1".to_string())));
    test_params.push((Label::Int(1234), Value::Text("test2".to_string())));
    pub_key.params = test_params;
    pub_key.canonicalize(CborOrdering::LengthFirstLexicographic);
    let identity = Identity {
        version: IDENTITY_VERSION,
        cert_chain: CertChain {
            version: EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION,
            root_key: EcVerifyKey::P256(pub_key),
            dice_cert_chain: None,
        },
        policy: None,
    };
    test_device.set_identity((EcSignKey::P256(priv_key), identity), iana::Algorithm::ES256);
    ag_test::test_get_identity(&test_device, &crate::BoringEcDsa);
}

#[test]
fn test_get_identity_with_root_key_in_correct_canonical_form() {
    // Check that the `Identity` returned from `get_identity` in the `Device` trait passes the
    // validation, given that the root key is in correct canonical form.
    let test_device = crate::test_device::AgDevice::default();
    let (priv_key, mut pub_key) = crate::ec::create_p256_key_pair(iana::Algorithm::ES256).unwrap();
    let mut test_params = Vec::<(Label, Value)>::new();
    for param in pub_key.params {
        test_params.push(param);
    }
    test_params.push((Label::Int(23), Value::Text("test1".to_string())));
    test_params.push((Label::Int(1234), Value::Text("test2".to_string())));
    pub_key.params = test_params;
    pub_key.canonicalize(CborOrdering::Lexicographic);
    let identity = Identity {
        version: IDENTITY_VERSION,
        cert_chain: CertChain {
            version: EXPLICIT_KEY_DICE_CERT_CHAIN_VERSION,
            root_key: EcVerifyKey::P256(pub_key),
            dice_cert_chain: None,
        },
        policy: None,
    };
    test_device.set_identity((EcSignKey::P256(priv_key), identity), iana::Algorithm::ES256);
    ag_test::test_get_identity(&test_device, &crate::BoringEcDsa);
}
