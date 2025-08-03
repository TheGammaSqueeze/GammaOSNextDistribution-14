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

#include <cinttypes>

#include "chre_api/chre.h"
#include "chre_stress_test_manager.h"

namespace chre {

extern "C" void nanoappHandleEvent(uint32_t senderInstanceId,
                                   uint16_t eventType, const void *eventData) {
  stress_test::ManagerSingleton::get()->handleEvent(senderInstanceId, eventType,
                                                    eventData);
}

extern "C" bool nanoappStart(void) {
  stress_test::ManagerSingleton::init();
  return true;
}

extern "C" void nanoappEnd(void) {
  stress_test::ManagerSingleton::deinit();
}

}  // namespace chre
