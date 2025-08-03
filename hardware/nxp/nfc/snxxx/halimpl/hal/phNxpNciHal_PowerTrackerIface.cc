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

#include "phNxpNciHal_PowerTrackerIface.h"

#include <dlfcn.h>

/*******************************************************************************
**
** Function         phNxpNciHal_isPowerTrackerConfigured()
**
** Description      This function is to check power tracker feature is
**                  configured or not by checking poll duration from
**                  config file.
**
** Parameters       pollDuration - Output parameter for fetching poll duration
**                                 if it is configured in libnfc-ncp.conf
** Returns          true if supported
**                  false otherwise
*******************************************************************************/
static bool phNxpNciHal_isPowerTrackerConfigured(unsigned long* pollDuration) {
  unsigned long num = 0;

  if ((GetNxpNumValue(NAME_NXP_SYSTEM_POWER_TRACE_POLL_DURATION, &num,
                      sizeof(num)))) {
    if ((uint8_t)num > 0) {
      NXPLOG_NCIHAL_D("%s: NxpNci isPowerTrackerSupported true", __func__);
      if (pollDuration) {
        // Convert from seconds to Milliseconds
        *pollDuration = num * 1000;
      }
      return true;
    }
  }
  NXPLOG_NCIHAL_D("%s: NxpNci isPowerTrackerSupported false", __func__);
  return false;
}

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

NFCSTATUS phNxpNciHal_PowerTrackerInit(PowerTrackerHandle* outHandle) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;

  if (outHandle == NULL) {
    return NFCSTATUS_FAILED;
  }
  if (!phNxpNciHal_isPowerTrackerConfigured(&outHandle->pollDuration)) {
    return status;
  }
  // Open power_tracker shared library
  NXPLOG_NCIHAL_D("Opening (/vendor/lib64/power_tracker.so)");
  outHandle->dlHandle = dlopen("/vendor/lib64/power_tracker.so", RTLD_NOW);
  if (outHandle->dlHandle == NULL) {
    NXPLOG_NCIHAL_D("Error : opening (/vendor/lib64/power_tracker.so) %s!!",
                    dlerror());
    return NFCSTATUS_FAILED;
  }
  outHandle->start = (PowerTrackerStartFunc_t)dlsym(
      outHandle->dlHandle, "phNxpNciHal_startPowerTracker");
  if (outHandle->start == NULL) {
    NXPLOG_NCIHAL_D(
        "Error : Failed to find symbol phNxpNciHal_startPowerTracker %s!!",
        dlerror());
  }
  outHandle->stateChange = (PowerTrackerStateChangeFunc_t)dlsym(
      outHandle->dlHandle, "phNxpNciHal_onRefreshNfccPowerState");
  if (outHandle->stateChange == NULL) {
    NXPLOG_NCIHAL_D(
        "Error : Failed to find symbol phNxpNciHal_onRefreshNfccPowerState "
        "%s!!",
        dlerror());
  }
  outHandle->stop = (PowerTrackerStopFunc_t)dlsym(
      outHandle->dlHandle, "phNxpNciHal_stopPowerTracker");
  if (outHandle->stop == NULL) {
    NXPLOG_NCIHAL_D(
        "Error : Failed to find symbol phNxpNciHal_stopPowerTracker %s !!",
        dlerror());
  }
  NXPLOG_NCIHAL_D("Opened (/vendor/lib64/power_tracker.so)");
  return status;
}

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

NFCSTATUS phNxpNciHal_PowerTrackerDeinit(PowerTrackerHandle* outHandle) {
  NXPLOG_NCIHAL_D("Closing (/vendor/lib64/power_tracker.so)");
  if (outHandle == NULL) {
    return NFCSTATUS_SUCCESS;
  }
  if (outHandle->dlHandle != NULL) {
    dlclose(outHandle->dlHandle);
    outHandle->dlHandle = NULL;
  }
  outHandle->start = NULL;
  outHandle->stateChange = NULL;
  outHandle->stop = NULL;
  return NFCSTATUS_SUCCESS;
}
