/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <vector>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <android/api-level.h>
#include <bootimg.h>
#include <fs_avb/fs_avb_util.h>
#include <gtest/gtest.h>
#include <libavb/libavb.h>
#include <storage_literals/storage_literals.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>

#include "gsi_validation_utils.h"

using namespace std::literals;
using namespace android::storage_literals;

namespace {

std::string GetBlockDevicePath(const std::string &name) {
  return "/dev/block/by-name/" + name + fs_mgr_get_slot_suffix();
}

class GkiBootImage {
 public:
  GkiBootImage(const uint8_t *data, size_t size) : data_(data, data + size) {}

  static uint32_t GetBootHeaderVersion(const void *data) {
    return static_cast<const boot_img_hdr_v0 *>(data)->header_version;
  }

  uint32_t header_version() const { return GetBootHeaderVersion(data()); }

  uint32_t kernel_pages() const { return GetNumberOfPages(kernel_size()); }

  uint32_t ramdisk_pages() const { return GetNumberOfPages(ramdisk_size()); }

  uint32_t kernel_offset() const {
    // The first page must be the boot image header.
    return page_size();
  }

  uint32_t ramdisk_offset() const {
    return kernel_offset() + kernel_pages() * page_size();
  }

  virtual uint32_t page_size() const = 0;
  virtual uint32_t os_version() const = 0;
  virtual uint32_t kernel_size() const = 0;
  virtual uint32_t ramdisk_size() const = 0;
  virtual uint32_t signature_size() const = 0;
  virtual uint32_t signature_offset() const = 0;

  uint32_t GetNumberOfPages(uint32_t value) const {
    return (value + page_size() - 1) / page_size();
  }

  std::vector<uint8_t> GetKernel() const {
    return Slice(kernel_offset(), kernel_size());
  }

  // Get "effective" boot image. The pure boot image without any boot signature.
  std::vector<uint8_t> GetBootImage() const {
    return Slice(0, signature_offset());
  }

  // Parse a vector of vbmeta image from the boot signature section.
  std::vector<android::fs_mgr::VBMetaData> GetBootSignatures() const {
    const auto begin_offset = std::clamp<size_t>(signature_offset(), 0, size());
    const uint8_t *buffer = data() + begin_offset;
    // begin_offset + remaining_bytes <= size() because boot_signature must be
    // the last section.
    size_t remaining_bytes =
        std::clamp<size_t>(signature_size(), 0, size() - begin_offset);
    // In case boot_signature is misaligned, shift to the first AVB magic, and
    // treat it as the actual beginning of boot signature.
    while (remaining_bytes >= AVB_MAGIC_LEN) {
      if (!memcmp(buffer, AVB_MAGIC, AVB_MAGIC_LEN)) {
        break;
      }
      ++buffer;
      --remaining_bytes;
    }
    std::vector<android::fs_mgr::VBMetaData> vbmeta_images;
    while (remaining_bytes >= sizeof(AvbVBMetaImageHeader)) {
      if (memcmp(buffer, AVB_MAGIC, AVB_MAGIC_LEN) != 0) {
        break;
      }
      // Extract only the header to calculate the vbmeta image size.
      android::fs_mgr::VBMetaData vbmeta_header(
          buffer, sizeof(AvbVBMetaImageHeader), "boot_signature");
      if (!vbmeta_header.GetVBMetaHeader(/* update_vbmeta_size */ true)) {
        GTEST_LOG_(ERROR) << __FUNCTION__
                          << "(): VBMetaData::GetVBMetaHeader() failed.";
        return {};
      }
      const auto vbmeta_image_size = vbmeta_header.size();
      GTEST_LOG_(INFO) << __FUNCTION__ << "(): Found vbmeta image with size "
                       << vbmeta_image_size;
      if (vbmeta_image_size < sizeof(AvbVBMetaImageHeader)) {
        GTEST_LOG_(ERROR) << __FUNCTION__
                          << "(): Impossible-sized vbmeta image: "
                          << vbmeta_image_size;
        return {};
      }

      if (vbmeta_image_size > remaining_bytes) {
        GTEST_LOG_(ERROR)
            << __FUNCTION__
            << "(): Premature EOF when parsing GKI boot signature.";
        return {};
      }

      vbmeta_images.emplace_back(buffer, vbmeta_image_size, "boot_signature");
      buffer += vbmeta_image_size;
      remaining_bytes -= vbmeta_image_size;
    }
    return vbmeta_images;
  }

  virtual ~GkiBootImage() = default;

 protected:
  const uint8_t *data() const { return data_.data(); }

  size_t size() const { return data_.size(); }

  std::vector<uint8_t> Slice(size_t offset, size_t length) const {
    const auto begin_offset = std::clamp<size_t>(offset, 0, size());
    const auto end_offset =
        std::clamp<size_t>(begin_offset + length, begin_offset, size());
    const auto begin = data() + begin_offset;
    const auto end = data() + end_offset;
    return {begin, end};
  }

 private:
  std::vector<uint8_t> data_;
};

class GkiBootImageV2 : public GkiBootImage {
 public:
  GkiBootImageV2(const uint8_t *data, size_t size) : GkiBootImage(data, size) {}

  const boot_img_hdr_v2 *boot_header() const {
    return reinterpret_cast<const boot_img_hdr_v2 *>(data());
  }

  uint32_t page_size() const override { return boot_header()->page_size; }

  uint32_t os_version() const override { return boot_header()->os_version; }

  uint32_t kernel_size() const override { return boot_header()->kernel_size; }

  uint32_t ramdisk_size() const override { return boot_header()->ramdisk_size; }

  uint32_t signature_size() const override {
    // The last 16K bytes are by definition the GKI boot signature.
    static constexpr uint32_t kBootSignatureSize = 16_KiB;
    return kBootSignatureSize;
  }

  uint32_t signature_offset() const override {
    if (size() < signature_size()) {
      return 0;
    }
    return size() - signature_size();
  }

  uint32_t recovery_dtbo_size() const {
    return boot_header()->recovery_dtbo_size;
  }

  uint64_t recovery_dtbo_offset() const {
    return boot_header()->recovery_dtbo_offset;
  }
};

class GkiBootImageV4 : public GkiBootImage {
 public:
  GkiBootImageV4(const uint8_t *data, size_t size) : GkiBootImage(data, size) {}

  const boot_img_hdr_v4 *boot_header() const {
    return reinterpret_cast<const boot_img_hdr_v4 *>(data());
  }

  uint32_t page_size() const override {
    static constexpr uint32_t kPageSize = 4096;
    return kPageSize;
  }

  uint32_t os_version() const override { return boot_header()->os_version; }

  uint32_t kernel_size() const override { return boot_header()->kernel_size; }

  uint32_t ramdisk_size() const override { return boot_header()->ramdisk_size; }

  uint32_t signature_size() const override {
    // For Android12 GKI, the |.signature_size| field is respected.
    // For Android13+ GKI, the |.signature_size| field must be zero, and the
    // last 16K bytes are by definition the GKI boot signature.
    static constexpr uint32_t kBootSignatureSize = 16_KiB;
    const uint32_t value = boot_header()->signature_size;
    return value ? value : kBootSignatureSize;
  }

  uint32_t signature_offset() const override {
    return ramdisk_offset() + ramdisk_pages() * page_size();
  }
};

std::string GetAvbProperty(
    const std::string &name,
    const std::vector<android::fs_mgr::VBMetaData> &vbmeta_images) {
  const std::string prop_name = "com.android.build." + name;
  return android::fs_mgr::GetAvbPropertyDescriptor(prop_name, vbmeta_images);
}

std::unique_ptr<GkiBootImage> LoadAndVerifyGkiBootImage(
    std::vector<android::fs_mgr::VBMetaData> *boot_signature_images) {
  const std::string block_device_path = GetBlockDevicePath("boot");
  const std::string TAG = __FUNCTION__ + "("s + block_device_path + ")";
  SCOPED_TRACE(TAG);

  std::string block_device_data;
  if (!android::base::ReadFileToString(block_device_path, &block_device_data,
                                       /* follow_symlinks */ true)) {
    ADD_FAILURE() << "Failed to read '" << block_device_path
                  << "': " << strerror(errno);
    return nullptr;
  }
  if (block_device_data.size() <= 4096) {
    ADD_FAILURE() << "Size of '" << block_device_path
                  << "' is impossibly small: " << block_device_data.size();
    return nullptr;
  }

  if (block_device_data.substr(0, BOOT_MAGIC_SIZE) != BOOT_MAGIC) {
    ADD_FAILURE() << "Device has invalid boot magic: " << block_device_path;
    return nullptr;
  }

  // Remove the AVB footer and chained vbmeta image if there is any.
  if (block_device_data.size() > AVB_FOOTER_SIZE) {
    const uint8_t *footer_address =
        reinterpret_cast<const uint8_t *>(block_device_data.data()) +
        block_device_data.size() - AVB_FOOTER_SIZE;
    AvbFooter vbmeta_footer;
    if (avb_footer_validate_and_byteswap(
            reinterpret_cast<const AvbFooter *>(footer_address),
            &vbmeta_footer)) {
      block_device_data.resize(vbmeta_footer.original_image_size);
    }
  }

  std::unique_ptr<GkiBootImage> boot_image;
  const auto boot_header_version =
      GkiBootImage::GetBootHeaderVersion(block_device_data.data());
  if (boot_header_version == 4) {
    boot_image = std::make_unique<GkiBootImageV4>(
        reinterpret_cast<const uint8_t *>(block_device_data.data()),
        block_device_data.size());
  } else if (boot_header_version == 2) {
    boot_image = std::make_unique<GkiBootImageV2>(
        reinterpret_cast<const uint8_t *>(block_device_data.data()),
        block_device_data.size());
  } else {
    ADD_FAILURE() << "Unexpected boot header version: " << boot_header_version;
    return nullptr;
  }

  *boot_signature_images = boot_image->GetBootSignatures();
  if (boot_signature_images->empty()) {
    ADD_FAILURE() << "Failed to load the boot signature.";
    return nullptr;
  }

  // Verify that the vbmeta images in boot_signature are certified.
  for (const auto &vbmeta_image : *boot_signature_images) {
    size_t pk_len;
    const uint8_t *pk_data;
    const auto vbmeta_verify_result = avb_vbmeta_image_verify(
        vbmeta_image.data(), vbmeta_image.size(), &pk_data, &pk_len);
    if (vbmeta_verify_result != AVB_VBMETA_VERIFY_RESULT_OK) {
      ADD_FAILURE() << "Failed to verify boot_signature: "
                    << avb_vbmeta_verify_result_to_string(vbmeta_verify_result);
      return nullptr;
    }
    const std::string out_public_key_data(
        reinterpret_cast<const char *>(pk_data), pk_len);
    if (out_public_key_data.empty()) {
      ADD_FAILURE() << "The GKI image descriptor is not signed.";
      continue;
    }
    if (!ValidatePublicKeyBlob(out_public_key_data)) {
      ADD_FAILURE()
          << "The GKI image descriptor is not signed by an official key.";
      continue;
    }
  }

  GTEST_LOG_(INFO) << TAG << ": boot.fingerprint: "
                   << GetAvbProperty("boot.fingerprint",
                                     *boot_signature_images);
  GTEST_LOG_(INFO) << TAG
                   << ": header version: " << boot_image->header_version()
                   << ", kernel size: " << boot_image->kernel_size()
                   << ", ramdisk size: " << boot_image->ramdisk_size()
                   << ", signature size: " << boot_image->signature_size();

  return boot_image;
}

// Verify image data integrity with an AVB hash descriptor.
void VerifyImageDescriptor(
    const std::vector<uint8_t> &image,
    const android::fs_mgr::FsAvbHashDescriptor &descriptor) {
  const std::string TAG = __FUNCTION__ + "("s + descriptor.partition_name + ")";
  SCOPED_TRACE(TAG);

  ASSERT_EQ(image.size(), descriptor.image_size);

  const std::string &salt_str = descriptor.salt;
  const std::string &expected_digest_str = descriptor.digest;

  const std::string hash_algorithm(
      reinterpret_cast<const char *>(descriptor.hash_algorithm));
  GTEST_LOG_(INFO) << TAG << ": hash_algorithm = " << hash_algorithm;

  std::unique_ptr<ShaHasher> hasher = CreateShaHasher(hash_algorithm);
  ASSERT_NE(nullptr, hasher);

  std::vector<uint8_t> salt, expected_digest, out_digest;

  ASSERT_TRUE(HexToBytes(salt_str, &salt))
      << "Invalid salt in descriptor: " << salt_str;
  ASSERT_TRUE(HexToBytes(expected_digest_str, &expected_digest))
      << "Invalid digest in descriptor: " << expected_digest_str;

  ASSERT_EQ(expected_digest.size(), hasher->GetDigestSize());
  out_digest.resize(hasher->GetDigestSize());

  ASSERT_TRUE(hasher->CalculateDigest(image.data(), image.size(), salt.data(),
                                      descriptor.salt_len, out_digest.data()))
      << "Unable to calculate image digest.";

  ASSERT_EQ(out_digest.size(), expected_digest.size())
      << "Calculated digest size does not match expected digest size.";

  ASSERT_EQ(out_digest, expected_digest)
      << "Calculated digest does not match expected digest.";
}
}  // namespace

class GkiComplianceTest : public testing::Test {
 protected:
  void SetUp() override {
    // Fetch device runtime information.
    runtime_info = android::vintf::VintfObject::GetRuntimeInfo();
    ASSERT_NE(nullptr, runtime_info);

    product_first_api_level = GetProductFirstApiLevel();

    /* Skip for non-arm64 kernels that do not mandate GKI yet. */
    if (runtime_info->hardwareId() != "aarch64" &&
        runtime_info->hardwareId() != "armv8l") {
      GTEST_SKIP() << "Exempt from GKI test on non-arm64 kernel devices";
    }

    /* Skip for form factors that do not mandate GKI yet */
    if (IsTvDevice()) {
      GTEST_SKIP() << "Exempt from GKI test on TV devices";
    }

    GTEST_LOG_(INFO) << runtime_info->osName() << " "
                     << runtime_info->osRelease();
    GTEST_LOG_(INFO) << "Product first API level: " << product_first_api_level;
  }

  bool ShouldSkipGkiComplianceV2();

  std::shared_ptr<const android::vintf::RuntimeInfo> runtime_info;
  int product_first_api_level;
};

bool GkiComplianceTest::ShouldSkipGkiComplianceV2() {
  /* Skip for devices if the kernel version is not >= 5.10. */
  if (runtime_info->kernelVersion().dropMinor() <
      android::vintf::Version{5, 10}) {
    GTEST_LOG_(INFO) << "Exempt from GKI 2.0 test on kernel version: "
                     << runtime_info->kernelVersion();
    return true;
  }
  /* Skip for devices launched before Android S. */
  if (product_first_api_level < __ANDROID_API_S__) {
    GTEST_LOG_(INFO) << "Exempt from GKI 2.0 test on pre-S launched devices";
    return true;
  }
  /*
   * Skip for automotive devices if the kernel version is not >= 5.15 or
   * the device is launched before Android T.
   */
  if (IsAutomotiveDevice()) {
    if (runtime_info->kernelVersion().dropMinor() <
        android::vintf::Version{5, 15}) {
      GTEST_LOG_(INFO) << "Exempt from GKI test on kernel version: "
                       << runtime_info->kernelVersion();
      return true;
    }
    if (product_first_api_level < __ANDROID_API_T__) {
      GTEST_LOG_(INFO) << "Exempt from GKI test on pre-T launched devices";
      return true;
    }
  }
  return false;
}

TEST_F(GkiComplianceTest, GkiComplianceV1) {
  if (product_first_api_level < __ANDROID_API_R__) {
    GTEST_SKIP() << "Exempt from GKI 1.0 test: product first API level ("
                 << product_first_api_level << ") < " << __ANDROID_API_R__;
  }
  if (IsAutomotiveDevice()) {
    GTEST_SKIP() << "Skip GKI vbmeta check for automotive devices";
  }
  /* Skip for devices if the kernel version is not 5.4. */
  if (runtime_info->kernelVersion().dropMinor() !=
      android::vintf::Version{5, 4}) {
    GTEST_SKIP() << "Exempt from GKI 1.0 test on kernel version: "
                 << runtime_info->kernelVersion();
  }

  /* load vbmeta struct from boot, verify struct integrity */
  std::string out_public_key_data;
  android::fs_mgr::VBMetaVerifyResult out_verify_result;
  const std::string boot_path = GetBlockDevicePath("boot");
  std::unique_ptr<android::fs_mgr::VBMetaData> vbmeta =
      android::fs_mgr::LoadAndVerifyVbmetaByPath(
          boot_path, "boot", "" /* expected_key_blob */,
          true /* allow verification error */, false /* rollback_protection */,
          false /* is_chained_vbmeta */, &out_public_key_data,
          nullptr /* out_verification_disabled */, &out_verify_result);

  ASSERT_TRUE(vbmeta) << "Verification of GKI vbmeta fails.";
  ASSERT_FALSE(out_public_key_data.empty()) << "The GKI image is not signed.";
  EXPECT_TRUE(ValidatePublicKeyBlob(out_public_key_data))
      << "The GKI image is not signed by an official key.";
  EXPECT_EQ(out_verify_result, android::fs_mgr::VBMetaVerifyResult::kSuccess)
      << "Verification of the GKI vbmeta structure failed.";

  /* verify boot partition according to vbmeta structure */
  std::unique_ptr<android::fs_mgr::FsAvbHashDescriptor> descriptor =
      android::fs_mgr::GetHashDescriptor("boot", std::move(*vbmeta));
  ASSERT_TRUE(descriptor)
      << "Failed to load hash descriptor from boot.img vbmeta";

  android::base::unique_fd fd(open(boot_path.c_str(), O_RDONLY));
  ASSERT_TRUE(fd.ok()) << "Fail to open boot partition. Try 'adb root'.";

  std::vector<uint8_t> boot_partition_vector;
  boot_partition_vector.resize(descriptor->image_size);
  ASSERT_TRUE(android::base::ReadFully(fd, boot_partition_vector.data(),
                                       descriptor->image_size))
      << "Could not read boot partition to vector.";

  ASSERT_NO_FATAL_FAILURE(
      VerifyImageDescriptor(boot_partition_vector, *descriptor));
}

// Verify the entire boot image.
TEST_F(GkiComplianceTest, GkiComplianceV2) {
  if (ShouldSkipGkiComplianceV2()) {
    GTEST_SKIP() << "Skipping GkiComplianceV2 test";
  }

  // GKI 2.0 ensures getKernelLevel() to return valid value.
  std::string error_msg;
  const auto kernel_level =
      android::vintf::VintfObject::GetInstance()->getKernelLevel(&error_msg);
  ASSERT_NE(android::vintf::Level::UNSPECIFIED, kernel_level) << error_msg;

  std::vector<android::fs_mgr::VBMetaData> boot_signature_images;
  std::unique_ptr<GkiBootImage> boot_image =
      LoadAndVerifyGkiBootImage(&boot_signature_images);
  ASSERT_NE(nullptr, boot_image);
  ASSERT_LE(1, boot_signature_images.size());
  EXPECT_EQ(4, boot_image->header_version());

  if (kernel_level >= android::vintf::Level::T) {
    GTEST_LOG_(INFO)
        << "Android T+ verification scheme. The GKI boot.img must contain only "
           "the generic kernel but not the generic ramdisk.";
    EXPECT_EQ(0, boot_image->ramdisk_size())
        << "'boot' partition mustn't include a ramdisk image.";
    EXPECT_EQ(0, boot_image->os_version())
        << "OS version and security patch level should be defined in the "
           "chained vbmeta image instead.";
  }

  std::unique_ptr<android::fs_mgr::FsAvbHashDescriptor> boot_descriptor =
      android::fs_mgr::GetHashDescriptor("boot", boot_signature_images);
  ASSERT_NE(nullptr, boot_descriptor)
      << "Failed to load the 'boot' hash descriptor.";
  ASSERT_NO_FATAL_FAILURE(
      VerifyImageDescriptor(boot_image->GetBootImage(), *boot_descriptor));
}

// Verify only the 'generic_kernel' descriptor.
TEST_F(GkiComplianceTest, GkiComplianceV2_kernel) {
  if (ShouldSkipGkiComplianceV2()) {
    GTEST_SKIP() << "Skipping GkiComplianceV2 test";
  }

  // GKI 2.0 ensures getKernelLevel() to return valid value.
  std::string error_msg;
  const auto kernel_level =
      android::vintf::VintfObject::GetInstance()->getKernelLevel(&error_msg);
  ASSERT_NE(android::vintf::Level::UNSPECIFIED, kernel_level) << error_msg;
  if (kernel_level < android::vintf::Level::T) {
    GTEST_SKIP() << "Skip for kernel level (" << kernel_level << ") < T ("
                 << android::vintf::Level::T << ")";
  }

  std::vector<android::fs_mgr::VBMetaData> boot_signature_images;
  std::unique_ptr<GkiBootImage> boot_image =
      LoadAndVerifyGkiBootImage(&boot_signature_images);
  ASSERT_NE(nullptr, boot_image);
  ASSERT_LE(1, boot_signature_images.size());

  std::unique_ptr<android::fs_mgr::FsAvbHashDescriptor>
      generic_kernel_descriptor = android::fs_mgr::GetHashDescriptor(
          "generic_kernel", boot_signature_images);
  ASSERT_NE(nullptr, generic_kernel_descriptor)
      << "Failed to load the 'generic_kernel' hash descriptor.";
  ASSERT_NO_FATAL_FAILURE(VerifyImageDescriptor(boot_image->GetKernel(),
                                                *generic_kernel_descriptor));
}

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  android::base::InitLogging(argv, android::base::StderrLogger);
  return RUN_ALL_TESTS();
}
