/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_RUNTIME_TRANSLATOR_RISCV64_H_
#define BERBERIS_RUNTIME_TRANSLATOR_RISCV64_H_

#include <cstddef>
#include <tuple>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/lite_translator/lite_translate_region.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

std::tuple<bool, HostCodePiece, size_t, GuestCodeEntry::Kind> TryLiteTranslateAndInstallRegion(
    GuestAddr pc,
    const LiteTranslateParams& params = LiteTranslateParams());
std::tuple<bool, HostCodePiece, size_t, GuestCodeEntry::Kind> HeavyOptimizeRegion(GuestAddr pc);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_TRANSLATOR_RISCV64_H_