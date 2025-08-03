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

/******************************************************************************
 *
 *  This file contains security manager protocol utility functions
 *
 ******************************************************************************/
#define LOG_TAG "smp"

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <bluetooth/log.h>

#include <algorithm>
#include <cstdint>
#include <cstring>

#include "crypto_toolbox/crypto_toolbox.h"
#include "hci/controller_interface.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "osi/include/osi.h"
#include "p_256_ecc_pp.h"
#include "smp_int.h"
#include "stack/btm/btm_ble_sec.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_ble_api.h"
#include "stack/include/btm_ble_sec_api.h"
#include "types/raw_address.h"

using bluetooth::common::BindOnce;
using bluetooth::common::OnceCallback;
using crypto_toolbox::aes_128;
using namespace bluetooth;

#ifndef SMP_MAX_ENC_REPEAT
#define SMP_MAX_ENC_REPEAT 3
#endif

static void smp_process_stk(tSMP_CB* p_cb, Octet16* p);
static Octet16 smp_calculate_legacy_short_term_key(tSMP_CB* p_cb);
static void smp_process_private_key(tSMP_CB* p_cb);

static void send_ble_rand(OnceCallback<void(uint64_t)> callback);

#define SMP_PASSKEY_MASK 0x000fffff

// If there is data saved here, then use its info instead
// This needs to be cleared on a successfult pairing using the oob data
static tSMP_LOC_OOB_DATA saved_local_oob_data = {};

void smp_save_local_oob_data(tSMP_CB* p_cb) {
  saved_local_oob_data = p_cb->sc_oob_data.loc_oob_data;
}

void smp_clear_local_oob_data() { saved_local_oob_data = {}; }

static bool is_oob_data_empty(tSMP_LOC_OOB_DATA* data) {
  tSMP_LOC_OOB_DATA empty_data = {};
  return memcmp(data, &empty_data, sizeof(tSMP_LOC_OOB_DATA)) == 0;
}

bool smp_has_local_oob_data() {
  return !is_oob_data_empty(&saved_local_oob_data);
}

void smp_debug_print_nbyte_little_endian(uint8_t* p, const char* key_name,
                                         uint8_t len) {}

inline void smp_debug_print_nbyte_little_endian(const Octet16& p,
                                                const char* key_name,
                                                uint8_t len) {
  smp_debug_print_nbyte_little_endian(const_cast<uint8_t*>(p.data()), key_name,
                                      len);
}

void smp_debug_print_nbyte_big_endian(uint8_t* p, const char* key_name,
                                      uint8_t len) {}

/** This function is called to process a passkey. */
void smp_proc_passkey(tSMP_CB* p_cb, uint64_t rand) {
  uint8_t* tt = p_cb->tk.data();
  uint32_t passkey = static_cast<uint32_t>(rand & SMP_PASSKEY_MASK);

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  /* truncate by maximum value */
  while (passkey > BTM_MAX_PASSKEY_VAL) passkey >>= 1;

  /* save the TK */
  p_cb->tk = {0};
  UINT32_TO_STREAM(tt, passkey);

  if (p_cb->p_callback) {
    tSMP_EVT_DATA smp_evt_data = {
        .passkey = passkey,
    };
    (*p_cb->p_callback)(SMP_PASSKEY_NOTIF_EVT, p_cb->pairing_bda,
                        &smp_evt_data);
  }

  if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_PASSKEY_DISP) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.passkey = passkey;
    smp_sm_event(&smp_cb, SMP_KEY_READY_EVT, &smp_int_data);
  } else {
    tSMP_KEY key;
    key.key_type = SMP_KEY_TYPE_TK;
    key.p_data = p_cb->tk.data();
    tSMP_INT_DATA smp_int_data;
    smp_int_data.key = key;
    smp_sm_event(p_cb, SMP_KEY_READY_EVT, &smp_int_data);
  }
}

/*******************************************************************************
 *
 * Function         smp_generate_passkey
 *
 * Description      This function is called to generate passkey.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_generate_passkey(tSMP_CB* p_cb, UNUSED_ATTR tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  /* generate MRand or SRand */
  send_ble_rand(BindOnce(&smp_proc_passkey, p_cb));
}

/*******************************************************************************
 *
 * Function         smp_generate_stk
 *
 * Description      This function is called to generate STK calculated by
 *                  running AES with the TK value as key and a concatenation of
 *                  the random values.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_generate_stk(tSMP_CB* p_cb, UNUSED_ATTR tSMP_INT_DATA* p_data) {
  Octet16 output;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->sc_mode_required_by_peer) {
    log::verbose("FOR LE SC LTK IS USED INSTEAD OF STK");
    output = p_cb->ltk;
  } else {
    output = smp_calculate_legacy_short_term_key(p_cb);
  }

  smp_process_stk(p_cb, &output);
}

/**
 * This function is called to calculate CSRK
 */
void smp_compute_csrk(uint16_t div, tSMP_CB* p_cb) {
  Octet16 buffer{}; /* for (r || DIV)  r=1*/
  uint16_t r = 1;
  uint8_t* p = buffer.data();

  p_cb->div = div;

  log::verbose("div=0x{:x}", p_cb->div);
  const Octet16& er = BTM_GetDeviceEncRoot();
  /* CSRK = d1(ER, DIV, 1) */
  UINT16_TO_STREAM(p, p_cb->div);
  UINT16_TO_STREAM(p, r);

  p_cb->csrk = aes_128(er, buffer);
  smp_send_csrk_info(p_cb, NULL);
}

/**
 * This function is called to calculate CSRK, starting with DIV generation.
 */
void smp_generate_csrk(tSMP_CB* p_cb, UNUSED_ATTR tSMP_INT_DATA* p_data) {
  bool div_status;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  div_status = btm_get_local_div(p_cb->pairing_bda, &p_cb->div);
  if (div_status) {
    smp_compute_csrk(p_cb->div, p_cb);
  } else {
    log::verbose("Generate DIV for CSRK");
    send_ble_rand(BindOnce(
        [](tSMP_CB* p_cb, uint64_t rand) {
          uint16_t div = static_cast<uint16_t>(rand);
          smp_compute_csrk(div, p_cb);
        },
        p_cb));
  }
}

/*******************************************************************************
 * Function         smp_concatenate_peer - LSB first
 *                  add pairing command sent from local device into p1.
 ******************************************************************************/
void smp_concatenate_local(tSMP_CB* p_cb, uint8_t** p_data, uint8_t op_code) {
  uint8_t* p = *p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  UINT8_TO_STREAM(p, op_code);
  UINT8_TO_STREAM(p, p_cb->local_io_capability);
  UINT8_TO_STREAM(p, p_cb->loc_oob_flag);
  UINT8_TO_STREAM(p, p_cb->loc_auth_req);
  UINT8_TO_STREAM(p, p_cb->loc_enc_size);
  UINT8_TO_STREAM(p, p_cb->local_i_key);
  UINT8_TO_STREAM(p, p_cb->local_r_key);

  *p_data = p;
}

/*******************************************************************************
 * Function         smp_concatenate_peer - LSB first
 *                  add pairing command received from peer device into p1.
 ******************************************************************************/
void smp_concatenate_peer(tSMP_CB* p_cb, uint8_t** p_data, uint8_t op_code) {
  uint8_t* p = *p_data;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  UINT8_TO_STREAM(p, op_code);
  UINT8_TO_STREAM(p, p_cb->peer_io_caps);
  UINT8_TO_STREAM(p, p_cb->peer_oob_flag);
  UINT8_TO_STREAM(p, p_cb->peer_auth_req);
  UINT8_TO_STREAM(p, p_cb->peer_enc_size);
  UINT8_TO_STREAM(p, p_cb->peer_i_key);
  UINT8_TO_STREAM(p, p_cb->peer_r_key);

  *p_data = p;
}

/** Generate Confirm/Compare Step1:
 *                  p1 = (MSB) pres || preq || rat' || iat' (LSB)
 *                  Fill in values LSB first thus
 *                  p1 = iat' || rat' || preq || pres
 */
Octet16 smp_gen_p1_4_confirm(tSMP_CB* p_cb,
                             tBLE_ADDR_TYPE remote_bd_addr_type) {
  log::verbose("pairing_addr:{}, rmt_addr_type:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda),
               AddressTypeText(remote_bd_addr_type));
  Octet16 p1;
  uint8_t* p = p1.data();
  if (p_cb->role == HCI_ROLE_CENTRAL) {
    /* iat': initiator's (local) address type */
    UINT8_TO_STREAM(p, p_cb->addr_type);
    /* rat': responder's (remote) address type */
    UINT8_TO_STREAM(p, remote_bd_addr_type);
    /* preq : Pairing Request (local) command */
    smp_concatenate_local(p_cb, &p, SMP_OPCODE_PAIRING_REQ);
    /* pres : Pairing Response (remote) command */
    smp_concatenate_peer(p_cb, &p, SMP_OPCODE_PAIRING_RSP);
  } else {
    /* iat': initiator's (remote) address type */
    UINT8_TO_STREAM(p, remote_bd_addr_type);
    /* rat': responder's (local) address type */
    UINT8_TO_STREAM(p, p_cb->addr_type);
    /* preq : Pairing Request (remote) command */
    smp_concatenate_peer(p_cb, &p, SMP_OPCODE_PAIRING_REQ);
    /* pres : Pairing Response (local) command */
    smp_concatenate_local(p_cb, &p, SMP_OPCODE_PAIRING_RSP);
  }
  smp_debug_print_nbyte_little_endian(p1, "p1 = iat' || rat' || preq || pres",
                                      16);

  return p1;
}

/** Generate Confirm/Compare Step2:
 *                  p2 = (MSB) padding || ia || ra (LSB)
 *                  Fill values LSB first and thus:
 *                  p2 = ra || ia || padding
 */
Octet16 smp_gen_p2_4_confirm(tSMP_CB* p_cb, const RawAddress& remote_bda) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  Octet16 p2{0};
  uint8_t* p = p2.data();
  /* 32-bit Padding */
  memset(p, 0, OCTET16_LEN);
  if (p_cb->role == HCI_ROLE_CENTRAL) {
    /* ra : Responder's (remote) address */
    BDADDR_TO_STREAM(p, remote_bda);
    /* ia : Initiator's (local) address */
    BDADDR_TO_STREAM(p, p_cb->local_bda);
  } else {
    /* ra : Responder's (local) address */
    BDADDR_TO_STREAM(p, p_cb->local_bda);
    /* ia : Initiator's (remote) address */
    BDADDR_TO_STREAM(p, remote_bda);
  }
  smp_debug_print_nbyte_little_endian(p2, "p2 = ra || ia || padding", 16);
  return p2;
}

/*******************************************************************************
 *
 * Function         smp_calculate_comfirm
 *
 * Description      This function (c1) is called to calculate Confirm value.
 *
 * Returns          tSMP_STATUS status of confirmation calculation
 *
 ******************************************************************************/
tSMP_STATUS smp_calculate_comfirm(tSMP_CB* p_cb, const Octet16& rand,
                                  Octet16* output) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  RawAddress remote_bda;
  tBLE_ADDR_TYPE remote_bd_addr_type = BLE_ADDR_PUBLIC;
  /* get remote connection specific bluetooth address */
  if (!BTM_ReadRemoteConnectionAddr(p_cb->pairing_bda, remote_bda,
                                    &remote_bd_addr_type, true)) {
    log::error("cannot obtain remote device address");
    return SMP_PAIR_FAIL_UNKNOWN;
  }
  /* get local connection specific bluetooth address */
  BTM_ReadConnectionAddr(p_cb->pairing_bda, p_cb->local_bda, &p_cb->addr_type,
                         true);
  /* generate p1 = pres || preq || rat' || iat' */
  Octet16 p1 = smp_gen_p1_4_confirm(p_cb, remote_bd_addr_type);
  /* p1' = rand XOR p1 */
  smp_xor_128(&p1, rand);
  smp_debug_print_nbyte_little_endian(p1, "p1' = p1 XOR r", 16);
  /* calculate e1 = e(k, p1'), where k = TK */
  smp_debug_print_nbyte_little_endian(p_cb->tk.data(), "TK", 16);
  Octet16 e1 = aes_128(p_cb->tk, p1);
  smp_debug_print_nbyte_little_endian(e1.data(), "e1 = e(k, p1')", 16);
  /* generate p2 = padding || ia || ra */
  Octet16 p2 = smp_gen_p2_4_confirm(p_cb, remote_bda);
  /* calculate p2' = (p2 XOR e1) */
  smp_xor_128(&p2, e1);
  smp_debug_print_nbyte_little_endian(p2, "p2' = p2 XOR e1", 16);
  /* calculate: c1 = e(k, p2') */
  *output = aes_128(p_cb->tk, p2);
  return SMP_SUCCESS;
}

/*******************************************************************************
 *
 * Function         smp_generate_confirm
 *
 * Description      This function is called when random number (MRand or SRand)
 *                  is generated by the controller and the stack needs to
 *                  calculate c1 value (MConfirm or SConfirm) for the first time
 *
 * Returns          void
 *
 ******************************************************************************/
static void smp_generate_confirm(tSMP_CB* p_cb) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_debug_print_nbyte_little_endian(p_cb->rand.data(), "local_rand", 16);
  Octet16 output;
  tSMP_STATUS status = smp_calculate_comfirm(p_cb, p_cb->rand, &output);
  if (status != SMP_SUCCESS) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = status;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }
  tSMP_KEY key;
  p_cb->confirm = output;
  smp_debug_print_nbyte_little_endian(p_cb->confirm, "Local Confirm generated",
                                      16);
  key.key_type = SMP_KEY_TYPE_CFM;
  key.p_data = output.data();
  tSMP_INT_DATA smp_int_data;
  smp_int_data.key = key;
  smp_sm_event(p_cb, SMP_KEY_READY_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         smp_generate_srand_mrand_confirm
 *
 * Description      This function is called to start the second pairing phase by
 *                  start generating random number.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_generate_srand_mrand_confirm(tSMP_CB* p_cb,
                                      UNUSED_ATTR tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  /* generate MRand or SRand */
  send_ble_rand(BindOnce(
      [](tSMP_CB* p_cb, uint64_t rand) {
        memcpy(p_cb->rand.data(), (uint8_t*)&rand, sizeof(uint64_t));
        /* generate 64 MSB of MRand or SRand */
        send_ble_rand(BindOnce(
            [](tSMP_CB* p_cb, uint64_t rand) {
              memcpy(p_cb->rand.data() + sizeof(uint64_t), (uint8_t*)&rand,
                     sizeof(uint64_t));
              smp_generate_confirm(p_cb);
            },
            p_cb));
      },
      p_cb));
}

/*******************************************************************************
 *
 * Function         smp_generate_compare
 *
 * Description      This function is called when random number (MRand or SRand)
 *                  is received from remote device and the c1 value (MConfirm
 *                  or SConfirm) needs to be generated to authenticate remote
 *                  device.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_generate_compare(tSMP_CB* p_cb, UNUSED_ATTR tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_debug_print_nbyte_little_endian(p_cb->rrand, "peer rand", 16);
  Octet16 output;
  tSMP_STATUS status = smp_calculate_comfirm(p_cb, p_cb->rrand, &output);
  if (status != SMP_SUCCESS) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = status;
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }
  tSMP_KEY key;
  smp_debug_print_nbyte_little_endian(output.data(), "Remote Confirm generated",
                                      16);
  key.key_type = SMP_KEY_TYPE_CMP;
  key.p_data = output.data();
  tSMP_INT_DATA smp_int_data;
  smp_int_data.key = key;
  smp_sm_event(p_cb, SMP_KEY_READY_EVT, &smp_int_data);
}

/** This function is called when STK is generated proceed to send the encrypt
 * the link using STK. */
static void smp_process_stk(tSMP_CB* p_cb, Octet16* p) {
  tSMP_KEY key;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  smp_mask_enc_key(p_cb->loc_enc_size, p);

  key.key_type = SMP_KEY_TYPE_STK;
  key.p_data = p->data();

  tSMP_INT_DATA smp_int_data;
  smp_int_data.key = key;
  smp_sm_event(p_cb, SMP_KEY_READY_EVT, &smp_int_data);
}

/** This function calculates EDIV = Y xor DIV */
static void smp_process_ediv(tSMP_CB* p_cb, Octet16& p) {
  tSMP_KEY key;
  uint8_t* pp = p.data();
  uint16_t y;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  STREAM_TO_UINT16(y, pp);

  /* EDIV = Y xor DIV */
  p_cb->ediv = p_cb->div ^ y;
  /* send LTK ready */
  key.key_type = SMP_KEY_TYPE_LTK;
  key.p_data = p.data();

  tSMP_INT_DATA smp_int_data;
  smp_int_data.key = key;
  smp_sm_event(p_cb, SMP_KEY_READY_EVT, &smp_int_data);
}

/**
 * This function is to proceed generate Y = E(DHK, Rand)
 */
static void smp_generate_y(tSMP_CB* p_cb, uint64_t rand) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  const Octet16& dhk = BTM_GetDeviceDHK();

  memcpy(p_cb->enc_rand, (uint8_t*)&rand, sizeof(uint64_t));
  Octet16 rand16{};
  memcpy(rand16.data(), (uint8_t*)&rand, sizeof(uint64_t));
  Octet16 output = aes_128(dhk, rand16);
  smp_process_ediv(p_cb, output);
}

/**
 * Calculate LTK = d1(ER, DIV, 0)= e(ER, DIV)
 */
static void smp_generate_ltk_cont(uint16_t div, tSMP_CB* p_cb) {
  p_cb->div = div;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  const Octet16& er = BTM_GetDeviceEncRoot();

  /* LTK = d1(ER, DIV, 0)= e(ER, DIV)*/
  Octet16 div16{};
  div16.data()[0] = div & 0xff;
  div16.data()[1] = div >> 8u;
  Octet16 ltk = aes_128(er, div16);
  /* mask the LTK */
  smp_mask_enc_key(p_cb->loc_enc_size, &ltk);
  p_cb->ltk = ltk;

  /* generate EDIV and rand now */
  send_ble_rand(BindOnce(&smp_generate_y, p_cb));
}

/*******************************************************************************
 *
 * Function         smp_generate_ltk
 *
 * Description      This function is called:
 *                  - in legacy pairing - to calculate LTK, starting with DIV
 *                    generation;
 *                  - in LE Secure Connections pairing over LE transport - to
 *                    process LTK already generated to encrypt LE link;
 *                  - in LE Secure Connections pairing over BR/EDR transport -
 *                    to start BR/EDR Link Key processing.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_generate_ltk(tSMP_CB* p_cb, UNUSED_ATTR tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (smp_get_br_state() == SMP_BR_STATE_BOND_PENDING) {
    smp_br_process_link_key(p_cb, NULL);
    return;
  } else if (p_cb->sc_mode_required_by_peer) {
    smp_process_secure_connection_long_term_key();
    return;
  }

  bool div_status = btm_get_local_div(p_cb->pairing_bda, &p_cb->div);

  if (div_status) {
    smp_generate_ltk_cont(p_cb->div, p_cb);
  } else {
    log::verbose("Generate DIV for LTK");

    /* generate MRand or SRand */
    send_ble_rand(BindOnce(
        [](tSMP_CB* p_cb, uint64_t rand) {
          uint16_t div = static_cast<uint16_t>(rand);
          smp_generate_ltk_cont(div, p_cb);
        },
        p_cb));
  }
}

/* The function calculates legacy STK */
Octet16 smp_calculate_legacy_short_term_key(tSMP_CB* p_cb) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  Octet16 text{};
  if (p_cb->role == HCI_ROLE_CENTRAL) {
    memcpy(text.data(), p_cb->rand.data(), BT_OCTET8_LEN);
    memcpy(text.data() + BT_OCTET8_LEN, p_cb->rrand.data(), BT_OCTET8_LEN);
  } else {
    memcpy(text.data(), p_cb->rrand.data(), BT_OCTET8_LEN);
    memcpy(text.data() + BT_OCTET8_LEN, p_cb->rand.data(), BT_OCTET8_LEN);
  }

  /* generate STK = Etk(rand|rrand)*/
  return aes_128(p_cb->tk, text);
}

/*******************************************************************************
 *
 * Function         smp_create_private_key
 *
 * Description      This function is called to create private key used to
 *                  calculate public key and DHKey.
 *                  The function starts private key creation requesting
 *                  for the controller to generate [0-7] octets of private key.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_create_private_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  // Only use the stored OOB data if we are in an oob association model
  if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB) {
    log::warn("OOB Association Model");
    // Make sure our data isn't empty, otherwise we generate new and eventually
    // pairing will fail Not much we can do about it at this point, just have to
    // generate new data The data will be cleared after the advertiser times
    // out, so if the advertiser times out we want the pairing to fail anyway.
    if (!is_oob_data_empty(&saved_local_oob_data)) {
      log::warn("Found OOB data, loading keys");
      for (int i = 0; i < BT_OCTET32_LEN; i++) {
        p_cb->private_key[i] = saved_local_oob_data.private_key_used[i];
        p_cb->loc_publ_key.x[i] = saved_local_oob_data.publ_key_used.x[i];
        p_cb->loc_publ_key.y[i] = saved_local_oob_data.publ_key_used.y[i];
      }
      p_cb->sc_oob_data.loc_oob_data = saved_local_oob_data;
      p_cb->local_random = saved_local_oob_data.randomizer;
      smp_process_private_key(p_cb);
      return;
    }
    log::warn("OOB Association Model with no saved data present");
  }

  send_ble_rand(BindOnce(
      [](tSMP_CB* p_cb, uint64_t rand) {
        memcpy(p_cb->private_key, (uint8_t*)&rand, sizeof(uint64_t));
        send_ble_rand(BindOnce(
            [](tSMP_CB* p_cb, uint64_t rand) {
              memcpy(&p_cb->private_key[8], (uint8_t*)&rand, sizeof(uint64_t));
              send_ble_rand(BindOnce(
                  [](tSMP_CB* p_cb, uint64_t rand) {
                    memcpy(&p_cb->private_key[16], (uint8_t*)&rand,
                           sizeof(uint64_t));
                    send_ble_rand(BindOnce(
                        [](tSMP_CB* p_cb, uint64_t rand) {
                          memcpy(&p_cb->private_key[24], (uint8_t*)&rand,
                                 sizeof(uint64_t));
                          smp_process_private_key(p_cb);
                        },
                        p_cb));
                  },
                  p_cb));
            },
            p_cb));
      },
      p_cb));
}

/*******************************************************************************
 *
 * Function         smp_use_oob_private_key
 *
 * Description      This function is called
 *                  - to save the secret key used to calculate the public key
 *                    used in calculations of commitment sent OOB to a peer
 *                  - to use this secret key to recalculate the public key and
 *                    start the process of sending this public key to the peer
 *                  if secret/public keys have to be reused.
 *                  If the keys aren't supposed to be reused, continue from the
 *                  point from which request for OOB data was issued.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_use_oob_private_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  log::info("req_oob_type:{}, role:{}", p_cb->req_oob_type, p_cb->role);

  switch (p_cb->req_oob_type) {
    case SMP_OOB_BOTH:
    case SMP_OOB_LOCAL:
      log::info("restore secret key");
      // Only use the stored OOB data if we are in an oob association model
      if (p_cb->selected_association_model == SMP_MODEL_SEC_CONN_OOB) {
        log::info("OOB Association Model");
        // Make sure our data isn't empty, otherwise we generate new and
        // eventually pairing will fail Not much we can do about it at this
        // point, just have to generate new data The data will be cleared after
        // the advertiser times out, so if the advertiser times out we want the
        // pairing to fail anyway.
        if (!is_oob_data_empty(&saved_local_oob_data)) {
          log::info("Found OOB data, loading keys");
          for (int i = 0; i < BT_OCTET32_LEN; i++) {
            p_cb->private_key[i] = saved_local_oob_data.private_key_used[i];
            p_cb->loc_publ_key.x[i] = saved_local_oob_data.publ_key_used.x[i];
            p_cb->loc_publ_key.y[i] = saved_local_oob_data.publ_key_used.y[i];
          }
          p_cb->sc_oob_data.loc_oob_data = saved_local_oob_data;
          p_cb->local_random = saved_local_oob_data.randomizer;
          smp_process_private_key(p_cb);
          return;
        }
        log::info("OOB Association Model with no saved data present");
      }

      memcpy(p_cb->private_key, p_cb->sc_oob_data.loc_oob_data.private_key_used,
             BT_OCTET32_LEN);
      smp_process_private_key(p_cb);
      break;
    default:
      log::info("create secret key anew");
      smp_set_state(SMP_STATE_PAIR_REQ_RSP);
      smp_decide_association_model(p_cb, NULL);
      break;
  }
}

/*******************************************************************************
 *
 * Function         smp_process_private_key
 *
 * Description      This function processes private key.
 *                  It calculates public key and notifies SM that private key /
 *                  public key pair is created.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_process_private_key(tSMP_CB* p_cb) {
  Point public_key;
  BT_OCTET32 private_key;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  memcpy(private_key, p_cb->private_key, BT_OCTET32_LEN);
  ECC_PointMult(&public_key, &(curve_p256.G), (uint32_t*)private_key);
  memcpy(p_cb->loc_publ_key.x, public_key.x, BT_OCTET32_LEN);
  memcpy(p_cb->loc_publ_key.y, public_key.y, BT_OCTET32_LEN);

  smp_debug_print_nbyte_little_endian(p_cb->private_key, "private",
                                      BT_OCTET32_LEN);
  smp_debug_print_nbyte_little_endian(p_cb->loc_publ_key.x, "local public(x)",
                                      BT_OCTET32_LEN);
  smp_debug_print_nbyte_little_endian(p_cb->loc_publ_key.y, "local public(y)",
                                      BT_OCTET32_LEN);
  p_cb->flags |= SMP_PAIR_FLAG_HAVE_LOCAL_PUBL_KEY;
  smp_sm_event(p_cb, SMP_LOC_PUBL_KEY_CRTD_EVT, NULL);
}

/*******************************************************************************
 *
 * Function         smp_compute_dhkey
 *
 * Description      The function:
 *                  - calculates a new public key using as input local private
 *                    key and peer public key;
 *                  - saves the new public key x-coordinate as DHKey.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_compute_dhkey(tSMP_CB* p_cb) {
  Point peer_publ_key, new_publ_key;
  BT_OCTET32 private_key;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  memcpy(private_key, p_cb->private_key, BT_OCTET32_LEN);
  memcpy(peer_publ_key.x, p_cb->peer_publ_key.x, BT_OCTET32_LEN);
  memcpy(peer_publ_key.y, p_cb->peer_publ_key.y, BT_OCTET32_LEN);

  ECC_PointMult(&new_publ_key, &peer_publ_key, (uint32_t*)private_key);

  memcpy(p_cb->dhkey, new_publ_key.x, BT_OCTET32_LEN);

  smp_debug_print_nbyte_little_endian(p_cb->dhkey, "Old DHKey", BT_OCTET32_LEN);

  smp_debug_print_nbyte_little_endian(p_cb->private_key, "private",
                                      BT_OCTET32_LEN);
  smp_debug_print_nbyte_little_endian(p_cb->peer_publ_key.x, "rem public(x)",
                                      BT_OCTET32_LEN);
  smp_debug_print_nbyte_little_endian(p_cb->peer_publ_key.y, "rem public(y)",
                                      BT_OCTET32_LEN);
  smp_debug_print_nbyte_little_endian(p_cb->dhkey, "Reverted DHKey",
                                      BT_OCTET32_LEN);
}

/** The function calculates and saves local commmitment in CB. */
void smp_calculate_local_commitment(tSMP_CB* p_cb) {
  uint8_t random_input;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  switch (p_cb->selected_association_model) {
    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
      if (p_cb->role == HCI_ROLE_CENTRAL)
        log::warn(
            "local commitment calc on central is not expected for Just "
            "Works/Numeric Comparison models");
      p_cb->commitment = crypto_toolbox::f4(
          p_cb->loc_publ_key.x, p_cb->peer_publ_key.x, p_cb->rand, 0);
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
      random_input =
          smp_calculate_random_input(p_cb->local_random.data(), p_cb->round);
      p_cb->commitment =
          crypto_toolbox::f4(p_cb->loc_publ_key.x, p_cb->peer_publ_key.x,
                             p_cb->rand, random_input);
      break;
    case SMP_MODEL_SEC_CONN_OOB:
      log::warn(
          "local commitment calc is expected for OOB model BEFORE pairing");
      p_cb->commitment = crypto_toolbox::f4(
          p_cb->loc_publ_key.x, p_cb->loc_publ_key.x, p_cb->local_random, 0);
      break;
    default:
      log::error("Association Model={} is not used in LE SC",
                 p_cb->selected_association_model);
      return;
  }
}

/** The function calculates peer commmitment */
Octet16 smp_calculate_peer_commitment(tSMP_CB* p_cb) {
  uint8_t ri;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  Octet16 output{0};
  switch (p_cb->selected_association_model) {
    case SMP_MODEL_SEC_CONN_JUSTWORKS:
    case SMP_MODEL_SEC_CONN_NUM_COMP:
      if (p_cb->role == HCI_ROLE_PERIPHERAL)
        log::warn(
            "peer commitment calc on peripheral is not expected for Just "
            "Works/Numeric Comparison models");
      output = crypto_toolbox::f4(p_cb->peer_publ_key.x, p_cb->loc_publ_key.x,
                                  p_cb->rrand, 0);
      break;
    case SMP_MODEL_SEC_CONN_PASSKEY_ENT:
    case SMP_MODEL_SEC_CONN_PASSKEY_DISP:
      ri = smp_calculate_random_input(p_cb->peer_random.data(), p_cb->round);
      output = crypto_toolbox::f4(p_cb->peer_publ_key.x, p_cb->loc_publ_key.x,
                                  p_cb->rrand, ri);
      break;
    case SMP_MODEL_SEC_CONN_OOB:
      output = crypto_toolbox::f4(p_cb->peer_publ_key.x, p_cb->peer_publ_key.x,
                                  p_cb->peer_random, 0);
      break;
    default:
      log::error("Association Model={} is not used in LE SC",
                 p_cb->selected_association_model);
      return output;
  }

  return output;
}

/*******************************************************************************
 *
 * Function         smp_calculate_numeric_comparison_display_number
 *
 * Description      The function calculates and saves number to display in
 *                  numeric comparison association mode.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_calculate_numeric_comparison_display_number(tSMP_CB* p_cb,
                                                     tSMP_INT_DATA* p_data) {
  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->role == HCI_ROLE_CENTRAL) {
    p_cb->number_to_display = crypto_toolbox::g2(
        p_cb->loc_publ_key.x, p_cb->peer_publ_key.x, p_cb->rand, p_cb->rrand);
  } else {
    p_cb->number_to_display = crypto_toolbox::g2(
        p_cb->peer_publ_key.x, p_cb->loc_publ_key.x, p_cb->rrand, p_cb->rand);
  }

  if (p_cb->number_to_display >= (BTM_MAX_PASSKEY_VAL + 1)) {
    tSMP_INT_DATA smp_int_data;
    smp_int_data.status = SMP_PAIR_FAIL_UNKNOWN;
    p_cb->failure = SMP_PAIR_FAIL_UNKNOWN;
    log::verbose("Number to display in numeric comparison={} too large",
                 p_cb->number_to_display);
    smp_sm_event(p_cb, SMP_AUTH_CMPL_EVT, &smp_int_data);
    return;
  }

  p_cb->cb_evt = SMP_NC_REQ_EVT;
  tSMP_INT_DATA smp_int_data;
  smp_int_data.passkey = p_cb->number_to_display;
  smp_sm_event(p_cb, SMP_SC_DSPL_NC_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         smp_calculate_local_dhkey_check
 *
 * Description      The function calculates and saves local device DHKey check
 *                  value in CB.
 *                  Before doing this it calls
 *                  smp_calculate_f5_mackey_and_long_term_key(...).
 *                  to calculate MacKey and LTK.
 *                  MacKey is used in dhkey calculation.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_calculate_local_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t iocap[3], a[7], b[7];

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  smp_calculate_f5_mackey_and_long_term_key(p_cb);

  smp_collect_local_io_capabilities(iocap, p_cb);

  smp_collect_local_ble_address(a, p_cb);
  smp_collect_peer_ble_address(b, p_cb);
  p_cb->dhkey_check = crypto_toolbox::f6(p_cb->mac_key, p_cb->rand, p_cb->rrand,
                                         p_cb->peer_random, iocap, a, b);
}

/*******************************************************************************
 *
 * Function         smp_calculate_peer_dhkey_check
 *
 * Description      The function calculates peer device DHKey check value.
 *
 * Returns          void
 *
 ******************************************************************************/
void smp_calculate_peer_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data) {
  uint8_t iocap[3], a[7], b[7];
  tSMP_KEY key;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  smp_collect_peer_io_capabilities(iocap, p_cb);

  smp_collect_local_ble_address(a, p_cb);
  smp_collect_peer_ble_address(b, p_cb);
  Octet16 param_buf = crypto_toolbox::f6(p_cb->mac_key, p_cb->rrand, p_cb->rand,
                                         p_cb->local_random, iocap, b, a);
  key.key_type = SMP_KEY_TYPE_PEER_DHK_CHCK;
  key.p_data = param_buf.data();
  tSMP_INT_DATA smp_int_data;
  smp_int_data.key = key;
  smp_sm_event(p_cb, SMP_SC_KEY_READY_EVT, &smp_int_data);
}

/*******************************************************************************
 *
 * Function         smp_calculate_link_key_from_long_term_key
 *
 * Description      The function calculates and saves BR/EDR link key derived
 *                  from LE SC LTK.
 *
 * Returns          false if out of resources, true in other cases.
 *
 ******************************************************************************/
bool smp_calculate_link_key_from_long_term_key(tSMP_CB* p_cb) {
  tBTM_SEC_DEV_REC* p_dev_rec;
  RawAddress bda_for_lk;
  tBLE_ADDR_TYPE conn_addr_type;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  if (p_cb->id_addr_rcvd && p_cb->id_addr_type == BLE_ADDR_PUBLIC) {
    log::verbose(
        "Use rcvd identity address as BD_ADDR of LK rcvd identity address");
    bda_for_lk = p_cb->id_addr;
  } else if ((BTM_ReadRemoteConnectionAddr(p_cb->pairing_bda, bda_for_lk,
                                           &conn_addr_type, true)) &&
             conn_addr_type == BLE_ADDR_PUBLIC) {
    log::verbose("Use rcvd connection address as BD_ADDR of LK");
  } else {
    log::warn("Don't have peer public address to associate with LK");
    return false;
  }

  p_dev_rec = btm_find_dev(p_cb->pairing_bda);
  if (p_dev_rec == NULL) {
    log::error("failed to find Security Record");
    return false;
  }

  Octet16 link_key =
      crypto_toolbox::ltk_to_link_key(p_cb->ltk, p_cb->key_derivation_h7_used);

  uint8_t link_key_type;
  if (p_cb->init_security_mode == BTM_SEC_MODE_SC) {
    /* Secure Connections Only Mode */
    link_key_type = BTM_LKEY_TYPE_AUTH_COMB_P_256;
  } else if (bluetooth::shim::GetController()->SupportsSecureConnections()) {
    /* both transports are SC capable */
    if (p_cb->sec_level == SMP_SEC_AUTHENTICATED)
      link_key_type = BTM_LKEY_TYPE_AUTH_COMB_P_256;
    else
      link_key_type = BTM_LKEY_TYPE_UNAUTH_COMB_P_256;
  } else if (p_cb->init_security_mode == BTM_SEC_MODE_SP) {
    /* BR/EDR transport is SSP capable */
    if (p_cb->sec_level == SMP_SEC_AUTHENTICATED)
      link_key_type = BTM_LKEY_TYPE_AUTH_COMB;
    else
      link_key_type = BTM_LKEY_TYPE_UNAUTH_COMB;
  } else {
    log::error("failed to update link_key. Sec Mode={}, sm4=0x{:02x}",
               p_cb->init_security_mode, p_dev_rec->sm4);
    return false;
  }

  link_key_type += BTM_LTK_DERIVED_LKEY_OFFSET;

  Octet16 notif_link_key;
  std::reverse_copy(link_key.begin(), link_key.end(), notif_link_key.begin());
  btm_sec_link_key_notification(bda_for_lk, notif_link_key, link_key_type);

  return true;
}

/** The function calculates and saves SC LTK derived from BR/EDR link key. */
bool smp_calculate_long_term_key_from_link_key(tSMP_CB* p_cb) {
  tBTM_SEC_DEV_REC* p_dev_rec;

  log::verbose("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));

  p_dev_rec = btm_find_dev(p_cb->pairing_bda);
  if (p_dev_rec == NULL) {
    log::error("ailed to find Security Record");
    return false;
  }

  uint8_t br_link_key_type;
  br_link_key_type = BTM_SecGetDeviceLinkKeyType(p_cb->pairing_bda);
  if (br_link_key_type == BTM_LKEY_TYPE_IGNORE) {
    log::error("failed to retrieve BR link type");
    return false;
  }

  if ((br_link_key_type != BTM_LKEY_TYPE_AUTH_COMB_P_256) &&
      (br_link_key_type != BTM_LKEY_TYPE_UNAUTH_COMB_P_256)) {
    log::error("LE SC LTK can't be derived from LK {}", br_link_key_type);
    return false;
  }

  Octet16 rev_link_key;
  std::reverse_copy(p_dev_rec->sec_rec.link_key.begin(),
                    p_dev_rec->sec_rec.link_key.end(), rev_link_key.begin());
  p_cb->ltk = crypto_toolbox::link_key_to_ltk(rev_link_key,
                                              p_cb->key_derivation_h7_used);

  p_cb->sec_level = (br_link_key_type == BTM_LKEY_TYPE_AUTH_COMB_P_256)
                        ? SMP_SEC_AUTHENTICATED
                        : SMP_SEC_UNAUTHENTICATE;
  return true;
}

/**
 * This function generates nonce.
 */
void smp_start_nonce_generation(tSMP_CB* p_cb) {
  log::verbose("start generating nonce");
  send_ble_rand(BindOnce(
      [](tSMP_CB* p_cb, uint64_t rand) {
        memcpy(p_cb->rand.data(), (uint8_t*)&rand, sizeof(uint64_t));
        send_ble_rand(BindOnce(
            [](tSMP_CB* p_cb, uint64_t rand) {
              memcpy(p_cb->rand.data() + sizeof(uint64_t), (uint8_t*)&rand,
                     sizeof(uint64_t));
              log::verbose("round {}, done", p_cb->round);
              /* notifies SM that it has new nonce. */
              smp_sm_event(p_cb, SMP_HAVE_LOC_NONCE_EVT, NULL);
            },
            p_cb));
      },
      p_cb));
}

static void send_ble_rand(OnceCallback<void(uint64_t)> callback) {
  bluetooth::shim::GetController()->LeRand(std::move(callback));
}
