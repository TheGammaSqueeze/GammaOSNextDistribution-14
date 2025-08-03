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

#ifndef AUDIO_SESSION_H
#define AUDIO_SESSION_H

#include <ImsMediaDefine.h>
#include <BaseSession.h>
#include <AudioStreamGraphRtpTx.h>
#include <AudioStreamGraphRtpRx.h>
#include <AudioStreamGraphRtcp.h>
#include <RtpConfig.h>
#include <MediaQualityAnalyzer.h>
#include <RtpHeaderExtension.h>
#include <list>

class AudioSession : public BaseSession
{
public:
    AudioSession();
    virtual ~AudioSession();
    virtual SessionState getState();
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2);
    virtual void setMediaQualityThreshold(const MediaQualityThreshold& threshold);
    virtual ImsMediaResult startGraph(RtpConfig* config);

    /**
     * @brief Add and start stream graph instance of the session. It has to be called only to create
     *        new StreamGraph should be added with different RtpConfig as a argument.
     *
     * @param config The parameters to operate nodes in the StreamGraph.
     * @param enableRtcp {@code true} when the rtcp needs to be enabled to the rest of the graphs
     * @return ImsMediaResult result of create or start graph. If the result has no error, it
     *         returns RESULT_SUCCESS. check #ImsMediaDefine.h.
     */
    ImsMediaResult addGraph(RtpConfig* config, bool enableRtcp);

    /**
     * @brief Determine to remain only one StreamGraph instance and remove other StreamGraph. If the
     *        target StreamGraph is not in RUN state, call start instance to change to RUN state.
     *        when the call session is converted to confirmed session. It has to be called with
     *        proper RtpConfig argument that can choose the StreamGraph with the config. If there is
     *        no matched StreamGraph with same RtpConfig, it returns failure of
     *        RESULT_INVALID_PARAM.
     *
     * @param config The parameters to operate nodes in the StreamGraph.
     * @return ImsMediaResult result of create or start graph. If the result has no error, it
     *         returns RESULT_SUCCESS. check #ImsMediaDefine.h.
     */
    ImsMediaResult confirmGraph(RtpConfig* config);

    /**
     * @brief Delete a StreamGraph which has a matched RtpConfig argument.
     *
     * @param config A parameter to find the matching StreamGraph instance.
     * @return ImsMediaResult A result of deleting StreamGraph instance. If the result has no error,
     *         it returns RESULT_SUCCESS. check #ImsMediaDefine.h.
     */
    ImsMediaResult deleteGraph(RtpConfig* config);

    /**
     * @brief Send Dtmf digit to the network
     *
     * @param digit A digit character
     * @param duration The duration of millisecond unit indicate how long to send the digit as a rtp
     * event packet
     */
    void sendDtmf(char digit, int duration);

    /**
     * @brief Request the current rtp reception statistics params for checking the current status of
     *        the rtp stream. It will trigger the notifyRtpReceptionStats() with the
     *        RtpReceptionStats.
     *
     * @param intervalMs The interval of the time in milliseconds of the rtp reception notification
     */
    void requestRtpReceptionStats(int32_t intervalMs);

    /**
     * @brief Adjust the delay in the jitter buffer to synchronize the audio with the time of video
     *        frames
     *
     * @param delayMs The additional delay to the jitter buffer in milliseconds unit. The value is
     *        always positive.
     */
    void adjustDelay(const int32_t delayMs);

    /**
     * @brief Send internal event to process in the stream graph
     *
     * @param type The type of internal event defined in ImsMediaDefine.h
     * @param param1 The additional parameter to set
     * @param param2 The additional parameter to set
     */
    void SendInternalEvent(int32_t type, uint64_t param1, uint64_t param2);

    /**
     * @brief Check whether the graph has the same remote address or not
     *
     * @param config The RtpConfig to check the remote address
     */
    bool IsGraphAlreadyExist(RtpConfig* config);

    /**
     * @brief Get graph list size with repective stream type
     *
     * @param type The graph type to fetch
     * @return uint32_t The size of list
     */
    uint32_t getGraphSize(ImsMediaStreamType type);

    /**
     * @brief Send rtp header extension to the audio rtp
     *
     * @param listExtension The list of rtp header extension data
     */
    void sendRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension);

    /**
     * @brief Move session to inactive. i.e., Rtp Tx and Rtp Rx graphs are stopped
     *
     * @return bool false if failed to move session to inactive
     */
    bool deactivate();

private:
    std::list<AudioStreamGraphRtpTx*> mListGraphRtpTx;
    std::list<AudioStreamGraphRtpRx*> mListGraphRtpRx;
    std::list<AudioStreamGraphRtcp*> mListGraphRtcp;
    std::unique_ptr<MediaQualityAnalyzer> mMediaQualityAnalyzer;
};

#endif