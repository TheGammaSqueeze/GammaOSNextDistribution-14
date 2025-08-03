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

#include "icing/index/main/posting-list-hit-serializer.h"

#include <cstdint>
#include <deque>
#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/hit-test-utils.h"

using testing::ElementsAre;
using testing::ElementsAreArray;
using testing::Eq;
using testing::IsEmpty;
using testing::Le;
using testing::Lt;

namespace icing {
namespace lib {

namespace {

struct HitElt {
  HitElt() = default;
  explicit HitElt(const Hit &hit_in) : hit(hit_in) {}

  static Hit get_hit(const HitElt &hit_elt) { return hit_elt.hit; }

  Hit hit;
};

TEST(PostingListHitSerializerTest, PostingListUsedPrependHitNotFull) {
  PostingListHitSerializer serializer;

  static const int kNumHits = 2551;
  static const size_t kHitsSize = kNumHits * sizeof(Hit);

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, kHitsSize));

  // Make used.
  Hit hit0(/*section_id=*/0, 0, /*term_frequency=*/56);
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit0));
  // Size = sizeof(uncompressed hit0)
  int expected_size = sizeof(Hit);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used), IsOkAndHolds(ElementsAre(hit0)));

  Hit hit1(/*section_id=*/0, 1, Hit::kDefaultTermFrequency);
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit1));
  // Size = sizeof(uncompressed hit1)
  //        + sizeof(hit0-hit1) + sizeof(hit0::term_frequency)
  expected_size += 2 + sizeof(Hit::TermFrequency);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit1, hit0)));

  Hit hit2(/*section_id=*/0, 2, /*term_frequency=*/56);
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit2));
  // Size = sizeof(uncompressed hit2)
  //        + sizeof(hit1-hit2)
  //        + sizeof(hit0-hit1) + sizeof(hit0::term_frequency)
  expected_size += 2;
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit2, hit1, hit0)));

  Hit hit3(/*section_id=*/0, 3, Hit::kDefaultTermFrequency);
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit3));
  // Size = sizeof(uncompressed hit3)
  //        + sizeof(hit2-hit3) + sizeof(hit2::term_frequency)
  //        + sizeof(hit1-hit2)
  //        + sizeof(hit0-hit1) + sizeof(hit0::term_frequency)
  expected_size += 2 + sizeof(Hit::TermFrequency);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit3, hit2, hit1, hit0)));
}

TEST(PostingListHitSerializerTest, PostingListUsedPrependHitAlmostFull) {
  PostingListHitSerializer serializer;

  int size = 2 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // Fill up the compressed region.
  // Transitions:
  // Adding hit0: EMPTY -> NOT_FULL
  // Adding hit1: NOT_FULL -> NOT_FULL
  // Adding hit2: NOT_FULL -> NOT_FULL
  Hit hit0(/*section_id=*/0, 0, Hit::kDefaultTermFrequency);
  Hit hit1 = CreateHit(hit0, /*desired_byte_length=*/2);
  Hit hit2 = CreateHit(hit1, /*desired_byte_length=*/2);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit0));
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit1));
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit2));
  // Size used will be 2+2+4=8 bytes
  int expected_size = sizeof(Hit::Value) + 2 + 2;
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit2, hit1, hit0)));

  // Add one more hit to transition NOT_FULL -> ALMOST_FULL
  Hit hit3 = CreateHit(hit2, /*desired_byte_length=*/3);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit3));
  // Compressed region would be 2+2+3+4=11 bytes, but the compressed region is
  // only 10 bytes. So instead, the posting list will transition to ALMOST_FULL.
  // The in-use compressed region will actually shrink from 8 bytes to 7 bytes
  // because the uncompressed version of hit2 will be overwritten with the
  // compressed delta of hit2. hit3 will be written to one of the special hits.
  // Because we're in ALMOST_FULL, the expected size is the size of the pl minus
  // the one hit used to mark the posting list as ALMOST_FULL.
  expected_size = size - sizeof(Hit);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit3, hit2, hit1, hit0)));

  // Add one more hit to transition ALMOST_FULL -> ALMOST_FULL
  Hit hit4 = CreateHit(hit3, /*desired_byte_length=*/2);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit4));
  // There are currently 7 bytes in use in the compressed region. hit3 will have
  // a 2-byte delta. That delta will fit in the compressed region (which will
  // now have 9 bytes in use), hit4 will be placed in one of the special hits
  // and the posting list will remain in ALMOST_FULL.
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit4, hit3, hit2, hit1, hit0)));

  // Add one more hit to transition ALMOST_FULL -> FULL
  Hit hit5 = CreateHit(hit4, /*desired_byte_length=*/2);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit5));
  // There are currently 9 bytes in use in the compressed region. hit4 will have
  // a 2-byte delta which will not fit in the compressed region. So hit4 will
  // remain in one of the special hits and hit5 will occupy the other, making
  // the posting list FULL.
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit5, hit4, hit3, hit2, hit1, hit0)));

  // The posting list is FULL. Adding another hit should fail.
  Hit hit6 = CreateHit(hit5, /*desired_byte_length=*/1);
  EXPECT_THAT(serializer.PrependHit(&pl_used, hit6),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListHitSerializerTest, PostingListUsedMinSize) {
  PostingListHitSerializer serializer;

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(
          &serializer, serializer.GetMinPostingListSize()));
  // PL State: EMPTY
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Eq(0));
  EXPECT_THAT(serializer.GetHits(&pl_used), IsOkAndHolds(IsEmpty()));

  // Add a hit, PL should shift to ALMOST_FULL state
  Hit hit0(/*section_id=*/0, 0, /*term_frequency=*/0,
           /*is_in_prefix_section=*/false,
           /*is_prefix_hit=*/true);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit0));
  // Size = sizeof(uncompressed hit0)
  int expected_size = sizeof(Hit);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used), IsOkAndHolds(ElementsAre(hit0)));

  // Add the smallest hit possible - no term_frequency and a delta of 1. PL
  // should shift to FULL state.
  Hit hit1(/*section_id=*/0, 0, /*term_frequency=*/0,
           /*is_in_prefix_section=*/true,
           /*is_prefix_hit=*/false);
  ICING_EXPECT_OK(serializer.PrependHit(&pl_used, hit1));
  // Size = sizeof(uncompressed hit1) + sizeof(uncompressed hit0)
  expected_size += sizeof(Hit);
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit1, hit0)));

  // Try to add the smallest hit possible. Should fail
  Hit hit2(/*section_id=*/0, 0, /*term_frequency=*/0,
           /*is_in_prefix_section=*/false,
           /*is_prefix_hit=*/false);
  EXPECT_THAT(serializer.PrependHit(&pl_used, hit2),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(serializer.GetBytesUsed(&pl_used), Le(expected_size));
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAre(hit1, hit0)));
}

TEST(PostingListHitSerializerTest,
     PostingListPrependHitArrayMinSizePostingList) {
  PostingListHitSerializer serializer;

  // Min Size = 10
  int size = serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<HitElt> hits_in;
  hits_in.emplace_back(Hit(1, 0, Hit::kDefaultTermFrequency));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  std::reverse(hits_in.begin(), hits_in.end());

  // Add five hits. The PL is in the empty state and an empty min size PL can
  // only fit two hits. So PrependHitArray should fail.
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t num_can_prepend,
      (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
          &pl_used, &hits_in[0], hits_in.size(), false)));
  EXPECT_THAT(num_can_prepend, Eq(2));

  int can_fit_hits = num_can_prepend;
  // The PL has room for 2 hits. We should be able to add them without any
  // problem, transitioning the PL from EMPTY -> ALMOST_FULL -> FULL
  const HitElt *hits_in_ptr = hits_in.data() + (hits_in.size() - 2);
  ICING_ASSERT_OK_AND_ASSIGN(
      num_can_prepend, (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
                           &pl_used, hits_in_ptr, can_fit_hits, false)));
  EXPECT_THAT(num_can_prepend, Eq(can_fit_hits));
  EXPECT_THAT(size, Eq(serializer.GetBytesUsed(&pl_used)));
  std::deque<Hit> hits_pushed;
  std::transform(hits_in.rbegin(),
                 hits_in.rend() - hits_in.size() + can_fit_hits,
                 std::front_inserter(hits_pushed), HitElt::get_hit);
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAreArray(hits_pushed)));
}

TEST(PostingListHitSerializerTest, PostingListPrependHitArrayPostingList) {
  PostingListHitSerializer serializer;

  // Size = 30
  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  std::vector<HitElt> hits_in;
  hits_in.emplace_back(Hit(1, 0, Hit::kDefaultTermFrequency));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  std::reverse(hits_in.begin(), hits_in.end());
  // The last hit is uncompressed and the four before it should only take one
  // byte. Total use = 8 bytes.
  // ----------------------
  // 29     delta(Hit #1)
  // 28     delta(Hit #2)
  // 27     delta(Hit #3)
  // 26     delta(Hit #4)
  // 25-22  Hit #5
  // 21-10  <unused>
  // 9-5    kSpecialHit
  // 4-0    Offset=22
  // ----------------------
  int byte_size = sizeof(Hit::Value) + hits_in.size() - 1;

  // Add five hits. The PL is in the empty state and should be able to fit all
  // five hits without issue, transitioning the PL from EMPTY -> NOT_FULL.
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t num_could_fit,
      (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
          &pl_used, &hits_in[0], hits_in.size(), false)));
  EXPECT_THAT(num_could_fit, Eq(hits_in.size()));
  EXPECT_THAT(byte_size, Eq(serializer.GetBytesUsed(&pl_used)));
  std::deque<Hit> hits_pushed;
  std::transform(hits_in.rbegin(), hits_in.rend(),
                 std::front_inserter(hits_pushed), HitElt::get_hit);
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAreArray(hits_pushed)));

  Hit first_hit = CreateHit(hits_in.begin()->hit, /*desired_byte_length=*/1);
  hits_in.clear();
  hits_in.emplace_back(first_hit);
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/2));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/1));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/2));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/3));
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/2));
  std::reverse(hits_in.begin(), hits_in.end());
  // Size increased by the deltas of these hits (1+2+1+2+3+2) = 11 bytes
  // ----------------------
  // 29     delta(Hit #1)
  // 28     delta(Hit #2)
  // 27     delta(Hit #3)
  // 26     delta(Hit #4)
  // 25     delta(Hit #5)
  // 24-23  delta(Hit #6)
  // 22     delta(Hit #7)
  // 21-20  delta(Hit #8)
  // 19-17  delta(Hit #9)
  // 16-15  delta(Hit #10)
  // 14-11  Hit #11
  // 10  <unused>
  // 9-5    kSpecialHit
  // 4-0    Offset=11
  // ----------------------
  byte_size += 11;

  // Add these 6 hits. The PL is currently in the NOT_FULL state and should
  // remain in the NOT_FULL state.
  ICING_ASSERT_OK_AND_ASSIGN(
      num_could_fit, (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
                         &pl_used, &hits_in[0], hits_in.size(), false)));
  EXPECT_THAT(num_could_fit, Eq(hits_in.size()));
  EXPECT_THAT(byte_size, Eq(serializer.GetBytesUsed(&pl_used)));
  // All hits from hits_in were added.
  std::transform(hits_in.rbegin(), hits_in.rend(),
                 std::front_inserter(hits_pushed), HitElt::get_hit);
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAreArray(hits_pushed)));

  first_hit = CreateHit(hits_in.begin()->hit, /*desired_byte_length=*/3);
  hits_in.clear();
  hits_in.emplace_back(first_hit);
  // ----------------------
  // 29     delta(Hit #1)
  // 28     delta(Hit #2)
  // 27     delta(Hit #3)
  // 26     delta(Hit #4)
  // 25     delta(Hit #5)
  // 24-23  delta(Hit #6)
  // 22     delta(Hit #7)
  // 21-20  delta(Hit #8)
  // 19-17  delta(Hit #9)
  // 16-15  delta(Hit #10)
  // 14-12  delta(Hit #11)
  // 11-10  <unused>
  // 9-5    Hit #12
  // 4-0    kSpecialHit
  // ----------------------
  byte_size = 25;

  // Add this 1 hit. The PL is currently in the NOT_FULL state and should
  // transition to the ALMOST_FULL state - even though there is still some
  // unused space.
  ICING_ASSERT_OK_AND_ASSIGN(
      num_could_fit, (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
                         &pl_used, &hits_in[0], hits_in.size(), false)));
  EXPECT_THAT(num_could_fit, Eq(hits_in.size()));
  EXPECT_THAT(byte_size, Eq(serializer.GetBytesUsed(&pl_used)));
  // All hits from hits_in were added.
  std::transform(hits_in.rbegin(), hits_in.rend(),
                 std::front_inserter(hits_pushed), HitElt::get_hit);
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAreArray(hits_pushed)));

  first_hit = CreateHit(hits_in.begin()->hit, /*desired_byte_length=*/1);
  hits_in.clear();
  hits_in.emplace_back(first_hit);
  hits_in.emplace_back(
      CreateHit(hits_in.rbegin()->hit, /*desired_byte_length=*/2));
  std::reverse(hits_in.begin(), hits_in.end());
  // ----------------------
  // 29     delta(Hit #1)
  // 28     delta(Hit #2)
  // 27     delta(Hit #3)
  // 26     delta(Hit #4)
  // 25     delta(Hit #5)
  // 24-23  delta(Hit #6)
  // 22     delta(Hit #7)
  // 21-20  delta(Hit #8)
  // 19-17  delta(Hit #9)
  // 16-15  delta(Hit #10)
  // 14-12  delta(Hit #11)
  // 11     delta(Hit #12)
  // 10     <unused>
  // 9-5    Hit #13
  // 4-0    Hit #14
  // ----------------------

  // Add these 2 hits. The PL is currently in the ALMOST_FULL state. Adding the
  // first hit should keep the PL in ALMOST_FULL because the delta between Hit
  // #12 and Hit #13 (1 byte) can fit in the unused area (2 bytes). Adding the
  // second hit should tranisition to the FULL state because the delta between
  // Hit #13 and Hit #14 (2 bytes) is larger than the remaining unused area
  // (1 byte).
  ICING_ASSERT_OK_AND_ASSIGN(
      num_could_fit, (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
                         &pl_used, &hits_in[0], hits_in.size(), false)));
  EXPECT_THAT(num_could_fit, Eq(hits_in.size()));
  EXPECT_THAT(size, Eq(serializer.GetBytesUsed(&pl_used)));
  // All hits from hits_in were added.
  std::transform(hits_in.rbegin(), hits_in.rend(),
                 std::front_inserter(hits_pushed), HitElt::get_hit);
  EXPECT_THAT(serializer.GetHits(&pl_used),
              IsOkAndHolds(ElementsAreArray(hits_pushed)));
}

TEST(PostingListHitSerializerTest, PostingListPrependHitArrayTooManyHits) {
  PostingListHitSerializer serializer;

  static constexpr int kNumHits = 128;
  static constexpr int kDeltaSize = 1;
  static constexpr int kTermFrequencySize = 1;
  static constexpr size_t kHitsSize =
      ((kNumHits * (kDeltaSize + kTermFrequencySize)) / 5) * 5;

  // Create an array with one too many hits
  std::vector<Hit> hits_in_too_many =
      CreateHits(kNumHits + 1, /*desired_byte_length=*/1);
  std::vector<HitElt> hit_elts_in_too_many;
  for (const Hit &hit : hits_in_too_many) {
    hit_elts_in_too_many.emplace_back(hit);
  }
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(
          &serializer, serializer.GetMinPostingListSize()));

  // PrependHitArray should fail because hit_elts_in_too_many is far too large
  // for the minimum size pl.
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t num_could_fit,
      (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
          &pl_used, &hit_elts_in_too_many[0], hit_elts_in_too_many.size(),
          false)));
  ASSERT_THAT(num_could_fit, Lt(hit_elts_in_too_many.size()));
  ASSERT_THAT(serializer.GetBytesUsed(&pl_used), Eq(0));
  ASSERT_THAT(serializer.GetHits(&pl_used), IsOkAndHolds(IsEmpty()));

  ICING_ASSERT_OK_AND_ASSIGN(
      pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, kHitsSize));
  // PrependHitArray should fail because hit_elts_in_too_many is one hit too
  // large for this pl.
  ICING_ASSERT_OK_AND_ASSIGN(
      num_could_fit, (serializer.PrependHitArray<HitElt, HitElt::get_hit>(
                         &pl_used, &hit_elts_in_too_many[0],
                         hit_elts_in_too_many.size(), false)));
  ASSERT_THAT(num_could_fit, Lt(hit_elts_in_too_many.size()));
  ASSERT_THAT(serializer.GetBytesUsed(&pl_used), Eq(0));
  ASSERT_THAT(serializer.GetHits(&pl_used), IsOkAndHolds(IsEmpty()));
}

TEST(PostingListHitSerializerTest,
     PostingListStatusJumpFromNotFullToFullAndBack) {
  PostingListHitSerializer serializer;

  const uint32_t pl_size = 3 * sizeof(Hit);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, pl_size));
  ICING_ASSERT_OK(serializer.PrependHit(&pl, Hit(Hit::kInvalidValue - 1, 0)));
  uint32_t bytes_used = serializer.GetBytesUsed(&pl);
  // Status not full.
  ASSERT_THAT(bytes_used,
              Le(pl_size - PostingListHitSerializer::kSpecialHitsSize));
  ICING_ASSERT_OK(serializer.PrependHit(&pl, Hit(Hit::kInvalidValue >> 2, 0)));
  // Status should jump to full directly.
  ASSERT_THAT(serializer.GetBytesUsed(&pl), Eq(pl_size));
  ICING_ASSERT_OK(serializer.PopFrontHits(&pl, 1));
  // Status should return to not full as before.
  ASSERT_THAT(serializer.GetBytesUsed(&pl), Eq(bytes_used));
}

TEST(PostingListHitSerializerTest, DeltaOverflow) {
  PostingListHitSerializer serializer;

  const uint32_t pl_size = 4 * sizeof(Hit);
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, pl_size));

  static const Hit::Value kOverflow[4] = {
      Hit::kInvalidValue >> 2,
      (Hit::kInvalidValue >> 2) * 2,
      (Hit::kInvalidValue >> 2) * 3,
      Hit::kInvalidValue - 1,
  };

  // Fit at least 4 ordinary values.
  for (Hit::Value v = 0; v < 4; v++) {
    ICING_EXPECT_OK(serializer.PrependHit(&pl, Hit(4 - v)));
  }

  // Cannot fit 4 overflow values.
  ICING_ASSERT_OK_AND_ASSIGN(
      pl, PostingListUsed::CreateFromUnitializedRegion(&serializer, pl_size));
  ICING_EXPECT_OK(serializer.PrependHit(&pl, Hit(kOverflow[3])));
  ICING_EXPECT_OK(serializer.PrependHit(&pl, Hit(kOverflow[2])));

  // Can fit only one more.
  ICING_EXPECT_OK(serializer.PrependHit(&pl, Hit(kOverflow[1])));
  EXPECT_THAT(serializer.PrependHit(&pl, Hit(kOverflow[0])),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST(PostingListHitSerializerTest, MoveFrom) {
  PostingListHitSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits1 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/1);
  for (const Hit &hit : hits1) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used1, hit));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits2 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/2);
  for (const Hit &hit : hits2) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used2, hit));
  }

  ICING_ASSERT_OK(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1));
  EXPECT_THAT(serializer.GetHits(&pl_used2),
              IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));
  EXPECT_THAT(serializer.GetHits(&pl_used1), IsOkAndHolds(IsEmpty()));
}

TEST(PostingListHitSerializerTest, MoveFromNullArgumentReturnsInvalidArgument) {
  PostingListHitSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits = CreateHits(/*num_hits=*/5, /*desired_byte_length=*/1);
  for (const Hit &hit : hits) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used1, hit));
  }

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used1, /*src=*/nullptr),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(serializer.GetHits(&pl_used1),
              IsOkAndHolds(ElementsAreArray(hits.rbegin(), hits.rend())));
}

TEST(PostingListHitSerializerTest,
     MoveFromInvalidPostingListReturnsInvalidArgument) {
  PostingListHitSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits1 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/1);
  for (const Hit &hit : hits1) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used1, hit));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits2 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/2);
  for (const Hit &hit : hits2) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used2, hit));
  }

  // Write invalid hits to the beginning of pl_used1 to make it invalid.
  Hit invalid_hit;
  Hit *first_hit = reinterpret_cast<Hit *>(pl_used1.posting_list_buffer());
  *first_hit = invalid_hit;
  ++first_hit;
  *first_hit = invalid_hit;
  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(serializer.GetHits(&pl_used2),
              IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));
}

TEST(PostingListHitSerializerTest,
     MoveToInvalidPostingListReturnsFailedPrecondition) {
  PostingListHitSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits1 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/1);
  for (const Hit &hit : hits1) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used1, hit));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits2 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/2);
  for (const Hit &hit : hits2) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used2, hit));
  }

  // Write invalid hits to the beginning of pl_used2 to make it invalid.
  Hit invalid_hit;
  Hit *first_hit = reinterpret_cast<Hit *>(pl_used2.posting_list_buffer());
  *first_hit = invalid_hit;
  ++first_hit;
  *first_hit = invalid_hit;
  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(serializer.GetHits(&pl_used1),
              IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));
}

TEST(PostingListHitSerializerTest, MoveToPostingListTooSmall) {
  PostingListHitSerializer serializer;

  int size = 3 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used1,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));
  std::vector<Hit> hits1 =
      CreateHits(/*num_hits=*/5, /*desired_byte_length=*/1);
  for (const Hit &hit : hits1) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used1, hit));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used2,
      PostingListUsed::CreateFromUnitializedRegion(
          &serializer, serializer.GetMinPostingListSize()));
  std::vector<Hit> hits2 =
      CreateHits(/*num_hits=*/1, /*desired_byte_length=*/2);
  for (const Hit &hit : hits2) {
    ICING_ASSERT_OK(serializer.PrependHit(&pl_used2, hit));
  }

  EXPECT_THAT(serializer.MoveFrom(/*dst=*/&pl_used2, /*src=*/&pl_used1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(serializer.GetHits(&pl_used1),
              IsOkAndHolds(ElementsAreArray(hits1.rbegin(), hits1.rend())));
  EXPECT_THAT(serializer.GetHits(&pl_used2),
              IsOkAndHolds(ElementsAreArray(hits2.rbegin(), hits2.rend())));
}

TEST(PostingListHitSerializerTest, PopHitsWithScores) {
  PostingListHitSerializer serializer;

  int size = 2 * serializer.GetMinPostingListSize();
  ICING_ASSERT_OK_AND_ASSIGN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromUnitializedRegion(&serializer, size));

  // This posting list is 20-bytes. Create four hits that will have deltas of
  // two bytes each and all of whom will have a non-default score. This posting
  // list will be almost_full.
  //
  // ----------------------
  // 19     score(Hit #0)
  // 18-17  delta(Hit #0)
  // 16     score(Hit #1)
  // 15-14  delta(Hit #1)
  // 13     score(Hit #2)
  // 12-11  delta(Hit #2)
  // 10     <unused>
  // 9-5    Hit #3
  // 4-0    kInvalidHitVal
  // ----------------------
  Hit hit0(/*section_id=*/0, /*document_id=*/0, /*score=*/5);
  Hit hit1 = CreateHit(hit0, /*desired_byte_length=*/2);
  Hit hit2 = CreateHit(hit1, /*desired_byte_length=*/2);
  Hit hit3 = CreateHit(hit2, /*desired_byte_length=*/2);
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit0));
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit1));
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit2));
  ICING_ASSERT_OK(serializer.PrependHit(&pl_used, hit3));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Hit> hits_out,
                             serializer.GetHits(&pl_used));
  EXPECT_THAT(hits_out, ElementsAre(hit3, hit2, hit1, hit0));

  // Now, pop the last hit. The posting list should contain the first three
  // hits.
  //
  // ----------------------
  // 19     score(Hit #0)
  // 18-17  delta(Hit #0)
  // 16     score(Hit #1)
  // 15-14  delta(Hit #1)
  // 13-10  <unused>
  // 9-5    Hit #2
  // 4-0    kInvalidHitVal
  // ----------------------
  ICING_ASSERT_OK(serializer.PopFrontHits(&pl_used, 1));
  ICING_ASSERT_OK_AND_ASSIGN(hits_out, serializer.GetHits(&pl_used));
  EXPECT_THAT(hits_out, ElementsAre(hit2, hit1, hit0));
}

}  // namespace

}  // namespace lib
}  // namespace icing
