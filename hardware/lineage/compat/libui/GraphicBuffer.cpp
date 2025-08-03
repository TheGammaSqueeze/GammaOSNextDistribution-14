/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdint.h>
#include <utils/Errors.h>

using android::status_t;

extern "C" {
status_t _ZN7android13GraphicBuffer4lockEjPPvPiS3_(void* thisptr, uint32_t inUsage, void** vaddr,
                                                   int32_t* outBytesPerPixel,
                                                   int32_t* outBytesPerStride);

status_t _ZN7android13GraphicBuffer4lockEjPPv(void* thisptr, uint32_t inUsage, void** vaddr) {
    return _ZN7android13GraphicBuffer4lockEjPPvPiS3_(thisptr, inUsage, vaddr, nullptr, nullptr);
}
}
