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

#include "apex_classpath.h"

#include <android-base/file.h>
#include <android-base/result-gmock.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <fstream>
#include <string>

namespace android {
namespace apex {

using android::base::WriteStringToFile;
using android::base::testing::Ok;
using android::base::testing::WithMessage;
using ::testing::HasSubstr;

TEST(ApexClassPathUnitTest, ParseFromFile) {
  TemporaryFile output;
  WriteStringToFile(
      "export BOOTCLASSPATH /apex/a/jar1:/apex/b/jar2\n"
      "export SYSTEMSERVERCLASSPATH\n"
      "export UNEXPECTED /apex/c/\n",
      output.path);
  auto result = ClassPath::ParseFromFile(output.path);
  ASSERT_THAT(result, Ok());

  ASSERT_THAT(result->HasClassPathJars("a"), true);
  ASSERT_THAT(result->HasClassPathJars("b"), true);
  ASSERT_THAT(result->HasClassPathJars("c"), true);
  ASSERT_THAT(result->HasClassPathJars("d"), false);
}

TEST(ApexClassPathUnitTest, ParseFromFileJarsNotInApex) {
  TemporaryFile output;
  // We accept jars with regex: /apex/<package-name>/*
  WriteStringToFile("export BOOTCLASSPATH a:b\n", output.path);
  auto result = ClassPath::ParseFromFile(output.path);
  ASSERT_THAT(result, Ok());

  ASSERT_THAT(result->HasClassPathJars("a"), false);
  ASSERT_THAT(result->HasClassPathJars("b"), false);
}

TEST(ApexClassPathUnitTest, ParseFromFilePackagesWithSamePrefix) {
  TemporaryFile output;
  WriteStringToFile(
      "export BOOTCLASSPATH /apex/media/:/apex/mediaprovider\n"
      "export SYSTEMSERVERCLASSPATH /apex/mediafoo/\n",
      output.path);
  auto result = ClassPath::ParseFromFile(output.path);
  ASSERT_THAT(result, Ok());

  ASSERT_THAT(result->HasClassPathJars("media"), true);
  // "/apex/mediaprovider" did not end with /
  ASSERT_THAT(result->HasClassPathJars("mediaprovider"), false);
  // A prefix of an apex name present should not be accepted
  ASSERT_THAT(result->HasClassPathJars("m"), false);
}

TEST(ApexClassPathUnitTest, ParseFromFileDoesNotExist) {
  auto result = ClassPath::ParseFromFile("/file/does/not/exist");
  ASSERT_THAT(result, HasError(WithMessage(HasSubstr(
                          "Failed to read classpath info from file"))));
}

TEST(ApexClassPathUnitTest, ParseFromFileEmptyJars) {
  TemporaryFile output;
  WriteStringToFile(
      "export BOOTCLASSPATH\n"
      "export SYSTEMSERVERCLASSPATH \n"
      "export DEX2OATBOOTCLASSPATH \n",
      output.path);
  auto result = ClassPath::ParseFromFile(output.path);
  ASSERT_THAT(result, Ok());
}

TEST(ApexClassPathUnitTest, DeriveClassPathNoStagedApex) {
  auto result = ClassPath::DeriveClassPath({});
  ASSERT_THAT(
      result,
      HasError(WithMessage(HasSubstr(
          "Invalid argument: There are no APEX to derive claspath from"))));
}

TEST(ApexClassPathUnitTest, DeriveClassPathPreferBinaryInStagedApex) {
  // Default location uses provided package name to compose binary path
  auto result = ClassPath::DeriveClassPath({"/apex/temp@123"}, "different");
  ASSERT_THAT(result,
              HasError(WithMessage(HasSubstr(
                  "binary path: /apex/different/bin/derive_classpath"))));

  // When staged apex has same package name, we use that location instead
  result = ClassPath::DeriveClassPath({"/apex/temp@123"}, "temp");
  ASSERT_THAT(result,
              HasError(WithMessage(HasSubstr(
                  "binary path: /apex/temp@123/bin/derive_classpath"))));
}

}  // namespace apex
}  // namespace android
