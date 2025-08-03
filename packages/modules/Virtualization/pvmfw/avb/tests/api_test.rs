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

mod utils;

use anyhow::{anyhow, Result};
use avb::{DescriptorError, SlotVerifyError};
use avb_bindgen::{AvbFooter, AvbVBMetaImageHeader};
use pvmfw_avb::{verify_payload, Capability, DebugLevel, PvmfwVerifyError, VerifiedBootData};
use std::{fs, mem::size_of, ptr};
use utils::*;

const TEST_IMG_WITH_ONE_HASHDESC_PATH: &str = "test_image_with_one_hashdesc.img";
const TEST_IMG_WITH_ROLLBACK_INDEX_5: &str = "test_image_with_rollback_index_5.img";
const TEST_IMG_WITH_PROP_DESC_PATH: &str = "test_image_with_prop_desc.img";
const TEST_IMG_WITH_SERVICE_VM_PROP_PATH: &str = "test_image_with_service_vm_prop.img";
const TEST_IMG_WITH_UNKNOWN_VM_TYPE_PROP_PATH: &str = "test_image_with_unknown_vm_type_prop.img";
const TEST_IMG_WITH_MULTIPLE_PROPS_PATH: &str = "test_image_with_multiple_props.img";
const TEST_IMG_WITH_DUPLICATED_CAP_PATH: &str = "test_image_with_duplicated_capability.img";
const TEST_IMG_WITH_NON_INITRD_HASHDESC_PATH: &str = "test_image_with_non_initrd_hashdesc.img";
const TEST_IMG_WITH_INITRD_AND_NON_INITRD_DESC_PATH: &str =
    "test_image_with_initrd_and_non_initrd_desc.img";
const TEST_IMG_WITH_MULTIPLE_CAPABILITIES: &str = "test_image_with_multiple_capabilities.img";
const UNSIGNED_TEST_IMG_PATH: &str = "unsigned_test.img";

const RANDOM_FOOTER_POS: usize = 30;

/// This test uses the Microdroid payload compiled on the fly to check that
/// the latest payload can be verified successfully.
#[test]
fn latest_normal_payload_passes_verification() -> Result<()> {
    assert_latest_payload_verification_passes(
        &load_latest_initrd_normal()?,
        b"initrd_normal",
        DebugLevel::None,
    )
}

#[test]
fn latest_debug_payload_passes_verification() -> Result<()> {
    assert_latest_payload_verification_passes(
        &load_latest_initrd_debug()?,
        b"initrd_debug",
        DebugLevel::Full,
    )
}

#[test]
fn payload_expecting_no_initrd_passes_verification_with_no_initrd() -> Result<()> {
    let public_key = load_trusted_public_key()?;
    let verified_boot_data = verify_payload(
        &fs::read(TEST_IMG_WITH_ONE_HASHDESC_PATH)?,
        /* initrd= */ None,
        &public_key,
    )
    .map_err(|e| anyhow!("Verification failed. Error: {}", e))?;

    let kernel_digest = hash(&[&hex::decode("1111")?, &fs::read(UNSIGNED_TEST_IMG_PATH)?]);
    let expected_boot_data = VerifiedBootData {
        debug_level: DebugLevel::None,
        kernel_digest,
        initrd_digest: None,
        public_key: &public_key,
        capabilities: vec![],
        rollback_index: 0,
    };
    assert_eq!(expected_boot_data, verified_boot_data);

    Ok(())
}

#[test]
fn payload_with_non_initrd_descriptor_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &fs::read(TEST_IMG_WITH_NON_INITRD_HASHDESC_PATH)?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        PvmfwVerifyError::InvalidDescriptors(DescriptorError::InvalidContents),
    )
}

#[test]
fn payload_with_non_initrd_descriptor_fails_verification_with_initrd() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &fs::read(TEST_IMG_WITH_INITRD_AND_NON_INITRD_DESC_PATH)?,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        PvmfwVerifyError::InvalidDescriptors(DescriptorError::InvalidContents),
    )
}

#[test]
fn payload_expecting_no_initrd_passes_verification_with_service_vm_prop() -> Result<()> {
    let public_key = load_trusted_public_key()?;
    let verified_boot_data = verify_payload(
        &fs::read(TEST_IMG_WITH_SERVICE_VM_PROP_PATH)?,
        /* initrd= */ None,
        &public_key,
    )
    .map_err(|e| anyhow!("Verification failed. Error: {}", e))?;

    let kernel_digest = hash(&[&hex::decode("2131")?, &fs::read(UNSIGNED_TEST_IMG_PATH)?]);
    let expected_boot_data = VerifiedBootData {
        debug_level: DebugLevel::None,
        kernel_digest,
        initrd_digest: None,
        public_key: &public_key,
        capabilities: vec![Capability::RemoteAttest],
        rollback_index: 0,
    };
    assert_eq!(expected_boot_data, verified_boot_data);

    Ok(())
}

#[test]
fn payload_with_unknown_vm_type_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &fs::read(TEST_IMG_WITH_UNKNOWN_VM_TYPE_PROP_PATH)?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        PvmfwVerifyError::UnknownVbmetaProperty,
    )
}

#[test]
fn payload_with_multiple_props_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &fs::read(TEST_IMG_WITH_MULTIPLE_PROPS_PATH)?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        PvmfwVerifyError::InvalidDescriptors(DescriptorError::InvalidContents),
    )
}

#[test]
fn payload_with_duplicated_capability_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &fs::read(TEST_IMG_WITH_DUPLICATED_CAP_PATH)?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        SlotVerifyError::InvalidMetadata.into(),
    )
}

#[test]
fn payload_with_prop_descriptor_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &fs::read(TEST_IMG_WITH_PROP_DESC_PATH)?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        PvmfwVerifyError::UnknownVbmetaProperty,
    )
}

#[test]
fn payload_expecting_initrd_fails_verification_with_no_initrd() -> Result<()> {
    assert_payload_verification_fails(
        &load_latest_signed_kernel()?,
        /* initrd= */ None,
        &load_trusted_public_key()?,
        SlotVerifyError::InvalidMetadata.into(),
    )
}

#[test]
fn payload_with_empty_public_key_fails_verification() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &load_latest_signed_kernel()?,
        &load_latest_initrd_normal()?,
        /* trusted_public_key= */ &[0u8; 0],
        SlotVerifyError::PublicKeyRejected.into(),
    )
}

#[test]
fn payload_with_an_invalid_public_key_fails_verification() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &load_latest_signed_kernel()?,
        &load_latest_initrd_normal()?,
        /* trusted_public_key= */ &[0u8; 512],
        SlotVerifyError::PublicKeyRejected.into(),
    )
}

#[test]
fn payload_with_a_different_valid_public_key_fails_verification() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &load_latest_signed_kernel()?,
        &load_latest_initrd_normal()?,
        &fs::read(PUBLIC_KEY_RSA2048_PATH)?,
        SlotVerifyError::PublicKeyRejected.into(),
    )
}

#[test]
fn payload_with_an_invalid_initrd_fails_verification() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &load_latest_signed_kernel()?,
        /* initrd= */ &fs::read(UNSIGNED_TEST_IMG_PATH)?,
        &load_trusted_public_key()?,
        SlotVerifyError::Verification(None).into(),
    )
}

#[test]
fn unsigned_kernel_fails_verification() -> Result<()> {
    assert_payload_verification_with_initrd_fails(
        &fs::read(UNSIGNED_TEST_IMG_PATH)?,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::Io.into(),
    )
}

#[test]
fn tampered_kernel_fails_verification() -> Result<()> {
    let mut kernel = load_latest_signed_kernel()?;
    kernel[1] = !kernel[1]; // Flip the bits

    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::Verification(None).into(),
    )
}

#[test]
fn kernel_footer_with_vbmeta_offset_overwritten_fails_verification() -> Result<()> {
    // Arrange.
    let mut kernel = load_latest_signed_kernel()?;
    let total_len = kernel.len() as u64;
    let footer = extract_avb_footer(&kernel)?;
    assert!(footer.vbmeta_offset < total_len);
    // TODO: use core::mem::offset_of once stable.
    let footer_addr = ptr::addr_of!(footer) as *const u8;
    let vbmeta_offset_addr = ptr::addr_of!(footer.vbmeta_offset) as *const u8;
    let vbmeta_offset_start =
        // SAFETY:
        // - both raw pointers `vbmeta_offset_addr` and `footer_addr` are not null;
        // - they are both derived from the `footer` object;
        // - the offset is known from the struct definition to be a small positive number of bytes.
        unsafe { vbmeta_offset_addr.offset_from(footer_addr) };
    let footer_start = kernel.len() - size_of::<AvbFooter>();
    let vbmeta_offset_start = footer_start + usize::try_from(vbmeta_offset_start)?;

    let wrong_offsets = [total_len, u64::MAX];
    for &wrong_offset in wrong_offsets.iter() {
        // Act.
        kernel[vbmeta_offset_start..(vbmeta_offset_start + size_of::<u64>())]
            .copy_from_slice(&wrong_offset.to_be_bytes());

        // Assert.
        let footer = extract_avb_footer(&kernel)?;
        // footer is unaligned; copy vbmeta_offset to local variable
        let vbmeta_offset = footer.vbmeta_offset;
        assert_eq!(wrong_offset, vbmeta_offset);
        assert_payload_verification_with_initrd_fails(
            &kernel,
            &load_latest_initrd_normal()?,
            &load_trusted_public_key()?,
            SlotVerifyError::Io.into(),
        )?;
    }
    Ok(())
}

#[test]
fn tampered_kernel_footer_fails_verification() -> Result<()> {
    let mut kernel = load_latest_signed_kernel()?;
    let avb_footer_index = kernel.len() - size_of::<AvbFooter>() + RANDOM_FOOTER_POS;
    kernel[avb_footer_index] = !kernel[avb_footer_index];

    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::InvalidMetadata.into(),
    )
}

#[test]
fn extended_initrd_fails_verification() -> Result<()> {
    let mut initrd = load_latest_initrd_normal()?;
    initrd.extend(b"androidboot.vbmeta.digest=1111");

    assert_payload_verification_with_initrd_fails(
        &load_latest_signed_kernel()?,
        &initrd,
        &load_trusted_public_key()?,
        SlotVerifyError::Verification(None).into(),
    )
}

#[test]
fn tampered_vbmeta_fails_verification() -> Result<()> {
    let mut kernel = load_latest_signed_kernel()?;
    let footer = extract_avb_footer(&kernel)?;
    let vbmeta_index: usize = (footer.vbmeta_offset + 1).try_into()?;

    kernel[vbmeta_index] = !kernel[vbmeta_index]; // Flip the bits

    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::InvalidMetadata.into(),
    )
}

#[test]
fn vbmeta_with_public_key_overwritten_fails_verification() -> Result<()> {
    let mut kernel = load_latest_signed_kernel()?;
    let footer = extract_avb_footer(&kernel)?;
    let vbmeta_header = extract_vbmeta_header(&kernel, &footer)?;
    let public_key_offset = footer.vbmeta_offset as usize
        + size_of::<AvbVBMetaImageHeader>()
        + vbmeta_header.authentication_data_block_size as usize
        + vbmeta_header.public_key_offset as usize;
    let public_key_size: usize = vbmeta_header.public_key_size.try_into()?;
    let empty_public_key = vec![0u8; public_key_size];

    kernel[public_key_offset..(public_key_offset + public_key_size)]
        .copy_from_slice(&empty_public_key);

    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &empty_public_key,
        SlotVerifyError::Verification(None).into(),
    )?;
    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::Verification(None).into(),
    )
}

#[test]
fn vbmeta_with_verification_flag_disabled_fails_verification() -> Result<()> {
    // From external/avb/libavb/avb_vbmeta_image.h
    const AVB_VBMETA_IMAGE_FLAGS_VERIFICATION_DISABLED: u32 = 2;

    // Arrange.
    let mut kernel = load_latest_signed_kernel()?;
    let footer = extract_avb_footer(&kernel)?;
    let vbmeta_header = extract_vbmeta_header(&kernel, &footer)?;

    // vbmeta_header is unaligned; copy flags to local variable
    let vbmeta_header_flags = vbmeta_header.flags;
    assert_eq!(0, vbmeta_header_flags, "The disable flag should not be set in the latest kernel.");
    let flags_addr = ptr::addr_of!(vbmeta_header.flags) as *const u8;
    // SAFETY: It is safe as both raw pointers `flags_addr` and `vbmeta_header` are not null.
    let flags_offset = unsafe { flags_addr.offset_from(ptr::addr_of!(vbmeta_header) as *const u8) };
    let flags_offset = usize::try_from(footer.vbmeta_offset)? + usize::try_from(flags_offset)?;

    // Act.
    kernel[flags_offset..(flags_offset + size_of::<u32>())]
        .copy_from_slice(&AVB_VBMETA_IMAGE_FLAGS_VERIFICATION_DISABLED.to_be_bytes());

    // Assert.
    let vbmeta_header = extract_vbmeta_header(&kernel, &footer)?;
    // vbmeta_header is unaligned; copy flags to local variable
    let vbmeta_header_flags = vbmeta_header.flags;
    assert_eq!(
        AVB_VBMETA_IMAGE_FLAGS_VERIFICATION_DISABLED, vbmeta_header_flags,
        "VBMeta verification flag should be disabled now."
    );
    assert_payload_verification_with_initrd_fails(
        &kernel,
        &load_latest_initrd_normal()?,
        &load_trusted_public_key()?,
        SlotVerifyError::Verification(None).into(),
    )
}

#[test]
fn payload_with_rollback_index() -> Result<()> {
    let public_key = load_trusted_public_key()?;
    let verified_boot_data = verify_payload(
        &fs::read(TEST_IMG_WITH_ROLLBACK_INDEX_5)?,
        /* initrd= */ None,
        &public_key,
    )
    .map_err(|e| anyhow!("Verification failed. Error: {}", e))?;

    let kernel_digest = hash(&[&hex::decode("1211")?, &fs::read(UNSIGNED_TEST_IMG_PATH)?]);
    let expected_boot_data = VerifiedBootData {
        debug_level: DebugLevel::None,
        kernel_digest,
        initrd_digest: None,
        public_key: &public_key,
        capabilities: vec![],
        rollback_index: 5,
    };
    assert_eq!(expected_boot_data, verified_boot_data);
    Ok(())
}

#[test]
fn payload_with_multiple_capabilities() -> Result<()> {
    let public_key = load_trusted_public_key()?;
    let verified_boot_data = verify_payload(
        &fs::read(TEST_IMG_WITH_MULTIPLE_CAPABILITIES)?,
        /* initrd= */ None,
        &public_key,
    )
    .map_err(|e| anyhow!("Verification failed. Error: {}", e))?;

    assert!(verified_boot_data.has_capability(Capability::RemoteAttest));
    assert!(verified_boot_data.has_capability(Capability::SecretkeeperProtection));
    Ok(())
}
