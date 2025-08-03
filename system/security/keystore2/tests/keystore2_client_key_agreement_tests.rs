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

use nix::unistd::getuid;

use openssl::ec::{EcGroup, EcKey};
use openssl::error::ErrorStack;
use openssl::nid::Nid;
use openssl::pkey::{PKey, PKeyRef, Private, Public};
use openssl::pkey_ctx::PkeyCtx;
use openssl::x509::X509;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Digest::Digest, EcCurve::EcCurve, ErrorCode::ErrorCode, KeyPurpose::KeyPurpose,
    SecurityLevel::SecurityLevel,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    KeyMetadata::KeyMetadata,
};

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error,
};

/// This macro is used to verify that the key agreement works for the given curve.
macro_rules! test_ec_key_agree {
    ( $test_name:ident, $ec_curve:expr ) => {
        #[test]
        fn $test_name() {
            perform_ec_key_agreement($ec_curve);
        }
    };
}

// Get the KeyMint key's public part.
fn get_keymint_public_key(keymint_key: &KeyMetadata) -> Result<PKey<Public>, ErrorStack> {
    let cert_bytes = keymint_key.certificate.as_ref().unwrap();
    let cert = X509::from_der(cert_bytes.as_ref()).unwrap();
    cert.public_key()
}

// Perform local ECDH between the two keys and check the derived secrets are the same.
fn check_agreement(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    keymint_key: &KeyDescriptor,
    keymint_pub_key: &PKey<Public>,
    local_key: &PKeyRef<Private>,
    local_pub_key: &[u8],
) {
    let authorizations = authorizations::AuthSetBuilder::new().purpose(KeyPurpose::AGREE_KEY);
    let key_agree_op = sec_level.createOperation(keymint_key, &authorizations, false).unwrap();
    assert!(key_agree_op.iOperation.is_some());

    let op = key_agree_op.iOperation.unwrap();
    let secret = op.finish(Some(local_pub_key), None).unwrap();
    assert!(secret.is_some());

    let mut ctx = PkeyCtx::new(local_key).unwrap();
    ctx.derive_init().unwrap();
    ctx.derive_set_peer(keymint_pub_key).unwrap();
    let mut peer_secret = vec![];
    ctx.derive_to_vec(&mut peer_secret).unwrap();

    assert_eq!(secret.unwrap(), peer_secret);
}

fn ec_curve_to_openrssl_curve_name(ec_curve: &EcCurve) -> Nid {
    match *ec_curve {
        EcCurve::P_224 => Nid::SECP224R1,
        EcCurve::P_256 => Nid::X9_62_PRIME256V1,
        EcCurve::P_384 => Nid::SECP384R1,
        EcCurve::P_521 => Nid::SECP521R1,
        _ => Nid::UNDEF,
    }
}

/// Generate two EC keys with given curve from KeyMint and OpeanSSL. Perform local ECDH between
/// them and verify that the derived secrets are the same.
fn perform_ec_key_agreement(ec_curve: EcCurve) {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();
    let openssl_ec_curve = ec_curve_to_openrssl_curve_name(&ec_curve);

    let alias = format!("ks_ec_test_key_agree_{}", getuid());
    let keymint_key = key_generations::generate_ec_agree_key(
        &sec_level,
        ec_curve,
        Digest::SHA_2_256,
        Domain::APP,
        -1,
        Some(alias),
    )
    .unwrap();

    let keymint_pub_key = get_keymint_public_key(&keymint_key).unwrap();

    let group = EcGroup::from_curve_name(openssl_ec_curve).unwrap();
    let ec_key = EcKey::generate(&group).unwrap();
    let local_key = PKey::from_ec_key(ec_key).unwrap();
    let local_pub_key = local_key.public_key_to_der().unwrap();

    check_agreement(&sec_level, &keymint_key.key, &keymint_pub_key, &local_key, &local_pub_key);
}

test_ec_key_agree!(test_ec_p224_key_agreement, EcCurve::P_224);
test_ec_key_agree!(test_ec_p256_key_agreement, EcCurve::P_256);
test_ec_key_agree!(test_ec_p384_key_agreement, EcCurve::P_384);
test_ec_key_agree!(test_ec_p521_key_agreement, EcCurve::P_521);

/// Generate two EC keys with curve `CURVE_25519` from KeyMint and OpeanSSL.
/// Perform local ECDH between them and verify that the derived secrets are the same.
#[test]
fn keystore2_ec_25519_agree_key_success() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_ec_25519_test_key_agree_{}", getuid());
    let keymint_key = key_generations::generate_ec_agree_key(
        &sec_level,
        EcCurve::CURVE_25519,
        Digest::NONE,
        Domain::APP,
        -1,
        Some(alias),
    )
    .unwrap();

    let keymint_pub_key = get_keymint_public_key(&keymint_key).unwrap();

    let local_key = PKey::generate_x25519().unwrap();
    let local_pub_key = local_key.public_key_to_der().unwrap();

    check_agreement(&sec_level, &keymint_key.key, &keymint_pub_key, &local_key, &local_pub_key);
}

/// Generate two EC keys with different curves and try to perform local ECDH. Since keys are using
/// different curves operation should fail with `ErrorCode:INVALID_ARGUMENT`.
#[test]
fn keystore2_ec_agree_key_with_different_curves_fail() {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let alias = format!("ks_test_key_agree_fail{}", getuid());
    let keymint_key = key_generations::generate_ec_agree_key(
        &sec_level,
        EcCurve::P_256,
        Digest::SHA_2_256,
        Domain::APP,
        -1,
        Some(alias),
    )
    .unwrap();

    let local_key = PKey::generate_x25519().unwrap();
    let local_pub_key = local_key.public_key_to_der().unwrap();

    // If the keys are using different curves KeyMint should fail with
    // ErrorCode:INVALID_ARGUMENT.
    let authorizations = authorizations::AuthSetBuilder::new().purpose(KeyPurpose::AGREE_KEY);
    let key_agree_op = sec_level.createOperation(&keymint_key.key, &authorizations, false).unwrap();
    assert!(key_agree_op.iOperation.is_some());

    let op = key_agree_op.iOperation.unwrap();
    let result = key_generations::map_ks_error(op.finish(Some(&local_pub_key), None));
    assert!(result.is_err());
    assert_eq!(Error::Km(ErrorCode::INVALID_ARGUMENT), result.unwrap_err());
}
