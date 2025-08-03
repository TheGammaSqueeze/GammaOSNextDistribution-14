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

#ifndef ICING_STORE_KEY_MAPPER_H_
#define ICING_STORE_KEY_MAPPER_H_

#include <cstdint>
#include <cstring>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_map>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/str_join.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// An interface for file-backed mapping between the string key and a trivially
// copyable value type.
//
// The implementation for KeyMapper should be thread-compatible
template <typename T, typename Formatter = absl_ports::DefaultFormatter>
class KeyMapper {
 public:
  class Iterator {
   public:
    virtual ~Iterator() = default;

    // Advance to the next entry.
    //
    // Returns:
    //   True on success, otherwise false.
    virtual bool Advance() = 0;

    // Get the key.
    //
    // REQUIRES: The preceding call for Advance() is true.
    virtual std::string_view GetKey() const = 0;

    // Get the value.
    //
    // REQUIRES: The preceding call for Advance() is true.
    virtual T GetValue() const = 0;
  };

  virtual ~KeyMapper() = default;

  // Inserts/Updates value for key.
  // Returns any encountered IO errors.
  //
  // NOTE: Put() doesn't automatically flush changes to disk and relies on
  // either explicit calls to PersistToDisk() or a clean shutdown of the class.
  virtual libtextclassifier3::Status Put(std::string_view key, T value) = 0;

  // Finds the current value for key and returns it. If key is not present, it
  // is inserted with next_value and next_value is returned.
  //
  // Returns any IO errors that may occur during Put.
  virtual libtextclassifier3::StatusOr<T> GetOrPut(std::string_view key,
                                                   T next_value) = 0;

  // Returns the value corresponding to the key.
  //
  // Returns NOT_FOUND error if the key was missing.
  // Returns any encountered IO errors.
  virtual libtextclassifier3::StatusOr<T> Get(std::string_view key) const = 0;

  // Deletes data related to the given key. Returns true on success.
  virtual bool Delete(std::string_view key) = 0;

  // Returns an iterator of the key mapper.
  //
  // Example usage:
  //   auto itr = key_mapper->GetIterator();
  //   while (itr->Advance()) {
  //     std::cout << itr->GetKey() << " " << itr->GetValue() << std::endl;
  //   }
  virtual std::unique_ptr<Iterator> GetIterator() const = 0;

  // Count of unique keys stored in the KeyMapper.
  virtual int32_t num_keys() const = 0;

  // Syncs all the changes made to the KeyMapper to disk.
  // Returns any encountered IO errors.
  //
  // NOTE: To control disk-churn, Put() doesn't automatically persist every
  // change to disk. The caller should explicitly call PersistToDisk() to make
  // sure that the data is durable.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  virtual libtextclassifier3::Status PersistToDisk() = 0;

  // Calculates and returns the disk usage in bytes. Rounds up to the nearest
  // block size.
  //
  // Returns:
  //   Disk usage on success
  //   INTERNAL_ERROR on IO error
  virtual libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const = 0;

  // Returns the size of the elements held in the key mapper. This excludes the
  // size of any internal metadata of the key mapper, e.g. the key mapper's
  // header.
  //
  // Returns:
  //   File size on success
  //   INTERNAL_ERROR on IO error
  virtual libtextclassifier3::StatusOr<int64_t> GetElementsSize() const = 0;

  // Computes and returns the checksum of the header and contents.
  virtual libtextclassifier3::StatusOr<Crc32> ComputeChecksum() = 0;

 private:
  static_assert(std::is_trivially_copyable<T>::value,
                "T must be trivially copyable");
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_KEY_MAPPER_H_
