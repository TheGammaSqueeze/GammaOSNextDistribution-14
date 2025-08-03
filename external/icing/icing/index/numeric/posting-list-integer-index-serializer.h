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

#ifndef ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_SERIALIZER_H_
#define ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_SERIALIZER_H_

#include <cstdint>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/numeric/integer-index-data.h"

namespace icing {
namespace lib {

// A serializer class to serialize IntegerIndexData to PostingListUsed.
class PostingListIntegerIndexSerializer : public PostingListSerializer {
 public:
  using SpecialDataType = SpecialData<IntegerIndexData>;
  static_assert(sizeof(SpecialDataType) == sizeof(IntegerIndexData), "");

  static constexpr uint32_t kSpecialDataSize =
      kNumSpecialData * sizeof(SpecialDataType);

  uint32_t GetDataTypeBytes() const override {
    return sizeof(IntegerIndexData);
  }

  uint32_t GetMinPostingListSize() const override {
    static constexpr uint32_t kMinPostingListSize = kSpecialDataSize;
    static_assert(sizeof(PostingListIndex) <= kMinPostingListSize,
                  "PostingListIndex must be small enough to fit in a "
                  "minimum-sized Posting List.");

    return kMinPostingListSize;
  }

  uint32_t GetMinPostingListSizeToFit(
      const PostingListUsed* posting_list_used) const override;

  uint32_t GetBytesUsed(
      const PostingListUsed* posting_list_used) const override;

  void Clear(PostingListUsed* posting_list_used) const override;

  libtextclassifier3::Status MoveFrom(PostingListUsed* dst,
                                      PostingListUsed* src) const override;

  // Prepend an IntegerIndexData to the posting list.
  //
  // RETURNS:
  //   - INVALID_ARGUMENT if !data.is_valid() or if data is not less than the
  //       previously added data.
  //   - RESOURCE_EXHAUSTED if there is no more room to add data to the posting
  //       list.
  libtextclassifier3::Status PrependData(PostingListUsed* posting_list_used,
                                         const IntegerIndexData& data) const;

  // Prepend multiple IntegerIndexData to the posting list. Data should be
  // sorted in ascending order (as defined by the less than operator for
  // IntegerIndexData)
  // If keep_prepended is true, whatever could be prepended is kept, otherwise
  // the posting list is reverted and left in its original state.
  //
  // RETURNS:
  //   The number of data that have been prepended to the posting list. If
  //   keep_prepended is false and reverted, then it returns 0.
  libtextclassifier3::StatusOr<uint32_t> PrependDataArray(
      PostingListUsed* posting_list_used, const IntegerIndexData* array,
      uint32_t num_data, bool keep_prepended) const;

  // Retrieves all data stored in the posting list.
  //
  // RETURNS:
  //   - On success, a vector of IntegerIndexData sorted by the reverse order of
  //     prepending.
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<IntegerIndexData>> GetData(
      const PostingListUsed* posting_list_used) const;

  // Same as GetData but appends data to data_arr_out.
  //
  // RETURNS:
  //   - OK on success, and data_arr_out will be appended IntegerIndexData
  //     sorted by the reverse order of prepending.
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::Status GetData(
      const PostingListUsed* posting_list_used,
      std::vector<IntegerIndexData>* data_arr_out) const;

  // Undo the last num_data data prepended. If num_data > number of data, then
  // we clear all data.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::Status PopFrontData(PostingListUsed* posting_list_used,
                                          uint32_t num_data) const;

  // Helper function to determine if posting list is full.
  bool IsFull(const PostingListUsed* posting_list_used) const {
    return GetSpecialData(posting_list_used, /*index=*/0).data().is_valid() &&
           GetSpecialData(posting_list_used, /*index=*/1).data().is_valid();
  }

 private:
  // Posting list layout formats:
  //
  // NOT_FULL
  // +-special-data-0--+-special-data-1--+------------+-----------------------+
  // |                 |                 |            |                       |
  // |data-start-offset|  Data::Invalid  | 0x00000000 |   (compressed) data   |
  // |                 |                 |            |                       |
  // +-----------------+-----------------+------------+-----------------------+
  //
  // ALMOST_FULL
  // +-special-data-0--+-special-data-1--+-----+------------------------------+
  // |                 |                 |     |                              |
  // |  Data::Invalid  |    1st data     |(pad)|      (compressed) data       |
  // |                 |                 |     |                              |
  // +-----------------+-----------------+-----+------------------------------+
  //
  // FULL
  // +-special-data-0--+-special-data-1--+-----+------------------------------+
  // |                 |                 |     |                              |
  // |    1st data     |    2nd data     |(pad)|      (compressed) data       |
  // |                 |                 |     |                              |
  // +-----------------+-----------------+-----+------------------------------+
  //
  // The first two uncompressed (special) data also implicitly encode
  // information about the size of the compressed data region.
  //
  // 1. If the posting list is NOT_FULL, then special_data_0 contains the byte
  //    offset of the start of the compressed data. Thus, the size of the
  //    compressed data is
  //    posting_list_used->size_in_bytes() - special_data_0.data_start_offset().
  //
  // 2. If posting list is ALMOST_FULL or FULL, then the compressed data region
  //    starts somewhere between
  //    [kSpecialDataSize, kSpecialDataSize + sizeof(IntegerIndexData) - 1] and
  //    ends at posting_list_used->size_in_bytes() - 1.
  //
  // EXAMPLE
  // Posting list storage. Posting list size: 36 bytes
  //
  // EMPTY!
  // +--- byte 0-11 ---+----- 12-23 -----+-------------- 24-35 ---------------+
  // |                 |                 |                                    |
  // |       36        |  Data::Invalid  |             0x00000000             |
  // |                 |                 |                                    |
  // +-----------------+-----------------+------------------------------------+
  //
  // Add IntegerIndexData(0x0FFFFCC3, 5)
  //   (DocumentId = 12, SectionId = 3; Key = 5)
  //   (VarInt64(5) is encoded as 10 (b'1010), requires 1 byte)
  // NOT FULL!
  // +--- byte 0-11 ---+----- 12-23 -----+------- 24-30 -------+--- 31-35 ----+
  // |                 |                 |                     | 0x0FFFFCC3   |
  // |       31        |  Data::Invalid  |     0x00000000      | VI64(5)      |
  // |                 |                 |                     |              |
  // +-----------------+-----------------+---------------------+--------------+
  //
  // Add IntegerIndexData(0x0FFFFB40, -2)
  //   (DocumentId = 18, SectionId = 0; Key = -2)
  //   (VarInt64(-2) is encoded as 3 (b'11), requires 1 byte)
  // Previous IntegerIndexData BasicHit delta varint encoding:
  //   0x0FFFFCC3 - 0x0FFFFB40 = 387, VarUnsignedInt(387) requires 2 bytes
  // +--- byte 0-11 ---+----- 12-23 -----+-- 24-27 ---+--- 28-32 ----+ 33-35 -+
  // |                 |                 |            | 0x0FFFFB40   |VUI(387)|
  // |       28        |  Data::Invalid  |    0x00    | VI64(-2)     |VI64(5) |
  // |                 |                 |            |              |        |
  // +-----------------+-----------------+------------+--------------+--------+
  //
  // Add IntegerIndexData(0x0FFFFA4A, 3)
  //   (DocumentId = 22, SectionId = 10; Key = 3)
  //   (VarInt64(3) is encoded as 6 (b'110), requires 1 byte)
  // Previous IntegerIndexData BasicHit delta varint encoding:
  //   0x0FFFFB40 - 0x0FFFFA4A = 246, VarUnsignedInt(246) requires 2 bytes
  // +--- byte 0-11 ---+----- 12-23 -----+---+--- 25-29 ----+ 30-32 -+ 33-35 -+
  // |                 |                 |   | 0x0FFFFA4A   |VUI(246)|VUI(387)|
  // |       25        |  Data::Invalid  |   | VI64(3)      |VI64(-2)|VI64(5) |
  // |                 |                 |   |              |        |        |
  // +-----------------+-----------------+---+--------------+--------+--------+
  //
  // Add IntegerIndexData(0x0FFFFA01, -4)
  //   (DocumentId = 23, SectionId = 1; Key = -4)
  //   (No VarInt64 for key, since it is stored in special data section)
  // Previous IntegerIndexData BasicHit delta varint encoding:
  //   0x0FFFFA4A - 0x0FFFFA01 = 73, VarUnsignedInt(73) requires 1 byte)
  // ALMOST_FULL!
  // +--- byte 0-11 ---+----- 12-23 -----+-- 24-27 ---+28-29+ 30-32 -+ 33-35 -+
  // |                 |   0x0FFFFA01    |            |(73) |VUI(246)|VUI(387)|
  // |  Data::Invalid  |   0xFFFFFFFF    |   (pad)    |(3)  |VI64(-2)|VI64(5) |
  // |                 |   0xFFFFFFFC    |            |     |        |        |
  // +-----------------+-----------------+------------+-----+--------+--------+
  //
  // Add IntegerIndexData(0x0FFFF904, 0)
  //   (DocumentId = 27, SectionId = 4; Key = 0)
  //   (No VarInt64 for key, since it is stored in special data section)
  // Previous IntegerIndexData:
  //   Since 0x0FFFFA01 - 0x0FFFF904 = 253 and VarInt64(-4) is encoded as 7
  //   (b'111), it requires only 3 bytes after compression. It's able to fit
  //   into the padding section.
  // Still ALMOST_FULL!
  // +--- byte 0-11 ---+----- 12-23 -----+---+ 25-27 -+28-29+ 30-32 -+ 33-35 -+
  // |                 |   0x0FFFF904    |   |VUI(253)|(73) |VUI(246)|VUI(387)|
  // |  Data::Invalid  |   0x00000000    |   |VI64(-4)|(3)  |VI64(-2)|VI64(5) |
  // |                 |   0x00000000    |   |        |     |        |        |
  // +-----------------+-----------------+---+--------+-----+--------+--------+
  //
  // Add IntegerIndexData(0x0FFFF8C3, -1)
  //   (DocumentId = 28, SectionId = 3; Key = -1)
  //   (No VarInt64 for key, since it is stored in special data section)
  //   (No VarUnsignedInt for previous IntegerIndexData BasicHit)
  // FULL!
  // +--- byte 0-11 ---+----- 12-23 -----+---+ 25-27 -+28-29+ 30-32 -+ 33-35 -+
  // |   0x0FFFF8C3    |   0x0FFFF904    |   |VUI(253)|(73) |VUI(246)|VUI(387)|
  // |   0xFFFFFFFF    |   0x00000000    |   |VI64(-4)|(3)  |VI64(-2)|VI64(5) |
  // |   0xFFFFFFFF    |   0x00000000    |   |        |     |        |        |
  // +-----------------+-----------------+---+--------+-----+--------+--------+

  // Helpers to determine what state the posting list is in.
  bool IsAlmostFull(const PostingListUsed* posting_list_used) const {
    return !GetSpecialData(posting_list_used, /*index=*/0).data().is_valid() &&
           GetSpecialData(posting_list_used, /*index=*/1).data().is_valid();
  }

  bool IsEmpty(const PostingListUsed* posting_list_used) const {
    return GetSpecialData(posting_list_used, /*index=*/0).data_start_offset() ==
               posting_list_used->size_in_bytes() &&
           !GetSpecialData(posting_list_used, /*index=*/1).data().is_valid();
  }

  // Returns false if both special data are invalid or if data start offset
  // stored in the special data is less than kSpecialDataSize or greater than
  // posting_list_used->size_in_bytes(). Returns true, otherwise.
  bool IsPostingListValid(const PostingListUsed* posting_list_used) const;

  // Prepend data to a posting list that is in the ALMOST_FULL state.
  //
  // RETURNS:
  //  - OK, if successful
  //  - INVALID_ARGUMENT if data is not less than the previously added data.
  libtextclassifier3::Status PrependDataToAlmostFull(
      PostingListUsed* posting_list_used, const IntegerIndexData& data) const;

  // Prepend data to a posting list that is in the EMPTY state. This will always
  // succeed because there are no pre-existing data and no validly constructed
  // posting list could fail to fit one data.
  void PrependDataToEmpty(PostingListUsed* posting_list_used,
                          const IntegerIndexData& data) const;

  // Prepend data to a posting list that is in the NOT_FULL state.
  //
  // RETURNS:
  //  - OK, if successful
  //  - INVALID_ARGUMENT if data is not less than the previously added data.
  libtextclassifier3::Status PrependDataToNotFull(
      PostingListUsed* posting_list_used, const IntegerIndexData& data,
      uint32_t offset) const;

  // Returns either 0 (FULL state), sizeof(IntegerIndexData) (ALMOST_FULL state)
  // or a byte offset between kSpecialDataSize and
  // posting_list_used->size_in_bytes() (inclusive) (NOT_FULL state).
  uint32_t GetStartByteOffset(const PostingListUsed* posting_list_used) const;

  // Sets special data 0 to properly reflect what start byte offset is (see
  // layout comment for further details).
  //
  // Returns false if offset > posting_list_used->size_in_bytes() or offset is
  // in range (kSpecialDataSize, sizeof(IntegerIndexData)) or
  // (sizeof(IntegerIndexData), 0). True, otherwise.
  bool SetStartByteOffset(PostingListUsed* posting_list_used,
                          uint32_t offset) const;

  // Helper for MoveFrom/GetData/PopFrontData. Adds limit number of data to out
  // or all data in the posting list if the posting list contains less than
  // limit number of data. out can be NULL.
  //
  // NOTE: If called with limit=1, pop=true on a posting list that transitioned
  // from NOT_FULL directly to FULL, GetDataInternal will not return the posting
  // list to NOT_FULL. Instead it will leave it in a valid state, but it will be
  // ALMOST_FULL.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::Status GetDataInternal(
      const PostingListUsed* posting_list_used, uint32_t limit, bool pop,
      std::vector<IntegerIndexData>* out) const;

  // Retrieves the value stored in the index-th special data.
  //
  // REQUIRES:
  //   0 <= index < kNumSpecialData.
  //
  // RETURNS:
  //   - A valid SpecialData<IntegerIndexData>.
  SpecialDataType GetSpecialData(const PostingListUsed* posting_list_used,
                                 uint32_t index) const;

  // Sets the value stored in the index-th special data to special_data.
  //
  // REQUIRES:
  //   0 <= index < kNumSpecialData.
  void SetSpecialData(PostingListUsed* posting_list_used, uint32_t index,
                      const SpecialDataType& special_data) const;

  // Prepends data to the memory region [offset - sizeof(IntegerIndexData),
  // offset - 1] and returns the new beginning of the region.
  //
  // RETURNS:
  //   - The new beginning of the padded region, if successful.
  //   - INVALID_ARGUMENT if data will not fit (uncompressed) between
  //       [kSpecialDataSize, offset - 1]
  libtextclassifier3::StatusOr<uint32_t> PrependDataUncompressed(
      PostingListUsed* posting_list_used, const IntegerIndexData& data,
      uint32_t offset) const;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_SERIALIZER_H_
