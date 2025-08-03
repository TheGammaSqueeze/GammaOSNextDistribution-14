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

#include "icing/store/namespace-fingerprint-identifier.h"

#include <cstdint>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/text_classifier/lib3/utils/hash/farmhash.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/store/namespace-id.h"
#include "icing/util/encode-util.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<NamespaceFingerprintIdentifier>
NamespaceFingerprintIdentifier::DecodeFromCString(
    std::string_view encoded_cstr) {
  if (encoded_cstr.size() < kMinEncodedLength) {
    return absl_ports::InvalidArgumentError("Invalid length");
  }

  NamespaceId namespace_id = encode_util::DecodeIntFromCString(
      encoded_cstr.substr(0, kEncodedNamespaceIdLength));
  uint64_t fingerprint = encode_util::DecodeIntFromCString(
      encoded_cstr.substr(kEncodedNamespaceIdLength));
  return NamespaceFingerprintIdentifier(namespace_id, fingerprint);
}

NamespaceFingerprintIdentifier::NamespaceFingerprintIdentifier(
    NamespaceId namespace_id, std::string_view target_str)
    : namespace_id_(namespace_id),
      fingerprint_(tc3farmhash::Fingerprint64(target_str)) {}

std::string NamespaceFingerprintIdentifier::EncodeToCString() const {
  // encoded_namespace_id_str should be 1 to 3 bytes based on the value of
  // namespace_id.
  std::string encoded_namespace_id_str =
      encode_util::EncodeIntToCString(namespace_id_);
  // Make encoded_namespace_id_str to fixed kEncodedNamespaceIdLength bytes.
  while (encoded_namespace_id_str.size() < kEncodedNamespaceIdLength) {
    // C string cannot contain 0 bytes, so we append it using 1, just like what
    // we do in encode_util::EncodeIntToCString.
    //
    // The reason that this works is because DecodeIntToString decodes a byte
    // value of 0x01 as 0x00. When EncodeIntToCString returns an encoded
    // namespace id that is less than 3 bytes, it means that the id contains
    // unencoded leading 0x00. So here we're explicitly encoding those bytes as
    // 0x01.
    encoded_namespace_id_str.push_back(1);
  }

  return absl_ports::StrCat(encoded_namespace_id_str,
                            encode_util::EncodeIntToCString(fingerprint_));
}

}  // namespace lib
}  // namespace icing
