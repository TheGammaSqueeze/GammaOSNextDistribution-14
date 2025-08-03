/*
 * Copyright 2012-2020, 2023 NXP
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
#ifndef _PHNXPNCIHAL_EXT_H_
#define _PHNXPNCIHAL_EXT_H_

#include <phNxpNciHal.h>
#include <string.h>
#define NCI_MT_CMD 0x20
#define NCI_MT_RSP 0x40
#define NCI_MT_NTF 0x60
#define NCI_MSG_CORE_RESET 0x00
#define NCI_MSG_CORE_INIT 0x01
#define HAL_HCI_NETWORK_RESET_EVT 0x07

#define NXP_NFC_SET_CONFIG_PARAM_EXT 0xA0
#define NXP_NFC_PARAM_ID_SWP2 0xD4
#define NXP_NFC_PARAM_ID_SWPUICC3 0xDC

#define CORE_GENERIC_ERR_CURRENT_NTF 0xEA
// PROTECTED_USER_AREA_AT_CRC_MISMATCH
#define CORE_GENERIC_ERR_UA_CRC_NTF 0xF6
// PROTECTED_MIR_USER_AREA_AT_CRC_MISMATCH
#define CORE_GENERIC_ERR_UA_MIR_CRC_NTF 0xF7

/* NCI GID index*/
#define NCI_GID_INDEX 0
/* NCI OID index*/
#define NCI_OID_INDEX 1
/* NCI message length index*/
#define NCI_MSG_LEN_INDEX 2
/* NCI message index for feature*/
#define NCI_MSG_INDEX_FOR_FEATURE 3
/* NCI message index feature value*/
#define NCI_MSG_INDEX_FEATURE_VALUE 4
#define NCI_GID_PROP 0x0F

/* Android Power Saving Opcode */
#define NCI_MSG_PROP_ANDROID_OID 0x0C

/* Android Parameters */
#define NCI_ANDROID_POWER_SAVING 0x01

/* Android Power Saving Params */
#define NCI_ANDROID_POWER_SAVING_PARAM_SIZE 2
#define NCI_ANDROID_POWER_SAVING_PARAM_DISABLE 0
#define NCI_ANDROID_POWER_SAVING_PARAM_ENABLE 1

#define NCI_RSP_SIZE 2
#define NCI_RSP_OK 0
#define NCI_RSP_FAIL 3

void printNfcMwVersion();
void phNxpNciHal_ext_init(void);
NFCSTATUS phNxpNciHal_process_ext_rsp(uint8_t* p_ntf, uint16_t* p_len);
NFCSTATUS phNxpNciHal_send_ext_cmd(uint16_t cmd_len, uint8_t* p_cmd);
NFCSTATUS phNxpNciHal_write_ext(uint16_t* cmd_len, uint8_t* p_cmd_data,
                                uint16_t* rsp_len, uint8_t* p_rsp_data);

extern bool_t wFwUpdateReq;
extern bool_t wRfUpdateReq;
extern fpRegRfFwDndl_t fpRegRfFwDndl;
extern fpPropConfCover_t fpPropConfCover;
#define UINT8_TO_STREAM(p, u8) \
  { *(p)++ = (uint8_t)(u8); }

#endif /* _PHNXPNICHAL_EXT_H_ */
