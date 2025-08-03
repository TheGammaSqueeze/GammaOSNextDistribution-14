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

#include "bta/include/bta_rfcomm_scn.h"

#define LOG_TAG "bta"

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/jv/bta_jv_int.h"      // tBTA_JV_CB
#include "stack/include/rfcdefs.h"  // RFCOMM_MAX_SCN

using namespace bluetooth;

extern tBTA_JV_CB bta_jv_cb;

/*******************************************************************************
 *
 * Function         BTM_AllocateSCN
 *
 * Description      Look through the Server Channel Numbers for a free one.
 *
 * Returns          Allocated SCN number or 0 if none.
 *
 ******************************************************************************/
uint8_t BTA_AllocateSCN(void) {
  // SCN can be allocated in the range of [1, RFCOMM_MAX_SCN]
  // btm_scn uses indexes 0 to RFCOMM_MAX_SCN-1 to track RFC ports
  for (uint8_t i = bta_jv_cb.scn_search_index; i < RFCOMM_MAX_SCN; ++i) {
    if (!bta_jv_cb.scn_in_use[i]) {
      bta_jv_cb.scn_in_use[i] = true;
      bta_jv_cb.scn_search_index = (i + 1);
      log::debug("Allocating scn: {}", i + 1);
      return (i + 1);  // allocated scn is index + 1
    }
  }

  // In order to avoid OOB, btm_available_index must be no more than
  // RFCOMM_MAX_SCN.
  bta_jv_cb.scn_search_index =
      std::min(bta_jv_cb.scn_search_index, (uint8_t)(RFCOMM_MAX_SCN));

  // Start from index 1 because index 0 (scn 1) is reserved for HFP
  // If there's no empty SCN from _last_index to BTM_MAX_SCN.
  for (uint8_t i = 1; i < bta_jv_cb.scn_search_index; ++i) {
    if (!bta_jv_cb.scn_in_use[i]) {
      bta_jv_cb.scn_in_use[i] = true;
      bta_jv_cb.scn_search_index = (i + 1);
      log::debug("Allocating scn: {}", i + 1);
      return (i + 1);  // allocated scn is index + 1
    }
  }
  log::debug("Unable to allocate an scn");
  return (0); /* No free ports */
}

/*******************************************************************************
 *
 * Function         BTM_TryAllocateSCN
 *
 * Description      Try to allocate a fixed server channel
 *
 * Returns          Returns true if server channel was available
 *
 ******************************************************************************/

bool BTA_TryAllocateSCN(uint8_t scn) {
  /* Make sure we don't exceed max scn range.
   * Stack reserves scn 1 for HFP and HSP
   */
  if ((scn > RFCOMM_MAX_SCN) || (scn == 1) || (scn == 0)) return false;

  /* check if this scn is available */
  if (!bta_jv_cb.scn_in_use[scn - 1]) {
    bta_jv_cb.scn_in_use[scn - 1] = true;
    log::debug("Allocating scn: {}", scn);
    return true;
  }
  log::debug("Unable to allocate scn {}", scn);
  return (false); /* scn was busy */
}

/*******************************************************************************
 *
 * Function         BTM_FreeSCN
 *
 * Description      Free the specified SCN.
 *
 * Returns          true or false
 *
 ******************************************************************************/
bool BTA_FreeSCN(uint8_t scn) {
  /* Since this isn't used by HFP, this function will only free valid SCNs
   * that aren't reserved for HFP, which is range [2, RFCOMM_MAX_SCN].
   */
  if (scn < RFCOMM_MAX_SCN && scn > 1) {
    bta_jv_cb.scn_in_use[scn - 1] = false;
    log::debug("Freed SCN: {}", scn);
    return (true);
  } else {
    log::warn("Invalid SCN: {}", scn);
    return (false); /* Illegal SCN passed in */
  }
}
