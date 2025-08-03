/******************************************************************************
 *
 *  Copyright 2014 The Android Open Source Project
 *  Copyright 2009-2012 Broadcom Corporation
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

/*******************************************************************************
 *
 *  Filename:      btif_core.c
 *
 *  Description:   Contains core functionality related to interfacing between
 *                 Bluetooth HAL and BTE core stack.
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_core"

#include <base/at_exit.h>
#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/threading/platform_thread.h>
#include <bluetooth/log.h>
#include <signal.h>
#include <sys/types.h>

#include <cstdint>

#include "btif/include/btif_common.h"
#include "btif/include/btif_config.h"
#include "btif/include/btif_dm.h"
#include "btif/include/btif_jni_task.h"
#include "btif/include/btif_profile_queue.h"
#include "btif/include/btif_sock.h"
#include "btif/include/btif_storage.h"
#include "btif/include/core_callbacks.h"
#include "btif/include/stack_manager_t.h"
#include "common/message_loop_thread.h"
#include "device/include/controller.h"
#include "device/include/device_iot_config.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/future.h"
#include "osi/include/properties.h"
#include "stack/include/a2dp_api.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_api.h"
#include "storage/config_keys.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using base::PlatformThread;
using bluetooth::Uuid;
using bluetooth::common::MessageLoopThread;
using namespace bluetooth;

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/

#ifndef BTE_DID_CONF_FILE
// TODO(armansito): Find a better way than searching by a hardcoded path.
#if defined(TARGET_FLOSS)
#define BTE_DID_CONF_FILE "/var/lib/bluetooth/bt_did.conf"
#elif defined(__ANDROID__)
#define BTE_DID_CONF_FILE \
  "/apex/com.android.btservices/etc/bluetooth/bt_did.conf"
#else  // !defined(__ANDROID__)
#define BTE_DID_CONF_FILE "bt_did.conf"
#endif  // defined(__ANDROID__)
#endif  // BTE_DID_CONF_FILE

#define CODEC_TYPE_NUMBER 32
#define DEFAULT_BUFFER_TIME (MAX_PCM_FRAME_NUM_PER_TICK * 2)
#define MAXIMUM_BUFFER_TIME (MAX_PCM_FRAME_NUM_PER_TICK * 2)
#define MINIMUM_BUFFER_TIME MAX_PCM_FRAME_NUM_PER_TICK

/*******************************************************************************
 *  Static variables
 ******************************************************************************/

static tBTA_SERVICE_MASK btif_enabled_services = 0;

/*
 * This variable should be set to 1, if the Bluedroid+BTIF libraries are to
 * function in DUT mode.
 *
 * To set this, the btif_init_bluetooth needs to be called with argument as 1
 */
static uint8_t btif_dut_mode = 0;

static base::AtExitManager* exit_manager;
static uid_set_t* uid_set;

/*******************************************************************************
 *  Externs
 ******************************************************************************/
void btif_dm_enable_service(tBTA_SERVICE_ID service_id, bool enable);
void btif_dm_load_local_oob(void);

/*******************************************************************************
 *
 * Function         btif_is_dut_mode
 *
 * Description      checks if BTIF is currently in DUT mode
 *
 * Returns          true if test mode, otherwise false
 *
 ******************************************************************************/

bool btif_is_dut_mode() { return btif_dut_mode == 1; }

/*******************************************************************************
 *
 * Function         btif_is_enabled
 *
 * Description      checks if main adapter is fully enabled
 *
 * Returns          1 if fully enabled, otherwize 0
 *
 ******************************************************************************/

int btif_is_enabled(void) {
  return ((!btif_is_dut_mode()) &&
          (stack_manager_get_interface()->get_stack_is_running()));
}

void btif_init_ok() {
  btif_dm_load_ble_local_keys();
}

/*******************************************************************************
 *
 * Function         btif_init_bluetooth
 *
 * Description      Creates BTIF task and prepares BT scheduler for startup
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/
bt_status_t btif_init_bluetooth() {
  log::info("entered");
  exit_manager = new base::AtExitManager();
  jni_thread_startup();
  GetInterfaceToProfiles()->events->invoke_thread_evt_cb(ASSOCIATE_JVM);
  log::info("finished");
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_enable_bluetooth_evt
 *
 * Description      Event indicating bluetooth enable is completed
 *                  Notifies HAL user with updated adapter state
 *
 * Returns          void
 *
 ******************************************************************************/

void btif_enable_bluetooth_evt() {
  /* Fetch the local BD ADDR */
  RawAddress local_bd_addr = *controller_get_interface()->get_address();

  std::string bdstr = local_bd_addr.ToString();

  // save bd addr to iot conf file
  device_iot_config_set_str(IOT_CONF_KEY_SECTION_ADAPTER, IOT_CONF_KEY_ADDRESS,
                            bdstr);

  char val[PROPERTY_VALUE_MAX] = "";
  int val_size = PROPERTY_VALUE_MAX;
  if (!btif_config_get_str(BTIF_STORAGE_SECTION_ADAPTER,
                           BTIF_STORAGE_KEY_ADDRESS, val, &val_size) ||
      strcmp(bdstr.c_str(), val) != 0) {
    // We failed to get an address or the one in the config file does not match
    // the address given by the controller interface. Update the config cache
    log::info("Storing '{}' into the config file",
              ADDRESS_TO_LOGGABLE_CSTR(local_bd_addr));
    btif_config_set_str(BTIF_STORAGE_SECTION_ADAPTER, BTIF_STORAGE_KEY_ADDRESS,
                        bdstr.c_str());

    // fire HAL callback for property change
    bt_property_t prop;
    prop.type = BT_PROPERTY_BDADDR;
    prop.val = (void*)&local_bd_addr;
    prop.len = sizeof(RawAddress);
    GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(
        BT_STATUS_SUCCESS, 1, &prop);
  }

  /* callback to HAL */
  uid_set = uid_set_create();

  btif_dm_init(uid_set);

  /* init rfcomm & l2cap api */
  btif_sock_init(uid_set);

  GetInterfaceToProfiles()->onBluetoothEnabled();

  /* load did configuration */
  bte_load_did_conf(BTE_DID_CONF_FILE);

  btif_dm_load_local_oob();

  future_ready(stack_manager_get_hack_future(), FUTURE_SUCCESS);
  log::info("Bluetooth enable event completed");
}

/*******************************************************************************
 *
 * Function         btif_cleanup_bluetooth
 *
 * Description      Cleanup BTIF state.
 *
 * Returns          void
 *
 ******************************************************************************/

bt_status_t btif_cleanup_bluetooth() {
  log::info("entered");
  btif_dm_cleanup();
  GetInterfaceToProfiles()->events->invoke_thread_evt_cb(DISASSOCIATE_JVM);
  btif_queue_release();
  jni_thread_shutdown();
  delete exit_manager;
  exit_manager = nullptr;
  btif_dut_mode = 0;
  log::info("finished");
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_dut_mode_configure
 *
 * Description      Configure Test Mode - 'enable' to 1 puts the device in test
 *                       mode and 0 exits test mode
 *
 ******************************************************************************/
void btif_dut_mode_configure(uint8_t enable) {
  log::verbose("");

  btif_dut_mode = enable;
  if (enable == 1) {
    BTA_EnableTestMode();
  } else {
    // Can't do in process reset anyways - just quit
    kill(getpid(), SIGKILL);
  }
}

/*******************************************************************************
 *
 * Function         btif_dut_mode_send
 *
 * Description     Sends a HCI Vendor specific command to the controller
 *
 ******************************************************************************/
void btif_dut_mode_send(uint16_t opcode, uint8_t* buf, uint8_t len) {
  log::verbose("");
  /* For now nothing to be done. */
  BTM_VendorSpecificCommand(opcode, len, buf, [](tBTM_VSC_CMPL*) {});
}

/*****************************************************************************
 *
 *   btif api adapter property functions
 *
 ****************************************************************************/

static bt_status_t btif_in_get_adapter_properties(void) {
  const static uint32_t NUM_ADAPTER_PROPERTIES = 7;
  bt_property_t properties[NUM_ADAPTER_PROPERTIES];
  uint32_t num_props = 0;

  RawAddress addr;
  bt_bdname_t name;
  bt_scan_mode_t mode;
  uint32_t disc_timeout;
  RawAddress bonded_devices[BTM_SEC_MAX_DEVICE_RECORDS];
  Uuid local_uuids[BT_MAX_NUM_UUIDS];
  bt_status_t status;
  bt_io_cap_t local_bt_io_cap;

  /* RawAddress */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props], BT_PROPERTY_BDADDR,
                             sizeof(addr), &addr);
  status = btif_storage_get_adapter_property(&properties[num_props]);
  // Add BT_PROPERTY_BDADDR property into list only when successful.
  // Otherwise, skip this property entry.
  if (status == BT_STATUS_SUCCESS) {
    num_props++;
  }

  /* BD_NAME */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props], BT_PROPERTY_BDNAME,
                             sizeof(name), &name);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  /* SCAN_MODE */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props],
                             BT_PROPERTY_ADAPTER_SCAN_MODE, sizeof(mode),
                             &mode);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  /* DISC_TIMEOUT */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props],
                             BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT,
                             sizeof(disc_timeout), &disc_timeout);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  /* BONDED_DEVICES */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props],
                             BT_PROPERTY_ADAPTER_BONDED_DEVICES,
                             sizeof(bonded_devices), bonded_devices);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  /* LOCAL UUIDs */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props], BT_PROPERTY_UUIDS,
                             sizeof(local_uuids), local_uuids);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  /* LOCAL IO Capabilities */
  BTIF_STORAGE_FILL_PROPERTY(&properties[num_props], BT_PROPERTY_LOCAL_IO_CAPS,
                             sizeof(bt_io_cap_t), &local_bt_io_cap);
  btif_storage_get_adapter_property(&properties[num_props]);
  num_props++;

  GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(
      BT_STATUS_SUCCESS, num_props, properties);
  return BT_STATUS_SUCCESS;
}

static bt_status_t btif_in_get_remote_device_properties(RawAddress* bd_addr) {
  bt_property_t remote_properties[8];
  uint32_t num_props = 0;

  bt_bdname_t name, alias;
  uint32_t cod, devtype;
  Uuid remote_uuids[BT_MAX_NUM_UUIDS];

  memset(remote_properties, 0, sizeof(remote_properties));
  BTIF_STORAGE_FILL_PROPERTY(&remote_properties[num_props], BT_PROPERTY_BDNAME,
                             sizeof(name), &name);
  btif_storage_get_remote_device_property(bd_addr,
                                          &remote_properties[num_props]);
  num_props++;

  BTIF_STORAGE_FILL_PROPERTY(&remote_properties[num_props],
                             BT_PROPERTY_REMOTE_FRIENDLY_NAME, sizeof(alias),
                             &alias);
  btif_storage_get_remote_device_property(bd_addr,
                                          &remote_properties[num_props]);
  num_props++;

  BTIF_STORAGE_FILL_PROPERTY(&remote_properties[num_props],
                             BT_PROPERTY_CLASS_OF_DEVICE, sizeof(cod), &cod);
  btif_storage_get_remote_device_property(bd_addr,
                                          &remote_properties[num_props]);
  num_props++;

  BTIF_STORAGE_FILL_PROPERTY(&remote_properties[num_props],
                             BT_PROPERTY_TYPE_OF_DEVICE, sizeof(devtype),
                             &devtype);
  btif_storage_get_remote_device_property(bd_addr,
                                          &remote_properties[num_props]);
  num_props++;

  BTIF_STORAGE_FILL_PROPERTY(&remote_properties[num_props], BT_PROPERTY_UUIDS,
                             sizeof(remote_uuids), remote_uuids);
  btif_storage_get_remote_device_property(bd_addr,
                                          &remote_properties[num_props]);
  num_props++;

  GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
      BT_STATUS_SUCCESS, *bd_addr, num_props, remote_properties);

  return BT_STATUS_SUCCESS;
}

static void btif_core_storage_adapter_write(bt_property_t* prop) {
  log::verbose("type: {}, len {}, {}", prop->type, prop->len,
               fmt::ptr(prop->val));
  bt_status_t status = btif_storage_set_adapter_property(prop);
  GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(status, 1,
                                                                 prop);
}

void btif_adapter_properties_evt(bt_status_t status, uint32_t num_props,
                                 bt_property_t* p_props) {
  GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(
      status, num_props, p_props);
}
void btif_remote_properties_evt(bt_status_t status, RawAddress* remote_addr,
                                uint32_t num_props, bt_property_t* p_props) {
  GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
      status, *remote_addr, num_props, p_props);
}

/*******************************************************************************
 *
 * Function         btif_get_adapter_properties
 *
 * Description      Fetch all available properties (local & remote)
 *
 ******************************************************************************/

void btif_get_adapter_properties(void) {
  log::verbose("");

  btif_in_get_adapter_properties();
}

/*******************************************************************************
 *
 * Function         btif_get_adapter_property
 *
 * Description      Fetches property value from local cache
 *
 ******************************************************************************/

void btif_get_adapter_property(bt_property_type_t type) {
  log::verbose("{}", type);

  bt_status_t status = BT_STATUS_SUCCESS;
  char buf[512];
  bt_property_t prop;
  prop.type = type;
  prop.val = (void*)buf;
  prop.len = sizeof(buf);
  if (prop.type == BT_PROPERTY_LOCAL_LE_FEATURES) {
    tBTM_BLE_VSC_CB cmn_vsc_cb;
    bt_local_le_features_t local_le_features;

    /* LE features are not stored in storage. Should be retrived from stack
     */
    BTM_BleGetVendorCapabilities(&cmn_vsc_cb);
    local_le_features.local_privacy_enabled = BTM_BleLocalPrivacyEnabled();

    prop.len = sizeof(bt_local_le_features_t);
    if (cmn_vsc_cb.filter_support == 1)
      local_le_features.max_adv_filter_supported = cmn_vsc_cb.max_filter;
    else
      local_le_features.max_adv_filter_supported = 0;
    local_le_features.max_adv_instance = cmn_vsc_cb.adv_inst_max;
    local_le_features.max_irk_list_size = cmn_vsc_cb.max_irk_list_sz;
    local_le_features.rpa_offload_supported = cmn_vsc_cb.rpa_offloading;
    local_le_features.scan_result_storage_size =
        cmn_vsc_cb.tot_scan_results_strg;
    local_le_features.activity_energy_info_supported =
        cmn_vsc_cb.energy_support;
    local_le_features.version_supported = cmn_vsc_cb.version_supported;
    local_le_features.total_trackable_advertisers =
        cmn_vsc_cb.total_trackable_advertisers;

    local_le_features.extended_scan_support =
        cmn_vsc_cb.extended_scan_support > 0;
    local_le_features.debug_logging_supported =
        cmn_vsc_cb.debug_logging_supported > 0;
    const controller_t* controller = controller_get_interface();

    if (controller->SupportsBleExtendedAdvertising()) {
      local_le_features.max_adv_instance =
          controller->get_ble_number_of_supported_advertising_sets();
    }
    local_le_features.le_2m_phy_supported = controller->SupportsBle2mPhy();
    local_le_features.le_coded_phy_supported =
        controller->SupportsBleCodedPhy();
    local_le_features.le_extended_advertising_supported =
        controller->SupportsBleExtendedAdvertising();
    local_le_features.le_periodic_advertising_supported =
        controller->SupportsBlePeriodicAdvertising();
    local_le_features.le_maximum_advertising_data_length =
        controller->get_ble_maximum_advertising_data_length();

    local_le_features.dynamic_audio_buffer_supported =
        cmn_vsc_cb.dynamic_audio_buffer_support;

    local_le_features.le_periodic_advertising_sync_transfer_sender_supported =
        controller->SupportsBlePeriodicAdvertisingSyncTransferSender();
    local_le_features.le_connected_isochronous_stream_central_supported =
        controller->SupportsBleConnectedIsochronousStreamCentral();
    local_le_features.le_isochronous_broadcast_supported =
        controller->SupportsBleIsochronousBroadcaster();
    local_le_features
        .le_periodic_advertising_sync_transfer_recipient_supported =
        controller->SupportsBlePeriodicAdvertisingSyncTransferRecipient();
    local_le_features.adv_filter_extended_features_mask =
        cmn_vsc_cb.adv_filter_extended_features_mask;

    memcpy(prop.val, &local_le_features, prop.len);
  } else if (prop.type == BT_PROPERTY_DYNAMIC_AUDIO_BUFFER) {
    tBTM_BLE_VSC_CB cmn_vsc_cb;
    bt_dynamic_audio_buffer_item_t dynamic_audio_buffer_item;

    BTM_BleGetVendorCapabilities(&cmn_vsc_cb);

    prop.len = sizeof(bt_dynamic_audio_buffer_item_t);
    if (GetInterfaceToProfiles()->config->isA2DPOffloadEnabled() == false) {
      log::verbose("Get buffer millis for A2DP software encoding");
      for (int i = 0; i < CODEC_TYPE_NUMBER; i++) {
        dynamic_audio_buffer_item.dab_item[i] = {
            .default_buffer_time = DEFAULT_BUFFER_TIME,
            .maximum_buffer_time = MAXIMUM_BUFFER_TIME,
            .minimum_buffer_time = MINIMUM_BUFFER_TIME};
      }
      memcpy(prop.val, &dynamic_audio_buffer_item, prop.len);
    } else {
      if (cmn_vsc_cb.dynamic_audio_buffer_support != 0) {
        log::verbose("Get buffer millis for A2DP Offload");
        tBTM_BT_DYNAMIC_AUDIO_BUFFER_CB
            bt_dynamic_audio_buffer_cb[CODEC_TYPE_NUMBER];
        BTM_BleGetDynamicAudioBuffer(bt_dynamic_audio_buffer_cb);

        for (int i = 0; i < CODEC_TYPE_NUMBER; i++) {
          dynamic_audio_buffer_item.dab_item[i] = {
              .default_buffer_time =
                  bt_dynamic_audio_buffer_cb[i].default_buffer_time,
              .maximum_buffer_time =
                  bt_dynamic_audio_buffer_cb[i].maximum_buffer_time,
              .minimum_buffer_time =
                  bt_dynamic_audio_buffer_cb[i].minimum_buffer_time};
        }
        memcpy(prop.val, &dynamic_audio_buffer_item, prop.len);
      } else {
        log::verbose("Don't support Dynamic Audio Buffer");
      }
    }
  } else {
    status = btif_storage_get_adapter_property(&prop);
  }
  GetInterfaceToProfiles()->events->invoke_adapter_properties_cb(status, 1,
                                                                 &prop);
}

bt_property_t* property_deep_copy(const bt_property_t* prop) {
  bt_property_t* copy =
      (bt_property_t*)osi_calloc(sizeof(bt_property_t) + prop->len);
  copy->type = prop->type;
  copy->len = prop->len;
  copy->val = (uint8_t*)(copy + 1);
  memcpy(copy->val, prop->val, prop->len);
  return copy;
}

/*******************************************************************************
 *
 * Function         btif_set_adapter_property
 *
 * Description      Updates core stack with property value and stores it in
 *                  local cache
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/

void btif_set_adapter_property(bt_property_t* property) {
  log::verbose("btif_set_adapter_property type: {}, len {}, {}", property->type,
               property->len, fmt::ptr(property->val));

  switch (property->type) {
    case BT_PROPERTY_BDNAME: {
      char bd_name[BTM_MAX_LOC_BD_NAME_LEN + 1];
      uint16_t name_len = property->len > BTM_MAX_LOC_BD_NAME_LEN
                              ? BTM_MAX_LOC_BD_NAME_LEN
                              : property->len;
      memcpy(bd_name, property->val, name_len);
      bd_name[name_len] = '\0';

      log::verbose("set property name : {}", (char*)bd_name);

      BTA_DmSetDeviceName((const char*)bd_name);

      btif_core_storage_adapter_write(property);
    } break;

    case BT_PROPERTY_ADAPTER_SCAN_MODE: {
      bt_scan_mode_t mode = *(bt_scan_mode_t*)property->val;
      log::verbose("set property scan mode : {:x}", mode);

      if (BTA_DmSetVisibility(mode)) {
        btif_core_storage_adapter_write(property);
      }
    } break;
    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT: {
      /* Nothing to do beside store the value in NV.  Java
         will change the SCAN_MODE property after setting timeout,
         if required */
      btif_core_storage_adapter_write(property);
    } break;
    case BT_PROPERTY_LOCAL_IO_CAPS: {
      // Changing IO Capability of stack at run-time is not currently supported.
      // This call changes the stored value which will affect the stack next
      // time it starts up.
      btif_core_storage_adapter_write(property);
    } break;
    default:
      break;
  }
}

/*******************************************************************************
 *
 * Function         btif_get_remote_device_property
 *
 * Description      Fetches the remote device property from the NVRAM
 *
 ******************************************************************************/
void btif_get_remote_device_property(RawAddress remote_addr,
                                     bt_property_type_t type) {
  char buf[1024];
  bt_property_t prop;
  prop.type = type;
  prop.val = (void*)buf;
  prop.len = sizeof(buf);

  bt_status_t status =
      btif_storage_get_remote_device_property(&remote_addr, &prop);
  GetInterfaceToProfiles()->events->invoke_remote_device_properties_cb(
      status, remote_addr, 1, &prop);
}

/*******************************************************************************
 *
 * Function         btif_get_remote_device_properties
 *
 * Description      Fetches all the remote device properties from NVRAM
 *
 ******************************************************************************/
void btif_get_remote_device_properties(RawAddress remote_addr) {
  btif_in_get_remote_device_properties(&remote_addr);
}

/*******************************************************************************
 *
 * Function         btif_set_remote_device_property
 *
 * Description      Writes the remote device property to NVRAM.
 *                  Currently, BT_PROPERTY_REMOTE_FRIENDLY_NAME is the only
 *                  remote device property that can be set
 *
 ******************************************************************************/
void btif_set_remote_device_property(RawAddress* remote_addr,
                                     bt_property_t* property) {
  btif_storage_set_remote_device_property(remote_addr, property);
}

/*******************************************************************************
 *
 * Function         btif_get_enabled_services_mask
 *
 * Description      Fetches currently enabled services
 *
 * Returns          tBTA_SERVICE_MASK
 *
 ******************************************************************************/

tBTA_SERVICE_MASK btif_get_enabled_services_mask(void) {
  return btif_enabled_services;
}

/*******************************************************************************
 *
 * Function         btif_enable_service
 *
 * Description      Enables the service 'service_ID' to the service_mask.
 *                  Upon BT enable, BTIF core shall invoke the BTA APIs to
 *                  enable the profiles
 *
 ******************************************************************************/
void btif_enable_service(tBTA_SERVICE_ID service_id) {
  btif_enabled_services |= (1 << service_id);

  log::verbose("current services:0x{:x}", btif_enabled_services);

  if (btif_is_enabled()) {
    btif_dm_enable_service(service_id, true);
  }
}
/*******************************************************************************
 *
 * Function         btif_disable_service
 *
 * Description      Disables the service 'service_ID' to the service_mask.
 *                  Upon BT disable, BTIF core shall invoke the BTA APIs to
 *                  disable the profiles
 *
 ******************************************************************************/
void btif_disable_service(tBTA_SERVICE_ID service_id) {
  btif_enabled_services &= (tBTA_SERVICE_MASK)(~(1 << service_id));

  log::verbose("Current Services:0x{:x}", btif_enabled_services);

  if (btif_is_enabled()) {
    btif_dm_enable_service(service_id, false);
  }
}

bt_status_t btif_set_dynamic_audio_buffer_size(int codec, int size) {
  log::verbose("");

  tBTM_BLE_VSC_CB cmn_vsc_cb;
  BTM_BleGetVendorCapabilities(&cmn_vsc_cb);

  if (!GetInterfaceToProfiles()->config->isA2DPOffloadEnabled()) {
    log::verbose("Set buffer size ({}) for A2DP software encoding", size);
    GetInterfaceToProfiles()
        ->profileSpecific_HACK->btif_av_set_dynamic_audio_buffer_size(
            uint8_t(size));
  } else {
    if (cmn_vsc_cb.dynamic_audio_buffer_support != 0) {
      log::verbose("Set buffer size ({}) for A2DP offload", size);
      uint16_t firmware_tx_buffer_length_byte;
      firmware_tx_buffer_length_byte = static_cast<uint16_t>(size);
      log::info("firmware_tx_buffer_length_byte: {}",
                firmware_tx_buffer_length_byte);
      bluetooth::shim::GetController()->SetDabAudioBufferTime(
          firmware_tx_buffer_length_byte);
    }
  }

  return BT_STATUS_SUCCESS;
}
