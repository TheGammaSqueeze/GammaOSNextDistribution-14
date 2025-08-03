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

#include "icing/file/persistent-storage.h"

#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/legacy/core/icing-string-util.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::Status PersistentStorage::Discard(
    const Filesystem& filesystem, const std::string& working_path,
    WorkingPathType working_path_type) {
  switch (working_path_type) {
    case WorkingPathType::kSingleFile: {
      if (!filesystem.DeleteFile(working_path.c_str())) {
        return absl_ports::InternalError(absl_ports::StrCat(
            "Failed to delete PersistentStorage file: ", working_path));
      }
      return libtextclassifier3::Status::OK;
    }
    case WorkingPathType::kDirectory: {
      if (!filesystem.DeleteDirectoryRecursively(working_path.c_str())) {
        return absl_ports::InternalError(absl_ports::StrCat(
            "Failed to delete PersistentStorage directory: ", working_path));
      }
      return libtextclassifier3::Status::OK;
    }
    case WorkingPathType::kDummy:
      return libtextclassifier3::Status::OK;
  }
  return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
      "Unknown working path type %d for PersistentStorage %s",
      static_cast<int>(working_path_type), working_path.c_str()));
}

}  // namespace lib
}  // namespace icing
