/*
 * Copyright 2023 The Android Open Source Project
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
 */

#pragma once

#include <cstdint>

#include "internal_include/bt_target.h"
#include "osi/include/alarm.h"
#include "osi/include/fixed_queue.h"
#include "osi/include/list.h"
#include "stack/btm/btm_sec_int_types.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/bt_octets.h"
#include "stack/include/security_client_callbacks.h"
#include "types/raw_address.h"

class tBTM_SEC_CB {
 public:
  tBTM_CFG cfg; /* Device configuration */

  /*****************************************************
  **     Local Device control block (on security)
  *****************************************************/
  tBTM_SEC_DEVCB devcb;

  uint16_t enc_handle{0};
  BT_OCTET8 enc_rand; /* received rand value from LTK request*/
  uint16_t ediv{0};   /* received ediv value from LTK request */
  uint8_t key_size{0};

 public:
  /*****************************************************
  **      Security Management
  *****************************************************/
  tBTM_APPL_INFO api;

  tBTM_SEC_DEV_REC* p_collided_dev_rec{nullptr};
  alarm_t* sec_collision_timer{nullptr};
  uint64_t collision_start_time{0};
  uint32_t dev_rec_count{0}; /* Counter used for device record timestamp */
  uint8_t security_mode{0};
  bool pairing_disabled{false};
  bool security_mode_changed{false}; /* mode changed during bonding */
  bool pin_type_changed{false};      /* pin type changed during bonding */
  bool sec_req_pending{false};       /*   true if a request is pending */

  uint8_t pin_code_len{0}; /* for legacy devices */
  PIN_CODE pin_code;       /* for legacy devices */
  tBTM_PAIRING_STATE pairing_state{
      BTM_PAIR_STATE_IDLE};               /* The current pairing state    */
  uint8_t pairing_flags{0};               /* The current pairing flags    */
  RawAddress pairing_bda;                 /* The device currently pairing */
  alarm_t* pairing_timer{nullptr};        /* Timer for pairing process    */
  alarm_t* execution_wait_timer{nullptr}; /* To avoid concurrent auth request */
  list_t* sec_dev_rec{nullptr}; /* list of tBTM_SEC_DEV_REC */
  tBTM_SEC_SERV_REC* p_out_serv{nullptr};
  tBTM_MKEY_CALLBACK* mkey_cback{nullptr};

  RawAddress connecting_bda;

  fixed_queue_t* sec_pending_q{nullptr}; /* pending sequrity requests in
                                            tBTM_SEC_QUEUE_ENTRY format */

  tBTM_SEC_SERV_REC sec_serv_rec[BTM_SEC_MAX_SERVICE_RECORDS];

  DEV_CLASS connecting_dc;

  void Init(uint8_t initial_security_mode);
  void Free();

  tBTM_SEC_SERV_REC* find_first_serv_rec(bool is_originator, uint16_t psm);

  bool IsDeviceBonded(const RawAddress bd_addr);
  bool IsDeviceEncrypted(const RawAddress bd_addr, tBT_TRANSPORT transport);
  bool IsDeviceAuthenticated(const RawAddress bd_addr, tBT_TRANSPORT transport);
  bool IsLinkKeyAuthenticated(const RawAddress bd_addr,
                              tBT_TRANSPORT transport);

  bool IsLinkKeyKnown(const RawAddress bd_addr, tBT_TRANSPORT transport);

  tBTM_SEC_REC* getSecRec(const RawAddress bd_addr);

  bool AddService(bool is_originator, const char* p_name, uint8_t service_id,
                  uint16_t sec_level, uint16_t psm, uint32_t mx_proto_id,
                  uint32_t mx_chan_id);
  uint8_t RemoveServiceById(uint8_t service_id);
  uint8_t RemoveServiceByPsm(uint16_t psm);

  void change_pairing_state(tBTM_PAIRING_STATE new_state);

  // misc static methods
  static const char* btm_pair_state_descr(tBTM_PAIRING_STATE state);
};

extern tBTM_SEC_CB btm_sec_cb;

void BTM_Sec_Init();
void BTM_Sec_Free();
