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

#include "icing/absl_ports/canonical_errors.h"

#include "icing/text_classifier/lib3/utils/base/status.h"

namespace icing {
namespace lib {
namespace absl_ports {

libtextclassifier3::Status CancelledError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::CANCELLED,
                                    std::string(error_message));
}

libtextclassifier3::Status UnknownError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::UNKNOWN,
                                    std::string(error_message));
}

libtextclassifier3::Status InvalidArgumentError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::INVALID_ARGUMENT,
      std::string(error_message));
}

libtextclassifier3::Status DeadlineExceededError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::DEADLINE_EXCEEDED,
      std::string(error_message));
}

libtextclassifier3::Status NotFoundError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::NOT_FOUND,
                                    std::string(error_message));
}

libtextclassifier3::Status AlreadyExistsError(std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::ALREADY_EXISTS,
      std::string(error_message));
}

libtextclassifier3::Status PermissionDeniedError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::PERMISSION_DENIED,
      std::string(error_message));
}

libtextclassifier3::Status ResourceExhaustedError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED,
      std::string(error_message));
}

libtextclassifier3::Status FailedPreconditionError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::FAILED_PRECONDITION,
      std::string(error_message));
}

libtextclassifier3::Status AbortedError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::ABORTED,
                                    std::string(error_message));
}

libtextclassifier3::Status OutOfRangeError(std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::OUT_OF_RANGE, std::string(error_message));
}

libtextclassifier3::Status UnimplementedError(std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::UNIMPLEMENTED,
      std::string(error_message));
}

libtextclassifier3::Status InternalError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::INTERNAL,
                                    std::string(error_message));
}

libtextclassifier3::Status UnavailableError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::UNAVAILABLE,
                                    std::string(error_message));
}

libtextclassifier3::Status DataLossError(std::string_view error_message) {
  return libtextclassifier3::Status(libtextclassifier3::StatusCode::DATA_LOSS,
                                    std::string(error_message));
}

libtextclassifier3::Status UnauthenticatedError(
    std::string_view error_message) {
  return libtextclassifier3::Status(
      libtextclassifier3::StatusCode::UNAUTHENTICATED,
      std::string(error_message));
}

bool IsCancelled(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::CANCELLED;
}
bool IsUnknown(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::UNKNOWN;
}
bool IsInvalidArgument(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::INVALID_ARGUMENT;
}
bool IsDeadlineExceeded(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::DEADLINE_EXCEEDED;
}
bool IsNotFound(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::NOT_FOUND;
}
bool IsAlreadyExists(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::ALREADY_EXISTS;
}
bool IsPermissionDenied(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::PERMISSION_DENIED;
}
bool IsResourceExhausted(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED;
}
bool IsFailedPrecondition(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::FAILED_PRECONDITION;
}
bool IsAborted(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::ABORTED;
}
bool IsOutOfRange(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::OUT_OF_RANGE;
}
bool IsUnimplemented(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::UNIMPLEMENTED;
}
bool IsInternal(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::INTERNAL;
}
bool IsUnavailable(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::UNAVAILABLE;
}
bool IsDataLoss(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() == libtextclassifier3::StatusCode::DATA_LOSS;
}
bool IsUnauthenticated(const libtextclassifier3::Status& status) {
  return status.CanonicalCode() ==
         libtextclassifier3::StatusCode::UNAUTHENTICATED;
}

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing
