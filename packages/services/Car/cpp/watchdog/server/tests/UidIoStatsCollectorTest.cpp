/*
 * Copyright 2020 The Android Open Source Project
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

#include "UidIoStatsCollector.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <gmock/gmock.h>

#include <unordered_map>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::StringAppendF;
using ::android::base::WriteStringToFile;
using ::testing::UnorderedElementsAreArray;

namespace {

std::string toString(std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid) {
    std::string buffer;
    for (const auto& [uid, stats] : uidIoStatsByUid) {
        StringAppendF(&buffer, "{%d: %s}\n", uid, stats.toString().c_str());
    }
    return buffer;
}

}  // namespace

TEST(UidIoStatsCollectorTest, TestValidStatFile) {
    // Format: uid fgRdChar fgWrChar fgRdBytes fgWrBytes bgRdChar bgWrChar bgRdBytes bgWrBytes
    // fgFsync bgFsync
    constexpr char firstSnapshot[] = "1001234 5000 1000 3000 500 0 0 0 0 20 0\n"
                                     "1005678 500 100 30 50 300 400 100 200 45 60\n"
                                     "1009 0 0 0 0 40000 50000 20000 30000 0 300\n"
                                     "1001000 4000 3000 2000 1000 400 300 200 100 50 10\n";
    std::unordered_map<uid_t, UidIoStats> expectedFirstUsage =
            {{1001234,
              UidIoStats{/*fgRdBytes=*/3'000, /*bgRdBytes=*/0, /*fgWrBytes=*/500,
                         /*bgWrBytes=*/0, /*fgFsync=*/20, /*bgFsync=*/0}},
             {1005678,
              UidIoStats{/*fgRdBytes=*/30, /*bgRdBytes=*/100, /*fgWrBytes=*/50, /*bgWrBytes=*/200,
                         /*fgFsync=*/45, /*bgFsync=*/60}},
             {1009,
              UidIoStats{/*fgRdBytes=*/0, /*bgRdBytes=*/20'000, /*fgWrBytes=*/0,
                         /*bgWrBytes=*/30'000,
                         /*fgFsync=*/0, /*bgFsync=*/300}},
             {1001000,
              UidIoStats{/*fgRdBytes=*/2'000, /*bgRdBytes=*/200, /*fgWrBytes=*/1'000,
                         /*bgWrBytes=*/100, /*fgFsync=*/50, /*bgFsync=*/10}}};
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(firstSnapshot, tf.path));

    UidIoStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    const auto& actualFirstUsage = collector.deltaStats();
    EXPECT_THAT(actualFirstUsage, UnorderedElementsAreArray(expectedFirstUsage))
            << "Expected: " << toString(expectedFirstUsage)
            << "Actual: " << toString(actualFirstUsage);

    constexpr char secondSnapshot[] = "1001234 10000 2000 7000 950 0 0 0 0 45 0\n"
                                      "1005678 600 100 40 50 1000 1000 1000 600 50 70\n"
                                      "1003456 300 500 200 300 0 0 0 0 50 0\n"
                                      "1001000 400 300 200 100 40 30 20 10 5 1\n";
    std::unordered_map<uid_t, UidIoStats> expectedSecondUsage =
            {{1001234,
              UidIoStats{/*fgRdBytes=*/4'000, /*bgRdBytes=*/0,
                         /*fgWrBytes=*/450, /*bgWrBytes=*/0, /*fgFsync=*/25,
                         /*bgFsync=*/0}},
             {1005678,
              UidIoStats{/*fgRdBytes=*/10, /*bgRdBytes=*/900, /*fgWrBytes=*/0, /*bgWrBytes=*/400,
                         /*fgFsync=*/5, /*bgFsync=*/10}},
             {1003456,
              UidIoStats{/*fgRdBytes=*/200, /*bgRdBytes=*/0, /*fgWrBytes=*/300, /*bgWrBytes=*/0,
                         /*fgFsync=*/50, /*bgFsync=*/0}}};
    ASSERT_TRUE(WriteStringToFile(secondSnapshot, tf.path));
    ASSERT_RESULT_OK(collector.collect());

    const auto& actualSecondUsage = collector.deltaStats();
    EXPECT_THAT(actualSecondUsage, UnorderedElementsAreArray(expectedSecondUsage))
            << "Expected: " << toString(expectedSecondUsage)
            << "Actual: " << toString(actualSecondUsage);
}

TEST(UidIoStatsCollectorTest, TestErrorOnInvalidStatFile) {
    // Format: uid fgRdChar fgWrChar fgRdBytes fgWrBytes bgRdChar bgWrChar bgRdBytes bgWrBytes
    // fgFsync bgFsync
    constexpr char contents[] = "1001234 5000 1000 3000 500 0 0 0 0 20 0\n"
                                "1005678 500 100 30 50 300 400 100 200 45 60\n"
                                "1009012 0 0 0 0 40000 50000 20000 30000 0 300\n"
                                "1001000 4000 3000 2000 1000 CORRUPTED DATA\n";
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(contents, tf.path));

    UidIoStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    EXPECT_FALSE(collector.collect().ok()) << "No error returned for invalid file";
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
