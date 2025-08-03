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
 *  This is the main implementation file for the BTA device manager.
 *
 ******************************************************************************/
#include <base/strings/stringprintf.h>
#include <stddef.h>

#include "bta/dm/bta_dm_disc.h"
#include "bta/dm/bta_dm_gatt_client.h"
#include "bta/dm/bta_dm_int.h"
#include "bta/dm/bta_dm_sec_int.h"
#include "main/shim/dumpsys.h"

tBTA_DM_ACL_CB bta_dm_acl_cb;
tBTA_DM_CB bta_dm_cb;
tBTA_DM_DI_CB bta_dm_di_cb;

tBTA_DM_SEC_CB bta_dm_sec_cb;

#define DUMPSYS_TAG "shim::legacy::bta::dm"
void DumpsysBtaDm(int fd) {
  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);
  DumpsysBtaDmDisc(fd);
  DumpsysBtaDmGattClient(fd);
}
#undef DUMPSYS_TAG
