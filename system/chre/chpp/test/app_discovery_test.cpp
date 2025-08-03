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
#include "chpp/platform/platform_link.h"
#include "chpp/platform/utils.h"
#include "chpp/services.h"
#include "chpp/transport.h"

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

constexpr uint16_t kNumCommands = 1;

struct ClientState {
  struct ChppEndpointState chppClientState;
  struct ChppOutgoingRequestState outReqStates[kNumCommands];
  bool resetNotified;
  bool matchNotified;
};

void clientNotifyReset(void *clientState);
void clientNotifyMatch(void *clientState);
bool clientInit(void *clientState, uint8_t handle,
                struct ChppVersion serviceVersion);
void clientDeinit(void *clientState);

constexpr struct ChppClient kClient = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .resetNotifierFunctionPtr = &clientNotifyReset,
    .matchNotifierFunctionPtr = &clientNotifyMatch,
    .responseDispatchFunctionPtr = nullptr,
    .notificationDispatchFunctionPtr = nullptr,
    .initFunctionPtr = &clientInit,
    .deinitFunctionPtr = &clientDeinit,
    .outReqCount = kNumCommands,
    .minLength = sizeof(struct ChppAppHeader),
};

void clientNotifyReset(void *clientState) {
  auto state = static_cast<struct ClientState *>(clientState);
  state->resetNotified = true;
}

void clientNotifyMatch(void *clientState) {
  auto state = static_cast<struct ClientState *>(clientState);
  state->matchNotified = true;
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
  bool resetNotified;
};

void serviceNotifyReset(void *serviceState) {
  auto state = static_cast<struct ServiceState *>(serviceState);
  state->resetNotified = true;
}

const struct ChppService kService = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.name = "Test",
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .resetNotifierFunctionPtr = &serviceNotifyReset,
    .requestDispatchFunctionPtr = nullptr,
    .notificationDispatchFunctionPtr = nullptr,
    .minLength = sizeof(struct ChppAppHeader),
};

// Test Clients/Services discovery and matching.
class AppDiscoveryTest : public testing::Test {
 protected:
  void SetUp() {
    chppClearTotalAllocBytes();
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
  }

  void TearDown() {
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

TEST_F(AppDiscoveryTest, workWhenThereIsNoService) {
  // Register the client
  memset(&mClientState, 0, sizeof(mClientState));
  chppRegisterClient(&mClientAppContext, &mClientState,
                     &mClientState.chppClientState,
                     &mClientState.outReqStates[0], &kClient);

  pthread_create(&mClientWorkThread, NULL, workThread,
                 &mClientTransportContext);

  std::this_thread::sleep_for(std::chrono::milliseconds(450));

  // Start the service thread (no service registered).
  pthread_create(&mServiceWorkThread, NULL, workThread,
                 &mServiceTransportContext);

  mClientLinkContext.linkEstablished = true;
  mServiceLinkContext.linkEstablished = true;

  EXPECT_TRUE(chppTransportWaitForResetComplete(&mClientTransportContext,
                                                kResetWaitTimeMs));
  EXPECT_TRUE(chppTransportWaitForResetComplete(&mServiceTransportContext,
                                                kResetWaitTimeMs));

  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mClientAppContext, kDiscoveryWaitTimeMs));
  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mServiceAppContext, kDiscoveryWaitTimeMs));

  EXPECT_FALSE(mClientState.resetNotified);
  EXPECT_FALSE(mClientState.matchNotified);
  EXPECT_EQ(mClientAppContext.discoveredServiceCount, 0);
  EXPECT_EQ(mClientAppContext.matchedClientCount, 0);
  EXPECT_EQ(mServiceAppContext.discoveredServiceCount, 0);
  EXPECT_EQ(mServiceAppContext.matchedClientCount, 0);
}

TEST_F(AppDiscoveryTest, servicesShouldBeDiscovered) {
  // Start the client thread (no client registered).
  pthread_create(&mClientWorkThread, NULL, workThread,
                 &mClientTransportContext);

  std::this_thread::sleep_for(std::chrono::milliseconds(450));

  // Register the service
  memset(&mServiceState, 0, sizeof(mServiceState));
  chppRegisterService(&mServiceAppContext, &mServiceState,
                      &mServiceState.chppServiceState, NULL /*outReqStates*/,
                      &kService);

  pthread_create(&mServiceWorkThread, NULL, workThread,
                 &mServiceTransportContext);

  mClientLinkContext.linkEstablished = true;
  mServiceLinkContext.linkEstablished = true;

  EXPECT_TRUE(chppTransportWaitForResetComplete(&mClientTransportContext,
                                                kResetWaitTimeMs));
  EXPECT_TRUE(chppTransportWaitForResetComplete(&mServiceTransportContext,
                                                kResetWaitTimeMs));

  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mClientAppContext, kDiscoveryWaitTimeMs));
  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mServiceAppContext, kDiscoveryWaitTimeMs));

  EXPECT_FALSE(mClientState.resetNotified);
  EXPECT_TRUE(mServiceState.resetNotified);
  EXPECT_FALSE(mClientState.matchNotified);
  EXPECT_EQ(mClientAppContext.discoveredServiceCount, 1);
  EXPECT_EQ(mClientAppContext.matchedClientCount, 0);
  EXPECT_EQ(mServiceAppContext.discoveredServiceCount, 0);
  EXPECT_EQ(mServiceAppContext.matchedClientCount, 0);
}

TEST_F(AppDiscoveryTest, discoveredServiceShouldBeMatchedWithClients) {
  // Register the client
  memset(&mClientState, 0, sizeof(mClientState));
  chppRegisterClient(&mClientAppContext, &mClientState,
                     &mClientState.chppClientState,
                     &mClientState.outReqStates[0], &kClient);

  pthread_create(&mClientWorkThread, NULL, workThread,
                 &mClientTransportContext);

  std::this_thread::sleep_for(std::chrono::milliseconds(450));

  // Register the service
  memset(&mServiceState, 0, sizeof(mServiceState));
  chppRegisterService(&mServiceAppContext, &mServiceState,
                      &mServiceState.chppServiceState, NULL /*outReqStates*/,
                      &kService);

  pthread_create(&mServiceWorkThread, NULL, workThread,
                 &mServiceTransportContext);

  mClientLinkContext.linkEstablished = true;
  mServiceLinkContext.linkEstablished = true;

  EXPECT_TRUE(chppTransportWaitForResetComplete(&mClientTransportContext,
                                                kResetWaitTimeMs));
  EXPECT_TRUE(chppTransportWaitForResetComplete(&mServiceTransportContext,
                                                kResetWaitTimeMs));

  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mClientAppContext, kDiscoveryWaitTimeMs));
  EXPECT_TRUE(
      chppWaitForDiscoveryComplete(&mServiceAppContext, kDiscoveryWaitTimeMs));

  EXPECT_FALSE(mClientState.resetNotified);
  EXPECT_TRUE(mServiceState.resetNotified);
  EXPECT_TRUE(mClientState.matchNotified);
  EXPECT_EQ(mClientAppContext.discoveredServiceCount, 1);
  EXPECT_EQ(mClientAppContext.matchedClientCount, 1);
  EXPECT_TRUE(mClientState.chppClientState.initialized);
  EXPECT_EQ(mServiceAppContext.discoveredServiceCount, 0);
  EXPECT_EQ(mServiceAppContext.matchedClientCount, 0);
}

}  // namespace

}  // namespace chre