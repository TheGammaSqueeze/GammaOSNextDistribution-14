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

#include <RtcpXrEncoder.h>
#include <RtcpConfig.h>
#include <ImsMediaTrace.h>
#include <limits.h>
#include <cmath>

RtcpXrEncoder::RtcpXrEncoder()
{
    mSsrc = 0;
    mSamplingRate = 16;
    mRoundTripDelay = 0;
    mVoipLossCount = 0;
    mVoipDiscardedCount = 0;
    mVoipPktCount = 0;
    mVoipLostCountInBurst = 0;
    mJitterBufferNominal = 0;
    mJitterBufferMax = 0;
    mJitterBufferAbsMax = 0;
    mVoipC11 = 0;
    mVoipC13 = 0;
    mVoipC14 = 0;
    mVoipC22 = 0;
    mVoipC23 = 0;
    mVoipC33 = 0;
    mVoipC31 = 0;
    mVoipC32 = 0;
}

RtcpXrEncoder::~RtcpXrEncoder() {}

void RtcpXrEncoder::setSsrc(const uint32_t ssrc)
{
    mSsrc = ssrc;
}

void RtcpXrEncoder::setSamplingRate(const uint32_t rate)
{
    IMLOGD1("[setSamplingRate] rate[%d]", rate);
    mSamplingRate = rate;
}

void RtcpXrEncoder::setRoundTripDelay(const uint32_t delay)
{
    IMLOGD1("[setRoundTripDelay] delay[%d]", delay);
    mRoundTripDelay = delay;
}

void RtcpXrEncoder::stackRxRtpStatus(const int32_t status, const uint32_t delay)
{
    bool packetLost = false;
    bool packetDiscarded = false;

    if (status == kRtpStatusLost)
    {
        mVoipLossCount++;
        packetLost = true;
    }
    else if (status == kRtpStatusLate || status == kRtpStatusDiscarded ||
            status == kRtpStatusDuplicated)
    {
        mVoipDiscardedCount++;
        packetDiscarded = true;
    }

    if (!packetLost && !packetDiscarded)
    {
        mVoipPktCount++;
    }
    else
    {
        if (mVoipPktCount >= G_MIN_THRESHOLD)
        {
            if (mVoipLostCountInBurst == 1)
            {
                mVoipC14++;
            }
            else
            {
                mVoipC13++;
            }

            mVoipLostCountInBurst = 1;
            mVoipC11 += mVoipPktCount;
        }
        else
        {
            mVoipLostCountInBurst++;

            if (mVoipPktCount == 0)
            {
                mVoipC33++;
            }
            else
            {
                mVoipC23++;
                mVoipC22 += (mVoipPktCount - 1);
            }
        }

        mVoipPktCount = 0;
    }

    if (status == kRtpStatusNormal && delay > mJitterBufferMax)
    {
        mJitterBufferMax = delay;
    }

    IMLOGD_PACKET8(IM_PACKET_LOG_RTCP,
            "[stackRxRtpStatus] lost[%d], discarded[%d], C11[%d], C13[%d], C14[%d], C22[%d], "
            "C23[%d], C33[%d]",
            packetLost, packetDiscarded, mVoipC11, mVoipC13, mVoipC14, mVoipC22, mVoipC23,
            mVoipC33);
}

void RtcpXrEncoder::setJitterBufferStatus(const uint32_t current, const uint32_t max)
{
    IMLOGD_PACKET2(
            IM_PACKET_LOG_RTCP, "[setJitterBufferStatus] current[%d], max[%d]", current, max);
    mJitterBufferNominal = current;
    mJitterBufferAbsMax = max;
}

bool RtcpXrEncoder::createRtcpXrReport(const uint32_t rtcpXrReport, std::list<RtpPacket*>* packets,
        std::list<LostPacket*>* lostPackets, uint16_t beginSeq, uint16_t endSeq, uint8_t* data,
        uint32_t& size)
{
    size = 0;

    if (data == nullptr || rtcpXrReport == RtcpConfig::FLAG_RTCPXR_NONE)
    {
        return false;
    }

    uint8_t* buffer = data;

    if (rtcpXrReport & RtcpConfig::FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK)
    {
        tLossReport* lossReport = createLossAnalysisReport(packets, lostPackets, beginSeq, endSeq);
        tJitterReport* jitterReport = createJitterAnalysisReport(packets, beginSeq, endSeq);
        tTTLReport* ttlReport = createTTLAnalysisReport(packets, beginSeq, endSeq);
        tDuplicateReport* duplicateReport =
                createDuplicateAnalysisReport(packets, beginSeq, endSeq);

        encodeStatisticSummeryReport(lossReport, jitterReport, ttlReport, duplicateReport, buffer);

        if (lossReport != nullptr)
        {
            delete lossReport;
        }

        if (jitterReport != nullptr)
        {
            delete jitterReport;
        }

        if (ttlReport != nullptr)
        {
            delete ttlReport;
        }

        if (duplicateReport != nullptr)
        {
            delete duplicateReport;
        }

        size += BLOCK_LENGTH_STATISTICS;
    }

    if (rtcpXrReport & RtcpConfig::FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK)
    {
        tVoIPMatricReport* voipReport = createVoIPMatricReport();
        encodeVoipMetricReport(voipReport, buffer + size);
        size += BLOCK_LENGTH_STATISTICS;

        if (voipReport != nullptr)
        {
            delete voipReport;
        }
    }

    IMLOGD_PACKET2(IM_PACKET_LOG_RTCP, "[createRtcpXrReport] rtcpXrReport[%d], size[%d]",
            rtcpXrReport, size);

    return (size > 0);
}

tLossReport* RtcpXrEncoder::createLossAnalysisReport(std::list<RtpPacket*>* packets,
        std::list<LostPacket*>* lostPackets, uint16_t beginSeq, uint16_t endSeq)
{
    tLossReport* report = new tLossReport();
    report->beginSeq = beginSeq;
    report->endSeq = endSeq;
    report->numLostPackets = 0;
    report->numPacketsReceived = 0;

    for (const auto& packet : *packets)
    {
        if (packet->seqNum >= beginSeq && packet->seqNum <= endSeq)
        {
            report->numPacketsReceived++;
        }
    }

    for (const auto& packet : *lostPackets)
    {
        if (packet->seqNum >= beginSeq && packet->seqNum <= endSeq)
        {
            for (int32_t i = 0; i < packet->numLoss; i++)
            {
                if (packet->seqNum + i > endSeq)
                {
                    break;
                }

                report->numLostPackets++;
            }
        }
    }

    IMLOGD_PACKET4(IM_PACKET_LOG_RTCP,
            "[createLossAnalysisReport] begin[%d], end[%d], lost[%d], received[%d]", beginSeq,
            endSeq, report->numLostPackets, report->numPacketsReceived);

    return report;
}

tJitterReport* RtcpXrEncoder::createJitterAnalysisReport(
        std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq)
{
    tJitterReport* report = new tJitterReport();
    report->beginSeq = beginSeq;
    report->endSeq = endSeq;

    report->minJitter = INT_MAX;
    report->maxJitter = INT_MIN;
    int64_t sumJitter = 0;
    int64_t sumJitterSqr = 0;
    uint32_t count = 0;

    for (const auto& packet : *packets)
    {
        if (packet->seqNum >= beginSeq && packet->seqNum <= endSeq)
        {
            // change units from ms to timestamp
            int32_t jitter = packet->jitter * mSamplingRate;
            // min
            if (jitter < report->minJitter)
            {
                report->minJitter = jitter;
            }

            // max
            if (jitter > report->maxJitter)
            {
                report->maxJitter = jitter;
            }

            sumJitter += jitter;
            sumJitterSqr += jitter * jitter;
            count++;
        }
    }

    count == 0 ? report->meanJitter = 0 : report->meanJitter = (double)sumJitter / count;

    report->devJitter = (int32_t)sqrt((double)(sumJitterSqr) / count -
            (double)(sumJitter) / count * (double)(sumJitter) / count);

    IMLOGD6("[createJitterAnalysisReport] begin[%d], end[%d], min[%d], max[%d], mean[%d], dev[%d]",
            beginSeq, endSeq, report->minJitter, report->maxJitter, report->meanJitter,
            report->devJitter);

    return report;
}

tTTLReport* RtcpXrEncoder::createTTLAnalysisReport(
        std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq)
{
    (void)packets;
    tTTLReport* report = new tTTLReport();
    report->beginSeq = beginSeq;
    report->endSeq = endSeq;

    // TODO: add implementation
    report->minTTL = 0;
    report->meanTTL = 0;
    report->maxTTL = 0;
    report->devTTL = 0;

    IMLOGD6("[createTTLAnalysisReport] begin[%d], end[%d], min[%d], max[%d], mean[%d], dev[%d]",
            beginSeq, endSeq, report->minTTL, report->maxTTL, report->meanTTL, report->devTTL);

    return report;
}

tDuplicateReport* RtcpXrEncoder::createDuplicateAnalysisReport(
        std::list<RtpPacket*>* packets, uint16_t beginSeq, uint16_t endSeq)
{
    tDuplicateReport* report = new tDuplicateReport();
    report->beginSeq = beginSeq;
    report->endSeq = endSeq;
    report->numDuplicatedPackets = 0;
    report->numPacketsReceived = 0;

    for (const auto& packet : *packets)
    {
        if (packet->seqNum >= beginSeq && packet->seqNum <= endSeq)
        {
            if (packet->status == kRtpStatusDuplicated)
            {
                report->numDuplicatedPackets++;
            }

            report->numPacketsReceived++;
        }
    }

    IMLOGD4("[createDuplicateAnalysisReport] begin[%d], end[%d], dup[%d], received[%d]", beginSeq,
            endSeq, report->numDuplicatedPackets, report->numPacketsReceived);

    return report;
}

tVoIPMatricReport* RtcpXrEncoder::createVoIPMatricReport()
{
    double p32 = 0;
    double p23 = 0;

    /** consider it is gap in case of (b) the period from the end of the last burst to either the
     time of the report in RFC3611 4.7.2. */
    if (mVoipPktCount != 0)
    {
        mVoipC11 += mVoipPktCount;
        mVoipPktCount = 0;
    }

    // Calculate additional transition counts.
    mVoipC31 = mVoipC13;
    mVoipC32 = mVoipC23;
    uint32_t cTotal =
            mVoipC11 + mVoipC14 + mVoipC13 + mVoipC22 + mVoipC23 + mVoipC31 + mVoipC32 + mVoipC33;

    // Calculate burst and densities.
    if (mVoipC32 == 0 || (mVoipC31 + mVoipC32 + mVoipC33) == 0)
    {
        p32 = 0;
    }
    else
    {
        p32 = static_cast<double>(mVoipC32) / (mVoipC31 + mVoipC32 + mVoipC33);
    }

    if ((mVoipC22 + mVoipC23) == 0)
    {
        p23 = 0;
    }
    else
    {
        p23 = 1 - static_cast<double>(mVoipC22) / (mVoipC22 + mVoipC23);
    }

    IMLOGD3("[createVoIPMatricReport] cTotal[%d], P23[%lf], P32[%lf]", cTotal, p23, p32);

    tVoIPMatricReport* report = new tVoIPMatricReport();
    report->ssrc = mSsrc;
    /* calculate loss and discard rates */
    report->lossRate = 255 * (double)mVoipLossCount / cTotal;
    report->discardRate = 255 * (double)mVoipDiscardedCount / cTotal;
    report->burstDensity = 255 * (double)p23 / (p23 + p32);
    report->gapDensity = 255 * (double)mVoipC14 / (mVoipC11 + mVoipC14);
    // Calculate burst and gap durations in ms
    uint32_t denum = 0;
    mVoipC13 == 0 ? denum = 1 : denum = mVoipC13;
    report->gapDuration = (mVoipC11 + mVoipC14 + mVoipC13) * 20 / denum;
    report->burstDuration = cTotal * 20 / denum - report->gapDuration;
    // get it from the rtp stack
    report->roundTripDelay = mRoundTripDelay;
    // not implemented yet
    report->endSystemDelay = 0;
    // sound signal quality - not support
    report->signalLevel = 0;
    report->noiseLevel = 0;
    report->rerl = 0;
    report->gMin = G_MIN_THRESHOLD;
    // call quaility - not support
    report->rFactor = 0;
    report->extRFactor = 0;
    report->rxConfig = 127;
    report->jitterBufferNominal = mJitterBufferNominal;
    report->jitterBufferMaximum = mJitterBufferMax;
    report->jitterBufferAbsMaximum = mJitterBufferAbsMax;

    IMLOGD6("[createVoIPMatricReport] lossRate[%d], discardRate[%d], burstDensity[%d], "
            "gapDensity[%d], gapDuration[%d], burstDuration[%d]",
            report->lossRate, report->discardRate, report->burstDensity, report->gapDensity,
            report->gapDuration, report->burstDuration);
    IMLOGD3("[createVoIPMatricReport] JBNominal[%d], JBMax[%d], JBAbsMaximum[%d]",
            report->jitterBufferNominal, report->jitterBufferMaximum,
            report->jitterBufferAbsMaximum);
    return report;
}

void RtcpXrEncoder::encodeStatisticSummeryReport(tLossReport* lossReport,
        tJitterReport* jitterReport, tTTLReport* ttlReport, tDuplicateReport* duplicateReport,
        uint8_t* data)
{
    /** The Statistics Summary Report* block format
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | Block Type = 6|L|D|J|ToH|rsvd.|      block length = 9         |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | SSRC of source                                                |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | begin_seq                     | end_seq                       |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | lost_packets                                                  |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | dup_packets                                                   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | min_jitter                                                    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | max_jitter                                                    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | mean_jitter                                                   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | dev_jitter                                                    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | min_ttl_or_hl | max_ttl_or_hl |mean_ttl_or_hl | dev_ttl_or_hl |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/
    if (data == nullptr || lossReport == nullptr || jitterReport == nullptr ||
            ttlReport == nullptr || duplicateReport == nullptr)
    {
        return;
    }

    memset(data, 0, BLOCK_LENGTH_STATISTICS);
    mBitWriter.SetBuffer(data, BLOCK_LENGTH_STATISTICS);
    mBitWriter.Write(6, 8);
    // flag of lost and duplicated packets --> always set
    mBitWriter.Write(1, 1);
    mBitWriter.Write(1, 1);
    // flag of jitter
    mBitWriter.Write(1, 1);

    // TTL and HL : 0 - not using, 1 - IPv4, 2 - IPv6, 3 must not used
    if (ttlReport->ipVersion == -1)
    {
        mBitWriter.Write(0, 2);
    }
    else if (ttlReport->ipVersion == IPV4)
    {
        mBitWriter.Write(1, 2);
    }
    else
    {
        mBitWriter.Write(2, 2);
    }

    // padding
    mBitWriter.Write(0, 3);
    // block length
    mBitWriter.Write(9, 16);
    // ssrc of source
    mBitWriter.WriteByteBuffer(mSsrc);
    // sequence number
    mBitWriter.Write(lossReport->beginSeq, 16);
    mBitWriter.Write(lossReport->endSeq, 16);
    // lost packets
    mBitWriter.WriteByteBuffer(lossReport->numLostPackets);
    // dup packets
    mBitWriter.WriteByteBuffer(duplicateReport->numDuplicatedPackets);
    IMLOGD6("[encodeStatisticSummeryReport] beginSeq[%hu], endSeq[%hu], nMinJitter[%d], "
            "nMaxJitter[%d], nMeanJitter[%d], nDevJitter[%d]",
            lossReport->beginSeq, lossReport->endSeq, jitterReport->minJitter,
            jitterReport->maxJitter, jitterReport->meanJitter, jitterReport->devJitter);
    // min, max, mean, dev jitter
    mBitWriter.WriteByteBuffer(jitterReport->minJitter);
    mBitWriter.WriteByteBuffer(jitterReport->maxJitter);
    mBitWriter.WriteByteBuffer(jitterReport->meanJitter);
    mBitWriter.WriteByteBuffer(jitterReport->devJitter);
    IMLOGD4("[encodeStatisticSummeryReport] nMinTTL[%d], nMaxTTL[%d], "
            "nMeanTTL[%d], nDevTTL[%d]",
            ttlReport->minTTL, ttlReport->maxTTL, ttlReport->meanTTL, ttlReport->devTTL);
    // min, max, mean, dev ttl/hl
    mBitWriter.Write(ttlReport->minTTL, 8);
    mBitWriter.Write(ttlReport->maxTTL, 8);
    mBitWriter.Write(ttlReport->meanTTL, 8);
    mBitWriter.Write(ttlReport->devTTL, 8);
}

void RtcpXrEncoder::encodeVoipMetricReport(tVoIPMatricReport* report, uint8_t* data)
{
    if (report == nullptr || data == nullptr)
    {
        return;
    }

    /** The VoIP Matircs Report block format
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      BT=7     |   reserved    |      block length = 8         |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | SSRC of source                                                |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | loss rate     | discard rate  | burst density | gap density   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | burst duration                | gap duration                  |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | round trip delay              | end system delay              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | signal level  | noise level   | RERL          | Gmin          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | R factor      | ext. R factor | MOS-LQ        | MOS-CQ        |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | RX config     | reserved      | JB nominal                    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    | JB maximum                    | JB abs max                    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/
    IMLOGD0("[encodeVoipMetricReport]");

    memset(data, 0, BLOCK_LENGTH_VOIP_METRICS);
    mBitWriter.SetBuffer(data, BLOCK_LENGTH_VOIP_METRICS);
    mBitWriter.Write(7, 8);
    mBitWriter.Write(0, 8);
    // block length
    mBitWriter.Write(8, 16);
    // ssrc of source
    mBitWriter.WriteByteBuffer(report->ssrc);
    mBitWriter.Write(report->lossRate, 8);
    mBitWriter.Write(report->discardRate, 8);
    mBitWriter.Write(report->burstDensity, 8);
    mBitWriter.Write(report->gapDensity, 8);
    mBitWriter.Write(report->burstDuration, 16);
    mBitWriter.Write(report->gapDuration, 16);
    mBitWriter.Write(report->roundTripDelay, 16);
    mBitWriter.Write(report->endSystemDelay, 16);
    // signal level - 127 unavailable
    mBitWriter.Write(127, 8);
    mBitWriter.Write(127, 8);
    mBitWriter.Write(127, 8);
    mBitWriter.Write(report->gMin, 8);
    // R factor - 127 unavailable
    mBitWriter.Write(127, 8);
    mBitWriter.Write(127, 8);
    // MOS - 127 unavailable
    mBitWriter.Write(127, 8);
    mBitWriter.Write(127, 8);
    // receiver configuration byte(Rx Config)
    mBitWriter.Write(report->rxConfig, 8);
    mBitWriter.Write(0, 8);
    // Jitter Buffer - milliseconds
    mBitWriter.Write(report->jitterBufferNominal, 16);
    mBitWriter.Write(report->jitterBufferMaximum, 16);
    mBitWriter.Write(report->jitterBufferAbsMaximum, 16);
}
