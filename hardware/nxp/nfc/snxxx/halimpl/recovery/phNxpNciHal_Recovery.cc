/*
 * Copyright 2021-2023 NXP
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

#if (NXP_NFC_RECOVERY == TRUE)

#include "phNxpNciHal_Recovery.h"

#include <phDnldNfc.h>
#include <phNfcStatus.h>
#include <phNfcTypes.h>
#include <phNxpLog.h>
#include <phNxpNciHal.h>
#include <phNxpNciHal_Dnld.h>
#include <phNxpNciHal_ext.h>
#include <phOsalNfc_Timer.h>
#include <phTmlNfc.h>
#undef property_set
#undef PROPERTY_VALUE_MAX
#undef property_get
#include <cutils/properties.h>
#define MAX_CORE_RESET 3

extern phNxpNciProfile_Control_t nxpprofile_ctrl;
extern phNxpNciHal_Control_t nxpncihal_ctrl;
extern phTmlNfc_Context_t* gpphTmlNfc_Context;
extern void* phNxpNciHal_client_thread(void* arg);

static void phnxpNciHal_partialClose();
static NFCSTATUS phnxpNciHal_partialOpen();

// property name for storing boot time init status
const char* halInitProperty = "vendor.nfc.min_firmware";

/******************************************************************************
 * Function         getHalInitStatus
 *
 * Description      Get property whether it is boot init/not
 *
 * Parameters       Parameter to return the hal status is boot init/not.
 *
 * Returns          None
 *
 ******************************************************************************/
static void getHalInitStatus(char* halInitStatus) {
  NXPLOG_NCIHAL_D("Enter : %s", __func__);
  if (property_get(halInitProperty, halInitStatus, "Boot-time") != 0) {
    NXPLOG_NCIHAL_E("Error in property_get : %s", __func__);
  }
}

/******************************************************************************
 * Function         setHalInitStatus
 *
 * Description      To set property as per input whether it is boot init/not
 *
 * Parameters       status to be updated in property
 *
 * Returns          void
 *
 ******************************************************************************/
static void setHalInitStatus(const char* status) {
  NXPLOG_NCIHAL_E("Enter : %s", __func__);
  if (property_set(halInitProperty, status) != 0) {
    NXPLOG_NCIHAL_E("Error in property_set : %s", __func__);
  }
}

/******************************************************************************
 * Function         phNxpNciHal_read_callback
 *
 * Description      Callback function for read request to tml reader thread
 *
 * Parameters       pContext - context value passed while callback register
 *                  pInfo    - Information which contains status and response
 *                             buffers.
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_read_callback(void* pContext,
                                      phTmlNfc_TransactInfo_t* pInfo) {
  UNUSED_PROP(pContext);
  if (pInfo != NULL) {
    NXPLOG_NCIHAL_E("%s Status %d", __func__, pInfo->wStatus);
    if (pInfo->wStatus == NFCSTATUS_SUCCESS) {
      nxpncihal_ctrl.p_rx_data = pInfo->pBuff;
      nxpncihal_ctrl.rx_data_len = pInfo->wLength;
    }
    nxpncihal_ctrl.ext_cb_data.status = pInfo->wStatus;
  } else {
    nxpncihal_ctrl.ext_cb_data.status = NFCSTATUS_FAILED;
  }
  SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
}

/******************************************************************************
 * Function         phNxpNciHal_write_callback
 *
 * Description      Callback function for write request to tml writer thread
 *
 * Parameters       pContext - context value passed while callback register
 *                  pInfo    - Information which contains status and response
 *                             buffers.
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_write_callback(void* pContext,
                                       phTmlNfc_TransactInfo_t* pInfo) {
  UNUSED_PROP(pContext);
  if (pInfo != NULL) {
    if (pInfo->wStatus != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("write error status = 0x%x", pInfo->wStatus);
    }
    nxpncihal_ctrl.ext_cb_data.status = pInfo->wStatus;
  } else {
    nxpncihal_ctrl.ext_cb_data.status = NFCSTATUS_FAILED;
  }
  SEM_POST(&(nxpncihal_ctrl.ext_cb_data));
}

/******************************************************************************
 * Function         phNxpNciHal_semWaitTimeout
 *
 * Description      Helper function for global sem wait with timeout value
 *
 * Parameters       timeout - wait timeout in nanoseconds
 *
 * Returns          NFCSTATUS
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_semWaitTimeout(long timeout) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  int retVal = 0;
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  ts.tv_nsec += timeout;
  ts.tv_sec += ts.tv_nsec / 1000000000;
  ts.tv_nsec %= 1000000000;
  while ((retVal = sem_timedwait_monotonic_np(&nxpncihal_ctrl.ext_cb_data.sem, &ts)) == -1 &&
         errno == EINTR) {
    continue; /* Restart if interrupted by handler */
  }
  if (retVal == -1 && errno != ETIMEDOUT) {
    NXPLOG_NCIHAL_E("%s : sem_timedwait failed : errno = 0x%x", __func__,
                    errno);
  }
  if (retVal != -1) {
    status = nxpncihal_ctrl.ext_cb_data.status;
  } else if (errno == ETIMEDOUT && retVal == -1) {
    NXPLOG_NCIHAL_E("%s :timed out errno = 0x%x", __func__, errno);
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_writeCmd
 *
 * Description      Helper function to write command to NFCC
 *
 * Parameters       timeout - wait timeout in nanoseconds
 *
 * Returns          NFCSTATUS
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_writeCmd(uint16_t data_len, const uint8_t* p_data,
                                      long timeout) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  const char context[] = "RecoveryWrite";

  if (p_data == NULL) {
    NXPLOG_NCIHAL_E("Invalid Command Buffer");
    return NFCSTATUS_INVALID_PARAMETER;
  }
  /* Create local copy of cmd_data */
  memcpy(nxpncihal_ctrl.p_cmd_data, p_data, data_len);
  nxpncihal_ctrl.cmd_len = data_len;
  status = phTmlNfc_Write(
      (uint8_t*)nxpncihal_ctrl.p_cmd_data, (uint16_t)nxpncihal_ctrl.cmd_len,
      (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_write_callback,
      (void*)context);
  if (status == NFCSTATUS_PENDING) {
    return phNxpNciHal_semWaitTimeout(timeout);
  }
  NXPLOG_NCIHAL_E("tml write request failed");
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_ReadResponse
 *
 * Description      Helper function to read response from NFCC
 *
 * Parameters       len - response buffer len
 *                  rsp_buffer - Ptr to the response buffer
 *                  timeout - wait timeout in nanoseconds
 *
 * Returns          NFCSTATUS
 *
 ******************************************************************************/
static NFCSTATUS phNxpNciHal_ReadResponse(uint16_t* len, uint8_t** rsp_buffer,
                                          long timeout) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  const char context[] = "RecoveryRead";

  if (len == NULL) {
    NXPLOG_NCIHAL_E("%s Invalid Parameters", __func__);
    return NFCSTATUS_INVALID_PARAMETER;
  }
  status = phTmlNfc_Read(
      nxpncihal_ctrl.p_rsp_data, NCI_MAX_DATA_LEN,
      (pphTmlNfc_TransactCompletionCb_t)&phNxpNciHal_read_callback,
      (void*)context);
  if (phNxpNciHal_semWaitTimeout(timeout) == NFCSTATUS_SUCCESS) {
    if (nxpncihal_ctrl.p_rx_data != NULL && nxpncihal_ctrl.rx_data_len > 0) {
      *rsp_buffer = nxpncihal_ctrl.p_rx_data;
      *len = nxpncihal_ctrl.rx_data_len;
      status = NFCSTATUS_SUCCESS;
    } else
      status = NFCSTATUS_FAILED;
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_readNFCCClockCfgValues
 *
 * Description      Helper function to read clock configuration from
 *                  nfcc configuration file and stores value in global strcture
 *
 * Returns          void
 *
 ******************************************************************************/
static void phNxpNciHal_readNFCCClockCfgValues(void) {
  unsigned long num = 0;
  int isfound = 0;

  isfound = GetNxpNumValue(NAME_NXP_SYS_CLK_SRC_SEL, &num, sizeof(num));
  if (isfound > 0) nxpprofile_ctrl.bClkSrcVal = num;
  num = 0;
  isfound = 0;
  isfound = GetNxpNumValue(NAME_NXP_SYS_CLK_FREQ_SEL, &num, sizeof(num));
  if (isfound > 0) nxpprofile_ctrl.bClkFreqVal = num;
}

/******************************************************************************
 * Function         phNxpNciHal_determineChipType
 *
 * Description      Helper function to determine the chip info in nci mode
 *                  from NCI command and stores value in global strcture
 *
 * Returns          bool
 *
 ******************************************************************************/
static bool phNxpNciHal_determineChipType(void) {
  const uint8_t cmd_reset_nci[] = {0x20, 0x00, 0x01, 0x00};
  uint8_t* rsp_buffer = NULL;
  uint16_t rsp_len = 0;
  uint8_t retry = 0;
  bool status = false;

  do {
    if ((phNxpNciHal_writeCmd(sizeof(cmd_reset_nci), cmd_reset_nci,
                              WRITE_TIMEOUT_NS) != NFCSTATUS_SUCCESS)) {
      NXPLOG_NCIHAL_E("NCI_CORE_RESET Write Failure ");
      break;
    }
    // 10ms delay  for first core reset response to avoid nfcc standby
    usleep(NCI_RESET_RESP_READ_DELAY_US);
    if ((phNxpNciHal_ReadResponse(&rsp_len, &rsp_buffer,
                                  RESPONSE_READ_TIMEOUT_NS) !=
         NFCSTATUS_SUCCESS) ||
        (rsp_buffer == NULL)) {
      NXPLOG_NCIHAL_E("NCI_CORE_RESET read response failed");
      break;
    }
    if (rsp_buffer[NCI_RSP_IDX] == NCI_MSG_RSP) {
      if ((phNxpNciHal_ReadResponse(&rsp_len, &rsp_buffer,
                                    RESPONSE_READ_TIMEOUT_NS) !=
           NFCSTATUS_SUCCESS) ||
          (rsp_buffer == NULL)) {
        NXPLOG_NCIHAL_E("NCI_CORE_RESET NTF read failed");
        break;
      }
      if (rsp_buffer[NCI_RSP_IDX] == NCI_MSG_NTF) {
        phNxpNciHal_configFeatureList(rsp_buffer, rsp_len);
        status = true;
        break;
      }
    } else {
      NXPLOG_NCIHAL_E("NCI_CORE_RESPONSE Wrong Status");
    }
  } while (retry++ < MAX_CORE_RESET);
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_isSessionClosed
 *
 * Description      Helper function to determine download session state
 *
 * Returns          true means session closed
 *
 ******************************************************************************/
bool phNxpNciHal_isSessionClosed(void) {
  const uint8_t get_session_cmd[] = {0x00, 0x04, 0xF2, 0x00,
                                     0x00, 0x00, 0xF5, 0x33};
  uint8_t* rsp_buffer = NULL;
  uint16_t rsp_len = 0;

  if ((phNxpNciHal_writeCmd(sizeof(get_session_cmd), get_session_cmd,
                            WRITE_TIMEOUT_NS) == NFCSTATUS_SUCCESS)) {
    if ((phNxpNciHal_ReadResponse(&rsp_len, &rsp_buffer,
                                  RESPONSE_READ_TIMEOUT_NS) !=
         NFCSTATUS_SUCCESS) ||
        (rsp_buffer == NULL)) {
      NXPLOG_NCIHAL_E("Get Session read response failed");
    } else if (rsp_buffer[DL_RSP_STAT_IDX] == DL_MSG_STAT_RSP &&
               rsp_buffer[DL_RSP_IDX] == DL_MSG_RSP) {
      if (rsp_buffer[DL_RSP_SESS_IDX] == DL_SESSION_CLOSE_TAG) {
        return true;
      }
    }
  }
  return false;
}

/******************************************************************************
 * Function         phNxpNciHal_determineChipTypeDlMode
 *
 * Description      Helper function to determine the chip info in download mode
 *                  from get version command and stores value in global strcture
 *
 * Returns          bool
 *
 ******************************************************************************/
static bool phNxpNciHal_determineChipTypeDlMode(void) {
  const uint8_t get_version_cmd[] = {0x00, 0x04, 0xF1, 0x00,
                                     0x00, 0x00, 0x6E, 0xEF};
  uint8_t* rsp_buffer = NULL;
  uint16_t rsp_len = 0;

  if ((phNxpNciHal_writeCmd(sizeof(get_version_cmd), get_version_cmd,
                            WRITE_TIMEOUT_NS) == NFCSTATUS_SUCCESS)) {
    if ((phNxpNciHal_ReadResponse(&rsp_len, &rsp_buffer,
                                  RESPONSE_READ_TIMEOUT_NS) !=
         NFCSTATUS_SUCCESS) ||
        (rsp_buffer == NULL)) {
      NXPLOG_NCIHAL_E("Get Version read response failed");
    } else if (rsp_buffer[DL_RSP_STAT_IDX] == DL_MSG_STAT_RSP &&
               rsp_buffer[DL_RSP_IDX] == DL_MSG_RSP) {
      phNxpNciHal_configFeatureList(rsp_buffer, rsp_len);
      return true;
    }
  }
  return false;
}

/******************************************************************************
 * Function        phNxpNciHal_RecoverFWTearDown
 *
 * Description     Function to determine the NFCC state and recovery using
 *                 minimal fw download.
 *
 * Parameters      None
 *
 * Returns         SUCCESS if recovery is successful else FAIL.
 *
 ******************************************************************************/
void phNxpNciHal_RecoverFWTearDown(void) {
  uint8_t nfcc_recovery_support = 0x00;
  // status post boot completed
  const char* status = "Boot-completed";
  char halInitStatus[PROPERTY_VALUE_MAX] = {0};

  NXPLOG_NCIHAL_D("phNxpNciHal_RecoverFWTearDown(): enter \n");
  if (!GetNxpNumValue(NAME_NXP_NFCC_RECOVERY_SUPPORT, &nfcc_recovery_support,
                      sizeof(nfcc_recovery_support))) {
    NXPLOG_NCIHAL_E("Failed to read NXP_NFC_RECOVERY_SUPPORT config :");
  }
  if (nfcc_recovery_support == 0x00) {
    NXPLOG_NCIHAL_D("NFCC Recovery not supported");
    return;
  }

  // If this is not boot time invocation return
  getHalInitStatus(halInitStatus);
  if (strncmp(halInitStatus, status, PROPERTY_VALUE_MAX) == 0) {
    NXPLOG_NCIHAL_D("Not boot time, skip minimal FW download");
    return;
  } else {
    NXPLOG_NCIHAL_D("boot time, check minimal FW download required");
  }

  if (phnxpNciHal_partialOpen() != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Failed to Initialize Partial HAL for NFCC recovery \n");
    return;
  }
  if (phTmlNfc_IoCtl(phTmlNfc_e_PowerReset) != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Failed to Perform VEN RESET \n");
    phnxpNciHal_partialClose();
    return;
  }
  if (phNxpNciHal_determineChipType()) {
    NXPLOG_NCIHAL_D("Recovery not required \n");
    phnxpNciHal_partialClose();
    setHalInitStatus(status);
    return;
  }
  if (phTmlNfc_IoCtl(phTmlNfc_e_EnableDownloadModeWithVenRst) !=
      NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Enable Download mode failed");
    phnxpNciHal_partialClose();
    setHalInitStatus(status);
    return;
  }

  phTmlNfc_EnableFwDnldMode(true);
  bool bEnableNormalMode = true;
  if (!phNxpNciHal_determineChipTypeDlMode()) {
    NXPLOG_NCIHAL_E("Not able to determine chiptype");
  } else if (IS_CHIP_TYPE_NE(sn100u)) {
    NXPLOG_NCIHAL_E("Recovery not supported for chiptype (%d)", nfcFL.chipType);
  } else if (phNxpNciHal_isSessionClosed()) {
    NXPLOG_NCIHAL_D("FW Dnld session is closed");
  } else if (phNxpNciHal_fw_download_seq(nxpprofile_ctrl.bClkSrcVal,
                                         nxpprofile_ctrl.bClkFreqVal, 0,
                                         true) != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Minimal FW Update failed \n");
  } else {
    /* In the success case, the phNxpNciHal_fw_download_seq() will enable normal
     * mode */
    bEnableNormalMode = false;
  }
  if (bEnableNormalMode) {
    phTmlNfc_IoCtl(phTmlNfc_e_EnableNormalMode);
  }
  phTmlNfc_IoCtl(phTmlNfc_e_PowerReset);
  phnxpNciHal_partialClose();
  // Minimal FW not required in this boot session
  setHalInitStatus(status);
}

/*******************************************************************************
 *
 * Function         phnxpNciHal_partialOpenCleanUp
 *
 * Description      Helper function to cleanUp the Memory and flags from
 *                  phnxpNciHal_partialOpen
 *
 * Parameters       nfc_dev_node - dev node to be freed
 *
 * Returns          NFCSTATUS
 *******************************************************************************/
static int phnxpNciHal_partialOpenCleanUp(char* nfc_dev_node) {
  if (nfc_dev_node != NULL) {
    free(nfc_dev_node);
    nfc_dev_node = NULL;
  }
  /* Report error status */
  phNxpNciHal_cleanup_monitor();
  nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
  return NFCSTATUS_FAILED;
}

/*******************************************************************************
 *
 * Function         phnxpNciHal_partialOpen
 *
 * Description      Initialize the Minimal HAL
 *
 * Parameters       none
 *
 * Returns          NFCSTATUS
 *******************************************************************************/
static NFCSTATUS phnxpNciHal_partialOpen(void) {
  phOsalNfc_Config_t tOsalConfig;
  phTmlNfc_Config_t tTmlConfig;
  char* nfc_dev_node = NULL;

  NXPLOG_NCIHAL_D("phnxpNciHal_partialOpen(): enter");
  if (nxpncihal_ctrl.halStatus == HAL_STATUS_MIN_OPEN) {
    NXPLOG_NCIHAL_D("phNxpNciHal: already open");
    return NFCSTATUS_SUCCESS;
  }
  /* initialize trace level */
  phNxpLog_InitializeLogLevel();
  if (phNxpNciHal_init_monitor() == NULL) {
    NXPLOG_NCIHAL_E("Init monitor failed");
    return NFCSTATUS_FAILED;
  }
  /* Create the local semaphore */
  if (phNxpNciHal_init_cb_data(&nxpncihal_ctrl.ext_cb_data, NULL) !=
      NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("Create ext_cb_data failed");
    return NFCSTATUS_FAILED;
  }
  CONCURRENCY_LOCK();
  memset(&tOsalConfig, 0x00, sizeof(tOsalConfig));
  memset(&tTmlConfig, 0x00, sizeof(tTmlConfig));
  memset(&nxpprofile_ctrl, 0, sizeof(phNxpNciProfile_Control_t));

  /* By default HAL status is HAL_STATUS_OPEN */
  nxpncihal_ctrl.halStatus = HAL_STATUS_OPEN;

  /*nci version NCI_VERSION_2_0 version by default for SN100 chip type*/
  nxpncihal_ctrl.nci_info.nci_version = NCI_VERSION_2_0;
  /* Read the nfc device node name */
  nfc_dev_node = (char*)malloc(NXP_MAX_CONFIG_STRING_LEN * sizeof(char));
  if (nfc_dev_node == NULL) {
    NXPLOG_NCIHAL_D("malloc of nfc_dev_node failed ");
    CONCURRENCY_UNLOCK();
    return phnxpNciHal_partialOpenCleanUp(nfc_dev_node);
  } else if (!GetNxpStrValue(NAME_NXP_NFC_DEV_NODE, nfc_dev_node,
                             NXP_MAX_CONFIG_STRING_LEN)) {
    NXPLOG_NCIHAL_D(
        "Invalid nfc device node name keeping the default device node "
        "/dev/pn54x");
    strlcpy(nfc_dev_node, "/dev/pn54x",
            (NXP_MAX_CONFIG_STRING_LEN * sizeof(char)));
  }
  /* Configure hardware link */
  nxpncihal_ctrl.gDrvCfg.nClientId = phDal4Nfc_msgget(0, 0600);
  nxpncihal_ctrl.gDrvCfg.nLinkType = ENUM_LINK_TYPE_I2C; /* For PN54X */
  tTmlConfig.pDevName = (int8_t*)nfc_dev_node;
  tOsalConfig.dwCallbackThreadId = (uintptr_t)nxpncihal_ctrl.gDrvCfg.nClientId;
  tOsalConfig.pLogFile = NULL;
  tTmlConfig.dwGetMsgThreadId = (uintptr_t)nxpncihal_ctrl.gDrvCfg.nClientId;

  /* Initialize TML layer */
  if (phTmlNfc_Init(&tTmlConfig) != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("phTmlNfc_Init Failed");
    CONCURRENCY_UNLOCK();
    return phnxpNciHal_partialOpenCleanUp(nfc_dev_node);
  } else {
    if (nfc_dev_node != NULL) {
      free(nfc_dev_node);
      nfc_dev_node = NULL;
    }
  }
  /* Create the client thread */
  if (pthread_create(&nxpncihal_ctrl.client_thread, NULL,
                     phNxpNciHal_client_thread, &nxpncihal_ctrl) != 0) {
    NXPLOG_NCIHAL_E("pthread_create failed");
    if (phTmlNfc_Shutdown_CleanUp() != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("phTmlNfc_Shutdown_CleanUp: Failed");
    }
    CONCURRENCY_UNLOCK();
    return phnxpNciHal_partialOpenCleanUp(nfc_dev_node);
  }
  phNxpNciHal_readNFCCClockCfgValues();
  CONCURRENCY_UNLOCK();
  return NFCSTATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         phnxpNciHal_partialClose
 *
 * Description      close the Minimal HAL
 *
 * Parameters       none
 *
 * Returns          void
 *******************************************************************************/
static void phnxpNciHal_partialClose(void) {
  phLibNfc_Message_t msg;
  nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;

  if (NULL != gpphTmlNfc_Context->pDevHandle) {
    msg.eMsgType = NCI_HAL_CLOSE_CPLT_MSG;
    msg.pMsgData = NULL;
    msg.Size = 0;
    phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &msg);
    /* Abort any pending read and write */
    phTmlNfc_ReadAbort();
    phTmlNfc_WriteAbort();
    phTmlNfc_Shutdown();
    if (0 != pthread_join(nxpncihal_ctrl.client_thread, (void**)NULL)) {
      NXPLOG_TML_E("Fail to kill client thread!");
    }
    phTmlNfc_CleanUp();
    phDal4Nfc_msgrelease(nxpncihal_ctrl.gDrvCfg.nClientId);
    phNxpNciHal_cleanup_cb_data(&nxpncihal_ctrl.ext_cb_data);
    memset(&nxpncihal_ctrl, 0x00, sizeof(nxpncihal_ctrl));
    NXPLOG_NCIHAL_D("phnxpNciHal_partialClose - phOsalNfc_DeInit completed");
  }
  CONCURRENCY_UNLOCK();
  phNxpNciHal_cleanup_monitor();
}

#endif
