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

#include "icing/file/file-backed-bitmap.h"

#include <cstdint>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"
#include "icing/util/math-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

int FileBackedBitmap::GetBlockCapacity(int num_blocks) {
  // The first block has a lower capacity due to the Header.
  const int capacity_bytes = kBlockByteSize * num_blocks - kHeaderByteSize;
  return capacity_bytes * 8;
}

libtextclassifier3::StatusOr<std::unique_ptr<FileBackedBitmap>>
FileBackedBitmap::Create(const Filesystem* filesystem,
                         std::string_view file_path,
                         MemoryMappedFile::Strategy mmap_strategy) {
  if (mmap_strategy == MemoryMappedFile::Strategy::READ_WRITE_MANUAL_SYNC) {
    return absl_ports::UnimplementedError(
        "FileBackedBitmap currently doesn't support READ_WRITE_MANUAL_SYNC "
        "mmap strategy.");
  }

  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile mmapper,
      MemoryMappedFile::Create(*filesystem, file_path, mmap_strategy));

  auto bitmap = std::unique_ptr<FileBackedBitmap>(
      new FileBackedBitmap(filesystem, file_path, std::move(mmapper)));

  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = bitmap->Initialize();
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message();
    return status;
  }
  return bitmap;
}

FileBackedBitmap::FileBackedBitmap(const Filesystem* filesystem,
                                   std::string_view file_path,
                                   MemoryMappedFile&& mmapper)
    : filesystem_(filesystem),
      file_path_(file_path),
      mmapper_(std::make_unique<MemoryMappedFile>(std::move(mmapper))) {}

FileBackedBitmap::~FileBackedBitmap() {
  // Only update if we have auto_sync setup, otherwise the checksum will be
  // updated when the client calls PersistToDisk
  if (mmapper_->strategy() ==
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC) {
    // Any valid, initialized file should at least have 1 block.
    if (mmapper_->region_size() >= kBlockByteSize &&
        header().version == kCurrentVersion &&
        header().state == Header::ChecksumState::kStale) {
      if (!PersistToDisk().ok()) {
        ICING_LOG(WARNING)
            << "Failed to persist bitmap to disk while destructing "
            << file_path_;
      }
    }
  }
}

const FileBackedBitmap::Header& FileBackedBitmap::header() const {
  return reinterpret_cast<const Header&>(*mmapper_->region());
}

FileBackedBitmap::Header* FileBackedBitmap::mutable_header() {
  return reinterpret_cast<Header*>(mmapper_->mutable_region());
}

libtextclassifier3::Status FileBackedBitmap::FileBackedBitmap::Initialize() {
  ICING_VLOG(1) << "Initialize bitmap file: " << file_path_;

  const bool is_new_bitmap = !filesystem_->FileExists(file_path_.c_str());

  int64_t file_size = 0;
  if (is_new_bitmap) {
    file_size = kBlockByteSize;
    if (!filesystem_->Grow(file_path_.c_str(), file_size)) {
      return absl_ports::InternalError(IcingStringUtil::StringPrintf(
          "Unable to create a minimal bitmap; "
          "filename: %s; target size: %lld",
          file_path_.c_str(), static_cast<long long>(file_size)));
    }

    ICING_VLOG(1) << "Creating new bitmap in file: " << file_path_
                  << " of size: " << file_size;
  } else {
    file_size = filesystem_->GetFileSize(file_path_.c_str());
    if (file_size == Filesystem::kBadFileSize) {
      return absl_ports::InternalError(IcingStringUtil::StringPrintf(
          "File corrupted; filename: %s; size: %lld.", file_path_.c_str(),
          static_cast<long long>(file_size)));
    }

    ICING_VLOG(1) << "Loading bitmap from  file: " << file_path_
                  << " of size: " << file_size;
  }

  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = mmapper_->Remap(0, file_size);
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message();
    return status;
  }

  if (is_new_bitmap) {
    mutable_header()->version = kCurrentVersion;
    mutable_header()->state = Header::ChecksumState::kStale;
    mutable_header()->checksum = 0;

    return mmapper_->PersistToDisk();
  }

  if (header().state == Header::ChecksumState::kStale) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "File corrupted, has partially flushed data; filename: ", file_path_));
  }

  if (header().checksum != ComputeChecksum()) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "File corrupted, checksum doesn't match; filename: ", file_path_));
  }

  if (header().version != kCurrentVersion) {
    return UpgradeToCurrentVersion();
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status FileBackedBitmap::UpgradeToCurrentVersion() {
  // Currently, only 1 format is supported.
  return absl_ports::InternalError(IcingStringUtil::StringPrintf(
      "File corrupted, mismatched version; filename: %s; %d vs %d.",
      file_path_.c_str(), header().version, kCurrentVersion));
}

libtextclassifier3::Status FileBackedBitmap::SetWord(int word_index,
                                                     Word word) {
  if (word_index >= NumBits() / kNumWordBits) {
    ICING_LOG(ERROR) << "word_index: " << word_index
                     << ", number of words: " << NumBits() / kNumWordBits;
    return absl_ports::InternalError("Trying to access invalid memory");
  }

  Word* bitmap_data =
      reinterpret_cast<Word*>(mmapper_->mutable_region() + kHeaderByteSize);

  bitmap_data[word_index] = word;

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<FileBackedBitmap::Word> FileBackedBitmap::GetWord(
    int word_index) const {
  if (word_index >= NumBits() / kNumWordBits) {
    ICING_LOG(ERROR) << "word_index: " << word_index
                     << ", number of words: " << NumBits() / kNumWordBits;
    return absl_ports::InternalError("Trying to access invalid memory");
  }

  const Word* bitmap_data = reinterpret_cast<const Word*>(
      mmapper_->mutable_region() + kHeaderByteSize);
  return bitmap_data[word_index];
}

int FileBackedBitmap::NumBits() const {
  return (mmapper_->region_size() - kHeaderByteSize) * 8;
}

libtextclassifier3::Status FileBackedBitmap::Set(int bit_index,
                                                 bool bit_value) {
  if (bit_index >= NumBits()) {
    // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
    // that can support error logging.
    libtextclassifier3::Status status = GrowTo(bit_index);
    if (!status.ok()) {
      ICING_LOG(ERROR) << status.error_message();
      return status;
    }

    if (!bit_value) {
      // All newly added bits are set to false.
      return libtextclassifier3::Status::OK;
    }
  }

  // Figure out which word needs to be modified.
  const int word_index = bit_index / kNumWordBits;
  const int word_mask = 1u << (bit_index % kNumWordBits);

  ICING_ASSIGN_OR_RETURN(Word old_word, GetWord(word_index));
  Word new_word = bit_value ? (old_word | word_mask) : old_word & ~word_mask;
  if (new_word != old_word) {
    ICING_RETURN_IF_ERROR(SetWord(word_index, new_word));
    mutable_header()->state = Header::ChecksumState::kStale;
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<bool> FileBackedBitmap::Get(int bit_index) const {
  if (bit_index >= NumBits()) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Bitmap file %s is of size %d and can't read bit_index %d.",
        file_path_.c_str(), NumBits(), bit_index));
  }

  const Word word_index = bit_index / kNumWordBits;
  const Word word_mask = 1u << (bit_index % kNumWordBits);

  ICING_ASSIGN_OR_RETURN(Word word, GetWord(word_index));
  return word & word_mask;
}

size_t FileBackedBitmap::FileSizeForBits(int num_bits) {
  const int word_index = num_bits / kNumWordBits;
  size_t new_file_size = kHeaderByteSize + (word_index + 1) * sizeof(Word);
  return math_util::RoundUpTo(new_file_size,
                              static_cast<size_t>(kBlockByteSize));
}

libtextclassifier3::Status FileBackedBitmap::GrowTo(int new_num_bits) {
  if (new_num_bits > kMaxNumBits) {
    return absl_ports::ResourceExhaustedError(IcingStringUtil::StringPrintf(
        "Bitmap file %s has a max-capacity of %d bits and cannot fit %d bits",
        file_path_.c_str(), kMaxNumBits, new_num_bits));
  }

  const size_t new_file_size = FileSizeForBits(new_num_bits);
  if (!filesystem_->Grow(file_path_.c_str(), new_file_size)) {
    return absl_ports::InternalError(
        IcingStringUtil::StringPrintf("Growing file %s to new size %zd failed",
                                      file_path_.c_str(), new_file_size));
  }

  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = mmapper_->Remap(0, new_file_size);
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message();
    return status;
  }

  ICING_VLOG(1) << "Grew file " << file_path_ << " to new size "
                << new_file_size;
  mutable_header()->state = Header::ChecksumState::kStale;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status FileBackedBitmap::TruncateTo(int new_num_bits) {
  if (new_num_bits > NumBits()) {
    return libtextclassifier3::Status::OK;
  }

  const size_t new_file_size = FileSizeForBits(new_num_bits);
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = mmapper_->Remap(0, new_file_size);
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message();
    return status;
  }
  if (!filesystem_->Truncate(file_path_.c_str(), new_file_size)) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Truncating file %s to new size %zd failed", file_path_.c_str(),
        new_file_size));
  }

  const int word_index = new_num_bits / kNumWordBits;
  // Mask to only keep bits <= new_num_bits and clear everything else.
  const Word word_mask = (1u << (new_num_bits % kNumWordBits)) - 1;

  ICING_ASSIGN_OR_RETURN(Word old_word, GetWord(word_index));
  Word new_word = old_word & word_mask;
  ICING_RETURN_IF_ERROR(SetWord(word_index, new_word));

  // TODO(cassiewang) It might be worth replacing this with memset().
  const int num_words = NumBits() / kNumWordBits;
  for (int i = word_index + 1; i < num_words; ++i) {
    ICING_RETURN_IF_ERROR(SetWord(i, 0));
  }

  mutable_header()->state = Header::ChecksumState::kStale;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status FileBackedBitmap::PersistToDisk() {
  mutable_header()->checksum = ComputeChecksum();
  mutable_header()->state = Header::ChecksumState::kFresh;
  return mmapper_->PersistToDisk();
}

uint32_t FileBackedBitmap::ComputeChecksum() const {
  std::string_view bitmap_bytes(mmapper_->region() + kHeaderByteSize,
                                NumBits() / 8);
  return Crc32().Append(bitmap_bytes);
}

}  // namespace lib
}  // namespace icing
