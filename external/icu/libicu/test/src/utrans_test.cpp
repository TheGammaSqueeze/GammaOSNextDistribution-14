/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <unicode/uchar.h>
#include <unicode/ustring.h>
#include <unicode/utrans.h>

TEST(Icu4cUTransliteratorTest, test_utrans_transUChars) {
  UErrorCode status = U_ZERO_ERROR;
  UChar id[] = u"Any-Upper";
  UTransliterator* utrans = utrans_openU(id, -1, UTRANS_FORWARD, NULL, 0, NULL, &status);
  ASSERT_EQ(U_ZERO_ERROR, status);

  UChar str[] = u"HeLlO WoRlD!";
  int32_t len = sizeof(str) / sizeof(UChar) - 1;

  utrans_transUChars(utrans, str, NULL, len + 1 /*textCapacity*/, 0, &len, &status);
  utrans_close(utrans);
  UChar expected[] = u"HELLO WORLD!";
  ASSERT_EQ(U_ZERO_ERROR, status);
  ASSERT_EQ(0, u_strcmp(expected, str));
}

