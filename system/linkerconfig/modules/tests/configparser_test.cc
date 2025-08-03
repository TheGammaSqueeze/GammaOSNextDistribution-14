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

#include "linkerconfig/configparser.h"

#include <android-base/file.h>
#include <gtest/gtest.h>

using android::linkerconfig::modules::ParseLinkerConfig;

const std::string kBaseDir =
    android::base::GetExecutableDirectory() + "/modules/tests/data/";

TEST(configparser, apex_file_not_exist) {
  auto result = ParseLinkerConfig(kBaseDir + "linker.config.noexist.pb");

  ASSERT_FALSE(result.ok());
}

TEST(configparser, apex_contents) {
  std::vector<std::string> expected_permitted_paths = {
      "/a",
      "/b/c",
      "/d/e/f",
  };

  auto result = ParseLinkerConfig(kBaseDir + "linker.config.apex.pb");

  ASSERT_TRUE(result.ok()) << result.error().message();
  std::vector<std::string> permitted_paths = {result->permittedpaths().begin(),
                                              result->permittedpaths().end()};
  ASSERT_EQ(permitted_paths, expected_permitted_paths);
  ASSERT_TRUE(result->visible());
}

TEST(configparser, system_contents) {
  std::vector<std::string> expected_provide_libs = {
      "a.so",
      "b.so",
      "c.so",
  };
  std::vector<std::string> expected_require_libs = {
      "foo.so",
      "bar.so",
      "baz.so",
  };

  auto result = ParseLinkerConfig(kBaseDir + "linker.config.system.pb");

  ASSERT_TRUE(result.ok());
  std::vector<std::string> provide_libs = {result->providelibs().begin(),
                                           result->providelibs().end()};
  std::vector<std::string> require_libs = {result->requirelibs().begin(),
                                           result->requirelibs().end()};
  ASSERT_EQ(provide_libs, expected_provide_libs);
  ASSERT_EQ(require_libs, expected_require_libs);
}