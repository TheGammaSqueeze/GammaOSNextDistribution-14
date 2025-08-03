// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "aemu/base/export.h"
#include "constants.h"
#include "host-common/hw-config.h"

/* MSVC only exports function pointers */
AEMU_EXPORT AndroidHwConfig* aemu_get_android_hw();

/* this is to support snapshot (currently only qemu1+software-renderer) */
extern const char* savevm_on_exit;

/* this indicates that guest has mounted data partition */
extern int guest_data_partition_mounted;

/* this indicates that guest has boot completed */
extern int guest_boot_completed;

extern int arm_snapshot_save_completed;

extern int host_emulator_is_headless;

/* are we using the emulator in the android mode or plain qemu? */
extern int android_qemu_mode;

/* are we using android-emu libraries for a minimal configuration? */
extern int min_config_qemu_mode;

/* is android-emu running Fuchsia? */
extern int is_fuchsia;

extern int android_snapshot_update_timer;

/* are we using qemu 2? */
/* remove this flag once we deprecate qemu1 on both dev and release branches. */
extern int engine_supports_snapshot;

/* are we changing the language, country, or locale? */
extern int changing_language_country_locale;
extern const char* to_set_language;
extern const char* to_set_country;
extern const char* to_set_locale;

/* True if we are using keycode forwarding instead of translating text value to
 * keycode */
/* on emulator host. */
extern int use_keycode_forwarding;

enum WinsysPreferredGlesBackend {
    WINSYS_GLESBACKEND_PREFERENCE_AUTO = 0,
    WINSYS_GLESBACKEND_PREFERENCE_ANGLE = 1,
    WINSYS_GLESBACKEND_PREFERENCE_ANGLE9 = 2,
    WINSYS_GLESBACKEND_PREFERENCE_SWIFTSHADER = 3,
    WINSYS_GLESBACKEND_PREFERENCE_NATIVEGL = 4,
    WINSYS_GLESBACKEND_PREFERENCE_NUM = 5,
};

enum WinsysPreferredGlesApiLevel {
    WINSYS_GLESAPILEVEL_PREFERENCE_AUTO = 0,
    WINSYS_GLESAPILEVEL_PREFERENCE_MAX = 1,
    WINSYS_GLESAPILEVEL_PREFERENCE_COMPAT = 2,
    WINSYS_GLESAPILEVEL_PREFERENCE_NUM = 3,
};

