/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file replaces "external/arm-trusted-firmware/include/common/asm_macros_common.S" for
 * building "external/arm-trusted-firmware/lib/aarch64/cache_helper.S". It defines the "func"
 * macro for declaring assembly function. However the original file uses assembly directives
 * such as ".size" for debug information which are not recogized by LLVM when building for
 * windows/msvc target. Thus we use our custom definition to workaround.
 */

/*
 * This macro marks the begin of a function. It simply creates a label.
 */
.macro func _name
\_name:
.endm

/*
 * This macro is used to mark the end of a function. For our usage, it's simply a noop.
 */
.macro endfunc _name
.endm
