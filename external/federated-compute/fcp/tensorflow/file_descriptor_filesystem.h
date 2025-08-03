/*
 * Copyright 2023 Google LLC
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
 
#ifndef FCP_TENSORFLOW_FILE_DESCRIPTOR_FILESYSTEM_H_
#define FCP_TENSORFLOW_FILE_DESCRIPTOR_FILESYSTEM_H_

#include <memory>
#include <vector>

#include "tensorflow/core/platform/file_system.h"

namespace tensorflow {
namespace fcp {

// Filesystem for file descriptors that reads URIs in the form "fd:///<int>",
// where <int> is a valid file descriptor number. This filesystem can be useful
// to support situations in which a file descriptor is opened in Java and passed
// to the JNI layer.
//
// CAVEATS:
// * This filesystem is non-hierarchical and read-only; many functions simply
//   return tensorflow::error::code::UNIMPLEMENTED.
// * To read a file descriptor, this filesystem makes a dup and closes the dup
//   when it is done with it. The code that originally created the URI is
//   responsible for closing the original file descriptor.
class FileDescriptorFileSystem : public tensorflow::FileSystem {
 public:
  FileDescriptorFileSystem() = default;
  ~FileDescriptorFileSystem() override = default;

  tensorflow::Status NewRandomAccessFile(
      const std::string& filename,
      std::unique_ptr<RandomAccessFile>* result) override;

  // Clears *results and stores pattern if pattern is a literal match of a valid
  // file descriptor. As such, this does not support the full pattern matching
  // specification as described by FileSystem::GetMatchingPaths.
  tensorflow::Status GetMatchingPaths(
      const std::string& pattern, std::vector<std::string>* results) override;

  tensorflow::Status Stat(const std::string& fname,
                          tensorflow::FileStatistics* stats) override;

  tensorflow::Status GetFileSize(const std::string& fname,
                                 uint64* size) override;

  // Not necessary to read TF checkpoints; these always return UNIMPLEMENTED
  tensorflow::Status NewReadOnlyMemoryRegionFromFile(
      const std::string& filename,
      std::unique_ptr<ReadOnlyMemoryRegion>* result) override;

  tensorflow::Status FileExists(const std::string& fname) override;

  // The fd filesystem is non-hierarchical; this always returns UNIMPLEMENTED
  tensorflow::Status GetChildren(const std::string& dir,
                                 std::vector<string>* r) override;

  // The fd filesystem is read-only; these always return UNIMPLEMENTED
  tensorflow::Status NewWritableFile(
      const std::string& fname, std::unique_ptr<WritableFile>* result) override;
  tensorflow::Status NewAppendableFile(
      const std::string& fname, std::unique_ptr<WritableFile>* result) override;
  tensorflow::Status DeleteFile(const std::string& f) override;
  tensorflow::Status CreateDir(const std::string& d) override;
  tensorflow::Status DeleteDir(const std::string& d) override;
  tensorflow::Status RenameFile(const std::string& s,
                                const std::string& t) override;
  tensorflow::Status CanCreateTempFile(const std::string& fname,
                                       bool* can_create_temp_file) override;
};

}  // namespace fcp
}  // namespace tensorflow

#endif  // FCP_TENSORFLOW_FILE_DESCRIPTOR_FILESYSTEM_H_
