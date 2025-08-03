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

#define LOG_TAG "smp_act"

#include <android_bluetooth_flags.h>
#include <bluetooth/log.h>

#include <cstring>

#include "btif/include/btif_common.h"
#include "btif/include/core_callbacks.h"
#include "btif/include/stack_manager_t.h"
#include "crypto_toolbox/crypto_toolbox.h"
#include "device/include/interop.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "p_256_ecc_pp.h"
#include "smp_int.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/smp_api_types.h"
#include "types/raw_address.h"

using namespace bluetooth;

namespace {
constexpr char kBtmLogTag[] = "SMP";
}

static void smp_key_distribution_by_transport(tSMP_CB* p_cb,
                                              tSMP_INT_DATA* p_data);

#define SMP_KEY_DIST_TYPE_MAX 4

const tSMP_ACT smp_distribute_act[] = {
    smp_generate_ltk,       /* SMP_SEC_KEY_TYPE_ENC - '1' bit index */
    smp_send_id_info,       /* SMP_SEC_KEY_TYPE_ID - '1' bit index */
    smp_generate_csrk,      /* SMP_SEC_KEY_TYPE_CSRK - '1' bit index */
    smp_set_derive_link_key /* SMP_SEC_KEY_TYPE_LK - '1' bit index */
};

static bool pts_test_send_authentication_complete_failure(tSMP_CB* p_cb) {
  tSMP_STATUS reason = p_cb->cert_failure;
  if (reason == SMP_PAIR_AUTH_FAIL || reason == SMP_PAIR_FAIL_UNKNOWN ||
      reason == SMP_PAIR_NOT_SUPPORT || reason == SMP_PASSKEY_ENTRY_FAIL ||
      reason == SMP_REPEATED_ATTEMPTS) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = reason;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return true;
  }
  return false;
}

/*******************************************************************************
 * Function         smp_update_key_mask
 * Description      This function updates the key mask for sending or receiving.
 ******************************************************************************/
static void smp_update_key_mask(tSMP_CB* p_cb, uint8_t key_type, bool recv) {
  log::verbose(
      "before update role={} recv={} local_i_key=0x{:02x}, "
      "local_r_key=0x{:02x}",
      p_cb->role, recv, p_cb->local_i_key, p_cb->local_r_key);

  if (((p_cb->sc_mode_required_by_peer) || (p_cb->smp_over_br)) &&
      ((key_type == SMP_SEC_KEY_TYPE_ENC) ||
       (key_type == SMP_SEC_KEY_TYPE_LK))) {
    /* in LE SC mode LTK, CSRK and BR/EDR LK are derived locally instead of
    ** being exchanged with the peer */
    p_cb->local_i_key &= ~key_type;
    p_cb->local_r_key &= ~key_type;
  } else if (p_cb->role == HCI_ROLE_PERIPHERAL) {
    if (recv)
      p_cb->local_i_key &= ~key_type;
    else
      p_cb->local_r_key &= ~key_type;
  } else {
    if (recv)
      p_cb->local_r_key &= ~key_type;
    else
      p_cb->local_i_key &= ~key_type;
  }

  log::verbose("updated local_i_key=0x{:02x}, local_r_key=0x{:02x}",
               p_cb->local_i_key, p_cb->local_r_key);
}

/*******************************************************************************
 * Function     smp_send_app_cback
 * Description  notifies application about the events the application is
 *              interested in
 ******************************************************************************/
void smp_send_app_cback(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tSMP_EVT_DATA cb_data;
  tBTM_STATUS callback_rc;
  uint8_t remote_lmp_version = 0;

  log::debug("addr:{} event:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda),
             smp_evt_to_text(p_cb->cb_evt));

  if (p_cb->p_callback && p_cb->cb_evt != 0) {
    switch (p_cb->cb_evt) {
      case SMP_IO_CAP_REQ_EVT:
        cb_data.io_req.auth_req = p_cb->peer_auth_req;
        cb_data.io_req.oob_data = SMP_OOB_NONE;
        cb_data.io_req.io_cap = SMP_IO_CAP_KBDISP;
        cb_data.io_req.max_key_size = SMP_MAX_ENC_KEY_SIZE;
        cb_data.io_req.init_keys = p_cb->local_i_key;
        cb_data.io_req.resp_keys = p_cb->local_r_key;
        log::debug("Notify app io_cap={}", cb_data.io_req.io_cap);
        break;

      case SMP_NC_REQ_EVT:
        cb_data.passkey = p_data->passkey;
        break;
      case SMP_SC_OOB_REQ_EVT:
        cb_data.req_oob_type = p_data->req_oob_type;
        break;
      case SMP_SC_LOC_OOB_DATA_UP_EVT:
        cb_data.loc_oob_data = p_cb->sc_oob_data.loc_oob_data;
        break;

      case SMP_BR_KEYS_REQ_EVT:
        cb_data.io_req.auth_req = 0;
        cb_data.io_req.oob_data = SMP_OOB_NONE;
        cb_data.io_req.io_cap = 0;
        cb_data.io_req.max_key_size = SMP_MAX_ENC_KEY_SIZE;
        cb_data.io_req.init_keys = SMP_BR_SEC_DEFAULT_KEY;
        cb_data.io_req.resp_keys = SMP_BR_SEC_DEFAULT_KEY;
        break;

      case SMP_LE_ADDR_ASSOC_EVT:
        cb_data.id_addr = p_cb->id_addr;
        break;

      default:
        log::error("Unexpected event:{}", p_cb->cb_evt);
        break;
    }

    callback_rc =
        (*p_cb->p_callback)(p_cb->cb_evt, p_cb->pairing_bda, &cb_data);

    if (callback_rc == BTM_SUCCESS) {
      switch (p_cb->cb_evt) {
        case SMP_IO_CAP_REQ_EVT:
          p_cb->loc_auth_req = cb_data.io_req.auth_req;
          p_cb->local_io_capability = cb_data.io_req.io_cap;
          p_cb->loc_oob_flag = cb_data.io_req.oob_data;
          p_cb->loc_enc_size = cb_data.io_req.max_key_size;
          p_cb->local_i_key = cb_data.io_req.init_keys;
          p_cb->local_r_key = cb_data.io_req.resp_keys;

          if (!(p_cb->loc_auth_req & SMP_AUTH_BOND)) {
            log::debug("Non bonding: No keys will be exchanged");
            p_cb->local_i_key = 0;
            p_cb->local_r_key = 0;
          }

          log::debug(
              "Remote request IO capabilities precondition "
              "auth_req:0x{:02x},io_cap:{} loc_oob_flag:{} loc_enc_size:{}, "
              "local_i_key:0x{:02x}, local_r_key:0x{:02x}",
              p_cb->loc_auth_req, p_cb->local_io_capability, p_cb->loc_oob_flag,
              p_cb->loc_enc_size, p_cb->local_i_key, p_cb->local_r_key);

          p_cb->sc_only_mode_locally_required =
              (p_cb->init_security_mode == BTM_SEC_MODE_SC) ? true : false;
          /* just for PTS, force SC bit */
          if (p_cb->sc_only_mode_locally_required) {
            p_cb->loc_auth_req |= SMP_SC_SUPPORT_BIT;
          }

          if (!BTM_ReadRemoteVersion(p_cb->pairing_bda, &remote_lmp_version,
                                     nullptr, nullptr)) {
            log::warn("SMP Unable to determine remote_lmp_version:{}",
                      remote_lmp_version);
          }

          if (!p_cb->sc_only_mode_locally_required &&
              (!(p_cb->loc_auth_req & SMP_SC_SUPPORT_BIT) ||
               (remote_lmp_version &&
                remote_lmp_version < HCI_PROTO_VERSION_4_2) ||
               interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS,
                                  (const RawAddress*)&p_cb->pairing_bda))) {
            log::debug(
                "Setting SC, H7 and LinkKey bits to false to support legacy "
                "device with lmp version:{}",
                remote_lmp_version);
            p_cb->loc_auth_req &= ~SMP_SC_SUPPORT_BIT;
            p_cb->loc_auth_req &= ~SMP_KP_SUPPORT_BIT;
            p_cb->local_i_key &= ~SMP_SEC_KEY_TYPE_LK;
            p_cb->local_r_key &= ~SMP_SEC_KEY_TYPE_LK;
          }

          if (remote_lmp_version &&
              remote_lmp_version < HCI_PROTO_VERSION_5_0) {
            p_cb->loc_auth_req &= ~SMP_H7_SUPPORT_BIT;
          }

          log::debug(
              "Remote request IO capabilities postcondition "
              "auth_req:0x{:02x},local_i_key:0x{:02x}, local_r_key:0x{:02x}",
              p_cb->loc_auth_req, p_cb->local_i_key, p_cb->local_r_key);

          smp_sm_event(p_cb, SMP_IO_RSP_EVT, NULL);
          break;

        case SMP_BR_KEYS_REQ_EVT:
          p_cb->loc_enc_size = cb_data.io_req.max_key_size;
          p_cb->local_i_key = cb_data.io_req.init_keys;
          p_cb->local_r_key = cb_data.io_req.resp_keys;
          p_cb->loc_auth_req |= SMP_H7_SUPPORT_BIT;

          p_cb->local_i_key &= ~SMP_SEC_KEY_TYPE_LK;
          p_cb->local_r_key &= ~SMP_SEC_KEY_TYPE_LK;

          log::debug(
              "for SMP over BR max_key_size:0x{:02x}, local_i_key:0x{:02x}, "
              "local_r_key:0x{:02x}, p_cb->loc_auth_req:0x{:02x}",
              p_cb->loc_enc_size, p_cb->local_i_key, p_cb->local_r_key,
              p_cb->loc_auth_req);

          smp_br_state_machine_event(p_cb, SMP_BR_KEYS_RSP_EVT, NULL);
          break;

        // Expected, but nothing to do
        case SMP_NC_REQ_EVT:
        case SMP_SC_OOB_REQ_EVT:
        case SMP_SC_LOC_OOB_DATA_UP_EVT:
        case SMP_LE_ADDR_ASSOC_EVT:
          break;

        default:
          log::error("Unexpected event:{}", p_cb->cb_evt);
      }
    }
  }

  if (!p_cb->cb_evt && p_cb->discard_sec_req) {
    p_cb->discard_sec_req = false;
    smp_sm_event(p_cb, SMP_DISCARD_SEC_REQ_EVT, NULL);
  }
}

/*******************************************************************************
 * Function     smp_send_pair_fail
 * Description  pairing failure to peer device if needed.
 ******************************************************************************/
void smp_send_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  p_cb->status = p_data->status;
  p_cb->failure = p_data->status;

  if (p_cb->status <= SMP_MAX_FAIL_RSN_PER_SPEC &&
      p_cb->status != SMP_SUCCESS) {
    log::error("Pairing failed smp_status:{}", smp_status_text(p_cb->status));
    BTM_LogHistory(kBtmLogTag, p_cb->pairing_bda, "Pairing failed",
                   base::StringPrintf("smp_status:%s",
                                      smp_status_text(p_cb->status).c_str()));
    smp_send_cmd(SMP_OPCODE_PAIRING_FAILED, p_cb);
    p_cb->wait_for_authorization_complete = true;
  }
}

/*******************************************************************************
 * Function     smp_send_pair_req
 * Description  actions related to sending pairing request
 ******************************************************************************/
void smp_send_pair_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_cb->pairing_bda);
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  /* erase all keys when central sends pairing req*/
  if (p_dev_rec) btm_sec_clear_ble_keys(p_dev_rec);
  /* do not manipulate the key, let app decide,
     leave out to BTM to mandate key distribution for bonding case */
  smp_send_cmd(SMP_OPCODE_PAIRING_REQ, p_cb);
}

/*******************************************************************************
 * Function     smp_send_pair_rsp
 * Description  actions related to sending pairing response
 ******************************************************************************/
void smp_send_pair_rsp(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  p_cb->local_i_key &= p_cb->peer_i_key;
  p_cb->local_r_key &= p_cb->peer_r_key;

  if (smp_send_cmd(SMP_OPCODE_PAIRING_RSP, p_cb)) {
    if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB)
      smp_use_oob_private_key(p_cb, NULL);
    else
      smp_decide_association_model(p_cb, NULL);
  }
}

/*******************************************************************************
 * Function     smp_send_confirm
 * Description  send confirmation to the peer
 ******************************************************************************/
void smp_send_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_send_cmd(SMP_OPCODE_CONFIRM, p_cb);
  p_cb->flags |= SMP_PAIR_FLAGS_CMD_CONFIRM_SENT;
}

/*******************************************************************************
 * Function     smp_send_rand
 * Description  send pairing random to the peer
 ******************************************************************************/
void smp_send_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_send_cmd(SMP_OPCODE_RAND, p_cb);
}

/*******************************************************************************
 * Function     smp_send_pair_public_key
 * Description  send pairing public key command to the peer
 ******************************************************************************/
void smp_send_pair_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_send_cmd(SMP_OPCODE_PAIR_PUBLIC_KEY, p_cb);
}

/*******************************************************************************
 * Function     SMP_SEND_COMMITMENT
 * Description send commitment command to the peer
 ******************************************************************************/
void smp_send_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_send_cmd(SMP_OPCODE_PAIR_COMMITM, p_cb);
}

/*******************************************************************************
 * Function     smp_send_dhkey_check
 * Description send DHKey Check command to the peer
 ******************************************************************************/
void smp_send_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_send_cmd(SMP_OPCODE_PAIR_DHKEY_CHECK, p_cb);
}

/*******************************************************************************
 * Function     smp_send_keypress_notification
 * Description send Keypress Notification command to the peer
 ******************************************************************************/
void smp_send_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  p_cb->local_keypress_notification = p_data->status;
  smp_send_cmd(SMP_OPCODE_PAIR_KEYPR_NOTIF, p_cb);
}

/*******************************************************************************
 * Function     smp_send_enc_info
 * Description  send encryption information command.
 ******************************************************************************/
void smp_send_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("p_cb->loc_enc_size={}", p_cb->loc_enc_size);
  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ENC, false);

  smp_send_cmd(SMP_OPCODE_ENCRYPT_INFO, p_cb);
  smp_send_cmd(SMP_OPCODE_CENTRAL_ID, p_cb);

  /* save the DIV and key size information when acting as peripheral device */
  tBTM_LE_KEY_VALUE le_key = {
      .lenc_key =
          {
              .ltk = p_cb->ltk,
              .div = p_cb->div,
              .key_size = p_cb->loc_enc_size,
              .sec_level = p_cb->sec_level,
          },
  };

  if ((p_cb->peer_auth_req & SMP_AUTH_BOND) &&
      (p_cb->loc_auth_req & SMP_AUTH_BOND))
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_LENC, &le_key, true);
  smp_key_distribution(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_send_id_info
 * Description  send ID information command.
 ******************************************************************************/
void smp_send_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ID, false);

  smp_send_cmd(SMP_OPCODE_IDENTITY_INFO, p_cb);
  smp_send_cmd(SMP_OPCODE_ID_ADDR, p_cb);

  if ((p_cb->peer_auth_req & SMP_AUTH_BOND) &&
      (p_cb->loc_auth_req & SMP_AUTH_BOND))
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_LID, nullptr, true);

  smp_key_distribution_by_transport(p_cb, NULL);
}

/**  send CSRK command. */
void smp_send_csrk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_CSRK, false);

  if (smp_send_cmd(SMP_OPCODE_SIGN_INFO, p_cb)) {
    tBTM_LE_KEY_VALUE key = {
        .lcsrk_key =
            {
                .counter = 0, /* initialize the local counter */
                .div = p_cb->div,
                .sec_level = p_cb->sec_level,
                .csrk = p_cb->csrk,
            },
    };
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_LCSRK, &key, true);
  }

  smp_key_distribution_by_transport(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_send_ltk_reply
 * Description  send LTK reply
 ******************************************************************************/
void smp_send_ltk_reply(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  Octet16 stk;
  memcpy(stk.data(), p_data->key.p_data, stk.size());
  /* send stk as LTK response */
  btm_ble_ltk_request_reply(p_cb->pairing_bda, true, stk);
}

/*******************************************************************************
 * Function     smp_proc_sec_req
 * Description  process security request.
 ******************************************************************************/
void smp_proc_sec_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  if (smp_command_has_invalid_length(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  tBTM_LE_AUTH_REQ auth_req = *(tBTM_LE_AUTH_REQ*)p_data->p_data;
  tBTM_BLE_SEC_REQ_ACT sec_req_act;

  log::verbose("auth_req=0x{:x}", auth_req);

  p_cb->cb_evt = SMP_EVT_NONE;

  btm_ble_link_sec_check(p_cb->pairing_bda, auth_req, &sec_req_act);

  log::verbose("sec_req_act={}", sec_req_act);

  switch (sec_req_act) {
    case BTM_BLE_SEC_REQ_ACT_ENCRYPT:
      log::verbose("BTM_BLE_SEC_REQ_ACT_ENCRYPT");
      smp_sm_event(p_cb, SMP_ENC_REQ_EVT, NULL);
      break;

    case BTM_BLE_SEC_REQ_ACT_PAIR:
      p_cb->sc_only_mode_locally_required =
          (p_cb->init_security_mode == BTM_SEC_MODE_SC) ? true : false;

      /* respond to non SC pairing request as failure in SC only mode */
      if (p_cb->sc_only_mode_locally_required &&
          (auth_req & SMP_SC_SUPPORT_BIT) == 0) {
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_PAIR_AUTH_FAIL;
        smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      } else {
        /* initialize local i/r key to be default keys */
        p_cb->peer_auth_req = auth_req;
        p_cb->local_r_key = p_cb->local_i_key = SMP_SEC_DEFAULT_KEY;
        p_cb->cb_evt = SMP_SEC_REQUEST_EVT;
      }
      break;

    case BTM_BLE_SEC_REQ_ACT_DISCARD:
      p_cb->discard_sec_req = true;
      break;

    default:
      /* do nothing */
      break;
  }
}

/*******************************************************************************
 * Function     smp_proc_sec_grant
 * Description  process security grant.
 ******************************************************************************/
void smp_proc_sec_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t res = p_data->status;
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (res != SMP_SUCCESS) {
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, p_data);
  } else /*otherwise, start pairing */
  {
    /* send IO request callback */
    p_cb->cb_evt = SMP_IO_CAP_REQ_EVT;
  }
}

/*******************************************************************************
 * Function     smp_proc_pair_fail
 * Description  process pairing failure from peer device
 ******************************************************************************/
void smp_proc_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->rcvd_cmd_len < 2) {
    log::warn("rcvd_cmd_len {} too short: must be at least 2",
              p_cb->rcvd_cmd_len);
    p_cb->status = SMP_INVALID_PARAMETERS;
  } else {
    if (IS_FLAG_ENABLED(fix_pairing_failure_reason_from_remote)) {
      p_cb->status = static_cast<tSMP_STATUS>(p_data->p_data[0]);
    } else {
      p_cb->status = p_data->status;
    }
  }

  /* Cancel pending auth complete timer if set */
  alarm_cancel(p_cb->delayed_auth_timer_ent);
}

/*******************************************************************************
 * Function     smp_proc_pair_cmd
 * Description  Process the SMP pairing request/response from peer device
 ******************************************************************************/
void smp_proc_pair_cmd(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_cb->pairing_bda);

  log::verbose("pairing_bda={}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  /* erase all keys if it is peripheral proc pairing req */
  if (p_dev_rec && (p_cb->role == HCI_ROLE_PERIPHERAL))
    btm_sec_clear_ble_keys(p_dev_rec);

  p_cb->flags |= SMP_PAIR_FLAG_ENC_AFTER_PAIR;

  if (smp_command_has_invalid_length(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  STREAM_TO_UINT8(p_cb->peer_io_caps, p);
  STREAM_TO_UINT8(p_cb->peer_oob_flag, p);
  STREAM_TO_UINT8(p_cb->peer_auth_req, p);
  STREAM_TO_UINT8(p_cb->peer_enc_size, p);
  STREAM_TO_UINT8(p_cb->peer_i_key, p);
  STREAM_TO_UINT8(p_cb->peer_r_key, p);

  tSMP_STATUS reason = p_cb->cert_failure;
  if (reason == SMP_ENC_KEY_SIZE) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = reason;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  // PTS Testing failure modes
  if (pts_test_send_authentication_complete_failure(p_cb)) return;

  if (p_cb->role == HCI_ROLE_PERIPHERAL) {
    if (!(p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD)) {
      /* peer (central) started pairing sending Pairing Request */
      p_cb->local_i_key = p_cb->peer_i_key;
      p_cb->local_r_key = p_cb->peer_r_key;

      p_cb->cb_evt =  SMP_IO_CAP_REQ_EVT;
    } else /* update local i/r key according to pairing request */
    {
      /* pairing started with this side (peripheral) sending Security Request */
      p_cb->local_i_key &= p_cb->peer_i_key;
      p_cb->local_r_key &= p_cb->peer_r_key;
      p_cb->selected_association_model = smp_select_association_model(p_cb);

      if (p_cb->sc_only_mode_locally_required &&
          (!(p_cb->sc_mode_required_by_peer) ||
           (p_cb->selected_association_model ==
            SMP_MODEL_SEC_CONN_JUSTWORKS))) {
        log::error(
            "pairing failed - peripheral requires secure connection only mode");
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_PAIR_AUTH_FAIL;
        smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
        return;
      }

      if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB) {
        if (smp_request_oob_data(p_cb)) return;
      } else {
        smp_send_pair_rsp(p_cb, NULL);
      }
    }
  } else /* Central receives pairing response */
  {
    p_cb->selected_association_model = smp_select_association_model(p_cb);

    if (p_cb->sc_only_mode_locally_required &&
        (!(p_cb->sc_mode_required_by_peer) ||
         (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS))) {
      log::error(
          "Central requires secure connection only mode but it can't be "
          "provided -> Central fails pairing");
      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = SMP_PAIR_AUTH_FAIL;
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      return;
    }

    if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB) {
      if (smp_request_oob_data(p_cb)) return;
    } else {
      smp_decide_association_model(p_cb, NULL);
    }
  }
}

/** process pairing confirm from peer device */
void smp_proc_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("pairing_bda={}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (p_data) {
    uint8_t* p = p_data->p_data;
    if (p != NULL) {
      /* save the SConfirm for comparison later */
      STREAM_TO_ARRAY(p_cb->rconfirm.data(), p, OCTET16_LEN);
    }
  }

  p_cb->flags |= SMP_PAIR_FLAGS_CMD_CONFIRM_RCVD;
}

/*******************************************************************************
 * Function     smp_proc_rand
 * Description  process pairing random (nonce) from peer device
 ******************************************************************************/
void smp_proc_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("pairing_bda={}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (IS_FLAG_ENABLED(fix_le_pairing_passkey_entry_bypass)) {
    if (!((p_cb->loc_auth_req & SMP_SC_SUPPORT_BIT) &&
          (p_cb->peer_auth_req & SMP_SC_SUPPORT_BIT)) &&
        !(p_cb->flags & SMP_PAIR_FLAGS_CMD_CONFIRM_SENT)) {
      // in legacy pairing, the peer should send its rand after
      // we send our confirm
      tSMP_INT_DATA smp_int_data{};
      smp_int_data.status = SMP_INVALID_PARAMETERS;
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      return;
    }
  }

  /* save the SRand for comparison */
  STREAM_TO_ARRAY(p_cb->rrand.data(), p, OCTET16_LEN);
}

/*******************************************************************************
 * Function     smp_process_pairing_public_key
 * Description  process pairing public key command from the peer device
 *              - saves the peer public key;
 *              - sets the flag indicating that the peer public key is received;
 *              - calls smp_wait_for_both_public_keys(...).
 *
 ******************************************************************************/
void smp_process_pairing_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  STREAM_TO_ARRAY(p_cb->peer_publ_key.x, p, BT_OCTET32_LEN);
  STREAM_TO_ARRAY(p_cb->peer_publ_key.y, p, BT_OCTET32_LEN);

  Point pt;
  memcpy(pt.x, p_cb->peer_publ_key.x, BT_OCTET32_LEN);
  memcpy(pt.y, p_cb->peer_publ_key.y, BT_OCTET32_LEN);

  if (!memcmp(p_cb->peer_publ_key.x, p_cb->loc_publ_key.x, BT_OCTET32_LEN)) {
    log::warn("Remote and local public keys can't match");
    tSMP_INT_DATA smp;
    smp.status = SMP_PAIR_AUTH_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp);
    return;
  }

  if (!ECC_ValidatePoint(pt)) {
    tSMP_INT_DATA smp;
    smp.status = SMP_PAIR_AUTH_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp);
    return;
  }

  p_cb->flags |= SMP_PAIR_FLAG_HAVE_PEER_PUBL_KEY;

  smp_wait_for_both_public_keys(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_process_pairing_commitment
 * Description  process pairing commitment from peer device
 ******************************************************************************/
void smp_process_pairing_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  p_cb->flags |= SMP_PAIR_FLAG_HAVE_PEER_COMM;

  if (p != NULL) {
    STREAM_TO_ARRAY(p_cb->remote_commitment.data(), p, OCTET16_LEN);
  }
}

/*******************************************************************************
 * Function     smp_process_dhkey_check
 * Description  process DHKey Check from peer device
 ******************************************************************************/
void smp_process_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (p != NULL) {
    STREAM_TO_ARRAY(p_cb->remote_dhkey_check.data(), p, OCTET16_LEN);
  }

  p_cb->flags |= SMP_PAIR_FLAG_HAVE_PEER_DHK_CHK;
}

/*******************************************************************************
 * Function     smp_process_keypress_notification
 * Description  process pairing keypress notification from peer device
 ******************************************************************************/
void smp_process_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  p_cb->status = p_data->status;

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (p != NULL) {
    STREAM_TO_UINT8(p_cb->peer_keypress_notification, p);
  } else {
    p_cb->peer_keypress_notification = SMP_SC_KEY_OUT_OF_RANGE;
  }
  p_cb->cb_evt = SMP_PEER_KEYPR_NOT_EVT;
}

/*******************************************************************************
 * Function     smp_br_process_pairing_command
 * Description  Process the SMP pairing request/response from peer device via
 *              BR/EDR transport.
 ******************************************************************************/
void smp_br_process_pairing_command(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_cb->pairing_bda);

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  /* rejecting BR pairing request over non-SC BR link */
  if (!p_dev_rec->sec_rec.new_encryption_key_is_p256 &&
      p_cb->role == HCI_ROLE_PERIPHERAL) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_XTRANS_DERIVE_NOT_ALLOW;
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  /* erase all keys if it is peripheral proc pairing req*/
  if (p_dev_rec && (p_cb->role == HCI_ROLE_PERIPHERAL))
    btm_sec_clear_ble_keys(p_dev_rec);

  p_cb->flags |= SMP_PAIR_FLAG_ENC_AFTER_PAIR;

  if (smp_command_has_invalid_length(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  STREAM_TO_UINT8(p_cb->peer_io_caps, p);
  STREAM_TO_UINT8(p_cb->peer_oob_flag, p);
  STREAM_TO_UINT8(p_cb->peer_auth_req, p);
  STREAM_TO_UINT8(p_cb->peer_enc_size, p);
  STREAM_TO_UINT8(p_cb->peer_i_key, p);
  STREAM_TO_UINT8(p_cb->peer_r_key, p);

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  /* peer (central) started pairing sending Pairing Request */
  /* or being central device always use received i/r key as keys to distribute
   */
  p_cb->local_i_key = p_cb->peer_i_key;
  p_cb->local_r_key = p_cb->peer_r_key;

  if (p_cb->role == HCI_ROLE_PERIPHERAL) {
    p_dev_rec->sec_rec.new_encryption_key_is_p256 = false;
    /* shortcut to skip Security Grant step */
    p_cb->cb_evt = SMP_BR_KEYS_REQ_EVT;
  } else {
    /* Central receives pairing response */
    log::verbose(
        "central rcvs valid PAIRING RESPONSE. Supposed to move to key "
        "distribution phase.");
  }

  /* auth_req received via BR/EDR SM channel is set to 0,
     but everything derived/exchanged has to be saved */
  p_cb->peer_auth_req |= SMP_AUTH_BOND;
  p_cb->loc_auth_req |= SMP_AUTH_BOND;
}

/*******************************************************************************
 * Function     smp_br_process_security_grant
 * Description  process security grant in case of pairing over BR/EDR transport.
 ******************************************************************************/
void smp_br_process_security_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (p_data->status != SMP_SUCCESS) {
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, p_data);
  } else {
    /* otherwise, start pairing; send IO request callback */
    p_cb->cb_evt = SMP_BR_KEYS_REQ_EVT;
  }
}

/*******************************************************************************
 * Function     smp_br_check_authorization_request
 * Description  sets the SMP kes to be derived/distribute over BR/EDR transport
 *              before starting the distribution/derivation
 ******************************************************************************/
void smp_br_check_authorization_request(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("rcvs i_keys=0x{:x} r_keys=0x{:x} (i-initiator r-responder)",
               p_cb->local_i_key, p_cb->local_r_key);

  /* In LE SC mode LK field is ignored when BR/EDR transport is used */
  p_cb->local_i_key &= ~SMP_SEC_KEY_TYPE_LK;
  p_cb->local_r_key &= ~SMP_SEC_KEY_TYPE_LK;

  /* In LE SC mode only IRK, IAI, CSRK are exchanged with the peer.
  ** Set local_r_key on central to expect only these keys. */
  if (p_cb->role == HCI_ROLE_CENTRAL) {
    p_cb->local_r_key &= (SMP_SEC_KEY_TYPE_ID | SMP_SEC_KEY_TYPE_CSRK);
  }

  /* Check if H7 function needs to be used for key derivation*/
  if ((p_cb->loc_auth_req & SMP_H7_SUPPORT_BIT) &&
      (p_cb->peer_auth_req & SMP_H7_SUPPORT_BIT)) {
    p_cb->key_derivation_h7_used = TRUE;
  }
  log::verbose(
      "use h7={}, i_keys=0x{:x} r_keys=0x{:x} (i-initiator r-responder)",
      p_cb->key_derivation_h7_used, p_cb->local_i_key, p_cb->local_r_key);

  if (/*((p_cb->peer_auth_req & SMP_AUTH_BOND) ||
          (p_cb->loc_auth_req & SMP_AUTH_BOND)) &&*/
      (p_cb->local_i_key || p_cb->local_r_key)) {
    smp_br_state_machine_event(p_cb, SMP_BR_BOND_REQ_EVT, NULL);

    /* if no peer key is expected, start central key distribution */
    if (p_cb->role == HCI_ROLE_CENTRAL && p_cb->local_r_key == 0)
      smp_key_distribution_by_transport(p_cb, NULL);
  } else {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_SUCCESS;
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
  }
}

/*******************************************************************************
 * Function     smp_br_select_next_key
 * Description  selects the next key to derive/send when BR/EDR transport is
 *              used.
 ******************************************************************************/
void smp_br_select_next_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("role={} (0-central) r_keys=0x{:x} i_keys=0x{:x}", p_cb->role,
               p_cb->local_r_key, p_cb->local_i_key);

  if (p_cb->role == HCI_ROLE_PERIPHERAL ||
      (!p_cb->local_r_key && p_cb->role == HCI_ROLE_CENTRAL)) {
    smp_key_pick_key(p_cb, p_data);
  }

  if (!p_cb->local_i_key && !p_cb->local_r_key) {
    /* state check to prevent re-entrance */
    if (smp_get_br_state() == SMP_BR_STATE_BOND_PENDING) {
      if (p_cb->total_tx_unacked == 0) {
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_SUCCESS;
        smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
      } else {
        p_cb->wait_for_authorization_complete = true;
      }
    }
  }
}

/** process encryption information from peer device */
void smp_proc_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  STREAM_TO_ARRAY(p_cb->ltk.data(), p, OCTET16_LEN);

  smp_key_distribution(p_cb, NULL);
}

/** process central ID from peripheral device */
void smp_proc_central_id(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->rcvd_cmd_len < 11) {  // 1(Code) + 2(EDIV) + 8(Rand)
    log::error("Invalid command length:{}, should be at least 11",
               p_cb->rcvd_cmd_len);
    return;
  }

  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ENC, true);

  tBTM_LE_KEY_VALUE le_key = {
      .penc_key = {},
  };
  STREAM_TO_UINT16(le_key.penc_key.ediv, p);
  STREAM_TO_ARRAY(le_key.penc_key.rand, p, BT_OCTET8_LEN);

  /* store the encryption keys from peer device */
  le_key.penc_key.ltk = p_cb->ltk;
  le_key.penc_key.sec_level = p_cb->sec_level;
  le_key.penc_key.key_size = p_cb->loc_enc_size;

  if ((p_cb->peer_auth_req & SMP_AUTH_BOND) &&
      (p_cb->loc_auth_req & SMP_AUTH_BOND))
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_PENC, &le_key, true);

  smp_key_distribution(p_cb, NULL);
}

/** process identity information from peer device */
void smp_proc_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  STREAM_TO_ARRAY(p_cb->tk.data(), p, OCTET16_LEN); /* reuse TK for IRK */
  smp_key_distribution_by_transport(p_cb, NULL);
}

/** process identity address from peer device */
void smp_proc_id_addr(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  const uint8_t* p = p_data->p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ID, true);

  tBTM_LE_KEY_VALUE pid_key = {
      .pid_key = {},
  };

  STREAM_TO_UINT8(pid_key.pid_key.identity_addr_type, p);
  STREAM_TO_BDADDR(pid_key.pid_key.identity_addr, p);
  pid_key.pid_key.irk = p_cb->tk;

  /* to use as BD_ADDR for lk derived from ltk */
  p_cb->id_addr_rcvd = true;
  p_cb->id_addr_type = pid_key.pid_key.identity_addr_type;
  p_cb->id_addr = pid_key.pid_key.identity_addr;

  /* store the ID key from peer device */
  if ((p_cb->peer_auth_req & SMP_AUTH_BOND) &&
      (p_cb->loc_auth_req & SMP_AUTH_BOND)) {
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_PID, &pid_key, true);
    p_cb->cb_evt = SMP_LE_ADDR_ASSOC_EVT;
    smp_send_app_cback(p_cb, NULL);
  }
  smp_key_distribution_by_transport(p_cb, NULL);
}

/* process security information from peer device */
void smp_proc_srk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_command_has_invalid_parameters(p_cb)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_INVALID_PARAMETERS;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_CSRK, true);

  /* save CSRK to security record */
  tBTM_LE_KEY_VALUE le_key = {
      .pcsrk_key =
          {
              .sec_level = p_cb->sec_level,
          },
  };

  /* get peer CSRK */
  maybe_non_aligned_memcpy(le_key.pcsrk_key.csrk.data(), p_data->p_data,
                           OCTET16_LEN);

  /* initialize the peer counter */
  le_key.pcsrk_key.counter = 0;

  if ((p_cb->peer_auth_req & SMP_AUTH_BOND) &&
      (p_cb->loc_auth_req & SMP_AUTH_BOND))
    btm_sec_save_le_key(p_cb->pairing_bda, BTM_LE_KEY_PCSRK, &le_key, true);
  smp_key_distribution_by_transport(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_proc_compare
 * Description  process compare value
 ******************************************************************************/
void smp_proc_compare(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (!memcmp(p_cb->rconfirm.data(), p_data->key.p_data, OCTET16_LEN)) {
    /* compare the max encryption key size, and save the smaller one for the
     * link */
    if (p_cb->peer_enc_size < p_cb->loc_enc_size)
      p_cb->loc_enc_size = p_cb->peer_enc_size;

    if (p_cb->role == HCI_ROLE_PERIPHERAL)
      smp_sm_event(p_cb, SMP_RAND_EVT, NULL);
    else {
      /* central device always use received i/r key as keys to distribute */
      p_cb->local_i_key = p_cb->peer_i_key;
      p_cb->local_r_key = p_cb->peer_r_key;

      smp_sm_event(p_cb, SMP_ENC_REQ_EVT, NULL);
    }

  } else {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_CONFIRM_VALUE_ERR;
    p_cb->failure = SMP_CONFIRM_VALUE_ERR;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  }
}

/*******************************************************************************
 * Function     smp_proc_sl_key
 * Description  process key ready events.
 ******************************************************************************/
void smp_proc_sl_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t key_type = p_data->key.key_type;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (key_type == SMP_KEY_TYPE_TK) {
    smp_generate_srand_mrand_confirm(p_cb, NULL);
  } else if (key_type == SMP_KEY_TYPE_CFM) {
    smp_set_state(SMP_STATE_WAIT_CONFIRM);

    if (p_cb->flags & SMP_PAIR_FLAGS_CMD_CONFIRM_RCVD)
      smp_sm_event(p_cb, SMP_CONFIRM_EVT, NULL);
  }
}

/*******************************************************************************
 * Function     smp_start_enc
 * Description  start encryption
 ******************************************************************************/
void smp_start_enc(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tBTM_STATUS cmd;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (p_data != NULL) {
    cmd = btm_ble_start_encrypt(p_cb->pairing_bda, true,
                                (Octet16*)p_data->key.p_data);
  } else {
    cmd = btm_ble_start_encrypt(p_cb->pairing_bda, false, NULL);
  }

  if (cmd != BTM_CMD_STARTED && cmd != BTM_BUSY) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_ENC_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  }
}

/*******************************************************************************
 * Function     smp_proc_discard
 * Description   processing for discard security request
 ******************************************************************************/
void smp_proc_discard(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (!(p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD))
    smp_reset_control_value(p_cb);
}

/*******************************************************************************
 * Function     smp_enc_cmpl
 * Description   encryption success
 ******************************************************************************/
void smp_enc_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t enc_enable = p_data->status;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  tSMP_INT_DATA smp_int_data;
  smp_int_data.status = enc_enable ? SMP_SUCCESS : SMP_ENC_FAIL;
  smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
}

/*******************************************************************************
 * Function     smp_sirk_verify
 * Description   verify if device belongs to csis group.
 ******************************************************************************/
void smp_sirk_verify(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tBTM_STATUS callback_rc;
  log::debug("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_data->status != SMP_SUCCESS) {
    log::debug(
        "Cancel device verification due to invalid status({}) while bonding.",
        p_data->status);

    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_SIRK_DEVICE_INVALID;

    BTM_LogHistory(
        kBtmLogTag, p_cb->pairing_bda, "SIRK verification",
        base::StringPrintf("Verification failed, smp_status:%s",
                           smp_status_text(smp_int_data.status).c_str()));

    smp_sm_event(p_cb, SMP_SIRK_DEVICE_VALID_EVT, &smp_int_data);

    return;
  }

  if (p_cb->p_callback) {
    p_cb->cb_evt = SMP_SIRK_VERIFICATION_REQ_EVT;
    callback_rc = (*p_cb->p_callback)(p_cb->cb_evt, p_cb->pairing_bda, nullptr);

    /* There is no member validator callback - device is by default valid */
    if (callback_rc == BTM_SUCCESS_NO_SECURITY) {
      BTM_LogHistory(kBtmLogTag, p_cb->pairing_bda, "SIRK verification",
                     base::StringPrintf("Device validated due to no security"));

      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = SMP_SUCCESS;
      smp_sm_event(p_cb, SMP_SIRK_DEVICE_VALID_EVT, &smp_int_data);
    }
  } else {
    log::error("There are no registrated callbacks for SMP");
  }
}

/*******************************************************************************
 * Function     smp_check_auth_req
 * Description  check authentication request
 ******************************************************************************/
void smp_check_auth_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t enc_enable = p_data->status;

  log::verbose(
      "rcvs enc_enable={} i_keys=0x{:x} r_keys=0x{:x} (i-initiator "
      "r-responder)",
      enc_enable, p_cb->local_i_key, p_cb->local_r_key);
  if (enc_enable == 1) {
    if (p_cb->sc_mode_required_by_peer) {
      /* In LE SC mode LTK is used instead of STK and has to be always saved */
      p_cb->local_i_key |= SMP_SEC_KEY_TYPE_ENC;
      p_cb->local_r_key |= SMP_SEC_KEY_TYPE_ENC;

      /* In LE SC mode LK is derived from LTK only if both sides request it */
      if (!(p_cb->local_i_key & SMP_SEC_KEY_TYPE_LK) ||
          !(p_cb->local_r_key & SMP_SEC_KEY_TYPE_LK)) {
        p_cb->local_i_key &= ~SMP_SEC_KEY_TYPE_LK;
        p_cb->local_r_key &= ~SMP_SEC_KEY_TYPE_LK;
      }

      /* In LE SC mode only IRK, IAI, CSRK are exchanged with the peer.
      ** Set local_r_key on central to expect only these keys.
      */
      if (p_cb->role == HCI_ROLE_CENTRAL) {
        p_cb->local_r_key &= (SMP_SEC_KEY_TYPE_ID | SMP_SEC_KEY_TYPE_CSRK);
      }
    } else {
      /* in legacy mode derivation of BR/EDR LK is not supported */
      p_cb->local_i_key &= ~SMP_SEC_KEY_TYPE_LK;
      p_cb->local_r_key &= ~SMP_SEC_KEY_TYPE_LK;
    }
    log::verbose(
        "rcvs upgrades:i_keys=0x{:x} r_keys=0x{:x} (i-initiator r-responder)",
        p_cb->local_i_key, p_cb->local_r_key);

    if (/*((p_cb->peer_auth_req & SMP_AUTH_BOND) ||
         (p_cb->loc_auth_req & SMP_AUTH_BOND)) &&*/
        (p_cb->local_i_key || p_cb->local_r_key)) {
      smp_sm_event(p_cb, SMP_BOND_REQ_EVT, NULL);
    } else {
      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = enc_enable ? SMP_SUCCESS : SMP_ENC_FAIL;
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    }
  } else if (enc_enable == 0) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = enc_enable ? SMP_SUCCESS : SMP_ENC_FAIL;
    /* if failed for encryption after pairing, send callback */
    if (p_cb->flags & SMP_PAIR_FLAG_ENC_AFTER_PAIR)
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    /* if enc failed for old security information */
    /* if central device, clean up and abck to idle; peripheral device do
     * nothing */
    else if (p_cb->role == HCI_ROLE_CENTRAL) {
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    }
  }
}

/*******************************************************************************
 * Function     smp_key_pick_key
 * Description  Pick a key distribution function based on the key mask.
 ******************************************************************************/
void smp_key_pick_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t key_to_dist = (p_cb->role == HCI_ROLE_PERIPHERAL) ? p_cb->local_r_key
                                                            : p_cb->local_i_key;
  uint8_t i = 0;

  log::verbose("key_to_dist=0x{:x}", key_to_dist);
  while (i < SMP_KEY_DIST_TYPE_MAX) {
    log::verbose("key to send=0x{:02x}, i={}", key_to_dist, i);

    if (key_to_dist & (1 << i)) {
      log::verbose("smp_distribute_act[{}]", i);
      (*smp_distribute_act[i])(p_cb, p_data);
      break;
    }
    i++;
  }
}
/*******************************************************************************
 * Function     smp_key_distribution
 * Description  start key distribution if required.
 ******************************************************************************/
void smp_key_distribution(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("role={} (0-central) r_keys=0x{:x} i_keys=0x{:x}", p_cb->role,
               p_cb->local_r_key, p_cb->local_i_key);

  if (p_cb->role == HCI_ROLE_PERIPHERAL ||
      (!p_cb->local_r_key && p_cb->role == HCI_ROLE_CENTRAL)) {
    smp_key_pick_key(p_cb, p_data);
  }

  if (!p_cb->local_i_key && !p_cb->local_r_key) {
    /* state check to prevent re-entrant */
    if (smp_get_state() == SMP_STATE_BOND_PENDING) {
      if (p_cb->derive_lk) {
        tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_cb->pairing_bda);
        if (!(p_dev_rec->sec_rec.sec_flags & BTM_SEC_LE_LINK_KEY_AUTHED) &&
            (p_dev_rec->sec_rec.sec_flags & BTM_SEC_LINK_KEY_AUTHED)) {
          log::verbose(
              "BR key is higher security than existing LE keys, don't derive "
              "LK from LTK");
        } else {
          smp_derive_link_key_from_long_term_key(p_cb, NULL);
        }
        p_cb->derive_lk = false;
      }

      if (p_cb->total_tx_unacked == 0) {
        /*
         * Instead of declaring authorization complete immediately,
         * delay the event from being sent by SMP_DELAYED_AUTH_TIMEOUT_MS.
         * This allows the peripheral to send over Pairing Failed if the
         * last key is rejected.  During this waiting window, the
         * state should remain in SMP_STATE_BOND_PENDING.
         */
        if (!alarm_is_scheduled(p_cb->delayed_auth_timer_ent)) {
          log::verbose("delaying auth complete");
          alarm_set_on_mloop(p_cb->delayed_auth_timer_ent,
                             SMP_DELAYED_AUTH_TIMEOUT_MS,
                             smp_delayed_auth_complete_timeout, NULL);
        }
      } else {
        p_cb->wait_for_authorization_complete = true;
      }
    }
  }
}

/*******************************************************************************
 * Function         smp_decide_association_model
 * Description      This function is called to select assoc model to be used for
 *                  STK generation and to start STK generation process.
 *
 ******************************************************************************/
void smp_decide_association_model(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tSMP_EVENT int_evt = SMP_NOP_EVT;
  tSMP_INT_DATA smp_int_data;

  log::verbose("Association Model={}", p_cb->selected_association_model);

  switch (p_cb->selected_association_model) {
    case SMP_MODEL_ENCRYPTION_ONLY: /* TK = 0, go calculate Confirm */
      if (p_cb->role == HCI_ROLE_CENTRAL &&
          ((p_cb->peer_auth_req & SMP_AUTH_YN_BIT) != 0) &&
          ((p_cb->loc_auth_req & SMP_AUTH_YN_BIT) == 0)) {
        log::error("IO capability does not meet authentication requirement");
        smp_int_data.status = SMP_PAIR_AUTH_FAIL;
        int_evt = SMP_AUTH_CMPL_EVT;
      } else {
        if (!GetInterfaceToProfiles()->config->isAndroidTVDevice() &&
            (p_cb->local_io_capability == SMP_IO_CAP_IO ||
             p_cb->local_io_capability == SMP_IO_CAP_KBDISP)) {
          /* display consent dialog if this device has a display */
          log::verbose("ENCRYPTION_ONLY showing Consent Dialog");
          p_cb->cb_evt = SMP_CONSENT_REQ_EVT;
          smp_set_state(SMP_STATE_WAIT_NONCE);
          smp_sm_event(p_cb, SMP_SC_DSPL_NC_EVT, NULL);
        } else {
          p_cb->sec_level = SMP_SEC_UNAUTHENTICATE;
          log::verbose("p_cb->sec_level={} (SMP_SEC_UNAUTHENTICATE)",
                       p_cb->sec_level);

          tSMP_KEY key;
          key.key_type = SMP_KEY_TYPE_TK;
          key.p_data = p_cb->tk.data();
          smp_int_data.key = key;

          p_cb->tk = {0};
          /* TK, ready  */
          int_evt = SMP_KEY_READY_EVT;
        }
      }
      break;

    case SMP_MODEL_PASSKEY:
      p_cb->sec_level = SMP_SEC_AUTHENTICATED;
      log::verbose("p_cb->sec_level={}(SMP_SEC_AUTHENTICATED)",
                   p_cb->sec_level);

      p_cb->cb_evt = SMP_PASSKEY_REQ_EVT;
      int_evt = SMP_TK_REQ_EVT;
      break;

    case SMP_MODEL_OOB:
      log::error("Association Model=SMP_MODEL_OOB");
      p_cb->sec_level = SMP_SEC_AUTHENTICATED;
      log::verbose("p_cb->sec_level={}(SMP_SEC_AUTHENTICATED)",
                   p_cb->sec_level);

      p_cb->cb_evt = SMP_OOB_REQ_EVT;
      int_evt = SMP_TK_REQ_EVT;
      break;

    case SMP_MODEL_KEY_NOTIF:
      p_cb->sec_level = SMP_SEC_AUTHENTICATED;
      log::verbose("Need to generate Passkey");

      /* generate passkey and notify application */
      smp_generate_passkey(p_cb, NULL);
      break;

    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
    case SMP_MODEL_SEC_CONN_OOB:
      int_evt = SMP_PUBL_KEY_EXCH_REQ_EVT;
      break;

    case SMP_MODEL_OUT_OF_RANGE:
      log::error("Association Model=SMP_MODEL_OUT_OF_RANGE (failed)");
      smp_int_data.status = SMP_UNKNOWN_IO_CAP;
      int_evt = SMP_AUTH_CMPL_EVT;
      break;

    default:
      log::error("Association Model={} (SOMETHING IS WRONG WITH THE CODE)",
                 p_cb->selected_association_model);
      smp_int_data.status = SMP_UNKNOWN_IO_CAP;
      int_evt = SMP_AUTH_CMPL_EVT;
  }

  log::verbose("sec_level={}", p_cb->sec_level);
  if (int_evt) smp_sm_event(p_cb, int_evt, &smp_int_data);
}

/*******************************************************************************
 * Function     smp_process_io_response
 * Description  process IO response for a peripheral device.
 ******************************************************************************/
void smp_process_io_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD) {
    /* pairing started by local (peripheral) Security Request */
    smp_set_state(SMP_STATE_SEC_REQ_PENDING);
    smp_send_cmd(SMP_OPCODE_SEC_REQ, p_cb);
  } else /* plan to send pairing respond */
  {
    /* pairing started by peer (central) Pairing Request */
    p_cb->selected_association_model = smp_select_association_model(p_cb);

    if (p_cb->sc_only_mode_locally_required &&
        (!(p_cb->sc_mode_required_by_peer) ||
         (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS))) {
      log::error(
          "Peripheral requires secure connection only mode but it can't be "
          "provided -> Peripheral fails pairing");
      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = SMP_PAIR_AUTH_FAIL;
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      return;
    }

    // If we are doing SMP_MODEL_SEC_CONN_OOB we don't need to request OOB data
    // locally if loc_oob_flag == 0x00 b/c there is no OOB data to give.  In the
    // event the loc_oob_flag is present value, we should request the OOB data
    // locally; otherwise fail.
    // If we are the initiator the OOB data has already been stored and will be
    // collected in the statemachine later.
    //
    // loc_oob_flag could be one of the following tSMP_OOB_FLAG enum values:
    // SMP_OOB_NONE = 0
    // SMP_OOB_PRESENT = 1
    // SMP_OOB_UNKNOWN = 2
    //
    // The only time Android cares about needing to provide the peer oob data
    // here would be in the advertiser situation or role.  If the
    // device is doing the connecting it will not need to get the data again as
    // it was already provided in the initiation call.
    //
    // loc_oob_flag should only equal SMP_OOB_PRESENT when PEER DATA exists and
    // device is the advertiser as opposed to being the connector.
    if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB) {
      switch (p_cb->loc_oob_flag) {
        case SMP_OOB_NONE:
          log::info("SMP_MODEL_SEC_CONN_OOB with SMP_OOB_NONE");
          smp_send_pair_rsp(p_cb, NULL);
          break;
        case SMP_OOB_PRESENT:
          log::info("SMP_MODEL_SEC_CONN_OOB with SMP_OOB_PRESENT");
          if (smp_request_oob_data(p_cb)) return;
          break;
        case SMP_OOB_UNKNOWN:
          log::warn("SMP_MODEL_SEC_CONN_OOB with SMP_OOB_UNKNOWN");
          tSMP_INT_DATA smp_int_data;
          smp_int_data.status = SMP_PAIR_AUTH_FAIL;
          smp_send_pair_fail(p_cb, &smp_int_data);
          return;
      }
    }

    // PTS Testing failure modes
    if (pts_test_send_authentication_complete_failure(p_cb)) return;

    smp_send_pair_rsp(p_cb, NULL);
  }
}

/*******************************************************************************
 * Function     smp_br_process_peripheral_keys_response
 * Description  process application keys response for a peripheral device
 *              (BR/EDR transport).
 ******************************************************************************/
void smp_br_process_peripheral_keys_response(tSMP_CB* p_cb,
                                             tSMP_INT_DATA* p_data) {
  smp_br_send_pair_response(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_br_send_pair_response
 * Description  actions related to sending pairing response over BR/EDR
 *              transport.
 ******************************************************************************/
void smp_br_send_pair_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  p_cb->local_i_key &= p_cb->peer_i_key;
  p_cb->local_r_key &= p_cb->peer_r_key;

  smp_send_cmd(SMP_OPCODE_PAIRING_RSP, p_cb);
}

/*******************************************************************************
 * Function         smp_pairing_cmpl
 * Description      This function is called to send the pairing complete
 *                  callback and remove the connection if needed.
 ******************************************************************************/
void smp_pairing_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  if (p_cb->total_tx_unacked == 0) {
    /* process the pairing complete */
    smp_proc_pairing_cmpl(p_cb);
  }
}

/*******************************************************************************
 * Function         smp_pair_terminate
 * Description      This function is called to send the pairing complete
 *                  callback and remove the connection if needed.
 ******************************************************************************/
void smp_pair_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  p_cb->status = SMP_CONN_TOUT;
  smp_proc_pairing_cmpl(p_cb);
}

/*******************************************************************************
 * Function         smp_idle_terminate
 * Description      This function calledin idle state to determine to send
 *                  authentication complete or not.
 ******************************************************************************/
void smp_idle_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  if (p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD) {
    log::verbose("Pairing terminated at IDLE state.");
    p_cb->status = SMP_FAIL;
    smp_proc_pairing_cmpl(p_cb);
  }
}

/*******************************************************************************
 * Function     smp_both_have_public_keys
 * Description  The function is called when both local and peer public keys are
 *              saved.
 *              Actions:
 *              - invokes DHKey computation;
 *              - on peripheral side invokes sending local public key to the
 *peer.
 *              - invokes SC phase 1 process.
 ******************************************************************************/
void smp_both_have_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  /* invokes DHKey computation */
  smp_compute_dhkey(p_cb);

  /* on peripheral side invokes sending local public key to the peer */
  if (p_cb->role == HCI_ROLE_PERIPHERAL) smp_send_pair_public_key(p_cb, NULL);

  smp_sm_event(p_cb, SMP_SC_DHKEY_CMPLT_EVT, NULL);
}

/*******************************************************************************
 * Function     smp_start_secure_connection_phase1
 * Description  Start Secure Connection phase1 i.e. invokes initialization of
 *              Secure Connection phase 1 parameters and starts building/sending
 *              to the peer messages appropriate for the role and association
 *              model.
 ******************************************************************************/
void smp_start_secure_connection_phase1(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS) {
    p_cb->sec_level = SMP_SEC_UNAUTHENTICATE;
    log::verbose("p_cb->sec_level={} (SMP_SEC_UNAUTHENTICATE)",
                 p_cb->sec_level);
  } else {
    p_cb->sec_level = SMP_SEC_AUTHENTICATED;
    log::verbose("p_cb->sec_level={} (SMP_SEC_AUTHENTICATED)", p_cb->sec_level);
  }

  switch (p_cb->selected_association_model) {
    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
      p_cb->local_random = {0};
      smp_start_nonce_generation(p_cb);
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
      /* user has to provide passkey */
      p_cb->cb_evt = SMP_PASSKEY_REQ_EVT;
      smp_sm_event(p_cb, SMP_TK_REQ_EVT, NULL);
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
      /* passkey has to be provided to user */
      log::verbose("Need to generate SC Passkey");
      smp_generate_passkey(p_cb, NULL);
      break;
    case SMP_MODEL_SEC_CONN_OOB:
      /* use the available OOB information */
      smp_process_secure_connection_oob_data(p_cb, NULL);
      break;
    default:
      log::error("Association Model={} is not used in LE SC",
                 p_cb->selected_association_model);
      break;
  }
}

/*******************************************************************************
 * Function     smp_process_local_nonce
 * Description  The function processes new local nonce.
 *
 * Note         It is supposed to be called in SC phase1.
 ******************************************************************************/
void smp_process_local_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  switch (p_cb->selected_association_model) {
    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
      if (p_cb->role == HCI_ROLE_PERIPHERAL) {
        /* peripheral calculates and sends local commitment */
        smp_calculate_local_commitment(p_cb);
        smp_send_commitment(p_cb, NULL);
        /* peripheral has to wait for peer nonce */
        smp_set_state(SMP_STATE_WAIT_NONCE);
      } else /* i.e. central */
      {
        if (p_cb->flags & SMP_PAIR_FLAG_HAVE_PEER_COMM) {
          /* peripheral commitment is already received, send local nonce, wait
           * for remote nonce*/
          log::verbose(
              "central in assoc mode={} already rcvd peripheral commitment - "
              "race condition",
              p_cb->selected_association_model);
          p_cb->flags &= ~SMP_PAIR_FLAG_HAVE_PEER_COMM;
          smp_send_rand(p_cb, NULL);
          smp_set_state(SMP_STATE_WAIT_NONCE);
        }
      }
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
      smp_calculate_local_commitment(p_cb);

      if (p_cb->role == HCI_ROLE_CENTRAL) {
        smp_send_commitment(p_cb, NULL);
      } else /* peripheral */
      {
        if (p_cb->flags & SMP_PAIR_FLAG_HAVE_PEER_COMM) {
          /* central commitment is already received */
          smp_send_commitment(p_cb, NULL);
          smp_set_state(SMP_STATE_WAIT_NONCE);
        }
      }
      break;
    case SMP_MODEL_SEC_CONN_OOB:
      if (p_cb->role == HCI_ROLE_CENTRAL) {
        smp_send_rand(p_cb, NULL);
      }

      smp_set_state(SMP_STATE_WAIT_NONCE);
      break;
    default:
      log::error("Association Model={} is not used in LE SC",
                 p_cb->selected_association_model);
      break;
  }
}

/*******************************************************************************
 * Function     smp_process_peer_nonce
 * Description  The function processes newly received and saved in CB peer
 *              nonce. The actions depend on the selected association model and
 *              the role.
 *
 * Note         It is supposed to be called in SC phase1.
 ******************************************************************************/
void smp_process_peer_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}, selected_association_model:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda),
               p_cb->selected_association_model);

  // PTS Testing failure modes
  if (p_cb->cert_failure == SMP_CONFIRM_VALUE_ERR) {
    log::error("failure case={}", p_cb->cert_failure);
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_CONFIRM_VALUE_ERR;
    p_cb->failure = SMP_CONFIRM_VALUE_ERR;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }
  // PTS Testing failure modes (for LT)
  if ((p_cb->cert_failure == SMP_NUMERIC_COMPAR_FAIL) &&
      (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS) &&
      (p_cb->role == HCI_ROLE_PERIPHERAL)) {
    log::error("failure case={}", p_cb->cert_failure);
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_NUMERIC_COMPAR_FAIL;
    p_cb->failure = SMP_NUMERIC_COMPAR_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  switch (p_cb->selected_association_model) {
    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
      /* in these models only central receives commitment */
      if (p_cb->role == HCI_ROLE_CENTRAL) {
        if (!smp_check_commitment(p_cb)) {
          tSMP_INT_DATA smp_int_data;
          smp_int_data.status = SMP_CONFIRM_VALUE_ERR;
          p_cb->failure = SMP_CONFIRM_VALUE_ERR;
          smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
          break;
        }
      } else {
        /* peripheral sends local nonce */
        smp_send_rand(p_cb, NULL);
      }

      if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS) {
        if (!GetInterfaceToProfiles()->config->isAndroidTVDevice() &&
            (p_cb->local_io_capability == SMP_IO_CAP_IO ||
             p_cb->local_io_capability == SMP_IO_CAP_KBDISP)) {
          /* display consent dialog */
          log::verbose("JUST WORKS showing Consent Dialog");
          p_cb->cb_evt = SMP_CONSENT_REQ_EVT;
          smp_set_state(SMP_STATE_WAIT_NONCE);
          smp_sm_event(p_cb, SMP_SC_DSPL_NC_EVT, NULL);
        } else {
          /* go directly to phase 2 */
          smp_sm_event(p_cb, SMP_SC_PHASE1_CMPLT_EVT, NULL);
        }
      } else /* numeric comparison */
      {
        smp_set_state(SMP_STATE_WAIT_NONCE);
        smp_sm_event(p_cb, SMP_SC_CALC_NC_EVT, NULL);
      }
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
      if (!smp_check_commitment(p_cb) &&
          p_cb->cert_failure != SMP_NUMERIC_COMPAR_FAIL) {
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_CONFIRM_VALUE_ERR;
        p_cb->failure = SMP_CONFIRM_VALUE_ERR;
        smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
        break;
      }

      if (p_cb->role == HCI_ROLE_PERIPHERAL) {
        smp_send_rand(p_cb, NULL);
      }

      if (++p_cb->round < 20) {
        smp_set_state(SMP_STATE_SEC_CONN_PHS1_START);
        p_cb->flags &= ~SMP_PAIR_FLAG_HAVE_PEER_COMM;
        smp_start_nonce_generation(p_cb);
        break;
      }

      smp_sm_event(p_cb, SMP_SC_PHASE1_CMPLT_EVT, NULL);
      break;
    case SMP_MODEL_SEC_CONN_OOB:
      if (p_cb->role == HCI_ROLE_PERIPHERAL) {
        smp_send_rand(p_cb, NULL);
      }

      smp_sm_event(p_cb, SMP_SC_PHASE1_CMPLT_EVT, NULL);
      break;
    default:
      log::error("Association Model={} is not used in LE SC",
                 p_cb->selected_association_model);
      break;
  }
}

/*******************************************************************************
 * Function     smp_match_dhkey_checks
 * Description  checks if the calculated peer DHKey Check value is the same as
 *              received from the peer DHKey check value.
 ******************************************************************************/
void smp_match_dhkey_checks(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (memcmp(p_data->key.p_data, p_cb->remote_dhkey_check.data(),
             OCTET16_LEN)) {
    log::warn("dhkey chcks do no match");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_DHKEY_CHK_FAIL;
    p_cb->failure = SMP_DHKEY_CHK_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  /* compare the max encryption key size, and save the smaller one for the link
   */
  if (p_cb->peer_enc_size < p_cb->loc_enc_size)
    p_cb->loc_enc_size = p_cb->peer_enc_size;

  if (p_cb->role == HCI_ROLE_PERIPHERAL) {
    smp_sm_event(p_cb, SMP_PAIR_DHKEY_CHCK_EVT, NULL);
  } else {
    /* central device always use received i/r key as keys to distribute */
    p_cb->local_i_key = p_cb->peer_i_key;
    p_cb->local_r_key = p_cb->peer_r_key;
    smp_sm_event(p_cb, SMP_ENC_REQ_EVT, NULL);
  }
}

/*******************************************************************************
 * Function     smp_move_to_secure_connections_phase2
 * Description  Signal State Machine to start SC phase 2 initialization (to
 *              compute local DHKey Check value).
 *
 * Note         SM is supposed to be in the state SMP_STATE_SEC_CONN_PHS2_START.
 ******************************************************************************/
void smp_move_to_secure_connections_phase2(tSMP_CB* p_cb,
                                           tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_sm_event(p_cb, SMP_SC_PHASE1_CMPLT_EVT, NULL);
}

/*******************************************************************************
 * Function     smp_phase_2_dhkey_checks_are_present
 * Description  generates event if dhkey check from the peer is already
 *              received.
 *
 * Note         It is supposed to be used on peripheral to prevent race
 *condition. It is supposed to be called after peripheral dhkey check is
 *              calculated.
 ******************************************************************************/
void smp_phase_2_dhkey_checks_are_present(tSMP_CB* p_cb,
                                          tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->flags & SMP_PAIR_FLAG_HAVE_PEER_DHK_CHK)
    smp_sm_event(p_cb, SMP_SC_2_DHCK_CHKS_PRES_EVT, NULL);
}

/*******************************************************************************
 * Function     smp_wait_for_both_public_keys
 * Description  generates SMP_BOTH_PUBL_KEYS_RCVD_EVT event when both local and
 *              central public keys are available.
 *
 * Note         on the peripheral it is used to prevent race condition.
 *
 ******************************************************************************/
void smp_wait_for_both_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if ((p_cb->flags & SMP_PAIR_FLAG_HAVE_PEER_PUBL_KEY) &&
      (p_cb->flags & SMP_PAIR_FLAG_HAVE_LOCAL_PUBL_KEY)) {
    if ((p_cb->role == HCI_ROLE_PERIPHERAL) &&
        ((p_cb->req_oob_type == SMP_OOB_LOCAL) ||
         (p_cb->req_oob_type == SMP_OOB_BOTH))) {
      smp_set_state(SMP_STATE_PUBLIC_KEY_EXCH);
    }
    smp_sm_event(p_cb, SMP_BOTH_PUBL_KEYS_RCVD_EVT, NULL);
  }
}

/*******************************************************************************
 * Function     smp_start_passkey_verification
 * Description  Starts SC passkey entry verification.
 ******************************************************************************/
void smp_start_passkey_verification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t* p = NULL;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  p = p_cb->local_random.data();
  UINT32_TO_STREAM(p, p_data->passkey);

  p = p_cb->peer_random.data();
  UINT32_TO_STREAM(p, p_data->passkey);

  p_cb->round = 0;
  smp_start_nonce_generation(p_cb);
}

/*******************************************************************************
 * Function     smp_process_secure_connection_oob_data
 * Description  Processes local/peer SC OOB data received from somewhere.
 ******************************************************************************/
void smp_process_secure_connection_oob_data(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  tSMP_SC_OOB_DATA* p_sc_oob_data = &p_cb->sc_oob_data;
  if (p_sc_oob_data->loc_oob_data.present) {
    p_cb->local_random = p_sc_oob_data->loc_oob_data.randomizer;
  } else {
    log::verbose("local OOB randomizer is absent");
    p_cb->local_random = {0};
  }

  if (p_cb->peer_oob_flag == SMP_OOB_PRESENT && !p_sc_oob_data->loc_oob_data.present) {
    log::warn(
        "local OOB data is not present but peer claims to have received it; dropping "
        "connection", __func__);
    tSMP_INT_DATA smp_int_data{};
    smp_int_data.status = SMP_OOB_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  if (!p_sc_oob_data->peer_oob_data.present) {
    log::verbose("peer OOB data is absent");
    p_cb->peer_random = {0};
  } else {
    p_cb->peer_random = p_sc_oob_data->peer_oob_data.randomizer;
    p_cb->remote_commitment = p_sc_oob_data->peer_oob_data.commitment;

    /* check commitment */
    if (!smp_check_commitment(p_cb)) {
      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = SMP_CONFIRM_VALUE_ERR;
      p_cb->failure = SMP_CONFIRM_VALUE_ERR;
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      return;
    }

    if (p_cb->peer_oob_flag != SMP_OOB_PRESENT) {
      /* the peer doesn't have local randomiser */
      log::verbose(
          "peer didn't receive local OOB data, set local randomizer to 0");
      p_cb->local_random = {0};
    }
  }

  print128(p_cb->local_random, "local OOB randomizer");
  print128(p_cb->peer_random, "peer OOB randomizer");
  smp_start_nonce_generation(p_cb);
}

/*******************************************************************************
 * Function     smp_set_local_oob_keys
 * Description  Saves calculated private/public keys in
 *              sc_oob_data.loc_oob_data, starts nonce generation
 *              (to be saved in sc_oob_data.loc_oob_data.randomizer).
 ******************************************************************************/
void smp_set_local_oob_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  memcpy(p_cb->sc_oob_data.loc_oob_data.private_key_used, p_cb->private_key,
         BT_OCTET32_LEN);
  p_cb->sc_oob_data.loc_oob_data.publ_key_used = p_cb->loc_publ_key;
  smp_start_nonce_generation(p_cb);
}

/*******************************************************************************
 * Function     smp_set_local_oob_random_commitment
 * Description  Saves calculated randomizer and commitment in
 *              sc_oob_data.loc_oob_data, passes sc_oob_data.loc_oob_data up
 *              for safekeeping.
 ******************************************************************************/
void smp_set_local_oob_random_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  p_cb->sc_oob_data.loc_oob_data.randomizer = p_cb->rand;

  p_cb->sc_oob_data.loc_oob_data.commitment =
      crypto_toolbox::f4(p_cb->sc_oob_data.loc_oob_data.publ_key_used.x,
                         p_cb->sc_oob_data.loc_oob_data.publ_key_used.x,
                         p_cb->sc_oob_data.loc_oob_data.randomizer, 0);

  p_cb->sc_oob_data.loc_oob_data.present = true;

  /* pass created OOB data up */
  p_cb->cb_evt = SMP_SC_LOC_OOB_DATA_UP_EVT;
  smp_send_app_cback(p_cb, NULL);

  // Store the data for later use when we are paired with
  // Event though the doc above says to pass up for safe keeping it never gets
  // kept safe. Additionally, when we need the data to make a decision we
  // wouldn't have it.  This will save the sc_oob_data in the smp_keys.cc such
  // that when we receive a request to create new keys we check to see if the
  // sc_oob_data exists and utilize the keys that are stored there otherwise the
  // connector will fail commitment check and dhkey exchange.
  smp_save_local_oob_data(p_cb);

  p_cb->reset();
}

/*******************************************************************************
 *
 * Function         smp_link_encrypted
 *
 * Description      This function is called when link is encrypted and notified
 *                  to the peripheral device. Proceed to to send LTK, DIV and ER
 *to central if bonding the devices.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_link_encrypted(const RawAddress& bda, uint8_t encr_enable) {
  tSMP_CB* p_cb = &smp_cb;

  if (smp_cb.pairing_bda == bda) {
    log::debug("SMP encryption enable:{} device:{}", encr_enable,
               ADDRESS_TO_LOGGABLE_CSTR(bda));

    /* encryption completed with STK, remember the key size now, could be
     * overwritten when key exchange happens                                 */
    if (p_cb->loc_enc_size != 0 && encr_enable) {
      /* update the link encryption key size if a SMP pairing just performed */
      btm_ble_update_sec_key_size(bda, p_cb->loc_enc_size);
    }

    tSMP_INT_DATA smp_int_data = {
        // TODO This is not a tSMP_STATUS
        .status = static_cast<tSMP_STATUS>(encr_enable),
    };

    smp_sm_event(&smp_cb, SMP_ENCRYPTED_EVT, &smp_int_data);
  } else {
    log::warn(
        "SMP state machine busy so skipping encryption enable:{} device:{}",
        encr_enable, ADDRESS_TO_LOGGABLE_CSTR(bda));
  }
}

void smp_cancel_start_encryption_attempt() {
  log::error("Encryption request cancelled");
  smp_sm_event(&smp_cb, SMP_DISCARD_SEC_REQ_EVT, NULL);
}

/*******************************************************************************
 *
 * Function         smp_proc_ltk_request
 *
 * Description      This function is called when LTK request is received from
 *                  controller.
 *
 * Returns          void
 *
 ******************************************************************************/
bool smp_proc_ltk_request(const RawAddress& bda) {
  log::verbose("addr:{},state={}", ADDRESS_TO_LOGGABLE_CSTR(bda), smp_cb.state);
  bool match = false;

  if (bda == smp_cb.pairing_bda) {
    match = true;
  } else {
    tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bda);
    if (p_dev_rec != NULL && p_dev_rec->ble.pseudo_addr == smp_cb.pairing_bda &&
        p_dev_rec->ble.pseudo_addr != RawAddress::kEmpty) {
      match = true;
    }
  }

  if (match && smp_cb.state == SMP_STATE_ENCRYPTION_PENDING) {
    smp_sm_event(&smp_cb, SMP_ENC_REQ_EVT, NULL);
    return true;
  }

  return false;
}

/*******************************************************************************
 *
 * Function         smp_process_secure_connection_long_term_key
 *
 * Description      This function is called to process SC LTK.
 *                  SC LTK is calculated and used instead of STK.
 *                  Here SC LTK is saved in BLE DB.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_process_secure_connection_long_term_key(void) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_save_secure_connections_long_term_key(p_cb);

  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ENC, false);
  smp_key_distribution(p_cb, NULL);
}

/*******************************************************************************
 *
 * Function         smp_set_derive_link_key
 *
 * Description      This function is called to set flag that indicates that
 *                  BR/EDR LK has to be derived from LTK after all keys are
 *                  distributed.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_set_derive_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  p_cb->derive_lk = true;
  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_LK, false);
  smp_key_distribution(p_cb, NULL);
}

/*******************************************************************************
 *
 * Function         smp_derive_link_key_from_long_term_key
 *
 * Description      This function is called to derive BR/EDR LK from LTK.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_derive_link_key_from_long_term_key(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data) {
  tSMP_STATUS status = SMP_PAIR_FAIL_UNKNOWN;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (!smp_calculate_link_key_from_long_term_key(p_cb)) {
    log::error("calc link key failed");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = status;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }
}

/*******************************************************************************
 *
 * Function         smp_br_process_link_key
 *
 * Description      This function is called to process BR/EDR LK:
 *                  - to derive SMP LTK from BR/EDR LK;
 *                  - to save SMP LTK.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_br_process_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  tSMP_STATUS status = SMP_PAIR_FAIL_UNKNOWN;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (!smp_calculate_long_term_key_from_link_key(p_cb)) {
    log::error("calc LTK failed");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = status;
    smp_sm_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(p_cb->pairing_bda);
  if (p_dev_rec) {
    log::verbose("dev_type={}", p_dev_rec->device_type);
    p_dev_rec->device_type |= BT_DEVICE_TYPE_BLE;
  } else {
    log::error("failed to find Security Record");
  }

  log::verbose("LTK derivation from LK successfully completed");
  smp_save_secure_connections_long_term_key(p_cb);
  smp_update_key_mask(p_cb, SMP_SEC_KEY_TYPE_ENC, false);
  smp_br_select_next_key(p_cb, NULL);
}

/*******************************************************************************
 * Function     smp_key_distribution_by_transport
 * Description  depending on the transport used at the moment calls either
 *              smp_key_distribution(...) or smp_br_key_distribution(...).
 ******************************************************************************/
static void smp_key_distribution_by_transport(tSMP_CB* p_cb,
                                              tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (p_cb->smp_over_br) {
    smp_br_select_next_key(p_cb, NULL);
  } else {
    smp_key_distribution(p_cb, NULL);
  }
}

/*******************************************************************************
 * Function         smp_br_pairing_complete
 * Description      This function is called to send the pairing complete
 *                  callback and remove the connection if needed.
 ******************************************************************************/
void smp_br_pairing_complete(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->total_tx_unacked == 0) {
    /* process the pairing complete */
    smp_proc_pairing_cmpl(p_cb);
  }
}
