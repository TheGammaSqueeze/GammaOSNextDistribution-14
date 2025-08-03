// Copyright (C) 2021 The Android Open Source Project
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

#include "include/instruction_test.h"

#include <dirent.h>

#include <ditto/parser.h>

class ExampleTest : public ::testing::Test {
 protected:
  std::string path;

  // Set path of the example folder
  void SetUp() override {
    if (absolute_path.empty()) {
      path = "example";
    } else {
      path = absolute_path + "/example";
    }
  }
};

// Test each .ditto file inside example/ folder to make sure that all of them follow the schema
// If .ditto is parsed successfully, exit with code 0 is expected
TEST_F(ExampleTest, ExampleDittoFilesAreCorrect) {
  DIR* directory = opendir(path.c_str());

  struct dirent* entry;
  while ((entry = readdir(directory)) != nullptr) {
    if (entry->d_type == DT_REG) {
      std::string file = path + "/" + entry->d_name;

      EXPECT_EXIT(
          {
            dittosuite::Parser::GetParser().Parse(file, {});
            exit(0);
          },
          testing::ExitedWithCode(0), "");
    }
  }

  closedir(directory);
}
