/******************************************************************************
 *
 *  Copyright (C) 2018 The Linux Foundation
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
 ******************************************************************************/

#include <bluetooth/log.h>

#include "bta_sec_api.h"
#include "btif_storage.h"
#include "device/include/device_iot_config.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "stack/include/btm_ble_api.h"

using namespace bluetooth;

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/
#define COD_UNCLASSIFIED ((0x1F) << 8)

/*******************************************************************************
 *
 * Function         btif_iot_save_pair_type
 *
 * Description      Store remote pair type to iot conf file
 *
 * Returns          void
 *
 *******************************************************************************/
static void btif_iot_save_pair_type(const RawAddress& bdaddr, bool is_ble,
                                    bool is_ssp) {
  if (is_ssp) {
    if (!is_ble)
      DEVICE_IOT_CONFIG_ADDR_SET_INT(bdaddr, IOT_CONF_KEY_PAIRTYPE,
                                     IOT_CONF_VAL_PAIR_TYPE_SSP);
    else
      DEVICE_IOT_CONFIG_ADDR_SET_INT(bdaddr, IOT_CONF_KEY_LE_PAIRTYPE,
                                     IOT_CONF_VAL_LE_PAIRTYPE_SECURE);
  } else {
    if (!is_ble)
      DEVICE_IOT_CONFIG_ADDR_SET_INT(bdaddr, IOT_CONF_KEY_PAIRTYPE,
                                     IOT_CONF_VAL_PAIR_TYPE_LEGACY);
    else
      DEVICE_IOT_CONFIG_ADDR_SET_INT(bdaddr, IOT_CONF_KEY_LE_PAIRTYPE,
                                     IOT_CONF_VAL_LE_PAIRTYPE_LEGACY);
  }
}

/*******************************************************************************
 *
 * Function         btif_iot_update_remote_info
 *
 * Description      Store remote dev info to iot conf file
 *
 * Returns          void
 *
 *******************************************************************************/
void btif_iot_update_remote_info(tBTA_DM_AUTH_CMPL* p_auth_cmpl, bool is_ble,
                                 bool is_ssp) {
  int name_length = 0;
  char value[1024];
  BD_NAME bd_name;
  int num_properties = 0;
  bt_property_t properties[2];
  uint32_t cod = 0;
  uint8_t lmp_ver = 0;
  uint16_t lmp_subver = 0;
  uint16_t mfct_set = 0;
  tBTM_STATUS btm_status;

  // save remote name to iot conf file
  if (strlen((const char*)p_auth_cmpl->bd_name)) {
    name_length = strlen((char*)p_auth_cmpl->bd_name) > BTM_MAX_LOC_BD_NAME_LEN
                      ? BTM_MAX_LOC_BD_NAME_LEN
                      : strlen((char*)p_auth_cmpl->bd_name) + 1;
    strncpy(value, (char*)p_auth_cmpl->bd_name, name_length);
    DEVICE_IOT_CONFIG_ADDR_SET_STR(p_auth_cmpl->bd_addr,
                                   IOT_CONF_KEY_REMOTE_NAME, value);
  } else {
    if (BTM_GetRemoteDeviceName(p_auth_cmpl->bd_addr, bd_name)) {
      DEVICE_IOT_CONFIG_ADDR_SET_STR(p_auth_cmpl->bd_addr,
                                     IOT_CONF_KEY_REMOTE_NAME, (char*)bd_name);
    }
  }

  // save remote dev class to iot conf file
  // Try to retrieve cod from storage
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                             BT_PROPERTY_CLASS_OF_DEVICE, sizeof(cod), &cod);
  if (btif_storage_get_remote_device_property(&p_auth_cmpl->bd_addr,
                                              &properties[num_properties]) ==
      BT_STATUS_SUCCESS)
    log::verbose("cod retrieved from storage is 0x{:06x}", cod);
  if (cod == 0) {
    log::verbose("cod is 0, set as unclassified");
    cod = COD_UNCLASSIFIED;
  }
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr, IOT_CONF_KEY_DEVCLASS,
                                 (int)cod);
  num_properties++;

  // save remote dev type to iot conf file
  bt_device_type_t dev_type;
  uint32_t remote_dev_type;
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_properties],
                             BT_PROPERTY_TYPE_OF_DEVICE, sizeof(uint32_t),
                             &remote_dev_type);
  if (btif_storage_get_remote_device_property(&p_auth_cmpl->bd_addr,
                                              &properties[num_properties]) ==
      BT_STATUS_SUCCESS) {
    log::verbose("retrieve dev type from storage");
    dev_type = (bt_device_type_t)(remote_dev_type | p_auth_cmpl->dev_type);
  } else {
    dev_type = (bt_device_type_t)(p_auth_cmpl->dev_type);
  }
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr, IOT_CONF_KEY_DEVTYPE,
                                 (int)dev_type);

  // save remote addr type to iot conf file
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr, IOT_CONF_KEY_ADDRTYPE,
                                 (int)p_auth_cmpl->addr_type);

  // save remote versions to iot conf file
  btm_status = BTM_ReadRemoteVersion(p_auth_cmpl->bd_addr, &lmp_ver, &mfct_set,
                                     &lmp_subver);

  if (btm_status == BTM_SUCCESS) {
    DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr,
                                   IOT_CONF_KEY_MANUFACTURER, mfct_set);
    DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr, IOT_CONF_KEY_LMPVER,
                                   lmp_ver);
    DEVICE_IOT_CONFIG_ADDR_SET_INT(p_auth_cmpl->bd_addr, IOT_CONF_KEY_LMPSUBVER,
                                   lmp_subver);
  }

  // save remote pair type to iot conf file
  btif_iot_save_pair_type(p_auth_cmpl->bd_addr, is_ble, is_ssp);

  device_iot_config_flush();
}
