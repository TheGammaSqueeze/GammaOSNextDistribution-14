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

// Utilities for accessing test data.

#ifndef LIBTEXTCLASSIFIER_UTILS_TEST_DATA_TEST_UTILS_H_
#define LIBTEXTCLASSIFIER_UTILS_TEST_DATA_TEST_UTILS_H_
#include <fstream>

#include "gtest/gtest.h"

namespace libtextclassifier3 {

// Get the file path to the test data.
inline std::string GetTestDataPath(const std::string& relative_path) {
  return "/data/local/tmp/" + relative_path;
}

inline std::string GetTestFileContent(const std::string& relative_path) {
  const std::string full_path = GetTestDataPath(relative_path);
  std::ifstream file_stream(full_path);
  return std::string(std::istreambuf_iterator<char>(file_stream), {});
}

}  // namespace  libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_TEST_DATA_TEST_UTILS_H_
