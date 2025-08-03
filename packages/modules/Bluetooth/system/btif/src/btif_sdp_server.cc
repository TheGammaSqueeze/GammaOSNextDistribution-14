/******************************************************************************
 *
 * Copyright 2014 Samsung System LSI
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
 *  Filename:      btif_sdp_server.cc
 *  Description:   SDP server Bluetooth Interface to create and remove SDP
 *                 records.
 *                 To be used in combination with the RFCOMM/L2CAP(LE) sockets.
 *
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_sdp_server"

#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_sdp.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include <mutex>

#include "bta/include/bta_sdp_api.h"
#include "bta/sys/bta_sys.h"
#include "btif_common.h"
#include "btif_sock_sdp.h"
#include "common/init_flags.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "stack/sdp/sdpint.h"
#include "types/bluetooth/uuid.h"
#include "utl.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

// Protects the sdp_slots array from concurrent access.
static std::recursive_mutex sdp_lock;

/**
 * The need for a state variable have been reduced to two states.
 * The remaining state control is handled by program flow
 */
typedef enum {
  SDP_RECORD_FREE = 0,
  SDP_RECORD_ALLOCED,
} sdp_state_t;

typedef struct {
  sdp_state_t state;
  int sdp_handle;
  bluetooth_sdp_record* record_data;
} sdp_slot_t;

namespace fmt {
template <>
struct formatter<sdp_state_t> : enum_formatter<sdp_state_t> {};
}  // namespace fmt

#define MAX_SDP_SLOTS 128
static sdp_slot_t sdp_slots[MAX_SDP_SLOTS];

/*****************************************************************************
 * LOCAL Functions
 *****************************************************************************/
static int add_maps_sdp(const bluetooth_sdp_mas_record* rec);
static int add_mapc_sdp(const bluetooth_sdp_mns_record* rec);
static int add_pbapc_sdp(const bluetooth_sdp_pce_record* rec);
static int add_pbaps_sdp(const bluetooth_sdp_pse_record* rec);
static int add_opps_sdp(const bluetooth_sdp_ops_record* rec);
static int add_saps_sdp(const bluetooth_sdp_sap_record* rec);
static int add_mps_sdp(const bluetooth_sdp_mps_record* rec);
bt_status_t remove_sdp_record(int record_id);
static int free_sdp_slot(int id);

/******************************************************************************
 * WARNING: Functions below are not called in BTU context.
 * Introduced to make it possible to create SDP records from JAVA with both a
 * RFCOMM channel and a L2CAP PSM.
 * Overall architecture:
 *  1) JAVA calls createRecord() which returns a pseudo ID which at a later
 *     point will be linked to a specific SDP handle.
 *  2) createRecord() requests the BTU task(thread) to call a callback in SDP
 *     which creates the actual record, and updates the ID<->SDPHandle map
 *     based on the ID beeing passed to BTA as user_data.
 *****************************************************************************/

static void init_sdp_slots() {
  int i;
  memset(sdp_slots, 0, sizeof(sdp_slot_t) * MAX_SDP_SLOTS);
  /* if SDP_RECORD_FREE is zero - no need to set the value */
  if (SDP_RECORD_FREE != 0) {
    for (i = 0; i < MAX_SDP_SLOTS; i++) {
      sdp_slots[i].state = SDP_RECORD_FREE;
    }
  }
}

bt_status_t sdp_server_init() {
  log::verbose("Sdp Server Init");
  init_sdp_slots();
  return BT_STATUS_SUCCESS;
}

void sdp_server_cleanup() {
  log::verbose("Sdp Server Cleanup");
  std::unique_lock<std::recursive_mutex> lock(sdp_lock);
  int i;
  for (i = 0; i < MAX_SDP_SLOTS; i++) {
    /*remove_sdp_record(i); we cannot send messages to the other threads, since
    * they might
    *                       have been shut down already. Just do local cleanup.
    */
    free_sdp_slot(i);
  }
}

int get_sdp_records_size(bluetooth_sdp_record* in_record, int count) {
  bluetooth_sdp_record* record = in_record;
  int records_size = 0;
  int i;
  for (i = 0; i < count; i++) {
    record = &in_record[i];
    records_size += sizeof(bluetooth_sdp_record);
    records_size += record->hdr.service_name_length;
    if (record->hdr.service_name_length > 0) {
      records_size++; /* + '\0' termination of string */
    }
    records_size += record->hdr.user1_ptr_len;
    records_size += record->hdr.user2_ptr_len;
  }
  return records_size;
}

/* Deep copy all content of in_records into out_records.
 * out_records must point to a chunk of memory large enough to contain all
 * the data. Use getSdpRecordsSize() to calculate the needed size. */
void copy_sdp_records(bluetooth_sdp_record* in_records,
                      bluetooth_sdp_record* out_records, int count) {
  int i;
  bluetooth_sdp_record* in_record;
  bluetooth_sdp_record* out_record;
  char* free_ptr =
      (char*)(&out_records[count]); /* set pointer to after the last entry */

  for (i = 0; i < count; i++) {
    in_record = &in_records[i];
    out_record = &out_records[i];
    *out_record = *in_record;

    if (in_record->hdr.service_name == NULL ||
        in_record->hdr.service_name_length == 0) {
      out_record->hdr.service_name = NULL;
      out_record->hdr.service_name_length = 0;
    } else {
      out_record->hdr.service_name = free_ptr;  // Update service_name pointer
      // Copy string
      memcpy(free_ptr, in_record->hdr.service_name,
             in_record->hdr.service_name_length);
      free_ptr += in_record->hdr.service_name_length;
      *(free_ptr) = '\0';  // Set '\0' termination of string
      free_ptr++;
    }
    if (in_record->hdr.user1_ptr != NULL) {
      out_record->hdr.user1_ptr = (uint8_t*)free_ptr;  // Update pointer
      memcpy(free_ptr, in_record->hdr.user1_ptr,
             in_record->hdr.user1_ptr_len);  // Copy content
      free_ptr += in_record->hdr.user1_ptr_len;
    }
    if (in_record->hdr.user2_ptr != NULL) {
      out_record->hdr.user2_ptr = (uint8_t*)free_ptr;  // Update pointer
      memcpy(free_ptr, in_record->hdr.user2_ptr,
             in_record->hdr.user2_ptr_len);  // Copy content
      free_ptr += in_record->hdr.user2_ptr_len;
    }
  }
  return;
}

/* Reserve a slot in sdp_slots, copy data and set a reference to the copy.
 * The record_data will contain both the record and any data pointed to by
 * the record.
 * Currently this covers:
 *   service_name string,
 *   user1_ptr and
 *   user2_ptr. */
static int alloc_sdp_slot(bluetooth_sdp_record* in_record) {
  int record_size = get_sdp_records_size(in_record, 1);
  /* We are optimists here, and preallocate the record.
   * This is to reduce the time we hold the sdp_lock. */
  bluetooth_sdp_record* record = (bluetooth_sdp_record*)osi_malloc(record_size);

  copy_sdp_records(in_record, record, 1);
  {
    std::unique_lock<std::recursive_mutex> lock(sdp_lock);
    for (int i = 0; i < MAX_SDP_SLOTS; i++) {
      if (sdp_slots[i].state == SDP_RECORD_FREE) {
        sdp_slots[i].state = SDP_RECORD_ALLOCED;
        sdp_slots[i].record_data = record;
        return i;
      }
    }
  }
  log::error("failed - no more free slots!");
  /* Rearly the optimist is too optimistic, and cleanup is needed...*/
  osi_free(record);
  return -1;
}

static int free_sdp_slot(int id) {
  int handle = -1;
  bluetooth_sdp_record* record = NULL;
  if (id < 0 || id >= MAX_SDP_SLOTS) {
    log::error("failed - id {} is invalid", id);
    return handle;
  }

  {
    std::unique_lock<std::recursive_mutex> lock(sdp_lock);
    handle = sdp_slots[id].sdp_handle;
    sdp_slots[id].sdp_handle = 0;
    if (sdp_slots[id].state != SDP_RECORD_FREE) {
      /* safe a copy of the pointer, and free after unlock() */
      record = sdp_slots[id].record_data;
    }
    sdp_slots[id].state = SDP_RECORD_FREE;
  }

  if (record != NULL) {
    osi_free(record);
  } else {
    // Record have already been freed
    handle = -1;
  }
  return handle;
}

/***
 * Use this to get a reference to a SDP slot AND change the state to
 * SDP_RECORD_CREATE_INITIATED.
 */
static const sdp_slot_t* start_create_sdp(int id) {
  if (id >= MAX_SDP_SLOTS) {
    log::error("failed - id {} is invalid", id);
    return NULL;
  }

  std::unique_lock<std::recursive_mutex> lock(sdp_lock);
  if (sdp_slots[id].state != SDP_RECORD_ALLOCED) {
    /* The record have been removed before this event occurred - e.g. deinit */
    log::error(
        "failed - state for id {} is sdp_slots[id].state = {} expected {}", id,
        sdp_slots[id].state, SDP_RECORD_ALLOCED);
    return NULL;
  }

  return &(sdp_slots[id]);
}

static void set_sdp_handle(int id, int handle) {
  std::unique_lock<std::recursive_mutex> lock(sdp_lock);
  sdp_slots[id].sdp_handle = handle;
}

bt_status_t create_sdp_record(bluetooth_sdp_record* record,
                              int* record_handle) {
  int handle;

  handle = alloc_sdp_slot(record);
  log::verbose("handle = 0x{:08x}", handle);

  if (handle < 0) return BT_STATUS_FAIL;

  BTA_SdpCreateRecordByUser(INT_TO_PTR(handle));

  *record_handle = handle;

  return BT_STATUS_SUCCESS;
}

bt_status_t remove_sdp_record(int record_id) {
  int handle;

  if (record_id >= MAX_SDP_SLOTS) {
    return BT_STATUS_PARM_INVALID;
  }

  bluetooth_sdp_record* record;
  bluetooth_sdp_types sdp_type = SDP_TYPE_RAW;
  {
    std::unique_lock<std::recursive_mutex> lock(sdp_lock);
    record = sdp_slots[record_id].record_data;
    if (record != NULL) {
      sdp_type = record->hdr.type;
    }
  }
  tBTA_SERVICE_ID service_id = -1;
  switch (sdp_type) {
    case SDP_TYPE_MAP_MAS:
      service_id = BTA_MAP_SERVICE_ID;
      break;
    case SDP_TYPE_MAP_MNS:
      service_id = BTA_MN_SERVICE_ID;
      break;
    case SDP_TYPE_PBAP_PSE:
      service_id = BTA_PBAP_SERVICE_ID;
      break;
    case SDP_TYPE_PBAP_PCE:
      service_id = BTA_PCE_SERVICE_ID;
      break;
    default:
      /* other enumeration values were not enabled in {@link on_create_record_event} */
      break;
  }
  if (service_id > 0) {
    // {@link btif_disable_service} sets the mask {@link btif_enabled_services}.
    btif_disable_service(service_id);
  }

  /* Get the Record handle, and free the slot */
  handle = free_sdp_slot(record_id);
  log::verbose("Sdp Server id={} to handle=0x{:08x}", record_id, handle);

  /* Pass the actual record handle */
  if (handle > 0) {
    BTA_SdpRemoveRecordByUser(INT_TO_PTR(handle));
    return BT_STATUS_SUCCESS;
  }
  log::verbose("Sdp Server - record already removed - or never created");
  return BT_STATUS_FAIL;
}

/******************************************************************************
 * CALLBACK FUNCTIONS
 * Called in BTA context to create/remove SDP records.
 ******************************************************************************/

void on_create_record_event(int id) {
  /*
   * 1) Fetch the record pointer, and change its state?
   * 2) switch on the type to create the correct record
   * 3) Update state on completion
   * 4) What to do at fail?
   * */
  log::verbose("Sdp Server");
  const sdp_slot_t* sdp_slot = start_create_sdp(id);
  tBTA_SERVICE_ID service_id = -1;
  bluetooth_sdp_record* record;
  /* In the case we are shutting down, sdp_slot is NULL */
  if (sdp_slot != nullptr && (record = sdp_slot->record_data) != nullptr) {
    int handle = -1;
    switch (record->hdr.type) {
      case SDP_TYPE_MAP_MAS:
        handle = add_maps_sdp(&record->mas);
        service_id = BTA_MAP_SERVICE_ID;
        break;
      case SDP_TYPE_MAP_MNS:
        handle = add_mapc_sdp(&record->mns);
        service_id = BTA_MN_SERVICE_ID;
        break;
      case SDP_TYPE_PBAP_PSE:
        handle = add_pbaps_sdp(&record->pse);
        service_id = BTA_PBAP_SERVICE_ID;
        break;
      case SDP_TYPE_OPP_SERVER:
        handle = add_opps_sdp(&record->ops);
        break;
      case SDP_TYPE_SAP_SERVER:
        handle = add_saps_sdp(&record->sap);
        break;
      case SDP_TYPE_PBAP_PCE:
        handle = add_pbapc_sdp(&record->pce);
        service_id = BTA_PCE_SERVICE_ID;
        break;
      case SDP_TYPE_MPS:
        handle = add_mps_sdp(&record->mps);
        break;
      case SDP_TYPE_RAW:
        if (record->hdr.rfcomm_channel_number > 0) {
          handle = add_rfc_sdp_rec(record->hdr.service_name, record->hdr.uuid,
                                   record->hdr.rfcomm_channel_number);
        }
        break;
      default:
        log::verbose("Record type {} is not supported", record->hdr.type);
        break;
    }
    if (handle != -1) {
      set_sdp_handle(id, handle);
      if (service_id > 0) {
        /**
         * {@link btif_enable_service} calls {@link btif_dm_enable_service}, which calls {@link
         * btif_in_execute_service_request}.
         *     - {@link btif_enable_service} sets the mask {@link btif_enabled_services}.
         *     - {@link btif_dm_enable_service} invokes the java callback to return uuids based
         *       on the enabled services mask.
         *     - {@link btif_in_execute_service_request} gates the java callback in {@link
         *       btif_dm_enable_service}.
         */
        btif_enable_service(service_id);
      }
    }
  }
}

void on_remove_record_event(int handle) {
  log::verbose("Sdp Server");

  // User data carries the actual SDP handle, not the ID.
  if (handle != -1 && handle != 0) {
    bool result;
    result = get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(handle);
    if (!result) {
      log::error("Unable to remove handle 0x{:08x}", handle);
    }
  }
}

/****
 * Below the actual functions accessing BTA context data - hence only call from
 * BTA context!
 */

/* Create a MAP MAS SDP record based on information stored in a
 * bluetooth_sdp_mas_record */
static int add_maps_sdp(const bluetooth_sdp_mas_record* rec) {
  tSDP_PROTOCOL_ELEM protoList[3];
  uint16_t service = UUID_SERVCLASS_MESSAGE_ACCESS;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;
  uint8_t temp[4];
  uint8_t* p_temp = temp;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register MAPS Service");
    return sdp_handle;
  }

  /* add service class */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);
  memset(protoList, 0, 3 * sizeof(tSDP_PROTOCOL_ELEM));

  /* add protocol list, including RFCOMM scn */
  protoList[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  protoList[0].num_params = 0;
  protoList[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  protoList[1].num_params = 1;
  protoList[1].params[0] = rec->hdr.rfcomm_channel_number;
  protoList[2].protocol_uuid = UUID_PROTOCOL_OBEX;
  protoList[2].num_params = 0;
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, 3, protoList);

  /* Add a name entry */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_MAP_PROFILE, rec->hdr.profile_version);

  /* Add MAS instance ID */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MAS_INSTANCE_ID, UINT_DESC_TYPE, (uint32_t)1,
      (uint8_t*)&rec->mas_instance_id);

  /* Add supported message types */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SUPPORTED_MSG_TYPE, UINT_DESC_TYPE, (uint32_t)1,
      (uint8_t*)&rec->supported_message_types);

  /* Add supported feature */
  UINT32_TO_BE_STREAM(p_temp, rec->supported_features);
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MAP_SUPPORTED_FEATURES, UINT_DESC_TYPE, (uint32_t)4,
      temp);

  /* Add the L2CAP PSM if present */
  if (rec->hdr.l2cap_psm != -1) {
    p_temp = temp;  // The macro modifies p_temp, hence rewind.
    UINT16_TO_BE_STREAM(p_temp, rec->hdr.l2cap_psm);
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_GOEP_L2CAP_PSM, UINT_DESC_TYPE, (uint32_t)2, temp);
  }

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
  } else {
    bta_sys_add_uuid(service); /* UUID_SERVCLASS_MESSAGE_ACCESS */
    log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  }
  return sdp_handle;
}

/* Create a MAP MNS SDP record based on information stored in a
 * bluetooth_sdp_mns_record */
static int add_mapc_sdp(const bluetooth_sdp_mns_record* rec) {
  tSDP_PROTOCOL_ELEM protoList[3];
  uint16_t service = UUID_SERVCLASS_MESSAGE_NOTIFICATION;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;
  uint8_t temp[4];
  uint8_t* p_temp = temp;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register MAP Notification Service");
    return sdp_handle;
  }

  /* add service class */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);
  memset(protoList, 0, 3 * sizeof(tSDP_PROTOCOL_ELEM));

  /* add protocol list, including RFCOMM scn */
  protoList[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  protoList[0].num_params = 0;
  protoList[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  protoList[1].num_params = 1;
  protoList[1].params[0] = rec->hdr.rfcomm_channel_number;
  protoList[2].protocol_uuid = UUID_PROTOCOL_OBEX;
  protoList[2].num_params = 0;
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, 3, protoList);

  /* Add a name entry */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_MAP_PROFILE, rec->hdr.profile_version);

  /* Add supported feature */
  UINT32_TO_BE_STREAM(p_temp, rec->supported_features);
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MAP_SUPPORTED_FEATURES, UINT_DESC_TYPE, (uint32_t)4,
      temp);

  /* Add the L2CAP PSM if present */
  if (rec->hdr.l2cap_psm != -1) {
    p_temp = temp;  // The macro modifies p_temp, hence rewind.
    UINT16_TO_BE_STREAM(p_temp, rec->hdr.l2cap_psm);
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_GOEP_L2CAP_PSM, UINT_DESC_TYPE, (uint32_t)2, temp);
  }

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
  } else {
    bta_sys_add_uuid(service); /* UUID_SERVCLASS_MESSAGE_ACCESS */
    log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  }
  return sdp_handle;
}

/* Create a PBAP Client SDP record based on information stored in a
 * bluetooth_sdp_pce_record */
static int add_pbapc_sdp(const bluetooth_sdp_pce_record* rec) {
  uint16_t service = UUID_SERVCLASS_PBAP_PCE;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register PBAP Client Service");
    return sdp_handle;
  }

  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);

  /* Add a name entry */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_PHONE_ACCESS, rec->hdr.profile_version);

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
    return sdp_handle;
  }
  bta_sys_add_uuid(service); /* UUID_SERVCLASS_PBAP_PCE */
  log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  return sdp_handle;
}

/* Create a PBAP Server SDP record based on information stored in a
 * bluetooth_sdp_pse_record */
static int add_pbaps_sdp(const bluetooth_sdp_pse_record* rec) {
  tSDP_PROTOCOL_ELEM protoList[3];
  uint16_t service = UUID_SERVCLASS_PBAP_PSE;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;
  uint8_t temp[4];
  uint8_t* p_temp = temp;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register PBAP Server Service");
    return sdp_handle;
  }

  /* add service class */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);
  memset(protoList, 0, 3 * sizeof(tSDP_PROTOCOL_ELEM));

  /* add protocol list, including RFCOMM scn */
  protoList[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  protoList[0].num_params = 0;
  protoList[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  protoList[1].num_params = 1;
  protoList[1].params[0] = rec->hdr.rfcomm_channel_number;
  protoList[2].protocol_uuid = UUID_PROTOCOL_OBEX;
  protoList[2].num_params = 0;
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, 3, protoList);

  /* Add a name entry */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);
  if (bluetooth::common::init_flags::
          pbap_pse_dynamic_version_upgrade_is_enabled()) {
    /*
    PBAP 1.1.1  repositories bits
    Bit 0 = Local Phonebook
    Bit 1 = SIM card
    Bit 2~7 reserved for future use */
    uint8_t supported_repositories_1_1_mask = 0x03;
    uint8_t supported_repositories_1_1 =
        ((uint8_t)rec->supported_repositories) &
        supported_repositories_1_1_mask;
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
        sdp_handle, UUID_SERVCLASS_PHONE_ACCESS, 0x0101);

    /* Add supported repositories 1 byte */
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_SUPPORTED_REPOSITORIES, UINT_DESC_TYPE, (uint32_t)1,
        (uint8_t*)&supported_repositories_1_1);
    log::verbose("supported_repositories_1_1: 0x{:x}",
                 supported_repositories_1_1);
    sdp_save_local_pse_record_attributes(
        rec->hdr.rfcomm_channel_number, rec->hdr.l2cap_psm,
        rec->hdr.profile_version, rec->supported_features,
        rec->supported_repositories);
  } else {
    /* Add in the Bluetooth Profile Descriptor List */
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
        sdp_handle, UUID_SERVCLASS_PHONE_ACCESS, rec->hdr.profile_version);

    /* Add supported repositories 1 byte */
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_SUPPORTED_REPOSITORIES, UINT_DESC_TYPE, (uint32_t)1,
        (uint8_t*)&rec->supported_repositories);
    /* Add supported feature 4 bytes*/
    UINT32_TO_BE_STREAM(p_temp, rec->supported_features);
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_PBAP_SUPPORTED_FEATURES, UINT_DESC_TYPE,
        (uint32_t)4, temp);

    /* Add the L2CAP PSM if present */
    if (rec->hdr.l2cap_psm != -1) {
      p_temp = temp;  // The macro modifies p_temp, hence rewind.
      UINT16_TO_BE_STREAM(p_temp, rec->hdr.l2cap_psm);
      status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
          sdp_handle, ATTR_ID_GOEP_L2CAP_PSM, UINT_DESC_TYPE, (uint32_t)2,
          temp);
    }
#if 0
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_PHONE_ACCESS, rec->hdr.profile_version);

  /* Add supported repositories 1 byte */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SUPPORTED_REPOSITORIES, UINT_DESC_TYPE, (uint32_t)1,
      (uint8_t*)&rec->supported_repositories);

  /* Add supported feature 4 bytes*/
  UINT32_TO_BE_STREAM(p_temp, rec->supported_features);
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_PBAP_SUPPORTED_FEATURES, UINT_DESC_TYPE, (uint32_t)4,
      temp);

  /* Add the L2CAP PSM if present */
  if (rec->hdr.l2cap_psm != -1) {
    p_temp = temp;  // The macro modifies p_temp, hence rewind.
    UINT16_TO_BE_STREAM(p_temp, rec->hdr.l2cap_psm);
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_GOEP_L2CAP_PSM, UINT_DESC_TYPE, (uint32_t)2, temp);
#endif
  }

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
  } else {
    bta_sys_add_uuid(service); /* UUID_SERVCLASS_MESSAGE_ACCESS */
    log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  }
  return sdp_handle;
}

/* Create a OPP Server SDP record based on information stored in a
 * bluetooth_sdp_ops_record */
static int add_opps_sdp(const bluetooth_sdp_ops_record* rec) {
  tSDP_PROTOCOL_ELEM protoList[3];
  uint16_t service = UUID_SERVCLASS_OBEX_OBJECT_PUSH;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  uint8_t type_len[rec->supported_formats_list_len];
  uint8_t desc_type[rec->supported_formats_list_len];
  uint8_t* type_value[rec->supported_formats_list_len];
  bool status = true;
  uint32_t sdp_handle = 0;
  uint8_t temp[4];
  uint8_t* p_temp = temp;
  tBTA_UTL_COD cod;
  int i, j;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register Object Push Server Service");
    return sdp_handle;
  }

  /* add service class */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);
  memset(protoList, 0, 3 * sizeof(tSDP_PROTOCOL_ELEM));

  /* add protocol list, including RFCOMM scn */
  protoList[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  protoList[0].num_params = 0;
  protoList[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  protoList[1].num_params = 1;
  protoList[1].params[0] = rec->hdr.rfcomm_channel_number;
  protoList[2].protocol_uuid = UUID_PROTOCOL_OBEX;
  protoList[2].num_params = 0;
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, 3, protoList);

  /* Add a name entry */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_OBEX_OBJECT_PUSH, rec->hdr.profile_version);

  /* add sequence for supported types */
  for (i = 0, j = 0; i < rec->supported_formats_list_len; i++) {
    type_value[j] = (uint8_t*)&rec->supported_formats_list[i];
    desc_type[j] = UINT_DESC_TYPE;
    type_len[j++] = 1;
  }

  status &= get_legacy_stack_sdp_api()->handle.SDP_AddSequence(
      sdp_handle, (uint16_t)ATTR_ID_SUPPORTED_FORMATS_LIST,
      (uint8_t)rec->supported_formats_list_len, desc_type, type_len,
      type_value);

  /* Add the L2CAP PSM if present */
  if (rec->hdr.l2cap_psm != -1) {
    p_temp = temp;  // The macro modifies p_temp, hence rewind.
    UINT16_TO_BE_STREAM(p_temp, rec->hdr.l2cap_psm);
    status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_GOEP_L2CAP_PSM, UINT_DESC_TYPE, (uint32_t)2, temp);
  }

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
  } else {
    /* set class of device */
    cod.service = BTM_COD_SERVICE_OBJ_TRANSFER;
    utl_set_device_class(&cod, BTA_UTL_SET_COD_SERVICE_CLASS);

    bta_sys_add_uuid(service); /* UUID_SERVCLASS_OBEX_OBJECT_PUSH */
    log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  }
  return sdp_handle;
}

// Create a Sim Access Profile SDP record based on information stored in a
// bluetooth_sdp_sap_record.
static int add_saps_sdp(const bluetooth_sdp_sap_record* rec) {
  tSDP_PROTOCOL_ELEM protoList[2];
  uint16_t services[2];
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register SAPS Service");
    return sdp_handle;
  }

  services[0] = UUID_SERVCLASS_SAP;
  services[1] = UUID_SERVCLASS_GENERIC_TELEPHONY;

  // add service class
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 2, services);
  memset(protoList, 0, 2 * sizeof(tSDP_PROTOCOL_ELEM));

  // add protocol list, including RFCOMM scn
  protoList[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  protoList[0].num_params = 0;
  protoList[1].protocol_uuid = UUID_PROTOCOL_RFCOMM;
  protoList[1].num_params = 1;
  protoList[1].params[0] = rec->hdr.rfcomm_channel_number;
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, 2, protoList);

  // Add a name entry
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, (uint16_t)ATTR_ID_SERVICE_NAME, (uint8_t)TEXT_STR_DESC_TYPE,
      (uint32_t)(rec->hdr.service_name_length + 1),
      (uint8_t*)rec->hdr.service_name);

  // Add in the Bluetooth Profile Descriptor List
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_SAP, rec->hdr.profile_version);

  // Make the service browseable
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED deleting record");
  } else {
    bta_sys_add_uuid(UUID_SERVCLASS_SAP);
    log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  }
  return sdp_handle;
}

/* Create a Multi-Profile Specification SDP record based on information stored
 * in a bluetooth_sdp_mps_record */
static int add_mps_sdp(const bluetooth_sdp_mps_record* rec) {
  uint16_t service = UUID_SERVCLASS_MPS_SC;
  uint16_t browse = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  bool status = true;
  uint32_t sdp_handle = 0;

  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();
  if (sdp_handle == 0) {
    log::error("Unable to register MPS record");
    return sdp_handle;
  }

  status &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, 1, &service);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_MPS_PROFILE, rec->hdr.profile_version);

  /* Add supported scenarios MPSD */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MPS_SUPPORTED_SCENARIOS_MPSD, UINT_DESC_TYPE,
      (uint32_t)8, (uint8_t*)&rec->supported_scenarios_mpsd);
  /* Add supported scenarios MPMD */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MPS_SUPPORTED_SCENARIOS_MPMD, UINT_DESC_TYPE,
      (uint32_t)8, (uint8_t*)&rec->supported_scenarios_mpmd);
  /* Add supported dependencies */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_MPS_SUPPORTED_DEPENDENCIES, UINT_DESC_TYPE,
      (uint32_t)2, (uint8_t*)&rec->supported_dependencies);

  /* Make the service browseable */
  status &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse);

  if (!status) {
    get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
    sdp_handle = 0;
    log::error("FAILED");
    return sdp_handle;
  }
  bta_sys_add_uuid(service); /* UUID_SERVCLASS_MPS_SC */
  log::verbose("SDP Registered (handle 0x{:08x})", sdp_handle);
  return sdp_handle;
}
