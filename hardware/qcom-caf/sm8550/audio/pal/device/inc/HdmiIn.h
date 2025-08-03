/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#ifndef HDMIIN_H
#define HDMIIN_H

#include "Device.h"

class HdmiIn : public Device
{
protected:
    static std::shared_ptr<Device> obj;
    HdmiIn(struct pal_device *device, std::shared_ptr<ResourceManager> Rm);
public:
    static std::shared_ptr<Device> getInstance(struct pal_device *device,
                                               std::shared_ptr<ResourceManager> Rm);
    static int32_t isSampleRateSupported(uint32_t sampleRate);
    static int32_t isChannelSupported(uint32_t numChannels);
    static int32_t isBitWidthSupported(uint32_t bitWidth);
    static int32_t checkAndUpdateBitWidth(uint32_t *bitWidth);
    static int32_t checkAndUpdateSampleRate(uint32_t *sampleRate);
    static std::shared_ptr<Device> getObject(pal_device_id_t id);
    static void releaseObject();
    virtual ~HdmiIn();
};


#endif //HDMIIN_H
