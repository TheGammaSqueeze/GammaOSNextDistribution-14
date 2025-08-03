/*
 * Copyright 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string>
#include <vector>

#include <gtest/gtest.h>

#include "dmesg_parser.h"

class DmesgParserTest : public ::testing::Test {
  public:
    void ReadLines(const std::vector<std::string>& lines);
    bool CheckReport(const std::vector<std::string>& lines);

    dmesg_parser::DmesgParser parser;
    std::string parsed_report;
};

void DmesgParserTest::ReadLines(const std::vector<std::string>& lines) {
    for (auto line : lines) parser.ProcessLine(line);
}

bool DmesgParserTest::CheckReport(const std::vector<std::string>& lines) {
    if (!parser.ReportReady()) return false;
    parsed_report = parser.FlushReport();

    std::string report;
    for (auto line : lines) {
        report += line;
    }
    EXPECT_EQ(report, parsed_report);
    return report == parsed_report;
}

TEST_F(DmesgParserTest, SimpleKasanReport) {
    std::vector<std::string> in = {
            "[  495.412333] [    T1] init: this line will be dropped\n",
            "[  495.412345] [ T9971] "
            "==================================================================\n",
            "[  495.496694] [ T9971] BUG: KASAN: invalid-access in crash_write+0x134/0x140\n",
            "[  495.712345] [ T9971] "
            "==================================================================\n",
            "[  495.767899] [ T9971] logs after the separator do not belong to report\n",
    };

    std::vector<std::string> report = {
            "[  495.496694] [ T9971] BUG: KASAN: invalid-access in crash_write+0x134/0x140\n",
    };

    ReadLines(in);
    ASSERT_TRUE(parser.ReportReady());
    ASSERT_EQ("KASAN", parser.ReportType());
    ASSERT_EQ("BUG: KASAN: invalid-access in crash_write+0x134/0x140", parser.ReportTitle());
    ASSERT_TRUE(CheckReport(report));
}

TEST_F(DmesgParserTest, StrippedKasanReport) {
    /*
     * From the following report, only the lines from T9971 between the "======="
     * delimiters will be preserved, and only those that do not contain raw
     * memory.
     * Task name is also stripped off, because it may contain sensitive data.
     */
    std::vector<std::string> in = {
            "[  495.412333] [    T1] init: this line will be dropped\n",
            "[  495.412345] [ T9971] "
            "==================================================================\n",
            "[  495.496694] [ T9971] BUG: KASAN: invalid-access in crash_write+0x134/0x140\n",
            "[  495.501234] [  T333] random_process: interleaving output with our error report\n",
            "[  495.503671] [ T9971] Read at addr f0ffff87c23fdf7f by task sh/9971\n",
            "[  495.510025] [ T9971] Pointer tag: [f0], memory tag: [fe]\n",
            "[  495.515400] [ T9971] \n",
            "[  495.667603] [ T9971] raw: 4000000000010200 0000000000000000 0000000000000000 "
            "0000000100200020\n",
            "[  495.667634] [ T9971] raw: dead000000000100 dead000000000200 ffffffc14900fc00 "
            "0000000000000000\n",
            "[  495.712345] [ T9971] "
            "==================================================================\n",
            "[  495.767899] [ T9971] logs after the separator do not belong to report\n",
    };

    std::vector<std::string> report = {
            "[  495.496694] [ T9971] BUG: KASAN: invalid-access in crash_write+0x134/0x140\n",
            "[  495.503671] [ T9971] Read at addr XXXXXXXXXXXXXXXX by task DELETED\n",
            "[  495.510025] [ T9971] Pointer tag: [f0], memory tag: [fe]\n",
            "[  495.515400] [ T9971] \n",
    };

    ReadLines(in);
    ASSERT_TRUE(parser.ReportReady());
    ASSERT_EQ("KASAN", parser.ReportType());
    ASSERT_EQ("BUG: KASAN: invalid-access in crash_write+0x134/0x140", parser.ReportTitle());
    ASSERT_TRUE(CheckReport(report));
}

TEST_F(DmesgParserTest, SimpleKfenceReport) {
    std::vector<std::string> in = {
            "[  495.412333] [    T1] init: this line will be dropped\n",
            "[  495.412345] [ T9971] "
            "==================================================================\n",
            "[  495.496694] [ T9971] BUG: KFENCE: memory corruption in "
            "test_corruption+0x98/0x19c\n",
            "[  495.712345] [ T9971] "
            "==================================================================\n",
            "[  495.767899] [ T9971] logs after the separator do not belong to report\n",
    };

    std::vector<std::string> report = {
            "[  495.496694] [ T9971] BUG: KFENCE: memory corruption in "
            "test_corruption+0x98/0x19c\n",
    };

    ReadLines(in);
    ASSERT_TRUE(parser.ReportReady());
    ASSERT_EQ("KFENCE", parser.ReportType());
    ASSERT_EQ("BUG: KFENCE: memory corruption in test_corruption+0x98/0x19c", parser.ReportTitle());
    ASSERT_TRUE(CheckReport(report));
}

TEST_F(DmesgParserTest, StrippedKfenceReport) {
    std::vector<std::string> in = {
            "[  200.412333] [    T1] init: this line will be dropped\n",
            "[  213.648234] [ T8752] "
            "==================================================================\n",
            "[  213.648253] [ T8752] BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174\n",
            "[  213.648262] [ T8752] Out-of-bounds write at 0xffffff8938a05000 (4096B left of "
            "kfence-#2):\n",
            "[  213.648270] [ T8752]  crash_write+0x14c/0x174\n",
            "[  213.648367] [ T8752] kfence-#2 [0xffffff8938a06000-0xffffff8938a0603f, size=64, "
            "cache=kmalloc-128] allocated by task 1:\n",
            "[  213.648471] [ T8752] CPU: 1 PID: 8752 Comm: sh Tainted: G         C O\n",
            "[  213.648478] [ T8752] Hardware name: Phone 1\n",
            "[  213.648498] [ T8752] "
            "==================================================================\n",
            "[  495.767899] [ T8752] logs after the separator do not belong to report\n",
    };

    std::vector<std::string> report = {
            "[  213.648253] [ T8752] BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174\n",
            "[  213.648262] [ T8752] Out-of-bounds write at XXXXXXXXXXXXXXXX (4096B left of "
            "kfence-#2):\n",
            "[  213.648270] [ T8752]  crash_write+0x14c/0x174\n",
            "[  213.648367] [ T8752] kfence-#2 [XXXXXXXXXXXXXXXX-XXXXXXXXXXXXXXXX, size=64, "
            "cache=kmalloc-128] allocated by task DELETED\n",
    };

    ReadLines(in);
    ASSERT_TRUE(parser.ReportReady());
    ASSERT_EQ("KFENCE", parser.ReportType());
    ASSERT_EQ("BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174", parser.ReportTitle());
    ASSERT_TRUE(CheckReport(report));
}

TEST_F(DmesgParserTest, PartialReport) {
    std::vector<std::string> in = {
            "[  213.648234] [ T8752] "
            "==================================================================\n",
            "[  213.648253] [ T8752] BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174\n",
            "[  213.648262] [ T8752] Out-of-bounds write at 0xffffff8938a05000 (4096B left of "
            "kfence-#2):\n",
            "[  213.648270] [ T8752]  crash_write+0x14c/0x174\n",
    };

    ReadLines(in);
    ASSERT_FALSE(parser.ReportReady());
}

TEST_F(DmesgParserTest, TwoReports) {
    std::vector<std::string> in = {
            "[  200.412333] [    T1] init: this line will be dropped\n",
            "[  213.648234] [ T8752] "
            "==================================================================\n",
            "[  213.648253] [ T8752] BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174\n",
            "[  213.648262] [ T8752] Out-of-bounds write at 0xffffff8938a05000 (4096B left of "
            "kfence-#2):\n",
            "[  214.648234] [ T9971] "
            "==================================================================\n",
            "[  215.496694] [ T9971] BUG: KFENCE: memory corruption in "
            "test_corruption+0x98/0x19c\n",
            "[  216.648270] [ T8752]  crash_write+0x14c/0x174\n",
            "[  217.648234] [ T8752] "
            "==================================================================\n",
    };

    std::vector<std::string> report = {
            "[  213.648253] [ T8752] BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174\n",
            "[  213.648262] [ T8752] Out-of-bounds write at XXXXXXXXXXXXXXXX (4096B left of "
            "kfence-#2):\n",
            "[  216.648270] [ T8752]  crash_write+0x14c/0x174\n",
    };

    ReadLines(in);
    ASSERT_TRUE(parser.ReportReady());
    ASSERT_EQ("KFENCE", parser.ReportType());
    ASSERT_EQ("BUG: KFENCE: out-of-bounds write in crash_write+0x14c/0x174", parser.ReportTitle());
    ASSERT_TRUE(CheckReport(report));
}
