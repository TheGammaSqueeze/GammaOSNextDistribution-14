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

#include "chpp/clients/timesync.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/app.h"
#include "chpp/clients.h"
#include "chpp/common/timesync.h"
#include "chpp/log.h"
#include "chpp/memory.h"
#include "chpp/time.h"
#include "chpp/transport.h"

#include "chpp/clients/discovery.h"

/************************************************
 *  Private Definitions
 ***********************************************/

/**
 * Structure to maintain state for the Timesync client and its Request/Response
 * (RR) functionality.
 */
struct ChppTimesyncClientState {
  struct ChppEndpointState client;                // CHPP client state
  struct ChppOutgoingRequestState measureOffset;  // Request response state

  struct ChppTimesyncResult timesyncResult;  // Result of measureOffset
};

/************************************************
 *  Public Functions
 ***********************************************/

void chppTimesyncClientInit(struct ChppAppState *appState) {
  CHPP_LOGD("Timesync client init");
  CHPP_DEBUG_NOT_NULL(appState);

  appState->timesyncClientContext =
      chppMalloc(sizeof(struct ChppTimesyncClientState));
  CHPP_NOT_NULL(appState->timesyncClientContext);
  struct ChppTimesyncClientState *state = appState->timesyncClientContext;

  memset(state, 0, sizeof(struct ChppTimesyncClientState));
  state->client.appContext = appState;
  state->timesyncResult.error = CHPP_APP_ERROR_NONE;

  chppClientInit(&state->client, CHPP_HANDLE_TIMESYNC);
  state->timesyncResult.error = CHPP_APP_ERROR_UNSPECIFIED;
  state->client.openState = CHPP_OPEN_STATE_OPENED;
}

void chppTimesyncClientDeinit(struct ChppAppState *appState) {
  CHPP_LOGD("Timesync client deinit");
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_NOT_NULL(appState->timesyncClientContext);
  chppClientDeinit(&appState->timesyncClientContext->client);
  CHPP_FREE_AND_NULLIFY(appState->timesyncClientContext);
}

void chppTimesyncClientReset(struct ChppAppState *appState) {
  CHPP_LOGD("Timesync client reset");
  CHPP_DEBUG_NOT_NULL(appState);
  struct ChppTimesyncClientState *state = appState->timesyncClientContext;
  CHPP_NOT_NULL(state);

  state->timesyncResult.error = CHPP_APP_ERROR_NONE;
  state->timesyncResult.offsetNs = 0;
  state->timesyncResult.rttNs = 0;
  state->timesyncResult.measurementTimeNs = 0;
}

bool chppDispatchTimesyncServiceResponse(struct ChppAppState *appState,
                                         const uint8_t *buf, size_t len) {
  CHPP_LOGD("Timesync client dispatch service response");
  CHPP_DEBUG_NOT_NULL(appState);
  struct ChppTimesyncClientState *state = appState->timesyncClientContext;
  CHPP_NOT_NULL(state);
  CHPP_NOT_NULL(buf);

  if (len < sizeof(struct ChppTimesyncResponse)) {
    CHPP_LOGE("Timesync resp short len=%" PRIuSIZE, len);
    state->timesyncResult.error = CHPP_APP_ERROR_INVALID_LENGTH;
    return false;
  }

  const struct ChppTimesyncResponse *response =
      (const struct ChppTimesyncResponse *)buf;
  if (chppTimestampIncomingResponse(state->client.appContext,
                                    &state->measureOffset, &response->header)) {
    state->timesyncResult.rttNs = state->measureOffset.responseTimeNs -
                                  state->measureOffset.requestTimeNs;
    int64_t offsetNs =
        (int64_t)(response->timeNs - state->measureOffset.responseTimeNs);
    int64_t offsetChangeNs = offsetNs - state->timesyncResult.offsetNs;

    int64_t clippedOffsetChangeNs = offsetChangeNs;
    if (state->timesyncResult.offsetNs != 0) {
      clippedOffsetChangeNs = MIN(clippedOffsetChangeNs,
                                  (int64_t)CHPP_CLIENT_TIMESYNC_MAX_CHANGE_NS);
      clippedOffsetChangeNs = MAX(clippedOffsetChangeNs,
                                  -(int64_t)CHPP_CLIENT_TIMESYNC_MAX_CHANGE_NS);
    }

    state->timesyncResult.offsetNs += clippedOffsetChangeNs;

    if (offsetChangeNs != clippedOffsetChangeNs) {
      CHPP_LOGW("Drift=%" PRId64 " clipped to %" PRId64 " at t=%" PRIu64,
                offsetChangeNs / (int64_t)CHPP_NSEC_PER_MSEC,
                clippedOffsetChangeNs / (int64_t)CHPP_NSEC_PER_MSEC,
                state->measureOffset.responseTimeNs / CHPP_NSEC_PER_MSEC);
    } else {
      state->timesyncResult.measurementTimeNs =
          state->measureOffset.responseTimeNs;
    }

    state->timesyncResult.error = CHPP_APP_ERROR_NONE;

    CHPP_LOGD("Timesync RTT=%" PRIu64 " correction=%" PRId64 " offset=%" PRId64
              " t=%" PRIu64,
              state->timesyncResult.rttNs / CHPP_NSEC_PER_MSEC,
              clippedOffsetChangeNs / (int64_t)CHPP_NSEC_PER_MSEC,
              offsetNs / (int64_t)CHPP_NSEC_PER_MSEC,
              state->timesyncResult.measurementTimeNs / CHPP_NSEC_PER_MSEC);
  }

  return true;
}

bool chppTimesyncMeasureOffset(struct ChppAppState *appState) {
  bool result = false;
  CHPP_LOGD("Measuring timesync t=%" PRIu64,
            chppGetCurrentTimeNs() / CHPP_NSEC_PER_MSEC);
  CHPP_DEBUG_NOT_NULL(appState);
  struct ChppTimesyncClientState *state = appState->timesyncClientContext;
  CHPP_NOT_NULL(state);

  state->timesyncResult.error =
      CHPP_APP_ERROR_BUSY;  // A measurement is in progress

  struct ChppAppHeader *request = chppAllocClientRequestCommand(
      &state->client, CHPP_TIMESYNC_COMMAND_GETTIME);
  size_t requestLen = sizeof(*request);

  if (request == NULL) {
    state->timesyncResult.error = CHPP_APP_ERROR_OOM;
    CHPP_LOG_OOM();

  } else if (!chppClientSendTimestampedRequestOrFail(
                 &state->client, &state->measureOffset, request, requestLen,
                 CHPP_REQUEST_TIMEOUT_INFINITE)) {
    state->timesyncResult.error = CHPP_APP_ERROR_UNSPECIFIED;

  } else {
    result = true;
  }

  return result;
}

int64_t chppTimesyncGetOffset(struct ChppAppState *appState,
                              uint64_t maxTimesyncAgeNs) {
  CHPP_DEBUG_NOT_NULL(appState);
  struct ChppTimesyncClientState *state = appState->timesyncClientContext;
  CHPP_DEBUG_NOT_NULL(state);

  bool timesyncNeverDone = state->timesyncResult.offsetNs == 0;
  bool timesyncIsStale =
      chppGetCurrentTimeNs() - state->timesyncResult.measurementTimeNs >
      maxTimesyncAgeNs;

  if (timesyncNeverDone || timesyncIsStale) {
    chppTimesyncMeasureOffset(appState);
  } else {
    CHPP_LOGD("No need to timesync at t~=%" PRIu64 "offset=%" PRId64,
              chppGetCurrentTimeNs() / CHPP_NSEC_PER_MSEC,
              state->timesyncResult.offsetNs / (int64_t)CHPP_NSEC_PER_MSEC);
  }

  return state->timesyncResult.offsetNs;
}

const struct ChppTimesyncResult *chppTimesyncGetResult(
    struct ChppAppState *appState) {
  CHPP_DEBUG_NOT_NULL(appState);
  CHPP_DEBUG_NOT_NULL(appState->timesyncClientContext);
  return &appState->timesyncClientContext->timesyncResult;
}
