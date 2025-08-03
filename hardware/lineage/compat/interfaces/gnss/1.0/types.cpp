/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/hardware/gnss/1.0/IGnssGeofenceCallback.h>
#include <android/hardware/gnss/1.0/types.h>

using android::hardware::gnss::V1_0::toString;
using GeofenceTransition = android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition;

std::string toStringNotInlined(GeofenceTransition transition) asm(
        "_ZN7android8hardware4gnss4V1_08toStringINS2_"
        "21IGnssGeofenceCallback18GeofenceTransitionEEENSt3__112basic_stringIcNS6_11char_"
        "traitsIcEENS6_9allocatorIcEEEEi");
std::string toStringNotInlined(GeofenceTransition transition) {
    return toString(transition);
}
