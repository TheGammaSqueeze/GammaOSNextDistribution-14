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

#ifndef ICING_JOIN_POSTING_LIST_JOIN_DATA_SERIALIZER_H_
#define ICING_JOIN_POSTING_LIST_JOIN_DATA_SERIALIZER_H_

#include <cstdint>
#include <cstring>
#include <limits>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// A serializer class to serialize JoinDataType to PostingListUsed. Usually
// JoinDataType is DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>,
// DocumentIdToJoinInfo<TermId>, or DocumentIdToJoinInfo<int64_t>.
//
// REQUIRES:
// - JoinDataType is comparable by operator <.
// - JoinDataType implements is_valid() method.
// - JoinDataType has static method GetInvalid() that returns a JoinDataType
//   instance containing invalid data.
template <typename JoinDataType>
class PostingListJoinDataSerializer : public PostingListSerializer {
 public:
  using SpecialDataType = SpecialData<JoinDataType>;
  static_assert(sizeof(SpecialDataType) == sizeof(JoinDataType), "");

  static constexpr uint32_t kSpecialDataSize =
      kNumSpecialData * sizeof(SpecialDataType);

  uint32_t GetDataTypeBytes() const override { return sizeof(JoinDataType); }

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

  // Prepend a JoinData to the posting list.
  //
  // RETURNS:
  //   - INVALID_ARGUMENT if !data.is_valid() or if data is not greater than the
  //     previously added data.
  //   - RESOURCE_EXHAUSTED if there is no more room to add data to the posting
  //     list.
  libtextclassifier3::Status PrependData(PostingListUsed* posting_list_used,
                                         const JoinDataType& data) const;

  // Prepend multiple JoinData to the posting list.
  // Data should be sorted in ascending order (as defined by the less than
  // operator for JoinData)
  // If keep_prepended is true, whatever could be prepended is kept, otherwise
  // the posting list is reverted and left in its original state.
  //
  // RETURNS:
  //   The number of data that have been prepended to the posting list. If
  //   keep_prepended is false and reverted, then it returns 0.
  libtextclassifier3::StatusOr<uint32_t> PrependDataArray(
      PostingListUsed* posting_list_used, const JoinDataType* array,
      uint32_t num_data, bool keep_prepended) const;

  // Retrieves all data stored in the posting list.
  //
  // RETURNS:
  //   - On success, a vector of JoinDataType sorted by the reverse order of
  //     prepending.
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<JoinDataType>> GetData(
      const PostingListUsed* posting_list_used) const;

  // Same as GetData but appends data to data_arr_out.
  //
  // RETURNS:
  //   - OK on success, and data_arr_out will be appended JoinDataType sorted by
  //     the reverse order of prepending.
  //   - INTERNAL_ERROR if the posting list has been corrupted somehow.
  libtextclassifier3::Status GetData(
      const PostingListUsed* posting_list_used,
      std::vector<JoinDataType>* data_arr_out) const;

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
  // In PostingListJoinDataSerializer, there is no compression, but we still use
  // the traditional posting list implementation.
  //
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
  //    [kSpecialDataSize, kSpecialDataSize + sizeof(JoinDataType) - 1] and ends
  //    at posting_list_used->size_in_bytes() - 1.
  //
  // EXAMPLE
  // JoinDataType = DocumentIdToJoinInfo<int64_t>. Posting list size: 48 bytes
  //
  // EMPTY!
  // +-- byte 0-11 --+---- 12-23 ----+------------ 24-47 -------------+
  // |               |               |                                |
  // |      48       | Data::Invalid |           0x00000000           |
  // |               |               |                                |
  // +---------------+---------------+--------------------------------+
  //
  // Add DocumentIdToJoinInfo<int64_t>(DocumentId = 12, JoinInteger = 5)
  // NOT FULL!
  // +-- byte 0-11 --+---- 12-23 ----+---- 24-35 ----+---- 36-47 ----+
  // |               |               |               | 12            |
  // |      36       | Data::Invalid |  0x00000000   |  5            |
  // |               |               |               |               |
  // +---------------+---------------+---------------+---------------+
  //
  // Add DocumentIdToJoinInfo<int64_t>(DocumentId = 18, JoinInteger = -2)
  // +-- byte 0-11 --+---- 12-23 ----+---- 24-35 ----+---- 36-47 ----+
  // |               |               | 18            | 12            |
  // |      24       | Data::Invalid | -2            |  5            |
  // |               |               |               |               |
  // +---------------+---------------+---------------+---------------+
  //
  // Add DocumentIdToJoinInfo<int64_t>(DocumentId = 22, JoinInteger = 3)
  // ALMOST_FULL!
  // +-- byte 0-11 --+---- 12-23 ----+---- 24-35 ----+---- 36-47 ----+
  // |               | 22            | 18            | 12            |
  // | Data::Invalid |  3            | -2            |  5            |
  // |               |               |               |               |
  // +---------------+---------------+---------------+---------------+
  //
  // Add DocumentIdToJoinInfo<int64_t>(DocumentId = 27, JoinInteger = 0)
  // FULL!
  // +-- byte 0-11 --+---- 12-23 ----+---- 24-35 ----+---- 36-47 ----+
  // | 27            | 22            | 18            | 12            |
  // |  0            |  3            | -2            |  5            |
  // |               |               |               |               |
  // +---------------+---------------+---------------+---------------+

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
      PostingListUsed* posting_list_used, const JoinDataType& data) const;

  // Prepend data to a posting list that is in the EMPTY state. This will always
  // succeed because there are no pre-existing data and no validly constructed
  // posting list could fail to fit one data.
  void PrependDataToEmpty(PostingListUsed* posting_list_used,
                          const JoinDataType& data) const;

  // Prepend data to a posting list that is in the NOT_FULL state.
  //
  // RETURNS:
  //  - OK, if successful
  //  - INVALID_ARGUMENT if data is not less than the previously added data.
  libtextclassifier3::Status PrependDataToNotFull(
      PostingListUsed* posting_list_used, const JoinDataType& data,
      uint32_t offset) const;

  // Returns either 0 (FULL state), sizeof(JoinDataType) (ALMOST_FULL state) or
  // a byte offset between kSpecialDataSize and
  // posting_list_used->size_in_bytes() (inclusive) (NOT_FULL state).
  uint32_t GetStartByteOffset(const PostingListUsed* posting_list_used) const;

  // Sets special data 0 to properly reflect what start byte offset is (see
  // layout comment for further details).
  //
  // Returns false if offset > posting_list_used->size_in_bytes() or offset is
  // in range (kSpecialDataSize, sizeof(JoinDataType)) or
  // (sizeof(JoinDataType), 0). True, otherwise.
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
      std::vector<JoinDataType>* out) const;

  // Retrieves the value stored in the index-th special data.
  //
  // REQUIRES:
  //   0 <= index < kNumSpecialData.
  //
  // RETURNS:
  //   - A valid SpecialData<JoinDataType>.
  SpecialDataType GetSpecialData(const PostingListUsed* posting_list_used,
                                 uint32_t index) const;

  // Sets the value stored in the index-th special data to special_data.
  //
  // REQUIRES:
  //   0 <= index < kNumSpecialData.
  void SetSpecialData(PostingListUsed* posting_list_used, uint32_t index,
                      const SpecialDataType& special_data) const;

  // Prepends data to the memory region
  // [offset - sizeof(JoinDataType), offset - 1] and
  // returns the new beginning of the region.
  //
  // RETURNS:
  //   - The new beginning of the padded region, if successful.
  //   - INVALID_ARGUMENT if data will not fit (uncompressed) between
  //       [kSpecialDataSize, offset - 1]
  libtextclassifier3::StatusOr<uint32_t> PrependDataUncompressed(
      PostingListUsed* posting_list_used, const JoinDataType& data,
      uint32_t offset) const;
};

template <typename JoinDataType>
uint32_t PostingListJoinDataSerializer<JoinDataType>::GetBytesUsed(
    const PostingListUsed* posting_list_used) const {
  // The special data will be included if they represent actual data. If they
  // represent the data start offset or the invalid data sentinel, they are not
  // included.
  return posting_list_used->size_in_bytes() -
         GetStartByteOffset(posting_list_used);
}

template <typename JoinDataType>
uint32_t
PostingListJoinDataSerializer<JoinDataType>::GetMinPostingListSizeToFit(
    const PostingListUsed* posting_list_used) const {
  if (IsFull(posting_list_used) || IsAlmostFull(posting_list_used)) {
    // If in either the FULL state or ALMOST_FULL state, this posting list *is*
    // the minimum size posting list that can fit these data. So just return the
    // size of the posting list.
    return posting_list_used->size_in_bytes();
  }

  // In NOT_FULL state, BytesUsed contains no special data. The minimum sized
  // posting list that would be guaranteed to fit these data would be
  // ALMOST_FULL, with kInvalidData in special data 0, the uncompressed data in
  // special data 1 and the n compressed data in the compressed region.
  // BytesUsed contains one uncompressed data and n compressed data. Therefore,
  // fitting these data into a posting list would require BytesUsed plus one
  // extra data.
  return GetBytesUsed(posting_list_used) + GetDataTypeBytes();
}

template <typename JoinDataType>
void PostingListJoinDataSerializer<JoinDataType>::Clear(
    PostingListUsed* posting_list_used) const {
  // Safe to ignore return value because posting_list_used->size_in_bytes() is
  // a valid argument.
  SetStartByteOffset(posting_list_used,
                     /*offset=*/posting_list_used->size_in_bytes());
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::MoveFrom(
    PostingListUsed* dst, PostingListUsed* src) const {
  ICING_RETURN_ERROR_IF_NULL(dst);
  ICING_RETURN_ERROR_IF_NULL(src);
  if (GetMinPostingListSizeToFit(src) > dst->size_in_bytes()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "src MinPostingListSizeToFit %d must be larger than size %d.",
        GetMinPostingListSizeToFit(src), dst->size_in_bytes()));
  }

  if (!IsPostingListValid(dst)) {
    return absl_ports::FailedPreconditionError(
        "Dst posting list is in an invalid state and can't be used!");
  }
  if (!IsPostingListValid(src)) {
    return absl_ports::InvalidArgumentError(
        "Cannot MoveFrom an invalid src posting list!");
  }

  // Pop just enough data that all of src's compressed data fit in
  // dst posting_list's compressed area. Then we can memcpy that area.
  std::vector<JoinDataType> data_arr;
  while (IsFull(src) || IsAlmostFull(src) ||
         (dst->size_in_bytes() - kSpecialDataSize < GetBytesUsed(src))) {
    if (!GetDataInternal(src, /*limit=*/1, /*pop=*/true, &data_arr).ok()) {
      return absl_ports::AbortedError(
          "Unable to retrieve data from src posting list.");
    }
  }

  // memcpy the area and set up start byte offset.
  Clear(dst);
  memcpy(dst->posting_list_buffer() + dst->size_in_bytes() - GetBytesUsed(src),
         src->posting_list_buffer() + GetStartByteOffset(src),
         GetBytesUsed(src));
  // Because we popped all data from src outside of the compressed area and we
  // guaranteed that GetBytesUsed(src) is less than dst->size_in_bytes() -
  // kSpecialDataSize. This is guaranteed to be a valid byte offset for the
  // NOT_FULL state, so ignoring the value is safe.
  SetStartByteOffset(dst, dst->size_in_bytes() - GetBytesUsed(src));

  // Put back remaining data.
  for (auto riter = data_arr.rbegin(); riter != data_arr.rend(); ++riter) {
    // PrependData may return:
    // - INVALID_ARGUMENT: if data is invalid or not less than the previous data
    // - RESOURCE_EXHAUSTED
    // RESOURCE_EXHAUSTED should be impossible because we've already assured
    // that there is enough room above.
    ICING_RETURN_IF_ERROR(PrependData(dst, *riter));
  }

  Clear(src);
  return libtextclassifier3::Status::OK;
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::PrependDataToAlmostFull(
    PostingListUsed* posting_list_used, const JoinDataType& data) const {
  SpecialDataType special_data = GetSpecialData(posting_list_used, /*index=*/1);
  if (data < special_data.data()) {
    return absl_ports::InvalidArgumentError(
        "JoinData being prepended must not be smaller than the most recent "
        "JoinData");
  }

  // Without compression, prepend a new data into ALMOST_FULL posting list will
  // change the posting list to FULL state. Therefore, set special data 0
  // directly.
  SetSpecialData(posting_list_used, /*index=*/0, SpecialDataType(data));
  return libtextclassifier3::Status::OK;
}

template <typename JoinDataType>
void PostingListJoinDataSerializer<JoinDataType>::PrependDataToEmpty(
    PostingListUsed* posting_list_used, const JoinDataType& data) const {
  // First data to be added. Just add verbatim, no compression.
  if (posting_list_used->size_in_bytes() == kSpecialDataSize) {
    // First data will be stored at special data 1.
    // Safe to ignore the return value because 1 < kNumSpecialData
    SetSpecialData(posting_list_used, /*index=*/1, SpecialDataType(data));
    // Safe to ignore the return value because sizeof(JoinDataType) is a valid
    // argument.
    SetStartByteOffset(posting_list_used, /*offset=*/sizeof(JoinDataType));
  } else {
    // Since this is the first data, size != kSpecialDataSize and
    // size % sizeof(JoinDataType) == 0, we know that there is room to fit
    // 'data' into the compressed region, so ValueOrDie is safe.
    uint32_t offset =
        PrependDataUncompressed(posting_list_used, data,
                                /*offset=*/posting_list_used->size_in_bytes())
            .ValueOrDie();
    // Safe to ignore the return value because PrependDataUncompressed is
    // guaranteed to return a valid offset.
    SetStartByteOffset(posting_list_used, offset);
  }
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::PrependDataToNotFull(
    PostingListUsed* posting_list_used, const JoinDataType& data,
    uint32_t offset) const {
  JoinDataType curr = JoinDataType::GetInvalid();
  memcpy(&curr, posting_list_used->posting_list_buffer() + offset,
         sizeof(JoinDataType));
  if (data < curr) {
    return absl_ports::InvalidArgumentError(
        "JoinData being prepended must not be smaller than the most recent "
        "JoinData");
  }

  if (offset >= kSpecialDataSize + sizeof(JoinDataType)) {
    offset =
        PrependDataUncompressed(posting_list_used, data, offset).ValueOrDie();
    SetStartByteOffset(posting_list_used, offset);
  } else {
    // The new data must be put in special data 1.
    SetSpecialData(posting_list_used, /*index=*/1, SpecialDataType(data));
    // State ALMOST_FULL. Safe to ignore the return value because
    // sizeof(JoinDataType) is a valid argument.
    SetStartByteOffset(posting_list_used, /*offset=*/sizeof(JoinDataType));
  }
  return libtextclassifier3::Status::OK;
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::PrependData(
    PostingListUsed* posting_list_used, const JoinDataType& data) const {
  if (!data.is_valid()) {
    return absl_ports::InvalidArgumentError("Cannot prepend an invalid data!");
  }
  if (!IsPostingListValid(posting_list_used)) {
    return absl_ports::FailedPreconditionError(
        "This PostingListUsed is in an invalid state and can't add any data!");
  }

  if (IsFull(posting_list_used)) {
    // State FULL: no space left.
    return absl_ports::ResourceExhaustedError("No more room for data");
  } else if (IsAlmostFull(posting_list_used)) {
    return PrependDataToAlmostFull(posting_list_used, data);
  } else if (IsEmpty(posting_list_used)) {
    PrependDataToEmpty(posting_list_used, data);
    return libtextclassifier3::Status::OK;
  } else {
    uint32_t offset = GetStartByteOffset(posting_list_used);
    return PrependDataToNotFull(posting_list_used, data, offset);
  }
}

template <typename JoinDataType>
libtextclassifier3::StatusOr<uint32_t>
PostingListJoinDataSerializer<JoinDataType>::PrependDataArray(
    PostingListUsed* posting_list_used, const JoinDataType* array,
    uint32_t num_data, bool keep_prepended) const {
  if (!IsPostingListValid(posting_list_used)) {
    return 0;
  }

  uint32_t i;
  for (i = 0; i < num_data; ++i) {
    if (!PrependData(posting_list_used, array[i]).ok()) {
      break;
    }
  }
  if (i != num_data && !keep_prepended) {
    // Didn't fit. Undo everything and check that we have the same offset as
    // before. PopFrontData guarantees that it will remove all 'i' data so long
    // as there are at least 'i' data in the posting list, which we know there
    // are.
    ICING_RETURN_IF_ERROR(PopFrontData(posting_list_used, /*num_data=*/i));
    return 0;
  }
  return i;
}

template <typename JoinDataType>
libtextclassifier3::StatusOr<std::vector<JoinDataType>>
PostingListJoinDataSerializer<JoinDataType>::GetData(
    const PostingListUsed* posting_list_used) const {
  std::vector<JoinDataType> data_arr_out;
  ICING_RETURN_IF_ERROR(GetData(posting_list_used, &data_arr_out));
  return data_arr_out;
}

template <typename JoinDataType>
libtextclassifier3::Status PostingListJoinDataSerializer<JoinDataType>::GetData(
    const PostingListUsed* posting_list_used,
    std::vector<JoinDataType>* data_arr_out) const {
  return GetDataInternal(posting_list_used,
                         /*limit=*/std::numeric_limits<uint32_t>::max(),
                         /*pop=*/false, data_arr_out);
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::PopFrontData(
    PostingListUsed* posting_list_used, uint32_t num_data) const {
  if (num_data == 1 && IsFull(posting_list_used)) {
    // The PL is in FULL state which means that we save 2 uncompressed data in
    // the 2 special postions. But FULL state may be reached by 2 different
    // states.
    // (1) In ALMOST_FULL state
    // +------------------+-----------------+-----+---------------------------+
    // |Data::Invalid     |1st data         |(pad)|(compressed) data          |
    // |                  |                 |     |                           |
    // +------------------+-----------------+-----+---------------------------+
    // When we prepend another data, we can only put it at special data 0, and
    // thus get a FULL PL
    // +------------------+-----------------+-----+---------------------------+
    // |new 1st data      |original 1st data|(pad)|(compressed) data          |
    // |                  |                 |     |                           |
    // +------------------+-----------------+-----+---------------------------+
    //
    // (2) In NOT_FULL state
    // +------------------+-----------------+-------+---------+---------------+
    // |data-start-offset |Data::Invalid    |(pad)  |1st data |(compressed)   |
    // |                  |                 |       |         |data           |
    // +------------------+-----------------+-------+---------+---------------+
    // When we prepend another data, we can reach any of the 3 following
    // scenarios:
    // (2.1) NOT_FULL
    // if the space of pad and original 1st data can accommodate the new 1st
    // data and the encoded delta value.
    // +------------------+-----------------+-----+--------+------------------+
    // |data-start-offset |Data::Invalid    |(pad)|new     |(compressed) data |
    // |                  |                 |     |1st data|                  |
    // +------------------+-----------------+-----+--------+------------------+
    // (2.2) ALMOST_FULL
    // If the space of pad and original 1st data cannot accommodate the new 1st
    // data and the encoded delta value but can accommodate the encoded delta
    // value only. We can put the new 1st data at special position 1.
    // +------------------+-----------------+---------+-----------------------+
    // |Data::Invalid     |new 1st data     |(pad)    |(compressed) data      |
    // |                  |                 |         |                       |
    // +------------------+-----------------+---------+-----------------------+
    // (2.3) FULL
    // In very rare case, it cannot even accommodate only the encoded delta
    // value. we can move the original 1st data into special position 1 and the
    // new 1st data into special position 0. This may happen because we use
    // VarInt encoding method which may make the encoded value longer (about
    // 4/3 times of original)
    // +------------------+-----------------+--------------+------------------+
    // |new 1st data      |original 1st data|(pad)         |(compressed) data |
    // |                  |                 |              |                  |
    // +------------------+-----------------+--------------+------------------+
    //
    // Suppose now the PL is in FULL state. But we don't know whether it arrived
    // this state from NOT_FULL (like (2.3)) or from ALMOST_FULL (like (1)).
    // We'll return to ALMOST_FULL state like (1) if we simply pop the new 1st
    // data, but we want to make the prepending operation "reversible". So
    // there should be some way to return to NOT_FULL if possible. A simple way
    // to do is:
    // - Pop 2 data out of the PL to state ALMOST_FULL or NOT_FULL.
    // - Add the second data ("original 1st data") back.
    //
    // Then we can return to the correct original states of (2.1) or (1). This
    // makes our prepending operation reversible.
    std::vector<JoinDataType> out;

    // Popping 2 data should never fail because we've just ensured that the
    // posting list is in the FULL state.
    ICING_RETURN_IF_ERROR(
        GetDataInternal(posting_list_used, /*limit=*/2, /*pop=*/true, &out));

    // PrependData should never fail because:
    // - out[1] is a valid data less than all previous data in the posting list.
    // - There's no way that the posting list could run out of room because it
    //   previously stored these 2 data.
    ICING_RETURN_IF_ERROR(PrependData(posting_list_used, out[1]));
  } else if (num_data > 0) {
    return GetDataInternal(posting_list_used, /*limit=*/num_data, /*pop=*/true,
                           /*out=*/nullptr);
  }
  return libtextclassifier3::Status::OK;
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataSerializer<JoinDataType>::GetDataInternal(
    const PostingListUsed* posting_list_used, uint32_t limit, bool pop,
    std::vector<JoinDataType>* out) const {
  uint32_t offset = GetStartByteOffset(posting_list_used);
  uint32_t count = 0;

  // First traverse the first two special positions.
  while (count < limit && offset < kSpecialDataSize) {
    // offset / sizeof(JoinDataType) < kNumSpecialData
    // because of the check above.
    SpecialDataType special_data = GetSpecialData(
        posting_list_used, /*index=*/offset / sizeof(JoinDataType));
    if (out != nullptr) {
      out->push_back(special_data.data());
    }
    offset += sizeof(JoinDataType);
    ++count;
  }

  // - We don't compress the data.
  // - The posting list size is a multiple of data type bytes.
  // So offset of the first non-special data is guaranteed to be at
  // kSpecialDataSize if in ALMOST_FULL or FULL state. In fact, we must not
  // apply padding skipping logic here when still storing uncompressed data,
  // because in this case 0 bytes are meanful (e.g. inverted doc id byte = 0).
  while (count < limit && offset < posting_list_used->size_in_bytes()) {
    JoinDataType data = JoinDataType::GetInvalid();
    memcpy(&data, posting_list_used->posting_list_buffer() + offset,
           sizeof(JoinDataType));
    offset += sizeof(JoinDataType);
    if (out != nullptr) {
      out->push_back(data);
    }
    ++count;
  }

  if (pop) {
    PostingListUsed* mutable_posting_list_used =
        const_cast<PostingListUsed*>(posting_list_used);
    // Modify the posting list so that we pop all data actually traversed.
    if (offset >= kSpecialDataSize &&
        offset < posting_list_used->size_in_bytes()) {
      memset(
          mutable_posting_list_used->posting_list_buffer() + kSpecialDataSize,
          0, offset - kSpecialDataSize);
    }
    SetStartByteOffset(mutable_posting_list_used, offset);
  }

  return libtextclassifier3::Status::OK;
}

template <typename JoinDataType>
typename PostingListJoinDataSerializer<JoinDataType>::SpecialDataType
PostingListJoinDataSerializer<JoinDataType>::GetSpecialData(
    const PostingListUsed* posting_list_used, uint32_t index) const {
  // It is ok to temporarily construct a SpecialData with offset = 0 since we're
  // going to overwrite it by memcpy.
  SpecialDataType special_data(0);
  memcpy(&special_data,
         posting_list_used->posting_list_buffer() +
             index * sizeof(SpecialDataType),
         sizeof(SpecialDataType));
  return special_data;
}

template <typename JoinDataType>
void PostingListJoinDataSerializer<JoinDataType>::SetSpecialData(
    PostingListUsed* posting_list_used, uint32_t index,
    const SpecialDataType& special_data) const {
  memcpy(posting_list_used->posting_list_buffer() +
             index * sizeof(SpecialDataType),
         &special_data, sizeof(SpecialDataType));
}

template <typename JoinDataType>
bool PostingListJoinDataSerializer<JoinDataType>::IsPostingListValid(
    const PostingListUsed* posting_list_used) const {
  if (IsAlmostFull(posting_list_used)) {
    // Special data 1 should hold a valid data.
    if (!GetSpecialData(posting_list_used, /*index=*/1).data().is_valid()) {
      ICING_LOG(ERROR)
          << "Both special data cannot be invalid at the same time.";
      return false;
    }
  } else if (!IsFull(posting_list_used)) {
    // NOT_FULL. Special data 0 should hold a valid offset.
    SpecialDataType special_data =
        GetSpecialData(posting_list_used, /*index=*/0);
    if (special_data.data_start_offset() > posting_list_used->size_in_bytes() ||
        special_data.data_start_offset() < kSpecialDataSize) {
      ICING_LOG(ERROR) << "Offset: " << special_data.data_start_offset()
                       << " size: " << posting_list_used->size_in_bytes()
                       << " sp size: " << kSpecialDataSize;
      return false;
    }
  }
  return true;
}

template <typename JoinDataType>
uint32_t PostingListJoinDataSerializer<JoinDataType>::GetStartByteOffset(
    const PostingListUsed* posting_list_used) const {
  if (IsFull(posting_list_used)) {
    return 0;
  } else if (IsAlmostFull(posting_list_used)) {
    return sizeof(JoinDataType);
  } else {
    return GetSpecialData(posting_list_used, /*index=*/0).data_start_offset();
  }
}

template <typename JoinDataType>
bool PostingListJoinDataSerializer<JoinDataType>::SetStartByteOffset(
    PostingListUsed* posting_list_used, uint32_t offset) const {
  if (offset > posting_list_used->size_in_bytes()) {
    ICING_LOG(ERROR) << "offset cannot be a value greater than size "
                     << posting_list_used->size_in_bytes() << ". offset is "
                     << offset << ".";
    return false;
  }
  if (offset < kSpecialDataSize && offset > sizeof(JoinDataType)) {
    ICING_LOG(ERROR) << "offset cannot be a value between ("
                     << sizeof(JoinDataType) << ", " << kSpecialDataSize
                     << "). offset is " << offset << ".";
    return false;
  }
  if (offset < sizeof(JoinDataType) && offset != 0) {
    ICING_LOG(ERROR) << "offset cannot be a value between (0, "
                     << sizeof(JoinDataType) << "). offset is " << offset
                     << ".";
    return false;
  }

  if (offset >= kSpecialDataSize) {
    // NOT_FULL state.
    SetSpecialData(posting_list_used, /*index=*/0, SpecialDataType(offset));
    SetSpecialData(posting_list_used, /*index=*/1,
                   SpecialDataType(JoinDataType::GetInvalid()));
  } else if (offset == sizeof(JoinDataType)) {
    // ALMOST_FULL state.
    SetSpecialData(posting_list_used, /*index=*/0,
                   SpecialDataType(JoinDataType::GetInvalid()));
  }
  // Nothing to do for the FULL state - the offset isn't actually stored
  // anywhere and both 2 special data hold valid data.
  return true;
}

template <typename JoinDataType>
libtextclassifier3::StatusOr<uint32_t>
PostingListJoinDataSerializer<JoinDataType>::PrependDataUncompressed(
    PostingListUsed* posting_list_used, const JoinDataType& data,
    uint32_t offset) const {
  if (offset < kSpecialDataSize + sizeof(JoinDataType)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Not enough room to prepend JoinData at offset %d.", offset));
  }
  offset -= sizeof(JoinDataType);
  memcpy(posting_list_used->posting_list_buffer() + offset, &data,
         sizeof(JoinDataType));
  return offset;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_POSTING_LIST_JOIN_DATA_SERIALIZER_H_
