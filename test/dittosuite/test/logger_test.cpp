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

#include <ditto/logger.h>
#include <gtest/gtest.h>

#include <vector>

namespace dittosuite {

class TestLogger : public Logger {};

TEST(DittoLogger, DefaultVerbosityIsInfo) {
  TestLogger logger;

  LogLevel log_level = logger.GetLogLevel();

  ASSERT_EQ(LogLevel::kInfo, log_level);
}

/* TODO this test should be properly refactored (split and mocked) to make sure
the strings are written to the logs. */
TEST(DittoLogger, SimpleLog) {
  for (int i = 10; i <= 11; i++) {
    Logger::GetInstance().SetLogLevel(LogLevel::kError);
    std::cout << "My log level:  " << static_cast<int>(Logger::GetInstance().GetLogLevel()) << '\n';
    LOGW("This is a warning");
    LOGE("This is an error");
    LOGI("This is an info");
    Logger::GetInstance().SetLogStream(LogStream::kLogcat);
    Logger::GetInstance().SetLogLevel(LogLevel::kWarning);
    LOGW("This is a warning");
    LOGE("This is an error");
    LOGI("This is an info");
  }
}

TEST(DittoLogger, LoggerPError) {
  TestLogger logger;

  FILE* pFile = fopen("nonexistent.txt", "re");

  ASSERT_EQ(nullptr, pFile);
}

class DittoLoggerStreamUpdate : public testing::TestWithParam<dittosuite::LogStream> {};

TEST_P(DittoLoggerStreamUpdate, SetStream) {
  TestLogger logger;

  logger.SetLogStream(GetParam());
  LogStream log_stream = logger.GetLogStream();

  ASSERT_EQ(GetParam(), log_stream);
}

INSTANTIATE_TEST_SUITE_P(Streams, DittoLoggerStreamUpdate,
                         testing::Values(LogStream::kStdout, LogStream::kLogcat));

class DittoLoggerLevelUpdate : public testing::TestWithParam<dittosuite::LogLevel> {};

TEST_P(DittoLoggerLevelUpdate, SetLevel) {
  TestLogger logger;

  logger.SetLogLevel(GetParam());
  LogLevel log_level = logger.GetLogLevel();

  ASSERT_EQ(GetParam(), log_level);
}

INSTANTIATE_TEST_SUITE_P(Levels, DittoLoggerLevelUpdate,
                         testing::Values(LogLevel::kInfo, LogLevel::kWarning, LogLevel::kError,
                                         LogLevel::kFatal, LogLevel::kVerbose, LogLevel::kDebug));

class DittoLoggerLevel
    : public testing::TestWithParam<std::pair<std::string_view, dittosuite::LogLevel>> {};

TEST_P(DittoLoggerLevel, ArgumentsToLevelStringParam) {
  TestLogger logger;

  LogLevel log_level = ArgToLogLevel(GetParam().first);

  ASSERT_EQ(GetParam().second, log_level);
}

INSTANTIATE_TEST_SUITE_P(
    StringMatchesPB, DittoLoggerLevel,
    testing::Values(
        std::make_pair("VERBOSE", LogLevel::kVerbose), std::make_pair("DEBUG", LogLevel::kDebug),
        std::make_pair("INFO", LogLevel::kInfo), std::make_pair("WARNING", LogLevel::kWarning),
        std::make_pair("ERROR", LogLevel::kError), std::make_pair("FATAL", LogLevel::kFatal),
        std::make_pair("5", LogLevel::kVerbose), std::make_pair("4", LogLevel::kDebug),
        std::make_pair("3", LogLevel::kInfo), std::make_pair("2", LogLevel::kWarning),
        std::make_pair("1", LogLevel::kError), std::make_pair("0", LogLevel::kFatal)));

INSTANTIATE_TEST_SUITE_P(StringMatchesNoPBReturnsInfo, DittoLoggerLevel,
                         testing::Values(std::make_pair("6", LogLevel::kInfo),
                                         std::make_pair("-1", LogLevel::kInfo),
                                         std::make_pair("UNKNOWN", LogLevel::kInfo),
                                         std::make_pair("", LogLevel::kInfo)));

}  // namespace dittosuite
