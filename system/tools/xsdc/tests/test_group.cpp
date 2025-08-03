/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <string>
#include <optional>

#include <android-base/macros.h>
#include <gtest/gtest.h>

#include "group.h"
#include "xmltest.h"

using namespace std;

TEST_F(XmlTest, Group) {
  using namespace group;
  Student student = *read(Resource("group.xml").c_str());

  EXPECT_EQ(student.getCity(), "Mountain View");
  EXPECT_EQ(student.getState(), "CA");
  EXPECT_EQ(student.getRoad(), "Street 101");

  ofstream out("old_group.xml");
  write(out, student);
}
