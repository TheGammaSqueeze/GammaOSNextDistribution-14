// Copyright (C) 2023 Google LLC
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

#ifndef ICING_FILE_PERSISTENT_STORAGE_H_
#define ICING_FILE_PERSISTENT_STORAGE_H_

#include <cstdint>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// PersistentStorage: an abstract class for all persistent data structures.
// - It provides some common persistent file methods, e.g. PersistToDisk.
// - It encapsulates most of the checksum handling logics (including update and
//   validation).
//
// Terminology:
// - Crcs: checksum section
// - Info: (custom) information for derived class
// - Metadata: Crcs + Info
//
// Usually a persistent data structure will have its own custom Info and
// storages (single or composite storages) definition. To create a new
// persistent data structure via PersistentStorage:
// - Decide what type the working path is (single file or directory). See
//   working_path_ and WorkingPathType for more details.
// - Create a new class that inherits PersistentStorage:
//   - Declare custom Info and design the metadata section layout.
//     Usually the layout is <Crcs><Info>, and there are 2 common ways to
//     manage metadata section:
//     - Have a separate file for metadata. In this case, the new persistent
//       data structure contains multiple files, so working path should be used
//       as directory path and multiple files will be stored under it. Example:
//       PersistentHashMap.
//     - Have a single file for both metadata and storage data. In this case,
//       the file layout should be <Crcs><Info><Storage Data>, and
//       working path should be used as file path. Example: FileBackedVector.
//   - Handle working path file/directory creation and deletion.
//     PersistentStorage only provides static Discard() method to use. The
//     derived class should implement other logics, e.g. working path (file
//     /directory) creation, check condition to discard working path and start
//     over new file(s).
//   - Implement all pure virtual methods:
//     - PersistStoragesToDisk: persist all (composite) storages. In general,
//       the implementation will be calling PersistToDisk for all composite
//       storages.
//     - PersistMetadataToDisk: persist metadata, including Crcs and Info.
//       - If the derived class maintains a concrete Crc and (custom) Info
//         instance, then it should perform write/pwrite into the metadata
//         section.
//       - If the derived class uses memory-mapped region directly for metadata,
//         then it should call MemoryMappedFile::PersistToDisk.
//       - See crcs() for more details.
//     - ComputeInfoChecksum: compute the checksum for custom Info.
//     - ComputeStoragesChecksum: compute the (combined) checksum for all
//       (composite) storages. In general, the implementation will be calling
//       UpdateChecksums for all composite storages and XOR all checksums.
//     - crcs(): provide the reference for PersistentStorage to write checksums.
//       The derived class can either maintain a concrete Crcs instance, or
//       reinterpret_cast the memory-mapped region to Crcs reference. Either
//       choice is fine as long as PersistMetadataToDisk flushes it to disk
//       correctly.
// - Call either InitializeNewStorage or InitializeExistingStorage when creating
//   and initializing an instance, depending on initializing new storage or from
//   existing file(s).
class PersistentStorage {
 public:
  enum class WorkingPathType {
    kSingleFile,
    kDirectory,
    kDummy,
  };

  // Crcs and Info will be written into the metadata section. Info is defined by
  // the actual implementation of each persistent storage. Usually the Metadata
  // layout is: <Crcs><Info>
  struct Crcs {
    struct ComponentCrcs {
      uint32_t info_crc;
      uint32_t storages_crc;

      bool operator==(const ComponentCrcs& other) const {
        return info_crc == other.info_crc && storages_crc == other.storages_crc;
      }

      Crc32 ComputeChecksum() const {
        return Crc32(std::string_view(reinterpret_cast<const char*>(this),
                                      sizeof(ComponentCrcs)));
      }
    } __attribute__((packed));

    bool operator==(const Crcs& other) const {
      return all_crc == other.all_crc && component_crcs == other.component_crcs;
    }

    uint32_t all_crc;
    ComponentCrcs component_crcs;
  } __attribute__((packed));
  static_assert(sizeof(Crcs) == 12, "");

  // Deletes working_path according to its type.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  //   - INVALID_ARGUMENT_ERROR if working_path_type is unknown type
  static libtextclassifier3::Status Discard(const Filesystem& filesystem,
                                            const std::string& working_path,
                                            WorkingPathType working_path_type);

  virtual ~PersistentStorage() = default;

  // Initializes new persistent storage. It computes the initial checksums and
  // writes into the metadata file.
  //
  // Note: either InitializeNewStorage or InitializeExistingStorage should be
  // invoked after creating a PersistentStorage instance before using, otherwise
  // an uninitialized instance will fail to use persistent storage features,
  // e.g. PersistToDisk, UpdateChecksums.
  //
  // Returns:
  //   - OK on success or already initialized
  //   - Any errors from ComputeInfoChecksum, ComputeStoragesChecksum, depending
  //     on actual implementation
  libtextclassifier3::Status InitializeNewStorage() {
    if (is_initialized_) {
      return libtextclassifier3::Status::OK;
    }

    ICING_RETURN_IF_ERROR(UpdateChecksumsInternal(/*force=*/true));
    ICING_RETURN_IF_ERROR(PersistStoragesToDisk(/*force=*/true));
    ICING_RETURN_IF_ERROR(PersistMetadataToDisk(/*force=*/true));

    is_initialized_ = true;
    return libtextclassifier3::Status::OK;
  }

  // Initializes persistent storage from existing file(s).
  //
  // It enforces the following check(s):
  // - Validate checksums.
  //
  // Note: either InitializeNewStorage or InitializeExistingStorage should be
  // invoked after creating a PersistentStorage instance before using.
  //
  // Returns:
  //   - OK on success or already initialized
  //   - FAILED_PRECONDITION_ERROR if checksum validation fails.
  //   - Any errors from ComputeInfoChecksum, ComputeStoragesChecksum, depending
  //     on actual implementation
  libtextclassifier3::Status InitializeExistingStorage() {
    if (is_initialized_) {
      return libtextclassifier3::Status::OK;
    }

    ICING_RETURN_IF_ERROR(ValidateChecksums());

    is_initialized_ = true;
    return libtextclassifier3::Status::OK;
  }

  // Flushes contents to underlying files.
  // 1) Flushes storages.
  // 2) Updates all checksums by new data.
  // 3) Flushes metadata.
  //
  // Force flag will be passed down to PersistMetadataToDisk,
  // PersistStoragesToDisk, ComputeInfoChecksum, ComputeStoragesChecksum.
  // - If force == true, then performs actual persisting operations/recomputes
  //   the checksum.
  // - Otherwise, the derived class can decide itself whether skipping
  //   persisting operations/doing lazy checksum recomputing if the storage is
  //   not dirty.
  //
  // Returns:
  //   - OK on success
  //   - FAILED_PRECONDITION_ERROR if PersistentStorage is uninitialized
  //   - Any errors from PersistStoragesToDisk, UpdateChecksums,
  //     PersistMetadataToDisk, depending on actual implementation
  libtextclassifier3::Status PersistToDisk(bool force = false) {
    if (!is_initialized_) {
      return absl_ports::FailedPreconditionError(absl_ports::StrCat(
          "PersistentStorage ", working_path_, " not initialized"));
    }

    ICING_RETURN_IF_ERROR(UpdateChecksumsInternal(force));
    ICING_RETURN_IF_ERROR(PersistStoragesToDisk(force));
    ICING_RETURN_IF_ERROR(PersistMetadataToDisk(force));
    return libtextclassifier3::Status::OK;
  }

  // Updates checksums of all components and returns the overall crc (all_crc)
  // of the persistent storage.
  //
  // Force flag will be passed down ComputeInfoChecksum,
  // ComputeStoragesChecksum.
  // - If force == true, then recomputes the checksum.
  // - Otherwise, the derived class can decide itself whether doing lazy
  //   checksum recomputing if the storage is not dirty.
  //
  // Returns:
  //   - Overall crc of the persistent storage on success
  //   - FAILED_PRECONDITION_ERROR if PersistentStorage is uninitialized
  //   - Any errors from ComputeInfoChecksum, ComputeStoragesChecksum, depending
  //     on actual implementation
  libtextclassifier3::StatusOr<Crc32> UpdateChecksums(bool force = false) {
    if (!is_initialized_) {
      return absl_ports::FailedPreconditionError(absl_ports::StrCat(
          "PersistentStorage ", working_path_, " not initialized"));
    }

    return UpdateChecksumsInternal(force);
  }

 protected:
  explicit PersistentStorage(const Filesystem& filesystem,
                             std::string working_path,
                             WorkingPathType working_path_type)
      : filesystem_(filesystem),
        working_path_(std::move(working_path)),
        working_path_type_(working_path_type),
        is_initialized_(false) {}

  // Flushes contents of metadata. The implementation should flush Crcs and Info
  // correctly, depending on whether they're using memory-mapped regions or
  // concrete instances in the derived class.
  //
  // Returns:
  //   - OK on success
  //   - Any other errors, depending on actual implementation
  virtual libtextclassifier3::Status PersistMetadataToDisk(bool force) = 0;

  // Flushes contents of all storages to underlying files.
  //
  // Returns:
  //   - OK on success
  //   - Any other errors, depending on actual implementation
  virtual libtextclassifier3::Status PersistStoragesToDisk(bool force) = 0;

  // Computes and returns Info checksum.
  // - If force = true, then recompute the entire checksum.
  // - Otherwise, the derived class can decide itself whether doing lazy
  //   checksum computing if the storage is not dirty.
  //
  // This function will be mainly called by UpdateChecksums.
  //
  // Returns:
  //   - Crc of the Info on success
  //   - Any other errors, depending on actual implementation
  virtual libtextclassifier3::StatusOr<Crc32> ComputeInfoChecksum(
      bool force) = 0;

  // Computes and returns all storages checksum. If there are multiple storages,
  // usually we XOR their checksums together to a single checksum.
  // - If force = true, then recompute the entire checksum.
  // - Otherwise, the derived class can decide itself whether doing lazy
  //   checksum computing if the storage is not dirty.
  //
  // This function will be mainly called by UpdateChecksums.
  //
  // Returns:
  //   - Crc of all storages on success
  //   - Any other errors from depending on actual implementation
  virtual libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) = 0;

  // Returns the Crcs instance reference. The derived class can either own a
  // concrete Crcs instance, or reinterpret_cast the memory-mapped region to
  // Crcs reference. PersistMetadataToDisk should flush it to disk correctly.
  virtual Crcs& crcs() = 0;
  virtual const Crcs& crcs() const = 0;

  const Filesystem& filesystem_;  // Does not own
  // Path to the storage. It can be a single file path or a directory path
  // depending on the implementation of the derived class.
  //
  // Note that the derived storage class will take full ownership and of
  // working_path_, including creation/deletion. It is the caller's
  // responsibility to specify correct working path and avoid mixing different
  // persistent storages together under the same path. Also the caller has the
  // ownership for the parent directory of working_path_, and it is responsible
  // for parent directory creation/deletion.
  std::string working_path_;
  WorkingPathType working_path_type_;

  bool is_initialized_;

 private:
  // Updates checksums of all components and returns the overall crc (all_crc)
  // of the persistent storage. Different from UpdateChecksums, it won't check
  // if PersistentStorage is initialized or not.
  //
  // Returns:
  //   - Overall crc of the persistent storage on success
  //   - Any errors from ComputeInfoChecksum, ComputeStoragesChecksum, depending
  //     on actual implementation
  libtextclassifier3::StatusOr<Crc32> UpdateChecksumsInternal(bool force) {
    Crcs& crcs_ref = crcs();
    // Compute and update storages + info checksums.
    ICING_ASSIGN_OR_RETURN(Crc32 info_crc, ComputeInfoChecksum(force));
    ICING_ASSIGN_OR_RETURN(Crc32 storages_crc, ComputeStoragesChecksum(force));
    if (crcs_ref.component_crcs.info_crc == info_crc.Get() &&
        crcs_ref.component_crcs.storages_crc == storages_crc.Get()) {
      // If info and storages crc haven't changed, then we don't have to update
      // checksums.
      return Crc32(crcs_ref.all_crc);
    }

    crcs_ref.component_crcs.info_crc = info_crc.Get();
    crcs_ref.component_crcs.storages_crc = storages_crc.Get();

    // Finally compute and update overall checksum.
    crcs_ref.all_crc = crcs_ref.component_crcs.ComputeChecksum().Get();
    return Crc32(crcs_ref.all_crc);
  }

  // Validates all checksums of the persistent storage.
  //
  // Returns:
  //   - OK on success
  //   - FAILED_PRECONDITION_ERROR if any checksum is incorrect.
  //   - Any errors from ComputeInfoChecksum, ComputeStoragesChecksum, depending
  //     on actual implementation
  libtextclassifier3::Status ValidateChecksums() {
    const Crcs& crcs_ref = crcs();
    if (crcs_ref.all_crc != crcs_ref.component_crcs.ComputeChecksum().Get()) {
      return absl_ports::FailedPreconditionError("Invalid all crc");
    }

    ICING_ASSIGN_OR_RETURN(Crc32 info_crc, ComputeInfoChecksum(/*force=*/true));
    if (crcs_ref.component_crcs.info_crc != info_crc.Get()) {
      return absl_ports::FailedPreconditionError("Invalid info crc");
    }

    ICING_ASSIGN_OR_RETURN(Crc32 storages_crc,
                           ComputeStoragesChecksum(/*force=*/true));
    if (crcs_ref.component_crcs.storages_crc != storages_crc.Get()) {
      return absl_ports::FailedPreconditionError("Invalid storages crc");
    }

    return libtextclassifier3::Status::OK;
  }
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_PERSISTENT_STORAGE_H_
