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

#ifndef ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_H_
#define ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-storage.h"
#include "icing/join/doc-join-info.h"
#include "icing/join/document-id-to-join-info.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// QualifiedIdJoinIndex: an abstract class to maintain data for qualified id
// joining.
class QualifiedIdJoinIndex : public PersistentStorage {
 public:
  class JoinDataIteratorBase {
   public:
    virtual ~JoinDataIteratorBase() = default;

    virtual libtextclassifier3::Status Advance() = 0;

    virtual const DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>&
    GetCurrent() const = 0;
  };

  static constexpr WorkingPathType kWorkingPathType =
      WorkingPathType::kDirectory;

  // Deletes QualifiedIdJoinIndex under working_path.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Discard(const Filesystem& filesystem,
                                            const std::string& working_path) {
    return PersistentStorage::Discard(filesystem, working_path,
                                      kWorkingPathType);
  }

  virtual ~QualifiedIdJoinIndex() override = default;

  // (v1 only) Puts a new data into index: DocJoinInfo (DocumentId,
  // JoinablePropertyId) references to ref_qualified_id_str (the identifier of
  // another document).
  //
  // REQUIRES: ref_qualified_id_str contains no '\0'.
  //
  // Returns:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR if doc_join_info is invalid
  //   - Any KeyMapper errors
  virtual libtextclassifier3::Status Put(
      const DocJoinInfo& doc_join_info,
      std::string_view ref_qualified_id_str) = 0;

  // (v2 only) Puts a list of referenced NamespaceFingerprintIdentifier into
  // index, given the DocumentId, SchemaTypeId and JoinablePropertyId.
  //
  // Returns:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR if schema_type_id, joinable_property_id, or
  //     document_id is invalid
  //   - Any KeyMapper/FlashIndexStorage errors
  virtual libtextclassifier3::Status Put(
      SchemaTypeId schema_type_id, JoinablePropertyId joinable_property_id,
      DocumentId document_id,
      std::vector<NamespaceFingerprintIdentifier>&&
          ref_namespace_fingerprint_ids) = 0;

  // (v1 only) Gets the referenced document's qualified id string by
  // DocJoinInfo.
  //
  // Returns:
  //   - A qualified id string referenced by the given DocJoinInfo (DocumentId,
  //     JoinablePropertyId) on success
  //   - INVALID_ARGUMENT_ERROR if doc_join_info is invalid
  //   - NOT_FOUND_ERROR if doc_join_info doesn't exist
  //   - Any KeyMapper errors
  virtual libtextclassifier3::StatusOr<std::string_view> Get(
      const DocJoinInfo& doc_join_info) const = 0;

  // (v2 only) Returns a JoinDataIterator for iterating through all join data of
  // the specified (schema_type_id, joinable_property_id).
  //
  // Returns:
  //   - On success: a JoinDataIterator
  //   - INVALID_ARGUMENT_ERROR if schema_type_id or joinable_property_id is
  //     invalid
  //   - Any KeyMapper/FlashIndexStorage errors
  virtual libtextclassifier3::StatusOr<std::unique_ptr<JoinDataIteratorBase>>
  GetIterator(SchemaTypeId schema_type_id,
              JoinablePropertyId joinable_property_id) const = 0;

  // Reduces internal file sizes by reclaiming space and ids of deleted
  // documents. Qualified id type joinable index will convert all entries to the
  // new document ids.
  //
  // - document_id_old_to_new: a map for converting old document id to new
  //   document id.
  // - namespace_id_old_to_new: a map for converting old namespace id to new
  //   namespace id.
  // - new_last_added_document_id: will be used to update the last added
  //                               document id in the qualified id type joinable
  //                               index.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error. This could potentially leave the index in
  //     an invalid state and the caller should handle it properly (e.g. discard
  //     and rebuild)
  virtual libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new,
      const std::vector<NamespaceId>& namespace_id_old_to_new,
      DocumentId new_last_added_document_id) = 0;

  // Clears all data and set last_added_document_id to kInvalidDocumentId.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  virtual libtextclassifier3::Status Clear() = 0;

  virtual bool is_v2() const = 0;

  virtual int32_t size() const = 0;

  virtual bool empty() const = 0;

  virtual DocumentId last_added_document_id() const = 0;

  virtual void set_last_added_document_id(DocumentId document_id) = 0;

 protected:
  explicit QualifiedIdJoinIndex(const Filesystem& filesystem,
                                std::string&& working_path)
      : PersistentStorage(filesystem, std::move(working_path),
                          kWorkingPathType) {}

  virtual libtextclassifier3::Status PersistStoragesToDisk(
      bool force) override = 0;

  virtual libtextclassifier3::Status PersistMetadataToDisk(
      bool force) override = 0;

  virtual libtextclassifier3::StatusOr<Crc32> ComputeInfoChecksum(
      bool force) override = 0;

  virtual libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override = 0;

  virtual Crcs& crcs() override = 0;
  virtual const Crcs& crcs() const override = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_H_
