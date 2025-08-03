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

#ifndef BERBERIS_PROXY_LOADER_H_
#define BERBERIS_PROXY_LOADER_H_

#include "berberis/guest_state/guest_addr.h"

namespace berberis {

void InterceptGuestSymbol(GuestAddr addr,
                          const char* library_name,
                          const char* name,
                          const char* proxy_prefix);

}  // namespace berberis

#endif  // BERBERIS_PROXY_LOADER_H_
