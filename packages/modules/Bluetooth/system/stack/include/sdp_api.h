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

#pragma once

#include <base/strings/stringprintf.h>

#include <cstdint>

#include "stack/include/sdp_callback.h"
#include "stack/include/sdp_device_id.h"
#include "stack/include/sdp_status.h"
#include "stack/include/sdpdefs.h"
#include "stack/sdp/internal/sdp_api.h"
#include "stack/sdp/sdp_discovery_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace legacy {
namespace stack {
namespace sdp {

struct tSdpApi {
  struct {
    /*******************************************************************************
      Function         SDP_InitDiscoveryDb

      Description      This function is called to initialize a discovery
                       database.

      Parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       len         - (input) size (in bytes) of the memory
                        NOTE: This must be larger than sizeof(tSDP_DISCOVERY_DB)
                       num_uuid    - (input) number of UUID filters applied
                       p_uuid_list - (input) list of UUID filters
                       num_attr    - (input) number of attribute filters
                                             applied
                       p_attr_list - (input) list of attribute filters

      Returns          true if successful, false if one or more parameters are
                       bad
     ******************************************************************************/
    bool (*SDP_InitDiscoveryDb)(tSDP_DISCOVERY_DB*, uint32_t, uint16_t,
                                const bluetooth::Uuid*, uint16_t,
                                const uint16_t*);

    /*******************************************************************************

      Function         SDP_CancelServiceSearch

      Description      This function cancels an active query to an SDP server.

      Parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.

      Returns          true if discovery cancelled, false if a matching
                       activity is not found.

     ******************************************************************************/
    bool (*SDP_CancelServiceSearch)(const tSDP_DISCOVERY_DB*);

    /*******************************************************************************

      Function         SDP_ServiceSearchRequest

      Description      This function queries an SDP server for information.

      Parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       p_cb        - (input) callback executed when complete

      Returns          true if discovery started, false if failed.

     ******************************************************************************/
    bool (*SDP_ServiceSearchRequest)(const RawAddress&, tSDP_DISCOVERY_DB*,
                                     tSDP_DISC_CMPL_CB*);

    /*******************************************************************************

      Function         SDP_ServiceSearchAttributeRequest

      Description      This function queries an SDP server for information.

                       The difference between this API function and the
                       function SDP_ServiceSearchRequest is that this one does
                       a combined ServiceSearchAttributeRequest SDP function.

      Parameters:      bd_addr     - (input) device address for service search
                       p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       p_cb        - (input) callback executed when complete

      Returns          true if discovery started, false if failed.

     ******************************************************************************/
    bool (*SDP_ServiceSearchAttributeRequest)(const RawAddress&,
                                              tSDP_DISCOVERY_DB*,
                                              tSDP_DISC_CMPL_CB*);

    /*******************************************************************************

      Function         SDP_ServiceSearchAttributeRequest2

      Description      This function queries an SDP server for information.

                       The difference between this API function and the
                       function SDP_ServiceSearchRequest is that this one does
                       a combined ServiceSearchAttributeRequest SDP function
                       with the user data piggyback

      parameters:      bd_addr     - (input) device address for service search
                       p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       p_cb2       - (input) callback executed when complete
                       p_data      - (input) user data

      Returns          true if discovery started, false if failed.

     ******************************************************************************/
    bool (*SDP_ServiceSearchAttributeRequest2)(const RawAddress&,
                                               tSDP_DISCOVERY_DB*,
                                               tSDP_DISC_CMPL_CB2*,
                                               const void*);
  } service;

  struct {
    /*******************************************************************************

      Function         SDP_FindServiceInDb

      Description      This function queries an SDP database for a specific
                       service. If the p_start_rec pointer is NULL, it looks
                       from the beginning of the database, else it continues
                       from the next record after p_start_rec.

      parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       uuid16      - (input) Uuid to search in db
                       disc_rec    - (output) Record found, null otherwise

      Returns          Pointer to record containing service class, or NULL

     ******************************************************************************/
    tSDP_DISC_REC* (*SDP_FindServiceInDb)(const tSDP_DISCOVERY_DB*, uint16_t,
                                          tSDP_DISC_REC*);

    /*******************************************************************************

      Function         SDP_FindServiceUUIDInDb

      Description      This function queries an SDP database for a specific
                       service. If the p_start_rec pointer is NULL, it looks
                       from the beginning of the database, else it continues
                       from the next record after p_start_rec.

      NOTE             the only difference between this function and the
                       previous function "SDP_FindServiceInDb()" is that this
                       function takes a Uuid input.

      parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       uuid        - (input) Uuid to search in db
                       disc_rec    - (input) Start record, null from beginning

      Returns          Pointer to record containing service class, or NULL

     ******************************************************************************/
    tSDP_DISC_REC* (*SDP_FindServiceUUIDInDb)(const tSDP_DISCOVERY_DB*,
                                              const bluetooth::Uuid&,
                                              tSDP_DISC_REC*);

    /*******************************************************************************

      Function         SDP_FindServiceInDb_128bit

      Description      Query an SDP database for a specific service.
                       If the p_start_rec pointer is NULL, look from the
                       beginning of the database, else continue from the next
                       record after p_start_rec.

      parameters:      p_db        - (input) address of an area of memory where
                                             the discovery database is managed.
                       disc_rec    - (input) Start record, null from beginning

      Returns          Pointer to record containing service class, or NULL

     ******************************************************************************/
    tSDP_DISC_REC* (*SDP_FindServiceInDb_128bit)(const tSDP_DISCOVERY_DB*,
                                                 tSDP_DISC_REC*);
  } db;

  struct {
    /*******************************************************************************

      Local discovery database API

      Function         SDP_FindAttributeInRec

      Description      This function searches an SDP discovery record for a
                       specific attribute.

      parameters:      disc_rec    - (input) Start record must not be null
                       attr_id     - (input) Attribute id to search

      Returns          Pointer to matching attribute entry, or NULL

     ******************************************************************************/
    tSDP_DISC_ATTR* (*SDP_FindAttributeInRec)(const tSDP_DISC_REC*, uint16_t);

    /*******************************************************************************

      Function         SDP_FindServiceUUIDInRec_128bit

      Description      Read the 128-bit service UUID within a record;
                       if there is any.

      Parameters:      p_rec      - (input) pointer to a SDP record.
                       p_uuid     - (output) parameter to save the UUID found.

      Returns          true if found, otherwise false.

     ******************************************************************************/
    bool (*SDP_FindServiceUUIDInRec_128bit)(const tSDP_DISC_REC*,
                                            bluetooth::Uuid*);

    /*******************************************************************************

      Function         SDP_FindProtocolListElemInRec

      Description      This function looks at a specific discovery record for a
                       protocol list element.

      Parameters:      p_rec      - (input) pointer to a SDP record.
                       p_uuid     - (input) layer UUID.
                       p_elem     - (output) protocol element

      Returns          true if found, false if not
                       If found, the passed protocol list element is filled in.

     ******************************************************************************/
    bool (*SDP_FindProtocolListElemInRec)(const tSDP_DISC_REC*, uint16_t,
                                          tSDP_PROTOCOL_ELEM*);

    /*******************************************************************************

      Function         SDP_FindProfileVersionInRec

      Description      This function looks at a specific discovery record for
                       the Profile list descriptor, and pulls out the version
                       number. The version number consists of an 8-bit major
                       version and an 8-bit minor version.

      Parameters:      p_rec      - (input) pointer to a SDP record.
                       p_uuid     - (input) profile UUID.
                       p_elem     - (output) major and minor version numbers

      Returns          true if found, false if not

     ******************************************************************************/
    bool (*SDP_FindProfileVersionInRec)(const tSDP_DISC_REC*, uint16_t,
                                        uint16_t*);

    /*******************************************************************************

      Function         SDP_FindServiceUUIDInRec

      Description      Read the service UUID within a record;
                       if there is any.

      Parameters:      p_rec      - (input) pointer to a SDP record.
                       p_uuid     - (output) found UUID or null.

      Returns          true if found, otherwise false.

     ******************************************************************************/
    bool (*SDP_FindServiceUUIDInRec)(const tSDP_DISC_REC* p_rec,
                                     bluetooth::Uuid* p_uuid);
  } record;

  struct {
    /*******************************************************************************

      API into SDP for Local service database updates

      Function         SDP_CreateRecord

      Description      This function is called to create a record in the
                       database. This would be through the SDP database
                       maintenance API. The record is created empty, teh
                       application should then call "add_attribute" *to add
                       the record's attributes.

      Returns          Record handle if OK, else 0.

     ******************************************************************************/
    uint32_t (*SDP_CreateRecord)(void);

    /*******************************************************************************

      Function         SDP_DeleteRecord

      Description      This function is called to add a record (or all records)
                       from the database. This would be through the SDP
                       database maintenance API.

      Parameters:      handle     - (input) Handle to delete, 0 for all records
                                            to be deleted

      Returns          true if succeeded, else false

     ******************************************************************************/
    bool (*SDP_DeleteRecord)(uint32_t);

    /*******************************************************************************

      Function         SDP_AddAttribute

      Description      This function is called to add an attribute to a record.
                       This would be through the SDP database maintenance API.
                       If the attribute already exists in the record, it is
                       replaced with the new value.

      NOTE             Attribute values must be passed as a Big Endian stream.

      Parameters:      handle     - (input) Handle to add
                       attr_id    - (input) Attribute id to add
                       attr_type  - (input) Attribute type to add
                       attr_len   - (input) Attribute data length
                       p_val      - (input) Attribute data value

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddAttribute)(uint32_t handle, uint16_t attr_id,
                             uint8_t attr_type, uint32_t attr_len,
                             uint8_t* p_val);

    /*******************************************************************************

      Function         SDP_AddSequence

      Description      This function is called to add a sequence to a record.
                       This would be through the SDP database maintenance API.
                       If the sequence already exists in the record, it is
                       replaced with the new sequence.

      NOTE             Element values must be passed as a Big Endian stream.

      Parameters:      handle     - (input) Handle to add
                       attr_id    - (input) Attribute id to add
                       num_elem   - (input) Number of elements in array
                       type[]     - (input) Element type
                       len[]      - (input) Element data length
                       p_val[]    - (input) Element data value

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddSequence)(uint32_t handle, uint16_t attr_id,
                            uint16_t num_elem, uint8_t type[], uint8_t len[],
                            uint8_t* p_val[]);

    /*******************************************************************************

      Function         SDP_AddUuidSequence

      Description      This function is called to add a UUID sequence to a
                       record. This would be through the SDP database
                       maintenance API. If the sequence already exists in the
                      record, it is replaced with the new sequence.

      Parameters:      handle     - (input) Handle to add
                       attr_id    - (input) Attribute id to add
                       num_uuids  - (input) Number of uuids in array
                       p_uuids[]  - (input) Array uuid

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddUuidSequence)(uint32_t handle, uint16_t attr_id,
                                uint16_t num_uuids, uint16_t* p_uuids);

    /*******************************************************************************

      Function         SDP_AddProtocolList

      Description      This function is called to add a protocol descriptor
                       list to a record. This would be through the SDP database
                       maintenance API. If the protocol list already exists in
                       the record, it is replaced with the new list.

      Parameters:      handle     - (input) Handle to add
                       num_elem   - (input) Number of elements to add
                       elem_list[]- (input) Element data list to add

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddProtocolList)(uint32_t handle, uint16_t num_elem,
                                tSDP_PROTOCOL_ELEM* p_elem_list);

    /*******************************************************************************

      Function         SDP_AddAdditionProtoLists

      Description      This function is called to add a protocol descriptor
                       list to a record. This would be through the SDP database
                       maintenance API. If the protocol list already exists in
                       the record, it is replaced with the new list.

      Parameters:      handle     - (input) Handle to add
                       num_elem   - (input) Number of elements to add
                       proto_list[]- (input) Element data list to add

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddAdditionProtoLists)(uint32_t handle, uint16_t num_elem,
                                      tSDP_PROTO_LIST_ELEM* p_proto_list);

    /*******************************************************************************

      Function         SDP_AddProfileDescriptorList

      Description      This function is called to add a profile descriptor list
                       to a record. This would be through the SDP database
                       maintenance API. If the version already exists in the
                       record, it is replaced with the new one.

      Parameters:      handle     - (input) Handle to add
                       uuid       - (input) Uuid to add
                       version    - (input) major and minor version

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddProfileDescriptorList)(uint32_t handle, uint16_t profile_uuid,
                                         uint16_t version);

    /*******************************************************************************

      Function         SDP_AddLanguageBaseAttrIDList

      Description      This function is called to add a language base attr list
                       to a record. This would be through the SDP database
                       maintenance API. If the version already exists in the
                       record, it is replaced with the new one.

      Parameters:      handle     - (input) Handle to add
                       lang       - (input) language base descriptor
                       char_enc   - (input) character encoding
                       base_id    - (input) base id

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddLanguageBaseAttrIDList)(uint32_t handle, uint16_t lang,
                                          uint16_t char_enc, uint16_t base_id);

    /*******************************************************************************

      Function         SDP_AddServiceClassIdList

      Description      This function is called to add a service list to a
                       record. This would be through the SDP database
                       maintenance API. If the service list already exists in
                       the record, it is replaced with the new list.

      Parameters:      handle       - (input) Handle to add
                       num_services - (input) number of services to add
                       uuids[]      - (input) list of service uuids to add

      Returns          true if added OK, else false

     ******************************************************************************/
    bool (*SDP_AddServiceClassIdList)(uint32_t handle, uint16_t num_services,
                                      uint16_t* p_service_uuids);
  } handle;

  struct {
    /*******************************************************************************

      Device Identification API

      Function         SDP_SetLocalDiRecord

      Description      This function adds a DI record to the local SDP
                       database.

      Parameters:      info         - (input) device identification record
                       p_handle     - (output) handle of record if successful

      Returns          Returns SDP_SUCCESS if record added successfully, else
                       error

     ******************************************************************************/
    uint16_t (*SDP_SetLocalDiRecord)(const tSDP_DI_RECORD* device_info,
                                     uint32_t* p_handle);

    /*******************************************************************************

      Device Identification API

      Function         SDP_DiDiscover

      Description      This function queries a remote device for DI
                       information.

      Parameters:      bd_addr      - (input) remote device
                       p_db         - (input) dicovery database
                       len          - (input ) data base length
                       p_cb         - (input) callback when complete

      Returns          SDP_SUCCESS if query started successfully, else error

     ******************************************************************************/
    tSDP_STATUS (*SDP_DiDiscover)(const RawAddress& remote_device,
                                  tSDP_DISCOVERY_DB* p_db, uint32_t len,
                                  tSDP_DISC_CMPL_CB* p_cb);

    /*******************************************************************************

      Device Identification API

      Function         SDP_GetNumDiRecords

      Description      Searches specified database for DI records

      Parameters:      p_db         - (input) dicovery database

      Returns          number of DI records found

     ******************************************************************************/
    uint8_t (*SDP_GetNumDiRecords)(const tSDP_DISCOVERY_DB* p_db);

    /*******************************************************************************

      Device Identification API

      Function         SDP_GetDiRecord

      Description      This function retrieves a remote device's DI record from
                       the specified database.

      Parameters:      index        - (input) record index to retrieve
                       device_info  - (input) dicovery database
                       p_cb         - (input) callback when complete

      Returns          SDP_SUCCESS if record retrieved, else error

     ******************************************************************************/
    uint16_t (*SDP_GetDiRecord)(uint8_t getRecordIndex,
                                tSDP_DI_GET_RECORD* device_info,
                                const tSDP_DISCOVERY_DB* p_db);

  } device_id;
};

const struct tSdpApi* get_legacy_stack_sdp_api();

struct tLegacyStackSdbCallback {
  void(tSDP_DISC_CMPL_CB)(const RawAddress& bd_addr, tSDP_RESULT result);
  void(tSDP_DISC_CMPL_CB2)(const RawAddress& bd_addr, tSDP_RESULT result,
                           const void* user_data);
};

}  // namespace sdp
}  // namespace stack
}  // namespace legacy
}  // namespace bluetooth
