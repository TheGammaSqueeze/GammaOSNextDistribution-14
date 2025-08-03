//
// Copyright (C) 2018 The Android Open Source Project
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

#include "update_engine/payload_generator/boot_img_filesystem.h"

#include <array>

#include <android-base/unique_fd.h>
#include <android-base/file.h>
#include <base/logging.h>
#include <bootimg.h>
#include <brillo/secure_blob.h>
#include <puffin/utils.h>
#include <sys/stat.h>

#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"

using std::string;
using std::unique_ptr;
using std::vector;

namespace chromeos_update_engine {

unique_ptr<BootImgFilesystem> BootImgFilesystem::CreateFromFile(
    const string& filename) {
  if (filename.empty()) {
    return nullptr;
  }
  android::base::unique_fd fd(open(filename.c_str(), O_RDONLY));
  if (!fd.ok()) {
    PLOG(ERROR) << "Failed to open " << filename;
    return nullptr;
  }
  struct stat st {};
  if (fstat(fd.get(), &st) < 0) {
    return nullptr;
  }
  if (static_cast<size_t>(st.st_size) < sizeof(boot_img_hdr_v0)) {
    LOG(INFO) << "Image " << filename
              << " is too small to be a boot image. file size: " << st.st_size;
    return nullptr;
  }
  std::array<char, BOOT_MAGIC_SIZE> header_magic{};
  if (!android::base::ReadFullyAtOffset(
          fd, header_magic.data(), BOOT_MAGIC_SIZE, 0) ||
      memcmp(header_magic.data(), BOOT_MAGIC, BOOT_MAGIC_SIZE) != 0) {
    return nullptr;
  }

  // The order of image header fields are different in version 3 from the
  // previous versions. But the position of "header_version" is fixed at #9
  // across all image headers.
  // See details in system/tools/mkbootimg/include/bootimg/bootimg.h
  constexpr size_t header_version_offset =
      BOOT_MAGIC_SIZE + 8 * sizeof(uint32_t);
  std::array<char, sizeof(uint32_t)> header_version_blob{};
  if (!android::base::ReadFullyAtOffset(fd,
                                        header_version_blob.data(),
                                        sizeof(uint32_t),
                                        header_version_offset)) {
    return nullptr;
  }
  uint32_t header_version =
      *reinterpret_cast<uint32_t*>(header_version_blob.data());
  if (header_version > 4) {
    LOG(WARNING) << "Boot image header version " << header_version
                 << " isn't supported for parsing";
    return nullptr;
  }

  // Read the bytes of boot image header based on the header version.
  size_t header_size =
      header_version == 3 ? sizeof(boot_img_hdr_v3) : sizeof(boot_img_hdr_v0);
  brillo::Blob header_blob(header_size);
  if (!ReadFullyAtOffset(fd, header_blob.data(), header_size, 0)) {
    return nullptr;
  }

  unique_ptr<BootImgFilesystem> result(new BootImgFilesystem());
  result->filename_ = filename;
  if (header_version < 3) {
    auto hdr_v0 = reinterpret_cast<boot_img_hdr_v0*>(header_blob.data());
    CHECK_EQ(0, memcmp(hdr_v0->magic, BOOT_MAGIC, BOOT_MAGIC_SIZE));
    CHECK_LT(hdr_v0->header_version, 3u);
    result->kernel_size_ = hdr_v0->kernel_size;
    result->ramdisk_size_ = hdr_v0->ramdisk_size;
    result->page_size_ = hdr_v0->page_size;
  } else if (header_version == 3) {
    auto hdr_v3 = reinterpret_cast<boot_img_hdr_v3*>(header_blob.data());
    CHECK_EQ(0, memcmp(hdr_v3->magic, BOOT_MAGIC, BOOT_MAGIC_SIZE));
    CHECK_EQ(3u, hdr_v3->header_version);
    result->kernel_size_ = hdr_v3->kernel_size;
    result->ramdisk_size_ = hdr_v3->ramdisk_size;
    result->page_size_ = 4096;
  } else {
    auto hdr_v4 = reinterpret_cast<boot_img_hdr_v4*>(header_blob.data());
    CHECK_EQ(0, memcmp(hdr_v4->magic, BOOT_MAGIC, BOOT_MAGIC_SIZE));
    CHECK_EQ(hdr_v4->header_version, 4u);
    result->kernel_size_ = hdr_v4->kernel_size;
    result->ramdisk_size_ = hdr_v4->ramdisk_size;
    // In boot image v3 and v4, page size is hard coded as 4096
    result->page_size_ = 4096;
    result->signature_size_ = hdr_v4->signature_size;
  }

  CHECK_GT(result->page_size_, 0u);

  return result;
}

size_t BootImgFilesystem::GetBlockSize() const {
  // Page size may not be 4K, but we currently only support 4K block size.
  return kBlockSize;
}

size_t BootImgFilesystem::GetBlockCount() const {
  return utils::DivRoundUp(utils::FileSize(filename_), kBlockSize);
}

FilesystemInterface::File BootImgFilesystem::GetFile(const string& name,
                                                     uint64_t offset,
                                                     uint64_t size) const {
  File file;
  file.name = name;
  file.extents = {ExtentForBytes(kBlockSize, offset, size)};

  brillo::Blob data;
  if (utils::ReadFileChunk(filename_, offset, size, &data) &&
      data.size() == size) {
    constexpr size_t kGZipHeaderSize = 10;
    // Check GZip header magic.
    if (data.size() > kGZipHeaderSize && data[0] == 0x1F && data[1] == 0x8B) {
      if (!puffin::LocateDeflatesInGzip(data, &file.deflates)) {
        LOG(ERROR) << "Error occurred parsing gzip " << name << " at offset "
                   << offset << " of " << filename_ << ", found "
                   << file.deflates.size() << " deflates.";
        return file;
      }
      for (auto& deflate : file.deflates) {
        deflate.offset += offset * 8;
      }
    }
  }
  return file;
}

bool BootImgFilesystem::GetFiles(vector<File>* files) const {
  files->clear();
  const uint64_t file_size = utils::FileSize(filename_);
  // The first page is header.
  uint64_t offset = page_size_;
  if (kernel_size_ > 0 && offset + kernel_size_ <= file_size) {
    files->emplace_back(GetFile("<kernel>", offset, kernel_size_));
  }
  offset += utils::RoundUp(kernel_size_, page_size_);
  if (ramdisk_size_ > 0 && offset + ramdisk_size_ <= file_size) {
    files->emplace_back(GetFile("<ramdisk>", offset, ramdisk_size_));
  }
  offset += utils::RoundUp(ramdisk_size_, page_size_);
  if (signature_size_ > 0) {
    files->emplace_back(GetFile("<boot signature>", offset, signature_size_));
  }
  return true;
}

}  // namespace chromeos_update_engine
