/******************************************************************************
 *
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

#ifndef BTIF_HH_H
#define BTIF_HH_H

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_hh.h>
#include <pthread.h>
#include <stdint.h>

#include "bta/include/bta_hh_api.h"
#include "macros.h"
#include "osi/include/alarm.h"
#include "osi/include/fixed_queue.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/

#define BTIF_HH_MAX_HID 8
#define BTIF_HH_MAX_ADDED_DEV 32

#define BTIF_HH_MAX_KEYSTATES 3
#define BTIF_HH_KEYSTATE_MASK_NUMLOCK 0x01
#define BTIF_HH_KEYSTATE_MASK_CAPSLOCK 0x02
#define BTIF_HH_KEYSTATE_MASK_SCROLLLOCK 0x04

#define BTIF_HH_MAX_POLLING_ATTEMPTS 10
#define BTIF_HH_POLLING_SLEEP_DURATION_US 5000

#ifndef ENABLE_UHID_SET_REPORT
#if defined(__ANDROID__) || defined(TARGET_FLOSS)
#define ENABLE_UHID_SET_REPORT 1
#else
#define ENABLE_UHID_SET_REPORT 0
#endif
#endif

/*******************************************************************************
 *  Type definitions and return values
 ******************************************************************************/

typedef enum : unsigned {
  BTIF_HH_DISABLED = 0,
  BTIF_HH_ENABLED,
  BTIF_HH_DISABLING,
  BTIF_HH_DEV_UNKNOWN,
  BTIF_HH_DEV_CONNECTING,
  BTIF_HH_DEV_CONNECTED,
  BTIF_HH_DEV_DISCONNECTED
} BTIF_HH_STATUS;

inline std::string btif_hh_status_text(const BTIF_HH_STATUS& status) {
  switch (status) {
    CASE_RETURN_TEXT(BTIF_HH_DISABLED);
    CASE_RETURN_TEXT(BTIF_HH_ENABLED);
    CASE_RETURN_TEXT(BTIF_HH_DISABLING);
    CASE_RETURN_TEXT(BTIF_HH_DEV_UNKNOWN);
    CASE_RETURN_TEXT(BTIF_HH_DEV_CONNECTING);
    CASE_RETURN_TEXT(BTIF_HH_DEV_CONNECTED);
    CASE_RETURN_TEXT(BTIF_HH_DEV_DISCONNECTED);
    default:
      return base::StringPrintf("UNKNOWN[%u]", status);
  }
}

// Shared with uhid polling thread
typedef struct {
  bthh_connection_state_t dev_status;
  uint8_t dev_handle;
  tAclLinkSpec link_spec;
  tBTA_HH_ATTR_MASK attr_mask;
  uint8_t sub_class;
  uint8_t app_id;
  int fd;
  bool ready_for_data;
  pthread_t hh_poll_thread_id;
  uint8_t hh_keep_polling;
  alarm_t* vup_timer;
  fixed_queue_t* get_rpt_id_queue;
#if ENABLE_UHID_SET_REPORT
  fixed_queue_t* set_rpt_id_queue;
#endif // ENABLE_UHID_SET_REPORT
  bool local_vup;  // Indicated locally initiated VUP
} btif_hh_device_t;

/* Control block to maintain properties of devices */
typedef struct {
  uint8_t dev_handle;
  tAclLinkSpec link_spec;
  tBTA_HH_ATTR_MASK attr_mask;
  bool reconnect_allowed;
} btif_hh_added_device_t;

/**
 * BTIF-HH control block to maintain added devices and currently
 * connected hid devices
 */
typedef struct {
  BTIF_HH_STATUS status;
  btif_hh_device_t devices[BTIF_HH_MAX_HID];
  uint32_t device_num;
  btif_hh_added_device_t added_devices[BTIF_HH_MAX_ADDED_DEV];
  bool service_dereg_active;
  tAclLinkSpec pending_link_spec;
} btif_hh_cb_t;

/*******************************************************************************
 *  Functions
 ******************************************************************************/

extern btif_hh_cb_t btif_hh_cb;

btif_hh_device_t* btif_hh_find_connected_dev_by_handle(uint8_t handle);
void btif_hh_remove_device(const tAclLinkSpec& link_spec);
bool btif_hh_add_added_dev(const tAclLinkSpec& link_spec,
                           tBTA_HH_ATTR_MASK attr_mask,
                           bool reconnect_allowed);
bt_status_t btif_hh_virtual_unplug(const tAclLinkSpec* link_spec);
void btif_hh_disconnect(const tAclLinkSpec* link_spec);
void btif_hh_setreport(btif_hh_device_t* p_dev, bthh_report_type_t r_type,
                       uint16_t size, uint8_t* report);
void btif_hh_senddata(btif_hh_device_t* p_dev, uint16_t size, uint8_t* report);
void btif_hh_getreport(btif_hh_device_t* p_dev, bthh_report_type_t r_type,
                       uint8_t reportId, uint16_t bufferSize);
void btif_hh_service_registration(bool enable);

void DumpsysHid(int fd);

namespace fmt {
template <>
struct formatter<BTIF_HH_STATUS> : enum_formatter<BTIF_HH_STATUS> {};
}  // namespace fmt

#endif
