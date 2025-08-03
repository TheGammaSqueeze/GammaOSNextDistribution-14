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
 *  This file contains the audio gateway functions performing SDP
 *  operations.
 *
 ******************************************************************************/

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <bluetooth/log.h>

#include "bta/ag/bta_ag_int.h"
#include "bta/include/bta_hfp_api.h"
#include "bta/include/bta_rfcomm_scn.h"
#include "btif/include/btif_config.h"
#include "common/init_flags.h"
#include "device/include/interop.h"
#include "device/include/interop_config.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/btm/btm_sco_hfp_hal.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/main_thread.h"
#include "stack/include/sdp_api.h"
#include "storage/config_keys.h"
#include "types/bluetooth/uuid.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;
using bluetooth::Uuid;

/* Number of protocol elements in protocol element list. */
#define BTA_AG_NUM_PROTO_ELEMS 2

/* Number of elements in service class id list. */
#define BTA_AG_NUM_SVC_ELEMS 2

/* size of database for service discovery */
#ifndef BTA_AG_DISC_BUF_SIZE
#define BTA_AG_DISC_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/* declare sdp callback functions */
void bta_ag_sdp_cback_1(const RawAddress& bd_addr, tSDP_RESULT);
void bta_ag_sdp_cback_2(const RawAddress& bd_addr, tSDP_RESULT);
void bta_ag_sdp_cback_3(const RawAddress& bd_addr, tSDP_RESULT);
void bta_ag_sdp_cback_4(const RawAddress& bd_addr, tSDP_RESULT);
void bta_ag_sdp_cback_5(const RawAddress& bd_addr, tSDP_RESULT);
void bta_ag_sdp_cback_6(const RawAddress& bd_addr, tSDP_RESULT);

/* SDP callback function table */
typedef tSDP_DISC_CMPL_CB* tBTA_AG_SDP_CBACK;
const tBTA_AG_SDP_CBACK bta_ag_sdp_cback_tbl[] = {
    bta_ag_sdp_cback_1, bta_ag_sdp_cback_2, bta_ag_sdp_cback_3,
    bta_ag_sdp_cback_4, bta_ag_sdp_cback_5, bta_ag_sdp_cback_6};

/*******************************************************************************
 *
 * Function         bta_ag_sdp_cback
 *
 * Description      SDP callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_ag_sdp_cback(uint16_t status, uint8_t idx) {
  log::verbose("status:0x{:x}", status);
  tBTA_AG_SCB* p_scb = bta_ag_scb_by_idx(idx);
  if (p_scb) {
    uint16_t event;
    /* set event according to int/acp */
    if (p_scb->role == BTA_AG_ACP) {
      event = BTA_AG_DISC_ACP_RES_EVT;
    } else {
      event = BTA_AG_DISC_INT_RES_EVT;
    }
    tBTA_AG_DATA disc_result = {.disc_result = {.status = status}};
    do_in_main_thread(FROM_HERE, base::BindOnce(&bta_ag_sm_execute_by_handle,
                                                idx, event, disc_result));
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_sdp_cback_1 to 6
 *
 * Description      SDP callback functions.  Since there is no way to
 *                  distinguish scb from the callback we need separate
 *                  callbacks for each scb.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_sdp_cback_1(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 1);
}
void bta_ag_sdp_cback_2(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 2);
}
void bta_ag_sdp_cback_3(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 3);
}
void bta_ag_sdp_cback_4(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 4);
}
void bta_ag_sdp_cback_5(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 5);
}
void bta_ag_sdp_cback_6(UNUSED_ATTR const RawAddress& bd_addr,
                        tSDP_STATUS status) {
  bta_ag_sdp_cback(status, 6);
}

/******************************************************************************
 *
 * Function         bta_ag_add_record
 *
 * Description      This function is called by a server application to add
 *                  HSP or HFP information to an SDP record.  Prior to
 *                  calling this function the application must call
 *                  SDP_CreateRecord() to create an SDP record.
 *
 * Returns          true if function execution succeeded,
 *                  false if function execution failed.
 *
 *****************************************************************************/
bool bta_ag_add_record(uint16_t service_uuid, const char* p_service_name,
                       uint8_t scn, tBTA_AG_FEAT features,
                       uint32_t sdp_handle) {
  tSDP_PROTOCOL_ELEM proto_elem_list[BTA_AG_NUM_PROTO_ELEMS];
  uint16_t svc_class_id_list[BTA_AG_NUM_SVC_ELEMS];
  uint16_t browse_list[] = {UUID_SERVCLASS_PUBLIC_BROWSE_GROUP};
  uint16_t version;
  uint16_t profile_uuid;
  uint8_t network;
  bool result = true;
  bool codec_supported = false;
  uint8_t buf[2];

  log::verbose("uuid: {:x}", service_uuid);
  log::info("features: {}", features);

  for (auto& proto_element : proto_elem_list) {
    proto_element = {};
  }

  /* add the protocol element sequence */
  proto_elem_list[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  proto_elem_list[0].num_params = 0;
  proto_elem_list[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  proto_elem_list[1].num_params = 1;
  proto_elem_list[1].params[0] = scn;
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, BTA_AG_NUM_PROTO_ELEMS, proto_elem_list);

  /* add service class id list */
  svc_class_id_list[0] = service_uuid;
  svc_class_id_list[1] = UUID_SERVCLASS_GENERIC_AUDIO;
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, BTA_AG_NUM_SVC_ELEMS, svc_class_id_list);

  /* add profile descriptor list */
  if (service_uuid == UUID_SERVCLASS_AG_HANDSFREE) {
    profile_uuid = UUID_SERVCLASS_HF_HANDSFREE;
    if (bluetooth::common::init_flags::hfp_dynamic_version_is_enabled()) {
      version = HFP_VERSION_1_6;
    } else {
      version = get_default_hfp_version();
    }
  } else {
    profile_uuid = UUID_SERVCLASS_HEADSET;
    version = HSP_VERSION_1_2;
  }
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, profile_uuid, version);

  /* add service name */
  if (p_service_name != nullptr && p_service_name[0] != 0) {
    result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_SERVICE_NAME, TEXT_STR_DESC_TYPE,
        (uint32_t)(strlen(p_service_name) + 1), (uint8_t*)p_service_name);
  }

  /* add features and network */
  if (service_uuid == UUID_SERVCLASS_AG_HANDSFREE) {
    network = (features & BTA_AG_FEAT_REJECT) ? 1 : 0;
    result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_DATA_STORES_OR_NETWORK, UINT_DESC_TYPE, 1,
        &network);

    if (features & BTA_AG_FEAT_CODEC) codec_supported = true;

    features &= BTA_AG_SDP_FEAT_SPEC;

    /* Codec bit position is different in SDP and in BRSF */
    if (codec_supported) features |= BTA_AG_FEAT_WBS_SUPPORT;
    // check property for SWB support
    if (hfp_hal_interface::get_swb_supported()) {
      features |= BTA_AG_FEAT_SWB_SUPPORT;
    }

    UINT16_TO_BE_FIELD(buf, features);
    result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE, 2, buf);
  }

  /* add browse group list */
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, browse_list);

  return result;
}

/*******************************************************************************
 *
 * Function         bta_ag_create_records
 *
 * Description      Create SDP records for registered services.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_create_records(tBTA_AG_SCB* p_scb, const tBTA_AG_DATA& data) {
  int i;
  tBTA_SERVICE_MASK services;

  services = p_scb->reg_services >> BTA_HSP_SERVICE_ID;
  for (i = 0; i < BTA_AG_NUM_IDX && services != 0; i++, services >>= 1) {
    /* if service is set in mask */
    if (services & 1) {
      /* add sdp record if not already registered */
      if (bta_ag_cb.profile[i].sdp_handle == 0) {
        bta_ag_cb.profile[i].sdp_handle =
            get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
        bta_ag_cb.profile[i].scn = BTA_AllocateSCN();
        bta_ag_add_record(bta_ag_uuid[i], data.api_register.p_name[i],
                          bta_ag_cb.profile[i].scn, data.api_register.features,
                          bta_ag_cb.profile[i].sdp_handle);
        bta_sys_add_uuid(bta_ag_uuid[i]);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_del_records
 *
 * Description      Delete SDP records for any registered services.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_del_records(tBTA_AG_SCB* p_scb) {
  tBTA_AG_SCB* p = &bta_ag_cb.scb[0];
  tBTA_SERVICE_MASK services;
  tBTA_SERVICE_MASK others = 0;
  int i;

  /* get services of all other registered servers */
  for (i = 0; i < BTA_AG_NUM_IDX; i++, p++) {
    if (p_scb == p) {
      continue;
    }

    if (p->in_use && !p->dealloc) {
      others |= p->reg_services;
    }
  }

  others >>= BTA_HSP_SERVICE_ID;
  services = p_scb->reg_services >> BTA_HSP_SERVICE_ID;
  for (i = 0; i < BTA_AG_NUM_IDX && services != 0;
       i++, services >>= 1, others >>= 1) {
    /* if service registered for this scb and not registered for any other scb
     */
    if (((services & 1) == 1) && ((others & 1) == 0)) {
      log::verbose("bta_ag_del_records {}", i);
      if (bta_ag_cb.profile[i].sdp_handle != 0) {
        get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
            bta_ag_cb.profile[i].sdp_handle);
        bta_ag_cb.profile[i].sdp_handle = 0;
      }
      BTA_FreeSCN(bta_ag_cb.profile[i].scn);
      bta_sys_remove_uuid(bta_ag_uuid[i]);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_sdp_find_attr
 *
 * Description      Process SDP discovery results to find requested attributes
 *                  for requested service.
 *
 *
 * Returns          true if results found, false otherwise.
 *
 ******************************************************************************/
bool bta_ag_sdp_find_attr(tBTA_AG_SCB* p_scb, tBTA_SERVICE_MASK service) {
  tSDP_DISC_REC* p_rec = nullptr;
  tSDP_DISC_ATTR* p_attr;
  tSDP_PROTOCOL_ELEM pe;
  uint16_t uuid;
  bool result = false;

  if (service & BTA_HFP_SERVICE_MASK) {
    uuid = UUID_SERVCLASS_HF_HANDSFREE;
    /* If there is no cached peer version, use default one */
    if (p_scb->peer_version == HFP_HSP_VERSION_UNKNOWN) {
      p_scb->peer_version = HFP_VERSION_1_1; /* Default version */
    }
  } else if (service & BTA_HSP_SERVICE_MASK && p_scb->role == BTA_AG_INT) {
    uuid = UUID_SERVCLASS_HEADSET_HS;
    p_scb->peer_version = HSP_VERSION_1_2; /* Default version */
  } else {
    uuid = UUID_SERVCLASS_HEADSET_HS;
    p_scb->peer_version = HSP_VERSION_1_0;
  }

  /* loop through all records we found */
  while (true) {
    /* get next record; if none found, we're done */
    p_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(p_scb->p_disc_db,
                                                               uuid, p_rec);
    if (p_rec == nullptr) {
      if (uuid == UUID_SERVCLASS_HEADSET_HS) {
        /* Search again in case the peer device uses the old HSP UUID */
        uuid = UUID_SERVCLASS_HEADSET;
        p_scb->peer_version = HSP_VERSION_1_0;
        p_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(
            p_scb->p_disc_db, uuid, p_rec);
        if (p_rec == nullptr) {
          break;
        }
      } else
        break;
    }

    /* get scn from proto desc list if initiator */
    if (p_scb->role == BTA_AG_INT) {
      if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
              p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
        p_scb->peer_scn = (uint8_t)pe.params[0];
      } else {
        continue;
      }
    }

    /* get profile version (if failure, version parameter is not updated) */
    uint16_t peer_version = HFP_HSP_VERSION_UNKNOWN;
    if (!get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
            p_rec, uuid, &peer_version)) {
      log::warn("Get peer_version failed, using default 0x{:04x}",
                p_scb->peer_version);
      peer_version = p_scb->peer_version;
    }

    if (service & BTA_HFP_SERVICE_MASK) {
      /* Update cached peer version if the new one is different */
      if (peer_version != p_scb->peer_version) {
        p_scb->peer_version = peer_version;
        if (btif_config_set_bin(
                p_scb->peer_addr.ToString(), BTIF_STORAGE_KEY_HFP_VERSION,
                (const uint8_t*)&peer_version, sizeof(peer_version))) {
        } else {
          log::warn("Failed to store peer HFP version for {}",
                    ADDRESS_TO_LOGGABLE_CSTR(p_scb->peer_addr));
        }
      }
      /* get features if HFP */
      p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
          p_rec, ATTR_ID_SUPPORTED_FEATURES);
      if (p_attr != nullptr &&
          SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
        /* Found attribute. Get value. */
        /* There might be race condition between SDP and BRSF.  */
        /* Do not update if we already received BRSF.           */
        uint16_t sdp_features = p_attr->attr_value.v.u16;
        bool sdp_wbs_support = sdp_features & BTA_AG_FEAT_WBS_SUPPORT;
        if (!p_scb->received_at_bac && sdp_wbs_support) {
          // Workaround for misbehaving HFs (e.g. some Hyundai car kit) that:
          // 1. Indicate WBS support in SDP and codec negotiation in BRSF
          // 2. But do not send required AT+BAC command
          // Will assume mSBC is enabled and try codec negotiation by default
          p_scb->codec_updated = true;
          p_scb->peer_codecs = BTM_SCO_CODEC_CVSD | BTM_SCO_CODEC_MSBC;
          p_scb->sco_codec = UUID_CODEC_MSBC;
        }
        if (sdp_features != p_scb->peer_sdp_features) {
          p_scb->peer_sdp_features = sdp_features;
          if (btif_config_set_bin(p_scb->peer_addr.ToString(),
                                  BTIF_STORAGE_KEY_HFP_SDP_FEATURES,
                                  (const uint8_t*)&sdp_features,
                                  sizeof(sdp_features))) {
          } else {
            log::warn("Failed to store peer HFP SDP Features for {}",
                      ADDRESS_TO_LOGGABLE_CSTR(p_scb->peer_addr));
          }
        }
        if (p_scb->peer_features == 0) {
          p_scb->peer_features = sdp_features & HFP_SDP_BRSF_FEATURES_MASK;
        }
        /* Remote supports 1.7, store it in HFP 1.7 BL file */
        if (bluetooth::common::init_flags::hfp_dynamic_version_is_enabled()) {
          if (p_scb->peer_version >= HFP_VERSION_1_9) {
            interop_database_add_addr(INTEROP_HFP_1_9_ALLOWLIST,
                                      &p_scb->peer_addr, 3);
          } else if (p_scb->peer_version >= HFP_VERSION_1_7) {
            interop_database_add_addr(INTEROP_HFP_1_7_ALLOWLIST,
                                      &p_scb->peer_addr, 3);
          }
        }
      }
    } else {
      /* No peer version caching for HSP, use discovered one directly */
      p_scb->peer_version = peer_version;
      /* get features if HSP */
      p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
          p_rec, ATTR_ID_REMOTE_AUDIO_VOLUME_CONTROL);
      if (p_attr != nullptr &&
            SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == BOOLEAN_DESC_TYPE &&
            SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 1) {
        /* Remote volume control of HSP */
        if (p_attr->attr_value.v.u8)
          p_scb->peer_features |= BTA_AG_PEER_FEAT_VOL;
        else
          p_scb->peer_features &= ~BTA_AG_PEER_FEAT_VOL;
      }
    }

    /* found what we needed */
    result = true;
    break;
  }
  return result;
}

/*******************************************************************************
 *
 * Function         bta_ag_do_disc
 *
 * Description      Do service discovery.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_do_disc(tBTA_AG_SCB* p_scb, tBTA_SERVICE_MASK service) {
  Uuid uuid_list[1];
  uint16_t num_uuid = 1;
  uint16_t attr_list[4];
  uint8_t num_attr;

  /* HFP initiator; get proto list and features */
  if (service & BTA_HFP_SERVICE_MASK && p_scb->role == BTA_AG_INT) {
    attr_list[0] = ATTR_ID_SERVICE_CLASS_ID_LIST;
    attr_list[1] = ATTR_ID_PROTOCOL_DESC_LIST;
    attr_list[2] = ATTR_ID_BT_PROFILE_DESC_LIST;
    attr_list[3] = ATTR_ID_SUPPORTED_FEATURES;
    num_attr = 4;
    uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HF_HANDSFREE);
  }
  /* HFP acceptor; get features */
  else if (service & BTA_HFP_SERVICE_MASK && p_scb->role == BTA_AG_ACP) {
    attr_list[0] = ATTR_ID_SERVICE_CLASS_ID_LIST;
    attr_list[1] = ATTR_ID_BT_PROFILE_DESC_LIST;
    attr_list[2] = ATTR_ID_SUPPORTED_FEATURES;
    num_attr = 3;
    uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HF_HANDSFREE);
  }
  /* HSP initiator; get proto list */
  else if (service & BTA_HSP_SERVICE_MASK && p_scb->role == BTA_AG_INT) {
    attr_list[0] = ATTR_ID_SERVICE_CLASS_ID_LIST;
    attr_list[1] = ATTR_ID_PROTOCOL_DESC_LIST;
    attr_list[2] = ATTR_ID_BT_PROFILE_DESC_LIST;
    attr_list[3] = ATTR_ID_REMOTE_AUDIO_VOLUME_CONTROL;
    num_attr = 4;
    // Although UUID_SERVCLASS_HEADSET_HS (0x1131) is to be used in HSP 1.2,
    // some HSP 1.2 implementations, such as PTS, still use
    // UUID_SERVCLASS_HEADSET (0x1108) to store its service record. However,
    // most of such devices are HSP 1.0 devices.
    if (p_scb->hsp_version >= HSP_VERSION_1_2) {
      uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HEADSET_HS);
    } else {
      uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HEADSET);
    }
  } else {
    /* HSP acceptor; get features */
    attr_list[0] = ATTR_ID_SERVICE_CLASS_ID_LIST;
    attr_list[1] = ATTR_ID_PROTOCOL_DESC_LIST;
    attr_list[2] = ATTR_ID_BT_PROFILE_DESC_LIST;
    attr_list[3] = ATTR_ID_REMOTE_AUDIO_VOLUME_CONTROL;
    num_attr = 4;

    if (p_scb->hsp_version >= HSP_VERSION_1_2) {
      uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HEADSET_HS);
    } else {
      /* Legacy from HSP v1.0 */
      uuid_list[0] = Uuid::From16Bit(UUID_SERVCLASS_HEADSET);
    }
  }

  if (p_scb->p_disc_db != nullptr) {
    log::error("Discovery already in progress... returning.");
    return;
  }

  /* allocate buffer for sdp database */
  p_scb->p_disc_db = (tSDP_DISCOVERY_DB*)osi_malloc(BTA_AG_DISC_BUF_SIZE);
  /* set up service discovery database; attr happens to be attr_list len */
  if (get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
          p_scb->p_disc_db, BTA_AG_DISC_BUF_SIZE, num_uuid, uuid_list, num_attr,
          attr_list)) {
    if (get_legacy_stack_sdp_api()->service.SDP_ServiceSearchAttributeRequest(
            p_scb->peer_addr, p_scb->p_disc_db,
            bta_ag_sdp_cback_tbl[bta_ag_scb_to_idx(p_scb) - 1])) {
      return;
    } else {
      log::error("failed to start SDP discovery for {}",
                 ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr));
    }
  } else {
    log::error("failed to init SDP discovery database for {}",
               ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr));
  }
  // Failure actions
  bta_ag_free_db(p_scb, tBTA_AG_DATA::kEmpty);
  bta_ag_sm_execute(p_scb, BTA_AG_DISC_FAIL_EVT, tBTA_AG_DATA::kEmpty);
}

/*******************************************************************************
 *
 * Function         bta_ag_free_db
 *
 * Description      Free discovery database.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_free_db(tBTA_AG_SCB* p_scb, const tBTA_AG_DATA& data) {
  osi_free_and_reset((void**)&p_scb->p_disc_db);
}
