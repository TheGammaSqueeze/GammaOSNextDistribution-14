/*
 * Copyright 2019 The Android Open Source Project
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

/**
 * Gabeldorsche related legacy-only-stack-side expansion and support code.
 */
#include "btcore/include/module.h"
#include "osi/include/future.h"

static const char GD_SHIM_MODULE[] = "gd_shim_module";

constexpr future_t* kReturnImmediate = nullptr;
constexpr module_lifecycle_fn kUnusedModuleApi = nullptr;
constexpr char* kUnusedModuleDependencies = nullptr;

namespace bluetooth {
namespace shim {

/**
 * Checks if the bluetooth gd stack has been started up.
 *
 * @return true if bluetooth gd stack is started, false otherwise.
 */
bool is_gd_stack_started_up();

/**
 * Checks if the dumpsys module has been started.
 *
 * @return true if specified module has started, false otherwise.
 */
bool is_gd_dumpsys_module_started();

/**
 * Checks whether discovery should be classic only (vs also triggering BLE).
 *
 * @return true if discovery should be limited to classic.
 */
bool is_classic_discovery_only_enabled();

}  // namespace shim
}  // namespace bluetooth
