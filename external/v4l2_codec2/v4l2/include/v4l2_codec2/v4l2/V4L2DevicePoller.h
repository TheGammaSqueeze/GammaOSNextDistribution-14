// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: f65c38dcdac2

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_POLLER_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_POLLER_H

#include <atomic>

#include <base/callback_forward.h>
#include <base/sequence_checker.h>
#include <base/sequenced_task_runner.h>
#include <base/synchronization/waitable_event.h>
#include <base/threading/thread.h>

namespace android {

class V4L2Device;

// Allows a client to poll() on a given V4L2Device and be signaled when a buffer is ready to be
// dequeued or a V4L2 event has been received. Polling is done on a dedicated thread, and
// notifications are delivered in the form of a callback to the listener's sequence.
//
// All the methods of this class (with the exception of the constructor) must be called from the
// same sequence.
//
// Note that the service callback may also be called when no particular event occurred due to the
// way poll() works. It is the responsibility of the caller to call SchedulePoll() again if there
// may still be pending events.
class V4L2DevicePoller {
public:
    // Callback to be called when buffer ready/V4L2 event has potentially been polled. |event| is
    // set if a V4L2 event has been detected.
    using EventCallback = ::base::RepeatingCallback<void(bool event)>;

    // Create a poller for |device|, using a thread named |threadName|. Notification won't start
    // until |startPolling()| is called.
    V4L2DevicePoller(V4L2Device* const device, const std::string& threadName,
                     scoped_refptr<::base::SequencedTaskRunner> taskRunner);
    ~V4L2DevicePoller();

    // Starts polling. |mEventCallback| will be posted on the caller's sequence every time an event
    // occurs. The client is then responsible for consuming all pending events in that callback. If
    // new events may still happen after the callback has run, the client must call |schedulePoll()|
    // again in order to be notified for them.
    //
    // If an error occurs during polling, |mErrorCallback| will be posted on the caller's sequence.
    bool startPolling(EventCallback eventCallback, ::base::RepeatingClosure errorCallback);
    // Stop polling and stop the thread. The poller won't post any new event to the caller's
    // sequence after this method has returned.
    bool stopPolling();
    // Returns true if currently polling, false otherwise.
    bool isPolling() const;
    // Attempts polling the V4L2 device. This method should be called whenever doing something that
    // may trigger an event of interest (buffer dequeue or V4L2 event), for instance queueing a
    // buffer. In the absence of a pending event, poll() will return immediately and the service
    // callback will be posted to the caller's sequence. The client is then responsible for calling
    // this method again when it is interested in receiving events.
    void schedulePoll();

private:
    // Perform a poll() on |mDevice| and post either |mEventCallback| or |mErrorCallback| on the
    // client's sequence when poll() returns.
    void devicePollTask();

    // V4L2 device we are polling.
    V4L2Device* const mDevice;
    // Thread on which polling is done.
    ::base::Thread mPollThread;
    // Callback to post to the client's sequence when an event occurs.
    EventCallback mEventCallback;
    // Closure to post to the client's sequence when an error occurs.
    ::base::RepeatingClosure mErrorCallback;
    // Client sequence's task runner, where closures are posted.
    scoped_refptr<::base::SequencedTaskRunner> mClientTaskTunner;

    // Set to true when we wish to stop polling, instructing the poller thread to break its loop.
    std::atomic_bool mStopPolling;
    // Set to true when we wish poll to await for QBUF/DQBUF readiness
    std::atomic_bool mPollBuffers;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_POLLER_H
