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

#include <RtpEncoderNode.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <ImsMediaVideoUtil.h>
#include <AudioConfig.h>
#include <VideoConfig.h>
#include <TextConfig.h>
#include <string.h>

RtpEncoderNode::RtpEncoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mRtpSession = nullptr;
    mDtmfMode = false;
    mMark = false;
    mPrevTimestamp = 0;
    mSamplingRate = 0;
    mRtpPayloadTx = 0;
    mRtpPayloadRx = 0;
    mRtpTxDtmfPayload = 0;
    mRtpRxDtmfPayload = 0;
    mDtmfSamplingRate = 0;
    mDtmfTimestamp = 0;
    mCvoValue = CVO_DEFINE_NONE;
    mRedundantLevel = 0;
    mRedundantPayload = 0;
}

RtpEncoderNode::~RtpEncoderNode()
{
    // remove IRtpSession here to avoid shared instance in other node from unable to use
    if (mRtpSession)
    {
        mRtpSession->StopRtp();
        mRtpSession->SetRtpEncoderListener(nullptr);
        IRtpSession::ReleaseInstance(mRtpSession);
        mRtpSession = nullptr;
    }
}

kBaseNodeId RtpEncoderNode::GetNodeId()
{
    return kNodeIdRtpEncoder;
}

ImsMediaResult RtpEncoderNode::Start()
{
    IMLOGD1("[Start] type[%d]", mMediaType);

    if (mRtpPayloadTx == 0 || mRtpPayloadRx == 0)
    {
        IMLOGE0("[Start] invalid payload number");
        return RESULT_INVALID_PARAM;
    }

    if (mRtpSession == nullptr)
    {
        mRtpSession = IRtpSession::GetInstance(mMediaType, mLocalAddress, mPeerAddress);

        if (mRtpSession == nullptr)
        {
            IMLOGE0("[Start] Can't create rtp session");
            return RESULT_NOT_READY;
        }
    }

    mRtpSession->SetRtpEncoderListener(this);

    if (mMediaType == IMS_MEDIA_AUDIO)
    {
        mRtpSession->SetRtpPayloadParam(mRtpPayloadTx, mRtpPayloadRx, mSamplingRate * 1000,
                mRtpTxDtmfPayload, mRtpRxDtmfPayload, mDtmfSamplingRate * 1000);

        if (mRtpContextParams.getSequenceNumber() >= 0)
        {
            // Set the next sequence number to use by RTP stack.
            mRtpSession->SetRtpContext(mRtpContextParams.getSsrc(),
                    mRtpContextParams.getTimestamp(), mRtpContextParams.getSequenceNumber() + 1);
        }
    }
    else if (mMediaType == IMS_MEDIA_VIDEO)
    {
        mRtpSession->SetRtpPayloadParam(mRtpPayloadTx, mRtpPayloadRx, mSamplingRate * 1000);
    }
    else if (mMediaType == IMS_MEDIA_TEXT)
    {
        if (mRedundantPayload > 0)
        {
            mRtpSession->SetRtpPayloadParam(mRtpPayloadTx, mRtpPayloadRx, mSamplingRate * 1000,
                    mRedundantPayload, mSamplingRate * 1000);
        }
        else
        {
            mRtpSession->SetRtpPayloadParam(mRtpPayloadTx, mRtpPayloadRx, mSamplingRate * 1000);
        }

        if (mRtpContextParams.getSequenceNumber() > 0)
        {
            // Set the next sequence number to use by RTP stack.
            mRtpSession->SetRtpContext(mRtpContextParams.getSsrc(),
                    mRtpContextParams.getTimestamp(), mRtpContextParams.getSequenceNumber() + 1);
        }
    }

    mRtpSession->StartRtp();
    mDtmfMode = false;
    mMark = true;
    mPrevTimestamp = 0;
#ifdef DEBUG_JITTER_GEN_SIMULATION_DELAY
    mNextTime = 0;
#endif
#ifdef DEBUG_JITTER_GEN_SIMULATION_REORDER
    jitterData.Clear();
    mReorderDataCount = 0;
#endif
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void RtpEncoderNode::Stop()
{
    IMLOGD1("[Stop] type[%d]", mMediaType);

    if (mRtpSession)
    {
        mRtpSession->StopRtp();
    }

    mRtpContextParams.setDefaultConfig();

    ClearDataQueue();
    mNodeState = kNodeStateStopped;
}

void RtpEncoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data, uint32_t size,
        uint32_t timestamp, bool mark, uint32_t /*seq*/, ImsMediaSubType /*dataType*/,
        uint32_t arrivalTime)
{
    if (mNodeState != kNodeStateRunning)
    {
        return;
    }

    if (mMediaType == IMS_MEDIA_AUDIO)
    {
        mArrivalTime = arrivalTime;
        ProcessAudioData(subtype, data, size, timestamp);
    }
    else if (mMediaType == IMS_MEDIA_VIDEO)
    {
        ProcessVideoData(subtype, data, size, timestamp, mark);
    }
    else if (mMediaType == IMS_MEDIA_TEXT)
    {
        ProcessTextData(subtype, data, size, timestamp, mark);
    }
}

bool RtpEncoderNode::IsSourceNode()
{
    return false;
}

void RtpEncoderNode::SetConfig(void* config)
{
    IMLOGD1("[SetConfig] media[%d]", mMediaType);

    if (config == nullptr)
    {
        return;
    }

    if (mMediaType == IMS_MEDIA_AUDIO)
    {
        AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);
        mPeerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
        mSamplingRate = pConfig->getSamplingRateKHz();
        mRtpPayloadTx = pConfig->getTxPayloadTypeNumber();
        mRtpPayloadRx = pConfig->getRxPayloadTypeNumber();
        mRtpTxDtmfPayload = pConfig->getTxDtmfPayloadTypeNumber();
        mRtpRxDtmfPayload = pConfig->getRxDtmfPayloadTypeNumber();
        mDtmfSamplingRate = pConfig->getDtmfsamplingRateKHz();

        RtpContextParams rtpContextParams = pConfig->getRtpContextParams();

        if (pConfig->getAccessNetwork() == ACCESS_NETWORK_IWLAN &&
                          rtpContextParams.getSequenceNumber() >= 0)
        {
            SetRtpContext(rtpContextParams);
        }
    }
    else if (mMediaType == IMS_MEDIA_VIDEO)
    {
        VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
        mPeerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
        mSamplingRate = pConfig->getSamplingRateKHz();
        mRtpPayloadTx = pConfig->getTxPayloadTypeNumber();
        mRtpPayloadRx = pConfig->getRxPayloadTypeNumber();
        mCvoValue = pConfig->getCvoValue();
    }
    else if (mMediaType == IMS_MEDIA_TEXT)
    {
        TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
        mPeerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
        mSamplingRate = pConfig->getSamplingRateKHz();
        mRtpPayloadTx = pConfig->getTxPayloadTypeNumber();
        mRtpPayloadRx = pConfig->getRxPayloadTypeNumber();
        mRedundantPayload = pConfig->getRedundantPayload();
        mRedundantLevel = pConfig->getRedundantLevel();

        RtpContextParams rtpContextParams = pConfig->getRtpContextParams();

        if (pConfig->getAccessNetwork() == ACCESS_NETWORK_IWLAN &&
                            rtpContextParams.getSequenceNumber() > 0)
        {
            SetRtpContext(rtpContextParams);
        }
    }

    IMLOGD2("[SetConfig] peer Ip[%s], port[%d]", mPeerAddress.ipAddress, mPeerAddress.port);
}

bool RtpEncoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    if (mMediaType == IMS_MEDIA_AUDIO)
    {
        AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);
        return (mPeerAddress ==
                        RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort()) &&
                mRtpContextParams == pConfig->getRtpContextParams() &&
                mSamplingRate == pConfig->getSamplingRateKHz() &&
                mRtpPayloadTx == pConfig->getTxPayloadTypeNumber() &&
                mRtpPayloadRx == pConfig->getRxPayloadTypeNumber() &&
                mRtpTxDtmfPayload == pConfig->getTxDtmfPayloadTypeNumber() &&
                mRtpRxDtmfPayload == pConfig->getRxDtmfPayloadTypeNumber() &&
                mDtmfSamplingRate == pConfig->getDtmfsamplingRateKHz());
    }
    else if (mMediaType == IMS_MEDIA_VIDEO)
    {
        VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
        return (mPeerAddress ==
                        RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort()) &&
                mSamplingRate == pConfig->getSamplingRateKHz() &&
                mRtpPayloadTx == pConfig->getTxPayloadTypeNumber() &&
                mRtpPayloadRx == pConfig->getRxPayloadTypeNumber() &&
                mCvoValue == pConfig->getCvoValue());
    }
    else if (mMediaType == IMS_MEDIA_TEXT)
    {
        TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
        return (mPeerAddress ==
                        RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort()) &&
                mRtpContextParams == pConfig->getRtpContextParams() &&
                mSamplingRate == pConfig->getSamplingRateKHz() &&
                mRtpPayloadTx == pConfig->getTxPayloadTypeNumber() &&
                mRtpPayloadRx == pConfig->getRxPayloadTypeNumber() &&
                mRedundantPayload == pConfig->getRedundantPayload() &&
                mRedundantLevel == pConfig->getRedundantLevel());
    }

    return false;
}

void RtpEncoderNode::OnRtpPacket(unsigned char* data, uint32_t nSize)
{
    SendDataToRearNode(
            MEDIASUBTYPE_RTPPACKET, data, nSize, 0, 0, 0, MEDIASUBTYPE_UNDEFINED, mArrivalTime);
}

void RtpEncoderNode::SetLocalAddress(const RtpAddress& address)
{
    mLocalAddress = address;
}

void RtpEncoderNode::SetPeerAddress(const RtpAddress& address)
{
    mPeerAddress = address;
}

bool RtpEncoderNode::SetCvoExtension(const int64_t facing, const int64_t orientation)
{
    IMLOGD3("[SetCvoExtension] cvoValue[%d], facing[%ld], orientation[%ld]", mCvoValue, facing,
            orientation);

    if (mCvoValue > 0)
    {
        uint32_t rotation = 0;
        uint32_t cameraId = 0;

        if (facing == kCameraFacingRear)
        {
            cameraId = 1;
        }

        switch (orientation)
        {
            default:
            case 0:
                rotation = 0;
                break;
            case 270:
                rotation = 1;
                break;
            case 180:
                rotation = 2;
                break;
            case 90:
                rotation = 3;
                break;
        }

        if (cameraId == 1)  // rear camera
        {
            if (rotation == 1)  // CCW90
            {
                rotation = 3;
            }
            else if (rotation == 3)  // CCW270
            {
                rotation = 1;
            }
        }

        int8_t extensionData[4];  // 32bit
        IMLOGD3("[SetCvoExtension] cvoValue[%d], facing[%d], orientation[%d]", mCvoValue, cameraId,
                rotation);

        extensionData[0] = (mCvoValue << 4) | 1;  // local identifier and data length
        extensionData[1] = (cameraId << 3) | rotation;
        extensionData[2] = 0;  // padding
        extensionData[3] = 0;  // padding

        mListRtpExtension.clear();
        mListRtpExtension.push_back(RtpHeaderExtensionInfo(
                RtpHeaderExtensionInfo::kBitPatternForOneByteHeader, 1, extensionData, 4));
        return true;
    }

    return false;
}

void RtpEncoderNode::SetRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension)
{
    if (listExtension == nullptr || listExtension->empty())
    {
        return;
    }

    /**
     * Check number of byte of the header. Based on RFC8285 4.2, one byte header has a local
     * identifier in range of 1 to 14. Two byte header has is a range of 1 to 255.
     */
    bool useTwoByteHeader = false;
    int32_t totalPayloadLength = 0;  // accumulate payload length except the header size

    for (auto extension : *listExtension)
    {
        if (extension.getLocalIdentifier() > 15)
        {
            useTwoByteHeader = true;
        }

        totalPayloadLength += extension.getExtensionDataSize();
    }

    // accumulate header size
    useTwoByteHeader ? totalPayloadLength += 2 * listExtension->size()
                     : totalPayloadLength += listExtension->size();

    // padding size
    int32_t paddingSize = totalPayloadLength % IMS_MEDIA_WORD_SIZE == 0
            ? 0
            : IMS_MEDIA_WORD_SIZE - totalPayloadLength % IMS_MEDIA_WORD_SIZE;
    totalPayloadLength += paddingSize;

    int8_t* extensionData = new int8_t[totalPayloadLength];
    int offset = 0;

    for (auto extension : *listExtension)
    {
        if (useTwoByteHeader)
        {
            extensionData[offset++] = extension.getLocalIdentifier();
            extensionData[offset++] = extension.getExtensionDataSize();
        }
        else
        {
            extensionData[offset++] =
                    extension.getLocalIdentifier() << 4 | (extension.getExtensionDataSize() - 1);
        }

        memcpy(extensionData + offset, extension.getExtensionData(),
                extension.getExtensionDataSize());
        offset += extension.getExtensionDataSize();
    }

    // add padding
    memset(extensionData + offset, 0, paddingSize);

    IMLOGD3("[SetRtpHeaderExtension] twoByte[%d], size[%d], list size[%d]", useTwoByteHeader,
            totalPayloadLength, listExtension->size());

    int16_t defineByProfile = useTwoByteHeader
            ? RtpHeaderExtensionInfo::kBitPatternForTwoByteHeader
            : RtpHeaderExtensionInfo::kBitPatternForOneByteHeader;
    mListRtpExtension.push_back(RtpHeaderExtensionInfo(
            defineByProfile, totalPayloadLength / 4, extensionData, totalPayloadLength));

    delete[] extensionData;
}

void RtpEncoderNode::ProcessAudioData(
        ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp)
{
    std::lock_guard<std::mutex> guard(mMutex);

    uint32_t timeDiff;
    uint32_t timestampDiff;

    if (subtype == MEDIASUBTYPE_DTMFSTART)
    {
        IMLOGD0("[ProcessAudioData] SetDTMF mode true");
        mDtmfMode = true;
        mMark = true;
    }
    else if (subtype == MEDIASUBTYPE_DTMFEND)
    {
        IMLOGD0("[ProcessAudioData] SetDTMF mode false");
        mDtmfMode = false;
        mMark = true;
    }
    else if (subtype == MEDIASUBTYPE_DTMF_PAYLOAD)
    {
        if (mDtmfMode)
        {
            timeDiff = timestamp - mPrevTimestamp;
            mMark ? mDtmfTimestamp = timestamp : timeDiff = 0;
            mPrevTimestamp = timestamp;
            timestampDiff = timeDiff * mSamplingRate;

            IMLOGD_PACKET3(IM_PACKET_LOG_RTP,
                    "[ProcessAudioData] dtmf payload, size[%u], TS[%u], diff[%u]", size,
                    mDtmfTimestamp, timestampDiff);
            mRtpSession->SendRtpPacket(
                    mRtpTxDtmfPayload, data, size, mDtmfTimestamp, mMark, timestampDiff);
            mMark = false;
        }
    }
    else  // MEDIASUBTYPE_RTPPAYLOAD
    {
        if (!mDtmfMode)
        {
            timeDiff = mPrevTimestamp == 0 ? 0 : ((timestamp - mPrevTimestamp) + 10) / 20 * 20;
            mPrevTimestamp = timestamp;

            RtpPacket* packet = new RtpPacket();
            packet->rtpDataType = kRtpDataTypeNormal;
            mCallback->SendEvent(
                    kCollectPacketInfo, kStreamRtpTx, reinterpret_cast<uint64_t>(packet));

            timestampDiff = timeDiff * mSamplingRate;
            IMLOGD_PACKET3(IM_PACKET_LOG_RTP, "[ProcessAudioData] size[%u], TS[%u], diff[%u]", size,
                    timestamp, timestampDiff);

            if (!mListRtpExtension.empty())
            {
                mRtpSession->SendRtpPacket(mRtpPayloadTx, data, size, timestamp, mMark,
                        timestampDiff, &mListRtpExtension.front());
                mListRtpExtension.pop_front();
            }
            else
            {
                mRtpSession->SendRtpPacket(
                        mRtpPayloadTx, data, size, timestamp, mMark, timestampDiff);
            }

            if (mMark)
            {
                mMark = false;
            }
        }
    }
}

void RtpEncoderNode::ProcessVideoData(
        ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp, bool mark)
{
    IMLOGD_PACKET4(IM_PACKET_LOG_RTP, "[ProcessVideoData] subtype[%d], size[%d], TS[%u], mark[%d]",
            subtype, size, timestamp, mark);

#ifdef SIMULATE_VIDEO_CVO_UPDATE
    const int64_t kCameraFacing = kCameraFacingFront;
    static int64_t sDeviceOrientation = 0;
    static int64_t sCount = 0;
    if ((++sCount % 100) == 0)
    {
        SetCvoExtension(kCameraFacing, (sDeviceOrientation += 90) % 360);
    }
#endif

    if (mCvoValue > 0 && mark && subtype == MEDIASUBTYPE_VIDEO_IDR_FRAME)
    {
        mRtpSession->SendRtpPacket(mRtpPayloadTx, data, size, timestamp, mark, 0,
                mListRtpExtension.empty() ? nullptr : &mListRtpExtension.front());
    }
    else
    {
        mRtpSession->SendRtpPacket(mRtpPayloadTx, data, size, timestamp, mark, 0);
    }
}

void RtpEncoderNode::ProcessTextData(
        ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp, bool mark)
{
    IMLOGD_PACKET4(IM_PACKET_LOG_RTP,
            "[ProcessTextData] subtype[%d], size[%d], timestamp[%d], mark[%d]", subtype, size,
            timestamp, mark);

    uint32_t timeDiff = mMark ? 0 : timestamp - mPrevTimestamp;

    if (subtype == MEDIASUBTYPE_BITSTREAM_T140)
    {
        if (mRedundantLevel > 1 && mRedundantPayload > 0)
        {
            mRtpSession->SendRtpPacket(mRedundantPayload, data, size, timestamp, mark, timeDiff);
        }
        else
        {
            mRtpSession->SendRtpPacket(mRtpPayloadRx, data, size, timestamp, mark, timeDiff);
        }
    }
    else if (subtype == MEDIASUBTYPE_BITSTREAM_T140_RED)
    {
        mRtpSession->SendRtpPacket(mRtpPayloadTx, data, size, timestamp, mark, timeDiff);
    }

    mMark = false;
    mPrevTimestamp = timestamp;
}

void RtpEncoderNode::SetRtpContext(RtpContextParams& rtpContextParams)
{
    mRtpContextParams = rtpContextParams;
}

void RtpEncoderNode::GetRtpContext(RtpContextParams& rtpContextParams)
{
    uint32_t ssrc = 0;
    uint32_t timestamp = 0;
    uint16_t seqNumber = 0;

    if (mRtpSession != nullptr)
    {
        mRtpSession->GetRtpContext(ssrc, timestamp, seqNumber);
    }

    rtpContextParams.setSsrc(ssrc);
    rtpContextParams.setTimestamp(timestamp);
    rtpContextParams.setSequenceNumber(seqNumber);
}