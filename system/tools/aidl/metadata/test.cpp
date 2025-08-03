/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <aidl/metadata.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <optional>

using ::android::AidlInterfaceMetadata;
using ::testing::ElementsAre;

static std::optional<AidlInterfaceMetadata> metadataForModule(const std::string& name) {
  for (const AidlInterfaceMetadata& info : AidlInterfaceMetadata::all()) {
    if (name == info.name) return info;
  }
  return std::nullopt;
}

TEST(AidlMetadata, HasTestInstances) {
  const auto& info = metadataForModule("test-piece-1");
  ASSERT_NE(info, std::nullopt);
  EXPECT_EQ(info->stability, "");
  EXPECT_THAT(info->types,
              ElementsAre("some_package.IFoo", "some_package.Thing",
                          "some_package.sub_package.IFoo", "some_package.sub_package.SubThing"));
  EXPECT_THAT(info->hashes, ElementsAre("13e24b2fac6a979971819fba2ab0d6d7c4182122",
                                        "dc2a9292847e43b4360bb183f7491f0e9895eaa9",
                                        "54f935920ab0934c242145cf00f9852ae3f5a63e",
                                        "be5dd6bf9c9000ee053621f118b7d6a7cfd1e79e"));
  EXPECT_THAT(info->versions, ElementsAre(1, 2, 3, 4));
  EXPECT_EQ(info->has_development, false);
}

TEST(AidlMetadata, HasTestInstancesNoDevelopment) {
  const auto& info = metadataForModule("test-piece-2");
  ASSERT_NE(info, std::nullopt);
  EXPECT_EQ(info->stability, "");
  EXPECT_THAT(info->types, ElementsAre("INoPackage", "some_package.IBar"));
  EXPECT_THAT(info->hashes, ElementsAre("c544902ab8a1d2e72ae9396032ba113e9b9698c4",
                                        "fcd36db451cdbeeb049833fd7f499a987acf3930"));
  EXPECT_THAT(info->versions, ElementsAre(1));
  EXPECT_EQ(info->has_development, false);
}

TEST(AidlMetadata, UseUnfrozen) {
  const auto& info = metadataForModule("tests-unfrozen-vendor");
  EXPECT_TRUE(info->use_unfrozen);
}
