/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *  Copyright 2018 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#define LOG_TAG "StEse_HalApi"

#include "StEseApi.h"
#include <cutils/properties.h>
#include <ese_config.h>
#include <pthread.h>
#include "SpiLayerComm.h"
#include "T1protocol.h"
#include "android_logmsg.h"

/*********************** Global Variables *************************************/

/* ESE Context structure */
ese_Context_t ese_ctxt;

const char* halVersion = "ST54-SE HAL1.0 Version 1.0.22";

pthread_mutex_t mutex;

bool ConfRead = 0;
unsigned int PollInt_confvalue = 1000;
unsigned int BGT_confvalue = 1000;

/******************************************************************************
 * Function         StEseLog_InitializeLogLevel
 *
 * Description      This function is called during StEse_init to initialize
 *                  debug log level.
 *
 * Returns          None
 *
 ******************************************************************************/

void StEseLog_InitializeLogLevel() { InitializeSTLogLevel(); }

/******************************************************************************
 * Function         StEse_init
 *
 * Description      This function is called by Jni during the
 *                  initialization of the ESE. It opens the physical connection
 *                  with ESE and creates required client thread for
 *                  operation.
 * Returns          This function return ESESTATUS_SUCCES (0) in case of success
 *                  In case of failure returns other failure value.
 *
 ******************************************************************************/
ESESTATUS StEse_init() {
  SpiDriver_config_t tSpiDriver;
  ESESTATUS wConfigStatus = ESESTATUS_SUCCESS;
  int ret;

  char ese_dev_node[64];
  std::string ese_node;

  STLOG_HAL_D("%s : SteSE_open Enter halVersion = %s ", __func__, halVersion);
  /*When spi channel is already opened return status as FAILED*/
  if (ese_ctxt.EseLibStatus != ESE_STATUS_CLOSE) {
    STLOG_HAL_D("already opened\n");
    return ESESTATUS_BUSY;
  }

  memset(&ese_ctxt, 0x00, sizeof(ese_ctxt));
  memset(&tSpiDriver, 0x00, sizeof(tSpiDriver));

  /* initialize trace level */
  StEseLog_InitializeLogLevel();

  /*Read device node path*/
  ese_node = EseConfig::getString(NAME_ST_ESE_DEV_NODE, "/dev/st54j");
  strcpy(ese_dev_node, ese_node.c_str());
  tSpiDriver.pDevName = ese_dev_node;

  if (!ConfRead) {
    PollInt_confvalue =
        EseConfig::getUnsigned(NAME_ST_ESE_DEV_POLLING_INTERVAL, 1000);
    BGT_confvalue = EseConfig::getUnsigned(NAME_ST_ESE_DEV_BGT, 1000);
    ConfRead = true;
  }

  tSpiDriver.polling_interval = PollInt_confvalue;
  tSpiDriver.bgt = BGT_confvalue;

  /* Initialize SPI Driver layer */
  if (T1protocol_init(&tSpiDriver) != ESESTATUS_SUCCESS) {
    STLOG_HAL_E("T1protocol_init Failed");
    if (intptr_t(tSpiDriver.pDevHandle) > 0) {
      ese_ctxt.pDevHandle = tSpiDriver.pDevHandle;
    }
    goto clean_and_return;
  }
  /* Copying device handle to ESE Lib context*/
  ese_ctxt.pDevHandle = tSpiDriver.pDevHandle;

  ret = pthread_mutex_init(&mutex, NULL);
  if (ret != 0) {
    STLOG_HAL_E("HAL: %s pthread_mutex_init failed", __func__);
  }

  STLOG_HAL_D("wConfigStatus %x", wConfigStatus);
  ese_ctxt.EseLibStatus = ESE_STATUS_OPEN;
  return wConfigStatus;

clean_and_return:
  if (NULL != ese_ctxt.pDevHandle) {
    SpiLayerInterface_close(ese_ctxt.pDevHandle);
    memset(&ese_ctxt, 0x00, sizeof(ese_ctxt));
  }
  ese_ctxt.EseLibStatus = ESE_STATUS_CLOSE;
  return ESESTATUS_FAILED;
}

/******************************************************************************
 * Function         StEseApi_isOpen
 *
 * \brief           Check if the hal is opened
 *
 * \retval return false if it is close, otherwise true.
 *
 ******************************************************************************/
bool StEseApi_isOpen() {
  STLOG_HAL_D(" %s  status 0x%x \n", __FUNCTION__, ese_ctxt.EseLibStatus);
  return ese_ctxt.EseLibStatus != ESE_STATUS_CLOSE;
}

/******************************************************************************
 * Function         StEse_Transceive
 *
 * Description      This function update the len and provided buffer
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS StEse_Transceive(StEse_data* pCmd, StEse_data* pRsp) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  static int pTxBlock_len = 0;
  int retry_count = 0;
  uint16_t pCmdlen = pCmd->len;

  STLOG_HAL_D("%s : Enter EseLibStatus = %d ", __func__, ese_ctxt.EseLibStatus);

  if ((NULL == pCmd) || (NULL == pRsp)) return ESESTATUS_INVALID_PARAMETER;

  if ((pCmd->len == 0) || pCmd->p_data == NULL) {
    STLOG_HAL_E(" StEse_Transceive - Invalid Parameter no data\n");
    return ESESTATUS_INVALID_PARAMETER;
  } else if ((ESE_STATUS_CLOSE == ese_ctxt.EseLibStatus)) {
    STLOG_HAL_E(" %s ESE Not Initialized \n", __FUNCTION__);
    return ESESTATUS_NOT_INITIALISED;
  }

  STLOG_HAL_D(" %s ESE - No access, waiting \n", __FUNCTION__);
  pthread_mutex_lock(&mutex);

  STLOG_HAL_D(" %s ESE - Access granted, processing \n", __FUNCTION__);

  uint8_t* CmdPart = pCmd->p_data;

retry:
  while (pCmdlen > ATP.ifsc) {
    pTxBlock_len = ATP.ifsc;

    int rc = T1protocol_transcieveApduPart(CmdPart, pTxBlock_len, false,
                                           (StEse_data*)pRsp, I_block);

    if ((rc == -2) && (retry_count < 3)) {
      retry_count++;
      STLOG_HAL_E(" %s ESE - resync was needed, resend the whole frame retry"
                  " = %d\n", __FUNCTION__, retry_count);
      pCmdlen = pCmd->len;
      CmdPart = pCmd->p_data;
      goto retry;
    } else if (rc < 0) {
      STLOG_HAL_E(" %s ESE - Error, release access \n", __FUNCTION__);
      status = ESESTATUS_FAILED;
      retry_count = 0;
      pthread_mutex_unlock(&mutex);

      return status;
    } else {
      retry_count = 0;
    }

    pCmdlen -= pTxBlock_len;
    CmdPart = CmdPart + pTxBlock_len;
  }

  int rc = T1protocol_transcieveApduPart(CmdPart, pCmdlen, true,
                                         (StEse_data*)pRsp, I_block);
  if ((rc == -2) && (retry_count < 3)) {
    retry_count++;
    STLOG_HAL_E(" %s ESE - resync was needed, resend retry = %d\n",
                __FUNCTION__, retry_count);
    pCmdlen = pCmd->len;
    CmdPart = pCmd->p_data;
    goto retry;
  } else if (rc < 0)
    status = ESESTATUS_FAILED;

  if (ESESTATUS_SUCCESS != status) {
    STLOG_HAL_E(" %s T1protocol_transcieveApduPart- Failed \n", __FUNCTION__);
  }
  retry_count = 0;
  STLOG_HAL_D(" %s ESE - Processing complete, release access \n", __FUNCTION__);

  pthread_mutex_unlock(&mutex);

  STLOG_HAL_D(" %s Exit status 0x%x \n", __FUNCTION__, status);

  return status;
}

/******************************************************************************
 * Function         StEse_close
 *
 * Description      This function close the ESE interface and free all
 *                  resources.
 *
 * Returns          Always return ESESTATUS_SUCCESS (0).
 *
 ******************************************************************************/
ESESTATUS StEse_close(void) {
  ESESTATUS status = ESESTATUS_SUCCESS;

  if ((ESE_STATUS_CLOSE == ese_ctxt.EseLibStatus)) {
    STLOG_HAL_E(" %s ESE Not Initialized \n", __FUNCTION__);
    return ESESTATUS_NOT_INITIALISED;
  }

  if (NULL != ese_ctxt.pDevHandle) {
    SpiLayerInterface_close(ese_ctxt.pDevHandle);
    memset(&ese_ctxt, 0x00, sizeof(ese_ctxt));
    STLOG_HAL_D("StEse_close - ESE Context deinit completed");
    ese_ctxt.EseLibStatus = ESE_STATUS_CLOSE;
  }

  pthread_mutex_destroy(&mutex);
  /* Return success always */
  return status;
}

/******************************************************************************
 * Function         StEse_getAtr
 *
 * Description      This function get the last ATR received.
 *
 * Returns          pointer to the ATR array.
 *
 ******************************************************************************/
uint8_t* StEse_getAtr(void) {
  STLOG_HAL_D("%s : Enter", __func__);
  // The ATR is not supported by SPI in the secure element
  return nullptr;
}

/******************************************************************************
 * Function         StEse_Reset
 *
 * Description      This function resets the eSE SPI interface by sending a
 *                  SE reset and negotiating the ifs.
 *
 * Returns          ESESTATUS_SUCCESS is successful, ESESTATUS_SUCCESS otherwise
 *
 ******************************************************************************/
ESESTATUS StEse_Reset(void) {
  STLOG_HAL_D("%s : Enter", __func__);
  if (SpiLayerInterface_setup() != 0) {
    return ESESTATUS_FAILED;
  }

  return ESESTATUS_SUCCESS;
}
