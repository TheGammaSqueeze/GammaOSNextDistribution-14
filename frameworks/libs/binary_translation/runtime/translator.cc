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

#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/runtime_primitives/translation_cache.h"

#include "berberis/base/checks.h"

namespace berberis {

// Invalidate regions overlapping with the range. Could be pretty slow.
void InvalidateGuestRange(GuestAddr start, GuestAddr end) {
  TranslationCache* cache = TranslationCache::GetInstance();
  cache->InvalidateGuestRange(start, end);
  // TODO(b/28081995): Specify region to avoid flushing too much.
  FlushGuestCodeCache();
}

}  // namespace berberis
