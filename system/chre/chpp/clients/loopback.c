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

#include "chpp/clients/loopback.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/log.h"
#include "chpp/memory.h"
#include "chpp/transport.h"

#include "chpp/clients/discovery.h"

/************************************************
 *  Prototypes
 ***********************************************/

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Structure to maintain state for the loopback client and its Request/Response
 * (RR) functionality.
 */
struct ChppLoopbackClientState {
  struct ChppEndpointState client;                  // CHPP client state
  struct ChppOutgoingRequestState runLoopbackTest;  // Loopback test state

  struct ChppLoopbackTestResult testResult;  // Last test result
  const uint8_t *loopbackData;               // Pointer to loopback data
};

/************************************************
 *  Public Functions
 ***********************************************/

void chppLoopbackClientInit(struct ChppAppState *appState) {
  CHPP_LOGD("Loopback client init");
  CHPP_DEBUG_NOT_NULL(appState);

  appState->loopbackClientContext =
      chppMalloc(sizeof(struct ChppLoopbackClientState));
  CHPP_NOT_NULL(appState->loopbackClientContext);
  struct ChppLoopbackClientState *state = appState->loopbackClientContext;
  memset(state, 0, sizeof(struct ChppLoopbackClientState));

  state->client.appContext = appState;
  chppClientInit(&state->client, CHPP_HANDLE_LOOPBACK);
  state->testResult.error = CHPP_APP_ERROR_NONE;
  state->client.openState = CHPP_OPEN_STATE_OPENED;
}

void chppLoopbackClientDeinit(struct ChppAppState *appState) {
  CHPP_LOGD("Loopback client deinit");
  CHPP_NOT_NULL(appState);
  CHPP_NOT_NULL(appState->loopbackClientContext);

  chppClientDeinit(&appState->loopbackClientContext->client);
  CHPP_FREE_AND_NULLIFY(appState->loopbackClientContext);
}

bool chppDispatchLoopbackServiceResponse(struct ChppAppState *appState,
                                         const uint8_t *response, size_t len) {
  CHPP_LOGD("Loopback client dispatch service response");
  CHPP_ASSERT(len >= CHPP_LOOPBACK_HEADER_LEN);
  CHPP_NOT_NULL(response);
  CHPP_DEBUG_NOT_NULL(appState);
  struct ChppLoopbackClientState *state = appState->loopbackClientContext;
  CHPP_NOT_NULL(state);
  CHPP_NOT_NULL(state->loopbackData);

  CHPP_ASSERT(chppTimestampIncomingResponse(
      state->client.appContext, &state->runLoopbackTest,
      (const struct ChppAppHeader *)response));

  struct ChppLoopbackTestResult *result = &state->testResult;

  result->error = CHPP_APP_ERROR_NONE;
  result->responseLen = len;
  result->firstError = len;
  result->byteErrors = 0;
  result->rttNs = state->runLoopbackTest.responseTimeNs -
                  state->runLoopbackTest.requestTimeNs;

  if (result->requestLen != result->responseLen) {
    result->error = CHPP_APP_ERROR_INVALID_LENGTH;
    result->firstError = MIN(result->requestLen, result->responseLen);
  }

  for (size_t loc = CHPP_LOOPBACK_HEADER_LEN;
       loc < MIN(result->requestLen, result->responseLen); loc++) {
    if (state->loopbackData[loc - CHPP_LOOPBACK_HEADER_LEN] != response[loc]) {
      result->error = CHPP_APP_ERROR_UNSPECIFIED;
      result->firstError =
          MIN(result->firstError, loc - CHPP_LOOPBACK_HEADER_LEN);
      result->byteErrors++;
    }
  }

  CHPP_LOGD("Loopback client RX err=0x%" PRIx16 " len=%" PRIuSIZE
            " req len=%" PRIuSIZE " first err=%" PRIuSIZE
            " total err=%" PRIuSIZE,
            result->error, result->responseLen, result->requestLen,
            result->firstError, result->byteErrors);

  // Notify waiting (synchronous) client
  chppMutexLock(&state->client.syncResponse.mutex);
  state->client.syncResponse.ready = true;
  chppConditionVariableSignal(&state->client.syncResponse.condVar);
  chppMutexUnlock(&state->client.syncResponse.mutex);

  return true;
}

struct ChppLoopbackTestResult chppRunLoopbackTest(struct ChppAppState *appState,
                                                  const uint8_t *buf,
                                                  size_t len) {
  CHPP_LOGD("Loopback client TX len=%" PRIuSIZE,
            len + CHPP_LOOPBACK_HEADER_LEN);

  if (appState == NULL) {
    CHPP_LOGE("Cannot run loopback test with null app");
    struct ChppLoopbackTestResult result;
    result.error = CHPP_APP_ERROR_UNSUPPORTED;
    return result;
  }

  if (!chppWaitForDiscoveryComplete(appState, 0 /* timeoutMs */)) {
    struct ChppLoopbackTestResult result;
    result.error = CHPP_APP_ERROR_NOT_READY;
    return result;
  }

  struct ChppLoopbackClientState *state = appState->loopbackClientContext;
  CHPP_NOT_NULL(state);
  struct ChppLoopbackTestResult *result = &state->testResult;

  if (result->error == CHPP_APP_ERROR_BLOCKED) {
    CHPP_DEBUG_ASSERT_LOG(false, "Another loopback in progress");
    return *result;
  }

  result->error = CHPP_APP_ERROR_BLOCKED;
  result->requestLen = len + CHPP_LOOPBACK_HEADER_LEN;
  result->responseLen = 0;
  result->firstError = 0;
  result->byteErrors = 0;
  result->rttNs = 0;
  state->runLoopbackTest.requestTimeNs = CHPP_TIME_NONE;
  state->runLoopbackTest.responseTimeNs = CHPP_TIME_NONE;

  if (len == 0) {
    CHPP_LOGE("Loopback payload=0!");
    result->error = CHPP_APP_ERROR_INVALID_LENGTH;
    return *result;
  }

  uint8_t *loopbackRequest =
      (uint8_t *)chppAllocClientRequest(&state->client, result->requestLen);

  if (loopbackRequest == NULL) {
    result->requestLen = 0;
    result->error = CHPP_APP_ERROR_OOM;
    CHPP_LOG_OOM();
    return *result;
  }

  state->loopbackData = buf;
  memcpy(&loopbackRequest[CHPP_LOOPBACK_HEADER_LEN], buf, len);

  if (!chppClientSendTimestampedRequestAndWaitTimeout(
          &state->client, &state->runLoopbackTest, loopbackRequest,
          result->requestLen, 5 * CHPP_NSEC_PER_SEC)) {
    result->error = CHPP_APP_ERROR_UNSPECIFIED;
  }

  return *result;
}
