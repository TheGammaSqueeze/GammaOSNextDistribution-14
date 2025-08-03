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

use nix::unistd::{Gid, Uid};
use serde::{Deserialize, Serialize};

use std::path::PathBuf;
use std::process::{Command, Output};

use openssl::bn::BigNum;
use openssl::encrypt::Encrypter;
use openssl::error::ErrorStack;
use openssl::hash::MessageDigest;
use openssl::nid::Nid;
use openssl::pkey::PKey;
use openssl::pkey::Public;
use openssl::rsa::Padding;
use openssl::sign::Verifier;
use openssl::x509::X509;

use binder::wait_for_interface;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    BlockMode::BlockMode, Digest::Digest, ErrorCode::ErrorCode,
    KeyParameterValue::KeyParameterValue, KeyPurpose::KeyPurpose, PaddingMode::PaddingMode,
    SecurityLevel::SecurityLevel, Tag::Tag,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    CreateOperationResponse::CreateOperationResponse, Domain::Domain,
    IKeystoreOperation::IKeystoreOperation, IKeystoreSecurityLevel::IKeystoreSecurityLevel,
    IKeystoreService::IKeystoreService, KeyDescriptor::KeyDescriptor, KeyMetadata::KeyMetadata,
    KeyParameters::KeyParameters, ResponseCode::ResponseCode,
};

use packagemanager_aidl::aidl::android::content::pm::IPackageManagerNative::IPackageManagerNative;

use keystore2_test_utils::{
    authorizations, get_keystore_service, key_generations, key_generations::Error, run_as,
};

/// This enum is used to communicate between parent and child processes.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum TestOutcome {
    Ok,
    BackendBusy,
    InvalidHandle,
    OtherErr,
}

/// This is used to notify the child or parent process that the expected state is reched.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BarrierReached;

/// Forced operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ForcedOp(pub bool);

/// Sample plain text input for encrypt operation.
pub const SAMPLE_PLAIN_TEXT: &[u8] = b"my message 11111";

pub const PACKAGE_MANAGER_NATIVE_SERVICE: &str = "package_native";
pub const APP_ATTEST_KEY_FEATURE: &str = "android.hardware.keystore.app_attest_key";
pub const DEVICE_ID_ATTESTATION_FEATURE: &str = "android.software.device_id_attestation";

/// Determines whether app_attest_key_feature is supported or not.
pub fn app_attest_key_feature_exists() -> bool {
    let pm = wait_for_interface::<dyn IPackageManagerNative>(PACKAGE_MANAGER_NATIVE_SERVICE)
        .expect("Failed to get package manager native service.");

    pm.hasSystemFeature(APP_ATTEST_KEY_FEATURE, 0).expect("hasSystemFeature failed.")
}

/// Determines whether device_id_attestation is supported or not.
pub fn device_id_attestation_feature_exists() -> bool {
    let pm = wait_for_interface::<dyn IPackageManagerNative>(PACKAGE_MANAGER_NATIVE_SERVICE)
        .expect("Failed to get package manager native service.");

    pm.hasSystemFeature(DEVICE_ID_ATTESTATION_FEATURE, 0).expect("hasSystemFeature failed.")
}

/// Determines whether to skip device id attestation tests on GSI build with API level < 34.
pub fn skip_device_id_attest_tests() -> bool {
    // b/298586194, there are some devices launched with Android T, and they will be receiving
    // only system update and not vendor update, newly added attestation properties
    // (ro.product.*_for_attestation) reading logic would not be available for such devices
    // hence skipping this test for such scenario.

    // This file is only present on GSI builds.
    let gsi_marker = PathBuf::from("/system/system_ext/etc/init/init.gsi.rc");

    get_vsr_api_level() < 34 && gsi_marker.as_path().is_file()
}

#[macro_export]
macro_rules! skip_test_if_no_app_attest_key_feature {
    () => {
        if !app_attest_key_feature_exists() {
            return;
        }
    };
}

#[macro_export]
macro_rules! skip_test_if_no_device_id_attestation_feature {
    () => {
        if !device_id_attestation_feature_exists() {
            return;
        }
    };
}

#[macro_export]
macro_rules! skip_device_id_attestation_tests {
    () => {
        if skip_device_id_attest_tests() {
            return;
        }
    };
}

#[macro_export]
macro_rules! skip_tests_if_keymaster_impl_present {
    () => {
        if !key_generations::has_default_keymint() {
            return;
        }
    };
}

/// Generate EC key and grant it to the list of users with given access vector.
/// Returns the list of granted keys `nspace` values in the order of given grantee uids.
pub fn generate_ec_key_and_grant_to_users(
    keystore2: &binder::Strong<dyn IKeystoreService>,
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    alias: Option<String>,
    grantee_uids: Vec<i32>,
    access_vector: i32,
) -> Result<Vec<i64>, binder::Status> {
    let key_metadata =
        key_generations::generate_ec_p256_signing_key(sec_level, Domain::APP, -1, alias, None)?;

    let mut granted_keys = Vec::new();

    for uid in grantee_uids {
        let granted_key = keystore2.grant(&key_metadata.key, uid, access_vector)?;
        assert_eq!(granted_key.domain, Domain::GRANT);
        granted_keys.push(granted_key.nspace);
    }

    Ok(granted_keys)
}

/// Generate a EC_P256 key using given domain, namespace and alias.
/// Create an operation using the generated key and perform sample signing operation.
pub fn create_signing_operation(
    forced_op: ForcedOp,
    op_purpose: KeyPurpose,
    op_digest: Digest,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
) -> binder::Result<CreateOperationResponse> {
    let keystore2 = get_keystore_service();
    let sec_level = keystore2.getSecurityLevel(SecurityLevel::TRUSTED_ENVIRONMENT).unwrap();

    let key_metadata =
        key_generations::generate_ec_p256_signing_key(&sec_level, domain, nspace, alias, None)
            .unwrap();

    sec_level.createOperation(
        &key_metadata.key,
        &authorizations::AuthSetBuilder::new().purpose(op_purpose).digest(op_digest),
        forced_op.0,
    )
}

/// Performs sample signing operation.
pub fn perform_sample_sign_operation(
    op: &binder::Strong<dyn IKeystoreOperation>,
) -> Result<(), binder::Status> {
    op.update(b"my message")?;
    let sig = op.finish(None, None)?;
    assert!(sig.is_some());
    Ok(())
}

/// Perform sample HMAC sign and verify operations.
pub fn perform_sample_hmac_sign_verify_op(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    key: &KeyDescriptor,
) {
    let sign_op = sec_level
        .createOperation(
            key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::SIGN)
                .digest(Digest::SHA_2_256)
                .mac_length(256),
            false,
        )
        .unwrap();
    assert!(sign_op.iOperation.is_some());

    let op = sign_op.iOperation.unwrap();
    op.update(b"my message").unwrap();
    let sig = op.finish(None, None).unwrap();
    assert!(sig.is_some());

    let sig = sig.unwrap();
    let verify_op = sec_level
        .createOperation(
            key,
            &authorizations::AuthSetBuilder::new()
                .purpose(KeyPurpose::VERIFY)
                .digest(Digest::SHA_2_256),
            false,
        )
        .unwrap();
    assert!(verify_op.iOperation.is_some());

    let op = verify_op.iOperation.unwrap();
    let result = op.finish(Some(b"my message"), Some(&sig)).unwrap();
    assert!(result.is_none());
}

/// Map KeyMint Digest values to OpenSSL MessageDigest.
pub fn get_openssl_digest_mode(digest: Option<Digest>) -> MessageDigest {
    match digest {
        Some(Digest::MD5) => MessageDigest::md5(),
        Some(Digest::SHA1) => MessageDigest::sha1(),
        Some(Digest::SHA_2_224) => MessageDigest::sha224(),
        Some(Digest::SHA_2_256) => MessageDigest::sha256(),
        Some(Digest::SHA_2_384) => MessageDigest::sha384(),
        Some(Digest::SHA_2_512) => MessageDigest::sha512(),
        _ => MessageDigest::sha256(),
    }
}

/// Map KeyMint PaddingMode values to OpenSSL Padding.
pub fn get_openssl_padding_mode(padding: PaddingMode) -> Padding {
    match padding {
        PaddingMode::RSA_OAEP => Padding::PKCS1_OAEP,
        PaddingMode::RSA_PSS => Padding::PKCS1_PSS,
        PaddingMode::RSA_PKCS1_1_5_SIGN => Padding::PKCS1,
        PaddingMode::RSA_PKCS1_1_5_ENCRYPT => Padding::PKCS1,
        _ => Padding::NONE,
    }
}

/// Perform sample sign and verify operations using RSA or EC key.
pub fn perform_sample_asym_sign_verify_op(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    key_metadata: &KeyMetadata,
    padding: Option<PaddingMode>,
    digest: Option<Digest>,
) {
    let mut authorizations = authorizations::AuthSetBuilder::new().purpose(KeyPurpose::SIGN);
    if let Some(value) = padding {
        authorizations = authorizations.padding_mode(value);
    }
    if let Some(value) = digest {
        authorizations = authorizations.digest(value);
    }

    let sign_op = sec_level.createOperation(&key_metadata.key, &authorizations, false).unwrap();
    assert!(sign_op.iOperation.is_some());

    let op = sign_op.iOperation.unwrap();
    op.update(b"my message").unwrap();
    let sig = op.finish(None, None).unwrap();
    assert!(sig.is_some());

    let sig = sig.unwrap();
    let cert_bytes = key_metadata.certificate.as_ref().unwrap();
    let cert = X509::from_der(cert_bytes.as_ref()).unwrap();
    let pub_key = cert.public_key().unwrap();
    let mut verifier = Verifier::new(get_openssl_digest_mode(digest), pub_key.as_ref()).unwrap();
    if let Some(value) = padding {
        verifier.set_rsa_padding(get_openssl_padding_mode(value)).unwrap();
    }
    verifier.update(b"my message").unwrap();
    assert!(verifier.verify(&sig).unwrap());
}

/// Create new operation on child proc and perform simple operation after parent notification.
///
/// # Safety
///
/// Must only be called from a single-threaded process.
pub unsafe fn execute_op_run_as_child(
    target_ctx: &'static str,
    domain: Domain,
    nspace: i64,
    alias: Option<String>,
    auid: Uid,
    agid: Gid,
    forced_op: ForcedOp,
) -> run_as::ChildHandle<TestOutcome, BarrierReached> {
    // SAFETY: The caller guarantees that there are no other threads.
    unsafe {
        run_as::run_as_child(target_ctx, auid, agid, move |reader, writer| {
            let result = key_generations::map_ks_error(create_signing_operation(
                forced_op,
                KeyPurpose::SIGN,
                Digest::SHA_2_256,
                domain,
                nspace,
                alias,
            ));

            // Let the parent know that an operation has been started, then
            // wait until the parent notifies us to continue, so the operation
            // remains open.
            writer.send(&BarrierReached {});
            reader.recv();

            // Continue performing the operation after parent notifies.
            match &result {
                Ok(CreateOperationResponse { iOperation: Some(op), .. }) => {
                    match key_generations::map_ks_error(perform_sample_sign_operation(op)) {
                        Ok(()) => TestOutcome::Ok,
                        Err(Error::Km(ErrorCode::INVALID_OPERATION_HANDLE)) => {
                            TestOutcome::InvalidHandle
                        }
                        Err(e) => panic!("Error in performing op: {:#?}", e),
                    }
                }
                Ok(_) => TestOutcome::OtherErr,
                Err(Error::Rc(ResponseCode::BACKEND_BUSY)) => TestOutcome::BackendBusy,
                _ => TestOutcome::OtherErr,
            }
        })
        .expect("Failed to create an operation.")
    }
}

/// Get NONCE value from given key parameters list.
pub fn get_op_nonce(parameters: &KeyParameters) -> Option<Vec<u8>> {
    for key_param in &parameters.keyParameter {
        if key_param.tag == Tag::NONCE {
            if let KeyParameterValue::Blob(val) = &key_param.value {
                return Some(val.clone());
            }
        }
    }
    None
}

/// This performs sample encryption operation with given symmetric key (AES/3DES).
/// It encrypts `SAMPLE_PLAIN_TEXT` of length 128-bits.
pub fn perform_sample_sym_key_encrypt_op(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    padding_mode: PaddingMode,
    block_mode: BlockMode,
    nonce: &mut Option<Vec<u8>>,
    mac_len: Option<i32>,
    key: &KeyDescriptor,
) -> binder::Result<Option<Vec<u8>>> {
    let mut op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::ENCRYPT)
        .padding_mode(padding_mode)
        .block_mode(block_mode);
    if let Some(value) = nonce {
        op_params = op_params.nonce(value.to_vec());
    }

    if let Some(val) = mac_len {
        op_params = op_params.mac_length(val);
    }

    let op_response = sec_level.createOperation(key, &op_params, false)?;
    assert!(op_response.iOperation.is_some());
    let op = op_response.iOperation.unwrap();
    if op_response.parameters.is_some() && nonce.is_none() {
        *nonce = get_op_nonce(&op_response.parameters.unwrap());
    }
    op.finish(Some(SAMPLE_PLAIN_TEXT), None)
}

/// This performs sample decryption operation with given symmetric key (AES/3DES).
pub fn perform_sample_sym_key_decrypt_op(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    input: &[u8],
    padding_mode: PaddingMode,
    block_mode: BlockMode,
    nonce: &mut Option<Vec<u8>>,
    mac_len: Option<i32>,
    key: &KeyDescriptor,
) -> binder::Result<Option<Vec<u8>>> {
    let mut op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::DECRYPT)
        .padding_mode(padding_mode)
        .block_mode(block_mode);
    if let Some(value) = nonce {
        op_params = op_params.nonce(value.to_vec());
    }

    if let Some(val) = mac_len {
        op_params = op_params.mac_length(val);
    }

    let op_response = sec_level.createOperation(key, &op_params, false)?;
    assert!(op_response.iOperation.is_some());
    let op = op_response.iOperation.unwrap();
    op.finish(Some(input), None)
}

/// Delete a key with domain APP.
pub fn delete_app_key(
    keystore2: &binder::Strong<dyn IKeystoreService>,
    alias: &str,
) -> binder::Result<()> {
    keystore2.deleteKey(&KeyDescriptor {
        domain: Domain::APP,
        nspace: -1,
        alias: Some(alias.to_string()),
        blob: None,
    })
}

/// Deletes all entries from keystore.
pub fn delete_all_entries(keystore2: &binder::Strong<dyn IKeystoreService>) {
    while keystore2.getNumberOfEntries(Domain::APP, -1).unwrap() != 0 {
        let key_descriptors = keystore2.listEntries(Domain::APP, -1).unwrap();
        key_descriptors.into_iter().map(|key| key.alias.unwrap()).for_each(|alias| {
            delete_app_key(keystore2, &alias).unwrap();
        });
    }
    assert!(keystore2.getNumberOfEntries(Domain::APP, -1).unwrap() == 0);
}

/// Encrypt the secure key with given transport key.
pub fn encrypt_secure_key(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
    secure_key: &[u8],
    aad: &[u8],
    nonce: Vec<u8>,
    mac_len: i32,
    key: &KeyDescriptor,
) -> binder::Result<Option<Vec<u8>>> {
    let op_params = authorizations::AuthSetBuilder::new()
        .purpose(KeyPurpose::ENCRYPT)
        .padding_mode(PaddingMode::NONE)
        .block_mode(BlockMode::GCM)
        .nonce(nonce)
        .mac_length(mac_len);

    let op_response = sec_level.createOperation(key, &op_params, false)?;

    let op = op_response.iOperation.unwrap();
    op.updateAad(aad)?;
    op.finish(Some(secure_key), None)
}

/// Encrypt the transport key with given RSA wrapping key.
pub fn encrypt_transport_key(
    transport_key: &[u8],
    pkey: &PKey<Public>,
) -> Result<Vec<u8>, ErrorStack> {
    let mut encrypter = Encrypter::new(pkey).unwrap();
    encrypter.set_rsa_padding(Padding::PKCS1_OAEP).unwrap();
    encrypter.set_rsa_oaep_md(MessageDigest::sha256()).unwrap();
    encrypter.set_rsa_mgf1_md(MessageDigest::sha1()).unwrap();

    let input = transport_key.to_vec();
    let buffer_len = encrypter.encrypt_len(&input).unwrap();
    let mut encoded = vec![0u8; buffer_len];
    let encoded_len = encrypter.encrypt(&input, &mut encoded).unwrap();
    let encoded = &encoded[..encoded_len];

    Ok(encoded.to_vec())
}

/// List aliases using given `startingPastAlias` and verify that the fetched list is matching with
/// the expected list of aliases.
pub fn verify_aliases(
    keystore2: &binder::Strong<dyn IKeystoreService>,
    starting_past_alias: Option<&str>,
    expected_aliases: Vec<String>,
) {
    let key_descriptors =
        keystore2.listEntriesBatched(Domain::APP, -1, starting_past_alias).unwrap();

    assert_eq!(key_descriptors.len(), expected_aliases.len());
    assert!(key_descriptors
        .iter()
        .all(|key| expected_aliases.contains(key.alias.as_ref().unwrap())));
}

// Get the value of the given system property, if the given system property doesn't exist
// then returns an empty byte vector.
pub fn get_system_prop(name: &str) -> Vec<u8> {
    match rustutils::system_properties::read(name) {
        Ok(Some(value)) => {
            return value.as_bytes().to_vec();
        }
        _ => {
            vec![]
        }
    }
}

fn get_integer_system_prop(name: &str) -> Option<i32> {
    let val = get_system_prop(name);
    if val.is_empty() {
        return None;
    }
    let val = std::str::from_utf8(&val).ok()?;
    val.parse::<i32>().ok()
}

pub fn get_vsr_api_level() -> i32 {
    if let Some(api_level) = get_integer_system_prop("ro.vendor.api_level") {
        return api_level;
    }

    let vendor_api_level = get_integer_system_prop("ro.board.api_level")
        .or_else(|| get_integer_system_prop("ro.board.first_api_level"));
    let product_api_level = get_integer_system_prop("ro.product.first_api_level")
        .or_else(|| get_integer_system_prop("ro.build.version.sdk"));

    match (vendor_api_level, product_api_level) {
        (Some(v), Some(p)) => std::cmp::min(v, p),
        (Some(v), None) => v,
        (None, Some(p)) => p,
        _ => panic!("Could not determine VSR API level"),
    }
}

/// Determines whether the SECOND-IMEI can be used as device attest-id.
pub fn is_second_imei_id_attestation_required(
    keystore2: &binder::Strong<dyn IKeystoreService>,
) -> bool {
    keystore2.getInterfaceVersion().unwrap() >= 3 && get_vsr_api_level() > 33
}

/// Run a service command and collect the output.
pub fn run_service_command(command: &[&str]) -> std::io::Result<Output> {
    Command::new("cmd").args(command).output()
}

/// Get IMEI from telephony service.
pub fn get_imei(slot: i32) -> Option<Vec<u8>> {
    let mut cmd = vec!["phone", "get-imei"];
    let slot_str = slot.to_string();
    cmd.push(slot_str.as_str());
    let output = run_service_command(&cmd).unwrap();
    if output.status.success() {
        let stdout = String::from_utf8(output.stdout).unwrap();
        let mut split_out = stdout.split_whitespace();
        let imei = split_out.next_back().unwrap();
        if imei == "null" {
            return None;
        }
        return Some(imei.as_bytes().to_vec());
    }

    None
}

/// Get value of the given attestation id.
pub fn get_attest_id_value(attest_id: Tag, prop_name: &str) -> Option<Vec<u8>> {
    match attest_id {
        Tag::ATTESTATION_ID_IMEI => get_imei(0),
        Tag::ATTESTATION_ID_SECOND_IMEI => get_imei(1),
        Tag::ATTESTATION_ID_SERIAL => Some(get_system_prop(format!("ro.{}", prop_name).as_str())),
        _ => {
            let prop_val =
                get_system_prop(format!("ro.product.{}_for_attestation", prop_name).as_str());
            if !prop_val.is_empty() {
                Some(prop_val)
            } else {
                let prop_val = get_system_prop(format!("ro.product.vendor.{}", prop_name).as_str());
                if !prop_val.is_empty() {
                    Some(prop_val)
                } else {
                    Some(get_system_prop(format!("ro.product.{}", prop_name).as_str()))
                }
            }
        }
    }
}

pub fn verify_certificate_subject_name(cert_bytes: &[u8], expected_subject: &[u8]) {
    let cert = X509::from_der(cert_bytes).unwrap();
    let subject = cert.subject_name();
    let cn = subject.entries_by_nid(Nid::COMMONNAME).next().unwrap();
    assert_eq!(cn.data().as_slice(), expected_subject);
}

pub fn verify_certificate_serial_num(cert_bytes: &[u8], expected_serial_num: &BigNum) {
    let cert = X509::from_der(cert_bytes).unwrap();
    let serial_num = cert.serial_number();
    assert_eq!(serial_num.to_bn().as_ref().unwrap(), expected_serial_num);
}
