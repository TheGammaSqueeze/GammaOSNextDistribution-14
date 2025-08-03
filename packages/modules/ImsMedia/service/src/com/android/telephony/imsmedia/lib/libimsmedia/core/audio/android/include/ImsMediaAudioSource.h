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

#ifndef IMSMEDIA_AUDIO_SOURCE_INCLUDED
#define IMSMEDIA_AUDIO_SOURCE_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaAudioDefine.h>
#include <mutex>
#include <IImsMediaThread.h>
#include <ImsMediaCondition.h>
#include <IFrameCallback.h>
#include <aaudio/AAudio.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#define MAX_EVS_BW_STRLEN 5

using android::sp;

class ImsMediaAudioSource : public IImsMediaThread
{
public:
    ImsMediaAudioSource();
    virtual ~ImsMediaAudioSource();

    /**
     * @brief Sets the uplink callback object to pass the encoded audio frame to the client
     *
     * @param callback the callback object
     */
    void SetUplinkCallback(IFrameCallback* callback);

    /**
     * @brief Sets the codec type
     *
     * @param type kAudioCodecType defined in ImsMediaDefine.h
     */
    void SetCodec(int32_t type);

    /**
     * @brief Sets the encoder mode.
     *
     * @param mode enum of codec bitrate
     */
    void SetCodecMode(uint32_t mode);

    /**
     * @brief Sets the bitrate of the evs encoder
     *
     * @param mode enum of codec bitrate
     */
    void SetEvsBitRate(uint32_t mode);

    /**
     * @brief Sets the ptime
     *
     * @param time Recommended length of time in milliseconds
     */
    void SetPtime(uint32_t time);

    /**
     * @brief Sets the evs bandwidth
     *
     * @param evsBandwidth enum of the bandwidth defined as the kEvsBandwidth
     */
    void SetEvsBandwidth(int32_t evsBandwidth);

    /**
     * @brief Sets the audio sampling rate in Hz units
     *
     * @param samplingRate audio sampling rate to get the audio frame in Hz units
     */
    void SetSamplingRate(int32_t samplingRate);

    /**
     * @brief Sets the evs channel aware mode offset
     *
     * @param offset Permissible values are -1, 0, 2, 3, 5, and 7. If ch-aw-recv is -1,
     * channel-aware mode is disabled
     */
    void SetEvsChAwOffset(int32_t offset);

    /**
     * @brief Sets audio media direction of the RTP session
     *
     * @param direction can be NO_FLOW, SEND_ONLY, RECEIVE_ONLY, SEND_RECEIVE, INACTIVE
     */
    void SetMediaDirection(int32_t direction);

    /**
     * @brief Set Whether discontinuous transmission is enabled or not
     *
     * @params isDtxEnabled, if set to true then enable discontinuous transmission
     */
    void SetDtxEnabled(bool isDtxEnabled);

    /**
     * @brief Setting octet-align for AMR/AMR-WB
     *
     * @params isOctetAligned, If it's set to true then all fields in the AMR/AMR-WB header
     * shall be aligned to octet boundaries by adding padding bits.
     */
    void SetOctetAligned(bool isOctetAligned);

    /**
     * @brief Starts aaudio and ndk audio codec to get the audio frame and encode the audio frames
     * with given configuration
     *
     * @return true Returns when the audio codec and aaduio starts without error
     * @return false Returns when the audio codec and aaudio configuration is invalid during the
     * start
     */
    bool Start();

    /**
     * @brief Stops the audio encoder and aaudio
     *
     */
    void Stop();

    /**
     * @brief Change bitrate of the encoding frames with given CMR value
     *
     * @param cmr The codec mode request value
     */
    void ProcessCmr(const uint32_t cmr);
    virtual void* run();

private:
    void openAudioStream();
    void restartAudioStream();
    bool startCodec();
    void stopCodec();
    void queueInputBuffer(int16_t* buffer, uint32_t size);
    void dequeueOutputBuffer();
    static void audioErrorCallback(AAudioStream* stream, void* userData, aaudio_result_t error);

    std::mutex mMutexUplink;
    IFrameCallback* mCallback;
    AAudioStream* mAudioStream;
    AMediaCodec* mCodec;
    AMediaFormat* mFormat;
    int32_t mCodecType;
    uint32_t mMode;
    uint32_t mPtime;
    uint32_t mSamplingRate;
    uint32_t mBufferSize;
    kEvsBandwidth mEvsBandwidth;
    char mEvsbandwidthStr[MAX_EVS_BW_STRLEN];
    int32_t mEvsBitRate;
    int32_t mEvsChAwOffset;
    ImsMediaCondition mConditionExit;
    bool mIsEvsInitialized;
    int32_t mMediaDirection;
    bool mIsDtxEnabled;
    bool mIsOctetAligned;
};

#endif
