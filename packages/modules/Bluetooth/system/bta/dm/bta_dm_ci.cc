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
 *  This is the API implementation file for the BTA device manager.
 *
 ******************************************************************************/
#include <base/functional/bind.h>

#include <memory>

#include "bta/dm/bta_dm_sec_int.h"
#include "stack/include/main_thread.h"
#include "types/raw_address.h"

/*******************************************************************************
 *
 * Function         bta_dm_ci_rmt_oob
 *
 * Description      This function must be called in response to function
 *                  bta_dm_co_rmt_oob() to provide the OOB data associated
 *                  with the remote device.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_dm_ci_rmt_oob(bool accept, const RawAddress& bd_addr, const Octet16& c,
                       const Octet16& r) {
  std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg =
      std::make_unique<tBTA_DM_CI_RMT_OOB>();

  msg->bd_addr = bd_addr;
  msg->accept = accept;
  msg->c = c;
  msg->r = r;

  do_in_main_thread(FROM_HERE,
                    base::Bind(bta_dm_ci_rmt_oob_act, base::Passed(&msg)));
}

