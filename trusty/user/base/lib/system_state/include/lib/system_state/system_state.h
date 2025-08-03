/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <interface/system_state/system_state.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <sys/types.h>

__BEGIN_CDECLS

/**
 * system_state_get_flag() - Get the current value of a system flag
 * @flag:   Identifier for flag to get. One of @enum system_state_flag.
 * @valuep: Pointer to return value in.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int system_state_get_flag(enum system_state_flag flag, uint64_t* valuep);

/**
 * system_state_get_flag_default() - Get the current value of a system flag
 * @flag:           Identifier for flag to get. One of @enum system_state_flag.
 * @default_value:  Value to return if system_state_get_flag() returns an error.
 *
 * Return: the current value of the flag if it was successfully read, or
 * @default_value if the flag could not be read.
 */
static inline uint64_t system_state_get_flag_default(
        enum system_state_flag flag,
        uint64_t default_value) {
    uint64_t value = default_value;
    system_state_get_flag(flag, &value);
    /* Ignore return code, value is unchanged on any error. */
    return value;
}

/**
 * system_state_provisioning_allowed() - Check if provisioning is allowed.
 *
 * Return: %true if provisioning is currently allowed, %false otherwise.
 */
static inline bool system_state_provisioning_allowed(void) {
    return system_state_get_flag_default(
                   SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED,
                   SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_NOT_ALLOWED) ==
           SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED_VALUE_ALLOWED;
}

/**
 * system_state_app_loading_unlocked() - Check if loading dev apps is allowed
 *
 * Return: %true if app loading is unlocked and dev signing are enabled, %false
 * otherwise.
 */
static inline bool system_state_app_loading_unlocked(void) {
    return system_state_get_flag_default(SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED,
                                         false);
}

/**
 * system_state_app_loading_skip_version_check() - Check if rollback version
 * check should be skipped when loading apps.
 *
 * Return: %true if the version check should be skipped, %false otherwise.
 */
static inline bool system_state_app_loading_skip_version_check(void) {
    return system_state_get_flag_default(
                   SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK,
                   SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_REQUIRED) ==
           SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_CHECK;
}

/**
 * system_state_app_loading_skip_version_update() - Check if rollback version
 * update should be skipped when loading apps.
 *
 * Version update is always skipped when
 * system_state_app_loading_skip_version_check() returns true.
 *
 * Return: %true if the version update should be skipped, %false otherwise.
 */
static inline bool system_state_app_loading_skip_version_update(void) {
    uint64_t value = system_state_get_flag_default(
            SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK,
            SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_REQUIRED);
    return value ==
                   SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_CHECK ||
           value ==
                   SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK_VALUE_SKIP_UPDATE;
}

__END_CDECLS
