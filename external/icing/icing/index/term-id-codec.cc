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

#include "icing/index/term-id-codec.h"

#include <cstdint>
#include <limits>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<std::unique_ptr<TermIdCodec>> TermIdCodec::Create(
    uint32_t max_main_tvi, uint32_t max_lite_tvi) {
  uint64_t sum =
      static_cast<uint64_t>(max_main_tvi) + static_cast<uint64_t>(max_lite_tvi);
  if (sum > std::numeric_limits<uint32_t>::max()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Sum of max_main_tvi, %d, and max_lite_tvi, %d must be less than the "
        "uint32_t max, %d.",
        max_main_tvi, max_lite_tvi, std::numeric_limits<uint32_t>::max()));
  }

  // TODO(cassiewang): When we convert these values to signed ints, we should
  // check to make sure they're >= 0.

  return std::unique_ptr<TermIdCodec>(
      new TermIdCodec(max_main_tvi, max_lite_tvi));
}

libtextclassifier3::StatusOr<uint32_t> TermIdCodec::EncodeTvi(
    uint32_t tvi, TviType tvi_type) const {
  switch (tvi_type) {
    case TviType::MAIN:
      if (tvi >= max_main_tvi_) {
        return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
            "Main tvi %d is greater "
            "than or equal to the max_main_tvi %d",
            tvi, max_main_tvi_));
      }
      return tvi;
    case TviType::LITE: {
      if (tvi >= max_lite_tvi_) {
        return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
            "Lite tvi %d is greater "
            "than or equal to the max_lite_tvi %d",
            tvi, max_lite_tvi_));
      }
      return max_main_tvi_ + tvi;
    }
  }
}

libtextclassifier3::StatusOr<TviType> TermIdCodec::DecodeTviType(
    uint32_t term_id) const {
  if (term_id < max_main_tvi_) {
    return TviType::MAIN;
  } else if (term_id < max_term_id()) {
    return TviType::LITE;
  }
  return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
      "Given TermId %d is greater than or equal to the max TermId %d", term_id,
      max_term_id()));
}

libtextclassifier3::StatusOr<TermIdCodec::DecodedTermInfo>
TermIdCodec::DecodeTermInfo(uint32_t term_id) const {
  DecodedTermInfo result;
  ICING_ASSIGN_OR_RETURN(result.tvi_type, DecodeTviType(term_id));
  switch (result.tvi_type) {
    case TviType::MAIN:
      result.tvi = term_id;
      break;
    case TviType::LITE:
      result.tvi = term_id - max_main_tvi_;
      break;
  }
  return result;
}

}  // namespace lib
}  // namespace icing
