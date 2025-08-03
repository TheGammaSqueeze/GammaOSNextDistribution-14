/******************************************************************************
 *
 *  Copyright (c) 2014 The Android Open Source Project
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
 *  This is the implementation of the API for the handsfree (HF role)
 *  subsystem of BTA
 *
 ******************************************************************************/

#include "bta/include/bta_hf_client_api.h"

#include <android_bluetooth_sysprop.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/sys/bta_sys.h"
#include "internal_include/bt_trace.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

using namespace bluetooth;

/*****************************************************************************
 *  External Function Declarations
 ****************************************************************************/

/*******************************************************************************
 *
 * Function         BTA_HfClientEnable
 *
 * Description      Enable the HF CLient service. It does the following:
 *                  1. Sets the state to initialized (control blocks)
 *                  2. Starts the SDP for the client role (HF)
 *                  3. Starts the RFCOMM server to accept incoming connections
 *                  The function is synchronous and returns with an error code
 *                  if anything went wrong. This should be the first call to the
 *                  API before doing an BTA_HfClientOpen
 *
 * Returns          BTA_SUCCESS if OK, BTA_FAILURE otherwise.
 *
 ******************************************************************************/
tBTA_STATUS BTA_HfClientEnable(tBTA_HF_CLIENT_CBACK* p_cback,
                               tBTA_HF_CLIENT_FEAT features,
                               const char* p_service_name) {
  return bta_hf_client_api_enable(p_cback, features, p_service_name);
}

/*******************************************************************************
 *
 * Function         BTA_HfClientDisable
 *
 * Description      Disable the HF Client service
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_HfClientDisable(void) { bta_hf_client_api_disable(); }

/*******************************************************************************
 *
 * Function         BTA_HfClientOpen
 *
 * Description      Opens up a RF connection to the remote device and
 *                  subsequently set it up for a HF SLC
 *
 * Returns          bt_status_t
 *
 ******************************************************************************/
bt_status_t BTA_HfClientOpen(const RawAddress& bd_addr, uint16_t* p_handle) {
  log::verbose("");
  tBTA_HF_CLIENT_API_OPEN* p_buf =
      (tBTA_HF_CLIENT_API_OPEN*)osi_malloc(sizeof(tBTA_HF_CLIENT_API_OPEN));

  if (!bta_hf_client_allocate_handle(bd_addr, p_handle)) {
    log::error("could not allocate handle");
    return BT_STATUS_FAIL;
  }

  p_buf->hdr.event = BTA_HF_CLIENT_API_OPEN_EVT;
  p_buf->hdr.layer_specific = *p_handle;
  p_buf->bd_addr = bd_addr;

  bta_sys_sendmsg(p_buf);
  return BT_STATUS_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BTA_HfClientClose
 *
 * Description      Close the current connection to an audio gateway.
 *                  Any current audio connection will also be closed
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_HfClientClose(uint16_t handle) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  p_buf->event = BTA_HF_CLIENT_API_CLOSE_EVT;
  p_buf->layer_specific = handle;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_HfCllientAudioOpen
 *
 * Description      Opens an audio connection to the currently connected
 *                 audio gateway
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_HfClientAudioOpen(uint16_t handle) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  p_buf->event = BTA_HF_CLIENT_API_AUDIO_OPEN_EVT;
  p_buf->layer_specific = handle;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_HfClientAudioClose
 *
 * Description      Close the currently active audio connection to an audio
 *                  gateway. The data connection remains open
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_HfClientAudioClose(uint16_t handle) {
  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));

  p_buf->event = BTA_HF_CLIENT_API_AUDIO_CLOSE_EVT;
  p_buf->layer_specific = handle;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_HfClientSendAT
 *
 * Description      send AT command
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BTA_HfClientSendAT(uint16_t handle, tBTA_HF_CLIENT_AT_CMD_TYPE at,
                        uint32_t val1, uint32_t val2, const char* str) {
  tBTA_HF_CLIENT_DATA_VAL* p_buf =
      (tBTA_HF_CLIENT_DATA_VAL*)osi_malloc(sizeof(tBTA_HF_CLIENT_DATA_VAL));

  p_buf->hdr.event = BTA_HF_CLIENT_SEND_AT_CMD_EVT;
  p_buf->uint8_val = at;
  p_buf->uint32_val1 = val1;
  p_buf->uint32_val2 = val2;

  if (str) {
    strlcpy(p_buf->str, str, BTA_HF_CLIENT_NUMBER_LEN + 1);
    p_buf->str[BTA_HF_CLIENT_NUMBER_LEN] = '\0';
  } else {
    p_buf->str[0] = '\0';
  }

  p_buf->hdr.layer_specific = handle;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         BTA_HfClientDumpStatistics
 *
 * Description      Dump statistics about the various control blocks
 *                  and other relevant connection statistics
 *
 * Returns          Void
 *
 ******************************************************************************/
void BTA_HfClientDumpStatistics(int fd) { bta_hf_client_dump_statistics(fd); }

/*******************************************************************************
 *
 * function         get_default_hf_client_features
 *
 * description      return the hf_client features.
 *                  value can be override via system property
 *
 * returns          int
 *
 ******************************************************************************/
int get_default_hf_client_features() {
#define DEFAULT_BTIF_HF_CLIENT_FEATURES                                        \
  (BTA_HF_CLIENT_FEAT_ECNR | BTA_HF_CLIENT_FEAT_3WAY |                         \
   BTA_HF_CLIENT_FEAT_CLI | BTA_HF_CLIENT_FEAT_VREC | BTA_HF_CLIENT_FEAT_VOL | \
   BTA_HF_CLIENT_FEAT_ECS | BTA_HF_CLIENT_FEAT_ECC | BTA_HF_CLIENT_FEAT_CODEC)

  return GET_SYSPROP(Hfp, hf_client_features, DEFAULT_BTIF_HF_CLIENT_FEATURES);
}
