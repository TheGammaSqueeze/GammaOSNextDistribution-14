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

#ifndef ICING_ABSL_PORTS_CANONICAL_ERRORS_H_
#define ICING_ABSL_PORTS_CANONICAL_ERRORS_H_

#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"

namespace icing {
namespace lib {
namespace absl_ports {

libtextclassifier3::Status CancelledError(std::string_view error_message);
libtextclassifier3::Status UnknownError(std::string_view error_message);
libtextclassifier3::Status InvalidArgumentError(std::string_view error_message);
libtextclassifier3::Status DeadlineExceededError(
    std::string_view error_message);
libtextclassifier3::Status NotFoundError(std::string_view error_message);
libtextclassifier3::Status AlreadyExistsError(std::string_view error_message);
libtextclassifier3::Status PermissionDeniedError(
    std::string_view error_message);
libtextclassifier3::Status ResourceExhaustedError(
    std::string_view error_message);
libtextclassifier3::Status FailedPreconditionError(
    std::string_view error_message);
libtextclassifier3::Status AbortedError(std::string_view error_message);
libtextclassifier3::Status OutOfRangeError(std::string_view error_message);
libtextclassifier3::Status UnimplementedError(std::string_view error_message);
libtextclassifier3::Status InternalError(std::string_view error_message);
libtextclassifier3::Status UnavailableError(std::string_view error_message);
libtextclassifier3::Status DataLossError(std::string_view error_message);
libtextclassifier3::Status UnauthenticatedError(std::string_view error_message);

bool IsCancelled(const libtextclassifier3::Status& status);
bool IsUnknown(const libtextclassifier3::Status& status);
bool IsInvalidArgument(const libtextclassifier3::Status& status);
bool IsDeadlineExceeded(const libtextclassifier3::Status& status);
bool IsNotFound(const libtextclassifier3::Status& status);
bool IsAlreadyExists(const libtextclassifier3::Status& status);
bool IsPermissionDenied(const libtextclassifier3::Status& status);
bool IsResourceExhausted(const libtextclassifier3::Status& status);
bool IsFailedPrecondition(const libtextclassifier3::Status& status);
bool IsAborted(const libtextclassifier3::Status& status);
bool IsOutOfRange(const libtextclassifier3::Status& status);
bool IsUnimplemented(const libtextclassifier3::Status& status);
bool IsInternal(const libtextclassifier3::Status& status);
bool IsUnavailable(const libtextclassifier3::Status& status);
bool IsDataLoss(const libtextclassifier3::Status& status);
bool IsUnauthenticated(const libtextclassifier3::Status& status);

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ABSL_PORTS_CANONICAL_ERRORS_H_
