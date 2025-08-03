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

#include "chpp/services/timesync.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/common/timesync.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/services.h"
#include "chpp/time.h"
#include "chpp/transport.h"

/**
 * Processes the GetTime (0x0001) request and responds with the time as on the
 * service. This should be the same clock used to timestamp any data samples
 * provided to CHPP.
 *
 * @param context Maintains status for each app layer instance.
 * @param requestHeader Header of the request.
 */
static void chppTimesyncGetTime(struct ChppAppState *context,
                                const struct ChppAppHeader *requestHeader) {
  struct ChppTimesyncResponse *response =
      chppAllocResponseFixed(requestHeader, struct ChppTimesyncResponse);
  size_t responseLen = sizeof(*response);

  if (response == NULL) {
    CHPP_LOG_OOM();
    CHPP_DEBUG_ASSERT(false);
  } else {
    response->timeNs = chppGetCurrentTimeNs();
    CHPP_LOGD("chppTimesyncGetTime returning %" PRIuSIZE
              " bytes at time=%" PRIu64,
              responseLen, response->timeNs / CHPP_NSEC_PER_MSEC);

    chppEnqueueTxDatagramOrFail(context->transportContext, response,
                                responseLen);
  }
}

/************************************************
 *  Public Functions
 ***********************************************/

bool chppDispatchTimesyncClientRequest(struct ChppAppState *context,
                                       const uint8_t *buf, size_t len) {
  UNUSED_VAR(len);
  const struct ChppAppHeader *rxHeader = (const struct ChppAppHeader *)buf;
  bool success = true;

  switch (rxHeader->command) {
    case CHPP_TIMESYNC_COMMAND_GETTIME: {
      chppTimesyncGetTime(context, rxHeader);
      break;
    }
    default: {
      success = false;
    }
  }
  return success;
}
