/*
 * Copyright (C) 2020 The Android Open Source Project
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

/*
 * Clang instruments HWASan-enabled code with __hwasan_check_* functions. If a
 * tag mismatch is detected, __hwasan_check_* calls into __hwasan_tag_mismatch
 * with a custom calling convention.
 *
 * __hwasan_check_* sets up the stack frame for __hwasan_tag_mismatch, saves x0,
 * x1, x29, and x30, then branches into __hwasan_tag_mismatch. Here is an
 * example:
 *    stp x0, x1, [sp,#-256]!
 *    stp x29, x30, [sp,#232]
 *    mov x0, <fault address>
 *    mov x1, <custom argument>
 *    b <address of __hwasan_tag_mismatch>
 *
 * __hwasan_check_* and __hwasan_tag_mismatch effectively share the stack frame.
 * __hwasan_tag_mismatch is expected to save the rest of the registers and do
 * error reporting. In our case, it calls into __hwasan_report_error, which is a
 * C function, using C calling convention.
 *
 * __hwasan_tag_mismatch is not expected to return. However, for testing
 * purposes, we report the error, restore the registers, then return to the
 * caller of __hwasan_check_*, and just continue execution.
 */

#include <arch/asm.h>

.section .text
.file "hwasan_tag_mismatch.S"
.global __hwasan_tag_mismatch;
.type __hwasan_tag_mismatch,STT_FUNC;
__hwasan_tag_mismatch:
	bti c
	str x28,      [sp, #224]
	stp x26, x27, [sp, #208]
	stp x24, x25, [sp, #192]
	stp x22, x23, [sp, #176]
	stp x20, x21, [sp, #160]
	stp x18, x19, [sp, #144]
	stp x16, x17, [sp, #128]
	stp x14, x15, [sp, #112]
	stp x12, x13, [sp, #96]
	stp x10, x11, [sp, #80]
	stp x8,  x9,  [sp, #64]
	stp x6,  x7,  [sp, #48]
	stp x4,  x5,  [sp, #32]
	stp x2,  x3,  [sp, #16]

	/*
	 * Frame is allocated by the caller of __hwasan_tag_mismatch(). Adjust frame
	 * pointer to correct spot.
	 */
	add fp, sp, #232

	mov x1, sp
	bl __hwasan_report_error

	/*
	 * HACK: this function must not return, but we do this for bring up and
	 * testing
	 */
	ldr x28,      [sp, #224]
	ldp x26, x27, [sp, #208]
	ldp x24, x25, [sp, #192]
	ldp x22, x23, [sp, #176]
	ldp x20, x21, [sp, #160]
	ldp x18, x19, [sp, #144]
	ldp x16, x17, [sp, #128]
	ldp x14, x15, [sp, #112]
	ldp x12, x13, [sp, #96]
	ldp x10, x11, [sp, #80]
	ldp x8,  x9,  [sp, #64]
	ldp x6,  x7,  [sp, #48]
	ldp x4,  x5,  [sp, #32]
	ldp x2,  x3,  [sp, #16]

	ldp x0,  x1,  [sp]
	ldp x29, x30, [sp, #232]
	add sp, sp, #256
	ret

SECTION_GNU_NOTE_PROPERTY_AARCH64_FEATURES(GNU_NOTE_FEATURE_AARCH64_BTI)
