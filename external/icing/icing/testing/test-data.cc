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

#include "icing/testing/test-data.h"

#include <sys/mman.h>

#include <cstdint>

#include "devtools/build/runtime/get_runfiles_dir.h"
#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "unicode/udata.h"
#include "unicode/utypes.h"

namespace icing {
namespace lib {

std::string GetTestFilePath(const std::string& google3_relative_file_path) {
  return absl_ports::StrCat(devtools_build::testonly::GetTestSrcdir(),
                            "/google3/", google3_relative_file_path);
}

}  // namespace lib
}  // namespace icing
