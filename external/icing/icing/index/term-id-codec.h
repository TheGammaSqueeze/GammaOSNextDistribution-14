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

#ifndef ICING_INDEX_TERM_ID_CODEC_H_
#define ICING_INDEX_TERM_ID_CODEC_H_

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"

// Encodes/decodes TermIds into different TviTypes. A "tvi" is a
// term_value_index into some space, essentially a unique id within that space.
// Across TviTypes, tvis are not necessarily unique (i.e. we can have a tvi of 0
// in the LITE space and a tvi of 0 in the MAIN space). The codec maps tvis into
// one overall TermId space so that tvis can be represented by a unique TermId
// across all TviTypes (i.e. a MAIN tvi of 0 can be represented by 0, and a LITE
// tvi of 0 can be represented by 10). The max TermId will be the sum of the max
// MAIN tvi and the max LITE tvi.
//
// Example use:
//   ICING_ASSIGN_OR_RETURN(auto term_id_codec,
//       TermIdCodec::Create(/*max_main_tvi=*/5, /*max_lite_tvi=*/5);
//
//   term_id_codec->tvi_type(0); // TviType::Main
//   term_id_codec->tvi_type(4); // TviType::Main
//   term_id_codec->tvi_type(5); // TviType::Lite
//   term_id_codec->tvi_type(9); // TviType::Lite
//
//   term_id_codec->tvi_type(100); // INVALID_ARGUMENT, exceeds max TermId
//
// TODO(cassiewang): Use signed integers for the tvi values. Currently, the max
// values that are passed in are ~5 million for max_main_tvi, and ~1 million for
// max_lite_tvi. Since the sum of both of these is still well under the int32_t
// max, we should use signed integers (go/totw/159) (go/totw/159). But since
// we're getting these values from icing::DynamicTrie, we need to convert all
// the uints at once to avoid even worse undefined conversion behavior.
namespace icing {
namespace lib {

enum TviType { MAIN, LITE };

class TermIdCodec {
 public:
  struct DecodedTermInfo {
    TviType tvi_type;
    uint32_t tvi;
  };

  // Encodes/decodes TermIds based on a max main tvi and a max lite tvi. The max
  // tvis are an exclusive upper bound on the values. For example, Create(5, 5)
  // creates a MAIN encoding that holds [0, 1, 2, 3, 4] TermIds and a LITE
  // encoding that holds [5, 6, 7, 8, 9] TermIds.
  //
  // Returns:
  //   unique_ptr to a TermIdCodec on success
  //   INVALID_ARGUMENT if the sum of max_main_tvi and max_lite_tvi is greater
  //     than the max uint32_t value
  static libtextclassifier3::StatusOr<std::unique_ptr<TermIdCodec>> Create(
      uint32_t max_main_tvi, uint32_t max_lite_tvi);

  // Returns:
  //   TermId that would represent the given tvi of tvi_type
  //   INVALID_ARGUMENT if the tvi of tvi_type would exceed the max TermId
  libtextclassifier3::StatusOr<uint32_t> EncodeTvi(uint32_t tvi,
                                                   TviType tvi_type) const;

  // Returns:
  //   TviType of the encoded TermId
  //   INVALID_ARGUMENT if the term_id exceeds the max TermId
  libtextclassifier3::StatusOr<TviType> DecodeTviType(uint32_t term_id) const;

  // Returns:
  //   Decoded info of the given term_id
  //   INVALID_ARGUMENT if the term_id exceeds the max TermId
  libtextclassifier3::StatusOr<DecodedTermInfo> DecodeTermInfo(
      uint32_t term_id) const;

  uint32_t max_main_tvi() const { return max_main_tvi_; }

  uint32_t max_lite_tvi() const { return max_lite_tvi_; }

  uint32_t max_term_id() const { return max_main_tvi_ + max_lite_tvi_; }

 private:
  explicit TermIdCodec(uint32_t max_main_tvi, uint32_t max_lite_tvi)
      : max_main_tvi_(max_main_tvi), max_lite_tvi_(max_lite_tvi) {}

  uint32_t max_main_tvi_;
  uint32_t max_lite_tvi_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_TERM_ID_CODEC_H_
