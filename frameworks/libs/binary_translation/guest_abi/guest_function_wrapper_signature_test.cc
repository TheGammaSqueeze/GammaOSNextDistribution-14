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

#include <cstdint>

#include "berberis/guest_abi/guest_function_wrapper_signature.h"

namespace berberis {

namespace {

static_assert('i' == kGuestFunctionWrapperSignatureChar<bool>);
static_assert('i' == kGuestFunctionWrapperSignatureChar<char>);
static_assert('i' == kGuestFunctionWrapperSignatureChar<int>);
static_assert('l' == kGuestFunctionWrapperSignatureChar<long long>);
static_assert('p' == kGuestFunctionWrapperSignatureChar<void*>);

static_assert('i' == kGuestFunctionWrapperSignatureChar<int32_t>);
static_assert('l' == kGuestFunctionWrapperSignatureChar<int64_t>);

static_assert('f' == kGuestFunctionWrapperSignatureChar<float>);
static_assert(sizeof(int32_t) == sizeof(float));
static_assert('d' == kGuestFunctionWrapperSignatureChar<double>);
static_assert(sizeof(int64_t) == sizeof(double));

using f1 = void();
static_assert(2 == sizeof(kGuestFunctionWrapperSignature<f1>));
static_assert('v' == kGuestFunctionWrapperSignature<f1>[0]);
static_assert('\0' == kGuestFunctionWrapperSignature<f1>[1]);

using pf1 = void (*)();
static_assert(2 == sizeof(kGuestFunctionWrapperSignature<pf1>));
static_assert('v' == kGuestFunctionWrapperSignature<pf1>[0]);
static_assert('\0' == kGuestFunctionWrapperSignature<pf1>[1]);

using f2 = int(double, double);
static_assert(4 == sizeof(kGuestFunctionWrapperSignature<f2>));
static_assert('i' == kGuestFunctionWrapperSignature<f2>[0]);
static_assert('d' == kGuestFunctionWrapperSignature<f2>[1]);
static_assert('d' == kGuestFunctionWrapperSignature<f2>[2]);
static_assert('\0' == kGuestFunctionWrapperSignature<f2>[3]);

}  // namespace

}  // namespace berberis
