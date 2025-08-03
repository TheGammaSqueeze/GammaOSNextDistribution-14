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

#ifndef CHRE_CORE_EVENT_HELPERS_H_
#define CHRE_CORE_EVENT_HELPERS_H_

#include <cstdint>

namespace chre {

/**
 * Generic event free callback that can be used by any event where the event
 * data is allocated via memoryAlloc, and no special processing is needed in the
 * event complete callback other than freeing the event data.
 */
void freeEventDataCallback(uint16_t eventType, void *eventData);

}  // namespace chre

#endif  // CHRE_CORE_EVENT_HELPERS_H_
