/******************************************************************************
 *
 *  Copyright 2003-2012 Broadcom Corporation
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

/******************************************************************************
 *
 *  This is the private interface file for the BTA device manager.
 *
 ******************************************************************************/
#ifndef BTA_DM_INT_H
#define BTA_DM_INT_H

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <string>
#include <vector>

#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "bta/sys/bta_sys.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "macros.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/

#define BTA_DM_MSG_LEN 50

#define BTA_DM_NUM_PEER_DEVICE 7

typedef enum : uint8_t {
  BTA_DM_NOT_CONNECTED = 0,
  BTA_DM_CONNECTED = 1,
  BTA_DM_UNPAIRING = 2,
} tBTA_DM_CONN_STATE;

inline std::string bta_conn_state_text(tBTA_DM_CONN_STATE state) {
  switch (state) {
    CASE_RETURN_TEXT(BTA_DM_NOT_CONNECTED);
    CASE_RETURN_TEXT(BTA_DM_CONNECTED);
    CASE_RETURN_TEXT(BTA_DM_UNPAIRING);
    default:
      return std::string("UNKNOWN");
  }
}

typedef enum : uint8_t {
  BTA_DM_DI_NONE = 0x00,      /* nothing special */
  BTA_DM_DI_SET_SNIFF = 0x01, /* set this bit if call BTM_SetPowerMode(sniff) */
  BTA_DM_DI_INT_SNIFF = 0x02, /* set this bit if call BTM_SetPowerMode(sniff) &
                                 enter sniff mode */
  BTA_DM_DI_ACP_SNIFF = 0x04, /* set this bit if peer init sniff */
  BTA_DM_DI_UNUSED = 0x08,
  BTA_DM_DI_USE_SSR = 0x10, /* set this bit if ssr is supported for this link */
  BTA_DM_DI_AV_ACTIVE = 0x20, /* set this bit if AV is active for this link */
} tBTA_DM_DEV_INFO_BITMASK;
typedef uint8_t tBTA_DM_DEV_INFO;

inline std::string device_info_text(tBTA_DM_DEV_INFO info) {
  const char* const device_info_text[] = {
      ":set_sniff", ":int_sniff", ":acp_sniff",
      ":unused",    ":use_ssr",   ":av_active",
  };

  std::string s = base::StringPrintf("0x%02x", info);
  if (info == BTA_DM_DI_NONE) return s + std::string(":none");
  for (size_t i = 0; i < sizeof(device_info_text) / sizeof(device_info_text[0]);
       i++) {
    if (info & (1u << i)) s += std::string(device_info_text[i]);
  }
  return s;
}

/* set power mode request type */
#define BTA_DM_PM_RESTART 1
#define BTA_DM_PM_NEW_REQ 2
#define BTA_DM_PM_EXECUTE 3
typedef uint8_t tBTA_DM_PM_REQ;

struct tBTA_DM_PEER_DEVICE {
  RawAddress peer_bdaddr;
  tBTA_DM_CONN_STATE conn_state;
  tBTA_PREF_ROLES pref_role;
  bool in_use;

 private:
  // Dynamic pieces of operational device information
  tBTA_DM_DEV_INFO info{BTA_DM_DI_NONE};

 public:
  std::string info_text() const { return device_info_text(info); }

  void reset_device_info() { info = BTA_DM_DI_NONE; }

  void set_av_active() { info |= BTA_DM_DI_AV_ACTIVE; }
  void reset_av_active() { info &= ~BTA_DM_DI_AV_ACTIVE; }
  bool is_av_active() const { return info & BTA_DM_DI_AV_ACTIVE; }

  void set_local_init_sniff() { info |= BTA_DM_DI_INT_SNIFF; }
  bool is_local_init_sniff() const { return info & BTA_DM_DI_INT_SNIFF; }
  void set_remote_init_sniff() { info |= BTA_DM_DI_ACP_SNIFF; }
  bool is_remote_init_sniff() const { return info & BTA_DM_DI_ACP_SNIFF; }

  void set_sniff_command_sent() { info |= BTA_DM_DI_SET_SNIFF; }
  void reset_sniff_command_sent() { info &= ~BTA_DM_DI_SET_SNIFF; }
  bool is_sniff_command_sent() const { return info & BTA_DM_DI_SET_SNIFF; }

  // NOTE: Why is this not used as a bitmask
  void set_both_device_ssr_capable() { info = BTA_DM_DI_USE_SSR; }

  void reset_sniff_flags() {
    info &= ~(BTA_DM_DI_INT_SNIFF | BTA_DM_DI_ACP_SNIFF | BTA_DM_DI_SET_SNIFF);
  }

  void set_ssr_active() { info |= BTA_DM_DI_USE_SSR; }
  void reset_ssr_active() { info &= ~BTA_DM_DI_USE_SSR; }
  bool is_ssr_active() const { return info & BTA_DM_DI_USE_SSR; }

  tBTA_DM_ENCRYPT_CBACK* p_encrypt_cback;
  tBTM_PM_STATUS prev_low; /* previous low power mode used */
  tBTA_DM_PM_ACTION pm_mode_attempted;
  tBTA_DM_PM_ACTION pm_mode_failed;
  bool remove_dev_pending;
  tBT_TRANSPORT transport;
};

/* structure to store list of
  active connections */
typedef struct {
  tBTA_DM_PEER_DEVICE peer_device[BTA_DM_NUM_PEER_DEVICE];
  uint8_t count;
  uint8_t le_count;
} tBTA_DM_ACTIVE_LINK;

typedef struct {
  RawAddress peer_bdaddr;
  tBTA_SYS_ID id;
  uint8_t app_id;
  tBTA_SYS_CONN_STATUS state;
  bool new_request;

  std::string ToString() const {
    return base::StringPrintf(
        "peer:%s sys_name:%s app_id:%hhu state:%s new:request:%s",
        ADDRESS_TO_LOGGABLE_CSTR(peer_bdaddr), BtaIdSysText(id).c_str(), app_id,
        bta_sys_conn_status_text(state).c_str(), logbool(new_request).c_str());
  }

} tBTA_DM_SRVCS;

#ifndef BTA_DM_NUM_CONN_SRVS
#define BTA_DM_NUM_CONN_SRVS 30
#endif

typedef struct {
  uint8_t count;
  tBTA_DM_SRVCS conn_srvc[BTA_DM_NUM_CONN_SRVS];

} tBTA_DM_CONNECTED_SRVCS;

typedef struct {
#define BTA_DM_PM_SNIFF_TIMER_IDX 0
#define BTA_DM_PM_PARK_TIMER_IDX 1
#define BTA_DM_PM_SUSPEND_TIMER_IDX 2
#define BTA_DM_PM_MODE_TIMER_MAX 3
  /*
   * Keep three different timers for PARK, SNIFF and SUSPEND if TBFC is
   * supported.
   */
  alarm_t* timer[BTA_DM_PM_MODE_TIMER_MAX];

  uint8_t srvc_id[BTA_DM_PM_MODE_TIMER_MAX];
  uint8_t pm_action[BTA_DM_PM_MODE_TIMER_MAX];
  uint8_t active; /* number of active timer */

  RawAddress peer_bdaddr;
  bool in_use;
} tBTA_PM_TIMER;

extern tBTA_DM_CONNECTED_SRVCS bta_dm_conn_srvcs;

#define BTA_DM_NUM_PM_TIMER 7

typedef struct {
  tBTA_DM_ACL_CBACK* p_acl_cback;
} tBTA_DM_ACL_CB;

/* DM control block */
typedef struct {
  tBTA_DM_ACTIVE_LINK device_list;
  tBTA_BLE_ENERGY_INFO_CBACK* p_energy_info_cback;
  bool disabling;
  alarm_t* disable_timer;
  uint8_t pm_id;
  tBTA_PM_TIMER pm_timer[BTA_DM_NUM_PM_TIMER];
  uint8_t cur_av_count;   /* current AV connecions */

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
  /* store UUID list for EIR */
  uint32_t eir_uuid[BTM_EIR_SERVICE_ARRAY_SIZE];
#if (BTA_EIR_SERVER_NUM_CUSTOM_UUID > 0)
  tBTA_CUSTOM_UUID bta_custom_uuid[BTA_EIR_SERVER_NUM_CUSTOM_UUID];
#endif

#endif

  alarm_t* switch_delay_timer;
} tBTA_DM_CB;

/* DI control block */
typedef struct {
  uint8_t di_num;                     /* total local DI record number */
  uint32_t di_handle[BTA_DI_NUM_MAX]; /* local DI record handle, the first one
                                         is primary record */
} tBTA_DM_DI_CB;

typedef struct {
  uint16_t page_timeout; /* timeout for page in slots */
  bool avoid_scatter; /* true to avoid scatternet when av is streaming (be the
                         central) */

} tBTA_DM_CFG;

extern const uint32_t bta_service_id_to_btm_srv_id_lkup_tbl[];

typedef struct {
  uint8_t id;
  uint8_t app_id;
  uint8_t cfg;

} tBTA_DM_RM;

extern const tBTA_DM_CFG* p_bta_dm_cfg;
extern const tBTA_DM_RM* p_bta_dm_rm_cfg;

typedef struct {
  uint8_t id;
  uint8_t app_id;
  uint8_t spec_idx; /* index of spec table to use */

} tBTA_DM_PM_CFG;

typedef struct {
  tBTA_DM_PM_ACTION power_mode;
  uint16_t timeout;

} tBTA_DM_PM_ACTN;

typedef struct {
  uint8_t allow_mask; /* mask of sniff/hold/park modes to allow */
  uint8_t ssr; /* set SSR on conn open/unpark */
  tBTA_DM_PM_ACTN actn_tbl[BTA_DM_PM_NUM_EVTS][2];

} tBTA_DM_PM_SPEC;

typedef struct {
  uint16_t max_lat;
  uint16_t min_rmt_to;
  uint16_t min_loc_to;
  const char* name{nullptr};
} tBTA_DM_SSR_SPEC;

typedef struct {
  uint16_t manufacturer;
  uint16_t lmp_sub_version;
  uint8_t lmp_version;
} tBTA_DM_LMP_VER_INFO;

extern const uint16_t bta_service_id_to_uuid_lkup_tbl[];

/* For Insight, PM cfg lookup tables are runtime configurable (to allow tweaking
 * of params for power consumption measurements) */
#ifndef BTE_SIM_APP
#define tBTA_DM_PM_TYPE_QUALIFIER const
#else
#define tBTA_DM_PM_TYPE_QUALIFIER
#endif

extern const tBTA_DM_PM_CFG* p_bta_dm_pm_cfg;
tBTA_DM_PM_TYPE_QUALIFIER tBTA_DM_PM_SPEC* get_bta_dm_pm_spec();
extern const tBTM_PM_PWR_MD* p_bta_dm_pm_md;
extern tBTA_DM_SSR_SPEC* p_bta_dm_ssr_spec;

/* update dynamic BRCM Aware EIR data */
extern const tBTA_DM_EIR_CONF bta_dm_eir_cfg;
extern const tBTA_DM_EIR_CONF* p_bta_dm_eir_cfg;

/* DM control block */
extern tBTA_DM_CB bta_dm_cb;

/* DM control block for ACL management */
extern tBTA_DM_ACL_CB bta_dm_acl_cb;

/* DI control block */
extern tBTA_DM_DI_CB bta_dm_di_cb;

void bta_dm_enable(tBTA_DM_SEC_CBACK*, tBTA_DM_ACL_CBACK*);
void bta_dm_disable();
void bta_dm_set_dev_name(const std::vector<uint8_t>&);
void bta_dm_close_acl(const RawAddress&, bool, tBT_TRANSPORT);

void bta_dm_pm_btm_status(const RawAddress&, tBTM_PM_STATUS, uint16_t,
                          tHCI_STATUS);
void bta_dm_pm_timer(const RawAddress&, tBTA_DM_PM_ACTION);

void bta_dm_ble_set_conn_params(const RawAddress&, uint16_t, uint16_t, uint16_t,
                                uint16_t);
void bta_dm_ble_update_conn_params(const RawAddress&, uint16_t, uint16_t,
                                   uint16_t, uint16_t, uint16_t, uint16_t);

void bta_dm_ble_set_data_length(const RawAddress& bd_addr);

void bta_dm_ble_get_energy_info(tBTA_BLE_ENERGY_INFO_CBACK*);

void bta_dm_init_pm(void);
void bta_dm_disable_pm(void);

uint8_t bta_dm_get_av_count(void);
tBTA_DM_PEER_DEVICE* bta_dm_find_peer_device(const RawAddress& peer_addr);

void bta_dm_clear_event_filter(void);
void bta_dm_clear_event_mask(void);
void bta_dm_clear_filter_accept_list(void);
void bta_dm_disconnect_all_acls(void);
void bta_dm_le_rand(LeRandCallback cb);
void bta_dm_set_event_filter_connection_setup_all_devices();
void bta_dm_allow_wake_by_hid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices);
void bta_dm_restore_filter_accept_list(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices);
void bta_dm_set_default_event_mask_except(uint64_t mask, uint64_t le_mask);
void bta_dm_set_event_filter_inquiry_result_all_devices();

void bta_dm_ble_reset_id(void);

void bta_dm_eir_update_uuid(uint16_t uuid16, bool adding);
void bta_dm_eir_update_cust_uuid(const tBTA_CUSTOM_UUID &curr, bool adding);

void bta_dm_ble_subrate_request(const RawAddress& bd_addr, uint16_t subrate_min,
                                uint16_t subrate_max, uint16_t max_latency,
                                uint16_t cont_num, uint16_t timeout);

namespace fmt {
template <>
struct formatter<tBTA_DM_CONN_STATE> : enum_formatter<tBTA_DM_CONN_STATE> {};
}  // namespace fmt

#endif /* BTA_DM_INT_H */
