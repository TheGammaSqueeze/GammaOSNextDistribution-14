// Copyright (C) 2022 Google LLC
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

#ifndef ICING_JOIN_QUALIFIED_ID_H_
#define ICING_JOIN_QUALIFIED_ID_H_

#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"

namespace icing {
namespace lib {

// QualifiedId definition: namespace and uri.
// This is a wrapper class for parsing qualified id string.
//
// Qualified id string format: escape(namespace) + '#' + escape(uri).
// - Use '#' as the separator to concat namespace and uri
// - Use '\' to escape '\' and '#' in namespace and uri.
// - There should be 1 separator '#' in a qualified string, and the rest part
//   should have correct escape format.
// - Raw namespace and uri cannot be empty.
class QualifiedId {
 public:
  static constexpr char kEscapeChar = '\\';
  static constexpr char kNamespaceUriSeparator = '#';

  // Parses a qualified id string "<escaped(namespace)>#<escaped(uri)>" and
  // creates an instance of QualifiedId.
  //
  // qualified_id_str: a qualified id string having the format mentioned above.
  //
  // Returns:
  //   - A QualifiedId instance with raw namespace and uri, on success.
  //   - INVALID_ARGUMENT_ERROR if the format of qualified_id_str is incorrect.
  static libtextclassifier3::StatusOr<QualifiedId> Parse(
      std::string_view qualified_id_str);

  explicit QualifiedId(std::string name_space, std::string uri)
      : name_space_(std::move(name_space)), uri_(std::move(uri)) {}

  const std::string& name_space() const { return name_space_; }
  const std::string& uri() const { return uri_; }

 private:
  std::string name_space_;
  std::string uri_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_QUALIFIED_ID_H_
