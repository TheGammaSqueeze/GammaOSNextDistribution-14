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

#include "icing/jni/scoped-utf-chars.h"

#include <jni.h>

#include <string>
#include <utility>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "util/java/mock_jni_env.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::IsNull;
using ::testing::Return;
using util::java::test::MockJNIEnv;

TEST(ScopedJniClassesTest, ScopedUtfCharsNull) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  ScopedUtfChars scoped_utf_chars(env_mock.get(), /*s=*/nullptr);
  EXPECT_THAT(scoped_utf_chars.c_str(), IsNull());
  EXPECT_THAT(scoped_utf_chars.size(), Eq(0));

  // Move construct a scoped utf chars
  ScopedUtfChars moved_scoped_utf_chars(std::move(scoped_utf_chars));
  EXPECT_THAT(moved_scoped_utf_chars.c_str(), IsNull());
  EXPECT_THAT(moved_scoped_utf_chars.size(), Eq(0));

  // Move assign a scoped utf chars
  ScopedUtfChars move_assigned_scoped_utf_chars =
      std::move(moved_scoped_utf_chars);
  EXPECT_THAT(move_assigned_scoped_utf_chars.c_str(), IsNull());
  EXPECT_THAT(move_assigned_scoped_utf_chars.size(), Eq(0));
}

TEST(ScopedJniClassesTest, ScopedUtfCharsConstruction) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  jstring fake_jstring = reinterpret_cast<jstring>(-303);
  std::string fake_string = "foo";
  ON_CALL(*env_mock, GetStringUTFChars(Eq(fake_jstring), IsNull()))
      .WillByDefault(Return(fake_string.c_str()));

  ScopedUtfChars scoped_utf_chars(env_mock.get(), /*s=*/fake_jstring);
  EXPECT_THAT(scoped_utf_chars.c_str(), Eq(fake_string.c_str()));
  EXPECT_THAT(scoped_utf_chars.size(), Eq(3));

  EXPECT_CALL(*env_mock,
              ReleaseStringUTFChars(Eq(fake_jstring), Eq(fake_string.c_str())))
      .Times(1);
}

TEST(ScopedJniClassesTest, ScopedUtfCharsMoveConstruction) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  jstring fake_jstring = reinterpret_cast<jstring>(-303);
  std::string fake_string = "foo";
  ON_CALL(*env_mock, GetStringUTFChars(Eq(fake_jstring), IsNull()))
      .WillByDefault(Return(fake_string.c_str()));

  ScopedUtfChars scoped_utf_chars(env_mock.get(), /*s=*/fake_jstring);

  // Move construct a scoped utf chars
  ScopedUtfChars moved_scoped_utf_chars(std::move(scoped_utf_chars));
  EXPECT_THAT(moved_scoped_utf_chars.c_str(), Eq(fake_string.c_str()));
  EXPECT_THAT(moved_scoped_utf_chars.size(), Eq(3));

  EXPECT_CALL(*env_mock,
              ReleaseStringUTFChars(Eq(fake_jstring), Eq(fake_string.c_str())))
      .Times(1);
}

TEST(ScopedJniClassesTest, ScopedUtfCharsMoveAssignment) {
  // Setup the mock to return:
  //   "foo" for jstring (-303)
  //   "bar baz" for jstring (-505)
  auto env_mock = std::make_unique<MockJNIEnv>();
  jstring fake_jstring1 = reinterpret_cast<jstring>(-303);
  std::string fake_string1 = "foo";
  ON_CALL(*env_mock, GetStringUTFChars(Eq(fake_jstring1), IsNull()))
      .WillByDefault(Return(fake_string1.c_str()));

  jstring fake_jstring2 = reinterpret_cast<jstring>(-505);
  std::string fake_string2 = "bar baz";
  ON_CALL(*env_mock, GetStringUTFChars(Eq(fake_jstring2), IsNull()))
      .WillByDefault(Return(fake_string2.c_str()));

  ScopedUtfChars scoped_utf_chars1(env_mock.get(), /*s=*/fake_jstring1);
  ScopedUtfChars scoped_utf_chars2(env_mock.get(), /*s=*/fake_jstring2);

  // Move assign a scoped utf chars
  scoped_utf_chars2 = std::move(scoped_utf_chars1);
  EXPECT_THAT(scoped_utf_chars2.c_str(), Eq(fake_string1.c_str()));
  EXPECT_THAT(scoped_utf_chars2.size(), Eq(3));

  EXPECT_CALL(*env_mock, ReleaseStringUTFChars(Eq(fake_jstring1),
                                               Eq(fake_string1.c_str())))
      .Times(1);
  EXPECT_CALL(*env_mock, ReleaseStringUTFChars(Eq(fake_jstring2),
                                               Eq(fake_string2.c_str())))
      .Times(1);
}

}  // namespace

}  // namespace lib
}  // namespace icing
