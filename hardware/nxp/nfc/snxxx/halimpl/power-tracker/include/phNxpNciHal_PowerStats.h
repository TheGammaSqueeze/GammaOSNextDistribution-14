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

#define STEP_TIME_MS 100

/**
 * NFCC power states
 */
enum NfccStates {
  STANDBY = 0,
  ULPDET,
  ACTIVE,
  MAX_STATES,
};

/*******************************************************************************
**
** Function         phNxpNciHal_registerToPowerStats()
**
** Description      Function to hook with android powerstats HAL.
**                  This abstracts power stats HAL implementation with power
**                  tracker feature.
** Parameters       None
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
NFCSTATUS phNxpNciHal_registerToPowerStats();

/*******************************************************************************
**
** Function         phNxpNciHal_unregisterPowerStats()
**
** Description      Function to unhook from android powerstats HAL.
**                  This abstracts power stats HAL implementation with power
**                  tracker feature.
** Parameters       None
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
NFCSTATUS phNxpNciHal_unregisterPowerStats();
