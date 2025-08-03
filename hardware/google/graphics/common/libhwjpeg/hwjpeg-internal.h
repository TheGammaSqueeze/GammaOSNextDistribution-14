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

#ifndef __HWJPEG_INTERNAL_H__
#define __HWJPEG_INTERNAL_H__

#ifndef LOG_TAG
#error "LOG_TAG is not defined!"
#endif

#include <log/log.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

#ifdef __GNUC__
#define __UNUSED__ __attribute__((__unused__))
#else
#define __UNUSED__
#endif

#ifndef ALOGERR
#define ALOGERR(fmt, args...) ((void)ALOG(LOG_ERROR, LOG_TAG, fmt " [%s]", ##args, strerror(errno)))
#endif

#define V4L2_CID_JPEG_SEC_COMP_QUALITY (V4L2_CID_JPEG_CLASS_BASE + 20)
#define V4L2_CID_JPEG_QTABLES2 (V4L2_CID_JPEG_CLASS_BASE + 22)
#define V4L2_CID_JPEG_HWFC_ENABLE (V4L2_CID_JPEG_CLASS_BASE + 25)
#define V4L2_CID_JPEG_PADDING (V4L2_CID_JPEG_CLASS_BASE + 26)
#define V4L2_CID_JPEG_SEC_PADDING (V4L2_CID_JPEG_CLASS_BASE + 27)

#define TO_MAIN_SIZE(val) ((val)&0xFFFF)
#define TO_THUMB_SIZE(val) (((val)&0xFFFF) << 16)
#define TO_IMAGE_SIZE(main, thumb) (TO_MAIN_SIZE(main) | TO_THUMB_SIZE(thumb))

#define PTR_TO_ULONG(ptr) reinterpret_cast<unsigned long>(ptr)
#define PTR_DIFF(ptr1, ptr2) (reinterpret_cast<size_t>(ptr2) - reinterpret_cast<size_t>(ptr1))

#define ARRSIZE(v) (sizeof(v) / sizeof(v[0]))

#ifndef min
template <typename T>
static inline T min(T val1, T val2) {
    return (val1 > val2) ? val2 : val1;
}
#endif

#ifndef max
template <typename T>
static inline T max(T val1, T val2) {
    return (val1 < val2) ? val2 : val1;
}
#endif

// H/W requires 16-byte alignment
#define HW_BASE_ALIGN_BITS 4
#define HW_BASE_ALIGN_SIZE (1 << HW_BASE_ALIGN_BITS)
#define HW_BASE_ALIGN_MASK ~(HW_BASE_ALIGN_SIZE - 1)

class CStopWatch {
    timespec m_tBegin;

public:
    CStopWatch(bool start = false) {
        if (start) Start();
    }
    bool Start();
    unsigned long GetElapsed();
    unsigned long GetElapsedUpdate();
};

bool WriteToFile(const char *path, const char *data, size_t len);
bool WriteToFile(const char *path, int dmabuf, size_t len);
#endif //__HWJPEG_INTERNAL_H__
