/*
 *    Copyright (C) 2013 SAMSUNG S.LSI
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 */

#ifndef __NFC_SEC_HALMSG__
#define __NFC_SEC_HALMSG__

/***************************************
 * NCI
 ***************************************/
#define HAL_EVT_SIZE 1
#define NCI_HDR_SIZE 3
#define NCI_MAX_PAYLOAD 0xFF
#define NCI_CTRL_SIZE (NCI_HDR_SIZE + NCI_MAX_PAYLOAD)

typedef struct {
  uint8_t oct0;
  uint8_t oid;
  uint8_t len;
  uint8_t payload[NCI_MAX_PAYLOAD];
} tNFC_NCI_PKT;
/* START [181106] Patch for supporting NCI v2.0 */
// [1. NCI Version Management]
#define NCI_VER_1_0 0x10
#define NCI_VER_2_0 0x20
/* END [181106] Patch for supporting NCI v2.0 */
#define NCI_MT(x) ((x)->oct0 & 0xE0)
#define NCI_PBF(x) ((x)->oct0 & 0x10)
#define NCI_GID(x) ((x)->oct0 & 0x0F)
#define NCI_OID(x) ((x)->oid & 0x3F)
#define NCI_LEN(x) ((x)->len)
#define NCI_PAYLOAD(x) ((x)->payload)
#define NCI_STATUS(x) (((x)->payload)[0])
#define NCI_MF_INFO_SIZE 4
#define NCI_MF_INFO(x) (((x)->payload) + (x)->len - NCI_MF_INFO_SIZE)
#define NCI_PKT_LEN(x) (NCI_HDR_SIZE + NCI_LEN(x))

#define NCI_MT_DATA 0x00
#define NCI_MT_CMD 0x20
#define NCI_MT_RSP 0x40
#define NCI_MT_NTF 0x60

#define NCI_PBF_LAST 0x00
#define NCI_PBF_CONTINUE 0x10

#define NCI_GID_CORE 0x00
#define NCI_GID_RF_MANAGE 0x01
#define NCI_GID_EE_MANAGE 0x02
#define NCI_GID_PROP 0x0F

#define NCI_CORE_RESET 0x00
#define NCI_CORE_INIT 0x01

#define NCI_PROP_AGAIN                                     \
  0x01 /* This prop oid is used only for N3 (sleep mode) \ \
        */
#define NCI_PROP_FW_CFG 0x28
#define NCI_PROP_WR_RESET 0x2F
#define NCI_PROP_SET_SLEEP_TIME 0x1A /* Last updated value: 20160530 */

#define SET_SLEEP_TIME_CFG 0
#define SET_SLEEP_TIME_ONCE 1
#define SET_SLEEP_TIME_FORCE 2

#define NCI_STATUS_OK 0x00
#define NCI_STATUS_E_SYNTAX 0x05

/* START [S15012201] - block flip cover in RF field */
#define HAL_NFC_STATUS_ERR_TRANSPORT 2
/* END [S15012201] - block flip cover in RF field */

/* Response Value for Clock Setting. */
#define NCI_CLOCK_STATUS_SYNTAX_ERROR 0x01
#define NCI_CLOCK_STATUS_MISMATCHED 0x02
#define NCI_CLOCK_STATUS_FULL 0x03
/***************************************
 * BOOTLOADER
 ***************************************/
#define FW_HDR_SIZE 4
typedef struct {
  uint8_t type;
  uint8_t code;
  uint16_t len;
  uint8_t payload[NCI_MAX_PAYLOAD + 1];
} tNFC_FW_PKT;
#define FW_PAYLOAD(x) ((x)->payload)

/* type */
typedef enum { FW_MSG_CMD = 0, FW_MSG_RSP, FW_MSG_DATA } eNFC_FW_BLTYPE;

/***************************************
 * HAL Message
 ***************************************/
#define MSG_EVENT_SIZE 1
typedef struct {
  uint8_t event;
  union {
    tNFC_NCI_PKT nci_packet;
    tNFC_FW_PKT fw_packet;
    uint8_t param[NCI_CTRL_SIZE];
  };
} tNFC_HAL_MSG;

#define HAL_EVT_OPEN 0x00
#define HAL_EVT_CORE_INIT 0x01
#define HAL_EVT_PRE_DISCOVER 0x02
#define HAL_EVT_WRITE 0x03
#define HAL_EVT_READ 0x04
#define HAL_EVT_CONTROL_GRANTED 0x05
#define HAL_EVT_TERMINATE 0x06
/* START - VTS */
#define HAL_EVT_POWER_CYCLE 0x07
/* END - VTS */
#define HAL_EVT_COMPLETE 0xF0
#define HAL_EVT_COMPLETE_FAILED 0xF1

/***************************************
 * NFC Message
 ***************************************/
#define NFC_STATUS_OK 0x00
#define NFC_STATUS_FAILED 0x01

#endif  //__NFC_SEC_HALMSG__
