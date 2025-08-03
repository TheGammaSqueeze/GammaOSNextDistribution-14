/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <limits>
#include <string>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <libavb/libavb.h>
#include <ziparchive/zip_archive.h>

#include "apex_file.h"
#include "apexd_test_utils.h"
#include "apexd_utils.h"

using android::base::GetExecutableDirectory;
using android::base::Result;

static const std::string kTestDataDir = GetExecutableDirectory() + "/";

namespace android {
namespace apex {
namespace {

struct ApexFileTestParam {
  const char* type;
  const char* prefix;
};

constexpr const ApexFileTestParam kParameters[] = {
    {"ext4", "apex.apexd_test"},
    {"f2fs", "apex.apexd_test_f2fs"},
    {"erofs", "apex.apexd_test_erofs"}};

class ApexFileTest : public ::testing::TestWithParam<ApexFileTestParam> {};

INSTANTIATE_TEST_SUITE_P(Apex, ApexFileTest, ::testing::ValuesIn(kParameters));

TEST_P(ApexFileTest, GetOffsetOfSimplePackage) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_TRUE(apex_file.ok());

  uint32_t zip_image_offset;
  size_t zip_image_size;
  {
    ZipArchiveHandle handle;
    int32_t rc = OpenArchive(file_path.c_str(), &handle);
    ASSERT_EQ(0, rc);
    auto close_guard =
        android::base::make_scope_guard([&handle]() { CloseArchive(handle); });

    ZipEntry entry;
    rc = FindEntry(handle, "apex_payload.img", &entry);
    ASSERT_EQ(0, rc);

    zip_image_offset = entry.offset;
    EXPECT_EQ(zip_image_offset % 4096, 0U);
    zip_image_size = entry.uncompressed_length;
    EXPECT_EQ(zip_image_size, entry.compressed_length);
  }

  EXPECT_EQ(zip_image_offset, apex_file->GetImageOffset().value());
  EXPECT_EQ(zip_image_size, apex_file->GetImageSize().value());
}

TEST_P(ApexFileTest, OpenBlockApex) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  TemporaryFile temp_file;
  auto loop_device = WriteBlockApex(file_path, temp_file.path);

  Result<ApexFile> apex_file_sized = ApexFile::Open(temp_file.path);
  ASSERT_RESULT_OK(apex_file_sized);

  EXPECT_EQ(apex_file->GetImageOffset(), apex_file_sized->GetImageOffset());
  EXPECT_EQ(apex_file->GetImageSize(), apex_file_sized->GetImageSize());
}

TEST(ApexFileTest, GetOffsetMissingFile) {
  const std::string file_path = kTestDataDir + "missing.apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_FALSE(apex_file.ok());
  ASSERT_THAT(apex_file.error().message(),
              ::testing::HasSubstr("Failed to open package"));
}

TEST_P(ApexFileTest, GetApexManifest) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);
  EXPECT_EQ("com.android.apex.test_package", apex_file->GetManifest().name());
  EXPECT_EQ(1u, apex_file->GetManifest().version());
}

TEST_P(ApexFileTest, VerifyApexVerity) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  auto verity_or =
      apex_file->VerifyApexVerity(apex_file->GetBundledPublicKey());
  ASSERT_RESULT_OK(verity_or);

  const ApexVerityData& data = *verity_or;
  EXPECT_NE(nullptr, data.desc.get());
  EXPECT_EQ(std::string("368a22e64858647bc45498e92f749f85482ac468"
                        "50ca7ec8071f49dfa47a243c"),
            data.salt);

  const std::string digest_path =
      kTestDataDir + GetParam().prefix + "_digest.txt";
  std::string root_digest;
  ASSERT_TRUE(android::base::ReadFileToString(digest_path, &root_digest))
      << "Failed to read " << digest_path;
  root_digest = android::base::Trim(root_digest);

  EXPECT_EQ(std::string(root_digest), data.root_digest);
}

TEST_P(ApexFileTest, VerifyApexVerityWrongKey) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  auto verity_or = apex_file->VerifyApexVerity("wrong-key");
  ASSERT_FALSE(verity_or.ok());
}

TEST_P(ApexFileTest, GetBundledPublicKey) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  const std::string key_path =
      kTestDataDir + "apexd_testdata/com.android.apex.test_package.avbpubkey";
  std::string key_content;
  ASSERT_TRUE(android::base::ReadFileToString(key_path, &key_content))
      << "Failed to read " << key_path;

  EXPECT_EQ(key_content, apex_file->GetBundledPublicKey());
}

TEST(ApexFileTest, CorrutedApexB146895998) {
  const std::string apex_path = kTestDataDir + "corrupted_b146895998.apex";
  Result<ApexFile> apex = ApexFile::Open(apex_path);
  ASSERT_RESULT_OK(apex);
  ASSERT_FALSE(apex->VerifyApexVerity("ignored").ok());
}

TEST_P(ApexFileTest, RetrieveFsType) {
  const std::string file_path = kTestDataDir + GetParam().prefix + ".apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_TRUE(apex_file.ok());

  EXPECT_EQ(std::string(GetParam().type), apex_file->GetFsType().value());
}

TEST(ApexFileTest, OpenInvalidFilesystem) {
  const std::string file_path =
      kTestDataDir + "apex.apexd_test_corrupt_superblock_apex.apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_FALSE(apex_file.ok());
  ASSERT_THAT(apex_file.error().message(),
              ::testing::HasSubstr("Failed to retrieve filesystem type"));
}

TEST(ApexFileTest, OpenCompressedApexFile) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_TRUE(apex_file.ok());

  ASSERT_TRUE(apex_file->IsCompressed());
  ASSERT_FALSE(apex_file->GetImageOffset().has_value());
  ASSERT_FALSE(apex_file->GetImageSize().has_value());
  ASSERT_FALSE(apex_file->GetFsType().has_value());
}

TEST(ApexFileTest, OpenFailureForCompressedApexWithoutApex) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1_without_apex.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_FALSE(apex_file.ok());
  ASSERT_THAT(apex_file.error().message(),
              ::testing::HasSubstr("Could not find entry"));
}

TEST(ApexFileTest, GetCompressedApexManifest) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);
  EXPECT_EQ("com.android.apex.compressed", apex_file->GetManifest().name());
  EXPECT_EQ(1u, apex_file->GetManifest().version());
}

TEST(ApexFileTest, GetBundledPublicKeyOfCompressedApex) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  const std::string key_path =
      kTestDataDir + "apexd_testdata/com.android.apex.compressed.avbpubkey";
  std::string key_content;
  ASSERT_TRUE(android::base::ReadFileToString(key_path, &key_content))
      << "Failed to read " << key_path;

  EXPECT_EQ(key_content, apex_file->GetBundledPublicKey());
}

TEST(ApexFileTest, CannotVerifyApexVerityForCompressedApex) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  auto apex = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex);
  auto result = apex->VerifyApexVerity(apex->GetBundledPublicKey());
  ASSERT_FALSE(result.ok());
  ASSERT_THAT(
      result.error().message(),
      ::testing::HasSubstr("Cannot verify ApexVerity of compressed APEX"));
}

TEST(ApexFileTest, DecompressCompressedApex) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  // Create a temp dir for decompression
  TemporaryDir tmp_dir;

  const std::string package_name = apex_file->GetManifest().name();
  const std::string decompression_file_path =
      tmp_dir.path + package_name + ".capex";

  auto result = apex_file->Decompress(decompression_file_path);
  ASSERT_RESULT_OK(result);

  // Assert output path is not empty
  auto exists = PathExists(decompression_file_path);
  ASSERT_RESULT_OK(exists);
  ASSERT_TRUE(*exists) << decompression_file_path << " does not exist";

  // Assert properties on the decompressed APEX.
  auto decompressed_apex_file = ApexFile::Open(decompression_file_path);
  ASSERT_RESULT_OK(decompressed_apex_file);
  ASSERT_EQ(apex_file->GetBundledPublicKey(),
            decompressed_apex_file->GetBundledPublicKey());
  ASSERT_EQ(apex_file->GetManifest().name(),
            decompressed_apex_file->GetManifest().name());
  ASSERT_EQ(apex_file->GetManifest().version(),
            decompressed_apex_file->GetManifest().version());
  auto verity_status = decompressed_apex_file->VerifyApexVerity(
      decompressed_apex_file->GetBundledPublicKey());
  ASSERT_RESULT_OK(verity_status);
}

TEST(ApexFileTest, DecompressFailForNormalApex) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1_original.apex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);
  ASSERT_RESULT_OK(apex_file);

  TemporaryFile decompression_file;

  auto result = apex_file->Decompress(decompression_file.path);
  ASSERT_FALSE(result.ok());
  ASSERT_THAT(result.error().message(),
              ::testing::HasSubstr("Cannot decompress an uncompressed APEX"));
}

TEST(ApexFileTest, DecompressFailIfDecompressionPathExists) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);

  // Attempt to decompress in a path that already exists
  TemporaryFile decompression_file;
  auto exists = PathExists(decompression_file.path);
  ASSERT_RESULT_OK(exists);
  ASSERT_TRUE(*exists) << decompression_file.path << " does not exist";

  auto result = apex_file->Decompress(decompression_file.path);
  ASSERT_FALSE(result.ok());
  ASSERT_THAT(result.error().message(),
              ::testing::HasSubstr("Failed to open decompression destination"));
}

TEST(ApexFileTest, GetPathReturnsRealpath) {
  const std::string real_path = kTestDataDir + "apex.apexd_test.apex";
  const std::string symlink_path =
      kTestDataDir + "apex.apexd_test.symlink.apex";

  // In case the link already exists
  int ret = unlink(symlink_path.c_str());
  ASSERT_TRUE(ret == 0 || errno == ENOENT)
      << "failed to unlink " << symlink_path;

  ret = symlink(real_path.c_str(), symlink_path.c_str());
  ASSERT_EQ(0, ret) << "failed to create symlink at " << symlink_path;

  // Open with the symlink. Realpath is expected.
  Result<ApexFile> apex_file = ApexFile::Open(symlink_path);
  ASSERT_RESULT_OK(apex_file);
  ASSERT_EQ(real_path, apex_file->GetPath());
}

TEST(ApexFileTest, CompressedSharedLibsApexIsRejected) {
  const std::string file_path =
      kTestDataDir + "com.android.apex.compressed_sharedlibs.capex";
  Result<ApexFile> apex_file = ApexFile::Open(file_path);

  ASSERT_FALSE(apex_file.ok());
  ASSERT_THAT(apex_file.error().message(),
              ::testing::HasSubstr("Apex providing sharedlibs shouldn't "
                                   "be compressed"));
}

// Check if CAPEX contains originalApexDigest in its manifest
TEST(ApexFileTest, OriginalApexDigest) {
  const std::string capex_path =
      kTestDataDir + "com.android.apex.compressed.v1.capex";
  auto capex = ApexFile::Open(capex_path);
  ASSERT_TRUE(capex.ok());
  const std::string decompressed_apex_path =
      kTestDataDir + "com.android.apex.compressed.v1_original.apex";
  auto decompressed_apex = ApexFile::Open(decompressed_apex_path);
  ASSERT_TRUE(decompressed_apex.ok());
  // Validate root digest
  auto digest = decompressed_apex->VerifyApexVerity(
      decompressed_apex->GetBundledPublicKey());
  ASSERT_TRUE(digest.ok());
  ASSERT_EQ(digest->root_digest,
            capex->GetManifest().capexmetadata().originalapexdigest());
}
}  // namespace
}  // namespace apex
}  // namespace android
