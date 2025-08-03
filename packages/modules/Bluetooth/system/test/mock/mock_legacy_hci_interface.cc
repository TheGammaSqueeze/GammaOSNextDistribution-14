/*
 * Copyright 2023 The Android Open Source Project
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

#include "test/mock/mock_legacy_hci_interface.h"

#include <stddef.h>

#include "stack/include/hcimsgs.h"

namespace bluetooth::legacy::hci {
namespace testing {
const MockInterface* interface_;
void SetMock(const MockInterface& mock) { interface_ = &mock; }
}  // namespace testing
const Interface& GetInterface() { return *testing::interface_; }
}  // namespace bluetooth::legacy::hci
