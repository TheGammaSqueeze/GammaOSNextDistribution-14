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

#include "lz4diff.h"
#include "lz4diff/lz4patch.h"
#include "lz4diff_compress.h"
#include "update_engine/payload_generator/filesystem_interface.h"
#include "update_engine/payload_generator/erofs_filesystem.h"
#include "update_engine/common/utils.h"

using namespace chromeos_update_engine;

template <typename T>
std::ostream& operator<<(std::ostream& out, const std::vector<T>& vec) {
  if (vec.begin() == vec.end()) {
    out << "{}";
    return out;
  }
  out << "{";
  auto begin = vec.begin();
  out << *begin;
  for (const auto& ext : Range{++begin, vec.end()}) {
    out << ", " << ext;
  }
  out << "}";
  return out;
}

enum Lz4DiffOp { DIFF, PATCH, TEST };

int ExecuteLz4diff(const char* src_image_path,
                   const char* dst_image_path,
                   const FilesystemInterface::File& src_file,
                   const FilesystemInterface::File& dst_file,
                   const char* patch_file,
                   Lz4DiffOp op) {
  brillo::Blob src_blob;
  CHECK(utils::ReadExtents(
      src_image_path, src_file.extents, &src_blob, kBlockSize));
  brillo::Blob dst_blob;
  CHECK(utils::ReadExtents(
      dst_image_path, dst_file.extents, &dst_blob, kBlockSize));

  brillo::Blob lz4diff_patch;
  if (op == DIFF || op == TEST) {
    Lz4Diff(src_blob,
            dst_blob,
            src_file.compressed_file_info,
            dst_file.compressed_file_info,
            &lz4diff_patch);
    if (patch_file) {
      CHECK(utils::WriteFile(
          patch_file, lz4diff_patch.data(), lz4diff_patch.size()));
    }
  }
  if (op == PATCH || op == TEST) {
    utils::ReadFile(patch_file, &lz4diff_patch);
    Blob actual_target;
    CHECK(Lz4Patch(
        ToStringView(src_blob), ToStringView(lz4diff_patch), &actual_target));
    if (actual_target != dst_blob) {
      LOG(ERROR) << "Final postfixed blob mismatch. " << src_file.name;
    } else {
      LOG(INFO) << "LZ4patch success. Final blob matches. " << src_file.name;
    }
  }
  return 0;
}

int ExecuteLz4diffOp(const char* src_image_path,
                     const char* dst_image_path,
                     const char* inode_path,
                     const char* patch_file,
                     Lz4DiffOp op) {
  auto src_fs = ErofsFilesystem::CreateFromFile(src_image_path);
  CHECK_NE(src_fs, nullptr);
  auto dst_fs = ErofsFilesystem::CreateFromFile(dst_image_path);
  CHECK_NE(dst_fs, nullptr);
  std::vector<FilesystemInterface::File> src_files;
  CHECK(src_fs->GetFiles(&src_files));
  std::vector<FilesystemInterface::File> dst_files;
  CHECK(dst_fs->GetFiles(&dst_files));
  ScopedTempFile temp_patch;
  if (patch_file == nullptr && op == TEST) {
    patch_file = temp_patch.path().c_str();
  }
  if (inode_path == nullptr) {
    for (const auto& src_file : src_files) {
      auto dst_file = std::find_if(
          dst_files.begin(),
          dst_files.end(),
          [path(src_file.name)](auto&& file) { return file.name == path; });
      int err = ExecuteLz4diff(
          src_image_path, dst_image_path, src_file, *dst_file, patch_file, op);
      if (err) {
        return err;
      }
    }
    return 0;
  }
  auto src_file = std::find_if(
      src_files.begin(), src_files.end(), [inode_path](auto&& file) {
        return file.name == inode_path;
      });
  if (src_file == src_files.end()) {
    LOG(ERROR) << "Failed to find " << inode_path << " in EROFS image"
               << src_image_path;
    return 2;
  }
  auto dst_file = std::find_if(
      dst_files.begin(), dst_files.end(), [inode_path](auto&& file) {
        return file.name == inode_path;
      });
  if (dst_file == dst_files.end()) {
    LOG(ERROR) << "Failed to find " << inode_path << " in EROFS image"
               << dst_image_path;
    return 3;
  }
  return ExecuteLz4diff(
      src_image_path, dst_image_path, *src_file, *dst_file, patch_file, op);
}

int main(int argc, const char** argv) {
  if (argc < 4) {
    printf(
        "Usage: %s <diff/patch/test> <src EROFS image> <dst EROFS image> "
        "...args\n",
        argv[0]);
    return 2;
  }
  const char* src_image_path = argv[2];
  const char* dst_image_path = argv[3];
  auto src_fs = ErofsFilesystem::CreateFromFile(src_image_path);
  CHECK_NE(src_fs, nullptr);
  auto dst_fs = ErofsFilesystem::CreateFromFile(dst_image_path);
  CHECK_NE(dst_fs, nullptr);
  std::vector<FilesystemInterface::File> src_files;
  CHECK(src_fs->GetFiles(&src_files));
  std::vector<FilesystemInterface::File> dst_files;
  CHECK(dst_fs->GetFiles(&dst_files));
  std::string_view op = argv[1];
  if (op == "diff") {
    if (argc != 6 && argc != 5) {
      printf(
          "Usage: %s diff <path to src erofs image> <path to dst erofs imaeg> "
          "<path "
          "of file inside erofs image> [output path]\n",
          argv[0]);
      return 1;
    }
    const char* path = argv[4];
    const char* patch_file = argc == 6 ? argv[5] : nullptr;
    return ExecuteLz4diffOp(
        src_image_path, dst_image_path, path, patch_file, DIFF);
  } else if (op == "patch") {
    if (argc != 6 && argc != 7) {
      printf(
          "Usage: %s patch <path to src erofs image> <path to dst erofs imaeg> "
          "<path "
          "of file inside erofs image> <patch file>\n",
          argv[0]);
      return 3;
    }
    const char* inode_path = argv[4];
    const char* patch_file = argv[5];
    return ExecuteLz4diffOp(
        src_image_path, dst_image_path, inode_path, patch_file, PATCH);
  } else if (op == "test") {
    if (argc != 4) {
      printf(
          "Usage: %s test <path to src erofs image> <path to dst erofs imaeg> "
          "\n",
          argv[0]);
      return 4;
    }
    return ExecuteLz4diffOp(
        src_image_path, dst_image_path, nullptr, nullptr, TEST);
  } else {
    LOG(ERROR) << "Unrecognized op " << op;
    return 4;
  }

  return 0;
}