// Copyright (C) 2021 Google LLC
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

// File-backed log of protos with append-only writes and position based reads.
//
// There should only be one instance of a PortableFileBackedProtoLog of the same
// file at a time; using multiple instances at the same time may lead to
// undefined behavior.
//
// The entire checksum is computed on initialization to verify the contents are
// valid. On failure, the log will be truncated to the last verified state when
// PersistToDisk() was called. If the log cannot successfully restore the last
// state due to disk corruption or some other inconsistency, then the entire log
// will be lost.
//
// Each proto written to the file will have a metadata written just before it.
// The metadata consists of
//   {
//     1 bytes of kProtoMagic;
//     3 bytes of the proto size
//     n bytes of the proto itself
//   }
//
// All metadata is written in a portable format, encoded with htonl before
// writing to file and decoded with ntohl when reading from file.
//
// Example usage:
//   ICING_ASSERT_OK_AND_ASSIGN(auto create_result,
//       PortableFileBackedProtoLog<DocumentProto>::Create(filesystem,
//       file_path_,
//                                                  options));
//   auto proto_log = create_result.proto_log;
//
//   Document document;
//   document.set_namespace("com.google.android.example");
//   document.set_uri("www.google.com");
//
//   int64_t document_offset = proto_log->WriteProto(document));
//   Document same_document = proto_log->ReadProto(document_offset));
//   proto_log->PersistToDisk();

#ifndef ICING_FILE_PORTABLE_FILE_BACKED_PROTO_LOG_H_
#define ICING_FILE_PORTABLE_FILE_BACKED_PROTO_LOG_H_

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/portable/endian.h"
#include "icing/portable/gzip_stream.h"
#include "icing/portable/platform.h"
#include "icing/portable/zlib.h"
#include "icing/util/bit-util.h"
#include "icing/util/crc32.h"
#include "icing/util/data-loss.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include <google/protobuf/io/zero_copy_stream_impl_lite.h>

namespace icing {
namespace lib {

template <typename ProtoT>
class PortableFileBackedProtoLog {
 public:
  struct Options {
    // Whether to compress each proto before writing to the proto log.
    bool compress;

    // Byte-size limit for each proto written to the store. This does not
    // include the bytes needed for the metadata of each proto.
    //
    // NOTE: Currently, we only support protos up to 16MiB. We store the proto
    // size in 3 bytes within the metadata.
    //
    // NOTE: This limit is only enforced for future writes. If the store
    // previously had a higher limit, then reading older entries could return
    // larger protos.
    //
    // NOTE: The max_proto_size is the upper limit for input protos into the
    // ProtoLog. Even if the proto is larger than max_proto_size, but compresses
    // to a smaller size, ProtoLog will not accept it. Protos that result in a
    // compressed size larger than max_proto_size are also not accepted.
    const int32_t max_proto_size;

    // Level of compression if enabled, NO_COMPRESSION = 0, BEST_SPEED = 1,
    // BEST_COMPRESSION = 9
    const int32_t compression_level;

    // Must specify values for options.
    Options() = delete;
    explicit Options(
        bool compress_in, const int32_t max_proto_size_in = kMaxProtoSize,
        const int32_t compression_level_in = kDeflateCompressionLevel)
        : compress(compress_in),
          max_proto_size(max_proto_size_in),
          compression_level(compression_level_in) {}
  };

  // Our internal max for protos.
  //
  // WARNING: Changing this to a larger number may invalidate our assumption
  // that that proto size can safely be stored in the last 3 bytes of the proto
  // header.
  static constexpr int kMaxProtoSize = (1 << 24) - 1;  // 16MiB
  static_assert(kMaxProtoSize <= 0x00FFFFFF,
                "kMaxProtoSize doesn't fit in 3 bytes");

  // Level of compression, BEST_SPEED = 1, BEST_COMPRESSION = 9
  static constexpr int kDeflateCompressionLevel = 3;

  // Number of bytes we reserve for the heading at the beginning of the proto
  // log. We reserve this so the header can grow without running into the
  // contents of the proto log, triggering an unnecessary migration of the data.
  static constexpr int kHeaderReservedBytes = 256;

  // Header stored at the beginning of the file before the rest of the log
  // contents. Stores metadata on the log.
  class Header {
   public:
    static constexpr int32_t kMagic = 0xf4c6f67a;

    // We should go directly from 0 to 2 the next time we have to change the
    // format.
    static constexpr int32_t kFileFormatVersion = 0;

    uint32_t CalculateHeaderChecksum() const {
      Crc32 crc;

      // Get a string_view of all the fields of the Header, excluding the
      // magic_nbytes_ and header_checksum_nbytes_
      std::string_view header_str(
          reinterpret_cast<const char*>(this) +
              offsetof(Header, header_checksum_nbytes_) +
              sizeof(header_checksum_nbytes_),
          sizeof(Header) - sizeof(magic_nbytes_) -
              sizeof(header_checksum_nbytes_));
      crc.Append(header_str);
      return crc.Get();
    }

    int32_t GetMagic() const { return GNetworkToHostL(magic_nbytes_); }

    void SetMagic(int32_t magic_in) {
      magic_nbytes_ = GHostToNetworkL(magic_in);
    }

    int32_t GetFileFormatVersion() const {
      return GNetworkToHostL(file_format_version_nbytes_);
    }

    void SetFileFormatVersion(int32_t file_format_version_in) {
      file_format_version_nbytes_ = GHostToNetworkL(file_format_version_in);
    }

    int32_t GetMaxProtoSize() const {
      return GNetworkToHostL(max_proto_size_nbytes_);
    }

    void SetMaxProtoSize(int32_t max_proto_size_in) {
      max_proto_size_nbytes_ = GHostToNetworkL(max_proto_size_in);
    }

    int32_t GetLogChecksum() const {
      return GNetworkToHostL(log_checksum_nbytes_);
    }

    void SetLogChecksum(int32_t log_checksum_in) {
      log_checksum_nbytes_ = GHostToNetworkL(log_checksum_in);
    }

    int64_t GetRewindOffset() const {
      return GNetworkToHostLL(rewind_offset_nbytes_);
    }

    void SetRewindOffset(int64_t rewind_offset_in) {
      rewind_offset_nbytes_ = GHostToNetworkLL(rewind_offset_in);
    }

    int32_t GetHeaderChecksum() const {
      return GNetworkToHostL(header_checksum_nbytes_);
    }

    void SetHeaderChecksum(int32_t header_checksum_in) {
      header_checksum_nbytes_ = GHostToNetworkL(header_checksum_in);
    }

    bool GetCompressFlag() const { return GetFlag(kCompressBit); }

    void SetCompressFlag(bool compress) { SetFlag(kCompressBit, compress); }

    bool GetDirtyFlag() const { return GetFlag(kDirtyBit); }

    void SetDirtyFlag(bool dirty) { SetFlag(kDirtyBit, dirty); }

   private:
    // The least-significant bit offset at which the compress flag is stored in
    // 'flags_nbytes_'. Represents whether the protos in the log are compressed
    // or not.
    static constexpr int32_t kCompressBit = 0;

    // The least-significant bit offset at which the dirty flag is stored in
    // 'flags'. Represents whether the checksummed portion of the log has been
    // modified after the last checksum was computed.
    static constexpr int32_t kDirtyBit = 1;

    bool GetFlag(int offset) const {
      return bit_util::BitfieldGet(flags_, offset, /*len=*/1);
    }

    void SetFlag(int offset, bool value) {
      bit_util::BitfieldSet(value, offset, /*len=*/1, &flags_);
    }

    // Holds the magic as a quick sanity check against file corruption.
    //
    // Field is in network-byte order.
    int32_t magic_nbytes_ = GHostToNetworkL(kMagic);

    // Must be at the beginning after kMagic. Contains the crc checksum of
    // the following fields.
    //
    // Field is in network-byte order.
    uint32_t header_checksum_nbytes_ = 0;

    // Last known good offset at which the log and its checksum were updated.
    // If we crash between writing to the log and updating the checksum, we can
    // try to rewind the log to this offset and verify the checksum is still
    // valid instead of throwing away the entire log.
    //
    // Field is in network-byte order.
    int64_t rewind_offset_nbytes_ = GHostToNetworkLL(kHeaderReservedBytes);

    // Version number tracking how we serialize the file to disk. If we change
    // how/what we write to disk, this version should be updated and this class
    // should handle a migration.
    //
    // Currently at kFileFormatVersion.
    //
    // Field is in network-byte order.
    int32_t file_format_version_nbytes_ = 0;

    // The maximum proto size that can be written to the log.
    //
    // Field is in network-byte order.
    int32_t max_proto_size_nbytes_ = 0;

    // Checksum of the log elements, doesn't include the header fields.
    //
    // Field is in network-byte order.
    uint32_t log_checksum_nbytes_ = 0;

    // Bits are used to hold various flags.
    //   Lowest bit is whether the protos are compressed or not.
    //
    // Field is only 1 byte, so is byte-order agnostic.
    uint8_t flags_ = 0;

    // NOTE: New fields should *almost always* be added to the end here. Since
    // this class may have already been written to disk, appending fields
    // increases the chances that changes are backwards-compatible.
  };
  static_assert(sizeof(Header) <= kHeaderReservedBytes,
                "Header has grown past our reserved bytes!");

  struct CreateResult {
    // A successfully initialized log.
    std::unique_ptr<PortableFileBackedProtoLog<ProtoT>> proto_log;

    // The data status after initializing from a previous state. Data loss can
    // happen if the file is corrupted or some previously added data was
    // unpersisted. This may be used to signal that any derived data off of the
    // proto log may need to be regenerated.
    DataLoss data_loss = DataLoss::NONE;

    // Whether the proto log had to recalculate the checksum to check its
    // integrity. This can be avoided if no changes were made or the log was
    // able to update its checksum before shutting down. But it may have to
    // recalculate if it's unclear if we crashed after updating the log, but
    // before updating our checksum.
    bool recalculated_checksum = false;

    bool has_data_loss() const {
      return data_loss == DataLoss::PARTIAL || data_loss == DataLoss::COMPLETE;
    }
  };

  // Factory method to create, initialize, and return a
  // PortableFileBackedProtoLog. Will create the file if it doesn't exist.
  //
  // If on re-initialization the log detects disk corruption or some previously
  // added data was unpersisted, the log will rewind to the last-good state. The
  // log saves these checkpointed "good" states when PersistToDisk() is called
  // or the log is safely destructed. If the log rewinds successfully to the
  // last-good state, then the returned CreateResult.data_loss indicates
  // whether it has a data loss and what kind of data loss it is (partial or
  // complete) so that any derived data may know that it needs to be updated. If
  // the log re-initializes successfully without any data loss,
  // CreateResult.data_loss will be NONE.
  //
  // Params:
  //   filesystem: Handles system level calls
  //   file_path: Path of the underlying file. Directory of the file should
  //   already exist
  //   options: Configuration options for the proto log
  //
  // Returns:
  //   PortableFileBackedProtoLog::CreateResult on success
  //   INVALID_ARGUMENT on an invalid option
  //   INTERNAL_ERROR on IO error
  static libtextclassifier3::StatusOr<CreateResult> Create(
      const Filesystem* filesystem, const std::string& file_path,
      const Options& options);

  // Not copyable
  PortableFileBackedProtoLog(const PortableFileBackedProtoLog&) = delete;
  PortableFileBackedProtoLog& operator=(const PortableFileBackedProtoLog&) =
      delete;

  // This will update the checksum of the log as well.
  ~PortableFileBackedProtoLog();

  // Writes the serialized proto to the underlying file. Writes are applied
  // directly to the underlying file. Users do not need to sync the file after
  // writing.
  //
  // Returns:
  //   Offset of the newly appended proto in file on success
  //   INVALID_ARGUMENT if proto is too large, as decided by
  //     Options.max_proto_size
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> WriteProto(const ProtoT& proto);

  // Reads out a proto located at file_offset from the file.
  //
  // Returns:
  //   A proto on success
  //   NOT_FOUND if the proto at the given offset has been erased
  //   OUT_OF_RANGE_ERROR if file_offset exceeds file size
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<ProtoT> ReadProto(int64_t file_offset) const;

  // Erases the data of a proto located at file_offset from the file.
  //
  // Returns:
  //   OK on success
  //   OUT_OF_RANGE_ERROR if file_offset exceeds file size
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status EraseProto(int64_t file_offset);

  // Calculates and returns the disk usage in bytes. Rounds up to the nearest
  // block size.
  //
  // Returns:
  //   Disk usage on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const;

  // Returns the file size of all the elements held in the log. File size is in
  // bytes. This excludes the size of any internal metadata of the log, e.g. the
  // log's header.
  //
  // Returns:
  //   File size on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetElementsFileSize() const;

  // An iterator helping to find offsets of all the protos in file.
  // Example usage:
  //
  // while (iterator.Advance().ok()) {
  //   int64_t offset = iterator.GetOffset();
  //   // Do something
  // }
  class Iterator {
   public:
    Iterator(const Filesystem& filesystem, int fd, int64_t initial_offset);

    // Advances to the position of next proto whether it has been erased or not.
    //
    // Returns:
    //   OK on success
    //   OUT_OF_RANGE_ERROR if it reaches the end
    //   INTERNAL_ERROR on IO error
    libtextclassifier3::Status Advance();

    // Returns the file offset of current proto.
    int64_t GetOffset();

   private:
    static constexpr int64_t kInvalidOffset = -1;
    // Used to read proto metadata
    // Offset of first proto
    const Filesystem* const filesystem_;
    int64_t initial_offset_;
    int64_t current_offset_;
    int64_t file_size_;
    int fd_;
  };

  // Returns an iterator of current proto log. The caller needs to keep the
  // proto log unchanged while using the iterator, otherwise unexpected
  // behaviors could happen.
  Iterator GetIterator();

  // Persists all changes since initialization or the last call to
  // PersistToDisk(). Any changes that aren't persisted may be lost if the
  // system fails to close safely.
  //
  // Example use case:
  //
  //   Document document;
  //   document.set_namespace("com.google.android.example");
  //   document.set_uri("www.google.com");
  //
  //   {
  //     ICING_ASSERT_OK_AND_ASSIGN(auto create_result,
  //         PortableFileBackedProtoLog<DocumentProto>::Create(filesystem,
  //         file_path,
  //                                                    options));
  //     auto proto_log = std::move(create_result.proto_log);
  //
  //     int64_t document_offset = proto_log->WriteProto(document));
  //
  //     // We lose the document here since it wasn't persisted.
  //     // *SYSTEM CRASH*
  //   }
  //
  //   {
  //     // Can still successfully create after a crash since the log can
  //     // rewind/truncate to recover into a previously good state
  //     ICING_ASSERT_OK_AND_ASSIGN(auto create_result,
  //         PortableFileBackedProtoLog<DocumentProto>::Create(filesystem,
  //         file_path,
  //                                                    options));
  //     auto proto_log = std::move(create_result.proto_log);
  //
  //     // Lost the proto since we didn't PersistToDisk before the crash
  //     proto_log->ReadProto(document_offset)); // INVALID_ARGUMENT error
  //
  //     int64_t document_offset = proto_log->WriteProto(document));
  //
  //     // Persisted this time, so we should be ok.
  //     ICING_ASSERT_OK(proto_log->PersistToDisk());
  //   }
  //
  //   {
  //     ICING_ASSERT_OK_AND_ASSIGN(auto create_result,
  //         PortableFileBackedProtoLog<DocumentProto>::Create(filesystem,
  //         file_path,
  //                                                    options));
  //     auto proto_log = std::move(create_result.proto_log);
  //
  //     // SUCCESS
  //     Document same_document = proto_log->ReadProto(document_offset));
  //   }
  //
  // NOTE: Since all protos are already written to the file directly, this
  // just updates the checksum and rewind position. Without these updates,
  // future initializations will truncate the file and discard unpersisted
  // changes.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status PersistToDisk();

  // Calculates the checksum of the log contents. Excludes the header content.
  //
  // Returns:
  //   Crc of the log content
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<Crc32> ComputeChecksum();

 private:
  // Object can only be instantiated via the ::Create factory.
  PortableFileBackedProtoLog(const Filesystem* filesystem,
                             const std::string& file_path,
                             std::unique_ptr<Header> header,
                             int32_t compression_level);

  // Initializes a new proto log.
  //
  // Returns:
  //   std::unique_ptr<CreateResult> on success
  //   INTERNAL_ERROR on IO error
  static libtextclassifier3::StatusOr<CreateResult> InitializeNewFile(
      const Filesystem* filesystem, const std::string& file_path,
      const Options& options);

  // Verifies that the existing proto log is in a good state. If not in a good
  // state, then the proto log may be truncated to the last good state and
  // content will be lost.
  //
  // Returns:
  //   std::unique_ptr<CreateResult> on success
  //   INTERNAL_ERROR on IO error or internal inconsistencies in the file
  //   INVALID_ARGUMENT_ERROR if options aren't consistent with previous
  //     instances
  static libtextclassifier3::StatusOr<CreateResult> InitializeExistingFile(
      const Filesystem* filesystem, const std::string& file_path,
      const Options& options, int64_t file_size);

  // Takes an initial checksum and updates it with the content between `start`
  // and `end` offsets in the file.
  //
  // Returns:
  //   Crc of the content between `start`, inclusive, and `end`, exclusive.
  //   INTERNAL_ERROR on IO error
  //   INVALID_ARGUMENT_ERROR if start and end aren't within the file size
  static libtextclassifier3::StatusOr<Crc32> ComputeChecksum(
      const Filesystem* filesystem, const std::string& file_path,
      Crc32 initial_crc, int64_t start, int64_t end);

  // Reads out the metadata of a proto located at file_offset from the fd.
  // Metadata will be returned in host byte order endianness.
  //
  // Returns:
  //   Proto's metadata on success
  //   OUT_OF_RANGE_ERROR if file_offset exceeds file_size
  //   INTERNAL_ERROR if the metadata is invalid or any IO errors happen
  static libtextclassifier3::StatusOr<int32_t> ReadProtoMetadata(
      const Filesystem* const filesystem, int fd, int64_t file_offset,
      int64_t file_size);

  // Writes metadata of a proto to the fd. Takes in a host byte order endianness
  // metadata and converts it into a portable metadata before writing.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on any IO errors
  static libtextclassifier3::Status WriteProtoMetadata(
      const Filesystem* filesystem, int fd, int32_t host_order_metadata);

  static bool IsEmptyBuffer(const char* buffer, int size) {
    return std::all_of(buffer, buffer + size,
                       [](const char byte) { return byte == 0; });
  }

  // Helper function to get stored proto size from the metadata.
  // Metadata format: 8 bits magic + 24 bits size
  static int GetProtoSize(int metadata) { return metadata & 0x00FFFFFF; }

  // Helper function to get stored proto magic from the metadata.
  // Metadata format: 8 bits magic + 24 bits size
  static uint8_t GetProtoMagic(int metadata) { return metadata >> 24; }

  // Magic number added in front of every proto. Used when reading out protos
  // as a first check for corruption in each entry in the file. Even if there is
  // a corruption, the best we can do is roll back to our last recovery point
  // and throw away un-flushed data. We can discard/reuse this byte if needed so
  // that we have 4 bytes to store the size of protos, and increase the size of
  // protos we support.
  static constexpr uint8_t kProtoMagic = 0x5C;

  // Chunks of the file to mmap at a time, so we don't mmap the entire file.
  // Only used on 32-bit devices
  static constexpr int kMmapChunkSize = 4 * 1024 * 1024;  // 4MiB

  ScopedFd fd_;
  const Filesystem* const filesystem_;
  const std::string file_path_;
  std::unique_ptr<Header> header_;
  const int32_t compression_level_;
};

template <typename ProtoT>
PortableFileBackedProtoLog<ProtoT>::PortableFileBackedProtoLog(
    const Filesystem* filesystem, const std::string& file_path,
    std::unique_ptr<Header> header, int32_t compression_level)
    : filesystem_(filesystem),
      file_path_(file_path),
      header_(std::move(header)),
      compression_level_(compression_level) {
  fd_.reset(filesystem_->OpenForAppend(file_path.c_str()));
}

template <typename ProtoT>
PortableFileBackedProtoLog<ProtoT>::~PortableFileBackedProtoLog() {
  if (!PersistToDisk().ok()) {
    ICING_LOG(WARNING) << "Error persisting to disk during destruction of "
                          "PortableFileBackedProtoLog: "
                       << file_path_;
  }
}

template <typename ProtoT>
libtextclassifier3::StatusOr<
    typename PortableFileBackedProtoLog<ProtoT>::CreateResult>
PortableFileBackedProtoLog<ProtoT>::Create(const Filesystem* filesystem,
                                           const std::string& file_path,
                                           const Options& options) {
  if (options.max_proto_size <= 0) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "options.max_proto_size must be greater than 0, was %d",
        options.max_proto_size));
  }

  // Since we store the proto_size in 3 bytes, we can only support protos of up
  // to 16MiB.
  if (options.max_proto_size > kMaxProtoSize) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "options.max_proto_size must be under 16MiB, was %d",
        options.max_proto_size));
  }

  if (options.compression_level < 0 || options.compression_level > 9) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "options.compression_level must be between 0 and 9 inclusive, was %d",
        options.compression_level));
  }

  if (!filesystem->FileExists(file_path.c_str())) {
    return InitializeNewFile(filesystem, file_path, options);
  }

  int64_t file_size = filesystem->GetFileSize(file_path.c_str());
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Bad file size '", file_path, "'"));
  }

  if (file_size == 0) {
    return InitializeNewFile(filesystem, file_path, options);
  }

  return InitializeExistingFile(filesystem, file_path, options, file_size);
}

template <typename ProtoT>
libtextclassifier3::StatusOr<
    typename PortableFileBackedProtoLog<ProtoT>::CreateResult>
PortableFileBackedProtoLog<ProtoT>::InitializeNewFile(
    const Filesystem* filesystem, const std::string& file_path,
    const Options& options) {
  // Grow to the minimum reserved bytes for the header.
  if (!filesystem->Truncate(file_path.c_str(), kHeaderReservedBytes)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to initialize file size: ", file_path));
  }

  // Create the header
  std::unique_ptr<Header> header = std::make_unique<Header>();
  header->SetCompressFlag(options.compress);
  header->SetMaxProtoSize(options.max_proto_size);
  header->SetHeaderChecksum(header->CalculateHeaderChecksum());

  if (!filesystem->Write(file_path.c_str(), header.get(), sizeof(Header))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to write header for file: ", file_path));
  }

  CreateResult create_result = {
      std::unique_ptr<PortableFileBackedProtoLog<ProtoT>>(
          new PortableFileBackedProtoLog<ProtoT>(filesystem, file_path,
                                                 std::move(header),
                                                 options.compression_level)),
      /*data_loss=*/DataLoss::NONE, /*recalculated_checksum=*/false};

  return create_result;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<
    typename PortableFileBackedProtoLog<ProtoT>::CreateResult>
PortableFileBackedProtoLog<ProtoT>::InitializeExistingFile(
    const Filesystem* filesystem, const std::string& file_path,
    const Options& options, int64_t file_size) {
  bool header_changed = false;
  if (file_size < kHeaderReservedBytes) {
    return absl_ports::InternalError(
        absl_ports::StrCat("File header too short for: ", file_path));
  }

  std::unique_ptr<Header> header = std::make_unique<Header>();
  if (!filesystem->PRead(file_path.c_str(), header.get(), sizeof(Header),
                         /*offset=*/0)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to read header for file: ", file_path));
  }

  // Make sure the header is still valid before we use any of its values. This
  // is covered by the header_checksum check below, but this is a quick check
  // that can save us from an extra crc computation.
  if (header->GetMagic() != Header::kMagic) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Invalid header kMagic for file: ", file_path));
  }

  if (header->GetHeaderChecksum() != header->CalculateHeaderChecksum()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Invalid header checksum for: ", file_path));
  }

  if (header->GetFileFormatVersion() != Header::kFileFormatVersion) {
    // If this changes, we might need to handle a migration rather than throwing
    // an error.
    return absl_ports::InternalError(
        absl_ports::StrCat("Invalid header file format version: ", file_path));
  }

  if (header->GetCompressFlag() != options.compress) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Inconsistent compress option, expected %d, actual %d",
        header->GetCompressFlag(), options.compress));
  }

  int32_t existing_max_proto_size = header->GetMaxProtoSize();
  if (existing_max_proto_size > options.max_proto_size) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Max proto size cannot be smaller than previous "
        "instantiations, previous size %d, wanted size %d",
        header->GetMaxProtoSize(), options.max_proto_size));
  } else if (existing_max_proto_size < options.max_proto_size) {
    // It's fine if our new max size is greater than our previous one. Existing
    // data is still valid.
    header->SetMaxProtoSize(options.max_proto_size);
    header_changed = true;
  }

  DataLoss data_loss = DataLoss::NONE;

  // If we have any documents in our tail, get rid of them since they're not in
  // our checksum. Our checksum reflects content up to the rewind offset.
  if (file_size > header->GetRewindOffset()) {
    if (!filesystem->Truncate(file_path.c_str(), header->GetRewindOffset())) {
      return absl_ports::InternalError(IcingStringUtil::StringPrintf(
          "Failed to truncate '%s' to size %lld", file_path.data(),
          static_cast<long long>(header->GetRewindOffset())));
    }
    data_loss = DataLoss::PARTIAL;
  }

  bool recalculated_checksum = false;

  // If our dirty flag is set, that means we might have crashed in the middle of
  // erasing a proto. This could have happened anywhere between:
  //   A. Set dirty flag to true and update header checksum
  //   B. Erase the proto
  //   C. Set dirty flag to false, update log checksum, update header checksum
  //
  // Scenario 1: We went down between A and B. Maybe our dirty flag is a
  // false alarm and we can keep all our data.
  //
  // Scenario 2: We went down between B and C. Our data is compromised and
  // we need to throw everything out.
  if (header->GetDirtyFlag()) {
    // Recompute the log's checksum to detect which scenario we're in.
    ICING_ASSIGN_OR_RETURN(
        Crc32 calculated_log_checksum,
        ComputeChecksum(filesystem, file_path, Crc32(),
                        /*start=*/kHeaderReservedBytes, /*end=*/file_size));

    if (header->GetLogChecksum() != calculated_log_checksum.Get()) {
      // Still doesn't match, we're in Scenario 2. Throw out all our data now
      // and initialize as a new instance.
      ICING_ASSIGN_OR_RETURN(CreateResult create_result,
                             InitializeNewFile(filesystem, file_path, options));
      create_result.data_loss = DataLoss::COMPLETE;
      create_result.recalculated_checksum = true;
      return create_result;
    }
    // Otherwise we're good, checksum matches our contents so continue
    // initializing like normal.
    recalculated_checksum = true;

    // Update our header.
    header->SetDirtyFlag(false);
    header_changed = true;
  }

  if (header_changed) {
    header->SetHeaderChecksum(header->CalculateHeaderChecksum());

    if (!filesystem->PWrite(file_path.c_str(), /*offset=*/0, header.get(),
                            sizeof(Header))) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Failed to update header to: ", file_path));
    }
  }

  CreateResult create_result = {
      std::unique_ptr<PortableFileBackedProtoLog<ProtoT>>(
          new PortableFileBackedProtoLog<ProtoT>(filesystem, file_path,
                                                 std::move(header),
                                                 options.compression_level)),
      data_loss, recalculated_checksum};

  return create_result;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<Crc32>
PortableFileBackedProtoLog<ProtoT>::ComputeChecksum(
    const Filesystem* filesystem, const std::string& file_path,
    Crc32 initial_crc, int64_t start, int64_t end) {
  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(*filesystem, file_path,
                               MemoryMappedFile::Strategy::READ_ONLY));
  Crc32 new_crc(initial_crc.Get());

  if (start < 0) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Starting checksum offset of file '%s' must be greater than 0, was "
        "%lld",
        file_path.c_str(), static_cast<long long>(start)));
  }

  if (end < start) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Ending checksum offset of file '%s' must be greater than start "
        "'%lld', was '%lld'",
        file_path.c_str(), static_cast<long long>(start),
        static_cast<long long>(end)));
  }

  int64_t file_size = filesystem->GetFileSize(file_path.c_str());
  if (end > file_size) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Ending checksum offset of file '%s' must be within "
        "file size of %lld, was %lld",
        file_path.c_str(), static_cast<long long>(file_size),
        static_cast<long long>(end)));
  }

  Architecture architecture = GetArchitecture();
  switch (architecture) {
    case Architecture::BIT_64: {
      // Don't mmap in chunks here since mmapping can be harmful on 64-bit
      // devices where mmap/munmap calls need the mmap write semaphore, which
      // blocks mmap/munmap/mprotect and all page faults from executing while
      // they run. On 64-bit devices, this doesn't actually load into memory, it
      // just makes the file faultable. So the whole file should be ok.
      // b/185822878.
      ICING_RETURN_IF_ERROR(mmapped_file.Remap(start, end - start));
      auto mmap_str = std::string_view(mmapped_file.region(), end - start);
      new_crc.Append(mmap_str);
      break;
    }
    case Architecture::BIT_32:
      [[fallthrough]];
    case Architecture::UNKNOWN: {
      // 32-bit devices only have 4GB of RAM. Mmap in chunks to not use up too
      // much memory at once. If we're unknown, then also chunk it because we're
      // not sure what the device can handle.
      for (int i = start; i < end; i += kMmapChunkSize) {
        // Don't read past the file size.
        int next_chunk_size = kMmapChunkSize;
        if ((i + kMmapChunkSize) >= end) {
          next_chunk_size = end - i;
        }

        ICING_RETURN_IF_ERROR(mmapped_file.Remap(i, next_chunk_size));

        auto mmap_str =
            std::string_view(mmapped_file.region(), next_chunk_size);
        new_crc.Append(mmap_str);
      }
      break;
    }
  }

  return new_crc;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<int64_t>
PortableFileBackedProtoLog<ProtoT>::WriteProto(const ProtoT& proto) {
  int64_t proto_size = proto.ByteSizeLong();
  int32_t host_order_metadata;
  int64_t current_position = filesystem_->GetCurrentPosition(fd_.get());

  if (proto_size > header_->GetMaxProtoSize()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "proto_size, %lld, was too large to write. Max is %d",
        static_cast<long long>(proto_size), header_->GetMaxProtoSize()));
  }

  // At this point, we've guaranteed that proto_size is under kMaxProtoSize
  // (see
  // ::Create), so we can safely store it in an int.
  int final_size = 0;

  std::string proto_str;
  google::protobuf::io::StringOutputStream proto_stream(&proto_str);

  if (header_->GetCompressFlag()) {
    protobuf_ports::GzipOutputStream::Options options;
    options.format = protobuf_ports::GzipOutputStream::ZLIB;
    options.compression_level = compression_level_;

    protobuf_ports::GzipOutputStream compressing_stream(&proto_stream, options);

    bool success = proto.SerializeToZeroCopyStream(&compressing_stream) &&
                   compressing_stream.Close();

    if (!success) {
      return absl_ports::InternalError("Error compressing proto.");
    }

    final_size = proto_str.size();

    // In case the compressed proto is larger than the original proto, we also
    // can't write it.
    if (final_size > header_->GetMaxProtoSize()) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Compressed proto size, %d, was greater than "
          "max_proto_size, %d",
          final_size, header_->GetMaxProtoSize()));
    }
  } else {
    // Serialize the proto directly into the write buffer at an offset of the
    // metadata.
    proto.SerializeToZeroCopyStream(&proto_stream);
    final_size = proto_str.size();
  }

  // 1st byte for magic, next 3 bytes for proto size.
  host_order_metadata = (kProtoMagic << 24) | final_size;

  // Actually write metadata, has to be done after we know the possibly
  // compressed proto size
  ICING_RETURN_IF_ERROR(
      WriteProtoMetadata(filesystem_, fd_.get(), host_order_metadata));

  // Write the serialized proto
  if (!filesystem_->Write(fd_.get(), proto_str.data(), proto_str.size())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to write proto to: ", file_path_));
  }

  return current_position;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<ProtoT>
PortableFileBackedProtoLog<ProtoT>::ReadProto(int64_t file_offset) const {
  int64_t file_size = filesystem_->GetFileSize(fd_.get());
  // Read out the metadata
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::OutOfRangeError("Unable to correctly read size.");
  }
  ICING_ASSIGN_OR_RETURN(
      int32_t metadata,
      ReadProtoMetadata(filesystem_, fd_.get(), file_offset, file_size));

  // Copy out however many bytes it says the proto is
  int stored_size = GetProtoSize(metadata);
  file_offset += sizeof(metadata);

  // Read the compressed proto out.
  if (file_offset + stored_size > file_size) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Trying to read from a location, %lld, "
                                      "out of range of the file size, %lld",
                                      static_cast<long long>(file_offset),
                                      static_cast<long long>(file_size - 1)));
  }
  auto buf = std::make_unique<char[]>(stored_size);
  if (!filesystem_->PRead(fd_.get(), buf.get(), stored_size, file_offset)) {
    return absl_ports::InternalError("");
  }

  if (IsEmptyBuffer(buf.get(), stored_size)) {
    return absl_ports::NotFoundError("The proto data has been erased.");
  }

  google::protobuf::io::ArrayInputStream proto_stream(buf.get(), stored_size);

  // Deserialize proto
  ProtoT proto;
  if (header_->GetCompressFlag()) {
    protobuf_ports::GzipInputStream decompress_stream(&proto_stream);
    proto.ParseFromZeroCopyStream(&decompress_stream);
  } else {
    proto.ParseFromZeroCopyStream(&proto_stream);
  }

  return proto;
}

template <typename ProtoT>
libtextclassifier3::Status PortableFileBackedProtoLog<ProtoT>::EraseProto(
    int64_t file_offset) {
  int64_t file_size = filesystem_->GetFileSize(fd_.get());
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::OutOfRangeError("Unable to correctly read size.");
  }

  ICING_ASSIGN_OR_RETURN(
      int32_t metadata,
      ReadProtoMetadata(filesystem_, fd_.get(), file_offset, file_size));
  // Copy out however many bytes it says the proto is
  int stored_size = GetProtoSize(metadata);
  file_offset += sizeof(metadata);
  if (file_offset + stored_size > file_size) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Trying to read from a location, %lld, "
                                      "out of range of the file size, %lld",
                                      static_cast<long long>(file_offset),
                                      static_cast<long long>(file_size - 1)));
  }
  auto buf = std::make_unique<char[]>(stored_size);

  // We need to update the crc checksum if the erased area is before the
  // rewind position.
  int32_t new_crc;
  if (file_offset < header_->GetRewindOffset()) {
    // Set to "dirty" before we start writing anything.
    header_->SetDirtyFlag(true);
    header_->SetHeaderChecksum(header_->CalculateHeaderChecksum());
    if (!filesystem_->PWrite(fd_.get(), /*offset=*/0, header_.get(),
                             sizeof(Header))) {
      return absl_ports::InternalError(absl_ports::StrCat(
          "Failed to update dirty bit of header to: ", file_path_));
    }

    // We need to calculate [original string xor 0s].
    // The xored string is the same as the original string because 0 xor 0 =
    // 0, 1 xor 0 = 1.
    // Read the compressed proto out.
    if (!filesystem_->PRead(fd_.get(), buf.get(), stored_size, file_offset)) {
      return absl_ports::InternalError("");
    }
    const std::string_view xored_str(buf.get(), stored_size);

    Crc32 crc(header_->GetLogChecksum());
    ICING_ASSIGN_OR_RETURN(
        new_crc,
        crc.UpdateWithXor(xored_str,
                          /*full_data_size=*/header_->GetRewindOffset() -
                              kHeaderReservedBytes,
                          /*position=*/file_offset - kHeaderReservedBytes));
  }

  // Clear the region.
  memset(buf.get(), '\0', stored_size);
  if (!filesystem_->PWrite(fd_.get(), file_offset, buf.get(), stored_size)) {
    return absl_ports::InternalError("");
  }

  // If we cleared something in our checksummed area, we should update our
  // checksum and reset our dirty bit.
  if (file_offset < header_->GetRewindOffset()) {
    header_->SetDirtyFlag(false);
    header_->SetLogChecksum(new_crc);
    header_->SetHeaderChecksum(header_->CalculateHeaderChecksum());

    if (!filesystem_->PWrite(fd_.get(), /*offset=*/0, header_.get(),
                             sizeof(Header))) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Failed to update header to: ", file_path_));
    }
  }

  return libtextclassifier3::Status::OK;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<int64_t>
PortableFileBackedProtoLog<ProtoT>::GetDiskUsage() const {
  int64_t size = filesystem_->GetDiskUsage(file_path_.c_str());
  if (size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError("Failed to get disk usage of proto log");
  }
  return size;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<int64_t>
PortableFileBackedProtoLog<ProtoT>::GetElementsFileSize() const {
  int64_t total_file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (total_file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        "Failed to get file size of elments in the proto log");
  }
  return total_file_size - kHeaderReservedBytes;
}

template <typename ProtoT>
PortableFileBackedProtoLog<ProtoT>::Iterator::Iterator(
    const Filesystem& filesystem, int fd, int64_t initial_offset)
    : filesystem_(&filesystem),
      initial_offset_(initial_offset),
      current_offset_(kInvalidOffset),
      fd_(fd) {
  file_size_ = filesystem_->GetFileSize(fd_);
  if (file_size_ == Filesystem::kBadFileSize) {
    // Fails all Advance() calls
    file_size_ = 0;
  }
}

template <typename ProtoT>
libtextclassifier3::Status
PortableFileBackedProtoLog<ProtoT>::Iterator::Advance() {
  if (current_offset_ == kInvalidOffset) {
    // First Advance() call
    current_offset_ = initial_offset_;
  } else {
    // Jumps to the next proto position
    ICING_ASSIGN_OR_RETURN(
        int32_t metadata,
        ReadProtoMetadata(filesystem_, fd_, current_offset_, file_size_));
    current_offset_ += sizeof(metadata) + GetProtoSize(metadata);
  }

  if (current_offset_ < file_size_) {
    return libtextclassifier3::Status::OK;
  } else {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "The next proto offset, %lld, is out of file range [0, %lld)",
        static_cast<long long>(current_offset_),
        static_cast<long long>(file_size_)));
  }
}

template <typename ProtoT>
int64_t PortableFileBackedProtoLog<ProtoT>::Iterator::GetOffset() {
  return current_offset_;
}

template <typename ProtoT>
typename PortableFileBackedProtoLog<ProtoT>::Iterator
PortableFileBackedProtoLog<ProtoT>::GetIterator() {
  return Iterator(*filesystem_, fd_.get(),
                  /*initial_offset=*/kHeaderReservedBytes);
}

template <typename ProtoT>
libtextclassifier3::StatusOr<int32_t>
PortableFileBackedProtoLog<ProtoT>::ReadProtoMetadata(
    const Filesystem* const filesystem, int fd, int64_t file_offset,
    int64_t file_size) {
  // Checks file_offset
  if (file_offset >= file_size) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "offset, %lld, is out of file range [0, %lld)",
        static_cast<long long>(file_offset),
        static_cast<long long>(file_size)));
  }
  int32_t portable_metadata;
  int metadata_size = sizeof(portable_metadata);
  if (file_offset + metadata_size >= file_size) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Wrong metadata offset %lld, metadata doesn't fit in "
        "with file range [0, %lld)",
        static_cast<long long>(file_offset),
        static_cast<long long>(file_size)));
  }

  if (!filesystem->PRead(fd, &portable_metadata, metadata_size, file_offset)) {
    return absl_ports::InternalError("");
  }

  // Need to switch it back to host order endianness after reading from disk.
  int32_t host_order_metadata = GNetworkToHostL(portable_metadata);

  // Checks magic number
  uint8_t stored_k_proto_magic = GetProtoMagic(host_order_metadata);
  if (stored_k_proto_magic != kProtoMagic) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Failed to read kProtoMagic, expected %d, actual %d", kProtoMagic,
        stored_k_proto_magic));
  }

  return host_order_metadata;
}

template <typename ProtoT>
libtextclassifier3::Status
PortableFileBackedProtoLog<ProtoT>::WriteProtoMetadata(
    const Filesystem* filesystem, int fd, int32_t host_order_metadata) {
  // Convert it into portable endian format before writing to disk
  int32_t portable_metadata = GHostToNetworkL(host_order_metadata);
  int portable_metadata_size = sizeof(portable_metadata);

  // Write metadata
  if (!filesystem->Write(fd, &portable_metadata, portable_metadata_size)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to write proto metadata."));
  }

  return libtextclassifier3::Status::OK;
}

template <typename ProtoT>
libtextclassifier3::Status PortableFileBackedProtoLog<ProtoT>::PersistToDisk() {
  int64_t file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (file_size == header_->GetRewindOffset()) {
    // No new protos appended, don't need to update the checksum.
    return libtextclassifier3::Status::OK;
  }

  ICING_ASSIGN_OR_RETURN(Crc32 crc, ComputeChecksum());

  header_->SetLogChecksum(crc.Get());
  header_->SetRewindOffset(file_size);
  header_->SetHeaderChecksum(header_->CalculateHeaderChecksum());

  if (!filesystem_->PWrite(fd_.get(), /*offset=*/0, header_.get(),
                           sizeof(Header)) ||
      !filesystem_->DataSync(fd_.get())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to update header to: ", file_path_));
  }

  return libtextclassifier3::Status::OK;
}

template <typename ProtoT>
libtextclassifier3::StatusOr<Crc32>
PortableFileBackedProtoLog<ProtoT>::ComputeChecksum() {
  int64_t file_size = filesystem_->GetFileSize(file_path_.c_str());
  int64_t new_content_size = file_size - header_->GetRewindOffset();
  Crc32 crc;
  if (new_content_size == 0) {
    // No new protos appended, return cached checksum
    return Crc32(header_->GetLogChecksum());
  } else if (new_content_size < 0) {
    // File shrunk, recalculate the entire checksum.
    ICING_ASSIGN_OR_RETURN(
        crc,
        ComputeChecksum(filesystem_, file_path_, Crc32(),
                        /*start=*/kHeaderReservedBytes, /*end=*/file_size));
  } else {
    // Append new changes to the existing checksum.
    ICING_ASSIGN_OR_RETURN(
        crc, ComputeChecksum(
                 filesystem_, file_path_, Crc32(header_->GetLogChecksum()),
                 /*start=*/header_->GetRewindOffset(), /*end=*/file_size));
  }
  return crc;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_PORTABLE_FILE_BACKED_PROTO_LOG_H_
