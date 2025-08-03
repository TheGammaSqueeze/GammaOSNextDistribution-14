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
#include <aidl/android/vendor/powerstats/BnPixelStateResidencyCallback.h>
#include <aidl/android/vendor/powerstats/IPixelStateResidencyProvider.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <phNxpNciHal_PowerStats.h>

#include "NfcProperties.sysprop.h"

using namespace vendor::nfc::nxp;
using ::aidl::android::hardware::power::stats::StateResidency;
using ::aidl::android::vendor::powerstats::BnPixelStateResidencyCallback;
using ::aidl::android::vendor::powerstats::IPixelStateResidencyCallback;
using ::aidl::android::vendor::powerstats::IPixelStateResidencyProvider;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;
using std::vector;

/******************* Constants *****************************************/
const char* kPowerStatsSrvName = "power.stats-vendor";
const char* kPowerEntityName = "Nfc";

/**
 * Class implements power state residency callback.
 *
 * power.stats-vendor service will invoke getStateResidency callback
 * periodically for fetching current value of cached power data for each Nfc
 * state.
 */
class NfcStateResidencyCallback : public BnPixelStateResidencyCallback {
 public:
  NfcStateResidencyCallback() {
    NXPLOG_NCIHAL_I("%s: descriptor = %s", __func__, descriptor);
  }
  /**
   * Returns current state residency data for all Nfc states.
   */
  virtual ScopedAStatus getStateResidency(
      vector<StateResidency>* stateResidency) {
    if (stateResidency == nullptr) {
      return ScopedAStatus::fromServiceSpecificErrorWithMessage(
          NFCSTATUS_INVALID_PARAMETER, "Invalid Param");
    }
    stateResidency->resize(MAX_STATES);

    NXPLOG_NCIHAL_I("%s: Returning state residency data", __func__);

    // STANDBY
    (*stateResidency)[STANDBY].id = STANDBY;
    (*stateResidency)[STANDBY].totalTimeInStateMs =
        NfcProps::standbyStateTick().value_or(0) * STEP_TIME_MS;
    (*stateResidency)[STANDBY].totalStateEntryCount =
        NfcProps::standbyStateEntryCount().value_or(0);
    (*stateResidency)[STANDBY].lastEntryTimestampMs = 0;
    // ULPDET
    (*stateResidency)[ULPDET].id = ULPDET;
    (*stateResidency)[ULPDET].totalTimeInStateMs =
        NfcProps::ulpdetStateTick().value_or(0) * STEP_TIME_MS;
    (*stateResidency)[ULPDET].totalStateEntryCount =
        NfcProps::ulpdetStateEntryCount().value_or(0);
    (*stateResidency)[ULPDET].lastEntryTimestampMs = 0;
    // ACTIVE
    (*stateResidency)[ACTIVE].id = ACTIVE;
    (*stateResidency)[ACTIVE].totalTimeInStateMs =
        NfcProps::activeStateTick().value_or(0) * STEP_TIME_MS;
    (*stateResidency)[ACTIVE].totalStateEntryCount =
        NfcProps::activeStateEntryCount().value_or(0);
    (*stateResidency)[ACTIVE].lastEntryTimestampMs = 0;

    return ScopedAStatus::ok();
  }
  ~NfcStateResidencyCallback() {}
};

/******************* Global variables *********************************/
std::shared_ptr<IPixelStateResidencyProvider> gStateResidencyProvider = NULL;
std::shared_ptr<IPixelStateResidencyCallback> gStateResidencyCallback =
    ndk::SharedRefBase::make<NfcStateResidencyCallback>();

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
NFCSTATUS phNxpNciHal_registerToPowerStats() {
  // Register with power states HAL.
  SpAIBinder binder(AServiceManager_checkService(kPowerStatsSrvName));
  gStateResidencyProvider = IPixelStateResidencyProvider::fromBinder(binder);
  if (gStateResidencyProvider == nullptr) {
    NXPLOG_NCIHAL_E("%s: Failed to get PowerStats service", __func__);
    return NFCSTATUS_FAILED;
  } else {
    NXPLOG_NCIHAL_D("%s: Registering with Power Stats service", __func__);
    gStateResidencyProvider->registerCallback(kPowerEntityName,
                                              gStateResidencyCallback);
    NXPLOG_NCIHAL_D(
        "%s: Starting thread pool for communication with powerstats", __func__);
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();
    NXPLOG_NCIHAL_D("%s: Thread pool started successfully", __func__);
  }
  return NFCSTATUS_SUCCESS;
}

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
NFCSTATUS phNxpNciHal_unregisterPowerStats() {
  // Unregister with PowerStats service
  if (gStateResidencyProvider != nullptr) {
    gStateResidencyProvider->unregisterCallback(gStateResidencyCallback);
    gStateResidencyProvider = nullptr;
    NXPLOG_NCIHAL_D("%s: Unregistered PowerStats callback successfully",
                    __func__);
  }
  return NFCSTATUS_SUCCESS;
}
