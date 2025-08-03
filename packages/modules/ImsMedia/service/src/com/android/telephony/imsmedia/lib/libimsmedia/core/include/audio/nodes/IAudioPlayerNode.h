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

#ifndef IAUDIO_PLAYER_NODE_H_INCLUDED
#define IAUDIO_PLAYER_NODE_H_INCLUDED

#include <BaseNode.h>
#include <JitterBufferControlNode.h>
#include <IImsMediaThread.h>
#include <ImsMediaCondition.h>
#include <ImsMediaAudioPlayer.h>
#include <AudioConfig.h>
#include <mutex>

/**
 * @brief This class describes an interface between depacketization module and audio device
 */
class IAudioPlayerNode : public JitterBufferControlNode, IImsMediaThread
{
public:
    IAudioPlayerNode(BaseSessionCallback* callback = nullptr);
    virtual ~IAudioPlayerNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void* run();
    void ProcessCmr(const uint32_t cmrType, const uint32_t cmrDefine);
    void AdjustDelay(const int32_t delayMs);

private:
    AudioConfig* mConfig;
    std::unique_ptr<ImsMediaAudioPlayer> mAudioPlayer;
    int32_t mCodecType;
    uint32_t mMode;
    ImsMediaCondition mCondition;
    int8_t mEvsChannelAwOffset;
    kEvsBandwidth mEvsBandwidth;
    int8_t mSamplingRate;
    int32_t mEvsPayloadHeaderMode;
    int32_t mAnbrDownlinkMode;
    bool mIsDtxEnabled;
    bool mIsOctetAligned;
    uint32_t mRunningCodecMode;
};

#endif
