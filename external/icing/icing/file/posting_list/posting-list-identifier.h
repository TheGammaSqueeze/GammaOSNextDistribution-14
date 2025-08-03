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

#ifndef ICING_FILE_POSTING_LIST_POSTING_LIST_IDENTIFIER_H_
#define ICING_FILE_POSTING_LIST_POSTING_LIST_IDENTIFIER_H_

#include <cstdint>

#include "icing/file/posting_list/posting-list-common.h"
#include "icing/legacy/index/icing-bit-util.h"

namespace icing {
namespace lib {

// 1M blocks * 4K page size = 4GB index
inline constexpr int kBlockIndexBits = 20;
inline constexpr int kMaxBlockIndex = (1u << kBlockIndexBits) - 1;

// Class used to store information necessary to identify any posting list within
// the index.
//
// The 20 leftmost bits in this identifier encode the block index. The 12
// rightmost bits encode both the posting list index and the maximum number of
// bits required to encode a posting list index on that block.
//
// Ex. An index block containing a max of 68 posting lists each of size 60
// bytes (and thus 7 posting list bits), with a block index of 13 and a posting
// list index of 5.
//   0000  0000  0000  0000  1101  1111  0000  0101
//  |__________block-index_______|__pad__|_pl-index_|
//
// "pad" is some region starting at kEncodedPostingListIndexBits (12) bit and
// continuing rightward until reaching a terminating "0". This padding encodes
// the posting list bits value - posting list bits value is the number of bits
// after the terminating '0' of the "pad" region.
//
// This value will eventually be stored in the Main Lexicon.
class PostingListIdentifier {
  // 1 bit is wasted to encode max pl index bits so there can be at most 2^11
  // posting lists per block. Block size would have to be >=40020 bytes for
  // there to be more than 2K+ posting lists in a block.
  static constexpr int kEncodedPostingListIndexBits = 12;
  static_assert(kEncodedPostingListIndexBits + kBlockIndexBits <=
                    8 * sizeof(uint32_t),
                "Not enough room in PostingListIdentifier value to encode "
                "block index and posting list index.");

 public:
  static PostingListIdentifier kInvalid;

  explicit PostingListIdentifier() { *this = kInvalid; }

  // 1. block_index - the index of this block within the FlashIndexStorage file
  // 2. posting_list_index - the index of this posting list within the block
  // 3. posting_list_index_bits - the number of bits needed to encode the
  //    largest posting_list_index that this block can have.
  explicit PostingListIdentifier(uint32_t block_index,
                                 PostingListIndex posting_list_index,
                                 int posting_list_index_bits) {
    val_ = 0;
    BITFIELD_OR(val_, /*offset=*/0, /*len=*/posting_list_index_bits,
                /*val=*/static_cast<uint64_t>(posting_list_index));
    BITFIELD_OR(
        val_, /*offset=*/posting_list_index_bits + 1,
        /*len=*/kEncodedPostingListIndexBits - posting_list_index_bits - 1,
        /*val=*/~0u);
    BITFIELD_OR(val_, /*offset=*/kEncodedPostingListIndexBits,
                /*len=*/kBlockIndexBits,
                /*val=*/block_index);
  }

  uint32_t block_index() const {
    return BITFIELD_GET(val_, kEncodedPostingListIndexBits, kBlockIndexBits);
  }

  PostingListIndex posting_list_index() const {
    return BITFIELD_GET(val_, 0, posting_list_index_bits());
  }

  // Returns the maximum number of bits that a posting list index on the block
  // referred to by block_index could use.
  int posting_list_index_bits() const {
    for (int bits = kEncodedPostingListIndexBits - 1; bits >= 0; --bits) {
      if (((1u << bits) & val_) == 0) {
        // Got to the zero bit. This is the start of pl index.
        return bits;
      }
    }
    return -1;
  }

  bool is_valid() const { return *this != kInvalid; }

  bool operator==(const PostingListIdentifier& rhs) const {
    return val_ == rhs.val_;
  }
  bool operator!=(const PostingListIdentifier& rhs) const {
    return !(*this == rhs);
  }

 private:
  uint32_t val_;
} __attribute__((packed));
static_assert(sizeof(PostingListIdentifier) == 4, "");

}  //  namespace lib
}  //  namespace icing

#endif  // ICING_FILE_POSTING_LIST_POSTING_LIST_IDENTIFIER_H_
