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

#ifdef ARCH_ARM64
#include <lk/asm.h>

SECTION_GNU_NOTE_PROPERTY_AARCH64_FEATURES(GNU_NOTE_FEATURE_AARCH64_BTI)
#endif

.section .data

.global boot_start_app_begin, boot_start_app_end
.hidden boot_start_app_begin, boot_start_app_end
.balign 4096
boot_start_app_begin:
.incbin BOOT_START_APP
boot_start_app_end:

.global never_start_app_begin, never_start_app_end
.hidden never_start_app_begin, never_start_app_end
.balign 4096
never_start_app_begin:
.incbin NEVER_START_APP
never_start_app_end:

.global port_start_app_begin, port_start_app_end
.hidden port_start_app_begin, port_start_app_end
.balign 4096
port_start_app_begin:
.incbin PORT_START_APP
port_start_app_end:

.global port_start_fail_app_begin, port_start_fail_app_end
.hidden port_start_fail_app_begin, port_start_fail_app_end
.balign 4096
port_start_fail_app_begin:
.incbin PORT_START_FAIL_APP
port_start_fail_app_end:

.global restart_app_begin, restart_app_end
.hidden restart_app_begin, restart_app_end
.balign 4096
restart_app_begin:
.incbin RESTART_APP
restart_app_end:

.global port_waiter_app_begin, port_waiter_app_end
.hidden port_waiter_app_begin, port_waiter_app_end
.balign 4096
port_waiter_app_begin:
.incbin PORT_WAITER_APP
port_waiter_app_end:

.global unsigned_app_begin, unsigned_app_end
.hidden unsigned_app_begin, unsigned_app_end
.balign 4096
unsigned_app_begin:
.incbin UNSIGNED_APP
unsigned_app_end:

.global dev_only_app_begin, dev_only_app_end
.hidden dev_only_app_begin, dev_only_app_end
.balign 4096
dev_only_app_begin:
.incbin DEV_ONLY_APP
dev_only_app_end:
