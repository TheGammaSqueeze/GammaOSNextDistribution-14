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
 */
#ifndef __NFC_SEC_HAL__
#define __NFC_SEC_HAL__

#include <hardware/nfc.h>

#include "hal_msg.h"
#include "osi.h"

#include "android/hardware/nfc/1.2/INfc.h"
#include "android/hardware/nfc/1.2/types.h"

using ::android::hardware::nfc::V1_1::NfcConfig;

#ifndef __bool_true_false_are_defined
#define __bool_true_false_are_defined
typedef enum { false, true } bool;
#endif

/***************************************
 * DEVICE
 ***************************************/
typedef enum {
  NFC_DEV_MODE_OFF = 0,
  NFC_DEV_MODE_ON,
  NFC_DEV_MODE_BOOTLOADER,
} eNFC_DEV_MODE;

/***************************************
 * States
 ***************************************/
typedef enum {
  HAL_STATE_INIT,
  HAL_STATE_DEINIT,
  HAL_STATE_OPEN,
  HAL_STATE_VS,
  HAL_STATE_POSTINIT,
  HAL_STATE_SERVICE,
  HAL_STATE_GRANTED,
  HAL_STATE_CLOSE,      /* VTS */
  HAL_STATE_POWERCYCLE, /* VTS */
} eHAL_STATE;

/* FW sub-state */
#define FW_DATA_PAYLOAD_MAX (256)

/* VS sub-state */
typedef enum {
  VS_INIT,
  VS_W4_COMPLETE,
} eNFC_HAL_VS_STATE;

/***************************************
 * Structures
 ***************************************/
/* VS related */

typedef struct {
  eNFC_HAL_VS_STATE state;
} tNFC_HAL_VS_INFO;
enum {
  CFG_SLEEP_TIMEOUT = 0,
  CFG_WAKEUP_DELAY,
  CFG_NCI_PROP,
  CFG_POWER_DRIVER,
  CFG_TRANS_DRIVER,
  CFG_TRACE_LEVEL,
  CFG_DATA_TRACE,
  CFG_FW_CLK_SPEED,
};

static const char* cfg_name_table[] = {
    "SLEEP_TIMEOUT", "WAKEUP_DELAY", "NCI_PROP",   "POWER_DRIVER",
    "TRANS_DRIVER",  "TRACE_LEVEL",  "DATA_TRACE", "FW_CFG_CLK_SPEED",
};

typedef struct {
  uint32_t sleep_timeout;
  uint32_t override_timeout;
} tNFC_HAL_CONFIG;

/* Granted related */
#define HAL_GRANT_SEND_NEXT 0x00
#define HAL_GRANT_WAIT_READ 0x01
#define HAL_GRANT_FINISH 0x02
typedef uint8_t(tNFC_HAL_GRANT_CALLBACK)(tNFC_NCI_PKT* pkt);

/* FLAGS */
#define HAL_FLAG_MASK_USING_TIMER 0x000F
#define HAL_FLAG_W4_CORE_RESET_RSP 0x0001
#define HAL_FLAG_W4_CORE_INIT_RSP 0x0002

#define HAL_FLAG_PROP_RESET 0x0010
#define HAL_FLAG_CLK_SET 0x0020
#define HAL_FLAG_PROP_ONE_TIMER 0x0040

#define HAL_FLAG_MASK_ALREADY 0x0F00
#define HAL_FLAG_ALREADY_RESET 0x0100
#define HAL_FLAG_ALREADY_INIT 0x0200

#define HAL_FLAG_MASK_SYSTEM 0xF000
#define HAL_FLAG_FORCE_FW_UPDATE 0x1000
#define HAL_FLAG_NTF_TRNS_ERROR 0x2000
#define HAL_FLAG_RETRY_TRNS 0x4000

/***************************************
 * Main information(context block)
 ***************************************/
typedef struct {
  tNFC_HAL_CONFIG cfg;
  eHAL_STATE state; /* HAL state */
  tNFC_HAL_VS_INFO vs_info;

  tOSI_TASK_HANDLER msg_task; /* HAL main task */
  tOSI_QUEUE_HANDLER msg_q;
  nfc_stack_callback_t* stack_cback;     /* Callback for HAL event */
  nfc_stack_data_callback_t* data_cback; /* Callback for data event */
  tNFC_NCI_PKT* nci_last_pkt;            /* last sent package */
  tNFC_NCI_PKT* nci_fragment_pkt;        /* Control msg flagmentation */
  tOSI_TIMER_HANDLER nci_timer;          /* Timer for NCI message */
  tOSI_TIMER_HANDLER sleep_timer;        /* Timer for NCI message */
  int trans_dev;                         /* transport device */
  int power_dev;                         /* power device */
  tOSI_QUEUE_HANDLER nci_q;
  tNFC_HAL_GRANT_CALLBACK* grant_cback;
  unsigned int flag;
  /* START [H16031401] */
  uint8_t msg_event; /* receiving message event */
                     /* END [H16031401] */
} tNFC_HAL_CB;

/*************************************
 * Global
 *************************************/
extern tNFC_HAL_CB nfc_hal_info;

/*************************************
 * NFC HAL API prototype
 *************************************/
int nfc_hal_init(void);
void nfc_hal_deinit(void);
int nfc_hal_open(nfc_stack_callback_t* p_cback,
                 nfc_stack_data_callback_t* p_data_cback);
int nfc_hal_write(uint16_t data_len, const uint8_t* p_data);
int nfc_hal_core_initialized(uint8_t* p_core_init_rsp_params);
int nfc_hal_pre_discover();
int nfc_hal_close();
int nfc_hal_control_granted();
int nfc_hal_power_cycle();
// HIDL INfc 1.1
int nfc_hal_factory_reset(void);
int nfc_hal_closeForPowerOffCase(void);
void nfc_hal_getVendorConfig(NfcConfig& config);  // To-Do

/*************************************
 * NFC HAL functions.
 *************************************/
bool nfc_stack_cback(nfc_event_t event, nfc_status_t event_status);
bool nfc_data_callback(tNFC_NCI_PKT* pkt);

void nfc_hal_task(void);

/* START - VTS */
void nfc_hal_power_sm(tNFC_HAL_MSG* msg);
/* END - VTS */
void nfc_hal_grant_sm(tNFC_HAL_MSG* msg);
void nfc_hal_service_sm(tNFC_HAL_MSG* msg);
void nfc_hal_vs_sm(tNFC_HAL_MSG* msg);
void nfc_hal_postinit_sm(tNFC_HAL_MSG* msg);
void nfc_hal_open_sm(tNFC_HAL_MSG* msg);

void setSleepTimeout(int option, uint32_t timeout);

const char* event_to_string(uint8_t event);
const char* state_to_string(eHAL_STATE state);

// SM
void hal_update_sleep_timer(void);
int __send_to_device(uint8_t* data, size_t len);

// FW
void fw_force_update(void* param);
int nfc_fw_send_data(uint8_t* data, int len);
int fw_read_payload(tNFC_HAL_MSG* msg);

// NCI
int hal_nci_send(tNFC_NCI_PKT* pkt);
void hal_nci_send_reset(void);
void hal_nci_send_prop_fw_cfg(void);
void hal_nci_send_clearLmrt(void);
void nci_init_timeout(void* param);
bool nfc_hal_prehandler(tNFC_NCI_PKT* pkt);
int nci_read_payload(tNFC_HAL_MSG* msg);

// TRACE
void sec_nci_analyzer(tNFC_NCI_PKT* pkt);

#endif  // __NFC_SEC_HAL__
