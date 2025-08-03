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

//! libavb_rs verification tests.

use crate::test_ops::TestOps;
use avb::{
    slot_verify, ChainPartitionDescriptor, ChainPartitionDescriptorFlags, Descriptor,
    HashDescriptor, HashDescriptorFlags, HashtreeDescriptor, HashtreeDescriptorFlags,
    HashtreeErrorMode, IoError, KernelCommandlineDescriptor, KernelCommandlineDescriptorFlags,
    PropertyDescriptor, SlotVerifyData, SlotVerifyError, SlotVerifyFlags, SlotVerifyResult,
};
use hex::decode;
use std::{ffi::CString, fs};
#[cfg(feature = "uuid")]
use uuid::uuid;

// These constants must match the values used to create the images in Android.bp.
const TEST_IMAGE_PATH: &str = "test_image.img";
const TEST_IMAGE_SIZE: usize = 16 * 1024;
const TEST_IMAGE_SALT_HEX: &str = "1000";
const TEST_HASHTREE_SALT_HEX: &str = "B000";
const TEST_VBMETA_PATH: &str = "test_vbmeta.img";
const TEST_VBMETA_2_PARTITIONS_PATH: &str = "test_vbmeta_2_parts.img";
const TEST_VBMETA_PERSISTENT_DIGEST_PATH: &str = "test_vbmeta_persistent_digest.img";
const TEST_VBMETA_WITH_PROPERTY_PATH: &str = "test_vbmeta_with_property.img";
const TEST_VBMETA_WITH_HASHTREE_PATH: &str = "test_vbmeta_with_hashtree.img";
const TEST_VBMETA_WITH_COMMANDLINE_PATH: &str = "test_vbmeta_with_commandline.img";
const TEST_VBMETA_WITH_CHAINED_PARTITION_PATH: &str = "test_vbmeta_with_chained_partition.img";
const TEST_IMAGE_WITH_VBMETA_FOOTER_PATH: &str = "avbrs_test_image_with_vbmeta_footer.img";
const TEST_IMAGE_WITH_VBMETA_FOOTER_FOR_BOOT_PATH: &str =
    "avbrs_test_image_with_vbmeta_footer_for_boot.img";
const TEST_IMAGE_WITH_VBMETA_FOOTER_FOR_TEST_PART_2: &str =
    "avbrs_test_image_with_vbmeta_footer_for_test_part_2.img";
const TEST_PUBLIC_KEY_PATH: &str = "data/testkey_rsa4096_pub.bin";
const TEST_PUBLIC_KEY_RSA8192_PATH: &str = "data/testkey_rsa8192_pub.bin";
const TEST_PARTITION_NAME: &str = "test_part";
const TEST_PARTITION_SLOT_C_NAME: &str = "test_part_c";
const TEST_PARTITION_2_NAME: &str = "test_part_2";
const TEST_PARTITION_PERSISTENT_DIGEST_NAME: &str = "test_part_persistent_digest";
const TEST_PARTITION_HASH_TREE_NAME: &str = "test_part_hashtree";
const TEST_VBMETA_ROLLBACK_LOCATION: usize = 0; // Default value, we don't explicitly set this.
const TEST_PROPERTY_KEY: &str = "test_prop_key";
const TEST_PROPERTY_VALUE: &[u8] = b"test_prop_value";
const TEST_KERNEL_COMMANDLINE: &str = "test_cmdline_key=test_cmdline_value";
const TEST_CHAINED_PARTITION_ROLLBACK_LOCATION: usize = 4;
const TEST_CHAINED_PARTITION_ROLLBACK_INDEX: u64 = 7;

// Expected values determined by examining the vbmeta image with `avbtool info_image`.
// Images can be found in <out>/soong/.intermediates/external/avb/rust/.
const TEST_IMAGE_DIGEST_HEX: &str =
    "89e6fd3142917b8c34ac7d30897a907a71bd3bf5d9b39d00bf938b41dcf3b84f";
const TEST_IMAGE_HASH_ALGO: &str = "sha256";
const TEST_HASHTREE_DIGEST_HEX: &str = "5373fc4ee3dd898325eeeffb5a1dbb041900c5f1";
const TEST_HASHTREE_ALGORITHM: &str = "sha1";

/// Initializes a `TestOps` object such that verification will succeed on `TEST_PARTITION_NAME`.
fn test_ops_one_image_one_vbmeta() -> TestOps {
    let mut ops = TestOps::default();
    ops.add_partition(TEST_PARTITION_NAME, fs::read(TEST_IMAGE_PATH).unwrap());
    ops.add_partition("vbmeta", fs::read(TEST_VBMETA_PATH).unwrap());
    ops.add_vbmeta_key(fs::read(TEST_PUBLIC_KEY_PATH).unwrap(), None, true);
    ops.rollbacks.insert(TEST_VBMETA_ROLLBACK_LOCATION, 0);
    ops.unlock_state = Ok(false);
    ops
}

/// Calls `slot_verify()` using standard args for `test_ops_one_image_one_vbmeta()` setup.
fn verify_one_image_one_vbmeta(ops: &mut TestOps) -> SlotVerifyResult<SlotVerifyData> {
    slot_verify(
        ops,
        &[&CString::new(TEST_PARTITION_NAME).unwrap()],
        None,
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    )
}

/// Initializes a `TestOps` object such that verification will succeed on `TEST_PARTITION_NAME` and
/// `TEST_PARTITION_2_NAME`.
fn test_ops_two_images_one_vbmeta() -> TestOps {
    let mut ops = test_ops_one_image_one_vbmeta();
    // Add in the contents of the second partition and overwrite the vbmeta partition to
    // include both partition descriptors.
    ops.add_partition(TEST_PARTITION_2_NAME, fs::read(TEST_IMAGE_PATH).unwrap());
    ops.add_partition("vbmeta", fs::read(TEST_VBMETA_2_PARTITIONS_PATH).unwrap());
    ops
}

/// Calls `slot_verify()` for both test partitions.
fn verify_two_images(ops: &mut TestOps) -> SlotVerifyResult<SlotVerifyData> {
    slot_verify(
        ops,
        &[
            &CString::new(TEST_PARTITION_NAME).unwrap(),
            &CString::new(TEST_PARTITION_2_NAME).unwrap(),
        ],
        None,
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    )
}

/// Initializes a `TestOps` object such that verification will succeed on the `boot` partition with
/// a combined image + vbmeta.
fn test_ops_boot_partition() -> TestOps {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions.clear();
    ops.add_partition(
        "boot",
        fs::read(TEST_IMAGE_WITH_VBMETA_FOOTER_FOR_BOOT_PATH).unwrap(),
    );
    ops
}

/// Calls `slot_verify()` using standard args for `test_ops_boot_partition()` setup.
fn verify_boot_partition(ops: &mut TestOps) -> SlotVerifyResult<SlotVerifyData> {
    slot_verify(
        ops,
        &[&CString::new("boot").unwrap()],
        None,
        // libavb has some special-case handling to automatically detect a combined image + vbmeta
        // in the `boot` partition; don't pass the `AVB_SLOT_VERIFY_FLAGS_NO_VBMETA_PARTITION` flag
        // so we can test this behavior.
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    )
}

/// Initializes a `TestOps` object such that verification will succeed on
/// `TEST_PARTITION_PERSISTENT_DIGEST_NAME`.
fn test_ops_persistent_digest(image: Vec<u8>) -> TestOps {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions.clear();
    // Use the vbmeta image with the persistent digest descriptor.
    ops.add_partition(
        "vbmeta",
        fs::read(TEST_VBMETA_PERSISTENT_DIGEST_PATH).unwrap(),
    );
    // Register the image contents to be stored via persistent digest.
    ops.add_partition(TEST_PARTITION_PERSISTENT_DIGEST_NAME, image);
    ops
}

/// Calls `slot_verify()` using standard args for `test_ops_persistent_digest()` setup.
fn verify_persistent_digest(ops: &mut TestOps) -> SlotVerifyResult<SlotVerifyData> {
    slot_verify(
        ops,
        &[&CString::new(TEST_PARTITION_PERSISTENT_DIGEST_NAME).unwrap()],
        None,
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    )
}

/// Modifies the partition contents by flipping a bit.
fn modify_partition_contents(ops: &mut TestOps, partition: &str) {
    ops.partitions.get_mut(partition).unwrap().contents[0] ^= 0x01;
}

/// Returns the persistent value name for `TEST_PARTITION_PERSISTENT_DIGEST_NAME`.
fn persistent_digest_value_name() -> String {
    // This exact format is a libavb implementation detail but is unlikely to change. If it does
    // just update this format to match.
    format!("avb.persistent_digest.{TEST_PARTITION_PERSISTENT_DIGEST_NAME}")
}

#[test]
fn one_image_one_vbmeta_passes_verification_with_correct_data() {
    let mut ops = test_ops_one_image_one_vbmeta();

    let result = verify_one_image_one_vbmeta(&mut ops);

    // Make sure the resulting `SlotVerifyData` looks correct.
    let data = result.unwrap();
    assert_eq!(data.ab_suffix().to_bytes(), b"");
    // We don't care about the exact commandline, just search for a substring we know will
    // exist to make sure the commandline is being provided to the caller correctly.
    assert!(data
        .cmdline()
        .to_str()
        .unwrap()
        .contains("androidboot.vbmeta.device_state=locked"));
    assert_eq!(data.rollback_indexes(), &[0; 32]);
    assert_eq!(
        data.resolved_hashtree_error_mode(),
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO
    );

    // Check the `VbmetaData` struct looks correct.
    assert_eq!(data.vbmeta_data().len(), 1);
    let vbmeta_data = &data.vbmeta_data()[0];
    assert_eq!(vbmeta_data.partition_name().to_str().unwrap(), "vbmeta");
    assert_eq!(vbmeta_data.data(), fs::read(TEST_VBMETA_PATH).unwrap());
    assert_eq!(vbmeta_data.verify_result(), Ok(()));

    // Check the `PartitionData` struct looks correct.
    assert_eq!(data.partition_data().len(), 1);
    let partition_data = &data.partition_data()[0];
    assert_eq!(
        partition_data.partition_name().to_str().unwrap(),
        TEST_PARTITION_NAME
    );
    assert_eq!(partition_data.data(), fs::read(TEST_IMAGE_PATH).unwrap());
    assert!(!partition_data.preloaded());
    assert!(partition_data.verify_result().is_ok());
}

#[test]
fn preloaded_image_passes_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    // Mark the image partition to be preloaded.
    ops.partitions
        .get_mut(TEST_PARTITION_NAME)
        .unwrap()
        .preloaded = true;

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    let partition_data = &data.partition_data()[0];
    assert!(partition_data.preloaded());
}

#[test]
fn slotted_partition_passes_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    // Move the partitions to a "_c" slot.
    ops.partitions.clear();
    ops.add_partition(
        TEST_PARTITION_SLOT_C_NAME,
        fs::read(TEST_IMAGE_PATH).unwrap(),
    );
    ops.add_partition("vbmeta_c", fs::read(TEST_VBMETA_PATH).unwrap());

    let result = slot_verify(
        &mut ops,
        &[&CString::new(TEST_PARTITION_NAME).unwrap()],
        Some(&CString::new("_c").unwrap()),
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NONE,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    );

    let data = result.unwrap();
    assert_eq!(data.ab_suffix().to_bytes(), b"_c");
}

#[test]
fn two_images_one_vbmeta_passes_verification() {
    let mut ops = test_ops_two_images_one_vbmeta();

    let result = verify_two_images(&mut ops);

    // We should still only have 1 `VbmetaData` since we only used 1 vbmeta image, but it
    // signed 2 partitions so we should have 2 `PartitionData` objects.
    let data = result.unwrap();
    assert_eq!(data.vbmeta_data().len(), 1);
    assert_eq!(data.partition_data().len(), 2);
    assert_eq!(
        data.partition_data()[0].partition_name().to_str().unwrap(),
        TEST_PARTITION_NAME
    );
    assert_eq!(
        data.partition_data()[1].partition_name().to_str().unwrap(),
        TEST_PARTITION_2_NAME
    );
}

#[test]
fn combined_image_vbmeta_partition_passes_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions.clear();
    // Register the single combined image + vbmeta in `TEST_PARTITION_NAME`.
    ops.add_partition(
        TEST_PARTITION_NAME,
        fs::read(TEST_IMAGE_WITH_VBMETA_FOOTER_PATH).unwrap(),
    );
    // For a combined image we need to register the public key specifically for this partition.
    ops.add_vbmeta_key_for_partition(
        fs::read(TEST_PUBLIC_KEY_PATH).unwrap(),
        None,
        true,
        TEST_PARTITION_NAME,
        TEST_VBMETA_ROLLBACK_LOCATION as u32,
    );

    let result = slot_verify(
        &mut ops,
        &[&CString::new(TEST_PARTITION_NAME).unwrap()],
        None,
        // Tell libavb that the vbmeta image is embedded, not in its own partition.
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_NO_VBMETA_PARTITION,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    );

    let data = result.unwrap();

    // Vbmeta should indicate that it came from `TEST_PARTITION_NAME`.
    assert_eq!(data.vbmeta_data().len(), 1);
    let vbmeta_data = &data.vbmeta_data()[0];
    assert_eq!(
        vbmeta_data.partition_name().to_str().unwrap(),
        TEST_PARTITION_NAME
    );

    // Partition should indicate that it came from `TEST_PARTITION_NAME`, but only contain the
    // image contents.
    assert_eq!(data.partition_data().len(), 1);
    let partition_data = &data.partition_data()[0];
    assert_eq!(
        partition_data.partition_name().to_str().unwrap(),
        TEST_PARTITION_NAME
    );
    assert_eq!(partition_data.data(), fs::read(TEST_IMAGE_PATH).unwrap());
}

// Validate the custom behavior if the combined image + vbmeta live in the `boot` partition.
#[test]
fn vbmeta_with_boot_partition_passes_verification() {
    let mut ops = test_ops_boot_partition();

    let result = verify_boot_partition(&mut ops);

    let data = result.unwrap();

    // Vbmeta should indicate that it came from `boot`.
    assert_eq!(data.vbmeta_data().len(), 1);
    let vbmeta_data = &data.vbmeta_data()[0];
    assert_eq!(vbmeta_data.partition_name().to_str().unwrap(), "boot");

    // Partition should indicate that it came from `boot`, but only contain the image contents.
    assert_eq!(data.partition_data().len(), 1);
    let partition_data = &data.partition_data()[0];
    assert_eq!(partition_data.partition_name().to_str().unwrap(), "boot");
    assert_eq!(partition_data.data(), fs::read(TEST_IMAGE_PATH).unwrap());
}

#[test]
fn persistent_digest_verification_updates_persistent_value() {
    // With persistent digests, the image hash isn't stored in the descriptor, but is instead
    // calculated on-demand and stored into a named persistent value. So our test image can contain
    // anything, but does have to match the size indicated by the descriptor.
    let image_contents = vec![0xAAu8; TEST_IMAGE_SIZE];
    let mut ops = test_ops_persistent_digest(image_contents.clone());

    {
        let result = verify_persistent_digest(&mut ops);
        let data = result.unwrap();
        assert_eq!(data.partition_data()[0].data(), image_contents);
    } // Drop `result` here so it releases `ops` and we can use it again.

    assert!(ops
        .persistent_values
        .contains_key(&persistent_digest_value_name()));
}

#[cfg(feature = "uuid")]
#[test]
fn successful_verification_substitutes_partition_guid() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions.get_mut("vbmeta").unwrap().uuid = uuid!("01234567-89ab-cdef-0123-456789abcdef");

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    assert!(data
        .cmdline()
        .to_str()
        .unwrap()
        .contains("androidboot.vbmeta.device=PARTUUID=01234567-89ab-cdef-0123-456789abcdef"));
}

#[cfg(feature = "uuid")]
#[test]
fn successful_verification_substitutes_boot_partition_guid() {
    let mut ops = test_ops_boot_partition();
    ops.partitions.get_mut("boot").unwrap().uuid = uuid!("01234567-89ab-cdef-0123-456789abcdef");

    let result = verify_boot_partition(&mut ops);

    let data = result.unwrap();
    // In this case libavb substitutes the `boot` partition GUID in for `vbmeta`.
    assert!(data
        .cmdline()
        .to_str()
        .unwrap()
        .contains("androidboot.vbmeta.device=PARTUUID=01234567-89ab-cdef-0123-456789abcdef"));
}

#[test]
fn corrupted_image_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    modify_partition_contents(&mut ops, TEST_PARTITION_NAME);

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Verification(None)));
}

#[test]
fn read_partition_callback_error_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions.remove(TEST_PARTITION_NAME);

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn undersized_partition_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions
        .get_mut(TEST_PARTITION_NAME)
        .unwrap()
        .contents
        .pop();

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn corrupted_vbmeta_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    modify_partition_contents(&mut ops, "vbmeta");

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::InvalidMetadata));
}

#[test]
fn rollback_violation_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    // Device with rollback = 1 should refuse to boot image with rollback = 0.
    ops.rollbacks.insert(TEST_VBMETA_ROLLBACK_LOCATION, 1);

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::RollbackIndex));
}

#[test]
fn rollback_callback_error_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.rollbacks.clear();

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn untrusted_vbmeta_keys_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.add_vbmeta_key(fs::read(TEST_PUBLIC_KEY_PATH).unwrap(), None, false);

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::PublicKeyRejected));
}

#[test]
fn vbmeta_keys_callback_error_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.vbmeta_keys.clear();

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn unlock_state_callback_error_fails_verification() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.unlock_state = Err(IoError::Io);

    let result = verify_one_image_one_vbmeta(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn persistent_digest_mismatch_fails_verification() {
    let image_contents = vec![0xAAu8; TEST_IMAGE_SIZE];
    let mut ops = test_ops_persistent_digest(image_contents.clone());
    // Put in an incorrect persistent digest; `slot_verify()` should detect the mismatch and fail.
    ops.add_persistent_value(&persistent_digest_value_name(), Ok(b"incorrect_digest"));
    // Make a copy so we can verify the persistent values don't change on failure.
    let original_persistent_values = ops.persistent_values.clone();

    assert!(verify_persistent_digest(&mut ops).is_err());

    // Persistent value should be unchanged.
    assert_eq!(ops.persistent_values, original_persistent_values);
}

#[test]
fn persistent_digest_callback_error_fails_verification() {
    let image_contents = vec![0xAAu8; TEST_IMAGE_SIZE];
    let mut ops = test_ops_persistent_digest(image_contents.clone());
    ops.add_persistent_value(&persistent_digest_value_name(), Err(IoError::NoSuchValue));

    let result = verify_persistent_digest(&mut ops);

    let error = result.unwrap_err();
    assert!(matches!(error, SlotVerifyError::Io));
}

#[test]
fn corrupted_image_with_allow_verification_error_flag_fails_verification_with_data() {
    let mut ops = test_ops_one_image_one_vbmeta();
    modify_partition_contents(&mut ops, TEST_PARTITION_NAME);

    let result = slot_verify(
        &mut ops,
        &[&CString::new(TEST_PARTITION_NAME).unwrap()],
        None,
        // Pass the flag to allow verification errors.
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_ALLOW_VERIFICATION_ERROR,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    );

    // Verification should fail, but with the `AVB_SLOT_VERIFY_FLAGS_ALLOW_VERIFICATION_ERROR` flag
    // it should give us back the verification data.
    let error = result.unwrap_err();
    let data = match error {
        SlotVerifyError::Verification(Some(data)) => data,
        _ => panic!("Expected verification data to exist"),
    };

    // vbmeta verification should have succeeded since that image was still correct.
    assert_eq!(data.vbmeta_data().len(), 1);
    assert_eq!(data.vbmeta_data()[0].verify_result(), Ok(()));
    // Partition verification should have failed since we modified the image.
    assert_eq!(data.partition_data().len(), 1);
    assert!(matches!(
        data.partition_data()[0].verify_result(),
        Err(SlotVerifyError::Verification(None))
    ));
}

#[test]
fn one_image_one_vbmeta_verification_data_display() {
    let mut ops = test_ops_one_image_one_vbmeta();

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    assert_eq!(
        format!("{data}"),
        r#"slot: "", vbmeta: ["vbmeta": Ok(())], images: ["test_part": Ok(())]"#
    );
}

#[test]
fn preloaded_image_verification_data_display() {
    let mut ops = test_ops_one_image_one_vbmeta();
    ops.partitions
        .get_mut(TEST_PARTITION_NAME)
        .unwrap()
        .preloaded = true;

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    assert_eq!(
        format!("{data}"),
        r#"slot: "", vbmeta: ["vbmeta": Ok(())], images: ["test_part"(p): Ok(())]"#
    );
}

#[test]
fn two_images_one_vbmeta_verification_data_display() {
    let mut ops = test_ops_two_images_one_vbmeta();

    let result = verify_two_images(&mut ops);

    let data = result.unwrap();
    assert_eq!(
        format!("{data}"),
        r#"slot: "", vbmeta: ["vbmeta": Ok(())], images: ["test_part": Ok(()), "test_part_2": Ok(())]"#
    );
}

#[test]
fn corrupted_image_verification_data_display() {
    let mut ops = test_ops_one_image_one_vbmeta();
    modify_partition_contents(&mut ops, TEST_PARTITION_NAME);

    let result = slot_verify(
        &mut ops,
        &[&CString::new(TEST_PARTITION_NAME).unwrap()],
        None,
        SlotVerifyFlags::AVB_SLOT_VERIFY_FLAGS_ALLOW_VERIFICATION_ERROR,
        HashtreeErrorMode::AVB_HASHTREE_ERROR_MODE_EIO,
    );

    let error = result.unwrap_err();
    let data = match error {
        SlotVerifyError::Verification(Some(data)) => data,
        _ => panic!("Expected verification data to exist"),
    };
    assert_eq!(
        format!("{data}"),
        r#"slot: "", vbmeta: ["vbmeta": Ok(())], images: ["test_part": Err(Verification(None))]"#
    );
}

#[test]
fn one_image_gives_single_descriptor() {
    let mut ops = test_ops_one_image_one_vbmeta();

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    assert_eq!(data.vbmeta_data()[0].descriptors().unwrap().len(), 1);
}

#[test]
fn two_images_gives_two_descriptors() {
    let mut ops = test_ops_two_images_one_vbmeta();

    let result = verify_two_images(&mut ops);

    let data = result.unwrap();
    assert_eq!(data.vbmeta_data()[0].descriptors().unwrap().len(), 2);
}

/// Runs verification on the given contents and checks for a resulting descriptor.
///
/// This test helper performs the following steps:
///
/// 1. set up a `TestOps` for the default test image/vbmeta
/// 2. replace the vbmeta image with the contents at `vbmeta_path`
/// 3. run verification
/// 4. check that the given `descriptor` exists in the verification data
fn verify_and_find_descriptor(vbmeta_path: &str, expected_descriptor: &Descriptor) {
    let mut ops = test_ops_one_image_one_vbmeta();

    // Replace the vbmeta image with the requested variation.
    ops.add_partition("vbmeta", fs::read(vbmeta_path).unwrap());

    let result = verify_one_image_one_vbmeta(&mut ops);

    let data = result.unwrap();
    let descriptors = &data.vbmeta_data()[0].descriptors().unwrap();
    assert!(descriptors.contains(expected_descriptor));
}

#[test]
fn verify_hash_descriptor() {
    verify_and_find_descriptor(
        // The standard vbmeta image should contain the hash descriptor.
        TEST_VBMETA_PATH,
        &Descriptor::Hash(HashDescriptor {
            image_size: TEST_IMAGE_SIZE as u64,
            hash_algorithm: TEST_IMAGE_HASH_ALGO,
            flags: HashDescriptorFlags(0),
            partition_name: TEST_PARTITION_NAME,
            salt: &decode(TEST_IMAGE_SALT_HEX).unwrap(),
            digest: &decode(TEST_IMAGE_DIGEST_HEX).unwrap(),
        }),
    );
}

#[test]
fn verify_property_descriptor() {
    verify_and_find_descriptor(
        TEST_VBMETA_WITH_PROPERTY_PATH,
        &Descriptor::Property(PropertyDescriptor {
            key: TEST_PROPERTY_KEY,
            value: TEST_PROPERTY_VALUE,
        }),
    );
}

#[test]
fn verify_hashtree_descriptor() {
    verify_and_find_descriptor(
        TEST_VBMETA_WITH_HASHTREE_PATH,
        &Descriptor::Hashtree(HashtreeDescriptor {
            dm_verity_version: 1,
            image_size: TEST_IMAGE_SIZE as u64,
            tree_offset: TEST_IMAGE_SIZE as u64,
            tree_size: 4096,
            data_block_size: 4096,
            hash_block_size: 4096,
            fec_num_roots: 0,
            fec_offset: 0,
            fec_size: 0,
            hash_algorithm: TEST_HASHTREE_ALGORITHM,
            flags: HashtreeDescriptorFlags(0),
            partition_name: TEST_PARTITION_HASH_TREE_NAME,
            salt: &decode(TEST_HASHTREE_SALT_HEX).unwrap(),
            root_digest: &decode(TEST_HASHTREE_DIGEST_HEX).unwrap(),
        }),
    );
}

#[test]
fn verify_kernel_commandline_descriptor() {
    verify_and_find_descriptor(
        TEST_VBMETA_WITH_COMMANDLINE_PATH,
        &Descriptor::KernelCommandline(KernelCommandlineDescriptor {
            flags: KernelCommandlineDescriptorFlags(0),
            commandline: TEST_KERNEL_COMMANDLINE,
        }),
    );
}

#[test]
fn verify_chain_partition_descriptor() {
    let mut ops = test_ops_two_images_one_vbmeta();

    // Set up the fake ops to contain:
    // * the default test image in TEST_PARTITION_NAME
    // * a signed test image with vbmeta footer in TEST_PARTITION_2_NAME
    // * a vbmeta image in "vbmeta" which:
    //   * signs the default TEST_PARTITION_NAME image
    //   * chains to TEST_PARTITION_2_NAME
    //
    // Since this is an unusual configuration, it's simpler to just set it up manually here
    // rather than try to adapt `verify_and_find_descriptor()` for this one case.
    ops.add_partition(
        "vbmeta",
        fs::read(TEST_VBMETA_WITH_CHAINED_PARTITION_PATH).unwrap(),
    );
    // Replace the chained partition with the combined image + vbmeta footer.
    ops.add_partition(
        TEST_PARTITION_2_NAME,
        fs::read(TEST_IMAGE_WITH_VBMETA_FOOTER_FOR_TEST_PART_2).unwrap(),
    );
    // Add the rollback index for the chained partition's location.
    ops.rollbacks.insert(
        TEST_CHAINED_PARTITION_ROLLBACK_LOCATION,
        TEST_CHAINED_PARTITION_ROLLBACK_INDEX,
    );

    let result = verify_two_images(&mut ops);

    let data = result.unwrap();
    // We should have two vbmeta images - one from the "vbmeta" partition, the other embedded
    // in the footer of TEST_PARTITION_2_NAME.
    let vbmetas = data.vbmeta_data();
    assert_eq!(vbmetas.len(), 2);
    // Search for the main vbmeta so we don't assume any particular order.
    let main_vbmeta = vbmetas
        .iter()
        .find(|v| v.partition_name().to_str().unwrap() == "vbmeta")
        .unwrap();

    // The main vbmeta should contain the chain descriptor.
    let expected = ChainPartitionDescriptor {
        rollback_index_location: TEST_CHAINED_PARTITION_ROLLBACK_LOCATION as u32,
        partition_name: TEST_PARTITION_2_NAME,
        public_key: &fs::read(TEST_PUBLIC_KEY_RSA8192_PATH).unwrap(),
        flags: ChainPartitionDescriptorFlags(0),
    };
    assert!(main_vbmeta
        .descriptors()
        .unwrap()
        .contains(&Descriptor::ChainPartition(expected)));
}
