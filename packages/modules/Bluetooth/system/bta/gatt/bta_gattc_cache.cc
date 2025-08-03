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
 *  This file contains the GATT client discovery procedures and cache
 *  related functions.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_gattc"

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <cstdio>
#include <vector>

#include "bta/gatt/bta_gattc_int.h"
#include "bta/gatt/database.h"
#include "common/init_flags.h"
#include "device/include/interop.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/btm/btm_sec.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/gatt_api.h"
#include "stack/include/sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth;

using base::StringPrintf;
using bluetooth::Uuid;
using gatt::Characteristic;
using gatt::Database;
using gatt::DatabaseBuilder;
using gatt::Descriptor;
using gatt::IncludedService;
using gatt::Service;

static tGATT_STATUS bta_gattc_sdp_service_disc(uint16_t conn_id,
                                               tBTA_GATTC_SERV* p_server_cb);
const Descriptor* bta_gattc_get_descriptor_srcb(tBTA_GATTC_SERV* p_srcb,
                                                uint16_t handle);
const Characteristic* bta_gattc_get_characteristic_srcb(tBTA_GATTC_SERV* p_srcb,
                                                        uint16_t handle);
static void bta_gattc_explore_srvc_finished(uint16_t conn_id,
                                            tBTA_GATTC_SERV* p_srvc_cb);

static void bta_gattc_read_db_hash_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                        const tBTA_GATTC_OP_CMPL* p_data,
                                        bool is_svc_chg);

static void bta_gattc_read_ext_prop_desc_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                              const tBTA_GATTC_OP_CMPL* p_data);

// define the max retry count for DATABASE_OUT_OF_SYNC
#define BTA_GATTC_DISCOVER_RETRY_COUNT 2

#define BTA_GATT_SDP_DB_SIZE 4096

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/

typedef struct {
  tSDP_DISCOVERY_DB* p_sdp_db;
  uint16_t sdp_conn_id;
} tBTA_GATTC_CB_DATA;

#if (BTA_GATT_DEBUG == TRUE)
/* utility functions */

/* debug function to display the server cache */
static void bta_gattc_display_cache_server(const Database& database) {
  log::info("<=--------------=Start Server Cache =-----------=>");
  std::istringstream iss(database.ToString());
  for (std::string line; std::getline(iss, line);) {
    log::info("{}", line);
  }
  log::info("<=--------------=End Server Cache =-----------=>");
}

/** debug function to display the exploration list */
static void bta_gattc_display_explore_record(const DatabaseBuilder& database) {
  log::info("<=--------------=Start Explore Queue =-----------=>");
  std::istringstream iss(database.ToString());
  for (std::string line; std::getline(iss, line);) {
    log::info("{}", line);
  }
  log::info("<=--------------= End Explore Queue =-----------=>");
}
#endif /* BTA_GATT_DEBUG == TRUE */

/** Initialize the database cache and discovery related resources */
void bta_gattc_init_cache(tBTA_GATTC_SERV* p_srvc_cb) {
  p_srvc_cb->gatt_database = gatt::Database();
  p_srvc_cb->pending_discovery.Clear();
}

const Service* bta_gattc_find_matching_service(
    const std::list<Service>& services, uint16_t handle) {
  for (const Service& service : services) {
    if (handle >= service.handle && handle <= service.end_handle)
      return &service;
  }

  return nullptr;
}

/// Whether the peer device uses robust caching
RobustCachingSupport GetRobustCachingSupport(const tBTA_GATTC_CLCB* p_clcb,
                                             const gatt::Database& db) {
  log::debug("GetRobustCachingSupport {}",
             p_clcb->bda.ToRedactedStringForLogging());

  // If the feature is disabled, then we never support it
  if (!bta_gattc_is_robust_caching_enabled()) {
    log::debug("robust caching is disabled, so UNSUPPORTED");
    return RobustCachingSupport::UNSUPPORTED;
  }

  // An empty database means that discovery hasn't taken place yet, so
  // we can't infer anything from that
  if (!db.IsEmpty()) {
    // Here, we can simply check whether the database hash is present
    for (const auto& service : db.Services()) {
      if (service.uuid.As16Bit() != UUID_SERVCLASS_GATT_SERVER) {
        continue;
      }
      for (const auto& characteristic : service.characteristics) {
        if (characteristic.uuid.As16Bit() == GATT_UUID_DATABASE_HASH) {
          // the hash was found, so we should read it
          log::debug("database hash characteristic found, so SUPPORTED");
          return RobustCachingSupport::SUPPORTED;
        }
      }
    }

    // The database hash characteristic was not found, so there's no point
    // searching for it. Even if the hash was previously not present but is now,
    // we will still get the service changed indication, so there's no need to
    // speculatively check for the hash every time.
    log::debug("database hash characteristic not found, so UNSUPPORTED");
    return RobustCachingSupport::UNSUPPORTED;
  }

  if (p_clcb->transport == BT_TRANSPORT_LE &&
      !BTM_IsRemoteVersionReceived(p_clcb->bda)) {
    log::info("version info is not ready yet");
    return RobustCachingSupport::W4_REMOTE_VERSION;
  }

  // This is workaround for the embedded devices being already on the market
  // and having a serious problem with handle Read By Type with
  // GATT_UUID_DATABASE_HASH. With this workaround, Android will assume that
  // embedded device having LMP version lower than 5.1 (0x0a), it does not
  // support GATT Caching.
  uint8_t lmp_version = 0;
  if (!BTM_ReadRemoteVersion(p_clcb->bda, &lmp_version, nullptr, nullptr)) {
    log::warn("Could not read remote version for {}",
              ADDRESS_TO_LOGGABLE_CSTR(p_clcb->bda));
  }

  if (lmp_version < 0x0a) {
    log::warn(
        "Device LMP version 0x{:02x} < Bluetooth 5.1. Ignore database cache "
        "read.",
        lmp_version);
    return RobustCachingSupport::UNSUPPORTED;
  }

  // Some LMP 5.2 devices also don't support robust caching. This workaround
  // conditionally disables the feature based on a combination of LMP
  // version and OUI prefix.
  if (lmp_version < 0x0c &&
      interop_match_addr(INTEROP_DISABLE_ROBUST_CACHING, &p_clcb->bda)) {
    log::warn(
        "Device LMP version 0x{:02x} <= Bluetooth 5.2 and MAC addr on interop "
        "list, skipping robust caching",
        lmp_version);
    return RobustCachingSupport::UNSUPPORTED;
  }

  // If we have no cached database and no interop considerations,
  // it is unknown whether or not robust caching is supported
  log::debug("database hash support is UNKNOWN");
  return RobustCachingSupport::UNKNOWN;
}

/** Start primary service discovery */
tGATT_STATUS bta_gattc_discover_pri_service(uint16_t conn_id,
                                            tBTA_GATTC_SERV* p_server_cb,
                                            tGATT_DISC_TYPE disc_type) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) return GATT_ERROR;

  if (p_clcb->transport == BT_TRANSPORT_LE) {
    return GATTC_Discover(conn_id, disc_type, 0x0001, 0xFFFF);
  }

  // only for Classic transport
  return bta_gattc_sdp_service_disc(conn_id, p_server_cb);
}

/** start exploring next service, or finish discovery if no more services left
 */
static void bta_gattc_explore_next_service(uint16_t conn_id,
                                           tBTA_GATTC_SERV* p_srvc_cb) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) {
    log::error("unknown conn_id={}", loghex(conn_id));
    return;
  }

  if (p_srvc_cb->pending_discovery.StartNextServiceExploration()) {
    const auto& service =
        p_srvc_cb->pending_discovery.CurrentlyExploredService();
    log::verbose("Start service discovery");

    /* start discovering included services */
    GATTC_Discover(conn_id, GATT_DISC_INC_SRVC, service.first, service.second);
    return;
  }
  // No more services to discover

  // As part of service discovery, read the values of "Characteristic Extended
  // Properties" descriptor
  const auto& descriptors =
      p_srvc_cb->pending_discovery.DescriptorHandlesToRead();
  if (!descriptors.empty()) {
    // set request field to READ_EXT_PROP_DESC
    p_clcb->request_during_discovery =
        BTA_GATTC_DISCOVER_REQ_READ_EXT_PROP_DESC;

    if (p_srvc_cb->read_multiple_not_supported || descriptors.size() == 1) {
      tGATT_READ_PARAM read_param{.by_handle = {.auth_req = GATT_AUTH_REQ_NONE,
                                                .handle = descriptors.front()}};
      GATTC_Read(conn_id, GATT_READ_BY_HANDLE, &read_param);
      // asynchronous continuation in bta_gattc_op_cmpl_during_discovery
      return;
    }

    // TODO(jpawlowski): as a limit we should use MTU/2 rather than
    // GATT_MAX_READ_MULTI_HANDLES
    /* each descriptor contains just 2 bytes, so response size is same as
     * request size */
    size_t num_handles =
        std::min(descriptors.size(), (size_t)GATT_MAX_READ_MULTI_HANDLES);

    tGATT_READ_PARAM read_param;
    memset(&read_param, 0, sizeof(tGATT_READ_PARAM));

    read_param.read_multiple.num_handles = num_handles;
    read_param.read_multiple.auth_req = GATT_AUTH_REQ_NONE;
    memcpy(&read_param.read_multiple.handles, descriptors.data(),
           sizeof(uint16_t) * num_handles);
    GATTC_Read(conn_id, GATT_READ_MULTIPLE, &read_param);

    // asynchronous continuation in bta_gattc_op_cmpl_during_discovery
    return;
  }

  bta_gattc_explore_srvc_finished(conn_id, p_srvc_cb);
}

static void bta_gattc_explore_srvc_finished(uint16_t conn_id,
                                            tBTA_GATTC_SERV* p_srvc_cb) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) {
    log::error("unknown conn_id={}", loghex(conn_id));
    return;
  }

  /* no service found at all, the end of server discovery*/
  log::info("service discovery finished");

  p_srvc_cb->gatt_database = p_srvc_cb->pending_discovery.Build();

#if (BTA_GATT_DEBUG == TRUE)
  bta_gattc_display_cache_server(p_srvc_cb->gatt_database);
#endif
  /* save cache to NV */
  p_clcb->p_srcb->state = BTA_GATTC_SERV_SAVE;

  // If robust caching is not enabled, use original design
  if (!bta_gattc_is_robust_caching_enabled()) {
    if (btm_sec_is_a_bonded_dev(p_srvc_cb->server_bda)) {
      bta_gattc_cache_write(p_clcb->p_srcb->server_bda,
                            p_clcb->p_srcb->gatt_database);
    }
  } else {
    // If robust caching is enabled, do something optimized
    Octet16 hash = p_clcb->p_srcb->gatt_database.Hash();
    bool success = bta_gattc_hash_write(hash, p_clcb->p_srcb->gatt_database);

    // If the device is trusted, link the addr file to hash file
    if (success && btm_sec_is_a_bonded_dev(p_srvc_cb->server_bda)) {
      log::debug("Linking db hash to address {}",
                 p_clcb->p_srcb->server_bda.ToRedactedStringForLogging());
      bta_gattc_cache_link(p_clcb->p_srcb->server_bda, hash);
    }

    // After success, reset the count.
    log::debug(
        "service discovery succeed, reset count to zero, conn_id=0x{:04x}",
        conn_id);
    p_srvc_cb->srvc_disc_count = 0;
  }

  bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_SUCCESS);
}

/** Start discovery for characteristic descriptor */
void bta_gattc_start_disc_char_dscp(uint16_t conn_id,
                                    tBTA_GATTC_SERV* p_srvc_cb) {
  log::verbose("starting discover characteristics descriptor");

  std::pair<uint16_t, uint16_t> range =
      p_srvc_cb->pending_discovery.NextDescriptorRangeToExplore();
  if (range == DatabaseBuilder::EXPLORE_END) {
    goto descriptor_discovery_done;
  }

  if (GATTC_Discover(conn_id, GATT_DISC_CHAR_DSCPT, range.first,
                     range.second) != 0) {
    goto descriptor_discovery_done;
  }
  return;

descriptor_discovery_done:
  /* all characteristic has been explored, start with next service if any */
  bta_gattc_explore_next_service(conn_id, p_srvc_cb);
  return;
}

/* Process the discovery result from sdp */
void bta_gattc_sdp_callback(UNUSED_ATTR const RawAddress& bd_addr,
                            tSDP_STATUS sdp_status, const void* user_data) {
  tBTA_GATTC_CB_DATA* cb_data = (tBTA_GATTC_CB_DATA*)user_data;
  tBTA_GATTC_SERV* p_srvc_cb = bta_gattc_find_scb_by_cid(cb_data->sdp_conn_id);

  if (p_srvc_cb == nullptr) {
    log::error("GATT service discovery is done on unknown connection");
    /* allocated in bta_gattc_sdp_service_disc */
    osi_free(cb_data);
    return;
  }

  if ((sdp_status != SDP_SUCCESS) && (sdp_status != SDP_DB_FULL)) {
    bta_gattc_explore_srvc_finished(cb_data->sdp_conn_id, p_srvc_cb);

    /* allocated in bta_gattc_sdp_service_disc */
    osi_free(cb_data);
    return;
  }

  bool no_pending_disc = !p_srvc_cb->pending_discovery.InProgress();

  tSDP_DISC_REC* p_sdp_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(
      cb_data->p_sdp_db, 0, nullptr);
  while (p_sdp_rec != nullptr) {
    /* find a service record, report it */
    Uuid service_uuid;
    if (!get_legacy_stack_sdp_api()->record.SDP_FindServiceUUIDInRec(
            p_sdp_rec, &service_uuid))
      continue;

    tSDP_PROTOCOL_ELEM pe;
    if (!get_legacy_stack_sdp_api()->record.SDP_FindProtocolListElemInRec(
            p_sdp_rec, UUID_PROTOCOL_ATT, &pe))
      continue;

    uint16_t start_handle = (uint16_t)pe.params[0];
    uint16_t end_handle = (uint16_t)pe.params[1];

#if (BTA_GATT_DEBUG == TRUE)
    log::verbose("Found ATT service uuid={}, s_handle={}, e_handle={}",
                 service_uuid, loghex(start_handle), loghex(end_handle));
#endif

    if (!GATT_HANDLE_IS_VALID(start_handle) ||
        !GATT_HANDLE_IS_VALID(end_handle)) {
      log::error("invalid start_handle={}, end_handle={}", loghex(start_handle),
                 loghex(end_handle));
      p_sdp_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(
          cb_data->p_sdp_db, 0, p_sdp_rec);
      continue;
    }

    /* discover services result, add services into a service list */
    p_srvc_cb->pending_discovery.AddService(start_handle, end_handle,
                                            service_uuid, true);

    p_sdp_rec = get_legacy_stack_sdp_api()->db.SDP_FindServiceInDb(
        cb_data->p_sdp_db, 0, p_sdp_rec);
  }

  // If discovery is already pending, no need to call
  // bta_gattc_explore_next_service. Next service will be picked up to discovery
  // once current one is discovered. If discovery is not pending, start one
  if (no_pending_disc) {
    bta_gattc_explore_next_service(cb_data->sdp_conn_id, p_srvc_cb);
  }

  /* allocated in bta_gattc_sdp_service_disc */
  osi_free(cb_data);
}

/* Start DSP Service Discovery */
static tGATT_STATUS bta_gattc_sdp_service_disc(uint16_t conn_id,
                                               tBTA_GATTC_SERV* p_server_cb) {
  uint16_t num_attrs = 2;
  uint16_t attr_list[2];

  /*
   * On success, cb_data will be freed inside bta_gattc_sdp_callback,
   * otherwise it will be freed within this function.
   */
  tBTA_GATTC_CB_DATA* cb_data = (tBTA_GATTC_CB_DATA*)osi_malloc(
      sizeof(tBTA_GATTC_CB_DATA) + BTA_GATT_SDP_DB_SIZE);

  cb_data->p_sdp_db = (tSDP_DISCOVERY_DB*)(cb_data + 1);
  attr_list[0] = ATTR_ID_SERVICE_CLASS_ID_LIST;
  attr_list[1] = ATTR_ID_PROTOCOL_DESC_LIST;

  Uuid uuid = Uuid::From16Bit(UUID_PROTOCOL_ATT);
  get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
      cb_data->p_sdp_db, BTA_GATT_SDP_DB_SIZE, 1, &uuid, num_attrs, attr_list);

  if (!get_legacy_stack_sdp_api()->service.SDP_ServiceSearchAttributeRequest2(
          p_server_cb->server_bda, cb_data->p_sdp_db, &bta_gattc_sdp_callback,
          const_cast<const void*>(static_cast<void*>(cb_data)))) {
    osi_free(cb_data);
    return GATT_ERROR;
  }

  cb_data->sdp_conn_id = conn_id;
  return GATT_SUCCESS;
}

/** operation completed */
void bta_gattc_op_cmpl_during_discovery(tBTA_GATTC_CLCB* p_clcb,
                                        const tBTA_GATTC_DATA* p_data) {
  // Currently, there are two cases needed to be handled.
  // 1. Read ext prop descriptor value after service discovery
  // 2. Read db hash before starting service discovery
  switch (p_clcb->request_during_discovery) {
    case BTA_GATTC_DISCOVER_REQ_READ_EXT_PROP_DESC:
      bta_gattc_read_ext_prop_desc_cmpl(p_clcb, &p_data->op_cmpl);
      break;
    case BTA_GATTC_DISCOVER_REQ_READ_DB_HASH:
    case BTA_GATTC_DISCOVER_REQ_READ_DB_HASH_FOR_SVC_CHG:
      if (bta_gattc_is_robust_caching_enabled()) {
        bool is_svc_chg = (p_clcb->request_during_discovery ==
                           BTA_GATTC_DISCOVER_REQ_READ_DB_HASH_FOR_SVC_CHG);
        bta_gattc_read_db_hash_cmpl(p_clcb, &p_data->op_cmpl, is_svc_chg);
      } else {
        // it is not possible here if flag is off, but just in case
        p_clcb->request_during_discovery = BTA_GATTC_DISCOVER_REQ_NONE;
      }
      break;
    case BTA_GATTC_DISCOVER_REQ_NONE:
    default:
      break;
  }
}

/** callback function to GATT client stack */
void bta_gattc_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                              tGATT_DISC_RES* p_data) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  tBTA_GATTC_SERV* p_srvc_cb = bta_gattc_find_scb_by_cid(conn_id);

  if (!p_srvc_cb || !p_clcb || p_clcb->state != BTA_GATTC_DISCOVER_ST) return;

  switch (disc_type) {
    case GATT_DISC_SRVC_ALL:
    case GATT_DISC_SRVC_BY_UUID:
      p_srvc_cb->pending_discovery.AddService(
          p_data->handle, p_data->value.group_value.e_handle,
          p_data->value.group_value.service_type, true);
      break;

    case GATT_DISC_INC_SRVC:
      p_srvc_cb->pending_discovery.AddIncludedService(
          p_data->handle, p_data->value.incl_service.service_type,
          p_data->value.incl_service.s_handle,
          p_data->value.incl_service.e_handle);
      break;

    case GATT_DISC_CHAR:
      p_srvc_cb->pending_discovery.AddCharacteristic(
          p_data->handle, p_data->value.dclr_value.val_handle,
          p_data->value.dclr_value.char_uuid,
          p_data->value.dclr_value.char_prop);
      break;

    case GATT_DISC_CHAR_DSCPT:
      p_srvc_cb->pending_discovery.AddDescriptor(p_data->handle, p_data->type);
      break;

    case GATT_DISC_MAX:
    default:
      log::error("Received illegal discovery item");
      break;
  }
}

void bta_gattc_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                               tGATT_STATUS status) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  tBTA_GATTC_SERV* p_srvc_cb = bta_gattc_find_scb_by_cid(conn_id);

  if (p_clcb && (status != GATT_SUCCESS || p_clcb->status != GATT_SUCCESS)) {
    if (status == GATT_SUCCESS) p_clcb->status = status;

    // if db out of sync is received, try to start service discovery if possible
    if (bta_gattc_is_robust_caching_enabled() &&
        status == GATT_DATABASE_OUT_OF_SYNC) {
      if (p_srvc_cb &&
          p_srvc_cb->srvc_disc_count < BTA_GATTC_DISCOVER_RETRY_COUNT) {
        p_srvc_cb->srvc_disc_count++;
        p_clcb->auto_update = BTA_GATTC_DISC_WAITING;
      } else {
        log::error("retry limit exceeds for db out of sync, conn_id={}",
                   conn_id);
      }
    }

    bta_gattc_sm_execute(p_clcb, BTA_GATTC_DISCOVER_CMPL_EVT, NULL);
    return;
  }

  if (!p_srvc_cb) return;

  switch (disc_type) {
    case GATT_DISC_SRVC_ALL:
    case GATT_DISC_SRVC_BY_UUID:
// definition of all services are discovered, now it's time to discover
// their content
#if (BTA_GATT_DEBUG == TRUE)
      bta_gattc_display_explore_record(p_srvc_cb->pending_discovery);
#endif
      bta_gattc_explore_next_service(conn_id, p_srvc_cb);
      break;

    case GATT_DISC_INC_SRVC: {
      auto& service = p_srvc_cb->pending_discovery.CurrentlyExploredService();
      /* start discovering characteristic */
      GATTC_Discover(conn_id, GATT_DISC_CHAR, service.first, service.second);
      break;
    }

    case GATT_DISC_CHAR: {
#if (BTA_GATT_DEBUG == TRUE)
      bta_gattc_display_explore_record(p_srvc_cb->pending_discovery);
#endif
      bta_gattc_start_disc_char_dscp(conn_id, p_srvc_cb);
      break;
    }

    case GATT_DISC_CHAR_DSCPT:
      /* start discovering next characteristic for char descriptor */
      bta_gattc_start_disc_char_dscp(conn_id, p_srvc_cb);
      break;

    case GATT_DISC_MAX:
    default:
      log::error("Received illegal discovery item");
      break;
  }
}

/** search local cache for matching service record */
void bta_gattc_search_service(tBTA_GATTC_CLCB* p_clcb, Uuid* p_uuid) {
  for (const Service& service : p_clcb->p_srcb->gatt_database.Services()) {
    if (p_uuid && *p_uuid != service.uuid) continue;

#if (BTA_GATT_DEBUG == TRUE)
    log::verbose("found service {} handle:{}", service.uuid, service.handle);
#endif
    if (!p_clcb->p_rcb->p_cback) continue;

    tBTA_GATTC cb_data;
    memset(&cb_data, 0, sizeof(tBTA_GATTC));
    cb_data.srvc_res.conn_id = p_clcb->bta_conn_id;
    cb_data.srvc_res.service_uuid.inst_id = service.handle;
    cb_data.srvc_res.service_uuid.uuid = service.uuid;

    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_SEARCH_RES_EVT, &cb_data);
  }
}

const std::list<Service>* bta_gattc_get_services_srcb(tBTA_GATTC_SERV* p_srcb) {
  if (!p_srcb || p_srcb->gatt_database.IsEmpty()) return NULL;

  return &p_srcb->gatt_database.Services();
}

const std::list<Service>* bta_gattc_get_services(uint16_t conn_id) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return NULL;

  tBTA_GATTC_SERV* p_srcb = p_clcb->p_srcb;

  return bta_gattc_get_services_srcb(p_srcb);
}

const Service* bta_gattc_get_service_for_handle_srcb(tBTA_GATTC_SERV* p_srcb,
                                                     uint16_t handle) {
  const std::list<Service>* services = bta_gattc_get_services_srcb(p_srcb);
  if (services == NULL) return NULL;
  return bta_gattc_find_matching_service(*services, handle);
}

const Service* bta_gattc_get_service_for_handle(uint16_t conn_id,
                                                uint16_t handle) {
  const std::list<Service>* services = bta_gattc_get_services(conn_id);
  if (services == NULL) return NULL;

  return bta_gattc_find_matching_service(*services, handle);
}

const Characteristic* bta_gattc_get_characteristic_srcb(tBTA_GATTC_SERV* p_srcb,
                                                        uint16_t handle) {
  const Service* service =
      bta_gattc_get_service_for_handle_srcb(p_srcb, handle);

  if (!service) return NULL;

  for (const Characteristic& charac : service->characteristics) {
    if (handle == charac.value_handle) return &charac;
  }

  return NULL;
}

const Characteristic* bta_gattc_get_characteristic(uint16_t conn_id,
                                                   uint16_t handle) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return NULL;

  tBTA_GATTC_SERV* p_srcb = p_clcb->p_srcb;
  return bta_gattc_get_characteristic_srcb(p_srcb, handle);
}

const Descriptor* bta_gattc_get_descriptor_srcb(tBTA_GATTC_SERV* p_srcb,
                                                uint16_t handle) {
  const Service* service =
      bta_gattc_get_service_for_handle_srcb(p_srcb, handle);

  if (!service) {
    return NULL;
  }

  for (const Characteristic& charac : service->characteristics) {
    for (const Descriptor& desc : charac.descriptors) {
      if (handle == desc.handle) return &desc;
    }
  }

  return NULL;
}

const Descriptor* bta_gattc_get_descriptor(uint16_t conn_id, uint16_t handle) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return NULL;

  tBTA_GATTC_SERV* p_srcb = p_clcb->p_srcb;
  return bta_gattc_get_descriptor_srcb(p_srcb, handle);
}

const Characteristic* bta_gattc_get_owning_characteristic_srcb(
    tBTA_GATTC_SERV* p_srcb, uint16_t handle) {
  const Service* service =
      bta_gattc_get_service_for_handle_srcb(p_srcb, handle);

  if (!service) return NULL;

  for (const Characteristic& charac : service->characteristics) {
    for (const Descriptor& desc : charac.descriptors) {
      if (handle == desc.handle) return &charac;
    }
  }

  return NULL;
}

const Characteristic* bta_gattc_get_owning_characteristic(uint16_t conn_id,
                                                          uint16_t handle) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) return NULL;

  return bta_gattc_get_owning_characteristic_srcb(p_clcb->p_srcb, handle);
}

/* request reading database hash */
bool bta_gattc_read_db_hash(tBTA_GATTC_CLCB* p_clcb, bool is_svc_chg) {
  tGATT_READ_PARAM read_param;
  memset(&read_param, 0, sizeof(tGATT_READ_BY_TYPE));

  read_param.char_type.s_handle = 0x0001;
  read_param.char_type.e_handle = 0xFFFF;
  read_param.char_type.uuid = Uuid::From16Bit(GATT_UUID_DATABASE_HASH);
  read_param.char_type.auth_req = GATT_AUTH_REQ_NONE;
  tGATT_STATUS status =
      GATTC_Read(p_clcb->bta_conn_id, GATT_READ_BY_TYPE, &read_param);

  if (status != GATT_SUCCESS) return false;

  if (is_svc_chg) {
    p_clcb->request_during_discovery =
        BTA_GATTC_DISCOVER_REQ_READ_DB_HASH_FOR_SVC_CHG;
  } else {
    p_clcb->request_during_discovery = BTA_GATTC_DISCOVER_REQ_READ_DB_HASH;
  }

  return true;
}

/* handle response of reading database hash */
static void bta_gattc_read_db_hash_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                        const tBTA_GATTC_OP_CMPL* p_data,
                                        bool is_svc_chg) {
  uint8_t op = (uint8_t)p_data->op_code;
  if (op != GATTC_OPTYPE_READ) {
    log::verbose("op = {}", p_data->hdr.layer_specific);
    return;
  }
  p_clcb->request_during_discovery = BTA_GATTC_DISCOVER_REQ_NONE;

  // run match flow only if the status is success
  bool matched = false;
  bool found = false;
  if (p_data->status == GATT_SUCCESS) {
    // start to compare local hash and remote hash
    uint16_t len = p_data->p_cmpl->att_value.len;
    uint8_t* data = p_data->p_cmpl->att_value.value;

    Octet16 remote_hash;
    if (len == remote_hash.max_size()) {
      std::copy(data, data + len, remote_hash.begin());

      Octet16 local_hash = p_clcb->p_srcb->gatt_database.Hash();
      matched = (local_hash == remote_hash);

      log::debug("lhash={}",
                 base::HexEncode(local_hash.data(), local_hash.size()));
      log::debug("rhash={}",
                 base::HexEncode(remote_hash.data(), remote_hash.size()));

      if (!matched) {
        gatt::Database db = bta_gattc_hash_load(remote_hash);
        if (!db.IsEmpty()) {
          p_clcb->p_srcb->gatt_database = db;
          found = true;
        }
        // If the device is trusted, link addr file to correct hash file
        if (found && (btm_sec_is_a_bonded_dev(p_clcb->p_srcb->server_bda))) {
          bta_gattc_cache_link(p_clcb->p_srcb->server_bda, remote_hash);
        }
      }
    }
  } else {
    // Only load cache for trusted device if no database hash on server side.
    // If is_svc_chg is true, do not read the existing cache.
    bool is_a_bonded_dev = btm_sec_is_a_bonded_dev(p_clcb->p_srcb->server_bda);
    if (!is_svc_chg && is_a_bonded_dev) {
      gatt::Database db = bta_gattc_cache_load(p_clcb->p_srcb->server_bda);
      if (!db.IsEmpty()) {
        p_clcb->p_srcb->gatt_database = db;
        found = true;
      }
      log::debug("load cache directly, result={}", found);
    } else {
      log::debug("skip read cache, is_svc_chg={}, is_a_bonded_dev={}",
                 is_svc_chg, is_a_bonded_dev);
    }
  }

  if (matched) {
    log::debug("hash is the same, skip service discovery");
    p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
    bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_SUCCESS);
  } else {
    if (found) {
      log::debug("hash found in cache, skip service discovery");

#if (BTA_GATT_DEBUG == TRUE)
      bta_gattc_display_cache_server(p_clcb->p_srcb->gatt_database);
#endif

      p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
      bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_SUCCESS);
    } else {
      log::debug("hash is not the same, start service discovery");
      bta_gattc_start_discover_internal(p_clcb);
    }
  }
}

/* handle response of reading extended properties descriptor */
static void bta_gattc_read_ext_prop_desc_cmpl(
    tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_OP_CMPL* p_data) {
  uint8_t op = (uint8_t)p_data->op_code;
  if (op != GATTC_OPTYPE_READ) {
    log::verbose("op = {}", p_data->hdr.layer_specific);
    return;
  }

  if (!p_clcb->disc_active) {
    log::verbose("not active in discover state");
    return;
  }
  p_clcb->request_during_discovery = BTA_GATTC_DISCOVER_REQ_NONE;

  tBTA_GATTC_SERV* p_srvc_cb = p_clcb->p_srcb;
  const uint8_t status = p_data->status;

  if (status == GATT_REQ_NOT_SUPPORTED &&
      !p_srvc_cb->read_multiple_not_supported) {
    // can't do "read multiple request", fall back to "read request"
    p_srvc_cb->read_multiple_not_supported = true;
    bta_gattc_explore_next_service(p_clcb->bta_conn_id, p_srvc_cb);
    return;
  }

  if (status != GATT_SUCCESS) {
    log::warn("Discovery on server failed: {}", loghex(status));
    bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_ERROR);
    return;
  }

  const tGATT_VALUE& att_value = p_data->p_cmpl->att_value;
  if (p_srvc_cb->read_multiple_not_supported && att_value.len != 2) {
    // Just one Characteristic Extended Properties value at a time in Read
    // Response
    log::warn("Read Response should be just 2 bytes!");
    bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_INVALID_PDU);
    return;
  }

  // Parsing is same for "Read Multiple Response", and for "Read Response"
  const uint8_t* p = att_value.value;
  std::vector<uint16_t> value_of_descriptors;
  while (p < att_value.value + att_value.len) {
    uint16_t extended_properties;
    STREAM_TO_UINT16(extended_properties, p);
    value_of_descriptors.push_back(extended_properties);
  }

  bool ret =
      p_srvc_cb->pending_discovery.SetValueOfDescriptors(value_of_descriptors);
  if (!ret) {
    log::warn("Problem setting Extended Properties descriptors values");
    bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_ERROR);
    return;
  }

  // Continue service discovery
  bta_gattc_explore_next_service(p_clcb->bta_conn_id, p_srvc_cb);
}

/*******************************************************************************
 *
 * Function         bta_gattc_fill_gatt_db_el
 *
 * Description      fill a btgatt_db_element_t value
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_fill_gatt_db_el(btgatt_db_element_t* p_attr,
                               bt_gatt_db_attribute_type_t type,
                               uint16_t att_handle, uint16_t s_handle,
                               uint16_t e_handle, uint16_t id, const Uuid& uuid,
                               uint8_t prop) {
  p_attr->type = type;
  p_attr->attribute_handle = att_handle;
  p_attr->start_handle = s_handle;
  p_attr->end_handle = e_handle;
  p_attr->id = id;
  p_attr->properties = prop;

  // Permissions are not discoverable using the attribute protocol.
  // Core 5.0, Part F, 3.2.5 Attribute Permissions
  p_attr->permissions = 0;
  p_attr->uuid = uuid;
}

/*******************************************************************************
 * Returns          number of elements inside db from start_handle to end_handle
 ******************************************************************************/
static size_t bta_gattc_get_db_size(const std::list<Service>& services,
                                    uint16_t start_handle,
                                    uint16_t end_handle) {
  if (services.empty()) return 0;

  size_t db_size = 0;

  for (const Service& service : services) {
    if (service.handle < start_handle) continue;

    if (service.end_handle > end_handle) break;

    db_size++;

    for (const Characteristic& charac : service.characteristics) {
      db_size++;

      db_size += charac.descriptors.size();
    }

    db_size += service.included_services.size();
  }

  return db_size;
}

/*******************************************************************************
 *
 * Function         bta_gattc_get_gatt_db_impl
 *
 * Description      copy the server GATT database into db parameter.
 *
 * Parameters       p_srvc_cb: server.
 *                  db: output parameter which will contain GATT database copy.
 *                      Caller is responsible for freeing it.
 *                  count: output parameter which will contain number of
 *                  elements in database.
 *
 * Returns          None.
 *
 ******************************************************************************/
static void bta_gattc_get_gatt_db_impl(tBTA_GATTC_SERV* p_srvc_cb,
                                       uint16_t start_handle,
                                       uint16_t end_handle,
                                       btgatt_db_element_t** db, int* count) {
  log::verbose("start_handle 0x{:04x}, end_handle 0x{:04x}", start_handle,
               end_handle);

  if (p_srvc_cb->gatt_database.IsEmpty()) {
    *count = 0;
    *db = NULL;
    return;
  }

  size_t db_size = bta_gattc_get_db_size(p_srvc_cb->gatt_database.Services(),
                                         start_handle, end_handle);

  void* buffer = osi_malloc(db_size * sizeof(btgatt_db_element_t));
  btgatt_db_element_t* curr_db_attr = (btgatt_db_element_t*)buffer;

  for (const Service& service : p_srvc_cb->gatt_database.Services()) {
    if (service.handle < start_handle) continue;

    if (service.end_handle > end_handle) break;

    bta_gattc_fill_gatt_db_el(curr_db_attr,
                              service.is_primary ? BTGATT_DB_PRIMARY_SERVICE
                                                 : BTGATT_DB_SECONDARY_SERVICE,
                              0 /* att_handle */, service.handle,
                              service.end_handle, service.handle, service.uuid,
                              0 /* prop */);
    curr_db_attr++;

    for (const Characteristic& charac : service.characteristics) {
      bta_gattc_fill_gatt_db_el(curr_db_attr, BTGATT_DB_CHARACTERISTIC,
                                charac.value_handle, 0 /* s_handle */,
                                0 /* e_handle */, charac.value_handle,
                                charac.uuid, charac.properties);
      btgatt_db_element_t* characteristic = curr_db_attr;
      curr_db_attr++;

      for (const Descriptor& desc : charac.descriptors) {
        bta_gattc_fill_gatt_db_el(
            curr_db_attr, BTGATT_DB_DESCRIPTOR, desc.handle, 0 /* s_handle */,
            0 /* e_handle */, desc.handle, desc.uuid, 0 /* property */);

        if (desc.uuid == Uuid::From16Bit(GATT_UUID_CHAR_EXT_PROP)) {
          characteristic->extended_properties =
              desc.characteristic_extended_properties;
        }
        curr_db_attr++;
      }
    }

    for (const IncludedService& p_isvc : service.included_services) {
      bta_gattc_fill_gatt_db_el(curr_db_attr, BTGATT_DB_INCLUDED_SERVICE,
                                p_isvc.handle, p_isvc.start_handle,
                                0 /* e_handle */, p_isvc.handle, p_isvc.uuid,
                                0 /* property */);
      curr_db_attr++;
    }
  }

  *db = (btgatt_db_element_t*)buffer;
  *count = db_size;
}

/*******************************************************************************
 *
 * Function         bta_gattc_get_gatt_db
 *
 * Description      copy the server GATT database into db parameter.
 *
 * Parameters       conn_id: connection ID which identify the server.
 *                  db: output parameter which will contain GATT database copy.
 *                      Caller is responsible for freeing it.
 *                  count: number of elements in database.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_get_gatt_db(uint16_t conn_id, uint16_t start_handle,
                           uint16_t end_handle, btgatt_db_element_t** db,
                           int* count) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  log::info("");
  if (p_clcb == NULL) {
    log::error("Unknown conn_id={}", loghex(conn_id));
    return;
  }

  if (p_clcb->state != BTA_GATTC_CONN_ST) {
    log::error("server cache not available, CLCB state={}", p_clcb->state);
    return;
  }

  if (!p_clcb->p_srcb || p_clcb->p_srcb->pending_discovery.InProgress() ||
      p_clcb->p_srcb->gatt_database.IsEmpty()) {
    log::error("No server cache available");
    return;
  }

  bta_gattc_get_gatt_db_impl(p_clcb->p_srcb, start_handle, end_handle, db,
                             count);
}
