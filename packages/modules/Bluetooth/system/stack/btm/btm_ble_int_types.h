/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

#ifndef BTM_BLE_INT_TYPES_H
#define BTM_BLE_INT_TYPES_H

#include "macros.h"
#include "osi/include/alarm.h"
#include "stack/btm/neighbor_inquiry.h"
#include "stack/include/btm_ble_api_types.h"
#include "types/raw_address.h"

/* scanning enable status */
#define BTM_BLE_SCAN_ENABLE 0x01
#define BTM_BLE_SCAN_DISABLE 0x00

/* advertising enable status */
#define BTM_BLE_ADV_ENABLE 0x01
#define BTM_BLE_ADV_DISABLE 0x00

#define BTM_BLE_AD_DATA_LEN 31

#define BTM_BLE_DUPLICATE_ENABLE 1
#define BTM_BLE_DUPLICATE_DISABLE 0

/* Interval(scan_int) = 11.25 ms= 0x0010 * 0.625 ms */
#define BTM_BLE_GAP_DISC_SCAN_INT 18
/* scan_window = 11.25 ms= 0x0010 * 0.625 ms */
#define BTM_BLE_GAP_DISC_SCAN_WIN 18
/* Tgap(gen_disc) = 1.28 s= 512 * 0.625 ms */
#define BTM_BLE_GAP_ADV_INT 512
/* Tgap(lim_timeout) = 180s max */
#define BTM_BLE_GAP_LIM_TIMEOUT_MS (180 * 1000)
/* Interval(scan_int) = 100ms= 160 * 0.625 ms */
#define BTM_BLE_LOW_LATENCY_SCAN_INT 160
/* scan_window = 100ms= 160 * 0.625 ms */
#define BTM_BLE_LOW_LATENCY_SCAN_WIN 160

/* TGAP(adv_fast_interval1) = 30(used) ~ 60 ms  = 48 *0.625 */
#define BTM_BLE_GAP_ADV_FAST_INT_1 48
/* TGAP(adv_fast_interval2) = 100(used) ~ 150 ms = 160 * 0.625 ms */
#define BTM_BLE_GAP_ADV_FAST_INT_2 160
/* Tgap(adv_slow_interval) = 1.28 s= 512 * 0.625 ms */
#define BTM_BLE_GAP_ADV_SLOW_INT 2048
/* Tgap(dir_conn_adv_int_max) = 500 ms = 800 * 0.625 ms */
#define BTM_BLE_GAP_ADV_DIR_MAX_INT 800
/* Tgap(dir_conn_adv_int_min) = 250 ms = 400 * 0.625 ms */
#define BTM_BLE_GAP_ADV_DIR_MIN_INT 400

#define BTM_BLE_GAP_FAST_ADV_TIMEOUT_MS (30 * 1000)

#define BTM_VSC_CHIP_CAPABILITY_L_VERSION 55
#define BTM_VSC_CHIP_CAPABILITY_M_VERSION 95
#define BTM_VSC_CHIP_CAPABILITY_S_VERSION 98

typedef struct {
  uint16_t data_mask;
  uint8_t* p_flags;
  uint8_t ad_data[BTM_BLE_AD_DATA_LEN];
  uint8_t* p_pad;
} tBTM_BLE_LOCAL_ADV_DATA;

#define BTM_BLE_ISVALID_PARAM(x, min, max) \
  (((x) >= (min) && (x) <= (max)) || ((x) == BTM_BLE_CONN_PARAM_UNDEF))

typedef struct {
  uint16_t discoverable_mode;
  uint16_t connectable_mode;
  uint16_t scan_window;
  uint16_t scan_interval;
  uint8_t scan_type;             /* current scan type: active or passive */

  tBTM_BLE_AFP afp; /* advertising filter policy */
  tBTM_BLE_SFP sfp; /* scanning filter policy */

  tBLE_ADDR_TYPE adv_addr_type;
  uint8_t evt_type;

  uint8_t adv_mode;
  void enable_advertising_mode() { adv_mode = BTM_BLE_ADV_ENABLE; }
  void disable_advertising_mode() { adv_mode = BTM_BLE_ADV_DISABLE; }
  bool is_advertising_mode_enabled() const {
    return (adv_mode == BTM_BLE_ADV_ENABLE);
  }

  tBLE_BD_ADDR direct_bda;
  tBTM_BLE_EVT directed_conn;
  bool fast_adv_on;
  alarm_t* fast_adv_timer;

  /* inquiry BD addr database */
  tBTM_BLE_LOCAL_ADV_DATA adv_data;
  tBTM_BLE_ADV_CHNL_MAP adv_chnl_map;

  alarm_t* inquiry_timer;
  bool scan_rsp;
  uint8_t state; /* Current state that the inquiry process is in */
} tBTM_BLE_INQ_CB;

/* random address resolving complete callback */
typedef void(tBTM_BLE_RESOLVE_CBACK)(void* match_rec, void* p);

typedef void(tBTM_BLE_ADDR_CBACK)(const RawAddress& static_random, void* p);

/* random address management control block */
typedef struct {
  tBLE_ADDR_TYPE own_addr_type; /* local device LE address type */
  RawAddress private_addr;
  alarm_t* refresh_raddr_timer;
} tBTM_LE_RANDOM_CB;

/* acceptlist using state as a bit mask */
constexpr uint8_t BTM_BLE_WL_IDLE = 0;
constexpr uint8_t BTM_BLE_ACCEPTLIST_INIT = 1;

/* resolving list using state as a bit mask */
enum : uint8_t {
  BTM_BLE_RL_IDLE = 0,
  BTM_BLE_RL_INIT = (1 << 0),
  BTM_BLE_RL_SCAN = (1 << 1),
  BTM_BLE_RL_ADV = (1 << 2),
};
typedef uint8_t tBTM_BLE_RL_STATE;

typedef struct { void* p_param; } tBTM_BLE_CONN_REQ;

/* LE state request */
#define BTM_BLE_STATE_INVALID 0
#define BTM_BLE_STATE_INIT 2
#define BTM_BLE_STATE_MAX 11

#define BTM_BLE_STATE_CONN_ADV_BIT 0x0001
#define BTM_BLE_STATE_INIT_BIT 0x0002
#define BTM_BLE_STATE_CENTRAL_BIT 0x0004
#define BTM_BLE_STATE_PERIPHERAL_BIT 0x0008
#define BTM_BLE_STATE_LO_DUTY_DIR_ADV_BIT 0x0010
#define BTM_BLE_STATE_HI_DUTY_DIR_ADV_BIT 0x0020
#define BTM_BLE_STATE_NON_CONN_ADV_BIT 0x0040
#define BTM_BLE_STATE_PASSIVE_SCAN_BIT 0x0080
#define BTM_BLE_STATE_ACTIVE_SCAN_BIT 0x0100
#define BTM_BLE_STATE_SCAN_ADV_BIT 0x0200
typedef uint16_t tBTM_BLE_STATE_MASK;

#define BTM_BLE_STATE_ALL_MASK 0x03ff
#define BTM_BLE_STATE_ALL_ADV_MASK                                  \
  (BTM_BLE_STATE_CONN_ADV_BIT | BTM_BLE_STATE_LO_DUTY_DIR_ADV_BIT | \
   BTM_BLE_STATE_HI_DUTY_DIR_ADV_BIT | BTM_BLE_STATE_SCAN_ADV_BIT)
#define BTM_BLE_STATE_ALL_CONN_MASK \
  (BTM_BLE_STATE_CENTRAL_BIT | BTM_BLE_STATE_PERIPHERAL_BIT)

typedef struct {
  RawAddress* resolve_q_random_pseudo{nullptr};
  uint8_t* resolve_q_action{nullptr};
  uint8_t q_next;
  uint8_t q_pending;
} tBTM_BLE_RESOLVE_Q;

/* BLE privacy mode */
#define BTM_PRIVACY_NONE 0 /* BLE no privacy */
#define BTM_PRIVACY_1_1 1  /* BLE privacy 1.1, do not support privacy 1.0 */
#define BTM_PRIVACY_1_2 2  /* BLE privacy 1.2 */
#define BTM_PRIVACY_MIXED \
  3 /* BLE privacy mixed mode, broadcom propietary mode */
typedef uint8_t tBTM_PRIVACY_MODE;

/* Define BLE Device Management control structure
*/
constexpr uint8_t kBTM_BLE_INQUIRY_ACTIVE = 0x10;
constexpr uint8_t kBTM_BLE_OBSERVE_ACTIVE = 0x80;
constexpr size_t kCentralAndPeripheralCount = 2;

typedef struct {
 private:
  uint8_t scan_activity_; /* LE scan activity mask */

 public:
  bool is_ble_inquiry_active() const {
    return (scan_activity_ & kBTM_BLE_INQUIRY_ACTIVE);
  }
  bool is_ble_observe_active() const {
    return (scan_activity_ & kBTM_BLE_OBSERVE_ACTIVE);
  }

  void set_ble_inquiry_active() { scan_activity_ |= kBTM_BLE_INQUIRY_ACTIVE; }
  void set_ble_observe_active() { scan_activity_ |= kBTM_BLE_OBSERVE_ACTIVE; }

  void reset_ble_inquiry() { scan_activity_ &= ~kBTM_BLE_INQUIRY_ACTIVE; }
  void reset_ble_observe() { scan_activity_ &= ~kBTM_BLE_OBSERVE_ACTIVE; }

  bool is_ble_scan_active() const {
    return (is_ble_inquiry_active() || is_ble_observe_active());
  }

  /*****************************************************
  **      BLE Inquiry
  *****************************************************/
  tBTM_BLE_INQ_CB inq_var;

  /* observer callback and timer */
  tBTM_INQ_RESULTS_CB* p_obs_results_cb;
  tBTM_CMPL_CB* p_obs_cmpl_cb;
  alarm_t* observer_timer;

  /* opportunistic observer */
  tBTM_INQ_RESULTS_CB* p_opportunistic_obs_results_cb;

  /* target announcement observer */
  tBTM_INQ_RESULTS_CB* p_target_announcement_obs_results_cb;

 private:
  enum : uint8_t { /* BLE connection state */
                   BLE_CONN_IDLE = 0,
                   BLE_CONNECTING = 2,
                   BLE_CONN_CANCEL = 3,
  } conn_state_{BLE_CONN_IDLE};

 public:
  bool is_connection_state_idle() const { return conn_state_ == BLE_CONN_IDLE; }
  bool is_connection_state_connecting() const {
    return conn_state_ == BLE_CONNECTING;
  }
  bool is_connection_state_cancelled() const {
    return conn_state_ == BLE_CONN_CANCEL;
  }
  void set_connection_state_idle() { conn_state_ = BLE_CONN_IDLE; }
  void set_connection_state_connecting() { conn_state_ = BLE_CONNECTING; }
  void set_connection_state_cancelled() { conn_state_ = BLE_CONN_CANCEL; }

  /* random address management control block */
  tBTM_LE_RANDOM_CB addr_mgnt_cb;

  tBTM_PRIVACY_MODE privacy_mode;    /* privacy mode */
  uint8_t resolving_list_avail_size; /* resolving list available size */
  tBTM_BLE_RESOLVE_Q resolving_list_pend_q; /* Resolving list queue */
  tBTM_BLE_RL_STATE suspended_rl_state;     /* Suspended resolving list state */
  /* IRK list availability mask, up to max entry bits */
  uint8_t* irk_list_mask{nullptr};
  tBTM_BLE_RL_STATE rl_state; /* Resolving list state */

  /* current BLE link state */
  tBTM_BLE_STATE_MASK cur_states; /* bit mask of tBTM_BLE_STATE */

  uint8_t link_count[kCentralAndPeripheralCount]; /* total link count central
                                                     and peripheral*/
} tBTM_BLE_CB;

#endif  // BTM_BLE_INT_TYPES_H
