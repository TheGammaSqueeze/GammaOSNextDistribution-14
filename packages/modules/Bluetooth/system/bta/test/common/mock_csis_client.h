/*
 * Copyright 2021 HIMSA II K/S - www.himsa.dk.
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

#include "bta_csis_api.h"

class MockCsisClient : public bluetooth::csis::CsisClient {
 public:
  /* Overrides */
  MOCK_METHOD((void), Connect, (const RawAddress& addr), (override));
  MOCK_METHOD((void), Disconnect, (const RawAddress& addr), (override));
  MOCK_METHOD((void), RemoveDevice, (const RawAddress& address), (override));
  MOCK_METHOD((int), GetGroupId, (const RawAddress& addr, bluetooth::Uuid uuid),
              (override));
  MOCK_METHOD((void), LockGroup,
              (const int group_id, bool lock, bluetooth::csis::CsisLockCb cb),
              (override));
  MOCK_METHOD((std::vector<RawAddress>), GetDeviceList, (int group_id),
              (override));
  MOCK_METHOD((int), GetDesiredSize, (int group_id), (override));

  /* Called from static methods */
  MOCK_METHOD((void), Initialize,
              (bluetooth::csis::CsisClientCallbacks * callbacks,
               base::Closure initCb));
  MOCK_METHOD((void), CleanUp, ());
  MOCK_METHOD((void), DebugDump, (int fd));
  MOCK_METHOD((bool), IsCsisClientRunning, ());
  MOCK_METHOD((CsisClient*), Get, ());

  static void SetMockInstanceForTesting(MockCsisClient* mock);
};
