/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright (C) 2013 ST Microelectronics S.A.
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
 *  Modified by ST Microelectronics S.A. (adaptation of nfc_nci.c for ST21NFC
 *NCI version)
 *
 ******************************************************************************/

#include <cutils/properties.h>
#include <errno.h>
#include <hardware/nfc.h>
#include <string.h>
#include <pthread.h>

#include "android_logmsg.h"
#include "halcore.h"

extern void HalCoreCallback(void* context, uint32_t event, const void* d,
                            size_t length);
extern bool I2cOpenLayer(void* dev, HAL_CALLBACK callb, HALHANDLE* pHandle);


typedef struct {
  struct nfc_nci_device nci_device;  // nci_device must be first struct member
  // below declarations are private variables within HAL
  nfc_stack_callback_t* p_cback;
  nfc_stack_data_callback_t* p_data_cback;
  HALHANDLE hHAL;
} st21nfc_dev_t;

char* halVersion = "ST21NFC NCI Version 3.0.5";
uint8_t cmd_set_nfc_mode_enable[] = {0x2f, 0x02, 0x02, 0x02, 0x01};
uint8_t hal_is_closed = 1;
pthread_mutex_t hal_mtx = PTHREAD_MUTEX_INITIALIZER;

uint8_t hal_dta_state = 0;


/*
 * NCI HAL method implementations. These must be overridden
 */

extern bool hal_wrapper_open(st21nfc_dev_t* dev, nfc_stack_callback_t* p_cback,
                             nfc_stack_data_callback_t* p_data_cback,
                             HALHANDLE* pHandle);

extern int hal_wrapper_close(int call_cb);

static int hal_open(const struct nfc_nci_device* p_dev,
                    nfc_stack_callback_t* p_cback,
                    nfc_stack_data_callback_t* p_data_cback) {
  bool result = false;

  STLOG_HAL_D("NFC-NCI HAL: %s %s", __func__, halVersion);

  (void)pthread_mutex_lock(&hal_mtx);
  st21nfc_dev_t* dev = (st21nfc_dev_t*)p_dev;
  if (! hal_is_closed ) {
    hal_wrapper_close(0);
  }
  dev->p_cback = p_cback;
  dev->p_data_cback = p_data_cback;

  hal_dta_state = 0;

  result = hal_wrapper_open(dev, p_cback, p_data_cback, &dev->hHAL);

  if (!result || !dev->hHAL)
    {
      dev->p_cback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
      (void) pthread_mutex_unlock(&hal_mtx);
      return -1;  // We are doomed, stop it here, NOW !
    }
  hal_is_closed = 0;
  (void)pthread_mutex_unlock(&hal_mtx);
  return 0;
}

static int hal_write(const struct nfc_nci_device* p_dev, uint16_t data_len,
                     const uint8_t* p_data) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  st21nfc_dev_t* dev = (st21nfc_dev_t*)p_dev;

  /* check if HAL is closed */
  int ret = (int)data_len;
  (void) pthread_mutex_lock(&hal_mtx);
  if (hal_is_closed)
    {
      ret = 0;
    }

  if (!ret)
    {
      (void) pthread_mutex_unlock(&hal_mtx);
      return ret;
    }
  if (!HalSendDownstream(dev->hHAL, p_data, data_len))
    {
      STLOG_HAL_E("NFC-NCI HAL: %s  SendDownstream failed", __func__);
      (void) pthread_mutex_unlock(&hal_mtx);
      return 0;
    }
  (void) pthread_mutex_unlock(&hal_mtx);

  return ret;
}

static int hal_core_initialized(const struct nfc_nci_device* p_dev,
                                uint8_t* p_core_init_rsp_params) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  (void)pthread_mutex_lock(&hal_mtx);
  st21nfc_dev_t* dev = (st21nfc_dev_t*)p_dev;
  hal_dta_state = *p_core_init_rsp_params;
  dev->p_cback(HAL_NFC_POST_INIT_CPLT_EVT, HAL_NFC_STATUS_OK);
  (void) pthread_mutex_unlock(&hal_mtx);

  return 0;  // return != 0 to signal ready immediate
}

static int hal_pre_discover(__attribute__((unused)) const struct nfc_nci_device* p_dev) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  return 0;  // false if no vendor-specific pre-discovery actions are needed
}

static int hal_close(__attribute__((unused)) const struct nfc_nci_device* p_dev) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  /* check if HAL is closed */
  (void)pthread_mutex_lock(&hal_mtx);
  if ( hal_is_closed ) {
    (void)pthread_mutex_unlock(&hal_mtx);
    return 1;
  }
  if (hal_wrapper_close(1) == 0) {
    hal_is_closed = 1;
    (void)pthread_mutex_unlock(&hal_mtx);
    return 1;
  }
  hal_is_closed = 1;
  (void)pthread_mutex_unlock(&hal_mtx);

  hal_dta_state = 0;

  return 0;
}

static int hal_control_granted(__attribute__((unused)) const struct nfc_nci_device* p_dev) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  return 0;
}

static int hal_power_cycle(const struct nfc_nci_device* p_dev) {
  STLOG_HAL_D("NFC-NCI HAL: %s", __func__);

  st21nfc_dev_t* dev = (st21nfc_dev_t*)p_dev;

  /* check if HAL is closed */
  int ret = HAL_NFC_STATUS_OK;
  (void) pthread_mutex_lock(&hal_mtx);
  if (hal_is_closed)
    {
      ret = HAL_NFC_STATUS_FAILED;
    }

  if (ret != HAL_NFC_STATUS_OK)
    {
      (void) pthread_mutex_unlock(&hal_mtx);
      return ret;
    }
  dev->p_cback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_OK);

  (void) pthread_mutex_unlock(&hal_mtx);
  return HAL_NFC_STATUS_OK;
}

/*
 * Generic device handling below
 */

/* Close an opened nfc device instance */
static int nfc_close(hw_device_t* dev) {
  (void) pthread_mutex_lock(&hal_mtx);
  free(dev);
  (void) pthread_mutex_unlock(&hal_mtx);
  return 0;
}

static int nfc_open(const hw_module_t* module, const char* name,
                    hw_device_t** device) {

  if (strcmp(name, NFC_NCI_CONTROLLER) == 0) {
    st21nfc_dev_t* dev = calloc(1, sizeof(st21nfc_dev_t));

    dev->nci_device.common.tag = HARDWARE_DEVICE_TAG;
    dev->nci_device.common.version = 0x00010000;  // [31:16] major, [15:0] minor
    dev->nci_device.common.module = (struct hw_module_t*)module;
    dev->nci_device.common.close = nfc_close;

    // NCI HAL method pointers
    dev->nci_device.open = hal_open;
    dev->nci_device.write = hal_write;
    dev->nci_device.core_initialized = hal_core_initialized;
    dev->nci_device.pre_discover = hal_pre_discover;
    dev->nci_device.close = hal_close;
    dev->nci_device.control_granted = hal_control_granted;
    dev->nci_device.power_cycle = hal_power_cycle;

    *device = (hw_device_t*)dev;

    // Initialize and get global logging level
    InitializeSTLogLevel();

    return 0;
  } else {
    return -EINVAL;
  }
}

static struct hw_module_methods_t nfc_module_methods = {
    .open = nfc_open,
};

struct nfc_nci_module_t HAL_MODULE_INFO_SYM = {
    .common =
        {
            .tag = HARDWARE_MODULE_TAG,
            .module_api_version = 0x0100,  // [15:8] major, [7:0] minor (1.0)
            .hal_api_version = 0x00,       // 0 is only valid value
            .id = "nfc_nci.st21nfc",
            .name = "ST Micro ST21NFC NCI HW HAL",
            .author = "ST Microelectronics SA ",
            .methods = &nfc_module_methods,
        },
};
