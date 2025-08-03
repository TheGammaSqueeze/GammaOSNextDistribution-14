//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

.text
.globl native_bridge_trace
.type native_bridge_trace, %function
native_bridge_trace:
  unimp

.globl native_bridge_intercept_symbol
.type native_bridge_intercept_symbol, %function
native_bridge_intercept_symbol:
  unimp

.globl native_bridge_post_init
.type native_bridge_post_init, %function
native_bridge_post_init:
  unimp

// Symbol to set guest return address to when guest function is called from the runtime.
// Provides unwind info that corresponds to ScopedHostCallFrame.
// Provides 1 readable insn before and 2 after.
  .cfi_startproc
  .cfi_def_cfa s0, 16
  .cfi_offset ra, -8
  .cfi_offset s0, -16
  nop
.globl native_bridge_call_guest
.type native_bridge_call_guest, %function
native_bridge_call_guest:
  nop
  nop
  unimp
  .cfi_endproc
