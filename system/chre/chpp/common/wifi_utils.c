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

#include "chpp/common/wifi_utils.h"

#include <inttypes.h>

#include "chpp/log.h"

static uint8_t gResultTotal = 0;
static uint8_t gResultAcc = 0;
static uint8_t gExpectedIndex = 0;

void chppCheckWifiScanEventNotificationReset(void) {
  gResultTotal = 0;
  gResultAcc = 0;
  gExpectedIndex = 0;
}

bool chppCheckWifiScanEventNotification(const struct chreWifiScanEvent *chre) {
  bool success = true;

  if (chre->eventIndex != gExpectedIndex) {
    CHPP_LOGE("Unexpected scan index %" PRIu8 " exp %" PRIu8, chre->eventIndex,
              gExpectedIndex);
    success = false;
  }
  if (chre->eventIndex == 0) {
    if (gResultAcc < gResultTotal) {
      CHPP_LOGE("Too few prev scan results (%" PRIu8 " missing)",
                gResultTotal - gResultAcc);
      success = false;
    }
    gExpectedIndex = 0;
    gResultAcc = 0;
    gResultTotal = chre->resultTotal;
  }

  if (gResultTotal != chre->resultTotal) {
    CHPP_LOGE("Inconsistent result total %" PRIu8 " exp %" PRIu8,
              chre->resultTotal, gResultTotal);
    success = false;
  }

  gResultAcc += chre->resultCount;

  if (gResultAcc >= gResultTotal) {
    if (gResultAcc > gResultTotal) {
      CHPP_LOGE("Too many scan results (%" PRIu8 " extra)",
                gResultAcc - gResultTotal);
      success = false;
    }
    gExpectedIndex = 0;
  } else {
    gExpectedIndex++;
  }

  return success;
}
