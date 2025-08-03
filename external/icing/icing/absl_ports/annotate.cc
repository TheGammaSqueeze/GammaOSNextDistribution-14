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

#include "icing/absl_ports/annotate.h"

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {
namespace absl_ports {

namespace {
constexpr std::string_view kErrorSeparator = ";";
}  // namespace

libtextclassifier3::Status Annotate(const libtextclassifier3::Status& s,
                                    std::string_view msg) {
  if (s.ok() || msg.empty()) {
    return s;
  }

  std::string new_msg =
      (!s.error_message().empty())
          ? absl_ports::StrCat(msg, kErrorSeparator, s.error_message())
          : std::string(msg);
  return libtextclassifier3::Status(s.CanonicalCode(), new_msg);
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing
