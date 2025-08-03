/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gtest/gtest.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <cstring>
#include <thread>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/clients/discovery.h"
#include "chpp/macros.h"
#include "chpp/notifier.h"
#include "chpp/platform/platform_link.h"
#include "chpp/platform/utils.h"
#include "chpp/services.h"
#include "chpp/transport.h"
#include "chre/util/enum.h"
#include "chre/util/time.h"

namespace chre {

namespace {

constexpr uint64_t kResetWaitTimeMs = 5000;
constexpr uint64_t kDiscoveryWaitTimeMs = 5000;

void *workThread(void *transportState) {
  ChppTransportState *state = static_cast<ChppTransportState *>(transportState);

  auto linkContext =
      static_cast<struct ChppLinuxLinkState *>(state->linkContext);

  pthread_setname_np(pthread_self(), linkContext->workThreadName);

  chppWorkThreadStart(state);

  return nullptr;
}

#define TEST_UUID                                                           \
  {                                                                         \
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, \
        0x00, 0x00, 0x00, 0x12                                              \
  }

enum class Commands : uint16_t {
  kServiceNotification,
  kClientNotification,
};

constexpr uint16_t kNumCommands = 1;

struct ClientState {
  struct ChppEndpointState chppClientState;
  struct ChppOutgoingRequestState outReqStates[kNumCommands];
  bool serviceNotificationStatus;
  struct ChppNotifier notifier;
};

bool clientInit(void *clientState, uint8_t handle,
                struct ChppVersion serviceVersion);
void clientDeinit(void *clientState);
enum ChppAppErrorCode clientDispatchNotification(void *clientState,
                                                 uint8_t *buf, size_t len);
constexpr struct ChppClient kClient = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .resetNotifierFunctionPtr = nullptr,
    .matchNotifierFunctionPtr = nullptr,
    .responseDispatchFunctionPtr = nullptr,
    .notificationDispatchFunctionPtr = &clientDispatchNotification,
    .initFunctionPtr = &clientInit,
    .deinitFunctionPtr = &clientDeinit,
    .outReqCount = kNumCommands,
    .minLength = sizeof(struct ChppAppHeader),
};

// Called when a notification from a service is received.
enum ChppAppErrorCode clientDispatchNotification(void *clientState,
                                                 uint8_t *buf, size_t len) {
  auto state = static_cast<struct ClientState *>(clientState);

  // The response is composed of the app header only.
  if (len != sizeof(ChppAppHeader)) {
    return CHPP_APP_ERROR_NONE;
  }

  auto notification = reinterpret_cast<struct ChppAppHeader *>(buf);

  switch (notification->command) {
    case asBaseType(Commands::kServiceNotification):
      state->serviceNotificationStatus =
          notification->error == CHPP_APP_ERROR_NONE;
      chppNotifierSignal(&state->notifier, 1 /*signal*/);
      return CHPP_APP_ERROR_NONE;

    default:
      return CHPP_APP_ERROR_NONE;
  }
}

bool clientInit(void *clientState, uint8_t handle,
                struct ChppVersion serviceVersion) {
  UNUSED_VAR(serviceVersion);
  auto state = static_cast<struct ClientState *>(clientState);
  state->chppClientState.openState = CHPP_OPEN_STATE_OPENED;
  chppClientInit(&state->chppClientState, handle);
  return true;
}

void clientDeinit(void *clientState) {
  auto state = static_cast<struct ClientState *>(clientState);
  chppClientDeinit(&state->chppClientState);
  state->chppClientState.openState = CHPP_OPEN_STATE_CLOSED;
}

// Service
struct ServiceState {
  struct ChppEndpointState chppServiceState;
  struct ChppIncomingRequestState inReqStates[kNumCommands];
  bool clientNotificationStatus;
  struct ChppNotifier notifier;
};

// Called when a notification from a client is received.
enum ChppAppErrorCode serviceDispatchNotification(void *serviceState,
                                                  uint8_t *buf, size_t len) {
  auto state = static_cast<struct ServiceState *>(serviceState);

  // The response is composed of the app header only.
  if (len != sizeof(ChppAppHeader)) {
    return CHPP_APP_ERROR_NONE;
  }

  auto notification = reinterpret_cast<struct ChppAppHeader *>(buf);

  switch (notification->command) {
    case asBaseType(Commands::kClientNotification):
      state->clientNotificationStatus =
          notification->error == CHPP_APP_ERROR_NONE;
      chppNotifierSignal(&state->notifier, 1 /*signal*/);
      return CHPP_APP_ERROR_NONE;

    default:
      return CHPP_APP_ERROR_NONE;
  }
}

const struct ChppService kService = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.name = "Test",
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .resetNotifierFunctionPtr = nullptr,
    .requestDispatchFunctionPtr = nullptr,
    .notificationDispatchFunctionPtr = &serviceDispatchNotification,
    .minLength = sizeof(struct ChppAppHeader),
};

// Test notifications.
class AppNotificationTest : public testing::Test {
 protected:
  void SetUp() {
    chppClearTotalAllocBytes();
    chppNotifierInit(&mClientState.notifier);
    chppNotifierInit(&mServiceState.notifier);
    memset(&mClientLinkContext, 0, sizeof(mClientLinkContext));
    memset(&mServiceLinkContext, 0, sizeof(mServiceLinkContext));

    mServiceLinkContext.linkThreadName = "Host Link";
    mServiceLinkContext.workThreadName = "Host worker";
    mServiceLinkContext.isLinkActive = true;
    mServiceLinkContext.remoteLinkState = &mClientLinkContext;
    mServiceLinkContext.rxInRemoteEndpointWorker = false;

    mClientLinkContext.linkThreadName = "CHRE Link";
    mClientLinkContext.workThreadName = "CHRE worker";
    mClientLinkContext.isLinkActive = true;
    mClientLinkContext.remoteLinkState = &mServiceLinkContext;
    mClientLinkContext.rxInRemoteEndpointWorker = false;

    // No default clients/services.
    struct ChppClientServiceSet set;
    memset(&set, 0, sizeof(set));

    const struct ChppLinkApi *linkApi = getLinuxLinkApi();

    // Init client side.
    chppTransportInit(&mClientTransportContext, &mClientAppContext,
                      &mClientLinkContext, linkApi);
    chppAppInitWithClientServiceSet(&mClientAppContext,
                                    &mClientTransportContext, set);

    // Init service side.
    chppTransportInit(&mServiceTransportContext, &mServiceAppContext,
                      &mServiceLinkContext, linkApi);
    chppAppInitWithClientServiceSet(&mServiceAppContext,
                                    &mServiceTransportContext, set);

    BringUpClient();
    std::this_thread::sleep_for(std::chrono::milliseconds(450));
    BringUpService();
    mClientLinkContext.linkEstablished = true;
    mServiceLinkContext.linkEstablished = true;

    EXPECT_TRUE(chppTransportWaitForResetComplete(&mClientTransportContext,
                                                  kResetWaitTimeMs));
    EXPECT_TRUE(chppTransportWaitForResetComplete(&mServiceTransportContext,
                                                  kResetWaitTimeMs));
    EXPECT_TRUE(
        chppWaitForDiscoveryComplete(&mClientAppContext, kDiscoveryWaitTimeMs));
    EXPECT_TRUE(chppWaitForDiscoveryComplete(&mServiceAppContext,
                                             kDiscoveryWaitTimeMs));
  }

  void BringUpClient() {
    memset(&mClientState, 0, sizeof(mClientState));
    chppRegisterClient(&mClientAppContext, &mClientState,
                       &mClientState.chppClientState,
                       &mClientState.outReqStates[0], &kClient);

    pthread_create(&mClientWorkThread, NULL, workThread,
                   &mClientTransportContext);
  }

  void BringUpService() {
    memset(&mServiceState, 0, sizeof(mServiceState));
    chppRegisterService(&mServiceAppContext, &mServiceState,
                        &mServiceState.chppServiceState, NULL /*outReqStates*/,
                        &kService);

    pthread_create(&mServiceWorkThread, NULL, workThread,
                   &mServiceTransportContext);
  }

  void TearDown() {
    chppNotifierDeinit(&mClientState.notifier);
    chppNotifierDeinit(&mServiceState.notifier);
    chppWorkThreadStop(&mClientTransportContext);
    chppWorkThreadStop(&mServiceTransportContext);
    pthread_join(mClientWorkThread, NULL);
    pthread_join(mServiceWorkThread, NULL);

    // Deinit client side.
    chppAppDeinit(&mClientAppContext);
    chppTransportDeinit(&mClientTransportContext);

    // Deinit service side.
    chppAppDeinit(&mServiceAppContext);
    chppTransportDeinit(&mServiceTransportContext);

    EXPECT_EQ(chppGetTotalAllocBytes(), 0);
  }

  // Client side.
  ChppLinuxLinkState mClientLinkContext = {};
  ChppTransportState mClientTransportContext = {};
  ChppAppState mClientAppContext = {};
  pthread_t mClientWorkThread;
  ClientState mClientState;

  // Service side
  ChppLinuxLinkState mServiceLinkContext = {};
  ChppTransportState mServiceTransportContext = {};
  ChppAppState mServiceAppContext = {};
  pthread_t mServiceWorkThread;
  ServiceState mServiceState;
};

TEST_F(AppNotificationTest, serviceSendANotificationToClient) {
  // Send a notification.
  constexpr size_t notificationLen = sizeof(struct ChppAppHeader);

  struct ChppAppHeader *notification =
      chppAllocServiceNotification(notificationLen);
  ASSERT_NE(notification, nullptr);
  notification->command = asBaseType(Commands::kServiceNotification);
  notification->handle = mServiceState.chppServiceState.handle;

  mClientState.serviceNotificationStatus = false;

  EXPECT_TRUE(chppEnqueueTxDatagramOrFail(&mServiceTransportContext,
                                          notification, notificationLen));

  chppNotifierWait(&mClientState.notifier);

  EXPECT_TRUE(mClientState.serviceNotificationStatus);
}

TEST_F(AppNotificationTest, clientSendANotificationToService) {
  // Send a notification.
  constexpr size_t notificationLen = sizeof(struct ChppAppHeader);

  struct ChppAppHeader *notification =
      chppAllocClientNotification(notificationLen);
  ASSERT_NE(notification, nullptr);
  notification->command = asBaseType(Commands::kClientNotification);
  notification->handle = mClientState.chppClientState.handle;

  mServiceState.clientNotificationStatus = false;

  EXPECT_TRUE(chppEnqueueTxDatagramOrFail(&mClientTransportContext,
                                          notification, notificationLen));

  chppNotifierWait(&mServiceState.notifier);

  EXPECT_TRUE(mServiceState.clientNotificationStatus);
}

}  // namespace

}  // namespace chre