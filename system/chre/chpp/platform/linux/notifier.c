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

#include "chpp/platform/platform_notifier.h"

#include <pthread.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/macros.h"
#include "chpp/mutex.h"
#include "chpp/platform/platform_time.h"
#include "chpp/transport.h"
#include "chpp/transport_signals.h"
#include "time.h"

/************************************************
 *  Public Functions
 ***********************************************/

void chppPlatformNotifierInit(struct ChppNotifier *notifier) {
  chppMutexInit(&notifier->mutex);
  pthread_cond_init(&notifier->cond, NULL);
}

void chppPlatformNotifierDeinit(struct ChppNotifier *notifier) {
  pthread_cond_destroy(&notifier->cond);
  chppMutexDeinit(&notifier->mutex);
}

uint32_t chppPlatformNotifierGetSignal(struct ChppNotifier *notifier) {
  chppMutexLock(&notifier->mutex);

  uint32_t signal = notifier->signal;
  notifier->signal = 0;

  chppMutexUnlock(&notifier->mutex);
  return signal;
}

uint32_t chppPlatformNotifierWait(struct ChppNotifier *notifier) {
  chppMutexLock(&notifier->mutex);

  while (notifier->signal == 0) {
    pthread_cond_wait(&notifier->cond, &notifier->mutex.lock);
  }
  uint32_t signal = notifier->signal;
  notifier->signal = 0;

  chppMutexUnlock(&notifier->mutex);
  return signal;
}
uint32_t chppPlatformNotifierTimedWait(struct ChppNotifier *notifier,
                                       uint64_t timeoutNs) {
  if (timeoutNs == CHPP_TRANSPORT_TIMEOUT_INFINITE) {
    return chppPlatformNotifierWait(notifier);

  } else {
    struct timespec timeout;
    struct timespec absTime;
    uint64_t timeoutS = timeoutNs / CHPP_NSEC_PER_SEC;
    timeoutNs = timeoutNs % CHPP_NSEC_PER_SEC;

    chppMutexLock(&notifier->mutex);

    clock_gettime(CLOCK_REALTIME, &absTime);
    timeout = absTime;
    timeout.tv_sec += timeoutS;
    timeout.tv_nsec += timeoutNs;

    while ((notifier->signal == 0) &&
           (CHPP_TIMESPEC_TO_NS(absTime) < CHPP_TIMESPEC_TO_NS(timeout))) {
      pthread_cond_timedwait(&notifier->cond, &notifier->mutex.lock, &absTime);
      clock_gettime(CLOCK_REALTIME, &absTime);
    }
    uint32_t signal = notifier->signal;
    notifier->signal = 0;

    chppMutexUnlock(&notifier->mutex);
    return signal;
  }
}

void chppPlatformNotifierSignal(struct ChppNotifier *notifier,
                                uint32_t signal) {
  chppMutexLock(&notifier->mutex);

  notifier->signal |= signal;
  pthread_cond_signal(&notifier->cond);

  chppMutexUnlock(&notifier->mutex);
}
