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

#include <cstdint>

#include "stack/include/sdp_callback.h"
#include "stack/include/sdp_device_id.h"
#include "stack/sdp/sdp_discovery_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/*******************************************************************************
 *
 * Function         SDP_InitDiscoveryDb
 *
 * Description      This function is called to initialize a discovery database.
 *
 * Returns          true if successful, false if one or more parameters are bad
 *
 ******************************************************************************/
bool SDP_InitDiscoveryDb(tSDP_DISCOVERY_DB* p_db, uint32_t len,
                         uint16_t num_uuid, const bluetooth::Uuid* p_uuid_list,
                         uint16_t num_attr, const uint16_t* p_attr_list);

/*******************************************************************************
 *
 * Function         SDP_CancelServiceSearch
 *
 * Description      This function cancels an active query to an SDP server.
 *
 * Returns          true if discovery cancelled, false if a matching activity is
 *                  not found.
 *
 ******************************************************************************/
bool SDP_CancelServiceSearch(const tSDP_DISCOVERY_DB* p_db);

/*******************************************************************************
 *
 * Function         SDP_ServiceSearchRequest
 *
 * Description      This function queries an SDP server for information.
 *
 * Returns          true if discovery started, false if failed.
 *
 ******************************************************************************/
bool SDP_ServiceSearchRequest(const RawAddress& p_bd_addr,
                              tSDP_DISCOVERY_DB* p_db, tSDP_DISC_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         SDP_ServiceSearchAttributeRequest
 *
 * Description      This function queries an SDP server for information.
 *
 *                  The difference between this API function and the function
 *                  SDP_ServiceSearchRequest is that this one does a
 *                  combined ServiceSearchAttributeRequest SDP function.
 *
 * Returns          true if discovery started, false if failed.
 *
 ******************************************************************************/
bool SDP_ServiceSearchAttributeRequest(const RawAddress& p_bd_addr,
                                       tSDP_DISCOVERY_DB* p_db,
                                       tSDP_DISC_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         SDP_ServiceSearchAttributeRequest2
 *
 * Description      This function queries an SDP server for information.
 *
 *                  The difference between this API function and the function
 *                  SDP_ServiceSearchRequest is that this one does a
 *                  combined ServiceSearchAttributeRequest SDP function with the
 *                  user data piggyback
 *
 * Returns          true if discovery started, false if failed.
 *
 ******************************************************************************/
bool SDP_ServiceSearchAttributeRequest2(const RawAddress& p_bd_addr,
                                        tSDP_DISCOVERY_DB* p_db,
                                        tSDP_DISC_CMPL_CB2* p_cb,
                                        const void* user_data);

/* API of utilities to find data in the local discovery database */

/*******************************************************************************
 *
 * Function         SDP_FindAttributeInRec
 *
 * Description      This function searches an SDP discovery record for a
 *                  specific attribute.
 *
 * Returns          Pointer to matching attribute entry, or NULL
 *
 ******************************************************************************/
tSDP_DISC_ATTR* SDP_FindAttributeInRec(const tSDP_DISC_REC* p_rec,
                                       uint16_t attr_id);

/*******************************************************************************
 *
 * Function         SDP_FindServiceInDb
 *
 * Description      This function queries an SDP database for a specific
 *                  service. If the p_start_rec pointer is NULL, it looks from
 *                  the beginning of the database, else it continues from the
 *                  next record after p_start_rec.
 *
 * Returns          Pointer to record containing service class, or NULL
 *
 ******************************************************************************/
tSDP_DISC_REC* SDP_FindServiceInDb(const tSDP_DISCOVERY_DB* p_db,
                                   uint16_t service_uuid,
                                   tSDP_DISC_REC* p_start_rec);

/*******************************************************************************
 *
 * Function         SDP_FindServiceUUIDInDb
 *
 * Description      This function queries an SDP database for a specific
 *                  service. If the p_start_rec pointer is NULL, it looks from
 *                  the beginning of the database, else it continues from the
 *                  next record after p_start_rec.
 *
 * NOTE             the only difference between this function and the previous
 *                  function "SDP_FindServiceInDb()" is that this function takes
 *                  a Uuid input.
 *
 * Returns          Pointer to record containing service class, or NULL
 *
 ******************************************************************************/
tSDP_DISC_REC* SDP_FindServiceUUIDInDb(const tSDP_DISCOVERY_DB* p_db,
                                       const bluetooth::Uuid& uuid,
                                       tSDP_DISC_REC* p_start_rec);

/*******************************************************************************
 *
 * Function         SDP_FindServiceUUIDInRec_128bit
 *
 * Description      Read the 128-bit service UUID within a record,
 *                  if there is any.
 *
 * Parameters:      p_rec      - pointer to a SDP record.
 *                  p_uuid     - output parameter to save the UUID found.
 *
 * Returns          true if found, otherwise false.
 *
 ******************************************************************************/
bool SDP_FindServiceUUIDInRec_128bit(const tSDP_DISC_REC* p_rec,
                                     bluetooth::Uuid* p_uuid);

/*******************************************************************************
 *
 * Function         SDP_FindServiceUUIDInRec
 *
 * Description      Read the service UUID within a record,
 *                  if there is any.
 *
 * Parameters:      p_rec  - pointer to a SDP record.
 *                  p_uuid - pointer to a UUID
 *
 * Returns          true if found, otherwise false.
 *
 ******************************************************************************/
bool SDP_FindServiceUUIDInRec(const tSDP_DISC_REC* p_rec,
                              bluetooth::Uuid* p_uuid);

/*******************************************************************************
 *
 * Function         SDP_FindServiceInDb_128bit
 *
 * Description      Query an SDP database for a specific service.
 *                  If the p_start_rec pointer is NULL, look from the beginning
 *                  of the database, else continue from the next record after
 *                  p_start_rec.
 *
 * Returns          Pointer to record containing service class, or NULL
 *
 ******************************************************************************/
tSDP_DISC_REC* SDP_FindServiceInDb_128bit(const tSDP_DISCOVERY_DB* p_db,
                                          tSDP_DISC_REC* p_start_rec);

/*******************************************************************************
 *
 * Function         SDP_FindProtocolListElemInRec
 *
 * Description      This function looks at a specific discovery record for a
 *                  protocol list element.
 *
 * Returns          true if found, false if not
 *                  If found, the passed protocol list element is filled in.
 *
 ******************************************************************************/
bool SDP_FindProtocolListElemInRec(const tSDP_DISC_REC* p_rec,
                                   uint16_t layer_uuid,
                                   tSDP_PROTOCOL_ELEM* p_elem);

/*******************************************************************************
 *
 * Function         SDP_FindProfileVersionInRec
 *
 * Description      This function looks at a specific discovery record for the
 *                  Profile list descriptor, and pulls out the version number.
 *                  The version number consists of an 8-bit major version and
 *                  an 8-bit minor version.
 *
 * Returns          true if found, false if not
 *                  If found, the major and minor version numbers that were
 *                  passed in are filled in.
 *
 ******************************************************************************/
bool SDP_FindProfileVersionInRec(const tSDP_DISC_REC* p_rec,
                                 uint16_t profile_uuid, uint16_t* p_version);

/* API into SDP for local service database updates */

/*******************************************************************************
 *
 * Function         SDP_CreateRecord
 *
 * Description      This function is called to create a record in the database.
 *                  This would be through the SDP database maintenance API. The
 *                  record is created empty, the application should then call
 *                  "add_attribute" to add the record's attributes.
 *
 * Returns          Record handle if OK, else 0.
 *
 ******************************************************************************/
uint32_t SDP_CreateRecord(void);

/*******************************************************************************
 *
 * Function         SDP_DeleteRecord
 *
 * Description      This function is called to add a record (or all records)
 *                  from the database. This would be through the SDP database
 *                  maintenance API.
 *
 *                  If a record handle of 0 is passed, all records are deleted.
 *
 * Returns          true if succeeded, else false
 *
 ******************************************************************************/
bool SDP_DeleteRecord(uint32_t handle);

/*******************************************************************************
 *
 * Function         SDP_AddAttribute
 *
 * Description      This function is called to add an attribute to a record.
 *                  This would be through the SDP database maintenance API.
 *                  If the attribute already exists in the record, it is
 *                  replaced with the new value.
 *
 * NOTE             Attribute values must be passed as a Big Endian stream.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddAttribute(uint32_t handle, uint16_t attr_id, uint8_t attr_type,
                      uint32_t attr_len, uint8_t* p_val);

/*******************************************************************************
 *
 * Function         SDP_AddSequence
 *
 * Description      This function is called to add a sequence to a record.
 *                  This would be through the SDP database maintenance API.
 *                  If the sequence already exists in the record, it is replaced
 *                  with the new sequence.
 *
 * NOTE             Element values must be passed as a Big Endian stream.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddSequence(uint32_t handle, uint16_t attr_id, uint16_t num_elem,
                     uint8_t type[], uint8_t len[], uint8_t* p_val[]);

/*******************************************************************************
 *
 * Function         SDP_AddUuidSequence
 *
 * Description      This function is called to add a UUID sequence to a record.
 *                  This would be through the SDP database maintenance API.
 *                  If the sequence already exists in the record, it is replaced
 *                  with the new sequence.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddUuidSequence(uint32_t handle, uint16_t attr_id, uint16_t num_uuids,
                         uint16_t* p_uuids);

/*******************************************************************************
 *
 * Function         SDP_AddProtocolList
 *
 * Description      This function is called to add a protocol descriptor list to
 *                  a record. This would be through the SDP database
 *                  maintenance API. If the protocol list already exists in the
 *                  record, it is replaced with the new list.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddProtocolList(uint32_t handle, uint16_t num_elem,
                         tSDP_PROTOCOL_ELEM* p_elem_list);

/*******************************************************************************
 *
 * Function         SDP_AddAdditionProtoLists
 *
 * Description      This function is called to add a protocol descriptor list to
 *                  a record. This would be through the SDP database maintenance
 *                  API. If the protocol list already exists in the record, it
 *                  is replaced with the new list.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddAdditionProtoLists(uint32_t handle, uint16_t num_elem,
                               tSDP_PROTO_LIST_ELEM* p_proto_list);

/*******************************************************************************
 *
 * Function         SDP_AddProfileDescriptorList
 *
 * Description      This function is called to add a profile descriptor list to
 *                  a record. This would be through the SDP database maintenance
 *                  API. If the version already exists in the record, it is
 *                  replaced with the new one.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddProfileDescriptorList(uint32_t handle, uint16_t profile_uuid,
                                  uint16_t version);

/*******************************************************************************
 *
 * Function         SDP_AddLanguageBaseAttrIDList
 *
 * Description      This function is called to add a language base attr list to
 *                  a record. This would be through the SDP database maintenance
 *                  API. If the version already exists in the record, it is
 *                  replaced with the new one.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddLanguageBaseAttrIDList(uint32_t handle, uint16_t lang,
                                   uint16_t char_enc, uint16_t base_id);

/*******************************************************************************
 *
 * Function         SDP_AddServiceClassIdList
 *
 * Description      This function is called to add a service list to a record.
 *                  This would be through the SDP database maintenance API.
 *                  If the service list already exists in the record, it is
 *                  replaced with the new list.
 *
 * Returns          true if added OK, else false
 *
 ******************************************************************************/
bool SDP_AddServiceClassIdList(uint32_t handle, uint16_t num_services,
                               uint16_t* p_service_uuids);

/* Device Identification APIs */

/*******************************************************************************
 *
 * Function         SDP_SetLocalDiRecord
 *
 * Description      This function adds a DI record to the local SDP database.
 *
 * Returns          Returns SDP_SUCCESS if record added successfully, else error
 *
 ******************************************************************************/
uint16_t SDP_SetLocalDiRecord(const tSDP_DI_RECORD* device_info,
                              uint32_t* p_handle);

/*******************************************************************************
 *
 * Function         SDP_DiDiscover
 *
 * Description      This function queries a remote device for DI information.
 *
 * Returns          SDP_SUCCESS if query started successfully, else error
 *
 ******************************************************************************/
tSDP_STATUS SDP_DiDiscover(const RawAddress& remote_device,
                           tSDP_DISCOVERY_DB* p_db, uint32_t len,
                           tSDP_DISC_CMPL_CB* p_cb);

/*******************************************************************************
 *
 * Function         SDP_GetNumDiRecords
 *
 * Description      Searches specified database for DI records
 *
 * Returns          number of DI records found
 *
 ******************************************************************************/
uint8_t SDP_GetNumDiRecords(const tSDP_DISCOVERY_DB* p_db);

/*******************************************************************************
 *
 * Function         SDP_GetDiRecord
 *
 * Description      This function retrieves a remote device's DI record from
 *                  the specified database.
 *
 * Returns          SDP_SUCCESS if record retrieved, else error
 *
 ******************************************************************************/
uint16_t SDP_GetDiRecord(uint8_t getRecordIndex,
                         tSDP_DI_GET_RECORD* device_info,
                         const tSDP_DISCOVERY_DB* p_db);
