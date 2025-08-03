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

#ifndef ICING_STORE_PERSISTENT_HASH_MAP_KEY_MAPPER_H_
#define ICING_STORE_PERSISTENT_HASH_MAP_KEY_MAPPER_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <type_traits>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/str_join.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-hash-map.h"
#include "icing/store/key-mapper.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// File-backed mapping between the string key and a trivially copyable value
// type.
template <typename T, typename Formatter = absl_ports::DefaultFormatter>
class PersistentHashMapKeyMapper : public KeyMapper<T, Formatter> {
 public:
  static constexpr int32_t kDefaultMaxNumEntries =
      PersistentHashMap::Entry::kMaxNumEntries;
  static constexpr int32_t kDefaultAverageKVByteSize =
      PersistentHashMap::Options::kDefaultAverageKVByteSize;
  static constexpr int32_t kDefaultMaxLoadFactorPercent =
      PersistentHashMap::Options::kDefaultMaxLoadFactorPercent;

  // Returns an initialized instance of PersistentHashMapKeyMapper that can
  // immediately handle read/write operations.
  // Returns any encountered IO errors.
  //
  // filesystem: Object to make system level calls
  // working_path: Working directory used to save all the files required to
  //               persist PersistentHashMapKeyMapper. If this working_path was
  //               previously used to create a PersistentHashMapKeyMapper, then
  //               this existing data would be loaded. Otherwise, an empty
  //               PersistentHashMapKeyMapper would be created. See
  //               PersistentStorage for more details about the concept of
  //               working_path.
  // pre_mapping_fbv: flag indicating whether memory map max possible file size
  //                  for underlying FileBackedVector before growing the actual
  //                  file size.
  // max_num_entries: max # of kvps. It will be used to compute 3 storages size.
  // average_kv_byte_size: average byte size of a single key + serialized value.
  //                       It will be used to compute kv_storage size.
  // max_load_factor_percent: percentage of the max loading for the hash map.
  //                          load_factor_percent = 100 * num_keys / num_buckets
  //                          If load_factor_percent exceeds
  //                          max_load_factor_percent, then rehash will be
  //                          invoked (and # of buckets will be doubled).
  //                          Note that load_factor_percent exceeding 100 is
  //                          considered valid.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PersistentHashMapKeyMapper<T, Formatter>>>
  Create(const Filesystem& filesystem, std::string working_path,
         bool pre_mapping_fbv, int32_t max_num_entries = kDefaultMaxNumEntries,
         int32_t average_kv_byte_size = kDefaultAverageKVByteSize,
         int32_t max_load_factor_percent = kDefaultMaxLoadFactorPercent);

  // Deletes working_path (and all the files under it recursively) associated
  // with the PersistentHashMapKeyMapper.
  //
  // working_path: Working directory used to save all the files required to
  //               persist PersistentHashMapKeyMapper. Should be the same as
  //               passed into Create().
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Delete(const Filesystem& filesystem,
                                           const std::string& working_path);

  ~PersistentHashMapKeyMapper() override = default;

  libtextclassifier3::Status Put(std::string_view key, T value) override {
    return persistent_hash_map_->Put(key, &value);
  }

  libtextclassifier3::StatusOr<T> GetOrPut(std::string_view key,
                                           T next_value) override {
    ICING_RETURN_IF_ERROR(persistent_hash_map_->GetOrPut(key, &next_value));
    return next_value;
  }

  libtextclassifier3::StatusOr<T> Get(std::string_view key) const override {
    T value;
    ICING_RETURN_IF_ERROR(persistent_hash_map_->Get(key, &value));
    return value;
  }

  bool Delete(std::string_view key) override {
    return persistent_hash_map_->Delete(key).ok();
  }

  std::unique_ptr<typename KeyMapper<T, Formatter>::Iterator> GetIterator()
      const override {
    return std::make_unique<PersistentHashMapKeyMapper<T, Formatter>::Iterator>(
        persistent_hash_map_.get());
  }

  int32_t num_keys() const override { return persistent_hash_map_->size(); }

  libtextclassifier3::Status PersistToDisk() override {
    return persistent_hash_map_->PersistToDisk();
  }

  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const override {
    return persistent_hash_map_->GetDiskUsage();
  }

  libtextclassifier3::StatusOr<int64_t> GetElementsSize() const override {
    return persistent_hash_map_->GetElementsSize();
  }

  libtextclassifier3::StatusOr<Crc32> ComputeChecksum() override {
    return persistent_hash_map_->UpdateChecksums();
  }

 private:
  class Iterator : public KeyMapper<T, Formatter>::Iterator {
   public:
    explicit Iterator(const PersistentHashMap* persistent_hash_map)
        : itr_(persistent_hash_map->GetIterator()) {}

    ~Iterator() override = default;

    bool Advance() override { return itr_.Advance(); }

    std::string_view GetKey() const override { return itr_.GetKey(); }

    T GetValue() const override {
      T value;
      memcpy(&value, itr_.GetValue(), sizeof(T));
      return value;
    }

   private:
    PersistentHashMap::Iterator itr_;
  };

  // Use PersistentHashMapKeyMapper::Create() to instantiate.
  explicit PersistentHashMapKeyMapper(
      std::unique_ptr<PersistentHashMap> persistent_hash_map)
      : persistent_hash_map_(std::move(persistent_hash_map)) {}

  std::unique_ptr<PersistentHashMap> persistent_hash_map_;

  static_assert(std::is_trivially_copyable<T>::value,
                "T must be trivially copyable");
};

template <typename T, typename Formatter>
/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PersistentHashMapKeyMapper<T, Formatter>>>
PersistentHashMapKeyMapper<T, Formatter>::Create(
    const Filesystem& filesystem, std::string working_path,
    bool pre_mapping_fbv, int32_t max_num_entries, int32_t average_kv_byte_size,
    int32_t max_load_factor_percent) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem, std::move(working_path),
          PersistentHashMap::Options(
              /*value_type_size_in=*/sizeof(T),
              /*max_num_entries_in=*/max_num_entries,
              /*max_load_factor_percent_in=*/max_load_factor_percent,
              /*average_kv_byte_size_in=*/average_kv_byte_size,
              /*init_num_buckets_in=*/
              PersistentHashMap::Options::kDefaultInitNumBuckets,
              /*pre_mapping_fbv_in=*/pre_mapping_fbv)));
  return std::unique_ptr<PersistentHashMapKeyMapper<T, Formatter>>(
      new PersistentHashMapKeyMapper<T, Formatter>(
          std::move(persistent_hash_map)));
}

template <typename T, typename Formatter>
/* static */ libtextclassifier3::Status
PersistentHashMapKeyMapper<T, Formatter>::Delete(
    const Filesystem& filesystem, const std::string& working_path) {
  return PersistentHashMap::Discard(filesystem, working_path);
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_PERSISTENT_HASH_MAP_KEY_MAPPER_H_
