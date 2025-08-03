/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <dlfcn.h>
#include <cstdint>

extern "C" {

#include "qsh_na_api.h"

}  // extern "C"

#include "chre/core/event_loop_manager.h"
#include "chre/core/nanoapp.h"
#include "chre/platform/assert.h"
#include "chre/platform/log.h"
#include "chre/platform/memory.h"
#include "chre/platform/slpi/qsh/qsh_proto_shim.h"
#include "chre/sensor.h"
#include "chre/util/macros.h"
#include "chre/util/system/event_callbacks.h"

namespace chre {
namespace {

//! Function pointer to store QSH's version of chreSensorFlushAsync
decltype(chreSensorFlushAsync) *gFlushFuncPtr = nullptr;

/*
 * Used by QSH to obtain the currently running nanoapp instance ID when nanoapps
 * invoke CHRE APIs implemented by its shim.
 */
bool getCurrentNanoappInstanceId(uint32_t *nanoappInstId) {
  CHRE_ASSERT(nanoappInstId != nullptr);
  if (nanoappInstId == nullptr) {
    return false;
  }

  bool success = false;
  Nanoapp *currentNanoapp =
      EventLoopManagerSingleton::get()->getEventLoop().getCurrentNanoapp();
  if (currentNanoapp == nullptr) {
    LOGE("No nanoapp currently executing");
  } else {
    *nanoappInstId = currentNanoapp->getInstanceId();
    success = true;
  }
  return success;
}

/*
 * Used by QSH to post events to the CHRE event loop. The caller continues to
 * own the event pointer after returning so a copy must be made of the data.
 */
bool postEventFromQsh(uint16_t eventType, void *event, uint32_t eventLen,
                      uint32_t nanoappInstId) {
  // Default success to true if the event is empty since an empty event can
  // still be sent to CHRE.
  bool success = false;
  void *eventCopy = nullptr;
  if (eventLen == 0) {
    CHRE_ASSERT(event == nullptr);
    if (event != nullptr) {
      LOGE("Event len 0 with non-null event data");
    } else {
      success = true;
    }
  } else {
    CHRE_ASSERT(event != nullptr);
    if (event != nullptr) {
      eventCopy = memoryAlloc(eventLen);
      if (eventCopy == nullptr) {
        LOG_OOM();
      } else {
        memcpy(eventCopy, event, eventLen);
        success = true;
      }
    }
  }

  if (success) {
    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        eventType, eventCopy, freeEventDataCallback,
        static_cast<uint16_t>(nanoappInstId));
  }
  return success;
}

const qsh_na_api_callbacks gQshCallbacks = {
    getCurrentNanoappInstanceId, /* get_current_nanoapp_inst_id */
    postEventFromQsh,            /* post_event */
};

}  // anonymous namespace

void openQsh() {
  if (!qsh_na_open(&gQshCallbacks)) {
    LOGE("QSH failed to open");
  } else {
    LOGI("QSH opened");
    gFlushFuncPtr = reinterpret_cast<decltype(gFlushFuncPtr)>(
        dlsym(RTLD_NEXT, STRINGIFY(chreSensorFlushAsync)));
    if (gFlushFuncPtr == nullptr) {
      LOGE("Flush function not found!");
    }
  }
}

void closeQsh() {
  qsh_na_close();
}

}  // namespace chre

// Define the delete operator so that SLPI doesn't have to expose this symbol
// since CHRE will never call it directly
void operator delete(void *ptr) noexcept {
  free(ptr);
}

// Export the chreSensorFlushAsync symbol from CHRE and then used the previously
// looked up symbol to WAR loader issue where nanoapps can't see QSH symbols.
DLL_EXPORT extern "C" bool chreSensorFlushAsync(uint32_t sensorHandle,
                                                const void *cookie) {
  return (chre::gFlushFuncPtr != nullptr)
             ? chre::gFlushFuncPtr(sensorHandle, cookie)
             : false;
}