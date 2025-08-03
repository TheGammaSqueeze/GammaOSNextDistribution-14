/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <gmock/gmock.h>

#include <optional>

#include "bt_octets.h"
#include "btm_api.h"
#include "stack/btm/security_device_record.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace manager {

class BtmInterface {
 public:
  virtual bool GetSecurityFlagsByTransport(const RawAddress& bd_addr,
                                           uint8_t* p_sec_flags,
                                           tBT_TRANSPORT transport) = 0;
  virtual bool IsLinkKeyKnown(const RawAddress& bd_addr,
                              tBT_TRANSPORT transport) = 0;
  virtual bool BTM_IsEncrypted(const RawAddress& bd_addr,
                               tBT_TRANSPORT transport) = 0;
  virtual tBTM_STATUS SetEncryption(const RawAddress& bd_addr,
                                    tBT_TRANSPORT transport,
                                    tBTM_SEC_CALLBACK* p_callback,
                                    void* p_ref_data,
                                    tBTM_BLE_SEC_ACT sec_act) = 0;
  virtual tBTM_SEC_DEV_REC* FindDevice(const RawAddress& bd_addr) = 0;
  virtual bool IsPhy2mSupported(const RawAddress& remote_bda,
                                tBT_TRANSPORT transport) = 0;
  virtual uint8_t GetPeerSCA(const RawAddress& remote_bda,
                             tBT_TRANSPORT transport) = 0;
  virtual void BleSetPhy(const RawAddress& bd_addr, uint8_t tx_phys,
                         uint8_t rx_phys, uint16_t phy_options) = 0;
  virtual bool SecIsSecurityPending(const RawAddress& bd_addr) = 0;
  virtual void RequestPeerSCA(RawAddress const& bd_addr,
                              tBT_TRANSPORT transport) = 0;
  virtual uint16_t GetHCIConnHandle(RawAddress const& bd_addr,
                                    tBT_TRANSPORT transport) = 0;
  virtual void AclDisconnectFromHandle(uint16_t handle, tHCI_STATUS reason) = 0;
  virtual tBTM_INQ_INFO* BTM_InqDbFirst() = 0;
  virtual tBTM_INQ_INFO* BTM_InqDbNext(tBTM_INQ_INFO* p_cur) = 0;
  virtual std::optional<Octet16> BTM_BleGetPeerLTK(
      const RawAddress address) = 0;
  virtual std::optional<Octet16> BTM_BleGetPeerIRK(
      const RawAddress address) = 0;

  virtual bool BTM_BleIsLinkKeyKnown(const RawAddress address) = 0;
  virtual std::optional<tBLE_BD_ADDR> BTM_BleGetIdentityAddress(
      const RawAddress address) = 0;

  virtual ~BtmInterface() = default;
};

class MockBtmInterface : public BtmInterface {
 public:
  MOCK_METHOD((bool), GetSecurityFlagsByTransport,
              (const RawAddress& bd_addr, uint8_t* p_sec_flags,
               tBT_TRANSPORT transport),
              (override));
  MOCK_METHOD((bool), IsLinkKeyKnown,
              (const RawAddress& bd_addr, tBT_TRANSPORT transport), (override));
  MOCK_METHOD((bool), BTM_IsEncrypted,
              (const RawAddress& bd_addr, tBT_TRANSPORT transport), (override));
  MOCK_METHOD((tBTM_STATUS), SetEncryption,
              (const RawAddress& bd_addr, tBT_TRANSPORT transport,
               tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
               tBTM_BLE_SEC_ACT sec_act),
              (override));
  MOCK_METHOD((tBTM_SEC_DEV_REC*), FindDevice, (const RawAddress& bd_addr),
              (override));
  MOCK_METHOD((bool), IsPhy2mSupported,
              (const RawAddress& remote_bda, tBT_TRANSPORT transport),
              (override));
  MOCK_METHOD((uint8_t), GetPeerSCA,
              (const RawAddress& remote_bda, tBT_TRANSPORT transport),
              (override));
  MOCK_METHOD((void), BleSetPhy,
              (const RawAddress& bd_addr, uint8_t tx_phys, uint8_t rx_phys,
               uint16_t phy_options),
              (override));
  MOCK_METHOD((bool), SecIsSecurityPending, (const RawAddress& bd_addr),
              (override));
  MOCK_METHOD((void), RequestPeerSCA,
              (RawAddress const& bd_addr, tBT_TRANSPORT transport), (override));
  MOCK_METHOD((uint16_t), GetHCIConnHandle,
              (RawAddress const& bd_addr, tBT_TRANSPORT transport), (override));
  MOCK_METHOD((void), AclDisconnectFromHandle,
              (uint16_t handle, tHCI_STATUS reason), (override));
  MOCK_METHOD((tBTM_INQ_INFO*), BTM_InqDbFirst, (), (override));
  MOCK_METHOD((tBTM_INQ_INFO*), BTM_InqDbNext, (tBTM_INQ_INFO * p_cur),
              (override));
  MOCK_METHOD((std::optional<Octet16>), BTM_BleGetPeerLTK,
              (const RawAddress address), (override));
  MOCK_METHOD((std::optional<Octet16>), BTM_BleGetPeerIRK,
              (const RawAddress address), (override));

  MOCK_METHOD((bool), BTM_BleIsLinkKeyKnown, (const RawAddress address),
              (override));
  MOCK_METHOD((std::optional<tBLE_BD_ADDR>), BTM_BleGetIdentityAddress,
              (const RawAddress address), (override));
};

/**
 * Set the {@link MockBtmInterface} for testing
 *
 * @param mock_btm_interface pointer to mock btm interface, could be null
 */
void SetMockBtmInterface(MockBtmInterface* mock_btm_interface);

}  // namespace manager
}  // namespace bluetooth
