/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
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
#pragma once

#include <gmock/gmock.h>

#include <cstddef>

namespace bluetooth {
namespace manager {

class BtifConfigInterface {
 public:
  virtual bool GetBin(const std::string& section, const std::string& key,
                      uint8_t* value, size_t* length) = 0;
  virtual size_t GetBinLength(const std::string& section,
                              const std::string& key) = 0;
  virtual ~BtifConfigInterface() = default;
};

class MockBtifConfigInterface : public BtifConfigInterface {
 public:
  MOCK_METHOD4(GetBin, bool(const std::string& section, const std::string& key,
                            uint8_t* value, size_t* length));
  MOCK_METHOD2(GetBinLength,
               size_t(const std::string& section, const std::string& key));
};

/**
 * Set the {@link MockBtifConfigInterface} for testing
 *
 * @param mock_btif_config_interface pointer to mock btif config interface,
 * could be null
 */
void SetMockBtifConfigInterface(
    MockBtifConfigInterface* mock_btif_config_interface);

}  // namespace manager
}  // namespace bluetooth
