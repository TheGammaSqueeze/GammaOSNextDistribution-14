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

#ifndef UPDATE_ENGINE_PAYLOAD_GENERATOR_EROFS_FILESYSTEM_H_
#define UPDATE_ENGINE_PAYLOAD_GENERATOR_EROFS_FILESYSTEM_H_

#include "update_engine/payload_generator/filesystem_interface.h"
#include "update_engine/payload_generator/delta_diff_generator.h"

struct erofs_sb_info;

namespace chromeos_update_engine {

class ErofsFilesystem final : public FilesystemInterface {
 public:
  // Creates an ErofsFilesystem from a erofs formatted filesystem stored in a
  // file. The file doesn't need to be loop-back mounted. Since erofs-utils
  // library functions are not concurrency safe(can't be used in multi-threaded
  // context, can't even work with multiple EROFS images concurrently on 1
  // thread), this function takes a global mutex.
  static std::unique_ptr<ErofsFilesystem> CreateFromFile(
      const std::string& filename,
      const CompressionAlgorithm& algo =
          PartitionConfig::GetDefaultCompressionParam());
  virtual ~ErofsFilesystem() = default;

  // FilesystemInterface overrides.
  size_t GetBlockSize() const override { return kBlockSize; }
  size_t GetBlockCount() const override { return fs_size_ / kBlockSize; }

  // GetFiles will return one FilesystemInterface::File for every file and every
  // directory in the filesystem. Hard-linked files will appear in the list
  // several times with the same list of blocks.
  // On addition to actual files, it also returns these pseudo-files:
  //  <free-space>: With all the unallocated data-blocks.
  //  <inode-blocks>: Will all the data-blocks for second and third level inodes
  //    of all the files.
  //  <group-descriptors>: With the block group descriptor and their reserved
  //    space.
  //  <metadata>: With the rest of ext2 metadata blocks, such as superblocks
  //    and bitmap tables.
  static bool GetFiles(struct erofs_sb_info* sbi,
                       const std::string& filename,
                       std::vector<File>* files,
                       const CompressionAlgorithm& algo);

  bool GetFiles(std::vector<File>* files) const override;

 private:
  ErofsFilesystem(std::string filename, size_t fs_size, std::vector<File> files)
      : filename_(filename), fs_size_(fs_size), files_(std::move(files)) {}

  // The file where the filesystem is stored.
  const std::string filename_;
  const size_t fs_size_;
  const std::vector<File> files_;
};  // namespace chromeos_update_engine

}  // namespace chromeos_update_engine

#endif