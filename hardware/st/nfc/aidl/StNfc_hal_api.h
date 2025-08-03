/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 ******************************************************************************/

#ifndef _STNFC_HAL_API_H_
#define _STNFC_HAL_API_H_

#include <aidl/android/hardware/nfc/INfc.h>
#include <aidl/android/hardware/nfc/NfcConfig.h>
#include <aidl/android/hardware/nfc/NfcEvent.h>
#include <aidl/android/hardware/nfc/NfcStatus.h>
#include <aidl/android/hardware/nfc/PresenceCheckAlgorithm.h>
#include <aidl/android/hardware/nfc/ProtocolDiscoveryConfig.h>

#include "hardware_nfc.h"

#define NFC_MODE_OFF 0
#define NFC_MODE_ON 1
#define NFC_MODE_QuickBoot 2

using NfcConfig = aidl::android::hardware::nfc::NfcConfig;
using NfcEvent = aidl::android::hardware::nfc::NfcEvent;
using NfcStatus = aidl::android::hardware::nfc::NfcStatus;
using PresenceCheckAlgorithm =
    aidl::android::hardware::nfc::PresenceCheckAlgorithm;
using ProtocolDiscoveryConfig =
    aidl::android::hardware::nfc::ProtocolDiscoveryConfig;

int StNfc_hal_open(nfc_stack_callback_t* p_cback,
                   nfc_stack_data_callback_t* p_data_cback);
int StNfc_hal_write(uint16_t data_len, const uint8_t* p_data);

int StNfc_hal_core_initialized();

int StNfc_hal_pre_discover();

int StNfc_hal_close(int nfc_mode);

int StNfc_hal_control_granted();

int StNfc_hal_power_cycle();

void StNfc_hal_factoryReset();

int StNfc_hal_closeForPowerOffCase();

void StNfc_hal_getConfig(NfcConfig& config);

void StNfc_hal_setLogging(bool enable);

bool StNfc_hal_isLoggingEnabled();

#endif /* _STNFC_HAL_API_H_ */
