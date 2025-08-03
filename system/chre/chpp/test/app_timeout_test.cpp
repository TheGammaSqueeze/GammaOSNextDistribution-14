/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "app_timeout_test.h"

#include <gtest/gtest.h>
#include <string.h>
#include <cstdint>
#include <thread>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/clients/gnss.h"
#include "chpp/clients/wifi.h"
#include "chpp/clients/wwan.h"
#include "chpp/macros.h"
#include "chpp/memory.h"
#include "chpp/platform/platform_link.h"
#include "chpp/platform/utils.h"
#include "chpp/services.h"
#include "chpp/time.h"
#include "chpp/transport.h"
#include "chre/pal/wwan.h"

namespace chre {
namespace {

#define TEST_UUID                                                           \
  {                                                                         \
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, \
        0x00, 0x00, 0x00, 0x12                                              \
  }

// Number of requests supported by the client and the service.
constexpr uint16_t kNumCommands = 3;

struct ClientState {
  struct ChppEndpointState chppClientState;
  struct ChppOutgoingRequestState outReqStates[kNumCommands];
};

constexpr struct ChppClient kClient = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .outReqCount = kNumCommands,
    .minLength = sizeof(struct ChppAppHeader),
};

struct ServiceState {
  struct ChppEndpointState chppServiceState;
  struct ChppOutgoingRequestState outReqStates[kNumCommands];
};

const struct ChppService kService = {
    .descriptor.uuid = TEST_UUID,
    .descriptor.name = "Test",
    .descriptor.version.major = 1,
    .descriptor.version.minor = 0,
    .descriptor.version.patch = 0,
    .outReqCount = kNumCommands,
    .minLength = sizeof(struct ChppAppHeader),
};

void ValidateClientStateAndReqState(struct ChppEndpointState *clientState,
                                    const struct ChppAppHeader *request) {
  ASSERT_NE(clientState, nullptr);
  const uint8_t clientIdx = clientState->index;

  ASSERT_NE(clientState->appContext, nullptr);
  ASSERT_NE(clientState->appContext->registeredClients, nullptr);
  ASSERT_NE(clientState->appContext->registeredClients[clientIdx], nullptr);
  ASSERT_LT(request->command,
            clientState->appContext->registeredClients[clientIdx]->outReqCount);
  ASSERT_NE(clientState->appContext->registeredClientStates[clientIdx],
            nullptr);
  ASSERT_NE(
      clientState->appContext->registeredClientStates[clientIdx]->outReqStates,
      nullptr);
  ASSERT_NE(clientState->appContext->registeredClientStates[clientIdx]->context,
            nullptr);
}

void ValidateServiceStateAndReqState(struct ChppEndpointState *serviceState,
                                     const struct ChppAppHeader *request) {
  ASSERT_NE(serviceState, nullptr);
  const uint8_t serviceIdx = serviceState->index;

  ASSERT_NE(serviceState->appContext, nullptr);
  ASSERT_NE(serviceState->appContext->registeredServices, nullptr);
  ASSERT_NE(serviceState->appContext->registeredServices[serviceIdx], nullptr);
  ASSERT_LT(
      request->command,
      serviceState->appContext->registeredServices[serviceIdx]->outReqCount);
  ASSERT_NE(serviceState->appContext->registeredServiceStates[serviceIdx],
            nullptr);
  ASSERT_NE(serviceState->appContext->registeredServiceStates[serviceIdx]
                ->outReqStates,
            nullptr);
  ASSERT_NE(
      serviceState->appContext->registeredServiceStates[serviceIdx]->context,
      nullptr);
}

void validateTimeout(uint64_t timeoutTimeNs, uint64_t expectedTimeNs) {
  constexpr uint64_t kJitterNs = 10 * CHPP_NSEC_PER_MSEC;

  if (expectedTimeNs == CHPP_TIME_MAX) {
    EXPECT_EQ(timeoutTimeNs, expectedTimeNs);
  } else {
    EXPECT_GE(timeoutTimeNs, expectedTimeNs);
    EXPECT_LE(timeoutTimeNs, expectedTimeNs + kJitterNs);
  }
}

void validateTimeoutResponse(const struct ChppAppHeader *request,
                             const struct ChppAppHeader *response) {
  ASSERT_NE(request, nullptr);
  ASSERT_NE(response, nullptr);

  EXPECT_EQ(response->handle, request->handle);

  EXPECT_EQ(response->type, request->type == CHPP_MESSAGE_TYPE_CLIENT_REQUEST
                                ? CHPP_MESSAGE_TYPE_SERVICE_RESPONSE
                                : CHPP_MESSAGE_TYPE_CLIENT_RESPONSE);
  EXPECT_EQ(response->transaction, request->transaction);
  EXPECT_EQ(response->error, CHPP_APP_ERROR_TIMEOUT);
  EXPECT_EQ(response->command, request->command);
}

/**
 * Test timeout for client and service side requests.
 *
 * The test parameter is:
 * - CHPP_MESSAGE_TYPE_CLIENT_REQUEST for client side requests
 * - CHPP_MESSAGE_TYPE_SERVICE_REQUEST for service side requests
 */
class TimeoutParamTest : public testing::TestWithParam<ChppMessageType> {
 protected:
  void SetUp() override {
    chppClearTotalAllocBytes();

    memset(&mClientLinkContext, 0, sizeof(mClientLinkContext));
    memset(&mServiceLinkContext, 0, sizeof(mServiceLinkContext));

    mServiceLinkContext.isLinkActive = true;
    mServiceLinkContext.remoteLinkState = &mClientLinkContext;
    mServiceLinkContext.rxInRemoteEndpointWorker = false;

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
    mClientTransportContext.resetState = CHPP_RESET_STATE_NONE;
    chppAppInitWithClientServiceSet(&mClientAppContext,
                                    &mClientTransportContext, set);

    // Init service side.
    chppTransportInit(&mServiceTransportContext, &mServiceAppContext,
                      &mServiceLinkContext, linkApi);
    mServiceTransportContext.resetState = CHPP_RESET_STATE_NONE;
    chppAppInitWithClientServiceSet(&mServiceAppContext,
                                    &mServiceTransportContext, set);

    // Bring up the client
    memset(&mClientState, 0, sizeof(mClientState));
    chppRegisterClient(&mClientAppContext, &mClientState,
                       &mClientState.chppClientState,
                       &mClientState.outReqStates[0], &kClient);

    // Bring up the service
    memset(&mServiceState, 0, sizeof(mServiceState));
    chppRegisterService(&mServiceAppContext, &mServiceState,
                        &mServiceState.chppServiceState,
                        &mServiceState.outReqStates[0], &kService);

    mClientLinkContext.linkEstablished = true;
    mServiceLinkContext.linkEstablished = true;

    chppClientInit(&mClientState.chppClientState,
                   CHPP_HANDLE_NEGOTIATED_RANGE_START);
  }

  void TearDown() override {
    chppAppDeinit(&mClientAppContext);
    chppTransportDeinit(&mClientTransportContext);
    chppClientDeinit(&mClientState.chppClientState);

    chppAppDeinit(&mServiceAppContext);
    chppTransportDeinit(&mServiceTransportContext);

    EXPECT_EQ(chppGetTotalAllocBytes(), 0);
  }

  struct ChppAppHeader *AllocRequestCommand(uint16_t command) {
    return GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST
               ? chppAllocClientRequestCommand(&mClientState.chppClientState,
                                               command)
               : chppAllocServiceRequestCommand(&mServiceState.chppServiceState,
                                                command);
  }

  void TimestampOutgoingRequest(struct ChppAppHeader *request,
                                uint64_t timeoutNs) {
    CHPP_NOT_NULL(request);

    const uint16_t command = request->command;

    if (GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST) {
      chppTimestampOutgoingRequest(&mClientAppContext,
                                   &mClientState.outReqStates[command], request,
                                   timeoutNs);
    } else {
      chppTimestampOutgoingRequest(&mServiceAppContext,
                                   &mServiceState.outReqStates[command],
                                   request, timeoutNs);
    }
  }

  bool TimestampIncomingResponse(struct ChppAppHeader *response) {
    CHPP_NOT_NULL(response);

    const uint16_t command = response->command;

    if (GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST) {
      return chppTimestampIncomingResponse(
          &mClientAppContext, &mClientState.outReqStates[command], response);
    }
    return chppTimestampIncomingResponse(
        &mServiceAppContext, &mServiceState.outReqStates[command], response);
  }

  uint64_t GetNextRequestTimeoutNs(void) {
    return GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST
               ? mClientAppContext.nextClientRequestTimeoutNs
               : mServiceAppContext.nextServiceRequestTimeoutNs;
  }

  struct ChppAppHeader *GetTimeoutResponse(void) {
    return GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST
               ? chppTransportGetRequestTimeoutResponse(
                     &mClientTransportContext, CHPP_ENDPOINT_CLIENT)
               : chppTransportGetRequestTimeoutResponse(
                     &mServiceTransportContext, CHPP_ENDPOINT_SERVICE);
  }

  void ValidateRequestState(struct ChppAppHeader *request) {
    CHPP_NOT_NULL(request);
    if (GetParam() == CHPP_MESSAGE_TYPE_CLIENT_REQUEST) {
      ValidateClientStateAndReqState(&mClientState.chppClientState, request);
    } else {
      ValidateServiceStateAndReqState(&mServiceState.chppServiceState, request);
    }
  }

  void RegisterAndValidateRequestForTimeout(struct ChppAppHeader *request,
                                            uint64_t kTimeoutNs,
                                            uint64_t expectedTimeNs) {
    CHPP_NOT_NULL(request);
    ValidateRequestState(request);
    TimestampOutgoingRequest(request, kTimeoutNs);

    validateTimeout(GetNextRequestTimeoutNs(), expectedTimeNs);
  }

  void RegisterAndValidateResponseForTimeout(struct ChppAppHeader *request,
                                             uint64_t expectedTimeNs) {
    CHPP_NOT_NULL(request);

    struct ChppAppHeader *response =
        chppAllocResponse(request, sizeof(*request));

    ValidateRequestState(request);
    TimestampIncomingResponse(response);

    validateTimeout(GetNextRequestTimeoutNs(), expectedTimeNs);

    chppFree(response);
  }

  // Client side.
  ChppLinuxLinkState mClientLinkContext = {};
  ChppTransportState mClientTransportContext = {};
  ChppAppState mClientAppContext = {};
  ClientState mClientState;

  // Service side
  ChppLinuxLinkState mServiceLinkContext = {};
  ChppTransportState mServiceTransportContext = {};
  ChppAppState mServiceAppContext = {};
  ServiceState mServiceState;
};

// Simulates a request and a response.
// There should be no error as the timeout is infinite.
TEST_P(TimeoutParamTest, RequestResponseTimestampValid) {
  struct ChppAppHeader *request = AllocRequestCommand(0 /* command */);
  ASSERT_NE(request, nullptr);
  TimestampOutgoingRequest(request, CHPP_REQUEST_TIMEOUT_INFINITE);

  struct ChppAppHeader *response = chppAllocResponse(request, sizeof(*request));
  EXPECT_TRUE(TimestampIncomingResponse(response));

  chppFree(request);
  chppFree(response);
}

// Simulates a single request with 2 responses.
TEST_P(TimeoutParamTest, RequestResponseTimestampDuplicate) {
  struct ChppAppHeader *request = AllocRequestCommand(0 /* command */);
  ASSERT_NE(request, nullptr);
  TimestampOutgoingRequest(request, CHPP_REQUEST_TIMEOUT_INFINITE);

  struct ChppAppHeader *response = chppAllocResponse(request, sizeof(*request));

  // The first response has no error.
  EXPECT_TRUE(TimestampIncomingResponse(response));

  // The second response errors as one response has already been received.
  EXPECT_FALSE(TimestampIncomingResponse(response));

  chppFree(request);
  chppFree(response);
}

// Simulates a response to a request that has not been timestamped.
TEST_P(TimeoutParamTest, RequestResponseTimestampInvalidId) {
  constexpr uint16_t command = 0;

  struct ChppAppHeader *request1 = AllocRequestCommand(command);
  ASSERT_NE(request1, nullptr);
  TimestampOutgoingRequest(request1, CHPP_REQUEST_TIMEOUT_INFINITE);

  struct ChppAppHeader *request2 = AllocRequestCommand(command);
  ASSERT_NE(request2, nullptr);

  // We expect a response for req but get a response for newReq.
  // That is an error (the transaction does not match).
  struct ChppAppHeader *response =
      chppAllocResponse(request2, sizeof(*request1));
  EXPECT_FALSE(TimestampIncomingResponse(response));

  chppFree(request1);
  chppFree(request2);
  chppFree(response);
}

// Make sure the request does not timeout right away.
TEST_P(TimeoutParamTest, RequestTimeoutAddRemoveSingle) {
  EXPECT_EQ(GetNextRequestTimeoutNs(), CHPP_TIME_MAX);

  struct ChppAppHeader *request = AllocRequestCommand(1 /* command */);
  ASSERT_NE(request, nullptr);

  const uint64_t timeNs = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeoutNs = 1000 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request, kTimeoutNs,
                                       timeNs + kTimeoutNs);

  // Timeout is not expired yet.
  EXPECT_EQ(GetTimeoutResponse(), nullptr);

  RegisterAndValidateResponseForTimeout(request, CHPP_TIME_MAX);

  chppFree(request);
}

TEST_P(TimeoutParamTest, RequestTimeoutAddRemoveMultiple) {
  EXPECT_EQ(GetNextRequestTimeoutNs(), CHPP_TIME_MAX);

  struct ChppAppHeader *request1 = AllocRequestCommand(0 /* command */);
  struct ChppAppHeader *request2 = AllocRequestCommand(1 /* command */);
  struct ChppAppHeader *request3 = AllocRequestCommand(2 /* command */);
  ASSERT_NE(request1, nullptr);
  ASSERT_NE(request2, nullptr);
  ASSERT_NE(request3, nullptr);

  // kTimeout1Ns is the smallest so it will be the first timeout to expire
  // for all the requests.
  const uint64_t time1Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout1Ns = 2000 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request1, kTimeout1Ns,
                                       time1Ns + kTimeout1Ns);

  const uint64_t time2Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout2Ns = 4000 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request2, kTimeout2Ns,
                                       time1Ns + kTimeout1Ns);

  const uint64_t time3Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout3Ns = 3000 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request3, kTimeout3Ns,
                                       time1Ns + kTimeout1Ns);

  RegisterAndValidateResponseForTimeout(request1, time3Ns + kTimeout3Ns);

  // Timeout is not expired yet.
  EXPECT_EQ(GetTimeoutResponse(), nullptr);

  // kTimeout4Ns is now the smallest timeout.
  const uint64_t time4Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout4Ns = 1000 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request1, kTimeout4Ns,
                                       time4Ns + kTimeout4Ns);

  RegisterAndValidateResponseForTimeout(request1, time3Ns + kTimeout3Ns);

  RegisterAndValidateResponseForTimeout(request3, time2Ns + kTimeout2Ns);

  RegisterAndValidateResponseForTimeout(request2, CHPP_TIME_MAX);

  EXPECT_EQ(GetTimeoutResponse(), nullptr);

  chppFree(request1);
  chppFree(request2);
  chppFree(request3);
}

TEST_P(TimeoutParamTest, DuplicateRequestTimeoutResponse) {
  // Sleep padding to make sure we timeout.
  constexpr auto kTimeoutPadding = std::chrono::milliseconds(50);

  EXPECT_EQ(GetNextRequestTimeoutNs(), CHPP_TIME_MAX);

  struct ChppAppHeader *request = AllocRequestCommand(1 /* command */);
  ASSERT_NE(request, nullptr);

  // Send the first request.
  constexpr uint64_t kTimeout1Ns = 20 * CHPP_NSEC_PER_MSEC;
  const uint64_t kShouldTimeout1AtNs = chppGetCurrentTimeNs() + kTimeout1Ns;
  RegisterAndValidateRequestForTimeout(request, kTimeout1Ns,
                                       kShouldTimeout1AtNs);

  // Override with a new request.
  constexpr uint64_t kTimeout2Ns = 400 * CHPP_NSEC_PER_MSEC;
  const uint64_t kShouldTimeout2AtNs = chppGetCurrentTimeNs() + kTimeout2Ns;
  RegisterAndValidateRequestForTimeout(request, kTimeout2Ns,
                                       kShouldTimeout2AtNs);

  std::this_thread::sleep_for(
      std::chrono::nanoseconds(kShouldTimeout1AtNs - chppGetCurrentTimeNs()) +
      kTimeoutPadding);
  // First request would have timed out but superseded by second request.
  EXPECT_GT(GetNextRequestTimeoutNs(), chppGetCurrentTimeNs());

  std::this_thread::sleep_for(
      std::chrono::nanoseconds(kShouldTimeout2AtNs - chppGetCurrentTimeNs()) +
      kTimeoutPadding);
  // Second request should have timed out - so we get a response.
  EXPECT_LT(GetNextRequestTimeoutNs(), chppGetCurrentTimeNs());

  struct ChppAppHeader *response = GetTimeoutResponse();
  ASSERT_NE(response, nullptr);
  validateTimeoutResponse(request, response);
  chppFree(response);

  RegisterAndValidateResponseForTimeout(request, CHPP_TIME_MAX);
  EXPECT_EQ(GetTimeoutResponse(), nullptr);

  chppFree(request);
}

TEST_P(TimeoutParamTest, RequestTimeoutResponse) {
  EXPECT_EQ(GetNextRequestTimeoutNs(), CHPP_TIME_MAX);

  struct ChppAppHeader *request1 = AllocRequestCommand(1 /* command */);
  struct ChppAppHeader *request2 = AllocRequestCommand(2 /* command */);
  ASSERT_NE(request1, nullptr);
  ASSERT_NE(request2, nullptr);

  const uint64_t time1Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout1Ns = 200 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request1, kTimeout1Ns,
                                       time1Ns + kTimeout1Ns);

  std::this_thread::sleep_for(std::chrono::nanoseconds(kTimeout1Ns));
  ASSERT_LT(GetNextRequestTimeoutNs(), chppGetCurrentTimeNs());

  // No response in time, we then get a timeout response.
  struct ChppAppHeader *response = GetTimeoutResponse();
  validateTimeoutResponse(request1, response);
  chppFree(response);

  RegisterAndValidateResponseForTimeout(request1, CHPP_TIME_MAX);
  // No other request in timeout.
  EXPECT_EQ(GetTimeoutResponse(), nullptr);

  // Simulate a new timeout and make sure we have a timeout response.
  const uint64_t time2Ns = chppGetCurrentTimeNs();
  constexpr uint64_t kTimeout2Ns = 200 * CHPP_NSEC_PER_MSEC;
  RegisterAndValidateRequestForTimeout(request2, kTimeout2Ns,
                                       time2Ns + kTimeout2Ns);

  std::this_thread::sleep_for(std::chrono::nanoseconds(kTimeout2Ns));
  ASSERT_LT(GetNextRequestTimeoutNs(), chppGetCurrentTimeNs());

  response = GetTimeoutResponse();
  validateTimeoutResponse(request2, response);
  chppFree(response);

  chppFree(request1);
  chppFree(request2);
}

INSTANTIATE_TEST_SUITE_P(
    TimeoutTest, TimeoutParamTest,
    testing::Values(CHPP_MESSAGE_TYPE_CLIENT_REQUEST,
                    CHPP_MESSAGE_TYPE_SERVICE_REQUEST),
    [](const testing::TestParamInfo<TimeoutParamTest::ParamType> &info) {
      return info.param == CHPP_MESSAGE_TYPE_CLIENT_REQUEST ? "ClientRequests"
                                                            : "ServiceRequests";
    });

}  // namespace
}  // namespace chre