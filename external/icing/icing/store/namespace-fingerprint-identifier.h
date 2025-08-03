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

#ifndef ICING_STORE_NAMESPACE_FINGERPRINT_IDENTIFIER_H_
#define ICING_STORE_NAMESPACE_FINGERPRINT_IDENTIFIER_H_

#include <cstdint>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/store/namespace-id.h"

namespace icing {
namespace lib {

class NamespaceFingerprintIdentifier {
 public:
  static constexpr int kEncodedNamespaceIdLength = 3;
  static constexpr int kMinEncodedLength = kEncodedNamespaceIdLength + 1;

  static libtextclassifier3::StatusOr<NamespaceFingerprintIdentifier>
  DecodeFromCString(std::string_view encoded_cstr);

  explicit NamespaceFingerprintIdentifier()
      : namespace_id_(0), fingerprint_(0) {}

  explicit NamespaceFingerprintIdentifier(NamespaceId namespace_id,
                                          uint64_t fingerprint)
      : namespace_id_(namespace_id), fingerprint_(fingerprint) {}

  explicit NamespaceFingerprintIdentifier(NamespaceId namespace_id,
                                          std::string_view target_str);

  std::string EncodeToCString() const;

  bool operator<(const NamespaceFingerprintIdentifier& other) const {
    if (namespace_id_ != other.namespace_id_) {
      return namespace_id_ < other.namespace_id_;
    }
    return fingerprint_ < other.fingerprint_;
  }

  bool operator==(const NamespaceFingerprintIdentifier& other) const {
    return namespace_id_ == other.namespace_id_ &&
           fingerprint_ == other.fingerprint_;
  }

  NamespaceId namespace_id() const { return namespace_id_; }
  uint64_t fingerprint() const { return fingerprint_; }

 private:
  NamespaceId namespace_id_;
  uint64_t fingerprint_;
} __attribute__((packed));
static_assert(sizeof(NamespaceFingerprintIdentifier) == 10, "");

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_NAMESPACE_FINGERPRINT_IDENTIFIER_H_
