/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <apploader/package.h>
#include <lib/apploader_policy_engine/apploader_policy_engine.h>
#include <lk/compiler.h>

__BEGIN_CDECLS

bool apploader_parse_manifest_from_metadata(
        struct apploader_package_metadata* pkg_meta,
        struct manifest_extracts* manifest_extracts);

bool apploader_parse_manifest(const char* manifest_start,
                              const size_t manifest_size,
                              struct manifest_extracts* manifest_extracts);

__END_CDECLS
