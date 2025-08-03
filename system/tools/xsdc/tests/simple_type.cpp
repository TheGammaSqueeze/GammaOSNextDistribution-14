/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <iostream>
#include <fstream>
#include <sstream>

#include <android-base/macros.h>

#include "simple_type.h"
#include "xmltest.h"

using namespace std;

TEST_F(XmlTest, Simpletype) {
  using namespace simple::type;
  for (const auto v : android::xsdc_enum_range<EnumType>()) {
    EXPECT_NE(v, EnumType::UNKNOWN);
    EXPECT_EQ(stringToEnumType(toString(v)), v);
  }

  string file_name = Resource("simple_type.xml");
  SimpleTypes simple = *readSimpleTypes(file_name.c_str());

  for (int i = 0; i < simple.getListInt().size(); ++i) {
    EXPECT_EQ(simple.getListInt()[i], i + 1);
  }
  EXPECT_EQ(*simple.getFirstUnionTest(), "100");
  EXPECT_EQ(simple.getYesOrNo()[0], EnumType::YES);
  EXPECT_EQ(simple.getYesOrNo()[1], EnumType::EMPTY);
  ofstream out("old_simple_type.xml");
  write(out, simple);
  SimpleTypes simple2 = *readSimpleTypes("old_simple_type.xml");
  for (int i = 0; i < simple.getListInt().size(); ++i) {
    EXPECT_EQ(simple.getListInt()[i], simple2.getListInt()[i]);
  }
  EXPECT_EQ(*simple.getFirstUnionTest(), *simple2.getFirstUnionTest());
  EXPECT_EQ(simple.getYesOrNo()[0], simple2.getYesOrNo()[0]);
  EXPECT_EQ(simple.getYesOrNo()[1], simple2.getYesOrNo()[1]);
}

TEST_F(XmlTest, Simpletype_AccessingEmptyOptionalAbortsWithMessage) {
  using namespace simple::type;

  string file_name = Resource("simple_type.xml");
  SimpleTypes simple = *readSimpleTypes(file_name.c_str());

  // trying to get the value for optional attribute
  ASSERT_DEATH(simple.isExample3(), "hasExample3()");
  // trying to get the value for optional element
  ASSERT_DEATH(simple.getOptionalIntList(), "hasOptionalIntList()");
  // trying to get the first value from optional element of list of simple values
  ASSERT_DEATH(simple.getFirstOptionalIntList(), "hasOptionalIntList()");
}

TEST_F(XmlTest, SimpleTypeRoot) {
  using namespace simple::type;

  string file_name = Resource("simple_type_root.xml");
  ASSERT_EQ(*readPercent(file_name.c_str()), 100);

  ostringstream out;
  writePercent(out, 100);
  ASSERT_EQ(out.str(), "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<percent>100</percent>\n");
}
