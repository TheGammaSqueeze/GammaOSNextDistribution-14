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

#define LOG_TAG "SEC_CB"

#include "stack/btm/btm_sec_cb.h"

#include <bluetooth/log.h>

#include <cstdint>

#include "internal_include/bt_trace.h"
#include "internal_include/stack_config.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/fixed_queue.h"
#include "osi/include/list.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/bt_psm_types.h"
#include "types/raw_address.h"

using namespace bluetooth;

void tBTM_SEC_CB::Init(uint8_t initial_security_mode) {
  memset(&cfg, 0, sizeof(cfg));
  memset(&devcb, 0, sizeof(devcb));
  memset(&enc_rand, 0, sizeof(enc_rand));
  memset(&api, 0, sizeof(api));
  memset(&pin_code, 0, sizeof(pin_code));
  memset(sec_serv_rec, 0, sizeof(sec_serv_rec));
  connecting_bda = RawAddress::kEmpty;
  memset(&connecting_dc, 0, sizeof(connecting_dc));

  sec_pending_q = fixed_queue_new(SIZE_MAX);
  sec_collision_timer = alarm_new("btm.sec_collision_timer");
  pairing_timer = alarm_new("btm.pairing_timer");
  execution_wait_timer = alarm_new("btm.execution_wait_timer");

  security_mode = initial_security_mode;
  pairing_bda = RawAddress::kAny;
  sec_dev_rec = list_new([](void* ptr) {
    // Invoke destructor for all record objects and reset to default
    // initialized value so memory may be properly freed
    *((tBTM_SEC_DEV_REC*)ptr) = {};
    osi_free(ptr);
  });
}

void tBTM_SEC_CB::Free() {
  fixed_queue_free(sec_pending_q, nullptr);
  sec_pending_q = nullptr;

  list_free(sec_dev_rec);
  sec_dev_rec = nullptr;

  alarm_free(sec_collision_timer);
  sec_collision_timer = nullptr;

  alarm_free(pairing_timer);
  pairing_timer = nullptr;

  alarm_free(execution_wait_timer);
  execution_wait_timer = nullptr;
}

tBTM_SEC_CB btm_sec_cb;

void BTM_Sec_Init() {
  btm_sec_cb.Init(stack_config_get_interface()->get_pts_secure_only_mode()
                      ? BTM_SEC_MODE_SC
                      : BTM_SEC_MODE_SP);
}

void BTM_Sec_Free() { btm_sec_cb.Free(); }

/*******************************************************************************
 *
 * Function         find_first_serv_rec
 *
 * Description      Look for the first record in the service database
 *                  with specified PSM
 *
 * Returns          Pointer to the record or NULL
 *
 ******************************************************************************/
tBTM_SEC_SERV_REC* tBTM_SEC_CB::find_first_serv_rec(bool is_originator,
                                                    uint16_t psm) {
  tBTM_SEC_SERV_REC* p_serv_rec = &sec_serv_rec[0];
  int i;

  if (is_originator && p_out_serv && p_out_serv->psm == psm) {
    /* If this is outgoing connection and the PSM matches p_out_serv,
     * use it as the current service */
    return p_out_serv;
  }

  /* otherwise, just find the first record with the specified PSM */
  for (i = 0; i < BTM_SEC_MAX_SERVICE_RECORDS; i++, p_serv_rec++) {
    if ((p_serv_rec->security_flags & BTM_SEC_IN_USE) &&
        (p_serv_rec->psm == psm))
      return (p_serv_rec);
  }
  return (NULL);
}

tBTM_SEC_REC* tBTM_SEC_CB::getSecRec(const RawAddress bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  if (p_dev_rec) {
    return &p_dev_rec->sec_rec;
  }
  return nullptr;
}

bool tBTM_SEC_CB::IsDeviceEncrypted(const RawAddress bd_addr,
                                    tBT_TRANSPORT transport) {
  tBTM_SEC_REC* sec_rec = getSecRec(bd_addr);
  if (sec_rec) {
    if (transport == BT_TRANSPORT_BR_EDR) {
      return sec_rec->is_device_encrypted();
    } else if (transport == BT_TRANSPORT_LE) {
      return sec_rec->is_le_device_encrypted();
    }
    log::error("unknown transport:{}", bt_transport_text(transport).c_str());
    return false;
  }

  log::error("unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  return false;
}

bool tBTM_SEC_CB::IsLinkKeyAuthenticated(const RawAddress bd_addr,
                                         tBT_TRANSPORT transport) {
  tBTM_SEC_REC* sec_rec = getSecRec(bd_addr);
  if (sec_rec) {
    if (transport == BT_TRANSPORT_BR_EDR) {
      return sec_rec->is_link_key_authenticated();
    } else if (transport == BT_TRANSPORT_LE) {
      return sec_rec->is_le_link_key_authenticated();
    }
    log::error("unknown transport:{}", bt_transport_text(transport).c_str());
    return false;
  }

  log::error("unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  return false;
}

bool tBTM_SEC_CB::IsDeviceAuthenticated(const RawAddress bd_addr,
                                        tBT_TRANSPORT transport) {
  tBTM_SEC_REC* sec_rec = getSecRec(bd_addr);
  if (sec_rec) {
    if (transport == BT_TRANSPORT_BR_EDR) {
      return sec_rec->is_device_authenticated();
    } else if (transport == BT_TRANSPORT_LE) {
      return sec_rec->is_le_device_authenticated();
    }
    log::error("unknown transport:{}", bt_transport_text(transport).c_str());
    return false;
  }

  log::error("unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  return false;
}

bool tBTM_SEC_CB::IsLinkKeyKnown(const RawAddress bd_addr,
                                 tBT_TRANSPORT transport) {
  tBTM_SEC_REC* sec_rec = getSecRec(bd_addr);
  if (sec_rec) {
    if (transport == BT_TRANSPORT_BR_EDR) {
      return sec_rec->is_link_key_known();
    } else if (transport == BT_TRANSPORT_LE) {
      return sec_rec->is_le_link_key_known();
    }
    log::error("unknown transport:{}", bt_transport_text(transport).c_str());
    return false;
  }

  log::error("unknown device:{}", ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
  return false;
}

bool tBTM_SEC_CB::IsDeviceBonded(const RawAddress bd_addr) {
  tBTM_SEC_DEV_REC* p_dev_rec = btm_find_dev(bd_addr);
  bool is_bonded = false;

  if (p_dev_rec && ((p_dev_rec->sec_rec.ble_keys.key_type &&
                     p_dev_rec->sec_rec.is_le_link_key_known()) ||
                    p_dev_rec->sec_rec.is_link_key_known())) {
    is_bonded = true;
  }
  log::debug("Device record bonded check peer:{} is_bonded:{}",
             ADDRESS_TO_LOGGABLE_CSTR(bd_addr), logbool(is_bonded).c_str());
  return is_bonded;
}

#define BTM_NO_AVAIL_SEC_SERVICES ((uint16_t)0xffff)
bool tBTM_SEC_CB::AddService(bool is_originator, const char* p_name,
                             uint8_t service_id, uint16_t sec_level,
                             uint16_t psm, uint32_t mx_proto_id,
                             uint32_t mx_chan_id) {
  tBTM_SEC_SERV_REC* p_srec;
  uint16_t index;
  uint16_t first_unused_record = BTM_NO_AVAIL_SEC_SERVICES;
  bool record_allocated = false;

  log::verbose("sec_level:0x{:x}", sec_level);

  /* See if the record can be reused (same service name, psm, mx_proto_id,
     service_id, and mx_chan_id), or obtain the next unused record */

  p_srec = &sec_serv_rec[0];

  for (index = 0; index < BTM_SEC_MAX_SERVICE_RECORDS; index++, p_srec++) {
    /* Check if there is already a record for this service */
    if (p_srec->security_flags & BTM_SEC_IN_USE) {
      if (p_srec->psm == psm && p_srec->mx_proto_id == mx_proto_id &&
          service_id == p_srec->service_id && p_name &&
          (!strncmp(p_name, (char*)p_srec->orig_service_name,
                    /* strlcpy replaces end char with termination char*/
                    BT_MAX_SERVICE_NAME_LEN - 1) ||
           !strncmp(p_name, (char*)p_srec->term_service_name,
                    /* strlcpy replaces end char with termination char*/
                    BT_MAX_SERVICE_NAME_LEN - 1))) {
        record_allocated = true;
        break;
      }
    }
    /* Mark the first available service record */
    else if (!record_allocated) {
      *p_srec = {};
      record_allocated = true;
      first_unused_record = index;
    }
  }

  if (!record_allocated) {
    log::warn("Out of Service Records ({})", BTM_SEC_MAX_SERVICE_RECORDS);
    return (record_allocated);
  }

  /* Process the request if service record is valid */
  /* If a duplicate service wasn't found, use the first available */
  if (index >= BTM_SEC_MAX_SERVICE_RECORDS) {
    index = first_unused_record;
    p_srec = &sec_serv_rec[index];
  }

  p_srec->psm = psm;
  p_srec->service_id = service_id;
  p_srec->mx_proto_id = mx_proto_id;

  if (is_originator) {
    p_srec->orig_mx_chan_id = mx_chan_id;
    strlcpy((char*)p_srec->orig_service_name, p_name,
            BT_MAX_SERVICE_NAME_LEN + 1);
    /* clear out the old setting, just in case it exists */
    {
      p_srec->security_flags &=
          ~(BTM_SEC_OUT_ENCRYPT | BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_OUT_MITM);
    }

    /* Parameter validation.  Originator should not set requirements for
     * incoming connections */
    sec_level &= ~(BTM_SEC_IN_ENCRYPT | BTM_SEC_IN_AUTHENTICATE |
                   BTM_SEC_IN_MITM | BTM_SEC_IN_MIN_16_DIGIT_PIN);

    if (security_mode == BTM_SEC_MODE_SP || security_mode == BTM_SEC_MODE_SC) {
      if (sec_level & BTM_SEC_OUT_AUTHENTICATE) sec_level |= BTM_SEC_OUT_MITM;
    }

    /* Make sure the authenticate bit is set, when encrypt bit is set */
    if (sec_level & BTM_SEC_OUT_ENCRYPT) sec_level |= BTM_SEC_OUT_AUTHENTICATE;

    /* outgoing connections usually set the security level right before
     * the connection is initiated.
     * set it to be the outgoing service */
    p_out_serv = p_srec;
  } else {
    p_srec->term_mx_chan_id = mx_chan_id;
    strlcpy((char*)p_srec->term_service_name, p_name,
            BT_MAX_SERVICE_NAME_LEN + 1);
    /* clear out the old setting, just in case it exists */
    {
      p_srec->security_flags &=
          ~(BTM_SEC_IN_ENCRYPT | BTM_SEC_IN_AUTHENTICATE | BTM_SEC_IN_MITM |
            BTM_SEC_IN_MIN_16_DIGIT_PIN);
    }

    /* Parameter validation.  Acceptor should not set requirements for outgoing
     * connections */
    sec_level &=
        ~(BTM_SEC_OUT_ENCRYPT | BTM_SEC_OUT_AUTHENTICATE | BTM_SEC_OUT_MITM);

    if (security_mode == BTM_SEC_MODE_SP || security_mode == BTM_SEC_MODE_SC) {
      if (sec_level & BTM_SEC_IN_AUTHENTICATE) sec_level |= BTM_SEC_IN_MITM;
    }

    /* Make sure the authenticate bit is set, when encrypt bit is set */
    if (sec_level & BTM_SEC_IN_ENCRYPT) sec_level |= BTM_SEC_IN_AUTHENTICATE;
  }

  p_srec->security_flags |= (uint16_t)(sec_level | BTM_SEC_IN_USE);

  log::debug(
      "[{}]: id:{}, is_orig:{} psm:0x{:04x} proto_id:{} chan_id:{}  : "
      "sec:0x{:x} service_name:[{}] (up to {} chars saved)",
      index, service_id, logbool(is_originator).c_str(), psm, mx_proto_id,
      mx_chan_id, p_srec->security_flags, p_name, BT_MAX_SERVICE_NAME_LEN);

  return (record_allocated);
}

uint8_t tBTM_SEC_CB::RemoveServiceById(uint8_t service_id) {
  tBTM_SEC_SERV_REC* p_srec = &sec_serv_rec[0];
  uint8_t num_freed = 0;
  int i;

  for (i = 0; i < BTM_SEC_MAX_SERVICE_RECORDS; i++, p_srec++) {
    /* Delete services with specified name (if in use and not SDP) */
    if ((p_srec->security_flags & BTM_SEC_IN_USE) &&
        (p_srec->psm != BT_PSM_SDP) &&
        (!service_id || (service_id == p_srec->service_id))) {
      log::verbose("BTM_SEC_CLR[{}]: id:{}", i, service_id);
      p_srec->security_flags = 0;
      num_freed++;
    }
  }
  return (num_freed);
}

uint8_t tBTM_SEC_CB::RemoveServiceByPsm(uint16_t psm) {
  tBTM_SEC_SERV_REC* p_srec = &sec_serv_rec[0];
  uint8_t num_freed = 0;
  int i;

  for (i = 0; i < BTM_SEC_MAX_SERVICE_RECORDS; i++, p_srec++) {
    /* Delete services with specified name (if in use and not SDP) */
    if ((p_srec->security_flags & BTM_SEC_IN_USE) && (p_srec->psm == psm)) {
      log::verbose("BTM_SEC_CLR[{}]: id {} ", i, p_srec->service_id);
      p_srec->security_flags = 0;
      num_freed++;
    }
  }
  log::verbose("psm:0x{:x} num_freed:{}", psm, num_freed);

  return (num_freed);
}
