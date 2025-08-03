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

#ifndef ICING_ABSL_PORTS_ANNOTATE_H_
#define ICING_ABSL_PORTS_ANNOTATE_H_

#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"

namespace icing {
namespace lib {
namespace absl_ports {

// Returns a Status that is identical to `s` except that the error_message()
// has been augmented by adding `msg` to the end of the original error message.
//
// Annotate should be used to add higher-level information to a Status.  E.g.,
//
//   libtextclassifier3::Status s = file::GetContents(...);
//   if (!s.ok()) {
//     return Annotate(s, "loading blacklist");
//   }
//
// Annotate() adds the appropriate separators, so callers should not include a
// separator in `msg`. The exact formatting is subject to change, so you should
// not depend on it in your tests.
//
// OK status values have no error message and therefore if `s` is OK, the result
// is unchanged.
libtextclassifier3::Status Annotate(const libtextclassifier3::Status& s,
                                    std::string_view msg);

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ABSL_PORTS_ANNOTATE_H_
