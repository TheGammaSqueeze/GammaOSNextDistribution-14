/******************************************************************************
 *
 *  Copyright 2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
#include "NfcExtns.h"

#include "phNfcStatus.h"
#include "phNxpConfig.h"
#include "phNxpNciHal_extOperations.h"

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {

void NfcExtns::getConfig(NfcConfig& config) {
  unsigned long num = 0;
  std::array<uint8_t, NXP_MAX_CONFIG_STRING_LEN> buffer;
  buffer.fill(0);
  long retlen = 0;
  memset(&config, 0x00, sizeof(NfcConfig));

  phNxpNciHal_getExtVendorConfig();

  if (GetNxpNumValue(NAME_NFA_POLL_BAIL_OUT_MODE, &num, sizeof(num))) {
    config.nfaPollBailOutMode = (bool)num;
  }
  if (GetNxpNumValue(NAME_ISO_DEP_MAX_TRANSCEIVE, &num, sizeof(num))) {
    config.maxIsoDepTransceiveLength = (uint32_t)num;
  }
  if (GetNxpNumValue(NAME_DEFAULT_OFFHOST_ROUTE, &num, sizeof(num))) {
    config.defaultOffHostRoute = (uint8_t)num;
  }
  if (GetNxpNumValue(NAME_DEFAULT_NFCF_ROUTE, &num, sizeof(num))) {
    config.defaultOffHostRouteFelica = (uint8_t)num;
  }
  if (GetNxpNumValue(NAME_DEFAULT_SYS_CODE_ROUTE, &num, sizeof(num))) {
    config.defaultSystemCodeRoute = (uint8_t)num;
  }
  if (GetNxpNumValue(NAME_DEFAULT_SYS_CODE_PWR_STATE, &num, sizeof(num))) {
    config.defaultSystemCodePowerState =
        phNxpNciHal_updateAutonomousPwrState((uint8_t)num);
  }
  if (GetNxpNumValue(NAME_DEFAULT_ROUTE, &num, sizeof(num))) {
    config.defaultRoute = (uint8_t)num;
  }
  if (GetNxpByteArrayValue(NAME_DEVICE_HOST_ALLOW_LIST, (char*)buffer.data(),
                           buffer.size(), &retlen)) {
    config.hostAllowlist.resize(retlen);
    for (long i = 0; i < retlen; i++) config.hostAllowlist[i] = buffer[i];
  }
  if (GetNxpNumValue(NAME_OFF_HOST_ESE_PIPE_ID, &num, sizeof(num))) {
    config.offHostESEPipeId = (uint8_t)num;
  }
  if (GetNxpNumValue(NAME_OFF_HOST_SIM_PIPE_ID, &num, sizeof(num))) {
    config.offHostSIMPipeId = (uint8_t)num;
  }
  if (GetNxpNumValue(NAME_DEFAULT_ISODEP_ROUTE, &num, sizeof(num))) {
    config.defaultIsoDepRoute = (uint8_t)num;
  }
  if (GetNxpByteArrayValue(NAME_OFFHOST_ROUTE_UICC, (char*)buffer.data(),
                           buffer.size(), &retlen)) {
    config.offHostRouteUicc.resize(retlen);
    for (long i = 0; i < retlen; i++) config.offHostRouteUicc[i] = buffer[i];
  }

  if (GetNxpByteArrayValue(NAME_OFFHOST_ROUTE_ESE, (char*)buffer.data(),
                           buffer.size(), &retlen)) {
    config.offHostRouteEse.resize(retlen);
    for (long i = 0; i < retlen; i++) config.offHostRouteEse[i] = buffer[i];
  }
  if ((GetNxpByteArrayValue(NAME_NFA_PROPRIETARY_CFG, (char*)buffer.data(),
                            buffer.size(), &retlen)) &&
      (retlen == 9)) {
    config.nfaProprietaryCfg.protocol18092Active = (uint8_t)buffer[0];
    config.nfaProprietaryCfg.protocolBPrime = (uint8_t)buffer[1];
    config.nfaProprietaryCfg.protocolDual = (uint8_t)buffer[2];
    config.nfaProprietaryCfg.protocol15693 = (uint8_t)buffer[3];
    config.nfaProprietaryCfg.protocolKovio = (uint8_t)buffer[4];
    config.nfaProprietaryCfg.protocolMifare = (uint8_t)buffer[5];
    config.nfaProprietaryCfg.discoveryPollKovio = (uint8_t)buffer[6];
    config.nfaProprietaryCfg.discoveryPollBPrime = (uint8_t)buffer[7];
    config.nfaProprietaryCfg.discoveryListenBPrime = (uint8_t)buffer[8];
  } else {
    memset(&config.nfaProprietaryCfg, 0xFF, sizeof(ProtocolDiscoveryConfig));
  }
  if ((GetNxpNumValue(NAME_PRESENCE_CHECK_ALGORITHM, &num, sizeof(num))) &&
      (num <= 2)) {
    config.presenceCheckAlgorithm = (PresenceCheckAlgorithm)num;
  }
}

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl