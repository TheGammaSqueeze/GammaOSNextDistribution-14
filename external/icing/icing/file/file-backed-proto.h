// Copyright (C) 2019 Google LLC
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

// A simple file-backed proto with an in-memory cache.
// WARNING: Only use this for small protos. Files storing larger protos can
// benefit from more sophisticated strategies like chunked reads/writes,
// using mmap and ideally, not even using protos.
//
// TODO(b/133793579) Consider exposing a checksum mismatch to callers.

#ifndef ICING_FILE_FILE_BACKED_PROTO_H_
#define ICING_FILE_FILE_BACKED_PROTO_H_

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/mutex.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/thread_annotations.h"
#include "icing/file/filesystem.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// This class is go/thread-compatible
template <typename ProtoT>
class FileBackedProto {
 public:
  // Header stored at the beginning of the file before the proto.
  struct Header {
    static constexpr int32_t kMagic = 0x726f746f;

    // Holds the magic as a quick sanity check against file corruption.
    int32_t magic;

    // Checksum of the serialized proto, for a more thorough check against file
    // corruption.
    uint32_t proto_checksum;
  };

  // Used the specified file to read older version of the proto and store
  // newer versions of the proto.
  //
  // file_path : Must be a path within in a directory that already exists.
  FileBackedProto(const Filesystem& filesystem, std::string_view file_path);

  // Reset the internal file_path for the file backed proto.
  // Example use:
  //   auto file_backed_proto1 = *FileBackedProto<Proto>::Create(...);
  //   auto file_backed_google::protobuf = *FileBackedProto<Proto>::Create(...);
  //   filesystem.SwapFiles(file1, file2);
  //   file_backed_proto1.SetSwappedFilepath(file2);
  //   file_backed_google::protobuf.SetSwappedFilepath(file1);
  void SetSwappedFilepath(std::string_view swapped_to_file_path) {
    file_path_ = swapped_to_file_path;
  }

  // Computes the checksum of the proto stored in this file and returns it.
  // RETURNS:
  //   - the checksum of the proto or 0 if the file is empty/non-existent
  //   - INTERNAL_ERROR if an IO error or a corruption was encountered.
  libtextclassifier3::StatusOr<Crc32> ComputeChecksum() const
      ICING_LOCKS_EXCLUDED(mutex_);

  // Returns a reference to the proto read from the file. It
  // internally caches the read proto so that future calls are fast.
  //
  // NOTE: The caller does NOT get ownership of the object returned and
  // the returned object is only valid till a new version of the proto is
  // written to the file.
  //
  // Returns NOT_FOUND if the file was empty or never written to.
  // Returns INTERNAL_ERROR if an IO error or a corruption was encountered.
  libtextclassifier3::StatusOr<const ProtoT*> Read() const
      ICING_LOCKS_EXCLUDED(mutex_);

  // Writes the new version of the proto provided through to disk.
  // Successful Write() invalidates any previously read version of the proto.
  //
  // Returns INTERNAL_ERROR if any IO error is encountered and will NOT
  // invalidate any previously read versions of the proto.
  //
  // TODO(cassiewang) The implementation today loses old data if Write() fails.
  // We should write to a tmp file first and rename the file to fix this.
  // TODO(cassiewang) Change to Write(ProtoT&& proto)
  libtextclassifier3::Status Write(std::unique_ptr<ProtoT> proto)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Disallow copy and assign.
  FileBackedProto(const FileBackedProto&) = delete;
  FileBackedProto& operator=(const FileBackedProto&) = delete;

 private:
  // Internal method to handle reading the proto from disk.
  // Requires the caller to hold an exclusive lock on mutex_.
  libtextclassifier3::StatusOr<const ProtoT*> ReadInternal() const
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Upper bound of file-size that is supported.
  static constexpr int32_t kMaxFileSize = 1 * 1024 * 1024;  // 1 MiB.

  // Used to provide reader and writer locks
  mutable absl_ports::shared_mutex mutex_;

  const Filesystem* const filesystem_;
  std::string file_path_;

  mutable std::unique_ptr<ProtoT> cached_proto_ ICING_GUARDED_BY(mutex_);

  mutable std::unique_ptr<Header> cached_header_ ICING_GUARDED_BY(mutex_);
};

template <typename ProtoT>
constexpr int32_t FileBackedProto<ProtoT>::kMaxFileSize;

template <typename ProtoT>
FileBackedProto<ProtoT>::FileBackedProto(const Filesystem& filesystem,
                                         const std::string_view file_path)
    : filesystem_(&filesystem), file_path_(file_path) {}

template <typename ProtoT>
libtextclassifier3::StatusOr<Crc32> FileBackedProto<ProtoT>::ComputeChecksum()
    const {
  absl_ports::unique_lock l(&mutex_);
  if (cached_proto_ == nullptr) {
    auto read_status = ReadInternal();
    if (!read_status.ok()) {
      if (absl_ports::IsNotFound(read_status.status())) {
        // File doesn't exist. So simply return 0.
        return Crc32();
      }
      return read_status.status();
    }
  }
  return Crc32(cached_header_->proto_checksum);
}

template <typename ProtoT>
libtextclassifier3::StatusOr<const ProtoT*> FileBackedProto<ProtoT>::Read()
    const {
  ICING_VLOG(1) << "Reading proto from file: " << file_path_;

  absl_ports::unique_lock l(&mutex_);

  return ReadInternal();
}

template <typename ProtoT>
libtextclassifier3::StatusOr<const ProtoT*>
FileBackedProto<ProtoT>::ReadInternal() const {
  // Return cached proto if we've already read from disk.
  if (cached_proto_ != nullptr) {
    ICING_VLOG(1) << "Reusing cached proto for file: " << file_path_;
    return cached_proto_.get();
  }

  int64_t file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (file_size == Filesystem::kBadFileSize || file_size == 0) {
    return absl_ports::NotFoundError(
        absl_ports::StrCat("Missing file: ", file_path_));
  }

  if (file_size > kMaxFileSize) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "File larger than expected, couldn't read: ", file_path_));
  }

  ScopedFd fd(filesystem_->OpenForRead(file_path_.c_str()));
  if (!fd.is_valid()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Unable to open file for read: ", file_path_));
  }

  ICING_VLOG(1) << "Loading proto from  file: " << file_path_
                << " of size: " << file_size;

  Header header;
  if (!filesystem_->PRead(fd.get(), &header, sizeof(Header), /*offset=*/0)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Unable to read header of: ", file_path_));
  }

  if (header.magic != Header::kMagic) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Invalid header kMagic for: ", file_path_));
  }

  int proto_size = file_size - sizeof(Header);
  auto buffer = std::make_unique<uint8_t[]>(proto_size);
  if (!filesystem_->PRead(fd.get(), buffer.get(), proto_size,
                          /*offset=*/sizeof(Header))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("File read failed: ", file_path_));
  }

  std::string_view buffer_str(reinterpret_cast<const char*>(buffer.get()),
                              proto_size);
  Crc32 crc;
  crc.Append(buffer_str);
  if (header.proto_checksum != crc.Get()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Checksum of file does not match: ", file_path_));
  }

  auto proto = std::make_unique<ProtoT>();
  if (!proto->ParseFromArray(buffer.get(), proto_size)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Proto parse failed. File corrupted: ", file_path_));
  }

  ICING_VLOG(1) << "Successfully read proto from file: " << file_path_;
  cached_proto_ = std::move(proto);
  cached_header_ = std::make_unique<Header>(std::move(header));
  return cached_proto_.get();
}

template <typename ProtoT>
libtextclassifier3::Status FileBackedProto<ProtoT>::Write(
    std::unique_ptr<ProtoT> new_proto) {
  ICING_VLOG(1) << "Writing proto to file: " << file_path_;

  absl_ports::unique_lock l(&mutex_);

  const std::string new_proto_str = new_proto->SerializeAsString();
  if (new_proto_str.size() >= kMaxFileSize) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "New proto too large. size: %d; limit: %d.",
        static_cast<int>(new_proto_str.size()), kMaxFileSize));
  }

  if (cached_proto_ != nullptr &&
      cached_proto_->SerializeAsString() == new_proto_str) {
    ICING_VLOG(1) << "Skip writing proto to file as contents are identical: "
                  << file_path_;
    return libtextclassifier3::Status::OK;
  }

  ScopedFd fd(filesystem_->OpenForWrite(file_path_.c_str()));
  if (!fd.is_valid()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Unable to open file for write: ", file_path_));
  }

  if (!filesystem_->Truncate(fd.get(), 0)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to truncate file: ", file_path_));
  }

  Header header;
  header.magic = Header::kMagic;

  Crc32 crc;
  crc.Append(new_proto_str);
  header.proto_checksum = crc.Get();
  if (!filesystem_->Write(fd.get(), &header, sizeof(Header))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to write header to file: ", file_path_));
  }

  if (!filesystem_->Write(fd.get(), new_proto_str.data(),
                          new_proto_str.size())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to write proto to file: ", file_path_));
  }

  if (!filesystem_->DataSync(fd.get())) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Failed to sync file; filename: %s; content_size: %d ",
        file_path_.c_str(), static_cast<int>(new_proto_str.size())));
  }

  ICING_VLOG(1) << "Successfully wrote proto to file: " << file_path_;
  cached_proto_ = std::move(new_proto);
  cached_header_ = std::make_unique<Header>(std::move(header));
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_FILE_BACKED_PROTO_H_
