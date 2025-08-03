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

#include "apex_file.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <fcntl.h>
#include <libavb/libavb.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <ziparchive/zip_archive.h>

#include <filesystem>
#include <fstream>
#include <span>

#include "apex_constants.h"
#include "apexd_utils.h"
#include "apexd_verity.h"

using android::base::borrowed_fd;
using android::base::ErrnoError;
using android::base::Error;
using android::base::ReadFullyAtOffset;
using android::base::RemoveFileIfExists;
using android::base::Result;
using android::base::unique_fd;
using ::apex::proto::ApexManifest;

namespace android {
namespace apex {
namespace {

constexpr const char* kImageFilename = "apex_payload.img";
constexpr const char* kCompressedApexFilename = "original_apex";
constexpr const char* kBundledPublicKeyFilename = "apex_pubkey";

struct FsMagic {
  const char* type;
  int32_t offset;
  int16_t len;
  const char* magic;
};
constexpr const FsMagic kFsType[] = {{"f2fs", 1024, 4, "\x10\x20\xf5\xf2"},
                                     {"ext4", 1024 + 0x38, 2, "\123\357"},
                                     {"erofs", 1024, 4, "\xe2\xe1\xf5\xe0"}};

Result<std::string> RetrieveFsType(borrowed_fd fd, uint32_t image_offset) {
  for (const auto& fs : kFsType) {
    char buf[fs.len];
    if (!ReadFullyAtOffset(fd, buf, fs.len, image_offset + fs.offset)) {
      return ErrnoError() << "Couldn't read filesystem magic";
    }
    if (memcmp(buf, fs.magic, fs.len) == 0) {
      return std::string(fs.type);
    }
  }
  return Error() << "Couldn't find filesystem magic";
}

}  // namespace

Result<ApexFile> ApexFile::Open(const std::string& path) {
  std::optional<uint32_t> image_offset;
  std::optional<size_t> image_size;
  std::string manifest_content;
  std::string pubkey;
  std::optional<std::string> fs_type;
  ZipEntry entry;

  unique_fd fd(open(path.c_str(), O_RDONLY | O_BINARY | O_CLOEXEC));
  if (fd < 0) {
    return ErrnoError() << "Failed to open package " << path << ": "
                        << "I/O error";
  }

  ZipArchiveHandle handle;
  auto handle_guard =
      android::base::make_scope_guard([&handle] { CloseArchive(handle); });
  int ret = OpenArchiveFd(fd.get(), path.c_str(), &handle,
                          /*assume_ownership=*/false);
  if (ret < 0) {
    return Error() << "Failed to open package " << path << ": "
                   << ErrorCodeString(ret);
  }

  bool is_compressed = true;
  ret = FindEntry(handle, kCompressedApexFilename, &entry);
  if (ret < 0) {
    is_compressed = false;
  }

  if (!is_compressed) {
    // Locate the mountable image within the zipfile and store offset and size.
    ret = FindEntry(handle, kImageFilename, &entry);
    if (ret < 0) {
      return Error() << "Could not find entry \"" << kImageFilename
                     << "\" or \"" << kCompressedApexFilename
                     << "\" in package " << path << ": "
                     << ErrorCodeString(ret);
    }
    image_offset = entry.offset;
    image_size = entry.uncompressed_length;

    auto fs_type_result = RetrieveFsType(fd, image_offset.value());
    if (!fs_type_result.ok()) {
      return Error() << "Failed to retrieve filesystem type for " << path
                     << ": " << fs_type_result.error();
    }
    fs_type = std::move(*fs_type_result);
  }

  ret = FindEntry(handle, kManifestFilenamePb, &entry);
  if (ret < 0) {
    return Error() << "Could not find entry \"" << kManifestFilenamePb
                   << "\" in package " << path << ": " << ErrorCodeString(ret);
  }

  uint32_t length = entry.uncompressed_length;
  manifest_content.resize(length, '\0');
  ret = ExtractToMemory(handle, &entry,
                        reinterpret_cast<uint8_t*>(&(manifest_content)[0]),
                        length);
  if (ret != 0) {
    return Error() << "Failed to extract manifest from package " << path << ": "
                   << ErrorCodeString(ret);
  }

  ret = FindEntry(handle, kBundledPublicKeyFilename, &entry);
  if (ret >= 0) {
    length = entry.uncompressed_length;
    pubkey.resize(length, '\0');
    ret = ExtractToMemory(handle, &entry,
                          reinterpret_cast<uint8_t*>(&(pubkey)[0]), length);
    if (ret != 0) {
      return Error() << "Failed to extract public key from package " << path
                     << ": " << ErrorCodeString(ret);
    }
  }

  Result<ApexManifest> manifest = ParseManifest(manifest_content);
  if (!manifest.ok()) {
    return manifest.error();
  }

  if (is_compressed && manifest->providesharedapexlibs()) {
    return Error() << "Apex providing sharedlibs shouldn't be compressed";
  }

  // b/179211712 the stored path should be the realpath, otherwise the path we
  // get by scanning the directory would be different from the path we get
  // by reading /proc/mounts, if the apex file is on a symlink dir.
  std::string realpath;
  if (!android::base::Realpath(path, &realpath)) {
    return ErrnoError() << "can't get realpath of " << path;
  }

  return ApexFile(realpath, image_offset, image_size, std::move(*manifest),
                  pubkey, fs_type, is_compressed);
}

// AVB-related code.

namespace {

static constexpr int kVbMetaMaxSize = 64 * 1024;

std::string GetSalt(const AvbHashtreeDescriptor& desc,
                    const uint8_t* trailing_data) {
  const uint8_t* desc_salt = trailing_data + desc.partition_name_len;

  return BytesToHex(desc_salt, desc.salt_len);
}

std::string GetDigest(const AvbHashtreeDescriptor& desc,
                      const uint8_t* trailing_data) {
  const uint8_t* desc_digest =
      trailing_data + desc.partition_name_len + desc.salt_len;

  return BytesToHex(desc_digest, desc.root_digest_len);
}

Result<std::unique_ptr<AvbFooter>> GetAvbFooter(const ApexFile& apex,
                                                const unique_fd& fd) {
  std::array<uint8_t, AVB_FOOTER_SIZE> footer_data;
  auto footer = std::make_unique<AvbFooter>();

  // The AVB footer is located in the last part of the image
  if (!apex.GetImageOffset() || !apex.GetImageSize()) {
    return Error() << "Cannot check avb footer without image offset and size";
  }
  off_t offset = apex.GetImageSize().value() + apex.GetImageOffset().value() -
                 AVB_FOOTER_SIZE;
  int ret = lseek(fd, offset, SEEK_SET);
  if (ret == -1) {
    return ErrnoError() << "Couldn't seek to AVB footer";
  }

  ret = read(fd, footer_data.data(), AVB_FOOTER_SIZE);
  if (ret != AVB_FOOTER_SIZE) {
    return ErrnoError() << "Couldn't read AVB footer";
  }

  if (!avb_footer_validate_and_byteswap((const AvbFooter*)footer_data.data(),
                                        footer.get())) {
    return Error() << "AVB footer verification failed.";
  }

  LOG(VERBOSE) << "AVB footer verification successful.";
  return footer;
}

bool CompareKeys(const uint8_t* key, size_t length,
                 const std::string& public_key_content) {
  return public_key_content.length() == length &&
         memcmp(&public_key_content[0], key, length) == 0;
}

// Verifies correctness of vbmeta and returns public key it was signed with.
Result<std::span<const uint8_t>> VerifyVbMetaSignature(const ApexFile& apex,
                                                       const uint8_t* data,
                                                       size_t length) {
  const uint8_t* pk;
  size_t pk_len;
  AvbVBMetaVerifyResult res;

  res = avb_vbmeta_image_verify(data, length, &pk, &pk_len);
  switch (res) {
    case AVB_VBMETA_VERIFY_RESULT_OK:
      break;
    case AVB_VBMETA_VERIFY_RESULT_OK_NOT_SIGNED:
    case AVB_VBMETA_VERIFY_RESULT_HASH_MISMATCH:
    case AVB_VBMETA_VERIFY_RESULT_SIGNATURE_MISMATCH:
      return Error() << "Error verifying " << apex.GetPath() << ": "
                     << avb_vbmeta_verify_result_to_string(res);
    case AVB_VBMETA_VERIFY_RESULT_INVALID_VBMETA_HEADER:
      return Error() << "Error verifying " << apex.GetPath() << ": "
                     << "invalid vbmeta header";
    case AVB_VBMETA_VERIFY_RESULT_UNSUPPORTED_VERSION:
      return Error() << "Error verifying " << apex.GetPath() << ": "
                     << "unsupported version";
    default:
      return Error() << "Unknown vmbeta_image_verify return value : " << res;
  }

  return std::span<const uint8_t>(pk, pk_len);
}

Result<std::unique_ptr<uint8_t[]>> VerifyVbMeta(const ApexFile& apex,
                                                const unique_fd& fd,
                                                const AvbFooter& footer,
                                                const std::string& public_key) {
  if (footer.vbmeta_size > kVbMetaMaxSize) {
    return Errorf("VbMeta size in footer exceeds kVbMetaMaxSize.");
  }

  if (!apex.GetImageOffset()) {
    return Error() << "Cannot check VbMeta size without image offset";
  }

  off_t offset = apex.GetImageOffset().value() + footer.vbmeta_offset;
  std::unique_ptr<uint8_t[]> vbmeta_buf(new uint8_t[footer.vbmeta_size]);

  if (!ReadFullyAtOffset(fd, vbmeta_buf.get(), footer.vbmeta_size, offset)) {
    return ErrnoError() << "Couldn't read AVB meta-data";
  }

  Result<std::span<const uint8_t>> st =
      VerifyVbMetaSignature(apex, vbmeta_buf.get(), footer.vbmeta_size);
  if (!st.ok()) {
    return st.error();
  }

  if (!CompareKeys(st->data(), st->size(), public_key)) {
    return Error() << "Error verifying " << apex.GetPath() << " : "
                   << "public key doesn't match the pre-installed one";
  }

  return vbmeta_buf;
}

Result<const AvbHashtreeDescriptor*> FindDescriptor(uint8_t* vbmeta_data,
                                                    size_t vbmeta_size) {
  const AvbDescriptor** descriptors;
  size_t num_descriptors;

  descriptors =
      avb_descriptor_get_all(vbmeta_data, vbmeta_size, &num_descriptors);

  // avb_descriptor_get_all() returns an internally allocated array
  // of pointers and it needs to be avb_free()ed after using it.
  auto guard = android::base::ScopeGuard(std::bind(avb_free, descriptors));

  for (size_t i = 0; i < num_descriptors; i++) {
    AvbDescriptor desc;
    if (!avb_descriptor_validate_and_byteswap(descriptors[i], &desc)) {
      return Errorf("Couldn't validate AvbDescriptor.");
    }

    if (desc.tag != AVB_DESCRIPTOR_TAG_HASHTREE) {
      // Ignore other descriptors
      continue;
    }

    // Check that hashtree descriptor actually fits into memory.
    const uint8_t* vbmeta_end = vbmeta_data + vbmeta_size;
    if ((uint8_t*)descriptors[i] + sizeof(AvbHashtreeDescriptor) > vbmeta_end) {
      return Errorf("Invalid length for AvbHashtreeDescriptor");
    }
    return (const AvbHashtreeDescriptor*)descriptors[i];
  }

  return Errorf("Couldn't find any AVB hashtree descriptors.");
}

Result<std::unique_ptr<AvbHashtreeDescriptor>> VerifyDescriptor(
    const AvbHashtreeDescriptor* desc) {
  auto verified_desc = std::make_unique<AvbHashtreeDescriptor>();

  if (!avb_hashtree_descriptor_validate_and_byteswap(desc,
                                                     verified_desc.get())) {
    return Errorf("Couldn't validate AvbDescriptor.");
  }

  return verified_desc;
}

}  // namespace

Result<ApexVerityData> ApexFile::VerifyApexVerity(
    const std::string& public_key) const {
  if (IsCompressed()) {
    return Error() << "Cannot verify ApexVerity of compressed APEX";
  }

  ApexVerityData verity_data;

  unique_fd fd(open(GetPath().c_str(), O_RDONLY | O_CLOEXEC));
  if (fd.get() == -1) {
    return ErrnoError() << "Failed to open " << GetPath();
  }

  Result<std::unique_ptr<AvbFooter>> footer = GetAvbFooter(*this, fd);
  if (!footer.ok()) {
    return footer.error();
  }

  Result<std::unique_ptr<uint8_t[]>> vbmeta_data =
      VerifyVbMeta(*this, fd, **footer, public_key);
  if (!vbmeta_data.ok()) {
    return vbmeta_data.error();
  }

  Result<const AvbHashtreeDescriptor*> descriptor =
      FindDescriptor(vbmeta_data->get(), (*footer)->vbmeta_size);
  if (!descriptor.ok()) {
    return descriptor.error();
  }

  Result<std::unique_ptr<AvbHashtreeDescriptor>> verified_descriptor =
      VerifyDescriptor(*descriptor);
  if (!verified_descriptor.ok()) {
    return verified_descriptor.error();
  }
  verity_data.desc = std::move(*verified_descriptor);

  // This area is now safe to access, because we just verified it
  const uint8_t* trailing_data =
      (const uint8_t*)*descriptor + sizeof(AvbHashtreeDescriptor);
  verity_data.hash_algorithm =
      reinterpret_cast<const char*>((*descriptor)->hash_algorithm);
  verity_data.salt = GetSalt(*verity_data.desc, trailing_data);
  verity_data.root_digest = GetDigest(*verity_data.desc, trailing_data);

  return verity_data;
}

Result<void> ApexFile::Decompress(const std::string& dest_path) const {
  const std::string& src_path = GetPath();

  LOG(INFO) << "Decompressing" << src_path << " to " << dest_path;

  // We should decompress compressed APEX files only
  if (!IsCompressed()) {
    return ErrnoError() << "Cannot decompress an uncompressed APEX";
  }

  // Get file descriptor of the compressed apex file
  unique_fd src_fd(open(src_path.c_str(), O_RDONLY | O_CLOEXEC));
  if (src_fd.get() == -1) {
    return ErrnoError() << "Failed to open compressed APEX " << GetPath();
  }

  // Open it as a zip file
  ZipArchiveHandle handle;
  int ret = OpenArchiveFd(src_fd.get(), src_path.c_str(), &handle, false);
  if (ret < 0) {
    return Error() << "Failed to open package " << src_path << ": "
                   << ErrorCodeString(ret);
  }
  auto handle_guard =
      android::base::make_scope_guard([&handle] { CloseArchive(handle); });

  // Find the original apex file inside the zip and extract to dest
  ZipEntry entry;
  ret = FindEntry(handle, kCompressedApexFilename, &entry);
  if (ret < 0) {
    return Error() << "Could not find entry \"" << kCompressedApexFilename
                   << "\" in package " << src_path << ": "
                   << ErrorCodeString(ret);
  }

  // Open destination file descriptor
  unique_fd dest_fd(
      open(dest_path.c_str(), O_WRONLY | O_CLOEXEC | O_CREAT | O_EXCL, 0644));
  if (dest_fd.get() == -1) {
    return ErrnoError() << "Failed to open decompression destination "
                        << dest_path.c_str();
  }

  // Prepare a guard that deletes the extracted file if anything goes wrong
  auto decompressed_guard = android::base::make_scope_guard(
      [&dest_path] { RemoveFileIfExists(dest_path); });

  // Extract the original_apex to dest_path
  ret = ExtractEntryToFile(handle, &entry, dest_fd.get());
  if (ret < 0) {
    return Error() << "Could not decompress to file " << dest_path << " "
                   << ErrorCodeString(ret);
  }

  // Verification complete. Accept the decompressed file
  decompressed_guard.Disable();
  LOG(VERBOSE) << "Decompressed " << src_path << " to " << dest_path;

  return {};
}

}  // namespace apex
}  // namespace android
