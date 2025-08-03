/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.7.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_main_shim_hci_layer.h"

#include "main/shim/hci_layer.h"
#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace bluetooth::shim {
namespace testing {
const hci_t* test_interface = nullptr;
void hci_layer_set_interface(const hci_t* interface) {
  test_interface = interface;
}
}  // namespace testing
}  // namespace bluetooth::shim

const hci_t* bluetooth::shim::hci_layer_get_interface() {
  return testing::test_interface;
}

namespace test {
namespace mock {
namespace main_shim_hci_layer {

// Function state capture and return values, if needed
struct OnTransmitPacketCommandComplete OnTransmitPacketCommandComplete;
struct OnTransmitPacketStatus OnTransmitPacketStatus;
struct hci_on_reset_complete hci_on_reset_complete;
struct hci_on_shutting_down hci_on_shutting_down;

}  // namespace main_shim_hci_layer
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace main_shim_hci_layer {}  // namespace main_shim_hci_layer
}  // namespace mock
}  // namespace test

// Mocked functions, if any
namespace cpp {
void OnTransmitPacketCommandComplete(command_complete_cb complete_callback,
                                     void* context,
                                     bluetooth::hci::CommandCompleteView view) {
  inc_func_call_count(__func__);
  test::mock::main_shim_hci_layer::OnTransmitPacketCommandComplete(
      complete_callback, context, view);
}
void OnTransmitPacketStatus(command_status_cb status_callback, void* context,
                            std::unique_ptr<OsiObject> command,
                            bluetooth::hci::CommandStatusView view) {
  inc_func_call_count(__func__);
  test::mock::main_shim_hci_layer::OnTransmitPacketStatus(
      status_callback, context, std::move(command), view);
}
}  // namespace cpp

void bluetooth::shim::hci_on_reset_complete() {
  inc_func_call_count(__func__);
  test::mock::main_shim_hci_layer::hci_on_reset_complete();
}
void bluetooth::shim::hci_on_shutting_down() {
  inc_func_call_count(__func__);
  test::mock::main_shim_hci_layer::hci_on_shutting_down();
}
// Mocked functions complete
// END mockcify generation
