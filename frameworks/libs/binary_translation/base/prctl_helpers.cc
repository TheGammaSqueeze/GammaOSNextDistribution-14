/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "berberis/base/prctl_helpers.h"

#if defined(__BIONIC__)
#include <sys/prctl.h>
#else
#include "berberis/base/macros.h"
#endif

namespace berberis {

int SetVmaAnonName(void* addr, size_t size, const char* name) {
#if defined(__BIONIC__)
  return prctl(PR_SET_VMA, PR_SET_VMA_ANON_NAME, addr, size, name);
#else
  UNUSED(addr, size, name);
  return 0;
#endif
}

}  // namespace berberis
