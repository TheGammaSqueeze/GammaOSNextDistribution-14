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
 *  This file contains internally used SMP definitions
 *
 ******************************************************************************/
#ifndef SMP_INT_H
#define SMP_INT_H

#include <bluetooth/log.h>

#include <cstdint>

#include "macros.h"
#include "osi/include/alarm.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_octets.h"
#include "stack/include/smp_api_types.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

typedef enum : uint16_t {
  SMP_METRIC_COMMAND_LE_FLAG = 0x0000,
  SMP_METRIC_COMMAND_BR_FLAG = 0x0100,
  SMP_METRIC_COMMAND_LE_PAIRING_CMPL = 0xFF00,
  SMP_METRIC_COMMAND_BR_PAIRING_CMPL = 0xFF01,
} tSMP_METRIC_COMMAND;

constexpr uint16_t SMP_METRIC_STATUS_INTERNAL_FLAG = 0x0100;

typedef enum : uint8_t {
  /* Legacy mode */
  SMP_MODEL_ENCRYPTION_ONLY = 0, /* Just Works model */
  SMP_MODEL_PASSKEY = 1,         /* Passkey Entry model, input the key */
  SMP_MODEL_OOB = 2,             /* OOB model */
  SMP_MODEL_KEY_NOTIF = 3,       /* Passkey Entry model, display the key */
  /* Secure connections mode */
  SMP_MODEL_SEC_CONN_JUSTWORKS = 4,   /* Just Works model */
  SMP_MODEL_SEC_CONN_NUM_COMP = 5,    /* Numeric Comparison model */
  SMP_MODEL_SEC_CONN_PASSKEY_ENT = 6, /* Passkey Entry model, */
  /* this side inputs the key */
  SMP_MODEL_SEC_CONN_PASSKEY_DISP = 7, /* Passkey Entry model, */
  /* this side displays the key */
  SMP_MODEL_SEC_CONN_OOB = 8, /* Secure Connections mode, OOB model */
  SMP_MODEL_OUT_OF_RANGE = 9,
} tSMP_ASSO_MODEL;

#define SMP_WAIT_FOR_RSP_TIMEOUT_MS (30 * 1000)
#define SMP_DELAYED_AUTH_TIMEOUT_MS 500

/* SMP command code */
typedef enum : uint8_t {
  SMP_OPCODE_PAIRING_REQ = 0x01,
  SMP_OPCODE_PAIRING_RSP = 0x02,
  SMP_OPCODE_CONFIRM = 0x03,
  SMP_OPCODE_RAND = 0x04,
  SMP_OPCODE_PAIRING_FAILED = 0x05,
  SMP_OPCODE_ENCRYPT_INFO = 0x06,
  SMP_OPCODE_CENTRAL_ID = 0x07,
  SMP_OPCODE_IDENTITY_INFO = 0x08,
  SMP_OPCODE_ID_ADDR = 0x09,
  SMP_OPCODE_SIGN_INFO = 0x0A,
  SMP_OPCODE_SEC_REQ = 0x0B,
  SMP_OPCODE_PAIR_PUBLIC_KEY = 0x0C,
  SMP_OPCODE_PAIR_DHKEY_CHECK = 0x0D,
  SMP_OPCODE_PAIR_KEYPR_NOTIF = 0x0E,
  SMP_OPCODE_MAX = SMP_OPCODE_PAIR_KEYPR_NOTIF,
  SMP_OPCODE_MIN = SMP_OPCODE_PAIRING_REQ,
  // NOTE: For some reason this is outside the MAX/MIN values
  SMP_OPCODE_PAIR_COMMITM = 0x0F,
} tSMP_OPCODE;

inline std::string smp_opcode_text(const tSMP_OPCODE opcode) {
  switch (opcode) {
    CASE_RETURN_TEXT(SMP_OPCODE_PAIRING_REQ);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIRING_RSP);
    CASE_RETURN_TEXT(SMP_OPCODE_CONFIRM);
    CASE_RETURN_TEXT(SMP_OPCODE_RAND);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIRING_FAILED);
    CASE_RETURN_TEXT(SMP_OPCODE_ENCRYPT_INFO);
    CASE_RETURN_TEXT(SMP_OPCODE_CENTRAL_ID);
    CASE_RETURN_TEXT(SMP_OPCODE_IDENTITY_INFO);
    CASE_RETURN_TEXT(SMP_OPCODE_ID_ADDR);
    CASE_RETURN_TEXT(SMP_OPCODE_SIGN_INFO);
    CASE_RETURN_TEXT(SMP_OPCODE_SEC_REQ);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIR_PUBLIC_KEY);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIR_DHKEY_CHECK);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIR_KEYPR_NOTIF);
    CASE_RETURN_TEXT(SMP_OPCODE_PAIR_COMMITM);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", opcode);
  }
}

/* SMP events */
typedef enum : uint8_t {
  SMP_NOP_EVT = 0,
  SMP_CONFIRM_EVT = SMP_OPCODE_CONFIRM,  // 0x03
  SMP_RAND_EVT = SMP_OPCODE_RAND,        // 0x04

  SMP_PAIR_COMMITM_EVT = SMP_OPCODE_PAIR_COMMITM,     // 0x0f
  SMP_SELF_DEF_EVT = (SMP_PAIR_COMMITM_EVT + 1),      // 0x10
  SMP_KEY_READY_EVT = (SMP_SELF_DEF_EVT),             // 0x04
  SMP_ENCRYPTED_EVT = (SMP_SELF_DEF_EVT + 1),         // 0x05
  SMP_L2CAP_CONN_EVT = (SMP_SELF_DEF_EVT + 2),        // 0x06
  SMP_L2CAP_DISCONN_EVT = (SMP_SELF_DEF_EVT + 3),     // 0x07
  SMP_IO_RSP_EVT = (SMP_SELF_DEF_EVT + 4),            // 0x08
  SMP_API_SEC_GRANT_EVT = (SMP_SELF_DEF_EVT + 5),     // 0x09
  SMP_TK_REQ_EVT = (SMP_SELF_DEF_EVT + 6),            // 0x0a
  SMP_AUTH_CMPL_EVT = (SMP_SELF_DEF_EVT + 7),         // 0x0b
  SMP_ENC_REQ_EVT = (SMP_SELF_DEF_EVT + 8),           // 0x0c
  SMP_BOND_REQ_EVT = (SMP_SELF_DEF_EVT + 9),          // 0x0d
  SMP_DISCARD_SEC_REQ_EVT = (SMP_SELF_DEF_EVT + 10),  // 0x0e

  SMP_BR_PAIR_KEYPR_NOTIF_EVT = SMP_OPCODE_PAIR_KEYPR_NOTIF,
  /* not over BR/EDR */                                     // 0x0e
  SMP_BR_SELF_DEF_EVT = SMP_BR_PAIR_KEYPR_NOTIF_EVT,        // 0x0e
  SMP_BR_KEY_READY_EVT = (SMP_BR_SELF_DEF_EVT + 1),         // 0x0f
  SMP_BR_ENCRYPTED_EVT = (SMP_BR_SELF_DEF_EVT + 2),         // 0x10
  SMP_BR_L2CAP_CONN_EVT = (SMP_BR_SELF_DEF_EVT + 3),        // 0x11
  SMP_BR_L2CAP_DISCONN_EVT = (SMP_BR_SELF_DEF_EVT + 4),     // 0x12
  SMP_BR_KEYS_RSP_EVT = (SMP_BR_SELF_DEF_EVT + 5),          // 0x13
  SMP_BR_API_SEC_GRANT_EVT = (SMP_BR_SELF_DEF_EVT + 6),     // 0x14
  SMP_BR_TK_REQ_EVT = (SMP_BR_SELF_DEF_EVT + 7),            // 0x15
  SMP_BR_AUTH_CMPL_EVT = (SMP_BR_SELF_DEF_EVT + 8),         // 0x16
  SMP_BR_ENC_REQ_EVT = (SMP_BR_SELF_DEF_EVT + 9),           // 0x17
  SMP_BR_BOND_REQ_EVT = (SMP_BR_SELF_DEF_EVT + 10),         // 0x18
  SMP_BR_DISCARD_SEC_REQ_EVT = (SMP_BR_SELF_DEF_EVT + 11),  // 0x19
  SMP_BR_MAX_EVT = (SMP_BR_SELF_DEF_EVT + 12),              // 0x1a

  SMP_PAIR_DHKEY_CHCK_EVT = SMP_OPCODE_PAIR_DHKEY_CHECK,  // 0x0d

  /* request to start public  key exchange */
  SMP_PUBL_KEY_EXCH_REQ_EVT = (SMP_SELF_DEF_EVT + 11),  // 0x1b

  /* local public key created */
  SMP_LOC_PUBL_KEY_CRTD_EVT = (SMP_SELF_DEF_EVT + 12),  // 0x1c

  /* both local and peer public keys are saved in cb */
  SMP_BOTH_PUBL_KEYS_RCVD_EVT = (SMP_SELF_DEF_EVT + 13),  // 0x1d

  /* DHKey computation is completed, time to start SC phase1 */
  SMP_SC_DHKEY_CMPLT_EVT = (SMP_SELF_DEF_EVT + 14),  // 0x1d

  /* new local nonce is generated and saved in p_cb->rand */
  SMP_HAVE_LOC_NONCE_EVT = (SMP_SELF_DEF_EVT + 15),  // 0x1f

  /* time to start SC phase2 */
  SMP_SC_PHASE1_CMPLT_EVT = (SMP_SELF_DEF_EVT + 16),  // 0x20

  /* request to calculate number for user check. Used only in the numeric
   * compare protocol */
  SMP_SC_CALC_NC_EVT = (SMP_SELF_DEF_EVT + 17),  // 0x21

  /* Request to display the number for user check to the user.*/
  /* Used only in the numeric compare protocol */
  SMP_SC_DSPL_NC_EVT = (SMP_SELF_DEF_EVT + 18),  // 0x22

  /* user confirms 'OK' numeric comparison request */
  SMP_SC_NC_OK_EVT = (SMP_SELF_DEF_EVT + 19),  // 0x23

  /* both local and peer DHKey Checks are already present - it is used on
   * peripheral to prevent a race condition */
  SMP_SC_2_DHCK_CHKS_PRES_EVT = (SMP_SELF_DEF_EVT + 20),  // 0x24

  /* same meaning as SMP_KEY_READY_EVT to separate between SC and legacy actions
   */
  SMP_SC_KEY_READY_EVT = (SMP_SELF_DEF_EVT + 21),             // 0x25
  SMP_KEYPRESS_NOTIFICATION_EVENT = (SMP_SELF_DEF_EVT + 22),  // 0x26

  /* SC OOB data from some repository is provided */
  SMP_SC_OOB_DATA_EVT = (SMP_SELF_DEF_EVT + 23),  // 0x27

  SMP_CR_LOC_SC_OOB_DATA_EVT = (SMP_SELF_DEF_EVT + 24),  // 0x28
  SMP_SIRK_DEVICE_VALID_EVT = (SMP_SELF_DEF_EVT + 25),   // 0x29
  SMP_MAX_EVT = SMP_SIRK_DEVICE_VALID_EVT,               // 0x29
} tSMP_EVENT;
typedef tSMP_EVENT tSMP_BR_EVENT;

/* Assumption it's only using the low 8 bits, if bigger than that, need to
 * expand it to 16 bits */
#define SMP_SEC_KEY_MASK 0x00ff

/* SMP pairing state */
enum {
  SMP_STATE_IDLE,
  SMP_STATE_WAIT_APP_RSP,
  SMP_STATE_SEC_REQ_PENDING,
  SMP_STATE_PAIR_REQ_RSP,
  SMP_STATE_WAIT_CONFIRM,
  SMP_STATE_CONFIRM,
  SMP_STATE_RAND,
  SMP_STATE_PUBLIC_KEY_EXCH,
  SMP_STATE_SEC_CONN_PHS1_START,
  SMP_STATE_WAIT_COMMITMENT,
  SMP_STATE_WAIT_NONCE,
  SMP_STATE_SEC_CONN_PHS2_START,
  SMP_STATE_WAIT_DHK_CHECK,
  SMP_STATE_DHK_CHECK,
  SMP_STATE_ENCRYPTION_PENDING,
  SMP_STATE_BOND_PENDING,
  SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA,
  SMP_STATE_MAX
};
typedef uint8_t tSMP_STATE;

/* SMP over BR/EDR pairing states */
enum {
  SMP_BR_STATE_IDLE = SMP_STATE_IDLE,
  SMP_BR_STATE_WAIT_APP_RSP,
  SMP_BR_STATE_PAIR_REQ_RSP,
  SMP_BR_STATE_BOND_PENDING,
  SMP_BR_STATE_MAX
};
typedef uint8_t tSMP_BR_STATE;

enum {
  SMP_KEY_TYPE_TK,
  SMP_KEY_TYPE_CFM,
  SMP_KEY_TYPE_CMP,
  SMP_KEY_TYPE_PEER_DHK_CHCK,
  SMP_KEY_TYPE_STK,
  SMP_KEY_TYPE_LTK
};
typedef struct {
  uint8_t key_type;
  uint8_t* p_data;
} tSMP_KEY;

typedef union {
  uint8_t* p_data; /* uint8_t type data pointer */
  tSMP_KEY key;
  tSMP_STATUS status;
  uint32_t passkey;
  tSMP_OOB_DATA_TYPE req_oob_type;
} tSMP_INT_DATA;

/* internal status mask */
#define SMP_PAIR_FLAGS_WE_STARTED_DD (1)
#define SMP_PAIR_FLAGS_PEER_STARTED_DD (1 << 1)
#define SMP_PAIR_FLAGS_CMD_CONFIRM_RCVD (1 << SMP_OPCODE_CONFIRM) /* 1 << 3 */
#define SMP_PAIR_FLAG_ENC_AFTER_PAIR (1 << 4)
#define SMP_PAIR_FLAG_HAVE_PEER_DHK_CHK \
  (1 << 5) /* used on peripheral to resolve race condition */
#define SMP_PAIR_FLAG_HAVE_PEER_PUBL_KEY \
  (1 << 6) /* used on peripheral to resolve race condition */
#define SMP_PAIR_FLAG_HAVE_PEER_COMM \
  (1 << 7) /* used to resolve race condition */
#define SMP_PAIR_FLAG_HAVE_LOCAL_PUBL_KEY \
  (1 << 8) /* used on peripheral to resolve race condition */

#define SMP_PAIR_FLAGS_CMD_CONFIRM_SENT (1 << 9)

/* check if authentication requirement need MITM protection */
#define SMP_NO_MITM_REQUIRED(x) (((x)&SMP_AUTH_YN_BIT) == 0)

/* SMP control block */
class tSMP_CB {
 public:
  void init(uint8_t security_mode);
  void reset();

 public:
  uint8_t init_security_mode{0};
  tSMP_CALLBACK* p_callback;
  alarm_t* smp_rsp_timer_ent;
  RawAddress pairing_bda;
  tSMP_STATE state;
  bool derive_lk;
  bool id_addr_rcvd;
  tBLE_ADDR_TYPE id_addr_type;
  RawAddress id_addr;
  bool smp_over_br;
  tSMP_BR_STATE br_state; /* if SMP over BR/ERD has priority over SMP */
  uint8_t failure;
  tSMP_STATUS status;
  tHCI_ROLE role;
  uint16_t flags;
  tSMP_EVT cb_evt;
  tSMP_SEC_LEVEL sec_level;
  bool connect_initialized;
  Octet16 confirm;
  Octet16 rconfirm;
  Octet16 rrand; /* for SC this is peer nonce */
  Octet16 rand;  /* for SC this is local nonce */
  BT_OCTET32 private_key;
  BT_OCTET32 dhkey;
  Octet16 commitment;
  Octet16 remote_commitment;
  Octet16 local_random; /* local randomizer - passkey or OOB randomizer */
  Octet16 peer_random;  /* peer randomizer - passkey or OOB randomizer */
  Octet16 dhkey_check;
  Octet16 remote_dhkey_check;
  tSMP_PUBLIC_KEY loc_publ_key;
  tSMP_PUBLIC_KEY peer_publ_key;
  tSMP_OOB_DATA_TYPE req_oob_type;
  tSMP_SC_OOB_DATA sc_oob_data;
  tSMP_IO_CAP peer_io_caps;
  tSMP_IO_CAP local_io_capability;
  tSMP_OOB_FLAG peer_oob_flag;
  tSMP_OOB_FLAG loc_oob_flag;
  tSMP_AUTH_REQ peer_auth_req;
  tSMP_AUTH_REQ loc_auth_req;

  bool sc_only_mode_locally_required; /* true if sc_only required required
                                         locally */
  bool sc_mode_required_by_peer;      /* true if peer requires sc in pair_req or
                                         pair_rsp */

  /* either in Secure Connections mode or not at all */
  tSMP_ASSO_MODEL selected_association_model;
  bool key_derivation_h7_used;
  bool le_sc_kp_notif_is_used;
  tSMP_SC_KEY_TYPE local_keypress_notification;
  tSMP_SC_KEY_TYPE peer_keypress_notification;
  uint8_t
      round; /* authentication stage 1 round for passkey association model */
  uint32_t number_to_display;
  Octet16 mac_key;
  uint8_t peer_enc_size;
  uint8_t loc_enc_size;
  uint8_t peer_i_key;
  uint8_t peer_r_key;
  uint8_t local_i_key;
  uint8_t local_r_key;

  Octet16 tk;
  Octet16 ltk;
  uint16_t div;
  Octet16 csrk; /* storage for local CSRK */
  uint16_t ediv;
  BT_OCTET8 enc_rand;
  tBLE_ADDR_TYPE addr_type;
  RawAddress local_bda;
  bool is_pair_cancel;
  bool discard_sec_req;
  uint8_t rcvd_cmd_code;
  uint8_t rcvd_cmd_len;
  uint16_t total_tx_unacked;
  bool wait_for_authorization_complete;
  tSMP_STATUS cert_failure; /*failure case for certification */
  alarm_t* delayed_auth_timer_ent;
  tBLE_BD_ADDR pairing_ble_bd_addr;
};

/* Server Action functions are of this type */
typedef void (*tSMP_ACT)(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);

extern tSMP_CB smp_cb;

/* smp main */
bool smp_sm_event(tSMP_CB* p_cb, tSMP_EVENT event, tSMP_INT_DATA* p_data);

tSMP_STATE smp_get_state(void);
void smp_set_state(tSMP_STATE state);

/* smp_br_main */
void smp_br_state_machine_event(tSMP_CB* p_cb, tSMP_BR_EVENT event,
                                tSMP_INT_DATA* p_data);
tSMP_BR_STATE smp_get_br_state(void);
void smp_set_br_state(tSMP_BR_STATE state);

/* smp_act.cc */
void smp_send_pair_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_pair_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_pair_fail(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_rand(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_pairing_public_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_central_id(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_id_addr(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_sec_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_sec_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_sl_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_start_enc(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_enc_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_discard(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_pairing_cmpl(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_decide_association_model(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_sirk_verify(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_app_cback(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_compare(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_check_auth_req(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_io_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_id_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_enc_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_csrk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_ltk_reply(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_pair_cmd(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_pair_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_idle_terminate(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_send_pair_rsp(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_key_distribution(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_proc_srk_info(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_csrk(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_key_pick_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_both_have_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_start_secure_connection_phase1(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_local_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_pairing_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_peer_nonce(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_match_dhkey_checks(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_keypress_notification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_move_to_secure_connections_phase2(tSMP_CB* p_cb,
                                           tSMP_INT_DATA* p_data);
void smp_phase_2_dhkey_checks_are_present(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_wait_for_both_public_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_start_passkey_verification(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_process_secure_connection_oob_data(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data);
void smp_process_secure_connection_long_term_key(void);
void smp_set_local_oob_keys(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_set_local_oob_random_commitment(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_set_derive_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_derive_link_key_from_long_term_key(tSMP_CB* p_cb,
                                            tSMP_INT_DATA* p_data);
void smp_br_process_pairing_command(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_process_security_grant(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_process_peripheral_keys_response(tSMP_CB* p_cb,
                                             tSMP_INT_DATA* p_data);
void smp_br_send_pair_response(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_check_authorization_request(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_select_next_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_process_link_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_br_pairing_complete(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);

/* smp_l2c */
void smp_l2cap_if_init(void);
void smp_data_ind(const RawAddress& bd_addr, BT_HDR* p_buf);

/* smp_util.cc */
void smp_log_metrics(const RawAddress& bd_addr, bool is_outgoing,
                     const uint8_t* p_buf, size_t buf_len, bool is_over_br);
bool smp_send_cmd(uint8_t cmd_code, tSMP_CB* p_cb);
void smp_reset_control_value(tSMP_CB* p_cb);
void smp_proc_pairing_cmpl(tSMP_CB* p_cb);
void smp_convert_string_to_tk(Octet16* tk, uint32_t passkey);
void smp_mask_enc_key(uint8_t loc_enc_size, Octet16* p_data);
void smp_rsp_timeout(void* data);
void smp_delayed_auth_complete_timeout(void* data);
bool smp_command_has_invalid_length(tSMP_CB* p_cb);
bool smp_command_has_invalid_parameters(tSMP_CB* p_cb);
void smp_reject_unexpected_pairing_command(const RawAddress& bd_addr);
tSMP_ASSO_MODEL smp_select_association_model(tSMP_CB* p_cb);
uint8_t smp_calculate_random_input(uint8_t* random, uint8_t round);
void smp_collect_local_io_capabilities(uint8_t* iocap, tSMP_CB* p_cb);
void smp_collect_peer_io_capabilities(uint8_t* iocap, tSMP_CB* p_cb);
void smp_collect_local_ble_address(uint8_t* le_addr, tSMP_CB* p_cb);
void smp_collect_peer_ble_address(uint8_t* le_addr, tSMP_CB* p_cb);
bool smp_check_commitment(tSMP_CB* p_cb);
void smp_save_secure_connections_long_term_key(tSMP_CB* p_cb);
void smp_calculate_f5_mackey_and_long_term_key(tSMP_CB* p_cb);
void smp_remove_fixed_channel(tSMP_CB* p_cb);
bool smp_request_oob_data(tSMP_CB* p_cb);

/* smp_keys.cc */
void smp_generate_srand_mrand_confirm(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_compare(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_stk(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_ltk(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_passkey(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_generate_rand_cont(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_create_private_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_use_oob_private_key(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_compute_dhkey(tSMP_CB* p_cb);
void smp_calculate_local_commitment(tSMP_CB* p_cb);
Octet16 smp_calculate_peer_commitment(tSMP_CB* p_cb);
void smp_calculate_numeric_comparison_display_number(tSMP_CB* p_cb,
                                                     tSMP_INT_DATA* p_data);
void smp_calculate_local_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_calculate_peer_dhkey_check(tSMP_CB* p_cb, tSMP_INT_DATA* p_data);
void smp_start_nonce_generation(tSMP_CB* p_cb);
bool smp_calculate_link_key_from_long_term_key(tSMP_CB* p_cb);
bool smp_calculate_long_term_key_from_link_key(tSMP_CB* p_cb);

void print128(const Octet16& x, const char* key_name);
void smp_xor_128(Octet16* a, const Octet16& b);

/* Save the p_cb->sc_oob_data.loc_oob_data for later, since the p_cb gets
 * cleaned up */
void smp_save_local_oob_data(tSMP_CB* p_cb);
void smp_clear_local_oob_data();
bool smp_has_local_oob_data();

namespace fmt {
template <>
struct formatter<tSMP_EVENT> : enum_formatter<tSMP_EVENT> {};
template <>
struct formatter<tSMP_OPCODE> : enum_formatter<tSMP_OPCODE> {};
template <>
struct formatter<tSMP_ASSO_MODEL> : enum_formatter<tSMP_ASSO_MODEL> {};
}  // namespace fmt

#endif /* SMP_INT_H */
