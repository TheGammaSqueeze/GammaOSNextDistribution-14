/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "chre/platform/memory_manager.h"
#include "chre/platform/shared/memory.h"
#include "chre/util/memory.h"

namespace chre {

void *MemoryManager::doAlloc(Nanoapp *app, uint32_t bytes) {
  if (app->isTcmApp()) {
    return chre::memoryAlloc(bytes);
  } else {
    return chre::memoryAllocDram(bytes);
  }
}

void MemoryManager::doFree(Nanoapp *app, void *ptr) {
  if (app->isTcmApp()) {
    chre::memoryFree(ptr);
  } else {
    chre::memoryFreeDram(ptr);
  }
}

}  // namespace chre
