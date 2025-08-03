/*
 * Copyright (C) 2015 The Android Open Source Project
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

//
// Run gtest tests from a JNI entry point.

#include "gtest/gtest.h"

#include <jni.h>

#include <stdio.h>

#include <algorithm>
#include <iterator>
#include <string>
#include <vector>

#include "jni_test_main.h"

namespace ndk_test {

namespace {

std::string JStringToString(JNIEnv* env, jstring str) {
  jboolean is_copy;
  const char* buffer = env->GetStringUTFChars(str, &is_copy);
  std::string result(buffer);

  // Release the buffer always (regardless of |is_copy|).
  // cf) http://developer.android.com/training/articles/perf-jni.html
  env->ReleaseStringUTFChars(str, buffer);

  return result;
}

void PrintTestList(const std::vector<std::string>& test_list) {
  for (size_t i = 0; i < test_list.size(); ++i) {
    fprintf(stderr, "%s%s", (i == 0) ? "" : ", ", test_list[i].c_str());
  }
}

// Verifies if all test cases are listed in |test_list|, and vice versa.
// If |test_list| is empty, the verification is skipped (i.e. always pass).
// On success, returns true, otherwise false and outputs the mismatch info
// to stderr.
bool VerifyTestList(const std::string& test_list) {
  // If the test_list is empty, we just skip the verification.
  if (test_list.empty()) return true;

  // Split test_list by ':'.
  std::vector<std::string> known_test_list;
  {
    size_t begin = 0;
    while (true) {
      size_t end = test_list.find(':', begin);
      size_t len = (end == std::string::npos) ? std::string::npos : (end - begin);
      known_test_list.push_back(test_list.substr(begin, len));
      if (end == std::string::npos) break;
      begin = end + 1;  // Skip ':'.
    }
  }
  std::sort(known_test_list.begin(), known_test_list.end());

  // Traverse all TestInfo, and extract its names.
  std::vector<std::string> actual_test_list;
  ::testing::UnitTest* unit_test = ::testing::UnitTest::GetInstance();
  for (int i = 0; i < unit_test->total_test_case_count(); ++i) {
    const ::testing::TestCase* test_case = unit_test->GetTestCase(i);
    for (int j = 0; j < test_case->total_test_count(); ++j) {
      const ::testing::TestInfo* test_info = test_case->GetTestInfo(j);
      actual_test_list.push_back(std::string(test_info->test_case_name()) + "." +
                                 test_info->name());
    }
  }
  std::sort(actual_test_list.begin(), actual_test_list.end());

  // Take the diff.
  std::vector<std::string> missing_test_list;
  std::set_difference(known_test_list.begin(),
                      known_test_list.end(),
                      actual_test_list.begin(),
                      actual_test_list.end(),
                      std::back_inserter(missing_test_list));
  std::vector<std::string> unknown_test_list;
  std::set_difference(actual_test_list.begin(),
                      actual_test_list.end(),
                      known_test_list.begin(),
                      known_test_list.end(),
                      std::back_inserter(unknown_test_list));

  if (missing_test_list.empty() && unknown_test_list.empty()) {
    // Verification passes successfully.
    return true;
  }

  // Output mismatch info.
  fprintf(stderr, "Mismatch test case is found.\n");
  fprintf(stderr, "Expected test names: ");
  PrintTestList(known_test_list);
  fprintf(stderr, "\n");
  fprintf(stderr, "Actual test names: ");
  PrintTestList(actual_test_list);
  fprintf(stderr, "\n");
  if (!unknown_test_list.empty()) {
    fprintf(stderr, "Unknown tests: ");
    PrintTestList(unknown_test_list);
    fprintf(stderr, "\n");
  }
  if (!missing_test_list.empty()) {
    fprintf(stderr, "Missing tests: ");
    PrintTestList(missing_test_list);
    fprintf(stderr, "\n");
  }
  fprintf(stderr,
          "Note: This mismatching happens maybe due to a new macro which is "
          "expanded to TEST or TEST_F. Then, you may need to modify "
          "extract_google_test_list.py script, which creates a full list "
          "of test cases from the source code. However, please also think "
          "about to use test expectations, instead.\n");

  return false;
}

}  // namespace

int RunAllTests(JNIEnv* env, jobject /* thiz */, jstring gtest_list, jstring gtest_filter) {
  int argc = 2;
  const char* args[3];
  args[0] = "";
  args[1] = "--gtest_output=xml:/data/data/com.example.ndk_tests/cache/out.xml";

  std::string gtest_filter_arg;
  if (env->GetStringUTFLength(gtest_filter) > 0) {
    gtest_filter_arg = "--gtest_filter=" + JStringToString(env, gtest_filter);
    args[2] = gtest_filter_arg.c_str();
    ++argc;
  }

  ::testing::InitGoogleTest(&argc, const_cast<char**>(args));
  if (!VerifyTestList(JStringToString(env, gtest_list))) {
    // On verification failure, return -1.
    return -1;
  }

  return RUN_ALL_TESTS();
}

}  // namespace ndk_test
