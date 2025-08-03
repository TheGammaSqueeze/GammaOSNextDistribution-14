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

#include "icing/testing/icu-data-file-helper.h"

#include <sys/mman.h>

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/filesystem.h"
#include "unicode/udata.h"
#include "unicode/utypes.h"

namespace icing {
namespace lib {

namespace icu_data_file_helper {

// ICU data file needs to be set up once, it can be shared between different
// Icing instances. Setting up the file too many times may cause out-of-memory
// segmentation fault errors.
bool has_set_up_icu_data_file = false;

libtextclassifier3::Status SetUpICUDataFile(
    const std::string& icu_data_file_absolute_path) {
  if (has_set_up_icu_data_file) {
    return libtextclassifier3::Status::OK;
  }

  Filesystem filesystem;
  int64_t file_size =
      filesystem.GetFileSize(icu_data_file_absolute_path.c_str());
  ScopedFd fd(filesystem.OpenForRead(icu_data_file_absolute_path.c_str()));
  if (!fd.is_valid()) {
    return absl_ports::InternalError("Unable to open file at provided path");
  }

  const void* data =
      mmap(nullptr, file_size, PROT_READ, MAP_PRIVATE, fd.get(), 0);

  UErrorCode status = U_ZERO_ERROR;
  udata_setCommonData(data, &status);

  if (U_FAILURE(status)) {
    return absl_ports::InternalError(
        "Failed to set up ICU data, please check if you have the data file at "
        "the given path.");
  }

  has_set_up_icu_data_file = true;

  return libtextclassifier3::Status::OK;
}

}  // namespace icu_data_file_helper

}  // namespace lib
}  // namespace icing
