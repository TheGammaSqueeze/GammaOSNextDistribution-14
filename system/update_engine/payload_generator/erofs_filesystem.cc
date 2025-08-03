//
// Copyright (C) 2021 The Android Open Source Project
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

#include "update_engine/payload_generator/erofs_filesystem.h"

#include <endian.h>
#include <fcntl.h>
#include <time.h>

#include <array>
#include <string>
#include <mutex>

#include <android-base/unique_fd.h>
#include <erofs/dir.h>
#include <erofs/io.h>
#include <erofs_fs.h>
#include <erofs/internal.h>

#include "erofs_iterate.h"
#include "lz4diff/lz4diff.pb.h"
#include "lz4diff/lz4patch.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_generator/filesystem_interface.h"

namespace chromeos_update_engine {

namespace {

static constexpr int GetOccupiedSize(const struct erofs_inode* inode,
                                     size_t block_size,
                                     erofs_off_t* size) {
  *size = 0;
  switch (inode->datalayout) {
    case EROFS_INODE_FLAT_INLINE:
    case EROFS_INODE_FLAT_PLAIN:
    case EROFS_INODE_CHUNK_BASED:
      *size = inode->i_size;
      break;
    case EROFS_INODE_COMPRESSED_FULL:
    case EROFS_INODE_COMPRESSED_COMPACT:
      *size = inode->u.i_blocks * block_size;
      break;
    default:
      LOG(ERROR) << "unknown datalayout " << inode->datalayout;
      return -1;
  }
  return 0;
}

static int ErofsMapBlocks(struct erofs_inode* inode,
                          struct erofs_map_blocks* map,
                          int flags) {
  if (erofs_inode_is_data_compressed(inode->datalayout)) {
    return z_erofs_map_blocks_iter(inode, map, flags);
  }
  return erofs_map_blocks(inode, map, flags);
}

static constexpr bool IsBlockCompressed(const struct erofs_map_blocks& block) {
  // Z_EROFS_COMPRESSION_SHIFTED means data inside this block are merely
  // memmove()'ed in place, instead of going through some compression function
  // like LZ4 or LZMA
  return block.m_flags & EROFS_MAP_ENCODED &&
         block.m_algorithmformat != Z_EROFS_COMPRESSION_SHIFTED;
}

static void FillExtentInfo(FilesystemInterface::File* p_file,
                           std::string_view image_filename,
                           struct erofs_inode* inode,
                           size_t* const unaligned_bytes) {
  auto& file = *p_file;

  struct erofs_map_blocks block {};
  block.m_la = 0;
  block.index = UINT_MAX;

  auto& compressed_blocks = file.compressed_file_info.blocks;
  auto last_pa = block.m_pa;
  auto last_plen = 0;
  while (block.m_la < inode->i_size) {
    auto error = ErofsMapBlocks(inode, &block, EROFS_GET_BLOCKS_FIEMAP);
    DEFER {
      block.m_la += block.m_llen;
    };
    if (error) {
      LOG(FATAL) << "Failed to map blocks for " << file.name << " in "
                 << image_filename;
    }
    if (block.m_pa % kBlockSize != 0) {
      // EROFS might put the last block on unalighed addresses, because the last
      // block is often < 1 full block size. That is fine, we can usually
      // tolerate small amount of data being unaligned.
      if (block.m_llen >= kBlockSize ||
          block.m_la + block.m_llen != inode->i_size) {
        LOG(ERROR) << "File `" << file.name
                   << "` has unaligned blocks: at physical byte offset: "
                   << block.m_pa << ", "
                   << " length: " << block.m_plen
                   << ", logical offset: " << block.m_la << ", remaining data: "
                   << inode->i_size - (block.m_la + block.m_llen);
      }
      (*unaligned_bytes) += block.m_plen;
    }
    // Certain uncompressed blocks have physical size > logical size. Usually
    // the physical block contains bunch of trailing zeros. Include thees
    // bytes in the logical size as well.
    if (!IsBlockCompressed(block)) {
      CHECK_LE(block.m_llen, block.m_plen);
      block.m_llen = block.m_plen;
    }

    if (last_pa + last_plen != block.m_pa) {
      if (last_plen != 0) {
        file.extents.push_back(ExtentForRange(
            last_pa / kBlockSize, utils::DivRoundUp(last_plen, kBlockSize)));
      }
      last_pa = block.m_pa;
      last_plen = block.m_plen;
    } else {
      last_plen += block.m_plen;
    }
    if (file.is_compressed) {
      // If logical size and physical size are the same, this block is
      // uncompressed. Join consecutive uncompressed blocks to save a bit memory
      // storing metadata.
      if (block.m_llen == block.m_plen && !compressed_blocks.empty() &&
          !compressed_blocks.back().IsCompressed()) {
        compressed_blocks.back().compressed_length += block.m_llen;
        compressed_blocks.back().uncompressed_length += block.m_llen;
      } else {
        compressed_blocks.push_back(
            CompressedBlock(block.m_la, block.m_plen, block.m_llen));
      }
    }
  }
  if (last_plen != 0) {
    file.extents.push_back(ExtentForRange(
        last_pa / kBlockSize, utils::DivRoundUp(last_plen, kBlockSize)));
  }
  return;
}

bool IsErofsImage(const char* path) {
  android::base::unique_fd fd(open(path, O_RDONLY));
  uint32_t buf{};
  if (pread(fd.get(), &buf, 4, EROFS_SUPER_OFFSET) < 0) {
    return false;
  }
  return le32toh(buf) == EROFS_SUPER_MAGIC_V1;
}

}  // namespace

std::unique_ptr<ErofsFilesystem> ErofsFilesystem::CreateFromFile(
    const std::string& filename, const CompressionAlgorithm& algo) {
  if (!IsErofsImage(filename.c_str())) {
    return {};
  }
  struct erofs_sb_info sbi {};

  if (const auto err = dev_open_ro(&sbi, filename.c_str()); err) {
    PLOG(INFO) << "Failed to open " << filename;
    return nullptr;
  }
  DEFER {
    dev_close(&sbi);
  };

  if (const auto err = erofs_read_superblock(&sbi); err) {
    PLOG(INFO) << "Failed to parse " << filename << " as EROFS image";
    return nullptr;
  }
  const auto block_size = 1UL << sbi.blkszbits;
  struct stat st {};
  if (const auto err = fstat(sbi.devfd, &st); err) {
    PLOG(ERROR) << "Failed to stat() " << filename;
    return nullptr;
  }
  const time_t time = sbi.build_time;
  std::vector<File> files;
  CHECK(ErofsFilesystem::GetFiles(&sbi, filename, &files, algo))
      << "Failed to parse EROFS image " << filename;

  LOG(INFO) << "Parsed EROFS image of size " << st.st_size << " built in "
            << ctime(&time) << " " << filename
            << ", number of files: " << files.size()
            << ", block size: " << block_size;
  LOG(INFO) << "Using compression algo " << algo << " for " << filename;
  // private ctor doesn't work with make_unique
  return std::unique_ptr<ErofsFilesystem>(
      new ErofsFilesystem(filename, st.st_size, std::move(files)));
}

bool ErofsFilesystem::GetFiles(std::vector<File>* files) const {
  *files = files_;
  return true;
}

bool ErofsFilesystem::GetFiles(struct erofs_sb_info* sbi,
                               const std::string& filename,
                               std::vector<File>* files,
                               const CompressionAlgorithm& algo) {
  size_t unaligned_bytes = 0;
  const auto block_size = 1UL << sbi->blkszbits;
  const auto err = erofs_iterate_root_dir(
      sbi, [&](struct erofs_iterate_dir_context* p_info) {
        const auto& info = *p_info;
        if (info.ctx.de_ftype != EROFS_FT_REG_FILE) {
          return 0;
        }
        struct erofs_inode inode {};
        inode.nid = info.ctx.de_nid;
        inode.sbi = sbi;
        int err = erofs_read_inode_from_disk(&inode);
        if (err) {
          LOG(ERROR) << "Failed to read inode " << inode.nid;
          return err;
        }
        const auto uncompressed_size = inode.i_size;
        erofs_off_t compressed_size = 0;
        if (uncompressed_size == 0) {
          return 0;
        }
        err = GetOccupiedSize(&inode, block_size, &compressed_size);
        if (err) {
          LOG(FATAL) << "Failed to get occupied size for " << filename;
          return err;
        }
        // For EROFS_INODE_FLAT_INLINE , most blocks are stored on aligned
        // addresses. Except the last block, which is stored right after the
        // inode. These nodes will have a slight amount of data unaligned, which
        // is fine.

        File file;
        file.name = info.path;
        file.compressed_file_info.zero_padding_enabled =
            erofs_sb_has_lz4_0padding(sbi);
        file.is_compressed = compressed_size != uncompressed_size;

        file.file_stat.st_size = uncompressed_size;
        file.file_stat.st_ino = inode.nid;
        FillExtentInfo(&file, filename, &inode, &unaligned_bytes);
        file.compressed_file_info.algo = algo;

        files->emplace_back(std::move(file));
        return 0;
      });
  if (err) {
    LOG(ERROR) << "EROFS files iteration filed " << strerror(-err);
    return false;
  }

  for (auto& file : *files) {
    NormalizeExtents(&file.extents);
  }
  LOG(INFO) << "EROFS image " << filename << " has " << unaligned_bytes
            << " unaligned bytes, which is "
            << static_cast<float>(unaligned_bytes) / utils::FileSize(filename) *
                   100.0f
            << "% of partition data";
  return true;
}

}  // namespace chromeos_update_engine