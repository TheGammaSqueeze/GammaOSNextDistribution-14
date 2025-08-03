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

#ifndef ICING_UTIL_STATUS_MACROS_H_
#define ICING_UTIL_STATUS_MACROS_H_

#include "icing/text_classifier/lib3/utils/base/status_macros.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"

#define ICING_RETURN_ERROR_IF_NULL(var)                             \
  do {                                                              \
    if ((var) == nullptr) {                                         \
      return absl_ports::FailedPreconditionError(#var " is null."); \
    }                                                               \
  } while (false)

#define ICING_RETURN_IF_ERROR(expr) TC3_RETURN_IF_ERROR(expr)

#define ICING_ASSIGN_OR_RETURN(...)                            \
  TC_STATUS_MACROS_IMPL_GET_VARIADIC_(                         \
      (__VA_ARGS__, TC_STATUS_MACROS_IMPL_ASSIGN_OR_RETURN_3_, \
       TC_STATUS_MACROS_IMPL_ASSIGN_OR_RETURN_2_))             \
  (__VA_ARGS__)

#endif  // ICING_UTIL_STATUS_MACROS_H_
