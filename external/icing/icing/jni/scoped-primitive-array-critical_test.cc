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

#include "icing/jni/scoped-primitive-array-critical.h"

#include <jni.h>

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

TEST(ScopedJniClassesTest, ScopedPrimitiveArrayNull) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  ScopedPrimitiveArrayCritical<uint8_t> scoped_primitive_array(
      env_mock.get(), /*array=*/nullptr);
  EXPECT_THAT(scoped_primitive_array.data(), IsNull());
  EXPECT_THAT(scoped_primitive_array.size(), Eq(0));

  // Move construct a scoped utf chars
  ScopedPrimitiveArrayCritical<uint8_t> moved_scoped_primitive_array(
      std::move(scoped_primitive_array));
  EXPECT_THAT(moved_scoped_primitive_array.data(), IsNull());
  EXPECT_THAT(moved_scoped_primitive_array.size(), Eq(0));

  // Move assign a scoped utf chars
  ScopedPrimitiveArrayCritical<uint8_t> move_assigned_scoped_primitive_array =
      std::move(moved_scoped_primitive_array);
  EXPECT_THAT(move_assigned_scoped_primitive_array.data(), IsNull());
  EXPECT_THAT(move_assigned_scoped_primitive_array.size(), Eq(0));
}

TEST(ScopedJniClassesTest, ScopedPrimitiveArrayConstruction) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  jarray fake_jarray = reinterpret_cast<jarray>(-303);
  uint8_t fake_array[] = {1, 8, 63, 90};
  ON_CALL(*env_mock, GetPrimitiveArrayCritical(Eq(fake_jarray), IsNull()))
      .WillByDefault(Return(fake_array));
  ON_CALL(*env_mock, GetArrayLength(Eq(fake_jarray))).WillByDefault(Return(4));

  ScopedPrimitiveArrayCritical<uint8_t> scoped_primitive_array(
      env_mock.get(),
      /*array=*/fake_jarray);
  EXPECT_THAT(scoped_primitive_array.data(), Eq(fake_array));
  EXPECT_THAT(scoped_primitive_array.size(), Eq(4));

  EXPECT_CALL(*env_mock, ReleasePrimitiveArrayCritical(Eq(fake_jarray),
                                                       Eq(fake_array), Eq(0)))
      .Times(1);
}

TEST(ScopedJniClassesTest, ScopedPrimitiveArrayMoveConstruction) {
  auto env_mock = std::make_unique<MockJNIEnv>();
  // Construct a scoped utf chars normally.
  jarray fake_jarray = reinterpret_cast<jarray>(-303);
  uint8_t fake_array[] = {1, 8, 63, 90};
  ON_CALL(*env_mock, GetPrimitiveArrayCritical(Eq(fake_jarray), IsNull()))
      .WillByDefault(Return(fake_array));
  ON_CALL(*env_mock, GetArrayLength(Eq(fake_jarray))).WillByDefault(Return(4));

  ScopedPrimitiveArrayCritical<uint8_t> scoped_primitive_array(
      env_mock.get(),
      /*array=*/fake_jarray);

  // Move construct a scoped utf chars
  ScopedPrimitiveArrayCritical<uint8_t> moved_scoped_primitive_array(
      std::move(scoped_primitive_array));
  EXPECT_THAT(moved_scoped_primitive_array.data(), Eq(fake_array));
  EXPECT_THAT(moved_scoped_primitive_array.size(), Eq(4));

  EXPECT_CALL(*env_mock, ReleasePrimitiveArrayCritical(Eq(fake_jarray),
                                                       Eq(fake_array), Eq(0)))
      .Times(1);
}

TEST(ScopedJniClassesTest, ScopedPrimitiveArrayMoveAssignment) {
  // Setup the mock to return:
  //   {1, 8, 63, 90} for jstring (-303)
  //   {5, 9, 82} for jstring (-505)
  auto env_mock = std::make_unique<MockJNIEnv>();
  jarray fake_jarray1 = reinterpret_cast<jarray>(-303);
  uint8_t fake_array1[] = {1, 8, 63, 90};
  ON_CALL(*env_mock, GetPrimitiveArrayCritical(Eq(fake_jarray1), IsNull()))
      .WillByDefault(Return(fake_array1));
  ON_CALL(*env_mock, GetArrayLength(Eq(fake_jarray1))).WillByDefault(Return(4));

  jarray fake_jarray2 = reinterpret_cast<jarray>(-505);
  uint8_t fake_array2[] = {5, 9, 82};
  ON_CALL(*env_mock, GetPrimitiveArrayCritical(Eq(fake_jarray2), IsNull()))
      .WillByDefault(Return(fake_array2));
  ON_CALL(*env_mock, GetArrayLength(Eq(fake_jarray2))).WillByDefault(Return(3));

  ScopedPrimitiveArrayCritical<uint8_t> scoped_primitive_array1(
      env_mock.get(),
      /*array=*/fake_jarray1);
  ScopedPrimitiveArrayCritical<uint8_t> scoped_primitive_array2(
      env_mock.get(),
      /*array=*/fake_jarray2);

  // Move assign a scoped utf chars
  scoped_primitive_array2 = std::move(scoped_primitive_array1);
  EXPECT_THAT(scoped_primitive_array2.data(), Eq(fake_array1));
  EXPECT_THAT(scoped_primitive_array2.size(), Eq(4));

  EXPECT_CALL(*env_mock, ReleasePrimitiveArrayCritical(Eq(fake_jarray1),
                                                       Eq(fake_array1), Eq(0)))
      .Times(1);
  EXPECT_CALL(*env_mock, ReleasePrimitiveArrayCritical(Eq(fake_jarray2),
                                                       Eq(fake_array2), Eq(0)))
      .Times(1);
}

}  // namespace

}  // namespace lib
}  // namespace icing
