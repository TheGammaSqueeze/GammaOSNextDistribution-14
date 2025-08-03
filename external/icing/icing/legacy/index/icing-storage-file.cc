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

#include "icing/legacy/index/icing-storage-file.h"

#include <unistd.h>

#include <cinttypes>
#include <string>

#include "icing/legacy/core/icing-compat.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/core/icing-timer.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

IcingStorageFile::IcingStorageFile(const std::string &filename,
                                   const IcingFilesystem *filesystem)
    : IIcingStorage(), filesystem_(filesystem), filename_(filename) {}

bool IcingStorageFile::Init() {
  if (!is_initialized_) {
    // Ensure the storage directory exists
    std::string storage_dir = filesystem_->GetDirname(filename_.c_str());
    if (!filesystem_->CreateDirectoryRecursively(storage_dir.c_str())) {
      return false;
    }

    is_initialized_ = OnInit();

    if (is_initialized_ && fd_.get() < 0) {  // if initalized, fd better be set
      ICING_LOG(FATAL)
          << "Storage file descriptor not set after initialization";
    }
  }
  return is_initialized_;
}

void IcingStorageFile::Close() {
  if (is_initialized_) {
    OnClose();
    fd_.reset();
    is_initialized_ = false;
  }
}

bool IcingStorageFile::Remove() {
  Close();
  return filesystem_->DeleteFile(filename_.c_str());
}

bool IcingStorageFile::Sync() {
  if (!is_initialized_) {
    ICING_LOG(FATAL) << "Storage file not initialized";
  }

  IcingTimer timer;
  if (!PreSync()) {
    ICING_LOG(ERROR) << "Pre-sync " << filename_ << " failed";
    return false;
  }
  if (!filesystem_->DataSync(fd_.get())) {
    ICING_LOG(ERROR) << "Sync " << filename_ << " failed";
    return false;
  }
  if (!PostSync()) {
    ICING_LOG(ERROR) << "Post-sync " << filename_ << " failed";
    return false;
  }
  ICING_VLOG(1) << "Syncing " << filename_ << " took " << timer.Elapsed() * 1000 << "ms";
  return true;
}

uint64_t IcingStorageFile::GetDiskUsage() const {
  return filesystem_->GetDiskUsage(fd_.get());
}

bool IcingStorageFile::PreSync() {
  // Default implementation is a no-op.
  return true;
}

bool IcingStorageFile::PostSync() {
  // Default implementation is a no-op.
  return true;
}

void IcingStorageFile::GetDebugInfo(int verbosity, std::string *out) const {
  if (!is_initialized_) {
    ICING_LOG(FATAL) << "Storage file not initialized";
  }

  if (verbosity >= 0) {  // Always
    uint64_t size = filesystem_->GetFileSize(fd_.get());
    IcingStringUtil::SStringAppendF(
        out, 1000, "Filename: %s Size: %" PRIu64 "\n", filename_.c_str(), size);
  }
}

}  // namespace lib
}  // namespace icing
