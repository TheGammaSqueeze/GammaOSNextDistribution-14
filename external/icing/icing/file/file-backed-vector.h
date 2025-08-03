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

// A file-backed vector that can store fixed-width elements. It provides
// built-in support for checksums to verify data integrity and an in-memory
// cache for fast read/writes.
//
// If the file is corrupted/in an invalid state, all contents are lost, i.e.
// there is no clear recovery path other than recreating/repopulating the
// contents.
//
// Note on Performance:
// The class keeps the vector in a mmapped area. This allows users to specify
// which MemoryMappedFile::Strategy they wish to use with this class. The vector
// will implicitly grow when the user tries to access an element beyond its
// current size. Growing happens in 16KiB chunks, up to a maximum size of 1MiB.
//
// Note on Checksumming:
// Checksumming happens lazily. We do tail checksums to avoid recalculating the
// checksum of the entire file on each modfification. A full checksum will be
// computed/verified at creation time, when persisting to disk, or whenever the
// user manually calls ComputeChecksum(). A separate header checksum is kept for
// a quick integrity check.
//
//
// Usage:
// RETURN_OR_ASSIGN(auto vector, FileBackedVector<char>::Create(...));
//
// ICING_RETURN_IF_ERROR(vector->Set(0, 'a'));
// ICING_RETURN_IF_ERROR(vector->Set(1, 'b'));
// ICING_RETURN_IF_ERROR(vector->Set(2, 'c'));
//
// vector->num_elements();  // Returns 3
//
// vector->At(2);  // Returns 'c'
//
// vector->TruncateTo(1);
// vector->num_elements();  // Returns 1
// vector->At(0);  // Returns 'a'
//
// vector->ComputeChecksum();  // Force a checksum update and gets the checksum
//
// vector->PersistToDisk();  // Persist contents to disk.

#ifndef ICING_FILE_FILE_BACKED_VECTOR_H_
#define ICING_FILE_FILE_BACKED_VECTOR_H_

#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <cinttypes>
#include <cstdint>
#include <cstring>
#include <functional>
#include <limits>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/portable/platform.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"
#include "icing/util/math-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

template <typename T>
class FileBackedVector {
 public:
  class MutableArrayView;
  class MutableView;

  // Header stored at the beginning of the file before the rest of the vector
  // elements. Stores metadata on the vector.
  struct Header {
    // Static assert constants.
    static constexpr int32_t kHeaderSize = 24;
    static constexpr int32_t kHeaderChecksumOffset = 16;

    static constexpr int32_t kMagic = 0x8bbbe237;

    // Holds the magic as quick sanity check against file corruption
    int32_t magic;

    // Byte size of each element in the vector
    int32_t element_size;

    // Number of elements currently in the vector
    int32_t num_elements;

    // Checksum of the vector elements, doesn't include the header fields.
    //
    // TODO(cassiewang): Add a checksum state that can track if the checksum is
    // fresh or stale. This lets us short circuit checksum computations if we
    // know the checksum is fresh.
    uint32_t vector_checksum;

    // Must be below all actual header content fields and above the padding
    // field. Contains the crc checksum of the preceding fields.
    uint32_t header_checksum;

    // This field has no actual meaning here but is just used as padding for the
    // struct so the size of the struct can be a multiple of 8. Doing this makes
    // the address right after the header a multiple of 8 and prevents a ubsan
    // misalign-pointer-use error (go/ubsan).
    //
    // NOTE: please remove this when adding new fields and re-assert that the
    // size is multiple of 8.
    int32_t padding_for_ptr_alignment;

    uint32_t CalculateHeaderChecksum() const {
      // Sanity check that the memory layout matches the disk layout.
      static_assert(std::is_standard_layout<FileBackedVector::Header>::value,
                    "");
      static_assert(sizeof(FileBackedVector::Header) == kHeaderSize, "");
      static_assert(
          sizeof(FileBackedVector::Header) % sizeof(void*) == 0,
          "Header has insufficient padding for void* pointer alignment");
      static_assert(offsetof(FileBackedVector::Header, header_checksum) ==
                        kHeaderChecksumOffset,
                    "");

      return Crc32(std::string_view(
                       reinterpret_cast<const char*>(this),
                       offsetof(FileBackedVector::Header, header_checksum)))
          .Get();
    }
  };

  // Absolute max file size for FileBackedVector.
  // - We memory map the whole file, so file size ~= memory size.
  // - On 32-bit platform, the virtual memory address space is 4GB. To avoid
  //   exhausting the memory, set smaller file size limit for 32-bit platform.
#ifdef ICING_ARCH_BIT_64
  static constexpr int32_t kMaxFileSize =
      std::numeric_limits<int32_t>::max();  // 2^31-1 Bytes, ~2.1 GB
#else
  static constexpr int32_t kMaxFileSize =
      (1 << 28) + Header::kHeaderSize;  // 2^28 + 12 Bytes, ~256 MiB
#endif

  // Size of element type T. The value is same as sizeof(T), while we should
  // avoid using sizeof(T) in our codebase to prevent unexpected unsigned
  // integer casting.
  static constexpr int32_t kElementTypeSize = static_cast<int32_t>(sizeof(T));
  static_assert(sizeof(T) <= (1 << 10));

  // Absolute max # of elements allowed. Since we are using int32_t to store
  // num_elements, max value is 2^31-1. Still the actual max # of elements are
  // determined by max_file_size, kMaxFileSize, kElementTypeSize, and
  // Header::kHeaderSize.
  static constexpr int32_t kMaxNumElements =
      std::numeric_limits<int32_t>::max();

  // Creates a new FileBackedVector to read/write content to.
  //
  // filesystem: Object to make system level calls
  // file_path : Specifies the file to persist the vector to; must be a path
  //             within a directory that already exists.
  // mmap_strategy : Strategy/optimizations to access the content in the vector,
  //                 see MemoryMappedFile::Strategy for more details
  // max_file_size: Maximum file size for FileBackedVector, default
  //                kMaxFileSize. Note that this value won't be written into the
  //                header, so maximum file size will always be specified in
  //                runtime and the caller should make sure the value is correct
  //                and reasonable. Also it will be cached in MemoryMappedFile
  //                member, so we can always call mmapped_file_->max_file_size()
  //                to get it.
  //                The range should be in
  //                [Header::kHeaderSize + kElementTypeSize, kMaxFileSize], and
  //                (max_file_size - Header::kHeaderSize) / kElementTypeSize is
  //                max # of elements that can be stored.
  // pre_mapping_mmap_size: pre-mapping size of MemoryMappedFile, default 0.
  //                        Pre-mapping a large memory region to the file and
  //                        grow the underlying file later, so we can avoid
  //                        remapping too frequently and reduce the cost of
  //                        system call and memory paging after remap. The user
  //                        should specify reasonable size to save remapping
  //                        cost and avoid exhausting the memory at once in the
  //                        beginning.
  //                        Note: if the file exists and pre_mapping_mmap_size
  //                        is smaller than file_size - Header::kHeaderSize,
  //                        then it still pre-maps file_size -
  //                        Header::kHeaderSize to make all existing elements
  //                        available.
  // TODO(b/247671531): figure out pre_mapping_mmap_size for each
  //                    FileBackedVector use case.
  //
  // Return:
  //   FAILED_PRECONDITION_ERROR if the file checksum doesn't match the stored
  //                             checksum.
  //   INTERNAL_ERROR on I/O errors.
  //   INVALID_ARGUMENT_ERROR if max_file_size is incorrect.
  //   UNIMPLEMENTED_ERROR if created with strategy READ_WRITE_MANUAL_SYNC.
  static libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
  Create(const Filesystem& filesystem, const std::string& file_path,
         MemoryMappedFile::Strategy mmap_strategy,
         int32_t max_file_size = kMaxFileSize,
         int32_t pre_mapping_mmap_size = 0);

  // Deletes the FileBackedVector
  //
  // filesystem: Object to make system level calls
  // file_path : Specifies the file the vector is persisted to.
  static libtextclassifier3::Status Delete(const Filesystem& filesystem,
                                           const std::string& file_path);

  // Not copyable
  FileBackedVector(const FileBackedVector&) = delete;
  FileBackedVector& operator=(const FileBackedVector&) = delete;

  // If the vector was created with
  // MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, then changes will be
  // synced by the system and the checksum will be updated.
  ~FileBackedVector();

  // Gets a copy of the element at idx.
  //
  // This is useful if you think the FileBackedVector may grow before you need
  // to access this return value. When the FileBackedVector grows, the
  // underlying mmap will be unmapped and remapped, which will invalidate any
  // pointers to the previously mapped region. Getting a copy will avoid
  // referencing the now-invalidated region.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx >= num_elements()
  libtextclassifier3::StatusOr<T> GetCopy(int32_t idx) const;

  // Gets an immutable pointer to the element at idx.
  //
  // WARNING: Subsequent calls to Set/Append/Allocate may invalidate the pointer
  // returned by Get.
  //
  // This is useful if you do not think the FileBackedVector will grow before
  // you need to reference this value, and you want to avoid a copy. When the
  // FileBackedVector grows, the underlying mmap will be unmapped and remapped,
  // which will invalidate this pointer to the previously mapped region.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx >= num_elements()
  libtextclassifier3::StatusOr<const T*> Get(int32_t idx) const;

  // Gets a MutableView to the element at idx.
  //
  // WARNING: Subsequent calls to Set/Append/Allocate may invalidate the
  // reference returned by MutableView::Get().
  //
  // This is useful if you do not think the FileBackedVector will grow before
  // you need to reference this value, and you want to mutate the underlying
  // data directly. When the FileBackedVector grows, the underlying mmap will be
  // unmapped and remapped, which will invalidate this MutableView to the
  // previously mapped region.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx >= num_elements()
  libtextclassifier3::StatusOr<MutableView> GetMutable(int32_t idx);

  // Gets a MutableArrayView to the elements at range [idx, idx + len).
  //
  // WARNING: Subsequent calls to Set/Append/Allocate may invalidate the
  // reference/pointer returned by MutableArrayView::operator[]/data().
  //
  // This is useful if you do not think the FileBackedVector will grow before
  // you need to reference this value, and you want to mutate the underlying
  // data directly. When the FileBackedVector grows, the underlying mmap will be
  // unmapped and remapped, which will invalidate this MutableArrayView to the
  // previously mapped region.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx + len > num_elements()
  libtextclassifier3::StatusOr<MutableArrayView> GetMutable(int32_t idx,
                                                            int32_t len);

  // Writes the value at idx.
  //
  // May grow the underlying file and mmapped region as needed to fit the new
  // value. If it does grow, then any pointers/references to previous values
  // returned from Get/GetMutable/Allocate may be invalidated.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx > kMaxIndex or file cannot be grown
  //                      to fit idx + 1 elements
  libtextclassifier3::Status Set(int32_t idx, const T& value);

  // Set [idx, idx + len) to a single value.
  //
  // May grow the underlying file and mmapped region as needed to fit the new
  // value. If it does grow, then any pointers/references to previous values
  // returned from Get/GetMutable/Allocate may be invalidated.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if idx < 0 or idx + len > kMaxNumElements or file
  //                      cannot be grown to fit idx + len elements
  libtextclassifier3::Status Set(int32_t idx, int32_t len, const T& value);

  // Appends the value to the end of the vector.
  //
  // May grow the underlying file and mmapped region as needed to fit the new
  // value. If it does grow, then any pointers/references to previous values
  // returned from Get/GetMutable/Allocate may be invalidated.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if file cannot be grown (i.e. reach
  //                      mmapped_file_->max_file_size())
  libtextclassifier3::Status Append(const T& value) {
    return Set(header_->num_elements, value);
  }

  // Allocates spaces with given length in the end of the vector and returns a
  // MutableArrayView to the space.
  //
  // May grow the underlying file and mmapped region as needed to fit the new
  // value. If it does grow, then any pointers/references to previous values
  // returned from Get/GetMutable/Allocate may be invalidated.
  //
  // WARNING: Subsequent calls to Set/Append/Allocate may invalidate the
  // reference/pointer returned by MutableArrayView::operator[]/data().
  //
  // This is useful if you do not think the FileBackedVector will grow before
  // you need to reference this value, and you want to allocate adjacent spaces
  // for multiple elements and mutate the underlying data directly. When the
  // FileBackedVector grows, the underlying mmap will be unmapped and remapped,
  // which will invalidate this MutableArrayView to the previously mapped
  // region.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if len <= 0 or file cannot be grown (i.e. reach
  //                      mmapped_file_->max_file_size())
  libtextclassifier3::StatusOr<MutableArrayView> Allocate(int32_t len);

  // Resizes to first len elements. The crc is cleared on truncation and will be
  // updated on destruction, or once the client calls ComputeChecksum() or
  // PersistToDisk().
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if len < 0 or len >= num_elements()
  libtextclassifier3::Status TruncateTo(int32_t new_num_elements);

  // Sorts the vector within range [begin_idx, end_idx).
  // It handles SetDirty properly for the file-backed-vector.
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if (0 <= begin_idx < end_idx <= num_elements()) does
  //                      not hold
  libtextclassifier3::Status Sort(int32_t begin_idx, int32_t end_idx);

  // Mark idx as changed iff idx < changes_end_, so later ComputeChecksum() can
  // update checksum by the cached changes without going over [0, changes_end_).
  //
  // If the buffer size exceeds kPartialCrcLimitDiv, then clear all change
  // buffers and set changes_end_ as 0, indicating that the checksum should be
  // recomputed from idx 0 (starting from the beginning). Otherwise cache the
  // change.
  void SetDirty(int32_t idx);

  // Flushes content to underlying file.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  libtextclassifier3::Status PersistToDisk();

  // Calculates and returns the disk usage in bytes. Rounds up to the nearest
  // block size.
  //
  // Returns:
  //   Disk usage on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const;

  // Returns the file size of the all the elements held in the vector. File size
  // is in bytes. This excludes the size of any internal metadata of the vector,
  // e.g. the vector's header.
  //
  // Returns:
  //   File size on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetElementsFileSize() const;

  // Updates checksum of the vector contents and returns it.
  //
  // Returns:
  //   INTERNAL_ERROR if the vector's internal state is inconsistent
  libtextclassifier3::StatusOr<Crc32> ComputeChecksum();

  // Accessors.
  const T* array() const {
    return reinterpret_cast<const T*>(mmapped_file_->region());
  }

  int32_t num_elements() const { return header_->num_elements; }

 public:
  class MutableArrayView {
   public:
    const T& operator[](int32_t idx) const { return data_[idx]; }
    T& operator[](int32_t idx) {
      SetDirty(idx);
      return data_[idx];
    }

    const T* data() const { return data_; }

    int32_t size() const { return len_; }

    // Set the mutable array slice (starting at idx) by the given element array.
    // It handles SetDirty properly for the file-backed-vector when modifying
    // elements.
    //
    // REQUIRES: arr is valid && arr_len >= 0 && idx >= 0 && idx + arr_len <=
    //           size(), otherwise the behavior is undefined.
    void SetArray(int32_t idx, const T* arr, int32_t arr_len) {
      for (int32_t i = 0; i < arr_len; ++i) {
        SetDirty(idx + i);
        data_[idx + i] = arr[i];
      }
    }

   private:
    MutableArrayView(FileBackedVector<T>* vector, T* data, int32_t len)
        : vector_(vector),
          data_(data),
          original_idx_(data - vector->array()),
          len_(len) {}

    void SetDirty(int32_t idx) { vector_->SetDirty(original_idx_ + idx); }

    // Does not own. For SetDirty only.
    FileBackedVector<T>* vector_;

    // data_ points at vector_->mutable_array()[original_idx_]
    T* data_;
    int32_t original_idx_;
    int32_t len_;

    friend class FileBackedVector;
  };

  class MutableView {
   public:
    const T& Get() const { return mutable_array_view_[0]; }
    T& Get() { return mutable_array_view_[0]; }

   private:
    MutableView(FileBackedVector<T>* vector, T* data)
        : mutable_array_view_(vector, data, 1) {}

    MutableArrayView mutable_array_view_;

    friend class FileBackedVector;
  };

 private:
  // We track partial updates to the array for crc updating. This
  // requires extra memory to keep track of original buffers but
  // allows for much faster crc re-computation. This is the frac limit
  // of byte len after which we will discard recorded changes and
  // recompute the entire crc instead.
  static constexpr int32_t kPartialCrcLimitDiv = 8;  // limit is 1/8th

  // Grow file by at least this many elements if array is growable.
  static constexpr int64_t kGrowElements = 1u << 14;  // 16K

  // Absolute max index allowed.
  static constexpr int32_t kMaxIndex = kMaxNumElements - 1;

  // Can only be created through the factory ::Create function
  explicit FileBackedVector(const Filesystem& filesystem,
                            const std::string& file_path,
                            std::unique_ptr<Header> header,
                            MemoryMappedFile&& mmapped_file);

  // Initialize a new FileBackedVector, and create the file.
  static libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
  InitializeNewFile(const Filesystem& filesystem, const std::string& file_path,
                    ScopedFd fd, MemoryMappedFile::Strategy mmap_strategy,
                    int32_t max_file_size, int32_t pre_mapping_mmap_size);

  // Initialize a FileBackedVector from an existing file.
  static libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
  InitializeExistingFile(const Filesystem& filesystem,
                         const std::string& file_path, ScopedFd fd,
                         MemoryMappedFile::Strategy mmap_strategy,
                         int32_t max_file_size, int32_t pre_mapping_mmap_size);

  // Grows the underlying file to hold at least num_elements
  //
  // Returns:
  //   OUT_OF_RANGE_ERROR if we can't grow to the specified size
  libtextclassifier3::Status GrowIfNecessary(int32_t num_elements);

  T* mutable_array() const {
    return reinterpret_cast<T*>(mmapped_file_->mutable_region());
  }

  // Cached constructor params.
  const Filesystem* const filesystem_;
  const std::string file_path_;
  std::unique_ptr<Header> header_;
  std::unique_ptr<MemoryMappedFile> mmapped_file_;

  // Offset before which all the elements have been included in the calculation
  // of crc at the time it was calculated.
  int32_t changes_end_ = 0;

  // Offset of changes that have happened since the last crc update between [0,
  // changes_end_).
  std::vector<int32_t> changes_;

  // Buffer of the original elements that have been changed since the last crc
  // update. Will be cleared if the size grows too big.
  std::string saved_original_buffer_;
};

template <typename T>
constexpr int32_t FileBackedVector<T>::kMaxFileSize;

template <typename T>
constexpr int32_t FileBackedVector<T>::kElementTypeSize;

template <typename T>
constexpr int32_t FileBackedVector<T>::kMaxNumElements;

template <typename T>
constexpr int32_t FileBackedVector<T>::kPartialCrcLimitDiv;

template <typename T>
constexpr int64_t FileBackedVector<T>::kGrowElements;

template <typename T>
constexpr int32_t FileBackedVector<T>::kMaxIndex;

template <typename T>
libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
FileBackedVector<T>::Create(const Filesystem& filesystem,
                            const std::string& file_path,
                            MemoryMappedFile::Strategy mmap_strategy,
                            int32_t max_file_size,
                            int32_t pre_mapping_mmap_size) {
  if (mmap_strategy == MemoryMappedFile::Strategy::READ_WRITE_MANUAL_SYNC) {
    // FileBackedVector's behavior of growing the file underneath the mmap is
    // inherently broken with MAP_PRIVATE. Growing the vector requires extending
    // the file size, then unmapping and then re-mmapping over the new, larger
    // file. But when we unmap, we lose all the vector's contents if they
    // weren't manually persisted. Either don't allow READ_WRITE_MANUAL_SYNC
    // vectors from growing, or make users aware of this somehow
    return absl_ports::UnimplementedError(
        "FileBackedVector currently doesn't support READ_WRITE_MANUAL_SYNC "
        "mmap strategy.");
  }

  if (max_file_size < Header::kHeaderSize + kElementTypeSize ||
      max_file_size > kMaxFileSize) {
    // FileBackedVector should be able to store at least 1 element, so
    // max_file_size should be at least Header::kHeaderSize + kElementTypeSize.
    return absl_ports::InvalidArgumentError(
        "Invalid max file size for FileBackedVector");
  }

  ScopedFd fd(filesystem.OpenForWrite(file_path.c_str()));
  if (!fd.is_valid()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to open ", file_path));
  }

  int64_t file_size = filesystem.GetFileSize(file_path.c_str());
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Bad file size for file ", file_path));
  }

  if (max_file_size < file_size) {
    return absl_ports::InvalidArgumentError(
        "Max file size should not be smaller than the existing file size");
  }

  const bool new_file = file_size == 0;
  if (new_file) {
    return InitializeNewFile(filesystem, file_path, std::move(fd),
                             mmap_strategy, max_file_size,
                             pre_mapping_mmap_size);
  }
  return InitializeExistingFile(filesystem, file_path, std::move(fd),
                                mmap_strategy, max_file_size,
                                pre_mapping_mmap_size);
}

template <typename T>
libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
FileBackedVector<T>::InitializeNewFile(const Filesystem& filesystem,
                                       const std::string& file_path,
                                       ScopedFd fd,
                                       MemoryMappedFile::Strategy mmap_strategy,
                                       int32_t max_file_size,
                                       int32_t pre_mapping_mmap_size) {
  // Create header.
  auto header = std::make_unique<Header>();
  header->magic = FileBackedVector<T>::Header::kMagic;
  header->element_size = kElementTypeSize;
  header->header_checksum = header->CalculateHeaderChecksum();

  // We use Write() here, instead of writing through the mmapped region
  // created below, so we can gracefully handle errors that occur when the
  // disk is full. See b/77309668 for details.
  if (!filesystem.PWrite(fd.get(), /*offset=*/0, header.get(),
                         Header::kHeaderSize)) {
    return absl_ports::InternalError("Failed to write header");
  }

  // Close the fd since constructor of MemoryMappedFile calls mmap() and we need
  // to flush fd before mmap().
  fd.reset();

  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem, file_path, mmap_strategy,
                               max_file_size,
                               /*pre_mapping_file_offset=*/Header::kHeaderSize,
                               /*pre_mapping_mmap_size=*/
                               std::min(max_file_size - Header::kHeaderSize,
                                        pre_mapping_mmap_size)));

  return std::unique_ptr<FileBackedVector<T>>(new FileBackedVector<T>(
      filesystem, file_path, std::move(header), std::move(mmapped_file)));
}

template <typename T>
libtextclassifier3::StatusOr<std::unique_ptr<FileBackedVector<T>>>
FileBackedVector<T>::InitializeExistingFile(
    const Filesystem& filesystem, const std::string& file_path,
    const ScopedFd fd, MemoryMappedFile::Strategy mmap_strategy,
    int32_t max_file_size, int32_t pre_mapping_mmap_size) {
  int64_t file_size = filesystem.GetFileSize(file_path.c_str());
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Bad file size for file ", file_path));
  }

  if (file_size < Header::kHeaderSize) {
    return absl_ports::InternalError(
        absl_ports::StrCat("File header too short for ", file_path));
  }

  auto header = std::make_unique<Header>();
  if (!filesystem.PRead(fd.get(), header.get(), Header::kHeaderSize,
                        /*offset=*/0)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to read header of ", file_path));
  }

  // Make sure the header is still valid before we use any of its values. This
  // should technically be included in the header_checksum check below, but this
  // is a quick/fast check that can save us from an extra crc computation.
  if (header->kMagic != FileBackedVector<T>::Header::kMagic) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Invalid header kMagic for ", file_path));
  }

  // Check header
  if (header->header_checksum != header->CalculateHeaderChecksum()) {
    return absl_ports::FailedPreconditionError(
        absl_ports::StrCat("Invalid header crc for ", file_path));
  }

  if (header->element_size != kElementTypeSize) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Inconsistent element size, expected %d, actual %d", kElementTypeSize,
        header->element_size));
  }

  int64_t min_file_size =
      static_cast<int64_t>(header->num_elements) * kElementTypeSize +
      Header::kHeaderSize;
  if (min_file_size > file_size) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Inconsistent file size, expected %" PRId64 ", actual %" PRId64,
        min_file_size, file_size));
  }

  // Mmap the content of the vector, excluding the header so its easier to
  // access elements from the mmapped region
  // Although users can specify their own pre_mapping_mmap_size, we should make
  // sure that the pre-map size is at least file_size - Header::kHeaderSize to
  // make all existing elements available.
  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(
          filesystem, file_path, mmap_strategy, max_file_size,
          /*pre_mapping_file_offset=*/Header::kHeaderSize,
          /*pre_mapping_mmap_size=*/
          std::max(
              file_size - Header::kHeaderSize,
              static_cast<int64_t>(std::min(max_file_size - Header::kHeaderSize,
                                            pre_mapping_mmap_size)))));

  // Check vector contents
  Crc32 vector_checksum(
      std::string_view(reinterpret_cast<const char*>(mmapped_file.region()),
                       header->num_elements * kElementTypeSize));

  if (vector_checksum.Get() != header->vector_checksum) {
    return absl_ports::FailedPreconditionError(
        absl_ports::StrCat("Invalid vector contents for ", file_path));
  }

  return std::unique_ptr<FileBackedVector<T>>(new FileBackedVector<T>(
      filesystem, file_path, std::move(header), std::move(mmapped_file)));
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::Delete(
    const Filesystem& filesystem, const std::string& file_path) {
  if (!filesystem.DeleteFile(file_path.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to delete file: ", file_path));
  }
  return libtextclassifier3::Status::OK;
}

template <typename T>
FileBackedVector<T>::FileBackedVector(const Filesystem& filesystem,
                                      const std::string& file_path,
                                      std::unique_ptr<Header> header,
                                      MemoryMappedFile&& mmapped_file)
    : filesystem_(&filesystem),
      file_path_(file_path),
      header_(std::move(header)),
      mmapped_file_(
          std::make_unique<MemoryMappedFile>(std::move(mmapped_file))),
      changes_end_(header_->num_elements) {}

template <typename T>
FileBackedVector<T>::~FileBackedVector() {
  if (mmapped_file_->strategy() ==
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC) {
    if (!PersistToDisk().ok()) {
      ICING_LOG(WARNING)
          << "Failed to persist vector to disk while destructing "
          << file_path_;
    }
  }
}

template <typename T>
libtextclassifier3::StatusOr<T> FileBackedVector<T>::GetCopy(
    int32_t idx) const {
  ICING_ASSIGN_OR_RETURN(const T* value, Get(idx));
  return *value;
}

template <typename T>
libtextclassifier3::StatusOr<const T*> FileBackedVector<T>::Get(
    int32_t idx) const {
  if (idx < 0) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Index, %d, was less than 0", idx));
  }

  if (idx >= header_->num_elements) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Index, %d, was greater than vector size, %d", idx,
        header_->num_elements));
  }

  return &array()[idx];
}

template <typename T>
libtextclassifier3::StatusOr<typename FileBackedVector<T>::MutableView>
FileBackedVector<T>::GetMutable(int32_t idx) {
  if (idx < 0) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Index, %d, was less than 0", idx));
  }

  if (idx >= header_->num_elements) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Index, %d, was greater than vector size, %d", idx,
        header_->num_elements));
  }

  return MutableView(this, &mutable_array()[idx]);
}

template <typename T>
libtextclassifier3::StatusOr<typename FileBackedVector<T>::MutableArrayView>
FileBackedVector<T>::GetMutable(int32_t idx, int32_t len) {
  if (idx < 0) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Index, %d, was less than 0", idx));
  }

  if (idx > header_->num_elements - len) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Index with len, %d %d, was greater than vector size, %d", idx, len,
        header_->num_elements));
  }

  return MutableArrayView(this, &mutable_array()[idx], len);
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::Set(int32_t idx,
                                                    const T& value) {
  return Set(idx, 1, value);
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::Set(int32_t idx, int32_t len,
                                                    const T& value) {
  if (idx < 0) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Index, %d, was less than 0", idx));
  }

  if (len <= 0) {
    return absl_ports::OutOfRangeError("Invalid set length");
  }

  if (idx > kMaxNumElements - len) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Length %d (with index %d), was too long "
                                      "for max num elements allowed, %d",
                                      len, idx, kMaxNumElements));
  }

  ICING_RETURN_IF_ERROR(GrowIfNecessary(idx + len));

  if (idx + len > header_->num_elements) {
    header_->num_elements = idx + len;
  }

  for (int32_t i = 0; i < len; ++i) {
    if (array()[idx + i] == value) {
      // No need to update
      continue;
    }

    SetDirty(idx + i);
    mutable_array()[idx + i] = value;
  }

  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::StatusOr<typename FileBackedVector<T>::MutableArrayView>
FileBackedVector<T>::Allocate(int32_t len) {
  if (len <= 0) {
    return absl_ports::OutOfRangeError("Invalid allocate length");
  }

  if (len > kMaxNumElements - header_->num_elements) {
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Cannot allocate %d elements", len));
  }

  // Although header_->num_elements + len doesn't exceed kMaxNumElements, the
  // actual max # of elements are determined by mmapped_file_->max_file_size(),
  // kElementTypeSize, and kHeaderSize. Thus, it is still possible to fail to
  // grow the file.
  ICING_RETURN_IF_ERROR(GrowIfNecessary(header_->num_elements + len));

  int32_t start_idx = header_->num_elements;
  header_->num_elements += len;

  return MutableArrayView(this, &mutable_array()[start_idx], len);
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::GrowIfNecessary(
    int32_t num_elements) {
  if (kElementTypeSize == 0) {
    // Growing is a no-op
    return libtextclassifier3::Status::OK;
  }

  if (num_elements <= header_->num_elements) {
    return libtextclassifier3::Status::OK;
  }

  if (num_elements > (mmapped_file_->max_file_size() - Header::kHeaderSize) /
                         kElementTypeSize) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "%d elements total size exceed maximum bytes of elements allowed, "
        "%" PRId64 " bytes",
        num_elements, mmapped_file_->max_file_size() - Header::kHeaderSize));
  }

  int32_t least_file_size_needed =
      Header::kHeaderSize + num_elements * kElementTypeSize;  // Won't overflow
  if (least_file_size_needed <= mmapped_file_->available_size()) {
    return libtextclassifier3::Status::OK;
  }

  int64_t round_up_file_size_needed = math_util::RoundUpTo(
      int64_t{least_file_size_needed},
      int64_t{FileBackedVector<T>::kGrowElements} * kElementTypeSize);

  // Call GrowAndRemapIfNecessary. It handles file growth internally and remaps
  // intelligently.
  // We've ensured that least_file_size_needed (for num_elements) doesn't exceed
  // mmapped_file_->max_file_size(), but it is still possible that
  // round_up_file_size_needed exceeds it, so use the smaller value of them as
  // new_mmap_size.
  ICING_RETURN_IF_ERROR(mmapped_file_->GrowAndRemapIfNecessary(
      /*new_file_offset=*/Header::kHeaderSize,
      /*new_mmap_size=*/std::min(round_up_file_size_needed,
                                 mmapped_file_->max_file_size()) -
          Header::kHeaderSize));

  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::TruncateTo(
    int32_t new_num_elements) {
  if (new_num_elements < 0) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Truncated length %d must be >= 0", new_num_elements));
  }

  if (new_num_elements >= header_->num_elements) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Truncated length %d must be less than the current size %d",
        new_num_elements, header_->num_elements));
  }

  ICING_VLOG(2)
      << "FileBackedVector truncating, need to recalculate entire checksum";
  changes_.clear();
  saved_original_buffer_.clear();
  changes_end_ = 0;
  header_->vector_checksum = 0;

  header_->num_elements = new_num_elements;
  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::Sort(int32_t begin_idx,
                                                     int32_t end_idx) {
  if (begin_idx < 0 || begin_idx >= end_idx ||
      end_idx > header_->num_elements) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Invalid sort index, %d, %d", begin_idx, end_idx));
  }
  for (int32_t i = begin_idx; i < end_idx; ++i) {
    SetDirty(i);
  }
  std::sort(mutable_array() + begin_idx, mutable_array() + end_idx);
  return libtextclassifier3::Status::OK;
}

template <typename T>
void FileBackedVector<T>::SetDirty(int32_t idx) {
  // Cache original value to update crcs.
  if (idx >= 0 && idx < changes_end_) {
    // If we exceed kPartialCrcLimitDiv, clear changes_end_ to
    // revert to full CRC.
    if ((saved_original_buffer_.size() + kElementTypeSize) *
            FileBackedVector<T>::kPartialCrcLimitDiv >
        changes_end_ * kElementTypeSize) {
      ICING_VLOG(2) << "FileBackedVector change tracking limit exceeded";
      changes_.clear();
      saved_original_buffer_.clear();
      changes_end_ = 0;
      header_->vector_checksum = 0;
    } else {
      int32_t start_byte = idx * kElementTypeSize;

      changes_.push_back(idx);
      saved_original_buffer_.append(
          reinterpret_cast<char*>(const_cast<T*>(array())) + start_byte,
          kElementTypeSize);
    }
  }
}

template <typename T>
libtextclassifier3::StatusOr<Crc32> FileBackedVector<T>::ComputeChecksum() {
  // First apply the modified area. Keep a bitmap of already updated
  // regions so we don't double-update.
  std::vector<bool> updated(changes_end_);
  uint32_t cur_offset = 0;
  Crc32 cur_crc(header_->vector_checksum);
  int num_partial_crcs = 0;
  int num_truncated = 0;
  int num_overlapped = 0;
  int num_duplicate = 0;
  for (const int32_t change_offset : changes_) {
    if (change_offset > changes_end_) {
      return absl_ports::InternalError(IcingStringUtil::StringPrintf(
          "Failed to update crc, change offset %d, changes_end_ %d",
          change_offset, changes_end_));
    }

    // Skip truncated tracked changes.
    if (change_offset >= header_->num_elements) {
      ++num_truncated;
      continue;
    }

    // Turn change buffer into change^original.
    const char* buffer_end =
        &saved_original_buffer_[cur_offset + kElementTypeSize];
    const char* cur_array = reinterpret_cast<const char*>(array()) +
                            change_offset * kElementTypeSize;
    // Now xor in. SSE acceleration please?
    for (char* cur = &saved_original_buffer_[cur_offset]; cur < buffer_end;
         cur++, cur_array++) {
      *cur ^= *cur_array;
    }

    // Skip over already updated bytes by setting update to 0.
    bool new_update = false;
    bool overlap = false;
    uint32_t cur_element = change_offset;
    for (char* cur = &saved_original_buffer_[cur_offset]; cur < buffer_end;
         cur_element++, cur += kElementTypeSize) {
      if (updated[cur_element]) {
        memset(cur, 0, kElementTypeSize);
        overlap = true;
      } else {
        updated[cur_element] = true;
        new_update = true;
      }
    }

    // Apply update to crc.
    if (new_update) {
      // Explicitly create the string_view with length
      std::string_view xored_str(buffer_end - kElementTypeSize,
                                 kElementTypeSize);
      if (!cur_crc
               .UpdateWithXor(xored_str, changes_end_ * kElementTypeSize,
                              change_offset * kElementTypeSize)
               .ok()) {
        return absl_ports::InternalError(IcingStringUtil::StringPrintf(
            "Failed to update crc, change offset %d, change "
            "length %zd changes_end_ %d",
            change_offset, xored_str.length(), changes_end_));
      }
      num_partial_crcs++;
      if (overlap) {
        num_overlapped++;
      }
    } else {
      num_duplicate++;
    }
    cur_offset += kElementTypeSize;
  }

  if (!changes_.empty()) {
    ICING_VLOG(2) << IcingStringUtil::StringPrintf(
        "Array update partial crcs %d truncated %d overlapped %d duplicate %d",
        num_partial_crcs, num_truncated, num_overlapped, num_duplicate);
  }

  // Now update with grown area.
  if (changes_end_ < header_->num_elements) {
    // Explicitly create the string_view with length
    std::string_view update_str(
        reinterpret_cast<const char*>(array()) +
            changes_end_ * kElementTypeSize,
        (header_->num_elements - changes_end_) * kElementTypeSize);
    cur_crc.Append(update_str);
    ICING_VLOG(2) << IcingStringUtil::StringPrintf(
        "Array update tail crc offset %d -> %d", changes_end_,
        header_->num_elements);
  }

  // Clear, now that we've applied changes.
  changes_.clear();
  saved_original_buffer_.clear();
  changes_end_ = header_->num_elements;

  // Commit new crc.
  header_->vector_checksum = cur_crc.Get();
  return cur_crc;
}

template <typename T>
libtextclassifier3::Status FileBackedVector<T>::PersistToDisk() {
  // Update and write the header
  ICING_ASSIGN_OR_RETURN(Crc32 checksum, ComputeChecksum());
  header_->vector_checksum = checksum.Get();
  header_->header_checksum = header_->CalculateHeaderChecksum();

  if (!filesystem_->PWrite(file_path_.c_str(), /*offset=*/0, header_.get(),
                           Header::kHeaderSize)) {
    return absl_ports::InternalError("Failed to sync header");
  }

  MemoryMappedFile::Strategy strategy = mmapped_file_->strategy();

  if (strategy == MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC) {
    // Changes should have been applied to the underlying file, but call msync()
    // as an extra safety step to ensure they are written out.
    ICING_RETURN_IF_ERROR(mmapped_file_->PersistToDisk());
  }

  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::StatusOr<int64_t> FileBackedVector<T>::GetDiskUsage()
    const {
  int64_t size = filesystem_->GetDiskUsage(file_path_.c_str());
  if (size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        "Failed to get disk usage of file-backed vector");
  }
  return size;
}

template <typename T>
libtextclassifier3::StatusOr<int64_t> FileBackedVector<T>::GetElementsFileSize()
    const {
  int64_t total_file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (total_file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        "Failed to get file size of elements in the file-backed vector");
  }
  if (total_file_size < Header::kHeaderSize) {
    return absl_ports::InternalError(
        "File size should not be smaller than header size");
  }
  return total_file_size - Header::kHeaderSize;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_FILE_BACKED_VECTOR_H_
