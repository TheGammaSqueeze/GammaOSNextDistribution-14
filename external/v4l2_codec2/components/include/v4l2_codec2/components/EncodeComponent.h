// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_COMPONENT_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_COMPONENT_H

#include <atomic>
#include <memory>
#include <optional>
#include <unordered_map>
#include <vector>

#include <C2Component.h>
#include <C2ComponentFactory.h>
#include <C2Config.h>
#include <C2Enum.h>
#include <C2Param.h>
#include <C2ParamDef.h>
#include <SimpleC2Interface.h>
#include <base/memory/scoped_refptr.h>
#include <base/single_thread_task_runner.h>
#include <base/synchronization/waitable_event.h>
#include <base/threading/thread.h>
#include <util/C2InterfaceHelper.h>
#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/common/VideoPixelFormat.h>

namespace android {

struct BitstreamBuffer;
class FormatConverter;
class VideoEncoder;
class EncodeInterface;

std::optional<std::vector<VideoFramePlane>> getVideoFrameLayout(const C2ConstGraphicBlock& block,
                                                                VideoPixelFormat* format);

std::optional<uint32_t> getVideoFrameStride(VideoPixelFormat format, ui::Size size);

class EncodeComponent : public C2Component, public std::enable_shared_from_this<EncodeComponent> {
public:
    virtual ~EncodeComponent() override;

    // Implementation of the C2Component interface.
    c2_status_t start() override;
    c2_status_t stop() override;
    c2_status_t reset() override;
    c2_status_t release() override;
    c2_status_t queue_nb(std::list<std::unique_ptr<C2Work>>* const items) override;
    c2_status_t drain_nb(drain_mode_t mode) override;
    c2_status_t flush_sm(flush_mode_t mode,
                         std::list<std::unique_ptr<C2Work>>* const flushedWork) override;
    c2_status_t announce_nb(const std::vector<C2WorkOutline>& items) override;
    c2_status_t setListener_vb(const std::shared_ptr<Listener>& listener,
                               c2_blocking_t mayBlock) override;
    std::shared_ptr<C2ComponentInterface> intf() override;

protected:
    // Possible component states.
    enum class ComponentState {
        UNLOADED,  // Initial state of component.
        LOADED,    // The component is stopped, ready to start running.
        RUNNING,   // The component is currently running.
        ERROR,     // An error occurred.
    };

    EncodeComponent(C2String name, c2_node_id_t id, std::shared_ptr<EncodeInterface> interface);

    EncodeComponent(const EncodeComponent&) = delete;
    EncodeComponent& operator=(const EncodeComponent&) = delete;

    // Initialize the V4L2 device for encoding with the requested configuration.
    virtual bool initializeEncoder() = 0;

    // Initialize the encoder on the encoder thread.
    void startTask(bool* success, ::base::WaitableEvent* done);
    // Destroy the encoder on the encoder thread.
    void stopTask(::base::WaitableEvent* done);
    // Queue a new encode work item on the encoder thread.
    void queueTask(std::unique_ptr<C2Work> work);
    // Drain all currently scheduled work on the encoder thread. The encoder will process all
    // scheduled work and mark the last item as EOS, before processing any new work.
    void drainTask(drain_mode_t drainMode);
    // Called on the encoder thread when a drain is completed.
    void onDrainDone(bool success);
    // Flush all currently scheduled work on the encoder thread. The encoder will abort all
    // scheduled work items, work that can be immediately aborted will be placed in |flushedWork|.
    void flushTask(::base::WaitableEvent* done,
                   std::list<std::unique_ptr<C2Work>>* const flushedWork);
    // Set the component listener on the encoder thread.
    void setListenerTask(const std::shared_ptr<Listener>& listener, ::base::WaitableEvent* done);

    // Update the |mBitrate| and |mFramerate| currently configured on the V4L2 device, to match the
    // values requested by the codec 2.0 framework.
    bool updateEncodingParameters();

    // Schedule the next encode operation on the V4L2 device.
    void scheduleNextEncodeTask();
    // Encode the specified |block| with corresponding |index| and |timestamp|.
    bool encode(C2ConstGraphicBlock block, uint64_t index, int64_t timestamp);
    // Flush the encoder.
    void flush();

    // Fetch a new output buffer from the output block pool with specified |size|.
    void fetchOutputBlock(uint32_t size, std::unique_ptr<BitstreamBuffer>* buffer);

    // Called on the encoder thread when the encoder is done using an input buffer.
    void onInputBufferDone(uint64_t index);
    // Called on the encoder thread when an output buffer is ready.
    void onOutputBufferDone(size_t dataSize, int64_t timestamp, bool keyFrame,
                            std::unique_ptr<BitstreamBuffer> buffer);

    // Helper function to find a work item in the output work queue by index.
    C2Work* getWorkByIndex(uint64_t index);
    // Helper function to find a work item in the output work queue by timestamp.
    C2Work* getWorkByTimestamp(int64_t timestamp);
    // Helper function to determine if the specified |work| item is finished.
    bool isWorkDone(const C2Work& work) const;
    // Notify the listener the specified |work| item is finished.
    void reportWork(std::unique_ptr<C2Work> work);

    // Configure the c2 block pool that will be used to create output buffers.
    bool getBlockPool();

    // Notify the client an error occurred and switch to the error state.
    void reportError(c2_status_t error);

    // Change the state of the component.
    void setComponentState(ComponentState state);
    // Get the specified component |state| as string.
    static const char* componentStateToString(ComponentState state);

    // The underlying V4L2 encoder.
    std::unique_ptr<VideoEncoder> mEncoder;

    // The component's registered name.
    const C2String mName;
    // The component's id, provided by the C2 framework upon initialization.
    const c2_node_id_t mId = 0;
    // The component's interface implementation.
    const std::shared_ptr<EncodeInterface> mInterface;

    // Mutex used by the component to synchronize start/stop/reset/release calls, as the codec 2.0
    // API can be accessed from any thread.
    std::mutex mComponentLock;

    // The component's listener to be notified when events occur, only accessed on encoder thread.
    std::shared_ptr<Listener> mListener;

    // The queue of encode work items waiting for free buffers in the input convertor.
    std::queue<std::unique_ptr<C2Work>> mInputConverterQueue;
    // An input format convertor will be used if the device doesn't support the video's format.
    std::unique_ptr<FormatConverter> mInputFormatConverter;

    // Pixel format of frames sent to V4L2 encoder, determined when the first input frame is queued.
    VideoPixelFormat mInputPixelFormat = VideoPixelFormat::UNKNOWN;
    // Layout of frames sent to V4L2 encoder, determined when the first input frame is queued.
    std::vector<VideoFramePlane> mInputLayout;

    // The bitrate currently configured on the v4l2 device.
    uint32_t mBitrate = 0;
    // The bitrate mode currently configured on the v4l2 device.
    C2Config::bitrate_mode_t mBitrateMode = C2Config::BITRATE_CONST;
    // The framerate currently configured on the v4l2 device.
    uint32_t mFramerate = 0;
    // The timestamp of the last frame encoded, used to dynamically adjust the framerate.
    std::optional<int64_t> mLastFrameTime;

    // Whether we need to extract and submit CSD (codec-specific data, e.g. H.264 SPS).
    bool mExtractCSD = false;

    // The queue of encode work items currently being processed.
    std::deque<std::unique_ptr<C2Work>> mWorkQueue;

    // The output block pool.
    std::shared_ptr<C2BlockPool> mOutputBlockPool;

    // The component state, accessible from any thread as C2Component interface is not thread-safe.
    std::atomic<ComponentState> mComponentState;

    // The encoder thread on which all interaction with the V4L2 device is performed.
    ::base::Thread mEncoderThread{"EncodeComponentThread"};
    // The task runner on the encoder thread.
    scoped_refptr<::base::SequencedTaskRunner> mEncoderTaskRunner;

    // The WeakPtrFactory used to get weak pointers of this.
    ::base::WeakPtr<EncodeComponent> mWeakThis;
    ::base::WeakPtrFactory<EncodeComponent> mWeakThisFactory{this};
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_COMPONENT_H
