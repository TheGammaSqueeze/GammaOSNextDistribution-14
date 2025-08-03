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

#ifndef __HARDWARE_EXYNOS_ACRYLIC_DEVICE_H__
#define __HARDWARE_EXYNOS_ACRYLIC_DEVICE_H__

#include <string>

class AcrylicDevice {
public:
    AcrylicDevice(const char *path);
    virtual ~AcrylicDevice();
    int ioctl(int cmd, void *arg);
private:
    bool open();

    std::string mDevPath;
    int mDevFD;
};

#define MAX_DEVICE_FD 3
class AcrylicRedundantDevice {
public:
    AcrylicRedundantDevice(const char *path);
    ~AcrylicRedundantDevice();
    int ioctl_unique(int cmd, void *arg);
    int ioctl_current(int cmd, void *arg);
    int ioctl_broadcast(int cmd, void *arg);
    int ioctl_single(int cmd, void *arg) {
        int ret = ioctl_current(cmd, arg);

        mFdIdx++;
        mFdIdx = mFdIdx % MAX_DEVICE_FD;
        return ret;

    }
private:
    bool open();

    std:: string mDevPath;
    int mDevFd[MAX_DEVICE_FD];
    int mFdIdx;
};

#endif //__HARDWARE_EXYNOS_ACRYLIC_DEVICE_H__
