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
#include <log/log.h>
#include <string.h>
#include <unistd.h>

#include "android_logmsg.h"
#include "hal_fd.h"
#include "hal_fwlog.h"
#include "halcore.h"
#include "st21nfc_dev.h"

extern void HalCoreCallback(void* context, uint32_t event, const void* d,
                            size_t length);
extern bool I2cOpenLayer(void* dev, HAL_CALLBACK callb, HALHANDLE* pHandle);
extern void I2cCloseLayer();
extern void I2cRecovery();

static void halWrapperDataCallback(uint16_t data_len, uint8_t* p_data);
static void halWrapperCallback(uint8_t event, uint8_t event_status);

nfc_stack_callback_t* mHalWrapperCallback = NULL;
nfc_stack_data_callback_t* mHalWrapperDataCallback = NULL;
hal_wrapper_state_e mHalWrapperState = HAL_WRAPPER_STATE_CLOSED;
HALHANDLE mHalHandle = NULL;

uint8_t mClfMode;
uint8_t mFwUpdateTaskMask;
int mRetryFwDwl;
uint8_t mFwUpdateResMask = 0;
uint8_t* ConfigBuffer = NULL;
uint8_t mError_count = 0;
bool mIsActiveRW = false;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t mutex_activerw = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t ready_cond = PTHREAD_COND_INITIALIZER;

static const uint8_t ApduGetAtr[] = {0x2F, 0x04, 0x05, 0x80,
                                     0x8A, 0x00, 0x00, 0x04};

static const uint8_t nciHeaderPropSetConfig[9] = {0x2F, 0x02, 0x98, 0x04, 0x00,
                                                  0x14, 0x01, 0x00, 0x92};
static uint8_t nciPropEnableFwDbgTraces[256];
static uint8_t nciPropGetFwDbgTracesConfig[] = {0x2F, 0x02, 0x05, 0x03,
                                                0x00, 0x14, 0x01, 0x00};
static uint8_t nciAndroidPassiveObserver[256];
static bool isDebuggable;

bool mReadFwConfigDone = false;

bool mHciCreditLent = false;
bool mfactoryReset = false;
bool ready_flag = 0;
bool mTimerStarted = false;
bool mFieldInfoTimerStarted = false;
bool forceRecover = false;
unsigned long hal_field_timer = 0;

static bool sEnableFwLog = false;
uint8_t mObserverMode = 0;
bool mObserverRsp = false;

void wait_ready() {
  pthread_mutex_lock(&mutex);
  while (!ready_flag) {
    pthread_cond_wait(&ready_cond, &mutex);
  }
  pthread_mutex_unlock(&mutex);
}

void set_ready(bool ready) {
  pthread_mutex_lock(&mutex);
  ready_flag = ready;
  pthread_cond_signal(&ready_cond);
  pthread_mutex_unlock(&mutex);
}

bool hal_wrapper_open(st21nfc_dev_t* dev, nfc_stack_callback_t* p_cback,
                      nfc_stack_data_callback_t* p_data_cback,
                      HALHANDLE* pHandle) {
  bool result;

  STLOG_HAL_D("%s", __func__);

  mFwUpdateResMask = hal_fd_init();
  mRetryFwDwl = 5;
  mFwUpdateTaskMask = 0;

  mHalWrapperState = HAL_WRAPPER_STATE_OPEN;
  mHciCreditLent = false;
  mReadFwConfigDone = false;
  mError_count = 0;

  mObserverMode = 0;
  mObserverRsp = false;

  mHalWrapperCallback = p_cback;
  mHalWrapperDataCallback = p_data_cback;

  dev->p_data_cback = halWrapperDataCallback;
  dev->p_cback = halWrapperCallback;

  result = I2cOpenLayer(dev, HalCoreCallback, pHandle);

  if (!result || !(*pHandle)) {
    return -1;  // We are doomed, stop it here, NOW !
  }

  isDebuggable = property_get_int32("ro.debuggable", 0);
  mHalHandle = *pHandle;

  HalSendDownstreamTimer(mHalHandle, 10000);

  return 1;
}

int hal_wrapper_close(int call_cb, int nfc_mode) {
  STLOG_HAL_V("%s - Sending PROP_NFC_MODE_SET_CMD(%d)", __func__, nfc_mode);
  uint8_t propNfcModeSetCmdQb[] = {0x2f, 0x02, 0x02, 0x02, (uint8_t)nfc_mode};

  mHalWrapperState = HAL_WRAPPER_STATE_CLOSING;
  // Send PROP_NFC_MODE_SET_CMD
  if (!HalSendDownstreamTimer(mHalHandle, propNfcModeSetCmdQb,
                              sizeof(propNfcModeSetCmdQb), 100)) {
    STLOG_HAL_E("NFC-NCI HAL: %s  HalSendDownstreamTimer failed", __func__);
    return -1;
  }
  // Let the CLF receive and process this
  usleep(50000);

  I2cCloseLayer();
  if (call_cb) mHalWrapperCallback(HAL_NFC_CLOSE_CPLT_EVT, HAL_NFC_STATUS_OK);

  return 1;
}

void hal_wrapper_send_core_config_prop() {
  long retlen = 0;
  int isfound = 0;

  // allocate buffer for setting parameters
  ConfigBuffer = (uint8_t*)malloc(256 * sizeof(uint8_t));
  if (ConfigBuffer != NULL) {
    isfound = GetByteArrayValue(NAME_CORE_CONF_PROP, (char*)ConfigBuffer, 256,
                                &retlen);

    if (isfound > 0) {
      STLOG_HAL_V("%s - Enter", __func__);
      set_ready(0);

      mHalWrapperState = HAL_WRAPPER_STATE_PROP_CONFIG;
      if (!HalSendDownstreamTimer(mHalHandle, ConfigBuffer, retlen, 1000)) {
        STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
      }
      wait_ready();
    }
    free(ConfigBuffer);
    ConfigBuffer = NULL;
  }
}

void hal_wrapper_send_vs_config() {
  STLOG_HAL_V("%s - Enter", __func__);
  set_ready(0);

  mReadFwConfigDone = true;
  if (!HalSendDownstreamTimer(mHalHandle, nciPropGetFwDbgTracesConfig,
                              sizeof(nciPropGetFwDbgTracesConfig), 1000)) {
    STLOG_HAL_E("%s - SendDownstream failed", __func__);
  }
  wait_ready();
}

void hal_wrapper_send_config() {
  hal_wrapper_send_core_config_prop();
  mHalWrapperState = HAL_WRAPPER_STATE_PROP_CONFIG;
  hal_wrapper_send_vs_config();
}

void hal_wrapper_factoryReset() {
  mfactoryReset = true;
  STLOG_HAL_V("%s - mfactoryReset = %d", __func__, mfactoryReset);
}

void hal_wrapper_set_observer_mode(uint8_t enable) {
  mObserverMode = enable;
  mObserverRsp = true;
}
void hal_wrapper_get_observer_mode() {
  mObserverRsp = true;
}
void hal_wrapper_update_complete() {
  STLOG_HAL_V("%s ", __func__);
  mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_OK);
  mHalWrapperState = HAL_WRAPPER_STATE_OPEN_CPLT;
}
void halWrapperDataCallback(uint16_t data_len, uint8_t* p_data) {
  uint8_t propNfcModeSetCmdOn[] = {0x2f, 0x02, 0x02, 0x02, 0x01};
  uint8_t coreInitCmd[] = {0x20, 0x01, 0x02, 0x00, 0x00};
  uint8_t coreResetCmd[] = {0x20, 0x00, 0x01, 0x01};
  unsigned long num = 0;
  unsigned long swp_log = 0;
  unsigned long rf_log = 0;
  int mObserverLength = 0;
  int nciPropEnableFwDbgTraces_size = sizeof(nciPropEnableFwDbgTraces);

  if (mObserverMode && (p_data[0] == 0x6f) && (p_data[1] == 0x02)) {
    // Firmware logs must not be formatted before sending to upper layer.
    if ((mObserverLength = notifyPollingLoopFrames(
             p_data, data_len, nciAndroidPassiveObserver)) > 0) {
      DispHal("RX DATA", (nciAndroidPassiveObserver), mObserverLength);
      mHalWrapperDataCallback(mObserverLength, nciAndroidPassiveObserver);
    }
  }

  switch (mHalWrapperState) {
    case HAL_WRAPPER_STATE_CLOSED:  // 0
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_CLOSED", __func__);
      break;
    case HAL_WRAPPER_STATE_OPEN:  // 1
      // CORE_RESET_NTF
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_OPEN", __func__);

      if ((p_data[0] == 0x60) && (p_data[1] == 0x00)) {
        mIsActiveRW = false;
        mFwUpdateTaskMask = ft_cmd_HwReset(p_data, &mClfMode);

        if (mfactoryReset == true) {
          STLOG_HAL_V(
              "%s - first boot after factory reset detected - start FW update",
              __func__);
          if ((mFwUpdateResMask & FW_PATCH_AVAILABLE) &&
              (mFwUpdateResMask & FW_CUSTOM_PARAM_AVAILABLE)) {
            mFwUpdateTaskMask = FW_UPDATE_NEEDED | CONF_UPDATE_NEEDED;
            mfactoryReset = false;
          }
        }
        STLOG_HAL_V(
            "%s - mFwUpdateTaskMask = %d,  mClfMode = %d,  mRetryFwDwl = %d",
            __func__, mFwUpdateTaskMask, mClfMode, mRetryFwDwl);
        // CLF in MODE LOADER & Update needed.
        if (mClfMode == FT_CLF_MODE_LOADER) {
          HalSendDownstreamStopTimer(mHalHandle);
          STLOG_HAL_V("%s --- CLF mode is LOADER ---", __func__);

          if (mRetryFwDwl == 0) {
            STLOG_HAL_V(
                "%s - Reached maximum nb of retries, FW update failed, exiting",
                __func__);
            mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
            I2cCloseLayer();
          } else {
            mHalWrapperState = HAL_WRAPPER_STATE_UPDATE;
            if (((p_data[3] == 0x01) && (p_data[8] == HW_ST54L)) ||
                ((p_data[2] == 0x41) && (p_data[3] == 0xA2))) {  // ST54L
              FwUpdateHandler(mHalHandle, data_len, p_data);
            } else {
              STLOG_HAL_V("%s - Send APDU_GET_ATR_CMD", __func__);
              mRetryFwDwl--;
              if (!HalSendDownstreamTimer(mHalHandle, ApduGetAtr,
                                          sizeof(ApduGetAtr),
                                          FW_TIMER_DURATION)) {
                STLOG_HAL_E("%s - SendDownstream failed", __func__);
              }
            }
          }
        } else if (mFwUpdateTaskMask == 0 || mRetryFwDwl == 0) {
          STLOG_HAL_V("%s - Proceeding with normal startup", __func__);
          if (p_data[3] == 0x01) {
            // Normal mode, start HAL
            mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_OK);
            mHalWrapperState = HAL_WRAPPER_STATE_OPEN_CPLT;
          } else {
            // No more retries or CLF not in correct mode
            mHalWrapperCallback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
          }
          // CLF in MODE ROUTER & Update needed.
        } else if (mClfMode == FT_CLF_MODE_ROUTER) {
          if ((mFwUpdateTaskMask & FW_UPDATE_NEEDED) &&
              (mFwUpdateResMask & FW_PATCH_AVAILABLE)) {
            STLOG_HAL_V(
                "%s - CLF in ROUTER mode, FW update needed, try upgrade FW -",
                __func__);
            mRetryFwDwl--;

            if (!HalSendDownstream(mHalHandle, coreResetCmd,
                                   sizeof(coreResetCmd))) {
              STLOG_HAL_E("%s - SendDownstream failed", __func__);
            }
            mHalWrapperState = HAL_WRAPPER_STATE_EXIT_HIBERNATE_INTERNAL;
          } else if ((mFwUpdateTaskMask & CONF_UPDATE_NEEDED) &&
                     (mFwUpdateResMask & FW_CUSTOM_PARAM_AVAILABLE)) {
            if (!HalSendDownstream(mHalHandle, coreResetCmd,
                                   sizeof(coreResetCmd))) {
              STLOG_HAL_E("%s - SendDownstream failed", __func__);
            }
            mHalWrapperState = HAL_WRAPPER_STATE_APPLY_CUSTOM_PARAM;
          } else if ((mFwUpdateTaskMask & UWB_CONF_UPDATE_NEEDED) &&
                     (mFwUpdateResMask & FW_UWB_PARAM_AVAILABLE)) {
            if (!HalSendDownstream(mHalHandle, coreResetCmd,
                                   sizeof(coreResetCmd))) {
              STLOG_HAL_E("%s - SendDownstream failed", __func__);
            }
            mHalWrapperState = HAL_WRAPPER_STATE_APPLY_UWB_PARAM;
          }
        }
      } else {
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;
    case HAL_WRAPPER_STATE_OPEN_CPLT:  // 2
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_OPEN_CPLT",
                  __func__);
      // CORE_INIT_RSP
      if ((p_data[0] == 0x40) && (p_data[1] == 0x01)) {
      } else if ((p_data[0] == 0x60) && (p_data[1] == 0x06)) {
        STLOG_HAL_V("%s - Sending PROP_NFC_MODE_SET_CMD", __func__);
        // Send PROP_NFC_MODE_SET_CMD(ON)
        mHalWrapperState = HAL_WRAPPER_STATE_NFC_ENABLE_ON;
        if (!HalSendDownstreamTimer(mHalHandle, propNfcModeSetCmdOn,
                                    sizeof(propNfcModeSetCmdOn), 500)) {
          STLOG_HAL_E("NFC-NCI HAL: %s  HalSendDownstreamTimer failed",
                      __func__);
        }
      } else {
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;

    case HAL_WRAPPER_STATE_NFC_ENABLE_ON:  // 3
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_NFC_ENABLE_ON",
                  __func__);
      // PROP_NFC_MODE_SET_RSP
      if ((p_data[0] == 0x4f) && (p_data[1] == 0x02)) {
        // DO nothing: wait for core_reset_ntf or timer timeout
      }
      // CORE_RESET_NTF
      else if ((p_data[0] == 0x60) && (p_data[1] == 0x00)) {
        // Stop timer
        HalSendDownstreamStopTimer(mHalHandle);
        if (forceRecover == true) {
          forceRecover = false;
          mHalWrapperDataCallback(data_len, p_data);
          break;
        }

        // Send CORE_INIT_CMD
        STLOG_HAL_V("%s - Sending CORE_INIT_CMD", __func__);
        if (!HalSendDownstream(mHalHandle, coreInitCmd, sizeof(coreInitCmd))) {
          STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
        }
      }
      // CORE_INIT_RSP
      else if ((p_data[0] == 0x40) && (p_data[1] == 0x01)) {
        STLOG_HAL_D("%s - NFC mode enabled", __func__);
        // Do we need to lend a credit ?
        if (p_data[13] == 0x00) {
          STLOG_HAL_D("%s - 1 credit lent", __func__);
          p_data[13] = 0x01;
          mHciCreditLent = true;
        }

        mHalWrapperState = HAL_WRAPPER_STATE_READY;
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;

    case HAL_WRAPPER_STATE_PROP_CONFIG:  // 4
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_PROP_CONFIG",
                  __func__);
      // CORE_SET_CONFIG_RSP
      if ((p_data[0] == 0x40) && (p_data[1] == 0x02)) {
        HalSendDownstreamStopTimer(mHalHandle);
        set_ready(1);

        STLOG_HAL_V("%s - Received config RSP, read FW dDBG config", __func__);
      } else if (mHciCreditLent && (p_data[0] == 0x60) && (p_data[1] == 0x06)) {
        // CORE_CONN_CREDITS_NTF
        if (p_data[4] == 0x01) {  // HCI connection
          mHciCreditLent = false;
          STLOG_HAL_D("%s - credit returned", __func__);
          if (p_data[5] == 0x01) {
            // no need to send this.
            break;
          } else {
            if (p_data[5] != 0x00 && p_data[5] != 0xFF) {
              // send with 1 less
              p_data[5]--;
            }
          }
        }
        mHalWrapperDataCallback(data_len, p_data);
      } else if (p_data[0] == 0x4f) {
        // PROP_RSP
        if (mReadFwConfigDone == true) {
          mReadFwConfigDone = false;
          HalSendDownstreamStopTimer(mHalHandle);
          set_ready(1);
          // NFC_STATUS_OK
          if (p_data[3] == 0x00) {
            bool confNeeded = false;
            bool firmware_debug_enabled =
                property_get_int32("persist.vendor.nfc.firmware_debug_enabled", 0);

            // Check if FW DBG shall be set
            if (GetNumValue(NAME_STNFC_FW_DEBUG_ENABLED, &num, sizeof(num)) ||
                isDebuggable || sEnableFwLog) {
              if (firmware_debug_enabled || sEnableFwLog) {
                num = 1;
                swp_log = 30;
                rf_log = 15;
              }
              if (num == 1) {
                GetNumValue(NAME_STNFC_FW_SWP_LOG_SIZE, &swp_log,
                            sizeof(swp_log));
                GetNumValue(NAME_STNFC_FW_RF_LOG_SIZE, &rf_log, sizeof(rf_log));
              }
              // limit swp and rf payload length between 4 and 30.
              if (swp_log > 30)
                swp_log = 30;
              else if (swp_log < 4)
                swp_log = 4;

              if (rf_log > 30)
                rf_log = 30;
              else if (rf_log < 4)
                rf_log = 4;

              if ((rf_log || swp_log) &&
                  ((p_data[15] != rf_log) || (p_data[17] != swp_log))) {
                STLOG_HAL_D("%s - FW DBG payload traces changes needed",
                            __func__);
                confNeeded = true;
              }

              // If conf file indicate set needed and not yet enabled
              if ((num == 1) && (p_data[7] == 0x00)) {
                STLOG_HAL_D("%s - FW DBG traces enabling needed", __func__);
                nciPropEnableFwDbgTraces[9] = 0x01;
                confNeeded = true;
              } else if ((num == 0) && (p_data[7] == 0x01)) {
                STLOG_HAL_D("%s - FW DBG traces disabling needed", __func__);
                nciPropEnableFwDbgTraces[9] = 0x00;
                confNeeded = true;
              } else {
                STLOG_HAL_D(
                    "%s - No FW DBG traces enable/disable change needed",
                    __func__);
              }

              if (data_len < 9 || p_data[6] == 0 || p_data[6] < (data_len - 7)
                  || p_data[6] > (sizeof(nciPropEnableFwDbgTraces) - 9)) {
                if (confNeeded) {
                  android_errorWriteLog(0x534e4554, "169328517");
                  confNeeded = false;
                }
              }

              if (confNeeded) {
                memcpy(nciPropEnableFwDbgTraces, nciHeaderPropSetConfig, 9);
                memcpy(&nciPropEnableFwDbgTraces[10], &p_data[8],
                       p_data[6] - 1);
                if (rf_log || swp_log) {
                  nciPropEnableFwDbgTraces[9] = (uint8_t)num;
                  nciPropEnableFwDbgTraces[17] = (uint8_t)rf_log;
                  nciPropEnableFwDbgTraces[19] = (uint8_t)swp_log;
                }
                if ((9 + p_data[6]) < sizeof(nciPropEnableFwDbgTraces)) {
                  nciPropEnableFwDbgTraces_size = 9 + p_data[6];
                }

                confNeeded = false;

                if (!HalSendDownstream(mHalHandle, nciPropEnableFwDbgTraces,
                                       nciPropEnableFwDbgTraces_size)) {
                  STLOG_HAL_E("%s - SendDownstream failed", __func__);
                }
                mHalWrapperState = HAL_WRAPPER_STATE_APPLY_PROP_CONFIG;
                break;
              }
            }
          }
        }
        GetNumValue(NAME_STNFC_REMOTE_FIELD_TIMER, &hal_field_timer,
                    sizeof(hal_field_timer));
        STLOG_HAL_D("%s - hal_field_timer = %lu", __func__, hal_field_timer);
        // Exit state, all processing done
        mHalWrapperCallback(HAL_NFC_POST_INIT_CPLT_EVT, HAL_NFC_STATUS_OK);
        mHalWrapperState = HAL_WRAPPER_STATE_READY;
      }
      break;

    case HAL_WRAPPER_STATE_READY:  // 5
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_READY", __func__);
      if (mObserverRsp) {
        if ((p_data[0] == 0x40) && (p_data[1] == 0x02)) {
          uint8_t rsp_status = p_data[3];
          mObserverRsp = false;
          p_data[0] = 0x4f;
          p_data[1] = 0x0c;
          p_data[2] = 0x02;
          p_data[3] = 0x02;
          p_data[4] = rsp_status;
          data_len = 0x5;
        } else if ((p_data[0] == 0x40) && (p_data[1] == 0x03) && (data_len > 7)) {
            uint8_t rsp_status = p_data[3];
            mObserverRsp = false;
            if (p_data[7] != mObserverMode) {
                STLOG_HAL_E("mObserverMode got out of sync");
                mObserverMode = p_data[7];
            }
            p_data[0] = 0x4f;
            p_data[1] = 0x0c;
            p_data[2] = 0x03;
            p_data[3] = 0x04;
            p_data[4] = rsp_status;
            p_data[5] =  p_data[7];
            data_len = 0x6;
          }
      }
      if (!((p_data[0] == 0x60) && (p_data[3] == 0xa0))) {
        if (mHciCreditLent && (p_data[0] == 0x60) && (p_data[1] == 0x06)) {
          if (p_data[4] == 0x01) {  // HCI connection
            mHciCreditLent = false;
            STLOG_HAL_D("%s - credit returned", __func__);
            if (p_data[5] == 0x01) {
              // no need to send this.
              break;
            } else {
              if (p_data[5] != 0x00 && p_data[5] != 0xFF) {
                // send with 1 less
                p_data[5]--;
              }
            }
          }
        } else if ((p_data[0] == 0x61) && (p_data[1] == 0x07)) {
          // RF_FIELD_INFO_NTF
          if (p_data[3] == 0x01) {  // field on
            // start timer
            if (hal_field_timer) {
              mFieldInfoTimerStarted = true;
              HalSendDownstreamTimer(mHalHandle, 20000);
            }
          } else if (p_data[3] == 0x00) {
            if (mFieldInfoTimerStarted) {
              HalSendDownstreamStopTimer(mHalHandle);
              mFieldInfoTimerStarted = false;
            }
          }
        } else if ((p_data[0] == 0x6f) && (p_data[1] == 0x05)) {
          (void)pthread_mutex_lock(&mutex_activerw);
          // start timer
          mTimerStarted = true;
          mIsActiveRW = true;
          HalSendDownstreamTimer(mHalHandle, 5000);
          (void)pthread_mutex_unlock(&mutex_activerw);
        } else if ((p_data[0] == 0x6f) && (p_data[1] == 0x06)) {
          (void)pthread_mutex_lock(&mutex_activerw);
          // stop timer
          if (mTimerStarted) {
            HalSendDownstreamStopTimer(mHalHandle);
            mTimerStarted = false;
          }
          if(mIsActiveRW == true) {
            mIsActiveRW = false;
          } else {
            mError_count ++;
            STLOG_HAL_E("Error Act -> Act count=%d", mError_count);
            if(mError_count > 20) {
              mError_count = 0;
              STLOG_HAL_E("NFC Recovery Start");
              mTimerStarted = true;
              HalSendDownstreamTimer(mHalHandle, 1);
            }
          }
          (void)pthread_mutex_unlock(&mutex_activerw);
        } else if (((p_data[0] == 0x61) && (p_data[1] == 0x05)) ||
                   ((p_data[0] == 0x61) && (p_data[1] == 0x03))) {
          mError_count = 0;
          // stop timer
          if (mFieldInfoTimerStarted) {
            HalSendDownstreamStopTimer(mHalHandle);
            mFieldInfoTimerStarted = false;
          }
          if (mTimerStarted) {
            HalSendDownstreamStopTimer(mHalHandle);
            mTimerStarted = false;
          }
        } else if (p_data[0] == 0x60 && p_data[1] == 0x00) {
          p_data[3] = 0x0;  // Only reset trigger that should be received in
                            // HAL_WRAPPER_STATE_READY is unreocoverable error.

        } else if (data_len >= 4 && p_data[0] == 0x60 && p_data[1] == 0x07) {
          if (p_data[3] == 0xE1) {
            // Core Generic Error - Buffer Overflow Ntf - Restart all
            STLOG_HAL_E("Core Generic Error - restart");
            p_data[0] = 0x60;
            p_data[1] = 0x00;
            p_data[2] = 0x03;
            p_data[3] = 0xE1;
            p_data[4] = 0x00;
            p_data[5] = 0x00;
            data_len = 0x6;
          } else if (p_data[3] == 0xE6) {
            unsigned long hal_ctrl_clk = 0;
            GetNumValue(NAME_STNFC_CONTROL_CLK, &hal_ctrl_clk,
                        sizeof(hal_ctrl_clk));
            if (hal_ctrl_clk) {
              STLOG_HAL_E("%s - Clock Error - restart", __func__);
              // Core Generic Error
              p_data[0] = 0x60;
              p_data[1] = 0x00;
              p_data[2] = 0x03;
              p_data[3] = 0xE6;
              p_data[4] = 0x00;
              p_data[5] = 0x00;
              data_len = 0x6;
            }
          } else if (p_data[3] == 0xA1) {
            if (mFieldInfoTimerStarted) {
              HalSendDownstreamStopTimer(mHalHandle);
              mFieldInfoTimerStarted = false;
            }
          }
        }
        mHalWrapperDataCallback(data_len, p_data);
      } else {
        STLOG_HAL_V("%s - Core reset notification - Nfc mode ", __func__);
      }
      break;

    case HAL_WRAPPER_STATE_CLOSING:  // 6
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_CLOSING",
                  __func__);
      hal_fd_close();
      if ((p_data[0] == 0x4f) && (p_data[1] == 0x02)) {
        // intercept this expected message, don t forward.
        mHalWrapperState = HAL_WRAPPER_STATE_CLOSED;
      } else {
        mHalWrapperDataCallback(data_len, p_data);
      }
      break;

    case HAL_WRAPPER_STATE_EXIT_HIBERNATE_INTERNAL:  // 6
      STLOG_HAL_V(
          "%s - mHalWrapperState = HAL_WRAPPER_STATE_EXIT_HIBERNATE_INTERNAL",
          __func__);
      ExitHibernateHandler(mHalHandle, data_len, p_data);
      break;

    case HAL_WRAPPER_STATE_UPDATE:  // 7
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_UPDATE", __func__);
      FwUpdateHandler(mHalHandle, data_len, p_data);
      break;
    case HAL_WRAPPER_STATE_APPLY_CUSTOM_PARAM:  // 8
      STLOG_HAL_V(
          "%s - mHalWrapperState = HAL_WRAPPER_STATE_APPLY_CUSTOM_PARAM",
          __func__);
      ApplyCustomParamHandler(mHalHandle, data_len, p_data);
      break;
    case HAL_WRAPPER_STATE_APPLY_UWB_PARAM:  // 9
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_APPLY_UWB_PARAM",
                  __func__);
      ApplyUwbParamHandler(mHalHandle, data_len, p_data);
      break;
    case HAL_WRAPPER_STATE_SET_ACTIVERW_TIMER:  // 10
      (void)pthread_mutex_lock(&mutex_activerw);
      if (mIsActiveRW == true) {
        STLOG_HAL_D(
            "%s - mHalWrapperState = "
            "HAL_WRAPPER_STATE_SET_ACTIVERW_TIMER",
            __func__);
        // start timer
        mTimerStarted = true;
        HalSendDownstreamTimer(mHalHandle, 5000);
        // Chip state should back to Active
        // at screen off state.
      }
      (void)pthread_mutex_unlock(&mutex_activerw);
      mHalWrapperState = HAL_WRAPPER_STATE_READY;
      mHalWrapperDataCallback(data_len, p_data);
      break;

    case HAL_WRAPPER_STATE_APPLY_PROP_CONFIG:
      STLOG_HAL_V("%s - mHalWrapperState = HAL_WRAPPER_STATE_APPLY_PROP_CONFIG",
                  __func__);
      if (p_data[0] == 0x4f) {
        I2cResetPulse();
      } else if ((p_data[0] == 0x60) && (p_data[1] == 0x00)) {
        // Send CORE_INIT_CMD
        STLOG_HAL_D("%s - Sending CORE_INIT_CMD", __func__);
        if (!HalSendDownstream(mHalHandle, coreInitCmd, sizeof(coreInitCmd))) {
          STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
        }
      }
      // CORE_INIT_RSP
      else if ((p_data[0] == 0x40) && (p_data[1] == 0x01)) {
        // Exit state, all processing done
        mHalWrapperCallback(HAL_NFC_POST_INIT_CPLT_EVT, HAL_NFC_STATUS_OK);
        mHalWrapperState = HAL_WRAPPER_STATE_READY;
      }
      break;
    case HAL_WRAPPER_STATE_RECOVERY:
      break;
  }
}

static void halWrapperCallback(uint8_t event, __attribute__((unused))uint8_t event_status) {
  uint8_t coreInitCmd[] = {0x20, 0x01, 0x02, 0x00, 0x00};
  uint8_t rfDeactivateCmd[] = {0x21, 0x06, 0x01, 0x00};
  uint8_t p_data[6];
  uint16_t data_len;

  switch (mHalWrapperState) {
    case HAL_WRAPPER_STATE_CLOSING:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        STLOG_HAL_D("NFC-NCI HAL: %s  Timeout. Close anyway", __func__);
        HalSendDownstreamStopTimer(mHalHandle);
        hal_fd_close();
        mHalWrapperState = HAL_WRAPPER_STATE_CLOSED;
        return;
      }
      break;

    case HAL_WRAPPER_STATE_OPEN:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        STLOG_HAL_D("NFC-NCI HAL: %s  Timeout accessing the CLF.", __func__);
        HalSendDownstreamStopTimer(mHalHandle);
        I2cRecovery();
        abort(); // TODO: fix it when we have a better recovery method.
        return;
      }
      break;

    case HAL_WRAPPER_STATE_CLOSED:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        STLOG_HAL_D("NFC-NCI HAL: %s  Timeout. Close anyway", __func__);
        HalSendDownstreamStopTimer(mHalHandle);
        return;
      }
      break;

    case HAL_WRAPPER_STATE_UPDATE:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        STLOG_HAL_E("%s - Timer for FW update procedure timeout, retry",
                    __func__);
        abort(); // TODO: fix it when we have a better recovery method.
        HalSendDownstreamStopTimer(mHalHandle);
        resetHandlerState();
        I2cResetPulse();
        mHalWrapperState = HAL_WRAPPER_STATE_OPEN;
      }
      break;

    case HAL_WRAPPER_STATE_NFC_ENABLE_ON:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        // timeout
        // Send CORE_INIT_CMD
        STLOG_HAL_V("%s - Sending CORE_INIT_CMD", __func__);
        if (!HalSendDownstream(mHalHandle, coreInitCmd, sizeof(coreInitCmd))) {
          STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
        }
        return;
      }
      break;
    case HAL_WRAPPER_STATE_PROP_CONFIG:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        STLOG_HAL_E("%s - Timer when sending conf parameters, retry", __func__);
        abort(); // TODO: fix it when we have a better recovery method.
        HalSendDownstreamStopTimer(mHalHandle);
        resetHandlerState();
        I2cResetPulse();
        mHalWrapperState = HAL_WRAPPER_STATE_OPEN;
      }
      break;

    case HAL_WRAPPER_STATE_READY:
      if (event == HAL_WRAPPER_TIMEOUT_EVT) {
        if (mTimerStarted || mFieldInfoTimerStarted) {
          STLOG_HAL_E("NFC-NCI HAL: %s  Timeout.. Recover!", __func__);
          STLOG_HAL_E("%s mIsActiveRW = %d", __func__, mIsActiveRW);
          HalSendDownstreamStopTimer(mHalHandle);
          mTimerStarted = false;
          mFieldInfoTimerStarted = false;
          // forceRecover = true;
          resetHandlerState();
          if (!HalSendDownstream(mHalHandle, rfDeactivateCmd,
                                 sizeof(rfDeactivateCmd))) {
            STLOG_HAL_E("%s - SendDownstream failed", __func__);
          }
          usleep(10000);
          // Core Generic Error

          p_data[0] = 0x60;
          p_data[1] = 0x00;
          p_data[2] = 0x03;
          p_data[3] = 0xAA;
          p_data[4] = 0x00;
          p_data[5] = 0x00;
          data_len = 0x6;
          mHalWrapperDataCallback(data_len, p_data);
          mHalWrapperState = HAL_WRAPPER_STATE_RECOVERY;
        }
        return;
      }
      break;

    default:
      break;
  }

  mHalWrapperCallback(event, event_status);
}

/*******************************************************************************
 **
 ** Function         nfc_set_state
 **
 ** Description      Set the state of NFC stack
 **
 ** Returns          void
 **
 *******************************************************************************/
void hal_wrapper_set_state(hal_wrapper_state_e new_wrapper_state) {
  ALOGD("nfc_set_state %d->%d", mHalWrapperState, new_wrapper_state);

  mHalWrapperState = new_wrapper_state;
}

/*******************************************************************************
 **
 ** Function         hal_wrapper_setFwLogging
 **
 ** Description      Enable the FW log by hte GUI if needed.
 **
 ** Returns          void
 **
 *******************************************************************************/
void hal_wrapper_setFwLogging(bool enable) {
  ALOGD("%s : enable = %d", __func__, enable);

  sEnableFwLog = enable;
}
