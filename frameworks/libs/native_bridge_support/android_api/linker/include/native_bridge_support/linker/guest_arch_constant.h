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

#ifndef NATIVE_BRIDGE_SUPPORT_LINKER_GUEST_ARCH_CONSTANT_H_
#define NATIVE_BRIDGE_SUPPORT_LINKER_GUEST_ARCH_CONSTANT_H_

#if defined(__arm__)
const constexpr char* kAppProcessPath = "/system/bin/arm/app_process";
const constexpr char* kPtInterpPath = "/system/bin/arm/linker";
const constexpr char* kVdsoPath = "/system/lib/arm/libnative_bridge_vdso.so";
#elif defined(__aarch64__)
const constexpr char* kAppProcessPath = "/system/bin/arm64/app_process64";
const constexpr char* kPtInterpPath = "/system/bin/arm64/linker64";
const constexpr char* kVdsoPath = "/system/lib64/arm64/libnative_bridge_vdso.so";
#else
#error "Unknown guest arch"
#endif

#endif  // NATIVE_BRIDGE_SUPPORT_LINKER_GUEST_ARCH_CONSTANT_H_
