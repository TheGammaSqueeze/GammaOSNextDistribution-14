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

#ifndef AUDIO_STREAM_GRAPH_RTP_RX_H
#define AUDIO_STREAM_GRAPH_RTP_RX_H

#include <ImsMediaDefine.h>
#include <AudioStreamGraph.h>

class AudioStreamGraphRtpRx : public AudioStreamGraph
{
public:
    AudioStreamGraphRtpRx(BaseSessionCallback* callback, int localFd = 0);
    virtual ~AudioStreamGraphRtpRx();
    virtual ImsMediaResult create(RtpConfig* config);
    virtual ImsMediaResult update(RtpConfig* config);
    virtual ImsMediaResult start();

    /**
     * @brief Set the cmr value to change the audio mode
     *
     * @param cmrType The 3 bit of cmr type for EVS and just cmr bit for AMR/AMR-WB codec
     * @param cmrDefine The 4 bit of cmr define code for EVS and not used for AMR/AMR-WB codec
     */
    void processCmr(const uint32_t cmrType, const uint32_t cmrDefine);

    /**
     * @brief Adjust the delay in the jitter buffer to synchronize the audio with the time of video
     *        frames
     *
     * @param delayMs The additional delay to the jitter buffer in milliseconds unit. The value is
     *        always positive.
     */
    void adjustDelay(const int32_t delayMs);
};

#endif