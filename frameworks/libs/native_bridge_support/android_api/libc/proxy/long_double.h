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

#ifndef NATIVE_BRIDGE_SUPPORT_ANDROID_API_LIBC_LONG_DOUBLE_H_
#define NATIVE_BRIDGE_SUPPORT_ANDROID_API_LIBC_LONG_DOUBLE_H_

namespace berberis {

// On ILP32 Android, long double is identical to double, while long double is
// usually 80bit under BMM x86. -mlong-double-64 can be used to force 64bit
// long double, but the flag is not supported by clang yet, so we workaround
// it by using this typedef instead of "long double".
// TODO(crbug.com/432441): Once our clang starts supporting -mlong-double-64,
// remove this typedef and just use long double everywhere.
typedef double android_long_double_t;

}  // namespace berberis

#endif  // NATIVE_BRIDGE_SUPPORT_ANDROID_API_LIBC_LONG_DOUBLE_H_
