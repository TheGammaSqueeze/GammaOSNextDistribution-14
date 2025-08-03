/*
 * Copyright (C) 2022-2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <gui/IGraphicBufferProducer.h>
#include <stdint.h>

using android::IBinder;
using android::IGraphicBufferProducer;
using android::sp;

extern "C" void*
_ZN7android7SurfaceC1ERKNS_2spINS_22IGraphicBufferProducerEEEbRKNS1_INS_7IBinderEEE(
        void* thisptr, const sp<IGraphicBufferProducer>& bufferProducer, bool controlledByApp,
        const sp<IBinder>& surfaceControlHandle);

extern "C" void* _ZN7android7SurfaceC1ERKNS_2spINS_22IGraphicBufferProducerEEEb(
        void* thisptr, const sp<IGraphicBufferProducer>& bufferProducer, bool controlledByApp) {
    return _ZN7android7SurfaceC1ERKNS_2spINS_22IGraphicBufferProducerEEEbRKNS1_INS_7IBinderEEE(
            thisptr, bufferProducer, controlledByApp, nullptr);
}
