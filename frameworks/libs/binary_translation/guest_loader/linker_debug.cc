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

#include "berberis/guest_loader/guest_loader.h"

#include <link.h>

#include "berberis/base/macros.h"
#include "berberis/base/tracing.h"
#include "berberis/instrument/loader.h"

#include "guest_loader_impl.h"  // MakeElfSymbolTrampolineCallable

namespace berberis {

namespace {

void DoCustomTrampoline_rtld_db_dlactivity(HostCode callee, ThreadState* state) {
  UNUSED(callee, state);

  // It would be also tempting to bind r_debug to callee, but then we need to know r_debug when
  // creating the trampoline. Also, it seems r_debug might still be 0 when rtld_db_dlactivity is
  // called first couple of times.
  // Thus, search and check.
  const auto* debug = GuestLoader::GetInstance()->FindRDebug();
  if (debug == nullptr) {
    return;
  }

  // ATTENTION: assume struct r_debug and struct link_map are compatible!
  if (debug->r_state == r_debug::RT_CONSISTENT) {
    OnConsistentLinkMap(debug->r_map);
  }
}

}  // namespace

void InitLinkerDebug(const LoadedElfFile& linker_elf_file) {
  if (!kInstrumentLoader) {
    return;
  }

  // The correct way to hook linker rtld_db_dlactivity would be to read struct r_debug pointer from
  // main executable's DT_DEBUG and get breakpoint address from there.
  // Unfortunately, DT_DEBUG gets initialized by guest linker, which didn't yet run at this point.
  // Instead, hope breakpoint symbol is exported.
  std::string error_msg;
  if (!MakeElfSymbolTrampolineCallable(linker_elf_file,
                                       "linker",
                                       "rtld_db_dlactivity",
                                       DoCustomTrampoline_rtld_db_dlactivity,
                                       nullptr,
                                       &error_msg)) {
    TRACE("failed to hook rtld_db_dlactivity: %s", error_msg.c_str());
  }
}

}  // namespace berberis