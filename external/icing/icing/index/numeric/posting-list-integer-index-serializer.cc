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

#include "icing/index/numeric/posting-list-integer-index-serializer.h"

#include <cstdint>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

uint32_t PostingListIntegerIndexSerializer::GetBytesUsed(
    const PostingListUsed* posting_list_used) const {
  // The special data will be included if they represent actual data. If they
  // represent the data start offset or the invalid data sentinel, they are not
  // included.
  return posting_list_used->size_in_bytes() -
         GetStartByteOffset(posting_list_used);
}

uint32_t PostingListIntegerIndexSerializer::GetMinPostingListSizeToFit(
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

void PostingListIntegerIndexSerializer::Clear(
    PostingListUsed* posting_list_used) const {
  // Safe to ignore return value because posting_list_used->size_in_bytes() is
  // a valid argument.
  SetStartByteOffset(posting_list_used,
                     /*offset=*/posting_list_used->size_in_bytes());
}

libtextclassifier3::Status PostingListIntegerIndexSerializer::MoveFrom(
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
  std::vector<IntegerIndexData> data_arr;
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

libtextclassifier3::Status
PostingListIntegerIndexSerializer::PrependDataToAlmostFull(
    PostingListUsed* posting_list_used, const IntegerIndexData& data) const {
  SpecialDataType special_data = GetSpecialData(posting_list_used, /*index=*/1);
  if (special_data.data().basic_hit() < data.basic_hit()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "BasicHit %d being prepended must not be greater than the most recent"
        "BasicHit %d",
        data.basic_hit().value(), special_data.data().basic_hit().value()));
  }

  // TODO(b/259743562): [Optimization 2] compression
  // Without compression, prepend a new data into ALMOST_FULL posting list will
  // change the posting list to FULL state. Therefore, set special data 0
  // directly.
  SetSpecialData(posting_list_used, /*index=*/0, SpecialDataType(data));
  return libtextclassifier3::Status::OK;
}

void PostingListIntegerIndexSerializer::PrependDataToEmpty(
    PostingListUsed* posting_list_used, const IntegerIndexData& data) const {
  // First data to be added. Just add verbatim, no compression.
  if (posting_list_used->size_in_bytes() == kSpecialDataSize) {
    // First data will be stored at special data 1.
    // Safe to ignore the return value because 1 < kNumSpecialData
    SetSpecialData(posting_list_used, /*index=*/1, SpecialDataType(data));
    // Safe to ignore the return value because sizeof(IntegerIndexData) is a
    // valid argument.
    SetStartByteOffset(posting_list_used,
                       /*offset=*/sizeof(IntegerIndexData));
  } else {
    // Since this is the first data, size != kSpecialDataSize and
    // size % sizeof(IntegerIndexData) == 0, we know that there is room to fit
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

libtextclassifier3::Status
PostingListIntegerIndexSerializer::PrependDataToNotFull(
    PostingListUsed* posting_list_used, const IntegerIndexData& data,
    uint32_t offset) const {
  IntegerIndexData cur;
  memcpy(&cur, posting_list_used->posting_list_buffer() + offset,
         sizeof(IntegerIndexData));
  if (cur.basic_hit() < data.basic_hit()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "BasicHit %d being prepended must not be greater than the most recent"
        "BasicHit %d",
        data.basic_hit().value(), cur.basic_hit().value()));
  }

  // TODO(b/259743562): [Optimization 2] compression
  if (offset >= kSpecialDataSize + sizeof(IntegerIndexData)) {
    offset =
        PrependDataUncompressed(posting_list_used, data, offset).ValueOrDie();
    SetStartByteOffset(posting_list_used, offset);
  } else {
    // The new data must be put in special data 1.
    SetSpecialData(posting_list_used, /*index=*/1, SpecialDataType(data));
    // State ALMOST_FULL. Safe to ignore the return value because
    // sizeof(IntegerIndexData) is a valid argument.
    SetStartByteOffset(posting_list_used, /*offset=*/sizeof(IntegerIndexData));
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status PostingListIntegerIndexSerializer::PrependData(
    PostingListUsed* posting_list_used, const IntegerIndexData& data) const {
  static_assert(
      sizeof(BasicHit::Value) <= sizeof(uint64_t),
      "BasicHit::Value cannot be larger than 8 bytes because the delta "
      "must be able to fit in 8 bytes.");

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

libtextclassifier3::StatusOr<uint32_t>
PostingListIntegerIndexSerializer::PrependDataArray(
    PostingListUsed* posting_list_used, const IntegerIndexData* array,
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

libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
PostingListIntegerIndexSerializer::GetData(
    const PostingListUsed* posting_list_used) const {
  std::vector<IntegerIndexData> data_arr_out;
  ICING_RETURN_IF_ERROR(GetData(posting_list_used, &data_arr_out));
  return data_arr_out;
}

libtextclassifier3::Status PostingListIntegerIndexSerializer::GetData(
    const PostingListUsed* posting_list_used,
    std::vector<IntegerIndexData>* data_arr_out) const {
  return GetDataInternal(posting_list_used,
                         /*limit=*/std::numeric_limits<uint32_t>::max(),
                         /*pop=*/false, data_arr_out);
}

libtextclassifier3::Status PostingListIntegerIndexSerializer::PopFrontData(
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
    std::vector<IntegerIndexData> out;

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

libtextclassifier3::Status PostingListIntegerIndexSerializer::GetDataInternal(
    const PostingListUsed* posting_list_used, uint32_t limit, bool pop,
    std::vector<IntegerIndexData>* out) const {
  // TODO(b/259743562): [Optimization 2] handle compressed data

  uint32_t offset = GetStartByteOffset(posting_list_used);
  uint32_t count = 0;

  // First traverse the first two special positions.
  while (count < limit && offset < kSpecialDataSize) {
    // offset / sizeof(IntegerIndexData) < kNumSpecialData because of the check
    // above.
    SpecialDataType special_data =
        GetSpecialData(posting_list_used,
                       /*index=*/offset / sizeof(IntegerIndexData));
    if (out != nullptr) {
      out->push_back(special_data.data());
    }
    offset += sizeof(IntegerIndexData);
    ++count;
  }

  // - We don't compress the data now.
  // - The posting list size is a multiple of data type bytes.
  // So offset of the first non-special data is guaranteed to be at
  // kSpecialDataSize if in ALMOST_FULL or FULL state. In fact, we must not
  // apply padding skipping logic here when still storing uncompressed data,
  // because in this case 0 bytes are meanful (e.g. inverted doc id byte = 0).
  // TODO(b/259743562): [Optimization 2] deal with padding skipping logic when
  //                    apply data compression.

  while (count < limit && offset < posting_list_used->size_in_bytes()) {
    IntegerIndexData data;
    memcpy(&data, posting_list_used->posting_list_buffer() + offset,
           sizeof(IntegerIndexData));
    offset += sizeof(IntegerIndexData);
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

PostingListIntegerIndexSerializer::SpecialDataType
PostingListIntegerIndexSerializer::GetSpecialData(
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

void PostingListIntegerIndexSerializer::SetSpecialData(
    PostingListUsed* posting_list_used, uint32_t index,
    const SpecialDataType& special_data) const {
  memcpy(posting_list_used->posting_list_buffer() +
             index * sizeof(SpecialDataType),
         &special_data, sizeof(SpecialDataType));
}

bool PostingListIntegerIndexSerializer::IsPostingListValid(
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

uint32_t PostingListIntegerIndexSerializer::GetStartByteOffset(
    const PostingListUsed* posting_list_used) const {
  if (IsFull(posting_list_used)) {
    return 0;
  } else if (IsAlmostFull(posting_list_used)) {
    return sizeof(IntegerIndexData);
  } else {
    return GetSpecialData(posting_list_used, /*index=*/0).data_start_offset();
  }
}

bool PostingListIntegerIndexSerializer::SetStartByteOffset(
    PostingListUsed* posting_list_used, uint32_t offset) const {
  if (offset > posting_list_used->size_in_bytes()) {
    ICING_LOG(ERROR) << "offset cannot be a value greater than size "
                     << posting_list_used->size_in_bytes() << ". offset is "
                     << offset << ".";
    return false;
  }
  if (offset < kSpecialDataSize && offset > sizeof(IntegerIndexData)) {
    ICING_LOG(ERROR) << "offset cannot be a value between ("
                     << sizeof(IntegerIndexData) << ", " << kSpecialDataSize
                     << "). offset is " << offset << ".";
    return false;
  }
  if (offset < sizeof(IntegerIndexData) && offset != 0) {
    ICING_LOG(ERROR) << "offset cannot be a value between (0, "
                     << sizeof(IntegerIndexData) << "). offset is " << offset
                     << ".";
    return false;
  }

  if (offset >= kSpecialDataSize) {
    // NOT_FULL state.
    SetSpecialData(posting_list_used, /*index=*/0, SpecialDataType(offset));
    SetSpecialData(posting_list_used, /*index=*/1,
                   SpecialDataType(IntegerIndexData()));
  } else if (offset == sizeof(IntegerIndexData)) {
    // ALMOST_FULL state.
    SetSpecialData(posting_list_used, /*index=*/0,
                   SpecialDataType(IntegerIndexData()));
  }
  // Nothing to do for the FULL state - the offset isn't actually stored
  // anywhere and both 2 special data hold valid data.
  return true;
}

libtextclassifier3::StatusOr<uint32_t>
PostingListIntegerIndexSerializer::PrependDataUncompressed(
    PostingListUsed* posting_list_used, const IntegerIndexData& data,
    uint32_t offset) const {
  if (offset < kSpecialDataSize + sizeof(IntegerIndexData)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Not enough room to prepend IntegerIndexData at offset %d.", offset));
  }
  offset -= sizeof(IntegerIndexData);
  memcpy(posting_list_used->posting_list_buffer() + offset, &data,
         sizeof(IntegerIndexData));
  return offset;
}

}  // namespace lib
}  // namespace icing
