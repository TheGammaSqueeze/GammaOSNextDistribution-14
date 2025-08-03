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

#include <android/hardware/nfc/1.1/INfc.h>
#include <android/hardware/nfc/1.1/types.h>
#include <hardware/nfc.h>

using ::android::hardware::nfc::V1_1::NfcConfig;

#define NFC_MODE_OFF 0
#define NFC_MODE_QuickBoot 2


int StNfc_hal_open(nfc_stack_callback_t* p_cback,
                   nfc_stack_data_callback_t* p_data_cback);
int StNfc_hal_write(uint16_t data_len, const uint8_t* p_data);
int StNfc_hal_core_initialized(uint8_t* p_core_init_rsp_params);

int StNfc_hal_pre_discover();

int StNfc_hal_close(int nfc_mode);

int StNfc_hal_control_granted();

int StNfc_hal_power_cycle();

void StNfc_hal_factoryReset();

int StNfc_hal_closeForPowerOffCase();

void StNfc_hal_getConfig(NfcConfig& config);

#endif /* _STNFC_HAL_API_H_ */
