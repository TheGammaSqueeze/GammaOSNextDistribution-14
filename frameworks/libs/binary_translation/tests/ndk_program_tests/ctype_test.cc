/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "gtest/gtest.h"

#include <ctype.h>

TEST(Ctype, CharType) {
  EXPECT_TRUE(isalnum('A'));
  EXPECT_TRUE(isalpha('A'));
  EXPECT_TRUE(isascii('A'));
  EXPECT_FALSE(isblank('A'));
  EXPECT_FALSE(iscntrl('A'));
  EXPECT_FALSE(isdigit('A'));
  EXPECT_TRUE(isgraph('A'));
  EXPECT_FALSE(islower('A'));
  EXPECT_TRUE(isprint('A'));
  EXPECT_FALSE(ispunct('A'));
  EXPECT_FALSE(isspace('A'));
  EXPECT_TRUE(isupper('A'));
  EXPECT_TRUE(isxdigit('A'));

  EXPECT_TRUE(isalnum('0'));
  EXPECT_FALSE(isalpha('0'));
  EXPECT_TRUE(isascii('0'));
  EXPECT_FALSE(isblank('A'));
  EXPECT_FALSE(iscntrl('0'));
  EXPECT_TRUE(isdigit('0'));
  EXPECT_TRUE(isgraph('0'));
  EXPECT_FALSE(islower('0'));
  EXPECT_TRUE(isprint('0'));
  EXPECT_FALSE(ispunct('0'));
  EXPECT_FALSE(isspace('0'));
  EXPECT_FALSE(isupper('0'));
  EXPECT_TRUE(isxdigit('0'));

  EXPECT_FALSE(isalnum(' '));
  EXPECT_FALSE(isalpha(' '));
  EXPECT_TRUE(isascii(' '));
  EXPECT_TRUE(isblank(' '));
  EXPECT_FALSE(iscntrl(' '));
  EXPECT_FALSE(isdigit(' '));
  EXPECT_FALSE(isgraph(' '));
  EXPECT_FALSE(islower(' '));
  EXPECT_TRUE(isprint(' '));
  EXPECT_FALSE(ispunct(' '));
  EXPECT_TRUE(isspace(' '));
  EXPECT_FALSE(isupper(' '));
  EXPECT_FALSE(isxdigit(' '));

  EXPECT_FALSE(isblank('\n'));
  EXPECT_TRUE(isspace('\n'));
  EXPECT_TRUE(ispunct(','));
  EXPECT_FALSE(isprint(1));
  EXPECT_TRUE(iscntrl(1));
  EXPECT_FALSE(isascii(-1));
}

using CharTypeFunc = int (*)(int chr);

// The ctype functions must not be inlined to test their function trampolines.  To prevent inlining,
// make an indirect call through a volatile function pointer.
#define NO_INLINE(func)                    \
  volatile CharTypeFunc func##_tmp = func; \
  CharTypeFunc func = func##_tmp;

TEST(Ctype, CharTypeNoInline) {
  NO_INLINE(isalnum);
  NO_INLINE(isalpha);
  NO_INLINE(isascii);
  NO_INLINE(iscntrl);
  NO_INLINE(isdigit);
  NO_INLINE(isgraph);
  NO_INLINE(islower);
  NO_INLINE(isprint);
  NO_INLINE(ispunct);
  NO_INLINE(isspace);
  NO_INLINE(isupper);
  NO_INLINE(isxdigit);

  EXPECT_TRUE(isalnum('A'));
  EXPECT_TRUE(isalpha('A'));
  EXPECT_TRUE(isascii('A'));
  EXPECT_FALSE(isblank('A'));
  EXPECT_FALSE(iscntrl('A'));
  EXPECT_FALSE(isdigit('A'));
  EXPECT_TRUE(isgraph('A'));
  EXPECT_FALSE(islower('A'));
  EXPECT_TRUE(isprint('A'));
  EXPECT_FALSE(ispunct('A'));
  EXPECT_FALSE(isspace('A'));
  EXPECT_TRUE(isupper('A'));
  EXPECT_TRUE(isxdigit('A'));

  EXPECT_TRUE(isalnum('0'));
  EXPECT_FALSE(isalpha('0'));
  EXPECT_TRUE(isascii('0'));
  EXPECT_FALSE(isblank('0'));
  EXPECT_FALSE(iscntrl('0'));
  EXPECT_TRUE(isdigit('0'));
  EXPECT_TRUE(isgraph('0'));
  EXPECT_FALSE(islower('0'));
  EXPECT_TRUE(isprint('0'));
  EXPECT_FALSE(ispunct('0'));
  EXPECT_FALSE(isspace('0'));
  EXPECT_FALSE(isupper('0'));
  EXPECT_TRUE(isxdigit('0'));

  EXPECT_FALSE(isalnum(' '));
  EXPECT_FALSE(isalpha(' '));
  EXPECT_TRUE(isascii(' '));
  EXPECT_TRUE(isblank(' '));
  EXPECT_FALSE(iscntrl(' '));
  EXPECT_FALSE(isdigit(' '));
  EXPECT_FALSE(isgraph(' '));
  EXPECT_FALSE(islower(' '));
  EXPECT_TRUE(isprint(' '));
  EXPECT_FALSE(ispunct(' '));
  EXPECT_TRUE(isspace(' '));
  EXPECT_FALSE(isupper(' '));
  EXPECT_FALSE(isxdigit(' '));

  EXPECT_FALSE(isblank('\n'));
  EXPECT_TRUE(isspace('\n'));
  EXPECT_TRUE(ispunct(','));
  EXPECT_FALSE(isprint(1));
  EXPECT_TRUE(iscntrl(1));
  EXPECT_FALSE(isascii(-1));
}

TEST(Ctype, ToLower) {
  EXPECT_EQ('a', tolower('A'));
  EXPECT_EQ('a', tolower('a'));
  EXPECT_EQ('0', tolower('0'));
}

TEST(Ctype, ToLowerNoInline) {
  NO_INLINE(tolower);
  EXPECT_EQ('a', tolower('A'));
  EXPECT_EQ('a', tolower('a'));
  EXPECT_EQ('0', tolower('0'));
}

TEST(Ctype, ToUpper) {
  EXPECT_EQ('A', toupper('a'));
  EXPECT_EQ('A', toupper('A'));
  EXPECT_EQ('0', toupper('0'));
}

TEST(Ctype, ToUpperNoInline) {
  NO_INLINE(toupper);
  EXPECT_EQ('A', toupper('a'));
  EXPECT_EQ('A', toupper('A'));
  EXPECT_EQ('0', toupper('0'));
}

TEST(Ctype, ToAscii) {
  EXPECT_EQ(0x7f, toascii(0xff));
}

TEST(Ctype, ToAsciiNoInline) {
  NO_INLINE(toascii);
  EXPECT_EQ(0x7f, toascii(0xff));
}
