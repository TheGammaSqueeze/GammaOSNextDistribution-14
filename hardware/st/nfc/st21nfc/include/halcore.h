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

#include <android/log.h>
#include <errno.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/* events sent from the callback */
#define HAL_EVENT_DSWRITE 1  /* write raw HAL data downstream   */
#define HAL_EVENT_DATAIND 2  /* new NCI frame received from CLF  */
#define HAL_EVENT_LINKLOST 3 /* connection/link lost             */
#define HAL_EVENT_ERROR 4    /* protocol got into an error state */
#define HAL_EVENT_JUNKRECEIVED \
  5 /* protocol signals that junk has been received. resyncronization */

#define HAL_EVENT_TIMER_TIMEOUT 6

/* flags to be passed to HalCreate */

#define HAL_WRAPPER_TIMEOUT_EVT 7

#define HAL_FLAG_NO_DEBUG 0 /* disable debug output */
#define HAL_FLAG_DEBUG 1    /* enable debug output */

typedef enum {
  HAL_WRAPPER_STATE_CLOSED,
  HAL_WRAPPER_STATE_OPEN,
  HAL_WRAPPER_STATE_OPEN_CPLT,
  HAL_WRAPPER_STATE_NFC_ENABLE_ON,
  HAL_WRAPPER_STATE_PROP_CONFIG,
  HAL_WRAPPER_STATE_READY,
  HAL_WRAPPER_STATE_CLOSING,
  HAL_WRAPPER_STATE_EXIT_HIBERNATE_INTERNAL,
  HAL_WRAPPER_STATE_UPDATE,
  HAL_WRAPPER_STATE_APPLY_CUSTOM_PARAM,
  HAL_WRAPPER_STATE_APPLY_UWB_PARAM,
  HAL_WRAPPER_STATE_SET_ACTIVERW_TIMER,
  HAL_WRAPPER_STATE_APPLY_PROP_CONFIG,
  HAL_WRAPPER_STATE_RECOVERY,
} hal_wrapper_state_e;

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
                            uint32_t duration);
bool HalSendDownstreamTimer(HALHANDLE hHAL, uint32_t duration);
bool HalSendDownstreamStopTimer(HALHANDLE hHAL);

/* send a complete HDLC frame from the CLF to the HOST */
bool HalSendUpstream(HALHANDLE hHAL, const uint8_t* data, size_t size);

void hal_wrapper_set_state(hal_wrapper_state_e new_wrapper_state);
void hal_wrapper_setFwLogging(bool enable);
void I2cResetPulse();
#endif
