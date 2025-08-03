/*
 * Copyright 2022 The Android Open Source Project
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

#include "../log_capture.h"

#include <gtest/gtest.h>

#include <cstring>
#include <memory>
#include <string>

#include "common/init_flags.h"
#include "os/log.h"

namespace {
const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

constexpr char kEmptyLine[] = "";
constexpr char kLogError[] = "LOG_ERROR";
constexpr char kLogWarn[] = "LOG_WARN";
constexpr char kLogInfo[] = "LOG_INFO";
constexpr char kLogDebug[] = "LOG_DEBUG";
constexpr char kLogVerbose[] = "LOG_VERBOSE";

}  // namespace

namespace bluetooth {
namespace testing {

class LogCaptureTest : public ::testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}

  // The line number is part of the log output and must be factored out
  size_t CalibrateOneLine(const char* log_line) {
    LOG_INFO("%s", log_line);
    return strlen(log_line);
  }
};

TEST_F(LogCaptureTest, no_output) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  ASSERT_TRUE(log_capture->Size() == 0);
}

// b/260917913
TEST_F(LogCaptureTest, DISABLED_truncate) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  CalibrateOneLine(kLogError);
  size_t size = log_capture->Size();
  ASSERT_TRUE(size > 0);

  log_capture->Reset();
  ASSERT_EQ(0UL, log_capture->Size());

  CalibrateOneLine(kLogError);
  ASSERT_EQ(size, log_capture->Size());
}

// b/260917913
TEST_F(LogCaptureTest, DISABLED_log_size) {
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  CalibrateOneLine(kEmptyLine);
  size_t empty_line_size = log_capture->Size();
  log_capture->Reset();

  std::vector<std::string> log_lines = {
      kLogError,
      kLogWarn,
      kLogInfo,
  };

  size_t msg_size{0};
  for (auto& log_line : log_lines) {
    msg_size += CalibrateOneLine(log_line.c_str());
  }

  ASSERT_EQ(empty_line_size * log_lines.size() + msg_size, log_capture->Size());

  ASSERT_TRUE(log_capture->Rewind()->Find(kLogError));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogWarn));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogInfo));
}

// b/260917913
TEST_F(LogCaptureTest, DISABLED_typical) {
  bluetooth::common::InitFlags::Load(nullptr);
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  LOG_ERROR("%s", kLogError);
  LOG_WARN("%s", kLogWarn);
  LOG_INFO("%s", kLogInfo);
  LOG_DEBUG("%s", kLogDebug);
  LOG_VERBOSE("%s", kLogVerbose);

  ASSERT_TRUE(log_capture->Rewind()->Find(kLogError));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogWarn));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogInfo));
  ASSERT_FALSE(log_capture->Rewind()->Find(kLogDebug));
  ASSERT_FALSE(log_capture->Rewind()->Find(kLogVerbose));
}

// b/260917913
TEST_F(LogCaptureTest, DISABLED_with_logging_debug_enabled_for_all) {
  bluetooth::common::InitFlags::Load(test_flags);
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  LOG_ERROR("%s", kLogError);
  LOG_WARN("%s", kLogWarn);
  LOG_INFO("%s", kLogInfo);
  LOG_DEBUG("%s", kLogDebug);
  LOG_VERBOSE("%s", kLogVerbose);

  ASSERT_TRUE(log_capture->Rewind()->Find(kLogError));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogWarn));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogInfo));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogDebug));
  ASSERT_TRUE(log_capture->Rewind()->Find(kLogVerbose));
  bluetooth::common::InitFlags::Load(nullptr);
}

// b/260917913
TEST_F(LogCaptureTest, DISABLED_wait_until_log_contains) {
  bluetooth::common::InitFlags::Load(test_flags);
  std::unique_ptr<LogCapture> log_capture = std::make_unique<LogCapture>();

  LOG_DEBUG("%s", kLogDebug);
  log_capture->WaitUntilLogContains(kLogDebug);
  bluetooth::common::InitFlags::Load(nullptr);
}

}  // namespace testing
}  // namespace bluetooth
