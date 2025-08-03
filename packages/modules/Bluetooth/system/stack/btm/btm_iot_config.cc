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

#define LOG_TAG "btm_iot"

#include <bluetooth/log.h>

#include "btif/include/btif_storage.h"
#include "btm_ble_api.h"
#include "device/include/device_iot_config.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "stack/acl/acl.h"

using namespace bluetooth;

/*******************************************************************************
 *
 * Function         btm_iot_save_remote_properties
 *
 * Description      Store remote basic properties to iot conf file
 *
 * Returns          void
 *
 *******************************************************************************/
void btm_iot_save_remote_properties(tACL_CONN* p_acl_cb) {
  BD_NAME bd_name;
  bt_property_t prop_name;
  uint32_t cod = 0;
  tBT_DEVICE_TYPE dev_type;
  tBLE_ADDR_TYPE addr_type;

  // save remote name to iot conf file
  if (BTM_GetRemoteDeviceName(p_acl_cb->remote_addr, bd_name)) {
    std::string name_str{(char*)bd_name};
    DEVICE_IOT_CONFIG_ADDR_SET_STR(p_acl_cb->remote_addr,
                                   IOT_CONF_KEY_REMOTE_NAME, name_str);
  }

  /* Try to retrieve cod from storage */
  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_CLASS_OF_DEVICE,
                             sizeof(cod), &cod);
  if (btif_storage_get_remote_device_property(&p_acl_cb->remote_addr,
                                              &prop_name) == BT_STATUS_SUCCESS)
    log::verbose("cod retrieved from storage is 0x{:06x}", cod);
  if (cod == 0) {
    log::verbose("cod is 0, set as unclassified");
    cod = (0x1F) << 8;
  }

  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_DEVCLASS,
                                 (int)cod);

  BTM_ReadDevInfo(p_acl_cb->remote_addr, &dev_type, &addr_type);

  // save remote dev type to iot conf file
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_DEVTYPE,
                                 (int)dev_type);

  // save remote addr type to iot conf file
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_ADDRTYPE,
                                 (int)addr_type);

  // save default recorded value to iot conf file
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_RECORDED,
                                 IOT_CONF_VAL_RECORDED_DEFAULT);
}

/*******************************************************************************
 *
 * Function         btm_iot_save_remote_versions
 *
 * Description      Store remote versions to iot conf file
 *
 * Returns          void
 *
 *******************************************************************************/
void btm_iot_save_remote_versions(tACL_CONN* p_acl_cb) {
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr,
                                 IOT_CONF_KEY_MANUFACTURER,
                                 p_acl_cb->remote_version_info.manufacturer);
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_LMPVER,
                                 p_acl_cb->remote_version_info.lmp_version);
  DEVICE_IOT_CONFIG_ADDR_SET_INT(p_acl_cb->remote_addr, IOT_CONF_KEY_LMPSUBVER,
                                 p_acl_cb->remote_version_info.lmp_subversion);
}
