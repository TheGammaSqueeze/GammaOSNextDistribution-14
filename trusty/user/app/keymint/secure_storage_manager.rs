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
//! Module used to interact with keymint secure storage data.
use crate::keybox;
use crate::keymaster_attributes;
use alloc::{format, string::String, vec::Vec};
use kmr_common::{
    crypto::{self, KeyMaterial},
    km_err, try_to_vec, vec_try, vec_try_with_capacity,
    wire::keymint,
    wire::AttestationIdInfo,
    Error,
};
use kmr_ta::device::{
    RetrieveAttestationIds, RetrieveCertSigningInfo, SigningAlgorithm, SigningKeyType,
};
use log::info;
use protobuf::{self, Message};
use storage::{OpenMode, Port, SecureFile, Session};
use trusty_sys;

#[cfg(feature = "soft_attestation_fallback")]
mod software;

/// Name of file holding attestation device ID information; matches the `kAttestationIdsFileName`
/// value in `secure_storage_manager.cpp` for back-compatibility.
const KM_ATTESTATION_ID_FILENAME: &'static str = "AttestationIds";

/// Filename prefix for files holding attestation keys and certificates; matches the
/// `kAttestKeyCertPrefix` value in `secure_storage_manager.cpp` for back-compatibility.
const KM_ATTESTATION_KEY_CERT_PREFIX: &'static str = "AttestKeyCert";

/// Maximum size of each attestation certificate
const MAX_CERT_SIZE: usize = 2048;
/// Maximum number of attestation certificates
const MAX_CERT_CHAIN_LEN: usize = 3;

/// Return the filename for the file holding attestation keys and certificates for the specified
/// signing algorithm.
fn get_key_slot_file_name(algorithm: SigningAlgorithm) -> String {
    let suffix = match algorithm {
        SigningAlgorithm::Ec => "ec",
        SigningAlgorithm::Rsa => "rsa",
    };
    format!("{}.{}", KM_ATTESTATION_KEY_CERT_PREFIX, suffix)
}

// `session` and `secure_file` are of type `Option` because close() takes self by value, so this was
// needed for the `Drop` implementation. The intent though is that they should always be populated
// on a OpenSecureFile object; which is `OpenSecureFile::new` behavior.
struct OpenSecureFile {
    session: Option<Session>,
    secure_file: Option<SecureFile>,
}

impl OpenSecureFile {
    /// Opens a secure storage session and creates the requested file
    fn new(file_name: &str) -> Result<Self, Error> {
        let mut session = Session::new(Port::TamperProof, true).map_err(|e| {
            km_err!(SecureHwCommunicationFailed, "couldn't create storage session: {:?}", e)
        })?;
        let secure_file = session.open_file(file_name, OpenMode::Create).map_err(|e| {
            km_err!(SecureHwCommunicationFailed, "couldn't create file {}: {:?}", file_name, e)
        })?;
        Ok(OpenSecureFile { session: Some(session), secure_file: Some(secure_file) })
    }

    /// Writes provided data in the previously opened file
    fn write_all(&mut self, data: &[u8]) -> Result<(), Error> {
        // Even though we are handling the case when secure_file and session are None, this is not
        // expected; if an OpenSecureFile object exists its `secure_file` and `session` elements
        // shall be populated.
        let session =
            self.session.as_mut().ok_or(km_err!(UnknownError, "session shouldn't ever be None"))?;
        let file = self
            .secure_file
            .as_mut()
            .ok_or(km_err!(UnknownError, "secure_file shouldn't ever be None"))?;
        session.write_all(file, data).map_err(|e| {
            km_err!(SecureHwCommunicationFailed, "failed to write data; received error: {:?}", e)
        })
    }

    /// Close the session and file handlers by taking ownership and letting the value be dropped
    #[cfg(test)]
    fn close(self) {}
}

impl Drop for OpenSecureFile {
    fn drop(&mut self) {
        // Even though we are handling the case when secure_file and session are None, this is not
        // expected; if an OpenSecureFile object exists its `secure_file` and `session` elements
        // shall be populated.
        if let Some(file) = self.secure_file.take() {
            file.close();
        }
        if let Some(session) = self.session.take() {
            session.close();
        }
    }
}

/// Creates an empty attestation IDs file
fn create_attestation_id_file() -> Result<OpenSecureFile, Error> {
    OpenSecureFile::new(KM_ATTESTATION_ID_FILENAME)
}

/// Creates and empty attestation key/certificates file for the given algorithm
fn create_attestation_key_file(algorithm: SigningAlgorithm) -> Result<OpenSecureFile, Error> {
    let file_name = get_key_slot_file_name(algorithm);
    OpenSecureFile::new(&file_name)
}

fn write_protobuf_to_attestation_key_file(
    algorithm: SigningAlgorithm,
    attestation_key_data: keymaster_attributes::AttestationKey,
) -> Result<(), Error> {
    let serialized_buffer = attestation_key_data.write_to_bytes().map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "couldn't serialize attestationKey: {:?}", e)
    })?;
    let mut file = create_attestation_key_file(algorithm)?;
    file.write_all(&serialized_buffer).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to provision attestation key file: {:?}", e)
    })?;
    Ok(())
}

/// Unwraps a keybox wrapped key and uses it to provision the key on the device.
pub(crate) fn set_wrapped_attestation_key(
    algorithm: SigningAlgorithm,
    key_data: &[u8],
) -> Result<(), Error> {
    let unwrapped_key = keybox::keybox_unwrap(key_data)?;
    provision_attestation_key_file(algorithm, &unwrapped_key)
}

/// Tries to read the file containing the attestation key and certificates and only replaces the key
/// section. If the file doesn't exist it will create it and save the provided key.
pub(crate) fn provision_attestation_key_file(
    algorithm: SigningAlgorithm,
    key_data: &[u8],
) -> Result<(), Error> {
    let mut attestation_key = read_attestation_key_content(algorithm)?;
    attestation_key.set_key(try_to_vec(key_data)?);

    write_protobuf_to_attestation_key_file(algorithm, attestation_key)
}

/// Tries to read the file containing the attestation key and certificates and adds a certificate to
/// it if there is still space left on the certificate section. If the file doesn't exist it will
/// create it and save the provided certificate.
pub(crate) fn append_attestation_cert_chain(
    algorithm: SigningAlgorithm,
    cert_data: &[u8],
) -> Result<(), Error> {
    if cert_data.len() == 0 {
        return Err(km_err!(InvalidInputLength, "received a certificate of length 0"));
    }

    if cert_data.len() > MAX_CERT_SIZE {
        return Err(km_err!(
            InvalidArgument,
            "certificate is too big. Size: {}, max size {}",
            cert_data.len(),
            MAX_CERT_SIZE
        ));
    }

    let mut attestation_key_data = read_attestation_key_content(algorithm)?;
    let cert_chain_len = attestation_key_data.get_certs().len();

    if cert_chain_len >= MAX_CERT_CHAIN_LEN {
        return Err(km_err!(
            InvalidArgument,
            "cannot accept more certificates, {} already provisioned",
            cert_chain_len
        ));
    }

    let mut cert = keymaster_attributes::AttestationCert::new();
    cert.set_content(try_to_vec(cert_data)?);
    attestation_key_data.mut_certs().push(cert);

    write_protobuf_to_attestation_key_file(algorithm, attestation_key_data)
}

/// Tries to read the file containing the attestation key delete only the certificate section.
pub(crate) fn clear_attestation_cert_chain(algorithm: SigningAlgorithm) -> Result<(), Error> {
    let mut attestation_key_data = read_attestation_key_content(algorithm)?;
    if attestation_key_data.get_certs().len() == 0 {
        // No certs found, nothing to delete.
        return Ok(());
    }
    attestation_key_data.clear_certs();
    write_protobuf_to_attestation_key_file(algorithm, attestation_key_data)?;
    // Checking that the certificates were indeed deleted
    let attestation_key_data = read_attestation_key_content(algorithm)?;
    let cert_chain_len = attestation_key_data.get_certs().len();
    if cert_chain_len != 0 {
        log::error!("Couldn't delete all certificates, found {}", cert_chain_len);
        return Err(km_err!(
            UnknownError,
            "couldn't delete all certificates, found {}",
            cert_chain_len
        ));
    }
    Ok(())
}

/// Creates a new attestation IDs file and saves the provided data there
pub(crate) fn provision_attestation_id_file(
    brand: &[u8],
    product: &[u8],
    device: &[u8],
    serial: &[u8],
    imei: &[u8],
    meid: &[u8],
    manufacturer: &[u8],
    model: &[u8],
    maybe_imei2: Option<&[u8]>,
) -> Result<(), Error> {
    let mut file = create_attestation_id_file()?;

    let mut attestation_ids = keymaster_attributes::AttestationIds::new();

    if brand.len() > 0 {
        attestation_ids.set_brand(try_to_vec(brand)?);
    }
    if device.len() > 0 {
        attestation_ids.set_device(try_to_vec(device)?);
    }
    if product.len() > 0 {
        attestation_ids.set_product(try_to_vec(product)?);
    }
    if serial.len() > 0 {
        attestation_ids.set_serial(try_to_vec(serial)?);
    }
    if imei.len() > 0 {
        attestation_ids.set_imei(try_to_vec(imei)?);
    }
    if meid.len() > 0 {
        attestation_ids.set_meid(try_to_vec(meid)?);
    }
    if manufacturer.len() > 0 {
        attestation_ids.set_manufacturer(try_to_vec(manufacturer)?);
    }
    if model.len() > 0 {
        attestation_ids.set_model(try_to_vec(model)?);
    }
    match maybe_imei2 {
        Some(imei2) if imei2.len() > 0 => {
            attestation_ids.set_second_imei(try_to_vec(imei2)?);
        }
        _ => (),
    }

    let serialized_buffer = attestation_ids.write_to_bytes().map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "couldn't serialize attestationIds: {:?}", e)
    })?;

    file.write_all(&serialized_buffer).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to provision attestation IDs file: {:?}", e)
    })?;

    Ok(())
}

/// Delete all attestation IDs from secure storage.
pub(crate) fn delete_attestation_ids() -> Result<(), Error> {
    let mut session = Session::new(Port::TamperProof, true).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to connect to storage port: {:?}", e)
    })?;
    session.remove(KM_ATTESTATION_ID_FILENAME).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to delete attestation IDs file: {:?}", e)
    })?;
    Ok(())
}

/// Return the contents of the specified file in secure storage.
fn get_file_contents(file_name: &str) -> Result<Option<Vec<u8>>, Error> {
    let mut session = Session::new(Port::TamperProof, true).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to connect to storage port: {:?}", e)
    })?;
    // Distinguishing between file not found and other errors, so we can match c++ behavior when
    // retrieving attestation IDs on unprovisioned devices.
    let file = match session.open_file(file_name, OpenMode::Open) {
        Ok(file) => file,
        Err(storage::Error::Code(trusty_sys::Error::NotFound)) => return Ok(None),
        Err(e) => {
            return Err(km_err!(
                SecureHwCommunicationFailed,
                "failed to open '{}': {:?}",
                file_name,
                e
            ));
        }
    };
    let size = session.get_size(&file).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to get size for '{}': {:?}", file_name, e)
    })?;
    let mut buffer = vec_try![0; size]?;
    let content = session.read_all(&file, buffer.as_mut_slice()).map_err(|e| {
        km_err!(SecureHwCommunicationFailed, "failed to read '{}': {:?}", file_name, e)
    })?;
    let total_size = content.len();
    buffer.resize(total_size, 0);
    Ok(Some(buffer))
}

/// Retrieve the attestation ID information from secure storage.
pub(crate) fn read_attestation_ids() -> Result<AttestationIdInfo, Error> {
    // Retrieving attestation IDs from file. If the file is not found (device not provisioned) we
    // will return an empty AttestationIdInfo info to match the c++ code behavior
    let content = match get_file_contents(KM_ATTESTATION_ID_FILENAME) {
        Ok(Some(file_contents)) => file_contents,
        Ok(None) => return Ok(AttestationIdInfo::default()),
        Err(e) => return Err(e),
    };
    let mut attestation_ids_pb: keymaster_attributes::AttestationIds =
        Message::parse_from_bytes(content.as_slice())
            .map_err(|e| km_err!(UnknownError, "failed to parse attestation IDs proto: {:?}", e))?;

    let brand = attestation_ids_pb.take_brand();
    let device = attestation_ids_pb.take_device();
    let product = attestation_ids_pb.take_product();
    let serial = attestation_ids_pb.take_serial();
    let imei = attestation_ids_pb.take_imei();
    let meid = attestation_ids_pb.take_meid();
    let manufacturer = attestation_ids_pb.take_manufacturer();
    let model = attestation_ids_pb.take_model();

    let imei2 = if attestation_ids_pb.has_second_imei() {
        // A second IMEI has been explicitly provisioned, so use that.
        attestation_ids_pb.take_second_imei()
    } else if cfg!(feature = "auto_second_imei") {
        // No second IMEI has been explicitly provisioned, but dual-SIM devices typically ship with
        // two sequential IMEIs, so treat (IMEI+1) as the second IMEI.
        kmr_common::tag::increment_imei(&imei)
    } else {
        Vec::new()
    };

    Ok(AttestationIdInfo { brand, device, product, serial, imei, imei2, meid, manufacturer, model })
}

/// Retrieve that attestation key information for the specified signing algorithm.
/// Returns an empty protobuf when file is not found to match c++ behavior
fn read_attestation_key_content(
    key_type: SigningAlgorithm,
) -> Result<keymaster_attributes::AttestationKey, Error> {
    let file_name = get_key_slot_file_name(key_type);
    let pb = match get_file_contents(&file_name)? {
        Some(content) => Message::parse_from_bytes(content.as_slice())
            .map_err(|e| km_err!(UnknownError, "failed to parse attestation key proto: {:?}", e))?,
        None => keymaster_attributes::AttestationKey::new(),
    };
    Ok(pb)
}

/// Tries to parse an EC private key as a SEC1 private EC key. If that fails, it tries to parse it
/// as a PKCS#8 encoded key.
fn import_ec_key(key_buffer: &[u8]) -> Result<KeyMaterial, Error> {
    match crypto::ec::import_sec1_private_key(key_buffer) {
        Ok(key_material) => Ok(key_material),
        Err(e) => {
            info!("couldn't parse as sec1 ECPrivateKey, will try as PKCS#8. Error {:?}", e);
            crypto::ec::import_pkcs8_key(key_buffer)
        }
    }
}

/// Tries to parse an RSA key as a PKCS#1 encoded key. If that fails, it tries to parse it as a
/// PKCS#8 one.
fn import_rsa_key(key_buffer: &[u8]) -> Result<KeyMaterial, Error> {
    match crypto::rsa::import_pkcs1_key(key_buffer) {
        Ok((key_material, _, _)) => Ok(key_material),
        Err(e) => {
            info!("couldn't parse PKCS#1 RSA key, will try as PKCS#8. Error {:?}", e);
            let (key_material, _, _) = crypto::rsa::import_pkcs8_key(key_buffer)?;
            Ok(key_material)
        }
    }
}

/// Retrieve the specified attestation key from the file in secure storage.
pub(crate) fn read_attestation_key(key_type: SigningKeyType) -> Result<KeyMaterial, Error> {
    let mut attestation_key_pb: keymaster_attributes::AttestationKey =
        read_attestation_key_content(key_type.algo_hint)?;

    if !(attestation_key_pb.has_key()) {
        return Err(km_err!(UnknownError, "attestation Key file found but it had no key"));
    }
    let key_buffer = attestation_key_pb.take_key();

    match key_type.algo_hint {
        SigningAlgorithm::Ec => import_ec_key(&key_buffer),
        SigningAlgorithm::Rsa => import_rsa_key(&key_buffer),
    }
}

pub(crate) fn get_cert_chain(key_type: SigningKeyType) -> Result<Vec<keymint::Certificate>, Error> {
    let mut attestation_certs_pb: keymaster_attributes::AttestationKey =
        read_attestation_key_content(key_type.algo_hint)?;
    let certs = attestation_certs_pb.take_certs();

    let num_certs = certs.len();
    if num_certs == 0 {
        return Err(km_err!(UnknownError, "attestation Key file found but it had no certs"));
    }
    let mut certificates = vec_try_with_capacity!(num_certs)?;
    for mut cert in certs {
        let certificate = keymint::Certificate { encoded_certificate: cert.take_content() };
        certificates.push(certificate);
    }
    Ok(certificates)
}

/// Implementation of attestation ID retrieval trait based on protobuf-encoded data in a file in
/// secure storage.
pub struct AttestationIds;

impl RetrieveAttestationIds for AttestationIds {
    fn get(&self) -> Result<AttestationIdInfo, Error> {
        read_attestation_ids()
    }

    /// Destroy all attestation IDs associated with the device.
    fn destroy_all(&mut self) -> Result<(), Error> {
        delete_attestation_ids()
    }
}

/// Implementation of attestation signing key retrieval trait based on data held in files in secure
/// storage.
pub struct CertSignInfo;

impl RetrieveCertSigningInfo for CertSignInfo {
    fn signing_key(&self, key_type: SigningKeyType) -> Result<KeyMaterial, Error> {
        let result = read_attestation_key(key_type);
        #[cfg(feature = "soft_attestation_fallback")]
        if let Err(e) = result {
            info!("failed to read attestation key ({:?}), fall back to test key", e);
            let fake = software::CertSignInfo::new();
            return fake.signing_key(key_type);
        }
        result
    }

    fn cert_chain(&self, key_type: SigningKeyType) -> Result<Vec<keymint::Certificate>, Error> {
        let result = get_cert_chain(key_type);
        #[cfg(feature = "soft_attestation_fallback")]
        if let Err(e) = result {
            info!("failed to read attestation chain ({:?}), fall back to test chain", e);
            let fake = software::CertSignInfo::new();
            return fake.cert_chain(key_type);
        }
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec;
    use core::iter::zip;
    use kmr_common::wire::AttestationIdInfo;
    use kmr_ta::device::{SigningAlgorithm, SigningKey, SigningKeyType};
    use test::{expect, expect_eq};

    // Generated by:
    // openssl genpkey -out rsakey2.key -outform DER -algorithm RSA -pkeyopt rsa_keygen_bits:2048
    // openssl pkcs8 -topk8 -in rsakey2.key -outform der -nocrypt -out rsakey.key
    const RSA_KEY: &'static [u8] = include_bytes!("rsakey.key");
    // Generated by:
    // openssl ecparam -name prime256v1 -genkey -outform der -out eckey2.key
    // openssl pkcs8 -topk8 -in eckey2.key -outform der -nocrypt -out eckey.key
    const EC_KEY: &'static [u8] = include_bytes!("eckey.key");
    // Generated by:
    // openssl genpkey -out rsakey2.key -outform DER -algorithm RSA -pkeyopt rsa_keygen_bits:2048
    const RSA_KEY_PKCS1: &'static [u8] = include_bytes!("rsakeyrfc3447.key");
    // Generated by:
    // openssl ecparam -name prime256v1 -genkey -outform der -out eckey2.key
    const EC_PRIVATE_KEY_SEC1: &'static [u8] = include_bytes!("eckeysec1.key");

    fn delete_key_file(algorithm: SigningAlgorithm) {
        let file_name = get_key_slot_file_name(algorithm);
        let mut session =
            Session::new(Port::TamperProof, true).expect("Couldn't connect to storage");
        session.remove(&file_name).expect("Couldn't delete attestation file.");
    }

    fn check_key_file_exists(algorithm: SigningAlgorithm) -> bool {
        let mut session =
            Session::new(Port::TamperProof, true).expect("Couldn't connect to storage");
        let file_name = get_key_slot_file_name(algorithm);
        session.open_file(&file_name, OpenMode::Open).is_ok()
    }

    fn delete_attestation_id_file() {
        let mut session =
            Session::new(Port::TamperProof, true).expect("Couldn't connect to storage");
        session.remove(KM_ATTESTATION_ID_FILENAME).expect("Couldn't delete attestation IDs file.");
    }

    fn check_attestation_id_file_exists() -> bool {
        let mut session =
            Session::new(Port::TamperProof, true).expect("Couldn't connect to storage");
        session.open_file(KM_ATTESTATION_ID_FILENAME, OpenMode::Open).is_ok()
    }

    fn compare_attestation_ids(lhs: &AttestationIdInfo, rhs: &AttestationIdInfo) {
        expect_eq!(lhs.brand, rhs.brand, "brand doesn't match");
        expect_eq!(lhs.device, rhs.device, "device doesn't match");
        expect_eq!(lhs.product, rhs.product, "product doesn't match");
        expect_eq!(lhs.serial, rhs.serial, "serial doesn't match");
        expect_eq!(lhs.imei, rhs.imei, "imei doesn't match");
        expect_eq!(lhs.meid, rhs.meid, "meid doesn't match");
        expect_eq!(lhs.manufacturer, rhs.manufacturer, "manufacturer doesn't match");
        expect_eq!(lhs.model, rhs.model, "model doesn't match");
        expect_eq!(lhs.imei2, rhs.imei2, "imei2 doesn't match");
    }

    fn read_certificates_test(algorithm: SigningAlgorithm) {
        let mut file =
            create_attestation_key_file(algorithm).expect("Couldn't create attestation key file");
        let mut key_cert = keymaster_attributes::AttestationKey::new();
        let certs_data = [[b'a'; 2048], [b'\0'; 2048], [b'c'; 2048]];

        let mut certs = protobuf::RepeatedField::<keymaster_attributes::AttestationCert>::new();

        for cert_data in certs_data.iter() {
            let mut cert = keymaster_attributes::AttestationCert::new();
            cert.set_content(cert_data.to_vec());
            certs.push(cert);
        }

        key_cert.set_certs(certs);

        let serialized_buffer = key_cert.write_to_bytes().expect("Couldn't serialize certs");

        file.write_all(&serialized_buffer).unwrap();
        file.close();

        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: algorithm };
        let certs = get_cert_chain(key_type).expect("Couldn't get certificates from storage");
        delete_key_file(algorithm);

        expect_eq!(certs.len(), 3, "Didn't recover all certificates");
        for (cert, cert_data) in zip(certs.iter(), certs_data.iter()) {
            expect_eq!(cert.encoded_certificate, cert_data.to_vec(), "Wrong certificate retrieved");
        }

        // Trying now using just a raw protobuf with same data
        let field_header = [18, 131, 16, 10, 128, 16];
        let mut raw_protobuf = Vec::new();
        for cert_data in certs_data.iter() {
            raw_protobuf.extend_from_slice(&field_header);
            raw_protobuf.extend_from_slice(cert_data);
        }

        let mut file =
            create_attestation_key_file(algorithm).expect("Couldn't create attestation key file");
        file.write_all(&raw_protobuf).unwrap();
        file.close();

        let certs_comp = get_cert_chain(key_type).expect("Couldn't get certificates from storage");

        expect_eq!(certs, certs_comp, "Retrieved certificates didn't match");

        delete_key_file(algorithm);
    }

    #[test]
    fn read_ec_rsa_certificates() {
        read_certificates_test(SigningAlgorithm::Rsa);
        read_certificates_test(SigningAlgorithm::Ec);
    }

    fn get_test_key_data(algorithm: SigningAlgorithm) -> &'static [u8] {
        match algorithm {
            SigningAlgorithm::Rsa => RSA_KEY,
            SigningAlgorithm::Ec => EC_KEY,
        }
    }

    fn get_non_pkcs8_test_key_data(algorithm: SigningAlgorithm) -> &'static [u8] {
        match algorithm {
            SigningAlgorithm::Rsa => RSA_KEY_PKCS1,
            SigningAlgorithm::Ec => EC_PRIVATE_KEY_SEC1,
        }
    }

    fn get_test_attestation_key(algorithm: SigningAlgorithm) -> Result<KeyMaterial, Error> {
        let key_buffer = get_test_key_data(algorithm);
        let key = match algorithm {
            SigningAlgorithm::Ec => crypto::ec::import_pkcs8_key(key_buffer)?,
            SigningAlgorithm::Rsa => {
                let (key_material, _key_size, _exponent) =
                    crypto::rsa::import_pkcs8_key(key_buffer)?;
                key_material
            }
        };
        Ok(key)
    }

    #[test]
    fn multiple_attestation_calls_work() {
        let ec_test_key =
            get_test_attestation_key(SigningAlgorithm::Ec).expect("Couldn't get test key");
        let rsa_test_key =
            get_test_attestation_key(SigningAlgorithm::Rsa).expect("Couldn't get test key");

        provision_attestation_key_file(SigningAlgorithm::Ec, EC_KEY)
            .expect("Couldn't provision key");
        provision_attestation_key_file(SigningAlgorithm::Rsa, RSA_KEY)
            .expect("Couldn't provision key");
        provision_attestation_key_file(SigningAlgorithm::Rsa, &[0])
            .expect("Couldn't provision key");
        provision_attestation_key_file(SigningAlgorithm::Ec, &[0, 0])
            .expect("Couldn't provision key");
        provision_attestation_key_file(SigningAlgorithm::Ec, EC_KEY)
            .expect("Couldn't provision key");
        provision_attestation_key_file(SigningAlgorithm::Rsa, RSA_KEY)
            .expect("Couldn't provision key");

        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: SigningAlgorithm::Ec };
        let read_ec_test_key =
            read_attestation_key(key_type).expect("Couldn't read key from storage");

        let key_type =
            SigningKeyType { which: SigningKey::Batch, algo_hint: SigningAlgorithm::Rsa };
        let read_rsa_test_key =
            read_attestation_key(key_type).expect("Couldn't read key from storage");

        delete_key_file(SigningAlgorithm::Ec);
        delete_key_file(SigningAlgorithm::Rsa);

        expect_eq!(ec_test_key, read_ec_test_key, "Provisioned key doesn't match original one");
        expect_eq!(rsa_test_key, read_rsa_test_key, "Provisioned key doesn't match original one");
    }

    fn read_key_test(algorithm: SigningAlgorithm) {
        let test_key = get_test_key_data(algorithm);

        provision_attestation_key_file(algorithm, test_key).expect("Couldn't provision key");

        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: algorithm };
        let att_key = read_attestation_key(key_type).expect("Couldn't read key from storage");

        delete_key_file(algorithm);

        // Trying now using just a raw protobuf with same data
        let key_header = match algorithm {
            SigningAlgorithm::Rsa => [10, 191, 9],
            SigningAlgorithm::Ec => [10, 138, 1],
        };
        let mut raw_protobuf = Vec::new();
        raw_protobuf.extend_from_slice(&key_header);
        raw_protobuf.extend_from_slice(&test_key);

        let mut file =
            create_attestation_key_file(algorithm).expect("Couldn't create attestation key file");
        file.write_all(&raw_protobuf).unwrap();
        file.close();

        let att_key_comp = read_attestation_key(key_type).expect("Couldn't read key from storage");

        expect_eq!(att_key, att_key_comp);

        delete_key_file(algorithm);
    }

    #[test]
    fn read_ec_rsa_key() {
        read_key_test(SigningAlgorithm::Rsa);
        read_key_test(SigningAlgorithm::Ec);
    }

    fn read_non_pkcs8_key_test(algorithm: SigningAlgorithm) {
        let test_key = get_non_pkcs8_test_key_data(algorithm);

        provision_attestation_key_file(algorithm, test_key).expect("Couldn't provision key");

        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: algorithm };
        let att_key = read_attestation_key(key_type);
        expect!(att_key.is_ok(), "Couldn't read a non-pkcs8 key from storage");

        delete_key_file(algorithm);
    }

    #[test]
    fn read_sec1_private_ec_key() {
        read_non_pkcs8_key_test(SigningAlgorithm::Ec);
    }

    #[test]
    fn read_pkcs1_rsa_key() {
        read_non_pkcs8_key_test(SigningAlgorithm::Rsa);
    }

    #[test]
    fn unprovisioned_keys_certs_reads_produces_error() {
        if check_key_file_exists(SigningAlgorithm::Ec) {
            delete_key_file(SigningAlgorithm::Ec);
        }
        if check_key_file_exists(SigningAlgorithm::Rsa) {
            delete_key_file(SigningAlgorithm::Rsa);
        }
        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: SigningAlgorithm::Ec };
        expect!(read_attestation_key(key_type).is_err(), "Shouldn't be able to read a key");
        let key_type =
            SigningKeyType { which: SigningKey::Batch, algo_hint: SigningAlgorithm::Rsa };
        expect!(read_attestation_key(key_type).is_err(), "Shouldn't be able to read a key");
    }

    #[test]
    fn provision_certs_test() {
        provision_certs_test_impl(SigningAlgorithm::Ec, true);
        provision_certs_test_impl(SigningAlgorithm::Rsa, true);
        provision_certs_test_impl(SigningAlgorithm::Ec, false);
        provision_certs_test_impl(SigningAlgorithm::Rsa, false);
    }

    fn provision_certs_test_impl(algorithm: SigningAlgorithm, key_first: bool) {
        if check_key_file_exists(algorithm) {
            delete_key_file(algorithm);
        }
        let test_key = get_test_key_data(algorithm);
        if key_first {
            provision_attestation_key_file(algorithm, test_key).expect("Couldn't provision key");
        }
        let cert1 = [b'a'; 2048].as_slice();
        let cert2 = [b'b'; 2048].as_slice();
        let cert3 = [b'c'; 2048].as_slice();
        let certs = [cert1, cert2, cert3];
        for cert_data in certs.iter() {
            append_attestation_cert_chain(algorithm, cert_data)
                .expect("Couldn't provision certificate");
        }
        expect!(
            append_attestation_cert_chain(algorithm, cert3).is_err(),
            "Shouldn't be able to add more certificates"
        );
        if !key_first {
            provision_attestation_key_file(algorithm, test_key).expect("Couldn't provision key");
        }
        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: algorithm };
        let read_test_key = read_attestation_key(key_type).expect("Couldn't read attestation key");
        //Getting test key data on a format that can be compared with the key in storage
        let test_key = get_test_attestation_key(algorithm).expect("Couldn't get test key");
        expect_eq!(test_key, read_test_key, "Test keys didn't match");

        let read_certs = get_cert_chain(key_type).expect("Couldn't get certificates from storage");
        expect_eq!(read_certs.len(), 3, "Didn't get all certificates back");
        for (cert, read_cert) in certs.iter().zip(read_certs.iter()) {
            expect_eq!(cert, &read_cert.encoded_certificate, "got wrong certificate back");
        }
        delete_key_file(algorithm);
    }

    fn clear_certificate_chain_works_when_unprovisioned_impl(algorithm: SigningAlgorithm) {
        if check_key_file_exists(algorithm) {
            delete_key_file(algorithm);
        }
        clear_attestation_cert_chain(algorithm).expect("couldn't clear certificate chain");
        expect!(
            check_key_file_exists(algorithm) == false,
            "Shouldn't have created a file if it didn't existed originally"
        );
    }

    #[test]
    fn clear_certificate_chain_works_when_unprovisioned() {
        clear_certificate_chain_works_when_unprovisioned_impl(SigningAlgorithm::Ec);
        clear_certificate_chain_works_when_unprovisioned_impl(SigningAlgorithm::Rsa);
    }

    fn clear_certificate_chain_works_impl(algorithm: SigningAlgorithm) {
        if check_key_file_exists(algorithm) {
            delete_key_file(algorithm);
        }
        let test_key = get_test_key_data(algorithm);
        provision_attestation_key_file(algorithm, test_key).expect("Couldn't provision key");
        let cert = [b'a'; 2048].as_slice();
        append_attestation_cert_chain(algorithm, cert).expect("Couldn't provision certificate");

        let key_type = SigningKeyType { which: SigningKey::Batch, algo_hint: algorithm };
        let read_certs = get_cert_chain(key_type).expect("Couldn't get certificates from storage");
        expect_eq!(read_certs.len(), 1, "Didn't get all certificates back");

        clear_attestation_cert_chain(algorithm).expect("couldn't clear certificate chain");

        expect!(get_cert_chain(key_type).is_err(), "Certificates were not deleted");

        let read_test_key = read_attestation_key(key_type).expect("Couldn't read attestation key");
        //Getting test key data on a format that can be compared with the key in storage
        let test_key = get_test_attestation_key(algorithm).expect("Couldn't get test key");
        expect_eq!(test_key, read_test_key, "Test keys didn't match");

        delete_key_file(algorithm);
    }

    #[test]
    fn clear_certificate_chain_works() {
        clear_certificate_chain_works_impl(SigningAlgorithm::Ec);
        clear_certificate_chain_works_impl(SigningAlgorithm::Rsa);
    }

    #[test]
    fn unprovisioned_attestation_ids_do_not_error() {
        if check_attestation_id_file_exists() {
            delete_attestation_id_file();
        }
        let attestation_ids =
            read_attestation_ids().expect("Couldn't read attestation IDs when unprovisioned");

        expect_eq!(attestation_ids.brand.len(), 0, "brand should be empty");
        expect_eq!(attestation_ids.device.len(), 0, "device should be empty");
        expect_eq!(attestation_ids.product.len(), 0, "product should be empty");
        expect_eq!(attestation_ids.serial.len(), 0, "serial should be empty");
        expect_eq!(attestation_ids.imei.len(), 0, "imei should be empty");
        expect_eq!(attestation_ids.meid.len(), 0, "meid should be empty");
        expect_eq!(attestation_ids.manufacturer.len(), 0, "manufacturer should be empty");
        expect_eq!(attestation_ids.model.len(), 0, "model should be empty");
        expect_eq!(attestation_ids.imei2.len(), 0, "imei2 should be empty");
    }

    #[test]
    fn single_attestation_id_field() {
        let mut file = create_attestation_id_file().expect("Couldn't create attestation id file");

        let mut attestation_ids = keymaster_attributes::AttestationIds::new();
        let brand = b"new brand";

        attestation_ids.set_brand(brand.to_vec());

        let serialized_buffer =
            attestation_ids.write_to_bytes().expect("Couldn't serialize attestationIds");

        file.write_all(&serialized_buffer).unwrap();
        file.close();

        let attestation_ids_info =
            read_attestation_ids().expect("Couldn't read attestation IDs from storage");

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );

        expect_eq!(attestation_ids_info.brand, brand.to_vec(), "brand doesn't match");
        expect_eq!(attestation_ids_info.device.len(), 0, "shouldn't have a device");
        expect_eq!(attestation_ids_info.product.len(), 0, "shouldn't have a product");
        expect_eq!(attestation_ids_info.serial.len(), 0, "shouldn't have a serial");
        expect_eq!(attestation_ids_info.imei.len(), 0, "shouldn't have a imei");
        expect_eq!(attestation_ids_info.meid.len(), 0, "shouldn't have a meid");
        expect_eq!(attestation_ids_info.manufacturer.len(), 0, "shouldn't have a manufacturer");
        expect_eq!(attestation_ids_info.model.len(), 0, "shouldn't have a model");
        expect_eq!(attestation_ids_info.imei2.len(), 0, "shouldn't have a model");

        // Now using a raw protobuf
        let raw_protobuf = [10, 9, 110, 101, 119, 32, 98, 114, 97, 110, 100];

        let mut file = create_attestation_id_file().expect("Couldn't create attestation id file");
        file.write_all(&raw_protobuf).unwrap();
        file.close();

        let attestation_ids_comp = read_attestation_ids()
            .expect("Couldn't read comparison set of attestation IDs from storage");

        compare_attestation_ids(&attestation_ids_info, &attestation_ids_comp);

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );
    }

    #[test]
    fn test_provision_attestation_id_file() {
        let brand = b"unknown brand";
        let product = b"";
        let device = b"my brand new device";
        let serial = vec![b'9'; 64];
        let imei = b" ";
        let meid = b"\0";
        let manufacturer = b"manufacturer #$%%^";
        let model = b"working one";
        let imei2 = b"0";

        assert!(provision_attestation_id_file(
            brand,
            product,
            device,
            &serial,
            imei,
            meid,
            manufacturer,
            model,
            Some(imei2)
        )
        .is_ok());

        let attestation_ids_info =
            read_attestation_ids().expect("Couldn't read attestation IDs from storage");

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );

        expect_eq!(attestation_ids_info.brand, brand.to_vec(), "brand doesn't match");
        expect_eq!(attestation_ids_info.device, device.to_vec(), "device doesn't match");
        expect_eq!(attestation_ids_info.product, product.to_vec(), "product doesn't match");
        expect_eq!(attestation_ids_info.serial, serial, "serial doesn't match");
        expect_eq!(attestation_ids_info.imei, imei.to_vec(), "imei doesn't match");
        expect_eq!(attestation_ids_info.meid, meid.to_vec(), "meid doesn't match");
        expect_eq!(
            attestation_ids_info.manufacturer,
            manufacturer.to_vec(),
            "manufacturer doesn't match"
        );
        expect_eq!(attestation_ids_info.model, model.to_vec(), "model doesn't match");
        expect_eq!(attestation_ids_info.imei2, imei2.to_vec(), "imei2 doesn't match");
    }

    #[test]
    fn test_provision_attestation_id_file_imei2_none() {
        let brand = b"unknown brand";
        let product = b"";
        let device = b"my brand new device";
        let serial = vec![b'9'; 64];
        let imei = b"000000123456782";
        let meid = b"\0";
        let manufacturer = b"manufacturer #$%%^";
        let model = b"working one";
        let expected_imei2 = b"123456790";

        assert!(provision_attestation_id_file(
            brand,
            product,
            device,
            &serial,
            imei,
            meid,
            manufacturer,
            model,
            None
        )
        .is_ok());

        let attestation_ids_info =
            read_attestation_ids().expect("Couldn't read attestation IDs from storage");

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );

        expect_eq!(attestation_ids_info.brand, brand.to_vec(), "brand doesn't match");
        expect_eq!(attestation_ids_info.device, device.to_vec(), "device doesn't match");
        expect_eq!(attestation_ids_info.product, product.to_vec(), "product doesn't match");
        expect_eq!(attestation_ids_info.serial, serial, "serial doesn't match");
        expect_eq!(attestation_ids_info.imei, imei.to_vec(), "imei doesn't match");
        expect_eq!(attestation_ids_info.meid, meid.to_vec(), "meid doesn't match");
        expect_eq!(
            attestation_ids_info.manufacturer,
            manufacturer.to_vec(),
            "manufacturer doesn't match"
        );
        expect_eq!(attestation_ids_info.model, model.to_vec(), "model doesn't match");
        expect_eq!(attestation_ids_info.imei2, expected_imei2.to_vec(), "imei2 doesn't match");
    }

    #[test]
    fn all_attestation_id_fields() {
        let mut file = create_attestation_id_file().expect("Couldn't create attestation id file");
        let mut attestation_ids = keymaster_attributes::AttestationIds::new();
        let brand = b"unknown brand";
        let device = b"my brand new device";
        let product = b"";
        let serial = vec![b'9'; 64];
        let imei = b" ";
        let meid = b"\0";
        let manufacturer = b"manufacturer #$%%^";
        let model = b"working one";
        let imei2 = b"0";

        attestation_ids.set_brand(brand.to_vec());
        attestation_ids.set_device(device.to_vec());
        attestation_ids.set_product(product.to_vec());
        attestation_ids.set_serial(serial.clone());
        attestation_ids.set_imei(imei.to_vec());
        attestation_ids.set_meid(meid.to_vec());
        attestation_ids.set_manufacturer(manufacturer.to_vec());
        attestation_ids.set_model(model.to_vec());
        attestation_ids.set_second_imei(imei2.to_vec());

        let serialized_buffer =
            attestation_ids.write_to_bytes().expect("Couldn't serialize attestationIds");

        file.write_all(&serialized_buffer).unwrap();
        file.close();

        let attestation_ids_info =
            read_attestation_ids().expect("Couldn't read attestation IDs from storage");

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );

        expect_eq!(attestation_ids_info.brand, brand.to_vec(), "brand doesn't match");
        expect_eq!(attestation_ids_info.device, device.to_vec(), "device doesn't match");
        expect_eq!(attestation_ids_info.product, product.to_vec(), "product doesn't match");
        expect_eq!(attestation_ids_info.serial, serial, "serial doesn't match");
        expect_eq!(attestation_ids_info.imei, imei.to_vec(), "imei doesn't match");
        expect_eq!(attestation_ids_info.meid, meid.to_vec(), "meid doesn't match");
        expect_eq!(
            attestation_ids_info.manufacturer,
            manufacturer.to_vec(),
            "manufacturer doesn't match"
        );
        expect_eq!(attestation_ids_info.model, model.to_vec(), "model doesn't match");
        expect_eq!(attestation_ids_info.imei2, imei2.to_vec(), "imei2 doesn't match");

        // Now trying the same from a raw protobuf
        let raw_protobuf = [
            10, 13, 117, 110, 107, 110, 111, 119, 110, 32, 98, 114, 97, 110, 100, 18, 19, 109, 121,
            32, 98, 114, 97, 110, 100, 32, 110, 101, 119, 32, 100, 101, 118, 105, 99, 101, 26, 0,
            34, 64, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57,
            57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57,
            57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57, 57,
            42, 1, 32, 50, 1, 0, 58, 18, 109, 97, 110, 117, 102, 97, 99, 116, 117, 114, 101, 114,
            32, 35, 36, 37, 37, 94, 66, 11, 119, 111, 114, 107, 105, 110, 103, 32, 111, 110, 101,
            74, 1, 48,
        ];

        let mut file = create_attestation_id_file().expect("Couldn't create attestation id file");
        file.write_all(&raw_protobuf).unwrap();
        file.close();

        let attestation_ids_comp = read_attestation_ids()
            .expect("Couldn't read comparison set of attestation IDs from storage");

        compare_attestation_ids(&attestation_ids_info, &attestation_ids_comp);

        delete_attestation_id_file();
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Couldn't delete attestation IDs file"
        );
    }

    #[test]
    fn delete_attestation_ids_file() {
        let mut file = create_attestation_id_file().expect("Couldn't create attestation id file");
        let raw_protobuf = [10, 9, 110, 101, 119, 32, 98, 114, 97, 110, 100];
        file.write_all(&raw_protobuf).unwrap();
        file.close();

        expect!(check_attestation_id_file_exists(), "Couldn't create attestation IDs file");
        expect!(delete_attestation_ids().is_ok(), "Couldn't delete attestation IDs file");
        expect_eq!(
            check_attestation_id_file_exists(),
            false,
            "Attestation IDs file was not deleted"
        );
    }

    #[test]
    fn protobuf_lib_version() {
        // We are generating the protobuf rust files out of tree because we cannot do it in-tree yet
        // Because the version of the tool used to autogenerate the files has to match the protobuf
        // library version, we check it here.
        expect_eq!("2.27.1", protobuf::VERSION, "autogenerated files version mistmatch");
    }
}
