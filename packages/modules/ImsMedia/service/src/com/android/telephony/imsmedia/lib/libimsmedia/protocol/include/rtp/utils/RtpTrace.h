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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTP_TRACE_H__
#define __RTP_TRACE_H__

#include <ImsMediaTrace.h>

#define RTP_TRACE_WARNING(a, b, c) IMLOGW2(a, b, c)
#define RTP_TRACE_ERROR(a, b, c)   IMLOGE2(a, b, c)
#define RTP_TRACE_MESSAGE(a, b, c) IMLOGD_PACKET2(IM_PACKET_LOG_RTPSTACK, a, b, c)

#endif  // __RTP_TRACE_H__

/** @}*/
