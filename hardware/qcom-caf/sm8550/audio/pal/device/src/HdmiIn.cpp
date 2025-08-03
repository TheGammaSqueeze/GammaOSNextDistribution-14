/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#define LOG_TAG "PAL: HdmiIn"

#include "HdmiIn.h"
#include <tinyalsa/asoundlib.h>
#include "PalAudioRoute.h"
#include "ResourceManager.h"
#include "Device.h"
#include "kvh2xml.h"

std::shared_ptr<Device> HdmiIn::obj = nullptr;

void HdmiIn::releaseObject() {
    if (obj) {
        PAL_INFO(LOG_TAG, "use_count: %d", obj.use_count());
        obj.reset();
    }
}


std::shared_ptr<Device> HdmiIn::getInstance(struct pal_device *device,
                                                std::shared_ptr<ResourceManager> Rm)
{
    if (!obj) {
        std::shared_ptr<Device> sp(new HdmiIn(device, Rm));
        obj = sp;
    }
    return obj;
}

std::shared_ptr<Device> HdmiIn::getObject(pal_device_id_t id)
{
    if ((id == PAL_DEVICE_IN_AUX_DIGITAL) ||
        (id == PAL_DEVICE_IN_HDMI)) {
        if (obj) {
            if (obj->getSndDeviceId() == id)
                return obj;
        }
    }
    return NULL;
}

HdmiIn::HdmiIn(struct pal_device *device, std::shared_ptr<ResourceManager> Rm) :
Device(device, Rm)
{

}

HdmiIn::~HdmiIn()
{

}

int32_t HdmiIn::isSampleRateSupported(uint32_t sampleRate)
{
    int32_t rc = 0;
    PAL_DBG(LOG_TAG, "sampleRate %u", sampleRate);
    switch (sampleRate) {
       //check what all need to be added
        case SAMPLINGRATE_8K:
        case SAMPLINGRATE_16K:
        case SAMPLINGRATE_44K:
        case SAMPLINGRATE_48K:
        case SAMPLINGRATE_96K:
        case SAMPLINGRATE_192K:
        case SAMPLINGRATE_384K:
            break;
        default:
            rc = -EINVAL;
            PAL_ERR(LOG_TAG, "sample rate not supported rc %d", rc);
            break;
    }
    return rc;
}

int32_t HdmiIn::isChannelSupported(uint32_t numChannels)
{
    int32_t rc = 0;
    PAL_DBG(LOG_TAG, "numChannels %u", numChannels);
    switch (numChannels) {
    //check what all needed
       case CHANNELS_1:
       case CHANNELS_2:
            break;
        default:
            rc = -EINVAL;
            PAL_ERR(LOG_TAG, "channels not supported rc %d", rc);
            break;
    }
    return rc;
}

int32_t HdmiIn::isBitWidthSupported(uint32_t bitWidth)
{
    int32_t rc = 0;
    PAL_DBG(LOG_TAG, "bitWidth %u", bitWidth);
    switch (bitWidth) {
        case BITWIDTH_16:
            break;
        default:
            rc = -EINVAL;
            PAL_ERR(LOG_TAG, "bit width not supported rc %d", rc);
            break;
    }
    return rc;
}

int32_t HdmiIn::checkAndUpdateBitWidth(uint32_t *bitWidth)
{
    int32_t rc = 0;
    PAL_DBG(LOG_TAG, "bitWidth %u", *bitWidth);
    switch (*bitWidth) {
        case BITWIDTH_16:
            break;
        default:
            *bitWidth = BITWIDTH_16;
            PAL_DBG(LOG_TAG, "bit width not supported, setting to default 16 bit");
            break;
    }
    return rc;
}

int32_t HdmiIn::checkAndUpdateSampleRate(uint32_t *sampleRate)
{
    int32_t rc = 0;

    if (*sampleRate <= SAMPLINGRATE_48K)
        *sampleRate = SAMPLINGRATE_48K;
    else if (*sampleRate > SAMPLINGRATE_48K && *sampleRate <= SAMPLINGRATE_96K)
        *sampleRate = SAMPLINGRATE_96K;
    else if (*sampleRate > SAMPLINGRATE_96K && *sampleRate <= SAMPLINGRATE_192K)
        *sampleRate = SAMPLINGRATE_192K;
    else if (*sampleRate > SAMPLINGRATE_192K && *sampleRate <= SAMPLINGRATE_384K)
        *sampleRate = SAMPLINGRATE_384K;

    PAL_DBG(LOG_TAG, "sampleRate %d", *sampleRate);

    return rc;
}
