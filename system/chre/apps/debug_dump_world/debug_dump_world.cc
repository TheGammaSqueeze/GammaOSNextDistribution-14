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

#include <cinttypes>

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_api/chre.h"

#define LOG_TAG "[DebugDumpWorld]"

/**
 * A nanoapp that log debug data on receiving CHRE_EVENT_DEBUG_DUMP.
 */

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

namespace {

uint32_t gEventCount = 0;
uint64_t gDwellTimeNs = 0;

}  // namespace

bool nanoappStart() {
  LOGI("Debug dump world start");
  chreConfigureDebugDumpEvent(true /* enable */);
  return true;
}

void nanoappEnd() {
  LOGI("Debug dump world end");

  // No need to disable debug dump event delivery since nanoapps can't receive
  // events after nanoappEnd anyway.
}

void handleDebugDumpEvent() {
  // CHRE adds the nanoapp name / ID to the debug dump automatically.
  chreDebugDumpLog("  Debug event count: %" PRIu32 "\n", ++gEventCount);
  chreDebugDumpLog("  Total dwell time: %" PRIu64 " us\n",
                   gDwellTimeNs / chre::kOneMicrosecondInNanoseconds);

  // Prefer the utility macro if you'll log a float, to suppress double
  // promotion warnings arising from varargs
  float floatVal = 1.23f;
  CHRE_DEBUG_DUMP_LOG("  This is a float: %f", floatVal);
}

void nanoappHandleEvent(uint32_t senderInstanceId, uint16_t eventType,
                        const void *eventData) {
  UNUSED_VAR(eventData);

  uint64_t tic = chreGetTime();
  switch (eventType) {
    case CHRE_EVENT_DEBUG_DUMP:
      LOGI("Receiving debug dump event");
      handleDebugDumpEvent();
      break;
    default:
      LOGW("Unknown event type %" PRIu16 " received from sender %" PRIu32,
           eventType, senderInstanceId);
      break;
  }
  gDwellTimeNs += chreGetTime() - tic;
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // anonymous namespace
}  // namespace chre

#include "chre/platform/static_nanoapp_init.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"

CHRE_STATIC_NANOAPP_INIT(DebugDumpWorld, chre::kDebugDumpWorldAppId, 0,
                         chre::NanoappPermissions::CHRE_PERMS_NONE);
#endif  // CHRE_NANOAPP_INTERNAL
