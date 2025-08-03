#ifndef GET_CFLAGS_H_
#define GET_CFLAGS_H_
/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

#pragma once

// Use the category name and flag name registered in SettingsToPropertiesMapper.java
// to query the experiment flag value. This method will return default_value if
// querying fails.
// Note that for flags from Settings.Global, experiment_category_name should
// always be global_settings.

#ifdef __cplusplus
extern "C" {
#endif

const char* server_configurable_flags_GetServerConfigurableFlag(
    const char* experiment_category_name,
    const char* experiment_flag_name,
    const char* default_value);

#ifdef __cplusplus
}
#endif

#endif // GET_CFLAGS_H_
