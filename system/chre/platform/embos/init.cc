/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre/core/init.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/static_nanoapps.h"
#include "chre/embos/init.h"

#include "RTOS.h"

namespace {

constexpr char kChreTaskName[] = "CHRE";
constexpr size_t kChreTaskNameLen = sizeof(kChreTaskName) - 1;

// The CHRE task priority was requested to be between the sub_task (prio=60),
// and the main task (prio=100).
constexpr OS_PRIO kChreTaskPriority = 80;

// Stack for the CHRE task of size 8KB (2048 * sizeof(uint32_t)).
constexpr size_t kChreTaskStackDepth = 2048;
OS_STACKPTR uint32_t gChreTaskStack[kChreTaskStackDepth];

OS_TASK gChreTcb;

void chreThreadEntry() {
  chre::init();
  chre::EventLoopManagerSingleton::get()->lateInit();
  chre::loadStaticNanoapps();

  chre::EventLoopManagerSingleton::get()->getEventLoop().run();

  // we only get here if the CHRE EventLoop exited
  chre::deinit();
}

}  // anonymous namespace

void chreEmbosInit() {
  OS_CREATETASK(&gChreTcb, kChreTaskName, chreThreadEntry, kChreTaskPriority,
                gChreTaskStack);
}

void chreEmbosDeinit() {
  if (OS_IsTask(&gChreTcb)) {
    chre::EventLoopManagerSingleton::get()->getEventLoop().stop();
  }
}

const char *getChreTaskName() {
  return kChreTaskName;
}

size_t getChreTaskNameLen() {
  return kChreTaskNameLen;
}
