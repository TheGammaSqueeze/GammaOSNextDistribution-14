/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <cstdint>

#include "stack/include/btm_status.h"
#include "types/raw_address.h"

// Note: From include/btm_api_types.h

/*****************************************************************************
 *  ACL CHANNEL MANAGEMENT
 ****************************************************************************/
/******************
 *  ACL Constants
 ******************/
/***************
 *  ACL Types
 ***************/

/* Structure returned with read RSSI event (in tBTM_CMPL_CB callback function)
 * in response to BTM_ReadRSSI call.
 */
typedef struct {
  tBTM_STATUS status;
  uint8_t hci_status;
  RawAddress rem_bda;
  int8_t rssi;
} tBTM_RSSI_RESULT;

/* Structure returned with read failed contact counter event
 * (in tBTM_CMPL_CB callback function) in response to
 * BTM_ReadFailedContactCounter call.
 */
typedef struct {
  tBTM_STATUS status;
  uint8_t hci_status;
  RawAddress rem_bda;
  uint16_t failed_contact_counter;
} tBTM_FAILED_CONTACT_COUNTER_RESULT;

/* Structure returned with read automatic flush timeout event
 * (in tBTM_CMPL_CB callback function) in response to
 * BTM_ReadAutomaticFlushTimeout call.
 */
typedef struct {
  tBTM_STATUS status;
  uint8_t hci_status;
  RawAddress rem_bda;
  uint16_t automatic_flush_timeout;
} tBTM_AUTOMATIC_FLUSH_TIMEOUT_RESULT;

/* Structure returned with read current TX power event (in tBTM_CMPL_CB callback
 * function) in response to BTM_ReadTxPower call.
 */
typedef struct {
  tBTM_STATUS status;
  uint8_t hci_status;
  RawAddress rem_bda;
  int8_t tx_power;
} tBTM_TX_POWER_RESULT;

/* Structure returned with read link quality event (in tBTM_CMPL_CB callback
 * function) in response to BTM_ReadLinkQuality call.
 */
typedef struct {
  tBTM_STATUS status;
  uint8_t hci_status;
  RawAddress rem_bda;
  uint8_t link_quality;
} tBTM_LINK_QUALITY_RESULT;

enum class tBTM_INQUIRY_STATE : uint8_t {
  BTM_INQUIRY_STARTED = 1,
  BTM_INQUIRY_CANCELLED = 2,
  BTM_INQUIRY_COMPLETE = 3,
};
