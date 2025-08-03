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

#include <phNxpNciHal.h>

typedef enum {
  SCREEN_OFF = 0,
  SCREEN_ON,
  ULPDET_OFF,
  ULPDET_ON
} RefreshNfccPowerState;

/*******************************************************************************
**
** Function         phNxpNciHal_startPowerTracker()
**
** Description      Function to start power tracker feature.
**
** Parameters       pollDuration - Poll duration in MS for fetching power data
**                  from NFCC.
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
extern "C" NFCSTATUS phNxpNciHal_startPowerTracker(unsigned long pollDuration);

/*******************************************************************************
**
** Function         phNxpNciHal_onRefreshNfccPowerState()
**
** Description      Callback invoked internally by HAL whenever there is system
**                  state change and power data needs to be refreshed.
**
** Parameters       state - Can be SCREEN_OFF, SCREEN_ON, ULPDET_OFF, ULPDET_ON
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
extern "C" NFCSTATUS phNxpNciHal_onRefreshNfccPowerState(
    RefreshNfccPowerState state);

/*******************************************************************************
**
** Function         phNxpNciHal_stopPowerTracker()
**
** Description      Function to stop power tracker feature.
**
** Parameters       None
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
extern "C" NFCSTATUS phNxpNciHal_stopPowerTracker();
