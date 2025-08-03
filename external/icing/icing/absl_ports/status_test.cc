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

#include <utility>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/proto/document.pb.h"

namespace icing {
namespace lib {

TEST(StatusTest, StatusOrOfProtoConstructorTest) {
  libtextclassifier3::StatusOr<DocumentProto> status_or =
      absl_ports::InvalidArgumentError("test");
  libtextclassifier3::StatusOr<DocumentProto> new_status_or = status_or;
}

TEST(StatusTest, StatusOrOfProtoMoveConstructorTest) {
  libtextclassifier3::StatusOr<DocumentProto> status_or =
      absl_ports::InvalidArgumentError("test");
  libtextclassifier3::StatusOr<DocumentProto> new_status_or =
      std::move(status_or);
}

TEST(StatusTest, StatusOrOfProtoAssignmentTest) {
  libtextclassifier3::StatusOr<DocumentProto> status_or =
      absl_ports::InvalidArgumentError("test");
  libtextclassifier3::StatusOr<DocumentProto> new_status_or;
  new_status_or = status_or;
}

TEST(StatusTest, StatusOrOfProtoMoveAssignmentTest) {
  libtextclassifier3::StatusOr<DocumentProto> status_or =
      absl_ports::InvalidArgumentError("test");
  libtextclassifier3::StatusOr<DocumentProto> new_status_or;
  new_status_or = std::move(status_or);
}

}  // namespace lib
}  // namespace icing
