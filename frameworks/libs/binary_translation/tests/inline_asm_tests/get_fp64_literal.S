// Copyright (C) 2019 The Android Open Source Project
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

        .arch armv8-a
        .text
        .align  2
        .global get_fp64_literal
        .type   get_fp64_literal, %function
get_fp64_literal:
        ldr     d0, .LC0
        fmov    x0, d0
        ret
        .size   get_fp64_literal, .-get_fp64_literal
        .section .rodata
        .align  3
.LC0:
        .word   0x89abcdef
        .word   0x01234567
