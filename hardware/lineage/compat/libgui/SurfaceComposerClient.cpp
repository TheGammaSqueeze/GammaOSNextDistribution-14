/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <utils/Errors.h>

using android::status_t;

extern "C" status_t _ZN7android21SurfaceComposerClient11Transaction5applyEbb(void* thisptr,
                                                                             bool synchronous,
                                                                             bool oneWay);

extern "C" status_t _ZN7android21SurfaceComposerClient11Transaction5applyEb(void* thisptr,
                                                                            bool synchronous) {
    return _ZN7android21SurfaceComposerClient11Transaction5applyEbb(thisptr, synchronous, false);
}
