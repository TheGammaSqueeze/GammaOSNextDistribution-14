/*
 * Copyright 2010-2023 NXP
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
#ifndef _PHNXPNCIHAL_H_
#define _PHNXPNCIHAL_H_

#include <hardware/nfc.h>
#include <phNxpNciHal_utils.h>
#include "NxpMfcReader.h"
#include "NxpNfcCapability.h"
#ifdef NXP_BOOTTIME_UPDATE
#include "eSEClientIntf.h"
#endif

#include "eSEClientExtns.h"
#include "phNxpNciHal_IoctlOperations.h"

/********************* Definitions and structures *****************************/
#define MAX_RETRY_COUNT 5
#define NCI_MAX_DATA_LEN 300
#define NCI_POLL_DURATION 500
#define HAL_NFC_ENABLE_I2C_FRAGMENTATION_EVT 0x07
#define NCI_VERSION_2_0 0x20
#define NCI_VERSION_1_1 0x11
#define NCI_VERSION_1_0 0x10
#define NCI_VERSION_UNKNOWN 0x00
#define SNXXX_NXP_AUTH_TIMEOUT_BUF_LEN 0x05
#define PN557_NXP_AUTH_TIMEOUT_BUF_LEN 0x0C

/*Mem alloc with 8 byte alignment*/
#define size_align(sz) ((((sz)-1) | 7) + 1)
#define nxp_malloc(size) malloc(size_align((size)))

typedef void(phNxpNciHal_control_granted_callback_t)();

#define FW_DBG_REASON_AVAILABLE (0xA3)

#define HOST_ID 0x00
#define ESE_ID 0x01
#define UICC1_ID 0x02
#define UICC2_ID 0x04
#define UICC3_ID 0x08
/* NCI Data */

//#define NCI_MT_CMD 0x20
//#define NCI_MT_RSP 0x40
//#define NCI_MT_NTF 0x60
#define NCI_OID_SYSTEM_TERMPERATURE_INFO_NTF 0x42
#define CORE_RESET_TRIGGER_TYPE_CORE_RESET_CMD_RECEIVED 0x02
#define CORE_RESET_TRIGGER_TYPE_POWERED_ON 0x01
#define NCI2_0_CORE_RESET_TRIGGER_TYPE_OVER_TEMPERATURE ((uint8_t)0xA1)
#define CORE_RESET_TRIGGER_TYPE_UNRECOVERABLE_ERROR 0x00
#define CORE_RESET_TRIGGER_TYPE_FW_ASSERT ((uint8_t)0xA0)
#define CORE_RESET_TRIGGER_TYPE_WATCHDOG_RESET ((uint8_t)0xA3)
#define CORE_RESET_TRIGGER_TYPE_INPUT_CLOCK_LOST ((uint8_t)0xA4)
//#define NCI_MSG_CORE_RESET           0x00
//#define NCI_MSG_CORE_INIT            0x01
#define NCI_MT_MASK 0xE0
#define NCI_OID_MASK 0x3F
/* GID: Group Identifier (byte 0) */
#define NCI_GID_MASK 0x0F
#define ORIG_NXPHAL 0x01
#define ORIG_LIBNFC 0x02
#define NXP_PROPCMD_GID 0x2F
#define NXP_FLUSH_SRAM_AO_TO_FLASH 0x21
#define NXP_CORE_GET_CONFIG_CMD 0x03
#define NXP_CORE_SET_CONFIG_CMD 0x02
#define NXP_MAX_CONFIG_STRING_LEN 260
#define NCI_HEADER_SIZE 3

typedef struct nci_data {
  uint16_t len;
  uint8_t p_data[NCI_MAX_DATA_LEN];
} nci_data_t;

typedef enum {
  HAL_STATUS_CLOSE = 0,
  HAL_STATUS_OPEN,
  HAL_STATUS_MIN_OPEN
} phNxpNci_HalStatus;

typedef enum {
  HAL_NFC_FW_UPDATE_INVALID = 0x00,
  HAL_NFC_FW_UPDATE_START,
  HAL_NFC_FW_UPDATE_SCUCCESS,
  HAL_NFC_FW_UPDATE_FAILED,
} HalNfcFwUpdateStatus;

typedef enum {
  GPIO_UNKNOWN = 0x00,
  GPIO_STORE = 0x01,
  GPIO_STORE_DONE = 0x02,
  GPIO_RESTORE = 0x10,
  GPIO_RESTORE_DONE = 0x20,
  GPIO_CLEAR = 0xFF
} phNxpNciHal_GpioInfoState;
#ifdef NXP_BOOTTIME_UPDATE
extern ese_update_state_t ese_update;
#endif
typedef struct phNxpNciGpioInfo {
  phNxpNciHal_GpioInfoState state;
  uint8_t values[2];
} phNxpNciGpioInfo_t;

/* Macros to enable and disable extensions */
#define HAL_ENABLE_EXT() (nxpncihal_ctrl.hal_ext_enabled = 1)
#define HAL_DISABLE_EXT() (nxpncihal_ctrl.hal_ext_enabled = 0)
typedef struct phNxpNciInfo {
  uint8_t nci_version;
  bool_t wait_for_ntf;
  uint8_t lastResetNtfReason;
} phNxpNciInfo_t;
/* NCI Control structure */
typedef struct phNxpNciHal_Control {
  phNxpNci_HalStatus halStatus; /* Indicate if hal is open or closed */
  pthread_t client_thread;      /* Integration thread handle */
  uint8_t thread_running;       /* Thread running if set to 1, else set to 0 */
  phLibNfc_sConfig_t gDrvCfg;   /* Driver config data */

  /* Rx data */
  uint8_t* p_rx_data;
  uint16_t rx_data_len;

  /* Rx data */
  uint8_t* p_rx_ese_data;
  uint16_t rx_ese_data_len;

  /* libnfc-nci callbacks */
  nfc_stack_callback_t* p_nfc_stack_cback;
  nfc_stack_data_callback_t* p_nfc_stack_data_cback;

  /* control granted callback */
  phNxpNciHal_control_granted_callback_t* p_control_granted_cback;

  /* HAL open status */
  bool_t hal_open_status;

  /* HAL extensions */
  uint8_t hal_ext_enabled;

  /* Waiting semaphore */
  phNxpNciHal_Sem_t ext_cb_data;
  sem_t syncSpiNfc;

  uint16_t cmd_len;
  uint8_t p_cmd_data[NCI_MAX_DATA_LEN];
  uint16_t rsp_len;
  uint8_t p_rsp_data[NCI_MAX_DATA_LEN];

  /* retry count used to force download */
  uint16_t retry_cnt;
  uint8_t read_retry_cnt;
  phNxpNciInfo_t nci_info;
  uint8_t hal_boot_mode;
  bool_t isCoreRstForFwDnld;
  /* to store and restore gpio values */
  phNxpNciGpioInfo_t phNxpNciGpioInfo;
  tNFC_chipType chipType;
  bool_t power_reset_triggered;
  bool_t isUlpdetModeEnabled;
} phNxpNciHal_Control_t;

typedef struct {
  uint8_t fw_update_reqd;
  uint8_t rf_update_reqd;
} phNxpNciHal_FwRfupdateInfo_t;

typedef struct phNxpNciClock {
  bool_t isClockSet;
  uint8_t p_rx_data[20];
  bool_t issetConfig;
} phNxpNciClock_t;

typedef struct phNxpNciRfSetting {
  bool_t isGetRfSetting;
  uint8_t p_rx_data[20];
} phNxpNciRfSetting_t;

typedef struct phNxpNciMwEepromArea {
  bool_t isGetEepromArea;
  uint8_t p_rx_data[32];
} phNxpNciMwEepromArea_t;

enum { SE_TYPE_ESE, SE_TYPE_EUICC, SE_TYPE_UICC, SE_TYPE_UICC2, NUM_SE_TYPES };

typedef enum {
  ANTENNA_CHECK_STATUS,
  ANTENNA_SET_VDDPA
} phNxpNci_Antenaa_Actions_type_t;

typedef void (*fpVerInfoStoreInEeprom_t)();
typedef int (*fpVerifyCscEfsTest_t)(char* nfcc_csc, char* rffilepath,
                                    char* fwfilepath);
typedef int (*fpRegRfFwDndl_t)(uint8_t* fw_update_req, uint8_t* rf_update_req,
                               uint8_t skipEEPROMRead);
typedef int (*fpPropConfCover_t)(bool attached, int type);
typedef int (*fpDoAntennaActivity_t)(phNxpNci_Antenaa_Actions_type_t action);
void phNxpNciHal_initializeRegRfFwDnld();
void phNxpNciHal_deinitializeRegRfFwDnld();
/*set config management*/

#define TOTAL_DURATION 0x00
#define ATR_REQ_GEN_BYTES_POLL 0x29
#define ATR_REQ_GEN_BYTES_LIS 0x61
#define LEN_WT 0x60

/*PN557 Ven Pin Configuration*/

#define PN557_VEN_CFG_VALUE 0x01
#define PN557_VEN_CFG_PULLDOWN 0x02
#define PN557_VEN_CFG_DEFAULT 0x03

/*Whenever a new get cfg need to be sent,
 * array must be updated with defined config type*/
static const uint8_t get_cfg_arr[] = {TOTAL_DURATION, ATR_REQ_GEN_BYTES_POLL,
                                      ATR_REQ_GEN_BYTES_LIS, LEN_WT};

//#define NXP_NFC_SET_CONFIG_PARAM_EXT 0xA0
//#define NXP_NFC_PARAM_ID_SWP2        0xD4
//#define NXP_NFC_PARAM_ID_SWPUICC3    0xDC
typedef enum {
  EEPROM_RF_CFG,
  EEPROM_FW_DWNLD,
  EEPROM_WIREDMODE_RESUME_TIMEOUT,
  EEPROM_ESE_SVDD_POWER,
  EEPROM_ESE_POWER_EXT_PMU,
  EEPROM_PROP_ROUTING,
  EEPROM_ESE_SESSION_ID,
  EEPROM_SWP1_INTF,
  EEPROM_SWP1A_INTF,
  EEPROM_SWP2_INTF,
  EEPROM_FLASH_UPDATE,
  EEPROM_AUTH_CMD_TIMEOUT,
  EEPROM_GUARD_TIMER,
  EEPROM_T4T_NFCEE_ENABLE,
  EEPROM_AUTONOMOUS_MODE,
  EEPROM_CE_PHONE_OFF_CFG,
  EEPROM_ENABLE_VEN_CFG,
  EEPROM_ISODEP_MERGE_SAK,
  EEPROM_SRD_TIMEOUT,
  EEPROM_UICC1_SESSION_ID,
  EEPROM_UICC2_SESSION_ID,
  EEPROM_CE_ACT_NTF,
  EEPROM_UICC_HCI_CE_STATE,
  EEPROM_EXT_FIELD_DETECT_MODE,
  EEPROM_CONF_GPIO_CTRL,
  EEPROM_SET_GPIO_VALUE,
  EEPROM_POWER_TRACKER_ENABLE,
  EEPROM_VDDPA,
} phNxpNci_EEPROM_request_type_t;

typedef struct phNxpNci_EEPROM_info {
  uint8_t request_mode;
  phNxpNci_EEPROM_request_type_t request_type;
  uint8_t update_mode;
  uint8_t* buffer;
  uint8_t bufflen;
} phNxpNci_EEPROM_info_t;

typedef struct phNxpNci_getCfg_info {
  bool_t isGetcfg;
  uint8_t total_duration[4];
  uint8_t total_duration_len;
  uint8_t atr_req_gen_bytes[48];
  uint8_t atr_req_gen_bytes_len;
  uint8_t atr_res_gen_bytes[48];
  uint8_t atr_res_gen_bytes_len;
  uint8_t pmid_wt[3];
  uint8_t pmid_wt_len;
  uint8_t auth_cmd_timeout[PN557_NXP_AUTH_TIMEOUT_BUF_LEN];
  uint8_t auth_cmd_timeoutlen;
} phNxpNci_getCfg_info_t;
typedef enum {
  NFC_FORUM_PROFILE,
  EMV_CO_PROFILE,
  SRD_PROFILE,
  INVALID_PROFILe
} phNxpNciProfile_t;
/* NXP Poll Profile control structure */
typedef struct phNxpNciProfile_Control {
  phNxpNciProfile_t profile_type;
  uint8_t bClkSrcVal; /* Holds the System clock source read from config file */
  uint8_t
      bClkFreqVal;  /* Holds the System clock frequency read from config file */
  uint8_t bTimeout; /* Holds the Timeout Value */
  uint8_t clkReqDelay; /* Holds default delay time before start clock request*/
} phNxpNciProfile_Control_t;

/* Internal messages to handle callbacks */
#define NCI_HAL_OPEN_CPLT_MSG 0x411
#define NCI_HAL_CLOSE_CPLT_MSG 0x412
#define NCI_HAL_POST_INIT_CPLT_MSG 0x413
#define NCI_HAL_PRE_DISCOVER_CPLT_MSG 0x414
#define NCI_HAL_ERROR_MSG 0x415
#define NCI_HAL_HCI_NETWORK_RESET_MSG 0x416
#define NCI_HAL_RX_MSG 0xF01
#define HAL_NFC_FW_UPDATE_STATUS_EVT 0x0A

#define NCIHAL_CMD_CODE_LEN_BYTE_OFFSET (2U)
#define NCIHAL_CMD_CODE_BYTE_LEN (3U)

/******************** NCI HAL exposed functions *******************************/
int phNxpNciHal_check_ncicmd_write_window(uint16_t cmd_len, uint8_t* p_cmd);
void phNxpNciHal_request_control(void);
void phNxpNciHal_release_control(void);
NFCSTATUS phNxpNciHal_send_get_cfgs();
int phNxpNciHal_write_unlocked(uint16_t data_len, const uint8_t* p_data,
                               int origin);
NFCSTATUS request_EEPROM(phNxpNci_EEPROM_info_t* mEEPROM_info);
NFCSTATUS phNxpNciHal_fw_download(uint8_t seq_handler_offset = 0,
                                  bool bIsNfccDlState = false);
NFCSTATUS phNxpNciHal_nfcc_core_reset_init(bool keep_config = false);
int phNxpNciHal_fw_mw_ver_check();
NFCSTATUS phNxpNciHal_check_clock_config(void);
NFCSTATUS phNxpNciHal_china_tianjin_rf_setting(void);
NFCSTATUS phNxpNciHal_CheckValidFwVersion(void);

NFCSTATUS phNxpNciHal_send_nfcee_pwr_cntl_cmd(uint8_t type);
NFCSTATUS phNxpNciHal_nfccClockCfgApply(void);
/*******************************************************************************
**
** Function         phNxpNciHal_configFeatureList
**
** Description      Configures the featureList based on chip type
**                  HW Version information number will provide chipType.
**                  HW Version can be obtained from CORE_INIT_RESPONSE(NCI 1.0)
**                  or CORE_RST_NTF(NCI 2.0)
**
** Parameters       CORE_INIT_RESPONSE/CORE_RST_NTF, len
**
** Returns          none
*******************************************************************************/
void phNxpNciHal_configFeatureList(uint8_t* init_rsp, uint16_t rsp_len);

/******************************************************************************
 * Function         phNxpNciHal_read_and_update_se_state
 *
 * Description      This will read NFCEE status from system properties
 *                  and update to NFCC to enable/disable.
 *
 * Returns          none
 *
 ******************************************************************************/
void phNxpNciHal_read_and_update_se_state();

/******************************************************************************
 * Function         phNxpNciHal_Abort
 *
 * Description      This will post the message to the upper layer
 *                  using the callback p_nfc_stack_cback_backup.
 *
 * Returns          none
 *
 ******************************************************************************/
extern bool phNxpNciHal_Abort();
/******************************************************************************
 * Function         phNxpNciHal_read_fw_dw_status
 *
 * Description      This will read the value of fw download status flag
 *                  from eeprom
 *
 * Parameters       value - this parameter will be updated with the flag
 *                  value from eeprom.
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_read_fw_dw_status(uint8_t& value);

/******************************************************************************
 * Function         phNxpNciHal_write_fw_dw_status
 *
 * Description      This will update value of fw download status flag
 *                  to eeprom
 *
 * Parameters       value - this value will be updated to eeprom flag.
 *
 * Returns          status of the write
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_write_fw_dw_status(uint8_t value);

/******************************************************************************
 * Function         phNxpNciHal_save_uicc_params
 *
 * Description      This will read the UICC HCI param values
 *                  from eeprom and store in global variable
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_save_uicc_params();

/******************************************************************************
 * Function         phNxpNciHal_restore_uicc_params
 *
 * Description      This will set the UICC HCI param values
 *                  back to eeprom from global variable
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_restore_uicc_params();

#endif /* _PHNXPNCIHAL_H_ */
