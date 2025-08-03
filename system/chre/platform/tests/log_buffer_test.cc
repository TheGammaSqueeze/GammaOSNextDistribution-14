/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <string>

#include "chre/core/event.h"
#include "chre/platform/atomic.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/bt_snoop_log.h"
#include "chre/platform/shared/log_buffer.h"

using testing::ContainerEq;

namespace chre {

// TODO(b/146164384): Test that the onLogsReady callback is called
//                    asynchronously

class TestLogBufferCallback : public LogBufferCallbackInterface {
 public:
  void onLogsReady() {
    // Do nothing
  }
};

static constexpr size_t kDefaultBufferSize = 1024;

// Helpers
void copyStringWithOffset(char *destination, const char *source,
                          size_t sourceOffset) {
  size_t strlength = strlen(source + sourceOffset);
  // +1 to copy nullbyte on the end
  memcpy(destination, source + sourceOffset, strlength + 1);
}

TEST(LogBuffer, HandleOneLogAndCopy) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 20;
  char outBuffer[kOutBufferSize];
  const char *testLogStr = "test";
  char testedBuffer[kOutBufferSize];
  TestLogBufferCallback callback;

  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, strlen(testLogStr) + LogBuffer::kLogDataOffset + 1);
  copyStringWithOffset(testedBuffer, outBuffer, LogBuffer::kLogDataOffset);
  EXPECT_TRUE(strcmp(testedBuffer, testLogStr) == 0);
}

TEST(LogBuffer, HandleTwoLogsAndCopy) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 30;
  char outBuffer[kOutBufferSize];
  const char *testLogStr = "test";
  const char *testLogStr2 = "test2";
  char testedBuffer[kOutBufferSize];
  TestLogBufferCallback callback;

  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr2);
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, strlen(testLogStr) + strlen(testLogStr2) +
                             2 * LogBuffer::kLogDataOffset + 2);
  copyStringWithOffset(testedBuffer, outBuffer, LogBuffer::kLogDataOffset);
  EXPECT_TRUE(strcmp(testedBuffer, testLogStr) == 0);
  copyStringWithOffset(testedBuffer, outBuffer,
                       2 * LogBuffer::kLogDataOffset + strlen(testLogStr) + 1);
  EXPECT_TRUE(strcmp(testedBuffer, testLogStr2) == 0);
}

TEST(LogBuffer, FailOnMoreCopyThanHandle) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 20;
  char outBuffer[kOutBufferSize];
  const char *testLogStr = "test";
  TestLogBufferCallback callback;

  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);
  size_t numLogsDropped;
  logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, FailOnHandleLargerLogThanBufferSize) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 20;
  char outBuffer[kOutBufferSize];
  // Note the size of this log is too big to fit in the buffer that we are
  // using for the LogBuffer object
  std::string testLogStrStr(kDefaultBufferSize + 1, 'a');
  TestLogBufferCallback callback;

  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStrStr.c_str());
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  // Should not be able to read this log out because there should be no log in
  // the first place
  EXPECT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, StringLogOverwritten) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 200;
  char outBuffer[kOutBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  constexpr size_t kLogPayloadSize = 100;
  constexpr size_t kBufferUsePerLog = LogBuffer::kLogDataOffset +
                                      LogBuffer::kStringLogOverhead +
                                      kLogPayloadSize;
  constexpr int kNumInsertions = 10;
  constexpr int kNumLogDropsExpected =
      kNumInsertions - kDefaultBufferSize / kBufferUsePerLog;
  static_assert(kNumLogDropsExpected > 0);

  // This for loop adds 1060 (kNumInsertions * kBufferUsePerLog) bytes of data
  // through the buffer which is > than 1024.
  for (size_t i = 0; i < kNumInsertions; i++) {
    std::string testLogStrStr(kLogPayloadSize, 'a' + i);
    const char *testLogStr = testLogStrStr.c_str();
    logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);
  }
  EXPECT_EQ(logBuffer.getBufferSize(),
            (kNumInsertions - kNumLogDropsExpected) * kBufferUsePerLog);
  EXPECT_EQ(logBuffer.getNumLogsDropped(), kNumLogDropsExpected);

  for (size_t i = logBuffer.getNumLogsDropped(); i < kNumInsertions; i++) {
    // Should have read out the i-th from front test log string which a string
    // log 'a' + i.
    size_t numLogsDropped;
    size_t bytesCopied =
        logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
    EXPECT_TRUE(strcmp(outBuffer + LogBuffer::kLogDataOffset,
                       std::string(kLogPayloadSize, 'a' + i).c_str()) == 0);
    EXPECT_EQ(bytesCopied, kBufferUsePerLog);
  }
}

TEST(LogBuffer, TokenizedLogOverwritten) {
  char buffer[kDefaultBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  constexpr size_t kLogPayloadSize = 100;
  constexpr size_t kBufferUsePerLog = LogBuffer::kLogDataOffset +
                                      LogBuffer::kTokenizedLogOffset +
                                      kLogPayloadSize;
  constexpr int kNumInsertions = 10;
  constexpr int kNumLogDropsExpected =
      kNumInsertions - kDefaultBufferSize / kBufferUsePerLog;
  static_assert(kNumLogDropsExpected > 0);

  // This for loop adds 1060 (kNumInsertions * kBufferUsePerLog) bytes of data
  // through the buffer which is > than 1024.
  for (size_t i = 0; i < kNumInsertions; i++) {
    std::vector<uint8_t> testData(kLogPayloadSize, i);
    logBuffer.handleEncodedLog(LogBufferLogLevel::INFO, 0, testData.data(),
                               testData.size());
  }
  EXPECT_EQ(logBuffer.getBufferSize(),
            (kNumInsertions - kNumLogDropsExpected) * kBufferUsePerLog);
  EXPECT_EQ(logBuffer.getNumLogsDropped(), kNumLogDropsExpected);

  for (size_t i = logBuffer.getNumLogsDropped(); i < kNumInsertions; i++) {
    // Should have read out the i-th from front test log data which is an
    // integer i.
    std::vector<uint8_t> outBuffer(kBufferUsePerLog, 0x77);
    size_t numLogsDropped;
    size_t bytesCopied =
        logBuffer.copyLogs(outBuffer.data(), outBuffer.size(), &numLogsDropped);

    // Validate that the log size in Tokenized log header matches the expected
    // log size.
    EXPECT_EQ(outBuffer[LogBuffer::kLogDataOffset], kLogPayloadSize);

    outBuffer.erase(outBuffer.begin(), outBuffer.begin() +
                                           LogBuffer::kLogDataOffset +
                                           LogBuffer::kTokenizedLogOffset);
    EXPECT_THAT(outBuffer,
                ContainerEq(std::vector<uint8_t>(kLogPayloadSize, i)));
    EXPECT_EQ(bytesCopied, kBufferUsePerLog);
  }
}

TEST(LogBuffer, BtSnoopLogOverwritten) {
  char buffer[kDefaultBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  constexpr size_t kLogPayloadSize = 100;
  constexpr size_t kBufferUsePerLog = LogBuffer::kLogDataOffset +
                                      LogBuffer::kBtSnoopLogOffset +
                                      kLogPayloadSize;
  constexpr int kNumInsertions = 10;
  constexpr int kNumLogDropsExpected =
      kNumInsertions - kDefaultBufferSize / kBufferUsePerLog;
  static_assert(kNumLogDropsExpected > 0);

  // This for loop adds 1070 (kNumInsertions * kBufferUsePerLog) bytes of data
  // through the buffer which is > than 1024.
  for (size_t i = 0; i < kNumInsertions; i++) {
    std::vector<uint8_t> testData(kLogPayloadSize, i);
    logBuffer.handleBtLog(BtSnoopDirection::INCOMING_FROM_BT_CONTROLLER, 0,
                          testData.data(), testData.size());
  }
  EXPECT_EQ(logBuffer.getBufferSize(),
            (kNumInsertions - kNumLogDropsExpected) * kBufferUsePerLog);
  EXPECT_EQ(logBuffer.getNumLogsDropped(), kNumLogDropsExpected);

  for (size_t i = logBuffer.getNumLogsDropped(); i < kNumInsertions; i++) {
    // Should have read out the i-th from front test log data which is an
    // integer i.
    std::vector<uint8_t> outBuffer(kBufferUsePerLog, 0x77);
    size_t numLogsDropped;
    size_t bytesCopied =
        logBuffer.copyLogs(outBuffer.data(), outBuffer.size(), &numLogsDropped);

    // Validate that the Bt Snoop log header matches the expected log direction
    // and size.
    constexpr int kBtSnoopLogHeaderSizeOffset = 1;
    EXPECT_EQ(
        static_cast<BtSnoopDirection>(outBuffer[LogBuffer::kLogDataOffset]),
        BtSnoopDirection::INCOMING_FROM_BT_CONTROLLER);
    EXPECT_EQ(
        outBuffer[LogBuffer::kLogDataOffset + kBtSnoopLogHeaderSizeOffset],
        kLogPayloadSize);

    outBuffer.erase(outBuffer.begin(), outBuffer.begin() +
                                           LogBuffer::kLogDataOffset +
                                           LogBuffer::kBtSnoopLogOffset);
    EXPECT_THAT(outBuffer,
                ContainerEq(std::vector<uint8_t>(kLogPayloadSize, i)));
    EXPECT_EQ(bytesCopied, kBufferUsePerLog);
  }
}

TEST(LogBuffer, NanoappTokenizedLogOverwritten) {
  char buffer[kDefaultBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  constexpr size_t kInstanceIdSize = 2;
  constexpr size_t kLogPayloadSize = 100;
  constexpr size_t kBufferUsePerLog = LogBuffer::kLogDataOffset +
                                      LogBuffer::kNanoappTokenizedLogOffset +
                                      kLogPayloadSize;
  constexpr int kNumInsertions = 10;
  constexpr int kNumLogDropsExpected =
      kNumInsertions - kDefaultBufferSize / kBufferUsePerLog;
  static_assert(kNumLogDropsExpected > 0);

  // This for loop adds 1080 (kNumInsertions * kBufferUsePerLog) bytes of data
  // through the buffer which is > than 1024.
  for (size_t i = 0; i < kNumInsertions; i++) {
    std::vector<uint8_t> testData(kLogPayloadSize, i);
    logBuffer.handleNanoappTokenizedLog(LogBufferLogLevel::INFO, 0,
                                        chre::kSystemInstanceId,
                                        testData.data(), testData.size());
  }
  EXPECT_EQ(logBuffer.getBufferSize(),
            (kNumInsertions - kNumLogDropsExpected) * kBufferUsePerLog);
  EXPECT_EQ(logBuffer.getNumLogsDropped(), kNumLogDropsExpected);

  for (size_t i = logBuffer.getNumLogsDropped(); i < kNumInsertions; i++) {
    // Should have read out the i-th from front test log data which is an
    // integer i.
    std::vector<uint8_t> outBuffer(kBufferUsePerLog, 0x77);
    size_t numLogsDropped;
    size_t bytesCopied =
        logBuffer.copyLogs(outBuffer.data(), outBuffer.size(), &numLogsDropped);

    // Validate that the log size in the nanoapp tokenized log header matches
    // the expected log size.
    EXPECT_EQ(outBuffer[LogBuffer::kLogDataOffset + kInstanceIdSize],
              kLogPayloadSize);

    outBuffer.erase(outBuffer.begin(),
                    outBuffer.begin() + LogBuffer::kLogDataOffset +
                        LogBuffer::kNanoappTokenizedLogOffset);
    EXPECT_THAT(outBuffer,
                ContainerEq(std::vector<uint8_t>(kLogPayloadSize, i)));
    EXPECT_EQ(bytesCopied, kBufferUsePerLog);
  }
}

TEST(LogBuffer, CopyIntoEmptyBuffer) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 0;
  char outBuffer[kOutBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, "test");
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, NoCopyInfoBufferAfterHandleEmptyLog) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 200;
  char outBuffer[kOutBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, "");
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, HandleLogOfNullBytes) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 200;
  char outBuffer[kOutBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, "\0\0\0");
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  EXPECT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, TruncateLongLog) {
  char buffer[kDefaultBufferSize];
  constexpr size_t kOutBufferSize = 500;
  char outBuffer[kOutBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);
  std::string testStr(LogBuffer::kLogMaxSize + 1, 'a');

  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testStr.c_str());
  size_t numLogsDropped;
  size_t bytesCopied =
      logBuffer.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);

  // Should truncate the logs down to the kLogMaxSize + kLogDataOffset by the
  // time it is copied out.
  EXPECT_EQ(bytesCopied, LogBuffer::kLogMaxSize + LogBuffer::kLogDataOffset);
}

TEST(LogBuffer, WouldCauseOverflowTest) {
  char buffer[kDefaultBufferSize];
  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  // With an empty buffer inserting an empty string (only null terminator)
  // should not overflow ASSERT because if this fails the next ASSERT statement
  // is undefined most likely.
  ASSERT_FALSE(logBuffer.logWouldCauseOverflow(1));

  // This for loop adds 1000 bytes of data (kLogPayloadSize + kStringLogOverhead
  // + kLogDataOffset). There is 24 bytes of space left in the buffer after this
  // loop.
  constexpr size_t kLogPayloadSize = 94;
  for (size_t i = 0; i < 10; i++) {
    std::string testLogStrStr(kLogPayloadSize, 'a');
    const char *testLogStr = testLogStrStr.c_str();
    logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);
  }

  // This adds 18 (kLogPayloadSize + kStringLogOverhead + kLogDataOffset) bytes
  // of data. After this log entry there is room enough for a log of character
  // size 1.
  constexpr size_t kLastLogPayloadSize = 12;
  std::string testLogStrStr(kLastLogPayloadSize, 'a');
  const char *testLogStr = testLogStrStr.c_str();
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr);

  // There should be just enough space for this log.
  ASSERT_FALSE(logBuffer.logWouldCauseOverflow(1));

  // Inserting any more than a one char log should cause overflow.
  ASSERT_TRUE(logBuffer.logWouldCauseOverflow(2));
}

TEST(LogBuffer, TransferTest) {
  char buffer[kDefaultBufferSize];
  const size_t kOutBufferSize = 10;
  char outBuffer[kOutBufferSize];
  size_t numLogsDropped;
  TestLogBufferCallback callback;
  LogBuffer logBufferFrom(&callback, buffer, kDefaultBufferSize);
  LogBuffer logBufferTo(&callback, buffer, kDefaultBufferSize);

  const char *str1 = "str1";
  const char *str2 = "str2";
  const char *str3 = "str3";

  logBufferFrom.handleLog(LogBufferLogLevel::INFO, 0, str1);
  logBufferFrom.handleLog(LogBufferLogLevel::INFO, 0, str2);
  logBufferFrom.handleLog(LogBufferLogLevel::INFO, 0, str3);

  logBufferFrom.transferTo(logBufferTo);

  // The logs should have the text of each of the logs pushed onto the From
  // buffer in FIFO ordering.
  logBufferTo.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
  ASSERT_TRUE(strcmp(outBuffer + LogBuffer::kLogDataOffset, str1) == 0);
  logBufferTo.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
  ASSERT_TRUE(strcmp(outBuffer + LogBuffer::kLogDataOffset, str2) == 0);
  logBufferTo.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
  ASSERT_TRUE(strcmp(outBuffer + LogBuffer::kLogDataOffset, str3) == 0);

  size_t bytesCopied =
      logBufferTo.copyLogs(outBuffer, kOutBufferSize, &numLogsDropped);
  // There should have been no logs left in the To buffer for that last copyLogs
  ASSERT_EQ(bytesCopied, 0);
}

TEST(LogBuffer, GetLogDataLengthTest) {
  char buffer[kDefaultBufferSize];

  TestLogBufferCallback callback;
  LogBuffer logBuffer(&callback, buffer, kDefaultBufferSize);

  constexpr size_t kLogPayloadSize = 10;
  constexpr size_t kBufferUseStringLog = LogBuffer::kLogDataOffset +
                                         LogBuffer::kStringLogOverhead +
                                         kLogPayloadSize;
  constexpr size_t kBufferUseTokenizedLog = LogBuffer::kLogDataOffset +
                                            LogBuffer::kTokenizedLogOffset +
                                            kLogPayloadSize;
  constexpr size_t kBufferUseBtSnoopLog = LogBuffer::kLogDataOffset +
                                          LogBuffer::kBtSnoopLogOffset +
                                          kLogPayloadSize;

  uint8_t mCurrentLogStartingIndex = 0;

  std::string testLogStr(kLogPayloadSize, 'a');
  logBuffer.handleLog(LogBufferLogLevel::INFO, 0, testLogStr.c_str());
  EXPECT_EQ(logBuffer.getLogDataLength(
                mCurrentLogStartingIndex + LogBuffer::kLogDataOffset,
                LogType::STRING),
            LogBuffer::kStringLogOverhead + kLogPayloadSize);
  mCurrentLogStartingIndex += kBufferUseStringLog;

  std::vector<uint8_t> testLogTokenized(kLogPayloadSize, 0x77);
  logBuffer.handleEncodedLog(LogBufferLogLevel::INFO, 0,
                             testLogTokenized.data(), testLogTokenized.size());
  EXPECT_EQ(logBuffer.getLogDataLength(
                mCurrentLogStartingIndex + LogBuffer::kLogDataOffset,
                LogType::TOKENIZED),
            LogBuffer::kTokenizedLogOffset + kLogPayloadSize);
  mCurrentLogStartingIndex += kBufferUseTokenizedLog;

  std::vector<uint8_t> testLogBtSnoop(kLogPayloadSize, 0x77);
  logBuffer.handleBtLog(BtSnoopDirection::INCOMING_FROM_BT_CONTROLLER, 0,
                        testLogBtSnoop.data(), testLogBtSnoop.size());
  EXPECT_EQ(logBuffer.getLogDataLength(
                mCurrentLogStartingIndex + LogBuffer::kLogDataOffset,
                LogType::BLUETOOTH),
            LogBuffer::kBtSnoopLogOffset + kLogPayloadSize);
  mCurrentLogStartingIndex += kBufferUseBtSnoopLog;

  std::vector<uint8_t> testLogNanoappTokenized(kLogPayloadSize, 0x77);
  logBuffer.handleNanoappTokenizedLog(LogBufferLogLevel::INFO, 0,
                                      kSystemInstanceId, testLogBtSnoop.data(),
                                      testLogBtSnoop.size());
  EXPECT_EQ(logBuffer.getLogDataLength(
                mCurrentLogStartingIndex + LogBuffer::kLogDataOffset,
                LogType::NANOAPP_TOKENIZED),
            LogBuffer::kNanoappTokenizedLogOffset + kLogPayloadSize);
}

// TODO(srok): Add multithreaded tests

}  // namespace chre
