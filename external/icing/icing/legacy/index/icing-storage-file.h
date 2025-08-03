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

// Base class for single file-based IIcingStorage implementations.

#ifndef ICING_LEGACY_INDEX_ICING_STORAGE_FILE_H_
#define ICING_LEGACY_INDEX_ICING_STORAGE_FILE_H_

#include <string>

#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-storage.h"

namespace icing {
namespace lib {

// Minimal implementation that is backed by a filename and file descriptor.
class IcingStorageFile : virtual public IIcingStorage {
 public:
  IcingStorageFile(const std::string &filename,
                   const IcingFilesystem *filesystem);

  // This must be called before the object is usable.
  // Returns true if the storage is in a usable state.
  bool Init() override;

  // Default implementation is a no-op.
  bool UpgradeTo(int new_version) override { return true; }

  // Closes all files and system resources.
  // Init() must be called before the object is used again.
  void Close() override;

  // Closes all system resources, then removes the backing file.
  // Init() is required before the object is used again.
  // Returns true on success.
  bool Remove() override;

  // Syncs any unwritten data to disk.
  // REQUIRES: is_initialized() == true
  bool Sync() override;

  // Gets the file size of the underlying file.
  // Returns kBadFileSize on error.
  uint64_t GetDiskUsage() const override;

  bool is_initialized() const { return is_initialized_; }

  const std::string &filename() const { return filename_; }

  void GetDebugInfo(int verbosity, std::string *out) const override;

 protected:
  // Implements any initialization, returning true if successful.
  // The child is repsonsible for calling open on the fd_ file descriptor,
  // before returning from OnInit();
  virtual bool OnInit() = 0;

  // OnClose should remove any resources, other than the file, created
  // during the Init.
  // The file itself will be closed after the OnClose.
  virtual void OnClose() = 0;

  // Called before the file is synced.  The child should write
  // anything it hasn't yet written to the file so that it can be
  // stored.  Default implementation is to do nothing. Return true if
  // successful.
  virtual bool PreSync();

  // Called after the file is synced. Default implementation is to do
  // nothing. Return true if successful.
  virtual bool PostSync();

  const IcingFilesystem *const filesystem_;
  IcingScopedFd fd_;

 private:
  const std::string filename_;
  bool is_initialized_ = false;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_STORAGE_FILE_H_
