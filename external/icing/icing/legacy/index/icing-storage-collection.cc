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

#include "icing/legacy/index/icing-storage-collection.h"

#include "icing/legacy/core/icing-compat.h"
#include "icing/legacy/index/icing-filesystem.h"

namespace icing {
namespace lib {

IcingStorageCollection::~IcingStorageCollection() {
  // TODO(b/75960589): fix loop styling throughout
  for (size_t i = 0; i < files_.size(); ++i) {
    delete files_[i].file;
  }
}

void IcingStorageCollection::Add(IIcingStorage *file,
                                 bool remove_if_corrupted) {
  files_.push_back(FileInfo(file, remove_if_corrupted));
}

void IcingStorageCollection::Swap(const IIcingStorage *current_file,
                                  IIcingStorage *new_file) {
  for (size_t i = 0; i < files_.size(); ++i) {
    if (files_[i].file == current_file) {
      delete files_[i].file;
      files_[i] = FileInfo(new_file, files_[i].remove_if_corrupted);
    }
  }
}

bool IcingStorageCollection::UpgradeTo(int new_version) {
  size_t count = 0;
  for (size_t i = 0; i < files_.size(); ++i) {
    if (files_[i].file->UpgradeTo(new_version)) {
      ++count;
    }
  }
  return count == files_.size();
}

bool IcingStorageCollection::Init() {
  size_t count = 0;
  for (size_t i = 0; i < files_.size(); ++i) {
    if (files_[i].remove_if_corrupted) {
      if (IIcingStorage::InitWithRetry(files_[i].file)) {
        ++count;
      }
    } else {
      if (files_[i].file->Init()) {
        ++count;
      }
    }
  }
  return count == files_.size();
}

void IcingStorageCollection::Close() {
  for (size_t i = 0; i < files_.size(); ++i) {
    files_[i].file->Close();
  }
}

bool IcingStorageCollection::Remove() {
  size_t count = 0;
  for (size_t i = 0; i < files_.size(); ++i) {
    if (files_[i].file->Remove()) {
      ++count;
    }
  }
  return count == files_.size();
}

bool IcingStorageCollection::Sync() {
  size_t count = 0;
  for (size_t i = 0; i < files_.size(); ++i) {
    if (files_[i].file->Sync()) {
      ++count;
    }
  }
  return count == files_.size();
}

uint64_t IcingStorageCollection::GetDiskUsage() const {
  uint64_t total = 0;
  for (auto &file_info : files_) {
    IcingFilesystem::IncrementByOrSetInvalid(file_info.file->GetDiskUsage(),
                                             &total);
  }
  return total;
}

void IcingStorageCollection::OnSleep() {
  for (size_t i = 0; i < files_.size(); ++i) {
    files_[i].file->OnSleep();
  }
}

void IcingStorageCollection::GetDebugInfo(int verbosity,
                                          std::string *out) const {
  for (size_t i = 0; i < files_.size(); ++i) {
    files_[i].file->GetDebugInfo(verbosity, out);
  }
}

}  // namespace lib
}  // namespace icing
