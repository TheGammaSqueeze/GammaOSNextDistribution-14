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

#include "icing/util/character-iterator.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/icu-i18n-test-utils.h"

namespace icing {
namespace lib {

using ::testing::Eq;
using ::testing::IsFalse;
using ::testing::IsTrue;

TEST(CharacterIteratorTest, BasicUtf8) {
  constexpr std::string_view kText = "¿Dónde está la biblioteca?";
  CharacterIterator iterator(kText);
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));

  EXPECT_THAT(iterator.AdvanceToUtf8(4), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.AdvanceToUtf8(18), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.AdvanceToUtf8(28), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.AdvanceToUtf8(29), IsTrue());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(0));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/29, /*utf16_index=*/26,
                                   /*utf32_index=*/26)));

  EXPECT_THAT(iterator.RewindToUtf8(28), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.RewindToUtf8(18), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.RewindToUtf8(4), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.RewindToUtf8(0), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/0, /*utf16_index=*/0,
                                   /*utf32_index=*/0)));
}

TEST(CharacterIteratorTest, BasicUtf16) {
  constexpr std::string_view kText = "¿Dónde está la biblioteca?";
  CharacterIterator iterator(kText);
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));

  EXPECT_THAT(iterator.AdvanceToUtf16(2), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.AdvanceToUtf16(15), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.AdvanceToUtf16(25), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.AdvanceToUtf16(26), IsTrue());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(0));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/29, /*utf16_index=*/26,
                                   /*utf32_index=*/26)));

  EXPECT_THAT(iterator.RewindToUtf16(25), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.RewindToUtf16(15), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.RewindToUtf16(2), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.RewindToUtf8(0), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/0, /*utf16_index=*/0,
                                   /*utf32_index=*/0)));
}

TEST(CharacterIteratorTest, BasicUtf32) {
  constexpr std::string_view kText = "¿Dónde está la biblioteca?";
  CharacterIterator iterator(kText);
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));

  EXPECT_THAT(iterator.AdvanceToUtf32(2), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.AdvanceToUtf32(15), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.AdvanceToUtf32(25), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.AdvanceToUtf32(26), IsTrue());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(0));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/29, /*utf16_index=*/26,
                                   /*utf32_index=*/26)));

  EXPECT_THAT(iterator.RewindToUtf32(25), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("?"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/28, /*utf16_index=*/25,
                                   /*utf32_index=*/25)));

  EXPECT_THAT(iterator.RewindToUtf32(15), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/18, /*utf16_index=*/15,
                                   /*utf32_index=*/15)));

  EXPECT_THAT(iterator.RewindToUtf32(2), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/3, /*utf16_index=*/2,
                                   /*utf32_index=*/2)));

  EXPECT_THAT(iterator.RewindToUtf32(0), IsTrue());
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("¿"));
  EXPECT_THAT(iterator,
              Eq(CharacterIterator(kText, /*utf8_index=*/0, /*utf16_index=*/0,
                                   /*utf32_index=*/0)));
}

TEST(CharacterIteratorTest, InvalidUtf) {
  // "\255" is an invalid sequence.
  constexpr std::string_view kText = "foo \255 bar";
  CharacterIterator iterator(kText);

  // Try to advance to the 'b' in 'bar'. This will fail and leave us pointed at
  // the invalid sequence '\255'. Get CurrentChar() should return an invalid
  // character.
  EXPECT_THAT(iterator.AdvanceToUtf8(6), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(i18n_utils::kInvalidUChar32));
  CharacterIterator exp_iterator(kText, /*utf8_index=*/4, /*utf16_index=*/4,
                                 /*utf32_index=*/4);
  EXPECT_THAT(iterator, Eq(exp_iterator));

  EXPECT_THAT(iterator.AdvanceToUtf16(6), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(i18n_utils::kInvalidUChar32));
  EXPECT_THAT(iterator, Eq(exp_iterator));

  EXPECT_THAT(iterator.AdvanceToUtf32(6), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(i18n_utils::kInvalidUChar32));
  EXPECT_THAT(iterator, Eq(exp_iterator));

  // Create the iterator with it pointing at the 'b' in 'bar'.
  iterator = CharacterIterator(kText, /*utf8_index=*/6, /*utf16_index=*/6,
                               /*utf32_index=*/6);
  EXPECT_THAT(UCharToString(iterator.GetCurrentChar()), Eq("b"));

  // Try to advance to the last 'o' in 'foo'. This will fail and leave us
  // pointed at the ' ' before the invalid sequence '\255'.
  exp_iterator = CharacterIterator(kText, /*utf8_index=*/5, /*utf16_index=*/5,
                                   /*utf32_index=*/5);
  EXPECT_THAT(iterator.RewindToUtf8(2), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(' '));
  EXPECT_THAT(iterator, Eq(exp_iterator));

  EXPECT_THAT(iterator.RewindToUtf16(2), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(' '));
  EXPECT_THAT(iterator, Eq(exp_iterator));

  EXPECT_THAT(iterator.RewindToUtf32(2), IsFalse());
  EXPECT_THAT(iterator.GetCurrentChar(), Eq(' '));
  EXPECT_THAT(iterator, Eq(exp_iterator));
}

TEST(CharacterIteratorTest, MoveToUtfNegativeIndex) {
  constexpr std::string_view kText = "¿Dónde está la biblioteca?";

  CharacterIterator iterator_utf8(kText, /*utf8_index=*/-1, /*utf16_index=*/0,
                             /*utf32_index=*/0);
  // We should be able to successfully move when the index is negative.
  EXPECT_THAT(iterator_utf8.MoveToUtf8(0), IsTrue());
  // The character cache should be reset and contain the first character when
  // resetting to index 0.
  EXPECT_THAT(UCharToString(iterator_utf8.GetCurrentChar()), Eq("¿"));
  EXPECT_THAT(iterator_utf8.utf8_index(), Eq(0));
  EXPECT_THAT(iterator_utf8.utf16_index(), Eq(0));
  EXPECT_THAT(iterator_utf8.utf32_index(), Eq(0));

  CharacterIterator iterator_utf16(kText, /*utf8_index=*/0, /*utf16_index=*/-1,
                             /*utf32_index=*/0);
  EXPECT_THAT(iterator_utf16.MoveToUtf16(1), IsTrue());
  EXPECT_THAT(iterator_utf16.GetCurrentChar(), Eq('D'));
  EXPECT_THAT(iterator_utf16.utf8_index(), Eq(2));
  EXPECT_THAT(iterator_utf16.utf16_index(), Eq(1));
  EXPECT_THAT(iterator_utf16.utf32_index(), Eq(1));

  CharacterIterator iterator_utf32(kText, /*utf8_index=*/0, /*utf16_index=*/0,
                             /*utf32_index=*/-1);
  EXPECT_THAT(iterator_utf32.MoveToUtf32(2), IsTrue());
  EXPECT_THAT(UCharToString(iterator_utf32.GetCurrentChar()), Eq("ó"));
  EXPECT_THAT(iterator_utf32.utf8_index(), Eq(3));
  EXPECT_THAT(iterator_utf32.utf16_index(), Eq(2));
  EXPECT_THAT(iterator_utf32.utf32_index(), Eq(2));
}

}  // namespace lib
}  // namespace icing
