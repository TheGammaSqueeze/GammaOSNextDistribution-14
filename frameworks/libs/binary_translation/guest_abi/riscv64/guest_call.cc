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

#include "berberis/guest_abi/guest_call.h"

#include "berberis/base/logging.h"
#include "berberis/runtime_primitives/runtime_library.h"

namespace berberis {

void GuestCall::AddArgInt32(uint32_t arg) {
  AddArgInt64(arg);
}

void GuestCall::AddArgInt64(uint64_t arg) {
  CHECK_LT(buf_.argc, 8);
  buf_.argv[buf_.argc++] = arg;
}

void GuestCall::RunVoid(GuestAddr func_addr) {
  RunGuestCall(func_addr, &buf_);
}

uint32_t GuestCall::RunResInt32(GuestAddr func_addr) {
  return RunResInt64(func_addr);
}

uint64_t GuestCall::RunResInt64(GuestAddr func_addr) {
  buf_.resc = 1;
  RunGuestCall(func_addr, &buf_);
  return buf_.argv[0];
}

}  // namespace berberis
