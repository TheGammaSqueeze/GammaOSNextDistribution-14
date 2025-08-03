// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gmock/gmock.h>

#include "host-common/logging.h"

#include <thread>

#include "aemu/base/testing/TestUtils.h"

namespace {

using ::testing::EndsWith;
using ::testing::HasSubstr;
using ::testing::Not;
using ::testing::StartsWith;
using ::testing::internal::CaptureStderr;
using ::testing::internal::CaptureStdout;
using ::testing::internal::GetCapturedStderr;
using ::testing::internal::GetCapturedStdout;

// Returns the microseconds since the Unix epoch for Sep 13, 2020 12:26:40.123456 in the machine's
// local timezone.
int64_t defaultTimestamp() {
    std::tm time = {};
    time.tm_year = 2020 - 1900;
    time.tm_mon = 9 - 1;  // month starts at 0
    time.tm_mday = 13;
    time.tm_hour = 12;
    time.tm_min = 26;
    time.tm_sec = 40;
    time.tm_isdst = -1;  // let mktime determine whether DST is in effect
    int64_t timestamp_s = mktime(&time);
    EXPECT_GT(timestamp_s, 0) << "mktime() failed";
    return timestamp_s * 1000000 + 123456;
}

TEST(Logging, ERRMacroNoArguments) {
    CaptureStderr();
    ERR("Hello world.");
    std::string log = GetCapturedStderr();
    EXPECT_THAT(log, StartsWith("E"));
    EXPECT_THAT(log, EndsWith("] Hello world.\n"));
}

TEST(Logging, ERRMacroWithArguments) {
    CaptureStderr();
    ERR("hello %s %d", "world", 1);
    std::string log = GetCapturedStderr();
    EXPECT_THAT(log, StartsWith("E"));
    EXPECT_THAT(log, EndsWith("] hello world 1\n"));
}

TEST(Logging, INFOMacroNoArguments) {
    CaptureStderr();
    INFO("Hello world.");
    std::string log = GetCapturedStderr();
    EXPECT_THAT(log, StartsWith("I"));
    EXPECT_THAT(log, EndsWith("] Hello world.\n"));
}

TEST(Logging, INFOMacroWithArguments) {
    CaptureStderr();
    INFO("hello %s %d", "world", 1);
    std::string log = GetCapturedStderr();
    EXPECT_THAT(log, StartsWith("I"));
    EXPECT_THAT(log, EndsWith("] hello world 1\n"));
}

TEST(Logging, FormatsPrefixCorrectly) {
    CaptureStderr();
    INFO("foo");
    std::string log = GetCapturedStderr();
    EXPECT_THAT(
        log, MatchesStdRegex(
                 R"re(I\d{4} \d{2}:\d{2}:\d{2}\.\d{6} +\w+ logging_unittest.cpp:\d+\] foo\n)re"));
}

TEST(Logging, OutputsTimestamp) {
    CaptureStdout();
    OutputLog(stdout, 'I', "", 0, defaultTimestamp(), "");
    std::string log = GetCapturedStdout();
    EXPECT_THAT(log, StartsWith("I0913 12:26:40.123456"));
}

#if defined(_WIN32)
TEST(Logging, FileHasBasenameOnlyWithBackwardsSlashes) {
    CaptureStdout();
    OutputLog(stdout, ' ', R"(c:\foo\bar\file_name)", 123, 0, "");
    std::string log = GetCapturedStdout();
    EXPECT_THAT(log, HasSubstr(" file_name:123"));
    EXPECT_THAT(log, Not(HasSubstr("bar")));
}
#endif

TEST(Logging, FileHasBasenameOnlyWithForwardSlashes) {
    CaptureStdout();
    OutputLog(stdout, ' ', "/foo/bar/file_name", 123, 0, "");
    std::string log = GetCapturedStdout();
    EXPECT_THAT(log, HasSubstr(" file_name:123"));
    EXPECT_THAT(log, Not(HasSubstr("bar")));
}

TEST(Logging, OutputsDifferentThreadIdsOnDifferentThreads) {
    CaptureStderr();
    INFO("hello");
    std::string log1 = GetCapturedStderr();

    CaptureStderr();
    std::thread([]() { INFO("from thread"); }).join();
    std::string log2 = GetCapturedStderr();

    std::string tid1 = log1.substr(21, 9);
    std::string tid2 = log2.substr(21, 9);
    EXPECT_THAT(tid1, MatchesStdRegex(R"( +\w+ )"));
    EXPECT_THAT(tid2, MatchesStdRegex(R"( +\w+ )"));
    EXPECT_NE(tid1, tid2);
}

}  // namespace
