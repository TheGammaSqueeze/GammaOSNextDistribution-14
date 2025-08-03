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

#ifndef MEDIA_QUALITY_ANALYZER_H_INCLUDED
#define MEDIA_QUALITY_ANALYZER_H_INCLUDED

#include <CallQuality.h>
#include <ImsMediaDefine.h>
#include <IImsMediaThread.h>
#include <ImsMediaCondition.h>
#include <RtcpXrEncoder.h>
#include <BaseSessionCallback.h>
#include <AudioConfig.h>
#include <MediaQualityThreshold.h>
#include <MediaQualityStatus.h>
#include <list>
#include <vector>
#include <mutex>
#include <algorithm>

class HysteresisTimeChecker
{
public:
    HysteresisTimeChecker(int32_t time = 0)
    {
        hysteresisTime = time;
        countHysteresisTime = hysteresisTime;
        notifiedDirection = 1;
        firstNotified = false;
        previousValue = 0;
    }

    void initialize(int32_t time)
    {
        hysteresisTime = time;
        countHysteresisTime = hysteresisTime;
        notifiedDirection = 1;
        firstNotified = false;
        previousValue = 0;
    }

    ~HysteresisTimeChecker() {}

    bool checkNotifiable(std::vector<int32_t> thresholds, int32_t currentValue)
    {
        if (thresholds.empty())
        {
            return false;
        }

        bool notifiable = false;

        // cross the threshold case
        auto iterCrossed = find_if(thresholds.begin(), thresholds.end(),
                [=, *this](int32_t thres)
                {
                    return ((currentValue >= thres && previousValue < thres) ||
                            (currentValue < thres && previousValue >= thres));
                });

        if (iterCrossed != thresholds.end())
        {
            uint32_t currentDirection = (currentValue - previousValue) > 0 ? 1 : 0;

            if (countHysteresisTime >= hysteresisTime || currentDirection == notifiedDirection)
            {
                if (!firstNotified)
                {
                    firstNotified = true;
                }

                previousValue = currentValue;
                countHysteresisTime = 0;
                notifiedDirection = currentDirection;
                notifiable = true;
            }

            countHysteresisTime++;
        }
        else
        {
            if (firstNotified)
            {
                countHysteresisTime = 1;
            }
        }

        return notifiable;
    }

    int32_t hysteresisTime;
    int32_t countHysteresisTime;
    int32_t previousValue;
    uint32_t notifiedDirection;
    bool firstNotified;
};

class MediaQualityAnalyzer : public IImsMediaThread
{
public:
    MediaQualityAnalyzer();
    virtual ~MediaQualityAnalyzer();

    /**
     * @brief Set the session callback to send the event
     */
    void setCallback(BaseSessionCallback* callback);

    /**
     * @brief Sets the audio codec type
     * @param config The AudioConfig to set
     */
    void setConfig(AudioConfig* config);

    /**
     * @brief Set the MediaQualityThreshold
     */
    void setMediaQualityThreshold(const MediaQualityThreshold& threshold);

    /**
     * @brief Sets the interval of the RTP reception statistics notification params for checking the
     *        current status of the rtp stream. It will trigger the notifyRtpReceptionStats() with
     *        the RtpReceptionStats.
     *
     * @param intervalMs The interval of the time in milliseconds of the rtp reception notification
     */
    void setNotifyRtpReceptionStatsInterval(const int32_t intervalMs);

    /**
     * @brief Check the audio config has different codec values
     *
     * @param config The AudioConfig to compare
     */
    bool isSameConfig(AudioConfig* config);

    /**
     * @brief Start for calculating statistics from collected datas
     */
    void start();

    /**
     * @brief Stop calculating the statistics from collected datas and send a report
     */
    void stop();

    /**
     * @brief Collect information of sending or receiving the rtp or the rtcp packet datas.
     *
     * @param streamType The stream type. Tx, Rx, Rtcp.
     * @param param The structure set of the rtp/rtcp payload.
     */
    void collectInfo(const int32_t streamType, uint64_t param);

    /**
     * @brief Collect optional information of sending or receiving the rtp or rtcp packet datas.
     *
     * @param optionType The optional type to collect. The TTL or the Round Trip delay.
     * @param seq The sequence number of the packet to collect.
     * @param value The optional value to collect.
     */
    void collectOptionalInfo(const int32_t optionType, const int32_t seq, const int32_t value);

    /**
     * @brief Collects Rtp status determined from the jitter buffer.
     *
     * @param seq The packet sequence number to collect.
     * @param status The status of the packet. Check in @link{kRtpPacketStatus}
     * @param time The time marked when the frame was played in milliseconds unit
     */
    void collectRxRtpStatus(const int32_t seq, const kRtpPacketStatus status, const uint32_t time);

    /**
     * @brief Collects jitter buffer size.
     *
     * @param currSize The current size of the jitter buffer.
     * @param maxSize The maximum jitter buffer size.
     */
    void collectJitterBufferSize(const int32_t currSize, const int32_t maxSize);

    /**
     * @brief generate  Rtcp-Xr report blocks with given report block enabled in bitmask type
     *
     * @param nReportBlocks The bitmask of report block to creates
     * @param data The byte array of total report blocks
     * @param size The size of total report blocks together
     * @return true The report block is not zero and data is valid
     * @return false The report block is zero or got error during create the report block
     */
    bool getRtcpXrReportBlock(const uint32_t nReportBlocks, uint8_t* data, uint32_t& size);

    /**
     * @brief Get the CallQuality member instance
     */
    CallQuality getCallQuality();

    /**
     * @brief Get number of rx packets in the list
     */
    uint32_t getRxPacketSize();

    /**
     * @brief Get number of tx packets in the list
     */
    uint32_t getTxPacketSize();

    /**
     * @brief Get number of lost packets in the list
     */
    uint32_t getLostPacketSize();

    /**
     * @brief Set the event handler time factor. This method is to adjust the event timing intervals
     * only for the testing purpose to reduce the testing time. The normal case, the default time
     * interval will be used.
     *
     * For example: if 100 is passed in, then the event processing period will be reduced from
     * 20ms to 200us and the call quality processing period will be reduced from 1 second to 10ms.
     *
     * @param timeFactor Time intervals will be divided by this value.  The maximum value is 1000.
     */
    void setEventTimeFactor(const uint32_t timeFactor);

    /**
     * @brief Send message event to event handler
     *
     * @param event The event type
     * @param paramA The 1st parameter
     * @param paramB The 2nd parameter
     */
    void SendEvent(uint32_t event, uint64_t paramA, uint64_t paramB = 0);

protected:
    /**
     * @brief Process the data stacked in the list
     *
     * @param timeCount The count increased every second
     */
    void processData(const int32_t timeCount);
    void processMediaQuality();
    void processRtpReceptionStats(const int32_t timeCount);
    void notifyCallQuality();
    void notifyMediaQualityStatus();
    void AddEvent(uint32_t event, uint64_t paramA, uint64_t paramB);
    void processEvent(uint32_t event, uint64_t paramA, uint64_t paramB);
    virtual void* run();
    void reset();
    void clearPacketList(std::list<RtpPacket*>& list, const int32_t seq);
    void clearLostPacketList(const int32_t seq);
    uint32_t getCallQuality(double lossRate);
    int32_t convertAudioCodecType(const int32_t codec, const int32_t bandwidth);

    BaseSessionCallback* mCallback;
    std::unique_ptr<RtcpXrEncoder> mRtcpXrEncoder;
    /** The list of the packets received ordered by arrival time */
    std::list<RtpPacket*> mListRxPacket;
    /** The list of the lost packets object */
    std::list<LostPacket*> mListLostPacket;
    /** The list of the packets sent */
    std::list<RtpPacket*> mListTxPacket;
    /** The time of call started in milliseconds unit*/
    int32_t mTimeStarted;
    /** The ssrc of the receiving Rtp stream to identify */
    int32_t mSSRC;
    /** The codec type of the audio session retrieved from the AudioConfig.h */
    int32_t mCodecType;
    /** The codec attribute of the audio session, it could be bandwidth in evs codec */
    int32_t mCodecAttribute;
    /** Whether RTP is activated for the receiver or not */
    bool mIsRxRtpEnabled;
    /** Whether RTCP is activated for both sender and receiver */
    bool mIsRtcpEnabled;
    /** The begin of the rx rtp packet sequence number for Rtcp-Xr report */
    int32_t mBeginSeq;
    /** The end of the rx rtp packet sequence number for Rtcp-Xr report */
    int32_t mEndSeq;
    /** The call quality structure to report */
    CallQuality mCallQuality;
    /** The sum of the relative jitter of rx packet for call quality */
    int64_t mCallQualitySumRelativeJitter;
    /** The sum of the round trip delay of the session for call quality */
    uint64_t mSumRoundTripTime;
    /** The number of the round trip delay of the session for call quality */
    uint32_t mCountRoundTripTime;
    /** The current jitter buffer size in milliseconds unit */
    uint32_t mCurrentBufferSize;
    /** The maximum jitter buffer size in milliseconds unit */
    uint32_t mMaxBufferSize;
    /** The number of rx packet received for call quality calculation */
    uint32_t mCallQualityNumRxPacket;
    /** The number of lost rx packet for call quality calculation */
    uint32_t mCallQualityNumLostPacket;
    /** The number of dropped packets this period for call quality calculation */
    uint32_t mCallQualityNumDroppedPacket;
    /** The number of received packets this period for call quality inactivity */
    uint32_t mCallQualityInactNumRxPacket;
    /** The number of 1s periods without activity for call quality inactivity */
    uint32_t mCallQualityNumInactPeriods;
    /** The list of the playout delay of the audio frames */
    std::list<uint32_t> mListPlayoutDelay;

    // MediaQualityThreshold parameters
    std::vector<int32_t> mBaseRtpInactivityTimes;
    std::vector<int32_t> mCurrentRtpInactivityTimes;
    int32_t mRtcpInactivityTime;
    int32_t mRtpHysteresisTime;
    int32_t mPacketLossDuration;
    std::vector<int32_t> mPacketLossThreshold;
    std::vector<int32_t> mJitterThreshold;
    bool mNotifyStatus;

    // Rtp Reception Statistics
    int32_t mReceptionInterval;
    int32_t mLatestRtcpSrTimestamp;
    int64_t mLatestRtcpSrNtpTimestamp;
    int32_t mLatestRoundTripDelayMs;

    // Counter for inactivity check
    int32_t mCountRtpInactivity;
    int32_t mCountRtcpInactivity;

    /** The MediaQualityStatus structure to report */
    MediaQualityStatus mQualityStatus;

    /** The number of received packet to check packet loss notification */
    uint32_t mNumRxPacket;
    /** The number of lost packet to check packet loss notification */
    uint32_t mNumLostPacket;
    /** The cumulated jitter value when any rx packet received */
    double mJitterRxPacket;
    /** The number of rtcp packet received */
    uint32_t mNumRtcpPacketReceived;

    HysteresisTimeChecker mPacketLossChecker;
    HysteresisTimeChecker mJitterChecker;

    // event parameters
    std::list<uint32_t> mListevent;
    std::list<uint64_t> mListParamA;
    std::list<uint64_t> mListParamB;
    std::mutex mEventMutex;
    ImsMediaCondition mConditionExit;
    uint32_t mTimeFactor;
};

#endif