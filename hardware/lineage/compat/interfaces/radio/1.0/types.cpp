/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/hardware/radio/1.0/types.h>

using android::hardware::radio::V1_0::CallState;
using android::hardware::radio::V1_0::PersoSubstate;
using android::hardware::radio::V1_0::toString;

std::string toStringNotInlined(CallState state) asm(
        "_ZN7android8hardware5radio4V1_08toStringENS2_9CallStateE");
std::string toStringNotInlined(CallState state) {
    return toString(state);
}

std::string toStringNotInlined(PersoSubstate state) asm(
        "_ZN7android8hardware5radio4V1_08toStringENS2_13PersoSubstateE");
std::string toStringNotInlined(PersoSubstate state) {
    return toString(state);
}
