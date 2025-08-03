/**
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef IAUDIO_SOURCE_NODE_H_INCLUDED
#define IAUDIO_SOURCE_NODE_H_INCLUDED

#include <BaseNode.h>
#include <ImsMediaAudioSource.h>
#include <IFrameCallback.h>

/**
 * @brief This class is interface between audio device and ims media packetization node
 */
class IAudioSourceNode : public BaseNode, IFrameCallback
{
public:
    IAudioSourceNode(BaseSessionCallback* callback = nullptr);
    virtual ~IAudioSourceNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult ProcessStart();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsRunTimeStart();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);

    /**
     * @brief Uplink callback of audio invoked when the audio read the valid audio frames from the
     * device
     *
     * @param buffer The data frame
     * @param size The size of the data frame
     * @param timestamp The timestamp of the data in milliseconds unit.
     * @param flag The flags of the frame
     */
    void onDataFrame(uint8_t* buffer, uint32_t size, int64_t timestamp, uint32_t flag);

    /**
     * @brief Change the bitrate with given cmr value
     *
     * @param cmr The cmr value to change. The value will be 0-7 for AMR, or 0-8 for AMR-WB. CMR
     * value 15 indicates that no mode request is present, and other values are for future use.
     */
    void ProcessCmr(const uint32_t cmrType, const uint32_t cmrDefine = 0);

    /**
     * @brief Trigger Anbr Query to change the bitrate with given cmr value
     *
     * @param cmr The cmr value to change. The value will be 0-7 for AMR, or 0-8 for AMR-WB. CMR
     * value 15 indicates that no mode request is present, and other values are for future use.
     */
    void triggerAnbrQuery(uint32_t cmr);

public:
    bool mFirstFrame;
    std::unique_ptr<ImsMediaAudioSource> mAudioSource;
    int32_t mCodecType;
    uint32_t mCodecMode;
    uint32_t mRunningCodecMode;
    int8_t mPtime;
    kEvsBandwidth mEvsBandwidth;
    int8_t mSamplingRate;
    int8_t mEvsChAwOffset;
    int32_t mMediaDirection;
    int32_t mAnbrUplinkMode;
    bool mIsDtxEnabled;
    bool mIsOctetAligned;
};

#endif
