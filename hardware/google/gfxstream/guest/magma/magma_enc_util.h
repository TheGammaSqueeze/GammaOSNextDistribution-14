// Copyright (C) 2023 The Android Open Source Project
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

#include <log/log.h>
#include <lib/magma/magma_common_defs.h>
#include <stddef.h>

namespace magma_enc_util {

size_t size_command_descriptor(magma_command_descriptor* descriptor);
void pack_command_descriptor(void* ptr, magma_connection_t connection, uint32_t context_id,
                             magma_command_descriptor* descriptor);

}  // namespace magma_enc_util
