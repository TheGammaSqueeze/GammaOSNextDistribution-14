/*
 * Copyright 2020 HIMSA II K/S - www.himsa.dk.
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

#include "types/bt_transport.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace manager {

class BtmApiInterface {
 public:
  virtual bool SetSecurityLevel(bool is_originator, const char* p_name,
                                uint8_t service_id, uint16_t sec_level,
                                uint16_t psm, uint32_t mx_proto_id,
                                uint32_t mx_chan_id) = 0;
  virtual uint8_t acl_link_role(const RawAddress& remote_bd_addr,
                                tBT_TRANSPORT transport) = 0;
  virtual bool IsEncrypted(const RawAddress& remote_bd_addr,
                           tBT_TRANSPORT transport) = 0;
  virtual bool IsLinkKeyKnown(const RawAddress& remote_bd_addr,
                              tBT_TRANSPORT transport) = 0;
  virtual uint8_t ReadSecKeySize(const RawAddress& remote_bd_addr) = 0;
  virtual ~BtmApiInterface() = default;
};

class MockBtmApiInterface : public BtmApiInterface {
 public:
  MOCK_METHOD7(SetSecurityLevel,
               bool(bool is_originator, const char* p_name, uint8_t service_id,
                    uint16_t sec_level, uint16_t psm, uint32_t mx_proto_id,
                    uint32_t mx_chan_id));
  MOCK_METHOD2(acl_link_role, uint8_t(const RawAddress& remote_bd_addr,
                                      tBT_TRANSPORT transport));
  MOCK_METHOD2(IsEncrypted,
               bool(const RawAddress& remote_bd_addr, tBT_TRANSPORT transport));
  MOCK_METHOD2(IsLinkKeyKnown,
               bool(const RawAddress& remote_bd_addr, tBT_TRANSPORT transport));
  MOCK_METHOD1(ReadSecKeySize, uint8_t(const RawAddress& remote_bd_addr));
};

/**
 * Set the {@link MockBtmApiInterface} for testing
 *
 * @param mock_btm_api_interface pointer to mock btm interface, could be null
 */
void SetMockBtmApiInterface(MockBtmApiInterface* mock_btm_interface);

}  // namespace manager
}  // namespace bluetooth
