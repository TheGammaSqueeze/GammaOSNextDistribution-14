// Copyright (C) 2022 Google LLC
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

#ifndef ICING_INDEX_NUMERIC_DUMMY_NUMERIC_INDEX_H_
#define ICING_INDEX_NUMERIC_DUMMY_NUMERIC_INDEX_H_

#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <queue>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-storage.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/doc-hit-info-iterator-numeric.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// DummyNumericIndex: dummy class to help with testing and unblock e2e
// integration for numeric search. It stores all numeric index data (keys and
// hits) in memory without actual persistent storages. All PersistentStorage
// features do not work as expected, i.e. they don't persist any data into disk
// and therefore data are volatile.
template <typename T>
class DummyNumericIndex : public NumericIndex<T> {
 public:
  static libtextclassifier3::StatusOr<std::unique_ptr<DummyNumericIndex<T>>>
  Create(const Filesystem& filesystem, std::string working_path) {
    auto dummy_numeric_index = std::unique_ptr<DummyNumericIndex<T>>(
        new DummyNumericIndex<T>(filesystem, std::move(working_path)));
    ICING_RETURN_IF_ERROR(dummy_numeric_index->InitializeNewStorage());
    return dummy_numeric_index;
  }

  ~DummyNumericIndex() override = default;

  std::unique_ptr<typename NumericIndex<T>::Editor> Edit(
      std::string_view property_path, DocumentId document_id,
      SectionId section_id) override {
    return std::make_unique<Editor>(property_path, document_id, section_id,
                                    storage_);
  }

  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>> GetIterator(
      std::string_view property_path, T key_lower, T key_upper,
      const DocumentStore&, const SchemaStore&, int64_t) const override;

  libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new,
      DocumentId new_last_added_document_id) override;

  libtextclassifier3::Status Clear() override {
    storage_.clear();
    last_added_document_id_ = kInvalidDocumentId;
    return libtextclassifier3::Status::OK;
  }

  DocumentId last_added_document_id() const override {
    return last_added_document_id_;
  }

  void set_last_added_document_id(DocumentId document_id) override {
    if (last_added_document_id_ == kInvalidDocumentId ||
        document_id > last_added_document_id_) {
      last_added_document_id_ = document_id;
    }
  }

  int num_property_indices() const override { return storage_.size(); }

 private:
  class Editor : public NumericIndex<T>::Editor {
   public:
    explicit Editor(
        std::string_view property_path, DocumentId document_id,
        SectionId section_id,
        std::unordered_map<std::string, std::map<T, std::vector<BasicHit>>>&
            storage)
        : NumericIndex<T>::Editor(property_path, document_id, section_id),
          storage_(storage) {}

    ~Editor() override = default;

    libtextclassifier3::Status BufferKey(T key) override {
      seen_keys_.insert(key);
      return libtextclassifier3::Status::OK;
    }

    libtextclassifier3::Status IndexAllBufferedKeys() && override;

   private:
    std::unordered_set<T> seen_keys_;
    std::unordered_map<std::string, std::map<T, std::vector<BasicHit>>>&
        storage_;  // Does not own.
  };

  class Iterator : public NumericIndex<T>::Iterator {
   public:
    // We group BasicHits (sorted by document_id) of a key into a Bucket (stored
    // as std::vector) and store key -> vector in an std::map. When doing range
    // query, we may access vectors from multiple keys and want to return
    // BasicHits to callers sorted by document_id. Therefore, this problem is
    // actually "merge K sorted vectors".
    // To implement this algorithm via priority_queue, we create this wrapper
    // class to store iterators of map and vector.
    class BucketInfo {
     public:
      explicit BucketInfo(
          typename std::map<T, std::vector<BasicHit>>::const_iterator
              bucket_iter)
          : bucket_iter_(bucket_iter),
            vec_iter_(bucket_iter_->second.rbegin()) {}

      bool Advance() { return ++vec_iter_ != bucket_iter_->second.rend(); }

      const BasicHit& GetCurrentBasicHit() const { return *vec_iter_; }

      bool operator<(const BucketInfo& other) const {
        // std::priority_queue is a max heap and we should return BasicHits in
        // DocumentId descending order.
        // - BucketInfo::operator< should have the same order as DocumentId.
        // - BasicHit encodes inverted document id and its operator< compares
        //   the encoded raw value directly.
        // - Therefore, BucketInfo::operator< should compare BasicHit reversely.
        // - This will make priority_queue return buckets in DocumentId
        //   descending and SectionId ascending order.
        // - Whatever direction we sort SectionId by (or pop by priority_queue)
        //   doesn't matter because all hits for the same DocumentId will be
        //   merged into a single DocHitInfo.
        return other.GetCurrentBasicHit() < GetCurrentBasicHit();
      }

     private:
      typename std::map<T, std::vector<BasicHit>>::const_iterator bucket_iter_;
      std::vector<BasicHit>::const_reverse_iterator vec_iter_;
    };

    explicit Iterator(T key_lower, T key_upper,
                      std::vector<BucketInfo>&& bucket_info_vec)
        : NumericIndex<T>::Iterator(key_lower, key_upper),
          pq_(std::less<BucketInfo>(), std::move(bucket_info_vec)),
          num_advance_calls_(0) {}

    ~Iterator() override = default;

    libtextclassifier3::Status Advance() override;

    DocHitInfo GetDocHitInfo() const override { return doc_hit_info_; }

    int32_t GetNumAdvanceCalls() const override { return num_advance_calls_; }

    int32_t GetNumBlocksInspected() const override { return 0; }

   private:
    std::priority_queue<BucketInfo> pq_;
    DocHitInfo doc_hit_info_;

    int32_t num_advance_calls_;
  };

  explicit DummyNumericIndex(const Filesystem& filesystem,
                             std::string&& working_path)
      : NumericIndex<T>(filesystem, std::move(working_path),
                        PersistentStorage::WorkingPathType::kDummy),
        dummy_crcs_buffer_(
            std::make_unique<uint8_t[]>(sizeof(PersistentStorage::Crcs))),
        last_added_document_id_(kInvalidDocumentId) {
    memset(dummy_crcs_buffer_.get(), 0, sizeof(PersistentStorage::Crcs));
  }

  libtextclassifier3::Status PersistStoragesToDisk(bool force) override {
    return libtextclassifier3::Status::OK;
  }

  libtextclassifier3::Status PersistMetadataToDisk(bool force) override {
    return libtextclassifier3::Status::OK;
  }

  libtextclassifier3::StatusOr<Crc32> ComputeInfoChecksum(bool force) override {
    return Crc32(0);
  }

  libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override {
    return Crc32(0);
  }

  PersistentStorage::Crcs& crcs() override {
    return *reinterpret_cast<PersistentStorage::Crcs*>(
        dummy_crcs_buffer_.get());
  }
  const PersistentStorage::Crcs& crcs() const override {
    return *reinterpret_cast<const PersistentStorage::Crcs*>(
        dummy_crcs_buffer_.get());
  }

  std::unordered_map<std::string, std::map<T, std::vector<BasicHit>>> storage_;
  std::unique_ptr<uint8_t[]> dummy_crcs_buffer_;
  DocumentId last_added_document_id_;
};

template <typename T>
libtextclassifier3::Status
DummyNumericIndex<T>::Editor::IndexAllBufferedKeys() && {
  auto property_map_iter = storage_.find(this->property_path_);
  if (property_map_iter == storage_.end()) {
    const auto& [inserted_iter, insert_result] =
        storage_.insert({this->property_path_, {}});
    if (!insert_result) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Failed to create a new map for property \"",
                             this->property_path_, "\""));
    }
    property_map_iter = inserted_iter;
  }

  for (const T& key : seen_keys_) {
    auto key_map_iter = property_map_iter->second.find(key);
    if (key_map_iter == property_map_iter->second.end()) {
      const auto& [inserted_iter, insert_result] =
          property_map_iter->second.insert({key, {}});
      if (!insert_result) {
        return absl_ports::InternalError("Failed to create a new map for key");
      }
      key_map_iter = inserted_iter;
    }
    key_map_iter->second.push_back(
        BasicHit(this->section_id_, this->document_id_));
  }
  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::Status DummyNumericIndex<T>::Iterator::Advance() {
  if (pq_.empty()) {
    return absl_ports::ResourceExhaustedError("End of iterator");
  }

  DocumentId document_id = pq_.top().GetCurrentBasicHit().document_id();
  doc_hit_info_ = DocHitInfo(document_id);
  // Merge sections with same document_id into a single DocHitInfo
  while (!pq_.empty() &&
         pq_.top().GetCurrentBasicHit().document_id() == document_id) {
    ++num_advance_calls_;
    doc_hit_info_.UpdateSection(pq_.top().GetCurrentBasicHit().section_id());

    BucketInfo info = pq_.top();
    pq_.pop();

    if (info.Advance()) {
      pq_.push(std::move(info));
    }
  }

  return libtextclassifier3::Status::OK;
}

template <typename T>
libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
DummyNumericIndex<T>::GetIterator(std::string_view property_path, T key_lower,
                                  T key_upper, const DocumentStore&,
                                  const SchemaStore&, int64_t) const {
  if (key_lower > key_upper) {
    return absl_ports::InvalidArgumentError(
        "key_lower should not be greater than key_upper");
  }

  auto property_map_iter = storage_.find(std::string(property_path));
  if (property_map_iter == storage_.end()) {
    // Return an empty iterator.
    return std::make_unique<DocHitInfoIteratorNumeric<T>>(nullptr);
  }

  std::vector<typename Iterator::BucketInfo> bucket_info_vec;
  for (auto key_map_iter = property_map_iter->second.lower_bound(key_lower);
       key_map_iter != property_map_iter->second.cend() &&
       key_map_iter->first <= key_upper;
       ++key_map_iter) {
    bucket_info_vec.push_back(typename Iterator::BucketInfo(key_map_iter));
  }

  return std::make_unique<DocHitInfoIteratorNumeric<T>>(
      std::make_unique<Iterator>(key_lower, key_upper,
                                 std::move(bucket_info_vec)));
}

template <typename T>
libtextclassifier3::Status DummyNumericIndex<T>::Optimize(
    const std::vector<DocumentId>& document_id_old_to_new,
    DocumentId new_last_added_document_id) {
  std::unordered_map<std::string, std::map<T, std::vector<BasicHit>>>
      new_storage;

  for (const auto& [property_path, old_property_map] : storage_) {
    std::map<T, std::vector<BasicHit>> new_property_map;
    for (const auto& [key, hits] : old_property_map) {
      for (const BasicHit& hit : hits) {
        DocumentId old_doc_id = hit.document_id();
        if (old_doc_id >= document_id_old_to_new.size() ||
            document_id_old_to_new[old_doc_id] == kInvalidDocumentId) {
          continue;
        }

        new_property_map[key].push_back(
            BasicHit(hit.section_id(), document_id_old_to_new[old_doc_id]));
      }
    }

    if (!new_property_map.empty()) {
      new_storage[property_path] = std::move(new_property_map);
    }
  }

  storage_ = std::move(new_storage);
  last_added_document_id_ = new_last_added_document_id;
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_DUMMY_NUMERIC_INDEX_H_
