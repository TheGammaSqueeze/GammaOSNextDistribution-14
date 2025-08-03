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

#include "UidCpuStatsCollector.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <gmock/gmock.h>

#include <inttypes.h>

#include <unordered_map>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::StringAppendF;
using ::android::base::WriteStringToFile;
using ::testing::UnorderedElementsAreArray;

namespace {

std::string toString(const std::unordered_map<uid_t, int64_t>& cpuTimeMillisByUid) {
    std::string buffer;
    for (const auto& [uid, cpuTime] : cpuTimeMillisByUid) {
        StringAppendF(&buffer, "{%d: %" PRId64 "}\n", uid, cpuTime);
    }
    return buffer;
}

}  // namespace

TEST(UidCpuStatsCollectorTest, TestValidStatFile) {
    // Format: <uid>: <user_time_micro_seconds> <system_time_micro_seconds>
    constexpr char firstSnapshot[] = "0: 7000000 5000000\n"
                                     "100: 1256700 4545636\n"
                                     "1009: 500000 500000\n"
                                     "1001000: 40000 30000\n";
    std::unordered_map<uid_t, int64_t> expectedFirstUsage = {{0, 12'000},
                                                             {100, 5'801},
                                                             {1009, 1'000},
                                                             {1001000, 70}};

    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(firstSnapshot, tf.path));

    UidCpuStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    const auto& actualFirstUsage = collector.deltaStats();
    EXPECT_THAT(actualFirstUsage, UnorderedElementsAreArray(expectedFirstUsage))
            << "Expected: " << toString(expectedFirstUsage)
            << "Actual: " << toString(actualFirstUsage);

    constexpr char secondSnapshot[] = "0: 7500000 5000000\n"
                                      "100: 1266700 4565636\n"
                                      "1009: 700000 600000\n"
                                      "1001000: 40000 30000\n";
    std::unordered_map<uid_t, int64_t> expectedSecondUsage = {{0, 500}, {100, 30}, {1009, 300}};

    ASSERT_TRUE(WriteStringToFile(secondSnapshot, tf.path));
    ASSERT_RESULT_OK(collector.collect());

    const auto& actualSecondUsage = collector.deltaStats();
    EXPECT_THAT(actualSecondUsage, UnorderedElementsAreArray(expectedSecondUsage))
            << "Expected: " << toString(expectedSecondUsage)
            << "Actual: " << toString(actualSecondUsage);
}

TEST(UidCpuStatsCollectorTest, TestErrorOnInvalidStatFile) {
    constexpr char contents[] = "0: 7000000 5000000\n"
                                "100: 1256700 4545636\n"
                                "1009: 500000 500000\n"
                                "1001000: CORRUPTED DATA\n";
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(contents, tf.path));

    UidCpuStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    EXPECT_FALSE(collector.collect().ok()) << "No error returned for invalid file";
}

TEST(UidCpuStatsCollectorTest, TestErrorOnEmptyStatFile) {
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);

    UidCpuStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    EXPECT_FALSE(collector.collect().ok()) << "No error returned for invalid file";
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
