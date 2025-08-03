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

#ifndef RTCP_XR_ANALYZER_H_INCLUDED
#define RTCP_XR_ANALYZER_H_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaBitWriter.h>
#include <list>
#include <vector>

#define MAX_BLOCK_LENGTH          220
#define BLOCK_LENGTH_STATISTICS   40
#define BLOCK_LENGTH_VOIP_METRICS 36
#define G_MIN_THRESHOLD           16

struct tLossReport
{
    tLossReport() :
            beginSeq(-1),
            endSeq(-1),
            numLostPackets(-1),
            numPacketsReceived(-1),
            lossRate(-1)
    {
    }
    int16_t beginSeq;
    int16_t endSeq;
    int32_t numLostPackets;
    int32_t numPacketsReceived;
    int32_t lossRate;
};

struct tTTLReport
{
    tTTLReport() :
            beginSeq(-1),
            endSeq(-1),
            ipVersion(-1),
            minTTL(-1),
            meanTTL(-1),
            maxTTL(-1),
            devTTL(-1)
    {
    }
    int16_t beginSeq;
    int16_t endSeq;
    uint8_t ipVersion;
    int32_t minTTL;
    int32_t meanTTL;
    int32_t maxTTL;
    int32_t devTTL;
};

struct tDuplicateReport
{
    tDuplicateReport() :
            beginSeq(-1),
            endSeq(-1),
            numDuplicatedPackets(-1),
            numPacketsReceived(-1)
    {
    }
    int16_t beginSeq;
    int16_t endSeq;
    int32_t numDuplicatedPackets;
    int32_t numPacketsReceived;
};

struct tJitterReport
{
    tJitterReport() :
            beginSeq(-1),
            endSeq(-1),
            minJitter(-1),
            meanJitter(-1),
            maxJitter(-1),
            devJitter(-1)
    {
    }
    int16_t beginSeq;
    int16_t endSeq;
    int32_t minJitter;
    int32_t meanJitter;
    int32_t maxJitter;
    int32_t devJitter;
};

struct tVoIPMatricReport
{
    tVoIPMatricReport() :
            ssrc(0),
            lossRate(0),
            discardRate(0),
            burstDensity(0),
            gapDensity(0),
            burstDuration(0),
            gapDuration(0),
            roundTripDelay(0),
            endSystemDelay(0),
            signalLevel(0),
            noiseLevel(0),
            rerl(0),
            gMin(0),
            rFactor(0),
            extRFactor(0),
            rxConfig(0),
            jitterBufferNominal(0),
            jitterBufferMaximum(0),
            jitterBufferAbsMaximum(0)
    {
    }
    uint32_t ssrc;
    uint32_t lossRate;
    uint32_t discardRate;
    uint32_t burstDensity;
    uint32_t gapDensity;
    uint32_t burstDuration;
    uint32_t gapDuration;
    uint32_t roundTripDelay;
    uint32_t endSystemDelay;
    uint32_t signalLevel;
    uint32_t noiseLevel;
    uint32_t rerl;
    uint32_t gMin;
    uint32_t rFactor;
    uint32_t extRFactor;
    uint8_t rxConfig;
    uint32_t jitterBufferNominal;
    uint32_t jitterBufferMaximum;
    uint32_t jitterBufferAbsMaximum;
};

class RtcpXrEncoder
{
public:
    RtcpXrEncoder();
    virtual ~RtcpXrEncoder();

    /**
     * @brief Set the receiving stream ssrc
     */
    void setSsrc(const uint32_t ssrc);

    /**
     * @brief Set the sampling rate of audio codec in kHz unit
     */
    void setSamplingRate(const uint32_t rate);

    /**
     * @brief Set the round trip delay in milliseconds unit
     */
    void setRoundTripDelay(const uint32_t delay);

    /**
     * @brief Stack receiving rtp status
     *
     * @param status The status of the audio frame how it is processed in jitter buffer
     * @param delay The delay of the audio frame from stacked in jitter buffer to play to the audio
     * codec
     */
    void stackRxRtpStatus(const int32_t status, const uint32_t delay);

    /**
     * @brief Set the jitter buffer size
     *
     * @param current The current jitter buffer size
     * @param max The maximum jitter buffer size in worst condition
     */
    void setJitterBufferStatus(const uint32_t current, const uint32_t max);

    /**
     * @brief Create a rtcp-xr report blocks
     *
     * @param nRtcpXrReport The bitmask of the enabled report block types
     * @param packets The list of the packets received to use in statistic summary report block
     * @param lostPackets The list of the lost packet counted to use in statistic summary report
     * block
     * @param beginSeq The beginning of the rtp sequence number to generate statistics summery
     * report block
     * @param endSeq The end of the rtp sequence number to generate statistics summery report block
     * @param data The data buffer to store the report block
     * @param size The size of the data buffer to stored
     * @return true The report block generated without error
     * @return false The bitmask of the report block types are zero or data buffer is null
     */
    bool createRtcpXrReport(const uint32_t nRtcpXrReport, std::list<RtpPacket*>* packets,
            std::list<LostPacket*>* lostPackets, uint16_t beginSeq, uint16_t endSeq, uint8_t* data,
            uint32_t& size);

private:
    tLossReport* createLossAnalysisReport(std::list<RtpPacket*>* packets,
            std::list<LostPacket*>* lostPackets, uint16_t beginSeq, uint16_t endSeq);
    tJitterReport* createJitterAnalysisReport(
            std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq);
    tTTLReport* createTTLAnalysisReport(
            std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq);
    tDuplicateReport* createDuplicateAnalysisReport(
            std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq);
    tVoIPMatricReport* createVoIPMatricReport();
    void encodeStatisticSummeryReport(tLossReport* lossReport, tJitterReport* jitterReport,
            tTTLReport* ttlReport, tDuplicateReport* duplicateReport, uint8_t* data);
    void encodeVoipMetricReport(tVoIPMatricReport* report, uint8_t* data);

    uint32_t mSsrc;
    uint32_t mSamplingRate;
    uint32_t mRoundTripDelay;
    uint32_t mVoipLossCount;
    uint32_t mVoipDiscardedCount;
    uint32_t mVoipPktCount;
    uint32_t mVoipLostCountInBurst;
    uint32_t mJitterBufferNominal;
    uint32_t mJitterBufferMax;
    uint32_t mJitterBufferAbsMax;

    /**
     * state 1 = received a packet during a gap
     * state 2 = received a packet during a burst
     * state 3 = lost a packet during a burst
     * state 4 = lost an isolated packet during a gap
     */
    // calculated when rtp packet received
    uint32_t mVoipC11;
    uint32_t mVoipC13;
    uint32_t mVoipC14;
    uint32_t mVoipC22;
    uint32_t mVoipC23;
    uint32_t mVoipC33;
    // calculate when create the voip report
    uint32_t mVoipC31;
    uint32_t mVoipC32;
    ImsMediaBitWriter mBitWriter;
};

#endif