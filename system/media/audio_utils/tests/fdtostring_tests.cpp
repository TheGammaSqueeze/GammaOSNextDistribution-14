/*
 * Copyright 2018 The Android Open Source Project
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

#include <audio_utils/FdToString.h>

#include <signal.h>
#include <chrono>

#include <gtest/gtest.h>

using namespace android::audio_utils;

TEST(audio_utils_fdtostring, basic) {
    signal(SIGPIPE, SIG_IGN);
    const std::string PREFIX{"aa "};
    const std::string TEST_STRING{"hello world\n"};

    auto writer_opt = FdToString::createWriter(PREFIX);
    ASSERT_TRUE(writer_opt.has_value());
    FdToString::Writer& writer = *writer_opt;
    const int fd = writer.borrowFdUnsafe();
    ASSERT_TRUE(fd >= 0);

    write(fd, TEST_STRING.c_str(), TEST_STRING.size());

    const std::string result = FdToString::closeWriterAndGetString(std::move(writer));
    ASSERT_EQ((PREFIX + TEST_STRING), result);
}

TEST(audio_utils_fdtostring, multiline) {
    signal(SIGPIPE, SIG_IGN);
    const std::string PREFIX{"aa "};
    const std::string INPUT[] = {"hello\n", "pt1", "pt2 ", "\n", "\n", "pt3\n", "pt4"};
    const std::string GOLDEN = "aa hello\naa pt1pt2 \naa \naa pt3\npt4";

    auto writer_opt = FdToString::createWriter(PREFIX);
    ASSERT_TRUE(writer_opt.has_value());
    FdToString::Writer& writer = *writer_opt;
    const int fd = writer.borrowFdUnsafe();
    ASSERT_TRUE(fd >= 0);

    for (const auto& str : INPUT) {
        write(fd, str.c_str(), str.size());
    }

    ASSERT_EQ(FdToString::closeWriterAndGetString(std::move(writer)), GOLDEN);
}

TEST(audio_utils_fdtostring, blocking) {
    signal(SIGPIPE, SIG_IGN);
    const std::string PREFIX{"- "};
    const std::string INPUT[] = {"1\n", "2\n", "3\n", "4\n", "5\n"};
    const std::string GOLDEN = "- 1\n- 2\n- 3\n- 4\n- 5\n";

    auto writer_opt = FdToString::createWriter(PREFIX, std::chrono::milliseconds{200});

    ASSERT_TRUE(writer_opt.has_value());
    FdToString::Writer& writer = *writer_opt;
    const int fd = writer.borrowFdUnsafe();
    ASSERT_TRUE(fd >= 0);

    // Chosen so that we shouldn't finish the entire array before the timeout
    constexpr auto WAIT = std::chrono::milliseconds{90};

    int count = 0;
    for (const auto& str : INPUT) {
        ASSERT_LT(count, 4) << "The reader has timed out, write should have failed by now";
        if (write(fd, str.c_str(), str.size()) < 0) break;
        std::this_thread::sleep_for(WAIT);
        count++;
    }

    ASSERT_EQ(FdToString::closeWriterAndGetString(std::move(writer)).substr(0, 8),
            GOLDEN.substr(0, 8)) << "Format mistake";
}
