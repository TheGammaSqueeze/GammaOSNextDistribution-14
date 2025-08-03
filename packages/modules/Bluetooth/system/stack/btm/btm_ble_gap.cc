/******************************************************************************
 *
 *  Copyright 2008-2014 Broadcom Corporation
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
 *  This file contains functions for BLE GAP.
 *
 ******************************************************************************/

#define LOG_TAG "bt_btm_ble"

#include <android_bluetooth_flags.h>
#include <android_bluetooth_sysprop.h>
#include <base/functional/bind.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>
#include <cutils/properties.h>

#include <cstdint>
#include <list>
#include <memory>
#include <type_traits>
#include <vector>

#include "bta/include/bta_api.h"
#include "common/time_util.h"
#include "device/include/controller.h"
#include "hci/controller.h"
#include "hci/controller_interface.h"
#include "include/check.h"
#include "main/shim/acl_api.h"
#include "main/shim/entry.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "osi/include/properties.h"
#include "osi/include/stack_power_telemetry.h"
#include "stack/acl/acl.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/btm/btm_ble_int_types.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/acl_api.h"
#include "stack/include/advertise_data_parser.h"
#include "stack/include/ble_scanner.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_ble_addr.h"
#include "stack/include/btm_ble_privacy.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/gap_api.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/inq_hci_link_interface.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

void btm_inq_remote_name_timer_timeout(void* data);
void btm_ble_adv_filter_init(void);

#define BTM_EXT_BLE_RMT_NAME_TIMEOUT_MS (30 * 1000)
#define MIN_ADV_LENGTH 2
#define BTM_VSC_CHIP_CAPABILITY_RSP_LEN 9
#define BTM_VSC_CHIP_CAPABILITY_RSP_LEN_L_RELEASE \
  BTM_VSC_CHIP_CAPABILITY_RSP_LEN

#ifndef BTM_VSC_CHIP_CAPABILITY_RSP_LEN_M_RELEASE
#define BTM_VSC_CHIP_CAPABILITY_RSP_LEN_M_RELEASE 15
#endif

#define BTM_VSC_CHIP_CAPABILITY_RSP_LEN_S_RELEASE 25

/* Sysprop paths for scan parameters */
static const char kPropertyInquiryScanInterval[] =
    "bluetooth.core.le.inquiry_scan_interval";
static const char kPropertyInquiryScanWindow[] =
    "bluetooth.core.le.inquiry_scan_window";

static void btm_ble_start_scan();
static void btm_ble_stop_scan();
static tBTM_STATUS btm_ble_stop_adv(void);
static tBTM_STATUS btm_ble_start_adv(void);

using bluetooth::shim::GetController;

namespace {

constexpr char kBtmLogTag[] = "SCAN";

class AdvertisingCache {
 public:
  /* Set the data to |data| for device |addr_type, addr| */
  const std::vector<uint8_t>& Set(uint8_t addr_type, const RawAddress& addr,
                                  std::vector<uint8_t> data) {
    auto it = Find(addr_type, addr);
    if (it != items.end()) {
      it->data = std::move(data);
      return it->data;
    }

    if (items.size() > cache_max) {
      items.pop_back();
    }

    items.emplace_front(addr_type, addr, std::move(data));
    return items.front().data;
  }

  bool Exist(uint8_t addr_type, const RawAddress& addr) {
    auto it = Find(addr_type, addr);
    if (it != items.end()) {
      return true;
    }
    return false;
  }

  /* Append |data| for device |addr_type, addr| */
  const std::vector<uint8_t>& Append(uint8_t addr_type, const RawAddress& addr,
                                     std::vector<uint8_t> data) {
    auto it = Find(addr_type, addr);
    if (it != items.end()) {
      it->data.insert(it->data.end(), data.begin(), data.end());
      return it->data;
    }

    if (items.size() > cache_max) {
      items.pop_back();
    }

    items.emplace_front(addr_type, addr, std::move(data));
    return items.front().data;
  }

  /* Clear data for device |addr_type, addr| */
  void Clear(uint8_t addr_type, const RawAddress& addr) {
    auto it = Find(addr_type, addr);
    if (it != items.end()) {
      items.erase(it);
    }
  }

  void ClearAll() { items.clear(); }

 private:
  struct Item {
    uint8_t addr_type;
    RawAddress addr;
    std::vector<uint8_t> data;

    Item(uint8_t addr_type, const RawAddress& addr, std::vector<uint8_t> data)
        : addr_type(addr_type), addr(addr), data(data) {}
  };

  std::list<Item>::iterator Find(uint8_t addr_type, const RawAddress& addr) {
    for (auto it = items.begin(); it != items.end(); it++) {
      if (it->addr_type == addr_type && it->addr == addr) {
        return it;
      }
    }
    return items.end();
  }

  /* we keep maximum 7 devices in the cache */
  const size_t cache_max = 7;
  std::list<Item> items;
};

/* Devices in this cache are waiting for eiter scan response, or chained packets
 * on secondary channel */
AdvertisingCache cache;

}  // namespace

bool ble_vnd_is_included() {
  // replace build time config BLE_VND_INCLUDED with runtime
  return GET_SYSPROP(Ble, vnd_included, true);
}

static tBTM_BLE_CTRL_FEATURES_CBACK* p_ctrl_le_feature_rd_cmpl_cback = NULL;
/**********PAST & PS *******************/
using StartSyncCb = base::Callback<void(
    uint8_t /*status*/, uint16_t /*sync_handle*/, uint8_t /*advertising_sid*/,
    uint8_t /*address_type*/, RawAddress /*address*/, uint8_t /*phy*/,
    uint16_t /*interval*/)>;
using SyncReportCb = base::Callback<void(
    uint16_t /*sync_handle*/, int8_t /*tx_power*/, int8_t /*rssi*/,
    uint8_t /*status*/, std::vector<uint8_t> /*data*/)>;
using SyncLostCb = base::Callback<void(uint16_t /*sync_handle*/)>;
using SyncTransferCb = base::Callback<void(uint8_t /*status*/, RawAddress)>;
#define MAX_SYNC_TRANSACTION 16
#define SYNC_TIMEOUT (30 * 1000)
#define ADV_SYNC_ESTB_EVT_LEN 16
#define SYNC_LOST_EVT_LEN 3
typedef enum {
  PERIODIC_SYNC_IDLE = 0,
  PERIODIC_SYNC_PENDING,
  PERIODIC_SYNC_ESTABLISHED,
  PERIODIC_SYNC_LOST,
} tBTM_BLE_PERIODIC_SYNC_STATE;

struct alarm_t* sync_timeout_alarm;
typedef struct {
  uint8_t sid;
  RawAddress remote_bda;
  tBTM_BLE_PERIODIC_SYNC_STATE sync_state;
  uint16_t sync_handle;
  bool in_use;
  StartSyncCb sync_start_cb;
  SyncReportCb sync_report_cb;
  SyncLostCb sync_lost_cb;
  BigInfoReportCb biginfo_report_cb;
} tBTM_BLE_PERIODIC_SYNC;

typedef struct {
  bool in_use;
  int conn_handle;
  RawAddress addr;
  SyncTransferCb cb;
} tBTM_BLE_PERIODIC_SYNC_TRANSFER;

static list_t* sync_queue;
static std::mutex sync_queue_mutex_;

typedef struct {
  bool busy;
  uint8_t sid;
  RawAddress address;
  uint16_t skip;
  uint16_t timeout;
} sync_node_t;
typedef struct {
  uint8_t sid;
  RawAddress address;
} remove_sync_node_t;
typedef enum {
  BTM_QUEUE_SYNC_REQ_EVT,
  BTM_QUEUE_SYNC_ADVANCE_EVT,
  BTM_QUEUE_SYNC_CLEANUP_EVT
} btif_queue_event_t;

typedef struct {
  tBTM_BLE_PERIODIC_SYNC p_sync[MAX_SYNC_TRANSACTION];
  tBTM_BLE_PERIODIC_SYNC_TRANSFER sync_transfer[MAX_SYNC_TRANSACTION];
} tBTM_BLE_PA_SYNC_TX_CB;
tBTM_BLE_PA_SYNC_TX_CB btm_ble_pa_sync_cb;
StartSyncCb sync_rcvd_cb;
static bool syncRcvdCbRegistered = false;
static int btm_ble_get_psync_index(uint8_t adv_sid, RawAddress addr);
static void btm_ble_start_sync_timeout(void* data);

/*****************************/
/*******************************************************************************
 *  Local functions
 ******************************************************************************/
static void btm_ble_update_adv_flag(uint8_t flag);
void btm_ble_process_adv_pkt_cont(uint16_t evt_type, tBLE_ADDR_TYPE addr_type,
                                  const RawAddress& bda, uint8_t primary_phy,
                                  uint8_t secondary_phy,
                                  uint8_t advertising_sid, int8_t tx_power,
                                  int8_t rssi, uint16_t periodic_adv_int,
                                  uint8_t data_len, const uint8_t* data,
                                  const RawAddress& original_bda);
static uint8_t btm_set_conn_mode_adv_init_addr(RawAddress& p_peer_addr_ptr,
                                               tBLE_ADDR_TYPE* p_peer_addr_type,
                                               tBLE_ADDR_TYPE* p_own_addr_type);
static void btm_ble_stop_observe(void);
static void btm_ble_fast_adv_timer_timeout(void* data);
static void btm_ble_start_slow_adv(void);
static void btm_ble_inquiry_timer_gap_limited_discovery_timeout(void* data);
static void btm_ble_inquiry_timer_timeout(void* data);
static void btm_ble_observer_timer_timeout(void* data);

enum : uint8_t {
  BTM_BLE_NOT_SCANNING = 0x00,
  BTM_BLE_INQ_RESULT = 0x01,
  BTM_BLE_OBS_RESULT = 0x02,
};

static bool ble_evt_type_is_connectable(uint16_t evt_type) {
  return evt_type & (1 << BLE_EVT_CONNECTABLE_BIT);
}

static bool ble_evt_type_is_scannable(uint16_t evt_type) {
  return evt_type & (1 << BLE_EVT_SCANNABLE_BIT);
}

static bool ble_evt_type_is_directed(uint16_t evt_type) {
  return evt_type & (1 << BLE_EVT_DIRECTED_BIT);
}

static bool ble_evt_type_is_scan_resp(uint16_t evt_type) {
  return evt_type & (1 << BLE_EVT_SCAN_RESPONSE_BIT);
}

static bool ble_evt_type_is_legacy(uint16_t evt_type) {
  return evt_type & (1 << BLE_EVT_LEGACY_BIT);
}

static uint8_t ble_evt_type_data_status(uint16_t evt_type) {
  return (evt_type >> 5) & 3;
}

constexpr uint8_t UNSUPPORTED = 255;

/* LE states combo bit to check */
const uint8_t btm_le_state_combo_tbl[BTM_BLE_STATE_MAX][BTM_BLE_STATE_MAX] = {
    {
        /* single state support */
        HCI_LE_STATES_CONN_ADV_BIT,   /* conn_adv */
        HCI_LE_STATES_INIT_BIT,       /* init */
        HCI_LE_STATES_INIT_BIT,       /* central */
        HCI_LE_STATES_PERIPHERAL_BIT, /* peripheral */
        UNSUPPORTED,                  /* todo: lo du dir adv, not covered ? */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_BIT, /* hi duty dir adv */
        HCI_LE_STATES_NON_CONN_ADV_BIT,    /* non connectable adv */
        HCI_LE_STATES_PASS_SCAN_BIT,       /*  passive scan */
        HCI_LE_STATES_ACTIVE_SCAN_BIT,     /*   active scan */
        HCI_LE_STATES_SCAN_ADV_BIT         /* scanable adv */
    },
    {
        /* conn_adv =0 */
        UNSUPPORTED,                            /* conn_adv */
        HCI_LE_STATES_CONN_ADV_INIT_BIT,        /* init: 32 */
        HCI_LE_STATES_CONN_ADV_CENTRAL_BIT,     /* central: 35 */
        HCI_LE_STATES_CONN_ADV_PERIPHERAL_BIT,  /* peripheral: 38,*/
        UNSUPPORTED,                            /* lo du dir adv */
        UNSUPPORTED,                            /* hi duty dir adv */
        UNSUPPORTED,                            /* non connectable adv */
        HCI_LE_STATES_CONN_ADV_PASS_SCAN_BIT,   /*  passive scan */
        HCI_LE_STATES_CONN_ADV_ACTIVE_SCAN_BIT, /*   active scan */
        UNSUPPORTED                             /* scanable adv */
    },
    {
        /* init */
        HCI_LE_STATES_CONN_ADV_INIT_BIT,           /* conn_adv: 32 */
        UNSUPPORTED,                               /* init */
        HCI_LE_STATES_INIT_CENTRAL_BIT,            /* central 28 */
        HCI_LE_STATES_INIT_CENTRAL_PERIPHERAL_BIT, /* peripheral 41 */
        HCI_LE_STATES_LO_DUTY_DIR_ADV_INIT_BIT,    /* lo du dir adv 34 */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_INIT_BIT,    /* hi duty dir adv 33 */
        HCI_LE_STATES_NON_CONN_INIT_BIT,           /*  non connectable adv */
        HCI_LE_STATES_PASS_SCAN_INIT_BIT,          /* passive scan */
        HCI_LE_STATES_ACTIVE_SCAN_INIT_BIT,        /*  active scan */
        HCI_LE_STATES_SCAN_ADV_INIT_BIT            /* scanable adv */

    },
    {
        /* central */
        HCI_LE_STATES_CONN_ADV_CENTRAL_BIT,        /* conn_adv: 35 */
        HCI_LE_STATES_INIT_CENTRAL_BIT,            /* init 28 */
        HCI_LE_STATES_INIT_CENTRAL_BIT,            /* central 28 */
        HCI_LE_STATES_CONN_ADV_INIT_BIT,           /* peripheral: 32 */
        HCI_LE_STATES_LO_DUTY_DIR_ADV_CENTRAL_BIT, /* lo duty cycle adv 37 */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_CENTRAL_BIT, /* hi duty cycle adv 36 */
        HCI_LE_STATES_NON_CONN_ADV_CENTRAL_BIT,    /*  non connectable adv*/
        HCI_LE_STATES_PASS_SCAN_CENTRAL_BIT,       /*  passive scan */
        HCI_LE_STATES_ACTIVE_SCAN_CENTRAL_BIT,     /*   active scan */
        HCI_LE_STATES_SCAN_ADV_CENTRAL_BIT         /*  scanable adv */

    },
    {
        /* peripheral */
        HCI_LE_STATES_CONN_ADV_PERIPHERAL_BIT,        /* conn_adv: 38,*/
        HCI_LE_STATES_INIT_CENTRAL_PERIPHERAL_BIT,    /* init 41 */
        HCI_LE_STATES_INIT_CENTRAL_PERIPHERAL_BIT,    /* central 41 */
        HCI_LE_STATES_CONN_ADV_PERIPHERAL_BIT,        /* peripheral: 38,*/
        HCI_LE_STATES_LO_DUTY_DIR_ADV_PERIPHERAL_BIT, /* lo duty cycle adv 40 */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_PERIPHERAL_BIT, /* hi duty cycle adv 39 */
        HCI_LE_STATES_NON_CONN_ADV_PERIPHERAL_BIT,    /* non connectable adv */
        HCI_LE_STATES_PASS_SCAN_PERIPHERAL_BIT,       /* passive scan */
        HCI_LE_STATES_ACTIVE_SCAN_PERIPHERAL_BIT,     /*  active scan */
        HCI_LE_STATES_SCAN_ADV_PERIPHERAL_BIT         /* scanable adv */

    },
    {
        /* lo duty cycle adv */
        UNSUPPORTED,                                  /* conn_adv: 38,*/
        HCI_LE_STATES_LO_DUTY_DIR_ADV_INIT_BIT,       /* init 34 */
        HCI_LE_STATES_LO_DUTY_DIR_ADV_CENTRAL_BIT,    /* central 37 */
        HCI_LE_STATES_LO_DUTY_DIR_ADV_PERIPHERAL_BIT, /* peripheral: 40 */
        UNSUPPORTED,                                  /* lo duty cycle adv 40 */
        UNSUPPORTED,                                  /* hi duty cycle adv 39 */
        UNSUPPORTED,                                  /*  non connectable adv */
        UNSUPPORTED, /* TODO: passive scan, not covered? */
        UNSUPPORTED, /* TODO:  active scan, not covered? */
        UNSUPPORTED  /*  scanable adv */
    },
    {
        /* hi duty cycle adv */
        UNSUPPORTED,                                  /* conn_adv: 38,*/
        HCI_LE_STATES_HI_DUTY_DIR_ADV_INIT_BIT,       /* init 33 */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_CENTRAL_BIT,    /* central 36 */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_PERIPHERAL_BIT, /* peripheral: 39*/
        UNSUPPORTED,                                  /* lo duty cycle adv 40 */
        UNSUPPORTED,                                  /* hi duty cycle adv 39 */
        UNSUPPORTED,                                  /* non connectable adv */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_PASS_SCAN_BIT,  /* passive scan */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_ACTIVE_SCAN_BIT, /* active scan */
        UNSUPPORTED                                    /* scanable adv */
    },
    {
        /* non connectable adv */
        UNSUPPORTED,                                /* conn_adv: */
        HCI_LE_STATES_NON_CONN_INIT_BIT,            /* init  */
        HCI_LE_STATES_NON_CONN_ADV_CENTRAL_BIT,     /* central  */
        HCI_LE_STATES_NON_CONN_ADV_PERIPHERAL_BIT,  /* peripheral: */
        UNSUPPORTED,                                /* lo duty cycle adv */
        UNSUPPORTED,                                /* hi duty cycle adv */
        UNSUPPORTED,                                /* non connectable adv */
        HCI_LE_STATES_NON_CONN_ADV_PASS_SCAN_BIT,   /* passive scan */
        HCI_LE_STATES_NON_CONN_ADV_ACTIVE_SCAN_BIT, /* active scan */
        UNSUPPORTED                                 /* scanable adv */
    },
    {
        /* passive scan */
        HCI_LE_STATES_CONN_ADV_PASS_SCAN_BIT,        /* conn_adv: */
        HCI_LE_STATES_PASS_SCAN_INIT_BIT,            /* init  */
        HCI_LE_STATES_PASS_SCAN_CENTRAL_BIT,         /* central  */
        HCI_LE_STATES_PASS_SCAN_PERIPHERAL_BIT,      /* peripheral: */
        UNSUPPORTED,                                 /* lo duty cycle adv */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_PASS_SCAN_BIT, /* hi duty cycle adv */
        HCI_LE_STATES_NON_CONN_ADV_PASS_SCAN_BIT,    /* non connectable adv */
        UNSUPPORTED,                                 /* passive scan */
        UNSUPPORTED,                                 /* active scan */
        HCI_LE_STATES_SCAN_ADV_PASS_SCAN_BIT         /* scanable adv */
    },
    {
        /* active scan */
        HCI_LE_STATES_CONN_ADV_ACTIVE_SCAN_BIT,        /* conn_adv: */
        HCI_LE_STATES_ACTIVE_SCAN_INIT_BIT,            /* init  */
        HCI_LE_STATES_ACTIVE_SCAN_CENTRAL_BIT,         /* central  */
        HCI_LE_STATES_ACTIVE_SCAN_PERIPHERAL_BIT,      /* peripheral: */
        UNSUPPORTED,                                   /* lo duty cycle adv */
        HCI_LE_STATES_HI_DUTY_DIR_ADV_ACTIVE_SCAN_BIT, /* hi duty cycle adv */
        HCI_LE_STATES_NON_CONN_ADV_ACTIVE_SCAN_BIT, /*  non connectable adv */
        UNSUPPORTED,                                /* TODO: passive scan */
        UNSUPPORTED,                                /* TODO:  active scan */
        HCI_LE_STATES_SCAN_ADV_ACTIVE_SCAN_BIT      /*  scanable adv */
    },
    {
        /* scanable adv */
        UNSUPPORTED,                            /* conn_adv: */
        HCI_LE_STATES_SCAN_ADV_INIT_BIT,        /* init  */
        HCI_LE_STATES_SCAN_ADV_CENTRAL_BIT,     /* central  */
        HCI_LE_STATES_SCAN_ADV_PERIPHERAL_BIT,  /* peripheral: */
        UNSUPPORTED,                            /* lo duty cycle adv */
        UNSUPPORTED,                            /* hi duty cycle adv */
        UNSUPPORTED,                            /* non connectable adv */
        HCI_LE_STATES_SCAN_ADV_PASS_SCAN_BIT,   /*  passive scan */
        HCI_LE_STATES_SCAN_ADV_ACTIVE_SCAN_BIT, /*  active scan */
        UNSUPPORTED                             /* scanable adv */
    }};

/* check LE combo state supported */
inline bool BTM_LE_STATES_SUPPORTED(const uint8_t* x, uint8_t bit_num) {
  uint8_t mask = 1 << (bit_num % 8);
  uint8_t offset = bit_num / 8;
  return ((x)[offset] & mask);
}

void BTM_BleOpportunisticObserve(bool enable,
                                 tBTM_INQ_RESULTS_CB* p_results_cb) {
  if (enable) {
    btm_cb.ble_ctr_cb.p_opportunistic_obs_results_cb = p_results_cb;
  } else {
    btm_cb.ble_ctr_cb.p_opportunistic_obs_results_cb = NULL;
  }
}

void BTM_BleTargetAnnouncementObserve(bool enable,
                                      tBTM_INQ_RESULTS_CB* p_results_cb) {
  if (enable) {
    btm_cb.ble_ctr_cb.p_target_announcement_obs_results_cb = p_results_cb;
  } else {
    btm_cb.ble_ctr_cb.p_target_announcement_obs_results_cb = NULL;
  }
}

std::pair<uint16_t /* interval */, uint16_t /* window */>
get_low_latency_scan_params() {
  uint16_t scan_interval = osi_property_get_int32(kPropertyInquiryScanInterval,
                                                  BTM_BLE_LOW_LATENCY_SCAN_INT);
  uint16_t scan_window = osi_property_get_int32(kPropertyInquiryScanWindow,
                                                BTM_BLE_LOW_LATENCY_SCAN_WIN);

  return std::make_pair(scan_interval, scan_window);
}

/*******************************************************************************
 *
 * Function         BTM_BleObserve
 *
 * Description      This procedure keep the device listening for advertising
 *                  events from a broadcast device.
 *
 * Parameters       start: start or stop observe.
 *                  duration: how long the scan should last, in seconds. 0 means
 *                  scan without timeout. Starting the scan second time without
 *                  timeout will disable the timer.
 *                  low_latency_scan: whether this is a low latency scan,
 *                                    default is false.
 *
 * Returns          void
 *
 ******************************************************************************/
tBTM_STATUS BTM_BleObserve(bool start, uint8_t duration,
                           tBTM_INQ_RESULTS_CB* p_results_cb,
                           tBTM_CMPL_CB* p_cmpl_cb, bool low_latency_scan) {
  tBTM_STATUS status = BTM_WRONG_MODE;

  uint16_t scan_interval = !btm_cb.ble_ctr_cb.inq_var.scan_interval
                               ? BTM_BLE_GAP_DISC_SCAN_INT
                               : btm_cb.ble_ctr_cb.inq_var.scan_interval;
  uint16_t scan_window = !btm_cb.ble_ctr_cb.inq_var.scan_window
                             ? BTM_BLE_GAP_DISC_SCAN_WIN
                             : btm_cb.ble_ctr_cb.inq_var.scan_window;

  // use low latency scanning if the scanning is active
  uint16_t ll_scan_interval, ll_scan_window;
  std::tie(ll_scan_interval, ll_scan_window) = get_low_latency_scan_params();
  if (low_latency_scan) {
    std::tie(scan_interval, scan_window) =
        std::tie(ll_scan_interval, ll_scan_window);
  }

  log::verbose("scan_type:{}, {}, {}", btm_cb.ble_ctr_cb.inq_var.scan_type,
               scan_interval, scan_window);

  if (!controller_get_interface()->SupportsBle()) return BTM_ILLEGAL_VALUE;

  if (start) {
    /* shared inquiry database, do not allow observe if any inquiry is active.
     * except we are doing CSIS active scanning
     */
    if (btm_cb.ble_ctr_cb.is_ble_observe_active()) {
      if (duration == 0) {
        if (alarm_is_scheduled(btm_cb.ble_ctr_cb.observer_timer)) {
          alarm_cancel(btm_cb.ble_ctr_cb.observer_timer);
        } else {
          log::error("Scan with no duration started twice!");
        }
      } else {
        if (!low_latency_scan &&
            alarm_is_scheduled(btm_cb.ble_ctr_cb.observer_timer)) {
          log::error("Scan with duration started twice!");
        }
      }
      /*
       * we stop current observation request for below scenarios
       * 1. if the scan we wish to start is not low latency
       * 2. current ongoing scanning is low latency
       */
      bool is_ongoing_low_latency =
          btm_cb.ble_ctr_cb.inq_var.scan_interval == ll_scan_interval &&
          btm_cb.ble_ctr_cb.inq_var.scan_window == ll_scan_window;
      if (!low_latency_scan || is_ongoing_low_latency) {
        log::warn("Observer was already active, is_low_latency: {}",
                  is_ongoing_low_latency);
        return BTM_CMD_STARTED;
      }
      // stop any scan without low latency config
      btm_ble_stop_observe();
    }

    btm_cb.ble_ctr_cb.p_obs_results_cb = p_results_cb;
    btm_cb.ble_ctr_cb.p_obs_cmpl_cb = p_cmpl_cb;
    status = BTM_CMD_STARTED;

    /* scan is not started */
    if (!btm_cb.ble_ctr_cb.is_ble_scan_active()) {
      /* allow config of scan type */
      cache.ClearAll();
      btm_cb.ble_ctr_cb.inq_var.scan_type =
          (btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_NONE)
              ? BTM_BLE_SCAN_MODE_ACTI
              : btm_cb.ble_ctr_cb.inq_var.scan_type;
      btm_send_hci_set_scan_params(
          btm_cb.ble_ctr_cb.inq_var.scan_type, (uint16_t)scan_interval,
          (uint16_t)scan_window, btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type,
          BTM_BLE_DEFAULT_SFP);

      btm_ble_start_scan();
    }

    btm_cb.neighbor.le_observe = {
        .start_time_ms = timestamper_in_milliseconds.GetTimestamp(),
        .results = 0,
    };

    BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le observe started",
                   base::StringPrintf("low latency scanning enabled: %d",
                                      low_latency_scan));

    if (status == BTM_CMD_STARTED) {
      btm_cb.ble_ctr_cb.set_ble_observe_active();
      if (duration != 0) {
        /* start observer timer */
        uint64_t duration_ms = duration * 1000;
        alarm_set_on_mloop(btm_cb.ble_ctr_cb.observer_timer, duration_ms,
                           btm_ble_observer_timer_timeout, NULL);
      }
    }
  } else if (btm_cb.ble_ctr_cb.is_ble_observe_active()) {
    const unsigned long long duration_timestamp =
        timestamper_in_milliseconds.GetTimestamp() -
        btm_cb.neighbor.le_observe.start_time_ms;
    BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le observe stopped",
                   base::StringPrintf("duration_s:%6.3f results:%-3lu",
                                      (double)duration_timestamp / 1000.0,
                                      btm_cb.neighbor.le_observe.results));
    status = BTM_CMD_STARTED;
    btm_ble_stop_observe();
  } else {
    log::error("Observe not active");
  }

  return status;
}

static void btm_get_dynamic_audio_buffer_vsc_cmpl_cback(
    tBTM_VSC_CMPL* p_vsc_cmpl_params) {
  log::info("");

  if (p_vsc_cmpl_params->param_len < 1) {
    log::error("The length of returned parameters is less than 1");
    return;
  }
  uint8_t* p_event_param_buf = p_vsc_cmpl_params->p_param_buf;
  uint8_t status = 0xff;
  uint8_t opcode = 0xff;
  uint32_t codec_mask = 0xffffffff;

  // [Return Parameter]         | [Size]   | [Purpose]
  // Status                     | 1 octet  | Command complete status
  // Dynamic_Audio_Buffer_opcode| 1 octet  | 0x01 - Get buffer time
  // Audio_Codedc_Type_Supported| 4 octet  | Bit masks for selected codec types
  // Audio_Codec_Buffer_Time    | 192 octet| Default/Max/Min buffer time
  STREAM_TO_UINT8(status, p_event_param_buf);
  if (status != HCI_SUCCESS) {
    log::error("Fail to configure DFTB. status: {}", loghex(status));
    return;
  }

  if (p_vsc_cmpl_params->param_len != 198) {
    log::fatal("The length of returned parameters is not equal to 198: {}",
               p_vsc_cmpl_params->param_len);
    return;
  }

  STREAM_TO_UINT8(opcode, p_event_param_buf);
  log::info("opcode = {}", loghex(opcode));

  if (opcode == 0x01) {
    STREAM_TO_UINT32(codec_mask, p_event_param_buf);
    log::info("codec_mask = {}", loghex(codec_mask));

    for (int i = 0; i < BTM_CODEC_TYPE_MAX_RECORDS; i++) {
      STREAM_TO_UINT16(btm_cb.dynamic_audio_buffer_cb[i].default_buffer_time,
                       p_event_param_buf);
      STREAM_TO_UINT16(btm_cb.dynamic_audio_buffer_cb[i].maximum_buffer_time,
                       p_event_param_buf);
      STREAM_TO_UINT16(btm_cb.dynamic_audio_buffer_cb[i].minimum_buffer_time,
                       p_event_param_buf);
    }

    log::info("Succeed to receive Media Tx Buffer.");
  }
}

/*******************************************************************************
 *
 * Function         btm_vsc_brcm_features_complete
 *
 * Description      Command Complete callback for HCI_BLE_VENDOR_CAP
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_vendor_capability_vsc_cmpl_cback(
    tBTM_VSC_CMPL* p_vcs_cplt_params) {
  log::verbose("");

  /* Check status of command complete event */
  CHECK(p_vcs_cplt_params->opcode == HCI_BLE_VENDOR_CAP);
  CHECK(p_vcs_cplt_params->param_len > 0);

  const uint8_t* p = p_vcs_cplt_params->p_param_buf;
  uint8_t raw_status;
  STREAM_TO_UINT8(raw_status, p);
  tHCI_STATUS status = to_hci_status_code(raw_status);

  if (status != HCI_SUCCESS) {
    log::verbose("Status = 0x{:02x} (0 is success)", status);
    return;
  }
  CHECK(p_vcs_cplt_params->param_len >= BTM_VSC_CHIP_CAPABILITY_RSP_LEN);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.adv_inst_max, p);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.rpa_offloading, p);
  STREAM_TO_UINT16(btm_cb.cmn_ble_vsc_cb.tot_scan_results_strg, p);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.max_irk_list_sz, p);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.filter_support, p);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.max_filter, p);
  STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.energy_support, p);

  if (p_vcs_cplt_params->param_len >
      BTM_VSC_CHIP_CAPABILITY_RSP_LEN_L_RELEASE) {
    STREAM_TO_UINT16(btm_cb.cmn_ble_vsc_cb.version_supported, p);
  } else {
    btm_cb.cmn_ble_vsc_cb.version_supported = BTM_VSC_CHIP_CAPABILITY_L_VERSION;
  }

  if (btm_cb.cmn_ble_vsc_cb.version_supported >=
      BTM_VSC_CHIP_CAPABILITY_M_VERSION) {

    /*
     * If the device has a BT firmware that advertises compliance with Marshmallow
     * LE capabilities but fails to pass the minimum expected response length, skip
     * checking its reported response length against the minimum expected by M_RELEASE
     * if said device is known to have Bluetooth working again by simply skipping past
     * this, and has the respective system property set.
     */
    if (!property_get_bool("bluetooth.device.m_rsplen_workaround", false)) {
      CHECK(p_vcs_cplt_params->param_len >=
          BTM_VSC_CHIP_CAPABILITY_RSP_LEN_M_RELEASE);
    }

    STREAM_TO_UINT16(btm_cb.cmn_ble_vsc_cb.total_trackable_advertisers, p);
    STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.extended_scan_support, p);
    STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.debug_logging_supported, p);
  }

  if (btm_cb.cmn_ble_vsc_cb.version_supported >=
      BTM_VSC_CHIP_CAPABILITY_S_VERSION) {
    if (p_vcs_cplt_params->param_len >=
        BTM_VSC_CHIP_CAPABILITY_RSP_LEN_S_RELEASE) {
      STREAM_TO_UINT8(
          btm_cb.cmn_ble_vsc_cb.le_address_generation_offloading_support, p);
      STREAM_TO_UINT32(
          btm_cb.cmn_ble_vsc_cb.a2dp_source_offload_capability_mask, p);
      STREAM_TO_UINT8(btm_cb.cmn_ble_vsc_cb.quality_report_support, p);
      STREAM_TO_UINT32(btm_cb.cmn_ble_vsc_cb.dynamic_audio_buffer_support, p);

      if (btm_cb.cmn_ble_vsc_cb.dynamic_audio_buffer_support != 0) {
        uint8_t param[3] = {0};
        uint8_t* p_param = param;

        UINT8_TO_STREAM(p_param, HCI_CONTROLLER_DAB_GET_BUFFER_TIME);
        BTM_VendorSpecificCommand(HCI_CONTROLLER_DAB, p_param - param, param,
                                  btm_get_dynamic_audio_buffer_vsc_cmpl_cback);
      }
    }
  }

  if (btm_cb.cmn_ble_vsc_cb.filter_support == 1 &&
      controller_get_interface()->get_bt_version()->manufacturer ==
          LMP_COMPID_QTI) {
    // QTI controller, TDS data filter are supported by default. Check is added
    // to keep backward compatibility.
    btm_cb.cmn_ble_vsc_cb.adv_filter_extended_features_mask = 0x01;
  } else {
    btm_cb.cmn_ble_vsc_cb.adv_filter_extended_features_mask = 0x00;
  }

  btm_cb.cmn_ble_vsc_cb.values_read = true;

  log::verbose("stat={}, irk={}, ADV ins:{}, rpa={}, ener={}, ext_scan={}",
               status, btm_cb.cmn_ble_vsc_cb.max_irk_list_sz,
               btm_cb.cmn_ble_vsc_cb.adv_inst_max,
               btm_cb.cmn_ble_vsc_cb.rpa_offloading,
               btm_cb.cmn_ble_vsc_cb.energy_support,
               btm_cb.cmn_ble_vsc_cb.extended_scan_support);

  if (btm_cb.cmn_ble_vsc_cb.max_filter > 0) btm_ble_adv_filter_init();

  /* VS capability included and non-4.2 device */
  if (controller_get_interface()->SupportsBle() &&
      controller_get_interface()->SupportsBlePrivacy() &&
      btm_cb.cmn_ble_vsc_cb.max_irk_list_sz > 0 &&
      controller_get_interface()->get_ble_resolving_list_max_size() == 0)
    btm_ble_resolving_list_init(btm_cb.cmn_ble_vsc_cb.max_irk_list_sz);

  if (p_ctrl_le_feature_rd_cmpl_cback != NULL)
    p_ctrl_le_feature_rd_cmpl_cback(static_cast<tHCI_STATUS>(status));
}

/*******************************************************************************
 *
 * Function         BTM_BleGetVendorCapabilities
 *
 * Description      This function reads local LE features
 *
 * Parameters       p_cmn_vsc_cb : Locala LE capability structure
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleGetVendorCapabilities(tBTM_BLE_VSC_CB* p_cmn_vsc_cb) {
  if (NULL != p_cmn_vsc_cb) {
    *p_cmn_vsc_cb = btm_cb.cmn_ble_vsc_cb;
  }
}

void BTM_BleGetDynamicAudioBuffer(
    tBTM_BT_DYNAMIC_AUDIO_BUFFER_CB p_dynamic_audio_buffer_cb[]) {
  log::verbose("BTM_BleGetDynamicAudioBuffer");

  if (NULL != p_dynamic_audio_buffer_cb) {
    for (int i = 0; i < 32; i++) {
      p_dynamic_audio_buffer_cb[i] = btm_cb.dynamic_audio_buffer_cb[i];
    }
  }
}

/******************************************************************************
 *
 * Function         BTM_BleReadControllerFeatures
 *
 * Description      Reads BLE specific controller features
 *
 * Parameters:      tBTM_BLE_CTRL_FEATURES_CBACK : Callback to notify when
 *                  features are read
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_BleReadControllerFeatures(tBTM_BLE_CTRL_FEATURES_CBACK* p_vsc_cback) {
  if (!ble_vnd_is_included()) return;

  if (btm_cb.cmn_ble_vsc_cb.values_read) return;

  log::verbose("BTM_BleReadControllerFeatures");

  if (IS_FLAG_ENABLED(report_vsc_data_from_the_gd_controller)) {
    btm_cb.cmn_ble_vsc_cb.values_read = true;
    bluetooth::hci::Controller::VendorCapabilities vendor_capabilities =
        GetController()->GetVendorCapabilities();

    btm_cb.cmn_ble_vsc_cb.adv_inst_max =
        vendor_capabilities.max_advt_instances_;
    btm_cb.cmn_ble_vsc_cb.rpa_offloading =
        vendor_capabilities.offloaded_resolution_of_private_address_;
    btm_cb.cmn_ble_vsc_cb.tot_scan_results_strg =
        vendor_capabilities.total_scan_results_storage_;
    btm_cb.cmn_ble_vsc_cb.max_irk_list_sz =
        vendor_capabilities.max_irk_list_sz_;
    btm_cb.cmn_ble_vsc_cb.filter_support =
        vendor_capabilities.filtering_support_;
    btm_cb.cmn_ble_vsc_cb.max_filter = vendor_capabilities.max_filter_;
    btm_cb.cmn_ble_vsc_cb.energy_support =
        vendor_capabilities.activity_energy_info_support_;

    btm_cb.cmn_ble_vsc_cb.version_supported =
        vendor_capabilities.version_supported_;
    btm_cb.cmn_ble_vsc_cb.total_trackable_advertisers =
        vendor_capabilities.total_num_of_advt_tracked_;
    btm_cb.cmn_ble_vsc_cb.extended_scan_support =
        vendor_capabilities.extended_scan_support_;
    btm_cb.cmn_ble_vsc_cb.debug_logging_supported =
        vendor_capabilities.debug_logging_supported_;

    btm_cb.cmn_ble_vsc_cb.le_address_generation_offloading_support =
        vendor_capabilities.le_address_generation_offloading_support_;
    btm_cb.cmn_ble_vsc_cb.a2dp_source_offload_capability_mask =
        vendor_capabilities.a2dp_source_offload_capability_mask_;
    btm_cb.cmn_ble_vsc_cb.quality_report_support =
        vendor_capabilities.bluetooth_quality_report_support_;
    btm_cb.cmn_ble_vsc_cb.dynamic_audio_buffer_support =
        vendor_capabilities.dynamic_audio_buffer_support_;
    btm_cb.cmn_ble_vsc_cb.a2dp_offload_v2_support =
        vendor_capabilities.a2dp_offload_v2_support_;

    if (vendor_capabilities.dynamic_audio_buffer_support_) {
      std::array<bluetooth::hci::DynamicAudioBufferCodecCapability,
                 BTM_CODEC_TYPE_MAX_RECORDS>
          capabilities = GetController()->GetDabCodecCapabilities();

      for (size_t i = 0; i < capabilities.size(); i++) {
        btm_cb.dynamic_audio_buffer_cb[i].default_buffer_time =
            capabilities[i].default_time_ms_;
        btm_cb.dynamic_audio_buffer_cb[i].maximum_buffer_time =
            capabilities[i].maximum_time_ms_;
        btm_cb.dynamic_audio_buffer_cb[i].minimum_buffer_time =
            capabilities[i].minimum_time_ms_;
      }
    }

    if (btm_cb.cmn_ble_vsc_cb.filter_support == 1 &&
        GetController()->GetLocalVersionInformation().manufacturer_name_ ==
            LMP_COMPID_QTI) {
      // QTI controller, TDS data filter are supported by default.
      btm_cb.cmn_ble_vsc_cb.adv_filter_extended_features_mask = 0x01;
    } else {
      btm_cb.cmn_ble_vsc_cb.adv_filter_extended_features_mask = 0x00;
    }

    log::verbose("irk={}, ADV ins:{}, rpa={}, ener={}, ext_scan={}",
                 btm_cb.cmn_ble_vsc_cb.max_irk_list_sz,
                 btm_cb.cmn_ble_vsc_cb.adv_inst_max,
                 btm_cb.cmn_ble_vsc_cb.rpa_offloading,
                 btm_cb.cmn_ble_vsc_cb.energy_support,
                 btm_cb.cmn_ble_vsc_cb.extended_scan_support);

    if (btm_cb.cmn_ble_vsc_cb.max_filter > 0) btm_ble_adv_filter_init();

    /* VS capability included and non-4.2 device */
    if (GetController()->SupportsBle() &&
        GetController()->SupportsBlePrivacy() &&
        btm_cb.cmn_ble_vsc_cb.max_irk_list_sz > 0 &&
        GetController()->GetLeResolvingListSize() == 0) {
      btm_ble_resolving_list_init(btm_cb.cmn_ble_vsc_cb.max_irk_list_sz);
    }

    if (p_vsc_cback != NULL) {
      p_vsc_cback(tHCI_STATUS::HCI_SUCCESS);
    }
  } else {
    p_ctrl_le_feature_rd_cmpl_cback = p_vsc_cback;
    BTM_VendorSpecificCommand(HCI_BLE_VENDOR_CAP, 0, NULL,
                              btm_ble_vendor_capability_vsc_cmpl_cback);
  }
}

/*******************************************************************************
 *
 * Function         BTM_BleConfigPrivacy
 *
 * Description      This function is called to enable or disable the privacy in
 *                   LE channel of the local device.
 *
 * Parameters       privacy_mode:  privacy mode on or off.
 *
 * Returns          bool    privacy mode set success; otherwise failed.
 *
 ******************************************************************************/
bool BTM_BleConfigPrivacy(bool privacy_mode) {
  log::warn("{}", (int)privacy_mode);

  /* if LE is not supported, return error */
  if (!controller_get_interface()->SupportsBle()) return false;

  tGAP_BLE_ATTR_VALUE gap_ble_attr_value;
  gap_ble_attr_value.addr_resolution = 0;
  if (!privacy_mode) /* if privacy disabled, always use public address */
  {
    btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type = BLE_ADDR_PUBLIC;
    btm_cb.ble_ctr_cb.privacy_mode = BTM_PRIVACY_NONE;
  } else /* privacy is turned on*/
  {
    /* always set host random address, used when privacy 1.1 or priavcy 1.2 is
     * disabled */
    btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type = BLE_ADDR_RANDOM;
    btm_gen_resolvable_private_addr(base::Bind(&btm_gen_resolve_paddr_low));

    /* 4.2 controller only allow privacy 1.2 or mixed mode, resolvable private
     * address in controller */
    if (controller_get_interface()->SupportsBlePrivacy()) {
      gap_ble_attr_value.addr_resolution = 1;
      btm_cb.ble_ctr_cb.privacy_mode = BTM_PRIVACY_1_2;
    } else /* 4.1/4.0 controller */
      btm_cb.ble_ctr_cb.privacy_mode = BTM_PRIVACY_1_1;
  }
  log::verbose("privacy_mode: {} own_addr_type: {}",
               btm_cb.ble_ctr_cb.privacy_mode,
               btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type);

  GAP_BleAttrDBUpdate(GATT_UUID_GAP_CENTRAL_ADDR_RESOL, &gap_ble_attr_value);

  bluetooth::shim::ACL_ConfigureLePrivacy(privacy_mode);
  return true;
}

/*******************************************************************************
 *
 * Function          BTM_BleMaxMultiAdvInstanceCount
 *
 * Description        Returns max number of multi adv instances supported by
 *                  controller
 *
 * Returns          Max multi adv instance count
 *
 ******************************************************************************/
uint8_t BTM_BleMaxMultiAdvInstanceCount(void) {
  return btm_cb.cmn_ble_vsc_cb.adv_inst_max < BTM_BLE_MULTI_ADV_MAX
             ? btm_cb.cmn_ble_vsc_cb.adv_inst_max
             : BTM_BLE_MULTI_ADV_MAX;
}

/*******************************************************************************
 *
 * Function         BTM_BleLocalPrivacyEnabled
 *
 * Description        Checks if local device supports private address
 *
 * Returns          Return true if local privacy is enabled else false
 *
 ******************************************************************************/
bool BTM_BleLocalPrivacyEnabled(void) {
  return (btm_cb.ble_ctr_cb.privacy_mode != BTM_PRIVACY_NONE);
}

static bool is_resolving_list_bit_set(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);

  if ((p_dev_rec->ble.in_controller_list & BTM_RESOLVING_LIST_BIT) != 0)
    return false;

  return true;
}

/*******************************************************************************
 * PAST and Periodic Sync helper functions
 ******************************************************************************/

static void sync_queue_add(sync_node_t* p_param) {
  std::unique_lock<std::mutex> guard(sync_queue_mutex_);
  if (!sync_queue) {
    log::info("allocating sync queue");
    sync_queue = list_new(osi_free);
    CHECK(sync_queue != NULL);
  }

  // Validity check
  CHECK(list_length(sync_queue) < MAX_SYNC_TRANSACTION);
  sync_node_t* p_node = (sync_node_t*)osi_malloc(sizeof(sync_node_t));
  *p_node = *p_param;
  list_append(sync_queue, p_node);
}

static void sync_queue_advance() {
  log::debug("");
  std::unique_lock<std::mutex> guard(sync_queue_mutex_);

  if (sync_queue && !list_is_empty(sync_queue)) {
    sync_node_t* p_head = (sync_node_t*)list_front(sync_queue);
    log::info("queue_advance");
    list_remove(sync_queue, p_head);
  }
}

static void sync_queue_cleanup(remove_sync_node_t* p_param) {
  std::unique_lock<std::mutex> guard(sync_queue_mutex_);
  if (!sync_queue) {
    return;
  }

  sync_node_t* sync_request;
  const list_node_t* node = list_begin(sync_queue);
  while (node && node != list_end(sync_queue)) {
    sync_request = (sync_node_t*)list_node(node);
    node = list_next(node);
    if (sync_request->sid == p_param->sid &&
        sync_request->address == p_param->address) {
      log::info("removing connection request SID={:04X}, bd_addr={}, busy={}",
                sync_request->sid,
                ADDRESS_TO_LOGGABLE_CSTR(sync_request->address),
                sync_request->busy);
      list_remove(sync_queue, sync_request);
    }
  }
}

void btm_ble_start_sync_request(uint8_t sid, RawAddress addr, uint16_t skip,
                                uint16_t timeout) {
  tBLE_ADDR_TYPE address_type = BLE_ADDR_RANDOM;
  tINQ_DB_ENT* p_i = btm_inq_db_find(addr);
  if (p_i) {
    address_type = p_i->inq_info.results.ble_addr_type;  // Random
  }
  btm_random_pseudo_to_identity_addr(&addr, &address_type);
  address_type &= ~BLE_ADDR_TYPE_ID_BIT;
  uint8_t options = 0;
  uint8_t cte_type = 7;
  int index = btm_ble_get_psync_index(sid, addr);

  if (index == MAX_SYNC_TRANSACTION) {
    log::error("Failed to get sync transfer index");
    return;
  }

  tBTM_BLE_PERIODIC_SYNC* p = &btm_ble_pa_sync_cb.p_sync[index];
  p->sync_state = PERIODIC_SYNC_PENDING;

  if (BleScanningManager::IsInitialized()) {
    BleScanningManager::Get()->PeriodicScanStart(options, sid, address_type,
                                                 addr, skip, timeout, cte_type);
  }

  alarm_set(sync_timeout_alarm, SYNC_TIMEOUT, btm_ble_start_sync_timeout, NULL);
}

static void btm_queue_sync_next() {
  if (!sync_queue || list_is_empty(sync_queue)) {
    log::debug("sync_queue empty");
    return;
  }

  sync_node_t* p_head = (sync_node_t*)list_front(sync_queue);

  log::info("executing sync request SID={:04X}, bd_addr={}", p_head->sid,
            ADDRESS_TO_LOGGABLE_CSTR(p_head->address));
  if (p_head->busy) {
    log::debug("BUSY");
    return;
  }

  p_head->busy = true;
  alarm_cancel(sync_timeout_alarm);
  btm_ble_start_sync_request(p_head->sid, p_head->address, p_head->skip,
                             p_head->timeout);
}

static void btm_ble_sync_queue_handle(uint16_t event, char* param) {
  switch (event) {
    case BTM_QUEUE_SYNC_REQ_EVT:
      log::debug("BTIF_QUEUE_SYNC_REQ_EVT");
      sync_queue_add((sync_node_t*)param);
      break;
    case BTM_QUEUE_SYNC_ADVANCE_EVT:
      log::debug("BTIF_QUEUE_ADVANCE_EVT");
      sync_queue_advance();
      break;
    case BTM_QUEUE_SYNC_CLEANUP_EVT:
      sync_queue_cleanup((remove_sync_node_t*)param);
      return;
  }
  btm_queue_sync_next();
}

void btm_queue_start_sync_req(uint8_t sid, RawAddress address, uint16_t skip,
                              uint16_t timeout) {
  log::debug("address = {}, sid = {}", ADDRESS_TO_LOGGABLE_CSTR(address), sid);
  sync_node_t node = {};
  node.sid = sid;
  node.address = address;
  node.skip = skip;
  node.timeout = timeout;
  btm_ble_sync_queue_handle(BTM_QUEUE_SYNC_REQ_EVT, (char*)&node);
}

static void btm_sync_queue_advance() {
  log::debug("");
  btm_ble_sync_queue_handle(BTM_QUEUE_SYNC_ADVANCE_EVT, nullptr);
}

static void btm_ble_start_sync_timeout(void* data) {
  log::debug("");
  sync_node_t* p_head = (sync_node_t*)list_front(sync_queue);
  uint8_t adv_sid = p_head->sid;
  RawAddress address = p_head->address;

  int index = btm_ble_get_psync_index(adv_sid, address);

  if (index == MAX_SYNC_TRANSACTION) {
    log::error("Failed to get sync transfer index");
    return;
  }

  tBTM_BLE_PERIODIC_SYNC* p = &btm_ble_pa_sync_cb.p_sync[index];

  if (BleScanningManager::IsInitialized()) {
    BleScanningManager::Get()->PeriodicScanCancelStart();
  }
  p->sync_start_cb.Run(0x3C, 0, p->sid, 0, p->remote_bda, 0, 0);

  p->sync_state = PERIODIC_SYNC_IDLE;
  p->in_use = false;
  p->remote_bda = RawAddress::kEmpty;
  p->sid = 0;
  p->sync_handle = 0;
  p->in_use = false;
}

static int btm_ble_get_psync_index_from_handle(uint16_t handle) {
  int i;
  for (i = 0; i < MAX_SYNC_TRANSACTION; i++) {
    if (btm_ble_pa_sync_cb.p_sync[i].sync_handle == handle &&
        btm_ble_pa_sync_cb.p_sync[i].sync_state == PERIODIC_SYNC_ESTABLISHED) {
      log::debug("found index at {}", i);
      return i;
    }
  }
  return i;
}

static int btm_ble_get_psync_index(uint8_t adv_sid, RawAddress addr) {
  int i;
  for (i = 0; i < MAX_SYNC_TRANSACTION; i++) {
    if (btm_ble_pa_sync_cb.p_sync[i].sid == adv_sid &&
        btm_ble_pa_sync_cb.p_sync[i].remote_bda == addr) {
      log::debug("found index at {}", i);
      return i;
    }
  }
  return i;
}

static int btm_ble_get_sync_transfer_index(uint16_t conn_handle) {
  int i;
  for (i = 0; i < MAX_SYNC_TRANSACTION; i++) {
    if (btm_ble_pa_sync_cb.sync_transfer[i].conn_handle == conn_handle) {
      log::debug("found index at {}", i);
      return i;
    }
  }
  return i;
}

/*******************************************************************************
 *
 * Function         btm_ble_periodic_adv_sync_established
 *
 * Description      Periodic Adv Sync Established callback from controller when
 &                  sync to PA is established
 *
 *
 ******************************************************************************/
void btm_ble_periodic_adv_sync_established(uint8_t status, uint16_t sync_handle,
                                           uint8_t adv_sid,
                                           uint8_t address_type,
                                           const RawAddress& addr, uint8_t phy,
                                           uint16_t interval,
                                           uint8_t adv_clock_accuracy) {
  log::debug(
      "[PSync]: status={}, sync_handle={}, s_id={}, addr_type={}, "
      "adv_phy={},adv_interval={}, clock_acc={}",
      status, sync_handle, adv_sid, address_type, phy, interval,
      adv_clock_accuracy);

  /*if (param_len != ADV_SYNC_ESTB_EVT_LEN) {
    log::error("[PSync]Invalid event length");
    STREAM_TO_UINT8(status, param);
    if (status == BTM_SUCCESS) {
      STREAM_TO_UINT16(sync_handle, param);
      //btsnd_hcic_ble_terminate_periodic_sync(sync_handle);
      if (BleScanningManager::IsInitialized()) {
        BleScanningManager::Get()->PeriodicScanTerminate(sync_handle);
      }
      return;
    }
  }*/

  RawAddress bda = addr;
  alarm_cancel(sync_timeout_alarm);

  tBLE_ADDR_TYPE ble_addr_type = to_ble_addr_type(address_type);
  if (ble_addr_type & BLE_ADDR_TYPE_ID_BIT) {
    btm_identity_addr_to_random_pseudo(&bda, &ble_addr_type, true);
  }
  int index = btm_ble_get_psync_index(adv_sid, bda);
  if (index == MAX_SYNC_TRANSACTION) {
    log::warn("[PSync]: Invalid index for sync established");
    if (status == BTM_SUCCESS) {
      log::warn("Terminate sync");
      if (BleScanningManager::IsInitialized()) {
        BleScanningManager::Get()->PeriodicScanTerminate(sync_handle);
      }
    }
    btm_sync_queue_advance();
    return;
  }
  tBTM_BLE_PERIODIC_SYNC* ps = &btm_ble_pa_sync_cb.p_sync[index];
  ps->sync_handle = sync_handle;
  ps->sync_state = PERIODIC_SYNC_ESTABLISHED;
  ps->sync_start_cb.Run(status, sync_handle, adv_sid,
                        from_ble_addr_type(ble_addr_type), bda, phy, interval);
  btm_sync_queue_advance();
}

/*******************************************************************************
 *
 * Function        btm_ble_periodic_adv_report
 *
 * Description     This callback is received when controller estalishes sync
 *                 to a PA requested from host
 *
 ******************************************************************************/
void btm_ble_periodic_adv_report(uint16_t sync_handle, uint8_t tx_power,
                                 int8_t rssi, uint8_t cte_type,
                                 uint8_t data_status, uint8_t data_len,
                                 const uint8_t* periodic_data) {
  log::debug(
      "[PSync]: sync_handle = {}, tx_power = {}, rssi = {},cte_type = {}, "
      "data_status = {}, data_len = {}",
      sync_handle, tx_power, rssi, cte_type, data_status, data_len);

  std::vector<uint8_t> data;
  for (int i = 0; i < data_len; i++) {
    data.push_back(periodic_data[i]);
  }
  int index = btm_ble_get_psync_index_from_handle(sync_handle);
  if (index == MAX_SYNC_TRANSACTION) {
    log::error("[PSync]: index not found for handle {}", sync_handle);
    return;
  }
  tBTM_BLE_PERIODIC_SYNC* ps = &btm_ble_pa_sync_cb.p_sync[index];
  log::debug("[PSync]: invoking callback");
  ps->sync_report_cb.Run(sync_handle, tx_power, rssi, data_status, data);
}

/*******************************************************************************
 *
 * Function        btm_ble_periodic_adv_sync_lost
 *
 * Description     This callback is received when sync to PA is lost
 *
 ******************************************************************************/
void btm_ble_periodic_adv_sync_lost(uint16_t sync_handle) {
  log::debug("[PSync]: sync_handle = {}", sync_handle);

  int index = btm_ble_get_psync_index_from_handle(sync_handle);
  if (index == MAX_SYNC_TRANSACTION) {
    log::error("[PSync]: index not found for handle {}", sync_handle);
    return;
  }
  tBTM_BLE_PERIODIC_SYNC* ps = &btm_ble_pa_sync_cb.p_sync[index];
  ps->sync_lost_cb.Run(sync_handle);

  ps->in_use = false;
  ps->sid = 0;
  ps->sync_handle = 0;
  ps->sync_state = PERIODIC_SYNC_IDLE;
  ps->remote_bda = RawAddress::kEmpty;
}

/*******************************************************************************
 *
 * Function        btm_ble_periodic_syc_transfer_cmd_cmpl
 *
 * Description     PAST complete callback
 *
 ******************************************************************************/
void btm_ble_periodic_syc_transfer_cmd_cmpl(uint8_t status,
                                            uint16_t conn_handle) {
  log::debug("[PAST]: status = {}, conn_handle ={}", status, conn_handle);

  int index = btm_ble_get_sync_transfer_index(conn_handle);
  if (index == MAX_SYNC_TRANSACTION) {
    log::error("[PAST]:Invalid, conn_handle {} not found in DB", conn_handle);
    return;
  }

  tBTM_BLE_PERIODIC_SYNC_TRANSFER* p_sync_transfer =
      &btm_ble_pa_sync_cb.sync_transfer[index];
  p_sync_transfer->cb.Run(status, p_sync_transfer->addr);

  p_sync_transfer->in_use = false;
  p_sync_transfer->conn_handle = -1;
  p_sync_transfer->addr = RawAddress::kEmpty;
}

void btm_ble_periodic_syc_transfer_param_cmpl(uint8_t status) {
  log::debug("[PAST]: status = {}", status);
}

/*******************************************************************************
 *
 * Function        btm_ble_biginfo_adv_report_rcvd
 *
 * Description     Host receives this event when synced PA has BIGInfo
 *
 ******************************************************************************/
void btm_ble_biginfo_adv_report_rcvd(const uint8_t* p, uint16_t param_len) {
  log::debug("[PAST]: BIGINFO report received, len={}", param_len);
  uint16_t sync_handle, iso_interval, max_pdu, max_sdu;
  uint8_t num_bises, nse, bn, pto, irc, phy, framing, encryption;
  uint32_t sdu_interval;

  // 2 bytes for sync handle, 1 byte for num_bises, 1 byte for nse, 2 bytes for
  // iso_interval, 1 byte each for bn, pto, irc, 2 bytes for max_pdu, 3 bytes
  // for sdu_interval, 2 bytes for max_sdu, 1 byte each for phy, framing,
  // encryption
  if (param_len < 19) {
    log::error("Insufficient data");
    return;
  }

  STREAM_TO_UINT16(sync_handle, p);
  STREAM_TO_UINT8(num_bises, p);
  STREAM_TO_UINT8(nse, p);
  STREAM_TO_UINT16(iso_interval, p);
  STREAM_TO_UINT8(bn, p);
  STREAM_TO_UINT8(pto, p);
  STREAM_TO_UINT8(irc, p);
  STREAM_TO_UINT16(max_pdu, p);
  STREAM_TO_UINT24(sdu_interval, p);
  STREAM_TO_UINT16(max_sdu, p);
  STREAM_TO_UINT8(phy, p);
  STREAM_TO_UINT8(framing, p);
  STREAM_TO_UINT8(encryption, p);
  log::debug(
      "[PAST]:sync_handle {}, num_bises = {}, nse = {},iso_interval = {}, bn = "
      "{}, pto = {}, irc = {}, max_pdu = {} sdu_interval = {}, max_sdu = {}, "
      "phy = {}, framing = {}, encryption  = {}",
      sync_handle, num_bises, nse, iso_interval, bn, pto, irc, max_pdu,
      sdu_interval, max_sdu, phy, framing, encryption);

  int index = btm_ble_get_psync_index_from_handle(sync_handle);
  if (index == MAX_SYNC_TRANSACTION) {
    log::error("[PSync]: index not found for handle {}", sync_handle);
    return;
  }
  tBTM_BLE_PERIODIC_SYNC* ps = &btm_ble_pa_sync_cb.p_sync[index];
  log::debug("[PSync]: invoking callback");
  ps->biginfo_report_cb.Run(sync_handle, encryption ? true : false);
}

/*******************************************************************************
 *
 * Function        btm_ble_periodic_adv_sync_tx_rcvd
 *
 * Description     Host receives this event when the controller receives sync
 *                 info of PA from the connected remote device and successfully
 *                 synced to PA associated with sync handle
 *
 ******************************************************************************/
void btm_ble_periodic_adv_sync_tx_rcvd(const uint8_t* p, uint16_t param_len) {
  log::debug("[PAST]: PAST received, param_len={}", param_len);
  if (param_len < 19) {
    log::error("Insufficient data");
    return;
  }
  uint8_t status, adv_sid, address_type, adv_phy, clk_acc;
  uint16_t pa_int, sync_handle, service_data, conn_handle;
  RawAddress addr;
  STREAM_TO_UINT8(status, p);
  STREAM_TO_UINT16(conn_handle, p);
  STREAM_TO_UINT16(service_data, p);
  STREAM_TO_UINT16(sync_handle, p);
  STREAM_TO_UINT8(adv_sid, p);
  STREAM_TO_UINT8(address_type, p);
  STREAM_TO_BDADDR(addr, p);
  STREAM_TO_UINT8(adv_phy, p);
  STREAM_TO_UINT16(pa_int, p);
  STREAM_TO_UINT8(clk_acc, p);
  log::verbose(
      "[PAST]: status = {}, conn_handle = {}, service_data = {}, sync_handle = "
      "{}, adv_sid = {}, address_type = {}, addr = {}, adv_phy = {}, pa_int = "
      "{}, clk_acc = {}",
      status, conn_handle, service_data, sync_handle, adv_sid, address_type,
      ADDRESS_TO_LOGGABLE_CSTR(addr), adv_phy, pa_int, clk_acc);
  if (syncRcvdCbRegistered) {
    sync_rcvd_cb.Run(status, sync_handle, adv_sid, address_type, addr, adv_phy,
                     pa_int);
  }
}

/*******************************************************************************
 *
 * Function         btm_set_conn_mode_adv_init_addr
 *
 * Description      set initator address type and local address type based on
 *                  adv mode.
 *
 *
 ******************************************************************************/
static uint8_t btm_set_conn_mode_adv_init_addr(
    RawAddress& p_peer_addr_ptr, tBLE_ADDR_TYPE* p_peer_addr_type,
    tBLE_ADDR_TYPE* p_own_addr_type) {
  uint8_t evt_type;
  tBTM_SEC_DEV_REC* p_dev_rec;

  if (btm_cb.ble_ctr_cb.inq_var.connectable_mode == BTM_BLE_NON_CONNECTABLE) {
    if (btm_cb.ble_ctr_cb.inq_var.scan_rsp) {
      evt_type = BTM_BLE_DISCOVER_EVT;
    } else {
      evt_type = BTM_BLE_NON_CONNECT_EVT;
    }
  } else {
    evt_type = BTM_BLE_CONNECT_EVT;
  }

  if (evt_type == BTM_BLE_CONNECT_EVT) {
    CHECK(p_peer_addr_type != nullptr);
    const tBLE_BD_ADDR ble_bd_addr = {
        .type = *p_peer_addr_type,
        .bda = p_peer_addr_ptr,
    };
    log::debug("Received BLE connect event {}",
               ADDRESS_TO_LOGGABLE_CSTR(ble_bd_addr));

    evt_type = btm_cb.ble_ctr_cb.inq_var.directed_conn;

    if (static_cast<std::underlying_type_t<tBTM_BLE_EVT>>(
            btm_cb.ble_ctr_cb.inq_var.directed_conn) ==
            BTM_BLE_CONNECT_DIR_EVT ||
        static_cast<std::underlying_type_t<tBTM_BLE_EVT>>(
            btm_cb.ble_ctr_cb.inq_var.directed_conn) ==
            BTM_BLE_CONNECT_LO_DUTY_DIR_EVT) {
      /* for privacy 1.2, convert peer address as static, own address set as ID
       * addr */
      if (btm_cb.ble_ctr_cb.privacy_mode == BTM_PRIVACY_1_2 ||
          btm_cb.ble_ctr_cb.privacy_mode == BTM_PRIVACY_MIXED) {
        /* only do so for bonded device */
        if ((p_dev_rec = btm_find_or_alloc_dev(
                 btm_cb.ble_ctr_cb.inq_var.direct_bda.bda)) != NULL &&
            p_dev_rec->ble.in_controller_list & BTM_RESOLVING_LIST_BIT) {
          p_peer_addr_ptr = p_dev_rec->ble.identity_address_with_type.bda;
          *p_peer_addr_type = p_dev_rec->ble.identity_address_with_type.type;
          *p_own_addr_type = BLE_ADDR_RANDOM_ID;
          return evt_type;
        }
        /* otherwise fall though as normal directed adv */
      }
      /* direct adv mode does not have privacy, if privacy is not enabled  */
      *p_peer_addr_type = btm_cb.ble_ctr_cb.inq_var.direct_bda.type;
      p_peer_addr_ptr = btm_cb.ble_ctr_cb.inq_var.direct_bda.bda;
      return evt_type;
    }
  }

  /* undirect adv mode or non-connectable mode*/
  /* when privacy 1.2 privacy only mode is used, or mixed mode */
  if ((btm_cb.ble_ctr_cb.privacy_mode == BTM_PRIVACY_1_2 &&
       btm_cb.ble_ctr_cb.inq_var.afp != AP_SCAN_CONN_ALL) ||
      btm_cb.ble_ctr_cb.privacy_mode == BTM_PRIVACY_MIXED) {
    list_node_t* n =
        list_foreach(btm_sec_cb.sec_dev_rec, is_resolving_list_bit_set, NULL);
    if (n) {
      /* if enhanced privacy is required, set Identity address and matching IRK
       * peer */
      tBTM_SEC_DEV_REC* p_dev_rec =
          static_cast<tBTM_SEC_DEV_REC*>(list_node(n));
      p_peer_addr_ptr = p_dev_rec->ble.identity_address_with_type.bda;
      *p_peer_addr_type = p_dev_rec->ble.identity_address_with_type.type;

      *p_own_addr_type = BLE_ADDR_RANDOM_ID;
    } else {
      /* resolving list is empty, not enabled */
      *p_own_addr_type = BLE_ADDR_RANDOM;
    }
  }
  /* privacy 1.1, or privacy 1.2, general discoverable/connectable mode, disable
     privacy in */
  /* controller fall back to host based privacy */
  else if (btm_cb.ble_ctr_cb.privacy_mode != BTM_PRIVACY_NONE) {
    *p_own_addr_type = BLE_ADDR_RANDOM;
  }

  /* if no privacy,do not set any peer address,*/
  /* local address type go by global privacy setting */
  return evt_type;
}

/*******************************************************************************
 *
 * Function         BTM__BLEReadDiscoverability
 *
 * Description      This function is called to read the current LE
 *                  discoverability mode of the device.
 *
 * Returns          BTM_BLE_NON_DISCOVERABLE ,BTM_BLE_LIMITED_DISCOVERABLE or
 *                     BTM_BLE_GENRAL_DISCOVERABLE
 *
 ******************************************************************************/
uint16_t BTM_BleReadDiscoverability() {
  log::verbose("");

  return (btm_cb.ble_ctr_cb.inq_var.discoverable_mode);
}

/*******************************************************************************
 *
 * Function         BTM__BLEReadConnectability
 *
 * Description      This function is called to read the current LE
 *                  connectability mode of the device.
 *
 * Returns          BTM_BLE_NON_CONNECTABLE or BTM_BLE_CONNECTABLE
 *
 ******************************************************************************/
uint16_t BTM_BleReadConnectability() {
  log::verbose("");

  return (btm_cb.ble_ctr_cb.inq_var.connectable_mode);
}

/*******************************************************************************
 *
 * Function         btm_ble_select_adv_interval
 *
 * Description      select adv interval based on device mode
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_select_adv_interval(uint8_t evt_type,
                                        uint16_t* p_adv_int_min,
                                        uint16_t* p_adv_int_max) {
  switch (evt_type) {
    case BTM_BLE_CONNECT_EVT:
    case BTM_BLE_CONNECT_LO_DUTY_DIR_EVT:
      *p_adv_int_min = *p_adv_int_max = BTM_BLE_GAP_ADV_FAST_INT_1;
      break;

    case BTM_BLE_NON_CONNECT_EVT:
    case BTM_BLE_DISCOVER_EVT:
      *p_adv_int_min = *p_adv_int_max = BTM_BLE_GAP_ADV_FAST_INT_2;
      break;

      /* connectable directed event */
    case BTM_BLE_CONNECT_DIR_EVT:
      *p_adv_int_min = BTM_BLE_GAP_ADV_DIR_MIN_INT;
      *p_adv_int_max = BTM_BLE_GAP_ADV_DIR_MAX_INT;
      break;

    default:
      *p_adv_int_min = *p_adv_int_max = BTM_BLE_GAP_ADV_SLOW_INT;
      break;
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_update_dmt_flag_bits
 *
 * Description      Obtain updated adv flag value based on connect and
 *                  discoverability mode. Also, setup DMT support value in the
 *                  flag based on whether the controller supports both LE and
 *                  BR/EDR.
 *
 * Parameters:      flag_value (Input / Output) - flag value
 *                  connect_mode (Input) - Connect mode value
 *                  disc_mode (Input) - discoverability mode
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_update_dmt_flag_bits(uint8_t* adv_flag_value,
                                  const uint16_t connect_mode,
                                  const uint16_t disc_mode) {
  /* BR/EDR non-discoverable , non-connectable */
  if ((disc_mode & BTM_DISCOVERABLE_MASK) == 0 &&
      (connect_mode & BTM_CONNECTABLE_MASK) == 0)
    *adv_flag_value |= BTM_BLE_BREDR_NOT_SPT;
  else
    *adv_flag_value &= ~BTM_BLE_BREDR_NOT_SPT;

  /* if local controller support, mark both controller and host support in flag
   */
  if (bluetooth::shim::GetController()->SupportsSimultaneousLeBrEdr())
    *adv_flag_value |= (BTM_BLE_DMT_CONTROLLER_SPT | BTM_BLE_DMT_HOST_SPT);
  else
    *adv_flag_value &= ~(BTM_BLE_DMT_CONTROLLER_SPT | BTM_BLE_DMT_HOST_SPT);
}

/*******************************************************************************
 *
 * Function         btm_ble_set_adv_flag
 *
 * Description      Set adv flag in adv data.
 *
 * Parameters:      connect_mode (Input)- Connect mode value
 *                  disc_mode (Input) - discoverability mode
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_set_adv_flag(uint16_t connect_mode, uint16_t disc_mode) {
  uint8_t flag = 0, old_flag = 0;
  tBTM_BLE_LOCAL_ADV_DATA* p_adv_data = &btm_cb.ble_ctr_cb.inq_var.adv_data;

  if (p_adv_data->p_flags != NULL) flag = old_flag = *(p_adv_data->p_flags);

  btm_ble_update_dmt_flag_bits(&flag, connect_mode, disc_mode);

  log::info("disc_mode {:04x}", disc_mode);
  /* update discoverable flag */
  if (disc_mode & BTM_BLE_LIMITED_DISCOVERABLE) {
    flag &= ~BTM_BLE_GEN_DISC_FLAG;
    flag |= BTM_BLE_LIMIT_DISC_FLAG;
  } else if (disc_mode & BTM_BLE_GENERAL_DISCOVERABLE) {
    flag |= BTM_BLE_GEN_DISC_FLAG;
    flag &= ~BTM_BLE_LIMIT_DISC_FLAG;
  } else /* remove all discoverable flags */
  {
    flag &= ~(BTM_BLE_LIMIT_DISC_FLAG | BTM_BLE_GEN_DISC_FLAG);
  }

  if (flag != old_flag) {
    btm_ble_update_adv_flag(flag);
  }
}
/*******************************************************************************
 *
 * Function         btm_ble_set_discoverability
 *
 * Description      This function is called to set BLE discoverable mode.
 *
 * Parameters:      combined_mode: discoverability mode.
 *
 * Returns          BTM_SUCCESS is status set successfully; otherwise failure.
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_set_discoverability(uint16_t combined_mode) {
  tBTM_LE_RANDOM_CB* p_addr_cb = &btm_cb.ble_ctr_cb.addr_mgnt_cb;
  uint16_t mode = (combined_mode & BTM_BLE_DISCOVERABLE_MASK);
  uint8_t new_mode = BTM_BLE_ADV_ENABLE;
  uint8_t evt_type;
  tBTM_STATUS status = BTM_SUCCESS;
  RawAddress address = RawAddress::kEmpty;
  tBLE_ADDR_TYPE init_addr_type = BLE_ADDR_PUBLIC,
                 own_addr_type = p_addr_cb->own_addr_type;
  uint16_t adv_int_min, adv_int_max;

  log::verbose("mode=0x{:0x} combined_mode=0x{:x}", mode, combined_mode);

  /*** Check mode parameter ***/
  if (mode > BTM_BLE_MAX_DISCOVERABLE) return (BTM_ILLEGAL_VALUE);

  btm_cb.ble_ctr_cb.inq_var.discoverable_mode = mode;

  evt_type =
      btm_set_conn_mode_adv_init_addr(address, &init_addr_type, &own_addr_type);

  if (btm_cb.ble_ctr_cb.inq_var.connectable_mode == BTM_BLE_NON_CONNECTABLE &&
      mode == BTM_BLE_NON_DISCOVERABLE)
    new_mode = BTM_BLE_ADV_DISABLE;

  btm_ble_select_adv_interval(evt_type, &adv_int_min, &adv_int_max);

  alarm_cancel(btm_cb.ble_ctr_cb.inq_var.fast_adv_timer);

  /* update adv params if start advertising */
  log::verbose("evt_type=0x{:x} p-cb->evt_type=0x{:x} ", evt_type,
               btm_cb.ble_ctr_cb.inq_var.evt_type);

  if (new_mode == BTM_BLE_ADV_ENABLE) {
    btm_ble_set_adv_flag(btm_cb.btm_inq_vars.connectable_mode, combined_mode);

    if (evt_type != btm_cb.ble_ctr_cb.inq_var.evt_type ||
        btm_cb.ble_ctr_cb.inq_var.adv_addr_type != own_addr_type ||
        !btm_cb.ble_ctr_cb.inq_var.fast_adv_on) {
      btm_ble_stop_adv();

      /* update adv params */
      btsnd_hcic_ble_write_adv_params(adv_int_min, adv_int_max, evt_type,
                                      own_addr_type, init_addr_type, address,
                                      btm_cb.ble_ctr_cb.inq_var.adv_chnl_map,
                                      btm_cb.ble_ctr_cb.inq_var.afp);
      btm_cb.ble_ctr_cb.inq_var.evt_type = evt_type;
      btm_cb.ble_ctr_cb.inq_var.adv_addr_type = own_addr_type;
    }
  }

  if (status == BTM_SUCCESS && btm_cb.ble_ctr_cb.inq_var.adv_mode != new_mode) {
    if (new_mode == BTM_BLE_ADV_ENABLE)
      status = btm_ble_start_adv();
    else
      status = btm_ble_stop_adv();
  }

  if (btm_cb.ble_ctr_cb.inq_var.adv_mode == BTM_BLE_ADV_ENABLE) {
    btm_cb.ble_ctr_cb.inq_var.fast_adv_on = true;
    /* start initial GAP mode adv timer */
    alarm_set_on_mloop(btm_cb.ble_ctr_cb.inq_var.fast_adv_timer,
                       BTM_BLE_GAP_FAST_ADV_TIMEOUT_MS,
                       btm_ble_fast_adv_timer_timeout, NULL);
  }

  /* set up stop advertising timer */
  if (status == BTM_SUCCESS && mode == BTM_BLE_LIMITED_DISCOVERABLE) {
    log::verbose("start timer for limited disc mode duration={} ms",
                 BTM_BLE_GAP_LIM_TIMEOUT_MS);
    /* start Tgap(lim_timeout) */
    alarm_set_on_mloop(
        btm_cb.ble_ctr_cb.inq_var.inquiry_timer, BTM_BLE_GAP_LIM_TIMEOUT_MS,
        btm_ble_inquiry_timer_gap_limited_discovery_timeout, NULL);
  }
  return status;
}

/*******************************************************************************
 *
 * Function         btm_ble_set_connectability
 *
 * Description      This function is called to set BLE connectability mode.
 *
 * Parameters:      combined_mode: connectability mode.
 *
 * Returns          BTM_SUCCESS is status set successfully; otherwise failure.
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_set_connectability(uint16_t combined_mode) {
  tBTM_LE_RANDOM_CB* p_addr_cb = &btm_cb.ble_ctr_cb.addr_mgnt_cb;
  uint16_t mode = (combined_mode & BTM_BLE_CONNECTABLE_MASK);
  uint8_t new_mode = BTM_BLE_ADV_ENABLE;
  uint8_t evt_type;
  tBTM_STATUS status = BTM_SUCCESS;
  RawAddress address = RawAddress::kEmpty;
  tBLE_ADDR_TYPE peer_addr_type = BLE_ADDR_PUBLIC,
                 own_addr_type = p_addr_cb->own_addr_type;
  uint16_t adv_int_min, adv_int_max;

  log::verbose("mode=0x{:0x} combined_mode=0x{:x}", mode, combined_mode);

  /*** Check mode parameter ***/
  if (mode > BTM_BLE_MAX_CONNECTABLE) return (BTM_ILLEGAL_VALUE);

  btm_cb.ble_ctr_cb.inq_var.connectable_mode = mode;

  evt_type =
      btm_set_conn_mode_adv_init_addr(address, &peer_addr_type, &own_addr_type);

  if (mode == BTM_BLE_NON_CONNECTABLE &&
      btm_cb.ble_ctr_cb.inq_var.discoverable_mode == BTM_BLE_NON_DISCOVERABLE)
    new_mode = BTM_BLE_ADV_DISABLE;

  btm_ble_select_adv_interval(evt_type, &adv_int_min, &adv_int_max);

  alarm_cancel(btm_cb.ble_ctr_cb.inq_var.fast_adv_timer);
  /* update adv params if needed */
  if (new_mode == BTM_BLE_ADV_ENABLE) {
    btm_ble_set_adv_flag(combined_mode, btm_cb.btm_inq_vars.discoverable_mode);
    if (btm_cb.ble_ctr_cb.inq_var.evt_type != evt_type ||
        btm_cb.ble_ctr_cb.inq_var.adv_addr_type != p_addr_cb->own_addr_type ||
        !btm_cb.ble_ctr_cb.inq_var.fast_adv_on) {
      btm_ble_stop_adv();

      btsnd_hcic_ble_write_adv_params(adv_int_min, adv_int_max, evt_type,
                                      own_addr_type, peer_addr_type, address,
                                      btm_cb.ble_ctr_cb.inq_var.adv_chnl_map,
                                      btm_cb.ble_ctr_cb.inq_var.afp);
      btm_cb.ble_ctr_cb.inq_var.evt_type = evt_type;
      btm_cb.ble_ctr_cb.inq_var.adv_addr_type = own_addr_type;
    }
  }

  /* update advertising mode */
  if (status == BTM_SUCCESS && new_mode != btm_cb.ble_ctr_cb.inq_var.adv_mode) {
    if (new_mode == BTM_BLE_ADV_ENABLE)
      status = btm_ble_start_adv();
    else
      status = btm_ble_stop_adv();
  }

  if (btm_cb.ble_ctr_cb.inq_var.adv_mode == BTM_BLE_ADV_ENABLE) {
    btm_cb.ble_ctr_cb.inq_var.fast_adv_on = true;
    /* start initial GAP mode adv timer */
    alarm_set_on_mloop(btm_cb.ble_ctr_cb.inq_var.fast_adv_timer,
                       BTM_BLE_GAP_FAST_ADV_TIMEOUT_MS,
                       btm_ble_fast_adv_timer_timeout, NULL);
  }
  return status;
}

static void btm_send_hci_scan_enable(uint8_t enable,
                                     uint8_t filter_duplicates) {
  if (controller_get_interface()->SupportsBleExtendedAdvertising()) {
    btsnd_hcic_ble_set_extended_scan_enable(enable, filter_duplicates, 0x0000,
                                            0x0000);
  } else {
    btsnd_hcic_ble_set_scan_enable(enable, filter_duplicates);
  }
}

void btm_send_hci_set_scan_params(uint8_t scan_type, uint16_t scan_int,
                                  uint16_t scan_win,
                                  tBLE_ADDR_TYPE addr_type_own,
                                  uint8_t scan_filter_policy) {
  if (controller_get_interface()->SupportsBleExtendedAdvertising()) {
    scanning_phy_cfg phy_cfg;
    phy_cfg.scan_type = scan_type;
    phy_cfg.scan_int = scan_int;
    phy_cfg.scan_win = scan_win;

    btsnd_hcic_ble_set_extended_scan_params(addr_type_own, scan_filter_policy,
                                            1, &phy_cfg);
  } else {
    btsnd_hcic_ble_set_scan_params(scan_type, scan_int, scan_win, addr_type_own,
                                   scan_filter_policy);
  }
}

/* Scan filter param config event */
static void btm_ble_scan_filt_param_cfg_evt(uint8_t avbl_space,
                                            tBTM_BLE_SCAN_COND_OP action_type,
                                            tBTM_STATUS btm_status) {
  if (btm_status != btm_status_value(BTM_SUCCESS)) {
    log::error("{}", btm_status);
  } else {
    log::verbose("");
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_start_inquiry
 *
 * Description      This function is called to start BLE inquiry procedure.
 *                  If the duration is zero, the periodic inquiry mode is
 *                  cancelled.
 *
 * Parameters:      duration - Duration of inquiry in seconds
 *
 * Returns          BTM_CMD_STARTED if successfully started
 *                  BTM_BUSY - if an inquiry is already active
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_start_inquiry(uint8_t duration) {
  log::verbose("btm_ble_start_inquiry: inq_active = 0x{:02x}",
               btm_cb.btm_inq_vars.inq_active);

  /* if selective connection is active, or inquiry is already active, reject it
   */
  if (btm_cb.ble_ctr_cb.is_ble_inquiry_active()) {
    log::error("LE Inquiry is active, can not start inquiry");
    return (BTM_BUSY);
  }

  /* Cleanup anything remaining on index 0 */
  BTM_BleAdvFilterParamSetup(BTM_BLE_SCAN_COND_DELETE,
                             static_cast<tBTM_BLE_PF_FILT_INDEX>(0), nullptr,
                             base::Bind(btm_ble_scan_filt_param_cfg_evt));

  auto adv_filt_param = std::make_unique<btgatt_filt_param_setup_t>();
  /* Add an allow-all filter on index 0*/
  adv_filt_param->dely_mode = IMMEDIATE_DELY_MODE;
  adv_filt_param->feat_seln = ALLOW_ALL_FILTER;
  adv_filt_param->filt_logic_type = BTA_DM_BLE_PF_FILT_LOGIC_OR;
  adv_filt_param->list_logic_type = BTA_DM_BLE_PF_LIST_LOGIC_OR;
  adv_filt_param->rssi_low_thres = LOWEST_RSSI_VALUE;
  adv_filt_param->rssi_high_thres = LOWEST_RSSI_VALUE;
  BTM_BleAdvFilterParamSetup(BTM_BLE_SCAN_COND_ADD, static_cast<tBTM_BLE_PF_FILT_INDEX>(0),
                 std::move(adv_filt_param), base::Bind(btm_ble_scan_filt_param_cfg_evt));

  uint16_t scan_interval, scan_window;
  std::tie(scan_interval, scan_window) = get_low_latency_scan_params();

  if (!btm_cb.ble_ctr_cb.is_ble_scan_active()) {
    cache.ClearAll();
    btm_send_hci_set_scan_params(
        BTM_BLE_SCAN_MODE_ACTI, scan_interval, scan_window,
        btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type, SP_ADV_ALL);
    btm_cb.ble_ctr_cb.inq_var.scan_type = BTM_BLE_SCAN_MODE_ACTI;
    btm_ble_start_scan();
  } else if ((btm_cb.ble_ctr_cb.inq_var.scan_interval != scan_interval) ||
             (btm_cb.ble_ctr_cb.inq_var.scan_window != scan_window)) {
    log::verbose("restart LE scan with low latency scan params");
    if (IS_FLAG_ENABLED(le_scan_parameters_fix)) {
      btm_cb.ble_ctr_cb.inq_var.scan_interval = scan_interval;
      btm_cb.ble_ctr_cb.inq_var.scan_window = scan_window;
    }
    btm_send_hci_scan_enable(BTM_BLE_SCAN_DISABLE, BTM_BLE_DUPLICATE_ENABLE);
    btm_send_hci_set_scan_params(
        BTM_BLE_SCAN_MODE_ACTI, scan_interval, scan_window,
        btm_cb.ble_ctr_cb.addr_mgnt_cb.own_addr_type, SP_ADV_ALL);
    btm_send_hci_scan_enable(BTM_BLE_SCAN_ENABLE, BTM_BLE_DUPLICATE_DISABLE);
  }

  btm_cb.btm_inq_vars.inq_active |= BTM_BLE_GENERAL_INQUIRY;
  btm_cb.ble_ctr_cb.set_ble_inquiry_active();

  log::verbose("btm_ble_start_inquiry inq_active = 0x{:02x}",
               btm_cb.btm_inq_vars.inq_active);

  if (duration != 0) {
    /* start inquiry timer */
    uint64_t duration_ms = duration * 1000;
    alarm_set_on_mloop(btm_cb.ble_ctr_cb.inq_var.inquiry_timer, duration_ms,
                       btm_ble_inquiry_timer_timeout, NULL);
  }

  btm_cb.neighbor.le_inquiry = {
      .start_time_ms = timestamper_in_milliseconds.GetTimestamp(),
      .results = 0,
  };
  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le inquiry started");

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_ble_read_remote_name_cmpl
 *
 * Description      This function is called when BLE remote name is received.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_read_remote_name_cmpl(bool status, const RawAddress& bda,
                                   uint16_t length, char* p_name) {
  tHCI_STATUS hci_status = HCI_SUCCESS;
  BD_NAME bd_name;

  memset(bd_name, 0, (BD_NAME_LEN + 1));
  if (length > BD_NAME_LEN) {
    length = BD_NAME_LEN;
  }
  memcpy((uint8_t*)bd_name, p_name, length);

  if ((!status) || (length == 0)) {
    hci_status = HCI_ERR_HOST_TIMEOUT;
  }

  btm_process_remote_name(&bda, bd_name, length + 1, hci_status);
  btm_sec_rmt_name_request_complete(&bda, (const uint8_t*)p_name, hci_status);
}

/*******************************************************************************
 *
 * Function         btm_ble_read_remote_name
 *
 * Description      This function read remote LE device name using GATT read
 *                  procedure.
 *
 * Parameters:       None.
 *
 * Returns          void
 *
 ******************************************************************************/
tBTM_STATUS btm_ble_read_remote_name(const RawAddress& remote_bda,
                                     tBTM_NAME_CMPL_CB* p_cb) {
  if (!controller_get_interface()->SupportsBle()) return BTM_ERR_PROCESSING;

  tINQ_DB_ENT* p_i = btm_inq_db_find(remote_bda);
  if (p_i && !ble_evt_type_is_connectable(p_i->inq_info.results.ble_evt_type)) {
    log::verbose("name request to non-connectable device failed.");
    return BTM_ERR_PROCESSING;
  }

  /* read remote device name using GATT procedure */
  if (btm_cb.btm_inq_vars.remname_active) return BTM_BUSY;

  if (!GAP_BleReadPeerDevName(remote_bda, btm_ble_read_remote_name_cmpl))
    return BTM_BUSY;

  btm_cb.btm_inq_vars.p_remname_cmpl_cb = p_cb;
  btm_cb.btm_inq_vars.remname_active = true;
  btm_cb.btm_inq_vars.remname_bda = remote_bda;

  alarm_set_on_mloop(btm_cb.btm_inq_vars.remote_name_timer,
                     BTM_EXT_BLE_RMT_NAME_TIMEOUT_MS,
                     btm_inq_remote_name_timer_timeout, NULL);

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_ble_cancel_remote_name
 *
 * Description      This function cancel read remote LE device name.
 *
 * Parameters:       None.
 *
 * Returns          void
 *
 ******************************************************************************/
bool btm_ble_cancel_remote_name(const RawAddress& remote_bda) {
  bool status;

  status = GAP_BleCancelReadPeerDevName(remote_bda);

  btm_cb.btm_inq_vars.remname_active = false;
  btm_cb.btm_inq_vars.remname_bda = RawAddress::kEmpty;
  alarm_cancel(btm_cb.btm_inq_vars.remote_name_timer);

  return status;
}

/*******************************************************************************
 *
 * Function         btm_ble_update_adv_flag
 *
 * Description      This function update the limited discoverable flag in the
 *                  adv data.
 *
 * Parameters:       None.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_update_adv_flag(uint8_t flag) {
  tBTM_BLE_LOCAL_ADV_DATA* p_adv_data = &btm_cb.ble_ctr_cb.inq_var.adv_data;
  uint8_t* p;

  log::verbose("btm_ble_update_adv_flag new=0x{:x}", flag);

  if (p_adv_data->p_flags != NULL) {
    log::verbose("btm_ble_update_adv_flag old=0x{:x}", *p_adv_data->p_flags);
    *p_adv_data->p_flags = flag;
  } else /* no FLAGS in ADV data*/
  {
    p = (p_adv_data->p_pad == NULL) ? p_adv_data->ad_data : p_adv_data->p_pad;
    /* need 3 bytes space to stuff in the flags, if not */
    /* erase all written data, just for flags */
    if ((BTM_BLE_AD_DATA_LEN - (p - p_adv_data->ad_data)) < 3) {
      p = p_adv_data->p_pad = p_adv_data->ad_data;
      memset(p_adv_data->ad_data, 0, BTM_BLE_AD_DATA_LEN);
    }

    *p++ = 2;
    *p++ = BTM_BLE_AD_TYPE_FLAG;
    p_adv_data->p_flags = p;
    *p++ = flag;
    p_adv_data->p_pad = p;
  }

  btsnd_hcic_ble_set_adv_data(
      (uint8_t)(p_adv_data->p_pad - p_adv_data->ad_data), p_adv_data->ad_data);
  p_adv_data->data_mask |= BTM_BLE_AD_BIT_FLAGS;
}

/**
 * Check ADV flag to make sure device is discoverable and match the search
 * condition
 */
static uint8_t btm_ble_is_discoverable(const RawAddress& bda,
                                       std::vector<uint8_t> const& adv_data) {
  uint8_t scan_state = BTM_BLE_NOT_SCANNING;

  /* for observer, always "discoverable */
  if (btm_cb.ble_ctr_cb.is_ble_observe_active())
    scan_state |= BTM_BLE_OBS_RESULT;

  if (!adv_data.empty()) {
    uint8_t flag = 0;
    uint8_t data_len;
    const uint8_t* p_flag = AdvertiseDataParser::GetFieldByType(
        adv_data, BTM_BLE_AD_TYPE_FLAG, &data_len);
    if (p_flag != NULL && data_len != 0) {
      flag = *p_flag;

      if ((btm_cb.btm_inq_vars.inq_active & BTM_BLE_GENERAL_INQUIRY) &&
          (flag & (BTM_BLE_LIMIT_DISC_FLAG | BTM_BLE_GEN_DISC_FLAG)) != 0) {
        scan_state |= BTM_BLE_INQ_RESULT;
      }
    }
  }
  return scan_state;
}

static DEV_CLASS btm_ble_appearance_to_cod(uint16_t appearance) {
  DEV_CLASS dev_class = kDevClassEmpty;

  switch (appearance) {
    case BTM_BLE_APPEARANCE_GENERIC_PHONE:
      dev_class[1] = BTM_COD_MAJOR_PHONE;
      dev_class[2] = BTM_COD_MINOR_UNCLASSIFIED;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_COMPUTER:
      dev_class[1] = BTM_COD_MAJOR_COMPUTER;
      dev_class[2] = BTM_COD_MINOR_UNCLASSIFIED;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_REMOTE:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_REMOTE_CONTROL;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_THERMOMETER:
    case BTM_BLE_APPEARANCE_THERMOMETER_EAR:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_THERMOMETER;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_HEART_RATE:
    case BTM_BLE_APPEARANCE_HEART_RATE_BELT:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_HEART_PULSE_MONITOR;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_BLOOD_PRESSURE:
    case BTM_BLE_APPEARANCE_BLOOD_PRESSURE_ARM:
    case BTM_BLE_APPEARANCE_BLOOD_PRESSURE_WRIST:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_BLOOD_MONITOR;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_PULSE_OXIMETER:
    case BTM_BLE_APPEARANCE_PULSE_OXIMETER_FINGERTIP:
    case BTM_BLE_APPEARANCE_PULSE_OXIMETER_WRIST:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_PULSE_OXIMETER;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_GLUCOSE:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_GLUCOSE_METER;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_WEIGHT:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_WEIGHING_SCALE;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_WALKING:
    case BTM_BLE_APPEARANCE_WALKING_IN_SHOE:
    case BTM_BLE_APPEARANCE_WALKING_ON_SHOE:
    case BTM_BLE_APPEARANCE_WALKING_ON_HIP:
      dev_class[1] = BTM_COD_MAJOR_HEALTH;
      dev_class[2] = BTM_COD_MINOR_STEP_COUNTER;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_WATCH:
    case BTM_BLE_APPEARANCE_SPORTS_WATCH:
      dev_class[1] = BTM_COD_MAJOR_WEARABLE;
      dev_class[2] = BTM_COD_MINOR_WRIST_WATCH;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_EYEGLASSES:
      dev_class[1] = BTM_COD_MAJOR_WEARABLE;
      dev_class[2] = BTM_COD_MINOR_GLASSES;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_DISPLAY:
      dev_class[1] = BTM_COD_MAJOR_IMAGING;
      dev_class[2] = BTM_COD_MINOR_DISPLAY;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_MEDIA_PLAYER:
      dev_class[1] = BTM_COD_MAJOR_AUDIO;
      dev_class[2] = BTM_COD_MINOR_UNCLASSIFIED;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_WEARABLE_AUDIO_DEVICE:
    case BTM_BLE_APPEARANCE_WEARABLE_AUDIO_DEVICE_EARBUD:
    case BTM_BLE_APPEARANCE_WEARABLE_AUDIO_DEVICE_HEADSET:
    case BTM_BLE_APPEARANCE_WEARABLE_AUDIO_DEVICE_HEADPHONES:
    case BTM_BLE_APPEARANCE_WEARABLE_AUDIO_DEVICE_NECK_BAND:
      dev_class[0] = (BTM_COD_SERVICE_AUDIO | BTM_COD_SERVICE_RENDERING) >> 8;
      dev_class[1] = (BTM_COD_MAJOR_AUDIO | BTM_COD_SERVICE_LE_AUDIO);
      dev_class[2] = BTM_COD_MINOR_WEARABLE_HEADSET;
      break;
    case BTM_BLE_APPEARANCE_GENERIC_BARCODE_SCANNER:
    case BTM_BLE_APPEARANCE_HID_BARCODE_SCANNER:
    case BTM_BLE_APPEARANCE_GENERIC_HID:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_UNCLASSIFIED;
      break;
    case BTM_BLE_APPEARANCE_HID_KEYBOARD:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_KEYBOARD;
      break;
    case BTM_BLE_APPEARANCE_HID_MOUSE:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_POINTING;
      break;
    case BTM_BLE_APPEARANCE_HID_JOYSTICK:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_JOYSTICK;
      break;
    case BTM_BLE_APPEARANCE_HID_GAMEPAD:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_GAMEPAD;
      break;
    case BTM_BLE_APPEARANCE_HID_DIGITIZER_TABLET:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_DIGITIZING_TABLET;
      break;
    case BTM_BLE_APPEARANCE_HID_CARD_READER:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_CARD_READER;
      break;
    case BTM_BLE_APPEARANCE_HID_DIGITAL_PEN:
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = BTM_COD_MINOR_DIGITAL_PAN;
      break;
    case BTM_BLE_APPEARANCE_UKNOWN:
    case BTM_BLE_APPEARANCE_GENERIC_CLOCK:
    case BTM_BLE_APPEARANCE_GENERIC_TAG:
    case BTM_BLE_APPEARANCE_GENERIC_KEYRING:
    case BTM_BLE_APPEARANCE_GENERIC_CYCLING:
    case BTM_BLE_APPEARANCE_CYCLING_COMPUTER:
    case BTM_BLE_APPEARANCE_CYCLING_SPEED:
    case BTM_BLE_APPEARANCE_CYCLING_CADENCE:
    case BTM_BLE_APPEARANCE_CYCLING_POWER:
    case BTM_BLE_APPEARANCE_CYCLING_SPEED_CADENCE:
    case BTM_BLE_APPEARANCE_GENERIC_OUTDOOR_SPORTS:
    case BTM_BLE_APPEARANCE_OUTDOOR_SPORTS_LOCATION:
    case BTM_BLE_APPEARANCE_OUTDOOR_SPORTS_LOCATION_AND_NAV:
    case BTM_BLE_APPEARANCE_OUTDOOR_SPORTS_LOCATION_POD:
    case BTM_BLE_APPEARANCE_OUTDOOR_SPORTS_LOCATION_POD_AND_NAV:
    default:
      dev_class[1] = BTM_COD_MAJOR_UNCLASSIFIED;
      dev_class[2] = BTM_COD_MINOR_UNCLASSIFIED;
  };
  return dev_class;
}

DEV_CLASS btm_ble_get_appearance_as_cod(std::vector<uint8_t> const& data) {
  /* Check to see the BLE device has the Appearance UUID in the advertising
   * data. If it does then try to convert the appearance value to a class of
   * device value Fluoride can use. Otherwise fall back to trying to infer if
   * it is a HID device based on the service class.
   */
  uint8_t len;
  const uint8_t* p_uuid16 = AdvertiseDataParser::GetFieldByType(
      data, BTM_BLE_AD_TYPE_APPEARANCE, &len);
  if (p_uuid16 && len == 2) {
    return btm_ble_appearance_to_cod((uint16_t)p_uuid16[0] |
                                     (p_uuid16[1] << 8));
  }

  p_uuid16 = AdvertiseDataParser::GetFieldByType(
      data, BTM_BLE_AD_TYPE_16SRV_CMPL, &len);
  if (p_uuid16 == NULL) {
    return kDevClassUnclassified;
  }

  for (uint8_t i = 0; i + 2 <= len; i = i + 2) {
    /* if this BLE device supports HID over LE, set HID Major in class of
     * device */
    if ((p_uuid16[i] | (p_uuid16[i + 1] << 8)) == UUID_SERVCLASS_LE_HID) {
      DEV_CLASS dev_class;
      dev_class[0] = 0;
      dev_class[1] = BTM_COD_MAJOR_PERIPHERAL;
      dev_class[2] = 0;
      return dev_class;
    }
  }

  return kDevClassUnclassified;
}

/**
 * Update adv packet information into inquiry result.
 */
static void btm_ble_update_inq_result(tINQ_DB_ENT* p_i, uint8_t addr_type,
                                      const RawAddress& bda, uint16_t evt_type,
                                      uint8_t primary_phy,
                                      uint8_t secondary_phy,
                                      uint8_t advertising_sid, int8_t tx_power,
                                      int8_t rssi, uint16_t periodic_adv_int,
                                      std::vector<uint8_t> const& data) {
  tBTM_INQ_RESULTS* p_cur = &p_i->inq_info.results;
  uint8_t len;

  /* Save the info */
  p_cur->inq_result_type |= BT_DEVICE_TYPE_BLE;
  p_cur->ble_addr_type = static_cast<tBLE_ADDR_TYPE>(addr_type);
  p_cur->rssi = rssi;
  p_cur->ble_primary_phy = primary_phy;
  p_cur->ble_secondary_phy = secondary_phy;
  p_cur->ble_advertising_sid = advertising_sid;
  p_cur->ble_tx_power = tx_power;
  p_cur->ble_periodic_adv_int = periodic_adv_int;

  if (btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_ACTI &&
      ble_evt_type_is_scannable(evt_type) &&
      !ble_evt_type_is_scan_resp(evt_type)) {
    p_i->scan_rsp = false;
  } else
    p_i->scan_rsp = true;

  if (p_i->inq_count != btm_cb.btm_inq_vars.inq_counter)
    p_cur->device_type = BT_DEVICE_TYPE_BLE;
  else
    p_cur->device_type |= BT_DEVICE_TYPE_BLE;

  if (evt_type != BTM_BLE_SCAN_RSP_EVT) p_cur->ble_evt_type = evt_type;

  p_i->inq_count =
      btm_cb.btm_inq_vars.inq_counter; /* Mark entry for current inquiry */

  bool has_advertising_flags = false;
  if (!data.empty()) {
    const uint8_t* p_flag =
        AdvertiseDataParser::GetFieldByType(data, BTM_BLE_AD_TYPE_FLAG, &len);
    if (p_flag != NULL && len != 0) {
      has_advertising_flags = true;
      p_cur->flag = *p_flag;
    }

    p_cur->dev_class = btm_ble_get_appearance_as_cod(data);

    const uint8_t* p_rsi =
        AdvertiseDataParser::GetFieldByType(data, BTM_BLE_AD_TYPE_RSI, &len);
    if (p_rsi != nullptr && len == 6) {
      STREAM_TO_BDADDR(p_cur->ble_ad_rsi, p_rsi);
    }

    const uint8_t* p_service_data = data.data();
    uint8_t service_data_len = 0;

    while ((p_service_data = AdvertiseDataParser::GetFieldByType(
                p_service_data + service_data_len,
                data.size() - (p_service_data - data.data()) - service_data_len,
                BTM_BLE_AD_TYPE_SERVICE_DATA_TYPE, &service_data_len))) {
      uint16_t uuid;
      const uint8_t* p_uuid = p_service_data;
      if (service_data_len < 2) {
        continue;
      }
      STREAM_TO_UINT16(uuid, p_uuid);

      if (uuid == 0x184E /* Audio Stream Control service */ ||
          uuid == 0x184F /* Broadcast Audio Scan service */ ||
          uuid == 0x1850 /* Published Audio Capabilities service */ ||
          uuid == 0x1853 /* Common Audio service */) {
        p_cur->ble_ad_is_le_audio_capable = true;
        break;
      }
    }
  }

  // Non-connectable packets may omit flags entirely, in which case nothing
  // should be assumed about their values (CSSv10, 1.3.1). Thus, do not
  // interpret the device type unless this packet has the flags set or is
  // connectable.
  bool should_process_flags =
      has_advertising_flags || ble_evt_type_is_connectable(evt_type);
  if (should_process_flags && (p_cur->flag & BTM_BLE_BREDR_NOT_SPT) == 0 &&
      !ble_evt_type_is_directed(evt_type)) {
    if (p_cur->ble_addr_type != BLE_ADDR_RANDOM) {
      log::verbose("NOT_BR_EDR support bit not set, treat device as DUMO");
      p_cur->device_type |= BT_DEVICE_TYPE_DUMO;
    } else {
      log::verbose("Random address, treat device as LE only");
    }
  } else {
    log::verbose("NOT_BR/EDR support bit set, treat device as LE only");
  }
}

void btm_ble_process_adv_addr(RawAddress& bda, tBLE_ADDR_TYPE* addr_type) {
  /* map address to security record */
  bool match = btm_identity_addr_to_random_pseudo(&bda, addr_type, false);

  log::verbose("bda={}", ADDRESS_TO_LOGGABLE_STR(bda));
  /* always do RRA resolution on host */
  if (!match && BTM_BLE_IS_RESOLVE_BDA(bda)) {
    tBTM_SEC_DEV_REC* match_rec = btm_ble_resolve_random_addr(bda);
    if (match_rec) {
      match_rec->ble.active_addr_type = BTM_BLE_ADDR_RRA;
      match_rec->ble.cur_rand_addr = bda;

      if (btm_ble_init_pseudo_addr(match_rec, bda)) {
        bda = match_rec->bd_addr;
      } else {
        // Assign the original address to be the current report address
        bda = match_rec->ble.pseudo_addr;
        *addr_type = match_rec->ble.AddressType();
      }
    }
  }
}

/**
 * This function is called after random address resolution is done, and proceed
 * to process adv packet.
 */
void btm_ble_process_adv_pkt_cont(uint16_t evt_type, tBLE_ADDR_TYPE addr_type,
                                  const RawAddress& bda, uint8_t primary_phy,
                                  uint8_t secondary_phy,
                                  uint8_t advertising_sid, int8_t tx_power,
                                  int8_t rssi, uint16_t periodic_adv_int,
                                  uint8_t data_len, const uint8_t* data,
                                  const RawAddress& original_bda) {
  bool update = true;

  std::vector<uint8_t> tmp;
  if (data_len != 0) tmp.insert(tmp.begin(), data, data + data_len);

  bool is_scannable = ble_evt_type_is_scannable(evt_type);
  bool is_scan_resp = ble_evt_type_is_scan_resp(evt_type);
  bool is_legacy = ble_evt_type_is_legacy(evt_type);

  // We might receive a legacy scan response without receving a ADV_IND
  // or ADV_SCAN_IND before. Only parsing the scan response data which
  // has no ad flag, the device will be set to DUMO mode. The createbond
  // procedure will use the wrong device mode.
  // In such case no necessary to report scan response
  if (is_legacy && is_scan_resp && !cache.Exist(addr_type, bda)) return;

  bool is_start = is_legacy && is_scannable && !is_scan_resp;

  if (is_legacy) AdvertiseDataParser::RemoveTrailingZeros(tmp);

  // We might have send scan request to this device before, but didn't get the
  // response. In such case make sure data is put at start, not appended to
  // already existing data.
  std::vector<uint8_t> const& adv_data =
      is_start ? cache.Set(addr_type, bda, std::move(tmp))
               : cache.Append(addr_type, bda, std::move(tmp));

  bool data_complete = (ble_evt_type_data_status(evt_type) != 0x01);

  if (!data_complete) {
    // If we didn't receive whole adv data yet, don't report the device.
    log::verbose("Data not complete yet, waiting for more {}",
                 ADDRESS_TO_LOGGABLE_STR(bda));
    return;
  }

  bool is_active_scan =
      btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_ACTI;
  if (is_active_scan && is_scannable && !is_scan_resp) {
    // If we didn't receive scan response yet, don't report the device.
    log::verbose(" Waiting for scan response {}", ADDRESS_TO_LOGGABLE_STR(bda));
    return;
  }

  if (!AdvertiseDataParser::IsValid(adv_data)) {
    log::verbose("Dropping bad advertisement packet: {}",
                 base::HexEncode(adv_data.data(), adv_data.size()));
    cache.Clear(addr_type, bda);
    return;
  }

  bool include_rsi = false;
  uint8_t len;
  if (AdvertiseDataParser::GetFieldByType(adv_data, BTM_BLE_AD_TYPE_RSI,
                                          &len)) {
    include_rsi = true;
  }

  tINQ_DB_ENT* p_i = btm_inq_db_find(bda);

  /* Check if this address has already been processed for this inquiry */
  if (btm_inq_find_bdaddr(bda)) {
    /* never been report as an LE device */
    if (p_i && (!(p_i->inq_info.results.device_type & BT_DEVICE_TYPE_BLE) ||
                /* scan response to be updated */
                (!p_i->scan_rsp) ||
                (!p_i->inq_info.results.include_rsi && include_rsi))) {
      update = true;
    } else if (btm_cb.ble_ctr_cb.is_ble_observe_active()) {
      update = false;
    } else {
      /* if yes, skip it */
      cache.Clear(addr_type, bda);
      return; /* assumption: one result per event */
    }
  }
  /* If existing entry, use that, else get  a new one (possibly reusing the
   * oldest) */
  if (p_i == NULL) {
    p_i = btm_inq_db_new(bda, true);
    if (p_i != NULL) {
      btm_cb.btm_inq_vars.inq_cmpl_info.num_resp++;
      p_i->time_of_resp = bluetooth::common::time_get_os_boottime_ms();
    } else
      return;
  } else if (p_i->inq_count !=
             btm_cb.btm_inq_vars
                 .inq_counter) /* first time seen in this inquiry */
  {
    p_i->time_of_resp = bluetooth::common::time_get_os_boottime_ms();
    btm_cb.btm_inq_vars.inq_cmpl_info.num_resp++;
  }

  /* update the LE device information in inquiry database */
  btm_ble_update_inq_result(p_i, addr_type, bda, evt_type, primary_phy,
                            secondary_phy, advertising_sid, tx_power, rssi,
                            periodic_adv_int, adv_data);

  if (include_rsi) {
    (&p_i->inq_info.results)->include_rsi = true;
  }

  tBTM_INQ_RESULTS_CB* p_opportunistic_obs_results_cb =
      btm_cb.ble_ctr_cb.p_opportunistic_obs_results_cb;
  if (p_opportunistic_obs_results_cb) {
    (p_opportunistic_obs_results_cb)((tBTM_INQ_RESULTS*)&p_i->inq_info.results,
                                     const_cast<uint8_t*>(adv_data.data()),
                                     adv_data.size());
  }

  tBTM_INQ_RESULTS_CB* p_target_announcement_obs_results_cb =
      btm_cb.ble_ctr_cb.p_target_announcement_obs_results_cb;
  if (p_target_announcement_obs_results_cb) {
    (p_target_announcement_obs_results_cb)(
        (tBTM_INQ_RESULTS*)&p_i->inq_info.results,
        const_cast<uint8_t*>(adv_data.data()), adv_data.size());
  }

  uint8_t result = btm_ble_is_discoverable(bda, adv_data);
  if (result == 0) {
    // Device no longer discoverable so discard outstanding advertising packet
    cache.Clear(addr_type, bda);
    return;
  }

  if (!update) result &= ~BTM_BLE_INQ_RESULT;

  tBTM_INQ_RESULTS_CB* p_inq_results_cb = btm_cb.btm_inq_vars.p_inq_results_cb;
  if (p_inq_results_cb && (result & BTM_BLE_INQ_RESULT)) {
    (p_inq_results_cb)((tBTM_INQ_RESULTS*)&p_i->inq_info.results,
                       const_cast<uint8_t*>(adv_data.data()), adv_data.size());
  }

  // Pass address up to GattService#onScanResult
  p_i->inq_info.results.original_bda = original_bda;

  tBTM_INQ_RESULTS_CB* p_obs_results_cb = btm_cb.ble_ctr_cb.p_obs_results_cb;
  if (p_obs_results_cb && (result & BTM_BLE_OBS_RESULT)) {
    (p_obs_results_cb)((tBTM_INQ_RESULTS*)&p_i->inq_info.results,
                       const_cast<uint8_t*>(adv_data.data()), adv_data.size());
  }

  cache.Clear(addr_type, bda);
}

/**
 * This function copy from btm_ble_process_adv_pkt_cont to process adv packet
 * from gd scanning module to handle inquiry result callback.
 */
void btm_ble_process_adv_pkt_cont_for_inquiry(
    uint16_t evt_type, tBLE_ADDR_TYPE addr_type, const RawAddress& bda,
    uint8_t primary_phy, uint8_t secondary_phy, uint8_t advertising_sid,
    int8_t tx_power, int8_t rssi, uint16_t periodic_adv_int,
    std::vector<uint8_t> advertising_data) {
  bool update = true;

  bool include_rsi = false;
  uint8_t len;
  if (AdvertiseDataParser::GetFieldByType(advertising_data, BTM_BLE_AD_TYPE_RSI,
                                          &len)) {
    include_rsi = true;
  }

  tINQ_DB_ENT* p_i = btm_inq_db_find(bda);

  /* Check if this address has already been processed for this inquiry */
  if (btm_inq_find_bdaddr(bda)) {
    /* never been report as an LE device */
    if (p_i && (!(p_i->inq_info.results.device_type & BT_DEVICE_TYPE_BLE) ||
                /* scan response to be updated */
                (!p_i->scan_rsp) ||
                (!p_i->inq_info.results.include_rsi && include_rsi))) {
      update = true;
    } else if (btm_cb.ble_ctr_cb.is_ble_observe_active()) {
      btm_cb.neighbor.le_observe.results++;
      update = false;
    } else {
      /* if yes, skip it */
      return; /* assumption: one result per event */
    }
  }

  /* If existing entry, use that, else get  a new one (possibly reusing the
   * oldest) */
  if (p_i == NULL) {
    p_i = btm_inq_db_new(bda, true);
    if (p_i != NULL) {
      btm_cb.btm_inq_vars.inq_cmpl_info.num_resp++;
      p_i->time_of_resp = bluetooth::common::time_get_os_boottime_ms();
      btm_cb.neighbor.le_inquiry.results++;
      btm_cb.neighbor.le_legacy_scan.results++;
    } else {
      log::warn("Unable to allocate entry for inquiry result");
      return;
    }
  } else if (p_i->inq_count !=
             btm_cb.btm_inq_vars
                 .inq_counter) /* first time seen in this inquiry */
  {
    p_i->time_of_resp = bluetooth::common::time_get_os_boottime_ms();
    btm_cb.btm_inq_vars.inq_cmpl_info.num_resp++;
  }

  /* update the LE device information in inquiry database */
  btm_ble_update_inq_result(p_i, addr_type, bda, evt_type, primary_phy,
                            secondary_phy, advertising_sid, tx_power, rssi,
                            periodic_adv_int, advertising_data);

  if (include_rsi) {
    (&p_i->inq_info.results)->include_rsi = true;
  }

  tBTM_INQ_RESULTS_CB* p_opportunistic_obs_results_cb =
      btm_cb.ble_ctr_cb.p_opportunistic_obs_results_cb;
  if (p_opportunistic_obs_results_cb) {
    (p_opportunistic_obs_results_cb)(
        (tBTM_INQ_RESULTS*)&p_i->inq_info.results,
        const_cast<uint8_t*>(advertising_data.data()), advertising_data.size());
  }

  tBTM_INQ_RESULTS_CB* p_target_announcement_obs_results_cb =
      btm_cb.ble_ctr_cb.p_target_announcement_obs_results_cb;
  if (p_target_announcement_obs_results_cb) {
    (p_target_announcement_obs_results_cb)(
        (tBTM_INQ_RESULTS*)&p_i->inq_info.results,
        const_cast<uint8_t*>(advertising_data.data()), advertising_data.size());
  }

  uint8_t result = btm_ble_is_discoverable(bda, advertising_data);
  if (result == 0) {
    return;
  }

  if (!update) result &= ~BTM_BLE_INQ_RESULT;

  tBTM_INQ_RESULTS_CB* p_inq_results_cb = btm_cb.btm_inq_vars.p_inq_results_cb;
  if (p_inq_results_cb && (result & BTM_BLE_INQ_RESULT)) {
    (p_inq_results_cb)((tBTM_INQ_RESULTS*)&p_i->inq_info.results,
                       const_cast<uint8_t*>(advertising_data.data()),
                       advertising_data.size());
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_start_scan
 *
 * Description      Start the BLE scan.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_start_scan() {
  btm_cb.neighbor.le_legacy_scan = {
      .start_time_ms = timestamper_in_milliseconds.GetTimestamp(),
      .results = 0,
  };
  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le legacy scan started",
                 "Duplicates:disable");

  /* start scan, disable duplicate filtering */
  btm_send_hci_scan_enable(BTM_BLE_SCAN_ENABLE, BTM_BLE_DUPLICATE_DISABLE);

  if (btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_ACTI)
    btm_ble_set_topology_mask(BTM_BLE_STATE_ACTIVE_SCAN_BIT);
  else
    btm_ble_set_topology_mask(BTM_BLE_STATE_PASSIVE_SCAN_BIT);
}

/*******************************************************************************
 *
 * Function         btm_ble_stop_scan
 *
 * Description      Stop the BLE scan.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_stop_scan(void) {
  if (btm_cb.ble_ctr_cb.inq_var.scan_type == BTM_BLE_SCAN_MODE_ACTI)
    btm_ble_clear_topology_mask(BTM_BLE_STATE_ACTIVE_SCAN_BIT);
  else
    btm_ble_clear_topology_mask(BTM_BLE_STATE_PASSIVE_SCAN_BIT);

  /* Clear the inquiry callback if set */
  btm_cb.ble_ctr_cb.inq_var.scan_type = BTM_BLE_SCAN_MODE_NONE;

  /* stop discovery now */
  const unsigned long long duration_timestamp =
      timestamper_in_milliseconds.GetTimestamp() -
      btm_cb.neighbor.le_legacy_scan.start_time_ms;
  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le legacy scan stopped",
                 base::StringPrintf("duration_s:%6.3f results:%-3lu",
                                    (double)duration_timestamp / 1000.0,
                                    btm_cb.neighbor.le_legacy_scan.results));
  btm_send_hci_scan_enable(BTM_BLE_SCAN_DISABLE, BTM_BLE_DUPLICATE_ENABLE);

  btm_update_scanner_filter_policy(SP_ADV_ALL);
}
/*******************************************************************************
 *
 * Function         btm_ble_stop_inquiry
 *
 * Description      Stop the BLE Inquiry.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_stop_inquiry(void) {
  alarm_cancel(btm_cb.ble_ctr_cb.inq_var.inquiry_timer);

  const unsigned long long duration_timestamp =
      timestamper_in_milliseconds.GetTimestamp() -
      btm_cb.neighbor.le_inquiry.start_time_ms;
  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Le inquiry stopped",
                 base::StringPrintf("duration_s:%6.3f results:%-3lu",
                                    (double)duration_timestamp / 1000.0,
                                    btm_cb.neighbor.le_inquiry.results));
  btm_cb.ble_ctr_cb.reset_ble_inquiry();

  /* Cleanup anything remaining on index 0 */
  BTM_BleAdvFilterParamSetup(BTM_BLE_SCAN_COND_DELETE,
                             static_cast<tBTM_BLE_PF_FILT_INDEX>(0), nullptr,
                             base::Bind(btm_ble_scan_filt_param_cfg_evt));

  /* If no more scan activity, stop LE scan now */
  if (!btm_cb.ble_ctr_cb.is_ble_scan_active()) {
    btm_ble_stop_scan();
  } else if (get_low_latency_scan_params() !=
             std::pair(btm_cb.ble_ctr_cb.inq_var.scan_interval,
                       btm_cb.ble_ctr_cb.inq_var.scan_window)) {
    log::verbose("setting default params for ongoing observe");
    btm_ble_stop_scan();
    btm_ble_start_scan();
  }

  /* If we have a callback registered for inquiry complete, call it */
  log::verbose("BTM Inq Compl Callback: status 0x{:02x}, num results {}",
               btm_cb.btm_inq_vars.inq_cmpl_info.status,
               btm_cb.btm_inq_vars.inq_cmpl_info.num_resp);

  // TODO: remove this call and make btm_process_inq_complete static
  btm_process_inq_complete(
      HCI_SUCCESS,
      (uint8_t)(btm_cb.btm_inq_vars.inqparms.mode & BTM_BLE_INQUIRY_MASK));
}

/*******************************************************************************
 *
 * Function         btm_ble_stop_observe
 *
 * Description      Stop the BLE Observe.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_stop_observe(void) {
  tBTM_CMPL_CB* p_obs_cb = btm_cb.ble_ctr_cb.p_obs_cmpl_cb;

  alarm_cancel(btm_cb.ble_ctr_cb.observer_timer);

  btm_cb.ble_ctr_cb.reset_ble_observe();

  btm_cb.ble_ctr_cb.p_obs_results_cb = NULL;
  btm_cb.ble_ctr_cb.p_obs_cmpl_cb = NULL;

  if (!btm_cb.ble_ctr_cb.is_ble_scan_active()) {
    btm_ble_stop_scan();
  }

  if (p_obs_cb) (p_obs_cb)(&btm_cb.btm_inq_vars.inq_cmpl_info);
}
/*******************************************************************************
 *
 * Function         btm_ble_adv_states_operation
 *
 * Description      Set or clear adv states in topology mask
 *
 * Returns          operation status. true if sucessful, false otherwise.
 *
 ******************************************************************************/
typedef bool(BTM_TOPOLOGY_FUNC_PTR)(tBTM_BLE_STATE_MASK);
static bool btm_ble_adv_states_operation(BTM_TOPOLOGY_FUNC_PTR* p_handler,
                                         uint8_t adv_evt) {
  bool rt = false;

  switch (adv_evt) {
    case BTM_BLE_CONNECT_EVT:
      rt = (*p_handler)(BTM_BLE_STATE_CONN_ADV_BIT);
      break;

    case BTM_BLE_NON_CONNECT_EVT:
      rt = (*p_handler)(BTM_BLE_STATE_NON_CONN_ADV_BIT);
      break;
    case BTM_BLE_CONNECT_DIR_EVT:
      rt = (*p_handler)(BTM_BLE_STATE_HI_DUTY_DIR_ADV_BIT);
      break;

    case BTM_BLE_DISCOVER_EVT:
      rt = (*p_handler)(BTM_BLE_STATE_SCAN_ADV_BIT);
      break;

    case BTM_BLE_CONNECT_LO_DUTY_DIR_EVT:
      rt = (*p_handler)(BTM_BLE_STATE_LO_DUTY_DIR_ADV_BIT);
      break;

    default:
      log::error("unknown adv event : {}", adv_evt);
      break;
  }

  return rt;
}

/*******************************************************************************
 *
 * Function         btm_ble_start_adv
 *
 * Description      start the BLE advertising.
 *
 * Returns          void
 *
 ******************************************************************************/
static tBTM_STATUS btm_ble_start_adv(void) {
  if (!btm_ble_adv_states_operation(btm_ble_topology_check,
                                    btm_cb.ble_ctr_cb.inq_var.evt_type))
    return BTM_WRONG_MODE;

  btsnd_hcic_ble_set_adv_enable(BTM_BLE_ADV_ENABLE);
  btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_ENABLE;
  btm_ble_adv_states_operation(btm_ble_set_topology_mask,
                               btm_cb.ble_ctr_cb.inq_var.evt_type);
  power_telemetry::GetInstance().LogBleAdvStarted();

  return BTM_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btm_ble_stop_adv
 *
 * Description      Stop the BLE advertising.
 *
 * Returns          void
 *
 ******************************************************************************/
static tBTM_STATUS btm_ble_stop_adv(void) {
  if (btm_cb.ble_ctr_cb.inq_var.adv_mode == BTM_BLE_ADV_ENABLE) {
    btsnd_hcic_ble_set_adv_enable(BTM_BLE_ADV_DISABLE);

    btm_cb.ble_ctr_cb.inq_var.fast_adv_on = false;
    btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_DISABLE;
    /* clear all adv states */
    btm_ble_clear_topology_mask(BTM_BLE_STATE_ALL_ADV_MASK);
    power_telemetry::GetInstance().LogBleAdvStopped();
  }
  return BTM_SUCCESS;
}

static void btm_ble_fast_adv_timer_timeout(UNUSED_ATTR void* data) {
  /* fast adv is completed, fall back to slow adv interval */
  btm_ble_start_slow_adv();
}

/*******************************************************************************
 *
 * Function         btm_ble_start_slow_adv
 *
 * Description      Restart adv with slow adv interval
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_start_slow_adv(void) {
  if (btm_cb.ble_ctr_cb.inq_var.adv_mode == BTM_BLE_ADV_ENABLE) {
    tBTM_LE_RANDOM_CB* p_addr_cb = &btm_cb.ble_ctr_cb.addr_mgnt_cb;
    RawAddress address = RawAddress::kEmpty;
    tBLE_ADDR_TYPE init_addr_type = BLE_ADDR_PUBLIC;
    tBLE_ADDR_TYPE own_addr_type = p_addr_cb->own_addr_type;

    btm_ble_stop_adv();

    btm_cb.ble_ctr_cb.inq_var.evt_type = btm_set_conn_mode_adv_init_addr(
        address, &init_addr_type, &own_addr_type);

    /* slow adv mode never goes into directed adv */
    btsnd_hcic_ble_write_adv_params(
        BTM_BLE_GAP_ADV_SLOW_INT, BTM_BLE_GAP_ADV_SLOW_INT,
        btm_cb.ble_ctr_cb.inq_var.evt_type, own_addr_type, init_addr_type,
        address, btm_cb.ble_ctr_cb.inq_var.adv_chnl_map,
        btm_cb.ble_ctr_cb.inq_var.afp);

    btm_ble_start_adv();
  }
}

static void btm_ble_inquiry_timer_gap_limited_discovery_timeout(
    UNUSED_ATTR void* data) {
  /* lim_timeout expired, limited discovery should exit now */
  btm_cb.btm_inq_vars.discoverable_mode &= ~BTM_BLE_LIMITED_DISCOVERABLE;
  btm_ble_set_adv_flag(btm_cb.btm_inq_vars.connectable_mode,
                       btm_cb.btm_inq_vars.discoverable_mode);
}

static void btm_ble_inquiry_timer_timeout(UNUSED_ATTR void* data) {
  btm_ble_stop_inquiry();
}

static void btm_ble_observer_timer_timeout(UNUSED_ATTR void* data) {
  btm_ble_stop_observe();
}

/*******************************************************************************
 *
 * Function         btm_ble_read_remote_features_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the read LE remote feature
 *                  supported complete event.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_read_remote_features_complete(uint8_t* p, uint8_t length) {
  uint16_t handle;
  uint8_t status;

  if (length < 3) {
    goto err_out;
  }

  STREAM_TO_UINT8(status, p);
  STREAM_TO_UINT16(handle, p);
  handle = handle & 0x0FFF;  // only 12 bits meaningful

  if (status != HCI_SUCCESS) {
    if (status != HCI_ERR_UNSUPPORTED_REM_FEATURE) {
      log::error("Failed to read remote features status:{}",
                 hci_error_code_text(static_cast<tHCI_STATUS>(status)).c_str());
      return;
    }
    log::warn("Remote does not support reading remote feature");
  }

  if (status == HCI_SUCCESS) {
    // BD_FEATURES_LEN additional bytes are read
    // in acl_set_peer_le_features_from_handle
    if (length < 3 + BD_FEATURES_LEN) {
      goto err_out;
    }

    if (!acl_set_peer_le_features_from_handle(handle, p)) {
      log::error(
          "Unable to find existing connection after read remote features");
      return;
    }
  }

  btsnd_hcic_rmt_ver_req(handle);

  return;

err_out:
  log::error("Bogus event packet, too short");
}

/*******************************************************************************
 *
 * Function         btm_ble_write_adv_enable_complete
 *
 * Description      This function process the write adv enable command complete.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_write_adv_enable_complete(uint8_t* p, uint16_t evt_len) {
  /* if write adv enable/disbale not succeed */
  if (evt_len < 1 || *p != HCI_SUCCESS) {
    /* toggle back the adv mode */
    btm_cb.ble_ctr_cb.inq_var.adv_mode = !btm_cb.ble_ctr_cb.inq_var.adv_mode;
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_dir_adv_tout
 *
 * Description      when directed adv time out
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_dir_adv_tout(void) {
  btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_DISABLE;

  /* make device fall back into undirected adv mode by default */
  btm_cb.ble_ctr_cb.inq_var.directed_conn = BTM_BLE_ADV_IND_EVT;
}

/*******************************************************************************
 *
 * Function         btm_ble_set_topology_mask
 *
 * Description      set BLE topology mask
 *
 * Returns          true is request is allowed, false otherwise.
 *
 ******************************************************************************/
bool btm_ble_set_topology_mask(tBTM_BLE_STATE_MASK request_state_mask) {
  request_state_mask &= BTM_BLE_STATE_ALL_MASK;
  btm_cb.ble_ctr_cb.cur_states |= (request_state_mask & BTM_BLE_STATE_ALL_MASK);
  return true;
}

/*******************************************************************************
 *
 * Function         btm_ble_clear_topology_mask
 *
 * Description      Clear BLE topology bit mask
 *
 * Returns          true is request is allowed, false otherwise.
 *
 ******************************************************************************/
bool btm_ble_clear_topology_mask(tBTM_BLE_STATE_MASK request_state_mask) {
  request_state_mask &= BTM_BLE_STATE_ALL_MASK;
  btm_cb.ble_ctr_cb.cur_states &= ~request_state_mask;
  return true;
}

/*******************************************************************************
 *
 * Function         btm_ble_update_link_topology_mask
 *
 * Description      This function update the link topology mask
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_update_link_topology_mask(uint8_t link_role,
                                              bool increase) {
  btm_ble_clear_topology_mask(BTM_BLE_STATE_ALL_CONN_MASK);

  if (increase)
    btm_cb.ble_ctr_cb.link_count[link_role]++;
  else if (btm_cb.ble_ctr_cb.link_count[link_role] > 0)
    btm_cb.ble_ctr_cb.link_count[link_role]--;

  if (btm_cb.ble_ctr_cb.link_count[HCI_ROLE_CENTRAL])
    btm_ble_set_topology_mask(BTM_BLE_STATE_CENTRAL_BIT);

  if (btm_cb.ble_ctr_cb.link_count[HCI_ROLE_PERIPHERAL])
    btm_ble_set_topology_mask(BTM_BLE_STATE_PERIPHERAL_BIT);

  if (link_role == HCI_ROLE_PERIPHERAL && increase) {
    btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_DISABLE;
    /* make device fall back into undirected adv mode by default */
    btm_cb.ble_ctr_cb.inq_var.directed_conn = BTM_BLE_ADV_IND_EVT;
    /* clear all adv states */
    btm_ble_clear_topology_mask(BTM_BLE_STATE_ALL_ADV_MASK);
  }
}

void btm_ble_increment_link_topology_mask(uint8_t link_role) {
  btm_ble_update_link_topology_mask(link_role, true);
}

void btm_ble_decrement_link_topology_mask(uint8_t link_role) {
  btm_ble_update_link_topology_mask(link_role, false);
}

/*******************************************************************************
 *
 * Function         btm_ble_update_mode_operation
 *
 * Description      This function update the GAP role operation when a link
 *                  status is updated.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_update_mode_operation(uint8_t link_role, const RawAddress* bd_addr,
                                   tHCI_STATUS status) {
  if (status == HCI_ERR_ADVERTISING_TIMEOUT) {
    btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_DISABLE;
    /* make device fall back into undirected adv mode by default */
    btm_cb.ble_ctr_cb.inq_var.directed_conn = BTM_BLE_ADV_IND_EVT;
    /* clear all adv states */
    btm_ble_clear_topology_mask(BTM_BLE_STATE_ALL_ADV_MASK);
  }

  if (btm_cb.ble_ctr_cb.inq_var.connectable_mode == BTM_BLE_CONNECTABLE) {
    btm_ble_set_connectability(btm_cb.btm_inq_vars.connectable_mode |
                               btm_cb.ble_ctr_cb.inq_var.connectable_mode);
  }
}

/*******************************************************************************
 *
 * Function         btm_ble_init
 *
 * Description      Initialize the control block variable values.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_ble_init(void) {
  log::verbose("");

  alarm_free(btm_cb.ble_ctr_cb.observer_timer);
  alarm_free(btm_cb.ble_ctr_cb.inq_var.fast_adv_timer);
  memset(&btm_cb.ble_ctr_cb, 0, sizeof(tBTM_BLE_CB));
  memset(&(btm_cb.cmn_ble_vsc_cb), 0, sizeof(tBTM_BLE_VSC_CB));
  btm_cb.cmn_ble_vsc_cb.values_read = false;

  btm_cb.ble_ctr_cb.observer_timer = alarm_new("btm_ble.observer_timer");
  btm_cb.ble_ctr_cb.cur_states = 0;

  btm_cb.ble_ctr_cb.inq_var.adv_mode = BTM_BLE_ADV_DISABLE;
  btm_cb.ble_ctr_cb.inq_var.scan_type = BTM_BLE_SCAN_MODE_NONE;
  btm_cb.ble_ctr_cb.inq_var.adv_chnl_map = BTM_BLE_DEFAULT_ADV_CHNL_MAP;
  btm_cb.ble_ctr_cb.inq_var.afp = BTM_BLE_DEFAULT_AFP;
  btm_cb.ble_ctr_cb.inq_var.sfp = BTM_BLE_DEFAULT_SFP;
  btm_cb.ble_ctr_cb.inq_var.connectable_mode = BTM_BLE_NON_CONNECTABLE;
  btm_cb.ble_ctr_cb.inq_var.discoverable_mode = BTM_BLE_NON_DISCOVERABLE;
  btm_cb.ble_ctr_cb.inq_var.fast_adv_timer =
      alarm_new("btm_ble_inq.fast_adv_timer");
  btm_cb.ble_ctr_cb.inq_var.inquiry_timer =
      alarm_new("btm_ble_inq.inquiry_timer");

  btm_cb.ble_ctr_cb.inq_var.evt_type = BTM_BLE_NON_CONNECT_EVT;

  btm_cb.ble_ctr_cb.addr_mgnt_cb.refresh_raddr_timer =
      alarm_new("btm_ble_addr.refresh_raddr_timer");
  btm_ble_pa_sync_cb = {};
  sync_timeout_alarm = alarm_new("btm.sync_start_task");
  if (!ble_vnd_is_included()) {
    btm_ble_adv_filter_init();
  }
}

// Clean up btm ble control block
void btm_ble_free() {
  alarm_free(btm_cb.ble_ctr_cb.addr_mgnt_cb.refresh_raddr_timer);
}

/*******************************************************************************
 *
 * Function         btm_ble_topology_check
 *
 * Description      check to see requested state is supported. One state check
 *                  at a time is supported
 *
 * Returns          true is request is allowed, false otherwise.
 *
 ******************************************************************************/
bool btm_ble_topology_check(tBTM_BLE_STATE_MASK request_state_mask) {
  bool rt = false;

  uint8_t state_offset = 0;
  uint16_t cur_states = btm_cb.ble_ctr_cb.cur_states;
  uint8_t request_state = 0;

  /* check only one bit is set and within valid range */
  if (request_state_mask == BTM_BLE_STATE_INVALID ||
      request_state_mask > BTM_BLE_STATE_SCAN_ADV_BIT ||
      (request_state_mask & (request_state_mask - 1)) != 0) {
    log::error("illegal state requested: {}", request_state_mask);
    return rt;
  }

  while (request_state_mask) {
    request_state_mask >>= 1;
    request_state++;
  }

  /* check if the requested state is supported or not */
  uint8_t bit_num = btm_le_state_combo_tbl[0][request_state - 1];
  const uint8_t* ble_supported_states =
      controller_get_interface()->get_ble_supported_states();

  if (!BTM_LE_STATES_SUPPORTED(ble_supported_states, bit_num)) {
    log::error("state requested not supported: {}", request_state);
    return rt;
  }

  rt = true;
  /* make sure currently active states are all supported in conjunction with the
     requested state. If the bit in table is UNSUPPORTED, the combination is not
     supported */
  while (cur_states != 0) {
    if (cur_states & 0x01) {
      uint8_t bit_num = btm_le_state_combo_tbl[request_state][state_offset];
      if (bit_num != UNSUPPORTED) {
        if (!BTM_LE_STATES_SUPPORTED(ble_supported_states, bit_num)) {
          rt = false;
          break;
        }
      }
    }
    cur_states >>= 1;
    state_offset++;
  }
  return rt;
}
