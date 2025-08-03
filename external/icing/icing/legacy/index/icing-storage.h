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
// Interface class for disk-backed storage.

#ifndef ICING_LEGACY_INDEX_ICING_STORAGE_H_
#define ICING_LEGACY_INDEX_ICING_STORAGE_H_

#include <cstdint>
#include <string>

namespace icing {
namespace lib {

// Abstract base class for interface.
class IIcingStorage {
 public:
  // Any resource that is not removed in the Close() function should
  // be removed in the child's destructor.
  virtual ~IIcingStorage() = default;

  // This is called to upgrade to a new version.
  // Returns true if the data store can be upgraded successfully.
  virtual bool UpgradeTo(int new_version) = 0;

  // This must be called before the object is usable.
  // Returns true if the storage is in a usable state.
  virtual bool Init() = 0;

  // Attempts to init the given IIcingStorage. On failure, clears the underlying
  // data and tries again. Returns false of the second init is also a failure.
  static bool InitWithRetry(IIcingStorage* file_in) {
    if (file_in->Init()) {
      return true;
    }
    return file_in->Remove() && file_in->Init();
  }

  // Closes all files and system resources.
  // Init() must be called before the object is used again.
  virtual void Close() = 0;

  // Closes all system resources, then removes the backing file.
  // Init() is required before the object is used again.
  // Returns true on success.
  virtual bool Remove() = 0;

  // Syncs any unwritten data to disk.
  virtual bool Sync() = 0;

  // Gets the total amount of disk usage for the object (i.e. the sum of the
  // bytes of all underlying files).
  // Note: reported values are estimated via the number of blocks the file takes
  // up on disk. Sparse files are reported as their physical disk usage, as
  // opposed to the logical size when read.
  // Returns kBadFileSize on error.
  virtual uint64_t GetDiskUsage() const = 0;

  // Optional handler for when our process is entering a vulnerable
  // state (highly likely to get killed). Default implementation does
  // nothing.
  virtual void OnSleep() {}

  virtual void GetDebugInfo(int verbosity, std::string* out) const = 0;

 protected:
  IIcingStorage() = default;

 private:
  // Document stores are non-copyable.
  IIcingStorage(const IIcingStorage&);
  IIcingStorage& operator=(const IIcingStorage&);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_STORAGE_H_
