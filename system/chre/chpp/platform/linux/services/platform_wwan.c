/**
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

#include <stdbool.h>
#include <stdint.h>

#include "chpp/common/wwan.h"
#include "chpp/services/wwan.h"
#include "chre/pal/wwan.h"

static const struct chrePalSystemApi *gSystemApi;
static const struct chrePalWwanCallbacks *gCallbacks;

static bool wwanPalOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalWwanCallbacks *callbacks) {
  gSystemApi = systemApi;
  gCallbacks = callbacks;

  return true;
}

static void wwanPalClose(void) {}

static uint32_t wwanPalGetCapabilities(void) {
  return CHRE_WWAN_GET_CELL_INFO;
}

static bool wwanPalRequestCellInfo(void) {
  struct chreWwanCellInfoResult *result =
      gSystemApi->memoryAlloc(sizeof(struct chreWwanCellInfoResult));
  CHPP_NOT_NULL(result);

  // TODO: <populate result>

  if (result->cellInfoCount > 0) {
    result->cells = gSystemApi->memoryAlloc(result->cellInfoCount *
                                            sizeof(struct chreWwanCellInfo));
    CHPP_NOT_NULL(cells);
  }

  // TODO: <populate cells>

  gCallbacks->cellInfoResultCallback(result);

  return true;  // If successful
}

static void wwanPalReleaseCellInfoResult(
    struct chreWwanCellInfoResult *result) {
  gSystemApi->memoryFree(CHPP_CONST_CAST_POINTER(result->cells));
  gSystemApi->memoryFree(result);
}

#ifdef CHPP_SERVICE_ENABLED_WWAN
const struct chrePalWwanApi *chrePalWwanGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalWwanApi api = {
      .moduleVersion = CHPP_PAL_WWAN_API_VERSION,
      .open = wwanPalOpen,
      .close = wwanPalClose,
      .getCapabilities = wwanPalGetCapabilities,
      .requestCellInfo = wwanPalRequestCellInfo,
      .releaseCellInfoResult = wwanPalReleaseCellInfoResult,
  };

  CHPP_STATIC_ASSERT(
      CHRE_PAL_WWAN_API_CURRENT_VERSION == CHPP_PAL_WWAN_API_VERSION,
      "A newer CHRE PAL API version is available. Please update.");

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(api.moduleVersion,
                                        requestedApiVersion)) {
    return NULL;
  } else {
    return &api;
  }
}
#endif
