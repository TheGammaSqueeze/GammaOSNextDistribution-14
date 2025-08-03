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
 *  This is the public interface file for the BTA system manager.
 *
 ******************************************************************************/
#ifndef BTA_SYS_H
#define BTA_SYS_H

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <chrono>
#include <cstdint>
#include <string>

#include "internal_include/bt_target.h"
#include "macros.h"
#include "osi/include/alarm.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/hci_error_code.h"
#include "types/bluetooth/uuid.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/

/* vendor specific event handler function type */
typedef bool(tBTA_SYS_VS_EVT_HDLR)(uint16_t evt, void* p);

/* event handler function type */
typedef bool(tBTA_SYS_EVT_HDLR)(const BT_HDR_RIGID* p_msg);
static_assert(
    sizeof(BT_HDR) == sizeof(BT_HDR_RIGID),
    "Rigid replacement should be same size struct with flexible member");

/* disable function type */
typedef void(tBTA_SYS_DISABLE)(void);

template <typename T, typename U>
inline const T* Specialize(U* u) {
  return const_cast<const T*>(reinterpret_cast<T*>(u));
}

#ifndef BTA_DM_NUM_JV_ID
#define BTA_DM_NUM_JV_ID 2
#endif

typedef enum : uint8_t {
  /* SW sub-systems */
  BTA_ID_SYS = 0,       /* system manager */
                        /* BLUETOOTH PART - from = 0, to BTA_ID_BLUETOOTH_MAX */
  BTA_ID_DM_SEARCH = 2, /* device manager search */
  BTA_ID_DM_SEC = 3,    /* device manager security */
  BTA_ID_DG = 4,        /* data gateway */
  BTA_ID_AG = 5,        /* audio gateway */
  BTA_ID_OPC = 6,       /* object push client */
  BTA_ID_OPS = 7,       /* object push server */
  BTA_ID_FTS = 8,       /* file transfer server */
  BTA_ID_CT = 9,        /* cordless telephony terminal */
  BTA_ID_FTC = 10,      /* file transfer client */
  BTA_ID_SS = 11,       /* synchronization server */
  BTA_ID_PR = 12,       /* Printer client */
  BTA_ID_BIC = 13,      /* Basic Imaging Client */
  BTA_ID_PAN = 14,      /* Personal Area Networking */
  BTA_ID_BIS = 15,      /* Basic Imaging Server */
  BTA_ID_ACC = 16,      /* Advanced Camera Client */
  BTA_ID_SC = 17,       /* SIM Card Access server */
  BTA_ID_AV = 18,       /* Advanced audio/video */
  BTA_ID_AVK = 19,      /* Audio/video sink */
  BTA_ID_HD = 20,       /* HID Device */
  BTA_ID_CG = 21,       /* Cordless Gateway */
  BTA_ID_BP = 22,       /* Basic Printing Client */
  BTA_ID_HH = 23,       /* Human Interface Device Host */
  BTA_ID_PBS = 24,      /* Phone Book Access Server */
  BTA_ID_PBC = 25,      /* Phone Book Access Client */
  BTA_ID_JV = 26,       /* Java */
  BTA_ID_HS = 27,       /* Headset */
  BTA_ID_MSE = 28,      /* Message Server Equipment */
  BTA_ID_MCE = 29,      /* Message Client Equipment */
  BTA_ID_HL = 30,       /* Health Device Profile*/
  BTA_ID_GATTC = 31,    /* GATT Client */
  BTA_ID_GATTS = 32,    /* GATT Client */
  BTA_ID_SDP = 33,      /* SDP Client */
  BTA_ID_BLUETOOTH_MAX = 34, /* last BT profile */

  BTA_ID_MAX = (44 + BTA_DM_NUM_JV_ID),
} tBTA_SYS_ID;

inline std::string BtaIdSysText(const tBTA_SYS_ID& sys_id) {
  switch (sys_id) {
    CASE_RETURN_TEXT(BTA_ID_SYS);
    CASE_RETURN_TEXT(BTA_ID_DM_SEARCH);
    CASE_RETURN_TEXT(BTA_ID_DM_SEC);
    CASE_RETURN_TEXT(BTA_ID_DG);
    CASE_RETURN_TEXT(BTA_ID_AG);
    CASE_RETURN_TEXT(BTA_ID_OPC);
    CASE_RETURN_TEXT(BTA_ID_OPS);
    CASE_RETURN_TEXT(BTA_ID_FTS);
    CASE_RETURN_TEXT(BTA_ID_CT);
    CASE_RETURN_TEXT(BTA_ID_FTC);
    CASE_RETURN_TEXT(BTA_ID_SS);
    CASE_RETURN_TEXT(BTA_ID_PR);
    CASE_RETURN_TEXT(BTA_ID_BIC);
    CASE_RETURN_TEXT(BTA_ID_PAN);
    CASE_RETURN_TEXT(BTA_ID_BIS);
    CASE_RETURN_TEXT(BTA_ID_ACC);
    CASE_RETURN_TEXT(BTA_ID_SC);
    CASE_RETURN_TEXT(BTA_ID_AV);
    CASE_RETURN_TEXT(BTA_ID_AVK);
    CASE_RETURN_TEXT(BTA_ID_HD);
    CASE_RETURN_TEXT(BTA_ID_CG);
    CASE_RETURN_TEXT(BTA_ID_BP);
    CASE_RETURN_TEXT(BTA_ID_HH);
    CASE_RETURN_TEXT(BTA_ID_PBS);
    CASE_RETURN_TEXT(BTA_ID_PBC);
    CASE_RETURN_TEXT(BTA_ID_JV);
    CASE_RETURN_TEXT(BTA_ID_HS);
    CASE_RETURN_TEXT(BTA_ID_MSE);
    CASE_RETURN_TEXT(BTA_ID_MCE);
    CASE_RETURN_TEXT(BTA_ID_HL);
    CASE_RETURN_TEXT(BTA_ID_GATTC);
    CASE_RETURN_TEXT(BTA_ID_GATTS);
    CASE_RETURN_TEXT(BTA_ID_SDP);
    CASE_RETURN_TEXT(BTA_ID_BLUETOOTH_MAX);
    default:
      return base::StringPrintf("Unknown[%hhu]", sys_id);
  }
}

typedef enum : uint8_t {
  BTA_SYS_CONN_OPEN = 0x00,
  BTA_SYS_CONN_CLOSE = 0x01,
  BTA_SYS_APP_OPEN = 0x02,
  BTA_SYS_APP_CLOSE = 0x03,
  BTA_SYS_SCO_OPEN = 0x04,
  BTA_SYS_SCO_CLOSE = 0x05,
  BTA_SYS_CONN_IDLE = 0x06,
  BTA_SYS_CONN_BUSY = 0x07,
  BTA_SYS_ROLE_CHANGE = 0x14, /* role change */
} tBTA_SYS_CONN_STATUS;

inline std::string bta_sys_conn_status_text(tBTA_SYS_CONN_STATUS status) {
  switch (status) {
    case BTA_SYS_CONN_OPEN:
      return std::string("BTA_SYS_CONN_OPEN");
    case BTA_SYS_CONN_CLOSE:
      return std::string("BTA_SYS_CONN_CLOSE");
    case BTA_SYS_APP_OPEN:
      return std::string("BTA_SYS_APP_OPEN");
    case BTA_SYS_APP_CLOSE:
      return std::string("BTA_SYS_APP_CLOSE");
    case BTA_SYS_SCO_OPEN:
      return std::string("BTA_SYS_SCO_OPEN");
    case BTA_SYS_SCO_CLOSE:
      return std::string("BTA_SYS_SCO_CLOSE");
    case BTA_SYS_CONN_IDLE:
      return std::string("BTA_SYS_CONN_IDLE");
    case BTA_SYS_CONN_BUSY:
      return std::string("BTA_SYS_CONN_BUSY");
    case BTA_SYS_ROLE_CHANGE:
      return std::string("BTA_SYS_ROLE_CHANGE");
    default:
      return std::string("UNKNOWN");
  }
}

/* conn callback for power mode manager */
typedef void(tBTA_SYS_CONN_CBACK)(tBTA_SYS_CONN_STATUS status,
                                  const tBTA_SYS_ID id, uint8_t app_id,
                                  const RawAddress& peer_addr);
/* conn callback for sco change */
typedef void(tBTA_SYS_CONN_SCO_CBACK)(tBTA_SYS_CONN_STATUS status,
                                      uint8_t num_sco_links, uint8_t app_id,
                                      const RawAddress& peer_addr);
/* callback for role switch */
typedef void(tBTA_SYS_ROLE_SWITCH_CBACK)(tBTA_SYS_CONN_STATUS status,
                                         tHCI_ROLE new_role,
                                         tHCI_STATUS hci_status,
                                         const RawAddress& peer_addr);
/* callback for sniff subrating updates */
typedef void(tBTA_SYS_SSR_CFG_CBACK)(uint8_t id, uint8_t app_id,
                                     uint16_t latency, uint16_t tout);

typedef void(tBTA_SYS_SNIFF_CBACK)(uint8_t id, uint8_t app_id,
                                   const RawAddress& peer_addr);

typedef struct {
  bluetooth::Uuid custom_uuid;
  uint32_t handle;
} tBTA_CUSTOM_UUID;

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
/* eir callback for adding/removeing UUID */
typedef void(tBTA_SYS_EIR_CBACK)(uint16_t uuid16, bool adding);
typedef void(tBTA_SYS_CUST_EIR_CBACK)(const tBTA_CUSTOM_UUID &curr, bool adding);
#endif

/* registration structure */
typedef struct {
  tBTA_SYS_EVT_HDLR* evt_hdlr;
  tBTA_SYS_DISABLE* disable;
} tBTA_SYS_REG;

/*****************************************************************************
 *  Macros
 ****************************************************************************/
/* Calculate start of event enumeration; id is top 8 bits of event */
#define BTA_SYS_EVT_START(id) ((id) << 8)

/*****************************************************************************
 *  Function declarations
 ****************************************************************************/
void bta_sys_init(void);
void bta_sys_register(uint8_t id, const tBTA_SYS_REG* p_reg);
void bta_sys_deregister(uint8_t id);
bool bta_sys_is_register(uint8_t id);
void bta_sys_sendmsg(void* p_msg);
void bta_sys_sendmsg_delayed(void* p_msg, std::chrono::microseconds delay);
void bta_sys_start_timer(alarm_t* alarm, uint64_t interval_ms, uint16_t event,
                         uint16_t layer_specific);
void bta_sys_disable();

void bta_sys_rm_register(tBTA_SYS_CONN_CBACK* p_cback);
void bta_sys_pm_register(tBTA_SYS_CONN_CBACK* p_cback);
void bta_sys_sniff_register(tBTA_SYS_SNIFF_CBACK* p_cback);
void bta_sys_sco_register(tBTA_SYS_CONN_SCO_CBACK* p_cback);

void bta_sys_conn_open(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr);
void bta_sys_conn_close(tBTA_SYS_ID id, uint8_t app_id,
                        const RawAddress& peer_addr);
void bta_sys_app_open(tBTA_SYS_ID id, uint8_t app_id,
                      const RawAddress& peer_addr);
void bta_sys_app_close(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr);
void bta_sys_sco_open(tBTA_SYS_ID id, uint8_t app_id,
                      const RawAddress& peer_addr);
void bta_sys_sco_close(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr);
void bta_sys_sco_use(tBTA_SYS_ID id, uint8_t app_id,
                     const RawAddress& peer_addr);
void bta_sys_sco_unuse(tBTA_SYS_ID id, uint8_t app_id,
                       const RawAddress& peer_addr);
void bta_sys_idle(tBTA_SYS_ID id, uint8_t app_id, const RawAddress& peer_addr);
void bta_sys_busy(tBTA_SYS_ID id, uint8_t app_id, const RawAddress& peer_addr);
void bta_sys_reset_sniff(uint8_t id, uint8_t app_id,
                         const RawAddress& peer_addr);
void bta_sys_ssr_cfg_register(tBTA_SYS_SSR_CFG_CBACK* p_cback);
void bta_sys_chg_ssr_config(tBTA_SYS_ID id, uint8_t app_id,
                            uint16_t max_latency, uint16_t min_tout);

void bta_sys_role_chg_register(tBTA_SYS_ROLE_SWITCH_CBACK* p_cback);
void bta_sys_notify_role_chg(const RawAddress& peer_addr, tHCI_ROLE new_role,
                             tHCI_STATUS hci_status);
void bta_sys_collision_register(tBTA_SYS_ID bta_id,
                                tBTA_SYS_CONN_CBACK* p_cback);
void bta_sys_notify_collision(const RawAddress& peer_addr);

#if (BTA_EIR_CANNED_UUID_LIST != TRUE)
void bta_sys_eir_register(tBTA_SYS_EIR_CBACK* p_cback);
void bta_sys_eir_unregister();
void bta_sys_add_uuid(uint16_t uuid16);
void bta_sys_remove_uuid(uint16_t uuid16);
void bta_sys_cust_eir_register(tBTA_SYS_CUST_EIR_CBACK* p_cback);
void bta_sys_add_cust_uuid(const tBTA_CUSTOM_UUID& curr);
void bta_sys_remove_cust_uuid(const tBTA_CUSTOM_UUID& curr);
#else
#define bta_sys_eir_register(ut)
#define bta_sys_eir_unregister()
#define bta_sys_add_uuid(ut)
#define bta_sys_remove_uuid(ut)
#define bta_sys_cust_eir_register(ut)
#define bta_sys_add_cust_uuid(ut)
#define bta_sys_remove_cust_uuid(ut)
#endif

namespace fmt {
template <>
struct formatter<tBTA_SYS_ID> : enum_formatter<tBTA_SYS_ID> {};
template <>
struct formatter<tBTA_SYS_CONN_STATUS> : enum_formatter<tBTA_SYS_CONN_STATUS> {
};
}  // namespace fmt

#endif /* BTA_SYS_H */
