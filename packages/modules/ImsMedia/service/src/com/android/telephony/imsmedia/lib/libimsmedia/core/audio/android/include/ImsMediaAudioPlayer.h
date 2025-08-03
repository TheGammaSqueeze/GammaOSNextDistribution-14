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

#ifndef IMSMEDIA_AUDIO_PLAYER_INCLUDED
#define IMSMEDIA_AUDIO_PLAYER_INCLUDED

#include <ImsMediaAudioDefine.h>
#include <aaudio/AAudio.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <mutex>

using android::sp;

enum FrameType : uint8_t
{
    SPEECH = 0,
    SID,
    LOST,
    NO_DATA
};

class ImsMediaAudioPlayer
{
public:
    ImsMediaAudioPlayer();
    virtual ~ImsMediaAudioPlayer();

    /**
     * @brief Sets the codec type
     *
     * @param type kAudioCodecType defined in ImsMediaDefine.h
     */
    void SetCodec(int32_t type);

    /**
     * @brief Sets the evs bitrate converted from codec mode
     *
     * @param mode
     */
    void SetEvsBitRate(int32_t bitRate);

    /**
     * @brief Sets the Sampling rate of the audio player
     *
     * @param samplingRate
     */
    void SetSamplingRate(int32_t samplingRate);

    /**
     * @brief sets the Evs Codec mode.
     *
     * @param mode used to define evs codec mode.
     */
    void SetCodecMode(uint32_t mode);

    /**
     * @brief Sets the EVS codec offset of the channel aware mode
     *
     * @param offset Permissible values are -1, 0, 2, 3, 5, and 7. If ch-aw-recv is -1,
     * channel-aware mode is disabled
     */
    void SetEvsChAwOffset(int32_t offset);

    /**
     * @brief Sets the bandwidth of the EVS codec.
     *
     * @param evsBandwidth kEvsBandwidth defined in ImsMediaDefine.h
     */
    void SetEvsBandwidth(int32_t evsBandwidth);

    /**
     * @brief Sets the payload header mode of the EVS codec.
     *
     * @param EvsPayloadHeaderMode kRtpPayloadHeaderMode defined in ImsMediaDefine.h
     */
    void SetEvsPayloadHeaderMode(int32_t EvsPayloadHeaderMode);

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
     * @brief Set the cmr value to change the audio mode
     *
     * @param cmr The 4 bit of cmr define code for EVS modes and not used for AMR/AMR-WB codec
     */
    void ProcessCmr(const uint32_t cmr);

    /**
     * @brief Starts audio player to play the decoded audio frame and ndk audio decoder to decode
     * the given data
     *
     * @return true Returns when the audio codec and aaudio runs without error
     * @return false Returns when the audio codec configuration is invalid gets error during the
     * launch
     */
    bool Start();

    /**
     * @brief Stops audio player to stop the aaudio and ndk audio decoder
     *
     */
    void Stop();

    /**
     * @brief decodeFrame sends encoded audio frame for decoding.
     *
     * @param decoderInput compressed audio frame passed to AoC for decode and playback.
     * @param nDataSize compressed frame size as per negotiated bitrate.
     * @param frameType
     *          0 - SPEECH frame
     *          1 - SID
     *              size > 0 : SID packet.
     *              size = 0 : SID interval.
     *          2 - LOST frame.
     *              size > 0 if Partial Frame is present and buffer contains partial frame (EVS
     * only). size = 0 iF Partial Frame is not present. 3 - NO_DATA : SID interval
     * @param hasNextFrame true when next frame is available in JitterBuffer (EVS only). false
     * otherwise.
     * @param nextFrameFirstByte first byte of next frame if available in JitterBuffer (EVS only). 0
     * otherwise.
     *
     * @return true on audio frame decoded successfully.
     * @return false on audio frame decode failure.
     */
    virtual bool onDataFrame(uint8_t* buffer, uint32_t size, FrameType frameType, bool hasNextFrame,
            uint8_t nextFrameByte);

private:
    void openAudioStream();
    void restartAudioStream();
    static void audioErrorCallback(AAudioStream* stream, void* userData, aaudio_result_t error);
    bool decodeAmr(uint8_t* buffer, uint32_t size);
    bool decodeEvs(uint8_t* buffer, uint32_t size);

    AAudioStream* mAudioStream;
    AMediaCodec* mCodec;
    AMediaFormat* mFormat;
    int32_t mCodecType;
    uint32_t mCodecMode;
    int32_t mSamplingRate;
    int32_t mEvsChAwOffset;
    kEvsBandwidth mEvsBandwidth;
    uint16_t mBuffer[PCM_BUFFER_SIZE];
    std::mutex mMutex;
    int32_t mEvsBitRate;
    kRtpPayloadHeaderMode mEvsCodecHeaderMode;
    bool mIsFirstFrame;
    bool mIsEvsInitialized;
    bool mIsDtxEnabled;
    bool mIsOctetAligned;
};

#endif
