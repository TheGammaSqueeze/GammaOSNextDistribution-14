/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2016 ST Microelectronics S.A.
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
 *
 *
 ----------------------------------------------------------------------*/
#ifndef __HALCORE_H_
#define __HALCORE_H_

#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>

/* events sent from the callback */
#define HAL_EVENT_DSWRITE 1  /* write raw HAL data downstream   */
#define HAL_EVENT_DATAIND 2  /* new NCI frame received from CLF  */
#define HAL_EVENT_LINKLOST 3 /* connection/link lost             */
#define HAL_EVENT_ERROR 4    /* protocol got into an error state */
#define HAL_EVENT_JUNKRECEIVED                                        \
  5 /* protocol signals that junk has been received. resyncronization */

#define HAL_EVENT_TIMER_TIMEOUT 6

/* flags to be passed to HalCreate */

#define HAL_WRAPPER_TIMEOUT_EVT 7

#define HAL_FLAG_NO_DEBUG 0 /* disable debug output */
#define HAL_FLAG_DEBUG 1    /* enable debug output */

extern bool halTraceMask;
extern pthread_mutex_t debugOutputSem;

#ifdef ANDROID
#include <android/log.h>
#define LOG(s)                                               \
  if (!halTraceMask) {                                       \
  } else {                                                   \
    pthread_mutex_lock(&debugOutputSem);                     \
    __android_log_print(ANDROID_LOG_DEBUG, "NfcHalCore", s); \
    pthread_mutex_unlock(&debugOutputSem);                   \
  }
#define LOGE(s)                                              \
  {                                                          \
    pthread_mutex_lock(&debugOutputSem);                     \
    __android_log_print(ANDROID_LOG_ERROR, "NfcHalCore", s); \
    pthread_mutex_unlock(&debugOutputSem);                   \
  }
#define LOGV(s, ...)                                                      \
  if (!halTraceMask) {                                                    \
  } else {                                                                \
    pthread_mutex_lock(&debugOutputSem);                                  \
    __android_log_print(ANDROID_LOG_DEBUG, "NfcHalCore", s, __VA_ARGS__); \
    pthread_mutex_unlock(&debugOutputSem);                                \
  }
#define LOGVE(s, ...)                                                     \
  {                                                                       \
    pthread_mutex_lock(&debugOutputSem);                                  \
    __android_log_print(ANDROID_LOG_ERROR, "NfcHalCore", s, __VA_ARGS__); \
    pthread_mutex_unlock(&debugOutputSem);                                \
  }
#else
#define LOG printf
#define LOGE printf
#define LOGV printf
#define LOGVE printf
#endif

/* callback function to communicate from HAL Core with the outside world */
typedef void (*HAL_CALLBACK)(void* context, uint32_t event, const void* data,
                             size_t length);

/* flags to be passed to HALCreate */

typedef void* HALHANDLE;

HALHANDLE HalCreate(void* context, HAL_CALLBACK callback, uint32_t flags);

void HalDestroy(HALHANDLE hHAL);

/* send an NCI frame from the HOST to the CLF */
bool HalSendDownstream(HALHANDLE hHAL, const uint8_t* data, size_t size);

// HAL WRAPPER
bool HalSendDownstreamTimer(HALHANDLE hHAL, const uint8_t* data, size_t size,
                            uint8_t duration);
bool HalSendDownstreamStopTimer(HALHANDLE hHAL);

/* send a complete HDLC frame from the CLF to the HOST */
bool HalSendUpstream(HALHANDLE hHAL, const uint8_t* data, size_t size);

#endif
