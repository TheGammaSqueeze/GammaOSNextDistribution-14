/******************************************************************************
 *
 *  Copyright 2014 The Android Open Source Project
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
 *  This file contains action functions for SDP search.
 ******************************************************************************/

#include <bluetooth/log.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <hardware/bt_sdp.h>

#include <cstdint>

#include "bta/include/bta_sdp_api.h"
#include "bta/sdp/bta_sdp_int.h"
#include "btif/include/btif_profile_storage.h"
#include "btif/include/btif_sock_sdp.h"
#include "common/init_flags.h"
#include "main/shim/metrics_api.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "stack/include/sdpdefs.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

static void bta_create_mns_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;
  tSDP_PROTOCOL_ELEM pe;
  uint16_t pversion = 0;
  record->mns.hdr.type = SDP_TYPE_MAP_MNS;
  record->mns.hdr.service_name_length = 0;
  record->mns.hdr.service_name = NULL;
  record->mns.hdr.rfcomm_channel_number = 0;
  record->mns.hdr.l2cap_psm = -1;
  record->mns.hdr.profile_version = 0;
  record->mns.supported_features = 0x0000001F;  // default value if not found

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_MAP_SUPPORTED_FEATURES);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 4) {
      record->mns.supported_features = p_attr->attr_value.v.u32;
    } else {
      log::error("ATTR_ID_MAP_SUPPORTED_FEATURES attr type or size wrong!!");
    }
  } else {
    log::error("ATTR_ID_MAP_SUPPORTED_FEATURES attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->mns.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      record->mns.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type not TEXT_STR_DESC_TYPE!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
          p_rec, UUID_SERVCLASS_MAP_PROFILE, &pversion)) {
    record->mns.hdr.profile_version = pversion;
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->mns.hdr.rfcomm_channel_number = pe.params[0];
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_GOEP_L2CAP_PSM);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->mns.hdr.l2cap_psm = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_GOEP_L2CAP_PSM attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_GOEP_L2CAP_PSM attr not found!!");
  }
}

static void bta_create_mas_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;
  tSDP_PROTOCOL_ELEM pe;
  uint16_t pversion = -1;

  record->mas.hdr.type = SDP_TYPE_MAP_MAS;
  record->mas.hdr.service_name_length = 0;
  record->mas.hdr.service_name = NULL;
  record->mas.hdr.rfcomm_channel_number = 0;
  record->mas.hdr.l2cap_psm = -1;
  record->mas.hdr.profile_version = 0;
  record->mas.mas_instance_id = 0;
  record->mas.supported_features = 0x0000001F;
  record->mas.supported_message_types = 0;

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_MAS_INSTANCE_ID);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 1) {
      record->mas.mas_instance_id = p_attr->attr_value.v.u8;
    } else {
      log::error("ATTR_ID_MAS_INSTANCE_ID attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_MAS_INSTANCE_ID attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SUPPORTED_MSG_TYPE);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 1) {
      record->mas.supported_message_types = p_attr->attr_value.v.u8;
    } else {
      log::error("ATTR_ID_SUPPORTED_MSG_TYPE attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_SUPPORTED_MSG_TYPE attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_MAP_SUPPORTED_FEATURES);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 4) {
      record->mas.supported_features = p_attr->attr_value.v.u32;
    } else {
      log::error("ATTR_ID_MAP_SUPPORTED_FEATURES attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_MAP_SUPPORTED_FEATURES attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->mas.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      record->mas.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type wrong!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
          p_rec, UUID_SERVCLASS_MAP_PROFILE, &pversion)) {
    record->mas.hdr.profile_version = pversion;
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->mas.hdr.rfcomm_channel_number = pe.params[0];
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_GOEP_L2CAP_PSM);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->mas.hdr.l2cap_psm = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_GOEP_L2CAP_PSM attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_GOEP_L2CAP_PSM attr not found!!");
  }
}

static void bta_create_pse_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;
  uint16_t pversion;
  tSDP_PROTOCOL_ELEM pe;

  record->pse.hdr.type = SDP_TYPE_PBAP_PSE;
  record->pse.hdr.service_name_length = 0;
  record->pse.hdr.service_name = NULL;
  record->pse.hdr.rfcomm_channel_number = 0;
  record->pse.hdr.l2cap_psm = -1;
  record->pse.hdr.profile_version = 0;
  record->pse.supported_features = 0x00000003;
  record->pse.supported_repositories = 0;

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SUPPORTED_REPOSITORIES);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 1) {
      record->pse.supported_repositories = p_attr->attr_value.v.u8;
    } else {
      log::error("ATTR_ID_SUPPORTED_REPOSITORIES attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_SUPPORTED_REPOSITORIES attr not found!!");
  }
  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_PBAP_SUPPORTED_FEATURES);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 4) {
      record->pse.supported_features = p_attr->attr_value.v.u32;
    } else {
      log::error("ATTR_ID_PBAP_SUPPORTED_FEATURES attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_PBAP_SUPPORTED_FEATURES attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->pse.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      // TODO: validate the lifetime of this value
      record->pse.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type NOT string!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
          p_rec, UUID_SERVCLASS_PHONE_ACCESS, &pversion)) {
    record->pse.hdr.profile_version = pversion;
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->pse.hdr.rfcomm_channel_number = pe.params[0];
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_GOEP_L2CAP_PSM);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->pse.hdr.l2cap_psm = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_GOEP_L2CAP_PSM attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_GOEP_L2CAP_PSM attr not found!!");
  }
}

static void bta_create_ops_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR *p_attr, *p_sattr;
  tSDP_PROTOCOL_ELEM pe;
  uint16_t pversion = -1;

  record->ops.hdr.type = SDP_TYPE_OPP_SERVER;
  record->ops.hdr.service_name_length = 0;
  record->ops.hdr.service_name = NULL;
  record->ops.hdr.rfcomm_channel_number = 0;
  record->ops.hdr.l2cap_psm = -1;
  record->ops.hdr.profile_version = 0;
  record->ops.supported_formats_list_len = 0;

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->ops.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      record->ops.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type NOT string!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
          p_rec, UUID_SERVCLASS_OBEX_OBJECT_PUSH, &pversion)) {
    record->ops.hdr.profile_version = pversion;
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->ops.hdr.rfcomm_channel_number = pe.params[0];
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_GOEP_L2CAP_PSM);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->ops.hdr.l2cap_psm = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_GOEP_L2CAP_PSM attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_GOEP_L2CAP_PSM attr not found!!");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SUPPORTED_FORMATS_LIST);
  if (p_attr != NULL) {
    /* Safety check - each entry should itself be a sequence */
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) != DATA_ELE_SEQ_DESC_TYPE) {
      record->ops.supported_formats_list_len = 0;
      log::error(
          "supported_formats_list - wrong attribute length/type: 0x{:02x} - "
          "expected 0x06",
          p_attr->attr_len_type);
    } else {
      int count = 0;
      /* 1 byte for type/length 1 byte for value */
      record->ops.supported_formats_list_len =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type) / 2;

      /* Extract each value into */
      for (p_sattr = p_attr->attr_value.v.p_sub_attr; p_sattr != NULL;
           p_sattr = p_sattr->p_next_attr) {
        if ((SDP_DISC_ATTR_TYPE(p_sattr->attr_len_type) == UINT_DESC_TYPE) &&
            (SDP_DISC_ATTR_LEN(p_sattr->attr_len_type) >= 1)) {
          if (count == sizeof(record->ops.supported_formats_list)) {
            log::error(
                "supported_formats_list - count overflow - too many sub "
                "attributes!!");
            /* If you hit this, new formats have been added,
             * update SDP_OPP_SUPPORTED_FORMATS_MAX_LENGTH */
            break;
          }
          record->ops.supported_formats_list[count] = p_sattr->attr_value.v.u8;
          count++;
        } else {
          log::error(
              "supported_formats_list - wrong sub attribute length/type: "
              "0x{:02x} - expected 0x80",
              p_sattr->attr_len_type);
          break;
        }
      }
      if (record->ops.supported_formats_list_len != count) {
        log::warn(
            "supported_formats_list - Length of attribute different from the "
            "actual number of sub-attributes in the sequence att-length: {} - "
            "number of elements: {}",
            record->ops.supported_formats_list_len, count);
      }
      record->ops.supported_formats_list_len = count;
    }
  }
}

static void bta_create_sap_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;
  tSDP_PROTOCOL_ELEM pe;
  uint16_t pversion = -1;

  record->sap.hdr.type = SDP_TYPE_MAP_MAS;
  record->sap.hdr.service_name_length = 0;
  record->sap.hdr.service_name = NULL;
  record->sap.hdr.rfcomm_channel_number = 0;
  record->sap.hdr.l2cap_psm = -1;
  record->sap.hdr.profile_version = 0;

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->sap.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      record->sap.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type NOT string!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
          p_rec, UUID_SERVCLASS_SAP, &pversion)) {
    record->sap.hdr.profile_version = pversion;
  }

  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->sap.hdr.rfcomm_channel_number = pe.params[0];
  }
}

static void bta_create_dip_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;

  log::verbose("");

  /* hdr is redundancy in dip */
  record->dip.hdr.type = SDP_TYPE_DIP;
  record->dip.hdr.service_name_length = 0;
  record->dip.hdr.service_name = nullptr;
  record->dip.hdr.rfcomm_channel_number = 0;
  record->dip.hdr.l2cap_psm = -1;
  record->dip.hdr.profile_version = 0;

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SPECIFICATION_ID);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->dip.spec_id = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_SPECIFICATION_ID attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_SPECIFICATION_ID not found");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_VENDOR_ID);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->dip.vendor = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_VENDOR_ID attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_VENDOR_ID not found");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_VENDOR_ID_SOURCE);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->dip.vendor_id_source = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_VENDOR_ID_SOURCE attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_VENDOR_ID_SOURCE not found");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_PRODUCT_ID);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->dip.product = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_PRODUCT_ID attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_PRODUCT_ID not found");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_PRODUCT_VERSION);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == UINT_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 2) {
      record->dip.version = p_attr->attr_value.v.u16;
    } else {
      log::error("ATTR_ID_PRODUCT_VERSION attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_PRODUCT_VERSION not found");
  }

  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_PRIMARY_RECORD);
  if (p_attr != nullptr) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == BOOLEAN_DESC_TYPE &&
        SDP_DISC_ATTR_LEN(p_attr->attr_len_type) >= 1) {
      record->dip.primary_record = !(!p_attr->attr_value.v.u8);
    } else {
      log::error("ATTR_ID_PRIMARY_RECORD attr type or len wrong!!");
    }
  } else {
    log::error("ATTR_ID_PRIMARY_RECORD not found");
  }
}

static void bta_create_raw_sdp_record(bluetooth_sdp_record* record,
                                      tSDP_DISC_REC* p_rec) {
  tSDP_DISC_ATTR* p_attr;
  tSDP_PROTOCOL_ELEM pe;

  record->hdr.type = SDP_TYPE_RAW;
  record->hdr.service_name_length = 0;
  record->hdr.service_name = NULL;
  record->hdr.rfcomm_channel_number = -1;
  record->hdr.l2cap_psm = -1;
  record->hdr.profile_version = -1;

  /* Try to extract a service name */
  p_attr = get_legacy_stack_sdp_api()->record.SDP_FindAttributeInRec(
      p_rec, ATTR_ID_SERVICE_NAME);
  if (p_attr != NULL) {
    if (SDP_DISC_ATTR_TYPE(p_attr->attr_len_type) == TEXT_STR_DESC_TYPE) {
      record->pse.hdr.service_name_length =
          SDP_DISC_ATTR_LEN(p_attr->attr_len_type);
      record->pse.hdr.service_name = (char*)p_attr->attr_value.v.array;
    } else {
      log::error("ATTR_ID_SERVICE_NAME attr type NOT string!!");
    }
  } else {
    log::error("ATTR_ID_SERVICE_NAME attr not found!!");
  }

  /* Try to extract an RFCOMM channel */
  if (get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
          p_rec, UUID_PROTOCOL_RFCOMM, &pe)) {
    record->pse.hdr.rfcomm_channel_number = pe.params[0];
  }
  record->hdr.user1_ptr_len = p_bta_sdp_cfg->p_sdp_db->raw_size;
  record->hdr.user1_ptr = p_bta_sdp_cfg->p_sdp_db->raw_data;
}

/** Callback from btm after search is completed */
static void bta_sdp_search_cback(UNUSED_ATTR const RawAddress& bd_addr,
                                 tSDP_RESULT result, const void* user_data) {
  tBTA_SDP_STATUS status = BTA_SDP_FAILURE;
  int count = 0;
  log::verbose("res: 0x{:x}", result);

  bta_sdp_cb.sdp_active = false;

  if (bta_sdp_cb.p_dm_cback == NULL) return;

  Uuid& uuid = *(reinterpret_cast<Uuid*>(const_cast<void*>(user_data)));

  tBTA_SDP_SEARCH_COMP evt_data;
  memset(&evt_data, 0, sizeof(evt_data));
  evt_data.remote_addr = bta_sdp_cb.remote_addr;
  evt_data.uuid = uuid;

  if (result == SDP_SUCCESS || result == SDP_DB_FULL) {
    tSDP_DISC_REC* p_rec = NULL;
    do {
      p_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceUUIDInDb(
          p_bta_sdp_cfg->p_sdp_db, uuid, p_rec);
      /* generate the matching record data pointer */
      if (!p_rec) {
        log::verbose("UUID not found");
        continue;
      }

      status = BTA_SDP_SUCCESS;
      if (uuid == UUID_MAP_MAS) {
        log::verbose("found MAP (MAS) uuid");
        bta_create_mas_sdp_record(&evt_data.records[count], p_rec);
      } else if (uuid == UUID_MAP_MNS) {
        log::verbose("found MAP (MNS) uuid");
        bta_create_mns_sdp_record(&evt_data.records[count], p_rec);
      } else if (uuid == UUID_PBAP_PSE) {
        log::verbose("found PBAP (PSE) uuid");
        bta_create_pse_sdp_record(&evt_data.records[count], p_rec);
      } else if (uuid == UUID_OBEX_OBJECT_PUSH) {
        log::verbose("found Object Push Server (OPS) uuid");
        bta_create_ops_sdp_record(&evt_data.records[count], p_rec);
      } else if (uuid == UUID_SAP) {
        log::verbose("found SAP uuid");
        bta_create_sap_sdp_record(&evt_data.records[count], p_rec);
      } else if (uuid == UUID_PBAP_PCE) {
        log::verbose("found PBAP (PCE) uuid");
        if (p_rec != NULL) {
          uint16_t peer_pce_version = 0;

          get_legacy_stack_sdp_api()->record.SDP_FindProfileVersionInRec(
              p_rec, UUID_SERVCLASS_PHONE_ACCESS, &peer_pce_version);
          if (peer_pce_version != 0) {
            btif_storage_set_pce_profile_version(p_rec->remote_bd_addr,
                                                 peer_pce_version);
          }
        } else {
          log::verbose("PCE Record is null");
        }
      } else if (uuid == UUID_DIP) {
        log::verbose("found DIP uuid");
        bta_create_dip_sdp_record(&evt_data.records[count], p_rec);
      } else {
        /* we do not have specific structure for this */
        log::verbose("profile not identified. using raw data");
        bta_create_raw_sdp_record(&evt_data.records[count], p_rec);
        p_rec = NULL;  // Terminate loop
        /* For raw, we only extract the first entry, and then return the
           entire raw data chunk.
           TODO: Find a way to split the raw data into record chunks, and
           iterate to extract generic data for each chunk - e.g. rfcomm
           channel and service name. */
      }
      count++;
    } while (p_rec != NULL && count < BTA_SDP_MAX_RECORDS);

    evt_data.record_count = count;
  }
  evt_data.status = status;

  tBTA_SDP bta_sdp;
  bta_sdp.sdp_search_comp = evt_data;
  bta_sdp_cb.p_dm_cback(BTA_SDP_SEARCH_COMP_EVT, &bta_sdp, (void*)&uuid);
  bluetooth::shim::CountCounterMetrics(
      android::bluetooth::CodePathCounterKeyEnum::SDP_SUCCESS, 1);
  osi_free(const_cast<void*>(
      user_data));  // We no longer need the user data to track the search
}

/*******************************************************************************
 *
 * Function     bta_sdp_enable
 *
 * Description  Initializes the SDP I/F
 *
 * Returns      void
 *
 ******************************************************************************/
void bta_sdp_enable(tBTA_SDP_DM_CBACK* p_cback) {
  log::verbose("in, sdp_active:{}", bta_sdp_cb.sdp_active);
  tBTA_SDP_STATUS status = BTA_SDP_SUCCESS;
  bta_sdp_cb.p_dm_cback = p_cback;
  tBTA_SDP bta_sdp;
  bta_sdp.status = status;
  bta_sdp_cb.p_dm_cback(BTA_SDP_ENABLE_EVT, &bta_sdp, NULL);
}

/*******************************************************************************
 *
 * Function     bta_sdp_search
 *
 * Description  Discovers all sdp records for an uuid on remote device
 *
 * Returns      void
 *
 ******************************************************************************/
void bta_sdp_search(const RawAddress bd_addr, const bluetooth::Uuid uuid) {
  tBTA_SDP_STATUS status = BTA_SDP_FAILURE;

  log::verbose("in, sdp_active:{}", bta_sdp_cb.sdp_active);

  if (bta_sdp_cb.sdp_active) {
    /* SDP is still in progress */
    status = BTA_SDP_BUSY;
    if (bta_sdp_cb.p_dm_cback) {
      tBTA_SDP_SEARCH_COMP result;
      memset(&result, 0, sizeof(result));
      result.uuid = uuid;
      result.remote_addr = bd_addr;
      result.status = status;
      tBTA_SDP bta_sdp;
      bta_sdp.sdp_search_comp = result;
      bta_sdp_cb.p_dm_cback(BTA_SDP_SEARCH_COMP_EVT, &bta_sdp, NULL);
    }
    return;
  }

  bta_sdp_cb.sdp_active = true;
  bta_sdp_cb.remote_addr = bd_addr;

  /* initialize the search for the uuid */
  log::verbose("init discovery with UUID: {}", uuid.ToString());
  get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
      p_bta_sdp_cfg->p_sdp_db, p_bta_sdp_cfg->sdp_db_size, 1, &uuid, 0, NULL);

  Uuid* bta_sdp_search_uuid = (Uuid*)osi_malloc(sizeof(Uuid));
  *bta_sdp_search_uuid = uuid;
  if (!get_legacy_stack_sdp_api()->service.SDP_ServiceSearchAttributeRequest2(
          bd_addr, p_bta_sdp_cfg->p_sdp_db, bta_sdp_search_cback,
          (void*)bta_sdp_search_uuid)) {
    bta_sdp_cb.sdp_active = false;

    /* failed to start SDP. report the failure right away */
    if (bta_sdp_cb.p_dm_cback) {
      tBTA_SDP_SEARCH_COMP result;
      memset(&result, 0, sizeof(result));
      result.uuid = uuid;
      result.remote_addr = bd_addr;
      result.status = status;
      tBTA_SDP bta_sdp;
      bta_sdp.sdp_search_comp = result;
      bta_sdp_cb.p_dm_cback(BTA_SDP_SEARCH_COMP_EVT, &bta_sdp, NULL);
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::SDP_FAILURE, 1);
    }
  }
  /*
  else report the result when the cback is called
  */
}

/*******************************************************************************
 *
 * Function     bta_sdp_record
 *
 * Description  Discovers all sdp records for an uuid on remote device
 *
 * Returns      void
 *
 ******************************************************************************/
void bta_sdp_create_record(void* user_data) {
  if (bta_sdp_cb.p_dm_cback)
    bta_sdp_cb.p_dm_cback(BTA_SDP_CREATE_RECORD_USER_EVT, NULL, user_data);
}

/*******************************************************************************
 *
 * Function     bta_sdp_create_record
 *
 * Description  Discovers all sdp records for an uuid on remote device
 *
 * Returns      void
 *
 ******************************************************************************/
void bta_sdp_remove_record(void* user_data) {
  if (bta_sdp_cb.p_dm_cback)
    bta_sdp_cb.p_dm_cback(BTA_SDP_REMOVE_RECORD_USER_EVT, NULL, user_data);
}

namespace bluetooth {
namespace testing {

void bta_create_dip_sdp_record(bluetooth_sdp_record* record,
                               tSDP_DISC_REC* p_rec) {
  ::bta_create_dip_sdp_record(record, p_rec);
}

void bta_sdp_search_cback(const RawAddress& bd_addr, tSDP_RESULT result,
                          const void* user_data) {
  ::bta_sdp_search_cback(bd_addr, result, user_data);
}

}  // namespace testing
}  // namespace bluetooth
