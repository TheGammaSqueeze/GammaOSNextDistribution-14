/*
 * Copyright 2022-2023 NXP
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
#include "phNxpNciHal_PowerTracker.h"

#include <inttypes.h>
#include <phNxpNciHal_PowerStats.h>

#include "IntervalTimer.h"
#include "NfcProperties.sysprop.h"
#include "NxpNfcThreadMutex.h"
#include "phNfcCommon.h"
#include "phNxpConfig.h"
#include "phNxpNciHal_ext.h"

using namespace vendor::nfc::nxp;
using std::vector;

/******************* Macro definition *****************************************/
#define STATE_STANDBY_STR "STANDBY"
#define STATE_ULPDET_STR "ULPDET"
#define STATE_ACTIVE_STR "ACTIVE"

#define TIME_MS(spec) \
  ((long)spec.tv_sec * 1000 + (long)(spec.tv_nsec / 1000000))

/******************* Local functions *****************************************/
static void* phNxpNciHal_pollPowerTrackerData(void* pContext);
static NFCSTATUS phNxpNciHal_syncPowerTrackerData();

/******************* Enums and Class declarations ***********************/
/**
 * Power data for each states.
 */
typedef struct {
  uint32_t stateEntryCount;
  uint32_t stateTickCount;
} StateResidencyData;

/**
 * Context object used internally by power tracker implementation.
 */
typedef struct {
  // Power data poll duration.
  long pollDurationMilliSec;
  // Power tracker thread id
  pthread_t thread;
  // To signal events.
  NfcHalThreadCondVar event;
  // To protect state data
  NfcHalThreadMutex dataMutex;
  // Poll for power tracker periodically if this is true.
  bool_t isRefreshNfccStateOngoing;
  // Timestamp of last power data sync.
  struct timespec lastSyncTime;
  // Power data for STANDBY, ULPDET, ACTIVE states
  StateResidencyData stateData[MAX_STATES];
  // Timer used for tracking ULPDET power data.
  IntervalTimer ulpdetTimer;
  // Timestamp of ULPDET start.
  struct timespec ulpdetStartTime;
  // True if ULPDET is on.
  bool_t isUlpdetOn;
} PowerTrackerContext;

/******************* External variables ***************************************/
extern phNxpNciHal_Control_t nxpncihal_ctrl;

/*********************** Global Variables *************************************/
static PowerTrackerContext gContext = {
    .pollDurationMilliSec = 0,
    .thread = 0,
    .isRefreshNfccStateOngoing = false,
    .lastSyncTime.tv_sec = 0,
    .lastSyncTime.tv_nsec = 0,
    .stateData[STANDBY].stateEntryCount = 0,
    .stateData[STANDBY].stateTickCount = 0,
    .stateData[ULPDET].stateEntryCount = 0,
    .stateData[ULPDET].stateTickCount = 0,
    .stateData[ACTIVE].stateEntryCount = 0,
    .stateData[ACTIVE].stateTickCount = 0,
};

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

NFCSTATUS phNxpNciHal_startPowerTracker(unsigned long pollDuration) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;

  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  uint8_t power_tracker_enable = 0x01;

  NXPLOG_NCIHAL_I("%s: Starting PowerTracker with poll duration %ld", __func__,
                  pollDuration);
  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  mEEPROM_info.buffer = (uint8_t*)&power_tracker_enable;
  mEEPROM_info.bufflen = sizeof(power_tracker_enable);
  mEEPROM_info.request_type = EEPROM_POWER_TRACKER_ENABLE;

  status = request_EEPROM(&mEEPROM_info);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Failed to Start PowerTracker, status - %d", status);
    return status;
  }
  if (!gContext.isRefreshNfccStateOngoing) {
    // Initialize last sync time to the start time.
    if (clock_gettime(CLOCK_MONOTONIC, &gContext.lastSyncTime) == -1) {
      NXPLOG_NCIHAL_E("%s Fail get time; errno=0x%X", __func__, errno);
    }
    // Sync Initial values of variables from sys properties.
    // so that previous values are used in case of Nfc HAL crash.
    gContext.stateData[ACTIVE].stateEntryCount =
        NfcProps::activeStateEntryCount().value_or(0);
    gContext.stateData[ACTIVE].stateTickCount =
        NfcProps::activeStateTick().value_or(0);
    gContext.stateData[STANDBY].stateEntryCount =
        NfcProps::standbyStateEntryCount().value_or(0);
    gContext.stateData[STANDBY].stateTickCount =
        NfcProps::standbyStateTick().value_or(0);
    gContext.stateData[ULPDET].stateEntryCount =
        NfcProps::ulpdetStateEntryCount().value_or(0);
    gContext.stateData[ULPDET].stateTickCount =
        NfcProps::ulpdetStateTick().value_or(0);

    // Start polling Thread
    gContext.pollDurationMilliSec = pollDuration;
    gContext.isRefreshNfccStateOngoing = true;
    if (pthread_create(&gContext.thread, NULL, phNxpNciHal_pollPowerTrackerData,
                       &gContext) != 0) {
      NXPLOG_NCIHAL_E("%s: Failed to create PowerTracker, thread - %d",
                      __func__, errno);
      return NFCSTATUS_FAILED;
    }
    phNxpNciHal_registerToPowerStats();
  } else {
    NXPLOG_NCIHAL_E("PowerTracker already enabled");
  }
  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_pollPowerTrackerData
**
** Description      Thread function which tracks power data in a loop with
**                  configured duration until power tracker feature is stopped.
**
** Parameters       pContext - Power tracker thread context if any.
** Returns          None
*******************************************************************************/

static void* phNxpNciHal_pollPowerTrackerData(void* pCtx) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  PowerTrackerContext* pContext = (PowerTrackerContext*)pCtx;

  NXPLOG_NCIHAL_D("Starting to poll for PowerTracker data");
  // Poll till power tracker is cancelled.
  while (pContext->isRefreshNfccStateOngoing) {
    struct timespec absoluteTime;

    if (clock_gettime(CLOCK_MONOTONIC, &absoluteTime) == -1) {
      NXPLOG_NCIHAL_E("%s Fail get time; errno=0x%X", __func__, errno);
    } else {
      absoluteTime.tv_sec += pContext->pollDurationMilliSec / 1000;
      long ns = absoluteTime.tv_nsec +
                ((pContext->pollDurationMilliSec % 1000) * 1000000);
      if (ns > 1000000000) {
        absoluteTime.tv_sec++;
        absoluteTime.tv_nsec = ns - 1000000000;
      } else
        absoluteTime.tv_nsec = ns;
    }
    pContext->event.lock();
    // Wait for poll duration
    pContext->event.timedWait(&absoluteTime);
    pContext->event.unlock();

    // Sync and cache power tracker data.
    if (pContext->isRefreshNfccStateOngoing) {
      status = phNxpNciHal_syncPowerTrackerData();
      if (NFCSTATUS_SUCCESS != status) {
        NXPLOG_NCIHAL_E("Failed to fetch PowerTracker data. error = %d",
                        status);
        // break;
      }
    }
  }
  NXPLOG_NCIHAL_D("Stopped polling for PowerTracker data");
  return NULL;
}

/*******************************************************************************
**
** Function         phNxpNciHal_syncPowerTrackerData()
**
** Description      Function to sync power tracker data from NFCC chip.
**
** Parameters       None.
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/

static NFCSTATUS phNxpNciHal_syncPowerTrackerData() {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  struct timespec currentTime = {.tv_sec = 0, .tv_nsec = 0};
  uint8_t cmd_getPowerTrackerData[] = {0x2F,
                                       0x2E,  // NCI_PROP_GET_PWR_TRACK_DATA_CMD
                                       0x00};  // LENGTH
  uint64_t activeTick = 0, activeCounter = 0;

  CONCURRENCY_LOCK();  // This lock is to protect origin field.
  status = phNxpNciHal_send_ext_cmd(sizeof(cmd_getPowerTrackerData),
                                    cmd_getPowerTrackerData);
  CONCURRENCY_UNLOCK();
  if (status != NFCSTATUS_SUCCESS) {
    return status;
  }
  if (nxpncihal_ctrl.p_rx_data[3] != NFCSTATUS_SUCCESS) {
    return (NFCSTATUS)nxpncihal_ctrl.p_rx_data[3];
  }

  if (clock_gettime(CLOCK_MONOTONIC, &currentTime) == -1) {
    NXPLOG_NCIHAL_E("%s Fail get time; errno=0x%X", __func__, errno);
  }
  uint8_t* rsp = nxpncihal_ctrl.p_rx_data;
  activeCounter = ((uint64_t)rsp[4] << 0) | ((uint64_t)rsp[5] << 8) |
                  ((uint64_t)rsp[6] << 16) | ((uint64_t)rsp[7] << 24);
  activeTick = ((uint64_t)rsp[8] << 0) | ((uint64_t)rsp[9] << 8) |
               ((uint64_t)rsp[10] << 16) | ((uint64_t)rsp[11] << 24);

  // Calculate time difference between two sync
  uint64_t totalTimeMs = TIME_MS(currentTime) - TIME_MS(gContext.lastSyncTime);

  NfcHalAutoThreadMutex(gContext.dataMutex);
  gContext.stateData[ACTIVE].stateEntryCount += activeCounter;
  gContext.stateData[ACTIVE].stateTickCount += activeTick;
  // Standby counter is same as active counter less one as current
  // data sync will move NFCC to active resulting in one value
  // higher than standby
  gContext.stateData[STANDBY].stateEntryCount =
      (gContext.stateData[ACTIVE].stateEntryCount - 1);
  if ((totalTimeMs / STEP_TIME_MS) > activeTick) {
    gContext.stateData[STANDBY].stateTickCount +=
        ((totalTimeMs / STEP_TIME_MS) - activeTick);
  } else {
    NXPLOG_NCIHAL_E("ActiveTick(%" PRIu64 ") > totalTick(%" PRIu64
                    "), Shouldn't happen !!!",
                    activeTick, (totalTimeMs / STEP_TIME_MS));
  }
  gContext.lastSyncTime = currentTime;

  // Sync values of variables to sys properties so that
  // previous values can be synced in case of Nfc HAL crash.
  NfcProps::activeStateEntryCount(gContext.stateData[ACTIVE].stateEntryCount);
  NfcProps::activeStateTick(gContext.stateData[ACTIVE].stateTickCount);
  NfcProps::standbyStateEntryCount(gContext.stateData[STANDBY].stateEntryCount);
  NfcProps::standbyStateTick(gContext.stateData[STANDBY].stateTickCount);

  NXPLOG_NCIHAL_D(
      "Successfully fetched PowerTracker data "
      "Active counter = %u, Active Tick = %u "
      "Standby Counter = %u, Standby Tick = %u "
      "ULPDET Counter = %u, ULPDET Tick = %u",
      gContext.stateData[ACTIVE].stateEntryCount,
      gContext.stateData[ACTIVE].stateTickCount,
      gContext.stateData[STANDBY].stateEntryCount,
      gContext.stateData[STANDBY].stateTickCount,
      gContext.stateData[ULPDET].stateEntryCount,
      gContext.stateData[ULPDET].stateTickCount);
  return status;
}

/*******************************************************************************
**
** Function         onUlpdetTimerExpired()
**
** Description      Callback invoked by Ulpdet timer when timeout happens.
**                  Currently ulpdet power data is tracked with same frequency
**                  as poll duration to be in sync with ACTIVE, STANDBY data.
**                  Once ULPDET timer expires after poll duration data are
**                  updated and timer is re created until ULPDET is off.
**
** Parameters       val - Timer context passed while starting timer.
** Returns          None
*******************************************************************************/

static void onUlpdetTimerExpired(union sigval val) {
  (void)val;
  NXPLOG_NCIHAL_D("%s Ulpdet Timer Expired,", __func__);
  struct timespec ulpdetEndTime = {.tv_sec = 0, .tv_nsec = 0};
  // End ulpdet Tick.
  if (clock_gettime(CLOCK_MONOTONIC, &ulpdetEndTime) == -1) {
    NXPLOG_NCIHAL_E("%s fail get time; errno=0x%X", __func__, errno);
  }
  long ulpdetTimeMs =
      TIME_MS(ulpdetEndTime) - TIME_MS(gContext.ulpdetStartTime);

  NfcHalAutoThreadMutex(gContext.dataMutex);
  // Convert to Tick with 100ms step
  gContext.stateData[ULPDET].stateTickCount += (ulpdetTimeMs / STEP_TIME_MS);
  // Sync values of variables to sys properties so that
  // previous values can be synced in case of Nfc HAL crash.
  NfcProps::ulpdetStateEntryCount(gContext.stateData[ULPDET].stateEntryCount);
  NfcProps::ulpdetStateTick(gContext.stateData[ULPDET].stateTickCount);
  gContext.ulpdetStartTime = ulpdetEndTime;
  if (gContext.isUlpdetOn) {
    // Start ULPDET Timer
    NXPLOG_NCIHAL_D("%s Refreshing Ulpdet Timer", __func__);
    gContext.ulpdetTimer.set(gContext.pollDurationMilliSec, NULL,
                             onUlpdetTimerExpired);
  }
}

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

NFCSTATUS phNxpNciHal_onRefreshNfccPowerState(RefreshNfccPowerState state) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  NXPLOG_NCIHAL_D("%s Enter, RefreshNfccPowerState = %u", __func__, state);
  union sigval val;
  switch (state) {
    case SCREEN_ON:
      // Signal power tracker thread to sync data from NFCC
      gContext.event.signal();
      break;
    case ULPDET_ON:
      if (phNxpNciHal_syncPowerTrackerData() != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Failed to fetch PowerTracker data. error = %d",
                        status);
      }
      gContext.dataMutex.lock();
      gContext.stateData[ULPDET].stateEntryCount++;
      if (clock_gettime(CLOCK_MONOTONIC, &gContext.ulpdetStartTime) == -1) {
        NXPLOG_NCIHAL_E("%s fail get time; errno=0x%X", __func__, errno);
      }
      gContext.isUlpdetOn = true;
      gContext.dataMutex.unlock();
      // Start ULPDET Timer
      gContext.ulpdetTimer.set(gContext.pollDurationMilliSec, NULL,
                               onUlpdetTimerExpired);
      break;
    case ULPDET_OFF:
      if (gContext.isUlpdetOn) {
        gContext.ulpdetTimer.kill();
        gContext.isUlpdetOn = false;
        onUlpdetTimerExpired(val);
        gContext.ulpdetStartTime = {.tv_sec = 0, .tv_nsec = 0};
      }
      break;
    default:
      status = NFCSTATUS_FAILED;
      break;
  }
  NXPLOG_NCIHAL_D("%s Exit", __func__);
  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_stopPowerTracker()
**
** Description      Function to stop power tracker feature.
**
** Parameters       None
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/

NFCSTATUS phNxpNciHal_stopPowerTracker() {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  uint8_t power_tracker_disable = 0x00;

  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  mEEPROM_info.buffer = (uint8_t*)&power_tracker_disable;
  mEEPROM_info.bufflen = sizeof(power_tracker_disable);
  mEEPROM_info.request_type = EEPROM_POWER_TRACKER_ENABLE;

  status = request_EEPROM(&mEEPROM_info);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s Failed to disable PowerTracker, error = %d", __func__,
                    status);
  }
  if (gContext.isRefreshNfccStateOngoing) {
    // Stop Polling Thread
    gContext.isRefreshNfccStateOngoing = false;
    gContext.event.signal();
    if (pthread_join(gContext.thread, NULL) != 0) {
      NXPLOG_NCIHAL_E("Failed to join with PowerTracker thread %d", errno);
    }
  } else {
    NXPLOG_NCIHAL_E("PowerTracker is already disabled");
  }
  if (!gContext.isUlpdetOn) {
    NXPLOG_NCIHAL_I("%s: Stopped PowerTracker", __func__);
    phNxpNciHal_unregisterPowerStats();
  } else {
    NXPLOG_NCIHAL_D("%s: Skipping unregister for ULPDET case", __func__);
  }
  return status;
}
