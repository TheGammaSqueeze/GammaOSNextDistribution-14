// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_VIDEO
#define LOG_TAG "DecodeComponent"

#include <v4l2_codec2/components/DecodeComponent.h>

#include <inttypes.h>
#include <linux/videodev2.h>
#include <stdint.h>

#include <memory>

#include <C2.h>
#include <C2PlatformSupport.h>
#include <Codec2Mapper.h>
#include <SimpleC2Interface.h>
#include <base/bind.h>
#include <base/callback_helpers.h>
#include <base/strings/stringprintf.h>
#include <base/time/time.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <media/stagefright/foundation/ColorUtils.h>
#include <utils/Trace.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/common/H264NalParser.h>
#include <v4l2_codec2/common/HEVCNalParser.h>
#include <v4l2_codec2/common/VideoTypes.h>
#include <v4l2_codec2/components/BitstreamBuffer.h>
#include <v4l2_codec2/components/VideoFramePool.h>

namespace android {
namespace {

// Mask against 30 bits to avoid (undefined) wraparound on signed integer.
int32_t frameIndexToBitstreamId(c2_cntr64_t frameIndex) {
    return static_cast<int32_t>(frameIndex.peeku() & 0x3FFFFFFF);
}

bool parseCodedColorAspects(const C2ConstLinearBlock& input, std::optional<VideoCodec> codec,
                            C2StreamColorAspectsInfo::input* codedAspects) {
    C2ReadView view = input.map().get();
    NalParser::ColorAspects aspects;
    std::unique_ptr<NalParser> parser;
    if (codec == VideoCodec::H264) {
        parser = std::make_unique<H264NalParser>(view.data(), view.capacity());
    } else if (codec == VideoCodec::HEVC) {
        parser = std::make_unique<HEVCNalParser>(view.data(), view.capacity());
    } else {
        ALOGV("Unsupported codec for finding color aspects");
        return false;
    }

    if (!parser->locateSPS()) {
        ALOGV("Couldn't find SPS");
        return false;
    }

    if (!parser->findCodedColorAspects(&aspects)) {
        ALOGV("Couldn't find color description in SPS");
        return false;
    }

    // Convert ISO color aspects to ColorUtils::ColorAspects.
    ColorAspects colorAspects;
    ColorUtils::convertIsoColorAspectsToCodecAspects(
            aspects.primaries, aspects.transfer, aspects.coeffs, aspects.fullRange, colorAspects);
    ALOGV("Parsed ColorAspects from bitstream: (R:%d, P:%d, M:%d, T:%d)", colorAspects.mRange,
          colorAspects.mPrimaries, colorAspects.mMatrixCoeffs, colorAspects.mTransfer);

    // Map ColorUtils::ColorAspects to C2StreamColorAspectsInfo::input parameter.
    if (!C2Mapper::map(colorAspects.mPrimaries, &codedAspects->primaries)) {
        codedAspects->primaries = C2Color::PRIMARIES_UNSPECIFIED;
    }
    if (!C2Mapper::map(colorAspects.mRange, &codedAspects->range)) {
        codedAspects->range = C2Color::RANGE_UNSPECIFIED;
    }
    if (!C2Mapper::map(colorAspects.mMatrixCoeffs, &codedAspects->matrix)) {
        codedAspects->matrix = C2Color::MATRIX_UNSPECIFIED;
    }
    if (!C2Mapper::map(colorAspects.mTransfer, &codedAspects->transfer)) {
        codedAspects->transfer = C2Color::TRANSFER_UNSPECIFIED;
    }

    return true;
}

bool isWorkDone(const C2Work& work) {
    const int32_t bitstreamId = frameIndexToBitstreamId(work.input.ordinal.frameIndex);

    // Exception: EOS work should be processed by reportEOSWork().
    // Always return false here no matter the work is actually done.
    if (work.input.flags & C2FrameData::FLAG_END_OF_STREAM) return false;

    // Work is done when all conditions meet:
    // 1. mDecoder has released the work's input buffer.
    // 2. mDecoder has returned the work's output buffer in normal case,
    //    or the input buffer is CSD, or we decide to drop the frame.
    bool inputReleased = (work.input.buffers.front() == nullptr);
    bool outputReturned = !work.worklets.front()->output.buffers.empty();
    bool ignoreOutput = (work.input.flags & C2FrameData::FLAG_CODEC_CONFIG) ||
                        (work.worklets.front()->output.flags & C2FrameData::FLAG_DROP_FRAME);
    ALOGV("work(%d): inputReleased: %d, outputReturned: %d, ignoreOutput: %d", bitstreamId,
          inputReleased, outputReturned, ignoreOutput);
    return inputReleased && (outputReturned || ignoreOutput);
}

bool isNoShowFrameWork(const C2Work& work, const C2WorkOrdinalStruct& currOrdinal) {
    // We consider Work contains no-show frame when all conditions meet:
    // 1. Work's ordinal is smaller than current ordinal.
    // 2. Work's output buffer is not returned.
    // 3. Work is not EOS, CSD, or marked with dropped frame.
    bool smallOrdinal = (work.input.ordinal.timestamp < currOrdinal.timestamp) &&
                        (work.input.ordinal.frameIndex < currOrdinal.frameIndex);
    bool outputReturned = !work.worklets.front()->output.buffers.empty();
    bool specialWork = (work.input.flags & C2FrameData::FLAG_END_OF_STREAM) ||
                       (work.input.flags & C2FrameData::FLAG_CODEC_CONFIG) ||
                       (work.worklets.front()->output.flags & C2FrameData::FLAG_DROP_FRAME);
    return smallOrdinal && !outputReturned && !specialWork;
}

}  // namespace

DecodeComponent::DecodeComponent(uint32_t debugStreamId, const std::string& name, c2_node_id_t id,
                                 const std::shared_ptr<DecodeInterface>& intfImpl)
      : mDebugStreamId(debugStreamId),
        mIntfImpl(intfImpl),
        mIntf(std::make_shared<SimpleInterface<DecodeInterface>>(name.c_str(), id, mIntfImpl)) {
    ALOGV("%s(%s)", __func__, name.c_str());
    mIsSecure = name.find(".secure") != std::string::npos;
}

DecodeComponent::~DecodeComponent() {
    ALOGV("%s()", __func__);
    if (mDecoderThread.IsRunning() && !mDecoderTaskRunner->RunsTasksInCurrentSequence()) {
        mDecoderTaskRunner->PostTask(FROM_HERE,
                                     ::base::BindOnce(&DecodeComponent::releaseTask, mWeakThis));
        mDecoderThread.Stop();
    }
    ALOGV("%s() done", __func__);
}

c2_status_t DecodeComponent::start() {
    ALOGV("%s()", __func__);
    std::lock_guard<std::mutex> lock(mStartStopLock);

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::STOPPED) {
        ALOGE("Could not start at %s state", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }

    if (!mDecoderThread.Start()) {
        ALOGE("Decoder thread failed to start.");
        return C2_CORRUPTED;
    }
    mDecoderTaskRunner = mDecoderThread.task_runner();
    mWeakThis = mWeakThisFactory.GetWeakPtr();

    c2_status_t status = C2_CORRUPTED;
    ::base::WaitableEvent done;
    mDecoderTaskRunner->PostTask(
            FROM_HERE, ::base::BindOnce(&DecodeComponent::startTask, mWeakThis,
                                        ::base::Unretained(&status), ::base::Unretained(&done)));
    done.Wait();

    if (status == C2_OK) mComponentState.store(ComponentState::RUNNING);
    return status;
}

std::unique_ptr<VideoFramePool> DecodeComponent::getVideoFramePool(const ui::Size& size,
                                                                   HalPixelFormat pixelFormat,
                                                                   size_t numBuffers) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    auto sharedThis = weak_from_this().lock();
    if (sharedThis == nullptr) {
        ALOGE("%s(): DecodeComponent instance is destroyed.", __func__);
        return nullptr;
    }

    // (b/157113946): Prevent malicious dynamic resolution change exhausts system memory.
    constexpr int kMaximumSupportedArea = 4096 * 4096;
    if (getArea(size).value_or(INT_MAX) > kMaximumSupportedArea) {
        ALOGE("The output size (%dx%d) is larger than supported size (4096x4096)", size.width,
              size.height);
        reportError(C2_BAD_VALUE);
        return nullptr;
    }

    // Get block pool ID configured from the client.
    auto poolId = mIntfImpl->getBlockPoolId();
    ALOGI("Using C2BlockPool ID = %" PRIu64 " for allocating output buffers", poolId);
    std::shared_ptr<C2BlockPool> blockPool;
    auto status = GetCodec2BlockPool(poolId, std::move(sharedThis), &blockPool);
    if (status != C2_OK) {
        ALOGE("Graphic block allocator is invalid: %d", status);
        reportError(status);
        return nullptr;
    }

    return VideoFramePool::Create(std::move(blockPool), numBuffers, size, pixelFormat, mIsSecure,
                                  mDecoderTaskRunner);
}

c2_status_t DecodeComponent::stop() {
    ALOGV("%s()", __func__);
    std::lock_guard<std::mutex> lock(mStartStopLock);

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::RUNNING && currentState != ComponentState::ERROR) {
        ALOGE("Could not stop at %s state", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }

    if (mDecoderThread.IsRunning()) {
        mDecoderTaskRunner->PostTask(FROM_HERE,
                                     ::base::BindOnce(&DecodeComponent::stopTask, mWeakThis));
        mDecoderThread.Stop();
        mDecoderTaskRunner = nullptr;
    }

    mComponentState.store(ComponentState::STOPPED);
    return C2_OK;
}

void DecodeComponent::stopTask() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    reportAbandonedWorks();
    mIsDraining = false;

    releaseTask();
}

c2_status_t DecodeComponent::reset() {
    ALOGV("%s()", __func__);

    return stop();
}

c2_status_t DecodeComponent::release() {
    ALOGV("%s()", __func__);
    std::lock_guard<std::mutex> lock(mStartStopLock);

    if (mDecoderThread.IsRunning()) {
        mDecoderTaskRunner->PostTask(FROM_HERE,
                                     ::base::BindOnce(&DecodeComponent::releaseTask, mWeakThis));
        mDecoderThread.Stop();
        mDecoderTaskRunner = nullptr;
    }

    mComponentState.store(ComponentState::RELEASED);
    return C2_OK;
}

void DecodeComponent::releaseTask() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    mWeakThisFactory.InvalidateWeakPtrs();
    mDecoder = nullptr;
}

c2_status_t DecodeComponent::setListener_vb(const std::shared_ptr<C2Component::Listener>& listener,
                                            c2_blocking_t mayBlock) {
    ALOGV("%s()", __func__);

    auto currentState = mComponentState.load();
    if (currentState == ComponentState::RELEASED ||
        (currentState == ComponentState::RUNNING && listener)) {
        ALOGE("Could not set listener at %s state", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }
    if (currentState == ComponentState::RUNNING && mayBlock != C2_MAY_BLOCK) {
        ALOGE("Could not set listener at %s state non-blocking",
              ComponentStateToString(currentState));
        return C2_BLOCKING;
    }

    // If the decoder thread is not running it's safe to update the listener directly.
    if (!mDecoderThread.IsRunning()) {
        mListener = listener;
        return C2_OK;
    }

    ::base::WaitableEvent done;
    mDecoderTaskRunner->PostTask(
            FROM_HERE, ::base::Bind(&DecodeComponent::setListenerTask, mWeakThis, listener, &done));
    done.Wait();
    return C2_OK;
}

void DecodeComponent::setListenerTask(const std::shared_ptr<Listener>& listener,
                                      ::base::WaitableEvent* done) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    mListener = listener;
    done->Signal();
}

c2_status_t DecodeComponent::queue_nb(std::list<std::unique_ptr<C2Work>>* const items) {
    ALOGV("%s()", __func__);

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::RUNNING) {
        ALOGE("Could not queue at state: %s", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }

    while (!items->empty()) {
        if (ATRACE_ENABLED()) {
            const std::string atraceLabel = ::base::StringPrintf("#%u C2Work", mDebugStreamId);
            ATRACE_ASYNC_BEGIN(atraceLabel.c_str(),
                               items->front()->input.ordinal.frameIndex.peekull());
        }

        mDecoderTaskRunner->PostTask(FROM_HERE,
                                     ::base::BindOnce(&DecodeComponent::queueTask, mWeakThis,
                                                      std::move(items->front())));
        items->pop_front();
    }
    return C2_OK;
}

void DecodeComponent::queueTask(std::unique_ptr<C2Work> work) {
    ATRACE_CALL();
    ALOGV("%s(): flags=0x%x, index=%llu, timestamp=%llu", __func__, work->input.flags,
          work->input.ordinal.frameIndex.peekull(), work->input.ordinal.timestamp.peekull());
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    if (work->worklets.size() != 1u || work->input.buffers.size() > 1u) {
        ALOGE("Invalid work: worklets.size()=%zu, input.buffers.size()=%zu", work->worklets.size(),
              work->input.buffers.size());
        work->result = C2_CORRUPTED;
        reportWork(std::move(work));
        return;
    }

    work->worklets.front()->output.flags = static_cast<C2FrameData::flags_t>(0);
    work->worklets.front()->output.buffers.clear();
    work->worklets.front()->output.ordinal = work->input.ordinal;
    if (work->input.buffers.empty()) {
        // Client may queue a work with no input buffer for either it's EOS or empty CSD, otherwise
        // every work must have one input buffer.
        if ((work->input.flags & C2FrameData::FLAG_END_OF_STREAM) == 0 &&
            (work->input.flags & C2FrameData::FLAG_CODEC_CONFIG) == 0) {
            ALOGE("Invalid work: work with no input buffer should be EOS or CSD.");
            reportError(C2_BAD_VALUE);
            return;
        }

        // Emplace a nullptr to unify the check for work done.
        ALOGV("Got a work with no input buffer! Emplace a nullptr inside.");
        work->input.buffers.emplace_back(nullptr);
    }

    mPendingWorks.push(std::move(work));
    pumpPendingWorks();
}

void DecodeComponent::pumpPendingWorks() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::RUNNING) {
        ALOGW("Could not pump C2Work at state: %s", ComponentStateToString(currentState));
        return;
    }

    while (!mPendingWorks.empty() && !mIsDraining) {
        std::unique_ptr<C2Work> pendingWork(std::move(mPendingWorks.front()));
        mPendingWorks.pop();

        const int32_t bitstreamId = frameIndexToBitstreamId(pendingWork->input.ordinal.frameIndex);
        const bool isCSDWork = pendingWork->input.flags & C2FrameData::FLAG_CODEC_CONFIG;
        const bool isEmptyWork = pendingWork->input.buffers.front() == nullptr;
        const bool isEOSWork = pendingWork->input.flags & C2FrameData::FLAG_END_OF_STREAM;
        const C2Work* work = pendingWork.get();
        ALOGV("Process C2Work bitstreamId=%d isCSDWork=%d, isEmptyWork=%d", bitstreamId, isCSDWork,
              isEmptyWork);

        auto res = mWorksAtDecoder.insert(std::make_pair(bitstreamId, std::move(pendingWork)));
        ALOGW_IF(!res.second, "We already inserted bitstreamId %d to decoder?", bitstreamId);

        if (!isEmptyWork) {
            if (isCSDWork) {
                processCSDWork(bitstreamId, work);
            } else {
                processWork(bitstreamId, work);
            }
        }

        if (isEOSWork) {
            mDecoder->drain(::base::BindOnce(&DecodeComponent::onDrainDone, mWeakThis));
            mIsDraining = true;
        }

        // Directly report the empty CSD work as finished.
        if (isCSDWork && isEmptyWork) reportWorkIfFinished(bitstreamId);
    }
}

void DecodeComponent::processCSDWork(const int32_t bitstreamId, const C2Work* work) {
    // If input.buffers is not empty, the buffer should have meaningful content inside.
    C2ConstLinearBlock linearBlock = work->input.buffers.front()->data().linearBlocks().front();
    ALOG_ASSERT(linearBlock.size() > 0u, "Input buffer of work(%d) is empty.", bitstreamId);

    if (mIntfImpl->getVideoCodec() == VideoCodec::VP9) {
        // The VP9 decoder does not support and does not need the Codec Specific Data (CSD):
        // https://www.webmproject.org/docs/container/#vp9-codec-feature-metadata-codecprivate.
        // The most of its content (profile, level, bit depth and chroma subsampling)
        // can be extracted directly from VP9 bitstream. Ignore CSD if it was passed.
        reportWorkIfFinished(bitstreamId);
        return;
    } else if ((!mIsSecure && mIntfImpl->getVideoCodec() == VideoCodec::H264) ||
               mIntfImpl->getVideoCodec() == VideoCodec::HEVC) {
        // Try to parse color aspects from bitstream for CSD work of non-secure H264 codec or HEVC
        // codec (HEVC will only be CENCv3 which is parseable for secure).
        C2StreamColorAspectsInfo::input codedAspects = {0u};
        if (parseCodedColorAspects(linearBlock, mIntfImpl->getVideoCodec(), &codedAspects)) {
            std::vector<std::unique_ptr<C2SettingResult>> failures;
            c2_status_t status = mIntfImpl->config({&codedAspects}, C2_MAY_BLOCK, &failures);
            if (status != C2_OK) {
                ALOGE("Failed to config color aspects to interface: %d", status);
                reportError(status);
                return;
            }
            // Record current frame index, color aspects should be updated only for output
            // buffers whose frame indices are not less than this one.
            mPendingColorAspectsChange = true;
            mPendingColorAspectsChangeFrameIndex = work->input.ordinal.frameIndex.peeku();
        }
    }

    processWorkBuffer(bitstreamId, linearBlock);
}

void DecodeComponent::processWork(const int32_t bitstreamId, const C2Work* work) {
    // If input.buffers is not empty, the buffer should have meaningful content inside.
    C2ConstLinearBlock linearBlock = work->input.buffers.front()->data().linearBlocks().front();
    ALOG_ASSERT(linearBlock.size() > 0u, "Input buffer of work(%d) is empty.", bitstreamId);

    processWorkBuffer(bitstreamId, linearBlock);
}

void DecodeComponent::processWorkBuffer(const int32_t bitstreamId,
                                        const C2ConstLinearBlock& linearBlock) {
    std::unique_ptr<ConstBitstreamBuffer> buffer = std::make_unique<ConstBitstreamBuffer>(
            bitstreamId, linearBlock, linearBlock.offset(), linearBlock.size());
    if (!buffer) {
        reportError(C2_CORRUPTED);
        return;
    }
    mDecoder->decode(std::move(buffer),
                     ::base::BindOnce(&DecodeComponent::onDecodeDone, mWeakThis, bitstreamId));
}

void DecodeComponent::onDecodeDone(int32_t bitstreamId, VideoDecoder::DecodeStatus status) {
    ATRACE_CALL();
    ALOGV("%s(bitstreamId=%d, status=%s)", __func__, bitstreamId,
          VideoDecoder::DecodeStatusToString(status));
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    auto it = mWorksAtDecoder.find(bitstreamId);
    ALOG_ASSERT(it != mWorksAtDecoder.end());
    C2Work* work = it->second.get();

    switch (status) {
    case VideoDecoder::DecodeStatus::kAborted:
        work->input.buffers.front().reset();
        work->worklets.front()->output.flags = static_cast<C2FrameData::flags_t>(
                work->worklets.front()->output.flags & C2FrameData::FLAG_DROP_FRAME);
        mOutputBitstreamIds.push(bitstreamId);

        pumpReportWork();
        return;

    case VideoDecoder::DecodeStatus::kError:
        reportError(C2_CORRUPTED);
        return;

    case VideoDecoder::DecodeStatus::kOk:
        // Release the input buffer.
        work->input.buffers.front().reset();

        // CSD Work doesn't have output buffer, the corresponding onOutputFrameReady() won't be
        // called. Push the bitstreamId here.
        if (work->input.flags & C2FrameData::FLAG_CODEC_CONFIG)
            mOutputBitstreamIds.push(bitstreamId);

        pumpReportWork();
        return;
    }
}

void DecodeComponent::onOutputFrameReady(std::unique_ptr<VideoFrame> frame) {
    ALOGV("%s(bitstreamId=%d)", __func__, frame->getBitstreamId());
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    const int32_t bitstreamId = frame->getBitstreamId();
    auto it = mWorksAtDecoder.find(bitstreamId);
    if (it == mWorksAtDecoder.end()) {
        ALOGE("Work with bitstreamId=%d not found, already abandoned?", bitstreamId);
        reportError(C2_CORRUPTED);
        return;
    }
    C2Work* work = it->second.get();

    C2ConstGraphicBlock constBlock = std::move(frame)->getGraphicBlock();
    std::shared_ptr<C2Buffer> buffer = C2Buffer::CreateGraphicBuffer(std::move(constBlock));
    if (mPendingColorAspectsChange &&
        work->input.ordinal.frameIndex.peeku() >= mPendingColorAspectsChangeFrameIndex) {
        mIntfImpl->queryColorAspects(&mCurrentColorAspects);
        mPendingColorAspectsChange = false;
    }
    if (mCurrentColorAspects) {
        buffer->setInfo(mCurrentColorAspects);
    }
    work->worklets.front()->output.buffers.emplace_back(std::move(buffer));

    // Check no-show frame by timestamps for VP8/VP9 cases before reporting the current work.
    if (mIntfImpl->getVideoCodec() == VideoCodec::VP8 ||
        mIntfImpl->getVideoCodec() == VideoCodec::VP9) {
        detectNoShowFrameWorksAndReportIfFinished(work->input.ordinal);
    }

    mOutputBitstreamIds.push(bitstreamId);
    pumpReportWork();
}

void DecodeComponent::detectNoShowFrameWorksAndReportIfFinished(
        const C2WorkOrdinalStruct& currOrdinal) {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    std::vector<int32_t> noShowFrameBitstreamIds;
    for (auto& kv : mWorksAtDecoder) {
        const int32_t bitstreamId = kv.first;
        const C2Work* work = kv.second.get();

        // A work in mWorksAtDecoder would be considered to have no-show frame if there is no
        // corresponding output buffer returned while the one of the work with latter timestamp is
        // already returned. (VD is outputted in display order.)
        if (isNoShowFrameWork(*work, currOrdinal)) {
            work->worklets.front()->output.flags = C2FrameData::FLAG_DROP_FRAME;

            // We need to call reportWorkIfFinished() for all detected no-show frame works. However,
            // we should do it after the detection loop since reportWorkIfFinished() may erase
            // entries in |mWorksAtDecoder|.
            noShowFrameBitstreamIds.push_back(bitstreamId);
            ALOGV("Detected no-show frame work index=%llu timestamp=%llu",
                  work->input.ordinal.frameIndex.peekull(),
                  work->input.ordinal.timestamp.peekull());
        }
    }

    // Try to report works with no-show frame.
    for (const int32_t bitstreamId : noShowFrameBitstreamIds) reportWorkIfFinished(bitstreamId);
}

void DecodeComponent::pumpReportWork() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    while (!mOutputBitstreamIds.empty()) {
        if (!reportWorkIfFinished(mOutputBitstreamIds.front())) break;
        mOutputBitstreamIds.pop();
    }
}

bool DecodeComponent::reportWorkIfFinished(int32_t bitstreamId) {
    ATRACE_CALL();
    ALOGV("%s(bitstreamId = %d)", __func__, bitstreamId);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    // EOS work will not be reported here. reportEOSWork() does it.
    if (mIsDraining && mWorksAtDecoder.size() == 1u) {
        ALOGV("work(bitstreamId = %d) is EOS Work.", bitstreamId);
        return false;
    }

    auto it = mWorksAtDecoder.find(bitstreamId);
    if (it == mWorksAtDecoder.end()) {
        ALOGI("work(bitstreamId = %d) is dropped, skip.", bitstreamId);
        return true;
    }

    if (!isWorkDone(*(it->second))) {
        ALOGV("work(bitstreamId = %d) is not done yet.", bitstreamId);
        return false;
    }

    std::unique_ptr<C2Work> work = std::move(it->second);
    mWorksAtDecoder.erase(it);

    work->result = C2_OK;
    work->workletsProcessed = static_cast<uint32_t>(work->worklets.size());
    // A work with neither flags nor output buffer would be treated as no-corresponding
    // output by C2 framework, and regain pipeline capacity immediately.
    if (work->worklets.front()->output.flags & C2FrameData::FLAG_DROP_FRAME)
        work->worklets.front()->output.flags = static_cast<C2FrameData::flags_t>(0);

    return reportWork(std::move(work));
}

bool DecodeComponent::reportEOSWork() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    const auto it =
            std::find_if(mWorksAtDecoder.begin(), mWorksAtDecoder.end(), [](const auto& kv) {
                return kv.second->input.flags & C2FrameData::FLAG_END_OF_STREAM;
            });
    if (it == mWorksAtDecoder.end()) {
        ALOGE("Failed to find EOS work.");
        return false;
    }

    std::unique_ptr<C2Work> eosWork(std::move(it->second));
    mWorksAtDecoder.erase(it);

    eosWork->result = C2_OK;
    eosWork->workletsProcessed = static_cast<uint32_t>(eosWork->worklets.size());
    eosWork->worklets.front()->output.flags = C2FrameData::FLAG_END_OF_STREAM;
    if (!eosWork->input.buffers.empty()) eosWork->input.buffers.front().reset();

    if (!mWorksAtDecoder.empty()) {
        ALOGW("There are remaining works except EOS work. abandon them.");
        for (const auto& kv : mWorksAtDecoder) {
            ALOGW("bitstreamId(%d) => Work index=%llu, timestamp=%llu", kv.first,
                  kv.second->input.ordinal.frameIndex.peekull(),
                  kv.second->input.ordinal.timestamp.peekull());
        }
        reportAbandonedWorks();
    }

    return reportWork(std::move(eosWork));
}

bool DecodeComponent::reportWork(std::unique_ptr<C2Work> work) {
    ATRACE_CALL();
    ALOGV("%s(work=%llu)", __func__, work->input.ordinal.frameIndex.peekull());
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    if (!mListener) {
        ALOGE("mListener is nullptr, setListener_vb() not called?");
        return false;
    }

    if (ATRACE_ENABLED()) {
        const std::string atraceLabel = ::base::StringPrintf("#%u C2Work", mDebugStreamId);
        ATRACE_ASYNC_END(atraceLabel.c_str(), work->input.ordinal.frameIndex.peekull());
    }
    std::list<std::unique_ptr<C2Work>> finishedWorks;
    finishedWorks.emplace_back(std::move(work));
    mListener->onWorkDone_nb(weak_from_this(), std::move(finishedWorks));
    return true;
}

c2_status_t DecodeComponent::flush_sm(flush_mode_t mode,
                                      std::list<std::unique_ptr<C2Work>>* const /* flushedWork */) {
    ATRACE_CALL();
    ALOGV("%s()", __func__);

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::RUNNING) {
        ALOGE("Could not flush at state: %s", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }
    if (mode != FLUSH_COMPONENT) {
        return C2_OMITTED;  // Tunneling is not supported by now
    }

    mDecoderTaskRunner->PostTask(FROM_HERE,
                                 ::base::BindOnce(&DecodeComponent::flushTask, mWeakThis));
    return C2_OK;
}

void DecodeComponent::flushTask() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    mDecoder->flush();
    reportAbandonedWorks();

    // Pending EOS work will be abandoned here due to component flush if any.
    mIsDraining = false;
}

void DecodeComponent::reportAbandonedWorks() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    std::list<std::unique_ptr<C2Work>> abandonedWorks;
    while (!mPendingWorks.empty()) {
        abandonedWorks.emplace_back(std::move(mPendingWorks.front()));
        mPendingWorks.pop();
    }
    for (auto& kv : mWorksAtDecoder) {
        abandonedWorks.emplace_back(std::move(kv.second));
    }
    mWorksAtDecoder.clear();

    for (auto& work : abandonedWorks) {
        // TODO: correlate the definition of flushed work result to framework.
        work->result = C2_NOT_FOUND;
        // When the work is abandoned, buffer in input.buffers shall reset by component.
        if (!work->input.buffers.empty()) {
            work->input.buffers.front().reset();
        }

        if (ATRACE_ENABLED()) {
            const std::string atraceLabel = ::base::StringPrintf("#%u C2Work", mDebugStreamId);
            ATRACE_ASYNC_END(atraceLabel.c_str(), work->input.ordinal.frameIndex.peekull());
        }
    }
    if (!abandonedWorks.empty()) {
        if (!mListener) {
            ALOGE("mListener is nullptr, setListener_vb() not called?");
            return;
        }
        mListener->onWorkDone_nb(weak_from_this(), std::move(abandonedWorks));
    }
}

c2_status_t DecodeComponent::drain_nb(drain_mode_t mode) {
    ALOGV("%s(mode=%u)", __func__, mode);

    auto currentState = mComponentState.load();
    if (currentState != ComponentState::RUNNING) {
        ALOGE("Could not drain at state: %s", ComponentStateToString(currentState));
        return C2_BAD_STATE;
    }

    switch (mode) {
    case DRAIN_CHAIN:
        return C2_OMITTED;  // Tunneling is not supported.

    case DRAIN_COMPONENT_NO_EOS:
        return C2_OK;  // Do nothing special.

    case DRAIN_COMPONENT_WITH_EOS:
        mDecoderTaskRunner->PostTask(FROM_HERE,
                                     ::base::BindOnce(&DecodeComponent::drainTask, mWeakThis));
        return C2_OK;
    }
}

void DecodeComponent::drainTask() {
    ATRACE_CALL();
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    if (!mPendingWorks.empty()) {
        ALOGV("Set EOS flag at last queued work.");
        auto& flags = mPendingWorks.back()->input.flags;
        flags = static_cast<C2FrameData::flags_t>(flags | C2FrameData::FLAG_END_OF_STREAM);
        return;
    }

    if (!mWorksAtDecoder.empty()) {
        ALOGV("Drain the pending works at the decoder.");
        mDecoder->drain(::base::BindOnce(&DecodeComponent::onDrainDone, mWeakThis));
        mIsDraining = true;
    }
}

void DecodeComponent::onDrainDone(VideoDecoder::DecodeStatus status) {
    ALOGV("%s(status=%s)", __func__, VideoDecoder::DecodeStatusToString(status));
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    switch (status) {
    case VideoDecoder::DecodeStatus::kAborted:
        return;

    case VideoDecoder::DecodeStatus::kError:
        reportError(C2_CORRUPTED);
        return;

    case VideoDecoder::DecodeStatus::kOk:
        mIsDraining = false;
        if (!reportEOSWork()) {
            reportError(C2_CORRUPTED);
            return;
        }

        mDecoderTaskRunner->PostTask(
                FROM_HERE, ::base::BindOnce(&DecodeComponent::pumpPendingWorks, mWeakThis));
        return;
    }
}

void DecodeComponent::reportError(c2_status_t error) {
    ALOGE("%s(error=%u)", __func__, static_cast<uint32_t>(error));
    ALOG_ASSERT(mDecoderTaskRunner->RunsTasksInCurrentSequence());

    if (mComponentState.load() == ComponentState::ERROR) return;
    mComponentState.store(ComponentState::ERROR);

    if (!mListener) {
        ALOGE("mListener is nullptr, setListener_vb() not called?");
        return;
    }
    mListener->onError_nb(weak_from_this(), static_cast<uint32_t>(error));
}

c2_status_t DecodeComponent::announce_nb(const std::vector<C2WorkOutline>& /* items */) {
    return C2_OMITTED;  // Tunneling is not supported by now
}

std::shared_ptr<C2ComponentInterface> DecodeComponent::intf() {
    return mIntf;
}

// static
const char* DecodeComponent::ComponentStateToString(ComponentState state) {
    switch (state) {
    case ComponentState::STOPPED:
        return "STOPPED";
    case ComponentState::RUNNING:
        return "RUNNING";
    case ComponentState::RELEASED:
        return "RELEASED";
    case ComponentState::ERROR:
        return "ERROR";
    }
}

}  // namespace android
