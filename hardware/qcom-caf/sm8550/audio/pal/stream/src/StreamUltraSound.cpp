/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2022-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#define LOG_TAG "PAL: StreamUltraSound"

#include "StreamUltraSound.h"
#include "Session.h"
#include "SessionAlsaPcm.h"
#include "ResourceManager.h"
#include "Device.h"
#include "us_detect_api.h"
#include <unistd.h>

StreamUltraSound::StreamUltraSound(const struct pal_stream_attributes *sattr __unused, struct pal_device *dattr __unused,
                    const uint32_t no_of_devices __unused, const struct modifier_kv *modifiers __unused,
                    const uint32_t no_of_modifiers __unused, const std::shared_ptr<ResourceManager> rm):
                  StreamCommon(sattr,dattr,no_of_devices,modifiers,no_of_modifiers,rm)
{
    gain = PAL_ULTRASOUND_GAIN_MUTE;
    session->registerCallBack((session_callback)HandleCallBack,((uint64_t) this));
    rm->registerStream(this);
}

StreamUltraSound::~StreamUltraSound()
{
    rm->resetStreamInstanceID(this);
    rm->deregisterStream(this);
}

int32_t StreamUltraSound::start()
{
    int32_t status = 0;
    struct pal_device dAttr;
    pal_ultrasound_gain_t gain = PAL_ULTRASOUND_GAIN_LOW;
    std::vector<std::shared_ptr<Device>> activeDeviceList;

    PAL_DBG(LOG_TAG, "Enter");

    status = StreamCommon::start();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "StreamCommon::start() failed, status = %d", status);
        return status;
    }

    if (!rm->IsCustomGainEnabledForUPD())
        goto skip_upd_set_gain;

    /* Set Ultrasound Gain based on currently active devices */
    rm->getActiveDevices(activeDeviceList);
    if (0 == activeDeviceList.size()) {
        PAL_DBG(LOG_TAG, "Did not find any active device, skip setting Ultrasound gain");
        goto skip_upd_set_gain;
    }

    for (int i = 0; i < activeDeviceList.size(); i++) {
        status = activeDeviceList[i]->getDeviceAttributes(&dAttr);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Fail to get device attribute for device %p, status = %d",
                    &activeDeviceList[i], status);
            continue;
        }
        if (PAL_DEVICE_OUT_SPEAKER == dAttr.id) {
            gain = PAL_ULTRASOUND_GAIN_HIGH;
        }
    }

    mStreamMutex.lock();
    status = setUltraSoundGain_l(gain);
    if (0 != status) {
        mStreamMutex.unlock();
        PAL_ERR(LOG_TAG, "Ultrasound set gain failed, status = %d", status);
        goto skip_upd_set_gain;
    }
    mStreamMutex.unlock();
    PAL_INFO(LOG_TAG, "Ultrasound gain(%d) set sucessfully", gain);

skip_upd_set_gain:
    PAL_DBG(LOG_TAG, "Exit status: %d", status);
    return status;
}


int32_t StreamUltraSound::stop()
{
    int32_t status = 0;
    pal_ultrasound_gain_t gain = PAL_ULTRASOUND_GAIN_MUTE;

    PAL_DBG(LOG_TAG, "Enter");

    if (rm->IsCustomGainEnabledForUPD()) {
        mStreamMutex.lock();
        if (currentState == STREAM_STARTED || currentState == STREAM_PAUSED) {

            status = setUltraSoundGain_l(PAL_ULTRASOUND_GAIN_MUTE);
            if (0 != status) {
                PAL_ERR(LOG_TAG, "Ultrasound set gain failed, status = %d", status);
            }
            /* Currently configured value is 20ms which allows 3 to 4 process call
             * to handle this value at ADSP side.
             * Increase or decrease this dealy based on requirements */
            usleep(20000);
        }
        mStreamMutex.unlock();
    }

    status = StreamCommon::stop();
    if (0 != status)
        PAL_ERR(LOG_TAG, "StreamCommon::stop() failed, status = %d", status);

    return status;
}

int32_t  StreamUltraSound::setParameters(uint32_t param_id, void *payload)
{
    int32_t status = 0;

    if (!payload)
    {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "invalid params");
        goto error;
    }

    mStreamMutex.lock();
    if (currentState == STREAM_IDLE) {
        PAL_ERR(LOG_TAG, "Invalid stream state: IDLE for param ID: %d", param_id);
        mStreamMutex.unlock();
        return -EINVAL;
    }
    // Stream may not know about tags, so use setParameters instead of setConfig
    switch (param_id) {
        case PAL_PARAM_ID_UPD_REGISTER_FOR_EVENTS:
        {
            status = session->setParameters(NULL, 0, param_id, payload);
            if (status)
                PAL_ERR(LOG_TAG, "Error:%d, Failed to setParam for registering an event",
                status);
            break;
        }
        default:
            PAL_ERR(LOG_TAG, "Error:Unsupported param id %u", param_id);
            status = -EINVAL;
            break;
    }

    mStreamMutex.unlock();
    PAL_DBG(LOG_TAG, "exit, session parameter %u set with status %d", param_id, status);
error:
    return status;
}

void StreamUltraSound::HandleEvent(uint32_t event_id, void *data, uint32_t event_size) {
    struct event_id_upd_detection_event_t *event_info = nullptr;
    event_info = (struct event_id_upd_detection_event_t *)data;
    uint32_t event_type = event_info->proximity_event_type;

    PAL_INFO(LOG_TAG, "%s event received %d",
            (event_type == US_DETECT_NEAR)? "NEAR": "FAR", event_type);

    if (callback_) {
        PAL_INFO(LOG_TAG, "Notify detection event to client");
        mStreamMutex.lock();
        callback_((pal_stream_handle_t *)this, event_id, &event_type,
                  event_size, cookie_);
        mStreamMutex.unlock();
    }
}

void StreamUltraSound::HandleCallBack(uint64_t hdl, uint32_t event_id,
                                      void *data, uint32_t event_size, uint32_t miid __unused) {
    StreamUltraSound *StreamUPD = nullptr;
    PAL_DBG(LOG_TAG, "Enter, event detected on SPF, event id = 0x%x, event size =%d",
                      event_id, event_size);
    if (event_id == EVENT_ID_GENERIC_US_DETECTION) {
        StreamUPD = (StreamUltraSound *)hdl;
        StreamUPD->HandleEvent(event_id, data, event_size);
    }
    PAL_DBG(LOG_TAG, "Exit");
}

int32_t StreamUltraSound::setUltraSoundGain(pal_ultrasound_gain_t new_gain)
{
    int32_t status = 0;
    PAL_INFO(LOG_TAG, "Entered, gain %d", new_gain);

    if (!rm->IsCustomGainEnabledForUPD()) {
        PAL_ERR(LOG_TAG,"Custom Gain not enabled for UPD, returning");
        return status;
    }

    mStreamMutex.lock();
    if (STREAM_STARTED == currentState)
        status = setUltraSoundGain_l(new_gain);
    else
        status = -EINVAL;
    mStreamMutex.unlock();

    return status;
}

int32_t StreamUltraSound::setUltraSoundGain_l(pal_ultrasound_gain_t new_gain)
{
    int32_t status = 0;
    pal_ultrasound_gain_t mute = PAL_ULTRASOUND_GAIN_MUTE;

    if (!rm->IsCustomGainEnabledForUPD()) {
        PAL_ERR(LOG_TAG,"Custom Gain not enabled for UPD, returning");
        return status;
    }

    PAL_DBG(LOG_TAG, "Received request to set Ultrasound gain(%d)", new_gain);

    if (gain != new_gain) {

        if ((gain != PAL_ULTRASOUND_GAIN_MUTE) && (new_gain != PAL_ULTRASOUND_GAIN_MUTE)) {
            /* For scanarios cases like, UPD followed by Music/Audio Playback,
             * in order to avoid sending gain LOW follwed by HIGH directly,
             * here we will send MUTE followed by some delay so module can rampdown
             * previous gain first before applying new gain */
            status = session->setParameters(this, TAG_ULTRASOUND_GAIN,
                            PAL_PARAM_ID_ULTRASOUND_SET_GAIN, &mute);
            if (status) {
                PAL_ERR(LOG_TAG, "Error:%d, Failed to setParam for Ultrasound set gain",
                        status);
            }
            gain = mute;
            PAL_DBG(LOG_TAG, "Ultrasound gain(%d), configured successfully", gain);

            /* Currently configured value is 20ms which allows 3 to 4 process call
             * to handle this value at ADSP side.
             * Increase or decrease this dealy based on requirements */
            usleep(20000);
        }

        status = session->setParameters(this, TAG_ULTRASOUND_GAIN, PAL_PARAM_ID_ULTRASOUND_SET_GAIN, &new_gain);
        if (status) {
            PAL_ERR(LOG_TAG, "Error:%d, Failed to setParam for Ultrasound set gain",
                    status);
        }
        gain = new_gain;
        PAL_DBG(LOG_TAG, "Ultrasound gain(%d), configured successfully", gain);
    } else {
        PAL_DBG(LOG_TAG, "Ultrasound gain(%d), already configured", gain);
    }
    return status;
}
