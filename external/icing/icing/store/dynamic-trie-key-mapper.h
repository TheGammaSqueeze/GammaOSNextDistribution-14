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

#ifndef ICING_STORE_DYNAMIC_TRIE_KEY_MAPPER_H_
#define ICING_STORE_DYNAMIC_TRIE_KEY_MAPPER_H_

#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <string_view>
#include <type_traits>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/file/filesystem.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/store/key-mapper.h"
#include "icing/util/crc32.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// File-backed mapping between the string key and a trivially copyable value
// type.
//
// DynamicTrieKeyMapper is thread-compatible
template <typename T, typename Formatter = absl_ports::DefaultFormatter>
class DynamicTrieKeyMapper : public KeyMapper<T, Formatter> {
 public:
  // Returns an initialized instance of DynamicTrieKeyMapper that can
  // immediately handle read/write operations.
  // Returns any encountered IO errors.
  //
  // base_dir : Base directory used to save all the files required to persist
  //            DynamicTrieKeyMapper. If this base_dir was previously used to
  //            create a DynamicTrieKeyMapper, then this existing data would be
  //            loaded. Otherwise, an empty DynamicTrieKeyMapper would be
  //            created.
  // maximum_size_bytes : The maximum allowable size of the key mapper storage.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<DynamicTrieKeyMapper<T, Formatter>>>
  Create(const Filesystem& filesystem, std::string_view base_dir,
         int maximum_size_bytes);

  // Deletes all the files associated with the DynamicTrieKeyMapper.
  //
  // base_dir : Base directory used to save all the files required to persist
  //            DynamicTrieKeyMapper. Should be the same as passed into
  //            Create().
  //
  // Returns
  //   OK on success
  //   INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Delete(const Filesystem& filesystem,
                                           std::string_view base_dir);

  ~DynamicTrieKeyMapper() override = default;

  libtextclassifier3::Status Put(std::string_view key, T value) override;

  libtextclassifier3::StatusOr<T> GetOrPut(std::string_view key,
                                           T next_value) override;

  libtextclassifier3::StatusOr<T> Get(std::string_view key) const override;

  bool Delete(std::string_view key) override;

  std::unique_ptr<typename KeyMapper<T, Formatter>::Iterator> GetIterator()
      const override;

  int32_t num_keys() const override { return trie_.size(); }

  libtextclassifier3::Status PersistToDisk() override;

  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const override;

  libtextclassifier3::StatusOr<int64_t> GetElementsSize() const override;

  libtextclassifier3::StatusOr<Crc32> ComputeChecksum() override;

 private:
  class Iterator : public KeyMapper<T, Formatter>::Iterator {
   public:
    explicit Iterator(const IcingDynamicTrie& trie)
        : itr_(trie, /*prefix=*/""), start_(true) {}

    ~Iterator() override = default;

    bool Advance() override {
      if (start_) {
        start_ = false;
        return itr_.IsValid();
      }
      return itr_.Advance();
    }

    std::string_view GetKey() const override {
      const char* key = itr_.GetKey();
      return std::string_view(key);
    }

    T GetValue() const override {
      T value;
      memcpy(&value, itr_.GetValue(), sizeof(T));
      return value;
    }

   private:
    IcingDynamicTrie::Iterator itr_;

    // TODO(b/241784804): remove this flag after changing IcingDynamicTrie to
    //                    follow the common iterator pattern in our codebase.
    bool start_;
  };

  static constexpr char kDynamicTrieKeyMapperDir[] = "key_mapper_dir";
  static constexpr char kDynamicTrieKeyMapperPrefix[] = "key_mapper";

  // Use DynamicTrieKeyMapper::Create() to instantiate.
  explicit DynamicTrieKeyMapper(std::string_view key_mapper_dir);

  // Load any existing DynamicTrieKeyMapper data from disk, or creates a new
  // instance of DynamicTrieKeyMapper on disk and gets ready to process
  // read/write operations.
  //
  // Returns any encountered IO errors.
  libtextclassifier3::Status Initialize(int maximum_size_bytes);

  const std::string file_prefix_;

  // TODO(adorokhine) Filesystem is a forked class that's available both in
  // icing and icing namespaces. We will need icing::Filesystem in order
  // to use IcingDynamicTrie. Filesystem class should be fully refactored
  // to have a single definition across both namespaces. Such a class should
  // use icing (and general google3) coding conventions and behave like
  // a proper C++ class.
  const IcingFilesystem icing_filesystem_;
  IcingDynamicTrie trie_;

  static_assert(std::is_trivially_copyable<T>::value,
                "T must be trivially copyable");
};

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<
    std::unique_ptr<DynamicTrieKeyMapper<T, Formatter>>>
DynamicTrieKeyMapper<T, Formatter>::Create(const Filesystem& filesystem,
                                           std::string_view base_dir,
                                           int maximum_size_bytes) {
  // We create a subdirectory since the trie creates and stores multiple files.
  // This makes it easier to isolate the trie files away from other files that
  // could potentially be in the same base_dir, and makes it easier to delete.
  const std::string key_mapper_dir =
      absl_ports::StrCat(base_dir, "/", kDynamicTrieKeyMapperDir);
  if (!filesystem.CreateDirectoryRecursively(key_mapper_dir.c_str())) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to create DynamicTrieKeyMapper directory: ", key_mapper_dir));
  }
  auto mapper = std::unique_ptr<DynamicTrieKeyMapper<T, Formatter>>(
      new DynamicTrieKeyMapper<T, Formatter>(key_mapper_dir));
  ICING_RETURN_IF_ERROR(mapper->Initialize(maximum_size_bytes));
  return mapper;
}

template <typename T, typename Formatter>
libtextclassifier3::Status DynamicTrieKeyMapper<T, Formatter>::Delete(
    const Filesystem& filesystem, std::string_view base_dir) {
  std::string key_mapper_dir =
      absl_ports::StrCat(base_dir, "/", kDynamicTrieKeyMapperDir);
  if (!filesystem.DeleteDirectoryRecursively(key_mapper_dir.c_str())) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to delete DynamicTrieKeyMapper directory: ", key_mapper_dir));
  }
  return libtextclassifier3::Status::OK;
}

template <typename T, typename Formatter>
DynamicTrieKeyMapper<T, Formatter>::DynamicTrieKeyMapper(
    std::string_view key_mapper_dir)
    : file_prefix_(
          absl_ports::StrCat(key_mapper_dir, "/", kDynamicTrieKeyMapperPrefix)),
      trie_(file_prefix_,
            IcingDynamicTrie::RuntimeOptions().set_storage_policy(
                IcingDynamicTrie::RuntimeOptions::kMapSharedWithCrc),
            &icing_filesystem_) {}

template <typename T, typename Formatter>
libtextclassifier3::Status DynamicTrieKeyMapper<T, Formatter>::Initialize(
    int maximum_size_bytes) {
  IcingDynamicTrie::Options options;
  // Divide the max space between the three internal arrays: nodes, nexts and
  // suffixes. MaxNodes and MaxNexts are in units of their own data structures.
  // MaxSuffixesSize is in units of bytes.
  options.max_nodes = maximum_size_bytes / (3 * sizeof(IcingDynamicTrie::Node));
  options.max_nexts = options.max_nodes;
  options.max_suffixes_size =
      sizeof(IcingDynamicTrie::Node) * options.max_nodes;
  options.value_size = sizeof(T);

  if (!trie_.CreateIfNotExist(options)) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to create DynamicTrieKeyMapper file: ", file_prefix_));
  }
  if (!trie_.Init()) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to init DynamicTrieKeyMapper file: ", file_prefix_));
  }
  return libtextclassifier3::Status::OK;
}

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<T> DynamicTrieKeyMapper<T, Formatter>::GetOrPut(
    std::string_view key, T next_value) {
  std::string string_key(key);
  uint32_t value_index;
  libtextclassifier3::Status status =
      trie_.Insert(string_key.c_str(), &next_value, &value_index,
                   /*replace=*/false);
  if (!status.ok()) {
    ICING_LOG(DBG) << "Unable to insert key " << string_key
                   << " into DynamicTrieKeyMapper " << file_prefix_ << ".\n"
                   << status.error_message();
    return status;
  }
  // This memory address could be unaligned since we're just grabbing the value
  // from somewhere in the trie's suffix array. The suffix array is filled with
  // chars, so the address might not be aligned to T values.
  const T* unaligned_value =
      static_cast<const T*>(trie_.GetValueAtIndex(value_index));

  // memcpy the value to ensure that the returned value here is in a T-aligned
  // address
  T aligned_value;
  memcpy(&aligned_value, unaligned_value, sizeof(T));
  return aligned_value;
}

template <typename T, typename Formatter>
libtextclassifier3::Status DynamicTrieKeyMapper<T, Formatter>::Put(
    std::string_view key, T value) {
  std::string string_key(key);
  libtextclassifier3::Status status = trie_.Insert(string_key.c_str(), &value);
  if (!status.ok()) {
    ICING_LOG(DBG) << "Unable to insert key " << string_key
                   << " into DynamicTrieKeyMapper " << file_prefix_ << ".\n"
                   << status.error_message();
    return status;
  }
  return libtextclassifier3::Status::OK;
}

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<T> DynamicTrieKeyMapper<T, Formatter>::Get(
    std::string_view key) const {
  std::string string_key(key);
  T value;
  if (!trie_.Find(string_key.c_str(), &value)) {
    return absl_ports::NotFoundError(
        absl_ports::StrCat("Key not found ", Formatter()(string_key),
                           " in DynamicTrieKeyMapper ", file_prefix_, "."));
  }
  return value;
}

template <typename T, typename Formatter>
bool DynamicTrieKeyMapper<T, Formatter>::Delete(std::string_view key) {
  return trie_.Delete(key);
}

template <typename T, typename Formatter>
std::unique_ptr<typename KeyMapper<T, Formatter>::Iterator>
DynamicTrieKeyMapper<T, Formatter>::GetIterator() const {
  return std::make_unique<DynamicTrieKeyMapper<T, Formatter>::Iterator>(trie_);
}

template <typename T, typename Formatter>
libtextclassifier3::Status DynamicTrieKeyMapper<T, Formatter>::PersistToDisk() {
  if (!trie_.Sync()) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to sync DynamicTrieKeyMapper file: ", file_prefix_));
  }

  return libtextclassifier3::Status::OK;
}

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<int64_t>
DynamicTrieKeyMapper<T, Formatter>::GetDiskUsage() const {
  int64_t size = trie_.GetDiskUsage();
  if (size == IcingFilesystem::kBadFileSize || size < 0) {
    return absl_ports::InternalError("Failed to get disk usage of key mapper");
  }
  return size;
}

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<int64_t>
DynamicTrieKeyMapper<T, Formatter>::GetElementsSize() const {
  int64_t size = trie_.GetElementsSize();
  if (size == IcingFilesystem::kBadFileSize || size < 0) {
    return absl_ports::InternalError(
        "Failed to get disk usage of elements in the key mapper");
  }
  return size;
}

template <typename T, typename Formatter>
libtextclassifier3::StatusOr<Crc32>
DynamicTrieKeyMapper<T, Formatter>::ComputeChecksum() {
  return Crc32(trie_.UpdateCrc());
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_DYNAMIC_TRIE_KEY_MAPPER_H_
