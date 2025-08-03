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

#include <cstdint>

#include "icing/file/file-backed-vector.h"
#include "icing/proto/usage.pb.h"
#include "icing/store/document-id.h"

#ifndef ICING_STORE_USAGE_STORE_H_
#define ICING_STORE_USAGE_STORE_H_

namespace icing {
namespace lib {

// A storage class that maintains scores that are calculated based on usage
// reports.
class UsageStore {
 public:
  // Factory function to create a UsageStore instance. The base directory is
  // used to persist usage scores. If a usage store was previously created with
  // this directory, it will reload the files saved by the last instance.
  //
  // TODO(b/169594617): consider returning StatusOr<UsageStore>
  //
  // Returns:
  //   A UsageStore on success
  //   FAILED_PRECONDITION on any null pointer input
  //   INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<std::unique_ptr<UsageStore>> Create(
      const Filesystem* filesystem, const std::string& base_dir);

  // The scores here reflect the timestamps and usage types defined in
  // usage.proto.
  struct UsageScores {
    // The latest timestamp in seconds reported with custom usage type 1.
    uint32_t usage_type1_last_used_timestamp_s = 0;

    // The latest timestamp in seconds reported with custom usage type 2.
    uint32_t usage_type2_last_used_timestamp_s = 0;

    // The latest timestamp in seconds reported with custom usage type 3.
    uint32_t usage_type3_last_used_timestamp_s = 0;

    // Count of reports with custom usage type 1
    int usage_type1_count = 0;

    // Count of reports with custom usage type 2
    int usage_type2_count = 0;

    // Count of reports with custom usage type 3
    int usage_type3_count = 0;

    bool operator==(const UsageScores& other) const {
      return usage_type1_last_used_timestamp_s ==
                 other.usage_type1_last_used_timestamp_s &&
             usage_type2_last_used_timestamp_s ==
                 other.usage_type2_last_used_timestamp_s &&
             usage_type3_last_used_timestamp_s ==
                 other.usage_type3_last_used_timestamp_s &&
             usage_type1_count == other.usage_type1_count &&
             usage_type2_count == other.usage_type2_count &&
             usage_type3_count == other.usage_type3_count;
    }
  };

  // Adds one usage report. The corresponding usage scores of the specified
  // document will be updated.
  //
  // Note: changes are written to disk automatically, callers can also call
  // PersistToDisk() to flush changes immediately.
  //
  // Returns:
  //   OK on success
  //   INVALID_ARGUMENT if document_id is invalid
  //   INTERNAL_ERROR on I/O errors.
  libtextclassifier3::Status AddUsageReport(const UsageReport& report,
                                            DocumentId document_id);

  // Deletes the usage scores of a document.
  //
  // Note: changes are written to disk automatically, callers can also call
  // PersistToDisk() to flush changes immediately.
  //
  // Returns:
  //   OK on success
  //   INVALID_ARGUMENT if document_id is invalid
  //   INTERNAL_ERROR on I/O errors
  libtextclassifier3::Status DeleteUsageScores(DocumentId document_id);

  // Gets the usage scores of a document.
  //
  // Returns:
  //   UsageScores on success
  //   INVALID_ARGUMENT if document_id is invalid
  //   INTERNAL_ERROR on I/O errors
  //
  // TODO(b/169433395): return a pointer instead of an object.
  libtextclassifier3::StatusOr<UsageScores> GetUsageScores(
      DocumentId document_id);

  // Sets the usage scores of a document.
  //
  // Note: changes are written to disk automatically, callers can also call
  // PersistToDisk() to flush changes immediately.
  //
  // Returns:
  //   OK on success
  //   INVALID_ARGUMENT if document_id is invalid
  //   INTERNAL_ERROR on I/O errors
  libtextclassifier3::Status SetUsageScores(DocumentId document_id,
                                            const UsageScores& usage_scores);

  // Clones the usage scores from one document to another.
  //
  // Returns:
  //   OK on success
  //   INVALID_ARGUMENT if any of the document ids is invalid
  //   INTERNAL_ERROR on I/O errors
  //
  // TODO(b/169433395): We can remove this method once GetUsageScores() returns
  // a pointer.
  libtextclassifier3::Status CloneUsageScores(DocumentId from_document_id,
                                              DocumentId to_document_id);

  // Syncs data to disk.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  libtextclassifier3::Status PersistToDisk();

  // Updates checksum of the usage scores and returns it.
  //
  // Returns:
  //   A Crc32 on success
  //   INTERNAL_ERROR if the internal state is inconsistent
  libtextclassifier3::StatusOr<Crc32> ComputeChecksum();

  // Returns the file size of the all the elements held in the UsageStore. File
  // size is in bytes. This excludes the size of any internal metadata, e.g. any
  // internal headers.
  //
  // Returns:
  //   File size on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetElementsFileSize() const;

  // Calculates and returns the disk usage in bytes. Rounds up to the nearest
  // block size.
  //
  // Returns:
  //   Disk usage on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const;

  // Resizes the storage so that only the usage scores of and before
  // last_document_id are stored.
  //
  // Returns:
  //   OK on success
  //   OUT_OF_RANGE_ERROR if num_documents is negative
  libtextclassifier3::Status TruncateTo(DocumentId num_documents);

  // Deletes all usage data and re-initialize the storage.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::Status Reset();

  int32_t num_elements() const { return usage_score_cache_->num_elements(); }

 private:
  explicit UsageStore(std::unique_ptr<FileBackedVector<UsageScores>>
                          document_id_to_scores_mapper,
                      const Filesystem& filesystem, std::string base_dir)
      : filesystem_(filesystem),
        base_dir_(std::move(base_dir)),
        usage_score_cache_(std::move(document_id_to_scores_mapper)) {}

  const Filesystem& filesystem_;

  // Base directory where the files are located.
  const std::string base_dir_;

  // Used to store the usage scores of documents.
  std::unique_ptr<FileBackedVector<UsageScores>> usage_score_cache_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_USAGE_STORE_H_
