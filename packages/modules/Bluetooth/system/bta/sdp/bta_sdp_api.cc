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
 *
 *  This is the implementation of the API for SDP search subsystem
 *
 ******************************************************************************/

#include "bta/include/bta_sdp_api.h"

#include <base/functional/bind.h>
#include <base/location.h>

#include "bta/sdp/bta_sdp_int.h"
#include "internal_include/bt_target.h"
#include "stack/include/main_thread.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants
 ****************************************************************************/

/*******************************************************************************
 *
 * Function         BTA_SdpEnable
 *
 * Description      Enable the SDP search I/F service. When the enable
 *                  operation is complete the callback function will be
 *                  called with a BTA_SDP_ENABLE_EVT. This function must
 *                  be called before other functions in the SDP search API are
 *                  called.
 *
 * Returns          BTA_SDP_SUCCESS if successful.
 *                  BTA_SDP_FAIL if internal failure.
 *
 ******************************************************************************/
tBTA_SDP_STATUS BTA_SdpEnable(tBTA_SDP_DM_CBACK* p_cback) {
  if (!p_cback) {
    return BTA_SDP_FAILURE;
  }

  memset(&bta_sdp_cb, 0, sizeof(tBTA_SDP_CB));
  do_in_main_thread(FROM_HERE, base::BindOnce(bta_sdp_enable, p_cback));
  return BTA_SDP_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTA_SdpSearch
 *
 * Description      This function performs service discovery for a specific
 *                  service on given peer device. When the operation is
 *                  completed the tBTA_SDP_DM_CBACK callback function will be
 *                  called with a BTA_SDP_SEARCH_COMPLETE_EVT.
 *
 * Returns          BTA_SDP_SUCCESS, if the request is being processed.
 *                  BTA_SDP_FAILURE, otherwise.
 *
 ******************************************************************************/
tBTA_SDP_STATUS BTA_SdpSearch(const RawAddress& bd_addr,
                              const bluetooth::Uuid& uuid) {
  do_in_main_thread(FROM_HERE, base::BindOnce(bta_sdp_search, bd_addr, uuid));
  return BTA_SDP_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTA_SdpCreateRecordByUser
 *
 * Description      This function is used to request a callback to create a SDP
 *                  record. The registered callback will be called with event
 *                  BTA_SDP_CREATE_RECORD_USER_EVT.
 *
 * Returns          BTA_SDP_SUCCESS, if the request is being processed.
 *                  BTA_SDP_FAILURE, otherwise.
 *
 ******************************************************************************/
tBTA_SDP_STATUS BTA_SdpCreateRecordByUser(void* user_data) {
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(bta_sdp_create_record, user_data));
  return BTA_SDP_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTA_SdpRemoveRecordByUser
 *
 * Description      This function is used to request a callback to remove a SDP
 *                  record. The registered callback will be called with event
 *                  BTA_SDP_REMOVE_RECORD_USER_EVT.
 *
 * Returns          BTA_SDP_SUCCESS, if the request is being processed.
 *                  BTA_SDP_FAILURE, otherwise.
 *
 ******************************************************************************/
tBTA_SDP_STATUS BTA_SdpRemoveRecordByUser(void* user_data) {
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(bta_sdp_remove_record, user_data));
  return BTA_SDP_SUCCESS;
}
