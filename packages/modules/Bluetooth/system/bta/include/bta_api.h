/******************************************************************************
 *
 *  Copyright 2003-2014 Broadcom Corporation
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
 *  This is the public interface file for BTA, Broadcom's Bluetooth
 *  application layer for mobile phones.
 *
 ******************************************************************************/
#ifndef BTA_API_H
#define BTA_API_H

#include <base/functional/callback.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <vector>

#include "bta_api_data_types.h"
#include "internal_include/bt_target.h"
#include "macros.h"
#include "os/log.h"
#include "stack/btm/power_mode.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_name.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/btm_ble_api_types.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/sdp_device_id.h"
#include "types/ble_address_with_type.h"
#include "types/bluetooth/uuid.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

/*
 * Service ID
 */

#define BTA_A2DP_SOURCE_SERVICE_ID 3 /* A2DP Source profile. */
#define BTA_HSP_SERVICE_ID 5         /* Headset profile. */
#define BTA_HFP_SERVICE_ID 6         /* Hands-free profile. */
#define BTA_BIP_SERVICE_ID 13        /* Basic Imaging profile */
#define BTA_A2DP_SINK_SERVICE_ID 18  /* A2DP Sink */
#define BTA_HID_SERVICE_ID 20        /* HID */
#define BTA_PBAP_SERVICE_ID 22       /* PhoneBook Access Server*/
#define BTA_HFP_HS_SERVICE_ID 24     /* HSP HS role */
#define BTA_MAP_SERVICE_ID 25        /* Message Access Profile */
#define BTA_MN_SERVICE_ID 26         /* Message Notification Service */
#define BTA_PCE_SERVICE_ID 28        /* PhoneBook Access Client */
#define BTA_SDP_SERVICE_ID 29        /* SDP Search */
#define BTA_HIDD_SERVICE_ID 30       /* HID Device */

/* BLE profile service ID */
#define BTA_BLE_SERVICE_ID 31  /* GATT profile */
#define BTA_USER_SERVICE_ID 32 /* User requested UUID */
#define BTA_MAX_SERVICE_ID 33

/* service IDs (BTM_SEC_SERVICE_FIRST_EMPTY + 1) to (BTM_SEC_MAX_SERVICES - 1)
 * are used by BTA JV */
#define BTA_FIRST_JV_SERVICE_ID (BTM_SEC_SERVICE_FIRST_EMPTY + 1)
#define BTA_LAST_JV_SERVICE_ID (BTM_SEC_MAX_SERVICES - 1)

typedef uint8_t tBTA_SERVICE_ID;

/* Service ID Mask */
#define BTA_RES_SERVICE_MASK 0x00000001    /* Reserved */
#define BTA_HSP_SERVICE_MASK 0x00000020    /* HSP AG role. */
#define BTA_HFP_SERVICE_MASK 0x00000040    /* HFP AG role */
#define BTA_HL_SERVICE_MASK 0x08000000     /* Health Device Profile */

#define BTA_BLE_SERVICE_MASK 0x40000000  /* GATT based service */
#define BTA_ALL_SERVICE_MASK 0x7FFFFFFF  /* All services supported by BTA. */
#define BTA_USER_SERVICE_MASK 0x80000000 /* Message Notification Profile */

typedef uint32_t tBTA_SERVICE_MASK;

#define BTA_APP_ID_PAN_MULTI 0xFE /* app id for pan multiple connection */
#define BTA_ALL_APP_ID 0xFF

/* Discoverable Modes */
#define BTA_DM_NON_DISC BTM_NON_DISCOVERABLE /* Device is not discoverable. */
#define BTA_DM_GENERAL_DISC                         \
  BTM_GENERAL_DISCOVERABLE /* General discoverable. \
                              */
#define BTA_DM_LIMITED_DISC BTM_LIMITED_DISCOVERABLE

typedef uint16_t
    tBTA_DM_DISC; /* this discoverability mode is a bit mask among BR mode and
                     LE mode */

/* Connectable Modes */
#define BTA_DM_NON_CONN BTM_NON_CONNECTABLE /* Device is not connectable. */
#define BTA_DM_CONN BTM_CONNECTABLE         /* Device is connectable. */

typedef uint16_t tBTA_DM_CONN;

/* Central/peripheral preferred roles */
typedef enum : uint8_t {
  BTA_ANY_ROLE = 0x00,
  BTA_CENTRAL_ROLE_PREF = 0x01,
  BTA_CENTRAL_ROLE_ONLY = 0x02,
  /* Used for PANU only, skip role switch to central */
  BTA_PERIPHERAL_ROLE_ONLY = 0x03,
} tBTA_PREF_ROLES;

inline tBTA_PREF_ROLES toBTA_PREF_ROLES(uint8_t role) {
  ASSERT_LOG(role <= BTA_PERIPHERAL_ROLE_ONLY,
             "Passing illegal preferred role:0x%02x [0x%02x<=>0x%02x]", role,
             BTA_ANY_ROLE, BTA_PERIPHERAL_ROLE_ONLY);
  return static_cast<tBTA_PREF_ROLES>(role);
}

inline std::string preferred_role_text(const tBTA_PREF_ROLES& role) {
  switch (role) {
    CASE_RETURN_TEXT(BTA_ANY_ROLE);
    CASE_RETURN_TEXT(BTA_CENTRAL_ROLE_PREF);
    CASE_RETURN_TEXT(BTA_CENTRAL_ROLE_ONLY);
    CASE_RETURN_TEXT(BTA_PERIPHERAL_ROLE_ONLY);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", role);
  }
}

enum {

  BTA_DM_NO_SCATTERNET,      /* Device doesn't support scatternet, it might
                                support "role switch during connection" for
                                an incoming connection, when it already has
                                another connection in central role */
  BTA_DM_PARTIAL_SCATTERNET, /* Device supports partial scatternet. It can have
                                simultaneous connection in Central and
                                Peripheral roles for short period of time */
  BTA_DM_FULL_SCATTERNET /* Device can have simultaneous connection in central
                            and peripheral roles */

};

typedef struct {
  uint8_t bta_dm_eir_min_name_len; /* minimum length of local name when it is
                                      shortened */
#if (BTA_EIR_CANNED_UUID_LIST == TRUE)
  uint8_t bta_dm_eir_uuid16_len; /* length of 16-bit UUIDs */
  uint8_t* bta_dm_eir_uuid16;    /* 16-bit UUIDs */
#else
  uint32_t uuid_mask[BTM_EIR_SERVICE_ARRAY_SIZE]; /* mask of UUID list in EIR */
#endif
  int8_t* bta_dm_eir_inq_tx_power;     /* Inquiry TX power         */
  uint8_t bta_dm_eir_flag_len;         /* length of flags in bytes */
  uint8_t* bta_dm_eir_flags;           /* flags for EIR */
  uint8_t bta_dm_eir_manufac_spec_len; /* length of manufacturer specific in
                                          bytes */
  uint8_t* bta_dm_eir_manufac_spec;    /* manufacturer specific */
  uint8_t bta_dm_eir_additional_len;   /* length of additional data in bytes */
  uint8_t* bta_dm_eir_additional;      /* additional data */
} tBTA_DM_EIR_CONF;

typedef uint8_t tBTA_DM_BLE_RSSI_ALERT_TYPE;

typedef enum: uint8_t {
  BTA_DM_LINK_UP_EVT = 5,          /* Connection UP event */
  BTA_DM_LINK_DOWN_EVT = 6,        /* Connection DOWN event */
  BTA_DM_LE_FEATURES_READ = 27,    /* Cotroller specific LE features are read */
  BTA_DM_LINK_UP_FAILED_EVT = 34,    /* Create connection failed event */
} tBTA_DM_ACL_EVT;

/* Structure associated with BTA_DM_LINK_UP_EVT */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  tBT_TRANSPORT transport_link_type;
  uint16_t acl_handle;
} tBTA_DM_LINK_UP;

/* Structure associated with BTA_DM_LINK_UP_FAILED_EVT */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  tBT_TRANSPORT transport_link_type;
  tHCI_STATUS status; /* The HCI error code associated with this event */
} tBTA_DM_LINK_UP_FAILED;

/* Structure associated with BTA_DM_LINK_DOWN_EVT */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  tBT_TRANSPORT transport_link_type;
  tHCI_STATUS status;
} tBTA_DM_LINK_DOWN;

typedef union {
  tBTA_DM_LINK_UP link_up;        /* ACL connection up event */
  tBTA_DM_LINK_UP_FAILED link_up_failed; /* ACL connection up failure event */
  tBTA_DM_LINK_DOWN link_down;    /* ACL connection down event */
} tBTA_DM_ACL;

typedef void(tBTA_DM_ACL_CBACK)(tBTA_DM_ACL_EVT event, tBTA_DM_ACL* p_data);

#define BTA_DM_BLE_PF_LIST_LOGIC_OR 1
#define BTA_DM_BLE_PF_FILT_LOGIC_OR 0

/* Search callback events */
typedef enum : uint8_t {
  BTA_DM_INQ_RES_EVT = 0,  /* Inquiry result for a peer device. */
  BTA_DM_INQ_CMPL_EVT = 1, /* Inquiry complete. */
  BTA_DM_DISC_RES_EVT = 2, /* Service Discovery result for a peer device. */
  BTA_DM_GATT_OVER_LE_RES_EVT =
      3,                    /* GATT services over LE transport discovered */
  BTA_DM_DISC_CMPL_EVT = 4, /* Discovery complete. */
  BTA_DM_SEARCH_CANCEL_CMPL_EVT = 5, /* Search cancelled */
  BTA_DM_DID_RES_EVT = 6,            /* Vendor/Product ID search result */
  BTA_DM_GATT_OVER_SDP_RES_EVT = 7,  /* GATT services over SDP discovered */
  BTA_DM_NAME_READ_EVT = 8,          /* Name read complete. */
} tBTA_DM_SEARCH_EVT;

inline std::string bta_dm_search_evt_text(const tBTA_DM_SEARCH_EVT& event) {
  switch (event) {
    CASE_RETURN_TEXT(BTA_DM_INQ_RES_EVT);
    CASE_RETURN_TEXT(BTA_DM_INQ_CMPL_EVT);
    CASE_RETURN_TEXT(BTA_DM_DISC_RES_EVT);
    CASE_RETURN_TEXT(BTA_DM_GATT_OVER_LE_RES_EVT);
    CASE_RETURN_TEXT(BTA_DM_DISC_CMPL_EVT);
    CASE_RETURN_TEXT(BTA_DM_SEARCH_CANCEL_CMPL_EVT);
    CASE_RETURN_TEXT(BTA_DM_DID_RES_EVT);
    CASE_RETURN_TEXT(BTA_DM_GATT_OVER_SDP_RES_EVT);
    CASE_RETURN_TEXT(BTA_DM_NAME_READ_EVT);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", event);
  }
}

/* Structure associated with BTA_DM_INQ_RES_EVT */
typedef struct {
  RawAddress bd_addr;          /* BD address peer device. */
  DEV_CLASS dev_class;         /* Device class of peer device. */
  bool remt_name_not_required; /* Application sets this flag if it already knows
                                  the name of the device */
  /* If the device name is known to application BTA skips the remote name
   * request */
  bool is_limited; /* true, if the limited inquiry bit is set in the CoD */
  int8_t rssi;     /* The rssi value */
  const uint8_t* p_eir; /* received EIR */
  uint16_t eir_len; /* received EIR length */
  uint8_t inq_result_type;
  tBLE_ADDR_TYPE ble_addr_type;
  uint16_t ble_evt_type;
  uint8_t ble_primary_phy;
  uint8_t ble_secondary_phy;
  uint8_t ble_advertising_sid;
  int8_t ble_tx_power;
  uint16_t ble_periodic_adv_int;
  tBT_DEVICE_TYPE device_type;
  uint8_t flag;
  bool include_rsi; /* true, if ADV contains RSI data */
  RawAddress original_bda; /* original address to pass up to
                              GattService#onScanResult */
  uint16_t clock_offset;
} tBTA_DM_INQ_RES;

/* Structure associated with BTA_DM_INQ_CMPL_EVT */
typedef struct {
  uint8_t num_resps; /* Number of inquiry responses. */
} tBTA_DM_INQ_CMPL;

/* Structure associated with BTA_DM_DISC_RES_EVT */
typedef struct {
  RawAddress bd_addr;          /* BD address peer device. */
  BD_NAME bd_name;             /* Name of peer device. */
  tBTA_SERVICE_MASK services;  /* Services found on peer device. */
  tBT_DEVICE_TYPE device_type; /* device type in case it is BLE device */
  size_t num_uuids;
  bluetooth::Uuid* p_uuid_list;
  tBTA_STATUS result;
  tHCI_STATUS hci_status;
} tBTA_DM_DISC_RES;

/* Structure associated with tBTA_DM_DISC_BLE_RES */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  BD_NAME bd_name;  /* Name of peer device. */
  std::vector<bluetooth::Uuid>*
      services; /* GATT based Services UUID found on peer device. */
} tBTA_DM_DISC_BLE_RES;

/* Structure associated with tBTA_DM_DID_RES */
typedef struct {
  RawAddress bd_addr; /* BD address peer device. */
  uint8_t vendor_id_src;
  uint16_t vendor_id;
  uint16_t product_id;
  uint16_t version;
} tBTA_DM_DID_RES;

/* Union of all search callback structures */
typedef union {
  tBTA_DM_INQ_RES inq_res;   /* Inquiry result for a peer device. */
  tBTA_DM_INQ_CMPL inq_cmpl; /* Inquiry complete. */
  tBTA_DM_DISC_RES disc_res; /* Discovery result for a peer device. */
  tBTA_DM_DISC_BLE_RES
      disc_ble_res;             /* discovery result for GATT based service */
  tBTA_DM_DID_RES did_res;      /* Vendor and Product ID of peer device */
} tBTA_DM_SEARCH;

/* Search callback */
typedef void(tBTA_DM_SEARCH_CBACK)(tBTA_DM_SEARCH_EVT event,
                                   tBTA_DM_SEARCH* p_data);

/* Execute call back */
typedef void(tBTA_DM_EXEC_CBACK)(void* p_param);

/* Encryption callback*/
typedef void(tBTA_DM_ENCRYPT_CBACK)(const RawAddress& bd_addr,
                                    tBT_TRANSPORT transport,
                                    tBTA_STATUS result);

typedef void(tBTA_BLE_ENERGY_INFO_CBACK)(tBTM_BLE_TX_TIME_MS tx_time,
                                         tBTM_BLE_RX_TIME_MS rx_time,
                                         tBTM_BLE_IDLE_TIME_MS idle_time,
                                         tBTM_BLE_ENERGY_USED energy_used,
                                         tBTM_CONTRL_STATE ctrl_state,
                                         tBTA_STATUS status);

/* Maximum service name length */
#define BTA_SERVICE_NAME_LEN 35

typedef enum : uint8_t {
  /* power mode actions  */
  BTA_DM_PM_NO_ACTION = 0x00, /* no change to the current pm setting */
  BTA_DM_PM_PARK = 0x10,      /* prefers park mode */
  BTA_DM_PM_SNIFF = 0x20,     /* prefers sniff mode */
  BTA_DM_PM_SNIFF1 = 0x21,    /* prefers sniff1 mode */
  BTA_DM_PM_SNIFF2 = 0x22,    /* prefers sniff2 mode */
  BTA_DM_PM_SNIFF3 = 0x23,    /* prefers sniff3 mode */
  BTA_DM_PM_SNIFF4 = 0x24,    /* prefers sniff4 mode */
  BTA_DM_PM_SNIFF5 = 0x25,    /* prefers sniff5 mode */
  BTA_DM_PM_SNIFF6 = 0x26,    /* prefers sniff6 mode */
  BTA_DM_PM_SNIFF7 = 0x27,    /* prefers sniff7 mode */
  BTA_DM_PM_SNIFF_USER0 =
      0x28, /* prefers user-defined sniff0 mode (testtool only) */
  BTA_DM_PM_SNIFF_USER1 =
      0x29, /* prefers user-defined sniff1 mode (testtool only) */
  BTA_DM_PM_ACTIVE = 0x40,  /* prefers active mode */
  BTA_DM_PM_RETRY = 0x80,   /* retry power mode based on current settings */
  BTA_DM_PM_SUSPEND = 0x04, /* prefers suspend mode */
  BTA_DM_PM_NO_PREF = 0x01, /* service has no preference on power mode setting.
                               eg. connection to \ service got closed */
  BTA_DM_PM_SNIFF_MASK = 0x0f,  // Masks the sniff submode
} tBTA_DM_PM_ACTION_BITMASK;
typedef uint8_t tBTA_DM_PM_ACTION;

/* index to bta_dm_ssr_spec */
enum {
  BTA_DM_PM_SSR0 = 0,
  /* BTA_DM_PM_SSR1 will be dedicated for \
     HH SSR setting entry, no other profile can use it */
  BTA_DM_PM_SSR1 = 1,
  BTA_DM_PM_SSR2 = 2,
  BTA_DM_PM_SSR3 = 3,
  BTA_DM_PM_SSR4 = 4,
};

#define BTA_DM_PM_NUM_EVTS 9

#ifndef BTA_DM_PM_PARK_IDX
#define BTA_DM_PM_PARK_IDX \
  7 /* the actual index to bta_dm_pm_md[] for PARK mode */
#endif

#ifndef BTA_DM_PM_SNIFF_A2DP_IDX
#define BTA_DM_PM_SNIFF_A2DP_IDX BTA_DM_PM_SNIFF
#endif

#ifndef BTA_DM_PM_SNIFF_HD_IDLE_IDX
#define BTA_DM_PM_SNIFF_HD_IDLE_IDX BTA_DM_PM_SNIFF2
#endif

#ifndef BTA_DM_PM_SNIFF_SCO_OPEN_IDX
#define BTA_DM_PM_SNIFF_SCO_OPEN_IDX BTA_DM_PM_SNIFF3
#endif

#ifndef BTA_DM_PM_SNIFF_HD_ACTIVE_IDX
#define BTA_DM_PM_SNIFF_HD_ACTIVE_IDX BTA_DM_PM_SNIFF4
#endif

#ifndef BTA_DM_PM_SNIFF_HH_OPEN_IDX
#define BTA_DM_PM_SNIFF_HH_OPEN_IDX BTA_DM_PM_SNIFF2
#endif

#ifndef BTA_DM_PM_SNIFF_HH_ACTIVE_IDX
#define BTA_DM_PM_SNIFF_HH_ACTIVE_IDX BTA_DM_PM_SNIFF2
#endif

#ifndef BTA_DM_PM_SNIFF_HH_IDLE_IDX
#define BTA_DM_PM_SNIFF_HH_IDLE_IDX BTA_DM_PM_SNIFF2
#endif

#ifndef BTA_DM_PM_HH_OPEN_DELAY
#define BTA_DM_PM_HH_OPEN_DELAY 30000
#endif

#ifndef BTA_DM_PM_HH_ACTIVE_DELAY
#define BTA_DM_PM_HH_ACTIVE_DELAY 30000
#endif

#ifndef BTA_DM_PM_HH_IDLE_DELAY
#define BTA_DM_PM_HH_IDLE_DELAY 30000
#endif

/* The Sniff Parameters defined below must be ordered from highest
 * latency (biggest interval) to lowest latency.  If there is a conflict
 * among the connected services the setting with the lowest latency will
 * be selected.  If a device should override a sniff parameter then it
 * must insure that order is maintained.
 */
#ifndef BTA_DM_PM_SNIFF_MAX
#define BTA_DM_PM_SNIFF_MAX 800
#define BTA_DM_PM_SNIFF_MIN 400
#define BTA_DM_PM_SNIFF_ATTEMPT 4
#define BTA_DM_PM_SNIFF_TIMEOUT 1
#endif

#ifndef BTA_DM_PM_SNIFF1_MAX
#define BTA_DM_PM_SNIFF1_MAX 400
#define BTA_DM_PM_SNIFF1_MIN 200
#define BTA_DM_PM_SNIFF1_ATTEMPT 4
#define BTA_DM_PM_SNIFF1_TIMEOUT 1
#endif

#ifndef BTA_DM_PM_SNIFF2_MAX
#define BTA_DM_PM_SNIFF2_MAX 54
#define BTA_DM_PM_SNIFF2_MIN 30
#define BTA_DM_PM_SNIFF2_ATTEMPT 4
#define BTA_DM_PM_SNIFF2_TIMEOUT 1
#endif

#ifndef BTA_DM_PM_SNIFF3_MAX
#define BTA_DM_PM_SNIFF3_MAX 150
#define BTA_DM_PM_SNIFF3_MIN 50
#define BTA_DM_PM_SNIFF3_ATTEMPT 4
#define BTA_DM_PM_SNIFF3_TIMEOUT 1
#endif

#ifndef BTA_DM_PM_SNIFF4_MAX
#define BTA_DM_PM_SNIFF4_MAX 18
#define BTA_DM_PM_SNIFF4_MIN 10
#define BTA_DM_PM_SNIFF4_ATTEMPT 4
#define BTA_DM_PM_SNIFF4_TIMEOUT 1
#endif

#ifndef BTA_DM_PM_SNIFF5_MAX
#define BTA_DM_PM_SNIFF5_MAX 36
#define BTA_DM_PM_SNIFF5_MIN 30
#define BTA_DM_PM_SNIFF5_ATTEMPT 2
#define BTA_DM_PM_SNIFF5_TIMEOUT 0
#endif

#ifndef BTA_DM_PM_SNIFF6_MAX
#define BTA_DM_PM_SNIFF6_MAX 18
#define BTA_DM_PM_SNIFF6_MIN 14
#define BTA_DM_PM_SNIFF6_ATTEMPT 1
#define BTA_DM_PM_SNIFF6_TIMEOUT 0
#endif

#ifndef BTA_DM_PM_PARK_MAX
#define BTA_DM_PM_PARK_MAX 800
#define BTA_DM_PM_PARK_MIN 400
#define BTA_DM_PM_PARK_ATTEMPT 0
#define BTA_DM_PM_PARK_TIMEOUT 0
#endif

/* Device Identification (DI) data structure
*/

#ifndef BTA_DI_NUM_MAX
#define BTA_DI_NUM_MAX 3
#endif

#define IMMEDIATE_DELY_MODE 0x00
#define ALLOW_ALL_FILTER 0x00
#define LOWEST_RSSI_VALUE 129

/*****************************************************************************
 *  External Function Declarations
 ****************************************************************************/

void BTA_dm_init();

/*******************************************************************************
 *
 * Function         BTA_EnableTestMode
 *
 * Description      Enables bluetooth device under test mode
 *
 *
 * Returns          tBTA_STATUS
 *
 ******************************************************************************/
extern void BTA_EnableTestMode(void);

/*******************************************************************************
 *
 * Function         BTA_DmSetDeviceName
 *
 * Description      This function sets the Bluetooth name of the local device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSetDeviceName(const char* p_name);

/*******************************************************************************
 *
 * Function         BTA_DmSetVisibility
 *
 * Description      This function sets the Bluetooth connectable,discoverable,
 *                  pairable and conn paired only modesmodes of the local
 *                  device.
 *                  This controls whether other Bluetooth devices can find and
 *                  connect to the local device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
bool BTA_DmSetVisibility(bt_scan_mode_t mode);

/*******************************************************************************
 *
 * Function         BTA_DmSearch
 *
 * Description      This function searches for peer Bluetooth devices.  It
 *                  first performs an inquiry; for each device found from the
 *                  inquiry it gets the remote name of the device.  If
 *                  parameter services is nonzero, service discovery will be
 *                  performed on each device for the services specified.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSearch(tBTA_DM_SEARCH_CBACK* p_cback);

/*******************************************************************************
 *
 * Function         BTA_DmSearchCancel
 *
 * Description      This function cancels a search that has been initiated
 *                  by calling BTA_DmSearch().
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSearchCancel(void);

/*******************************************************************************
 *
 * Function         BTA_DmDiscover
 *
 * Description      This function performs service discovery for the services
 *                  of a particular peer device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmDiscover(const RawAddress& bd_addr, tBTA_DM_SEARCH_CBACK* p_cback,
                    tBT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function         BTA_DmGetCachedRemoteName
 *
 * Description      Retieve cached remote name if available
 *
 * Returns          BTA_SUCCESS if cached name was retrieved
 *                  BTA_FAILURE if cached name is not available
 *
 ******************************************************************************/
tBTA_STATUS BTA_DmGetCachedRemoteName(const RawAddress& remote_device,
                                      uint8_t** pp_cached_name);

/*******************************************************************************
 *
 * Function         BTA_GetEirService
 *
 * Description      This function is called to get BTA service mask from EIR.
 *
 * Parameters       p_eir - pointer of EIR significant part
 *                  eir_len - EIR length
 *                  p_services - return the BTA service mask
 *
 * Returns          None
 *
 ******************************************************************************/
void BTA_GetEirService(const uint8_t* p_eir, size_t eir_len,
                       tBTA_SERVICE_MASK* p_services);

/*******************************************************************************
 *
 * Function         BTA_DmGetConnectionState
 *
 * Description      Returns whether the remote device is currently connected.
 *
 * Returns          true if the device is NOT connected, false otherwise.
 *
 ******************************************************************************/
bool BTA_DmGetConnectionState(const RawAddress& bd_addr);

/*******************************************************************************
 *
 * Function         BTA_DmSetLocalDiRecord
 *
 * Description      This function adds a DI record to the local SDP database.
 *
 * Returns          BTA_SUCCESS if record set sucessfully, otherwise error code.
 *
 ******************************************************************************/
tBTA_STATUS BTA_DmSetLocalDiRecord(tSDP_DI_RECORD* p_device_info,
                                   uint32_t* p_handle);

/*******************************************************************************
 *
 *
 * Function         BTA_DmCloseACL
 *
 * Description      This function force to close an ACL connection and remove
 the
 *                  device from the security database list of known devices.
 *
 * Parameters:      bd_addr       - Address of the peer device
 *                  remove_dev    - remove device or not after link down
 *                  transport     - which transport to close

 *
 * Returns          void.
 *
 ******************************************************************************/
void BTA_DmCloseACL(const RawAddress& bd_addr, bool remove_dev,
                    tBT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function         BTA_DmSetBlePrefConnParams
 *
 * Description      This function is called to set the preferred connection
 *                  parameters when default connection parameter is not desired.
 *
 * Parameters:      bd_addr          - BD address of the peripheral
 *                  min_conn_int     - minimum preferred connection interval
 *                  max_conn_int     - maximum preferred connection interval
 *                  peripheral_latency    - preferred peripheral latency
 *                  supervision_tout - preferred supervision timeout
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmSetBlePrefConnParams(const RawAddress& bd_addr,
                                uint16_t min_conn_int, uint16_t max_conn_int,
                                uint16_t peripheral_latency,
                                uint16_t supervision_tout);

/*******************************************************************************
 *
 * Function         BTA_DmBleObserve
 *
 * Description      This procedure keep the device listening for advertising
 *                  events from a broadcast device.
 *
 * Parameters       start: start or stop observe.
 *                  duration : Duration of the scan. Continuous scan if 0 is
 *                             passed
 *                  p_results_cb: Callback to be called with scan results
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleObserve(bool start, uint8_t duration,
                      tBTA_DM_SEARCH_CBACK* p_results_cb);

/*******************************************************************************
 *
 * Function         BTA_DmBleScan
 *
 * Description      Start or stop the scan procedure if it's not already started
 *                  with BTA_DmBleObserve().
 *
 * Parameters       start: start or stop the scan procedure,
 *                  duration_sec: Duration of the scan. Continuous scan if 0 is
 *                                passed,
 *                  low_latency_scan: whether this is a low latency scan,
 *                                    default is false,
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleScan(bool start, uint8_t duration, bool low_latency_scan = false);

/*******************************************************************************
 *
 * Function         BTA_DmBleCsisObserve
 *
 * Description      This procedure keeps the external observer listening for
 *                  advertising events from a CSIS grouped device.
 *
 * Parameters       observe: enable or disable passive observe,
 *                  p_results_cb: Callback to be called with scan results,
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleCsisObserve(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb);

/*******************************************************************************
 *
 * Function         BTA_DmBleConfigLocalPrivacy
 *
 * Description      Enable/disable privacy on the local device
 *
 * Parameters:      privacy_enable   - enable/disabe privacy on remote device.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleConfigLocalPrivacy(bool privacy_enable);

/*******************************************************************************
 *
 * Function         BTA_DmBleEnableRemotePrivacy
 *
 * Description      Enable/disable privacy on a remote device
 *
 * Parameters:      bd_addr          - BD address of the peer
 *                  privacy_enable   - enable/disabe privacy on remote device.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleEnableRemotePrivacy(const RawAddress& bd_addr,
                                  bool privacy_enable);

/*******************************************************************************
 *
 * Function         BTA_DmBleUpdateConnectionParams
 *
 * Description      Update connection parameters, can only be used when
 *                  connection is up.
 *
 * Parameters:      bd_addr   - BD address of the peer
 *                  min_int   - minimum connection interval, [0x0004 ~ 0x4000]
 *                  max_int   - maximum connection interval, [0x0004 ~ 0x4000]
 *                  latency   - peripheral latency [0 ~ 500]
 *                  timeout   - supervision timeout [0x000a ~ 0xc80]
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleUpdateConnectionParams(const RawAddress& bd_addr,
                                     uint16_t min_int, uint16_t max_int,
                                     uint16_t latency, uint16_t timeout,
                                     uint16_t min_ce_len, uint16_t max_ce_len);

/*******************************************************************************
 *
 * Function         BTA_DmBleSetDataLength
 *
 * Description      This function is to set maximum LE data packet size
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleRequestMaxTxDataLength(const RawAddress& remote_device);

/*******************************************************************************
 *
 * Function         BTA_DmBleGetEnergyInfo
 *
 * Description      This function is called to obtain the energy info
 *
 * Parameters       p_cmpl_cback - Command complete callback
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleGetEnergyInfo(tBTA_BLE_ENERGY_INFO_CBACK* p_cmpl_cback);

/*******************************************************************************
 *
 * Function         BTA_BrcmInit
 *
 * Description      This function initializes Broadcom specific VS handler in
 *                  BTA
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_VendorInit(void);

/*******************************************************************************
 *
 * Function         BTA_DmClearEventFilter
 *
 * Description      This function clears the event filter
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmClearEventFilter(void);

/*******************************************************************************
 *
 * Function         BTA_DmClearEventMask
 *
 * Description      This function clears the event mask
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmClearEventMask(void);

/*******************************************************************************
 *
 * Function         BTA_DmDisconnectAllAcls
 *
 * Description      This function will disconnect all LE and Classic ACLs.
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmDisconnectAllAcls(void);

/*******************************************************************************
 *
 * Function         BTA_DmClearFilterAcceptList
 *
 * Description      This function clears the filter accept list
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmClearFilterAcceptList(void);

using LeRandCallback = base::OnceCallback<void(uint64_t)>;
/*******************************************************************************
 *
 * Function         BTA_DmLeRand
 *
 * Description      This function clears the event filter
 *
 * Returns          cb: callback to receive the resulting random number
 *
 ******************************************************************************/
void BTA_DmLeRand(LeRandCallback cb);

/*******************************************************************************
 *
 * Function        BTA_DmSetEventFilterConnectionSetupAllDevices
 *
 * Description    Tell the controller to allow all devices
 *
 * Parameters
 *
 *******************************************************************************/
void BTA_DmSetEventFilterConnectionSetupAllDevices();

/*******************************************************************************
 *
 * Function        BTA_DmAllowWakeByHid
 *
 * Description    Allow the device to be woken by HID devices
 *
 * Parameters
 *
 *******************************************************************************/
void BTA_DmAllowWakeByHid(
    std::vector<RawAddress> classic_hid_devices,
    std::vector<std::pair<RawAddress, uint8_t>> le_hid_devices);

/*******************************************************************************
 *
 * Function        BTA_DmRestoreFilterAcceptList
 *
 * Description    Floss: Restore the state of the for the filter accept list
 *
 * Parameters
 *
 *******************************************************************************/
void BTA_DmRestoreFilterAcceptList(
    std::vector<std::pair<RawAddress, uint8_t>> le_devices);

/*******************************************************************************
 *
 * Function       BTA_DmSetDefaultEventMaskExcept
 *
 * Description    Floss: Set the default event mask for Classic and LE except
 *                the given values (they will be disabled in the final set
 *                mask).
 *
 * Parameters     Bits set for event mask and le event mask that should be
 *                disabled in the final value.
 *
 *******************************************************************************/
void BTA_DmSetDefaultEventMaskExcept(uint64_t mask, uint64_t le_mask);

/*******************************************************************************
 *
 * Function        BTA_DmSetEventFilterInquiryResultAllDevices
 *
 * Description    Floss: Set the event filter to inquiry result device all
 *
 * Parameters
 *
 *******************************************************************************/
void BTA_DmSetEventFilterInquiryResultAllDevices();

/*******************************************************************************
 *
 * Function         BTA_DmBleResetId
 *
 * Description      This function resets the ble keys such as IRK
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleResetId(void);

/*******************************************************************************
 *
 * Function         BTA_DmBleSubrateRequest
 *
 * Description      subrate request, can only be used when connection is up.
 *
 * Parameters:      bd_addr       - BD address of the peer
 *                  subrate_min   - subrate min
 *                  subrate_max   - subrate max
 *                  max_latency   - max latency
 *                  cont_num      - continuation number
 *                  timeout       - supervision timeout
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_DmBleSubrateRequest(const RawAddress& bd_addr, uint16_t subrate_min,
                             uint16_t subrate_max, uint16_t max_latency,
                             uint16_t cont_num, uint16_t timeout);

/*******************************************************************************
 *
 * Function         BTA_DmCheckLeAudioCapable
 *
 * Description      Checks if device should be considered as LE Audio capable
 *
 * Returns          True if Le Audio capable device, false otherwise
 *
 ******************************************************************************/
bool BTA_DmCheckLeAudioCapable(const RawAddress& address);

void DumpsysBtaDm(int fd);

namespace fmt {
template <>
struct formatter<tBTA_DM_SEARCH_EVT> : enum_formatter<tBTA_DM_SEARCH_EVT> {};
template <>
struct formatter<tBTA_DM_ACL_EVT> : enum_formatter<tBTA_DM_ACL_EVT> {};
}  // namespace fmt

#endif /* BTA_API_H */
