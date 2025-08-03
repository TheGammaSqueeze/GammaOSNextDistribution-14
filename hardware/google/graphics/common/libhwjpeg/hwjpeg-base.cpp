/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
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

#include <exynos-hwjpeg.h>
#include <fcntl.h>
#include <linux/v4l2-controls.h>
#include <linux/videodev2.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "hwjpeg-internal.h"

CHWJpegBase::CHWJpegBase(const char *path) : m_iFD(-1), m_uiDeviceCaps(0), m_uiAuxFlags(0) {
    m_iFD = open(path, O_RDWR);
    if (m_iFD < 0) ALOGERR("Failed to open '%s'", path);
}

CHWJpegBase::~CHWJpegBase() {
    if (m_iFD >= 0) close(m_iFD);
}

void CHWJpegBase::SetAuxFlags(unsigned int auxflags) {
    ALOGW_IF(!!(m_uiAuxFlags & auxflags),
             "Configuration auxiliary flags %#x overrides previous flags %#x", auxflags,
             m_uiAuxFlags);

    m_uiAuxFlags |= auxflags;
}

void CHWJpegBase::ClearAuxFlags(unsigned int auxflags) {
    ALOGW_IF(!!(m_uiAuxFlags & auxflags) && ((m_uiAuxFlags & auxflags) != auxflags),
             "Clearing auxiliary flags %#x overrides previous flags %#x", auxflags, m_uiAuxFlags);

    m_uiAuxFlags &= ~auxflags;
}

bool CStopWatch::Start() {
    int ret = clock_gettime(CLOCK_MONOTONIC, &m_tBegin);
    if (ret) {
        ALOGERR("Failed to get current clock");
        memset(&m_tBegin, 0, sizeof(m_tBegin));
        return false;
    }

    return true;
}

unsigned long CStopWatch::GetElapsed() {
    timespec tp;
    int ret = clock_gettime(CLOCK_MONOTONIC, &tp);
    if (ret) {
        ALOGERR("Failed to get current clock");
        return 0;
    }

    unsigned long elapsed = (tp.tv_sec - m_tBegin.tv_sec) * 1000000;
    return (m_tBegin.tv_nsec > tp.tv_nsec) ? elapsed - (m_tBegin.tv_nsec - tp.tv_nsec) / 1000
                                           : elapsed + (tp.tv_nsec - m_tBegin.tv_nsec) / 1000;
}

unsigned long CStopWatch::GetElapsedUpdate() {
    timespec tp;
    int ret = clock_gettime(CLOCK_MONOTONIC, &tp);
    if (ret) {
        ALOGERR("Failed to get current clock");
        return 0;
    }

    unsigned long elapsed = (tp.tv_sec - m_tBegin.tv_sec) * 1000000;
    elapsed = (m_tBegin.tv_nsec > tp.tv_nsec) ? elapsed - (m_tBegin.tv_nsec - tp.tv_nsec) / 1000
                                              : elapsed + (tp.tv_nsec - m_tBegin.tv_nsec) / 1000;

    m_tBegin = tp;
    return elapsed;
}

bool WriteToFile(const char *path, const char *data, size_t len) {
    int fd = open(path, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH | S_IWGRP);
    if (fd < 0) {
        ALOGERR("Failed to open '%s' for write/create", path);
        return false;
    }

    ssize_t written = write(fd, data, len);
    close(fd);
    if (written < 0) {
        ALOGERR("Failed to write %zu bytes to '%s'", len, path);
        return false;
    }

    ALOGI("%zu/%zu bytes from ptr %p are written to '%s'", written, len, data, path);

    return true;
}

bool WriteToFile(const char *path, int dmabuf, size_t len) {
    char *p = reinterpret_cast<char *>(
            mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, dmabuf, 0));
    if (p == MAP_FAILED) {
        ALOGERR("Filed to map the given dmabuf fd %d", dmabuf);
        return false;
    }

    int fd = open(path, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH | S_IWGRP);
    if (fd < 0) {
        ALOGERR("Failed to open '%s' for write/create", path);
        munmap(p, len);
        return false;
    }

    ssize_t written = write(fd, p, len);
    if (written < 0)
        ALOGERR("Failed to write %zu bytes to '%s'", len, path);
    else
        ALOGI("%zu/%zu bytes from dmabuf fd %d are written to '%s'", written, len, dmabuf, path);

    munmap(p, len);
    close(fd);

    return true;
}
