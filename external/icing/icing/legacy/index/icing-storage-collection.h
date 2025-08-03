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

// Author: sbanacho@google.com (Scott Banachowski)
//         vmarko@google.com (Vladimir Marko)
//
// An implementation of IIcingStorage that holds an arbitrary number of nested
// IIcingStorage items.

#ifndef ICING_LEGACY_INDEX_ICING_STORAGE_COLLECTION_H_
#define ICING_LEGACY_INDEX_ICING_STORAGE_COLLECTION_H_

#include <string>
#include <vector>

#include "icing/legacy/index/icing-storage.h"

namespace icing {
namespace lib {

// Class that owns a list of IDocumentStores.
class IcingStorageCollection : public IIcingStorage {
 public:
  ~IcingStorageCollection() override;
  // remove_if_corrupted specifies Init behavior when backing file is
  // corrupted: if false, Init will fail, else Init will clear the
  // underlying file and succeed.
  void Add(IIcingStorage *file, bool remove_if_corrupted);
  void Swap(const IIcingStorage *current_file, IIcingStorage *new_file);
  bool UpgradeTo(int new_version) override;
  bool Init() override;
  void Close() override;
  bool Remove() override;
  bool Sync() override;
  uint64_t GetDiskUsage() const override;
  void OnSleep() override;
  void GetDebugInfo(int verbosity, std::string *out) const override;

 private:
  struct FileInfo {
    FileInfo(IIcingStorage *file_in, bool remove_if_corrupted_in)
        : file(file_in), remove_if_corrupted(remove_if_corrupted_in) {}

    IIcingStorage *file;
    bool remove_if_corrupted;
  };
  std::vector<FileInfo> files_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_STORAGE_COLLECTION_H_
