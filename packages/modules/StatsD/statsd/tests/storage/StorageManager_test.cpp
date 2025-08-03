// Copyright (C) 2019 The Android Open Source Project
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

#include "src/storage/StorageManager.h"

#include <android-base/unique_fd.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include "android-base/stringprintf.h"
#include "stats_log_util.h"
#include "tests/statsd_test_util.h"
#include "utils/DbUtils.h"

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

using namespace testing;

using std::make_shared;
using std::shared_ptr;
using std::vector;
using testing::Contains;

TEST(StorageManagerTest, TrainInfoReadWriteTest) {
    InstallTrainInfo trainInfo;
    trainInfo.trainVersionCode = 12345;
    trainInfo.trainName = "This is a train name #)$(&&$";
    trainInfo.status = 1;
    const char* expIds = "test_ids";
    trainInfo.experimentIds.assign(expIds, expIds + strlen(expIds));

    bool result;

    result = StorageManager::writeTrainInfo(trainInfo);

    EXPECT_TRUE(result);

    InstallTrainInfo trainInfoResult;
    result = StorageManager::readTrainInfo(trainInfo.trainName, trainInfoResult);
    EXPECT_TRUE(result);

    EXPECT_EQ(trainInfo.trainVersionCode, trainInfoResult.trainVersionCode);
    ASSERT_EQ(trainInfo.trainName.size(), trainInfoResult.trainName.size());
    EXPECT_EQ(trainInfo.trainName, trainInfoResult.trainName);
    EXPECT_EQ(trainInfo.status, trainInfoResult.status);
    ASSERT_EQ(trainInfo.experimentIds.size(), trainInfoResult.experimentIds.size());
    EXPECT_EQ(trainInfo.experimentIds, trainInfoResult.experimentIds);
}

TEST(StorageManagerTest, TrainInfoReadWriteTrainNameSizeOneTest) {
    InstallTrainInfo trainInfo;
    trainInfo.trainVersionCode = 12345;
    trainInfo.trainName = "{";
    trainInfo.status = 1;
    const char* expIds = "test_ids";
    trainInfo.experimentIds.assign(expIds, expIds + strlen(expIds));

    bool result;

    result = StorageManager::writeTrainInfo(trainInfo);

    EXPECT_TRUE(result);

    InstallTrainInfo trainInfoResult;
    result = StorageManager::readTrainInfo(trainInfo.trainName, trainInfoResult);
    EXPECT_TRUE(result);

    EXPECT_EQ(trainInfo.trainVersionCode, trainInfoResult.trainVersionCode);
    ASSERT_EQ(trainInfo.trainName.size(), trainInfoResult.trainName.size());
    EXPECT_EQ(trainInfo.trainName, trainInfoResult.trainName);
    EXPECT_EQ(trainInfo.status, trainInfoResult.status);
    ASSERT_EQ(trainInfo.experimentIds.size(), trainInfoResult.experimentIds.size());
    EXPECT_EQ(trainInfo.experimentIds, trainInfoResult.experimentIds);
}

TEST(StorageManagerTest, SortFileTest) {
    vector<StorageManager::FileInfo> list;
    // assume now sec is 500
    list.emplace_back("200_5000_123454", false, 20, 300);
    list.emplace_back("300_2000_123454_history", true, 30, 200);
    list.emplace_back("400_100009_123454_history", true, 40, 100);
    list.emplace_back("100_2000_123454", false, 50, 400);

    StorageManager::sortFiles(&list);
    EXPECT_EQ("200_5000_123454", list[0].mFileName);
    EXPECT_EQ("100_2000_123454", list[1].mFileName);
    EXPECT_EQ("400_100009_123454_history", list[2].mFileName);
    EXPECT_EQ("300_2000_123454_history", list[3].mFileName);
}

const string testDir = "/data/misc/stats-data/";
const string file1 = testDir + "2557169347_1066_1";
const string file2 = testDir + "2557169349_1066_1";
const string file1_history = file1 + "_history";
const string file2_history = file2 + "_history";

bool prepareLocalHistoryTestFiles() {
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(
            open(file1.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, S_IRUSR | S_IWUSR)));
    if (fd != -1) {
        dprintf(fd, "content");
    } else {
        return false;
    }

    android::base::unique_fd fd2(TEMP_FAILURE_RETRY(
            open(file2.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, S_IRUSR | S_IWUSR)));
    if (fd2 != -1) {
        dprintf(fd2, "content");
    } else {
        return false;
    }
    return true;
}

void clearLocalHistoryTestFiles() {
    TEMP_FAILURE_RETRY(remove(file1.c_str()));
    TEMP_FAILURE_RETRY(remove(file2.c_str()));
    TEMP_FAILURE_RETRY(remove(file1_history.c_str()));
    TEMP_FAILURE_RETRY(remove(file2_history.c_str()));
}

bool fileExist(string name) {
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(name.c_str(), O_RDONLY | O_CLOEXEC)));
    return fd != -1;
}

/* The following AppendConfigReportTests test the 4 combinations of [whether erase data] [whether
 * the caller is adb] */
TEST(StorageManagerTest, AppendConfigReportTest1) {
    EXPECT_TRUE(prepareLocalHistoryTestFiles());

    ProtoOutputStream out;
    StorageManager::appendConfigMetricsReport(ConfigKey(1066, 1), &out, false /*erase?*/,
                                              false /*isAdb?*/);

    EXPECT_FALSE(fileExist(file1));
    EXPECT_FALSE(fileExist(file2));

    EXPECT_TRUE(fileExist(file1_history));
    EXPECT_TRUE(fileExist(file2_history));
    clearLocalHistoryTestFiles();
}

TEST(StorageManagerTest, AppendConfigReportTest2) {
    EXPECT_TRUE(prepareLocalHistoryTestFiles());

    ProtoOutputStream out;
    StorageManager::appendConfigMetricsReport(ConfigKey(1066, 1), &out, true /*erase?*/,
                                              false /*isAdb?*/);

    EXPECT_FALSE(fileExist(file1));
    EXPECT_FALSE(fileExist(file2));
    EXPECT_FALSE(fileExist(file1_history));
    EXPECT_FALSE(fileExist(file2_history));

    clearLocalHistoryTestFiles();
}

TEST(StorageManagerTest, AppendConfigReportTest3) {
    EXPECT_TRUE(prepareLocalHistoryTestFiles());

    ProtoOutputStream out;
    StorageManager::appendConfigMetricsReport(ConfigKey(1066, 1), &out, false /*erase?*/,
                                              true /*isAdb?*/);

    EXPECT_TRUE(fileExist(file1));
    EXPECT_TRUE(fileExist(file2));
    EXPECT_FALSE(fileExist(file1_history));
    EXPECT_FALSE(fileExist(file2_history));

    clearLocalHistoryTestFiles();
}

TEST(StorageManagerTest, AppendConfigReportTest4) {
    EXPECT_TRUE(prepareLocalHistoryTestFiles());

    ProtoOutputStream out;
    StorageManager::appendConfigMetricsReport(ConfigKey(1066, 1), &out, true /*erase?*/,
                                              true /*isAdb?*/);

    EXPECT_FALSE(fileExist(file1));
    EXPECT_FALSE(fileExist(file2));
    EXPECT_FALSE(fileExist(file1_history));
    EXPECT_FALSE(fileExist(file2_history));

    clearLocalHistoryTestFiles();
}

TEST(StorageManagerTest, TrainInfoReadWrite32To64BitTest) {
    InstallTrainInfo trainInfo;
    trainInfo.trainVersionCode = 12345;
    trainInfo.trainName = "This is a train name #)$(&&$";
    trainInfo.status = 1;
    const char* expIds = "test_ids";
    trainInfo.experimentIds.assign(expIds, expIds + strlen(expIds));

    // Write the train info. fork the code to always write in 32 bit.
    StorageManager::deleteSuffixedFiles(TRAIN_INFO_DIR, trainInfo.trainName.c_str());
    std::string fileName = base::StringPrintf("%s/%ld_%s", TRAIN_INFO_DIR, (long)getWallClockSec(),
                                              trainInfo.trainName.c_str());

    int fd = open(fileName.c_str(), O_WRONLY | O_CREAT | O_CLOEXEC, S_IRUSR | S_IWUSR);
    ASSERT_NE(fd, -1);

    size_t result;
    // Write the magic word
    result = write(fd, &TRAIN_INFO_FILE_MAGIC, sizeof(TRAIN_INFO_FILE_MAGIC));
    ASSERT_EQ(result, sizeof(TRAIN_INFO_FILE_MAGIC));

    // Write the train version
    const size_t trainVersionCodeByteCount = sizeof(trainInfo.trainVersionCode);
    result = write(fd, &trainInfo.trainVersionCode, trainVersionCodeByteCount);
    ASSERT_EQ(result, trainVersionCodeByteCount);

    // Write # of bytes in trainName to file.
    // NB: this is changed from size_t to int32_t for this test.
    const int32_t trainNameSize = trainInfo.trainName.size();
    const size_t trainNameSizeByteCount = sizeof(trainNameSize);
    result = write(fd, (uint8_t*)&trainNameSize, trainNameSizeByteCount);
    ASSERT_EQ(result, trainNameSizeByteCount);

    // Write trainName to file
    result = write(fd, trainInfo.trainName.c_str(), trainNameSize);
    ASSERT_EQ(result, trainNameSize);

    // Write status to file
    const size_t statusByteCount = sizeof(trainInfo.status);
    result = write(fd, (uint8_t*)&trainInfo.status, statusByteCount);
    ASSERT_EQ(result, statusByteCount);

    // Write experiment id count to file.
    // NB: this is changed from size_t to int32_t for this test.
    const int32_t experimentIdsCount = trainInfo.experimentIds.size();
    const size_t experimentIdsCountByteCount = sizeof(experimentIdsCount);
    result = write(fd, (uint8_t*)&experimentIdsCount, experimentIdsCountByteCount);
    ASSERT_EQ(result, experimentIdsCountByteCount);

    // Write experimentIds to file
    for (size_t i = 0; i < experimentIdsCount; i++) {
        const int64_t experimentId = trainInfo.experimentIds[i];
        const size_t experimentIdByteCount = sizeof(experimentId);
        result = write(fd, &experimentId, experimentIdByteCount);
        ASSERT_EQ(result, experimentIdByteCount);
    }

    // Write bools to file
    const size_t boolByteCount = sizeof(trainInfo.requiresStaging);
    result = write(fd, (uint8_t*)&trainInfo.requiresStaging, boolByteCount);
    ASSERT_EQ(result, boolByteCount);
    result = write(fd, (uint8_t*)&trainInfo.rollbackEnabled, boolByteCount);
    ASSERT_EQ(result, boolByteCount);
    result = write(fd, (uint8_t*)&trainInfo.requiresLowLatencyMonitor, boolByteCount);
    ASSERT_EQ(result, boolByteCount);
    close(fd);

    InstallTrainInfo trainInfoResult;
    EXPECT_TRUE(StorageManager::readTrainInfo(trainInfo.trainName, trainInfoResult));

    EXPECT_EQ(trainInfo.trainVersionCode, trainInfoResult.trainVersionCode);
    ASSERT_EQ(trainInfo.trainName.size(), trainInfoResult.trainName.size());
    EXPECT_EQ(trainInfo.trainName, trainInfoResult.trainName);
    EXPECT_EQ(trainInfo.status, trainInfoResult.status);
    ASSERT_EQ(trainInfo.experimentIds.size(), trainInfoResult.experimentIds.size());
    EXPECT_EQ(trainInfo.experimentIds, trainInfoResult.experimentIds);
}

TEST(StorageManagerTest, DeleteUnmodifiedOldDbFiles) {
    if (!isAtLeastU()) {
        GTEST_SKIP();
    }
    ConfigKey key(123, 12345);
    unique_ptr<LogEvent> event = CreateRestrictedLogEvent(/*atomTag=*/10, /*timestampNs=*/1000);
    dbutils::createTableIfNeeded(key, /*metricId=*/1, *event);
    EXPECT_TRUE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));

    int64_t wallClockSec = getWallClockSec() + (StatsdStats::kMaxAgeSecond + 1);
    StorageManager::enforceDbGuardrails(STATS_RESTRICTED_DATA_DIR, wallClockSec,
                                        /*maxBytes=*/INT_MAX);

    EXPECT_FALSE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
}

TEST(StorageManagerTest, DeleteLargeDbFiles) {
    if (!isAtLeastU()) {
        GTEST_SKIP();
    }
    ConfigKey key(123, 12345);
    unique_ptr<LogEvent> event = CreateRestrictedLogEvent(/*atomTag=*/10, /*timestampNs=*/1000);
    dbutils::createTableIfNeeded(key, /*metricId=*/1, *event);
    EXPECT_TRUE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));

    StorageManager::enforceDbGuardrails(STATS_RESTRICTED_DATA_DIR,
                                        /*wallClockSec=*/getWallClockSec(),
                                        /*maxBytes=*/0);

    EXPECT_FALSE(StorageManager::hasFile(
            base::StringPrintf("%s/%s", STATS_RESTRICTED_DATA_DIR, "123_12345.db").c_str()));
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
