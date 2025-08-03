/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "host/hal_generic/aidl/event_logger.h"

#include "aidl/android/hardware/contexthub/NanoappBinary.h"
#include "chre_host/generated/host_messages_generated.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace aidl::android::hardware::contexthub {
namespace {

using ::testing::IsEmpty;
using ::testing::Not;

// Exposes protected members for testing.
class TestEventLogger : public EventLogger {
 public:
  void setNowMs(int64_t ms) {
    mNowMs = ms;
  }

  using EventLogger::NanoappLoad;

  const auto &nanoappLoads() {
    return mNanoappLoads;
  }

  const auto &nanoappUnloads() {
    return mNanoappUnloads;
  }

  const auto &contextHubRestarts() {
    return mContextHubRestarts;
  }

  const auto &messagesToNanoapp() {
    return mMsgToNanoapp;
  }

  const auto &messagesFromNanoapp() {
    return mMsgFromNanoapp;
  }
};

TEST(EventLogger, keepTheMostRecentNanoappLoads) {
  TestEventLogger log;
  for (int i = 0; i < EventLogger::kMaxNanoappEvents + 10; ++i) {
    NanoappBinary app;
    app.nanoappId = i;
    log.logNanoappLoad(app, true);
  }

  EXPECT_EQ(log.nanoappLoads().size(), EventLogger::kMaxNanoappEvents);

  for (int i = 0; i < EventLogger::kMaxNanoappEvents; ++i) {
    EXPECT_EQ(log.nanoappLoads()[i].id, i + 10);
  }
}

TEST(EventLogger, keepTheMostRecentNanoappUnloads) {
  TestEventLogger log;
  for (int i = 0; i < EventLogger::kMaxNanoappEvents + 10; ++i) {
    log.logNanoappUnload(i, true);
  }

  EXPECT_EQ(log.nanoappUnloads().size(), EventLogger::kMaxNanoappEvents);

  for (int i = 0; i < EventLogger::kMaxNanoappEvents; ++i) {
    EXPECT_EQ(log.nanoappUnloads()[i].id, i + 10);
  }
}

TEST(EventLogger, keepTheMostRecentContextHubRestarts) {
  TestEventLogger log;
  for (int i = 0; i < EventLogger::kMaxRestartEvents + 10; ++i) {
    log.setNowMs(i);
    log.logContextHubRestart();
  }

  EXPECT_EQ(log.contextHubRestarts().size(), EventLogger::kMaxRestartEvents);

  for (int i = 0; i < EventLogger::kMaxRestartEvents; ++i) {
    EXPECT_EQ(log.contextHubRestarts()[i], i + 10);
  }
}

TEST(EventLogger, keepTheMostRecentMessagesToNanoapp) {
  TestEventLogger log;
  for (int i = 0; i < EventLogger::kMaxMessageEvents + 10; ++i) {
    ContextHubMessage msg;
    msg.nanoappId = i;
    log.logMessageToNanoapp(msg, true);
  }

  EXPECT_EQ(log.messagesToNanoapp().size(), EventLogger::kMaxMessageEvents);

  for (int i = 0; i < EventLogger::kMaxMessageEvents; ++i) {
    EXPECT_EQ(log.messagesToNanoapp()[i].id, i + 10);
  }
}

TEST(EventLogger, keepTheMostRecentMessagesFromNanoapp) {
  TestEventLogger log;
  for (int i = 0; i < EventLogger::kMaxMessageEvents + 10; ++i) {
    chre::fbs::NanoappMessageT msg;
    msg.app_id = i;
    log.logMessageFromNanoapp(msg);
  }

  EXPECT_EQ(log.messagesFromNanoapp().size(), EventLogger::kMaxMessageEvents);

  for (int i = 0; i < EventLogger::kMaxMessageEvents; ++i) {
    EXPECT_EQ(log.messagesFromNanoapp()[i].id, i + 10);
  }
}

TEST(EventLogger, dumpTheEventsAsString) {
  TestEventLogger log;

  log.setNowMs(10);
  NanoappBinary app;
  app.nanoappId = 1;
  app.nanoappVersion = 2;
  app.customBinary = {1, 2, 3};
  log.logNanoappLoad(app, true);

  log.setNowMs(20);
  log.logNanoappUnload(2, true);

  log.setNowMs(30);
  log.logContextHubRestart();

  log.setNowMs(40);
  ContextHubMessage toMsg;
  toMsg.nanoappId = 4;
  toMsg.messageBody = {1, 2, 3};
  log.logMessageToNanoapp(toMsg, true);

  log.setNowMs(50);
  chre::fbs::NanoappMessageT fromMsg;
  fromMsg.app_id = 5;
  fromMsg.message = {1, 2, 3};
  log.logMessageFromNanoapp(fromMsg);

  EXPECT_THAT(log.dump(), Not(IsEmpty()));
}

}  // namespace
}  // namespace aidl::android::hardware::contexthub
