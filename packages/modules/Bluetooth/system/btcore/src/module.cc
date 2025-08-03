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

#define LOG_TAG "bt_core_module"

#include "btcore/include/module.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <dlfcn.h>
#include <string.h>

#include <mutex>
#include <unordered_map>

#include "common/message_loop_thread.h"
#include "include/check.h"
#include "os/log.h"

using bluetooth::common::MessageLoopThread;
using namespace bluetooth;

typedef enum {
  MODULE_STATE_NONE = 0,
  MODULE_STATE_INITIALIZED = 1,
  MODULE_STATE_STARTED = 2
} module_state_t;

static std::unordered_map<const module_t*, module_state_t> metadata;

// TODO(jamuraa): remove this lock after the startup sequence is clean
static std::mutex metadata_mutex;

static bool call_lifecycle_function(module_lifecycle_fn function);
static module_state_t get_module_state(const module_t* module);
static void set_module_state(const module_t* module, module_state_t state);

void module_management_start(void) {}

void module_management_stop(void) { metadata.clear(); }

const module_t* get_module(const char* name) {
  module_t* module = (module_t*)dlsym(RTLD_DEFAULT, name);
  CHECK(module);
  return module;
}

bool module_init(const module_t* module) {
  CHECK(module != NULL);
  CHECK(get_module_state(module) == MODULE_STATE_NONE);

  if (!call_lifecycle_function(module->init)) {
    log::error("Failed to initialize module \"{}\"", module->name);
    return false;
  }

  set_module_state(module, MODULE_STATE_INITIALIZED);
  return true;
}

bool module_start_up(const module_t* module) {
  CHECK(module != NULL);
  // TODO(zachoverflow): remove module->init check once automagic order/call is
  // in place.
  // This hack is here so modules which don't require init don't have to have
  // useless calls
  // as we're converting the startup sequence.
  CHECK(get_module_state(module) == MODULE_STATE_INITIALIZED ||
        module->init == NULL);

  log::info("Starting module \"{}\"", module->name);
  if (!call_lifecycle_function(module->start_up)) {
    log::error("Failed to start up module \"{}\"", module->name);
    return false;
  }
  log::info("Started module \"{}\"", module->name);

  set_module_state(module, MODULE_STATE_STARTED);
  return true;
}

void module_shut_down(const module_t* module) {
  CHECK(module != NULL);
  module_state_t state = get_module_state(module);
  CHECK(state <= MODULE_STATE_STARTED);

  // Only something to do if the module was actually started
  if (state < MODULE_STATE_STARTED) return;

  log::info("Shutting down module \"{}\"", module->name);
  if (!call_lifecycle_function(module->shut_down)) {
    log::error("Failed to shutdown module \"{}\". Continuing anyway.",
               module->name);
  }
  log::info("Shutdown of module \"{}\" completed", module->name);

  set_module_state(module, MODULE_STATE_INITIALIZED);
}

void module_clean_up(const module_t* module) {
  CHECK(module != NULL);
  module_state_t state = get_module_state(module);
  CHECK(state <= MODULE_STATE_INITIALIZED);

  // Only something to do if the module was actually initialized
  if (state < MODULE_STATE_INITIALIZED) return;

  log::info("Cleaning up module \"{}\"", module->name);
  if (!call_lifecycle_function(module->clean_up)) {
    log::error("Failed to cleanup module \"{}\". Continuing anyway.",
               module->name);
  }
  log::info("Cleanup of module \"{}\" completed", module->name);

  set_module_state(module, MODULE_STATE_NONE);
}

static bool call_lifecycle_function(module_lifecycle_fn function) {
  // A NULL lifecycle function means it isn't needed, so assume success
  if (!function) return true;

  future_t* future = function();

  // A NULL future means synchronous success
  if (!future) return true;

  // Otherwise fall back to the future
  return future_await(future);
}

static module_state_t get_module_state(const module_t* module) {
  std::lock_guard<std::mutex> lock(metadata_mutex);
  auto map_ptr = metadata.find(module);

  return (map_ptr != metadata.end()) ? map_ptr->second : MODULE_STATE_NONE;
}

static void set_module_state(const module_t* module, module_state_t state) {
  std::lock_guard<std::mutex> lock(metadata_mutex);
  metadata[module] = state;
}
