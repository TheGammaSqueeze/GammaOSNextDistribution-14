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

#include <errno.h>
#include <wchar.h>

#include "berberis/ndk_program_tests/file.h"  // NOLINT

// Generally in Bionic there is no support for wide-chars. Majority of functions just convert
// between char and wchar_t. But some operations call to BSD-internals that process wide-chars
// correctly.

// File input-output tests

// wint_t doesn't coincide with the type of L'A'
static wint_t wint(char symbol) {
  return static_cast<wint_t>(symbol);
}

// TODO(b/190469865): Fix and enable!
TEST(WChar, DISABLED_FgetwcFputwc) {
  TempFile f;
  ASSERT_EQ(fputwc(L'A', f.get()), wint(L'A'));
  ASSERT_EQ(fputwc(L'B', f.get()), wint(L'B'));
  ASSERT_EQ(fseek(f.get(), 0, SEEK_SET), 0);
  EXPECT_EQ(fgetwc(f.get()), wint(L'A'));
  EXPECT_EQ(fgetwc(f.get()), wint(L'B'));
}

// TODO(b/190469865): Fix and enable!
TEST(WChar, DISABLED_Ungetwc) {
  TempFile f;
  ASSERT_EQ(ungetwc(wint(L'A'), f.get()), wint(L'A'));
  EXPECT_EQ(fgetwc(f.get()), wint(L'A'));
  ASSERT_EQ(ungetwc(wint(L'B'), f.get()), wint(L'B'));
  EXPECT_EQ(fgetwc(f.get()), wint(L'B'));
  EXPECT_EQ(fgetwc(f.get()), WEOF);
}

// Wchar-type tests

TEST(WChar, Iswctype) {
  EXPECT_TRUE(iswctype(wint(L'A'), wctype("alpha")));
  EXPECT_TRUE(iswctype(wint(L' '), wctype("blank")));
  EXPECT_TRUE(iswctype(wint(L'\n'), wctype("cntrl")));
  EXPECT_TRUE(iswctype(wint(L'0'), wctype("digit")));
  EXPECT_TRUE(iswctype(wint(L'A'), wctype("graph")));
  EXPECT_TRUE(iswctype(wint(L'a'), wctype("lower")));
  EXPECT_TRUE(iswctype(wint(L'A'), wctype("print")));
  EXPECT_TRUE(iswctype(wint(L'!'), wctype("punct")));
  EXPECT_TRUE(iswctype(wint(L' '), wctype("space")));
  EXPECT_TRUE(iswctype(wint(L'A'), wctype("upper")));
  EXPECT_TRUE(iswctype(wint(L'F'), wctype("xdigit")));

  EXPECT_FALSE(iswctype(wint(L'Z'), wctype("xdigit")));
}

TEST(WChar, Towupper) {
  EXPECT_EQ(towupper(wint(L'a')), wint(L'A'));
}

TEST(WChar, MbrtowcWcrtomb) {
  wchar_t wc = L'A';
  wchar_t ref_wc = L'B';
  EXPECT_TRUE(mbrtowc(&wc, reinterpret_cast<char*>(&ref_wc), sizeof(wchar_t), nullptr));
  EXPECT_EQ(wc, ref_wc);

  char c = 'C';
  EXPECT_EQ(wcrtomb(&c, L'D', nullptr), 1U);
  EXPECT_EQ(c, 'D');
}

TEST(WChar, Wcscoll) {
  // Bionic's wcscoll doesn't use locale but correctly processes wide-strings (calling wcscmp)
  EXPECT_EQ(wcscoll(L"ABC", L"ABC"), 0);
  EXPECT_LT(wcscoll(L"ABC", L"a"), 0);
}

TEST(WChar, Wcsftime) {
  wchar_t buffer[100];
  struct tm time_data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 0, "GMT"};
  EXPECT_EQ(wcsftime(buffer, 100, L"%c", &time_data), 24U);
  EXPECT_EQ(wcscmp(L"Sat May  3 02:01:00 1905", buffer), 0);
}

TEST(WChar, Wcsxfrm) {
  // Bionic's wcsxfrm doesn't use locale but correctly processes wide-strings (calling wcslen)
  wchar_t dest[2];
  EXPECT_EQ(wcsxfrm(nullptr, L"ABC", 0), 3U);
  EXPECT_EQ(wcsxfrm(dest, L"ABC", 2), 3U);
  EXPECT_EQ(dest[0], L'A');
  EXPECT_EQ(dest[1], L'\0');
}
