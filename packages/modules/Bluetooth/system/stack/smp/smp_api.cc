/******************************************************************************
 *
 *  Copyright 2008-2012 Broadcom Corporation
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
 *  This file contains the implementation of the SMP interface used by
 *  applications that can run over an SMP.
 *
 ******************************************************************************/
#define LOG_TAG "smp"

#include "smp_api.h"

#include <bluetooth/log.h>
#include <string.h>

#include "l2c_api.h"
#include "l2cdefs.h"
#include "os/log.h"
#include "smp_int.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_sec_api_types.h"
#include "types/raw_address.h"

using namespace bluetooth;

/*******************************************************************************
 *
 * Function         SMP_Init
 *
 * Description      This function initializes the SMP unit.
 *
 * Returns          void
 *
 ******************************************************************************/
void SMP_Init(uint8_t init_security_mode) { smp_cb.init(init_security_mode); }

/*******************************************************************************
 *
 * Function         SMP_Register
 *
 * Description      This function register for the SMP services callback.
 *
 * Returns          void
 *
 ******************************************************************************/
bool SMP_Register(tSMP_CALLBACK* p_cback) {
  log::verbose("state={}", smp_cb.state);

  if (smp_cb.p_callback != NULL) {
    log::error("duplicate registration, overwrite it");
  }
  smp_cb.p_callback = p_cback;

  return (true);
}

/*******************************************************************************
 *
 * Function         SMP_Pair
 *
 * Description      This function call to perform a SMP pairing with peer
 *                  device. Device support one SMP pairing at one time.
 *
 * Parameters       bd_addr - peer device bd address.
 *
 * Returns          None
 *
 ******************************************************************************/
tSMP_STATUS SMP_Pair(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("state={} br_state={} flag=0x{:x}, bd_addr={}", p_cb->state,
               p_cb->br_state, p_cb->flags, ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  if (p_cb->state != SMP_STATE_IDLE ||
      p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD || p_cb->smp_over_br) {
    /* pending security on going, reject this one */
    return SMP_BUSY;
  } else {
    p_cb->flags = SMP_PAIR_FLAGS_WE_STARTED_DD;
    p_cb->pairing_bda = bd_addr;

    p_cb->pairing_ble_bd_addr = {
        .type = addr_type,
        .bda = bd_addr,
    };
    if (!L2CA_ConnectFixedChnl(L2CAP_SMP_CID, bd_addr)) {
      tSMP_INT_DATA smp_int_data;
      smp_int_data.status = SMP_PAIR_INTERNAL_ERR;
      p_cb->status = SMP_PAIR_INTERNAL_ERR;
      log::error("L2C connect fixed channel failed.");
      smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      return SMP_PAIR_INTERNAL_ERR;
    }

    return SMP_STARTED;
  }
}

tSMP_STATUS SMP_Pair(const RawAddress& bd_addr) {
  return SMP_Pair(bd_addr, BLE_ADDR_PUBLIC);
}

/*******************************************************************************
 *
 * Function         SMP_BR_PairWith
 *
 * Description      This function is called to start a SMP pairing over BR/EDR.
 *                  Device support one SMP pairing at one time.
 *
 * Parameters       bd_addr - peer device bd address.
 *
 * Returns          SMP_STARTED if pairing started, otherwise the reason for
 *                  failure.
 *
 ******************************************************************************/
tSMP_STATUS SMP_BR_PairWith(const RawAddress& bd_addr) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("state={} br_state={} flag=0x{:x}, bd_addr={}", p_cb->state,
               p_cb->br_state, p_cb->flags, ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  if (p_cb->state != SMP_STATE_IDLE || p_cb->smp_over_br ||
      p_cb->flags & SMP_PAIR_FLAGS_WE_STARTED_DD) {
    /* pending security on going, reject this one */
    return SMP_BUSY;
  }

  p_cb->role = HCI_ROLE_CENTRAL;
  p_cb->flags = SMP_PAIR_FLAGS_WE_STARTED_DD;
  p_cb->smp_over_br = true;
  p_cb->pairing_bda = bd_addr;

  if (!L2CA_ConnectFixedChnl(L2CAP_SMP_BR_CID, bd_addr)) {
    log::error("L2C connect fixed channel failed.");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_PAIR_INTERNAL_ERR;
    p_cb->status = SMP_PAIR_INTERNAL_ERR;
    smp_br_state_machine_event(p_cb, SMP_BR_AUTH_CMPL_EVT, &smp_int_data);
    return SMP_PAIR_INTERNAL_ERR;
  }

  return SMP_STARTED;
}

/*******************************************************************************
 *
 * Function         SMP_PairCancel
 *
 * Description      This function call to cancel a SMP pairing with peer device.
 *
 * Parameters       bd_addr - peer device bd address.
 *
 * Returns          true - Pairining is cancelled
 *
 ******************************************************************************/
bool SMP_PairCancel(const RawAddress& bd_addr) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("state={} flag=0x{:x}", p_cb->state, p_cb->flags);
  if (p_cb->state != SMP_STATE_IDLE && p_cb->pairing_bda == bd_addr) {
    p_cb->is_pair_cancel = true;
    log::verbose("set fail reason Unknown");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_PAIR_FAIL_UNKNOWN;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return true;
  }

  return false;
}
/*******************************************************************************
 *
 * Function         SMP_SecurityGrant
 *
 * Description      This function is called to grant security process.
 *
 * Parameters       bd_addr - peer device bd address.
 *                  res     - result of the operation SMP_SUCCESS if success.
 *                            Otherwise, SMP_REPEATED_ATTEMPTS if too many
 *                            attempts.
 *
 * Returns          None
 *
 ******************************************************************************/
void SMP_SecurityGrant(const RawAddress& bd_addr, tSMP_STATUS res) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  // If just showing consent dialog, send response
  if (smp_cb.cb_evt == SMP_CONSENT_REQ_EVT) {
    // If JUSTWORKS, this is used to display the consent dialog
    if (smp_cb.selected_association_model == SMP_MODEL_SEC_CONN_JUSTWORKS) {
      if (res == SMP_SUCCESS) {
        smp_sm_event(&smp_cb, SMP_SC_NC_OK_EVT, NULL);
      } else {
        log::warn("Consent dialog fails for JUSTWORKS");
        /* send pairing failure */
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_NUMERIC_COMPAR_FAIL;
        smp_sm_event(&smp_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      }
    } else if (smp_cb.selected_association_model == SMP_MODEL_ENCRYPTION_ONLY) {
      if (res == SMP_SUCCESS) {
        smp_cb.sec_level = SMP_SEC_UNAUTHENTICATE;

        tSMP_KEY key;
        tSMP_INT_DATA smp_int_data;
        key.key_type = SMP_KEY_TYPE_TK;
        key.p_data = smp_cb.tk.data();
        smp_int_data.key = key;

        smp_cb.tk = {0};
        smp_sm_event(&smp_cb, SMP_KEY_READY_EVT, &smp_int_data);
      } else {
        log::warn("Consent dialog fails for ENCRYPTION_ONLY");
        /* send pairing failure */
        tSMP_INT_DATA smp_int_data;
        smp_int_data.status = SMP_NUMERIC_COMPAR_FAIL;
        smp_sm_event(&smp_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
      }
    }
    return;
  }

  if (smp_cb.smp_over_br) {
    if (smp_cb.br_state != SMP_BR_STATE_WAIT_APP_RSP ||
        smp_cb.cb_evt != SMP_SEC_REQUEST_EVT || smp_cb.pairing_bda != bd_addr) {
      return;
    }

    /* clear the SMP_SEC_REQUEST_EVT event after get grant */
    /* avoid generating duplicate pair request */
    smp_cb.cb_evt = SMP_EVT_NONE;
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = res;
    smp_br_state_machine_event(&smp_cb, SMP_BR_API_SEC_GRANT_EVT,
                               &smp_int_data);
    return;
  }

  if (smp_cb.state != SMP_STATE_WAIT_APP_RSP ||
      smp_cb.cb_evt != SMP_SEC_REQUEST_EVT || smp_cb.pairing_bda != bd_addr)
    return;
  /* clear the SMP_SEC_REQUEST_EVT event after get grant */
  /* avoid generate duplicate pair request */
  smp_cb.cb_evt = SMP_EVT_NONE;
  tSMP_INT_DATA smp_int_data;
  smp_int_data.status = res;
  smp_sm_event(&smp_cb, SMP_API_SEC_GRANT_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         SMP_PasskeyReply
 *
 * Description      This function is called when the user replies
 *                  passkey after being requested.
 *
 * Parameters:      bd_addr - Address of the device for which passkey was
 *                            requested
 *                  res     - result of the operation SMP_SUCCESS if success
 *                  passkey - numeric value in the range of
 *                            BTM_MIN_PASSKEY_VAL(0) -
 *                            BTM_MAX_PASSKEY_VAL(999999(0xF423F)).
 *
 ******************************************************************************/
void SMP_PasskeyReply(const RawAddress& bd_addr, uint8_t res,
                      uint32_t passkey) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("Key:{}  Result:{}", passkey, res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (p_cb->cb_evt != SMP_PASSKEY_REQ_EVT) {
    log::warn("Wrong State:{}", p_cb->state);
    return;
  }

  if (bd_addr != p_cb->pairing_bda) {
    log::error("Wrong BD Addr");
    return;
  }

  if (passkey > BTM_MAX_PASSKEY_VAL || res != SMP_SUCCESS) {
    log::warn("Invalid passkey value:{} or passkey entry fail", passkey);
    /* send pairing failure */
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_PASSKEY_ENTRY_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  } else if (p_cb->selected_association_model ==
             SMP_MODEL_SEC_CONN_PASSKEY_ENT) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.passkey = passkey;
    smp_sm_event(&smp_cb, SMP_SC_KEY_READY_EVT, &smp_int_data);
  } else {
    smp_convert_string_to_tk(&p_cb->tk, passkey);
  }
}

/*******************************************************************************
 *
 * Function         SMP_ConfirmReply
 *
 * Description      This function is called after Security Manager submitted
 *                  numeric comparison request to the application.
 *
 * Parameters:      bd_addr      - Address of the device with which numeric
 *                                 comparison was requested
 *                  res          - comparison result SMP_SUCCESS if success
 *
 ******************************************************************************/
void SMP_ConfirmReply(const RawAddress& bd_addr, uint8_t res) {
  tSMP_CB* p_cb = &smp_cb;

  log::verbose("addr:{}, Result:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr), res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (p_cb->cb_evt != SMP_NC_REQ_EVT) {
    log::warn("Wrong State:{}", p_cb->state);
    return;
  }

  if (bd_addr != p_cb->pairing_bda) {
    log::error("Wrong BD Addr");
    return;
  }

  if (res != SMP_SUCCESS) {
    log::warn("Numeric Comparison fails");
    /* send pairing failure */
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_NUMERIC_COMPAR_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  } else {
    smp_sm_event(p_cb, SMP_SC_NC_OK_EVT, NULL);
  }
}

/*******************************************************************************
 *
 * Function         SMP_OobDataReply
 *
 * Description      This function is called to provide the OOB data for
 *                  SMP in response to SMP_OOB_REQ_EVT
 *
 * Parameters:      bd_addr     - Address of the peer device
 *                  res         - result of the operation SMP_SUCCESS if success
 *                  p_data      - simple pairing Randomizer  C.
 *
 ******************************************************************************/
void SMP_OobDataReply(const RawAddress& bd_addr, tSMP_STATUS res, uint8_t len,
                      uint8_t* p_data) {
  tSMP_CB* p_cb = &smp_cb;
  tSMP_KEY key;

  log::verbose("State:{}  res:{}", smp_cb.state, res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (p_cb->state != SMP_STATE_WAIT_APP_RSP || p_cb->cb_evt != SMP_OOB_REQ_EVT)
    return;

  if (res != SMP_SUCCESS || len == 0 || !p_data) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_OOB_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  } else {
    if (len > OCTET16_LEN) len = OCTET16_LEN;

    memcpy(p_cb->tk.data(), p_data, len);

    key.key_type = SMP_KEY_TYPE_TK;
    key.p_data = p_cb->tk.data();

    tSMP_INT_DATA smp_int_data;
    smp_int_data.key = key;
    smp_sm_event(&smp_cb, SMP_KEY_READY_EVT, &smp_int_data);
  }
}

/*******************************************************************************
 *
 * Function         SMP_SecureConnectionOobDataReply
 *
 * Description      This function is called to provide the SC OOB data for
 *                  SMP in response to SMP_SC_OOB_REQ_EVT
 *
 * Parameters:      p_data      - pointer to the data
 *
 ******************************************************************************/
void SMP_SecureConnectionOobDataReply(uint8_t* p_data) {
  tSMP_CB* p_cb = &smp_cb;

  tSMP_SC_OOB_DATA* p_oob = (tSMP_SC_OOB_DATA*)p_data;
  if (!p_oob) {
    log::error("received no data");
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_OOB_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  log::verbose(
      "req_oob_type:{}, loc_oob_data.present:{}, peer_oob_data.present:{}",
      p_cb->req_oob_type, p_oob->loc_oob_data.present,
      p_oob->peer_oob_data.present);

  if (p_cb->state != SMP_STATE_WAIT_APP_RSP ||
      p_cb->cb_evt != SMP_SC_OOB_REQ_EVT)
    return;

  bool data_missing = false;
  switch (p_cb->req_oob_type) {
    case SMP_OOB_PEER:
      if (!p_oob->peer_oob_data.present) data_missing = true;
      break;
    case SMP_OOB_LOCAL:
      if (!p_oob->loc_oob_data.present) data_missing = true;
      break;
    case SMP_OOB_BOTH:
      // Check for previous local OOB data in cache
      // This would be in the case data was generated BEFORE pairing was
      // attempted and this instance is the connector or pairing initiator.
      // [NOTICE]: Overridding data present here if the data exists so state
      // machine asks for it later
      p_oob->loc_oob_data.present = smp_has_local_oob_data();
      if (!p_oob->loc_oob_data.present || !p_oob->peer_oob_data.present)
        data_missing = true;
      break;
    default:
      log::verbose("Unexpected OOB data type requested. Fail OOB");
      data_missing = true;
      break;
  }

  tSMP_INT_DATA smp_int_data;
  if (data_missing) {
    smp_int_data.status = SMP_OOB_FAIL;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  p_cb->sc_oob_data = *p_oob;

  smp_int_data.p_data = p_data;
  smp_sm_event(&smp_cb, SMP_SC_OOB_DATA_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         SMP_CrLocScOobData
 *
 * Description      This function is called to generate a public key to be
 *                  passed to a remote device via Out of Band transport.
 *
 * Returns          true if the request is successfully sent and executed by the
 *                  state machine, false otherwise
 *
 ******************************************************************************/
bool SMP_CrLocScOobData() {
  tSMP_INT_DATA smp_int_data;
  return smp_sm_event(&smp_cb, SMP_CR_LOC_SC_OOB_DATA_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         SMP_ClearLocScOobData
 *
 * Description      This function is called to clear out the OOB stored locally.
 *
 ******************************************************************************/
void SMP_ClearLocScOobData() { smp_clear_local_oob_data(); }

/*******************************************************************************
 *
 * Function         SMP_SirkConfirmDeviceReply
 *
 * Description      This function is called after Security Manager submitted
 *                  verification of device with CSIP.
 *
 * Parameters:      bd_addr      - Address of the device with which verification
 *                                 was requested
 *                  res          - comparison result SMP_SUCCESS if success
 *
 ******************************************************************************/
void SMP_SirkConfirmDeviceReply(const RawAddress& bd_addr, uint8_t res) {
  tSMP_CB* p_cb = &smp_cb;

  log::info("Result:{}", res);

  /* If timeout already expired or has been canceled, ignore the reply */
  if (p_cb->cb_evt != SMP_SIRK_VERIFICATION_REQ_EVT) {
    log::warn("Wrong State:{}", p_cb->state);
    return;
  }

  if (bd_addr != p_cb->pairing_bda) {
    log::warn("Wrong confirmation BD Addr: {} vs expected {}",
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr),
              ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
    return;
  }

  tSMP_INT_DATA smp_int_data;
  if (res != SMP_SUCCESS) {
    log::warn("Verification fails");
    /* send pairing failure */
    smp_int_data.status = SMP_SIRK_DEVICE_INVALID;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
  } else {
    smp_int_data.status = SMP_SUCCESS;
    smp_sm_event(p_cb, SMP_SIRK_DEVICE_VALID_EVT, &smp_int_data);
  }
}
