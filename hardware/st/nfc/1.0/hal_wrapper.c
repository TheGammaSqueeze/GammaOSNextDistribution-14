/******************************************************************************
 *
 *  Copyright (C) 2017 ST Microelectronics S.A.
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
#define LOG_TAG "NfcNciHalWrapper"
#include <cutils/properties.h>
#include <errno.h>
#include <hardware/nfc.h>
#include <string.h>
#include "android_logmsg.h"
#include "halcore.h"

extern void HalCoreCallback(void* context, uint32_t event, const void* d,
                            size_t length);
extern bool I2cOpenLayer(void* dev, HAL_CALLBACK callb, HALHANDLE* pHandle);
extern void I2cCloseLayer();

typedef struct {
  struct nfc_nci_device nci_device;  // nci_device must be first struct member
  // below declarations are private variables within HAL
  nfc_stack_callback_t* p_cback;
  nfc_stack_data_callback_t* p_data_cback;
  HALHANDLE hHAL;
} st21nfc_dev_t;

typedef enum {
  HAL_WRAPPER_STATE_CLOSED,
  HAL_WRAPPER_STATE_OPEN,
  HAL_WRAPPER_STATE_OPEN_CPLT,
  HAL_WRAPPER_STATE_NFC_ENABLE_ON,
  HAL_WRAPPER_STATE_READY
} hal_wrapper_state_e;

static void halWrapperDataCallback(uint16_t data_len, uint8_t* p_data);
static void halWrapperCallback(uint8_t event, uint8_t event_status);

nfc_stack_callback_t* mHalWrapperCallback = NULL;
nfc_stack_data_callback_t* mHalWrapperDataCallback = NULL;
hal_wrapper_state_e mHalWrapperState = HAL_WRAPPER_STATE_CLOSED;
HALHANDLE mHalHandle = NULL;

bool hal_wrapper_open(st21nfc_dev_t* dev, nfc_stack_callback_t* p_cback,
                      nfc_stack_data_callback_t* p_data_cback,
                      HALHANDLE* pHandle) {
  bool result;

  STLOG_HAL_D("%s", __func__);

  mHalWrapperState = HAL_WRAPPER_STATE_OPEN;

  mHalWrapperCallback = p_cback;
  mHalWrapperDataCallback = p_data_cback;

  dev->p_data_cback = halWrapperDataCallback;
  dev->p_cback = halWrapperCallback;

  result = I2cOpenLayer(dev, HalCoreCallback, pHandle);

  if (!result || !(*pHandle)) {
    return -1;  // We are doomed, stop it here, NOW !
  }

  mHalHandle = *pHandle;

  return 1;
}

int hal_wrapper_close(int call_cb) {

  STLOG_HAL_D("%s", __func__);

  mHalWrapperState = HAL_WRAPPER_STATE_CLOSED;
  I2cCloseLayer();
  if (call_cb)
  mHalWrapperCallback(HAL_NFC_CLOSE_CPLT_EVT, HAL_NFC_STATUS_OK);

  return 1;
}

void halWrapperDataCallback(uint16_t data_len, uint8_t* p_data) {
  uint8_t propNfcModeSetCmdOn[] = {0x2f, 0x02, 0x02, 0x02, 0x01};
  uint8_t coreInitCmd[] = {0x20, 0x01, 0x02, 0x00, 0x00};

  STLOG_HAL_D("%s - mHalWrapperState = %d", __func__, mHalWrapperState);

  switch (mHalWrapperState) {
    case HAL_WRAPPER_STATE_CLOSED:  // 0
      break;
    case HAL_WRAPPER_STATE_OPEN:  // 1
      // CORE_RESET_NTF
      if ((p_data[0] == 0x60) && (p_data[1] == 0x00)) {
        if (p_data[3] == 0x01) {
          mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_OK);
          mHalWrapperState = HAL_WRAPPER_STATE_OPEN_CPLT;
        } else if (p_data[3] == 0xa1)  // Loader mode
        {
          mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
        }
      } else {
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;
    case HAL_WRAPPER_STATE_OPEN_CPLT:  // 2
      // CORE_INIT_RSP
      if ((p_data[0] == 0x40) && (p_data[1] == 0x01)) {
      } else if ((p_data[0] == 0x60) && (p_data[1] == 0x06)) {
        STLOG_HAL_D("%s - Sending PROP_NFC_MODE_SET_CMD", __func__);
        // Send PROP_NFC_MODE_SET_CMD(ON)
        if (!HalSendDownstreamTimer(mHalHandle, propNfcModeSetCmdOn,
                                    sizeof(propNfcModeSetCmdOn), 100)) {
          STLOG_HAL_E("NFC-NCI HAL: %s  HalSendDownstreamTimer failed", __func__);
        }
        mHalWrapperState = HAL_WRAPPER_STATE_NFC_ENABLE_ON;
      } else {
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;
    case HAL_WRAPPER_STATE_NFC_ENABLE_ON:  // 3
      // PROP_NFC_MODE_SET_RSP
      if ((p_data[0] == 0x4f) && (p_data[1] == 0x02)) {
        // DO nothing: wait for core_reset_ntf or timer timeout
      }
      // CORE_RESET_NTF
      else if ((p_data[0] == 0x60) && (p_data[1] == 0x00)) {
        // Stop timer
        HalSendDownstreamStopTimer(mHalHandle);

        // Send CORE_INIT_CMD
        STLOG_HAL_D("%s - Sending CORE_INIT_CMD", __func__);
        if (!HalSendDownstream(mHalHandle, coreInitCmd, sizeof(coreInitCmd))) {
           STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
        }
      }
      // CORE_INIT_RSP
      else if ((p_data[0] == 0x40) && (p_data[1] == 0x01)) {
        STLOG_HAL_D("%s - NFC mode enabled", __func__);
        mHalWrapperState = HAL_WRAPPER_STATE_READY;
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;
    case HAL_WRAPPER_STATE_READY:
      mHalWrapperDataCallback(data_len, p_data);
      break;
  }
}

static void halWrapperCallback(uint8_t event, uint8_t event_status) {
  uint8_t coreInitCmd[] = {0x20, 0x01, 0x02, 0x00, 0x00};

  switch (mHalWrapperState) {
    case HAL_WRAPPER_STATE_CLOSED:
      break;

    case HAL_WRAPPER_STATE_OPEN:
      break;

    case HAL_WRAPPER_STATE_OPEN_CPLT:
      break;

    case HAL_WRAPPER_STATE_NFC_ENABLE_ON:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        // timeout
        // Send CORE_INIT_CMD
          STLOG_HAL_D("%s - Sending CORE_INIT_CMD", __func__);
        if (!HalSendDownstream(mHalHandle, coreInitCmd, sizeof(coreInitCmd))) {
            STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
        }
        return;
      }
      break;

    case HAL_WRAPPER_STATE_READY:
      break;

    default:
      break;
  }

  mHalWrapperCallback(event, event_status);
}
