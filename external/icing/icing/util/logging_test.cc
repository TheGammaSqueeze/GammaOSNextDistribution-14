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

#include "icing/util/logging.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/debug.pb.h"
#include "icing/util/logging_raw.h"

namespace icing {
namespace lib {

namespace {
using ::testing::EndsWith;
using ::testing::IsEmpty;

TEST(LoggingTest, SetLoggingLevelWithInvalidArguments) {
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::DBG, 1));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::INFO, 1));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::WARNING, 1));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::ERROR, 1));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::FATAL, 1));

  EXPECT_FALSE(SetLoggingLevel(LogSeverity::DBG, 2));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::INFO, 2));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::WARNING, 2));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::ERROR, 2));
  EXPECT_FALSE(SetLoggingLevel(LogSeverity::FATAL, 2));

  EXPECT_FALSE(SetLoggingLevel(LogSeverity::VERBOSE, -1));
}

TEST(LoggingTest, SetLoggingLevelTest) {
  // Set to INFO
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::INFO));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));

  // Set to WARNING
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::WARNING));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_FALSE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));

  // Set to DEBUG
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));
}

TEST(LoggingTest, VerboseLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::VERBOSE, 1));
  EXPECT_TRUE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_TRUE(ShouldLog(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));
  EXPECT_TRUE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, VerboseLoggingIsControlledByVerbosity) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::VERBOSE, 2));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 3));
  EXPECT_TRUE(ShouldLog(LogSeverity::VERBOSE, 2));
  EXPECT_TRUE(ShouldLog(LogSeverity::VERBOSE, 1));

  ASSERT_TRUE(SetLoggingLevel(LogSeverity::VERBOSE, 1));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 2));
  EXPECT_TRUE(ShouldLog(LogSeverity::VERBOSE, 1));

  ASSERT_TRUE(SetLoggingLevel(LogSeverity::VERBOSE, 0));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_TRUE(ShouldLog(LogSeverity::VERBOSE, 0));

  // Negative verbosity is invalid.
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, -1));
}

TEST(LoggingTest, DebugLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::DBG));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_TRUE(ShouldLog(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));
  EXPECT_TRUE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, InfoLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::INFO));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_TRUE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));
  EXPECT_TRUE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, WarningLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::WARNING));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_FALSE(ShouldLog(LogSeverity::INFO));
  EXPECT_TRUE(ShouldLog(LogSeverity::WARNING));
  EXPECT_TRUE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, ErrorLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::ERROR));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_FALSE(ShouldLog(LogSeverity::INFO));
  EXPECT_FALSE(ShouldLog(LogSeverity::WARNING));
  EXPECT_TRUE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, FatalLoggingTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::FATAL));
  EXPECT_FALSE(ShouldLog(LogSeverity::VERBOSE, 1));
  EXPECT_FALSE(ShouldLog(LogSeverity::DBG));
  EXPECT_FALSE(ShouldLog(LogSeverity::INFO));
  EXPECT_FALSE(ShouldLog(LogSeverity::WARNING));
  EXPECT_FALSE(ShouldLog(LogSeverity::ERROR));
  EXPECT_TRUE(ShouldLog(LogSeverity::FATAL));
}

TEST(LoggingTest, LoggingStreamTest) {
  ASSERT_TRUE(SetLoggingLevel(LogSeverity::INFO));
  // This one should be logged.
  LoggingStringStream stream1 = (ICING_LOG(INFO) << "Hello"
                                                 << "World!");
  EXPECT_THAT(stream1.message, EndsWith("HelloWorld!"));

  // This one should not be logged, thus empty.
  LoggingStringStream stream2 = (ICING_LOG(DBG) << "Hello"
                                                << "World!");
  EXPECT_THAT(stream2.message, IsEmpty());
}

}  // namespace
}  // namespace lib
}  // namespace icing
