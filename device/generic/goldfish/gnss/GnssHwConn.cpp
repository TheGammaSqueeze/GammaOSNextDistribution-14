/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <log/log.h>
#include <fcntl.h>
#include <qemud.h>
#include <qemu_pipe_bp.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <debug.h>
#include "GnssHwConn.h"
#include "GnssHwListener.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {
namespace {
constexpr char kCMD_QUIT = 'q';

int epollCtlAdd(int epollFd, int fd) {
    int ret;

    /* make the fd non-blocking */
    ret = TEMP_FAILURE_RETRY(fcntl(fd, F_GETFL));
    if (ret < 0) {
        return FAILURE(ret);
    }
    ret = TEMP_FAILURE_RETRY(fcntl(fd, F_SETFL, ret | O_NONBLOCK));
    if (ret < 0) {
        return FAILURE(ret);
    }

    struct epoll_event ev;
    ev.events  = EPOLLIN;
    ev.data.fd = fd;

    ret = TEMP_FAILURE_RETRY(epoll_ctl(epollFd, EPOLL_CTL_ADD, fd, &ev));
    if (ret < 0) {
        return FAILURE(ret);
    }

    return 0;
}

int workerThreadRcvCommand(const int fd) {
    char buf;
    if (TEMP_FAILURE_RETRY(read(fd, &buf, 1)) == 1) {
        return buf;
    } else {
        return FAILURE(-1);
    }
}

void workerThread(const int devFd, const int threadsFd, GnssHwListener& listener) {
    ALOGD("%s:%s:%d", "GnssHwConn", __func__, __LINE__);

    const unique_fd epollFd(epoll_create1(0));
    LOG_ALWAYS_FATAL_IF(!epollFd.ok(), "%s:%d: epoll_create1 failed",
                        __func__, __LINE__);

    epollCtlAdd(epollFd.get(), devFd);
    epollCtlAdd(epollFd.get(), threadsFd);

    while (true) {
        struct epoll_event events[2];
        const int kTimeoutMs = 60000;
        const int n = TEMP_FAILURE_RETRY(epoll_wait(epollFd.get(),
                                                    events, 2,
                                                    kTimeoutMs));
        if (n < 0) {
            ALOGE("%s:%d: epoll_wait failed with '%s'",
                  __func__, __LINE__, strerror(errno));
            continue;
        }

        for (int i = 0; i < n; ++i) {
            const struct epoll_event* ev = &events[i];
            const int fd = ev->data.fd;
            const int ev_events = ev->events;

            if (fd == devFd) {
                if (ev_events & (EPOLLERR | EPOLLHUP)) {
                    LOG_ALWAYS_FATAL("%s:%d: epoll_wait: devFd has an error, "
                                     "ev_events=%x", __func__, __LINE__, ev_events);
                } else if (ev_events & EPOLLIN) {
                    char buf[64];
                    while (true) {
                        int n = TEMP_FAILURE_RETRY(read(fd, buf, sizeof(buf)));
                        if (n > 0) {
                            listener.consume(buf, n);
                        } else {
                            break;
                        }
                    }
                }
            } else if (fd == threadsFd) {
                if (ev_events & (EPOLLERR | EPOLLHUP)) {
                    LOG_ALWAYS_FATAL("%s:%d: epoll_wait: threadsFd has an error, "
                                     "ev_events=%x", __func__, __LINE__, ev_events);
                } else if (ev_events & EPOLLIN) {
                    const int cmd = workerThreadRcvCommand(fd);
                    switch (cmd) {
                        case kCMD_QUIT:
                            ALOGD("%s:%s:%d", "GnssHwConn", __func__, __LINE__);
                            return;

                        default:
                            LOG_ALWAYS_FATAL("%s:%d: workerThreadRcvCommand returned "
                                             "unexpected command, cmd=%d",
                                             __func__, __LINE__, cmd);
                            break;
                    }
                }
            } else {
                ALOGE("%s:%d: epoll_wait() returned unexpected fd",
                      __func__, __LINE__);
            }
        }
    }
}

}  // namespace

GnssHwConn::GnssHwConn(IDataSink& sink) {
    mDevFd.reset(qemu_pipe_open_ns("qemud", "gps", O_RDWR));
    if (!mDevFd.ok()) {
        ALOGE("%s:%d: qemu_pipe_open_ns failed", __func__, __LINE__);
        return;
    }

    unique_fd threadsFd;
    if (!::android::base::Socketpair(AF_LOCAL, SOCK_STREAM, 0,
                                     &mCallersFd, &threadsFd)) {
        ALOGE("%s:%d: Socketpair failed", __func__, __LINE__);
        mDevFd.reset();
        return;
    }

    std::promise<void> isReadyPromise;
    const int devFd = mDevFd.get();
    mThread = std::thread([devFd, threadsFd = std::move(threadsFd), &sink,
                           &isReadyPromise]() {
        GnssHwListener listener(sink);
        isReadyPromise.set_value();
        workerThread(devFd, threadsFd.get(), listener);
    });

    isReadyPromise.get_future().wait();
}

GnssHwConn::~GnssHwConn() {
    if (mThread.joinable()) {
        sendWorkerThreadCommand(kCMD_QUIT);
        mThread.join();
    }
}

bool GnssHwConn::ok() const {
    return mThread.joinable();
}

bool GnssHwConn::sendWorkerThreadCommand(char cmd) const {
    return (TEMP_FAILURE_RETRY(write(mCallersFd.get(), &cmd, 1)) == 1) ?
        true : FAILURE(false);
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
