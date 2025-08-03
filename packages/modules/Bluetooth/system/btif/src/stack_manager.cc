/******************************************************************************
 *
 *  Copyright 2014 Google, Inc.
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

#define LOG_TAG "bt_stack_manager"

#include <bluetooth/log.h>
#include <hardware/bluetooth.h>

#include <cstdlib>
#include <cstring>

#include "btcore/include/module.h"
#include "btcore/include/osi_module.h"
#include "btif/include/stack_manager_t.h"
#include "btif_api.h"
#include "btif_common.h"
#include "common/message_loop_thread.h"
#include "core_callbacks.h"
#include "include/check.h"
#include "main/shim/shim.h"
#include "os/log.h"
#include "stack/include/acl_api.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/main_thread.h"

// Temp includes
#include "bta/sys/bta_sys.h"
#include "btif_config.h"
#include "btif_profile_queue.h"
#include "device/include/device_iot_config.h"
#include "internal_include/bt_target.h"
#include "stack/include/gatt_api.h"
#include "stack/include/l2c_api.h"
#include "stack/include/port_api.h"
#include "stack/sdp/sdpint.h"
#if (BNEP_INCLUDED == TRUE)
#include "stack/include/bnep_api.h"
#endif
#include "stack/include/gap_api.h"
#if (PAN_INCLUDED == TRUE)
#include "stack/include/pan_api.h"
#endif
#if (HID_HOST_INCLUDED == TRUE)
#include "stack/include/hidh_api.h"
#endif
#include "bta/dm/bta_dm_int.h"
#include "device/include/interop.h"
#include "internal_include/stack_config.h"
#include "main/shim/controller.h"
#include "rust/src/core/ffi/module.h"
#include "stack/btm/btm_ble_int.h"
#include "stack/include/smp_api.h"

#ifndef BT_STACK_CLEANUP_WAIT_MS
#define BT_STACK_CLEANUP_WAIT_MS 1000
#endif

// Validate or respond to various conditional compilation flags

// Once BTA_PAN_INCLUDED is no longer exposed via bt_target.h
// this check and error statement may be removed.
static_assert(
    BTA_PAN_INCLUDED,
    "#define BTA_PAN_INCLUDED preprocessor compilation flag is unsupported"
    "  Pan profile is always included in the bluetooth stack"
    "*** Conditional Compilation Directive error");

// Once PAN_SUPPORTS_ROLE_NAP is no longer exposed via bt_target.h
// this check and error statement may be removed.
static_assert(
    PAN_SUPPORTS_ROLE_NAP,
    "#define PAN_SUPPORTS_ROLE_NAP preprocessor compilation flag is unsupported"
    "  Pan profile always supports network access point in the bluetooth stack"
    "*** Conditional Compilation Directive error");

// Once PAN_SUPPORTS_ROLE_PANU is no longer exposed via bt_target.h
// this check and error statement may be removed.
static_assert(
    PAN_SUPPORTS_ROLE_PANU,
    "#define PAN_SUPPORTS_ROLE_PANU preprocessor compilation flag is "
    "unsupported"
    "  Pan profile always supports user as a client in the bluetooth stack"
    "*** Conditional Compilation Directive error");

// Once BTA_HH_INCLUDED is no longer exposed via bt_target.h
// this check and error statement may be removed.
static_assert(
    BTA_HH_INCLUDED,
    "#define BTA_HH_INCLUDED preprocessor compilation flag is "
    "unsupported"
    "  Host interface device profile is always enabled in the bluetooth stack"
    "*** Conditional Compilation Directive error");

void BTA_dm_on_hw_on();
void BTA_dm_on_hw_off();

using bluetooth::common::MessageLoopThread;
using namespace bluetooth;

static MessageLoopThread management_thread("bt_stack_manager_thread");

// If initialized, any of the bluetooth API functions can be called.
// (e.g. turning logging on and off, enabling/disabling the stack, etc)
static bool stack_is_initialized;
// If running, the stack is fully up and able to bluetooth.
static bool stack_is_running;

static void event_init_stack(std::promise<void> promise,
                             bluetooth::core::CoreInterface* interface);
static void event_start_up_stack(bluetooth::core::CoreInterface* interface,
                                 ProfileStartCallback startProfiles,
                                 ProfileStopCallback stopProfiles);
static void event_shut_down_stack(ProfileStopCallback stopProfiles);
static void event_clean_up_stack(std::promise<void> promise,
                                 ProfileStopCallback stopProfiles);

static void event_signal_stack_up(void* context);
static void event_signal_stack_down(void* context);

static bluetooth::core::CoreInterface* interfaceToProfiles;

bluetooth::core::CoreInterface* GetInterfaceToProfiles() {
  return interfaceToProfiles;
}

// Unvetted includes/imports, etc which should be removed or vetted in the
// future
static future_t* hack_future;
// End unvetted section

// Interface functions

static void init_stack(bluetooth::core::CoreInterface* interface) {
  // This is a synchronous process. Post it to the thread though, so
  // state modification only happens there. Using the thread to perform
  // all stack operations ensures that the operations are done serially
  // and do not overlap.
  std::promise<void> promise;
  auto future = promise.get_future();
  management_thread.DoInThread(
      FROM_HERE, base::BindOnce(event_init_stack, std::move(promise),
                                base::Unretained(interface)));
  future.wait();
}

static void start_up_stack_async(bluetooth::core::CoreInterface* interface,
                                 ProfileStartCallback startProfiles,
                                 ProfileStopCallback stopProfiles) {
  management_thread.DoInThread(
      FROM_HERE, base::BindOnce(event_start_up_stack, interface, startProfiles,
                                stopProfiles));
}

static void shut_down_stack_async(ProfileStopCallback stopProfiles) {
  management_thread.DoInThread(
      FROM_HERE, base::BindOnce(event_shut_down_stack, stopProfiles));
}

static void clean_up_stack(ProfileStopCallback stopProfiles) {
  // This is a synchronous process. Post it to the thread though, so
  // state modification only happens there.
  std::promise<void> promise;
  auto future = promise.get_future();
  management_thread.DoInThread(
      FROM_HERE,
      base::BindOnce(event_clean_up_stack, std::move(promise), stopProfiles));

  auto status =
      future.wait_for(std::chrono::milliseconds(BT_STACK_CLEANUP_WAIT_MS));
  if (status == std::future_status::ready) {
    management_thread.ShutDown();
  } else {
    log::error("cleanup could not be completed in time, abandon it");
  }
}

static bool get_stack_is_running() { return stack_is_running; }

// Internal functions
extern const module_t bt_utils_module;
extern const module_t bte_logmsg_module;
extern const module_t btif_config_module;
extern const module_t gd_controller_module;
extern const module_t gd_shim_module;
extern const module_t interop_module;
extern const module_t osi_module;
extern const module_t rust_module;
extern const module_t stack_config_module;
extern const module_t device_iot_config_module;

struct module_lookup {
  const char* name;
  const module_t* module;
};

const struct module_lookup module_table[] = {
    {BTE_LOGMSG_MODULE, &bte_logmsg_module},
    {BTIF_CONFIG_MODULE, &btif_config_module},
    {GD_CONTROLLER_MODULE, &gd_controller_module},
    {GD_SHIM_MODULE, &gd_shim_module},
    {INTEROP_MODULE, &interop_module},
    {OSI_MODULE, &osi_module},
    {RUST_MODULE, &rust_module},
    {STACK_CONFIG_MODULE, &stack_config_module},
    {DEVICE_IOT_CONFIG_MODULE, &device_iot_config_module},
    {NULL, NULL},
};

inline const module_t* get_local_module(const char* name) {
  size_t len = strlen(name);

  for (const struct module_lookup* l = module_table; l->module; l++) {
    if (strncmp(l->name, name, len) == 0) {
      return l->module;
    }
  }

  log::fatal("Cannot find module {}, aborting", name);
  return nullptr;
}

static void init_stack_internal(bluetooth::core::CoreInterface* interface) {
  // all callbacks out of libbluetooth-core happen via this interface
  interfaceToProfiles = interface;

  module_management_start();

  main_thread_start_up();

  module_init(get_local_module(DEVICE_IOT_CONFIG_MODULE));
  module_init(get_local_module(OSI_MODULE));
  module_start_up(get_local_module(GD_SHIM_MODULE));
  module_init(get_local_module(BTIF_CONFIG_MODULE));
  btif_init_bluetooth();

  module_init(get_local_module(INTEROP_MODULE));
  module_init(get_local_module(STACK_CONFIG_MODULE));

  // stack init is synchronous, so no waiting necessary here
  stack_is_initialized = true;
}

// Synchronous function to initialize the stack
static void event_init_stack(std::promise<void> promise,
                             bluetooth::core::CoreInterface* interface) {
  log::info("is initializing the stack");

  if (stack_is_initialized) {
    log::info("found the stack already in initialized state");
  } else {
    init_stack_internal(interface);
  }

  log::info("finished");

  promise.set_value();
}

static void ensure_stack_is_initialized(
    bluetooth::core::CoreInterface* interface) {
  if (!stack_is_initialized) {
    log::warn("found the stack was uninitialized. Initializing now.");
    // No future needed since we are calling it directly
    init_stack_internal(interface);
  }
}

// Synchronous function to start up the stack
static void event_start_up_stack(bluetooth::core::CoreInterface* interface,
                                 ProfileStartCallback startProfiles,
                                 ProfileStopCallback stopProfiles) {
  if (stack_is_running) {
    log::info("stack already brought up");
    return;
  }

  ensure_stack_is_initialized(interface);

  log::info("is bringing up the stack");
  future_t* local_hack_future = future_new();
  hack_future = local_hack_future;

  log::info("Gd shim module enabled");
  get_btm_client_interface().lifecycle.btm_init();
  module_start_up(get_local_module(BTIF_CONFIG_MODULE));

  l2c_init();
  sdp_init();
  gatt_init();
  SMP_Init(get_btm_client_interface().security.BTM_GetSecurityMode());
  get_btm_client_interface().lifecycle.btm_ble_init();

  RFCOMM_Init();
  GAP_Init();

  startProfiles();

  bta_sys_init();

  module_init(get_local_module(BTE_LOGMSG_MODULE));

  btif_init_ok();
  BTA_dm_init();
  bta_dm_enable(btif_dm_sec_evt, btif_dm_acl_evt);

  btm_acl_device_down();
  CHECK(module_start_up(get_local_module(GD_CONTROLLER_MODULE)));
  BTM_reset_complete();

  BTA_dm_on_hw_on();

  if (future_await(local_hack_future) != FUTURE_SUCCESS) {
    log::error("failed to start up the stack");
    stack_is_running = true;  // So stack shutdown actually happens
    event_shut_down_stack(stopProfiles);
    return;
  }

  module_start_up(get_local_module(RUST_MODULE));

  stack_is_running = true;
  log::info("finished");
  do_in_jni_thread(FROM_HERE, base::BindOnce(event_signal_stack_up, nullptr));
}

// Synchronous function to shut down the stack
static void event_shut_down_stack(ProfileStopCallback stopProfiles) {
  if (!stack_is_running) {
    log::info("stack is already brought down");
    return;
  }

  log::info("is bringing down the stack");
  future_t* local_hack_future = future_new();
  hack_future = local_hack_future;
  stack_is_running = false;

  module_shut_down(get_local_module(RUST_MODULE));

  do_in_main_thread(FROM_HERE, base::BindOnce(&btm_ble_scanner_cleanup));

  btif_dm_on_disable();
  stopProfiles();

  do_in_main_thread(FROM_HERE, base::BindOnce(bta_dm_disable));

  btif_dm_cleanup();

  future_await(local_hack_future);
  local_hack_future = future_new();
  hack_future = local_hack_future;

  bta_sys_disable();
  BTA_dm_on_hw_off();

  module_shut_down(get_local_module(BTIF_CONFIG_MODULE));
  module_shut_down(get_local_module(DEVICE_IOT_CONFIG_MODULE));

  future_await(local_hack_future);

  module_clean_up(get_local_module(BTE_LOGMSG_MODULE));

  gatt_free();
  l2c_free();
  sdp_free();
  get_btm_client_interface().lifecycle.btm_ble_free();

  get_btm_client_interface().lifecycle.btm_free();

  hack_future = future_new();
  do_in_jni_thread(FROM_HERE, base::BindOnce(event_signal_stack_down, nullptr));
  future_await(hack_future);
  log::info("finished");
}

static void ensure_stack_is_not_running(ProfileStopCallback stopProfiles) {
  if (stack_is_running) {
    log::warn("found the stack was still running. Bringing it down now.");
    event_shut_down_stack(stopProfiles);
  }
}

// Synchronous function to clean up the stack
static void event_clean_up_stack(std::promise<void> promise,
                                 ProfileStopCallback stopProfiles) {
  if (!stack_is_initialized) {
    log::info("found the stack already in a clean state");
    goto cleanup;
  }

  ensure_stack_is_not_running(stopProfiles);

  log::info("is cleaning up the stack");
  stack_is_initialized = false;

  btif_cleanup_bluetooth();

  module_clean_up(get_local_module(STACK_CONFIG_MODULE));
  module_clean_up(get_local_module(INTEROP_MODULE));

  module_clean_up(get_local_module(BTIF_CONFIG_MODULE));
  module_clean_up(get_local_module(DEVICE_IOT_CONFIG_MODULE));

  module_clean_up(get_local_module(OSI_MODULE));
  log::info("Gd shim module disabled");
  module_shut_down(get_local_module(GD_SHIM_MODULE));

  main_thread_shut_down();

  module_management_stop();
  log::info("finished");

cleanup:;
  promise.set_value();
}

static void event_signal_stack_up(void* /* context */) {
  // Notify BTIF connect queue that we've brought up the stack. It's
  // now time to dispatch all the pending profile connect requests.
  btif_queue_connect_next();
  GetInterfaceToProfiles()->events->invoke_adapter_state_changed_cb(
      BT_STATE_ON);
}

static void event_signal_stack_down(void* /* context */) {
  GetInterfaceToProfiles()->events->invoke_adapter_state_changed_cb(
      BT_STATE_OFF);
  future_ready(stack_manager_get_hack_future(), FUTURE_SUCCESS);
}

static void ensure_manager_initialized() {
  if (management_thread.IsRunning()) return;

  management_thread.StartUp();
  if (!management_thread.IsRunning()) {
    log::error("unable to start stack management thread");
    return;
  }
}

static const stack_manager_t interface = {init_stack, start_up_stack_async,
                                          shut_down_stack_async, clean_up_stack,
                                          get_stack_is_running};

const stack_manager_t* stack_manager_get_interface() {
  ensure_manager_initialized();
  return &interface;
}

future_t* stack_manager_get_hack_future() { return hack_future; }

namespace bluetooth {
namespace legacy {
namespace testing {

void set_interface_to_profiles(
    bluetooth::core::CoreInterface* interfaceToProfiles) {
  ::interfaceToProfiles = interfaceToProfiles;
}

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth
