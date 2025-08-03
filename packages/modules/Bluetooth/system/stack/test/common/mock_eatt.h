/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

#include "stack/eatt/eatt.h"
#include "types/raw_address.h"

using bluetooth::eatt::EattChannel;
using bluetooth::eatt::EattExtension;

class MockEattExtension : public EattExtension {
 public:
  MockEattExtension() = default;
  MockEattExtension(const MockEattExtension&) = delete;
  MockEattExtension& operator=(const MockEattExtension&) = delete;

  ~MockEattExtension() override = default;

  static MockEattExtension* GetInstance();

  MOCK_METHOD((void), Connect, (const RawAddress& bd_addr));
  MOCK_METHOD((void), Disconnect, (const RawAddress& bd_addr, uint16_t cid));
  MOCK_METHOD((void), Reconfigure,
              (const RawAddress& bd_addr, uint16_t cid, uint16_t mtu));
  MOCK_METHOD((void), ReconfigureAll,
              (const RawAddress& bd_addr, uint16_t mtu));

  MOCK_METHOD((bool), IsEattSupportedByPeer, (const RawAddress& bd_addr));
  MOCK_METHOD((EattChannel*), FindEattChannelByCid,
              (const RawAddress& bd_addr, uint16_t cid));
  MOCK_METHOD((EattChannel*), FindEattChannelByTransId,
              (const RawAddress& bd_addr, uint32_t trans_id));
  MOCK_METHOD((bool), IsIndicationPending,
              (const RawAddress& bd_addr, uint16_t indication_handle));
  MOCK_METHOD((EattChannel*), GetChannelAvailableForIndication,
              (const RawAddress& bd_addr));
  MOCK_METHOD((void), FreeGattResources, (const RawAddress& bd_addr));
  MOCK_METHOD((bool), IsOutstandingMsgInSendQueue, (const RawAddress& bd_addr));
  MOCK_METHOD((EattChannel*), GetChannelWithQueuedDataToSend,
              (const RawAddress& bd_addr));
  MOCK_METHOD((EattChannel*), GetChannelAvailableForClientRequest,
              (const RawAddress& bd_addr));
  MOCK_METHOD((void), StartIndicationConfirmationTimer,
              (const RawAddress& bd_addr, uint16_t cid));
  MOCK_METHOD((void), StopIndicationConfirmationTimer,
              (const RawAddress& bd_addr, uint16_t cid));

  MOCK_METHOD((void), StartAppIndicationTimer,
              (const RawAddress& bd_addr, uint16_t cid));
  MOCK_METHOD((void), StopAppIndicationTimer,
              (const RawAddress& bd_addr, uint16_t cid));

  MOCK_METHOD((void), Start, ());
  MOCK_METHOD((void), Stop, ());
};
