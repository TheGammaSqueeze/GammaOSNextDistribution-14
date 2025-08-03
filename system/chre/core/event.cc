/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/core/event.h"
#include "chre/platform/system_time.h"
#include "chre/util/time.h"

namespace chre {

uint16_t Event::getTimeMillis() {
  Milliseconds now = SystemTime::getMonotonicTime();
  // Truncating, but we want to save space and really only care about delta time
  // between pending events (for debugging), which shouldn't get close to 65
  // seconds unless something is very wrong
  return static_cast<uint16_t>(now.getMilliseconds());
}

}  // namespace chre
