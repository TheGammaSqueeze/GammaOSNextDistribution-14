/******************************************************************************
 *
 *  Copyright 2003-2016 Broadcom Corporation
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
 *  AVRCP SDP related functions
 *
 ******************************************************************************/
#define LOG_TAG "avrcp"

#include <bluetooth/log.h>
#include <string.h>

#include "avrc_api.h"
#include "avrc_int.h"
#include "os/log.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "stack/include/sdpdefs.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth;
using namespace bluetooth::legacy::stack::sdp;

using bluetooth::Uuid;

/*****************************************************************************
 *  Global data
 ****************************************************************************/
tAVRC_CB avrc_cb;
static uint16_t a2dp_attr_list_sdp[] = {
    ATTR_ID_SERVICE_CLASS_ID_LIST, /* update A2DP_NUM_ATTR, if changed */
    ATTR_ID_BT_PROFILE_DESC_LIST,  ATTR_ID_SUPPORTED_FEATURES,
    ATTR_ID_SERVICE_NAME,          ATTR_ID_PROTOCOL_DESC_LIST,
    ATTR_ID_PROVIDER_NAME};

/******************************************************************************
 *
 * Function         avrc_sdp_cback
 *
 * Description      This is the SDP callback function used by A2DP_FindService.
 *                  This function will be executed by SDP when the service
 *                  search is completed.  If the search is successful, it
 *                  finds the first record in the database that matches the
 *                  UUID of the search.  Then retrieves various parameters
 *                  from the record.  When it is finished it calls the
 *                  application callback function.
 *
 * Returns          Nothing.
 *
 *****************************************************************************/
static void avrc_sdp_cback(UNUSED_ATTR const RawAddress& bd_addr,
                           tSDP_STATUS status) {
  log::verbose("status: {}", status);

  /* reset service_uuid, so can start another find service */
  avrc_cb.service_uuid = 0;

  /* return info from sdp record in app callback function */
  avrc_cb.find_cback.Run(status);

  return;
}

/******************************************************************************
 *
 * Function         AVRC_FindService
 *
 * Description      This function is called by the application to perform
 *                  service discovery and retrieve AVRCP SDP record information
 *                  from a peer device.  Information is returned for the first
 *                  service record found on the server that matches the service
 *                  UUID. The callback function will be executed when service
 *                  discovery is complete.  There can only be one outstanding
 *                  call to AVRC_FindService() at a time; the application must
 *                  wait for the callback before it makes another call to the
 *                  function.  The application is responsible for allocating
 *                  memory for the discovery database.  It is recommended that
 *                  the size of the discovery database be at least 300 bytes.
 *                  The application can deallocate the memory after the
 *                  callback function has executed.
 *
 *                  Input Parameters:
 *                      service_uuid: Indicates
 *                                       TG(UUID_SERVCLASS_AV_REM_CTRL_TARGET)
 *                                     r CT(UUID_SERVCLASS_AV_REMOTE_CONTROL)
 *
 *                      bd_addr:  BD address of the peer device.
 *
 *                      p_db:  SDP discovery database parameters.
 *
 *                      p_cback:  Pointer to the callback function.
 *
 *                  Output Parameters:
 *                      None.
 *
 * Returns          AVRC_SUCCESS if successful.
 *                  AVRC_BAD_PARAMS if discovery database parameters are
 *                  invalid.
 *                  AVRC_NO_RESOURCES if there are not enough resources to
 *                                    perform the service search.
 *
 *****************************************************************************/
uint16_t AVRC_FindService(uint16_t service_uuid, const RawAddress& bd_addr,
                          tAVRC_SDP_DB_PARAMS* p_db,
                          const tAVRC_FIND_CBACK& find_cback) {
  bool result = true;

  log::verbose("uuid: {:x}", service_uuid);
  if ((service_uuid != UUID_SERVCLASS_AV_REM_CTRL_TARGET &&
       service_uuid != UUID_SERVCLASS_AV_REMOTE_CONTROL) ||
      p_db == NULL || p_db->p_db == NULL || find_cback.is_null())
    return AVRC_BAD_PARAM;

  /* check if it is busy */
  if (avrc_cb.service_uuid == UUID_SERVCLASS_AV_REM_CTRL_TARGET ||
      avrc_cb.service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL)
    return AVRC_NO_RESOURCES;


  if (p_db->p_attrs == NULL || p_db->num_attr == 0) {
    p_db->p_attrs = a2dp_attr_list_sdp;
    p_db->num_attr = AVRC_NUM_ATTR;
  }

  Uuid uuid_list = Uuid::From16Bit(service_uuid);
  result = get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
      p_db->p_db, p_db->db_len, 1, &uuid_list, p_db->num_attr, p_db->p_attrs);

  if (result) {
    /* store service_uuid and discovery db pointer */
    avrc_cb.p_db = p_db->p_db;
    avrc_cb.service_uuid = service_uuid;
    avrc_cb.find_cback = find_cback;

    /* perform service search */
    result =
        get_legacy_stack_sdp_api()->service.SDP_ServiceSearchAttributeRequest(
            bd_addr, p_db->p_db, avrc_sdp_cback);

    if (!result) {
      log::error("Failed to init SDP for peer {}",
                 ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
      avrc_sdp_cback(bd_addr, SDP_GENERIC_ERROR);
    }
  }

  return (result ? AVRC_SUCCESS : AVRC_FAIL);
}

/******************************************************************************
 *
 * Function         AVRC_AddRecord
 *
 * Description      This function is called to build an AVRCP SDP record.
 *                  Prior to calling this function the application must
 *                  call get_legacy_stack_sdp_api()->handle.SDP_CreateRecord()
 *                  to create an SDP record.
 *
 *                  Input Parameters:
 *                      service_uuid:  Indicates
 *                                        TG(UUID_SERVCLASS_AV_REM_CTRL_TARGET)
 *                                     or CT(UUID_SERVCLASS_AV_REMOTE_CONTROL)
 *
 *                      p_service_name:  Pointer to a null-terminated character
 *                                       string containing the service name.
 *                      If service name is not used set this to NULL.
 *
 *                      p_provider_name:  Pointer to a null-terminated character
 *                                        string containing the provider name.
 *                      If provider name is not used set this to NULL.
 *
 *                      categories:  Supported categories.
 *
 *                      sdp_handle:  SDP handle returned by
 *                      get_legacy_stack_sdp_api()->handle.SDP_CreateRecord().
 *
 *                      browse_supported:  browse support info.
 *
 *                      profile_version:  profile version of avrcp record.
 *
 *                      cover_art_psm: The PSM of a cover art service, if
 *                      supported. Use 0 Otherwise. Ignored on controller
 *
 *                  Output Parameters:
 *                      None.
 *
 * Returns          AVRC_SUCCESS if successful.
 *                  AVRC_NO_RESOURCES if not enough resources to build the SDP
 *                                    record.
 *
 *****************************************************************************/
uint16_t AVRC_AddRecord(uint16_t service_uuid, const char* p_service_name,
                        const char* p_provider_name, uint16_t categories,
                        uint32_t sdp_handle, bool browse_supported,
                        uint16_t profile_version, uint16_t cover_art_psm) {
  uint16_t browse_list[1];
  bool result = true;
  uint8_t temp[8];
  uint8_t* p;
  uint16_t count = 1;
  uint8_t index = 0;
  uint16_t class_list[2];

  log::verbose(
      "Add AVRCP SDP record, uuid: {:x}, profile_version: 0x{:x}, "
      "supported_features: 0x{:x}, psm: 0x{:x}",
      service_uuid, profile_version, categories, cover_art_psm);

  if (service_uuid != UUID_SERVCLASS_AV_REM_CTRL_TARGET &&
      service_uuid != UUID_SERVCLASS_AV_REMOTE_CONTROL)
    return AVRC_BAD_PARAM;

  /* add service class id list */
  class_list[0] = service_uuid;
  if ((service_uuid == UUID_SERVCLASS_AV_REMOTE_CONTROL) &&
      (profile_version > AVRC_REV_1_3)) {
    class_list[1] = UUID_SERVCLASS_AV_REM_CTRL_CONTROL;
    count = 2;
  }
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(
      sdp_handle, count, class_list);

  uint16_t protocol_reported_version;
  /* AVRCP versions 1.3 to 1.5 report (version - 1) in the protocol
     descriptor list. Oh, and 1.6 and 1.6.1 report version 1.4.
     /because-we-smart */
  if (profile_version < AVRC_REV_1_6) {
    protocol_reported_version = profile_version - 1;
  } else {
    protocol_reported_version = AVCT_REV_1_4;
  }

  /* add protocol descriptor list */
  tSDP_PROTOCOL_ELEM avrc_proto_desc_list[AVRC_NUM_PROTO_ELEMS];
  avrc_proto_desc_list[0].num_params = 1;
  avrc_proto_desc_list[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  avrc_proto_desc_list[0].params[0] = AVCT_PSM;
  avrc_proto_desc_list[0].params[1] = 0;
  for (index = 1; index < AVRC_NUM_PROTO_ELEMS; index++) {
    avrc_proto_desc_list[index].num_params = 1;
    avrc_proto_desc_list[index].protocol_uuid = UUID_PROTOCOL_AVCTP;
    avrc_proto_desc_list[index].params[0] = protocol_reported_version;
    avrc_proto_desc_list[index].params[1] = 0;
  }
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddProtocolList(
      sdp_handle, AVRC_NUM_PROTO_ELEMS, &avrc_proto_desc_list[0]);

  /* additional protocal descriptor, required only for version > 1.3 */
  if (profile_version > AVRC_REV_1_3) {
    int num_additional_protocols = 0;
    int i = 0;
    tSDP_PROTO_LIST_ELEM avrc_add_proto_desc_lists[2];

    /* If we support browsing then add the list */
    if (browse_supported) {
      log::verbose("Add Browsing PSM to additional protocol descriptor lists");
      num_additional_protocols++;
      avrc_add_proto_desc_lists[i].num_elems = 2;
      avrc_add_proto_desc_lists[i].list_elem[0].num_params = 1;
      avrc_add_proto_desc_lists[i].list_elem[0].protocol_uuid =
          UUID_PROTOCOL_L2CAP;
      avrc_add_proto_desc_lists[i].list_elem[0].params[0] = AVCT_BR_PSM;
      avrc_add_proto_desc_lists[i].list_elem[0].params[1] = 0;
      avrc_add_proto_desc_lists[i].list_elem[1].num_params = 1;
      avrc_add_proto_desc_lists[i].list_elem[1].protocol_uuid =
          UUID_PROTOCOL_AVCTP;
      avrc_add_proto_desc_lists[i].list_elem[1].params[0] =
          protocol_reported_version;
      avrc_add_proto_desc_lists[i].list_elem[1].params[1] = 0;
      i++;
    }

    /* Add the BIP PSM for cover art on 1.6+ target devices that support it */
    if (profile_version >= AVRC_REV_1_6 &&
        service_uuid == UUID_SERVCLASS_AV_REM_CTRL_TARGET &&
        cover_art_psm > 0) {
      log::verbose(
          "Add AVRCP BIP PSM to additional protocol descriptor lists, psm: "
          "0x{:x}",
          cover_art_psm);
      num_additional_protocols++;
      avrc_add_proto_desc_lists[i].num_elems = 2;
      avrc_add_proto_desc_lists[i].list_elem[0].num_params = 1;
      avrc_add_proto_desc_lists[i].list_elem[0].protocol_uuid =
          UUID_PROTOCOL_L2CAP;
      avrc_add_proto_desc_lists[i].list_elem[0].params[0] = cover_art_psm;
      avrc_add_proto_desc_lists[i].list_elem[0].params[1] = 0;
      avrc_add_proto_desc_lists[i].list_elem[1].num_params = 0;
      avrc_add_proto_desc_lists[i].list_elem[1].protocol_uuid =
          UUID_PROTOCOL_OBEX;
      avrc_add_proto_desc_lists[i].list_elem[1].params[0] = 0;
      i++;
    }

    /* Add the additional lists if we support any */
    if (num_additional_protocols > 0) {
      log::verbose("Add {} additional protocol descriptor lists",
                   num_additional_protocols);
      result &= get_legacy_stack_sdp_api()->handle.SDP_AddAdditionProtoLists(
          sdp_handle, num_additional_protocols, avrc_add_proto_desc_lists);
    }
  }
  /* add profile descriptor list   */
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, UUID_SERVCLASS_AV_REMOTE_CONTROL, profile_version);

  /* add supported categories */
  p = temp;
  UINT16_TO_BE_STREAM(p, categories);
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SUPPORTED_FEATURES, UINT_DESC_TYPE, (uint32_t)2,
      (uint8_t*)temp);

  /* add provider name */
  if (p_provider_name != NULL) {
    result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_PROVIDER_NAME, TEXT_STR_DESC_TYPE,
        (uint32_t)(strlen(p_provider_name) + 1), (uint8_t*)p_provider_name);
  }

  /* add service name */
  if (p_service_name != NULL) {
    result &= get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_SERVICE_NAME, TEXT_STR_DESC_TYPE,
        (uint32_t)(strlen(p_service_name) + 1), (uint8_t*)p_service_name);
  }

  /* add browse group list */
  browse_list[0] = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  result &= get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, browse_list);

  return (result ? AVRC_SUCCESS : AVRC_FAIL);
}

/*******************************************************************************
 *
 * Function          AVRC_RemoveRecord
 *
 * Description       This function is called to remove an AVRCP SDP record.
 *
 *                   Input Parameters:
 *                       sdp_handle:  Handle you used with AVRC_AddRecord
 *
 * Returns           AVRC_SUCCESS if successful.
 *                   AVRC_FAIL otherwise
 *
 *******************************************************************************/
uint16_t AVRC_RemoveRecord(uint32_t sdp_handle) {
  log::verbose("remove AVRCP SDP record");
  bool result = get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(sdp_handle);
  return (result ? AVRC_SUCCESS : AVRC_FAIL);
}

/*******************************************************************************
 *
 * Function         AVRC_Init
 *
 * Description      This function is called at stack startup to allocate the
 *                  control block (if using dynamic memory), and initializes the
 *                  control block and tracing level.
 *
 * Returns          void
 *
 ******************************************************************************/
void AVRC_Init(void) {
  memset(&avrc_cb, 0, sizeof(tAVRC_CB));
}
