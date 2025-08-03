// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: 22d34680c8ac

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2DevicePoller"

#include <v4l2_codec2/v4l2/V4L2DevicePoller.h>

#include <string>

#include <base/bind.h>
#include <base/threading/sequenced_task_runner_handle.h>
#include <base/threading/thread_checker.h>
#include <log/log.h>

#include <v4l2_codec2/v4l2/V4L2Device.h>

namespace android {

V4L2DevicePoller::V4L2DevicePoller(V4L2Device* const device, const std::string& threadName,
                                   scoped_refptr<base::SequencedTaskRunner> taskRunner)
      : mDevice(device),
        mPollThread(std::move(threadName)),
        mClientTaskTunner(std::move(taskRunner)),
        mStopPolling(false) {}

V4L2DevicePoller::~V4L2DevicePoller() {
    ALOG_ASSERT(mClientTaskTunner->RunsTasksInCurrentSequence());

    stopPolling();
}

bool V4L2DevicePoller::startPolling(EventCallback eventCallback,
                                    base::RepeatingClosure errorCallback) {
    ALOG_ASSERT(mClientTaskTunner->RunsTasksInCurrentSequence());

    if (isPolling()) return true;

    ALOGV("Starting polling");

    mErrorCallback = errorCallback;

    if (!mPollThread.Start()) {
        ALOGE("Failed to start device poll thread");
        return false;
    }

    mEventCallback = std::move(eventCallback);

    mPollBuffers.store(false);
    mStopPolling.store(false);
    mPollThread.task_runner()->PostTask(
            FROM_HERE, base::BindOnce(&V4L2DevicePoller::devicePollTask, base::Unretained(this)));

    ALOGV("Polling thread started");

    schedulePoll();

    return true;
}

bool V4L2DevicePoller::stopPolling() {
    ALOG_ASSERT(mClientTaskTunner->RunsTasksInCurrentSequence());

    if (!isPolling()) return true;

    ALOGV("Stopping polling");

    mStopPolling.store(true);

    if (!mDevice->setDevicePollInterrupt()) {
        ALOGE("Failed to interrupt device poll.");
        return false;
    }

    ALOGV("Stop device poll thread");
    mPollThread.Stop();

    if (!mDevice->clearDevicePollInterrupt()) {
        ALOGE("Failed to clear interrupting device poll.");
        return false;
    }

    ALOGV("Polling thread stopped");

    return true;
}

bool V4L2DevicePoller::isPolling() const {
    ALOG_ASSERT(mClientTaskTunner->RunsTasksInCurrentSequence());

    return mPollThread.IsRunning();
}

void V4L2DevicePoller::schedulePoll() {
    ALOG_ASSERT(mClientTaskTunner->RunsTasksInCurrentSequence());

    // A call to DevicePollTask() will be posted when we actually start polling.
    if (!isPolling()) return;

    if (!mPollBuffers.exchange(true)) {
        // Call mDevice->setDevicePollInterrupt only if pollBuffers is not
        // already pending.
        ALOGV("Scheduling poll");
        if (!mDevice->setDevicePollInterrupt()) {
            ALOGE("Failed to clear interrupting device poll.");
        }
    }
}

void V4L2DevicePoller::devicePollTask() {
    ALOG_ASSERT(mPollThread.task_runner()->RunsTasksInCurrentSequence());

    while (true) {
        ALOGV("Waiting for poll to be scheduled.");

        if (mStopPolling) {
            ALOGV("Poll stopped, exiting.");
            break;
        }

        bool event_pending = false;
        bool buffers_pending = false;

        ALOGV("Polling device.");
        bool poll_buffers = mPollBuffers.exchange(false);
        if (!mDevice->poll(true, poll_buffers, &event_pending, &buffers_pending)) {
            ALOGE("An error occurred while polling, calling error callback");
            mClientTaskTunner->PostTask(FROM_HERE, mErrorCallback);
            return;
        }

        if (poll_buffers && !buffers_pending) {
            // If buffer polling was requested but the buffers are not pending,
            // then set to poll buffers again in the next iteration.
            mPollBuffers.exchange(true);
        }

        if (!mDevice->clearDevicePollInterrupt()) {
            ALOGE("Failed to clear interrupting device poll.");
        }

        if (buffers_pending || event_pending) {
            ALOGV("Poll returned, calling event callback. event_pending=%d buffers_pending=%d",
                  event_pending, buffers_pending);
            mClientTaskTunner->PostTask(FROM_HERE, base::Bind(mEventCallback, event_pending));
        }
    }
}

}  // namespace android
