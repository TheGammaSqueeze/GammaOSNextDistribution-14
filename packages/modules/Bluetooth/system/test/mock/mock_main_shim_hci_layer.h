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

#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.7.0
 */

#include <functional>

#include "hci/include/hci_layer.h"

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include <base/functional/bind.h>

#include "hci/hci_packets.h"
#include "osi/include/allocator.h"

// Original usings
using CommandCallbackData = struct {
  void* context;
};
// Mocked compile conditionals, if any

namespace bluetooth::shim {
namespace testing {
extern void hci_layer_set_interface(const hci_t* interface);
}  // namespace testing
}  // namespace bluetooth::shim

namespace test {
namespace mock {
namespace main_shim_hci_layer {

// Shared state between mocked functions and tests
// Name: OnTransmitPacketCommandComplete
// Params: command_complete_cb complete_callback, void* context,
// bluetooth::hci::CommandCompleteView view Return: void
struct OnTransmitPacketCommandComplete {
  std::function<void(command_complete_cb complete_callback, void* context,
                     bluetooth::hci::CommandCompleteView view)>
      body{[](command_complete_cb /* complete_callback */, void* /* context */,
              bluetooth::hci::CommandCompleteView /* view */) {}};
  void operator()(command_complete_cb complete_callback, void* context,
                  bluetooth::hci::CommandCompleteView view) {
    body(complete_callback, context, view);
  };
};
extern struct OnTransmitPacketCommandComplete OnTransmitPacketCommandComplete;

// Name: OnTransmitPacketStatus
// Params: command_status_cb status_callback, void* context,
// std::unique_ptr<OsiObject> command, bluetooth::hci::CommandStatusView view
// Return: void
struct OnTransmitPacketStatus {
  std::function<void(command_status_cb status_callback, void* context,
                     std::unique_ptr<OsiObject> command,
                     bluetooth::hci::CommandStatusView view)>
      body{[](command_status_cb /* status_callback */, void* /* context */,
              std::unique_ptr<OsiObject> /* command */,
              bluetooth::hci::CommandStatusView /* view */) {}};
  void operator()(command_status_cb status_callback, void* context,
                  std::unique_ptr<OsiObject> command,
                  bluetooth::hci::CommandStatusView view) {
    body(status_callback, context, std::move(command), view);
  };
};
extern struct OnTransmitPacketStatus OnTransmitPacketStatus;

// Name: hci_layer_get_interface
// Params:
// Return: const hci_t*
struct hci_layer_get_interface {
  static const hci_t* return_value;
  std::function<const hci_t*()> body{[]() { return return_value; }};
  const hci_t* operator()() { return body(); };
};
extern struct hci_layer_get_interface hci_layer_get_interface;

// Name: hci_on_reset_complete
// Params:
// Return: void
struct hci_on_reset_complete {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct hci_on_reset_complete hci_on_reset_complete;

// Name: hci_on_shutting_down
// Params:
// Return: void
struct hci_on_shutting_down {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct hci_on_shutting_down hci_on_shutting_down;

}  // namespace main_shim_hci_layer
}  // namespace mock
}  // namespace test

// END mockcify generation
