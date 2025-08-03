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

#define LOG_TAG "smp"

#include <bluetooth/log.h>

#include "os/log.h"
#include "smp_int.h"
#include "stack/include/btm_log_history.h"

using namespace bluetooth;

namespace {

constexpr char kBtmLogTag[] = "SMP";

}

const char* const smp_state_name[] = {
    "SMP_STATE_IDLE",
    "SMP_STATE_WAIT_APP_RSP",
    "SMP_STATE_SEC_REQ_PENDING",
    "SMP_STATE_PAIR_REQ_RSP",
    "SMP_STATE_WAIT_CONFIRM",
    "SMP_STATE_CONFIRM",
    "SMP_STATE_RAND",
    "SMP_STATE_PUBLIC_KEY_EXCH",
    "SMP_STATE_SEC_CONN_PHS1_START",
    "SMP_STATE_WAIT_COMMITMENT",
    "SMP_STATE_WAIT_NONCE",
    "SMP_STATE_SEC_CONN_PHS2_START",
    "SMP_STATE_WAIT_DHK_CHECK",
    "SMP_STATE_DHK_CHECK",
    "SMP_STATE_ENCRYPTION_PENDING",
    "SMP_STATE_BOND_PENDING",
    "SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA",
    "SMP_STATE_MAX"};

const char* const smp_event_name[] = {"PAIRING_REQ_EVT",
                                      "PAIRING_RSP_EVT",
                                      "CONFIRM_EVT",
                                      "RAND_EVT",
                                      "PAIRING_FAILED_EVT",
                                      "ENC_INFO_EVT",
                                      "CENTRAL_ID_EVT",
                                      "ID_INFO_EVT",
                                      "ID_ADDR_EVT",
                                      "SIGN_INFO_EVT",
                                      "SECURITY_REQ_EVT",
                                      "PAIR_PUBLIC_KEY_EVT",
                                      "PAIR_DHKEY_CHECK_EVT",
                                      "PAIR_KEYPRESS_NOTIFICATION_EVT",
                                      "PAIR_COMMITMENT_EVT",
                                      "KEY_READY_EVT",
                                      "ENCRYPTED_EVT",
                                      "L2CAP_CONN_EVT",
                                      "L2CAP_DISCONN_EVT",
                                      "API_IO_RSP_EVT",
                                      "API_SEC_GRANT_EVT",
                                      "TK_REQ_EVT",
                                      "AUTH_CMPL_EVT",
                                      "ENC_REQ_EVT",
                                      "BOND_REQ_EVT",
                                      "DISCARD_SEC_REQ_EVT",
                                      "PUBLIC_KEY_EXCHANGE_REQ_EVT",
                                      "LOCAL_PUBLIC_KEY_CRTD_EVT",
                                      "BOTH_PUBLIC_KEYS_RCVD_EVT",
                                      "SEC_CONN_DHKEY_COMPLETE_EVT",
                                      "HAVE_LOCAL_NONCE_EVT",
                                      "SEC_CONN_PHASE1_CMPLT_EVT",
                                      "SEC_CONN_CALC_NC_EVT",
                                      "SEC_CONN_DISPLAY_NC_EVT",
                                      "SEC_CONN_OK_EVT",
                                      "SEC_CONN_2_DHCK_CHECKS_PRESENT_EVT",
                                      "SEC_CONN_KEY_READY_EVT",
                                      "KEYPRESS_NOTIFICATION_EVT",
                                      "SEC_CONN_OOB_DATA_EVT",
                                      "CREATE_LOCAL_SEC_CONN_OOB_DATA_EVT",
                                      "SIRK_DEVICE_VALID_EVT",
                                      "OUT_OF_RANGE_EVT"};

const char* smp_get_event_name(tSMP_EVENT event);
const char* smp_get_state_name(tSMP_STATE state);

#define SMP_SM_IGNORE 0
#define SMP_NUM_ACTIONS 2
#define SMP_SME_NEXT_STATE 2
#define SMP_SM_NUM_COLS 3

typedef const uint8_t (*tSMP_SM_TBL)[SMP_SM_NUM_COLS];

enum {
  SMP_PROC_SEC_REQ,
  SMP_SEND_PAIR_REQ,
  SMP_SEND_PAIR_RSP,
  SMP_SEND_CONFIRM,
  SMP_SEND_PAIR_FAIL,
  SMP_SEND_RAND,
  SMP_SEND_ENC_INFO,
  SMP_SEND_ID_INFO,
  SMP_SEND_LTK_REPLY,
  SMP_PROC_PAIR_CMD,
  SMP_PROC_PAIR_FAIL,
  SMP_PROC_CONFIRM,
  SMP_PROC_RAND,
  SMP_PROC_ENC_INFO,
  SMP_PROC_CENTRAL_ID,
  SMP_PROC_ID_INFO,
  SMP_PROC_ID_ADDR,
  SMP_PROC_SRK_INFO,
  SMP_PROC_SEC_GRANT,
  SMP_PROC_SL_KEY,
  SMP_PROC_COMPARE,
  SMP_PROC_IO_RSP,
  SMP_GENERATE_COMPARE,
  SMP_GENERATE_CONFIRM,
  SMP_GENERATE_STK,
  SMP_KEY_DISTRIBUTE,
  SMP_START_ENC,
  SMP_PAIRING_CMPL,
  SMP_DECIDE_ASSO_MODEL,
  SMP_SEND_APP_CBACK,
  SMP_CHECK_AUTH_REQ,
  SMP_PAIR_TERMINATE,
  SMP_ENC_CMPL,
  SMP_SIRK_VERIFY,
  SMP_PROC_DISCARD,
  SMP_CREATE_PRIVATE_KEY,
  SMP_USE_OOB_PRIVATE_KEY,
  SMP_SEND_PAIR_PUBLIC_KEY,
  SMP_PROCESS_PAIR_PUBLIC_KEY,
  SMP_HAVE_BOTH_PUBLIC_KEYS,
  SMP_START_SEC_CONN_PHASE1,
  SMP_PROCESS_LOCAL_NONCE,
  SMP_SEND_COMMITMENT,
  SMP_PROCESS_PAIRING_COMMITMENT,
  SMP_PROCESS_PEER_NONCE,
  SMP_CALCULATE_LOCAL_DHKEY_CHECK,
  SMP_SEND_DHKEY_CHECK,
  SMP_PROCESS_DHKEY_CHECK,
  SMP_CALCULATE_PEER_DHKEY_CHECK,
  SMP_MATCH_DHKEY_CHECKS,
  SMP_CALCULATE_NUMERIC_COMPARISON_DISPLAY_NUMBER,
  SMP_MOVE_TO_SEC_CONN_PHASE2,
  SMP_PH2_DHKEY_CHECKS_ARE_PRESENT,
  SMP_WAIT_FOR_BOTH_PUBLIC_KEYS,
  SMP_START_PASSKEY_VERIFICATION,
  SMP_SEND_KEYPRESS_NOTIFICATION,
  SMP_PROCESS_KEYPRESS_NOTIFICATION,
  SMP_PROCESS_SECURE_CONNECTION_OOB_DATA,
  SMP_SET_LOCAL_OOB_KEYS,
  SMP_SET_LOCAL_OOB_RAND_COMMITMENT,
  SMP_IDLE_TERMINATE,
  SMP_SM_NO_ACTION
};

static const tSMP_ACT smp_sm_action[] = {
    smp_proc_sec_req,
    smp_send_pair_req,
    smp_send_pair_rsp,
    smp_send_confirm,
    smp_send_pair_fail,
    smp_send_rand,
    smp_send_enc_info,
    smp_send_id_info,
    smp_send_ltk_reply,
    smp_proc_pair_cmd,
    smp_proc_pair_fail,
    smp_proc_confirm,
    smp_proc_rand,
    smp_proc_enc_info,
    smp_proc_central_id,
    smp_proc_id_info,
    smp_proc_id_addr,
    smp_proc_srk_info,
    smp_proc_sec_grant,
    smp_proc_sl_key,
    smp_proc_compare,
    smp_process_io_response,
    smp_generate_compare,
    smp_generate_srand_mrand_confirm,
    smp_generate_stk,
    smp_key_distribution,
    smp_start_enc,
    smp_pairing_cmpl,
    smp_decide_association_model,
    smp_send_app_cback,
    smp_check_auth_req,
    smp_pair_terminate,
    smp_enc_cmpl,
    smp_sirk_verify,
    smp_proc_discard,
    smp_create_private_key,
    smp_use_oob_private_key,
    smp_send_pair_public_key,
    smp_process_pairing_public_key,
    smp_both_have_public_keys,
    smp_start_secure_connection_phase1,
    smp_process_local_nonce,
    smp_send_commitment,
    smp_process_pairing_commitment,
    smp_process_peer_nonce,
    smp_calculate_local_dhkey_check,
    smp_send_dhkey_check,
    smp_process_dhkey_check,
    smp_calculate_peer_dhkey_check,
    smp_match_dhkey_checks,
    smp_calculate_numeric_comparison_display_number,
    smp_move_to_secure_connections_phase2,
    smp_phase_2_dhkey_checks_are_present,
    smp_wait_for_both_public_keys,
    smp_start_passkey_verification,
    smp_send_keypress_notification,
    smp_process_keypress_notification,
    smp_process_secure_connection_oob_data,
    smp_set_local_oob_keys,
    smp_set_local_oob_random_commitment,
    smp_idle_terminate};

/************ SMP Central FSM State/Event Indirection Table **************/
static const uint8_t smp_central_entry_map[][SMP_STATE_MAX] = {
    /* state name: */
    /* Idle, WaitApp Rsp, SecReq Pend, Pair ReqRsp, Wait Cfm,
       Confirm, Rand, PublKey Exch, SCPhs1 Strt, Wait Cmtm, Wait Nonce,
       SCPhs2 Strt, Wait DHKChk, DHKChk, Enc Pend, Bond Pend, CrLocSc OobData
     */
    /* PAIR_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_RSP */
    {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* CONFIRM */
    {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* RAND */
    {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0},
    /* PAIR_FAIL */
    {0, 0x81, 0, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81,
     0x81, 0, 0x81, 0},
    /* ENC_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
    /* CENTRAL_ID */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0},
    /* ID_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0},
    /* ID_ADDR */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0},
    /* SIGN_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0},
    /* SEC_REQ */
    {2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_PUBLIC_KEY */
    {0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_DHKEY_CHCK */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
    /* PAIR_KEYPR_NOTIF */
    {0, 8, 0, 0, 0, 0, 0, 0, 5, 2, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_COMMITM */
    {0, 0, 0, 0, 0, 0, 0, 0, 6, 1, 0, 0, 0, 0, 0, 0, 0},
    /* KEY_READY */
    {0, 3, 0, 3, 1, 0, 2, 0, 4, 0, 0, 0, 0, 0, 1, 6, 0},
    /* ENC_CMPL */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0},
    /* L2C_CONN */
    {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* L2C_DISC */
    {3, 0x83, 0, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83,
     0x83, 0x83, 0x83, 0},
    /* IO_RSP */
    {0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SEC_GRANT */
    {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* TK_REQ */
    {0, 0, 0, 2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0},
    /* AUTH_CMPL */
    {4, 0x82, 0, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82,
     0x82, 0x82, 7, 0},
    /* ENC_REQ */
    {0, 4, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0},
    /* BOND_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0},
    /* DISCARD_SEC_REQ */
    {0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0},
    /* PUBL_KEY_EXCH_REQ */
    {0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* LOC_PUBL_KEY_CRTD */
    {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1},
    /* BOTH_PUBL_KEYS_RCVD */
    {0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_DHKEY_CMPLT */
    {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
    /* HAVE_LOC_NONCE */
    {0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 2},
    /* SC_PHASE1_CMPLT */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
    /* SC_CALC_NC */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0},
    /* SC_DSPL_NC */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0},
    /* SC_NC_OK */
    {0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_2_DHCK_CHKS_PRES */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_KEY_READY */
    {0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
    /* KEYPR_NOTIF */
    {0, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_OOB_DATA */
    {0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* CR_LOC_SC_OOB_DATA */
    {5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SIRK_VERIFY */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x82, 0},
};

static const uint8_t smp_all_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action             Next State */
    /* PAIR_FAIL */
    {SMP_PROC_PAIR_FAIL, SMP_PAIRING_CMPL, SMP_STATE_IDLE},
    /* AUTH_CMPL */
    {SMP_SEND_PAIR_FAIL, SMP_PAIRING_CMPL, SMP_STATE_IDLE},
    /* L2C_DISC */
    {SMP_PAIR_TERMINATE, SMP_SM_NO_ACTION, SMP_STATE_IDLE}};

static const uint8_t smp_central_idle_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action               Next State */
    /* L2C_CONN */
    {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
    /* SEC_REQ */
    {SMP_PROC_SEC_REQ, SMP_SEND_APP_CBACK, SMP_STATE_WAIT_APP_RSP},
    /* L2C_DISC */
    {SMP_IDLE_TERMINATE, SMP_SM_NO_ACTION, SMP_STATE_IDLE},
    /* AUTH_CMPL */
    {SMP_PAIRING_CMPL, SMP_SM_NO_ACTION, SMP_STATE_IDLE},
    /* CR_LOC_SC_OOB_DATA */
    {SMP_CREATE_PRIVATE_KEY, SMP_SM_NO_ACTION,
     SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA}

};

static const uint8_t
    smp_central_wait_for_app_response_table[][SMP_SM_NUM_COLS] = {
        /* Event                Action               Next State */
        /* SEC_GRANT */
        {SMP_PROC_SEC_GRANT, SMP_SEND_APP_CBACK, SMP_STATE_WAIT_APP_RSP},
        /* IO_RSP */
        {SMP_SEND_PAIR_REQ, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},

        /* TK ready */
        /* KEY_READY */
        {SMP_GENERATE_CONFIRM, SMP_SM_NO_ACTION, SMP_STATE_WAIT_CONFIRM},

        /* start enc mode setup */
        /* ENC_REQ */
        {SMP_START_ENC, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
        /* DISCARD_SEC_REQ */
        {SMP_PROC_DISCARD, SMP_SM_NO_ACTION, SMP_STATE_IDLE}
        /* user confirms NC 'OK', i.e. phase 1 is completed */
        /* SC_NC_OK */,
        {SMP_MOVE_TO_SEC_CONN_PHASE2, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS2_START},
        /* user-provided passkey is rcvd */
        /* SC_KEY_READY */
        {SMP_START_PASSKEY_VERIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* PAIR_KEYPR_NOTIF */
        {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
         SMP_STATE_WAIT_APP_RSP},
        /* KEYPR_NOTIF */
        {SMP_SEND_KEYPRESS_NOTIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_WAIT_APP_RSP},
        /* SC_OOB_DATA */
        {SMP_USE_OOB_PRIVATE_KEY, SMP_SM_NO_ACTION, SMP_STATE_PUBLIC_KEY_EXCH}};

static const uint8_t
    smp_central_pair_request_response_table[][SMP_SM_NUM_COLS] = {
        /* Event                  Action            Next State */
        /* PAIR_RSP */
        {SMP_PROC_PAIR_CMD, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
        /* TK_REQ */
        {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},

        /* TK ready */
        /* KEY_READY */
        {SMP_GENERATE_CONFIRM, SMP_SM_NO_ACTION, SMP_STATE_WAIT_CONFIRM}
        /* PUBL_KEY_EXCH_REQ */,
        {SMP_CREATE_PRIVATE_KEY, SMP_SM_NO_ACTION, SMP_STATE_PUBLIC_KEY_EXCH}};

static const uint8_t smp_central_wait_for_confirm_table[][SMP_SM_NUM_COLS] = {
    /* Event                Action            Next State */
    /* KEY_READY*/
    /* CONFIRM ready */
    {SMP_SEND_CONFIRM, SMP_SM_NO_ACTION, SMP_STATE_CONFIRM}};

static const uint8_t smp_central_confirm_table[][SMP_SM_NUM_COLS] = {
    /* Event            Action         Next State */
    /* CONFIRM */
    {SMP_PROC_CONFIRM, SMP_SEND_RAND, SMP_STATE_RAND}};

static const uint8_t smp_central_rand_table[][SMP_SM_NUM_COLS] = {
    /*               Event                  Action Next State */
    /* RAND */
    {SMP_PROC_RAND, SMP_GENERATE_COMPARE, SMP_STATE_RAND},
    /* KEY_READY */
    {SMP_PROC_COMPARE, SMP_SM_NO_ACTION, SMP_STATE_RAND}, /* Compare ready */
    /* ENC_REQ */
    {SMP_GENERATE_STK, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING}};

static const uint8_t smp_central_public_key_exchange_table[][SMP_SM_NUM_COLS] =
    {
        /* Event                        Action              Next State */
        /* LOC_PUBL_KEY_CRTD */
        {SMP_SEND_PAIR_PUBLIC_KEY, SMP_SM_NO_ACTION, SMP_STATE_PUBLIC_KEY_EXCH},
        /* PAIR_PUBLIC_KEY */
        {SMP_PROCESS_PAIR_PUBLIC_KEY, SMP_SM_NO_ACTION,
         SMP_STATE_PUBLIC_KEY_EXCH},
        /* BOTH_PUBL_KEYS_RCVD */
        {SMP_HAVE_BOTH_PUBLIC_KEYS, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
};

static const uint8_t smp_central_sec_conn_phs1_start_table[][SMP_SM_NUM_COLS] =
    {
        /* Event                  Action                Next State */
        /* SC_DHKEY_CMPLT */
        {SMP_START_SEC_CONN_PHASE1, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* HAVE_LOC_NONCE */
        {SMP_PROCESS_LOCAL_NONCE, SMP_SM_NO_ACTION, SMP_STATE_WAIT_COMMITMENT},
        /* TK_REQ */
        {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
        /* SMP_MODEL_SEC_CONN_PASSKEY_DISP model, passkey is sent up to
           display,*/
        /* It's time to start commitment calculation */
        /* KEY_READY */
        {SMP_START_PASSKEY_VERIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* PAIR_KEYPR_NOTIF */
        {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* PAIR_COMMITM */
        {SMP_PROCESS_PAIRING_COMMITMENT, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
};

static const uint8_t smp_central_wait_commitment_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* PAIR_COMMITM */
    {SMP_PROCESS_PAIRING_COMMITMENT, SMP_SEND_RAND, SMP_STATE_WAIT_NONCE},
    /* PAIR_KEYPR_NOTIF */
    {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
     SMP_STATE_WAIT_COMMITMENT},
};

static const uint8_t smp_central_wait_nonce_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* peer nonce is received */
    /* RAND */
    {SMP_PROC_RAND, SMP_PROCESS_PEER_NONCE, SMP_STATE_SEC_CONN_PHS2_START},
    /* NC model, time to calculate number for NC */
    /* SC_CALC_NC */
    {SMP_CALCULATE_NUMERIC_COMPARISON_DISPLAY_NUMBER, SMP_SM_NO_ACTION,
     SMP_STATE_WAIT_NONCE},
    /* NC model, time to display calculated number for NC to the user */
    /* SC_DSPL_NC */
    {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
};

static const uint8_t smp_central_sec_conn_phs2_start_table[][SMP_SM_NUM_COLS] =
    {
        /* Event                           Action                 Next State */
        /* SC_PHASE1_CMPLT */
        {SMP_CALCULATE_LOCAL_DHKEY_CHECK, SMP_SEND_DHKEY_CHECK,
         SMP_STATE_WAIT_DHK_CHECK},
};

static const uint8_t smp_central_wait_dhk_check_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                          Next State */
    /* PAIR_DHKEY_CHCK */
    {SMP_PROCESS_DHKEY_CHECK, SMP_CALCULATE_PEER_DHKEY_CHECK,
     SMP_STATE_DHK_CHECK},
};

static const uint8_t smp_central_dhk_check_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* locally calculated peer dhkey check is ready -> compare it withs DHKey
     * Check
     * actually received from peer */
    /* SC_KEY_READY */
    {SMP_MATCH_DHKEY_CHECKS, SMP_SM_NO_ACTION, SMP_STATE_DHK_CHECK},
    /* locally calculated peer dhkey check is ready -> calculate STK, go to
     * sending
     */
    /* HCI LE Start Encryption command */
    /* ENC_REQ */
    {SMP_GENERATE_STK, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
};

static const uint8_t smp_central_enc_pending_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* STK ready */
    /* KEY_READY */
    {SMP_START_ENC, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
    /* ENCRYPTED */
    {SMP_CHECK_AUTH_REQ, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
    /* BOND_REQ */
    {SMP_KEY_DISTRIBUTE, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING}};

static const uint8_t smp_central_bond_pending_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* ENC_INFO */
    {SMP_PROC_ENC_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* ID_INFO */
    {SMP_PROC_ID_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* SIGN_INFO */
    {SMP_PROC_SRK_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* CENTRAL_ID */
    {SMP_PROC_CENTRAL_ID, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* ID_ADDR */
    {SMP_PROC_ID_ADDR, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* KEY_READY */
    /* LTK ready */
    {SMP_SEND_ENC_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* AUTH_CMPL */
    {SMP_SIRK_VERIFY, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
};

static const uint8_t
    smp_central_create_local_sec_conn_oob_data[][SMP_SM_NUM_COLS] = {
        /* Event                   Action            Next State */
        /* LOC_PUBL_KEY_CRTD */
        {SMP_SET_LOCAL_OOB_KEYS, SMP_SM_NO_ACTION,
         SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA},
        /* HAVE_LOC_NONCE */
        {SMP_SET_LOCAL_OOB_RAND_COMMITMENT, SMP_SM_NO_ACTION, SMP_STATE_IDLE}};

/************ SMP Peripheral FSM State/Event Indirection Table **************/
static const uint8_t smp_peripheral_entry_map[][SMP_STATE_MAX] = {
    /* state name: */
    /* Idle, WaitApp Rsp, SecReq Pend, Pair ReqRsp, Wait Cfm, Confirm, Rand,
       PublKey Exch, SCPhs1 Strt, Wait Cmtm, Wait Nonce, SCPhs2 Strt, Wait
       DHKChk, DHKChk, Enc Pend, Bond Pend, CrLocSc OobData */
    /* PAIR_REQ */
    {2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_RSP */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* CONFIRM */
    {0, 4, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* RAND */
    {0, 0, 0, 0, 0, 1, 2, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0},
    /* PAIR_FAIL */
    {0, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81,
     0x81, 0x81, 0, 0},
    /* ENC_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0},
    /* CENTRAL_ID */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 0},
    /* ID_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0},
    /* ID_ADDR */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0},
    /* SIGN_INFO */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0},
    /* SEC_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_PUBLIC_KEY */
    {0, 0, 0, 5, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_DHKEY_CHCK */
    {0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 2, 0, 0, 0},
    /* PAIR_KEYPR_NOTIF */
    {0, 9, 0, 0, 0, 0, 0, 0, 5, 2, 0, 0, 0, 0, 0, 0, 0},
    /* PAIR_COMMITM */
    {0, 8, 0, 0, 0, 0, 0, 0, 6, 1, 0, 0, 0, 0, 0, 0, 0},
    /* KEY_READY */
    {0, 3, 0, 3, 2, 2, 1, 0, 4, 0, 0, 0, 0, 0, 2, 1, 0},
    /* ENC_CMPL */
    {0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0},
    /* L2C_CONN */
    {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* L2C_DISC */
    {0, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83, 0x83,
     0x83, 0x83, 0x83, 0},
    /* IO_RSP */
    {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SEC_GRANT */
    {0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* TK_REQ */
    {0, 0, 0, 2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0},
    /* AUTH_CMPL */
    {0, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82, 0x82,
     0x82, 0x82, 0x82, 0},
    /* ENC_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0},
    /* BOND_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 1},
    /* DISCARD_SEC_REQ */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* PUBL_KEY_EXCH_REQ */
    {0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* LOC_PUBL_KEY_CRTD */
    {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1},
    /* BOTH_PUBL_KEYS_RCVD */
    {0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_DHKEY_CMPLT */
    {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
    /* HAVE_LOC_NONCE */
    {0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 2},
    /* SC_PHASE1_CMPLT */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
    /* SC_CALC_NC */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0},
    /* SC_DSPL_NC */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0},
    /* SC_NC_OK */
    {0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_2_DHCK_CHKS_PRES */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0},
    /* SC_KEY_READY */
    {0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
    /* KEYPR_NOTIF */
    {0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SC_OOB_DATA */
    {0, 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* CR_LOC_SC_OOB_DATA */
    {3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
    /* SIRK_VERIFY */
    {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
};

static const uint8_t smp_peripheral_idle_table[][SMP_SM_NUM_COLS] = {
    /* Event                 Action                Next State */
    /* L2C_CONN */
    {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
    /* PAIR_REQ */
    {SMP_PROC_PAIR_CMD, SMP_SEND_APP_CBACK, SMP_STATE_WAIT_APP_RSP},
    /* CR_LOC_SC_OOB_DATA */
    {SMP_CREATE_PRIVATE_KEY, SMP_SM_NO_ACTION,
     SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA}};

static const uint8_t
    smp_peripheral_wait_for_app_response_table[][SMP_SM_NUM_COLS] = {
        /* Event                   Action                 Next State */
        /* IO_RSP */
        {SMP_PROC_IO_RSP, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
        /* SEC_GRANT */
        {SMP_PROC_SEC_GRANT, SMP_SEND_APP_CBACK, SMP_STATE_WAIT_APP_RSP},

        /* TK ready */
        /* KEY_READY */
        {SMP_PROC_SL_KEY, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
        /* CONFIRM */
        {SMP_PROC_CONFIRM, SMP_SM_NO_ACTION, SMP_STATE_CONFIRM},
        /* DHKey Check from central is received before phase 1 is completed -
           race */
        /* PAIR_DHKEY_CHCK */
        {SMP_PROCESS_DHKEY_CHECK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
        /* user confirms NC 'OK', i.e. phase 1 is completed */
        /* SC_NC_OK */
        {SMP_MOVE_TO_SEC_CONN_PHASE2, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS2_START},
        /* user-provided passkey is rcvd */
        /* SC_KEY_READY */
        {SMP_START_PASSKEY_VERIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* PAIR_COMMITM */
        {SMP_PROCESS_PAIRING_COMMITMENT, SMP_SM_NO_ACTION,
         SMP_STATE_WAIT_APP_RSP},
        /* PAIR_KEYPR_NOTIF */
        {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
         SMP_STATE_WAIT_APP_RSP},
        /* KEYPR_NOTIF */
        {SMP_SEND_KEYPRESS_NOTIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_WAIT_APP_RSP},
        /* SC_OOB_DATA */
        {SMP_SEND_PAIR_RSP, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
};

static const uint8_t smp_peripheral_sec_request_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* PAIR_REQ */
    {SMP_PROC_PAIR_CMD, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
    /* ENCRYPTED*/
    {SMP_ENC_CMPL, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
};

static const uint8_t
    smp_peripheral_pair_request_response_table[][SMP_SM_NUM_COLS] = {
        /* Event                  Action                 Next State */
        /* CONFIRM */
        {SMP_PROC_CONFIRM, SMP_SM_NO_ACTION, SMP_STATE_CONFIRM},
        /* TK_REQ */
        {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},

        /* TK/Confirm ready */
        /* KEY_READY */
        {SMP_PROC_SL_KEY, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
        /* PUBL_KEY_EXCH_REQ */
        {SMP_CREATE_PRIVATE_KEY, SMP_SM_NO_ACTION, SMP_STATE_PUBLIC_KEY_EXCH},
        /* PAIR_PUBLIC_KEY */
        {SMP_PROCESS_PAIR_PUBLIC_KEY, SMP_SM_NO_ACTION, SMP_STATE_PAIR_REQ_RSP},
};

static const uint8_t smp_peripheral_wait_confirm_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* CONFIRM */
    {SMP_PROC_CONFIRM, SMP_SEND_CONFIRM, SMP_STATE_CONFIRM},
    /* KEY_READY*/
    {SMP_PROC_SL_KEY, SMP_SM_NO_ACTION, SMP_STATE_WAIT_CONFIRM}};

static const uint8_t smp_peripheral_confirm_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* RAND */
    {SMP_PROC_RAND, SMP_GENERATE_COMPARE, SMP_STATE_RAND},

    /* TK/Confirm ready */
    /* KEY_READY*/
    {SMP_PROC_SL_KEY, SMP_SM_NO_ACTION, SMP_STATE_CONFIRM}};

static const uint8_t smp_peripheral_rand_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* KEY_READY */
    {SMP_PROC_COMPARE, SMP_SM_NO_ACTION, SMP_STATE_RAND}, /* compare match */
    /* RAND */
    {SMP_SEND_RAND, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING}};

static const uint8_t smp_peripheral_public_key_exch_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* LOC_PUBL_KEY_CRTD */
    {SMP_WAIT_FOR_BOTH_PUBLIC_KEYS, SMP_SM_NO_ACTION,
     SMP_STATE_PUBLIC_KEY_EXCH},
    /* PAIR_PUBLIC_KEY */
    {SMP_PROCESS_PAIR_PUBLIC_KEY, SMP_SM_NO_ACTION, SMP_STATE_PUBLIC_KEY_EXCH},
    /* BOTH_PUBL_KEYS_RCVD */
    {SMP_HAVE_BOTH_PUBLIC_KEYS, SMP_SM_NO_ACTION,
     SMP_STATE_SEC_CONN_PHS1_START},
};

static const uint8_t
    smp_peripheral_sec_conn_phs1_start_table[][SMP_SM_NUM_COLS] = {
        /* Event                  Action                 Next State */
        /* SC_DHKEY_CMPLT */
        {SMP_START_SEC_CONN_PHASE1, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* HAVE_LOC_NONCE */
        {SMP_PROCESS_LOCAL_NONCE, SMP_SM_NO_ACTION, SMP_STATE_WAIT_COMMITMENT},
        /* TK_REQ */
        {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
        /* SMP_MODEL_SEC_CONN_PASSKEY_DISP model, passkey is sent up to display,
         * it's
         * time to start */
        /* commitment calculation */
        /* KEY_READY */
        {SMP_START_PASSKEY_VERIFICATION, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
        /* PAIR_KEYPR_NOTIF */
        {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
         SMP_STATE_SEC_CONN_PHS1_START},
        /*COMMIT*/
        {SMP_PROCESS_PAIRING_COMMITMENT, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS1_START},
};

static const uint8_t smp_peripheral_wait_commitment_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* PAIR_COMMITM */
    {SMP_PROCESS_PAIRING_COMMITMENT, SMP_SEND_COMMITMENT, SMP_STATE_WAIT_NONCE},
    /* PAIR_KEYPR_NOTIF */
    {SMP_PROCESS_KEYPRESS_NOTIFICATION, SMP_SEND_APP_CBACK,
     SMP_STATE_WAIT_COMMITMENT},
};

static const uint8_t smp_peripheral_wait_nonce_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* peer nonce is received */
    /* RAND */
    {SMP_PROC_RAND, SMP_PROCESS_PEER_NONCE, SMP_STATE_SEC_CONN_PHS2_START},
    /* NC model, time to calculate number for NC */
    /* SC_CALC_NC */
    {SMP_CALCULATE_NUMERIC_COMPARISON_DISPLAY_NUMBER, SMP_SM_NO_ACTION,
     SMP_STATE_WAIT_NONCE},
    /* NC model, time to display calculated number for NC to the user */
    /* SC_DSPL_NC */
    {SMP_SEND_APP_CBACK, SMP_SM_NO_ACTION, SMP_STATE_WAIT_APP_RSP},
};

static const uint8_t
    smp_peripheral_sec_conn_phs2_start_table[][SMP_SM_NUM_COLS] = {
        /* Event                  Action                 Next State */
        /* SC_PHASE1_CMPLT */
        {SMP_CALCULATE_LOCAL_DHKEY_CHECK, SMP_PH2_DHKEY_CHECKS_ARE_PRESENT,
         SMP_STATE_WAIT_DHK_CHECK},
        /* DHKey Check from central is received before peripheral DHKey
         * calculation is completed - race */
        /* PAIR_DHKEY_CHCK */
        {SMP_PROCESS_DHKEY_CHECK, SMP_SM_NO_ACTION,
         SMP_STATE_SEC_CONN_PHS2_START},
};

static const uint8_t smp_peripheral_wait_dhk_check_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* PAIR_DHKEY_CHCK */
    {SMP_PROCESS_DHKEY_CHECK, SMP_CALCULATE_PEER_DHKEY_CHECK,
     SMP_STATE_DHK_CHECK},
    /* DHKey Check from central was received before peripheral came to this
       state */
    /* SC_2_DHCK_CHKS_PRES */
    {SMP_CALCULATE_PEER_DHKEY_CHECK, SMP_SM_NO_ACTION, SMP_STATE_DHK_CHECK},
};

static const uint8_t smp_peripheral_dhk_check_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */

    /* locally calculated peer dhkey check is ready -> compare it withs DHKey
     * Check
     */
    /* actually received from peer */
    /* SC_KEY_READY */
    {SMP_MATCH_DHKEY_CHECKS, SMP_SM_NO_ACTION, SMP_STATE_DHK_CHECK},

    /* dhkey checks match -> send local dhkey check to central, go to wait for
     * HCI LE
     */
    /* Long Term Key Request Event */
    /* PAIR_DHKEY_CHCK */
    {SMP_SEND_DHKEY_CHECK, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
};

static const uint8_t smp_peripheral_enc_pending_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */
    /* ENC_REQ */
    {SMP_GENERATE_STK, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},

    /* STK ready */
    /* KEY_READY */
    {SMP_SEND_LTK_REPLY, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
    /* ENCRYPTED */
    {SMP_CHECK_AUTH_REQ, SMP_SM_NO_ACTION, SMP_STATE_ENCRYPTION_PENDING},
    /* BOND_REQ */
    {SMP_KEY_DISTRIBUTE, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING}};

static const uint8_t smp_peripheral_bond_pending_table[][SMP_SM_NUM_COLS] = {
    /* Event                  Action                 Next State */

    /* LTK ready */
    /* KEY_READY */
    {SMP_SEND_ENC_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},

    /* rev SRK */
    /* SIGN_INFO */
    {SMP_PROC_SRK_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* ENC_INFO */
    {SMP_PROC_ENC_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* ID_INFO */
    {SMP_PROC_ID_INFO, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* CENTRAL_ID*/
    {SMP_PROC_CENTRAL_ID, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* ID_ADDR */
    {SMP_PROC_ID_ADDR, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
    /* AUTH_CMPL */
    {SMP_SIRK_VERIFY, SMP_SM_NO_ACTION, SMP_STATE_BOND_PENDING},
};

static const uint8_t
    smp_peripheral_create_local_sec_conn_oob_data[][SMP_SM_NUM_COLS] = {
        /* Event                  Action                 Next State */
        /* LOC_PUBL_KEY_CRTD */
        {SMP_SET_LOCAL_OOB_KEYS, SMP_SM_NO_ACTION,
         SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA},
        /* HAVE_LOC_NONCE */
        {SMP_SET_LOCAL_OOB_RAND_COMMITMENT, SMP_SM_NO_ACTION, SMP_STATE_IDLE}};

static const tSMP_SM_TBL smp_state_table[][2] = {
    /* SMP_STATE_IDLE */
    {smp_central_idle_table, smp_peripheral_idle_table},

    /* SMP_STATE_WAIT_APP_RSP */
    {smp_central_wait_for_app_response_table,
     smp_peripheral_wait_for_app_response_table},

    /* SMP_STATE_SEC_REQ_PENDING */
    {NULL, smp_peripheral_sec_request_table},

    /* SMP_STATE_PAIR_REQ_RSP */
    {smp_central_pair_request_response_table,
     smp_peripheral_pair_request_response_table},

    /* SMP_STATE_WAIT_CONFIRM */
    {smp_central_wait_for_confirm_table, smp_peripheral_wait_confirm_table},

    /* SMP_STATE_CONFIRM */
    {smp_central_confirm_table, smp_peripheral_confirm_table},

    /* SMP_STATE_RAND */
    {smp_central_rand_table, smp_peripheral_rand_table},

    /* SMP_STATE_PUBLIC_KEY_EXCH */
    {smp_central_public_key_exchange_table,
     smp_peripheral_public_key_exch_table},

    /* SMP_STATE_SEC_CONN_PHS1_START */
    {smp_central_sec_conn_phs1_start_table,
     smp_peripheral_sec_conn_phs1_start_table},

    /* SMP_STATE_WAIT_COMMITMENT */
    {smp_central_wait_commitment_table, smp_peripheral_wait_commitment_table},

    /* SMP_STATE_WAIT_NONCE */
    {smp_central_wait_nonce_table, smp_peripheral_wait_nonce_table},

    /* SMP_STATE_SEC_CONN_PHS2_START */
    {smp_central_sec_conn_phs2_start_table,
     smp_peripheral_sec_conn_phs2_start_table},

    /* SMP_STATE_WAIT_DHK_CHECK */
    {smp_central_wait_dhk_check_table, smp_peripheral_wait_dhk_check_table},

    /* SMP_STATE_DHK_CHECK */
    {smp_central_dhk_check_table, smp_peripheral_dhk_check_table},

    /* SMP_STATE_ENCRYPTION_PENDING */
    {smp_central_enc_pending_table, smp_peripheral_enc_pending_table},

    /* SMP_STATE_BOND_PENDING */
    {smp_central_bond_pending_table, smp_peripheral_bond_pending_table},

    /* SMP_STATE_CREATE_LOCAL_SEC_CONN_OOB_DATA */
    {smp_central_create_local_sec_conn_oob_data,
     smp_peripheral_create_local_sec_conn_oob_data},
};

typedef const uint8_t (*tSMP_ENTRY_TBL)[SMP_STATE_MAX];
static const tSMP_ENTRY_TBL smp_entry_table[] = {smp_central_entry_map,
                                                 smp_peripheral_entry_map};

tSMP_CB smp_cb;

#define SMP_ALL_TBL_MASK 0x80

/*******************************************************************************
 * Function     smp_set_state
 * Returns      None
 ******************************************************************************/
void smp_set_state(tSMP_STATE state) {
  if (state < SMP_STATE_MAX) {
    log::debug("State change: {}({})==>{}({})",
               smp_get_state_name(smp_cb.state), smp_cb.state,
               smp_get_state_name(state), state);
    if (smp_cb.state != state) {
      BTM_LogHistory(
          kBtmLogTag, smp_cb.pairing_ble_bd_addr, "Security state changed",
          base::StringPrintf("%s => %s", smp_get_state_name(smp_cb.state),
                             smp_get_state_name(state)));
    }
    smp_cb.state = state;
  } else {
    log::error("invalid state={}", state);
  }
}

/*******************************************************************************
 * Function     smp_get_state
 * Returns      The smp state
 ******************************************************************************/
tSMP_STATE smp_get_state(void) { return smp_cb.state; }

/*******************************************************************************
 *
 * Function     smp_sm_event
 *
 * Description  Handle events to the state machine. It looks up the entry
 *              in the smp_entry_table array.
 *              If it is a valid entry, it gets the state table. Set the next
 *              state, if not NULL state. Execute the action function according
 *              to the state table. If the state returned by action function is
 *              not NULL state, adjust the new state to the returned state. If
 *              (api_evt != MAX), call callback function.
 *
 * Returns      true if the event is executed and a state transition can be
 *              expected, false if the event is ignored, state is invalid, or
 *              the role is invalid for the control block.
 *
 ******************************************************************************/
bool smp_sm_event(tSMP_CB* p_cb, tSMP_EVENT event, tSMP_INT_DATA* p_data) {
  uint8_t curr_state = p_cb->state;
  tSMP_SM_TBL state_table;
  uint8_t action, entry, i;

  log::debug("addr:{}", ADDRESS_TO_LOGGABLE_CSTR(p_cb->pairing_bda));
  if (p_cb->role >= 2) {
    log::error("Invalid role:{}", p_cb->role);
    return false;
  }

  tSMP_ENTRY_TBL entry_table = smp_entry_table[p_cb->role];

  if (curr_state >= SMP_STATE_MAX) {
    log::error("Invalid state:{}", curr_state);
    return false;
  }

  log::debug("Role:{}, State:[{}({})], Event:[{}({})]",
             hci_role_text(p_cb->role), smp_get_state_name(p_cb->state),
             p_cb->state, smp_get_event_name(event), event);

  /* look up the state table for the current state */
  /* lookup entry /w event & curr_state */
  /* If entry is ignore, return.
   * Otherwise, get state table (according to curr_state or all_state) */
  if ((event <= SMP_MAX_EVT) &&
      ((entry = entry_table[event - 1][curr_state]) != SMP_SM_IGNORE)) {
    if (entry & SMP_ALL_TBL_MASK) {
      entry &= ~SMP_ALL_TBL_MASK;
      state_table = smp_all_table;
    } else {
      state_table = smp_state_table[curr_state][p_cb->role];
    }
  } else {
    log::warn("Ignore event[{}({})] in state[{}({})]",
              smp_get_event_name(event), event, smp_get_state_name(curr_state),
              curr_state);
    return false;
  }

  /* Get possible next state from state table. */
  smp_set_state(state_table[entry - 1][SMP_SME_NEXT_STATE]);

  /* If action is not ignore, clear param, exec action and get next state.
   * The action function may set the Param for cback.
   * Depending on param, call cback or free buffer. */
  /* execute action */
  /* execute action functions */
  for (i = 0; i < SMP_NUM_ACTIONS; i++) {
    action = state_table[entry - 1][i];
    if (action != SMP_SM_NO_ACTION) {
      (*smp_sm_action[action])(p_cb, p_data);
    } else {
      break;
    }
  }
  log::debug("result state={}", smp_get_state_name(p_cb->state));
  return true;
}

/*******************************************************************************
 * Function     smp_get_state_name
 * Returns      The smp state name.
 ******************************************************************************/
const char* smp_get_state_name(tSMP_STATE state) {
  const char* p_str = smp_state_name[SMP_STATE_MAX];

  if (state < SMP_STATE_MAX) {
    p_str = smp_state_name[state];
  }
  return p_str;
}

/*******************************************************************************
 * Function     smp_get_event_name
 * Returns      The smp event name.
 ******************************************************************************/
const char* smp_get_event_name(tSMP_EVENT event) {
  const char* p_str = smp_event_name[SMP_MAX_EVT];

  if (event <= SMP_MAX_EVT) {
    p_str = smp_event_name[event - 1];
  }
  return p_str;
}
