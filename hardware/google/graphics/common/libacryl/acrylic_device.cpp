/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <cstring>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include <log/log.h>

#include "acrylic_internal.h"
#include "acrylic_device.h"

AcrylicDevice::AcrylicDevice(const char *devpath)
    : mDevPath(devpath), mDevFD(-1)
{
}

AcrylicDevice::~AcrylicDevice()
{
    if (mDevFD >= 0)
        ::close(mDevFD);
}

bool AcrylicDevice::open()
{
    if (mDevFD >= 0)
        return true;

    mDevFD = ::open(mDevPath.c_str(), O_RDWR);
    if (mDevFD < 0) {
        ALOGERR("Failed to open %s", mDevPath.c_str());
        return false;
    }

    ALOGD_TEST("Opened %s on fd %d", mDevPath.c_str(), mDevFD);

    return true;
}

int AcrylicDevice::ioctl(int cmd, void *arg)
{
    if (!open())
        return -1;

    return ::ioctl(mDevFD, cmd, arg);
}

AcrylicRedundantDevice::AcrylicRedundantDevice(const char *devpath)
    : mDevPath(devpath), mDevFd{-1, -1, -1}, mFdIdx(0)
{
    mDevPath = devpath;
}

AcrylicRedundantDevice::~AcrylicRedundantDevice()
{
    for (int i = 0; i < MAX_DEVICE_FD; i++)
        ::close(mDevFd[i]);
}

bool AcrylicRedundantDevice::open()
{
    if (mDevFd[0] >= 0)
        return true;

    for (int i = 0; i < MAX_DEVICE_FD; i++) {
        if (mDevFd[i] < 0) {
            mDevFd[i] = ::open(mDevPath.c_str(), O_RDWR);
            if (mDevFd[i] < 0) {
                ALOGERR("Failed to open %s for devfd[%d]", mDevPath.c_str(), i);
                while (i-- > 0) {
                    ::close(mDevFd[i]);
                    mDevFd[i] = -1;
                }
                return false;
            }

            ALOGD_TEST("Opened %s on devfd[%d] %d", mDevPath.c_str(), i, mDevFd[i]);
        }
    }

    return true;
}

int AcrylicRedundantDevice::ioctl_unique(int cmd, void *arg)
{
    if (!open())
        return -1;

    return ::ioctl(mDevFd[0], cmd, arg);
}

int AcrylicRedundantDevice::ioctl_current(int cmd, void *arg)
{
    if (!open())
        return -1;

    return ::ioctl(mDevFd[mFdIdx], cmd, arg);
}

int AcrylicRedundantDevice::ioctl_broadcast(int cmd, void *arg) {
    if (!open())
        return -1;

    for (int i = 0; i < MAX_DEVICE_FD; i++) {
        int ret = ::ioctl(mDevFd[i], cmd, arg);
        if (ret < 0)
            return ret;
    }

    return 0;
}
