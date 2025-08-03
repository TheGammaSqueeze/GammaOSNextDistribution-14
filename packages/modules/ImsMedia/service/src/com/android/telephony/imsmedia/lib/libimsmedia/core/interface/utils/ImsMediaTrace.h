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

#ifndef IMS_MEDIA_TRACE_H_INCLUDED
#define IMS_MEDIA_TRACE_H_INCLUDED
#include <stdint.h>

enum IM_LOG_MODE
{
    kLogEnableUnknown = 0,
    kLogEnableVerbose,
    kLogEnableDebug,
    kLogEnableInfo,
    kLogEnableWarning,
    kLogEnableError,
};

enum IM_PACKET_LOG_TYPE
{
    IM_PACKET_LOG_SOCKET = 1 << 0,
    IM_PACKET_LOG_AUDIO = 1 << 1,
    IM_PACKET_LOG_VIDEO = 1 << 2,
    IM_PACKET_LOG_TEXT = 1 << 3,
    IM_PACKET_LOG_RTP = 1 << 4,
    IM_PACKET_LOG_PH = 1 << 5,
    IM_PACKET_LOG_JITTER = 1 << 6,
    IM_PACKET_LOG_RTCP = 1 << 7,
    IM_PACKET_LOG_RTPSTACK = 1 << 8,
    IM_PACKET_LOG_VIDEO_JITTER = 1 << 9
};

#define IMLOGD_PACKET0(type, format)  \
    ImsMediaTrace::IMLOGD_PACKET_ARG( \
            type, "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__)
#define IMLOGD_PACKET1(type, format, a)                       \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a)
#define IMLOGD_PACKET2(type, format, a, b)                    \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b)
#define IMLOGD_PACKET3(type, format, a, b, c)                 \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c)
#define IMLOGD_PACKET4(type, format, a, b, c, d)              \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c, d)
#define IMLOGD_PACKET5(type, format, a, b, c, d, e)           \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c, d, e)
#define IMLOGD_PACKET6(type, format, a, b, c, d, e, f)        \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c, d, e, f)
#define IMLOGD_PACKET7(type, format, a, b, c, d, e, f, g)     \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c, d, e, f, g)
#define IMLOGD_PACKET8(type, format, a, b, c, d, e, f, g, h)  \
    ImsMediaTrace::IMLOGD_PACKET_ARG(type, "[%s:%d] " format, \
            ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b, c, d, e, f, g, h)

#define IMLOGI0(format)        \
    ImsMediaTrace::IMLOGI_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__)
#define IMLOGI1(format, a)     \
    ImsMediaTrace::IMLOGI_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a)
#define IMLOGI2(format, a, b)  \
    ImsMediaTrace::IMLOGI_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b)
#define IMLOGI3(format, a, b, c)                                                                   \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c)
#define IMLOGI4(format, a, b, c, d)                                                                \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d)
#define IMLOGI5(format, a, b, c, d, e)                                                             \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e)
#define IMLOGI6(format, a, b, c, d, e, f)                                                          \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f)
#define IMLOGI7(format, a, b, c, d, e, f, g)                                                       \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g)
#define IMLOGI8(format, a, b, c, d, e, f, g, h)                                                    \
    ImsMediaTrace::IMLOGI_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g, h)

#define IMLOGD0(format)        \
    ImsMediaTrace::IMLOGD_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__)
#define IMLOGD1(format, a)     \
    ImsMediaTrace::IMLOGD_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a)
#define IMLOGD2(format, a, b)  \
    ImsMediaTrace::IMLOGD_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b)
#define IMLOGD3(format, a, b, c)                                                                   \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c)
#define IMLOGD4(format, a, b, c, d)                                                                \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d)
#define IMLOGD5(format, a, b, c, d, e)                                                             \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e)
#define IMLOGD6(format, a, b, c, d, e, f)                                                          \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f)
#define IMLOGD7(format, a, b, c, d, e, f, g)                                                       \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g)
#define IMLOGD8(format, a, b, c, d, e, f, g, h)                                                    \
    ImsMediaTrace::IMLOGD_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g, h)

#define IMLOGW0(format)        \
    ImsMediaTrace::IMLOGW_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__)
#define IMLOGW1(format, a)     \
    ImsMediaTrace::IMLOGW_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a)
#define IMLOGW2(format, a, b)  \
    ImsMediaTrace::IMLOGW_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b)
#define IMLOGW3(format, a, b, c)                                                                   \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c)
#define IMLOGW4(format, a, b, c, d)                                                                \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d)
#define IMLOGW5(format, a, b, c, d, e)                                                             \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e)
#define IMLOGW6(format, a, b, c, d, e, f)                                                          \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f)
#define IMLOGW7(format, a, b, c, d, e, f, g)                                                       \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g)
#define IMLOGW8(format, a, b, c, d, e, f, g, h)                                                    \
    ImsMediaTrace::IMLOGW_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g, h)

#define IMLOGE0(format)        \
    ImsMediaTrace::IMLOGE_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__)
#define IMLOGE1(format, a)     \
    ImsMediaTrace::IMLOGE_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a)
#define IMLOGE2(format, a, b)  \
    ImsMediaTrace::IMLOGE_ARG( \
            "[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), __LINE__, a, b)
#define IMLOGE3(format, a, b, c)                                                                   \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c)
#define IMLOGE4(format, a, b, c, d)                                                                \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d)
#define IMLOGE5(format, a, b, c, d, e)                                                             \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e)
#define IMLOGE6(format, a, b, c, d, e, f)                                                          \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f)
#define IMLOGE7(format, a, b, c, d, e, f, g)                                                       \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g)
#define IMLOGE8(format, a, b, c, d, e, f, g, h)                                                    \
    ImsMediaTrace::IMLOGE_ARG("[%s:%d] " format, ImsMediaTrace::IM_StripFileName((char*)__FILE__), \
            __LINE__, a, b, c, d, e, f, g, h)

#define IMLOGB(a, b, c) ImsMediaTrace::IMLOGD_BINARY(a, b, c)

class ImsMediaTrace
{
public:
    static void IMLOGD_PACKET_ARG(IM_PACKET_LOG_TYPE type, const char* format, ...);
    static void IMSetLogMode(uint32_t mode);
    static void IMSetDebugLogMode(uint32_t mode);
    static uint32_t IMGetDebugLog();
    static void IMLOGD_ARG(const char* format, ...);
    static void IMLOGI_ARG(const char* format, ...);
    static void IMLOGW_ARG(const char* format, ...);
    static void IMLOGE_ARG(const char* format, ...);
    static char* IMTrace_Bin2String(const char* s, int length);
    static void IMLOGD_BINARY(const char* msg, const char* s, int length);
    static char* IM_StripFileName(char* pcFileName);
};

#endif