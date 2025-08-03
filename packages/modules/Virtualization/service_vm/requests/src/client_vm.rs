// Copyright 2023, The Android Open Source Project
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

//! This module contains functions related to the attestation of the
//! client VM.

use crate::cert;
use crate::dice::{
    validate_client_vm_dice_chain_prefix_match, ClientVmDiceChain, DiceChainEntryPayload,
};
use crate::keyblob::decrypt_private_key;
use alloc::vec::Vec;
use bssl_avf::{rand_bytes, sha256, Digester, EcKey, PKey};
use cbor_util::parse_value_array;
use ciborium::value::Value;
use core::result;
use coset::{AsCborValue, CborSerializable, CoseSign, CoseSign1};
use der::{Decode, Encode};
use diced_open_dice::{DiceArtifacts, HASH_SIZE};
use log::{error, info};
use microdroid_kernel_hashes::{HASH_SIZE as KERNEL_HASH_SIZE, OS_HASHES};
use service_vm_comm::{ClientVmAttestationParams, Csr, CsrPayload, RequestProcessingError};
use x509_cert::{certificate::Certificate, name::Name};

type Result<T> = result::Result<T, RequestProcessingError>;

const DICE_CDI_LEAF_SIGNATURE_INDEX: usize = 0;
const ATTESTATION_KEY_SIGNATURE_INDEX: usize = 1;

pub(super) fn request_attestation(
    params: ClientVmAttestationParams,
    dice_artifacts: &dyn DiceArtifacts,
) -> Result<Vec<u8>> {
    let csr = Csr::from_cbor_slice(&params.csr)?;
    let cose_sign = CoseSign::from_slice(&csr.signed_csr_payload)?;
    let csr_payload = cose_sign.payload.as_ref().ok_or_else(|| {
        error!("No CsrPayload found in the CSR");
        RequestProcessingError::InternalError
    })?;
    let csr_payload = CsrPayload::from_cbor_slice(csr_payload)?;

    // Validates the prefix of the Client VM DICE chain in the CSR.
    let service_vm_dice_chain =
        dice_artifacts.bcc().ok_or(RequestProcessingError::MissingDiceChain)?;
    let service_vm_dice_chain = parse_value_array(service_vm_dice_chain, "service_vm_dice_chain")?;
    let client_vm_dice_chain = parse_value_array(&csr.dice_cert_chain, "client_vm_dice_chain")?;
    validate_client_vm_dice_chain_prefix_match(&client_vm_dice_chain, &service_vm_dice_chain)?;
    // Validates the signatures in the Client VM DICE chain and extracts the partially decoded
    // DiceChainEntryPayloads.
    let client_vm_dice_chain =
        ClientVmDiceChain::validate_signatures_and_parse_dice_chain(client_vm_dice_chain)?;

    // The last entry in the service VM DICE chain describes the service VM, which should
    // be signed with the same key as the kernel image.
    let service_vm_entry = service_vm_dice_chain.last().unwrap();
    validate_kernel_authority_hash(client_vm_dice_chain.microdroid_kernel(), service_vm_entry)?;
    validate_kernel_code_hash(&client_vm_dice_chain)?;

    // AAD is empty as defined in service_vm/comm/client_vm_csr.cddl.
    let aad = &[];

    // Verifies the first signature with the leaf private key in the DICE chain.
    cose_sign.verify_signature(DICE_CDI_LEAF_SIGNATURE_INDEX, aad, |signature, message| {
        client_vm_dice_chain.microdroid_payload().subject_public_key.verify(signature, message)
    })?;

    // Verifies the second signature with the public key in the CSR payload.
    let ec_public_key = EcKey::from_cose_public_key_slice(&csr_payload.public_key)?;
    cose_sign.verify_signature(ATTESTATION_KEY_SIGNATURE_INDEX, aad, |signature, message| {
        ecdsa_verify(&ec_public_key, signature, message)
    })?;
    let subject_public_key_info = PKey::try_from(ec_public_key)?.subject_public_key_info()?;

    // Builds the TBSCertificate.
    // The serial number can be up to 20 bytes according to RFC5280 s4.1.2.2.
    // In this case, a serial number with a length of 16 bytes is used to ensure that each
    // certificate signed by RKP VM has a unique serial number.
    // Attention: Do not use 20 bytes here as when the MSB is 1, a leading 0 byte can be
    // added during the encoding to make the serial number length exceed 20 bytes.
    let mut serial_number = [0u8; 16];
    rand_bytes(&mut serial_number)?;
    let subject = Name::encode_from_string("CN=Android Protected Virtual Machine Key")?;
    let rkp_cert = Certificate::from_der(&params.remotely_provisioned_cert)?;
    let vm_components =
        if let Some(components) = client_vm_dice_chain.microdroid_payload_components() {
            components.iter().map(cert::VmComponent::new).collect::<der::Result<Vec<_>>>()?
        } else {
            Vec::new()
        };

    info!("The client VM DICE chain validation succeeded. Beginning to generate the certificate.");
    let attestation_ext = cert::AttestationExtension::new(
        &csr_payload.challenge,
        client_vm_dice_chain.all_entries_are_secure(),
        vm_components,
    )
    .to_der()?;
    let tbs_cert = cert::build_tbs_certificate(
        &serial_number,
        rkp_cert.tbs_certificate.subject,
        Name::from_der(&subject)?,
        rkp_cert.tbs_certificate.validity,
        &subject_public_key_info,
        &attestation_ext,
    )?;

    // Signs the TBSCertificate and builds the Certificate.
    // The two private key structs below will be zeroed out on drop.
    let private_key =
        decrypt_private_key(&params.remotely_provisioned_key_blob, dice_artifacts.cdi_seal())
            .map_err(|e| {
                error!("Failed to decrypt the remotely provisioned key blob: {e}");
                RequestProcessingError::FailedToDecryptKeyBlob
            })?;
    let ec_private_key = EcKey::from_ec_private_key(private_key.as_slice())?;
    let signature = ecdsa_sign(&ec_private_key, &tbs_cert.to_der()?)?;
    let certificate = cert::build_certificate(tbs_cert, &signature)?;
    Ok(certificate.to_der()?)
}

fn ecdsa_verify(key: &EcKey, signature: &[u8], message: &[u8]) -> bssl_avf::Result<()> {
    // The message was signed with ECDSA with curve P-256 and SHA-256 at the signature generation.
    let digest = sha256(message)?;
    key.ecdsa_verify(signature, &digest)
}

fn ecdsa_sign(key: &EcKey, message: &[u8]) -> bssl_avf::Result<Vec<u8>> {
    let digest = sha256(message)?;
    key.ecdsa_sign(&digest)
}

/// Validates that the authority hash of the Microdroid kernel in the Client VM DICE chain
/// matches the authority hash of the service VM entry in the service VM DICE chain, because
/// the Microdroid kernel is signed with the same key as the one used for the service VM.
fn validate_kernel_authority_hash(
    kernel: &DiceChainEntryPayload,
    service_vm_entry: &Value,
) -> Result<()> {
    if expected_kernel_authority_hash(service_vm_entry)? == kernel.authority_hash {
        Ok(())
    } else {
        error!("The authority hash of the Microdroid kernel does not match the expected value");
        Err(RequestProcessingError::InvalidDiceChain)
    }
}

/// Validates that the kernel code hash in the Client VM DICE chain matches the code hashes
/// embedded during the build time.
fn validate_kernel_code_hash(dice_chain: &ClientVmDiceChain) -> Result<()> {
    let kernel = dice_chain.microdroid_kernel();
    if matches_any_kernel_code_hash(&kernel.code_hash, /* is_debug= */ false)? {
        return Ok(());
    }
    if matches_any_kernel_code_hash(&kernel.code_hash, /* is_debug= */ true)? {
        if dice_chain.all_entries_are_secure() {
            error!("The Microdroid kernel has debug initrd but the DICE chain is secure");
            return Err(RequestProcessingError::InvalidDiceChain);
        }
        return Ok(());
    }
    error!("The kernel code hash in the Client VM DICE chain does not match any expected values");
    Err(RequestProcessingError::InvalidDiceChain)
}

fn matches_any_kernel_code_hash(actual_code_hash: &[u8], is_debug: bool) -> bssl_avf::Result<bool> {
    for os_hash in OS_HASHES {
        let mut code_hash = [0u8; KERNEL_HASH_SIZE * 2];
        code_hash[0..KERNEL_HASH_SIZE].copy_from_slice(&os_hash.kernel);
        if is_debug {
            code_hash[KERNEL_HASH_SIZE..].copy_from_slice(&os_hash.initrd_debug);
        } else {
            code_hash[KERNEL_HASH_SIZE..].copy_from_slice(&os_hash.initrd_normal);
        }
        if Digester::sha512().digest(&code_hash)? == actual_code_hash {
            return Ok(true);
        }
    }
    Ok(false)
}

fn expected_kernel_authority_hash(service_vm_entry: &Value) -> Result<[u8; HASH_SIZE]> {
    let cose_sign1 = CoseSign1::from_cbor_value(service_vm_entry.clone())?;
    let payload = cose_sign1.payload.ok_or_else(|| {
        error!("No payload found in the service VM DICE chain entry");
        RequestProcessingError::InternalError
    })?;
    let service_vm = DiceChainEntryPayload::from_slice(&payload)?;
    Ok(service_vm.authority_hash)
}
