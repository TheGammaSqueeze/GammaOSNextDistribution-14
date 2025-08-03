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

#include "mock_device_groups.h"

#include <base/logging.h>

static MockDeviceGroups* mock_groups = nullptr;

void MockDeviceGroups::SetMockInstanceForTesting(MockDeviceGroups* groups) {
  mock_groups = groups;
}

bluetooth::groups::DeviceGroups* bluetooth::groups::DeviceGroups::Get() {
  return mock_groups;
}

void bluetooth::groups::DeviceGroups::Initialize(
    bluetooth::groups::DeviceGroupsCallbacks* callbacks) {
  LOG_ASSERT(mock_groups) << "Mock Device Groups not set!";
  mock_groups->Initialize(callbacks);
};

void bluetooth::groups::DeviceGroups::DebugDump(int) {}

void bluetooth::groups::DeviceGroups::CleanUp(
    bluetooth::groups::DeviceGroupsCallbacks* callbacks) {
  LOG_ASSERT(mock_groups) << "Mock Device Groups not set!";
  mock_groups->CleanUp(callbacks);
}
