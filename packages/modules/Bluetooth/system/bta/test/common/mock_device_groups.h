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

#include "bta_groups.h"

class MockDeviceGroups : public bluetooth::groups::DeviceGroups {
 public:
  /* Overrides */
  MOCK_METHOD((int), AddDevice,
              (const RawAddress& addr, bluetooth::Uuid uuid, int group_id),
              (override));
  MOCK_METHOD((int), GetGroupId, (const RawAddress& addr, bluetooth::Uuid uuid),
              (const override));
  MOCK_METHOD((void), RemoveDevice, (const RawAddress& addr, int group_id),
              (override));
  MOCK_METHOD((size_t), GetSerializedSize, (const RawAddress& addr),
              (const override));
  MOCK_METHOD((bool), SerializeDeviceGroups,
              (const RawAddress& addr, uint8_t* p_out, size_t buffer_size),
              (const override));

  /* Called from static methods */
  MOCK_METHOD((void), Initialize,
              (bluetooth::groups::DeviceGroupsCallbacks * callbacks));
  MOCK_METHOD((void), CleanUp,
              (bluetooth::groups::DeviceGroupsCallbacks * callbacks));

  static void SetMockInstanceForTesting(MockDeviceGroups* machine);
};
