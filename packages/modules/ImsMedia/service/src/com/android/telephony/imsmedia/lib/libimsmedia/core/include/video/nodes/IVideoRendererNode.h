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

#ifndef IVIDEO_RENDERER_NODE_H_INCLUDED
#define IVIDEO_RENDERER_NODE_H_INCLUDED

#include <BaseNode.h>
#include <JitterBufferControlNode.h>
#include <IImsMediaThread.h>
#include <ImsMediaCondition.h>
#include <ImsMediaVideoRenderer.h>
#include <ImsMediaVideoUtil.h>
#include <android/native_window.h>
#include <mutex>

#define USE_JITTER_BUFFER  // off this definition ONLY for test purpose.
#define DEMON_NTP2MSEC 65.55

enum FrameType
{
    UNKNOWN,
    SPS,
    PPS,
    VPS,
    IDR,
    NonIDR,
};

/**
 * @brief This class describes an interface between depacketization module and audio device
 */
class IVideoRendererNode : public JitterBufferControlNode
{
public:
    IVideoRendererNode(BaseSessionCallback* callback = nullptr);
    virtual ~IVideoRendererNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void ProcessData();

    /**
     * @brief Updates display surface
     *
     * @param window surface buffer to update
     */
    void UpdateSurface(ANativeWindow* window);

    /**
     * @brief Update network round trip time delay to the VideoJitterBuffer
     *
     * @param delay time delay in ntp timestamp unit
     */
    void UpdateRoundTripTimeDelay(int32_t delay);

    /**
     * @brief Set the packet loss monitoring duration and packet loss rate threshold
     *
     * @param time The time duration of milliseconds unit to monitor the packet loss
     * @param rate The packet loss rate threshold in the monitoring duration range
     */
    void SetPacketLossParam(uint32_t time, uint32_t rate);

private:
    bool hasStartingCode(uint8_t* buffer, uint32_t bufferSize);
    FrameType GetFrameType(uint8_t* buffer, uint32_t bufferSize);
    void SaveConfigFrame(uint8_t* buffer, uint32_t bufferSize, uint32_t type);
    bool RemoveAUDNalUnit(
            uint8_t* inBuffer, uint32_t ibufferSize, uint8_t** outBuffer, uint32_t* outBufferSize);
    void CheckResolution(uint32_t nWidth, uint32_t nHeight);
    ImsMediaResult ParseAvcSps(uint8_t* buffer, uint32_t bufferSize, tCodecConfig* config);
    ImsMediaResult ParseHevcSps(uint8_t* buffer, uint32_t bufferSize, tCodecConfig* config);
    void QueueConfigFrame(uint32_t timestamp);
    void NotifyPeerDimensionChanged();

    std::unique_ptr<ImsMediaVideoRenderer> mVideoRenderer;
    ANativeWindow* mWindow;
    ImsMediaCondition mCondition;
    std::mutex mMutex;
    int32_t mCodecType;
    uint32_t mWidth;
    uint32_t mHeight;
    int8_t mSamplingRate;
    int32_t mCvoValue;
    uint8_t mConfigBuffer[MAX_CONFIG_INDEX][MAX_CONFIG_LEN];
    uint32_t mConfigLen[MAX_CONFIG_INDEX];
    uint8_t mBuffer[MAX_RTP_PAYLOAD_BUFFER_SIZE];
    uint32_t mDeviceOrientation;
    bool mFirstFrame;
    ImsMediaSubType mSubtype;
    uint32_t mFramerate;
    uint32_t mLossDuration;
    uint32_t mLossRateThreshold;
    uint32_t mPrevTimestamp;
    uint32_t mCurrentFramerate;
};

#endif