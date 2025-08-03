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

#ifndef AUDIO_STREAM_GRAPH_RTP_TX_H
#define AUDIO_STREAM_GRAPH_RTP_TX_H

#include <ImsMediaDefine.h>
#include <AudioStreamGraph.h>
#include <RtpHeaderExtension.h>

class AudioStreamGraphRtpTx : public AudioStreamGraph
{
public:
    AudioStreamGraphRtpTx(BaseSessionCallback* callback, int localFd = 0);
    virtual ~AudioStreamGraphRtpTx();
    virtual ImsMediaResult create(RtpConfig* config);
    virtual ImsMediaResult update(RtpConfig* config);
    virtual ImsMediaResult start();

    /**
     * @brief Create a graph for send dtmf digit to network
     *
     * @param config AudioConfig for setting the parameters for nodes
     * @param rtpEncoderNode The RtpEncoderNode instance to connect as a rear node after the
     * DtmfSenderNode, if it is null, no dtmf packet will be delivered to RtpEncoderNode.
     * @return true Returns when the graph created without error
     * @return false Returns when the given parameters are invalid.
     */
    bool createDtmfGraph(RtpConfig* config, BaseNode* rtpEncoderNode);

    /**
     * @brief Creates and send dtmf packet to the network through the node created
     *
     * @param digit A digit to send as a RtpEvent packet
     * @param duration The milliseconds unit of duration how long to send the dtmf packet.
     * @return true Returns true when the dtmf digit is sent without error.
     * @return false Returns false when the dtmf digit cannot be sent to the node.
     */
    bool sendDtmf(char digit, int duration);

    /**
     * @brief Set the cmr value to change the audio mode
     *
     * @param cmrType The 3 bit of cmr type for EVS and just cmr bit for AMR/AMR-WB codec
     * @param cmrDefine The 4 bit of cmr define code for EVS and not used for AMR/AMR-WB codec
     */
    void processCmr(const uint32_t cmrType, const uint32_t cmrDefine = 0);

    /**
     * @brief Send rtp header extension to the audio rtp
     *
     * @param listExtension The list of rtp header extension data
     */
    void sendRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension);

private:
    std::list<BaseNode*> mListDtmfNodes;
};

#endif