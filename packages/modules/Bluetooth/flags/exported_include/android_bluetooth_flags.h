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

#pragma once

#ifndef TARGET_FLOSS

#include <com_android_bluetooth_flags.h>

#define IS_FLAG_ENABLED(flag_name) com::android::bluetooth::flags::flag_name()
#define IS_FLAG_ENABLED_P(provider, flag_name) provider.flag_name()

#else

// FLOSS does not yet support android aconfig flags
#define IS_FLAG_ENABLED(flag_name) false
#define IS_FLAG_ENABLED_P(provider, flag_name) false

namespace com::android::bluetooth::flags {
struct flag_provider_interface {};
}  // namespace com::android::bluetooth::flags

#endif
