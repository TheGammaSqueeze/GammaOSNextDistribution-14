/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2018 ST Microelectronics S.A.
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

#ifndef HAL_FD_H_
#define HAL_FD_H_

#include "halcore.h"

#define STFLASHTOOL_VERSION 0x0503
/*
 *Structure containing fw version info
 */
typedef struct FWInfo {
  uint32_t patchVersion;
  uint32_t chipFwVersion;
  uint8_t chipHwVersion;
  uint32_t chipLoaderVersion;
  uint16_t chipCustVersion;
  uint16_t chipUwbVersion;
  bool hibernate_exited;

  uint16_t fileUwbVersion;
  uint8_t fileHwVersion;  // if 0, no FW patch available.
  uint32_t fileFwVersion;
  uint16_t fileCustVersion;  // if 0, no custom params available.
  uint8_t chipProdType;
} FWInfo;

typedef enum {
  //  HAL_FD_STATE_GET_ATR,
  HAL_FD_STATE_AUTHENTICATE,
  HAL_FD_STATE_ERASE_FLASH,
  HAL_FD_STATE_SEND_RAW_APDU,
  HAL_FD_STATE_EXIT_APDU,
} hal_fd_state_e;

typedef enum {
  HAL_FD_ST54L_STATE_PUY_KEYUSER,
  HAL_FD_ST54L_STATE_ERASE_UPGRADE_START,
  HAL_FD_ST54L_STATE_ERASE_NFC_AREA,
  HAL_FD_ST54L_STATE_ERASE_UPGRADE_STOP,
  HAL_FD_ST54L_STATE_SEND_RAW_APDU,
  HAL_FD_ST54L_STATE_SET_CONFIG,
  HAL_FD_ST54L_STATE_SWITCH_TO_USER,
} hal_fd_st54l_state_e;

#define FT_CLF_MODE_ERROR 0
#define FT_CLF_MODE_LOADER 1
#define FT_CLF_MODE_ROUTER 2

#define FW_TIMER_DURATION 3000

#define FW_PATCH_AVAILABLE 1
#define FW_CUSTOM_PARAM_AVAILABLE 2
#define FW_UWB_PARAM_AVAILABLE 4

#define FW_UPDATE_NEEDED 1
#define CONF_UPDATE_NEEDED 2
#define UWB_CONF_UPDATE_NEEDED 4

#define MAX_BUFFER_SIZE 300

// HwVersion :
#define HW_NFCD 0x04
#define HW_ST54J 0x05
#define HW_ST54L 0x06

/* Function declarations */
int hal_fd_init();
void hal_fd_close();
uint8_t ft_cmd_HwReset(uint8_t* pdata, uint8_t* clf_mode);
void ExitHibernateHandler(HALHANDLE mHalHandle, uint16_t data_len,
                          uint8_t* p_data);
void FwUpdateHandler(HALHANDLE mHalHandle, uint16_t data_len, uint8_t* p_data);
void ApplyCustomParamHandler(HALHANDLE mHalHandle, uint16_t data_len,
                             uint8_t* p_data);
void ApplyUwbParamHandler(HALHANDLE mHalHandle, uint16_t data_len,
                             uint8_t* p_data);
void resetHandlerState();
bool ft_CheckUWBConf() ;
#endif /* HAL_FD_H_ */
