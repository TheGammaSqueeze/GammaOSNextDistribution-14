/*
 * Copyright 2022 NXP
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
#pragma once

#include "phNxpNciHal_PowerTracker.h"

typedef NFCSTATUS (*PowerTrackerStartFunc_t)(unsigned long pollDuration);
typedef NFCSTATUS (*PowerTrackerStateChangeFunc_t)(RefreshNfccPowerState state);
typedef NFCSTATUS (*PowerTrackerStopFunc_t)();

/**
 * Handle to the Power Tracker stack implementation.
 */
typedef struct {
  // Power data refresh duration
  unsigned long pollDuration;
  // Function to start power tracker feature.
  PowerTrackerStartFunc_t start;
  // Function to inform state change in system like screen
  // state change, ulpdet state change.
  PowerTrackerStateChangeFunc_t stateChange;
  // Function to stop power tracker feature.
  PowerTrackerStopFunc_t stop;
  // power_tracker.so dynamic library handle.
  void* dlHandle;
} PowerTrackerHandle;

/*******************************************************************************
**
** Function         phNxpNciHal_PowerTrackerInit()
**
** Description      Initialize power tracker framework.
**
** Parameters       outHandle - Power Tracker Handle
** Returns          NFCSTATUS_SUCCESS if success.
**                  NFCSTATUS_FAILURE otherwise
*******************************************************************************/
NFCSTATUS phNxpNciHal_PowerTrackerInit(PowerTrackerHandle* outHandle);

/*******************************************************************************
**
** Function         phNxpNciHal_PowerTrackerDeinit()
**
** Description      Deinitialize power tracker framework.
**
** Parameters       outHandle - Power Tracker Handle
** Returns          NFCSTATUS_SUCCESS if success.
**                  NFCSTATUS_FAILURE otherwise
*******************************************************************************/

NFCSTATUS phNxpNciHal_PowerTrackerDeinit(PowerTrackerHandle* outHandle);
