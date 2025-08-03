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
#include "ramdisk_utils.h"

#include <android-base/file.h>
#include <android-base/result.h>
#include <bootimg.h>
#include <iostream>

#include "cpio.h"
#include "lz4_legacy.h"

using android::base::ErrnoError;
using android::base::Error;
using android::base::ReadFullyAtOffset;
using android::base::WriteStringToFd;

namespace android {

namespace {
// Extract ramdisk from boot image / partition at |boot_path|.
// Return a temporary file storing the ramdisk section.
android::base::Result<std::unique_ptr<TemporaryFile>> ExtractRamdiskRaw(
    std::string_view boot_path) {
  android::base::unique_fd bootimg(
      TEMP_FAILURE_RETRY(open(boot_path.data(), O_RDONLY)));
  if (!bootimg.ok()) return ErrnoError() << "open(" << boot_path << ")";
  boot_img_hdr_v3 hdr{};
  if (!ReadFullyAtOffset(bootimg.get(), &hdr, sizeof(hdr), 0))
    return ErrnoError() << "read header";
  if (0 != memcmp(hdr.magic, BOOT_MAGIC, BOOT_MAGIC_SIZE))
    return Error() << "Boot magic mismatch";

  if (hdr.header_version < 3)
    return Error() << "Unsupported header version V" << hdr.header_version;
  if (hdr.ramdisk_size <= 0) {
    return Error() << boot_path
                   << " contains a valid bootimg header but no ramdisk";
  }

  // See bootimg.h
  auto kernel_size_bytes = (hdr.kernel_size + 4096 - 1) / 4096 * 4096;
  auto ramdisk_offset = 4096 + kernel_size_bytes;

  std::string ramdisk_content(hdr.ramdisk_size, '\0');
  if (!ReadFullyAtOffset(bootimg.get(), ramdisk_content.data(),
                         hdr.ramdisk_size, ramdisk_offset))
    return ErrnoError() << "read ramdisk section";

  auto ramdisk_content_file = std::make_unique<TemporaryFile>();
  if (!WriteStringToFd(ramdisk_content, ramdisk_content_file->fd))
    return ErrnoError() << "write ramdisk section to file";
  fsync(ramdisk_content_file->fd);

  return ramdisk_content_file;
}

}  // namespace

// From the boot image / partition, extract the ramdisk section, decompress it,
// and extract from the cpio archive.
android::base::Result<std::unique_ptr<TemporaryDir>> ExtractRamdiskToDirectory(
    std::string_view boot_path) {
  auto raw_ramdisk_file = ExtractRamdiskRaw(boot_path);
  if (!raw_ramdisk_file.ok()) return raw_ramdisk_file.error();

  TemporaryFile decompressed;
  auto decompress_res = android::Lz4DecompressLegacy((*raw_ramdisk_file)->path,
                                                     decompressed.path);
  if (!decompress_res.ok()) return decompress_res.error();

  return android::CpioExtract(decompressed.path);
}

}  // namespace android
