/*
 * Copyright 2023 The Android Open Source Project
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
 */

#define LOG_TAG "sec_interf"

#include <bluetooth/log.h>

#include "os/log.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sec.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/include/btm_ble_sec_api.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/security_client_callbacks.h"
#include "types/bt_transport.h"

using namespace bluetooth;

static void BTM_SecConfirmReqReply(tBTM_STATUS res, tBT_TRANSPORT transport,
                                   const RawAddress bd_addr) {
  if (transport == BT_TRANSPORT_BR_EDR) {
    BTM_ConfirmReqReply(res, bd_addr);
  } else if (transport == BT_TRANSPORT_LE) {
    BTM_BleConfirmReply(bd_addr, res);
  } else {
    log::error("Unexpected transport:{}", transport);
  }
}

static SecurityClientInterface security = {
    .BTM_Sec_Init = BTM_Sec_Init,
    .BTM_Sec_Free = BTM_Sec_Free,
    .BTM_SecRegister = BTM_SecRegister,

    .BTM_BleLoadLocalKeys = BTM_BleLoadLocalKeys,

    .BTM_SecAddDevice = BTM_SecAddDevice,
    .BTM_SecAddBleDevice = BTM_SecAddBleDevice,
    .BTM_SecDeleteDevice = BTM_SecDeleteDevice,
    .BTM_SecAddBleKey = BTM_SecAddBleKey,
    .BTM_SecClearSecurityFlags = BTM_SecClearSecurityFlags,
    .BTM_SetEncryption = BTM_SetEncryption,
    .BTM_IsEncrypted = BTM_IsEncrypted,
    .BTM_SecIsSecurityPending = BTM_SecIsSecurityPending,
    .BTM_IsLinkKeyKnown = BTM_IsLinkKeyKnown,

    .BTM_SetSecurityLevel = BTM_SetSecurityLevel,
    .BTM_SecClrService = BTM_SecClrService,
    .BTM_SecClrServiceByPsm = BTM_SecClrServiceByPsm,

    .BTM_SecBond = BTM_SecBond,
    .BTM_SecBondCancel = BTM_SecBondCancel,
    .BTM_RemoteOobDataReply = BTM_RemoteOobDataReply,
    .BTM_PINCodeReply = BTM_PINCodeReply,
    .BTM_SecConfirmReqReply = BTM_SecConfirmReqReply,
    .BTM_BleSirkConfirmDeviceReply = BTM_BleSirkConfirmDeviceReply,
    .BTM_BlePasskeyReply = BTM_BlePasskeyReply,

    .BTM_GetSecurityMode = BTM_GetSecurityMode,

    .BTM_SecReadDevName = BTM_SecReadDevName,
    .BTM_SecAddRmtNameNotifyCallback = BTM_SecAddRmtNameNotifyCallback,
    .BTM_SecDeleteRmtNameNotifyCallback = BTM_SecDeleteRmtNameNotifyCallback,
};

const SecurityClientInterface& get_security_client_interface() {
  return security;
}
