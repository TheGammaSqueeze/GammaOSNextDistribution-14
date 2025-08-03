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

/*******************************************************************************
 *
 *  Filename:      btif_hf.c
 *
 *  Description:   Handsfree Profile Bluetooth Interface
 *
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_hf"

#include <android_bluetooth_flags.h>
#include <android_bluetooth_sysprop.h>
#include <base/functional/callback.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>

#include <cstdint>
#include <string>

#include "bta/include/bta_ag_api.h"
#include "bta/include/utl.h"
#include "bta_ag_swb_aptx.h"
#include "btif/include/btif_common.h"
#include "btif/include/btif_metrics_logging.h"
#include "btif/include/btif_profile_queue.h"
#include "btif/include/btif_util.h"
#include "common/metrics.h"
#include "device/include/device_iot_config.h"
#include "include/check.h"
#include "include/hardware/bluetooth_headset_callbacks.h"
#include "include/hardware/bluetooth_headset_interface.h"
#include "include/hardware/bt_hf.h"
#include "internal_include/bt_target.h"
#include "stack/btm/btm_sco_hfp_hal.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_log_history.h"
#include "types/raw_address.h"

namespace {
constexpr char kBtmLogTag[] = "HFP";
}

namespace bluetooth {
namespace headset {

/*******************************************************************************
 *  Constants & Macros
 ******************************************************************************/
#ifndef BTIF_HSAG_SERVICE_NAME
#define BTIF_HSAG_SERVICE_NAME ("Headset Gateway")
#endif

#ifndef BTIF_HFAG_SERVICE_NAME
#define BTIF_HFAG_SERVICE_NAME ("Handsfree Gateway")
#endif

#ifndef BTIF_HF_SERVICE_NAMES
#define BTIF_HF_SERVICE_NAMES \
  { BTIF_HSAG_SERVICE_NAME, BTIF_HFAG_SERVICE_NAME }
#endif

static uint32_t get_hf_features();
/* HF features supported at runtime */
static uint32_t btif_hf_features = get_hf_features();

#define BTIF_HF_INVALID_IDX (-1)

/* Max HF clients supported from App */
static int btif_max_hf_clients = 1;
static RawAddress active_bda = {};

/*******************************************************************************
 *  Static variables
 ******************************************************************************/
static Callbacks* bt_hf_callbacks = nullptr;

#define CHECK_BTHF_INIT()                \
  do {                                   \
    if (!bt_hf_callbacks) {              \
      log::warn("BTHF not initialized"); \
      return BT_STATUS_NOT_READY;        \
    } else {                             \
      log::verbose("BTHF ok");           \
    }                                    \
  } while (false)

/* BTIF-HF control block to map bdaddr to BTA handle */
struct btif_hf_cb_t {
  uint16_t handle;
  bool is_initiator;
  RawAddress connected_bda;
  bthf_connection_state_t state;
  tBTA_AG_PEER_FEAT peer_feat;
  int num_active;
  int num_held;
  bthf_call_state_t call_setup_state;
};

static btif_hf_cb_t btif_hf_cb[BTA_AG_MAX_NUM_CLIENTS];

static const char* dump_hf_call_state(bthf_call_state_t call_state) {
  switch (call_state) {
    CASE_RETURN_STR(BTHF_CALL_STATE_IDLE)
    CASE_RETURN_STR(BTHF_CALL_STATE_HELD)
    CASE_RETURN_STR(BTHF_CALL_STATE_DIALING)
    CASE_RETURN_STR(BTHF_CALL_STATE_ALERTING)
    CASE_RETURN_STR(BTHF_CALL_STATE_INCOMING)
    CASE_RETURN_STR(BTHF_CALL_STATE_WAITING)
    CASE_RETURN_STR(BTHF_CALL_STATE_ACTIVE)
    CASE_RETURN_STR(BTHF_CALL_STATE_DISCONNECTED)
    default:
      return "UNKNOWN CALL STATE";
  }
}

/**
 * Check if bd_addr is the current active device.
 *
 * @param bd_addr target device address
 * @return True if bd_addr is the current active device, False otherwise or if
 * no active device is set (i.e. active_device_addr is empty)
 */
static bool is_active_device(const RawAddress& bd_addr) {
  return !active_bda.IsEmpty() && active_bda == bd_addr;
}

static tBTA_SERVICE_MASK get_BTIF_HF_SERVICES() {
  return GET_SYSPROP(Hfp, hf_services,
                     BTA_HSP_SERVICE_MASK | BTA_HFP_SERVICE_MASK);
}

/* HF features supported at runtime */
static uint32_t get_hf_features() {
#if TARGET_FLOSS
#define DEFAULT_BTIF_HF_FEATURES \
  (BTA_AG_FEAT_ECS | BTA_AG_FEAT_CODEC | BTA_AG_FEAT_UNAT | BTA_AG_FEAT_HF_IND)
#else
#define DEFAULT_BTIF_HF_FEATURES                                  \
  (BTA_AG_FEAT_3WAY | BTA_AG_FEAT_ECNR | BTA_AG_FEAT_REJECT |     \
   BTA_AG_FEAT_ECS | BTA_AG_FEAT_EXTERR | BTA_AG_FEAT_VREC |      \
   BTA_AG_FEAT_CODEC | BTA_AG_FEAT_HF_IND | BTA_AG_FEAT_ESCO_S4 | \
   BTA_AG_FEAT_UNAT)
#endif

  return GET_SYSPROP(Hfp, hf_features, DEFAULT_BTIF_HF_FEATURES);
}

/*******************************************************************************
 *
 * Function         is_connected
 *
 * Description      Internal function to check if HF is connected
 *                  is_connected(nullptr) returns TRUE if one of the control
 *                  blocks is connected
 *
 * Returns          true if connected
 *
 ******************************************************************************/
static bool is_connected(RawAddress* bd_addr) {
  for (int i = 0; i < btif_max_hf_clients; ++i) {
    if (((btif_hf_cb[i].state == BTHF_CONNECTION_STATE_CONNECTED) ||
         (btif_hf_cb[i].state == BTHF_CONNECTION_STATE_SLC_CONNECTED)) &&
        (!bd_addr || *bd_addr == btif_hf_cb[i].connected_bda))
      return true;
  }
  return false;
}

/*******************************************************************************
 *
 * Function         btif_hf_idx_by_bdaddr
 *
 * Description      Internal function to get idx by bdaddr
 *
 * Returns          idx
 *
 ******************************************************************************/
static int btif_hf_idx_by_bdaddr(RawAddress* bd_addr) {
  for (int i = 0; i < btif_max_hf_clients; ++i) {
    if (*bd_addr == btif_hf_cb[i].connected_bda) return i;
  }
  return BTIF_HF_INVALID_IDX;
}

/*******************************************************************************
 *
 * Function         callstate_to_callsetup
 *
 * Description      Converts HAL call state to BTA call setup indicator value
 *
 * Returns          BTA call indicator value
 *
 ******************************************************************************/
static uint8_t callstate_to_callsetup(bthf_call_state_t call_state) {
  switch (call_state) {
    case BTHF_CALL_STATE_INCOMING:
      return 1;
    case BTHF_CALL_STATE_DIALING:
      return 2;
    case BTHF_CALL_STATE_ALERTING:
      return 3;
    default:
      return 0;
  }
}

/*******************************************************************************
 *
 * Function         send_at_result
 *
 * Description      Send AT result code (OK/ERROR)
 *
 * Returns          void
 *
 ******************************************************************************/
static void send_at_result(uint8_t ok_flag, uint16_t errcode, int idx) {
  tBTA_AG_RES_DATA ag_res = {};
  ag_res.ok_flag = ok_flag;
  if (ok_flag == BTA_AG_OK_ERROR) {
    ag_res.errcode = errcode;
  }
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_UNAT_RES, ag_res);
}

/*******************************************************************************
 *
 * Function         send_indicator_update
 *
 * Description      Send indicator update (CIEV)
 *
 * Returns          void
 *
 ******************************************************************************/
static void send_indicator_update(const btif_hf_cb_t& control_block,
                                  uint16_t indicator, uint16_t value) {
  tBTA_AG_RES_DATA ag_res = {};
  ag_res.ind.id = indicator;
  ag_res.ind.value = value;
  BTA_AgResult(control_block.handle, BTA_AG_IND_RES, ag_res);
}

static bool is_nth_bit_enabled(uint32_t value, int n) {
  return (value & (static_cast<uint32_t>(1) << n)) != 0;
}

void clear_phone_state_multihf(btif_hf_cb_t* hf_cb) {
  hf_cb->call_setup_state = BTHF_CALL_STATE_IDLE;
  hf_cb->num_active = 0;
  hf_cb->num_held = 0;
}

static void reset_control_block(btif_hf_cb_t* hf_cb) {
  hf_cb->state = BTHF_CONNECTION_STATE_DISCONNECTED;
  hf_cb->is_initiator = false;
  hf_cb->connected_bda = RawAddress::kEmpty;
  hf_cb->peer_feat = 0;
  clear_phone_state_multihf(hf_cb);
}

/**
 * Check if Service Level Connection (SLC) is established for bd_addr
 *
 * @param bd_addr remote device address
 * @return true if SLC is established for bd_addr
 */
static bool IsSlcConnected(RawAddress* bd_addr) {
  if (!bd_addr) {
    log::warn("bd_addr is null");
    return false;
  }
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if (idx < 0 || idx > BTA_AG_MAX_NUM_CLIENTS) {
    log::warn("invalid index {} for {}", idx,
              ADDRESS_TO_LOGGABLE_STR(*bd_addr));
    return false;
  }
  return btif_hf_cb[idx].state == BTHF_CONNECTION_STATE_SLC_CONNECTED;
}

/*******************************************************************************
 *
 * Function         btif_hf_upstreams_evt
 *
 * Description      Executes HF UPSTREAMS events in btif context
 *
 * Returns          void
 *
 ******************************************************************************/
static void btif_hf_upstreams_evt(uint16_t event, char* p_param) {
  if (event == BTA_AG_ENABLE_EVT || event == BTA_AG_DISABLE_EVT) {
    log::info("AG enable/disable event {}", event);
    return;
  }
  if (p_param == nullptr) {
    log::error("parameter is null");
    return;
  }
  tBTA_AG* p_data = (tBTA_AG*)p_param;
  int idx = p_data->hdr.handle - 1;

  log::debug("HF Upstream event:{}", dump_hf_event(event));

  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("{} Invalid client index:{}", dump_hf_event(event), idx);
    return;
  }
  if (!bt_hf_callbacks) {
    log::error("{} Headset callback is not set", dump_hf_event(event));
    return;
  }

  switch (event) {
    case BTA_AG_REGISTER_EVT:
      btif_hf_cb[idx].handle = p_data->reg.hdr.handle;
      log::debug("{} idx:{} btif_hf_cb.handle = {}", dump_hf_event(event), idx,
                 btif_hf_cb[idx].handle);
      break;
    // RFCOMM connected or failed to connect
    case BTA_AG_OPEN_EVT:
      bt_hf_callbacks->ConnectionStateCallback(BTHF_CONNECTION_STATE_CONNECTING,
                                               &(p_data->open.bd_addr));
      // Check if an outgoing connection is pending
      if (btif_hf_cb[idx].is_initiator) {
        // There is an outgoing connection.
        // Check the incoming open event status and the outgoing connection
        // state.
        if ((p_data->open.status != BTA_AG_SUCCESS) &&
            btif_hf_cb[idx].state != BTHF_CONNECTION_STATE_CONNECTING) {
          // Check if the incoming open event and the outgoing connection are
          // for the same device.
          if (p_data->open.bd_addr == btif_hf_cb[idx].connected_bda) {
            log::warn(
                "btif_hf_cb state[{}] is not expected, possible connection "
                "collision, ignoring AG open failure event for the same device "
                "{}",
                p_data->open.status,
                ADDRESS_TO_LOGGABLE_STR(p_data->open.bd_addr));
          } else {
            log::warn(
                "btif_hf_cb state[{}] is not expected, possible connection "
                "collision, ignoring AG open failure event for the different "
                "devices btif_hf_cb bda: {}, p_data bda: {}, report disconnect "
                "state for p_data bda.",
                p_data->open.status,
                ADDRESS_TO_LOGGABLE_STR(btif_hf_cb[idx].connected_bda),
                ADDRESS_TO_LOGGABLE_STR(p_data->open.bd_addr));
            bt_hf_callbacks->ConnectionStateCallback(
                BTHF_CONNECTION_STATE_DISCONNECTED, &(p_data->open.bd_addr));
            log_counter_metrics_btif(
                android::bluetooth::CodePathCounterKeyEnum::
                    HFP_COLLISON_AT_AG_OPEN,
                1);
          }
          break;
        }

        // There is an outgoing connection.
        // Check the outgoing connection state and address.
        CHECK_EQ(btif_hf_cb[idx].state, BTHF_CONNECTION_STATE_CONNECTING)
            << "Control block must be in connecting state when initiating";
        CHECK(!btif_hf_cb[idx].connected_bda.IsEmpty())
            << "Remote device address must not be empty when initiating";
        // Check if the incoming open event and the outgoing connection are
        // for the same device.
        if (btif_hf_cb[idx].connected_bda != p_data->open.bd_addr) {
          log::warn(
              "possible connection collision, ignore the outgoing connection "
              "for the different devices btif_hf_cb bda: {}, p_data bda: {}, "
              "report disconnect state for btif_hf_cb bda.",
              ADDRESS_TO_LOGGABLE_STR(btif_hf_cb[idx].connected_bda),
              ADDRESS_TO_LOGGABLE_STR(p_data->open.bd_addr));
          bt_hf_callbacks->ConnectionStateCallback(
              BTHF_CONNECTION_STATE_DISCONNECTED,
              &(btif_hf_cb[idx].connected_bda));
          log_counter_metrics_btif(android::bluetooth::CodePathCounterKeyEnum::
                                       HFP_COLLISON_AT_CONNECTING,
                                   1);
          reset_control_block(&btif_hf_cb[idx]);
          btif_queue_advance();
        }
      }

      // There is no pending outgoing connection.
      if (p_data->open.status == BTA_AG_SUCCESS) {
        // In case this is an incoming connection
        btif_hf_cb[idx].connected_bda = p_data->open.bd_addr;
        if (btif_hf_cb[idx].state != BTHF_CONNECTION_STATE_CONNECTING) {
          DEVICE_IOT_CONFIG_ADDR_SET_INT(btif_hf_cb[idx].connected_bda,
                                         IOT_CONF_KEY_HFP_ROLE,
                                         IOT_CONF_VAL_HFP_ROLE_CLIENT);
          DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(btif_hf_cb[idx].connected_bda,
                                             IOT_CONF_KEY_HFP_SLC_CONN_COUNT);
        }

        btif_hf_cb[idx].state = BTHF_CONNECTION_STATE_CONNECTED;
        btif_hf_cb[idx].peer_feat = 0;
        clear_phone_state_multihf(&btif_hf_cb[idx]);
        bluetooth::common::BluetoothMetricsLogger::GetInstance()
            ->LogHeadsetProfileRfcConnection(p_data->open.service_id);
        bt_hf_callbacks->ConnectionStateCallback(
            btif_hf_cb[idx].state, &btif_hf_cb[idx].connected_bda);
      } else {
        if (!btif_hf_cb[idx].is_initiator) {
          // Ignore remote initiated open failures
          log::warn("Unexpected AG open failure {} for {} is ignored",
                    p_data->open.status,
                    ADDRESS_TO_LOGGABLE_STR(p_data->open.bd_addr));
          break;
        }
        log::error("self initiated AG open failed for {}, status {}",
                   ADDRESS_TO_LOGGABLE_STR(btif_hf_cb[idx].connected_bda),
                   p_data->open.status);
        RawAddress connected_bda = btif_hf_cb[idx].connected_bda;
        reset_control_block(&btif_hf_cb[idx]);
        bt_hf_callbacks->ConnectionStateCallback(btif_hf_cb[idx].state,
                                                 &connected_bda);
        log_counter_metrics_btif(android::bluetooth::CodePathCounterKeyEnum::
                                     HFP_SELF_INITIATED_AG_FAILED,
                                 1);
        btif_queue_advance();
        DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(
            connected_bda, IOT_CONF_KEY_HFP_SLC_CONN_FAIL_COUNT);
      }
      break;
    case BTA_AG_CLOSE_EVT: {
      log::debug(
          "SLC and RFCOMM both disconnected event:{} idx:{} "
          "btif_hf_cb.handle:{}",
          dump_hf_event(event), idx, btif_hf_cb[idx].handle);
      RawAddress connected_bda = btif_hf_cb[idx].connected_bda;
      bt_hf_callbacks->ConnectionStateCallback(
          BTHF_CONNECTION_STATE_DISCONNECTING, &connected_bda);
      // If AG_OPEN was received but SLC was not connected in time, then
      // AG_CLOSE may be received. We need to advance the queue here.
      bool failed_to_setup_slc =
          (btif_hf_cb[idx].state != BTHF_CONNECTION_STATE_SLC_CONNECTED) &&
          btif_hf_cb[idx].is_initiator;

      reset_control_block(&btif_hf_cb[idx]);
      bt_hf_callbacks->ConnectionStateCallback(btif_hf_cb[idx].state,
                                               &connected_bda);
      if (failed_to_setup_slc) {
        log::error("failed to setup SLC for {}",
                   ADDRESS_TO_LOGGABLE_STR(connected_bda));
        log_counter_metrics_btif(
            android::bluetooth::CodePathCounterKeyEnum::HFP_SLC_SETUP_FAILED,
            1);
        btif_queue_advance();
        DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(
            btif_hf_cb[idx].connected_bda,
            IOT_CONF_KEY_HFP_SLC_CONN_FAIL_COUNT);
      }
      break;
    }
    case BTA_AG_CONN_EVT:
      DEVICE_IOT_CONFIG_ADDR_SET_HEX(
          btif_hf_cb[idx].connected_bda, IOT_CONF_KEY_HFP_CODECTYPE,
          p_data->conn.peer_codec == 0x03 ? IOT_CONF_VAL_HFP_CODECTYPE_CVSDMSBC
                                          : IOT_CONF_VAL_HFP_CODECTYPE_CVSD,
          IOT_CONF_BYTE_NUM_1);
      DEVICE_IOT_CONFIG_ADDR_SET_HEX(
          btif_hf_cb[idx].connected_bda, IOT_CONF_KEY_HFP_FEATURES,
          p_data->conn.peer_feat, IOT_CONF_BYTE_NUM_2);

      log::debug("SLC connected event:{} idx:{}", dump_hf_event(event), idx);
      btif_hf_cb[idx].peer_feat = p_data->conn.peer_feat;
      btif_hf_cb[idx].state = BTHF_CONNECTION_STATE_SLC_CONNECTED;
      bt_hf_callbacks->ConnectionStateCallback(btif_hf_cb[idx].state,
                                               &btif_hf_cb[idx].connected_bda);
      if (btif_hf_cb[idx].is_initiator) {
        btif_queue_advance();
      }
      break;

    case BTA_AG_AUDIO_OPEN_EVT:
      log::debug("Audio open event:{}", dump_hf_event(event));
      bt_hf_callbacks->AudioStateCallback(BTHF_AUDIO_STATE_CONNECTED,
                                          &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AUDIO_CLOSE_EVT:
      log::debug("Audio close event:{}", dump_hf_event(event));

      DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(btif_hf_cb[idx].connected_bda,
                                         IOT_CONF_KEY_HFP_SCO_CONN_FAIL_COUNT);

      bt_hf_callbacks->AudioStateCallback(BTHF_AUDIO_STATE_DISCONNECTED,
                                          &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_SPK_EVT:
    case BTA_AG_MIC_EVT:
      log::debug("BTA auto-responds, silently discard event:{}",
                 dump_hf_event(event));
      bt_hf_callbacks->VolumeControlCallback(
          (event == BTA_AG_SPK_EVT) ? BTHF_VOLUME_TYPE_SPK
                                    : BTHF_VOLUME_TYPE_MIC,
          p_data->val.num, &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_A_EVT:
      bt_hf_callbacks->AnswerCallCallback(&btif_hf_cb[idx].connected_bda);
      break;

    /* Java needs to send OK/ERROR for these commands */
    case BTA_AG_AT_BLDN_EVT:
    case BTA_AG_AT_D_EVT:
      bt_hf_callbacks->DialCallCallback(
          (event == BTA_AG_AT_D_EVT) ? p_data->val.str : (char*)"",
          &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_CHUP_EVT:
      bt_hf_callbacks->HangupCallCallback(&btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_CIND_EVT:
      bt_hf_callbacks->AtCindCallback(&btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_VTS_EVT:
      bt_hf_callbacks->DtmfCmdCallback(p_data->val.str[0],
                                       &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_BVRA_EVT:
      bt_hf_callbacks->VoiceRecognitionCallback((p_data->val.num == 1)
                                                    ? BTHF_VR_STATE_STARTED
                                                    : BTHF_VR_STATE_STOPPED,
                                                &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_NREC_EVT:
      bt_hf_callbacks->NoiseReductionCallback(
          (p_data->val.num == 1) ? BTHF_NREC_START : BTHF_NREC_STOP,
          &btif_hf_cb[idx].connected_bda);
      break;

    /* TODO: Add a callback for CBC */
    case BTA_AG_AT_CBC_EVT:
      break;

    case BTA_AG_AT_CKPD_EVT:
      bt_hf_callbacks->KeyPressedCallback(&btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_CODEC_EVT:
      log::verbose(
          "BTA_AG_CODEC_EVT Set codec status {} codec {} 1=CVSD 2=MSBC 4=LC3",
          p_data->val.hdr.status, p_data->val.num);
      if (p_data->val.num == BTM_SCO_CODEC_CVSD) {
        bt_hf_callbacks->WbsCallback(BTHF_WBS_NO,
                                     &btif_hf_cb[idx].connected_bda);
        bt_hf_callbacks->SwbCallback(BTHF_SWB_CODEC_LC3, BTHF_SWB_NO,
                                     &btif_hf_cb[idx].connected_bda);
      } else if (p_data->val.num == BTM_SCO_CODEC_MSBC) {
        bt_hf_callbacks->WbsCallback(BTHF_WBS_YES,
                                     &btif_hf_cb[idx].connected_bda);
        bt_hf_callbacks->SwbCallback(BTHF_SWB_CODEC_LC3, BTHF_SWB_NO,
                                     &btif_hf_cb[idx].connected_bda);
      } else if (p_data->val.num == BTM_SCO_CODEC_LC3) {
        bt_hf_callbacks->WbsCallback(BTHF_WBS_NO,
                                     &btif_hf_cb[idx].connected_bda);
        bt_hf_callbacks->SwbCallback(BTHF_SWB_CODEC_LC3, BTHF_SWB_YES,
                                     &btif_hf_cb[idx].connected_bda);
      } else {
        bt_hf_callbacks->WbsCallback(BTHF_WBS_NONE,
                                     &btif_hf_cb[idx].connected_bda);

        bthf_swb_codec_t codec = BTHF_SWB_CODEC_LC3;
        bthf_swb_config_t config = BTHF_SWB_NONE;

        if (is_hfp_aptx_voice_enabled()) {
          codec = BTHF_SWB_CODEC_VENDOR_APTX;

          log::verbose(
              "AG final selected SWB codec is 0x{:02x} 0=Q0 4=Q1 6=Q2 7=Q3",
              p_data->val.num);
          if (p_data->val.num == BTA_AG_SCO_APTX_SWB_SETTINGS_Q0 ||
              p_data->val.num == BTA_AG_SCO_APTX_SWB_SETTINGS_Q1 ||
              p_data->val.num == BTA_AG_SCO_APTX_SWB_SETTINGS_Q2 ||
              p_data->val.num == BTA_AG_SCO_APTX_SWB_SETTINGS_Q3) {
            config = BTHF_SWB_YES;
          } else {
            config = BTHF_SWB_NO;
          }
        }
        bt_hf_callbacks->SwbCallback(codec, config,
                                     &btif_hf_cb[idx].connected_bda);
      }
      break;

    /* Java needs to send OK/ERROR for these commands */
    case BTA_AG_AT_CHLD_EVT:
      bt_hf_callbacks->AtChldCallback((bthf_chld_type_t)atoi(p_data->val.str),
                                      &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_CLCC_EVT:
      bt_hf_callbacks->AtClccCallback(&btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_COPS_EVT:
      bt_hf_callbacks->AtCopsCallback(&btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_UNAT_EVT:
      bt_hf_callbacks->UnknownAtCallback(p_data->val.str,
                                         &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_CNUM_EVT:
      bt_hf_callbacks->AtCnumCallback(&btif_hf_cb[idx].connected_bda);
      break;

    /* TODO: Some of these commands may need to be sent to app. For now respond
     * with error */
    case BTA_AG_AT_BINP_EVT:
    case BTA_AG_AT_BTRH_EVT:
      send_at_result(BTA_AG_OK_ERROR, BTA_AG_ERR_OP_NOT_SUPPORTED, idx);
      break;
    case BTA_AG_AT_BAC_EVT:
      log::verbose("AG Bitmap of peer-codecs {}", p_data->val.num);
      /* If the peer supports mSBC and the BTIF preferred codec is also mSBC,
       * then we should set the BTA AG Codec to mSBC. This would trigger a +BCS
       * to mSBC at the time of SCO connection establishment */
      if (hfp_hal_interface::get_swb_supported() &&
          (p_data->val.num & BTM_SCO_CODEC_LC3)) {
        log::verbose("btif_hf override-Preferred Codec to LC3");
        BTA_AgSetCodec(btif_hf_cb[idx].handle, BTM_SCO_CODEC_LC3);
      } else if (hfp_hal_interface::get_wbs_supported() &&
                 (p_data->val.num & BTM_SCO_CODEC_MSBC)) {
        log::verbose("btif_hf override-Preferred Codec to mSBC");
        BTA_AgSetCodec(btif_hf_cb[idx].handle, BTM_SCO_CODEC_MSBC);
      } else {
        log::verbose("btif_hf override-Preferred Codec to CVSD");
        BTA_AgSetCodec(btif_hf_cb[idx].handle, BTM_SCO_CODEC_CVSD);
      }
      break;

    case BTA_AG_AT_BCS_EVT:
      log::verbose("AG final selected codec is 0x{:02x} 1=CVSD 2=MSBC",
                   p_data->val.num);
      /* No BTHF_WBS_NONE case, because HF1.6 supported device can send BCS */
      /* Only CVSD is considered narrow band speech */
      bt_hf_callbacks->WbsCallback(
          (p_data->val.num == BTM_SCO_CODEC_MSBC) ? BTHF_WBS_YES : BTHF_WBS_NO,
          &btif_hf_cb[idx].connected_bda);
      bt_hf_callbacks->SwbCallback(
          BTHF_SWB_CODEC_LC3,
          (p_data->val.num == BTM_SCO_CODEC_LC3) ? BTHF_SWB_YES : BTHF_SWB_NO,
          &btif_hf_cb[idx].connected_bda);
      break;

    case BTA_AG_AT_BIND_EVT:
      if (p_data->val.hdr.status == BTA_AG_SUCCESS) {
        bt_hf_callbacks->AtBindCallback(p_data->val.str,
                                        &btif_hf_cb[idx].connected_bda);
      }
      break;

    case BTA_AG_AT_BIEV_EVT:
      if (p_data->val.hdr.status == BTA_AG_SUCCESS) {
        bt_hf_callbacks->AtBievCallback((bthf_hf_ind_type_t)p_data->val.lidx,
                                        (int)p_data->val.num,
                                        &btif_hf_cb[idx].connected_bda);
      }
      break;
    case BTA_AG_AT_BIA_EVT:
      if (p_data->val.hdr.status == BTA_AG_SUCCESS) {
        uint32_t bia_mask_out = p_data->val.num;
        bool service = !is_nth_bit_enabled(bia_mask_out, BTA_AG_IND_SERVICE);
        bool roam = !is_nth_bit_enabled(bia_mask_out, BTA_AG_IND_ROAM);
        bool signal = !is_nth_bit_enabled(bia_mask_out, BTA_AG_IND_SIGNAL);
        bool battery = !is_nth_bit_enabled(bia_mask_out, BTA_AG_IND_BATTCHG);
        bt_hf_callbacks->AtBiaCallback(service, roam, signal, battery,
                                       &btif_hf_cb[idx].connected_bda);
      }
      break;

    case BTA_AG_AT_QCS_EVT:
      if (!is_hfp_aptx_voice_enabled()) {
        log::warn("unhandled event {}. Aptx codec is not enabled", event);
        break;
      }

      log::info("AG final selected SWB codec is {:#02x} 0=Q0 4=Q1 6=Q2 7=Q3",
                p_data->val.num);
      bt_hf_callbacks->SwbCallback(
          BTHF_SWB_CODEC_VENDOR_APTX,
          p_data->val.num <= BTA_AG_SCO_APTX_SWB_SETTINGS_Q3 ? BTHF_SWB_YES
                                                             : BTHF_SWB_NO,
          &btif_hf_cb[idx].connected_bda);
      break;

    default:
      log::warn("unhandled event {}", event);
      break;
  }
}

/*******************************************************************************
 *
 * Function         bte_hf_evt
 *
 * Description      Switches context from BTE to BTIF for all HF events
 *
 * Returns          void
 *
 ******************************************************************************/

static void bte_hf_evt(tBTA_AG_EVT event, tBTA_AG* p_data) {
  bt_status_t status;
  int param_len = 0;

  /* TODO: BTA sends the union members and not tBTA_AG. If using
   * param_len=sizeof(tBTA_AG), we get a crash on memcpy */
  if (BTA_AG_REGISTER_EVT == event)
    param_len = sizeof(tBTA_AG_REGISTER);
  else if (BTA_AG_OPEN_EVT == event)
    param_len = sizeof(tBTA_AG_OPEN);
  else if (BTA_AG_CONN_EVT == event)
    param_len = sizeof(tBTA_AG_CONN);
  else if ((BTA_AG_CLOSE_EVT == event) || (BTA_AG_AUDIO_OPEN_EVT == event) ||
           (BTA_AG_AUDIO_CLOSE_EVT == event))
    param_len = sizeof(tBTA_AG_HDR);
  else if (p_data)
    param_len = sizeof(tBTA_AG_VAL);

  /* switch context to btif task context (copy full union size for convenience)
   */
  status = btif_transfer_context(btif_hf_upstreams_evt, (uint16_t)event,
                                 (char*)p_data, param_len, nullptr);

  /* catch any failed context transfers */
  ASSERTC(status == BT_STATUS_SUCCESS, "context transfer failed", status);
}

/*******************************************************************************
 *
 * Function         connect
 *
 * Description     connect to headset
 *
 * Returns         bt_status_t
 *
 ******************************************************************************/
static bt_status_t connect_int(RawAddress* bd_addr, uint16_t uuid) {
  CHECK_BTHF_INIT();
  if (is_connected(bd_addr)) {
    log::warn("device {} is already connected",
              ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_DONE;
  }
  btif_hf_cb_t* hf_cb = nullptr;
  for (int i = 0; i < btif_max_hf_clients; i++) {
    if (btif_hf_cb[i].state == BTHF_CONNECTION_STATE_DISCONNECTED) {
      hf_cb = &btif_hf_cb[i];
      break;
    }
    // Due to btif queue implementation, when connect_int is called, no btif
    // control block should be in connecting state
    // Crash here to prevent future code changes from breaking this mechanism
    if (btif_hf_cb[i].state == BTHF_CONNECTION_STATE_CONNECTING) {
      log::fatal("{}, handle {}, is still in connecting state {}",
                 ADDRESS_TO_LOGGABLE_STR(btif_hf_cb[i].connected_bda),
                 btif_hf_cb[i].handle, btif_hf_cb[i].state);
    }
  }
  if (hf_cb == nullptr) {
    log::warn("Cannot connect {}: maximum {} clients already connected",
              ADDRESS_TO_LOGGABLE_CSTR(*bd_addr), btif_max_hf_clients);
    return BT_STATUS_BUSY;
  }
  hf_cb->state = BTHF_CONNECTION_STATE_CONNECTING;
  hf_cb->connected_bda = *bd_addr;
  hf_cb->is_initiator = true;
  hf_cb->peer_feat = 0;
  BTA_AgOpen(hf_cb->handle, hf_cb->connected_bda);

  DEVICE_IOT_CONFIG_ADDR_SET_INT(hf_cb->connected_bda, IOT_CONF_KEY_HFP_ROLE,
                                 IOT_CONF_VAL_HFP_ROLE_CLIENT);
  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(hf_cb->connected_bda,
                                     IOT_CONF_KEY_HFP_SLC_CONN_COUNT);
  return BT_STATUS_SUCCESS;
}

static void UpdateCallStates(btif_hf_cb_t* control_block, int num_active,
                             int num_held, bthf_call_state_t call_setup_state) {
  control_block->num_active = num_active;
  control_block->num_held = num_held;
  control_block->call_setup_state = call_setup_state;
}

/*******************************************************************************
 *
 * Function         btif_hf_is_call_idle
 *
 * Description      returns true if no call is in progress
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/
bool IsCallIdle() {
  if (!bt_hf_callbacks) return true;

  for (int i = 0; i < btif_max_hf_clients; ++i) {
    if ((btif_hf_cb[i].call_setup_state != BTHF_CALL_STATE_IDLE) ||
        ((btif_hf_cb[i].num_held + btif_hf_cb[i].num_active) > 0))
      return false;
  }

  return true;
}

class HeadsetInterface : Interface {
 public:
  static Interface* GetInstance() {
    static Interface* instance = new HeadsetInterface();
    return instance;
  }
  bt_status_t Init(Callbacks* callbacks, int max_hf_clients,
                   bool inband_ringing_enabled) override;
  bt_status_t Connect(RawAddress* bd_addr) override;
  bt_status_t Disconnect(RawAddress* bd_addr) override;
  bt_status_t ConnectAudio(RawAddress* bd_addr, int disabled_codecs) override;
  bt_status_t DisconnectAudio(RawAddress* bd_addr) override;
  bt_status_t isNoiseReductionSupported(RawAddress* bd_addr) override;
  bt_status_t isVoiceRecognitionSupported(RawAddress* bd_addr) override;
  bt_status_t StartVoiceRecognition(RawAddress* bd_addr) override;
  bt_status_t StopVoiceRecognition(RawAddress* bd_addr) override;
  bt_status_t VolumeControl(bthf_volume_type_t type, int volume,
                            RawAddress* bd_addr) override;
  bt_status_t DeviceStatusNotification(bthf_network_state_t ntk_state,
                                       bthf_service_type_t svc_type, int signal,
                                       int batt_chg,
                                       RawAddress* bd_addr) override;
  bt_status_t CopsResponse(const char* cops, RawAddress* bd_addr) override;
  bt_status_t CindResponse(int svc, int num_active, int num_held,
                           bthf_call_state_t call_setup_state, int signal,
                           int roam, int batt_chg,
                           RawAddress* bd_addr) override;
  bt_status_t FormattedAtResponse(const char* rsp,
                                  RawAddress* bd_addr) override;
  bt_status_t AtResponse(bthf_at_response_t response_code, int error_code,
                         RawAddress* bd_addr) override;
  bt_status_t ClccResponse(int index, bthf_call_direction_t dir,
                           bthf_call_state_t state, bthf_call_mode_t mode,
                           bthf_call_mpty_type_t mpty, const char* number,
                           bthf_call_addrtype_t type,
                           RawAddress* bd_addr) override;
  bt_status_t PhoneStateChange(int num_active, int num_held,
                               bthf_call_state_t call_setup_state,
                               const char* number, bthf_call_addrtype_t type,
                               const char* name, RawAddress* bd_addr) override;

  void Cleanup() override;
  bt_status_t SetScoOffloadEnabled(bool value) override;
  bt_status_t SetScoAllowed(bool value) override;
  bt_status_t SendBsir(bool value, RawAddress* bd_addr) override;
  bt_status_t SetActiveDevice(RawAddress* active_device_addr) override;
  bt_status_t DebugDump() override;
};

bt_status_t HeadsetInterface::Init(Callbacks* callbacks, int max_hf_clients,
                                   bool inband_ringing_enabled) {
  if (inband_ringing_enabled) {
    btif_hf_features |= BTA_AG_FEAT_INBAND;
  } else {
    btif_hf_features &= ~BTA_AG_FEAT_INBAND;
  }
  CHECK_LE(max_hf_clients, BTA_AG_MAX_NUM_CLIENTS)
      << __func__
      << "Too many HF clients,"
         " maximum is "
      << BTA_AG_MAX_NUM_CLIENTS << " was given " << max_hf_clients;
  btif_max_hf_clients = max_hf_clients;
  log::verbose(
      "btif_hf_features={}, max_hf_clients={}, inband_ringing_enabled={}",
      btif_hf_features, btif_max_hf_clients, inband_ringing_enabled);
  bt_hf_callbacks = callbacks;
  for (btif_hf_cb_t& hf_cb : btif_hf_cb) {
    reset_control_block(&hf_cb);
  }

// Invoke the enable service API to the core to set the appropriate service_id
// Internally, the HSP_SERVICE_ID shall also be enabled if HFP is enabled
// (phone) otherwise only HSP is enabled (tablet)
  if (get_BTIF_HF_SERVICES() & BTA_HFP_SERVICE_MASK) {
    btif_enable_service(BTA_HFP_SERVICE_ID);
  } else {
    btif_enable_service(BTA_HSP_SERVICE_ID);
  }

  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::Connect(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  return btif_queue_connect(UUID_SERVCLASS_AG_HANDSFREE, bd_addr, connect_int);
}

bt_status_t HeadsetInterface::Disconnect(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  BTA_AgClose(btif_hf_cb[idx].handle);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::ConnectAudio(RawAddress* bd_addr,
                                           int disabled_codecs) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  /* Check if SLC is connected */
  if (!IsSlcConnected(bd_addr)) {
    log::error("SLC not connected for {}", ADDRESS_TO_LOGGABLE_STR(*bd_addr));
    return BT_STATUS_NOT_READY;
  }
  do_in_jni_thread(base::BindOnce(&Callbacks::AudioStateCallback,
                                  // Manual pointer management for now
                                  base::Unretained(bt_hf_callbacks),
                                  BTHF_AUDIO_STATE_CONNECTING,
                                  &btif_hf_cb[idx].connected_bda));
  BTA_AgAudioOpen(btif_hf_cb[idx].handle, disabled_codecs);

  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(*bd_addr, IOT_CONF_KEY_HFP_SCO_CONN_COUNT);

  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::DisconnectAudio(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  BTA_AgAudioClose(btif_hf_cb[idx].handle);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::isNoiseReductionSupported(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!(btif_hf_cb[idx].peer_feat & BTA_AG_PEER_FEAT_ECNR)) {
    return BT_STATUS_UNSUPPORTED;
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::isVoiceRecognitionSupported(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!(btif_hf_cb[idx].peer_feat & BTA_AG_PEER_FEAT_VREC)) {
    return BT_STATUS_UNSUPPORTED;
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::StartVoiceRecognition(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_NOT_READY;
  }
  if (!(btif_hf_cb[idx].peer_feat & BTA_AG_PEER_FEAT_VREC)) {
    log::error("voice recognition not supported, features=0x{:x}",
               btif_hf_cb[idx].peer_feat);
    return BT_STATUS_UNSUPPORTED;
  }
  tBTA_AG_RES_DATA ag_res = {};
  ag_res.state = true;
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_BVRA_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::StopVoiceRecognition(RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);

  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_NOT_READY;
  }
  if (!(btif_hf_cb[idx].peer_feat & BTA_AG_PEER_FEAT_VREC)) {
    log::error("voice recognition not supported, features=0x{:x}",
               btif_hf_cb[idx].peer_feat);
    return BT_STATUS_UNSUPPORTED;
  }
  tBTA_AG_RES_DATA ag_res = {};
  ag_res.state = false;
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_BVRA_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::VolumeControl(bthf_volume_type_t type, int volume,
                                            RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  tBTA_AG_RES_DATA ag_res = {};
  ag_res.num = static_cast<uint16_t>(volume);
  BTA_AgResult(btif_hf_cb[idx].handle,
               (type == BTHF_VOLUME_TYPE_SPK) ? BTA_AG_SPK_RES : BTA_AG_MIC_RES,
               ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::DeviceStatusNotification(
    bthf_network_state_t ntk_state, bthf_service_type_t svc_type, int signal,
    int batt_chg, RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  if (!bd_addr) {
    log::warn("bd_addr is null");
    return BT_STATUS_FAIL;
  }
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if (idx < 0 || idx > BTA_AG_MAX_NUM_CLIENTS) {
    log::warn("invalid index {} for {}", idx,
              ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  const btif_hf_cb_t& control_block = btif_hf_cb[idx];
  // ok if no device is connected
  if (is_connected(nullptr)) {
    // send all indicators to BTA.
    // BTA will make sure no duplicates are sent out
    send_indicator_update(control_block, BTA_AG_IND_SERVICE,
                          (ntk_state == BTHF_NETWORK_STATE_AVAILABLE) ? 1 : 0);
    send_indicator_update(control_block, BTA_AG_IND_ROAM,
                          (svc_type == BTHF_SERVICE_TYPE_HOME) ? 0 : 1);
    send_indicator_update(control_block, BTA_AG_IND_SIGNAL, signal);
    send_indicator_update(control_block, BTA_AG_IND_BATTCHG, batt_chg);
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::CopsResponse(const char* cops,
                                           RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  tBTA_AG_RES_DATA ag_res = {};
  /* Format the response */
  snprintf(ag_res.str, sizeof(ag_res.str), "0,0,\"%.16s\"", cops);
  ag_res.ok_flag = BTA_AG_OK_DONE;
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_COPS_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::CindResponse(int svc, int num_active,
                                           int num_held,
                                           bthf_call_state_t call_setup_state,
                                           int signal, int roam, int batt_chg,
                                           RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  tBTA_AG_RES_DATA ag_res = {};
  // per the errata 2043, call=1 implies atleast one call is in progress
  // (active/held), see:
  // https://www.bluetooth.org/errata/errata_view.cfm?errata_id=2043
  snprintf(ag_res.str, sizeof(ag_res.str), "%d,%d,%d,%d,%d,%d,%d",
           (num_active + num_held) ? 1 : 0,          /* Call state */
           callstate_to_callsetup(call_setup_state), /* Callsetup state */
           svc,                                      /* network service */
           signal,                                   /* Signal strength */
           roam,                                     /* Roaming indicator */
           batt_chg,                                 /* Battery level */
           ((num_held == 0) ? 0 : ((num_active == 0) ? 2 : 1))); /* Call held */
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_CIND_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::FormattedAtResponse(const char* rsp,
                                                  RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  tBTA_AG_RES_DATA ag_res = {};
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  /* Format the response and send */
  strncpy(ag_res.str, rsp, BTA_AG_AT_MAX_LEN);
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_UNAT_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::AtResponse(bthf_at_response_t response_code,
                                         int error_code, RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  send_at_result(
      (response_code == BTHF_AT_RESPONSE_OK) ? BTA_AG_OK_DONE : BTA_AG_OK_ERROR,
      static_cast<uint16_t>(error_code), idx);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::ClccResponse(
    int index, bthf_call_direction_t dir, bthf_call_state_t state,
    bthf_call_mode_t mode, bthf_call_mpty_type_t mpty, const char* number,
    bthf_call_addrtype_t type, RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {}", idx);
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} is not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  tBTA_AG_RES_DATA ag_res = {};
  /* Format the response */
  if (index == 0) {
    ag_res.ok_flag = BTA_AG_OK_DONE;
  } else {
    std::string cell_number(number ? number : "");
    log::verbose(
        "clcc_response: [{}] dir {} state {} mode {} number = {} type = {}",
        index, dir, state, mode, PRIVATE_CELL(cell_number), type);
    int res_strlen = snprintf(ag_res.str, sizeof(ag_res.str), "%d,%d,%d,%d,%d",
                              index, dir, state, mode, mpty);
    if (number) {
      size_t rem_bytes = sizeof(ag_res.str) - res_strlen;
      char dialnum[sizeof(ag_res.str)];
      size_t newidx = 0;
      if (type == BTHF_CALL_ADDRTYPE_INTERNATIONAL && *number != '+') {
        dialnum[newidx++] = '+';
      }
      for (size_t i = 0; number[i] != 0; i++) {
        if (newidx >= (sizeof(dialnum) - res_strlen - 1)) {
          break;
        }
        if (utl_isdialchar(number[i])) {
          dialnum[newidx++] = number[i];
        }
      }
      dialnum[newidx] = 0;
      // Reserve 5 bytes for ["][,][3_digit_type]
      snprintf(&ag_res.str[res_strlen], rem_bytes - 5, ",\"%s", dialnum);
      std::stringstream remaining_string;
      remaining_string << "\"," << type;
      strncat(&ag_res.str[res_strlen], remaining_string.str().c_str(), 5);
    }
  }
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_CLCC_RES, ag_res);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::PhoneStateChange(
    int num_active, int num_held, bthf_call_state_t call_setup_state,
    const char* number, bthf_call_addrtype_t type, const char* name,
    RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  if (bd_addr == nullptr) {
    log::warn("bd_addr is null");
    return BT_STATUS_FAIL;
  }

  const RawAddress raw_address(*bd_addr);
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if (idx < 0 || idx >= BTA_AG_MAX_NUM_CLIENTS) {
    log::warn("Invalid index {} for {}", idx,
              ADDRESS_TO_LOGGABLE_CSTR(raw_address));
    return BT_STATUS_FAIL;
  }

  const btif_hf_cb_t& control_block = btif_hf_cb[idx];
  if (!IsSlcConnected(bd_addr)) {
    log::warn("SLC not connected for {}", ADDRESS_TO_LOGGABLE_STR(*bd_addr));
    return BT_STATUS_NOT_READY;
  }
  if (call_setup_state == BTHF_CALL_STATE_DISCONNECTED) {
    // HFP spec does not handle cases when a call is being disconnected.
    // Since DISCONNECTED state must lead to IDLE state, ignoring it here.s
    log::info(
        "Ignore call state change to DISCONNECTED, idx={}, addr={}, "
        "num_active={}, num_held={}",
        idx, ADDRESS_TO_LOGGABLE_STR(*bd_addr), num_active, num_held);
    return BT_STATUS_SUCCESS;
  }
  log::debug(
      "bd_addr:{} active_bda:{} num_active:{} prev_num_active:{} num_held:{} "
      "prev_num_held:{} call_state:{} prev_call_state:{}",
      ADDRESS_TO_LOGGABLE_CSTR((*bd_addr)),
      ADDRESS_TO_LOGGABLE_CSTR(active_bda), num_active,
      control_block.num_active, num_held, control_block.num_held,
      dump_hf_call_state(call_setup_state),
      dump_hf_call_state(control_block.call_setup_state));
  tBTA_AG_RES res = BTA_AG_UNKNOWN;
  bt_status_t status = BT_STATUS_SUCCESS;
  bool active_call_updated = false;

  /* if all indicators are 0, send end call and return */
  if (num_active == 0 && num_held == 0 &&
      call_setup_state == BTHF_CALL_STATE_IDLE) {
    if (control_block.num_active > 0) {
      BTM_LogHistory(kBtmLogTag, raw_address, "Call Ended");
    }
    BTA_AgResult(control_block.handle, BTA_AG_END_CALL_RES,
                 tBTA_AG_RES_DATA::kEmpty);
    /* if held call was present, reset that as well */
    if (control_block.num_held) {
      send_indicator_update(control_block, BTA_AG_IND_CALLHELD, 0);
    }
    UpdateCallStates(&btif_hf_cb[idx], num_active, num_held, call_setup_state);
    return status;
  }

  /* active state can change when:
  ** 1. an outgoing/incoming call was answered
  ** 2. an held was resumed
  ** 3. without callsetup notifications, call became active
  ** (3) can happen if call is active and a headset connects to us
  **
  ** In the case of (3), we will have to notify the stack of an active
  ** call, instead of sending an indicator update. This will also
  ** force the SCO to be setup. Handle this special case here prior to
  ** call setup handling
  */
  if (((num_active + num_held) > 0) && (control_block.num_active == 0) &&
      (control_block.num_held == 0) &&
      (control_block.call_setup_state == BTHF_CALL_STATE_IDLE)) {
    tBTA_AG_RES_DATA ag_res = {};
    log::verbose(
        "Active/Held call notification received without call setup update");

    ag_res.audio_handle = BTA_AG_HANDLE_SCO_NO_CHANGE;
    // Addition call setup with the Active call
    // CIND response should have been updated.
    // just open SCO connection.
    if (call_setup_state != BTHF_CALL_STATE_IDLE) {
      res = BTA_AG_MULTI_CALL_RES;
    } else {
      res = BTA_AG_OUT_CALL_CONN_RES;
    }
    BTA_AgResult(control_block.handle, res, ag_res);
    active_call_updated = true;
  }

  /* Ringing call changed? */
  if (call_setup_state != control_block.call_setup_state) {
    tBTA_AG_RES_DATA ag_res = {};
    ag_res.audio_handle = BTA_AG_HANDLE_SCO_NO_CHANGE;
    log::verbose("Call setup states changed. old: {} new: {}",
                 dump_hf_call_state(control_block.call_setup_state),
                 dump_hf_call_state(call_setup_state));
    switch (call_setup_state) {
      case BTHF_CALL_STATE_IDLE: {
        switch (control_block.call_setup_state) {
          case BTHF_CALL_STATE_INCOMING:
            if (num_active > control_block.num_active) {
              res = BTA_AG_IN_CALL_CONN_RES;
              if (is_active_device(*bd_addr)) {
                ag_res.audio_handle = control_block.handle;
              }
            } else if (num_held > control_block.num_held)
              res = BTA_AG_IN_CALL_HELD_RES;
            else
              res = BTA_AG_CALL_CANCEL_RES;
            break;
          case BTHF_CALL_STATE_DIALING:
          case BTHF_CALL_STATE_ALERTING:
            if (num_active > control_block.num_active) {
              res = BTA_AG_OUT_CALL_CONN_RES;
            } else
              res = BTA_AG_CALL_CANCEL_RES;
            break;
          default:
            log::error("Incorrect call state prev={}, now={}",
                       control_block.call_setup_state, call_setup_state);
            status = BT_STATUS_PARM_INVALID;
            break;
        }
      } break;

      case BTHF_CALL_STATE_INCOMING:
        if (num_active || num_held) {
          res = BTA_AG_CALL_WAIT_RES;
        } else {
          res = BTA_AG_IN_CALL_RES;
          if (is_active_device(*bd_addr)) {
            ag_res.audio_handle = control_block.handle;
          }
        }
        if (number) {
          std::ostringstream call_number_stream;
          if ((type == BTHF_CALL_ADDRTYPE_INTERNATIONAL) && (*number != '+')) {
            call_number_stream << "\"+";
          } else {
            call_number_stream << "\"";
          }

          std::string name_str;
          if (name) {
            name_str.append(name);
          }
          std::string number_str(number);
          // 13 = ["][+]["][,][3_digit_type][,,,]["]["][null_terminator]
          int overflow_size =
              13 + static_cast<int>(number_str.length() + name_str.length()) -
              static_cast<int>(sizeof(ag_res.str));
          if (overflow_size > 0) {
            int extra_overflow_size =
                overflow_size - static_cast<int>(name_str.length());
            if (extra_overflow_size > 0) {
              number_str.resize(number_str.length() - extra_overflow_size);
              name_str.clear();
            } else {
              name_str.resize(name_str.length() - overflow_size);
            }
          }
          call_number_stream << number_str << "\"";

          // Store caller id string and append type info.
          // Make sure type info is valid, otherwise add 129 as default type
          ag_res.num = static_cast<uint16_t>(type);
          if ((ag_res.num < BTA_AG_CLIP_TYPE_MIN) ||
              (ag_res.num > BTA_AG_CLIP_TYPE_MAX)) {
            if (ag_res.num != BTA_AG_CLIP_TYPE_VOIP) {
              ag_res.num = BTA_AG_CLIP_TYPE_DEFAULT;
            }
          }

          if (res == BTA_AG_CALL_WAIT_RES || name_str.empty()) {
            call_number_stream << "," << std::to_string(ag_res.num);
          } else {
            call_number_stream << "," << std::to_string(ag_res.num) << ",,,\""
                               << name_str << "\"";
          }
          snprintf(ag_res.str, sizeof(ag_res.str), "%s",
                   call_number_stream.str().c_str());
        }
        {
          std::string cell_number(number);
          BTM_LogHistory(
              kBtmLogTag, raw_address, "Call Incoming",
              base::StringPrintf("number:%s", PRIVATE_CELL(cell_number)));
        }
        // base::StringPrintf("number:%s", PRIVATE_CELL(number)));
        break;
      case BTHF_CALL_STATE_DIALING:
        if (!(num_active + num_held) && is_active_device(*bd_addr)) {
          ag_res.audio_handle = control_block.handle;
        }
        res = BTA_AG_OUT_CALL_ORIG_RES;
        break;
      case BTHF_CALL_STATE_ALERTING:
        /* if we went from idle->alert, force SCO setup here. dialing usually
         * triggers it */
        if ((control_block.call_setup_state == BTHF_CALL_STATE_IDLE) &&
            !(num_active + num_held) && is_active_device(*bd_addr)) {
          ag_res.audio_handle = control_block.handle;
        }
        res = BTA_AG_OUT_CALL_ALERT_RES;
        break;
      default:
        log::error("Incorrect call state prev={}, now={}",
                   control_block.call_setup_state, call_setup_state);
        status = BT_STATUS_PARM_INVALID;
        break;
    }
    log::verbose("Call setup state changed. res={}, audio_handle={}", res,
                 ag_res.audio_handle);

    if (res != 0xFF) {
      BTA_AgResult(control_block.handle, res, ag_res);
    }

    /* if call setup is idle, we have already updated call indicator, jump out
     */
    if (call_setup_state == BTHF_CALL_STATE_IDLE) {
      /* check & update callheld */
      if ((num_held > 0) && (num_active > 0)) {
        send_indicator_update(control_block, BTA_AG_IND_CALLHELD, 1);
      }
      UpdateCallStates(&btif_hf_cb[idx], num_active, num_held,
                       call_setup_state);
      return status;
    }
  }

  /**
   * Handle call indicator change
   *
   * Per the errata 2043, call=1 implies at least one call is in progress
   * (active or held)
   * See: https://www.bluetooth.org/errata/errata_view.cfm?errata_id=2043
   *
   **/
  if (!active_call_updated &&
      ((num_active + num_held) !=
       (control_block.num_active + control_block.num_held))) {
    log::verbose(
        "in progress call states changed, active=[{}->{}], held=[{}->{}]",
        control_block.num_active, num_active, control_block.num_held, num_held);
    send_indicator_update(control_block, BTA_AG_IND_CALL,
                          ((num_active + num_held) > 0) ? BTA_AG_CALL_ACTIVE
                                                        : BTA_AG_CALL_INACTIVE);
  }

  /* Held Changed? */
  if (num_held != control_block.num_held ||
      ((num_active == 0) && ((num_held + control_block.num_held) > 1))) {
    log::verbose("Held call states changed. old: {} new: {}",
                 control_block.num_held, num_held);
    send_indicator_update(control_block, BTA_AG_IND_CALLHELD,
                          ((num_held == 0) ? 0 : ((num_active == 0) ? 2 : 1)));
  }

  /* Calls Swapped? */
  if ((call_setup_state == control_block.call_setup_state) &&
      (num_active && num_held) && (num_active == control_block.num_active) &&
      (num_held == control_block.num_held)) {
    log::verbose("Calls swapped");
    send_indicator_update(control_block, BTA_AG_IND_CALLHELD, 1);
  }

  /* When call is hung up and still there is another call is in active,
   * some of the HF cannot acquire the call states by its own. If HF try
   * to terminate a call, it may not send the command AT+CHUP because the
   * call states are not updated properly. HF should get informed the call
   * status forcibly.
   */
  if ((control_block.num_active == num_active && num_active != 0) &&
      (control_block.num_held != num_held && num_held == 0)) {
    tBTA_AG_RES_DATA ag_res = {};
    ag_res.ind.id = BTA_AG_IND_CALL;
    ag_res.ind.value = num_active;
    BTA_AgResult(control_block.handle, BTA_AG_IND_RES_ON_DEMAND, ag_res);
  }

  UpdateCallStates(&btif_hf_cb[idx], num_active, num_held, call_setup_state);

  DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(btif_hf_cb[idx].connected_bda,
                                     IOT_CONF_KEY_HFP_SCO_CONN_COUNT);

  return status;
}

void HeadsetInterface::Cleanup() {
  log::verbose("");

  btif_queue_cleanup(UUID_SERVCLASS_AG_HANDSFREE);

  tBTA_SERVICE_MASK mask = btif_get_enabled_services_mask();
  if (get_BTIF_HF_SERVICES() & BTA_HFP_SERVICE_MASK) {
    if ((mask & (1 << BTA_HFP_SERVICE_ID)) != 0) {
      btif_disable_service(BTA_HFP_SERVICE_ID);
    }
  } else {
    if ((mask & (1 << BTA_HSP_SERVICE_ID)) != 0) {
      btif_disable_service(BTA_HSP_SERVICE_ID);
    }
  }

  do_in_jni_thread(FROM_HERE,
                   base::BindOnce([]() { bt_hf_callbacks = nullptr; }));
}

bt_status_t HeadsetInterface::SetScoOffloadEnabled(bool value) {
  CHECK_BTHF_INIT();
  BTA_AgSetScoOffloadEnabled(value);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::SetScoAllowed(bool value) {
  CHECK_BTHF_INIT();
  BTA_AgSetScoAllowed(value);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::SendBsir(bool value, RawAddress* bd_addr) {
  CHECK_BTHF_INIT();
  int idx = btif_hf_idx_by_bdaddr(bd_addr);
  if ((idx < 0) || (idx >= BTA_AG_MAX_NUM_CLIENTS)) {
    log::error("Invalid index {} for {}", idx,
               ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  if (!is_connected(bd_addr)) {
    log::error("{} not connected", ADDRESS_TO_LOGGABLE_CSTR(*bd_addr));
    return BT_STATUS_FAIL;
  }
  tBTA_AG_RES_DATA ag_result = {};
  ag_result.state = value;
  BTA_AgResult(btif_hf_cb[idx].handle, BTA_AG_INBAND_RING_RES, ag_result);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::SetActiveDevice(RawAddress* active_device_addr) {
  CHECK_BTHF_INIT();
  active_bda = *active_device_addr;
  BTA_AgSetActiveDevice(*active_device_addr);
  return BT_STATUS_SUCCESS;
}

bt_status_t HeadsetInterface::DebugDump() {
  CHECK_BTHF_INIT();
  tBTM_SCO_DEBUG_DUMP debug_dump = BTM_GetScoDebugDump();
  bt_hf_callbacks->DebugDumpCallback(
      debug_dump.is_active, debug_dump.codec_id,
      debug_dump.total_num_decoded_frames, debug_dump.pkt_loss_ratio,
      debug_dump.latest_data.begin_ts_raw_us,
      debug_dump.latest_data.end_ts_raw_us,
      debug_dump.latest_data.status_in_hex.c_str(),
      debug_dump.latest_data.status_in_binary.c_str());
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_hf_execute_service
 *
 * Description      Initializes/Shuts down the service
 *
 * Returns          BT_STATUS_SUCCESS on success, BT_STATUS_FAIL otherwise
 *
 ******************************************************************************/
bt_status_t ExecuteService(bool b_enable) {
  log::info("service starts to: {}", b_enable ? "Initialize" : "Shutdown");
  const char* service_names_raw[] = BTIF_HF_SERVICE_NAMES;
  std::vector<std::string> service_names;
  for (const char* service_name_raw : service_names_raw) {
    if (service_name_raw) {
      service_names.emplace_back(service_name_raw);
    }
  }
  if (b_enable) {
    /* Enable and register with BTA-AG */
    BTA_AgEnable(bte_hf_evt);
    for (uint8_t app_id = 0; app_id < btif_max_hf_clients; app_id++) {
      BTA_AgRegister(get_BTIF_HF_SERVICES(), btif_hf_features, service_names,
                     app_id);
    }
  } else {
    /* De-register AG */
    for (int i = 0; i < btif_max_hf_clients; i++) {
      BTA_AgDeregister(btif_hf_cb[i].handle);
    }
    /* Disable AG */
    BTA_AgDisable();
  }
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         btif_hf_get_interface
 *
 * Description      Get the hf callback interface
 *
 * Returns          bthf_interface_t
 *
 ******************************************************************************/
Interface* GetInterface() {
  log::verbose("");
  return HeadsetInterface::GetInstance();
}

}  // namespace headset
}  // namespace bluetooth
