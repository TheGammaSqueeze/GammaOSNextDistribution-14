/*
 * Copyright 2012-2023 NXP
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

#include <EseAdaptation.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <dlfcn.h>
#include <log/log.h>
#include <phDal4Nfc_messageQueueLib.h>
#include <phDnldNfc.h>
#include <phNxpConfig.h>
#include <phNxpEventLogger.h>
#include <phNxpLog.h>
#include <phNxpNciHal.h>
#include <phNxpNciHal_Adaptation.h>
#include <phNxpNciHal_Dnld.h>
#include <phNxpNciHal_ext.h>
#include <phNxpTempMgr.h>
#include <phTmlNfc.h>
#include <sys/stat.h>

#include "NfccTransportFactory.h"
#include "NxpNfcThreadMutex.h"
#include "phNxpNciHal_IoctlOperations.h"
#include "phNxpNciHal_LxDebug.h"
#include "phNxpNciHal_PowerTrackerIface.h"
#include "phNxpNciHal_ULPDet.h"
#include "phNxpNciHal_extOperations.h"

using android::base::StringPrintf;
using android::base::WriteStringToFile;

/*********************** Global Variables *************************************/
#define PN547C2_CLOCK_SETTING
#define CORE_RES_STATUS_BYTE 3
#define MAX_NXP_HAL_EXTN_BYTES 10
#define DEFAULT_MINIMAL_FW_VERSION 0x0110DE
#define EOS_FW_SESSION_STATE_LOCKED 0x02

bool bEnableMfcExtns = false;
bool bEnableMfcReader = false;

/* Processing of ISO 15693 EOF */
extern uint8_t icode_send_eof;
extern uint8_t icode_detected;
static uint8_t cmd_icode_eof[] = {0x00, 0x00, 0x00};
static const char* rf_block_num[] = {
    "1",  "2",  "3",  "4",  "5",  "6",  "7",  "8",  "9",  "10", "11",
    "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22",
    "23", "24", "25", "26", "27", "28", "29", "30", NULL};
const char* rf_block_name = "NXP_RF_CONF_BLK_";
static uint8_t read_failed_disable_nfc = false;
/* FW download success flag */
static uint8_t fw_download_success = 0;
static uint8_t config_access = false;
static uint8_t config_success = true;
NfcHalThreadMutex sHalFnLock;

/* NCI HAL Control structure */
phNxpNciHal_Control_t nxpncihal_ctrl;

/* NXP Poll Profile structure */
phNxpNciProfile_Control_t nxpprofile_ctrl;

/* TML Context */
extern phTmlNfc_Context_t* gpphTmlNfc_Context;
extern spTransport gpTransportObj;

extern void phTmlNfc_set_fragmentation_enabled(
    phTmlNfc_i2cfragmentation_t result);

extern NFCSTATUS phNxpNciHal_ext_send_sram_config_to_flash();
extern NFCSTATUS phNxpNciHal_enableDefaultUICC2SWPline(uint8_t uicc2_sel);
extern void phNxpNciHal_conf_nfc_forum_mode();
extern void phNxpNciHal_prop_conf_lpcd(bool enableLPCD);
extern void phNxpNciHal_prop_conf_rssi();

nfc_stack_callback_t* p_nfc_stack_cback_backup;
phNxpNci_getCfg_info_t* mGetCfg_info = NULL;
/* global variable to get FW version from NCI response or dl get version
 * response*/
uint32_t wFwVerRsp;
EseAdaptation* gpEseAdapt = NULL;
#ifdef NXP_BOOTTIME_UPDATE
ese_update_state_t ese_update = ESE_UPDATE_COMPLETED;
#endif
/* External global variable to get FW version */
extern uint16_t wFwVer;
extern uint8_t gRecFWDwnld;
static uint8_t gRecFwRetryCount;  // variable to hold recovery FW retry count
static uint8_t write_unlocked_status = NFCSTATUS_SUCCESS;
uint8_t wFwUpdateReq = false;
uint8_t wRfUpdateReq = false;
uint32_t timeoutTimerId = 0;
#ifndef FW_DWNLD_FLAG
uint8_t fw_dwnld_flag = false;
#endif
bool nfc_debug_enabled = true;
PowerTrackerHandle gPowerTrackerHandle;

/*  Used to send Callback Transceive data during Mifare Write.
 *  If this flag is enabled, no need to send response to Upper layer */
bool sendRspToUpperLayer = true;

phNxpNciHal_Sem_t config_data;

phNxpNciClock_t phNxpNciClock = {0, {0}, false};

phNxpNciRfSetting_t phNxpNciRfSet = {false, {0}};

phNxpNciMwEepromArea_t phNxpNciMwEepromArea = {false, {0}};

volatile bool_t gsIsFirstHalMinOpen = true;
volatile bool_t gsIsFwRecoveryRequired = false;

void* RfFwRegionDnld_handle = NULL;
fpVerInfoStoreInEeprom_t fpVerInfoStoreInEeprom = NULL;
fpRegRfFwDndl_t fpRegRfFwDndl = NULL;
fpPropConfCover_t fpPropConfCover = NULL;
fpDoAntennaActivity_t fpDoAntennaActivity = NULL;
void* phNxpNciHal_client_thread(void* arg);
/**************** local methods used in this file only ************************/
static void phNxpNciHal_open_complete(NFCSTATUS status);
static void phNxpNciHal_MinOpen_complete(NFCSTATUS status);
static void phNxpNciHal_write_complete(void* pContext,
                                       phTmlNfc_TransactInfo_t* pInfo);
static void phNxpNciHal_read_complete(void* pContext,
                                      phTmlNfc_TransactInfo_t* pInfo);
static void phNxpNciHal_close_complete(NFCSTATUS status);
static void phNxpNciHal_core_initialized_complete(NFCSTATUS status);
static void phNxpNciHal_power_cycle_complete(NFCSTATUS status);
static void phNxpNciHal_kill_client_thread(
    phNxpNciHal_Control_t* p_nxpncihal_ctrl);
static void phNxpNciHal_nfccClockCfgRead(void);
static void phNxpNciHal_hci_network_reset(void);
static NFCSTATUS phNxpNciHal_do_swp_session_reset(void);
static void phNxpNciHal_print_res_status(uint8_t* p_rx_data, uint16_t* p_len);
static void phNxpNciHal_enable_i2c_fragmentation();
static NFCSTATUS phNxpNciHal_get_mw_eeprom(void);
static NFCSTATUS phNxpNciHal_set_mw_eeprom(void);
static void phNxpNciHal_configureLxDebugMode();
static void phNxpNciHal_gpio_restore(phNxpNciHal_GpioInfoState state);
static void phNxpNciHal_initialize_debug_enabled_flag();
static void phNxpNciHal_initialize_mifare_flag();
static NFCSTATUS phNxpNciHalRFConfigCmdRecSequence();
static NFCSTATUS phNxpNciHal_CheckRFCmdRespStatus();
static void phNxpNciHal_UpdateFwStatus(HalNfcFwUpdateStatus fwStatus);
static NFCSTATUS phNxpNciHal_resetDefaultSettings(uint8_t fw_update_req,
                                                  bool keep_config);
static NFCSTATUS phNxpNciHal_force_fw_download(uint8_t seq_handler_offset = 0,
                                               bool bIsNfccDlState = false);
static int phNxpNciHal_MinOpen_Clean(char* nfc_dev_node);
static void phNxpNciHal_DownloadFw(bool isMinFwVer,
                                   bool degradedFwDnld = false);
static void phNxpNciHal_CheckAndHandleFwTearDown(void);
static NFCSTATUS phNxpNciHal_getChipInfoInFwDnldMode(
    bool bIsVenResetReqd = false);
static uint8_t phNxpNciHal_getSessionInfoInFwDnldMode();
static NFCSTATUS phNxpNciHal_dlResetInFwDnldMode();
static NFCSTATUS phNxpNciHal_enableTmlRead();

/******************************************************************************
 * Function         onLoadLibrary
 *
 * Description      This function as marked with attribute constructor causes
 *                  the function to be called automatically before execution
 *                  enters main (). It is useful for initializing execution
 *                  context  that will be used implicitly during the execution
 *                  of the program like loading another dynamic library.
 * PARAM            None
 * Returns          void
 *
 ******************************************************************************/
static __attribute__((constructor)) void onLoadLibrary(void) {
  NXPLOG_NCIHAL_D("Initializing power tracker");
  phNxpNciHal_PowerTrackerInit(&gPowerTrackerHandle);
}

/******************************************************************************
 * Function         onUnloadLibrary
 *
 * Description      This function as marked with attribute destructor causes
 *                  the function to be called automatically after execution
 *                  main () has completed. It is useful for de-initializing
 *                  execution context  that were be used implicitly during the
 *                  execution of the program like unloading another dynamic
 *                  library.
 * PARAM            None
 * Returns          void
 *
 ******************************************************************************/
static __attribute__((destructor)) void onUnloadLibrary(void) {
  NXPLOG_NCIHAL_D("De-initializing power tracker");
  phNxpNciHal_PowerTrackerDeinit(&gPowerTrackerHandle);
}

/******************************************************************************
 * Function         phNxpNciHal_initialize_debug_enabled_flag
 *
 * Description      This function gets the value for nfc_debug_enabled
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_initialize_debug_enabled_flag() {
  unsigned long num = 0;
  char valueStr[PROPERTY_VALUE_MAX] = {0};
  if (GetNxpNumValue(NAME_NFC_DEBUG_ENABLED, &num, sizeof(num))) {
    nfc_debug_enabled = (num == 0) ? false : true;
  }

  int len = property_get("nfc.debug_enabled", valueStr, "");
  if (len > 0) {
    // let Android property override .conf variable
    unsigned debug_enabled = 0;
    int ret = sscanf(valueStr, "%u", &debug_enabled);
    if (ret) nfc_debug_enabled = (debug_enabled == 0) ? false : true;
  }
  NXPLOG_NCIHAL_D("nfc_debug_enabled : %d", nfc_debug_enabled);
}

/******************************************************************************
 * Function         phNxpNciHal_client_thread
 *
 * Description      This function is a thread handler which handles all TML and
 *                  NCI messages.
 *
 * Returns          void
 *
 ******************************************************************************/
void* phNxpNciHal_client_thread(void* arg) {
  phNxpNciHal_Control_t* p_nxpncihal_ctrl = (phNxpNciHal_Control_t*)arg;
  phLibNfc_Message_t msg;

  NXPLOG_NCIHAL_D("thread started");

  p_nxpncihal_ctrl->thread_running = 1;

  while (p_nxpncihal_ctrl->thread_running == 1) {
    /* Fetch next message from the NFC stack message queue */
    if (phDal4Nfc_msgrcv(p_nxpncihal_ctrl->gDrvCfg.nClientId, &msg, 0, 0) ==
        -1) {
      NXPLOG_NCIHAL_E("NFC client received bad message");
      continue;
    }

    if (p_nxpncihal_ctrl->thread_running == 0) {
      break;
    }

    switch (msg.eMsgType) {
      case PH_LIBNFC_DEFERREDCALL_MSG: {
        phLibNfc_DeferredCall_t* deferCall =
            (phLibNfc_DeferredCall_t*)(msg.pMsgData);

        REENTRANCE_LOCK();
        deferCall->pCallback(deferCall->pParameter);
        REENTRANCE_UNLOCK();

        break;
      }

      case NCI_HAL_OPEN_CPLT_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_OPEN_CPLT_EVT,
                                              HAL_NFC_STATUS_OK);
        }
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_CLOSE_CPLT_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_CLOSE_CPLT_EVT,
                                              HAL_NFC_STATUS_OK);
        }
        phNxpNciHal_kill_client_thread(&nxpncihal_ctrl);
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_POST_INIT_CPLT_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_POST_INIT_CPLT_EVT,
                                              HAL_NFC_STATUS_OK);
        }
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_PRE_DISCOVER_CPLT_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_PRE_DISCOVER_CPLT_EVT,
                                              HAL_NFC_STATUS_OK);
        }
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_HCI_NETWORK_RESET_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(
              (uint32_t)HAL_HCI_NETWORK_RESET_EVT, HAL_NFC_STATUS_OK);
        }
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_ERROR_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_ERROR_EVT,
                                              HAL_NFC_STATUS_FAILED);
        }
        REENTRANCE_UNLOCK();
        break;
      }

      case NCI_HAL_RX_MSG: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_data_cback != NULL) {
          (*nxpncihal_ctrl.p_nfc_stack_data_cback)(nxpncihal_ctrl.rsp_len,
                                                   nxpncihal_ctrl.p_rsp_data);
        }
        REENTRANCE_UNLOCK();
        break;
      }
      case HAL_NFC_FW_UPDATE_STATUS_EVT: {
        REENTRANCE_LOCK();
        if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
          /* Send the event */
          (*nxpncihal_ctrl.p_nfc_stack_cback)(msg.eMsgType,
                                              *((uint8_t*)msg.pMsgData));
        }
        REENTRANCE_UNLOCK();
        break;
      }
    }
  }

  NXPLOG_NCIHAL_D("NxpNciHal thread stopped");

  return NULL;
}

/******************************************************************************
 * Function         phNxpNciHal_kill_client_thread
 *
 * Description      This function safely kill the client thread and clean all
 *                  resources.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_kill_client_thread(
    phNxpNciHal_Control_t* p_nxpncihal_ctrl) {
  NXPLOG_NCIHAL_D("Terminating phNxpNciHal client thread...");

  p_nxpncihal_ctrl->p_nfc_stack_cback = NULL;
  p_nxpncihal_ctrl->p_nfc_stack_data_cback = NULL;
  p_nxpncihal_ctrl->thread_running = 0;

  return;
}
/******************************************************************************
 * Function         phNxpNciHal_CheckIntegrityRecovery
 *
 * Description     This function to enter in recovery if FW download fails with
 *                 check integrity.
 *
 * Returns         NFCSTATUS
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_CheckIntegrityRecovery() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  if (phNxpNciHal_nfcc_core_reset_init(false) == NFCSTATUS_SUCCESS) {
    status = phNxpNciHal_fw_download();
  } else {
    status = NFCSTATUS_FW_CHECK_INTEGRITY_FAILED;
  }
  return status;
}
/******************************************************************************
 * Function         phNxpNciHal_force_fw_download
 *
 * Description     This function, based on the offset provided, will trigger
 *                 Secure FW download sequence.
 *                 It will retry the FW download in case the Check Integrity
 *                 has been failed.
 *
 * Parameters      Offset by which the FW dnld Seq handler shall be triggered.
 *                 e.g. if we want to send only the Check Integrity command,
 *                 then the offset shall be 7.
 *                 bIsNfccDlState : Indicates if current FW State is FW
 *                 Download/NCI.
 *
 * Returns         SUCCESS if FW download is successful else FAIL.
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_force_fw_download(uint8_t seq_handler_offset,
                                               bool bIsNfccDlState) {
  NFCSTATUS wConfigStatus = NFCSTATUS_SUCCESS;
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  /*Get FW version from device*/
  for (int retry = 1; retry >= 0; retry--) {
    if (phDnldNfc_InitImgInfo() == NFCSTATUS_SUCCESS) {
      break;
    } else {
      phDnldNfc_ReSetHwDevHandle();
      NXPLOG_NCIHAL_E("Image information extraction Failed!!");
      if (!retry) return NFCSTATUS_FAILED;
    }
  }

  NXPLOG_NCIHAL_D("FW version for FW file = 0x%x", wFwVer);
  NXPLOG_NCIHAL_D("FW version from device = 0x%x", wFwVerRsp);
  if (wFwVerRsp == 0) {
    status = phNxpNciHal_getChipInfoInFwDnldMode(true);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("phNxpNciHal_getChipInfoInFwDnldMode Failed");
    }
    bIsNfccDlState = true;
  }
  if (NFCSTATUS_SUCCESS == phNxpNciHal_CheckValidFwVersion()) {
    NXPLOG_NCIHAL_D("FW update required");
    nxpncihal_ctrl.phNxpNciGpioInfo.state = GPIO_UNKNOWN;
    if (IS_CHIP_TYPE_L(sn100u)) phNxpNciHal_gpio_restore(GPIO_STORE);
    fw_download_success = 0;
    /*We are expecting NFC to be either in NFC or in the FW Download state*/
    status = phNxpNciHal_fw_download(seq_handler_offset, bIsNfccDlState);
    if (status == NFCSTATUS_FW_CHECK_INTEGRITY_FAILED) {
      status = phNxpNciHal_CheckIntegrityRecovery();
    }
    property_set("nfc.fw.downloadmode_force", "0");
    if (status == NFCSTATUS_SUCCESS) {
      wConfigStatus = NFCSTATUS_SUCCESS;
      fw_download_success = TRUE;
    } else if (status == NFCSTATUS_FW_CHECK_INTEGRITY_FAILED ||
               (phNxpNciHal_fw_mw_ver_check() != NFCSTATUS_SUCCESS)) {
      phOsalNfc_Timer_Cleanup();
      phNxpTempMgr::GetInstance().Reset();
      phTmlNfc_Shutdown_CleanUp();
      return NFCSTATUS_CMD_ABORTED;
    }

    status = phNxpNciHal_nfcc_core_reset_init();
    if (status == NFCSTATUS_SUCCESS && IS_CHIP_TYPE_L(sn100u)) {
      if (status == NFCSTATUS_SUCCESS) {
        phNxpNciHal_gpio_restore(GPIO_RESTORE);
      } else {
        NXPLOG_NCIHAL_E("Failed to restore GPIO values!!!\n");
      }
    }
  }
  return wConfigStatus;
}

/******************************************************************************
 * Function         phNxpNciHal_fw_download
 *
 * Description      This function download the PN54X secure firmware to IC. If
 *                  firmware version in Android filesystem and firmware in the
 *                  IC is same then firmware download will return with success
 *                  without downloading the firmware.
 *
 * Returns          NFCSTATUS_SUCCESS if firmware download successful
 *                  NFCSTATUS_FAILED in case of failure
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_fw_download(uint8_t seq_handler_offset,
                                  bool bIsNfccDlState) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  phNxpNciHal_UpdateFwStatus(HAL_NFC_FW_UPDATE_START);
  phNxpNciHal_nfccClockCfgRead();

  if (!bIsNfccDlState) {
    status = phNxpNciHal_write_fw_dw_status(TRUE);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("%s: NXP Set FW DW Flag failed", __FUNCTION__);
    }

    NXPLOG_NCIHAL_D("nfcFL.nfccFL._NFCC_DWNLD_MODE %x\n",
                    nfcFL.nfccFL._NFCC_DWNLD_MODE);

    if (IS_CHIP_TYPE_GE(sn100u)) {
      uint8_t ven_cfg_low_cmd[] = {0x20, 0x02, 0x05, 0x01,
                                   0xA0, 0x07, 0x01, 0x00};
      status =
          phNxpNciHal_send_ext_cmd(sizeof(ven_cfg_low_cmd), ven_cfg_low_cmd);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Failed to set VEN_CFG to low \n");
      }
    }
    /*Save UICC params */
    status = phNxpNciHal_save_uicc_params();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to save UICC params \n");
    }

    status = phTmlNfc_IoCtl(phTmlNfc_e_EnableDownloadMode);
    if (NFCSTATUS_SUCCESS != status) {
      phTmlNfc_EnableFwDnldMode(false);
      phNxpNciHal_UpdateFwStatus(HAL_NFC_FW_UPDATE_FAILED);
      return NFCSTATUS_FAILED;
    }
  }

  /* Make sure read thread is pending before updating phTmlNfc_EnableFwDnldMode
   * to true*/
  NFCSTATUS readStatus = phNxpNciHal_enableTmlRead();
  if (readStatus != PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_BUSY)) {
    NXPLOG_NCIHAL_E("Read Thread is not pending already. status = 0x%x \n",
                    readStatus);
  }

  if (nfcFL.nfccFL._NFCC_DWNLD_MODE == NFCC_DWNLD_WITH_NCI_CMD &&
      (!bIsNfccDlState)) {
    nxpncihal_ctrl.isCoreRstForFwDnld = TRUE;
    /*NCI_RESET_CMD*/
    static uint8_t cmd_reset_nci_dwnld[] = {0x20, 0x00, 0x01, 0x80};
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci_dwnld),
                                      cmd_reset_nci_dwnld);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Core reset FW download command failed \n");
    }
    nxpncihal_ctrl.isCoreRstForFwDnld = FALSE;
  }
  if (NFCSTATUS_SUCCESS == status) {
    phTmlNfc_EnableFwDnldMode(true);
    /* Set the obtained device handle to download module */

    phDnldNfc_SetHwDevHandle();

    NXPLOG_NCIHAL_D("Calling Seq handler for FW Download \n");
    status = phNxpNciHal_fw_download_seq(nxpprofile_ctrl.bClkSrcVal,
                                         nxpprofile_ctrl.bClkFreqVal,
                                         seq_handler_offset);

    if (phNxpNciHal_dlResetInFwDnldMode() != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("DL Reset failed in FW DN mode");
    }

    /* FW download done.Therefore if previous I2C write failed then we can
     * change the state to NFCSTATUS_SUCCESS*/
    write_unlocked_status = NFCSTATUS_SUCCESS;
  } else {
    phTmlNfc_EnableFwDnldMode(false);
    status = NFCSTATUS_FAILED;
  }
  if (NFCSTATUS_SUCCESS == status) {
    phNxpNciHal_UpdateFwStatus(HAL_NFC_FW_UPDATE_SCUCCESS);
  } else {
    phNxpNciHal_UpdateFwStatus(HAL_NFC_FW_UPDATE_FAILED);
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_CheckValidFwVersion
 *
 * Description      This function checks the valid FW for Mobile device.
 *                  If the FW doesn't belong the Mobile device it further
 *                  checks nxp config file to override.
 *
 * Returns          NFCSTATUS_SUCCESS if valid fw version found
 *                  NFCSTATUS_NOT_ALLOWED in case of FW not valid for mobile
 *                  device
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_CheckValidFwVersion(void) {
  NFCSTATUS status = NFCSTATUS_NOT_ALLOWED;
  const unsigned char sfw_infra_major_no = 0x02;
  unsigned char ufw_current_major_no = 0x00;
  uint8_t rom_version = 0xFF & (wFwVerRsp >> 16);
  uint8_t fw_maj_ver = 0xFF & (wFwVerRsp >> 8);

  /* extract the firmware's major no */
  ufw_current_major_no = ((0x00FF) & (wFwVer >> 8U));
  NXPLOG_NCIHAL_D("%s current_major_no = 0x%x", __func__, ufw_current_major_no);
  NXPLOG_NCIHAL_D("%s fw_maj_ver = 0x%x", __func__, fw_maj_ver);
  if (IS_CHIP_TYPE_EQ(pn557)) {
    if (ufw_current_major_no >= fw_maj_ver) {
      /* if file major version is grater than the one from the
         Nfc init command allow FW download
      */
      status = NFCSTATUS_SUCCESS;
    }
    return status;
  }

  if (wFwVerRsp == 0) {
    NXPLOG_NCIHAL_E(
        "FW Version not received by NCI command >>> Force Firmware download");
    status = NFCSTATUS_SUCCESS;
  } else if ((ufw_current_major_no == nfcFL._FW_MOBILE_MAJOR_NUMBER) ||
             ((ufw_current_major_no == FW_MOBILE_MAJOR_NUMBER_PN81A) &&
              (nxpncihal_ctrl.nci_info.nci_version == NCI_VERSION_2_0))) {
    NXPLOG_NCIHAL_E("FW Version 2");
    status = NFCSTATUS_SUCCESS;
  } else if (ufw_current_major_no == sfw_infra_major_no) {
    if ((rom_version == FW_MOBILE_ROM_VERSION_PN553 ||
         rom_version == FW_MOBILE_ROM_VERSION_PN557)) {
      NXPLOG_NCIHAL_D(" PN557  allow Fw download with major number =  0x%x",
                      ufw_current_major_no);
      status = NFCSTATUS_SUCCESS;
    } else {
      status = NFCSTATUS_NOT_ALLOWED;
    }
  } else {
    NXPLOG_NCIHAL_E("Wrong FW Version >>> Firmware download not allowed");
  }

  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_MinOpen_Clean
 *
 * Description      This function shall be called from phNxpNciHal_MinOpen when
 *                  any unrecoverable error has encountered which needs to mark
 *                  min open as failed, HAL status as closed & deallocate any
 *                  memory if allocated.
 *
 * Returns          This function always returns Failure
 *
 ******************************************************************************/
static int phNxpNciHal_MinOpen_Clean(char* nfc_dev_node) {
  if (nfc_dev_node != NULL) {
    free(nfc_dev_node);
    nfc_dev_node = NULL;
  }
  if (mGetCfg_info != NULL) {
    free(mGetCfg_info);
    mGetCfg_info = NULL;
  }
  /* Report error status */
  phNxpNciHal_cleanup_monitor();
  nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
  return NFCSTATUS_FAILED;
}

/******************************************************************************
 * Function         phNxpNciHal_MinOpen
 *
 * Description      This function initializes the least required resources to
 *                  communicate to NFCC.This is mainly used to communicate to
 *                  NFCC when NFC service is not available.
 *
 *
 * Returns          This function return NFCSTATUS_SUCCESS (0) in case of
 *                  success In case of failure returns other failure value.
 *
 ******************************************************************************/
int phNxpNciHal_MinOpen() {
  phOsalNfc_Config_t tOsalConfig;
  phTmlNfc_Config_t tTmlConfig;
  char* nfc_dev_node = NULL;
  const uint16_t max_len = 260;
  NFCSTATUS wConfigStatus = NFCSTATUS_SUCCESS;
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  int dnld_retry_cnt = 0;
  NXPLOG_NCIHAL_D("phNxpNci_MinOpen(): enter");

  if (nxpncihal_ctrl.halStatus == HAL_STATUS_MIN_OPEN) {
    NXPLOG_NCIHAL_D("phNxpNciHal_MinOpen(): already open");
    return NFCSTATUS_SUCCESS;
  }
  phNxpNciHal_initializeRegRfFwDnld();

  int8_t ret_val = 0x00;

  phNxpNciHal_initialize_debug_enabled_flag();
  /* initialize trace level */
  phNxpLog_InitializeLogLevel();

  /* initialize Mifare flags*/
  phNxpNciHal_initialize_mifare_flag();

  /*Create the timer for extns write response*/
  timeoutTimerId = phOsalNfc_Timer_Create();

  if (phNxpNciHal_init_monitor() == NULL) {
    NXPLOG_NCIHAL_E("Init monitor failed");
    return NFCSTATUS_FAILED;
  }

  CONCURRENCY_LOCK();
  memset(&tOsalConfig, 0x00, sizeof(tOsalConfig));
  memset(&tTmlConfig, 0x00, sizeof(tTmlConfig));
  memset(&nxpprofile_ctrl, 0, sizeof(phNxpNciProfile_Control_t));

  /*Init binary semaphore for Spi Nfc synchronization*/
  if (0 != sem_init(&nxpncihal_ctrl.syncSpiNfc, 0, 1)) {
    NXPLOG_NCIHAL_E("sem_init() FAiled, errno = 0x%02X", errno);
    CONCURRENCY_UNLOCK();
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  }

  /* By default HAL status is HAL_STATUS_OPEN */
  nxpncihal_ctrl.halStatus = HAL_STATUS_OPEN;

  /*nci version NCI_VERSION_2_0 version by default for SN100 chip type*/
  nxpncihal_ctrl.nci_info.nci_version = NCI_VERSION_2_0;
  /* Read the nfc device node name */
  nfc_dev_node = (char*)malloc(max_len * sizeof(char));
  if (nfc_dev_node == NULL) {
    NXPLOG_NCIHAL_D("malloc of nfc_dev_node failed ");
    CONCURRENCY_UNLOCK();
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  } else if (!GetNxpStrValue(NAME_NXP_NFC_DEV_NODE, nfc_dev_node, max_len)) {
    NXPLOG_NCIHAL_D(
        "Invalid nfc device node name keeping the default device node "
        "/dev/pn54x");
    strlcpy(nfc_dev_node, "/dev/pn54x", (max_len * sizeof(char)));
  }
  /* Configure hardware link */
  nxpncihal_ctrl.gDrvCfg.nClientId = phDal4Nfc_msgget(0, 0600);
  nxpncihal_ctrl.gDrvCfg.nLinkType = ENUM_LINK_TYPE_I2C; /* For PN54X */
  tTmlConfig.pDevName = (int8_t*)nfc_dev_node;
  tOsalConfig.dwCallbackThreadId = (uintptr_t)nxpncihal_ctrl.gDrvCfg.nClientId;
  tOsalConfig.pLogFile = NULL;
  tTmlConfig.dwGetMsgThreadId = (uintptr_t)nxpncihal_ctrl.gDrvCfg.nClientId;
  mGetCfg_info = NULL;
  mGetCfg_info =
      (phNxpNci_getCfg_info_t*)nxp_malloc(sizeof(phNxpNci_getCfg_info_t));
  if (mGetCfg_info == NULL) {
    CONCURRENCY_UNLOCK();
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  }
  memset(mGetCfg_info, 0x00, sizeof(phNxpNci_getCfg_info_t));

  /* Initialize TML layer */
  wConfigStatus = phTmlNfc_Init(&tTmlConfig);
  if (wConfigStatus != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("phTmlNfc_Init Failed");
    CONCURRENCY_UNLOCK();
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  } else {
    if (nfc_dev_node != NULL) {
      free(nfc_dev_node);
      nfc_dev_node = NULL;
    }
  }

  /* Create the client thread */
  ret_val = pthread_create(&nxpncihal_ctrl.client_thread, NULL,
                           phNxpNciHal_client_thread, &nxpncihal_ctrl);
  if (ret_val != 0) {
    NXPLOG_NCIHAL_E("pthread_create failed");
    wConfigStatus = phTmlNfc_Shutdown_CleanUp();
    CONCURRENCY_UNLOCK();
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  }

  CONCURRENCY_UNLOCK();
  /* call read pending */
  status = phTmlNfc_Read(
      nxpncihal_ctrl.p_rsp_data, NCI_MAX_DATA_LEN,
      (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_read_complete, NULL);
  if (status != NFCSTATUS_PENDING) {
    NXPLOG_NCIHAL_E("TML Read status error status = %x", status);
    wConfigStatus = phTmlNfc_Shutdown_CleanUp();
    wConfigStatus = NFCSTATUS_FAILED;
    return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
  }

  /* Get the chip-type to know if it is PN557
   Then don't send the Get version command */
  unsigned long chipInfo = 0;
  if (GetNxpNumValue(NAME_NXP_NFC_CHIP, &chipInfo, sizeof(chipInfo))) {
    NXPLOG_NCIHAL_D("The chip type is %lx", chipInfo);
  }

  if (gsIsFirstHalMinOpen) {
    /*Skip get version command for pn557*/
    if (chipInfo != pn557) phNxpNciHal_CheckAndHandleFwTearDown();
  }

  uint8_t seq_handler_offset = 0x00;
  uint8_t fw_update_req = 1;
  uint8_t rf_update_req;
  bool bVenResetRequired = false;
  bool bIsNfccDlState = false;
  phNxpNciHal_ext_init();

  phTmlNfc_IoCtl(phTmlNfc_e_EnableVen);

  if (phNxpNciHal_isULPDetSupported()) {
    status = phTmlNfc_IoCtl(phTmlNfc_e_PullVenHigh);
    if (NFCSTATUS_SUCCESS == status) {
      NXPLOG_NCIHAL_D("ULPDET phTmlNfc_e_PullVenHigh - SUCCESS\n");
    } else {
      NXPLOG_NCIHAL_D("ULPDET phTmlNfc_e_PullVenHigh - FAILED\n");
    }
  }

  if (wFwVerRsp == 0) {
    bVenResetRequired = true;
  }
  /* reset version info new version info will be fetch */
  wFwVerRsp = 0x00;
  wFwVer = 0x00;
  if (NFCSTATUS_SUCCESS == phNxpNciHal_nfcc_core_reset_init(true)) {
    setNxpFwConfigPath();
    if (IS_CHIP_TYPE_L(sn100u)) phNxpNciHal_enable_i2c_fragmentation();

    status = phNxpNciHal_CheckFwRegFlashRequired(&fw_update_req, &rf_update_req,
                                                 false);
    if (status != NFCSTATUS_OK) {
      NXPLOG_NCIHAL_D(
          "phNxpNciHal_CheckFwRegFlashRequired() failed:exit status = %x",
          status);
      fw_update_req = FALSE;
      rf_update_req = FALSE;
    }

    if (!wFwUpdateReq) {
      uint8_t is_teared_down = 0x00;
      status = phNxpNciHal_read_fw_dw_status(is_teared_down);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("%s: NXP get FW DW Flag failed", __FUNCTION__);
      }
      if (is_teared_down) {
        seq_handler_offset = PHLIBNFC_DNLD_CHECKINTEGRITY_OFFSET;
        fw_update_req = TRUE;
      } else {
        NXPLOG_NCIHAL_D("FW update not required");
        property_set("nfc.fw.downloadmode_force", "0");
        phDnldNfc_ReSetHwDevHandle();
      }
    }
  } else if (bVenResetRequired) {
    if (NFCSTATUS_SUCCESS == phNxpNciHal_getChipInfoInFwDnldMode(true))
      bIsNfccDlState = true;
  }

  if (gsIsFirstHalMinOpen && gsIsFwRecoveryRequired) {
    NXPLOG_NCIHAL_E("FW Recovery is required");
    fw_update_req = true;
  }

  do {
    if (fw_update_req && !fw_download_success) {
      gsIsFwRecoveryRequired = false;
      status =
          phNxpNciHal_force_fw_download(seq_handler_offset, bIsNfccDlState);
      if (status == NFCSTATUS_CMD_ABORTED) {
        return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
      } else if (fw_download_success) {
        wConfigStatus = NFCSTATUS_SUCCESS;
      }
    }
    status = phNxpNciHal_resetDefaultSettings(
        fw_update_req, fw_download_success ? false : true);

    if ((status != NFCSTATUS_SUCCESS && fw_download_success) ||
        (gsIsFwRecoveryRequired && (fw_update_req || gsIsFirstHalMinOpen))) {
      NXPLOG_NCIHAL_E(
          "FW Recovery required, Perform Force FW Download "
          "gsIsFwRecoveryRequired %d",
          gsIsFwRecoveryRequired);
      fw_update_req = 1;
      dnld_retry_cnt++;
    } else if (status != NFCSTATUS_SUCCESS) {
      return phNxpNciHal_MinOpen_Clean(nfc_dev_node);
    } else {
      break;
    }

    if (dnld_retry_cnt > 1) {
      wConfigStatus = NFCSTATUS_FAILED;
      break;
    }

  } while (status != NFCSTATUS_SUCCESS || gsIsFwRecoveryRequired);

  if (fpDoAntennaActivity != NULL &&
      (gsIsFirstHalMinOpen || fw_download_success)) {
    fpDoAntennaActivity(ANTENNA_CHECK_STATUS);
  }
  /* Call open complete */
  phNxpNciHal_MinOpen_complete(wConfigStatus);
  NXPLOG_NCIHAL_D("phNxpNciHal_MinOpen(): exit");
  return wConfigStatus;
}

/******************************************************************************
 * Function         phNxpNciHal_open
 *
 * Description      This function is called by libnfc-nci during the
 *                  initialization of the NFCC. It opens the physical connection
 *                  with NFCC (PN54X) and creates required client thread for
 *                  operation.
 *                  After open is complete, status is informed to libnfc-nci
 *                  through callback function.
 *
 * Returns          This function return NFCSTATUS_SUCCESS (0) in case of
 *                  success In case of failure returns other failure value.
 *
 ******************************************************************************/
int phNxpNciHal_open(nfc_stack_callback_t* p_cback,
                     nfc_stack_data_callback_t* p_data_cback) {
  NFCSTATUS wConfigStatus = NFCSTATUS_SUCCESS;
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  NXPLOG_NCIHAL_E("phNxpNciHal_open NFC HAL OPEN");
#ifdef NXP_BOOTTIME_UPDATE
  if (ese_update != ESE_UPDATE_COMPLETED) {
    ALOGD("BLOCK NFC HAL OPEN");
    if (p_cback != NULL) {
      p_nfc_stack_cback_backup = p_cback;
      (*p_cback)(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
    }
    return NFCSTATUS_FAILED;
  }
#endif
  NfcHalAutoThreadMutex a(sHalFnLock);
  if (nxpncihal_ctrl.halStatus == HAL_STATUS_OPEN) {
    NXPLOG_NCIHAL_D("phNxpNciHal_open already open");
    phNxpNciHal_open_complete(wConfigStatus);
    return wConfigStatus;
  } else if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE) {
    PhNxpEventLogger::GetInstance().Initialize();
    memset(&nxpncihal_ctrl, 0x00, sizeof(nxpncihal_ctrl));
    nxpncihal_ctrl.p_nfc_stack_cback = p_cback;
    nxpncihal_ctrl.p_nfc_stack_data_cback = p_data_cback;
    status = phNxpNciHal_MinOpen();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("phNxpNciHal_MinOpen failed");
      goto clean_and_return;
    } /*else its already in MIN_OPEN state. continue with rest of
         functionality*/
  } else {
    nxpncihal_ctrl.p_nfc_stack_cback = p_cback;
    nxpncihal_ctrl.p_nfc_stack_data_cback = p_data_cback;
  }
  /* Call open complete */
  phNxpNciHal_open_complete(wConfigStatus);

  return wConfigStatus;

clean_and_return:
  CONCURRENCY_UNLOCK();
  /* Report error status */
  if (p_cback != NULL) {
    (*p_cback)(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
  }

  nxpncihal_ctrl.p_nfc_stack_cback = NULL;
  nxpncihal_ctrl.p_nfc_stack_data_cback = NULL;
  phNxpNciHal_cleanup_monitor();
  nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
  return NFCSTATUS_FAILED;
}

/******************************************************************************
 * Function         phNxpNciHal_fw_mw_check
 *
 * Description      This function inform the status of phNxpNciHal_fw_mw_check
 *                  function to libnfc-nci.
 *
 * Returns          int.
 *
 ******************************************************************************/
int phNxpNciHal_fw_mw_ver_check() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t rom_version = 0xFF & (wFwVerRsp >> 16);
  uint8_t fw_maj_ver = 0xFF & (wFwVerRsp >> 8);

  switch (nfcFL.chipType) {
    case pn557:
      if ((rom_version == FW_MOBILE_ROM_VERSION_PN557) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_PN557))
        status = NFCSTATUS_SUCCESS;
      break;
    case pn80T:
      /* PN553 & PN80T have same rom & fw major version */
      [[fallthrough]];
    case pn553:
      if ((rom_version == FW_MOBILE_ROM_VERSION_PN553) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_PN553))
        status = NFCSTATUS_SUCCESS;
      break;
    case pn67T:
      /* PN551 & PN67T have same rom & fw major version */
      [[fallthrough]];
    case pn551:
      if ((rom_version == FW_MOBILE_ROM_VERSION_PN551) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_PN551))
        status = NFCSTATUS_SUCCESS;
      break;
    case sn100u:
      if ((rom_version == FW_MOBILE_ROM_VERSION_SN100U) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_SN100U))
        status = NFCSTATUS_SUCCESS;
      break;
    case sn220u:
      if ((rom_version == FW_MOBILE_ROM_VERSION_SN220U) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_SN220U))
        status = NFCSTATUS_SUCCESS;
      break;
    case sn300u:
      if ((rom_version == FW_MOBILE_ROM_VERSION_SN300U) &&
          (fw_maj_ver == FW_MOBILE_MAJOR_NUMBER_SN300U))
        status = NFCSTATUS_SUCCESS;
      break;
    default:
      status = NFCSTATUS_FAILED;
  }
  if (NFCSTATUS_SUCCESS != status) {
    NXPLOG_NCIHAL_D("Chip Version Middleware Version mismatch!!!!");
  }
  return status;
}
/******************************************************************************
 * Function         phNxpNciHal_MinOpen_complete
 *
 * Description      This function updates the status of
 *phNxpNciHal_MinOpen_complete to halstatus.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_MinOpen_complete(NFCSTATUS status) {
  gsIsFirstHalMinOpen = false;
  if (status == NFCSTATUS_SUCCESS) {
    nxpncihal_ctrl.halStatus = HAL_STATUS_MIN_OPEN;
  }

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_open_complete
 *
 * Description      This function inform the status of phNxpNciHal_open
 *                  function to libnfc-nci.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_open_complete(NFCSTATUS status) {
  static phLibNfc_Message_t msg;

  if (status == NFCSTATUS_SUCCESS) {
    msg.eMsgType = NCI_HAL_OPEN_CPLT_MSG;
    nxpncihal_ctrl.hal_open_status = true;
    nxpncihal_ctrl.halStatus = HAL_STATUS_OPEN;
  } else {
    msg.eMsgType = NCI_HAL_ERROR_MSG;
  }

  msg.pMsgData = NULL;
  msg.Size = 0;

  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                        (phLibNfc_Message_t*)&msg);

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_write
 *
 * Description      This function write the data to NFCC through physical
 *                  interface (e.g. I2C) using the PN54X driver interface.
 *                  Before sending the data to NFCC, phNxpNciHal_write_ext
 *                  is called to check if there is any extension processing
 *                  is required for the NCI packet being sent out.
 *
 * Returns          It returns number of bytes successfully written to NFCC.
 *
 ******************************************************************************/
int phNxpNciHal_write(uint16_t data_len, const uint8_t* p_data) {
  if (bEnableMfcExtns && p_data[NCI_GID_INDEX] == 0x00) {
    return NxpMfcReaderInstance.Write(data_len, p_data);
  } else if (phNxpNciHal_isVendorSpecificCommand(data_len, p_data)) {
    return phNxpNciHal_handleVendorSpecificCommand(data_len, p_data);
  }
  return phNxpNciHal_write_internal(data_len, p_data);
}

/******************************************************************************
 * Function         phNxpNciHal_write_internal
 *
 * Description      This function write the data to NFCC through physical
 *                  interface (e.g. I2C) using the PN54X driver interface.
 *                  Before sending the data to NFCC, phNxpNciHal_write_ext
 *                  is called to check if there is any extension processing
 *                  is required for the NCI packet being sent out.
 *
 * Returns          It returns number of bytes successfully written to NFCC.
 *
 ******************************************************************************/
int phNxpNciHal_write_internal(uint16_t data_len, const uint8_t* p_data) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  static phLibNfc_Message_t msg;
  if (nxpncihal_ctrl.halStatus != HAL_STATUS_OPEN) {
    return NFCSTATUS_FAILED;
  }
  if ((data_len + MAX_NXP_HAL_EXTN_BYTES) > NCI_MAX_DATA_LEN) {
    NXPLOG_NCIHAL_D("cmd_len exceeds limit NCI_MAX_DATA_LEN");
    android_errorWriteLog(0x534e4554, "121267042");
    goto clean_and_return;
  }

  CONCURRENCY_LOCK();
  /* Create local copy of cmd_data */
  memcpy(nxpncihal_ctrl.p_cmd_data, p_data, data_len);
  nxpncihal_ctrl.cmd_len = data_len;

  /* Check for NXP ext before sending write */
  status =
      phNxpNciHal_write_ext(&nxpncihal_ctrl.cmd_len, nxpncihal_ctrl.p_cmd_data,
                            &nxpncihal_ctrl.rsp_len, nxpncihal_ctrl.p_rsp_data);
  if (status != NFCSTATUS_SUCCESS) {
    /* Do not send packet to PN54X, send response directly */
    msg.eMsgType = NCI_HAL_RX_MSG;
    msg.pMsgData = NULL;
    msg.Size = 0;

    phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                          (phLibNfc_Message_t*)&msg);
    goto clean_and_return;
  }

  data_len = phNxpNciHal_write_unlocked(nxpncihal_ctrl.cmd_len,
                                        nxpncihal_ctrl.p_cmd_data, ORIG_LIBNFC);

  if (IS_CHIP_TYPE_L(sn100u) && IS_CHIP_TYPE_NE(pn557) && icode_send_eof == 1) {
    usleep(10000);
    icode_send_eof = 2;
    status = phNxpNciHal_send_ext_cmd(3, cmd_icode_eof);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("ICODE end of frame command failed");
    }
  }

clean_and_return:
  /* No data written */
  CONCURRENCY_UNLOCK();
  return data_len;
}

/******************************************************************************
 * Function         phNxpNciHal_write_unlocked
 *
 * Description      This is the actual function which is being called by
 *                  phNxpNciHal_write. This function writes the data to NFCC.
 *                  It waits till write callback provide the result of write
 *                  process.
 *
 * Returns          It returns number of bytes successfully written to NFCC.
 *
 ******************************************************************************/
int phNxpNciHal_write_unlocked(uint16_t data_len, const uint8_t* p_data,
                               int origin) {
  NFCSTATUS status = NFCSTATUS_INVALID_PARAMETER;
  phNxpNciHal_Sem_t cb_data;
  nxpncihal_ctrl.retry_cnt = 0;
  int sem_val = 0;
  static uint8_t reset_ntf[] = {0x60, 0x00, 0x06, 0xA0, 0x00,
                                0xC7, 0xD4, 0x00, 0x00};
  /* Create the local semaphore */
  if (phNxpNciHal_init_cb_data(&cb_data, NULL) != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("phNxpNciHal_write_unlocked Create cb data failed");
    data_len = 0;
    goto clean_and_return;
  }

  /* Create local copy of cmd_data */
  memcpy(nxpncihal_ctrl.p_cmd_data, p_data, data_len);
  nxpncihal_ctrl.cmd_len = data_len;
  write_unlocked_status = NFCSTATUS_FAILED;
  /* check for write synchronyztion */
  if (phNxpNciHal_check_ncicmd_write_window(nxpncihal_ctrl.cmd_len,
                                            nxpncihal_ctrl.p_cmd_data) !=
      NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("phNxpNciHal_write_unlocked  CMD window  check failed");
    data_len = 0;
    goto clean_and_return;
  }

  if (origin == ORIG_NXPHAL) HAL_ENABLE_EXT();

retry:

  data_len = nxpncihal_ctrl.cmd_len;
  if (!phNxpTempMgr::GetInstance().IsICTempOk())
    phNxpTempMgr::GetInstance().Wait();

  status = phTmlNfc_Write(
      (uint8_t*)nxpncihal_ctrl.p_cmd_data, (uint16_t)nxpncihal_ctrl.cmd_len,
      (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_write_complete,
      (void*)&cb_data);
  if (status != NFCSTATUS_PENDING) {
    NXPLOG_NCIHAL_E("write_unlocked status error");
    data_len = 0;
    goto clean_and_return;
  }

  /* Wait for callback response */
  if (SEM_WAIT(cb_data)) {
    NXPLOG_NCIHAL_E("write_unlocked semaphore error");
    data_len = 0;
    goto clean_and_return;
  }

  if (cb_data.status != NFCSTATUS_SUCCESS) {
    data_len = 0;
    if (nxpncihal_ctrl.retry_cnt++ < MAX_RETRY_COUNT) {
      NXPLOG_NCIHAL_D(
          "write_unlocked failed - PN54X Maybe in Standby Mode - Retry");
      /* 10ms delay to give NFCC wake up delay */
      usleep(1000 * 10);
      goto retry;
    } else {
      NXPLOG_NCIHAL_E(
          "write_unlocked failed - PN54X Maybe in Standby Mode (max count = "
          "0x%x)",
          nxpncihal_ctrl.retry_cnt);

      status = phTmlNfc_IoCtl(phTmlNfc_e_ResetDevice);

      if (NFCSTATUS_SUCCESS == status) {
        NXPLOG_NCIHAL_D("PN54X Reset - SUCCESS\n");
      } else {
        NXPLOG_NCIHAL_D("PN54X Reset - FAILED\n");
      }
      if (nxpncihal_ctrl.p_nfc_stack_data_cback != NULL &&
          nxpncihal_ctrl.hal_open_status == true) {
        if (nxpncihal_ctrl.p_rx_data != NULL) {
          NXPLOG_NCIHAL_D(
              "Send the Core Reset NTF to upper layer, which will trigger the "
              "recovery\n");
          // Send the Core Reset NTF to upper layer, which will trigger the
          // recovery.
          abort();
          nxpncihal_ctrl.rx_data_len = sizeof(reset_ntf);
          memcpy(nxpncihal_ctrl.p_rx_data, reset_ntf, sizeof(reset_ntf));
          (*nxpncihal_ctrl.p_nfc_stack_data_cback)(nxpncihal_ctrl.rx_data_len,
                                                   nxpncihal_ctrl.p_rx_data);
        } else {
          (*nxpncihal_ctrl.p_nfc_stack_data_cback)(0x00, NULL);
        }
        write_unlocked_status = NFCSTATUS_FAILED;
      }
    }
  } else {
    write_unlocked_status = NFCSTATUS_SUCCESS;
  }

clean_and_return:
  if (write_unlocked_status == NFCSTATUS_FAILED) {
    sem_getvalue(&(nxpncihal_ctrl.syncSpiNfc), &sem_val);
    if (((nxpncihal_ctrl.p_cmd_data[0] & NCI_MT_MASK) == NCI_MT_CMD) &&
        sem_val == 0) {
      sem_post(&(nxpncihal_ctrl.syncSpiNfc));
      NXPLOG_NCIHAL_D("HAL write  failed CMD window check releasing \n");
    }
  }
  phNxpNciHal_cleanup_cb_data(&cb_data);
  return data_len;
}

/******************************************************************************
 * Function         phNxpNciHal_write_complete
 *
 * Description      This function handles write callback.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_write_complete(void* pContext,
                                       phTmlNfc_TransactInfo_t* pInfo) {
  phNxpNciHal_Sem_t* p_cb_data = (phNxpNciHal_Sem_t*)pContext;
  if (pInfo->wStatus == NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("write successful status = 0x%x", pInfo->wStatus);
  } else {
    NXPLOG_NCIHAL_D("write error status = 0x%x", pInfo->wStatus);
  }

  p_cb_data->status = pInfo->wStatus;

  SEM_POST(p_cb_data);

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_read_complete
 *
 * Description      This function is called whenever there is an NCI packet
 *                  received from NFCC. It could be RSP or NTF packet. This
 *                  function provide the received NCI packet to libnfc-nci
 *                  using data callback of libnfc-nci.
 *                  There is a pending read called from each
 *                  phNxpNciHal_read_complete so each a packet received from
 *                  NFCC can be provide to libnfc-nci.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_read_complete(void* pContext,
                                      phTmlNfc_TransactInfo_t* pInfo) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  int sem_val;
  UNUSED_PROP(pContext);
  if (nxpncihal_ctrl.read_retry_cnt == 1) {
    nxpncihal_ctrl.read_retry_cnt = 0;
  }
  if (pInfo->wStatus == NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("read successful status = 0x%x", pInfo->wStatus);

    /*Check the Omapi command response and store in dedicated buffer to solve
     * sync issue*/
    if (IS_CHIP_TYPE_LE(sn100u) && pInfo->wLength > 2 &&
        pInfo->pBuff[0] == 0x4F && pInfo->pBuff[1] == 0x01 &&
        pInfo->pBuff[2] == 0x01) {
      nxpncihal_ctrl.p_rx_ese_data = pInfo->pBuff;
      nxpncihal_ctrl.rx_ese_data_len = pInfo->wLength;
      SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
    } else {
      nxpncihal_ctrl.p_rx_data = pInfo->pBuff;
      nxpncihal_ctrl.rx_data_len = pInfo->wLength;
      status = phNxpNciHal_process_ext_rsp(nxpncihal_ctrl.p_rx_data,
                                           &nxpncihal_ctrl.rx_data_len);
      if (nxpncihal_ctrl.hal_ext_enabled && phTmlNfc_IsFwDnldModeEnabled()) {
        SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
      }
    }
    phNxpNciHal_print_res_status(pInfo->pBuff, &pInfo->wLength);
    if (nxpncihal_ctrl.power_reset_triggered == true) {
      nxpncihal_ctrl.power_reset_triggered = false;
    }

    /* Check if response should go to hal module only */
    if (nxpncihal_ctrl.hal_ext_enabled == TRUE &&
        (nxpncihal_ctrl.p_rx_data[0x00] & NCI_MT_MASK) == NCI_MT_RSP) {
      if (status == NFCSTATUS_FAILED) {
        NXPLOG_NCIHAL_D("enter into NFCC init recovery");
        nxpncihal_ctrl.ext_cb_data.status = status;
      }
      /* Unlock semaphore only for responses*/
      if ((nxpncihal_ctrl.p_rx_data[0x00] & NCI_MT_MASK) == NCI_MT_RSP ||
          (IS_CHIP_TYPE_L(sn100u) && (icode_detected == true) &&
           (icode_send_eof == 3))) {
        /* Unlock semaphore */
        SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
      }
    }  // Notification Checking
    else if ((nxpncihal_ctrl.hal_ext_enabled == TRUE) &&
             ((nxpncihal_ctrl.p_rx_data[0x00] & NCI_MT_MASK) == NCI_MT_NTF) &&
             ((nxpncihal_ctrl.p_cmd_data[0x00] & NCI_GID_MASK) ==
              (nxpncihal_ctrl.p_rx_data[0x00] & NCI_GID_MASK)) &&
             ((nxpncihal_ctrl.p_cmd_data[0x01] & NCI_OID_MASK) ==
              (nxpncihal_ctrl.p_rx_data[0x01] & NCI_OID_MASK)) &&
             (nxpncihal_ctrl.nci_info.wait_for_ntf == TRUE)) {
      /* Unlock semaphore waiting for only  ntf*/
      nxpncihal_ctrl.nci_info.wait_for_ntf = FALSE;
      SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
    } else if (!sendRspToUpperLayer &&
               (nxpncihal_ctrl.p_rx_data[0x00] == 0x00)) {
      sendRspToUpperLayer = true;
      NFCSTATUS mfcRspStatus = NxpMfcReaderInstance.CheckMfcResponse(
          nxpncihal_ctrl.p_rx_data, nxpncihal_ctrl.rx_data_len);
      NXPLOG_NCIHAL_D("Mfc Response Status = 0x%x", mfcRspStatus);
      SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
    }
    /* Read successful send the event to higher layer */
    else if ((nxpncihal_ctrl.p_nfc_stack_data_cback != NULL) &&
             (status == NFCSTATUS_SUCCESS)) {
      NxpMfcReaderInstance.MfcNotifyOnAckReceived(nxpncihal_ctrl.p_rx_data);
      (*nxpncihal_ctrl.p_nfc_stack_data_cback)(nxpncihal_ctrl.rx_data_len,
                                               nxpncihal_ctrl.p_rx_data);
      // workaround for sync issue between SPI and NFC
      if (IS_CHIP_TYPE_EQ(pn557) && nxpncihal_ctrl.p_rx_data[0] == 0x62 &&
          nxpncihal_ctrl.p_rx_data[1] == 0x00 &&
          nxpncihal_ctrl.p_rx_data[3] == 0xC0 &&
          nxpncihal_ctrl.p_rx_data[4] == 0x00) {
        uint8_t nfcee_notifiations[3][9] = {
            {0x61, 0x0A, 0x06, 0x01, 0x00, 0x03, 0xC0, 0x80, 0x04},
            {0x61, 0x0A, 0x06, 0x01, 0x00, 0x03, 0xC0, 0x81, 0x04},
            {0x61, 0x0A, 0x06, 0x01, 0x00, 0x03, 0xC0, 0x82, 0x03},
        };

        for (int i = 0; i < 3; i++) {
          (*nxpncihal_ctrl.p_nfc_stack_data_cback)(
              sizeof(nfcee_notifiations[i]), nfcee_notifiations[i]);
        }
      }
    }
    /* Unblock next Write Command Window */
    sem_getvalue(&(nxpncihal_ctrl.syncSpiNfc), &sem_val);
    if (((pInfo->pBuff[0] & NCI_MT_MASK) == NCI_MT_RSP) && sem_val == 0) {
      sem_post(&(nxpncihal_ctrl.syncSpiNfc));
    }
  } else {
    NXPLOG_NCIHAL_E("read error status = 0x%x", pInfo->wStatus);
  }

  if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE &&
      (nxpncihal_ctrl.p_cmd_data[0x00] & NCI_GID_MASK) ==
          (nxpncihal_ctrl.p_rx_data[0x00] & NCI_GID_MASK) &&
      (nxpncihal_ctrl.p_cmd_data[0x01] & NCI_OID_MASK) ==
          (nxpncihal_ctrl.p_rx_data[0x01] & NCI_OID_MASK) &&
      nxpncihal_ctrl.nci_info.wait_for_ntf == FALSE) {
    NXPLOG_NCIHAL_D(" Ignoring read , HAL close triggered");
    return;
  }
  /* Read again because read must be pending always except FWDNLD.*/
  if (!phTmlNfc_IsFwDnldModeEnabled()) {
    status = phTmlNfc_Read(
        nxpncihal_ctrl.p_rsp_data, NCI_MAX_DATA_LEN,
        (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_read_complete, NULL);
    if (status != NFCSTATUS_PENDING) {
      NXPLOG_NCIHAL_E("read status error status = %x", status);
      /* TODO: Not sure how to handle this ? */
    }
  }
  return;
}

/******************************************************************************
 * Function         phNxpNciHal_enableTmlRead
 *
 * Description      Invokes TmlNfc Read to make sure always read thread is
 *                  pending
 *
 * Returns          Returns read status
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_enableTmlRead() {
  /* Read again because read must be pending always.*/
  NFCSTATUS status = phTmlNfc_Read(
      nxpncihal_ctrl.p_rsp_data, NCI_MAX_DATA_LEN,
      (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_read_complete, NULL);
  if (status != NFCSTATUS_PENDING) {
    NXPLOG_NCIHAL_E("read status error status = %x", status);
  }
  return status;
}
/******************************************************************************
 * Function         phNxpNciHal_core_initialized
 *
 * Description      This function is called by libnfc-nci after successful open
 *                  of NFCC. All proprietary setting for PN54X are done here.
 *                  After completion of proprietary settings notification is
 *                  provided to libnfc-nci through callback function.
 *
 * Returns          Always returns NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_core_initialized(uint16_t core_init_rsp_params_len,
                                 uint8_t* p_core_init_rsp_params) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint8_t* buffer = NULL;
  uint8_t isfound = 0;
  uint8_t fw_dwnld_flag = false;
  uint8_t setConfigAlways = false;

  uint8_t swp_full_pwr_mode_on_cmd[] = {0x20, 0x02, 0x05, 0x01,
                                        0xA0, 0xF1, 0x01, 0x01};
  uint8_t enable_ce_in_phone_off = 0x01;
  uint8_t enable_ven_cfg = 0x01;

  uint8_t swp_switch_timeout_cmd[] = {0x20, 0x02, 0x06, 0x01, 0xA0,
                                      0xF3, 0x02, 0x00, 0x00};

  config_success = true;
  long bufflen = 260;
  long retlen = 0;
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
#if (NFC_NXP_HFO_SETTINGS == TRUE)
  /* Temp fix to re-apply the proper clock setting */
  int temp_fix = 1;
#endif
  unsigned long num = 0;
  /*initialize recovery FW variables*/
  gRecFwRetryCount = 0;
  gRecFWDwnld = 0;
  // recovery --start
  /*NCI_INIT_CMD*/
  static uint8_t cmd_init_nci[] = {0x20, 0x01, 0x00};
  /*NCI_RESET_CMD*/
  static uint8_t cmd_reset_nci[] = {0x20, 0x00, 0x01,
                                    0x00};  // keep configuration
  static uint8_t cmd_init_nci2_0[] = {0x20, 0x01, 0x02, 0x00, 0x00};
  /* reset config cache */
  uint8_t retry_core_init_cnt = 0;
  if (nxpncihal_ctrl.halStatus != HAL_STATUS_OPEN) {
    return NFCSTATUS_FAILED;
  }
  if (core_init_rsp_params_len >= 1 && (*p_core_init_rsp_params > 0) &&
      (*p_core_init_rsp_params < 4))  // initializing for recovery.
  {
  retry_core_init:
    config_access = false;
    if (mGetCfg_info != NULL) {
      mGetCfg_info->isGetcfg = false;
    }
    if (buffer != NULL) {
      free(buffer);
      buffer = NULL;
    }
    if (retry_core_init_cnt > 3) {
      return NFCSTATUS_FAILED;
    }
    if (IS_CHIP_TYPE_L(sn100u)) {
      status = phTmlNfc_IoCtl(phTmlNfc_e_ResetDevice);
      if (NFCSTATUS_SUCCESS == status) {
        NXPLOG_NCIHAL_D("PN54X Reset - SUCCESS\n");
      } else {
        NXPLOG_NCIHAL_D("PN54X Reset - FAILED\n");
      }
    }

    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci), cmd_reset_nci);
    if ((status != NFCSTATUS_SUCCESS) &&
        (nxpncihal_ctrl.retry_cnt >= MAX_RETRY_COUNT)) {
      NXPLOG_NCIHAL_E("Force FW Download, NFCC not coming out from Standby");
      retry_core_init_cnt++;
      goto retry_core_init;
    } else if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("NCI_CORE_RESET: Failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }

    if (nxpncihal_ctrl.nci_info.nci_version == NCI_VERSION_2_0) {
      status =
          phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci2_0), cmd_init_nci2_0);
    } else {
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci), cmd_init_nci);
    }
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("NCI_CORE_INIT : Failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
  }
  // recovery --end

  buffer = (uint8_t*)malloc(bufflen * sizeof(uint8_t));
  if (NULL == buffer) {
    return NFCSTATUS_FAILED;
  }
  config_access = true;
  retlen = 0;
  isfound = GetNxpByteArrayValue(NAME_NXP_ACT_PROP_EXTN, (char*)buffer, bufflen,
                                 &retlen);
  if (isfound > 0 && retlen > 0) {
    /* NXP ACT Proprietary Ext */
    status = phNxpNciHal_send_ext_cmd(retlen, buffer);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("NXP ACT Proprietary Ext failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
  }
  if (IS_CHIP_TYPE_EQ(sn100u)) {
    uint8_t cmd_get_cfg_dbg_info[] = {0x20, 0x03, 0x0D, 0x06, 0xA0, 0x39,
                                      0xA0, 0x1A, 0xA0, 0x1B, 0xA0, 0x1C,
                                      0xA0, 0x27, 0xA1, 0x1F};
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_get_cfg_dbg_info),
                                      cmd_get_cfg_dbg_info);
  } else if (IS_CHIP_TYPE_GE(sn220u) || IS_CHIP_TYPE_EQ(pn557)) {
    uint8_t cmd_get_cfg_dbg_info[] = {0x20, 0x03, 0x0B, 0x05, 0xA0, 0x39, 0xA0,
                                      0x1A, 0xA0, 0x1B, 0xA0, 0x1C, 0xA0, 0x27};
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_get_cfg_dbg_info),
                                      cmd_get_cfg_dbg_info);
  }
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Failed to retrieve NFCC debug info");
  }

  if (IS_CHIP_TYPE_GE(sn220u)) {
    uint8_t cmd_get_hard_fault_ctr_info[] = {0x20, 0x03, 0x03,
                                             0x01, 0xA1, 0x5A};
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_get_hard_fault_ctr_info),
                                      cmd_get_hard_fault_ctr_info);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to retrieve NFCC hard fault counter debug info");
    }
  }

  num = 0;
  if (GetNxpNumValue("NXP_I3C_MODE", &num, sizeof(num))) {
    if (num == 1) {
      uint8_t coreStandBy[] = {0x2F, 0x00, 0x01, 0x00};
      NXPLOG_NCIHAL_E("Disable NFCC standby");
      status = phNxpNciHal_send_ext_cmd(sizeof(coreStandBy), coreStandBy);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Failed to set NFCC Standby Disabled");
      }
    }
  }

  status = phNxpNciHal_setAutonomousMode();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Set Autonomous enable: Failed");
    retry_core_init_cnt++;
    goto retry_core_init;
  }

  if (IS_CHIP_TYPE_EQ(pn557)) enable_ven_cfg = PN557_VEN_CFG_DEFAULT;

  mEEPROM_info.buffer = &enable_ven_cfg;
  mEEPROM_info.bufflen = sizeof(uint8_t);
  mEEPROM_info.request_type = EEPROM_ENABLE_VEN_CFG;
  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  request_EEPROM(&mEEPROM_info);

  if (IS_CHIP_TYPE_GE(sn100u)) {
    mEEPROM_info.buffer = &enable_ce_in_phone_off;
    mEEPROM_info.bufflen = sizeof(enable_ce_in_phone_off);
    mEEPROM_info.request_type = EEPROM_CE_PHONE_OFF_CFG;
    mEEPROM_info.request_mode = SET_EEPROM_DATA;
    request_EEPROM(&mEEPROM_info);
  }

  phNxpNciHal_propConfULPDetMode(false);

  if (gPowerTrackerHandle.start != NULL) {
    gPowerTrackerHandle.start(gPowerTrackerHandle.pollDuration);
  }
  config_access = false;
  status = phNxpNciHal_read_fw_dw_status(fw_dwnld_flag);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s: NXP get FW DW Flag failed", __FUNCTION__);
  }
  fw_dwnld_flag |= (bool)fw_download_success;
  if (fw_dwnld_flag == true) {
    phNxpNciHal_hci_network_reset();
  }
  if (IS_CHIP_TYPE_L(sn100u)) {
    // Check if firmware download success
    status = phNxpNciHal_get_mw_eeprom();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("NXP GET MW EEPROM AREA Proprietary Ext failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
  }

  config_access = true;
  setConfigAlways = false;
  isfound = GetNxpNumValue(NAME_NXP_SET_CONFIG_ALWAYS, &num, sizeof(num));
  if (isfound > 0) {
    setConfigAlways = num;
  }
  NXPLOG_NCIHAL_D("EEPROM_fw_dwnld_flag : 0x%02x SetConfigAlways flag : 0x%02x",
                  fw_dwnld_flag, setConfigAlways);

  if (isNxpConfigModified() || (fw_dwnld_flag == true)) {
    retlen = 0;
    fw_download_success = 0;

    /* EEPROM access variables */
    mEEPROM_info.request_mode = GET_EEPROM_DATA;
    retlen = 0;
    memset(buffer, 0x00, bufflen);
    isfound = GetNxpByteArrayValue(NAME_NXP_AUTH_TIMEOUT_CFG, (char*)buffer,
                                   bufflen, &retlen);

    if ((isfound > 0) && (retlen > 0)) {
      uint64_t auth_timeout_buffer_length;
      if (IS_CHIP_TYPE_GE(sn100u)) {
        auth_timeout_buffer_length = SNXXX_NXP_AUTH_TIMEOUT_BUF_LEN;
      } else {
        auth_timeout_buffer_length = PN557_NXP_AUTH_TIMEOUT_BUF_LEN;
      }
      uint8_t auth_timeout_buffer[auth_timeout_buffer_length];
      memcpy(&auth_timeout_buffer, buffer, auth_timeout_buffer_length);
      mEEPROM_info.request_mode = SET_EEPROM_DATA;
      mEEPROM_info.buffer = auth_timeout_buffer;
      mEEPROM_info.bufflen = auth_timeout_buffer_length;
      mEEPROM_info.request_type = EEPROM_AUTH_CMD_TIMEOUT;
      status = request_EEPROM(&mEEPROM_info);
      if (NFCSTATUS_SUCCESS == status) {
        memcpy(&mGetCfg_info->auth_cmd_timeout, mEEPROM_info.buffer,
               mEEPROM_info.bufflen);
        mGetCfg_info->auth_cmd_timeoutlen = mEEPROM_info.bufflen;
      }
    }
    NXPLOG_NCIHAL_D("Performing TVDD Settings");
    isfound = GetNxpNumValue(NAME_NXP_EXT_TVDD_CFG, &num, sizeof(num));
    if (isfound > 0) {
      if (num == 1) {
        isfound = GetNxpByteArrayValue(NAME_NXP_EXT_TVDD_CFG_1, (char*)buffer,
                                       bufflen, &retlen);
        if (isfound && retlen > 0) {
          status = phNxpNciHal_send_ext_cmd(retlen, buffer);
          if (status != NFCSTATUS_SUCCESS) {
            NXPLOG_NCIHAL_E("EXT TVDD CFG 1 Settings failed");
            retry_core_init_cnt++;
            goto retry_core_init;
          }
        }
      } else if (num == 2) {
        isfound = GetNxpByteArrayValue(NAME_NXP_EXT_TVDD_CFG_2, (char*)buffer,
                                       bufflen, &retlen);
        if (isfound && retlen > 0) {
          status = phNxpNciHal_send_ext_cmd(retlen, buffer);
          if (status != NFCSTATUS_SUCCESS) {
            NXPLOG_NCIHAL_E("EXT TVDD CFG 2 Settings failed");
            retry_core_init_cnt++;
            goto retry_core_init;
          }
        }
      } else if (num == 3) {
        isfound = GetNxpByteArrayValue(NAME_NXP_EXT_TVDD_CFG_3, (char*)buffer,
                                       bufflen, &retlen);
        if (isfound && retlen > 0) {
          status = phNxpNciHal_send_ext_cmd(retlen, buffer);
          if (status != NFCSTATUS_SUCCESS) {
            NXPLOG_NCIHAL_E("EXT TVDD CFG 3 Settings failed");
            retry_core_init_cnt++;
            goto retry_core_init;
          }
        }
      } else {
        NXPLOG_NCIHAL_E("Wrong Configuration Value %ld", num);
      }
    }
  }
  if ((true == fw_dwnld_flag) || (true == setConfigAlways) ||
      isNxpConfigModified()) {
    config_access = true;

    if (IS_CHIP_TYPE_NE(pn547C2)) {
      config_access = true;
    }

    retlen = 0;
    /*Select UICC2/UICC3 SWP line from config param*/
    if (GetNxpNumValue(NAME_NXP_DEFAULT_UICC2_SELECT, (void*)&retlen,
                       sizeof(retlen))) {
      if (retlen > 0) phNxpNciHal_enableDefaultUICC2SWPline((uint8_t)retlen);
    }
    status = phNxpNciHal_setExtendedFieldMode();
    if (status != NFCSTATUS_SUCCESS &&
        status != NFCSTATUS_FEATURE_NOT_SUPPORTED) {
      NXPLOG_NCIHAL_E("phNxpNciHal_setExtendedFieldMode failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
    status = phNxpNciHal_setGuardTimer();
    if (status != NFCSTATUS_SUCCESS &&
        status != NFCSTATUS_FEATURE_NOT_SUPPORTED) {
      NXPLOG_NCIHAL_E("phNxpNciHal_setGuardTimer failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
#if (NXP_SRD == TRUE)
    status = phNxpNciHal_setSrdtimeout();
    if (status != NFCSTATUS_SUCCESS &&
        status != NFCSTATUS_FEATURE_NOT_SUPPORTED) {
      NXPLOG_NCIHAL_E("phNxpNciHal_setSrdtimeout failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
#endif
    config_access = true;
    retlen = 0;
    NXPLOG_NCIHAL_D("Performing ndef nfcee config settings");
    uint8_t cmd_t4t_nfcee_cfg;

    if (!GetNxpNumValue(NAME_NXP_T4T_NFCEE_ENABLE, (void*)&retlen,
                        sizeof(retlen))) {
      retlen = 0x00;
      NXPLOG_NCIHAL_D(
          "T4T_NFCEE_ENABLE not found. Taking default value : 0x%02lx", retlen);
    }
    cmd_t4t_nfcee_cfg = (uint8_t)retlen;
    mEEPROM_info.buffer = &cmd_t4t_nfcee_cfg;
    mEEPROM_info.bufflen = sizeof(cmd_t4t_nfcee_cfg);
    mEEPROM_info.request_type = EEPROM_T4T_NFCEE_ENABLE;
    mEEPROM_info.request_mode = SET_EEPROM_DATA;
    request_EEPROM(&mEEPROM_info);
    if (IS_CHIP_TYPE_GE(sn100u)) {
      if (phNxpNciHal_configure_merge_sak() != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Applying iso_dep sak merge settings failed");
      }
    }
  }
  if ((true == fw_dwnld_flag) || (true == setConfigAlways) ||
      isNxpConfigModified() || (wRfUpdateReq == true)) {
    retlen = 0;
    NXPLOG_NCIHAL_D("Performing NAME_NXP_CORE_CONF_EXTN Settings");
    isfound = GetNxpByteArrayValue(NAME_NXP_CORE_CONF_EXTN, (char*)buffer,
                                   bufflen, &retlen);
    if (isfound > 0 && retlen > 0) {
      /* NXP ACT Proprietary Ext */
      status = phNxpNciHal_send_ext_cmd(retlen, buffer);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("NXP Core configuration failed");
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    }

    NXPLOG_NCIHAL_D("Performing SE Settings");
    phNxpNciHal_read_and_update_se_state();

    NXPLOG_NCIHAL_D("Performing NAME_NXP_CORE_CONF Settings");
    retlen = 0;
    isfound = GetNxpByteArrayValue(NAME_NXP_CORE_CONF, (char*)buffer, bufflen,
                                   &retlen);
    if (isfound > 0 && retlen > 0) {
      /* NXP ACT Proprietary Ext */
      status = phNxpNciHal_send_ext_cmd(retlen, buffer);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Core Set Config failed");
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    }

    phNxpNciHal_setDCDCConfig();

    if (fpVerInfoStoreInEeprom != NULL) {
      fpVerInfoStoreInEeprom();
    }
  }
  config_access = false;
  if ((true == fw_dwnld_flag) || (true == setConfigAlways) ||
      isNxpRFConfigModified()) {
    unsigned long loopcnt = 0;

    do {
      char rf_conf_block[22] = {'\0'};
      strlcpy(rf_conf_block, rf_block_name, sizeof(rf_conf_block));
      retlen = 0;
      strlcat(rf_conf_block, rf_block_num[loopcnt++], sizeof(rf_conf_block));
      isfound =
          GetNxpByteArrayValue(rf_conf_block, (char*)buffer, bufflen, &retlen);
      if (isfound > 0 && retlen > 0) {
        NXPLOG_NCIHAL_D(" Performing RF Settings BLK %ld", loopcnt);
        status = phNxpNciHal_send_ext_cmd(retlen, buffer);

        if (status == NFCSTATUS_SUCCESS) {
          status = phNxpNciHal_CheckRFCmdRespStatus();
          /*STATUS INVALID PARAM 0x09*/
          if (status == 0x09) {
            phNxpNciHalRFConfigCmdRecSequence();
            retry_core_init_cnt++;
            goto retry_core_init;
          }
        } else if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("RF Settings BLK %ld failed", loopcnt);
          retry_core_init_cnt++;
          goto retry_core_init;
        }
      }
    } while (rf_block_num[loopcnt] != NULL);
    loopcnt = 0;
    if (phNxpNciHal_nfccClockCfgApply() != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("phNxpNciHal_nfccClockCfgApply failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
  }
  if (fpDoAntennaActivity != NULL) {
    fpDoAntennaActivity(ANTENNA_SET_VDDPA);
  }
  config_access = true;

  retlen = 0;
  if (IS_CHIP_TYPE_NE(pn547C2)) {
    config_access = false;
  }
  isfound = GetNxpByteArrayValue(NAME_NXP_CORE_RF_FIELD, (char*)buffer, bufflen,
                                 &retlen);
  if (isfound > 0 && retlen > 0) {
    /* NXP ACT Proprietary Ext */
    status = phNxpNciHal_send_ext_cmd(retlen, buffer);
    if (status == NFCSTATUS_SUCCESS) {
      status = phNxpNciHal_CheckRFCmdRespStatus();
      /*STATUS INVALID PARAM 0x09*/
      if (status == 0x09) {
        phNxpNciHalRFConfigCmdRecSequence();
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    } else if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Setting NXP_CORE_RF_FIELD status failed");
      retry_core_init_cnt++;
      goto retry_core_init;
    }
  }
  config_access = true;

  retlen = 0;
  /* NXP SWP switch timeout Setting*/
  if (GetNxpNumValue(NAME_NXP_SWP_SWITCH_TIMEOUT, (void*)&retlen,
                     sizeof(retlen))) {
    // Check the permissible range [0 - 60]
    if (0 <= retlen && retlen <= 60) {
      if (0 < retlen) {
        unsigned int timeout = (uint32_t)retlen * 1000;
        unsigned int timeoutHx = 0x0000;

        char tmpbuffer[10] = {0};
        snprintf((char*)tmpbuffer, 10, "%04x", timeout);
        int ret = sscanf((char*)tmpbuffer, "%x", &timeoutHx);
        if (!ret) timeoutHx = 0x0000;

        swp_switch_timeout_cmd[7] = (timeoutHx & 0xFF);
        swp_switch_timeout_cmd[8] = ((timeoutHx & 0xFF00) >> 8);
      }

      status = phNxpNciHal_send_ext_cmd(sizeof(swp_switch_timeout_cmd),
                                        swp_switch_timeout_cmd);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("SWP switch timeout Setting Failed");
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    } else {
      NXPLOG_NCIHAL_E("SWP switch timeout Setting Failed - out of range!");
    }
  }

  status = phNxpNciHal_china_tianjin_rf_setting();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("phNxpNciHal_china_tianjin_rf_setting failed");
    retry_core_init_cnt++;
    goto retry_core_init;
  }
  if (IS_CHIP_TYPE_L(sn100u)) {
    // Update eeprom value
    status = phNxpNciHal_set_mw_eeprom();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("NXP Update MW EEPROM Proprietary Ext failed");
    }
  }

  retlen = 0;
  config_access = false;

  retlen = 0;

  /* SWP FULL PWR MODE SETTING ON */
  if (GetNxpNumValue(NAME_NXP_SWP_FULL_PWR_ON, (void*)&retlen,
                     sizeof(retlen))) {
    if (1 == retlen) {
      status = phNxpNciHal_send_ext_cmd(sizeof(swp_full_pwr_mode_on_cmd),
                                        swp_full_pwr_mode_on_cmd);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("SWP FULL PWR MODE SETTING ON CMD FAILED");
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    } else {
      swp_full_pwr_mode_on_cmd[7] = 0x00;
      status = phNxpNciHal_send_ext_cmd(sizeof(swp_full_pwr_mode_on_cmd),
                                        swp_full_pwr_mode_on_cmd);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("SWP FULL PWR MODE SETTING OFF CMD FAILED");
        retry_core_init_cnt++;
        goto retry_core_init;
      }
    }
  }

  uint8_t gpioCtrl[3] = {0x00, 0x00, 0x00};
  long gpioCtrlLen = 0;
  isfound = GetNxpByteArrayValue(NAME_CONF_GPIO_CONTROL, (char*)gpioCtrl,
                                 sizeof(gpioCtrl), &gpioCtrlLen);
  if (isfound > 0 && gpioCtrlLen != 0) {
    phNxpNciHal_configGPIOControl(gpioCtrl, gpioCtrlLen);
  }
  phNxpNciHal_configureLxDebugMode();

  if (IS_CHIP_TYPE_EQ(pn557)) {
    if (GetNxpNumValue(NAME_NXP_PROP_CE_ACTION_NTF, (void*)&retlen,
                       sizeof(retlen))) {
      uint8_t value = (uint8_t)retlen;
      NXPLOG_NCIHAL_D("Prop CE ACT NTF %x", value);
      mEEPROM_info.buffer = &value;
      mEEPROM_info.bufflen = sizeof(value);
      mEEPROM_info.request_type = EEPROM_CE_ACT_NTF;
      mEEPROM_info.request_mode = SET_EEPROM_DATA;
      request_EEPROM(&mEEPROM_info);
    }
  }

  config_access = false;
  {
    if (isNxpRFConfigModified() || isNxpConfigModified() || fw_dwnld_flag ||
        setConfigAlways) {
      if (IS_CHIP_TYPE_GE(sn100u)) {
        status = phNxpNciHal_ext_send_sram_config_to_flash();
        if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("Updation of the SRAM contents failed");
        }
      }
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci), cmd_reset_nci);
      if (status == NFCSTATUS_SUCCESS) {
        if (nxpncihal_ctrl.nci_info.nci_version == NCI_VERSION_2_0) {
          status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci2_0),
                                            cmd_init_nci2_0);
        } else {
          status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci), cmd_init_nci);
        }
      }
    }
    if (status == NFCSTATUS_SUCCESS) {
      status = phNxpNciHal_restore_uicc_params();
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("%s: Restore UICC params failed", __FUNCTION__);
      }

      phNxpNciHal_prop_conf_rssi();

      fw_dwnld_flag = false;
      status = phNxpNciHal_write_fw_dw_status(fw_dwnld_flag);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("%s: NXP Set FW Download Flag failed", __FUNCTION__);
      }
      status = phNxpNciHal_send_get_cfgs();
      if (status == NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Send get Configs SUCCESS");
      } else {
        NXPLOG_NCIHAL_E("Send get Configs FAILED");
      }
    }
  }
  retry_core_init_cnt = 0;

  if (buffer) {
    free(buffer);
    buffer = NULL;
  }
  // initialize recovery FW variables
  gRecFWDwnld = 0;
  gRecFwRetryCount = 0;

  phNxpNciHal_core_initialized_complete(status);
  if (isNxpConfigModified()) {
    updateNxpConfigTimestamp();
  }
  if (isNxpRFConfigModified()) {
    updateNxpRfConfigTimestamp();
  }
  return NFCSTATUS_SUCCESS;
}
/******************************************************************************
 * Function         phNxpNciHal_CheckRFCmdRespStatus
 *
 * Description      This function is called to check the resp status of
 *                  RF update commands.
 *
 * Returns          NFCSTATUS_SUCCESS           if successful,
 *                  NFCSTATUS_INVALID_PARAMETER if parameter is inavlid
 *                  NFCSTATUS_FAILED            if failed response
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_CheckRFCmdRespStatus() {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  static uint16_t INVALID_PARAM = 0x09;
  if ((nxpncihal_ctrl.rx_data_len > 0) && (nxpncihal_ctrl.p_rx_data[2] > 0)) {
    if (nxpncihal_ctrl.p_rx_data[3] == 0x09) {
      status = INVALID_PARAM;
    } else if (nxpncihal_ctrl.p_rx_data[3] != NFCSTATUS_SUCCESS) {
      status = NFCSTATUS_FAILED;
    }
  }
  return status;
}
/******************************************************************************
 * Function         phNxpNciHalRFConfigCmdRecSequence
 *
 * Description      This function is called to handle recovery FW sequence
 *                  Whenever RF settings are failed to apply with invalid param
 *                  response, recovery mechanism includes recovery firmware
 *                  download followed by firmware download and then config
 *                  settings. The recovery firmware changes the major number of
 *                  the firmware inside NFCC.Then actual firmware dowenload will
 *                  be successful. This can be retried maximum three times.
 *
 * Returns          Always returns NFCSTATUS_SUCCESS
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHalRFConfigCmdRecSequence() {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint16_t recFWState = 1;
  gRecFWDwnld = true;
  gRecFwRetryCount++;
  if (gRecFwRetryCount > 0x03) {
    NXPLOG_NCIHAL_D("Max retry count for RF config FW recovery exceeded ");
    gRecFWDwnld = false;
    return NFCSTATUS_FAILED;
  }
  do {
    status = phTmlNfc_IoCtl(phTmlNfc_e_ResetDevice);
    phDnldNfc_InitImgInfo();
    if (NFCSTATUS_SUCCESS == phNxpNciHal_CheckValidFwVersion()) {
      status = phNxpNciHal_fw_download();
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("error in download = %x", status);
      }
      break;
    }
    gRecFWDwnld = false;
  } while (recFWState--);
  gRecFWDwnld = false;
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_core_initialized_complete
 *
 * Description      This function is called when phNxpNciHal_core_initialized
 *                  complete all proprietary command exchanges. This function
 *                  informs libnfc-nci about completion of core initialize
 *                  and result of that through callback.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_core_initialized_complete(NFCSTATUS status) {
  static phLibNfc_Message_t msg;

  if (status == NFCSTATUS_SUCCESS) {
    msg.eMsgType = NCI_HAL_POST_INIT_CPLT_MSG;
  } else {
    msg.eMsgType = NCI_HAL_ERROR_MSG;
  }
  msg.pMsgData = NULL;
  msg.Size = 0;

  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                        (phLibNfc_Message_t*)&msg);
  return;
}

/******************************************************************************
 * Function         phNxpNciHal_pre_discover
 *
 * Description      This function is called by libnfc-nci to perform any
 *                  proprietary exchange before RF discovery.
 *
 * Returns          It always returns NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_pre_discover(void) {
  /* Nothing to do here for initial version */
  // This is set to return Failed as no vendor specific pre-discovery action is
  // needed in case of HalPrediscover
  return NFCSTATUS_FAILED;
}

/******************************************************************************
 * Function         phNxpNciHal_release_info
 *
 * Description      This function frees allocated memory for mGetCfg_info
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_release_info(void) {
  NXPLOG_NCIHAL_D("phNxpNciHal_release_info mGetCfg_info");
  if (mGetCfg_info != NULL) {
    free(mGetCfg_info);
    mGetCfg_info = NULL;
  }
}
/******************************************************************************
 * Function         phNxpNciHal_close
 *
 * Description      This function close the NFCC interface and free all
 *                  resources.This is called by libnfc-nci on NFC service stop.
 *
 * Returns          Always return NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_close(bool bShutdown) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t cmd_ce_discovery_nci[10] = {
      0x21,
      0x03,
  };
  uint8_t cmd_reset_nci[] = {0x20, 0x00, 0x01, 0x00};
  uint8_t cmd_system_ese_power_cycle[] = {0x2F, 0x1E, 0x00};
  uint8_t cmd_ce_in_phone_off[] = {0x20, 0x02, 0x05, 0x01,
                                   0xA0, 0x8E, 0x01, 0x00};
  uint8_t cmd_ce_in_phone_off_pn557[] = {0x20, 0x02, 0x05, 0x01,
                                         0xA0, 0x07, 0x01, 0x02};
  uint8_t cmd_system_set_service_status[] = {0x2F, 0x01, 0x01, 0x00};
  uint8_t length = 0;
  uint8_t numPrms = 0;
  uint8_t ptr = 4;
  unsigned long uiccListenMask = 0x00;
  unsigned long eseListenMask = 0x00;
  uint8_t retry = 0;

  phNxpNciHal_deinitializeRegRfFwDnld();
  NfcHalAutoThreadMutex a(sHalFnLock);
  if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE) {
    NXPLOG_NCIHAL_D("phNxpNciHal_close is already closed, ignoring close");
    return NFCSTATUS_FAILED;
  }
  if (gPowerTrackerHandle.stop != NULL) {
    gPowerTrackerHandle.stop();
  }
  if (IS_CHIP_TYPE_L(sn100u)) {
    if (!(GetNxpNumValue(NAME_NXP_UICC_LISTEN_TECH_MASK, &uiccListenMask,
                         sizeof(uiccListenMask)))) {
      uiccListenMask = 0x07;
      NXPLOG_NCIHAL_D("UICC_LISTEN_TECH_MASK = 0x%0lX", uiccListenMask);
    }

    if (!(GetNxpNumValue(NAME_NXP_ESE_LISTEN_TECH_MASK, &eseListenMask,
                         sizeof(eseListenMask)))) {
      eseListenMask = 0x07;
      NXPLOG_NCIHAL_D("NXP_ESE_LISTEN_TECH_MASK = 0x%0lX", eseListenMask);
    }
  }

  CONCURRENCY_LOCK();
  int sem_val;
  sem_getvalue(&(nxpncihal_ctrl.syncSpiNfc), &sem_val);
  if (sem_val == 0) {
    sem_post(&(nxpncihal_ctrl.syncSpiNfc));
  }
  if (!bShutdown && phNxpNciHal_getULPDetFlag() == false) {
    if (IS_CHIP_TYPE_GE(sn100u)) {
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_ce_in_phone_off),
                                        cmd_ce_in_phone_off);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("CMD_CE_IN_PHONE_OFF: Failed");
      }
      config_ext.autonomous_mode = 0x00;
      status = phNxpNciHal_setAutonomousMode();
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Autonomous mode Disable: Failed");
      }
    } else {
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_ce_in_phone_off_pn557),
                                        cmd_ce_in_phone_off_pn557);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("CMD_CE_IN_PHONE_OFF: Failed");
      }
    }
  }
  if (nfcFL.nfccFL._NFCC_I2C_READ_WRITE_IMPROVEMENT &&
      read_failed_disable_nfc) {
    read_failed_disable_nfc = false;
    goto close_and_return;
  }

  if (write_unlocked_status == NFCSTATUS_FAILED) {
    NXPLOG_NCIHAL_D("phNxpNciHal_close i2c write failed .Clean and Return");
    goto close_and_return;
  }

  if ((!bShutdown) && IS_CHIP_TYPE_L(sn100u)) {
    if ((uiccListenMask & 0x1) == 0x01 || (eseListenMask & 0x1) == 0x01) {
      NXPLOG_NCIHAL_D("phNxpNciHal_close (): Adding A passive listen");
      numPrms++;
      cmd_ce_discovery_nci[ptr++] = 0x80;
      cmd_ce_discovery_nci[ptr++] = 0x01;
      length += 2;
    }
    if ((uiccListenMask & 0x2) == 0x02 || (eseListenMask & 0x4) == 0x04) {
      NXPLOG_NCIHAL_D("phNxpNciHal_close (): Adding B passive listen");
      numPrms++;
      cmd_ce_discovery_nci[ptr++] = 0x81;
      cmd_ce_discovery_nci[ptr++] = 0x01;
      length += 2;
    }
    if ((uiccListenMask & 0x4) == 0x04 || (eseListenMask & 0x4) == 0x04) {
      NXPLOG_NCIHAL_D("phNxpNciHal_close (): Adding F passive listen");
      numPrms++;
      cmd_ce_discovery_nci[ptr++] = 0x82;
      cmd_ce_discovery_nci[ptr++] = 0x01;
      length += 2;
    }

    if (length != 0) {
      cmd_ce_discovery_nci[2] = length + 1;
      cmd_ce_discovery_nci[3] = numPrms;
      status = phNxpNciHal_send_ext_cmd(length + 4, cmd_ce_discovery_nci);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("CMD_CE_DISC_NCI: Failed");
      }
    } else {
      NXPLOG_NCIHAL_E(
          "No changes in the discovery command, sticking to last discovery "
          "command sent");
    }
  } else if ((!bShutdown) && IS_CHIP_TYPE_GE(sn220u)) {
    if (phNxpNciHal_getULPDetFlag() == true) {
      phNxpNciHal_propConfULPDetMode(true);
    }
  }
close_and_return:
  if (IS_CHIP_TYPE_EQ(sn100u) && bShutdown) {
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_system_ese_power_cycle),
                                      cmd_system_ese_power_cycle);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("ese power cycle failed");
    }
  }
  if (IS_CHIP_TYPE_L(sn220u) || bShutdown) {
    nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
  }
  if (phNxpNciHal_getULPDetFlag() == false) {
    do {
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci), cmd_reset_nci);

      if (status == NFCSTATUS_SUCCESS) {
        break;
      } else {
        NXPLOG_NCIHAL_E("NCI_CORE_RESET: Failed, perform retry after delay");
        usleep(1000 * 1000);
        if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE) {
          // make sure read is pending
          NFCSTATUS readStatus = phNxpNciHal_enableTmlRead();
          NXPLOG_NCIHAL_D("read status = %x", readStatus);
        }
        retry++;
        if (retry > 3) {
          NXPLOG_NCIHAL_E(
              "Maximum retries performed, shall restart HAL to recover");
          abort();
        }
      }
    } while (1);

    if (IS_CHIP_TYPE_GE(sn220u) && !bShutdown) {
      nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_system_set_service_status),
                                        cmd_system_set_service_status);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("NCI SYSTEM SET SERVICE STATUS to OFF Failed");
      }
    }
  }

  sem_destroy(&nxpncihal_ctrl.syncSpiNfc);

  if (NULL != gpphTmlNfc_Context->pDevHandle) {
    phNxpNciHal_close_complete(NFCSTATUS_SUCCESS);
    /* Abort any pending read and write */
    status = phTmlNfc_ReadAbort();
    status = phTmlNfc_WriteAbort();

    phOsalNfc_Timer_Cleanup();

    status = phTmlNfc_Shutdown();

    if (0 != pthread_join(nxpncihal_ctrl.client_thread, (void**)NULL)) {
      NXPLOG_TML_E("Fail to kill client thread!");
    }
    PhNxpEventLogger::GetInstance().Finalize();
    phNxpTempMgr::GetInstance().Reset();
    phTmlNfc_CleanUp();

    phDal4Nfc_msgrelease(nxpncihal_ctrl.gDrvCfg.nClientId);

    memset(&nxpncihal_ctrl, 0x00, sizeof(nxpncihal_ctrl));

    NXPLOG_NCIHAL_D("phNxpNciHal_close - phOsalNfc_DeInit completed");
  }

  CONCURRENCY_UNLOCK();

  phNxpNciHal_cleanup_monitor();
  write_unlocked_status = NFCSTATUS_SUCCESS;
  phNxpNciHal_release_info();
  /* reset config cache */
  resetNxpConfig();
  /* Return success always */
  return NFCSTATUS_SUCCESS;
}

/******************************************************************************
 * Function         phNxpNciHal_close_complete
 *
 * Description      This function inform libnfc-nci about result of
 *                  phNxpNciHal_close.
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_close_complete(NFCSTATUS status) {
  static phLibNfc_Message_t msg;

  if (status == NFCSTATUS_SUCCESS) {
    msg.eMsgType = NCI_HAL_CLOSE_CPLT_MSG;
  } else {
    msg.eMsgType = NCI_HAL_ERROR_MSG;
  }
  msg.pMsgData = NULL;
  msg.Size = 0;
  nxpncihal_ctrl.hal_open_status = false;
  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &msg);

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_configDiscShutdown
 *
 * Description      Enable the CE and VEN config during shutdown.
 *
 * Returns          Always return NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_configDiscShutdown(void) {
  NFCSTATUS status;
  /*NCI_RESET_CMD*/

  uint8_t cmd_disable_disc[] = {0x21, 0x06, 0x01, 0x00};

  uint8_t cmd_ce_disc_nci[] = {0x21, 0x03, 0x07, 0x03, 0x80,
                               0x01, 0x81, 0x01, 0x82, 0x01};

  uint8_t cmd_ven_pulld_enable_nci[] = {0x20, 0x02, 0x05, 0x01,
                                        0xA0, 0x07, 0x01, 0x03};

  /* Discover map - PROTOCOL_ISO_DEP, PROTOCOL_T3T and MIFARE Classic*/
  uint8_t cmd_disc_map[] = {0x21, 0x00, 0x0A, 0x03, 0x04, 0x03, 0x02,
                            0x03, 0x02, 0x01, 0x80, 0x01, 0x80};
  CONCURRENCY_LOCK();

  status = phNxpNciHal_send_ext_cmd(sizeof(cmd_disable_disc), cmd_disable_disc);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("CMD_DISABLE_DISCOVERY: Failed");
  }
  if (IS_CHIP_TYPE_L(sn100u)) {
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_ven_pulld_enable_nci),
                                      cmd_ven_pulld_enable_nci);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("CMD_VEN_PULLD_ENABLE_NCI: Failed");
    }
  }

  if (IS_CHIP_TYPE_GE(sn100u)) {
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_disc_map), cmd_disc_map);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Discovery Map command: Failed");
    }
    status = phNxpNciHal_ext_send_sram_config_to_flash();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Updation of the SRAM contents failed");
    }
  }
  status = phNxpNciHal_send_ext_cmd(sizeof(cmd_ce_disc_nci), cmd_ce_disc_nci);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("CMD_CE_DISC_NCI: Failed");
  }

  CONCURRENCY_UNLOCK();

  status = phNxpNciHal_close(true);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("NCI_HAL_CLOSE: Failed");
  }

  /* Return success always */
  return NFCSTATUS_SUCCESS;
}

/******************************************************************************
 * Function         phNxpNciHal_notify_i2c_fragmentation
 *
 * Description      This function can be used by HAL to inform
 *                 libnfc-nci that i2c fragmentation is enabled/disabled
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_notify_i2c_fragmentation(void) {
  if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
    /*inform libnfc-nci that i2c fragmentation is enabled/disabled */
    (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_ENABLE_I2C_FRAGMENTATION_EVT,
                                        HAL_NFC_STATUS_OK);
  }
}
/******************************************************************************
 * Function         phNxpNciHal_control_granted
 *
 * Description      Called by libnfc-nci when NFCC control is granted to HAL.
 *
 * Returns          Always returns NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_control_granted(void) {
  /* Take the concurrency lock so no other calls from upper layer
   * will be allowed
   */
  CONCURRENCY_LOCK();

  if (NULL != nxpncihal_ctrl.p_control_granted_cback) {
    (*nxpncihal_ctrl.p_control_granted_cback)();
  }
  /* At the end concurrency unlock so calls from upper layer will
   * be allowed
   */
  CONCURRENCY_UNLOCK();
  return NFCSTATUS_SUCCESS;
}

/******************************************************************************
 * Function         phNxpNciHal_request_control
 *
 * Description      This function can be used by HAL to request control of
 *                  NFCC to libnfc-nci. When control is provided to HAL it is
 *                  notified through phNxpNciHal_control_granted.
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_request_control(void) {
  if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
    /* Request Control of NCI Controller from NCI NFC Stack */
    (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_REQUEST_CONTROL_EVT,
                                        HAL_NFC_STATUS_OK);
  }

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_release_control
 *
 * Description      This function can be used by HAL to release the control of
 *                  NFCC back to libnfc-nci.
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_release_control(void) {
  if (nxpncihal_ctrl.p_nfc_stack_cback != NULL) {
    /* Release Control of NCI Controller to NCI NFC Stack */
    (*nxpncihal_ctrl.p_nfc_stack_cback)(HAL_NFC_RELEASE_CONTROL_EVT,
                                        HAL_NFC_STATUS_OK);
  }

  return;
}

/******************************************************************************
 * Function         phNxpNciHal_power_cycle
 *
 * Description      This function is called by libnfc-nci when power cycling is
 *                  performed. When processing is complete it is notified to
 *                  libnfc-nci through phNxpNciHal_power_cycle_complete.
 *
 * Returns          Always return NFCSTATUS_SUCCESS (0).
 *
 ******************************************************************************/
int phNxpNciHal_power_cycle(void) {
  NXPLOG_NCIHAL_D("Power Cycle");
  NFCSTATUS status = NFCSTATUS_FAILED;
  if (nxpncihal_ctrl.halStatus != HAL_STATUS_OPEN) {
    NXPLOG_NCIHAL_D("Power Cycle failed due to hal status not open");
    return NFCSTATUS_FAILED;
  }
  nxpncihal_ctrl.power_reset_triggered = true;
  status = phTmlNfc_IoCtl(phTmlNfc_e_PowerReset);

  if (NFCSTATUS_SUCCESS == status) {
    NXPLOG_NCIHAL_D("PN54X Reset - SUCCESS\n");
  } else {
    NXPLOG_NCIHAL_D("PN54X Reset - FAILED\n");
  }

  phNxpNciHal_power_cycle_complete(NFCSTATUS_SUCCESS);
  return NFCSTATUS_SUCCESS;
}

/******************************************************************************
 * Function         phNxpNciHal_power_cycle_complete
 *
 * Description      This function is called to provide the status of
 *                  phNxpNciHal_power_cycle to libnfc-nci through callback.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_power_cycle_complete(NFCSTATUS status) {
  static phLibNfc_Message_t msg;

  if (status == NFCSTATUS_SUCCESS) {
    msg.eMsgType = NCI_HAL_OPEN_CPLT_MSG;
  } else {
    msg.eMsgType = NCI_HAL_ERROR_MSG;
  }
  msg.pMsgData = NULL;
  msg.Size = 0;

  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &msg);

  return;
}
/******************************************************************************
 * Function         phNxpNciHal_check_ncicmd_write_window
 *
 * Description      This function is called to check the write synchroniztion
 *                  status if write already acquired then wait for corresponding
                    read to complete.
 *
 * Returns          return 0 on success and -1 on fail.
 *
 ******************************************************************************/

int phNxpNciHal_check_ncicmd_write_window(uint16_t cmd_len, uint8_t* p_cmd) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  int sem_timedout = 2, s;
  struct timespec ts;

  if (cmd_len < 1) {
    android_errorWriteLog(0x534e4554, "153880357");
    return NFCSTATUS_FAILED;
  }

  if ((p_cmd[0] & 0xF0) == 0x20) {
    clock_gettime(CLOCK_MONOTONIC, &ts);
    ts.tv_sec += sem_timedout;
    while ((s = sem_timedwait_monotonic_np(&nxpncihal_ctrl.syncSpiNfc, &ts)) == -1 &&
           errno == EINTR) {
      continue; /* Restart if interrupted by handler */
    }
    if (s != -1) {
      status = NFCSTATUS_SUCCESS;
    }
  } else {
    /* cmd window check not required for writing data packet */
    status = NFCSTATUS_SUCCESS;
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_ioctl
 *
 * Description      This function is called by jni when wired mode is
 *                  performed.First Pn54x driver will give the access
 *                  permission whether wired mode is allowed or not
 *                  arg (0):
 * Returns          return 0 on success and -1 on fail, On success
 *                  update the acutual state of operation in arg pointer
 *
 ******************************************************************************/
int phNxpNciHal_ioctl(long arg, void* p_data) {
  return phNxpNciHal_ioctlIf(arg, p_data);
}

/******************************************************************************
 * Function         phNxpNciHal_nfccClockCfgRead
 *
 * Description      This function is called for loading a data strcuture from
 *                  the config file with clock source and clock frequency values
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_nfccClockCfgRead(void) {
  unsigned long num = 0;
  int isfound = 0;

  nxpprofile_ctrl.bClkSrcVal = 0;
  nxpprofile_ctrl.bClkFreqVal = 0;
  nxpprofile_ctrl.bTimeout = 0;

  isfound = GetNxpNumValue(NAME_NXP_SYS_CLK_SRC_SEL, &num, sizeof(num));
  if (isfound > 0) {
    nxpprofile_ctrl.bClkSrcVal = num;
  }

  num = 0;
  isfound = 0;
  isfound = GetNxpNumValue(NAME_NXP_SYS_CLK_FREQ_SEL, &num, sizeof(num));
  if (isfound > 0) {
    nxpprofile_ctrl.bClkFreqVal = num;
  }

  num = 0;
  isfound = 0;
  isfound = GetNxpNumValue(NAME_NXP_SYS_CLOCK_TO_CFG, &num, sizeof(num));
  if (isfound > 0) {
    nxpprofile_ctrl.bTimeout = num;
  }

  NXPLOG_FWDNLD_D("gphNxpNciHal_fw_IoctlCtx.bClkSrcVal = 0x%x",
                  nxpprofile_ctrl.bClkSrcVal);
  NXPLOG_FWDNLD_D("gphNxpNciHal_fw_IoctlCtx.bClkFreqVal = 0x%x",
                  nxpprofile_ctrl.bClkFreqVal);
  NXPLOG_FWDNLD_D("gphNxpNciHal_fw_IoctlCtx.bClkFreqVal = 0x%x",
                  nxpprofile_ctrl.bTimeout);

  if ((nxpprofile_ctrl.bClkSrcVal < CLK_SRC_XTAL) ||
      (nxpprofile_ctrl.bClkSrcVal > CLK_SRC_PLL)) {
    NXPLOG_FWDNLD_E(
        "Clock source value is wrong in config file, setting it as default");
    nxpprofile_ctrl.bClkSrcVal = NXP_SYS_CLK_SRC_SEL;
  }
  if ((nxpprofile_ctrl.bClkFreqVal < CLK_FREQ_13MHZ) ||
      (nxpprofile_ctrl.bClkFreqVal > CLK_FREQ_48MHZ)) {
    NXPLOG_FWDNLD_E(
        "Clock frequency value is wrong in config file, setting it as default");
    nxpprofile_ctrl.bClkFreqVal = NXP_SYS_CLK_FREQ_SEL;
  }
  if ((nxpprofile_ctrl.bTimeout < CLK_TO_CFG_DEF) ||
      (nxpprofile_ctrl.bTimeout > CLK_TO_CFG_MAX)) {
    NXPLOG_FWDNLD_E(
        "Clock timeout value is wrong in config file, setting it as default");
    nxpprofile_ctrl.bTimeout = CLK_TO_CFG_DEF;
  }
}

/******************************************************************************
 * Function         phNxpNciHal_determineConfiguredClockSrc
 *
 * Description      This function determines and encodes clock source based on
 *                  clock frequency
 *
 * Returns          encoded form of clock source
 *
 *****************************************************************************/
int phNxpNciHal_determineConfiguredClockSrc() {
  uint8_t param_clock_src = CLK_SRC_PLL;
  if (nxpprofile_ctrl.bClkSrcVal == CLK_SRC_PLL) {
    if (IS_CHIP_TYPE_EQ(pn553)) {
      param_clock_src = param_clock_src << 3;
    } else if (IS_CHIP_TYPE_GE(sn100u)) {
      param_clock_src = 0;
    }

    if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_13MHZ) {
      param_clock_src |= 0x00;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_19_2MHZ) {
      param_clock_src |= 0x01;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_24MHZ) {
      param_clock_src |= 0x02;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_26MHZ) {
      param_clock_src |= 0x03;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_38_4MHZ) {
      param_clock_src |= 0x04;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_52MHZ) {
      param_clock_src |= 0x05;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_32MHZ) {
      param_clock_src |= 0x06;
    } else if (nxpprofile_ctrl.bClkFreqVal == CLK_FREQ_48MHZ) {
      param_clock_src |= 0x0A;
    } else {
      NXPLOG_NCIHAL_E("Wrong clock freq, send default PLL@19.2MHz");
      if (IS_CHIP_TYPE_L(sn100u))
        param_clock_src = 0x11;
      else
        param_clock_src = 0x01;
    }
  } else if (nxpprofile_ctrl.bClkSrcVal == CLK_SRC_XTAL) {
    param_clock_src = 0x08;

  } else {
    NXPLOG_NCIHAL_E("Wrong clock source. Don't apply any modification");
  }
  return param_clock_src;
}

/******************************************************************************
 * Function         phNxpNciHal_determineConfiguredClockSrc
 *
 * Description      This function determines and encodes clock source based on
 *                  clock frequency
 *
 * Returns          encoded form of clock source
 *
 *****************************************************************************/
int phNxpNciHal_determineClockDelayRequest(uint8_t nfcc_cfg_clock_src) {
  unsigned long num = 0;
  int isfound = 0;
  uint8_t nfcc_clock_delay_req = 0;
  uint8_t nfcc_clock_set_needed = false;

  isfound = GetNxpNumValue(NAME_NXP_CLOCK_REQ_DELAY, &num, sizeof(num));
  if (isfound > 0) {
    nxpprofile_ctrl.clkReqDelay = num;
  }
  if ((nxpprofile_ctrl.clkReqDelay < CLK_REQ_DELAY_MIN) ||
      (nxpprofile_ctrl.clkReqDelay > CLK_REQ_DELAY_MAX)) {
    NXPLOG_FWDNLD_E(
        "default delay to start clock value is wrong in config "
        "file, setting it as default");
    nxpprofile_ctrl.clkReqDelay = CLK_REQ_DELAY_DEF;
    return nfcc_clock_set_needed;
  }
  nfcc_clock_delay_req = nxpprofile_ctrl.clkReqDelay;

  /*Check if the clock source is XTAL as per config*/
  if (nfcc_cfg_clock_src == CLK_CFG_XTAL) {
    if (nfcc_clock_delay_req !=
        (phNxpNciClock.p_rx_data[CLK_REQ_DELAY_XTAL_OFFSET] &
         CLK_REQ_DELAY_MASK)) {
      nfcc_clock_set_needed = true;
      phNxpNciClock.p_rx_data[CLK_REQ_DELAY_XTAL_OFFSET] &=
          ~(CLK_REQ_DELAY_MASK);
      phNxpNciClock.p_rx_data[CLK_REQ_DELAY_XTAL_OFFSET] |=
          (nfcc_clock_delay_req & CLK_REQ_DELAY_MASK);
    }
  }
  /*Check if the clock source is PLL as per config*/
  else if (nfcc_cfg_clock_src < 6) {
    if (nfcc_clock_delay_req !=
        (phNxpNciClock.p_rx_data[CLK_REQ_DELAY_PLL_OFFSET] &
         CLK_REQ_DELAY_MASK)) {
      nfcc_clock_set_needed = true;
      phNxpNciClock.p_rx_data[CLK_REQ_DELAY_PLL_OFFSET] &=
          ~(CLK_REQ_DELAY_MASK);
      phNxpNciClock.p_rx_data[CLK_REQ_DELAY_PLL_OFFSET] |=
          (nfcc_clock_delay_req & CLK_REQ_DELAY_MASK);
    }
  }
  return nfcc_clock_set_needed;
}

/******************************************************************************
 * Function         phNxpNciHal_nfccClockCfgApply
 *
 * Description      This function is called after successful download
 *                  to check if clock settings in config file and chip
 *                  is same
 *
 * Returns          void.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_nfccClockCfgApply(void) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint8_t nfcc_cfg_clock_src, nfcc_cur_clock_src;
  uint8_t nfcc_clock_set_needed;
  uint8_t nfcc_clock_delay_req;
  static uint8_t* get_clock_cmd;
  uint8_t get_clck_cmd[] = {0x20, 0x03, 0x07, 0x03, 0xA0,
                            0x02, 0xA0, 0x03, 0xA0, 0x04};
  uint8_t get_clck_cmd_sn100[] = {0x20, 0x03, 0x03, 0x01, 0xA0, 0x11};
  uint8_t set_clck_cmd[] = {0x20, 0x02, 0x0B, 0x01, 0xA0, 0x11, 0x07,
                            0x01, 0x0A, 0x32, 0x02, 0x01, 0xF6, 0xF6};
  uint8_t get_clk_size = 0;

  if (IS_CHIP_TYPE_L(sn100u)) {
    get_clock_cmd = get_clck_cmd;
    get_clk_size = sizeof(get_clck_cmd);
  } else {
    get_clock_cmd = get_clck_cmd_sn100;
    get_clk_size = sizeof(get_clck_cmd_sn100);
  }
  phNxpNciHal_nfccClockCfgRead();
  phNxpNciClock.isClockSet = true;
  status = phNxpNciHal_send_ext_cmd(get_clk_size, get_clock_cmd);
  phNxpNciClock.isClockSet = false;

  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("unable to retrieve get_clk_src_sel");
    return status;
  }

  nfcc_cfg_clock_src = phNxpNciHal_determineConfiguredClockSrc();
  if (IS_CHIP_TYPE_L(sn100u)) {
    nfcc_cur_clock_src = phNxpNciClock.p_rx_data[12];
  } else {
    nfcc_cur_clock_src = phNxpNciClock.p_rx_data[8];
  }

  if (IS_CHIP_TYPE_L(sn100u)) {
    nfcc_clock_set_needed =
        (nfcc_cfg_clock_src != nfcc_cur_clock_src ||
         phNxpNciClock.p_rx_data[16] == nxpprofile_ctrl.bTimeout)
            ? true
            : false;
  } else {
    nfcc_clock_delay_req =
        phNxpNciHal_determineClockDelayRequest(nfcc_cfg_clock_src);
    /**Determine clock src is as expected*/
    nfcc_clock_set_needed =
        ((nfcc_cfg_clock_src != nfcc_cur_clock_src || nfcc_clock_delay_req)
             ? true
             : false);
  }

  if (nfcc_clock_set_needed) {
    NXPLOG_NCIHAL_D("Setting Clock Source and Frequency");
    if (IS_CHIP_TYPE_L(sn100u)) {
      phNxpNciHal_txNfccClockSetCmd();
    } else {
      /*Read the preset value from FW*/
      memcpy(&set_clck_cmd[7], &phNxpNciClock.p_rx_data[8],
             phNxpNciClock.p_rx_data[7]);
      /*Update clock source and frequency as per DH configuration*/
      set_clck_cmd[7] = nfcc_cfg_clock_src;
      status = phNxpNciHal_send_ext_cmd(sizeof(set_clck_cmd), set_clck_cmd);
    }
  }

  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_get_mw_eeprom
 *
 * Description      This function is called to retrieve data in mw eeprom area
 *
 * Returns          NFCSTATUS.
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_get_mw_eeprom(void) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint8_t retry_cnt = 0;
  static uint8_t get_mw_eeprom_cmd[] = {0x20, 0x03, 0x03, 0x01, 0xA0, 0x0F};

retry_send_ext:
  if (retry_cnt > 3) {
    return NFCSTATUS_FAILED;
  }

  phNxpNciMwEepromArea.isGetEepromArea = true;
  status =
      phNxpNciHal_send_ext_cmd(sizeof(get_mw_eeprom_cmd), get_mw_eeprom_cmd);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("unable to get the mw eeprom data");
    phNxpNciMwEepromArea.isGetEepromArea = false;
    retry_cnt++;
    goto retry_send_ext;
  }
  phNxpNciMwEepromArea.isGetEepromArea = false;

  if (phNxpNciMwEepromArea.p_rx_data[12]) {
    fw_download_success = 1;
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_set_mw_eeprom
 *
 * Description      This function is called to update data in mw eeprom area
 *
 * Returns          void.
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_set_mw_eeprom(void) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint8_t retry_cnt = 0;
  uint8_t set_mw_eeprom_cmd[39] = {0};
  uint8_t cmd_header[] = {0x20, 0x02, 0x24, 0x01, 0xA0, 0x0F, 0x20};

  memcpy(set_mw_eeprom_cmd, cmd_header, sizeof(cmd_header));
  phNxpNciMwEepromArea.p_rx_data[12] = 0;
  memcpy(set_mw_eeprom_cmd + sizeof(cmd_header), phNxpNciMwEepromArea.p_rx_data,
         sizeof(phNxpNciMwEepromArea.p_rx_data));

retry_send_ext:
  if (retry_cnt > 3) {
    return NFCSTATUS_FAILED;
  }

  status =
      phNxpNciHal_send_ext_cmd(sizeof(set_mw_eeprom_cmd), set_mw_eeprom_cmd);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("unable to update the mw eeprom data");
    retry_cnt++;
    goto retry_send_ext;
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_china_tianjin_rf_setting
 *
 * Description      This function is called to check RF Setting
 *
 * Returns          Status.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_china_tianjin_rf_setting(void) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  int isfound = 0;
  unsigned long config_value = 0;
  int rf_val = 0;
  int flag_send_tianjin_config = true;
  int flag_send_transit_config = true;
  int flag_send_cmabypass_config = true;
  int flag_send_mfc_rf_setting_config = true;
  uint8_t retry_cnt = 0;
  int enable_bit = 0;

  static uint8_t get_rf_cmd[] = {0x20, 0x03, 0x03, 0x01, 0xA0, 0x85};
  NXPLOG_NCIHAL_D("phNxpNciHal_china_tianjin_rf_setting - Enter");

retry_send_ext:
  if (retry_cnt > 3) {
    return NFCSTATUS_FAILED;
  }

  phNxpNciRfSet.isGetRfSetting = true;
  status = phNxpNciHal_send_ext_cmd(sizeof(get_rf_cmd), get_rf_cmd);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("unable to get the RF setting");
    phNxpNciRfSet.isGetRfSetting = false;
    retry_cnt++;
    goto retry_send_ext;
  }
  phNxpNciRfSet.isGetRfSetting = false;
  if (phNxpNciRfSet.p_rx_data[3] != 0x00) {
    NXPLOG_NCIHAL_E("GET_CONFIG_RSP is FAILED for CHINA TIANJIN");
    return status;
  }

  /* check if tianjin_rf_setting is required */
  rf_val = phNxpNciRfSet.p_rx_data[10];
  isfound = (GetNxpNumValue(NAME_NXP_CHINA_TIANJIN_RF_ENABLED,
                            (void*)&config_value, sizeof(config_value)));
  if (isfound > 0) {
    enable_bit = rf_val & 0x40;
    if (nfcFL.nfccFL._NFCC_MIFARE_TIANJIN) {
      if ((enable_bit != 0x40) && (config_value == 1)) {
        phNxpNciRfSet.p_rx_data[10] |= 0x40;  // Enable if it is disabled
      } else if ((enable_bit == 0x40) && (config_value == 0)) {
        phNxpNciRfSet.p_rx_data[10] &= 0xBF;  // Disable if it is Enabled
      } else {
        flag_send_tianjin_config = false;  // No need to change in RF setting
      }
    } else {
      enable_bit = phNxpNciRfSet.p_rx_data[11] & 0x10;
      if ((config_value == 1) && (enable_bit != 0x10)) {
        NXPLOG_NCIHAL_E("Setting Non-Mifare reader for china tianjin");
        phNxpNciRfSet.p_rx_data[11] |= 0x10;
      } else if ((config_value == 0) && (enable_bit == 0x10)) {
        NXPLOG_NCIHAL_E("Setting Non-Mifare reader for china tianjin");
        phNxpNciRfSet.p_rx_data[11] &= 0xEF;
      } else {
        flag_send_tianjin_config = false;
      }
    }
  } else {
    flag_send_tianjin_config = false;
  }

  config_value = 0;
  /*check MFC NACK settings*/
  rf_val = phNxpNciRfSet.p_rx_data[9];
  isfound = (GetNxpNumValue(NAME_NXP_MIFARE_NACK_TO_RATS_ENABLE,
                            (void*)&config_value, sizeof(config_value)));
  if (isfound > 0) {
    enable_bit = rf_val & 0x20;
    if ((enable_bit != 0x20) && (config_value == 1)) {
      phNxpNciRfSet.p_rx_data[9] |= 0x20;  // Enable if it is disabled
    } else if ((enable_bit == 0x20) && (config_value == 0)) {
      phNxpNciRfSet.p_rx_data[9] &= ~0x20;  // Disable if it is Enabled
    } else {
      flag_send_mfc_rf_setting_config =
          false;  // No need to change in RF setting
    }
  } else {
    flag_send_mfc_rf_setting_config = FALSE;  // No need to change in RF setting
  }

  config_value = 0;
  /*check if china block number check is required*/
  rf_val = phNxpNciRfSet.p_rx_data[8];
  isfound = (GetNxpNumValue(NAME_NXP_CHINA_BLK_NUM_CHK_ENABLE,
                            (void*)&config_value, sizeof(config_value)));
  if (isfound > 0) {
    enable_bit = rf_val & 0x40;
    if ((enable_bit != 0x40) && (config_value == 1)) {
      phNxpNciRfSet.p_rx_data[8] |= 0x40;  // Enable if it is disabled
    } else if ((enable_bit == 0x40) && (config_value == 0)) {
      phNxpNciRfSet.p_rx_data[8] &= ~0x40;  // Disable if it is Enabled
    } else {
      flag_send_transit_config = false;  // No need to change in RF setting
    }
  } else {
    flag_send_transit_config = FALSE;  // No need to change in RF setting
  }

  config_value = 0;
  isfound = (GetNxpNumValue(NAME_NXP_CN_TRANSIT_CMA_BYPASSMODE_ENABLE,
                            (void*)&config_value, sizeof(config_value)));
  if (isfound > 0) {
    if (config_value == 0 && ((phNxpNciRfSet.p_rx_data[10] & 0x80) == 0x80)) {
      NXPLOG_NCIHAL_D("Disable CMA_BYPASSMODE Supports EMVCo PICC Complaincy");
      phNxpNciRfSet.p_rx_data[10] &=
          ~0x80;  // set 24th bit of RF MISC SETTING to 0 for EMVCo PICC
                  // Complaincy support
    } else if (config_value == 1 &&
               ((phNxpNciRfSet.p_rx_data[10] & 0x80) == 0)) {
      NXPLOG_NCIHAL_D(
          "Enable CMA_BYPASSMODE bypass the ISO14443-3A state machine from "
          "READY to ACTIVE and backward compatibility with MIfrae Reader ");
      phNxpNciRfSet.p_rx_data[10] |=
          0x80;  // set 24th bit of RF MISC SETTING to 1 for backward
                 // compatibility with MIfrae Reader
    } else {
      flag_send_cmabypass_config = FALSE;  // No need to change in RF setting
    }
  } else {
    flag_send_cmabypass_config = FALSE;
  }

  if (flag_send_tianjin_config || flag_send_transit_config ||
      flag_send_cmabypass_config || flag_send_mfc_rf_setting_config) {
    static uint8_t set_rf_cmd[] = {0x20, 0x02, 0x08, 0x01, 0xA0, 0x85,
                                   0x04, 0x50, 0x08, 0x68, 0x00};
    memcpy(&set_rf_cmd[4], &phNxpNciRfSet.p_rx_data[5], 7);
    status = phNxpNciHal_send_ext_cmd(sizeof(set_rf_cmd), set_rf_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("unable to set the RF setting");
      retry_cnt++;
      goto retry_send_ext;
    }
  }

  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_DownloadFw
 *
 * Description      It is used to trigger the FW download as part of FW tearing
 *                  scenario handling. It downloads either degraded or Normal
 *                  FW, based on the session state of the NFCC.
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_DownloadFw(bool isMinFwVer, bool degradedFwDnld) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  phTmlNfc_IoCtl(phTmlNfc_e_EnableDownloadMode);
  if (isMinFwVer) {
    /* since minimal fw required dlreset to boot in Download mode */
    status = phNxpNciHal_dlResetInFwDnldMode();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("DL Reset failed for minimal fw");
    }
  }
  phTmlNfc_EnableFwDnldMode(true);

  /* Set the obtained device handle to download module */
  phDnldNfc_SetHwDevHandle();
  NXPLOG_NCIHAL_D("Calling Seq handler for FW Download \n");
  status = phNxpNciHal_fw_download_seq(nxpprofile_ctrl.bClkSrcVal,
                                       nxpprofile_ctrl.bClkFreqVal, 0, false,
                                       degradedFwDnld);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("FW Download Sequence Handler Failed.");
  } else {
    property_set("nfc.fw.force_download", "0");
    fw_download_success = 1;
  }

  status = phNxpNciHal_dlResetInFwDnldMode();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("DL Reset failed in FW DN mode");
  }
}

/******************************************************************************
 * Function         phNxpNciHal_CheckAndHandleFwTearDown
 *
 * Description      Check Whether chip is in FW download mode, If chip is in
 *                  Download mode and previous session is not complete, then
 *                  Do force FW update.
 *
 * Returns          void
 *
 ******************************************************************************/
void phNxpNciHal_CheckAndHandleFwTearDown() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t session_state = -1;
  unsigned long minimal_fw_version = DEFAULT_MINIMAL_FW_VERSION;
  bool isMinFwVer = false;
  status = phNxpNciHal_getChipInfoInFwDnldMode();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Get Chip Info Failed");
    usleep(150 * 1000);
    return;
  }
  if (!GetNxpNumValue(NAME_NXP_MINIMAL_FW_VERSION, &minimal_fw_version,
                      sizeof(minimal_fw_version))) {
    /* If config file doesn't contain the info use default */
    minimal_fw_version = DEFAULT_MINIMAL_FW_VERSION;
  }
  if (wFwVerRsp != minimal_fw_version) {
    session_state = phNxpNciHal_getSessionInfoInFwDnldMode();
    if (session_state == 0) {
      NXPLOG_NCIHAL_E("NFC not in the teared state, boot NFCC in NCI mode");
      return;
    }
  } else {
    isMinFwVer = true;
  }
  if (session_state == EOS_FW_SESSION_STATE_LOCKED) {
    phNxpNciHal_DownloadFw(isMinFwVer, true);
  }
  phNxpNciHal_DownloadFw(isMinFwVer);
}

/******************************************************************************
 * Function         phNxpNciHal_getChipInfoInFwDnldMode
 *
 * Description      Helper function to get the chip info in download mode
 *
 * Returns          Status
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_getChipInfoInFwDnldMode(bool bIsVenResetReqd) {
  uint8_t get_chip_info_cmd[] = {0x00, 0x04, 0xF1, 0x00,
                                 0x00, 0x00, 0x6E, 0xEF};
  NFCSTATUS status = NFCSTATUS_FAILED;
  int retry_cnt = 0;
  if (bIsVenResetReqd) {
    status = phTmlNfc_IoCtl(phTmlNfc_e_EnableDownloadModeWithVenRst);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Enable Download mode failed");
      return status;
    }
  }
  phTmlNfc_EnableFwDnldMode(true);
  do {
    status =
        phNxpNciHal_send_ext_cmd(sizeof(get_chip_info_cmd), get_chip_info_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      /* break the loop if HAL write failed or response Timeout */
      break;
    } else {
      /* Check FW getResponse command response status byte */
      if (nxpncihal_ctrl.p_rx_data[0] == 0x00) {
        if (nxpncihal_ctrl.p_rx_data[2] != 0x00) {
          status = NFCSTATUS_FAILED;
          /* Resend DL_GET_VERSION_CMD to recover from error
           * such as DL_PROTOCOL_ERROR.
           */
          if (retry_cnt < MAX_RETRY_COUNT) {
            retry_cnt++;
            /* No default read pending in FW dowbload mode.
             * Thus, keep read pending before every cmd retry
             */
            if (phNxpNciHal_enableTmlRead() != NFCSTATUS_PENDING) {
              NXPLOG_NCIHAL_E("%s read error", __func__);
            }
          }
        }
      } else {
        status = NFCSTATUS_FAILED;
        break;
      }
    }
  } while ((status != NFCSTATUS_SUCCESS) && (retry_cnt < MAX_RETRY_COUNT));

  phTmlNfc_EnableFwDnldMode(false);
  if (phNxpNciHal_enableTmlRead() != NFCSTATUS_PENDING) {
    NXPLOG_NCIHAL_E("%s read status error status", __FUNCTION__);
  }
  if (status == NFCSTATUS_SUCCESS) {
    phNxpNciHal_configFeatureList(nxpncihal_ctrl.p_rx_data,
                                  nxpncihal_ctrl.rx_data_len);
    wFwVerRsp = pConfigFL->getFWVersionInfo(nxpncihal_ctrl.p_rx_data,
                                            nxpncihal_ctrl.rx_data_len);
    setNxpFwConfigPath();
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_getSessionInfoInFwDnldMode
 *
 * Description      Helper function to get the session info in download mode
 *
 * Returns          0 means session closed
 *
 ******************************************************************************/
uint8_t phNxpNciHal_getSessionInfoInFwDnldMode() {
  uint8_t session_status = -1;
  uint8_t get_session_info_cmd[] = {0x00, 0x04, 0xF2, 0x00,
                                    0x00, 0x00, 0xF5, 0x33};
  phTmlNfc_EnableFwDnldMode(true);
  NFCSTATUS status = phNxpNciHal_send_ext_cmd(sizeof(get_session_info_cmd),
                                              get_session_info_cmd);
  if (status == NFCSTATUS_SUCCESS) {
    /* Check FW getResponse command response status byte */
    if (nxpncihal_ctrl.p_rx_data[2] == 0x00 &&
        nxpncihal_ctrl.p_rx_data[0] == 0x00) {
      session_status = nxpncihal_ctrl.p_rx_data[3];
    } else {
      NXPLOG_NCIHAL_D("get session info Failed !!!");
      usleep(150 * 1000);
    }
  }
  status = phNxpNciHal_dlResetInFwDnldMode();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("DL Reset failed in FW DN mode");
  }
  return session_status;
}

/******************************************************************************
 * Function         phNxpNciHal_dlResetInFwDnldMode
 *
 * Description      Helper function to change the mode from FW to NCI
 *
 * Returns          Status
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_dlResetInFwDnldMode() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  phTmlNfc_EnableFwDnldMode(true);
  NXPLOG_NCIHAL_D("Sending DL Reset for NFCC soft reboot");
  phDnldNfc_SetHwDevHandle();

  status = phNxpNciHal_fw_dnld_switch_normal_mode();

  phTmlNfc_EnableFwDnldMode(false);
  phTmlNfc_ReadAbort();
  phDnldNfc_ReSetHwDevHandle();
  if (phNxpNciHal_enableTmlRead() != NFCSTATUS_PENDING) {
    NXPLOG_NCIHAL_E("%s read status error status", __FUNCTION__);
    status = NFCSTATUS_FAILED;
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_gpio_restore
 *
 * Description      This function restores the gpio values into eeprom
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_gpio_restore(phNxpNciHal_GpioInfoState state) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  uint8_t get_gpio_values_cmd[] = {0x20, 0x03, 0x03, 0x01, 0xA0, 0x00};
  uint8_t set_gpio_values_cmd[] = {
      0x20, 0x02, 0x00, 0x01, 0xA0, 0x00, 0x20, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

  if (state == GPIO_STORE) {
    nxpncihal_ctrl.phNxpNciGpioInfo.state = GPIO_STORE;
    get_gpio_values_cmd[5] = 0x08;
    status = phNxpNciHal_send_ext_cmd(sizeof(get_gpio_values_cmd),
                                      get_gpio_values_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to get GPIO values!!!\n");
      return;
    }

    nxpncihal_ctrl.phNxpNciGpioInfo.state = GPIO_STORE_DONE;
    set_gpio_values_cmd[2] = 0x24;
    set_gpio_values_cmd[5] = 0x14;
    set_gpio_values_cmd[7] = nxpncihal_ctrl.phNxpNciGpioInfo.values[0];
    set_gpio_values_cmd[8] = nxpncihal_ctrl.phNxpNciGpioInfo.values[1];
    status = phNxpNciHal_send_ext_cmd(sizeof(set_gpio_values_cmd),
                                      set_gpio_values_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to set GPIO values!!!\n");
      return;
    }
  } else if (state == GPIO_RESTORE) {
    nxpncihal_ctrl.phNxpNciGpioInfo.state = GPIO_RESTORE;
    get_gpio_values_cmd[5] = 0x14;
    status = phNxpNciHal_send_ext_cmd(sizeof(get_gpio_values_cmd),
                                      get_gpio_values_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to get GPIO values!!!\n");
      return;
    }

    nxpncihal_ctrl.phNxpNciGpioInfo.state = GPIO_RESTORE_DONE;
    set_gpio_values_cmd[2] = 0x06;
    set_gpio_values_cmd[5] = 0x08;  // update TAG
    set_gpio_values_cmd[6] = 0x02;  // update length
    set_gpio_values_cmd[7] = nxpncihal_ctrl.phNxpNciGpioInfo.values[0];
    set_gpio_values_cmd[8] = nxpncihal_ctrl.phNxpNciGpioInfo.values[1];
    status = phNxpNciHal_send_ext_cmd(9, set_gpio_values_cmd);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("Failed to set GPIO values!!!\n");
      return;
    }
  } else {
    NXPLOG_NCIHAL_E("GPIO Restore Invalid Option!!!\n");
  }
}

/******************************************************************************
 * Function         phNxpNciHal_nfcc_core_reset_init
 *
 * Description      Helper function to do nfcc core reset & core init
 *
 * Returns          Status
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_nfcc_core_reset_init(bool keep_config) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t retry_cnt = 0;
  uint8_t cmd_reset_nci[] = {0x20, 0x00, 0x01, 0x01};

  if (keep_config) {
    cmd_reset_nci[3] = 0x00;
  }
retry_core_reset:
  status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci), cmd_reset_nci);
  if ((status != NFCSTATUS_SUCCESS) && (retry_cnt < 3)) {
    NXPLOG_NCIHAL_D("Retry: NCI_CORE_RESET");
    retry_cnt++;
    goto retry_core_reset;
  } else if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("NCI_CORE_RESET failed!!!\n");
    return status;
  }

  retry_cnt = 0;
  uint8_t cmd_init_nci[] = {0x20, 0x01, 0x00};
  uint8_t cmd_init_nci2_0[] = {0x20, 0x01, 0x02, 0x00, 0x00};
retry_core_init:
  if (nxpncihal_ctrl.nci_info.nci_version == NCI_VERSION_2_0) {
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci2_0), cmd_init_nci2_0);
  } else {
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci), cmd_init_nci);
  }

  if ((status != NFCSTATUS_SUCCESS) && (retry_cnt < 3)) {
    NXPLOG_NCIHAL_D("Retry: NCI_CORE_INIT\n");
    retry_cnt++;
    goto retry_core_init;
  } else if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("NCI_CORE_INIT failed!!!\n");
    return status;
  }

  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_resetDefaultSettings
 *
 * Description      Helper function to do nfcc core reset, core init
 *                  (if previously firmware update was triggered) and
 *                  apply default NFC settings
 *
 * Returns          Status
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_resetDefaultSettings(uint8_t fw_update_req,
                                           bool keep_config) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  if (fw_update_req) {
    status = phNxpNciHal_nfcc_core_reset_init(keep_config);
  }
  if (status == NFCSTATUS_SUCCESS) {
    unsigned long num = 0;
    int ret = 0;
    phNxpNciHal_conf_nfc_forum_mode();
    if (IS_CHIP_TYPE_GE(sn100u)) {
      ret = GetNxpNumValue(NAME_NXP_RDR_DISABLE_ENABLE_LPCD, &num, sizeof(num));
      if (!ret || num == 1 || num == 2) {
        phNxpNciHal_prop_conf_lpcd(true);
      } else if (ret && num == 0) {
        phNxpNciHal_prop_conf_lpcd(false);
      }
    }
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_enable_i2c_fragmentation
 *
 * Description      This function is called to process the response status
 *                  and print the status byte.
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_enable_i2c_fragmentation() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  static uint8_t fragmentation_enable_config_cmd[] = {0x20, 0x02, 0x05, 0x01,
                                                      0xA0, 0x05, 0x01, 0x10};
  long i2c_status = 0x00;
  long config_i2c_vlaue = 0xff;
  /*NCI_RESET_CMD*/
  static uint8_t cmd_reset_nci[] = {0x20, 0x00, 0x01, 0x00};
  /*NCI_INIT_CMD*/
  static uint8_t cmd_init_nci[] = {0x20, 0x01, 0x00};
  static uint8_t cmd_init_nci2_0[] = {0x20, 0x01, 0x02, 0x00, 0x00};
  static uint8_t get_i2c_fragmentation_cmd[] = {0x20, 0x03, 0x03,
                                                0x01, 0xA0, 0x05};
  if (GetNxpNumValue(NAME_NXP_I2C_FRAGMENTATION_ENABLED, (void*)&i2c_status,
                     sizeof(i2c_status)) == true) {
    NXPLOG_FWDNLD_D("I2C status : %ld", i2c_status);
  } else {
    NXPLOG_FWDNLD_E("I2C status read not succeeded. Default value : %ld",
                    i2c_status);
  }
  status = phNxpNciHal_send_ext_cmd(sizeof(get_i2c_fragmentation_cmd),
                                    get_i2c_fragmentation_cmd);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("unable to retrieve  get_i2c_fragmentation_cmd");
  } else {
    if (nxpncihal_ctrl.p_rx_data[8] == 0x10) {
      config_i2c_vlaue = 0x01;
      phNxpNciHal_notify_i2c_fragmentation();
      phTmlNfc_set_fragmentation_enabled(I2C_FRAGMENTATION_ENABLED);
    } else if (nxpncihal_ctrl.p_rx_data[8] == 0x00) {
      config_i2c_vlaue = 0x00;
    }
    // if the value already matches, nothing to be done
    if (config_i2c_vlaue != i2c_status) {
      if (i2c_status == 0x01) {
        /* NXP I2C fragmenation enabled*/
        status =
            phNxpNciHal_send_ext_cmd(sizeof(fragmentation_enable_config_cmd),
                                     fragmentation_enable_config_cmd);
        if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("NXP fragmentation enable failed");
        }
      } else if (i2c_status == 0x00 || config_i2c_vlaue == 0xff) {
        fragmentation_enable_config_cmd[7] = 0x00;
        /* NXP I2C fragmentation disabled*/
        status =
            phNxpNciHal_send_ext_cmd(sizeof(fragmentation_enable_config_cmd),
                                     fragmentation_enable_config_cmd);
        if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("NXP fragmentation disable failed");
        }
      }
      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_reset_nci), cmd_reset_nci);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("NCI_CORE_RESET: Failed");
      }
      if (nxpncihal_ctrl.nci_info.nci_version == NCI_VERSION_2_0) {
        status =
            phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci2_0), cmd_init_nci2_0);
      } else {
        status = phNxpNciHal_send_ext_cmd(sizeof(cmd_init_nci), cmd_init_nci);
      }
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("NCI_CORE_INIT : Failed");
      } else if (i2c_status == 0x01) {
        phNxpNciHal_notify_i2c_fragmentation();
        phTmlNfc_set_fragmentation_enabled(I2C_FRAGMENTATION_ENABLED);
      }
    }
  }
}
/******************************************************************************
 * Function         phNxpNciHal_do_se_session_reset
 *
 * Description      This function is called to set the session id to default
 *                  value.
 *
 * Returns          NFCSTATUS.
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_do_swp_session_reset(void) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  static uint8_t reset_swp_session_identity_set[] = {
      0x20, 0x02, 0x17, 0x02, 0xA0, 0xEA, 0x08, 0xFF, 0xFF,
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xA0, 0x1E, 0x08,
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  status = phNxpNciHal_send_ext_cmd(sizeof(reset_swp_session_identity_set),
                                    reset_swp_session_identity_set);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("NXP reset_ese_session_identity_set command failed");
  }
  return status;
}
/******************************************************************************
 * Function         phNxpNciHal_do_factory_reset
 *
 * Description      This function is called during factory reset to clear/reset
 *                  nfc sub-system persistent data.
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_do_factory_reset(void) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  // After factory reset phone will turnoff so mutex not required here.
  if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE) {
    status = phNxpNciHal_MinOpen();
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("%s: NXP Nfc Open failed", __func__);
      return;
    }
    phNxpNciHal_deinitializeRegRfFwDnld();
  }
  status = phNxpNciHal_do_swp_session_reset();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s failed. status = %x ", __func__, status);
  }
}
/******************************************************************************
 * Function         phNxpNciHal_hci_network_reset
 *
 * Description      This function resets the session id's of all the se's
 *                  in the HCI network and notify to HCI_NETWORK_RESET event to
 *                  NFC HAL Client.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_hci_network_reset(void) {
  static phLibNfc_Message_t msg;
  msg.pMsgData = NULL;
  msg.Size = 0;

  NFCSTATUS status = phNxpNciHal_do_swp_session_reset();

  if (status != NFCSTATUS_SUCCESS) {
    msg.eMsgType = NCI_HAL_ERROR_MSG;
  } else {
    msg.eMsgType = NCI_HAL_HCI_NETWORK_RESET_MSG;
  }
  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &msg);
}
/******************************************************************************
 * Function         phNxpNciHal_print_res_status
 *
 * Description      This function is called to process the response status
 *                  and print the status byte.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void phNxpNciHal_print_res_status(uint8_t* p_rx_data, uint16_t* p_len) {
  static uint8_t response_buf[][30] = {"STATUS_OK",
                                       "STATUS_REJECTED",
                                       "STATUS_RF_FRAME_CORRUPTED",
                                       "STATUS_FAILED",
                                       "STATUS_NOT_INITIALIZED",
                                       "STATUS_SYNTAX_ERROR",
                                       "STATUS_SEMANTIC_ERROR",
                                       "RFU",
                                       "RFU",
                                       "STATUS_INVALID_PARAM",
                                       "STATUS_MESSAGE_SIZE_EXCEEDED",
                                       "STATUS_UNDEFINED"};
  int status_byte;
  if (p_rx_data[0] == 0x40 && (p_rx_data[1] == 0x02 || p_rx_data[1] == 0x03)) {
    if (p_rx_data[2] && p_rx_data[3] <= 10) {
      status_byte = p_rx_data[CORE_RES_STATUS_BYTE];
      NXPLOG_NCIHAL_D("%s: response status =%s", __func__,
                      response_buf[status_byte]);
    } else {
      NXPLOG_NCIHAL_D("%s: response status =%s", __func__, response_buf[11]);
    }
    if (phNxpNciClock.isClockSet) {
      int i, len = sizeof(phNxpNciClock.p_rx_data);
      if (*p_len > len) {
        android_errorWriteLog(0x534e4554, "169257710");
      } else {
        len = *p_len;
      }
      for (i = 0; i < len; i++) {
        phNxpNciClock.p_rx_data[i] = p_rx_data[i];
      }
    }

    else if (phNxpNciRfSet.isGetRfSetting) {
      int i, len = sizeof(phNxpNciRfSet.p_rx_data);
      if (*p_len > len) {
        android_errorWriteLog(0x534e4554, "169258733");
      } else {
        len = *p_len;
      }
      for (i = 0; i < len; i++) {
        phNxpNciRfSet.p_rx_data[i] = p_rx_data[i];
        // NXPLOG_NCIHAL_D("%s: response status =0x%x",__func__,p_rx_data[i]);
      }
    } else if (phNxpNciMwEepromArea.isGetEepromArea) {
      int i, len = sizeof(phNxpNciMwEepromArea.p_rx_data) + 8;
      if (*p_len > len) {
        android_errorWriteLog(0x534e4554, "169258884");
      } else {
        len = *p_len;
      }
      for (i = 8; i < len; i++) {
        phNxpNciMwEepromArea.p_rx_data[i - 8] = p_rx_data[i];
      }
    } else if (nxpncihal_ctrl.phNxpNciGpioInfo.state == GPIO_STORE) {
      NXPLOG_NCIHAL_D("%s: Storing GPIO Values...", __func__);
      nxpncihal_ctrl.phNxpNciGpioInfo.values[0] = p_rx_data[9];
      nxpncihal_ctrl.phNxpNciGpioInfo.values[1] = p_rx_data[8];
    } else if (nxpncihal_ctrl.phNxpNciGpioInfo.state == GPIO_RESTORE) {
      NXPLOG_NCIHAL_D("%s: Restoring GPIO Values...", __func__);
      nxpncihal_ctrl.phNxpNciGpioInfo.values[0] = p_rx_data[9];
      nxpncihal_ctrl.phNxpNciGpioInfo.values[1] = p_rx_data[8];
    }
  }

  if (p_rx_data[2] && (config_access == true)) {
    if (p_rx_data[3] != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_W("Invalid Data from config file.");
      config_success = false;
    }
  }
}
/******************************************************************************
 * Function         phNxpNciHal_initialize_mifare_flag
 *
 * Description      This function gets the value for Mfc flags.
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_initialize_mifare_flag() {
  unsigned long num = 0;
  bEnableMfcReader = false;
  // 1: Enable Mifare Classic protocol in RF Discovery.
  // 0: Remove Mifare Classic protocol in RF Discovery.
  if (GetNxpNumValue(NAME_MIFARE_READER_ENABLE, &num, sizeof(num))) {
    bEnableMfcReader = (num == 0) ? false : true;
  }
}

/*****************************************************************************
 * Function         phNxpNciHal_send_get_cfgs
 *
 * Description      This function is called to  send get configs
 *                  for all the types in get_cfg_arr.
 *                  Response of getConfigs(EEPROM stored) will be
 *                  compared with request coming from MW during discovery.
 *                  If same, then current setConfigs will be dropped
 *
 * Returns          Returns NFCSTATUS_SUCCESS if sending cmd is successful and
 *                  response is received.
 *
 *****************************************************************************/
NFCSTATUS phNxpNciHal_send_get_cfgs() {
  NXPLOG_NCIHAL_D("%s Enter", __func__);
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t num_cfgs = sizeof(get_cfg_arr) / sizeof(uint8_t);
  uint8_t cfg_count = 0, retry_cnt = 0;
  if (mGetCfg_info != NULL) {
    mGetCfg_info->isGetcfg = true;
  }
  uint8_t cmd_get_cfg[] = {0x20, 0x03, 0x02, 0x01, 0x00};

  while (cfg_count < num_cfgs) {
    cmd_get_cfg[sizeof(cmd_get_cfg) - 1] = get_cfg_arr[cfg_count];

  retry_get_cfg:
    status = phNxpNciHal_send_ext_cmd(sizeof(cmd_get_cfg), cmd_get_cfg);
    if (status != NFCSTATUS_SUCCESS && retry_cnt < 3) {
      NXPLOG_NCIHAL_E("cmd_get_cfg failed");
      retry_cnt++;
      goto retry_get_cfg;
    }
    if (retry_cnt == 3) {
      break;
    }
    cfg_count++;
    retry_cnt = 0;
  }
  mGetCfg_info->isGetcfg = false;
  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_configFeatureList
**
** Description      Configures the featureList based on chip type &
**                  Configure fragmentation length based on chip type.
**                  HW Version information number will provide chipType.
**                  HW Version can be obtained from CORE_INIT_RESPONSE(NCI 1.0)
**                  or CORE_RST_NTF(NCI 2.0)
**
** Parameters       CORE_INIT_RESPONSE/CORE_RST_NTF, len
**
** Returns          none
*******************************************************************************/
void phNxpNciHal_configFeatureList(uint8_t* init_rsp, uint16_t rsp_len) {
  nxpncihal_ctrl.chipType = pConfigFL->processChipType(init_rsp, rsp_len);
  tNFC_chipType chipType = nxpncihal_ctrl.chipType;
  NXPLOG_NCIHAL_D("%s chipType = %s", __func__, pConfigFL->product[chipType]);
  CONFIGURE_FEATURELIST(chipType);
  /* update fragment len based on the chip type.*/
  phTmlNfc_IoCtl(phTmlNfc_e_setFragmentSize);
}

/*******************************************************************************
**
** Function         phNxpNciHal_UpdateFwStatus
**
** Description      It shall be called to update the FW download status to the
**                  libnfc-nci.
**
** Parameters       fwStatus: FW update status
**
** Returns          void
*******************************************************************************/
static void phNxpNciHal_UpdateFwStatus(HalNfcFwUpdateStatus fwStatus) {
  static phLibNfc_Message_t msg;
  static uint8_t status;
  if (RfFwRegionDnld_handle == NULL) {
    /* If proprietary feature not supported */
    return;
  }
  NXPLOG_NCIHAL_D("phNxpNciHal_UpdateFwStatus Enter");

  status = (uint8_t)fwStatus;
  msg.eMsgType = HAL_NFC_FW_UPDATE_STATUS_EVT;
  msg.pMsgData = &status;
  msg.Size = sizeof(status);
  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                        (phLibNfc_Message_t*)&msg);
  return;
}

/*******************************************************************************
**
** Function         phNxpNciHal_configureLxDebugMode
**
** Description      Helper function to configure LxDebug modes
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void phNxpNciHal_configureLxDebugMode() {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  unsigned long lx_debug_cfg = 0;
  uint8_t isfound = 0;
  static uint8_t cmd_lxdebug[] = {0x20, 0x02, 0x06, 0x01, 0xA0,
                                  0x1D, 0x02, 0x00, 0x00};

  isfound = GetNxpNumValue(NAME_NXP_CORE_PROP_SYSTEM_DEBUG, &lx_debug_cfg,
                           sizeof(lx_debug_cfg));

  if (isfound) {
    if (lx_debug_cfg & LX_DEBUG_CFG_MASK_RFU) {
      NXPLOG_NCIHAL_E(
          "One or more RFU bits are enabled.\nMasking the RFU bits");
      lx_debug_cfg = lx_debug_cfg & ~LX_DEBUG_CFG_MASK_RFU;
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_L1_EVENT) {
      NXPLOG_NCIHAL_D("Enable L1 RF NTF debugs");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_L2_EVENT) {
      NXPLOG_NCIHAL_D("Enable L2 RF NTF debugs (CE)");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_FELICA_RF) {
      NXPLOG_NCIHAL_D("Enable all Felica CM events");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_FELICA_SYSCODE) {
      NXPLOG_NCIHAL_D("Enable Felica System Code");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_L2_EVENT_READER) {
      NXPLOG_NCIHAL_D("Enable L2 RF NTF debugs (Reader)");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_MOD_DETECTED_EVENT) {
      NXPLOG_NCIHAL_D("Enable Modulation detected event");
    }
    if (lx_debug_cfg & LX_DEBUG_CFG_ENABLE_CMA_EVENTS) {
      NXPLOG_NCIHAL_D("Enable CMA events");
    }

    cmd_lxdebug[7] = (uint8_t)(lx_debug_cfg & LX_DEBUG_CFG_MASK);
    cmd_lxdebug[8] = (uint8_t)((lx_debug_cfg & LX_DEBUG_CFG_MASK) >> 8);
  }
  if (lx_debug_cfg == LX_DEBUG_CFG_DISABLE) {
    NXPLOG_NCIHAL_D("Disable LxDebug");
  }
  status = phNxpNciHal_send_ext_cmd(
      sizeof(cmd_lxdebug) / sizeof(cmd_lxdebug[0]), cmd_lxdebug);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Set lxDebug config failed");
  }
}

/*******************************************************************************
**
** Function         phNxpNciHal_initializeRegRfFwDnld(void)
**
** Description      Loads the module & initializes function pointers for Region
**                  based RF & FW update module
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void phNxpNciHal_initializeRegRfFwDnld() {
  // Getting pointer to RF & RF Region Code Download module
  RfFwRegionDnld_handle =
      dlopen("/system/vendor/lib64/libonebinary.so", RTLD_NOW);
  if (RfFwRegionDnld_handle == NULL) {
    NXPLOG_NCIHAL_D(
        "Error : opening (/system/vendor/lib64/libonebinary.so) !!");
    return;
  }
  if ((fpVerInfoStoreInEeprom = (fpVerInfoStoreInEeprom_t)dlsym(
           RfFwRegionDnld_handle, "read_version_info_and_store_in_eeprom")) ==
      NULL) {
    NXPLOG_NCIHAL_D(
        "Error while linking (read_version_info_and_store_in_eeprom) !!");
    return;
  }
  if ((fpRegRfFwDndl = (fpRegRfFwDndl_t)dlsym(RfFwRegionDnld_handle,
                                              "RegRfFwDndl")) == NULL) {
    NXPLOG_NCIHAL_D("Error while linking (RegRfFwDndl) !!");
    return;
  }
  if ((fpPropConfCover = (fpPropConfCover_t)dlsym(RfFwRegionDnld_handle,
                                                  "prop_conf_cover")) == NULL) {
    NXPLOG_NCIHAL_D("Error while linking (prop_conf_cover) !!");
    return;
  }
  if ((fpDoAntennaActivity = (fpDoAntennaActivity_t)dlsym(
           RfFwRegionDnld_handle, "DoAntennaActivity")) == NULL) {
    NXPLOG_NCIHAL_E("Error while linking (DoAntennaActivity) !!");
    return;
  }
}

/*******************************************************************************
**
** Function         phNxpNciHal_deinitializeRegRfFwDnld(void)
**
** Description      Resets the module handle & all the function pointers for
**                  Region based RF & FW update module
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void phNxpNciHal_deinitializeRegRfFwDnld() {
  if (RfFwRegionDnld_handle != NULL) {
    NXPLOG_NCIHAL_D("closing libonebinary.so");
    fpVerInfoStoreInEeprom = NULL;
    fpRegRfFwDndl = NULL;
    fpPropConfCover = NULL;
    dlclose(RfFwRegionDnld_handle);
    RfFwRegionDnld_handle = NULL;
  }
}

/******************************************************************************
 * Function         phNxpNciHal_setVerboseLogging
 *
 * Description      This function enables the nfc_debug_enabled
 *
 * Returns          void
 *
 *****************************************************************************/

void phNxpNciHal_setVerboseLogging(bool enable) { nfc_debug_enabled = enable; }

/******************************************************************************
 * Function         phNxpNciHal_getVerboseLogging
 *
 * Description      This function returns the value of nfc_debug_enabled
 *
 * Returns          void
 *
 *****************************************************************************/

bool phNxpNciHal_getVerboseLogging() { return nfc_debug_enabled; }
